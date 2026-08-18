package com.myapp.feature.note.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.myapp.core.ui.navigation.Route
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
 */
fun NavGraphBuilder.noteGraph(
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit,
) {
    composable<Route.NoteList> {
        NoteListScreen(onNavigate = onNavigate)
    }
    composable<Route.NoteDetail> {
        NoteEditScreen(onBack = onBack)
    }
}
