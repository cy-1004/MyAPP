package com.myapp.feature.note.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.note.edit.NoteEditScreen
import com.myapp.feature.note.list.NoteListScreen

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
        NoteListScreen(onNavigate = onNavigate, onBack = onBack)
    }
    composable<Route.NoteDetail> {
        NoteEditScreen(onBack = onBack)
    }
}
