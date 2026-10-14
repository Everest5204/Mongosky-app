package com.mongosky.app.myactivity

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Execute on a phone/emulator; fake data keeps these UI checks independent of production. */
@RunWith(AndroidJUnit4::class)
class ActivityScreenTest {
    @get:Rule val compose = createComposeRule()
    private val owner = "507f1f77bcf86cd799439011"
    private val repo = DeviceActivityRepository()
    private val model = ActivityViewModel({ "device-test-token" },repo)
    @After fun cleanup() { compose.runOnIdle { model.endSession() } }
    private fun render() {
        compose.setContent { MaterialTheme { ActivityScreen(model,true,{},{} ) } }
        compose.runOnIdle { model.enter(owner,1) }
        compose.waitUntil(5000) { !model.state.loading }
    }
    @Test fun scrollingPaginatedActivityKeepsPageVisible() {
        render()
        val list = compose.onNodeWithTag("activity-list")
        list.performScrollToNode(hasText("Post 19"))
        compose.waitUntil(5000) { model.state.items.size >= 40 }
        list.performScrollToNode(hasText("Post 39"))
        compose.onNodeWithText("My Activity").assertIsDisplayed()
        compose.onNodeWithText("Post 39").assertIsDisplayed()
    }
    @Test fun deletingActivityRequiresConfirmationAndCancelKeepsPost() {
        render()
        compose.onAllNodesWithContentDescription("Options for Shared a post")[0].performClick()
        compose.onNodeWithText("Delete post",useUnmergedTree = true).performClick()
        compose.onNodeWithText("Delete post?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Post 0").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0,repo.writes) }
    }
    private class DeviceActivityRepository : ActivityRepository {
        var writes = 0
        override suspend fun page(token: String,owner: String,filter: ActivityFilter,cursor: String?): ActivityPage {
            val start = cursor?.toIntOrNull() ?: 0
            val rows = (start until minOf(start + 20,60)).map { n -> ActivityItem("%024x".format(1000+n),"post",ActivityCategory.POSTS,
                Instant.parse("2026-10-13T12:00:00Z").minusSeconds(n.toLong()),"Shared a post","Post $n",postId = "507f1f77bcf86cd799439013",
                available = true,action = ActivityAction.DELETE_POST) }
            val more = start + 20 < 60
            return ActivityPage(rows,mapOf(ActivityCategory.POSTS to 60L),if (more) (start+20).toString() else null,more)
        }
        override suspend fun target(token: String,owner: String,item: ActivityItem) = ActivityTarget(item)
        override suspend fun remove(token: String,item: ActivityItem) { writes++ }
        override fun changes(token: String,owner: String): Flow<Unit> = emptyFlow()
    }
}
