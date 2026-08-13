package com.myapp.feature.knowledge.data

import com.myapp.core.common.contract.KnowledgeSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 把 [KnowledgeRepository] 绑定为 [KnowledgeSource] 的实现（PRD 4.7.4）。
 *
 * 单绑定（不是 @IntoSet）：[KnowledgeSource] 是单接口契约，不是集合。
 * 将来换飞书 OAuth 私有文档 / Notion 实现时只需换这一行绑定，调用方无感知。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class KnowledgeModule {

    @Binds
    @Singleton
    abstract fun bindKnowledgeSource(impl: KnowledgeRepository): KnowledgeSource
}
