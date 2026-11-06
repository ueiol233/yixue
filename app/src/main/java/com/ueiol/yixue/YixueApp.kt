package com.ueiol.yixue

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.ueiol.yixue.data.AppContainer
import com.ueiol.yixue.notify.ReviewReminderWorker

class YixueApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // PDF 教材解析库初始化（加载内置字体资源）
        PDFBoxResourceLoader.init(applicationContext)
        container = AppContainer(this)
        // 复习提醒：通知渠道 + 每日一次到期检查（无到期不发通知）
        ReviewReminderWorker.ensureChannel(this)
        ReviewReminderWorker.schedule(this)
    }
}
