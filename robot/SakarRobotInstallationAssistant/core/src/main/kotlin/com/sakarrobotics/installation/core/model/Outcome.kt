package com.sakarrobotics.installation.core.model

enum class ErrorCode {
    INVALID_NAME,
    DUPLICATE_NAME,
    NOT_FOUND,
    WRONG_STATE,
    INSUFFICIENT_COVERAGE,
    FLOOR_HAS_MAPS,
    NO_INSTALLATION,
    NOTE_REQUIRED,
    LIMIT_REACHED,
    INVALID_BACKUP,
    INCOMPATIBLE_BACKUP
}

/** Result of a repository operation. Failures carry a code (for logic/tests) and a UI-ready message. */
sealed class Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>()
    data class Failure(val code: ErrorCode, val message: String) : Outcome<Nothing>()

    val isSuccess: Boolean get() = this is Success

    fun getOrNull(): T? = (this as? Success)?.value
    fun failureOrNull(): Failure? = this as? Failure
}
