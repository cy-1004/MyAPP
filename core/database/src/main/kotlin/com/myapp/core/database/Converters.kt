package com.myapp.core.database

import androidx.room.TypeConverter

/**
 * Room 全局 TypeConverter。
 *
 * 注册到 [com.myapp.core.database.di.DatabaseModule.provideDatabase] 的
 * `addTypeConverter(Converters())`。
 *
 * 当前只服务 `List<String> <-> String`，用于 [model.NoteEntity.imagesJson]。
 * 用 ``（ASCII Unit Separator，文件名禁用字符）作分隔符--
 * 图片路径里不会出现这个字符，分隔安全。
 *
 * 不用 JSON 序列化库：`:core:database` 没引入 kotlinx.serialization，
 * 为这一个字段拉依赖不划算；手写转换器零依赖、可测试。
 */
class Converters {

    @TypeConverter
    fun fromStringList(value: List<String>): String = value.joinToString(SEPARATOR)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isEmpty()) emptyList() else value.split(SEPARATOR)

    private companion object {
        const val SEPARATOR = ""
    }
}
