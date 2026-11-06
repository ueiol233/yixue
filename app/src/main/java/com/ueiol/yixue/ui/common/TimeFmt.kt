package com.ueiol.yixue.ui.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 全项目统一的时间显示格式，避免各页面重复定义 SimpleDateFormat。 */

// SimpleDateFormat 非线程安全，用 ThreadLocal 缓存避免每次调用新建 + 列表滚动时反复分配
private val shortFormat = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue() = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
}
private val fullFormat = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
}

/** 短格式：MM-dd HH:mm（列表卡片等场景）。 */
fun formatShortTime(ms: Long): String = shortFormat.get()!!.format(Date(ms))

/** 全格式：yyyy-MM-dd HH:mm（笔记等需要完整日期的场景）。 */
fun formatFullTime(ms: Long): String = fullFormat.get()!!.format(Date(ms))
