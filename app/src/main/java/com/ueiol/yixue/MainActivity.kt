package com.ueiol.yixue

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ueiol.yixue.notify.ReviewReminderWorker
import com.ueiol.yixue.ui.navigation.MainScreen
import com.ueiol.yixue.ui.theme.YixueTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 复习提醒通知点击 → 直接进错题本
        val openWrong = intent?.getBooleanExtra(ReviewReminderWorker.EXTRA_OPEN_WRONG, false) ?: false
        setContent {
            YixueTheme {
                MainScreen(openWrong = openWrong)
            }
        }
    }
}
