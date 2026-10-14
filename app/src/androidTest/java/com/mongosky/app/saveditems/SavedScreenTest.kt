package com.mongosky.app.saveditems

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mongosky.app.post.FeedAuthor
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Run on a phone/emulator. The repository is isolated from real accounts. */
@RunWith(AndroidJUnit4::class)
class SavedScreenTest {
    @get:Rule val compose = createComposeRule()
    private val owner = "507f1f77bcf86cd799439011"
    private val repo = DeviceSavedRepository()
    private val model = SavedItemsViewModel({ "device-test-token" },repo)
    @After fun cleanup() { compose.runOnIdle { model.endSession() } }
    private fun render() {
        compose.setContent { MaterialTheme { CompositionLocalProvider(LocalSavedItems provides model) {
            Box(Modifier.fillMaxSize()) { SavedItemsScreen(model,true,{},{})
                SavedNoticeHost(model,Modifier.align(Alignment.BottomCenter)) }
        } } }
        compose.runOnIdle { model.bind(owner,1);model.observe(SavedItemsViewModel.PAGE) }
        compose.waitUntil(5000) { !model.state.loading && model.state.items.isNotEmpty() }
    }
    @Test fun scrollingPagesKeepsSavedScreenVisible() {
        render();val list = compose.onNodeWithTag("saved-list")
        list.performScrollToNode(hasText("Saved post 19"))
        compose.waitUntil(5000) { model.state.items.size >= 40 }
        list.performScrollToNode(hasText("Saved post 39"))
        compose.onNodeWithText("Saved Items").assertIsDisplayed()
        compose.onNodeWithText("Saved post 39").assertIsDisplayed()
    }
    @Test fun removingAndUndoingDoesNotDeleteOriginal() {
        render();compose.onAllNodesWithContentDescription("Options for saved Text post")[0].performClick()
        compose.onNodeWithText("Remove from saved",useUnmergedTree = true).performClick()
        compose.waitUntil(5000) { repo.writes == 1 && !model.cell(repo.first).saving }
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(5000) { repo.writes == 2 && !model.cell(repo.first).saving }
        compose.runOnIdle { assertEquals(60,repo.originals.size);assertEquals(true,model.cell(repo.first).status?.saved) }
    }
    @Test fun filteringAndSearchingUseSavedData() {
        render();compose.onNodeWithText("Videos").performClick()
        compose.waitUntil(5000) { !model.state.loading && model.state.items.isEmpty() }
        compose.onNodeWithText("No matching saved items").assertIsDisplayed()
        compose.onNodeWithText("All",useUnmergedTree = true).performClick()
        compose.onNodeWithTag("saved-search").performTextInput("Saved post 7")
        compose.waitUntil(5000) { !model.state.loading && model.state.items.size == 1 }
        compose.onNodeWithText("Saved post 7").assertIsDisplayed()
    }
    private class DeviceSavedRepository : SavedRepository {
        var writes = 0
        val originals = (0 until 60).associate { n -> "%024x".format(1000+n) to "Saved post $n" }
        val first = "%024x".format(1000)
        private val statuses = originals.keys.associateWith { id -> SavedStatus(id,true,1,Instant.parse("2026-10-13T12:00:00Z").minusSeconds(originals.keys.indexOf(id).toLong())) }.toMutableMap()
        private fun row(id: String): SavedItem { val value = statuses.getValue(id);return SavedItem(id,SavedKind.TEXT,value.revision,value.savedAt!!,true,"Text post",originals.getValue(id),FeedAuthor("507f1f77bcf86cd799439012","Test","Author",null),entryId = id) }
        override suspend fun state(token: String,owner: String,postId: String) = statuses[postId] ?: SavedStatus(postId,false,0)
        override suspend fun update(token: String,owner: String,attempt: SaveAttempt): SavedStatus {
            writes++;return SavedStatus(attempt.postId,attempt.saved,attempt.expectedRevision+1,if (attempt.saved) Instant.parse("2026-10-13T12:01:00Z") else null).also { statuses[attempt.postId] = it }
        }
        override suspend fun page(token: String,owner: String,filter: SavedFilter,cursor: String?): SavedPage {
            val start = cursor?.toIntOrNull() ?: 0
            val rows = statuses.values.filter { it.saved }.map { row(it.postId) }.sortedByDescending { it.savedAt }
                .filter { (filter.kind == SavedKind.ALL || filter.kind == it.kind) && (filter.search.isBlank() || it.text.contains(filter.search,true)) }
            val more = start+20 < rows.size
            return SavedPage(rows.drop(start).take(20),if (more) (start+20).toString() else null,more,writes.toLong())
        }
        override suspend fun resolve(token: String,owner: String,ids: Set<String>) = ids.filter { statuses[it]?.saved == true }.map(::row).sortedByDescending { it.savedAt }
        override suspend fun post(token: String,owner: String,postId: String) = SavedTarget(postId)
        override fun changes(token: String,owner: String): Flow<Long> = emptyFlow()
    }
}
