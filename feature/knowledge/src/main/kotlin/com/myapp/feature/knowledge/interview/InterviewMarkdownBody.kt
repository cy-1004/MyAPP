package com.myapp.feature.knowledge.interview

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors

/**
 * 面试题正文渲染（PRD 3.7）。
 *
 * 图片走 `file:///android_asset/...`：题库图片是随 APK 打包的静态资源，
 * Coil 原生支持这个 scheme，不需要先拷到 filesDir 再读。
 *
 * **letterSpacing 硬约束**：所有 [SpanStyle] 一律不设 `letterSpacing`，
 * 避免 Em/Sp lerp 崩溃（PRD 5.2，与 :feature:note 的渲染器同一条纪律）。
 */
@Composable
fun InterviewMarkdownBody(
    markdown: String,
    /** assets 里该文档的目录，如 `interview/backend`。图片相对它解析。 */
    assetDir: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(markdown) { parseInterviewBlocks(markdown) }
    Column(modifier = modifier, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.sm)) {
        blocks.forEach { block ->
            when (block) {
                is InterviewBlock.Heading -> Text(
                    text = inline(block.text),
                    style = when (block.level) {
                        1, 2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                is InterviewBlock.Paragraph -> Text(
                    text = inline(block.text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                is InterviewBlock.Bullet -> BulletRow(marker = "•", text = block.text)

                is InterviewBlock.Ordered -> BulletRow(marker = "${block.index}.", text = block.text)

                is InterviewBlock.Quote -> Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "│ ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.appColors.textTertiary,
                    )
                    Text(
                        text = inline(block.text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.appColors.textSecondary,
                    )
                }

                is InterviewBlock.Code -> CodeBlock(block)

                is InterviewBlock.Image -> AsyncImage(
                    model = "file:///android_asset/$assetDir/${block.path}",
                    contentDescription = block.alt.ifBlank { null },
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp)),
                )
            }
        }
    }
}

@Composable
private fun BulletRow(marker: String, text: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = marker,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.appColors.textSecondary,
            modifier = Modifier.width(24.dp),
        )
        Text(
            text = inline(text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * 代码块：等宽字体 + 底色块 + **横向滚动**。
 * 不换行是刻意的——代码折行之后缩进全乱，比左右滑更难读。
 */
@Composable
private fun CodeBlock(block: InterviewBlock.Code) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(Spacing.sm),
    ) {
        if (block.language != null) {
            Text(
                text = block.language,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.appColors.textTertiary,
            )
        }
        Text(
            text = block.content,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            softWrap = false,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}

/**
 * 行内标记：`**加粗**`、`` `代码` ``，以及去掉飞书导出的反斜杠转义。
 * 不做嵌套（`**外 `内` 外**`），题库里没有这种写法。
 */
@Composable
private fun inline(text: String) = buildAnnotatedString {
    val codeStyle = SpanStyle(
        fontFamily = FontFamily.Monospace,
        background = MaterialTheme.colorScheme.surfaceVariant,
    )
    val boldStyle = SpanStyle(fontWeight = FontWeight.Bold)
    var rest = text
    while (rest.isNotEmpty()) {
        val bold = BOLD.find(rest)
        val code = CODE.find(rest)
        val next = listOfNotNull(bold, code).minByOrNull { it.range.first }
        if (next == null) {
            append(unescape(rest))
            break
        }
        append(unescape(rest.substring(0, next.range.first)))
        withStyle(if (next === bold) boldStyle else codeStyle) {
            append(unescape(next.groupValues[1]))
        }
        rest = rest.substring(next.range.last + 1)
    }
}

/** 飞书导出会把 `(`、`+`、`.` 等转义成 `\(`、`\+`、`\.`，显示时还原。 */
private fun unescape(text: String): String = text.replace(ESCAPE, "$1")

private val BOLD = Regex("""\*\*(.+?)\*\*""")
private val CODE = Regex("""`([^`]+)`""")
private val ESCAPE = Regex("""\\(.)""")
