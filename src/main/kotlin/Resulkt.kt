package com.mantono.result

/**
 * A Result models one of three different possible outcomes for an operation
 * where a functions operates on an input of type [T] and returns an output
 * of type [S].
 *
 * 1. The operation was successful and an output of type [S] was returned
 * 2. The operation was successful in such way that it did not encounter any
 * errors, but it could still not return an output of the requested type. This
 * could be a scenario for example where a request to look up a user in a
 * database, only to find that it did not exist. It is likely that such outcome
 * should not be regarded as an error, while still not returning a value.
 * 3. An error was encountered, which may or may not have been caused by an
 * exception.
 */
sealed class Result<T> {
	inline fun <S> onSuccess(operation: (T) -> Result<S>): Result<S> {
		return when(this) {
			is Failure -> Failure(this)
			is Halt -> Halt(this.message)
			is Success -> try {
				operation(this.value)
			} catch(e: Throwable) {
				Failure.fromException<S>(e)
			}
		}
	}

	fun get(): T? = when(this) {
		is Success -> this.value
		is Halt -> null
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

data class Halt<T>(val message: String): Result<T>() {
	override fun toString(): String = "Halt: $message"
}


data class Failure<T>(
	val message: String,
	val exception: Throwable? = null,
	val metadata: Map<String, Any> = emptyMap()
): Result<T>() {

	constructor(failure: Failure<*>):
		this(failure.message, failure.exception)

	override fun toString(): String = "Failure: ${exception?.message ?: message}"

	companion object {
		fun <T> fromException(exception: Throwable): Failure<T> =
			Failure(exception.message ?: exception.toString(), exception)
	}
}

class ResultException private constructor(
	override val message: String,
	override val cause: Throwable?
): Exception(message, cause) {
	internal constructor(result: Failure<*>): this(result.message, result.exception)
	internal constructor(error: String): this(error, null)
}