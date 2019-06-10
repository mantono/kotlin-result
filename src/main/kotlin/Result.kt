package com.mantono.result

/**
 * A Result models one of two different possible outcomes for an operation
 * where a functions operates on an input of type [T] and returns an output
 * of type [S].
 *
 * 1. The operation was successful ([Success]) and an output of type [S] was returned
 * 2. An error ([Failure]) was encountered, which may or may not have been caused by an
 * exception, where either
 * 2a. The error is of such nature that it is permanent ([Failure.Permanent]),
 * in other words, given the same execution context
 * and data the same result is always to be
 * expected, and any further attempts would be futile. Such error could be
 * a serialization error or division by zero.
 * 2b. The cause of the error is of transient ([Failure.Transient]) nature.
 * Example of causes of
 * such error could be a temporary network failure, a remote API being
 * unresponsive or some other resource being temporarily unavailable. A new
 * attempt in the future is likely to yield a different outcome.
 */
sealed class Result<T> {
    inline fun <S> onSuccess(operation: (T) -> Result<S>): Result<S> {
        return when(this) {
            is Failure.Permanent -> Failure.Permanent(this)
            is Failure.Transient -> Failure.Transient(this)
            is Success -> try {
                operation(this.value)
            } catch(e: Throwable) {
                Failure.fromException<S>(e)
            }
        }
    }

    fun get(): T = when(this) {
        is Success -> this.value
        is Failure -> throw ResultException(this)
    }

    inline operator fun <S> invoke(operation: (T) -> Result<S>): Result<S> = onSuccess(operation)

    companion object {
        inline operator fun <S> invoke(operation: () -> Result<S>): Result<S> {
            return try {
                operation()
            } catch(e: Throwable) {
                return Failure.fromException(e)
            }
        }
    }
}

data class Success<T>(val value: T): Result<T>() {
    companion object {
        operator fun invoke(): Success<Unit> = Success(Unit)
    }

    override fun toString(): String = "Success [$value]"
}

sealed class Failure<T>: Result<T>() {
    abstract val message: String
    abstract val exception: Throwable?
    abstract val metadata: Map<String, Any>

    data class Permanent<T>(
        override val message: String,
        override val exception: Throwable? = null,
        override val metadata: Map<String, Any> = emptyMap()
    ): Failure<T>() {
        constructor(failure: Permanent<*>):
            this(failure.message, failure.exception, failure.metadata)
    }

    data class Transient<T>(
        override val message: String,
        override val exception: Throwable? = null,
        override val metadata: Map<String, Any> = emptyMap()
    ): Failure<T>() {

        constructor(failure: Transient<*>):
            this(failure.message, failure.exception, failure.metadata)
    }

    companion object {
        fun <T> fromException(exception: Throwable): Failure<T> =
            Transient(exception.message ?: exception.toString(), exception)

        operator fun <T> invoke(failure: Failure<*>): Failure<T> = when(failure) {
            is Permanent -> Permanent(failure)
            is Transient -> Transient(failure)
        }
    }
}

class ResultException private constructor(
    override val message: String,
    override val cause: Throwable?
): Exception(message, cause) {
    internal constructor(result: Failure<*>): this(result.message, result.exception)
    internal constructor(error: String): this(error, null)
