package com.ueiol.yixue.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ueiol.yixue.YixueApp
import com.ueiol.yixue.data.settings.AiSettings
import com.ueiol.yixue.data.settings.ProviderConfig
import com.ueiol.yixue.data.settings.SettingsStore
import com.ueiol.yixue.ui.common.YxEmptyState
import com.ueiol.yixue.ui.common.YxActionChip
import com.ueiol.yixue.ui.common.YxConfirmDialog
import com.ueiol.yixue.ui.common.YxLargeTitle
import com.ueiol.yixue.ui.common.YxPill
import com.ueiol.yixue.ui.theme.YxCardShape
import com.ueiol.yixue.ui.theme.YxExt
import com.ueiol.yixue.ui.theme.YxSheetShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * AI 服务设置：多 Provider 管理。
 * 默认没有任何接口；从内置平台模板添加，名称 / 地址 / 密钥 / 模型库都可修改，
 * 每个接口各自维护模型列表，点「设为当前使用」切换生效（主页「+」里也能切模型）。
 */
@Composable
fun ApiSettingsPage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val app = LocalContext.current.applicationContext as YixueApp
    val store = app.container.settings
    val saved by store.settings.collectAsState()
    val scope = rememberCoroutineScope()

    // 正在编辑的 Provider（本地副本，点「保存修改」写回）
    var editingId by remember { mutableStateOf<String?>(null) }
    val editing = saved.providers.firstOrNull { it.id == editingId }
    var showAddPicker by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var fetchingModels by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var deletingProvider by remember { mutableStateOf<ProviderConfig?>(null) }
    var temperature by remember(saved.temperature) { mutableStateOf(saved.temperature.toString()) }
    var maxTokens by remember(saved.maxTokens) { mutableStateOf(saved.maxTokens.toString()) }

    // 任何修改直接保存：名称/地址/密钥/模型在输入时即时写盘，
    // 数值参数输入停顿 600ms 后自动生效
    LaunchedEffect(temperature, maxTokens) {
        delay(600)
        val t = temperature.toDoubleOrNull()?.coerceIn(0.0, 2.0)
        val m = maxTokens.toIntOrNull()?.coerceIn(64, 1_000_000)
        if (t != null && m != null && (t != saved.temperature || m != saved.maxTokens)) {
            store.updateGenerationParams(t, m)
        }
    }

    fun saveEditing() {
        val e = editing ?: return
        store.upsertProvider(e)
    }

    Column(modifier = modifier.fillMaxSize().navigationBarsPadding()) {
        YxLargeTitle(title = "AI 服务设置", actions = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
            }
        })

        if (saved.providers.isEmpty()) {
            YxEmptyState(
                icon = Icons.Outlined.Key,
                title = "还没有添加 AI 接口",
                hint = "从 DeepSeek / 硅基流动 / 智谱等平台模板添加\n名字、地址、模型都可以自己改",
                modifier = Modifier.weight(1f)
            )
            YxPill(
                text = "添加接口",
                primary = true,
                height = 44.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) { showAddPicker = true }
        } else {
            // Provider 横滑列表：绿点 = 当前使用；「+」添加
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                saved.providers.forEach { p ->
                    FilterChip(
                        selected = p.id == editingId,
                        onClick = { editingId = p.id },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (p.id == saved.activeProviderId) {
                                    androidx.compose.foundation.layout.Box(
                                        Modifier
                                            .size(8.dp)
                                            .background(YxExt.success, CircleShape)
                                    )
                                    Spacer(Modifier.size(6.dp))
                                }
                                Text(p.name)
                            }
                        }
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = { showAddPicker = true },
                    label = { Icon(Icons.Outlined.Add, contentDescription = "添加接口", modifier = Modifier.size(16.dp)) }
                )
            }

            val e = editing ?: saved.activeProvider ?: saved.providers.first()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingsField("名称（自己起，方便区分）", e.name, onValueChange = { v ->
                    editingId = e.id
                    val updated = e.copy(name = v)
                    store.upsertProvider(updated)
                })
                SettingsField("接口地址（填到 /v1 即可）", e.baseUrl) { v ->
                    editingId = e.id
                    store.upsertProvider(e.copy(baseUrl = v))
                }
                SettingsField("API Key", e.apiKey, isPassword = true) { v ->
                    editingId = e.id
                    store.upsertProvider(e.copy(apiKey = v))
                }

                // 模型库
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = YxCardShape, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("当前模型", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            e.selectedModel.ifBlank { "未选择" },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "模型库 ${e.models.size} 个 · 主页「+」里可随时切换",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            YxPill(text = if (fetchingModels) "拉取中…" else "拉取模型列表", height = 40.dp, enabled = !fetchingModels) {
                                fetchingModels = true
                                message = null
                                saveEditing()
                                scope.launch {
                                    try {
                                        val list = app.container.aiClient.listModels()
                                        if (list.isEmpty()) message = "接口未返回可用模型"
                                        else {
                                            store.upsertProvider(e.copy(models = (e.models + list).distinct().sorted()))
                                            showModelPicker = true
                                        }
                                    } catch (t: Throwable) {
                                        message = "拉取失败：${t.message?.take(200)}"
                                    } finally {
                                        fetchingModels = false
                                    }
                                }
                            }
                            YxActionChip(icon = Icons.Outlined.Functions, text = "管理模型（${e.models.size}）") {
                                // 先锁定当前编辑对象：否则没碰过输入框时 editing 为 null，弹窗条件不成立（点不动）
                                editingId = e.id
                                showModelPicker = true
                            }
                        }
                    }
                }

                SettingsField("温度（0-2，越大越发散，自动保存）", temperature) { temperature = it }
                SettingsField("最大回复长度（token，自动保存）", maxTokens) { maxTokens = it }

                if (e.id != saved.activeProviderId) {
                    Text(
                        "设为当前使用",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable {
                                saveEditing()
                                store.setActiveProvider(e.id)
                                message = "已切换到「${e.name}」"
                            }
                            .padding(vertical = 12.dp)
                    )
                }
                Text(
                    "删除此接口",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .clickable { deletingProvider = e }
                        .padding(vertical = 12.dp)
                )
                message?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (it.startsWith("拉取失败")) MaterialTheme.colorScheme.error else YxExt.success
                    )
                }
            }
        }
    }

    // 添加 Provider：选内置模板或自定义
    deletingProvider?.let { p ->
        YxConfirmDialog(
            title = "删除接口「${p.name}」？",
            text = "该接口的地址、密钥与模型库会一并删除。",
            confirmText = "删除",
            danger = true,
            onDismiss = { deletingProvider = null },
            onConfirm = {
                store.removeProvider(p.id)
                deletingProvider = null
                if (editingId == p.id) editingId = null
            }
        )
    }
    if (showAddPicker) {
        Dialog(onDismissRequest = { showAddPicker = false }) {
            Surface(color = MaterialTheme.colorScheme.surface, shape = YxSheetShape, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "添加接口",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "选一个平台作为起点，名称 / 地址 / 模型之后都能改",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        SettingsStore.DEFAULT_PLATFORMS.forEach { (name, base) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val p = ProviderConfig(
                                            id = SettingsStore.newId(),
                                            name = name,
                                            baseUrl = base
                                        )
                                        store.upsertProvider(p)
                                        editingId = p.id
                                        showAddPicker = false
                                    }
                                    .padding(vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                                    Text(base, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(
                                    Icons.Outlined.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val p = ProviderConfig(
                                        id = SettingsStore.newId(),
                                        name = "自定义接口",
                                        baseUrl = "https://"
                                    )
                                    store.upsertProvider(p)
                                    editingId = p.id
                                    showAddPicker = false
                                }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("自定义接口（任意 OpenAI 兼容地址）", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            Icon(Icons.Outlined.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    // 模型库管理（当前编辑 Provider）
    if (showModelPicker && editing != null) {
        val e = editing
        var newModel by remember { mutableStateOf("") }
        var filter by remember { mutableStateOf("") }
        val shown = e.models.filter { it.contains(filter.trim(), ignoreCase = true) }
        Dialog(onDismissRequest = { showModelPicker = false }) {
            Surface(color = MaterialTheme.colorScheme.surface, shape = YxSheetShape, modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("「${e.name}」的模型库", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = filter,
                        onValueChange = { filter = it },
                        placeholder = { Text("搜索模型") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        if (e.models.isEmpty()) {
                            Text(
                                "还没有模型。可以先「拉取模型列表」，或在下方手动添加。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                        shown.forEach { m ->
                            val chosen = m == e.selectedModel
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        editingId = e.id
                                        store.upsertProvider(e.copy(selectedModel = m))
                                    }
                                    .heightIn(min = 48.dp)
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    m,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                if (chosen) {
                                    Icon(Icons.Outlined.Check, contentDescription = "已选择", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                                IconButton(
                                    onClick = { store.upsertProvider(e.copy(models = e.models - m)) },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Outlined.Close, contentDescription = "移除", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newModel,
                            onValueChange = { newModel = it },
                            placeholder = { Text("手动添加模型名") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        YxPill(
                            text = "添加",
                            primary = true,
                            enabled = newModel.isNotBlank(),
                            height = 40.dp
                        ) {
                            val name = newModel.trim()
                            editingId = e.id
                            store.upsertProvider(e.copy(models = (e.models + name).distinct(), selectedModel = e.selectedModel.ifBlank { name }))
                            newModel = ""
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    YxPill(text = "关闭", height = 40.dp, modifier = Modifier.fillMaxWidth()) { showModelPicker = false }
                }
            }
        }
    }
}

@Composable
private fun SettingsField(
    label: String,
    value: String,
    isPassword: Boolean = false,
    onValueChange: (String) -> Unit
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None
        )
    }
}
