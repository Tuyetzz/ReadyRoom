package com.readyroom.app.ui.home

import com.readyroom.app.domain.model.DisasterMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the home screen's state transitions.
 *
 * These run on the JVM — no device needed. They exercise the advice transcript,
 * which is the part that must stay consistent with the hard safety rules.
 */
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
    fun `selecting a mode appends that mode's advice`() {
        val viewModel = HomeViewModel()

        viewModel.selectMode(DisasterMode.FLOOD)
        viewModel.selectMode(DisasterMode.CYCLONE)

        val messages = viewModel.uiState.value.messages
        assertEquals(3, messages.size)
        assertEquals(DisasterMode.FLOOD.advice, messages[1].text)
        assertEquals(DisasterMode.CYCLONE.advice, messages[2].text)
        assertTrue(messages.none { it.fromUser })
    }

    @Test
    fun `reselecting the current mode does not duplicate advice`() {
        val viewModel = HomeViewModel()

        viewModel.selectMode(DisasterMode.FLOOD)
        viewModel.selectMode(DisasterMode.FLOOD)

        assertEquals(2, viewModel.uiState.value.messages.size)
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
}
