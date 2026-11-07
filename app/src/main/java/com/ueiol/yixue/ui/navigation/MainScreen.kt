package com.ueiol.yixue.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ueiol.yixue.ui.chat.ChatPage
import com.ueiol.yixue.ui.chat.ChatViewModel
import com.ueiol.yixue.ui.chat.SessionListPage
import com.ueiol.yixue.ui.notebook.NotebookPage
import com.ueiol.yixue.ui.profile.ProfilePage
import com.ueiol.yixue.ui.settings.ApiSettingsPage
import com.ueiol.yixue.ui.wrong.WrongBookPage

data class TabItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val iconSelected: ImageVector
)

private val tabs = listOf(
    TabItem("chat", "对话", Icons.AutoMirrored.Outlined.Chat, Icons.AutoMirrored.Filled.Chat),
    TabItem("wrong", "错题本", Icons.AutoMirrored.Outlined.FactCheck, Icons.AutoMirrored.Filled.FactCheck),
    TabItem("notebook", "笔记本", Icons.Outlined.EditNote, Icons.Filled.EditNote),
    TabItem("profile", "我的", Icons.Outlined.Person, Icons.Filled.Person)
)

object Routes {
    const val CHAT = "chat"
    const val WRONG = "wrong"
    const val NOTEBOOK = "notebook"
    const val PROFILE = "profile"
    const val SESSIONS = "sessions"
    const val API_SETTINGS = "settings/api"
    const val SUBJECTS = "settings/subjects"
    const val BACKUP = "settings/backup"
    const val STATS = "settings/stats"
    const val SEARCH = "search"
    const val NOTE_EDIT = "note/{id}?offset={offset}&len={len}"
    // offset/len：全局搜索命中的绝对偏移与长度（-1 表示普通打开，不定位）
    fun noteEdit(id: Long, offset: Int = -1, len: Int = 0) = "note/$id?offset=$offset&len=$len"
    const val QUIZ_SHEET = "quiz/{id}"
    const val QUIZ_RESULT = "quizResult/{id}"
    const val QUIZ_HISTORY = "quizHistory"
    fun quizSheet(id: Long) = "quiz/$id"
    fun quizResult(id: Long) = "quizResult/$id"
}
@Composable
fun MainScreen(openWrong: Boolean = false) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // 复习提醒通知点击进来：直接切到错题本 tab
    LaunchedEffect(openWrong) {
        if (openWrong) navController.navigate(Routes.WRONG) { launchSingleTop = true }
    }
    val chatVm: ChatViewModel = viewModel(factory = viewModelFactory {
        initializer {
            val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as com.ueiol.yixue.YixueApp
            ChatViewModel(
                app.container.chatRepository,
                app.container.quizRepository,
                app.container.noteDao,
                app.container.subjectDao,
                app.container.textbookDao,
                app.container.quizAgent,
                app.container.aiClient,
                app,
                { app.container.settings.isConfigured() }
            )
        }
    })

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // 顶部 inset 由各页面的 YxLargeTitle 自行处理，避免双倍空白
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        bottomBar = {
            if (tabs.any { it.route == currentRoute }) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    tonalElevation = 0.dp,
                    // M3 默认 80dp 偏高：收窄到 64dp，手势条间距自行处理
                    windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                    modifier = Modifier.navigationBarsPadding().height(64.dp)
                ) {
                    tabs.forEach { tab ->
                        val selected = tab.route == currentRoute
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.iconSelected else tab.icon,
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        CompositionLocalProvider(LocalScaffoldPadding provides innerPadding) {
            NavHost(
                navController = navController,
                startDestination = Routes.CHAT,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                // 默认转场是 ~700ms 的纯淡入淡出，很拖沓；换成 150ms 级别的快速淡入 + 轻微滑动
                enterTransition = {
                    fadeIn(tween(150)) + slideInHorizontally(tween(200)) { it / 12 }
                },
                exitTransition = {
                    fadeOut(tween(120))
                },
                popEnterTransition = {
                    fadeIn(tween(150)) + slideInHorizontally(tween(200)) { -it / 12 }
                },
                popExitTransition = {
                    fadeOut(tween(120)) + slideOutHorizontally(tween(200)) { it / 12 }
                }
            ) {
            composable(Routes.CHAT) {
                ChatPage(
                    vm = chatVm,
                    onOpenSessions = { navController.navigate(Routes.SESSIONS) },
                    onOpenQuiz = { id -> navController.navigate(Routes.quizSheet(id)) },
                    onOpenResult = { id -> navController.navigate(Routes.quizResult(id)) }
                )
            }
            composable(Routes.QUIZ_SHEET) { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                val quizVm = com.ueiol.yixue.ui.quiz.quizViewModel(id)
                com.ueiol.yixue.ui.quiz.QuizSheetPage(
                    vm = quizVm,
                    onBack = { navController.popBackStack() },
                    onSubmit = { sessionId, report ->
                        // 交卷后回对话页：报告先作为“附件”挂在输入框，由用户确认后发送
                        chatVm.attachReport(sessionId, report)
                        navController.navigate(Routes.CHAT) {
                            popUpTo(Routes.CHAT) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onOpenResult = { navController.navigate(Routes.quizResult(id)) }
                )
            }
            composable(Routes.QUIZ_HISTORY) {
                com.ueiol.yixue.ui.quiz.QuizHistoryPage(
                    onBack = { navController.popBackStack() },
                    onOpenQuiz = { id -> navController.navigate(Routes.quizSheet(id)) }
                )
            }
            composable(Routes.QUIZ_RESULT) { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                val quizVm = com.ueiol.yixue.ui.quiz.quizViewModel(id)
                com.ueiol.yixue.ui.quiz.QuizResultPage(vm = quizVm, onBack = { navController.popBackStack() })
            }
            composable(Routes.WRONG) {
                com.ueiol.yixue.ui.wrong.WrongBookPage(
                    vm = com.ueiol.yixue.ui.wrong.WrongBookViewModel.create(),
                    onOpenQuiz = { id -> navController.navigate(Routes.quizSheet(id)) }
                )
            }
            composable(Routes.NOTEBOOK) {
                val nbVm = com.ueiol.yixue.ui.notebook.NotebookViewModel.create()
                NotebookPage(vm = nbVm, onOpenNote = { id -> navController.navigate(Routes.noteEdit(id)) })
            }
            composable(
                Routes.NOTE_EDIT,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("offset") { type = NavType.IntType; defaultValue = -1 },
                    navArgument("len") { type = NavType.IntType; defaultValue = 0 }
                )
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                val jumpOffset = entry.arguments?.getInt("offset") ?: -1
                val jumpLen = entry.arguments?.getInt("len") ?: 0
                val editorVm = com.ueiol.yixue.ui.notebook.NoteEditorViewModel.create(id)
                com.ueiol.yixue.ui.notebook.NoteEditorPage(
                    vm = editorVm,
                    onBack = { navController.popBackStack() },
                    jumpOffset = jumpOffset,
                    jumpLen = jumpLen
                )
            }
            composable(Routes.PROFILE) {
                ProfilePage(
                    onOpenApiSettings = { navController.navigate(Routes.API_SETTINGS) },
                    onOpenSubjects = { navController.navigate(Routes.SUBJECTS) },
                    onOpenQuizHistory = { navController.navigate(Routes.QUIZ_HISTORY) },
                    onOpenBackup = { navController.navigate(Routes.BACKUP) },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenStats = { navController.navigate(Routes.STATS) }
                )
            }
            composable(Routes.SESSIONS) { SessionListPage(vm = chatVm, onBack = { navController.popBackStack() }) }
            composable(Routes.API_SETTINGS) { ApiSettingsPage(onBack = { navController.popBackStack() }) }
            composable(Routes.SUBJECTS) { com.ueiol.yixue.ui.settings.SubjectsPage(onBack = { navController.popBackStack() }) }
            composable(Routes.BACKUP) { com.ueiol.yixue.ui.settings.BackupPage(onBack = { navController.popBackStack() }) }
            composable(Routes.STATS) { com.ueiol.yixue.ui.stats.StatsPage(onBack = { navController.popBackStack() }) }
            composable(Routes.SEARCH) {
                com.ueiol.yixue.ui.search.GlobalSearchPage(
                    onBack = { navController.popBackStack() },
                    onOpenNote = { id, offset, len ->
                        navController.navigate(Routes.noteEdit(id, offset, len))
                    },
                    onOpenSession = { sessionId, messageId ->
                        // 跳回对话 tab，切换会话并滚动定位到命中消息
                        chatVm.selectSession(sessionId)
                        chatVm.requestScrollToMessage(messageId)
                        navController.navigate(Routes.CHAT) {
                            popUpTo(Routes.CHAT) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onOpenQuiz = { id -> navController.navigate(Routes.quizSheet(id)) }
                )
            }
            }
        }
    }
}

/** 各页面用于 inset 消费的 Scaffold padding。 */
val LocalScaffoldPadding = staticCompositionLocalOf { PaddingValues(0.dp) }