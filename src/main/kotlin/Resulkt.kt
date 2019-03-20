package com.mantono.result


/**
 * A Result models one of two different possible outcomes for an operation
 * where a functions operates on an input of type [T] and returns an output
 * of type [S].
 *
 * 1. The operation was successful and an output of type [S] was returned
 * 2. An error was encountered, which may or may not have been caused by an
 * exception.
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
		constructor(failure: Failure.Permanent<*>):
			this(failure.message, failure.exception, failure.metadata)
	}

	data class Transient<T>(
		override val message: String,
		override val exception: Throwable? = null,
		override val metadata: Map<String, Any> = emptyMap()
	): Failure<T>() {

		constructor(failure: Failure.Transient<*>):
			this(failure.message, failure.exception, failure.metadata)
	}

	companion object {
		fun <T> fromException(exception: Throwable): Failure<T> =
			Failure.Transient(exception.message ?: exception.toString(), exception)

		operator fun <T> invoke(failure: Failure<*>): Failure<T> = when(failure) {
			is Failure.Permanent -> Failure.Permanent(failure)
			is Failure.Transient -> Failure.Transient(failure)
		}
	}
}

class ResultException private constructor(
	override val message: String,
	override val cause: Throwable?
): Exception(message, cause) {
	internal constructor(result: Failure<*>): this(result.message, result.exception)
	internal constructor(error: String): this(error, null)
}