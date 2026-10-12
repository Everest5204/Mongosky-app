package com.mongosky.app.ai

import java.time.Instant
import java.util.UUID
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** All state belongs to one account; network work runs only on the resumed AI route. */
internal class AiController(
    private val scope: CoroutineScope,
    private val readToken: suspend () -> String?,
    private val api: AiActions = AiApi(),
    private val events: AiEvents = AiStream(),
    private val frameMillis: Long = 32
) {
    private val mutable = MutableStateFlow(AiState())
    val state: StateFlow<AiState> = mutable.asStateFlow()
    private var owner: String? = null
    private var token: String? = null
    private var active = false
    private var foreground = false
    private var epoch = 0L
    private var threadRequest = 0L
    private var listRequest = 0L
    private var chatsRevision = 0L
    private var mutationRequest = 0L
    private var inputRevision = 0L
    private var reconcile = false
    private var authJob: Job? = null
    private var listJob: Job? = null
    private var historyJob: Job? = null
    private var streamJob: Job? = null
    private var mutationJob: Job? = null
    private var flushJob: Job? = null
    private val cache = LinkedHashMap<String,List<AiMessage>>(4,0.75f,true)
    private val visible get() = active && foreground && owner != null && !mutable.value.expired

    fun bindSession(identity: String) {
        if (owner == identity) return
        val wasActive = active; val wasForeground = foreground
        endSession(); owner = identity; active = wasActive; foreground = wasForeground
        ensureReady()
    }
    fun endSession() {
        ++epoch; cancelWork(); authJob?.cancel(); authJob=null
        owner=null;token=null;active=false;foreground=false;reconcile=false;cache.clear()
        mutable.value=AiState(inputRevision=++inputRevision)
    }
    fun setActive(value: Boolean) {
        if (active==value) return
        active=value
        if (visible) resume() else pause()
    }
    fun setForeground(value: Boolean) {
        if (foreground==value) return
        foreground=value
        if (visible) resume() else pause()
    }
    private fun resume() {
        if (token==null) ensureReady()
        else {
            refreshChats()
            if (reconcile) { reconcile=false; mutable.value.activeChatId?.let(::loadMessages) }
        }
    }
    private fun pause() {
        reconcile = reconcile || mutable.value.activeChatId!=null
        cancelWork()
        if (token==null) { authJob?.cancel(); authJob=null; ++epoch }
        val current=mutable.value
        mutable.value=current.copy(chatsLoading=false,messagesLoading=false,messagesRefreshing=false,
            sending=false,deletingId=null,clearing=false,messages=interrupted(current.messages))
    }
    private fun cancelWork() {
        ++threadRequest; ++listRequest; ++mutationRequest
        listJob?.cancel();historyJob?.cancel();streamJob?.cancel();mutationJob?.cancel();flushJob?.cancel()
        listJob=null;historyJob=null;streamJob=null;mutationJob=null;flushJob=null
    }
    private fun ensureReady() {
        if (!visible || token!=null || authJob?.isActive==true) return
        val version=epoch
        authJob=scope.launch {
            try {
                val saved=readToken()
                if (!visible || version!=epoch) return@launch
                if (saved==null || !AiPolicy.validToken(saved)) { expire(); return@launch }
                token=saved;mutable.value=mutable.value.copy(ready=true,error=null)
                refreshChats()
                if (reconcile) { reconcile=false; mutable.value.activeChatId?.let(::loadMessages) }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                if (visible && version==epoch) mutable.value=mutable.value.copy(error=AiError("Could not read your saved session. Please try again.",AiErrorAction.CHATS))
            } finally { if(version==epoch) authJob=null }
        }
    }
    fun changeDraft(value: String) = changeInput(TextFieldValue(value,TextRange(value.length)),mutable.value.inputRevision)
    fun changeInput(value: TextFieldValue,expectedRevision: Long) {
        val current=mutable.value
        // Reject commits from the field that existed before Send, New Chat or an account change.
        if(expectedRevision!=current.inputRevision || current.sending || current.expired) return
        val limited=AiPolicy.limitDraft(value.text)
        val input=if(limited==value.text) value else TextFieldValue(limited,TextRange(limited.length))
        mutable.value=current.copy(input=input)
    }
    fun dismissError() { if(!mutable.value.expired) mutable.value=mutable.value.copy(error=null) }
    fun retryRead() {
        if(!visible || mutable.value.sending || mutable.value.mutating) return
        val action=mutable.value.error?.action
        dismissError()
        if(token==null) ensureReady()
        else if(action==AiErrorAction.MESSAGES) mutable.value.activeChatId?.let(::loadMessages)
        else refreshChats()
    }
    fun refreshChats() {
        val credential=token ?: return
        if(!visible) return
        listJob?.cancel(); val request=++listRequest;val version=epoch;val revision=chatsRevision
        mutable.value=mutable.value.copy(chatsLoading=true)
        listJob=scope.launch {
            try {
                val chats=api.chats(credential)
                if(!current(version,credential) || request!=listRequest) return@launch
                mutable.value=mutable.value.copy(chats=if(revision==chatsRevision) chats else mutable.value.chats,
                    chatsLoaded=true,chatsLoading=false)
            } catch(cancel:CancellationException) { throw cancel }
            catch(error:Exception) {
                if(current(version,credential) && request==listRequest) {
                    mutable.value=mutable.value.copy(chatsLoading=false)
                    fail(error,AiErrorAction.CHATS)
                }
            }
        }
    }
    fun newChat() {
        if(mutable.value.mutating || mutable.value.expired) return
        cancelThread()
        reconcile=false
        mutable.value=mutable.value.copy(activeChatId=null,messages=emptyList(),input=TextFieldValue(),inputRevision=++inputRevision,error=null,
            threadVersion=mutable.value.threadVersion+1,messagesLoading=false,messagesRefreshing=false,sending=false)
    }
    fun openChat(id: String) {
        if(!visible || !AiPolicy.validId(id) || mutable.value.mutating) return
        if(mutable.value.activeChatId==id && !mutable.value.sending) {
            if(mutable.value.error!=null) loadMessages(id)
            return
        }
        cancelThread();reconcile=false
        val cached=cache[id].orEmpty()
        mutable.value=mutable.value.copy(activeChatId=id,messages=cached,input=TextFieldValue(),inputRevision=++inputRevision,error=null,
            threadVersion=mutable.value.threadVersion+1,sending=false,messagesLoading=cached.isEmpty())
        loadMessages(id)
    }
    private fun cancelThread() {
        ++threadRequest;historyJob?.cancel();streamJob?.cancel();flushJob?.cancel()
        historyJob=null;streamJob=null;flushJob=null
    }
    private fun loadMessages(id: String) {
        val credential=token ?: return
        if(!visible || mutable.value.sending || !AiPolicy.validId(id)) return
        historyJob?.cancel();val request=++threadRequest;val version=epoch
        mutable.value=mutable.value.copy(messagesLoading=mutable.value.messages.isEmpty(),messagesRefreshing=mutable.value.messages.isNotEmpty(),error=null)
        historyJob=scope.launch {
            try {
                val messages=api.messages(credential,id)
                if(!currentThread(version,credential,request) || mutable.value.activeChatId!=id) return@launch
                saveCache(id,messages)
                mutable.value=mutable.value.copy(messages=messages,messagesLoading=false,messagesRefreshing=false,error=null)
            } catch(cancel:CancellationException) { throw cancel }
            catch(error:Exception) {
                if(currentThread(version,credential,request) && mutable.value.activeChatId==id) {
                    mutable.value=mutable.value.copy(messagesLoading=false,messagesRefreshing=false)
                    fail(error,AiErrorAction.MESSAGES)
                }
            }
        }
    }
    fun send() {
        val credential=token ?: return
        val before=mutable.value
        if(!visible || !before.canSend) return
        val text=AiPolicy.cleanDraft(before.draft) ?: return
        cancelThread();val request=threadRequest;val version=epoch
        val userKey="local-user-${UUID.randomUUID()}";val answerKey="local-answer-${UUID.randomUUID()}"
        val now=Instant.now();val id=before.activeChatId.orEmpty()
        val user=AiMessage(userKey,id,AiRole.USER,text,AiSource.USER,now,userKey,AiDelivery.PENDING)
        val answer=AiMessage(answerKey,id,AiRole.ASSISTANT,"",AiSource.GEMINI,now,answerKey,AiDelivery.STREAMING)
        mutable.value=before.copy(messages=before.messages+user+answer,input=TextFieldValue(),inputRevision=++inputRevision,sending=true,error=null)
        streamJob=scope.launch {
            val pending=StringBuilder();var completed=false;var userSaved=false;var displayed=false
            fun flush() {
                if(!currentThread(version,credential,request)) return
                val value=pending.toString()
                mutable.value=mutable.value.copy(messages=mutable.value.messages.map { if(it.key==answerKey) it.copy(content=value) else it })
            }
            try {
                events.frames(credential,before.activeChatId,text).collect { frame ->
                    if(!currentThread(version,credential,request)) return@collect
                    when(frame) {
                        is AiFrame.Conversation -> {
                            ++chatsRevision
                            mutable.value=mutable.value.copy(activeChatId=frame.chat.id,chats=AiPolicy.upsert(mutable.value.chats,frame.chat))
                        }
                        is AiFrame.UserMessage -> {
                            userSaved=true
                            mutable.value=mutable.value.copy(messages=mutable.value.messages.map { if(it.key==userKey) frame.message.copy(key=userKey) else it })
                        }
                        is AiFrame.Delta -> {
                            pending.append(frame.text)
                            if(!displayed) { displayed=true;flush() }
                            else if(flushJob?.isActive!=true) flushJob=scope.launch { delay(frameMillis);flush() }
                        }
                        is AiFrame.Done -> {
                            flushJob?.cancel();flushJob=null;completed=true;++chatsRevision
                            val saved=mutable.value.messages.map { if(it.key==answerKey) frame.message.copy(key=answerKey) else it }
                            saveCache(frame.chat.id,saved)
                            mutable.value=mutable.value.copy(messages=saved,activeChatId=frame.chat.id,
                                chats=AiPolicy.upsert(mutable.value.chats,frame.chat),sending=false,error=null)
                        }
                    }
                }
                if(!completed) throw AiException("The response was interrupted. Reload this chat before sending again.",AiFailure.INTERRUPTED)
                if(currentThread(version,credential,request)) refreshChats()
            } catch(cancel:CancellationException) { throw cancel }
            catch(error:Exception) {
                if(currentThread(version,credential,request)) {
                    flushJob?.cancel();flush()
                    val rows=interrupted(mutable.value.messages)
                    mutable.value=mutable.value.copy(messages=rows,sending=false,
                        input=if(!userSaved) before.input.copy(composition=null) else mutable.value.input,inputRevision=++inputRevision)
                    fail(error,if(mutable.value.activeChatId!=null) AiErrorAction.MESSAGES else AiErrorAction.CHATS)
                }
            } finally { if(currentThread(version,credential,request)) flushJob?.cancel() }
        }
    }
    fun deleteChat(id: String) {
        if(!AiPolicy.validId(id) || !canMutate()) return
        val credential=token ?: return;val version=epoch;val request=++mutationRequest
        mutable.value=mutable.value.copy(deletingId=id,error=null)
        mutationJob=scope.launch {
            try {
                api.delete(credential,id)
                if(!current(version,credential) || request!=mutationRequest) return@launch
                ++chatsRevision;cache.remove(id)
                mutable.value=mutable.value.copy(chats=mutable.value.chats.filterNot{it.id==id},deletingId=null)
                if(mutable.value.activeChatId==id) newChat()
                refreshChats()
            } catch(cancel:CancellationException) { throw cancel }
            catch(error:Exception) {
                if(current(version,credential) && request==mutationRequest) {
                    mutable.value=mutable.value.copy(deletingId=null);fail(error,AiErrorAction.CHATS)
                }
            }
        }
    }
    fun clearChats() {
        if(!canMutate()) return
        val credential=token ?: return;val version=epoch;val request=++mutationRequest
        mutable.value=mutable.value.copy(clearing=true,error=null)
        mutationJob=scope.launch {
            try {
                api.clear(credential)
                if(!current(version,credential) || request!=mutationRequest) return@launch
                ++chatsRevision;cache.clear()
                mutable.value=mutable.value.copy(chats=emptyList(),clearing=false)
                newChat();refreshChats()
            } catch(cancel:CancellationException) { throw cancel }
            catch(error:Exception) {
                if(current(version,credential) && request==mutationRequest) {
                    mutable.value=mutable.value.copy(clearing=false);fail(error,AiErrorAction.CHATS)
                }
            }
        }
    }
    private fun canMutate() = visible && token!=null && !mutable.value.sending && !mutable.value.mutating
    private fun current(version:Long,credential:String) = visible && version==epoch && token==credential
    private fun currentThread(version:Long,credential:String,request:Long) = current(version,credential) && threadRequest==request
    private fun fail(error:Exception,action:AiErrorAction) {
        if(error is AiException && error.failure==AiFailure.AUTH) { expire();return }
        mutable.value=mutable.value.copy(error=AiError(if(error is AiException) error.message.orEmpty() else "Mongosky AI could not complete this request. Please try again.",action))
    }
    private fun expire() {
        val key=owner;endSession();owner=key
        mutable.value=AiState(expired=true,inputRevision=++inputRevision,error=AiError("Your session has expired. Please sign in again."))
    }
    private fun interrupted(rows:List<AiMessage>) = rows.mapNotNull {
        if(it.role==AiRole.ASSISTANT && it.delivery==AiDelivery.STREAMING)
            if(it.content.isBlank()) null else it.copy(delivery=AiDelivery.INTERRUPTED)
        else it
    }
    private fun saveCache(id:String,rows:List<AiMessage>) {
        val saved=AiPolicy.saved(rows)
        if(saved.sumOf { it.content.length.toLong() }>AiPolicy.MAX_CACHE_CHARS) { cache.remove(id);return }
        cache[id]=saved
        while(cache.size>4 || cache.values.sumOf { list -> list.sumOf { it.content.length.toLong() } }>AiPolicy.MAX_CACHE_CHARS) cache.remove(cache.keys.first())
    }
}
