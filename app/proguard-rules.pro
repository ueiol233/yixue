# 易学 release 混淆规则
# Room / Compose / OkHttp / kotlinx-serialization 的 consumer 规则随依赖自带，无需手写。

# kotlinx.serialization：保留 @Serializable 类的序列化器（Room Entity 备份导出用）
-keepclassmembers class * implements kotlinx.serialization.KSerializer {
    public static ** Companion;
}
-keepclasseswithmembers class com.ueiol.yixue.data.db.** {
    <fields>;
}

# pdfbox-android（教材导入）：JPEG2000 为可选依赖，引用但不打包
-dontwarn com.gemalto.jp2.**

# Tink（EncryptedSharedPreferences）引用的 errorprone 注解仅编译期存在
-dontwarn com.google.errorprone.annotations.Immutable

# 保留崩溃行号，便于排查线上问题
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
