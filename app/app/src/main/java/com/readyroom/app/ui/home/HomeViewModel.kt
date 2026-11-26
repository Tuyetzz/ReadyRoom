package com.readyroom.app.ui.home

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import com.readyroom.app.domain.model.CapturedScan
import com.readyroom.app.domain.model.ChatMessage
import com.readyroom.app.domain.model.DisasterMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * One hazard mode's conversation.
 *
 * The draft lives here rather than beside the transcript so a half-typed
 * question stays with the mode it was written for — switching to Flood and
 * back to Earthquake does not lose or mix up what you were typing.
 *
 * When the real assistant lands, its per-conversation state (in-flight
 * request, error, token usage) belongs here too.
 */
data class ChatSession(
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    /**
     * The photo this conversation is about, if the user took one.
     *
     * In memory only — nothing is written to disk, and it does not survive the
     * process. The README's privacy section says nothing is stored by default.
     */
    val photo: Bitmap? = null,
)

/** Everything the home screen draws. */
data class HomeUiState(
    val mode: DisasterMode = DisasterMode.EARTHQUAKE,
    val sessions: Map<DisasterMode, ChatSession> = emptyMap(),
) {
    /** The conversation for the selected mode. */
    val session: ChatSession
        get() = sessions[mode] ?: ChatSession()

    val messages: List<ChatMessage> get() = session.messages

    val draft: String get() = session.draft
}

/**
 * Holds home-screen state.
 *
 * Each mode keeps its own conversation, so switching hazard type swaps the
 * transcript instead of stacking one hazard's advice onto another's. Nothing
 * is persisted: the chat is scaffolding for the current session, and the thing
 * worth keeping is the scan result, not the questions asked about it.
 *
 * The replies here are canned. They are written from each mode's
 * [DisasterMode.advice] so the transcript stays consistent with the hard safety
 * rules — the real assistant must pass those rules as a system prompt and have
 * its output checked against them before display. Nothing in this class may
 * invent safety advice.
 */
class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        HomeUiState(
            sessions = DisasterMode.entries.associateWith { mode ->
                ChatSession(
                    messages = listOf(ChatMessage(mode.advice, fromUser = false)),
                )
            },
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** Swaps to another mode's conversation. Each keeps its own transcript. */
    fun selectMode(mode: DisasterMode) {
        if (mode == _uiState.value.mode) return
        _uiState.update { it.copy(mode = mode) }
    }

    fun updateDraft(text: String) {
        _uiState.update { state ->
            state.copy(
                sessions = state.sessions.updateSession(state.mode) {
                    it.copy(draft = text)
                },
            )
        }
    }

    /**
     * Attaches a captured photo to the current mode's conversation.
     *
     * Only one photo per mode is kept: the assistant is asked about the room in
     * front of the user, so an older photo would be stale context. Replacing it
     * recycles the previous bitmap, since the ViewModel is the last owner.
     *
     * Posts a summary message so the transcript reflects what was captured,
     * which is what the user will see the assistant referring to.
     */
    fun attachScan(scan: CapturedScan) {
        val mode = _uiState.value.mode
        _uiState.update { state ->
            state.copy(
                sessions = state.sessions.updateSession(mode) { session ->
                    session.photo?.recycle()
                    session.copy(
                        photo = scan.bitmap,
                        messages = session.messages + listOf(
                            ChatMessage(scanSummary(scan), fromUser = false),
                        ),
                    )
                },
            )
        }
    }

    /** Recycled on clear, since the ViewModel held the only reference. */
    protected fun releasePhotos() {
        _uiState.value.sessions.values.forEach { it.photo?.recycle() }
    }

    override fun onCleared() {
        super.onCleared()
        releasePhotos()
    }

    /**
     * Exposes cleanup to tests.
     *
     * `onCleared` is protected and only called by the framework, so a unit test
     * cannot reach it directly. This calls the same path.
     */
    internal fun clearForTest() = releasePhotos()

    /**
     * Describes what the detector found.
     *
     * Deliberately reports only what the model produced — no risk tiers, no
     * advice. Scoring is a separate step that has not been built, and inventing
     * a tier here would put unverified safety guidance in front of the user.
     */
    private fun scanSummary(scan: CapturedScan): String {
        if (scan.detections.isEmpty()) {
            return "Photo saved. I could not identify any furniture or fixtures " +
                "in it — try again with more of the room in frame."
        }

        val counts = scan.detections
            .groupingBy { it.className }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .joinToString(", ") { (name, count) ->
                if (count == 1) name else "$count× $name"
            }

        return "Photo saved. I can see: $counts. Risk scoring is not wired up " +
            "yet, so I cannot tell you which of these are hazards."
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
        val state = _uiState.value
        val text = state.draft.trim()
        if (text.isEmpty()) return

        val mode = state.mode
        _uiState.update { current ->
            current.copy(
                sessions = current.sessions.updateSession(mode) { session ->
                    session.copy(
                        messages = session.messages + listOf(
                            ChatMessage(text, fromUser = true),
                            ChatMessage(preparednessReply(mode), fromUser = false),
                        ),
                        draft = "",
                    )
                },
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

/** Applies [transform] to one mode's session, leaving the others untouched. */
private fun Map<DisasterMode, ChatSession>.updateSession(
    mode: DisasterMode,
    transform: (ChatSession) -> ChatSession,
): Map<DisasterMode, ChatSession> =
    this + (mode to transform(this[mode] ?: ChatSession()))
