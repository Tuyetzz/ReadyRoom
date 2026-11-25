package com.readyroom.app.domain.model

/**
 * One bubble in the advice chat.
 *
 * [fromUser] splits the two sides of the transcript: the user's questions on
 * the right, Ready Room's replies on the left.
 */
data class ChatMessage(
    val text: String,
    val fromUser: Boolean,
)
