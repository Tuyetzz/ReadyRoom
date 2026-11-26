package com.readyroom.app.ui.home

import android.graphics.Bitmap
import com.readyroom.app.domain.model.CapturedScan
import com.readyroom.app.domain.model.Detection
import com.readyroom.app.domain.model.DisasterMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers the home screen's state transitions.
 *
 * Runs under Robolectric so tests that build a real [Bitmap] for a captured
 * scan work on the JVM without a device.
 *
 * These exercise the advice transcript, which is the part that must stay
 * consistent with the hard safety rules.
 */
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {

    @Test
    fun `starts on earthquake with its advice already shown`() {
        val state = HomeViewModel().uiState.value

        assertEquals(DisasterMode.EARTHQUAKE, state.mode)
        assertEquals(1, state.messages.size)
        assertEquals(DisasterMode.EARTHQUAKE.advice, state.messages.single().text)
        assertFalse(state.messages.single().fromUser)
    }

    @Test
    fun `each mode starts with only its own advice`() {
        val viewModel = HomeViewModel()

        viewModel.selectMode(DisasterMode.FLOOD)

        // Regression: switching modes used to append the new mode's advice to
        // the previous mode's transcript instead of swapping conversations.
        val messages = viewModel.uiState.value.messages
        assertEquals(1, messages.size)
        assertEquals(DisasterMode.FLOOD.advice, messages.single().text)
    }

    @Test
    fun `switching modes preserves each conversation separately`() {
        val viewModel = HomeViewModel()

        viewModel.updateDraft("Is my bookshelf safe?")
        viewModel.sendMessage()
        val earthquakeCount = viewModel.uiState.value.messages.size

        viewModel.selectMode(DisasterMode.FLOOD)
        assertEquals(1, viewModel.uiState.value.messages.size)

        viewModel.updateDraft("Can I hide under the table?")
        viewModel.sendMessage()

        // Back to earthquake: its transcript is intact and unpolluted.
        viewModel.selectMode(DisasterMode.EARTHQUAKE)
        val earthquake = viewModel.uiState.value.messages
        assertEquals(earthquakeCount, earthquake.size)
        // Matched on the exact question, not the word "table" — the
        // earthquake advice mentions a table itself ("Get under a sturdy
        // table"), so a looser check would fail on the mode's own guidance.
        assertTrue(earthquake.none { it.text == "Can I hide under the table?" })

        // Flood kept its own.
        viewModel.selectMode(DisasterMode.FLOOD)
        assertTrue(viewModel.uiState.value.messages.any { it.text == "Can I hide under the table?" })
    }

    @Test
    fun `a draft stays with the mode it was typed for`() {
        val viewModel = HomeViewModel()

        viewModel.updateDraft("half-typed question")
        viewModel.selectMode(DisasterMode.FLOOD)

        assertEquals("", viewModel.uiState.value.draft)

        viewModel.selectMode(DisasterMode.EARTHQUAKE)
        assertEquals("half-typed question", viewModel.uiState.value.draft)
    }

    @Test
    fun `sending a message echoes it and clears the draft`() {
        val viewModel = HomeViewModel()

        viewModel.updateDraft("Can I hide under the table?")
        viewModel.sendMessage()

        val state = viewModel.uiState.value
        // Order is [advice, user text, reply] — the reply is appended last.
        assertEquals(3, state.messages.size)
        assertTrue(state.messages[state.messages.lastIndex - 1].fromUser)
        assertEquals(
            "Can I hide under the table?",
            state.messages[state.messages.lastIndex - 1].text,
        )
        assertFalse(state.messages.last().fromUser)
        assertEquals("", state.draft)
    }

    @Test
    fun `blank messages are ignored`() {
        val viewModel = HomeViewModel()
        val before = viewModel.uiState.value.messages.size

        viewModel.updateDraft("   ")
        viewModel.sendMessage()

        assertEquals(before, viewModel.uiState.value.messages.size)
    }

    @Test
    fun `flood reply never suggests sheltering under furniture`() {
        val viewModel = HomeViewModel()

        viewModel.selectMode(DisasterMode.FLOOD)
        viewModel.updateDraft("Can I hide under this?")
        viewModel.sendMessage()

        val reply = viewModel.uiState.value.messages.last().text.lowercase()
        assertTrue(
            "Flood guidance must not recommend going low or under furniture",
            reply.contains("never lower"),
        )
    }

    @Test
    fun `fire reply never suggests hiding`() {
        val viewModel = HomeViewModel()

        viewModel.selectMode(DisasterMode.FIRE)
        viewModel.updateDraft("Where should I hide?")
        viewModel.sendMessage()

        val reply = viewModel.uiState.value.messages.last().text.lowercase()
        assertTrue(
            "Fire guidance must tell the user to get out, not hide",
            reply.contains("get out"),
        )
    }

    @Test
    fun `every mode is selectable`() {
        val viewModel = HomeViewModel()

        // Regression: modes other than Earthquake were previously disabled in
        // the picker, so switching to them did nothing.
        DisasterMode.entries.forEach { mode ->
            viewModel.selectMode(mode)
            assertEquals(mode, viewModel.uiState.value.mode)
        }
    }

    @Test
    fun `switching mode retargets the advice header to that mode`() {
        val viewModel = HomeViewModel()

        viewModel.selectMode(DisasterMode.CYCLONE)

        val state = viewModel.uiState.value
        assertEquals(DisasterMode.CYCLONE, state.mode)
        assertEquals(DisasterMode.CYCLONE.advice, state.messages.last().text)
    }

    @Test
    fun `attaching a scan posts a summary of what was detected`() {
        val viewModel = HomeViewModel()

        viewModel.attachScan(scanOf("table" to 1, "window" to 2))

        val summary = viewModel.uiState.value.messages.last().text
        assertTrue("summary should name the window", summary.contains("window"))
        assertTrue("summary should name the table", summary.contains("table"))
        assertTrue("summary should report the count", summary.contains("2× window"))
    }

    @Test
    fun `attaching a scan with nothing detected says so plainly`() {
        val viewModel = HomeViewModel()

        viewModel.attachScan(scanOf())

        val summary = viewModel.uiState.value.messages.last().text
        assertTrue(
            "an empty result must not imply the room is safe",
            summary.contains("could not identify"),
        )
    }

    @Test
    fun `scan summary does not claim a risk tier`() {
        // Risk scoring is not built. A summary that implied one would put
        // unverified safety advice in front of the user.
        val viewModel = HomeViewModel()

        viewModel.attachScan(scanOf("shelf" to 1))

        val summary = viewModel.uiState.value.messages.last().text.lowercase()
        for (claim in listOf("safe", "low risk", "high risk", "no danger")) {
            assertFalse(
                "summary must not assert '$claim' before scoring exists",
                summary.contains(claim),
            )
        }
    }

    @Test
    fun `a scan attaches only to the mode it was taken in`() {
        val viewModel = HomeViewModel()

        viewModel.attachScan(scanOf("table" to 1))
        assertNotNull(viewModel.uiState.value.session.photo)

        viewModel.selectMode(DisasterMode.FLOOD)

        assertNull(
            "the flood conversation should not inherit an earthquake scan",
            viewModel.uiState.value.session.photo,
        )
    }

    @Test
    fun `reattaching recycles the previous photo`() {
        val viewModel = HomeViewModel()

        viewModel.attachScan(scanOf("table" to 1))
        val first = viewModel.uiState.value.session.photo!!

        viewModel.attachScan(scanOf("table" to 2))

        assertTrue("the replaced bitmap should be recycled", first.isRecycled)
        assertFalse(viewModel.uiState.value.session.photo!!.isRecycled)
    }

    @Test
    fun `clearing the view model recycles the held photo`() {
        val viewModel = HomeViewModel()
        viewModel.attachScan(scanOf("table" to 1))
        val held = viewModel.uiState.value.session.photo!!

        // onCleared is protected; clear() is the public entry point.
        viewModel.clearForTest()

        assertTrue("the ViewModel owned the only reference", held.isRecycled)
    }

    /** Builds a CapturedScan with one bitmap and the named detections. */
    private fun scanOf(vararg counts: Pair<String, Int>): CapturedScan {
        val detections = counts.flatMap { (name, count) ->
            List(count) {
                Detection(
                    classId = 0,
                    className = name,
                    confidence = 0.9f,
                    left = 0f,
                    top = 0f,
                    right = 10f,
                    bottom = 10f,
                )
            }
        }
        return CapturedScan(
            bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888),
            detections = detections,
        )
    }
}
