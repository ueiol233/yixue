package com.ueiol.yixue.ui.plot

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ueiol.yixue.ui.common.FunctionPlot
import com.ueiol.yixue.ui.common.PlotSpec
import com.ueiol.yixue.ui.common.YxLargeTitle
import com.ueiol.yixue.ui.theme.YixueTheme

/**
 * 函数图大图查看器：独立 Activity，全屏绘制，支持双指缩放、单指平移、重置视野。
 * 列表/聊天里的缩略图只拦截点击，不再吞掉上下滚动。
 */
class PlotViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val spec = PlotSpec(
            expressions = intent.getStringArrayListExtra(EXTRA_EXPRESSIONS).orEmpty(),
            xMin = intent.getDoubleExtra(EXTRA_X_MIN, -10.0),
            xMax = intent.getDoubleExtra(EXTRA_X_MAX, 10.0),
            yMin = intent.getDoubleExtra(EXTRA_Y_MIN, -8.0),
            yMax = intent.getDoubleExtra(EXTRA_Y_MAX, 8.0)
        )
        setContent {
            YixueTheme {
                PlotViewerScreen(spec = spec, onClose = { finish() })
            }
        }
    }

    companion object {
        private const val EXTRA_EXPRESSIONS = "expressions"
        private const val EXTRA_X_MIN = "x_min"
        private const val EXTRA_X_MAX = "x_max"
        private const val EXTRA_Y_MIN = "y_min"
        private const val EXTRA_Y_MAX = "y_max"

        fun intent(context: Context, spec: PlotSpec): Intent =
            Intent(context, PlotViewerActivity::class.java).apply {
                putStringArrayListExtra(EXTRA_EXPRESSIONS, ArrayList(spec.expressions))
                putExtra(EXTRA_X_MIN, spec.xMin)
                putExtra(EXTRA_X_MAX, spec.xMax)
                putExtra(EXTRA_Y_MIN, spec.yMin)
                putExtra(EXTRA_Y_MAX, spec.yMax)
            }
    }
}

@Composable
private fun PlotViewerScreen(spec: PlotSpec, onClose: () -> Unit) {
    var resetTick by remember { mutableIntStateOf(0) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
    ) {
        YxLargeTitle(
            title = "函数图像",
            subtitle = spec.expressions.takeIf { it.isNotEmpty() }?.joinToString("；"),
            actions = {
                IconButton(onClick = { resetTick++ }) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = "重置视野",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        )
        key(resetTick) {
            FunctionPlot(
                spec = spec,
                modifier = Modifier.fillMaxSize().padding(8.dp),
                fillParent = true,
                allowGesture = true,
                singleFingerPan = true
            )
        }
        Text(
            text = "双指缩放 · 单指拖动平移",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}
