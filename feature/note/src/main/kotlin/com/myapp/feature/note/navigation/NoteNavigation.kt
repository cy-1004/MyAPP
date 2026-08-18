package com.myapp.feature.note.navigation

import androidx.navigation.NavGraphBuilder
import com.myapp.core.ui.navigation.Route
import com.myapp.core.ui.navigation.sharedElementComposable
import com.myapp.feature.note.edit.NoteEditScreen
import com.myapp.feature.note.list.NoteListScreen
import com.myapp.feature.note.notify.NoteQuickEntryTarget
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 常驻快捷入口点击 → 新建笔记页深链，:app 用 EntryPointAccessors 取用
 * （与 KnowledgeGraphEntryPoint / LedgerGraphEntryPoint 同一套模式）。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface NoteGraphEntryPoint {
    fun noteQuickEntryTarget(): NoteQuickEntryTarget
}

/**
 * 笔记的导航图。只在 :app 的 AppNavHost 里注册一次，其余模块无感知。
 *
 * 新建与编辑共用 [Route.NoteDetail]：id 默认 0 表示新建，与待办同一套约定。
 *
 * 两个目的地都用 [sharedElementComposable] 而非 `composable`：列表卡片要变形成
 * 编辑页的正文区（PRD 6.2 共享元素转场），两端都得拿到自己的过渡作用域。
 */
fun NavGraphBuilder.noteGraph(
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit,
) {
    sharedElementComposable<Route.NoteList> {
        NoteListScreen(onNavigate = onNavigate)
    }
    sharedElementComposable<Route.NoteDetail> {
        NoteEditScreen(onBack = onBack)
    }
}
