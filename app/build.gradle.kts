import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.serialization)
}

// ---------- 版本信息：build 号每次编译 +1（version.properties，不入库）、编译时间、git 提交号 ----------
val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) versionPropsFile.inputStream().use { load(it) }
}
val buildNumber = (versionProps.getProperty("buildNumber")?.toIntOrNull() ?: 1).also { n ->
    versionProps.setProperty("buildNumber", (n + 1).toString())
    versionPropsFile.outputStream().use { versionProps.store(it, "yixue build counter (auto-increment per build)") }
}
val buildTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
val gitCommit = try {
    providers.exec { commandLine("git", "rev-parse", "--short", "HEAD") }.standardOutput.asText.get().trim()
} catch (_: Exception) {
    "dev"
}
val repoUrl = "https://github.com/ueiol233/yixue"

android {
    namespace = "com.ueiol.yixue"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ueiol.yixue"
        minSdk = 24
        targetSdk = 36
        // versionCode 随编译递增，直接用 build 号
        versionCode = buildNumber
        versionName = "1.0"

        buildConfigField("int", "BUILD_NUMBER", buildNumber.toString())
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
        buildConfigField("String", "GIT_COMMIT", "\"$gitCommit\"")
        buildConfigField("String", "REPO_URL", "\"$repoUrl\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // 只保留 ARM 系列：arm64-v8a（主流 64 位真机）+ armeabi-v7a（32 位老设备）
            // x86/x86_64 仅模拟器使用，不进包
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            // R8 混淆 + 无用资源收缩：material-icons-extended 未用图标、无用代码全部剔除
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kapt {
    // Room schema 导出目录：exportSchema=true 时每次版本变更生成 app/schemas/N.json（迁移历史入库）
    // 注：KSP 曾尝试接入（2.0.21-1.0.28），当前镜像仓库不可达，暂保留 kapt
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.security.crypto)
    implementation(libs.coil.compose)
    // 复习提醒：每日错题到期检查（周期性后台任务 + 本地通知）
    implementation(libs.androidx.work.runtime.ktx)
    // PDF 文本提取（安卓版 pdfbox，用于教材导入）
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}