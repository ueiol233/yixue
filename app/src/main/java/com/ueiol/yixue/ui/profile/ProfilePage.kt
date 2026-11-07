package com.ueiol.yixue.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ueiol.yixue.BuildConfig
import com.ueiol.yixue.YixueApp
import com.ueiol.yixue.ui.common.YxCard
import com.ueiol.yixue.ui.common.YxLargeTitle

/**
 * 我的页（阶段 1 填充 API 设置，科目管理在阶段 5）。
 */
@Composable
fun ProfilePage(
    onOpenApiSettings: () -> Unit = {},
    onOpenSubjects: () -> Unit = {},
    onOpenQuizHistory: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        YxLargeTitle(
            title = "我的",
            actions = {
                IconButton(onClick = onOpenSearch) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = "全局搜索",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        )
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            SettingsCard {
                SettingsRow(
                    icon = Icons.Outlined.Insights, title = "学习统计",
                    subtitle = "答题量 / 正确率 / 错题分布", onClick = onOpenStats
                )
                SettingsRow(
                    icon = Icons.Outlined.History, title = "测验记录",
                    subtitle = "继续未完成的练习 / 重做已交卷的卷子", onClick = onOpenQuizHistory
                )
                SettingsRow(
                    icon = Icons.Outlined.Api, title = "AI 服务设置",
                    subtitle = "接口地址 / 密钥 / 模型", onClick = onOpenApiSettings
                )
                SettingsRow(
                    icon = Icons.Outlined.School, title = "科目管理",
                    subtitle = "科目分类与教材导入，AI 按教材原文讲解", onClick = onOpenSubjects
                )
                SettingsRow(
                    icon = Icons.Outlined.Backup, title = "数据备份",
                    subtitle = "导出 / 恢复全部学习数据", onClick = onOpenBackup
                )
                ReminderRow()
                VersionRow()
            }
        }
    }
}

/** 版本信息：版本号 / build 号（每次编译 +1）/ 编译时间，点击打开 GitHub 项目主页。 */
@Composable
private fun VersionRow() {
    val context = LocalContext.current
    SettingsRow(
        icon = Icons.Outlined.Info,
        title = "版本信息",
        subtitle = "v1.0 · Build ${BuildConfig.BUILD_NUMBER} · ${BuildConfig.BUILD_TIME}" +
            (if (BuildConfig.GIT_COMMIT != "dev") " · ${BuildConfig.GIT_COMMIT}" else "") +
            "\n点击打开 GitHub 项目主页 ↗",
        onClick = {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.REPO_URL)))
            }
        }
    )
}

/** 复习提醒开关：读写 SettingsStore 的独立偏好，后台 Worker 每日检查前会读取。 */
@Composable
private fun ReminderRow() {
    val app = LocalContext.current.applicationContext as YixueApp
    val enabled by app.container.settings.reviewReminderEnabled.collectAsState()
    SettingsRow(
        icon = Icons.Outlined.Notifications,
        title = "复习提醒",
        subtitle = "每天检查到期错题并发通知，点击进入错题本",
        onClick = { app.container.settings.setReviewReminderEnabled(!enabled) },
        trailing = {
            Switch(
                checked = enabled,
                onCheckedChange = { app.container.settings.setReviewReminderEnabled(it) }
            )
        }
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    YxCard(modifier = Modifier.fillMaxWidth()) {
        Column { content() }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit = {},
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (trailing == null) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            trailing()
        }
    }
}
