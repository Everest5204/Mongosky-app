package com.mongosky.app.auth

import com.mongosky.app.signup.SignupApi
import com.mongosky.app.signup.SignupException
import java.io.IOException
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AuthNetworkTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    @Before fun before() {
        server = MockWebServer().apply { start() }
        client = OkHttpClient.Builder().retryOnConnectionFailure(false)
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> =
                    if (hostname == "localhost") listOf(InetAddress.getByName("127.0.0.1")) else Dns.SYSTEM.lookup(hostname)
            })
            .readTimeout(2, TimeUnit.SECONDS).callTimeout(4, TimeUnit.SECONDS).build()
    }
    @After fun after() {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        server.shutdown()
        client.dispatcher.executorService.shutdownNow()
    }
    private fun repository() = AuthRepository(client = client, endpoint = server.url("/api/auth/login").toString())
    private fun success(idKey: String = "id") = JSONObject().put("token", "test-token")
        .put("user", JSONObject().put(idKey, "0123456789abcdef01234567")
            .put("first_name", "Test").put("last_name", "User")).toString()
    private suspend fun loginError(): LoginException {
        return try { repository().login("test@example.test", "a safe password"); throw AssertionError("Login should fail") }
        catch (error: LoginException) { error }
    }

    @Test fun firstLoginUsesOneRequestAndPreservesPasswordExactly() = runBlocking {
        server.enqueue(MockResponse().setBody(success()))
        val session = repository().login(" test@example.test ", "  password with spaces  ")
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        val body = JSONObject(request.body.readUtf8())
        assertEquals("test-token", session.token)
        assertEquals("test@example.test", body.getString("email_or_phone"))
        assertEquals("  password with spaces  ", body.getString("password"))
        assertEquals("/api/auth/login", request.path)
        assertNull(request.getHeader("Authorization"))
        assertEquals("no-store", request.getHeader("Cache-Control"))
        assertEquals(1, server.requestCount)
    }

    @Test fun firstConnectionFailureRecoversWithinOneLoginCall() = runBlocking {
        val attempts = AtomicInteger()
        client = client.newBuilder().addInterceptor { chain ->
            if (attempts.getAndIncrement() == 0) throw IOException("Connection reset")
            chain.proceed(chain.request())
        }.build()
        server.enqueue(MockResponse().setBody(success()))
        assertEquals("test-token", repository().login("test@example.test", "a safe password").token)
        assertEquals(2, attempts.get())
        assertEquals(1, server.requestCount)
    }

    @Test fun lostResponseAfterLoginRequestRecoversOnce() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(MockResponse().setBody(success()))
        assertEquals("test-token", repository().login("test@example.test", "a safe password").token)
        assertEquals(2, server.requestCount)
    }

    @Test fun repeatedConnectionFailureStopsAfterTwoAttempts() = runBlocking {
        val attempts = AtomicInteger()
        client = client.newBuilder().addInterceptor { attempts.incrementAndGet(); throw IOException("Offline") }.build()
        assertEquals(LoginFailure.CONNECTION, loginError().failure)
        assertEquals(2, attempts.get())
    }

    @Test fun interruptedSuccessBodyRecoversWithoutAnotherButtonTap() = runBlocking {
        server.enqueue(MockResponse().setBody(success().padEnd(16_000, ' '))
            .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
        server.enqueue(MockResponse().setBody(success()))
        assertEquals("test-token", repository().login("test@example.test", "a safe password").token)
        assertEquals(2, server.requestCount)
    }

    @Test fun incorrectCredentialsAreNotRetriedOrCalledAnInternetProblem() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("{\"message\":\"Incorrect password\"}"))
        val error = loginError()
        assertEquals(LoginFailure.REJECTED, error.failure)
        assertEquals(401, error.statusCode)
        assertEquals("Incorrect password", error.message)
        assertEquals(1, server.requestCount)
    }

    @Test fun truncatedRejectionBodyDoesNotReplayCredentials() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("x".repeat(20_000))
            .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
        val error = loginError()
        assertEquals(LoginFailure.REJECTED, error.failure)
        assertEquals(401, error.statusCode)
        assertEquals(1, server.requestCount)
    }

    @Test fun rateLimitDoesNotRetry() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("{}"))
        assertEquals(LoginFailure.RATE_LIMITED, loginError().failure)
        assertEquals(1, server.requestCount)
    }

    @Test fun serverFailureDoesNotReplayLoginEvenWithImmediateRetryHeader() = runBlocking {
        client = client.newBuilder().retryOnConnectionFailure(true).build()
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0").setBody("{}"))
        val error = loginError()
        assertEquals(LoginFailure.SERVER, error.failure)
        assertFalse(error.message.orEmpty().contains("internet"))
        assertEquals(1, server.requestCount)
    }

    @Test fun malformedSuccessIsAnInvalidResponseNotAnInternetError() = runBlocking {
        server.enqueue(MockResponse().setBody("<html>Proxy error</html>"))
        assertEquals(LoginFailure.INVALID_RESPONSE, loginError().failure)
        assertEquals(1, server.requestCount)
    }

    @Test fun jsonNullIdentityCannotBecomeTheStringNull() = runBlocking {
        server.enqueue(MockResponse().setBody("{\"token\":\"test-token\",\"user\":{\"id\":null}}"))
        assertEquals(LoginFailure.INVALID_RESPONSE, loginError().failure)
    }

    @Test fun mongoIdAliasIsAccepted() = runBlocking {
        server.enqueue(MockResponse().setBody(success("_id")))
        assertEquals("0123456789abcdef01234567", repository().login("test@example.test", "a safe password").userId)
    }

    @Test fun oversizedBodyIsBoundedAndNotRetried() = runBlocking {
        server.enqueue(MockResponse().setBody("x".repeat(65_537)))
        assertEquals(LoginFailure.INVALID_RESPONSE, loginError().failure)
        assertEquals(1, server.requestCount)
    }

    @Test fun timeoutIsReportedSeparatelyAndNotRepeated() = runBlocking {
        client = client.newBuilder().readTimeout(100, TimeUnit.MILLISECONDS).build()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertEquals(LoginFailure.TIMEOUT, loginError().failure)
        assertEquals(1, server.requestCount)
    }

    @Test fun totalBudgetCancelsAnUnresponsiveCall() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val error = runCatching {
            AuthHttp(client, Dispatchers.IO, timeoutMillis = 150).execute(Request.Builder().url(server.url("/slow")).build())
        }.exceptionOrNull()
        assertTrue(error is SocketTimeoutException)
        assertEquals(1, server.requestCount)
    }

    @Test fun cancellingLoginCancelsTheUnderlyingCall() = runBlocking {
        val cancelled = AtomicBoolean()
        client = client.newBuilder().eventListener(object : EventListener() {
            override fun canceled(call: Call) { cancelled.set(true) }
        }).build()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job = async(Dispatchers.IO) { repository().login("test@example.test", "a safe password") }
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        job.cancelAndJoin()
        assertTrue(cancelled.get())
        assertEquals(1, server.requestCount)
    }

    @Test fun redirectsAreNotFollowedWithCredentials() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/other")))
        assertEquals(302, loginError().statusCode)
        assertEquals(1, server.requestCount)
    }

    @Test fun sessionOnlyTreatsUnauthorizedAsExpired() = runBlocking {
        val api = SessionApi(client, server.url("/api/").toString())
        for (status in listOf(403, 429, 500)) {
            server.enqueue(MockResponse().setResponseCode(status).setBody("{}"))
            val error = runCatching { api.getSession("test-token") }.exceptionOrNull()
            assertTrue(error is IOException)
            assertFalse(error is SessionExpiredException)
        }
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        assertTrue(runCatching { api.getSession("test-token") }.exceptionOrNull() is SessionExpiredException)
    }

    @Test fun signupLostResponseNeverReplaysAccountCreation() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        val api = SignupApi(client = client, endpoint = server.url("/api/auth/signup").toString())
        val error = runCatching { api.signup(testSignup()) }.exceptionOrNull()
        assertTrue(error is SignupException)
        assertTrue((error as SignupException).outcomeUnknown)
        assertEquals(1, server.requestCount)
    }
}
