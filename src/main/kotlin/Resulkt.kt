package com.mantono.result


/**
 * A Result models one of two different possible outcomes for an operation
 * where a functions operates on an input of type [T] and returns an output
 * of type [S].
 *
 * 1. The operation was successful and an output of type [S] was returned
 * 2. An error was encountered, which may or may not have been caused by an
 * cause.
 */
sealed class Result<T> {
	inline fun <S> tryThen(operation: (T) -> S): Result<S> {
		return when(this) {
			is Failure.Permanent -> Failure.Permanent(this)
			is Failure.Transient -> Failure.Transient(this)
			is Success -> try {
				Success(operation(this.value))
			} catch(e: Throwable) {
				Failure.Permanent<S>(e.message ?: "Operation failed", e)
			}
		}
	}

	inline fun <S> then(operation: (T) -> S): Result<S> {
		return when(this) {
			is Failure.Permanent -> Failure.Permanent(this)
			is Failure.Transient -> Failure.Transient(this)
			is Success -> try {
				Success(operation(this.value))
			} catch(e: Throwable) {
				Failure.fromException<S>(e)
			}
		}
	}

	fun get(): T = when(this) {
		is Success -> this.value
		is Failure -> throw ResultException(this)
	}
}

inline fun <S> tryExecute(operation: () -> S): Result<S> {
	return try {
		Success(operation())
	} catch(e: Throwable) {
		Failure.Permanent(e.message ?: "Operation failed", e)
	}
}

inline fun <S> execute(operation: () -> S): Result<S> {
	return try {
		Success(operation())
	} catch(e: Throwable) {
		Failure.fromException(e)
	}
}

data class Success<T>(val value: T): Result<T>() {
	companion object {
		operator fun invoke(): Success<Unit> = Success(Unit)
	}

	override fun toString(): String = "Success [$value]"
}

interface TraceableError {
	val message: String
	val cause: Throwable?
	val metadata: Map<String, Any>
}

sealed class Failure<T>: Result<T>(), TraceableError {
	data class Permanent<T>(
		override val message: String,
		override val cause: Throwable? = null,
		override val metadata: Map<String, Any> = emptyMap()
	): Failure<T>() {
		constructor(failure: Failure.Permanent<*>):
			this(failure.message, failure.cause, failure.metadata)
	}

	data class Transient<T>(
		override val message: String,
		override val cause: Throwable? = null,
		override val metadata: Map<String, Any> = emptyMap()
	): Failure<T>() {

		constructor(failure: Failure.Transient<*>):
			this(failure.message, failure.cause, failure.metadata)
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
	override val cause: Throwable? = null,
	override val metadata: Map<String, Any> = emptyMap()
): Exception(message, cause), TraceableError {
	internal constructor(result: TraceableError): this(result.message, result.cause, result.metadata)
	internal constructor(error: String): this(error, null)
}

internal class PermanentException(
	override val message: String,
	override val cause: Throwable? = null,
	override val metadata: Map<String, Any> = emptyMap()
): Throwable(), TraceableError {

}


fun failure(
	message: String,
	cause: Throwable? = null,
	metadata: Map<String, Any?> = emptyMap()
): Nothing {
	val failure = Failure.Permanent(message)
	val exception: Throwable = if(cause is TraceableError) {
		ResultException(message, cause, cause.metadata + metadata)
	} else {
		PermanentException(message, cause, metadata)
	}
	throw exception
}

fun panic(
	message: String,
	cause: Throwable? = null,
	metadata: Map<String, Any?> = emptyMap()
): Nothing {
	val exception: Throwable = if(cause is TraceableError) {
		TransientException(message, cause, cause.metadata + metadata)
	} else {
		TransientException(message, cause, metadata)
	}
	throw exception
}