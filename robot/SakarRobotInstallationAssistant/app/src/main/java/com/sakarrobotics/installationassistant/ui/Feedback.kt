package com.sakarrobotics.installationassistant.ui

import com.sakarrobotics.installation.core.model.Outcome
import com.sakarrobotics.installationassistant.ui.components.Tone

/** A transient message shown in a banner after an operation (success or failure). */
data class Feedback(val text: String, val tone: Tone)

fun <T> Outcome<T>.toFeedback(successText: String? = null): Feedback? = when (this) {
    is Outcome.Success -> successText?.let { Feedback(it, Tone.SUCCESS) }
    is Outcome.Failure -> Feedback(message, Tone.DANGER)
}
