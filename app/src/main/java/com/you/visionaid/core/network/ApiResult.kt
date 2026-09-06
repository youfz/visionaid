package com.you.visionaid.core.network

/** A transport-independent result returned by repositories to the UI layer. */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class HttpError(val code: Int, val message: String?) : ApiResult<Nothing>
    data class NetworkError(val cause: Throwable) : ApiResult<Nothing>
    data class UnexpectedError(val cause: Throwable) : ApiResult<Nothing>
}
