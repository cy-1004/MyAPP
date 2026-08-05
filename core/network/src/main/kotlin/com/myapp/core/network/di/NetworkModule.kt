package com.myapp.core.network.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient

/**
 * 网络基建。
 *
 * 本项目无自建后端，网络只用于两件事：
 *   1. 拉取 RSS（XML）
 *   2. 加载飞书公开网页（走 WebView，不经这里）
 * 因此配置保持极简，不引入复杂的拦截器链。
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttp(
        @ApplicationContext context: Context,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        // RSS 源大多支持 ETag/Last-Modified，磁盘缓存能显著减少流量与耗电
        .cache(Cache(File(context.cacheDir, "http"), 20L * 1024 * 1024))
        .retryOnConnectionFailure(true)
        .build()
}
