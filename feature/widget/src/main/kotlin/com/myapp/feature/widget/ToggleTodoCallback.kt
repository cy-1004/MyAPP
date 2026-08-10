package com.myapp.feature.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import com.myapp.feature.widget.di.WidgetDataProvider
import dagger.hilt.android.EntryPointAccessors

/**
 * 小组件圆圈勾选待办的动作（PRD 3.10 W3 / W1）。
 *
 * Glance 的 ActionCallback 由框架反射实例化（要求无参构造），onAction 里再经
 * EntryPointAccessors 取 Hilt 图里的 [com.myapp.core.common.contract.TodoToggleWriter]
 * ——不走 TodoDao 直写，重复任务生成下一次、提醒注册这些行为必须与 App 内一致。
 *
 * 勾选后直接 [WidgetUpdateManager.updateAllWidgets] 而不是发刷新事件：
 * onAction 可能在应用主进程收集协程未就绪时执行，直接调用更确定。
 */
class ToggleTodoCallback : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val todoId = parameters[TodoId] ?: return
        if (todoId <= 0L) return
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetDataProvider::class.java,
        )
        entry.todoToggleWriter().setDone(todoId, done = true)
        entry.widgetUpdateManager().updateAllWidgets()
    }

    companion object {
        val TodoId = ActionParameters.Key<Long>("todo_id")

        /** 给待办圆圈挂的勾选动作。 */
        fun toggle(todoId: Long) = actionRunCallback<ToggleTodoCallback>(
            actionParametersOf(TodoId to todoId),
        )
    }
}
