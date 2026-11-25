package com.readyroom.app.ui.home

import androidx.lifecycle.ViewModel
import com.readyroom.app.domain.model.ChatMessage
import com.readyroom.app.domain.model.DisasterMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Everything the home screen draws. */
data class HomeUiState(
    val mode: DisasterMode = DisasterMode.EARTHQUAKE,
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
)

/**
 * Holds home-screen state.
 *
 * The replies here are canned. They are written from each mode's [DisasterMode.advice]
 * so the transcript stays consistent with the hard safety rules — the real
 * assistant must pass those rules as a system prompt and have its output
 * checked against them before display. Nothing in this class may invent
 * safety advice.
 */
class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        HomeUiState(messages = listOf(ChatMessage(DisasterMode.EARTHQUAKE.advice, fromUser = false)))
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /**
     * Switches mode and posts that mode's guidance line.
     *
     * The message is appended rather than replacing the transcript so the user
     * can scroll back and compare modes.
     */
    fun selectMode(mode: DisasterMode) {
        if (mode == _uiState.value.mode) return

        _uiState.update { state ->
            state.copy(
                mode = mode,
                messages = state.messages + ChatMessage(mode.advice, fromUser = false),
            )
        }
    }

    fun updateDraft(text: String) {
        _uiState.update { it.copy(draft = text) }
    }

    /**
     * Echoes the user's question with a placeholder reply.
     *
     * TODO: replace with the LLM call. The reply must be filtered against the
     * hard safety rules before it reaches the transcript — see README
     * "Guardrails". Until then this returns guidance rather than an answer, so
     * it cannot be mistaken for a real assessment of the user's room.
     */
    fun sendMessage() {
        val text = _uiState.value.draft.trim()
        if (text.isEmpty()) return

        val mode = _uiState.value.mode
        _uiState.update { state ->
            state.copy(
                messages = state.messages + listOf(
                    ChatMessage(text, fromUser = true),
                    ChatMessage(preparednessReply(mode), fromUser = false),
                ),
                draft = "",
            )
        }
    }

    /**
     * Posts the reply for a question.
     *
     * TODO: replace with the LLM call. The response must be checked against
     * the hard safety rules before it reaches the transcript — see README
     * "Guardrails". Until then this returns the mode's standing guidance
     * rather than an assessment of the user's actual room, so it cannot be
     * mistaken for a real answer.
     */
    private fun preparednessReply(mode: DisasterMode): String =
        "I can only answer general preparedness questions until the room " +
            "scan is wired up. For ${mode.label.lowercase()}: ${mode.advice}"
}
