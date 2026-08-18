package com.myapp.feature.note.ui

/**
 * 笔记共享元素的 key（PRD 6.2）。
 *
 * 列表卡片和编辑页两端必须用**同一个 key** 才连得上，
 * 两处各手写一遍字符串迟早写歪--写歪的表现是「没有报错，只是动画不见了」，
 * 极难排查。所以统一从这里取。
 *
 * 新建笔记（id = 0）没有对应的列表卡片，调用方应跳过标记：
 * 只有一端的 key 不会报错，但会白付一份 overlay 开销。
 */
internal fun noteCardSharedKey(id: Long): String = "note-card-$id"
