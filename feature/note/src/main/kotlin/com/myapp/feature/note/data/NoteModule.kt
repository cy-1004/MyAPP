package com.myapp.feature.note.data

import com.myapp.core.common.contract.NoteWriter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 把 [NoteRepository] 绑定为 [NoteWriter] 的实现。
 *
 * 单绑定（不是 @IntoSet）：[NoteWriter] 是单接口契约，不是集合。
 * 其他 feature（如未来的资讯页「存为笔记」）注入 NoteWriter 时，
 * Hilt 会拿到 NoteRepository 实例。
 *
 * 删除 :feature:note 时只需换一个空实现，调用方一行都不用改（PRD 4.7.4）。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NoteModule {

    @Binds
    @Singleton
    abstract fun bindNoteWriter(impl: NoteRepository): NoteWriter
}
