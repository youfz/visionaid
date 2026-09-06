package com.you.visionaid.core.network

import retrofit2.HttpException
import java.io.IOException
import kotlinx.coroutines.CancellationException

/** Converts Retrofit exceptions into values that repositories can handle explicitly. */
suspend inline fun <T> safeApiCall(crossinline request: suspend () -> T): ApiResult<T> =
    try {
        ApiResult.Success(request())
    } catch (error: HttpException) {
        ApiResult.HttpError(error.code(), error.message())
    } catch (error: IOException) {
        ApiResult.NetworkError(error)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        ApiResult.UnexpectedError(error)
    }
