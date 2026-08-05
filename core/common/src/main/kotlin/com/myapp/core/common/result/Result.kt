package com.myapp.core.common.result

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * 统一的加载状态封装。
 *
 * 之所以不用 kotlin.Result：它不适合表示「加载中」，而本项目每个首页卡片
 * 都需要独立的骨架屏状态（PRD 5.4），Loading 必须是一等状态。
 */
sealed interface Result<out T> {
    data object Loading : Result<Nothing>
    data class Success<T>(val data: T) : Result<T>
    data class Error(val throwable: Throwable) : Result<Nothing>
}

/**
 * 把任意数据流包装成带 Loading / Error 的状态流。
 *
 * 关键作用：单个卡片的数据源异常不会让整个首页崩掉，
 * 只会让那一张卡片显示错误态（PRD 4.7.2）。
 */
fun <T> Flow<T>.asResult(): Flow<Result<T>> = this
    .map<T, Result<T>> { Result.Success(it) }
    .onStart { emit(Result.Loading) }
    .catch { emit(Result.Error(it)) }

inline fun <T> Result<T>.onSuccess(action: (T) -> Unit): Result<T> {
    if (this is Result.Success) action(data)
    return this
}

fun <T> Result<T>.dataOrNull(): T? = (this as? Result.Success)?.data
