package com.ueiol.yixue.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.ueiol.yixue.domain.latex.LatexConverter

/**
 * LaTeX 文本组件：当前为 Unicode 降级渲染（规划书预案），
 * 后续接入 KaTeX/JLatexMath 时仅替换内部实现。
 */
@Composable
fun LatexText(
    latex: String,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    modifier: Modifier = Modifier
) {
    val rendered = remember(latex) { LatexConverter.toUnicode(latex) }
    Text(
        text = rendered,
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.horizontalScroll(rememberScrollState())
    )
}