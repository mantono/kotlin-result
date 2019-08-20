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
	inline fun <S> then(operation: (T) -> S): Result<S> {
		return when(this) {
			is Failure.Permanent -> Failure.Permanent(this)
			is Failure.Transient -> Failure.Transient(this)
			is Success -> try {
				Success(operation(this.value))
			} catch(exception: Throwable) {
				when(exception) {
					is PermanentError -> Failure.Permanent<S>(exception.message, exception.cause, exception.metadata)
					is TransientError -> Failure.Transient<S>(exception.message, exception.cause, exception.metadata)
					else -> Failure.Permanent<S>(exception.message ?: "Operation failed", exception)
				}
			}
		}
	}

	inline fun <S> attempt(operation: (T) -> S): Result<S> {
		return when(this) {
			is Failure.Permanent -> Failure.Permanent(this)
			is Failure.Transient -> Failure.Transient(this)
			is Success -> try {
				Success(operation(this.value))
			} catch(exception: Throwable) {
				when(exception) {
					is PermanentError -> Failure.Permanent<S>(exception.message, exception.cause, exception.metadata)
					is TransientError -> Failure.Transient<S>(exception.message, exception.cause, exception.metadata)
					else -> Failure.Transient<S>(exception.message ?: "Operation failed", exception)
				}
			}
		}
	}

	fun get(): T = when(this) {
		is Success -> this.value
		is Failure -> throw ResultException(this)
	}

	override fun toString(): String = when(this) {
		is Success<T> -> "Success: $value"
		is Failure.Transient<*> -> "Failure.Transient: $message"
		is Failure.Permanent<*> -> "Failure.Permanent: $message"
	}
}

inline fun <S> execute(operation: () -> S): Result<S> {
	return try {
		Success(operation())
	} catch(e: Throwable) {
		Failure.Permanent(e.message ?: "Operation failed", e)
	}
}

inline fun <S> attempt(operation: () -> S): Result<S> {
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

/**
 * An error of non-transient (permanent) nature. Retrying an operation with the
 * same input that yielded the error should **always** give the same
 * result.
 */
interface PermanentError: TraceableError

/**
 * An error of transient nature. Retrying an operation with the
 * same input that yielded the error could result in a different outcome in
 * the future.
 */
interface TransientError: TraceableError

fun failure(
	message: String,
	cause: Throwable? = null,
	metadata: Map<String, Any> = emptyMap()
): Nothing {
	throw object: PermanentError, TraceableError, Throwable() {
		override val message: String = message
		override val cause: Throwable? = cause
		override val metadata: Map<String, Any> = inheritMetadata(cause, metadata)
	}
}

fun panic(
	message: String,
	cause: Throwable? = null,
	metadata: Map<String, Any> = emptyMap()
): Nothing {
	throw object: TransientError, TraceableError, Throwable() {
		override val message: String = message
		override val cause: Throwable? = cause
		override val metadata: Map<String, Any> = inheritMetadata(cause, metadata)
	}
}

private inline fun <reified T> inheritMetadata(obj: T?, metadata: Map<String, Any>): Map<String, Any> =
	if(obj is TraceableError) obj.metadata + metadata else metadata