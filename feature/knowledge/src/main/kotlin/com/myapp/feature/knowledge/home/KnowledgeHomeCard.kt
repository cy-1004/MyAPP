package com.myapp.feature.knowledge.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.core.common.contract.KnowledgeItem
import com.myapp.core.designsystem.component.AppCard
import com.myapp.core.designsystem.component.CardHeader
import com.myapp.core.designsystem.component.EmptyState
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors
import com.myapp.core.ui.home.BaseHomeCard
import com.myapp.core.ui.home.HomeCard
import com.myapp.core.ui.home.HomeCardOrder
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.knowledge.data.KnowledgeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 今日知识点首页卡片（PRD 3.8）。
 *
 * 知识池为空/笔记也没有时不显示（[isEnabled]）——但 PRD 本身承诺"永不空白"
 * （池空会自动降级到笔记），所以这张卡片实际只在用户一条笔记都没写过时才会消失。
 */
class KnowledgeHomeCard @Inject constructor(
    private val repository: KnowledgeRepository,
) : BaseHomeCard(
    id = "knowledge",
    defaultOrder = HomeCardOrder.KNOWLEDGE,
    displayName = "今日知识点",
) {

    override fun isEnabled(): Flow<Boolean> = repository.observeDailyPick().map { it != null }

    @Composable
    override fun Content(onNavigate: (Route) -> Unit) {
        val viewModel: KnowledgeHomeCardViewModel = hiltViewModel()
        val item by viewModel.pick.collectAsStateWithLifecycle()

        // 用户可能刚从知识源列表页把某条加进/移出知识池——ViewModel 本身跟首页这个
        // NavBackStackEntry 同生命周期，不会因为切一趟列表页重建，回到前台时手动刷新一次。
        LifecycleResumeEffect(Unit) {
            viewModel.refresh()
            onPauseOrDispose { }
        }

        AppCard {
            CardHeader(title = "今日知识点")
            val current = item
            if (current == null) {
                EmptyState(text = "今天的知识点还没准备好")
            } else {
                DailyKnowledgeCard(
                    item = current,
                    onOpenSource = {
                        val route = if (current.isNoteFallback) {
                            Route.NoteDetail(current.sourceId)
                        } else {
                            Route.KnowledgeReader(current.sourceId)
                        }
                        onNavigate(route)
                    },
                    onMastered = { viewModel.mastered(current) },
                    onSnoozed = { viewModel.snoozed(current) },
                )
            }
        }
    }
}

/**
 * [visible] 用 item 身份（sourceId + isNoteFallback）做 key：点「已掌握」后延迟调用
 * [onMastered]（等退出动画播完），新 item 换上来时 key 变了，[visible] 自动重置为 true，
 * 新卡片带着入场动画出现，不需要手动管理进入/退出两套状态机。
 */
@Composable
private fun DailyKnowledgeCard(
    item: KnowledgeItem,
    onOpenSource: () -> Unit,
    onMastered: () -> Unit,
    onSnoozed: () -> Unit,
) {
    var visible by remember(item.sourceId, item.isNoteFallback) { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    AnimatedVisibility(
        visible = visible,
        exit = shrinkHorizontally(animationSpec = tween(EXIT_DURATION_MS)) { 0 } +
            slideOutHorizontally(animationSpec = tween(EXIT_DURATION_MS)) { it } +
            fadeOut(animationSpec = tween(EXIT_DURATION_MS)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            FlippableSummary(item)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!item.isNoteFallback) {
                    TextButton(onClick = onOpenSource) { Text("跳转原页面") }
                    Spacer(Modifier.width(Spacing.xs))
                    OutlinedButton(onClick = onSnoozed) { Text("再看看") }
                    Spacer(Modifier.width(Spacing.sm))
                }
                TextButton(
                    onClick = {
                        visible = false
                        scope.launch {
                            delay(EXIT_DURATION_MS.toLong())
                            onMastered()
                        }
                    },
                ) {
                    Text("已掌握")
                }
            }
        }
    }
}

/**
 * 点击标题区域绕 Y 轴翻转展开/收起正文（PRD 3.11「卡片轻微 3D 翻转」）。
 * `rotationY` 转到 90° 时内容侧面不可见，正好在那一刻切换展开/收起的内容，
 * 转过 90° 后再用一层反向 `rotationY` 把文字转正——否则后半段文字是镜像的。
 */
@Composable
private fun FlippableSummary(item: KnowledgeItem) {
    var expanded by rememberSaveable(item.sourceId, item.isNoteFallback) { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) FLIP_DEGREES else 0f,
        animationSpec = tween(FLIP_DURATION_MS),
        label = "knowledgeFlip",
    )
    val showExpandedFace = rotation > FLIP_DEGREES / 2

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = FLIP_CAMERA_DISTANCE * density
            }
            .then(
                if (showExpandedFace) {
                    Modifier.graphicsLayer { rotationY = FLIP_DEGREES }
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { expanded = !expanded },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = if (showExpandedFace) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.appColors.textSecondary,
            maxLines = if (showExpandedFace) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "来自「${item.sourceName}」",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.appColors.textTertiary,
        )
    }
}

private const val EXIT_DURATION_MS = 250
private const val FLIP_DURATION_MS = 220
private const val FLIP_DEGREES = 180f
private const val FLIP_CAMERA_DISTANCE = 12f

@Module
@InstallIn(SingletonComponent::class)
interface KnowledgeHomeCardModule {
    @Binds
    @IntoSet
    fun bindKnowledgeHomeCard(card: KnowledgeHomeCard): HomeCard
}
