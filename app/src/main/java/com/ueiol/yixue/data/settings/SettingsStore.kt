package com.ueiol.yixue.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.ueiol.yixue.domain.model.AppJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString

/**
 * 一个 AI 接口（Provider）：用户可添加多个，各自配名称 / 地址 / 密钥 / 模型库。
 * 内置平台作为添加时的模板，名字与地址都可改。
 */
@kotlinx.serialization.Serializable
data class ProviderConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String = "",
    val models: List<String> = emptyList(),
    // 该 provider 下当前选用的模型
    val selectedModel: String = ""
)

data class AiSettings(
    // 以下五项为「当前生效」快照（= activeProvider 的值），所有请求方直接读取
    val baseUrl: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val model: String = "gpt-4o-mini",
    val temperature: Double = 0.7,
    val maxTokens: Int = 4096,
    // 多 Provider 管理
    val providers: List<ProviderConfig> = emptyList(),
    val activeProviderId: String = ""
) {
    val activeProvider: ProviderConfig?
        get() = providers.firstOrNull { it.id == activeProviderId }
}

/**
 * AI 服务配置存储：密钥与 Provider 列表（含各密钥）用 EncryptedSharedPreferences，
 * 其余项用普通 prefs。加密存储初始化失败时降级为普通存储，不阻塞应用。
 * 兼容旧版本：首次升级时把旧的单套配置自动转换为一个 Provider。
 */
class SettingsStore(context: Context) {

    private val plainPrefs: SharedPreferences =
        context.getSharedPreferences("yixue_settings", Context.MODE_PRIVATE)

    private val securePrefs: SharedPreferences? = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "yixue_secure_settings",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (t: Throwable) {
        null
    }

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AiSettings> = _settings

    val current: AiSettings get() = _settings.value

    // ---------- 复习提醒开关（独立偏好，不进 AiSettings 快照，避免被快照重建覆盖） ----------

    private val _reviewReminderEnabled =
        MutableStateFlow(plainPrefs.getBoolean(KEY_REVIEW_REMINDER, true))
    val reviewReminderEnabled: StateFlow<Boolean> = _reviewReminderEnabled

    fun setReviewReminderEnabled(enabled: Boolean) {
        _reviewReminderEnabled.value = enabled
        plainPrefs.edit().putBoolean(KEY_REVIEW_REMINDER, enabled).apply()
    }

    private fun load(): AiSettings {
        val secure = securePrefs ?: plainPrefs
        val oldBase = plainPrefs.getString(KEY_BASE_URL, null)
            ?: "https://api.openai.com/v1"
        val oldKey = secure.getString(KEY_API_KEY, null) ?: ""
        val oldModel = plainPrefs.getString(KEY_MODEL, null)
            ?: plainPrefs.getString(KEY_TEXT_MODEL, null)
            ?: "gpt-4o-mini"
        val temperature = plainPrefs.getFloat(KEY_TEMPERATURE, 0.7f).toDouble()
        val maxTokens = plainPrefs.getInt(KEY_MAX_TOKENS, 4096)

        // 读取已保存的 Provider 列表（密文）
        var providers = secure.getString(KEY_PROVIDERS, null)?.let { raw ->
            runCatching { AppJson.decodeFromString<List<ProviderConfig>>(raw) }.getOrNull()
        } ?: emptyList()
        var activeId = plainPrefs.getString(KEY_ACTIVE_PROVIDER, null) ?: ""

        // 旧版本迁移：只有单套配置时转换为一个 Provider
        if (providers.isEmpty() && (oldKey.isNotBlank() || plainPrefs.contains(KEY_BASE_URL))) {
            val oldModels = plainPrefs.getString(KEY_PROVIDER_MODELS, null)?.let { raw ->
                runCatching { AppJson.decodeFromString<Map<String, List<String>>>(raw) }
                    .getOrNull()
            }?.get(normalizeBaseUrl(oldBase)).orEmpty()
            val migrated = ProviderConfig(
                id = newId(),
                name = DEFAULT_PLATFORMS.firstOrNull { normalizeBaseUrl(it.second) == normalizeBaseUrl(oldBase) }?.first
                    ?: "自定义接口",
                baseUrl = oldBase,
                apiKey = oldKey,
                models = (listOf(oldModel) + oldModels).distinct(),
                selectedModel = oldModel
            )
            providers = listOf(migrated)
            activeId = migrated.id
            plainPrefs.edit().remove(KEY_PROVIDER_MODELS).apply()
            val snap = buildSnapshot(providers, activeId, oldModel, temperature, maxTokens)
            // load() 期间 _settings 尚未初始化，只能写盘不能更新 StateFlow
            writePrefs(providers, activeId, snap)
            return snap
        }

        if (providers.isNotEmpty() && activeId !in providers.map { it.id }) {
            activeId = providers.first().id
        }
        return buildSnapshot(providers, activeId, oldModel, temperature, maxTokens)
    }

    /** 由 activeProvider 生成请求方读取的快照；无 provider 时保留传入的全局参数。 */
    private fun buildSnapshot(
        providers: List<ProviderConfig>,
        activeId: String,
        fallbackModel: String,
        temperature: Double,
        maxTokens: Int
    ): AiSettings {
        val active = providers.firstOrNull { it.id == activeId }
        return if (active == null) {
            AiSettings(
                baseUrl = "https://api.openai.com/v1",
                apiKey = "",
                model = fallbackModel,
                temperature = temperature,
                maxTokens = maxTokens,
                providers = providers,
                activeProviderId = ""
            )
        } else {
            AiSettings(
                baseUrl = active.baseUrl,
                apiKey = active.apiKey,
                model = active.selectedModel.ifBlank { active.models.firstOrNull() ?: fallbackModel },
                temperature = temperature,
                maxTokens = maxTokens,
                providers = providers,
                activeProviderId = activeId
            )
        }
    }

    private fun persist(providers: List<ProviderConfig>, activeId: String, snapshot: AiSettings) {
        writePrefs(providers, activeId, snapshot)
        _settings.value = snapshot
    }

    /** 只写盘，不更新 StateFlow——供 load() 迁移期间调用（此时 _settings 尚未初始化）。 */
    private fun writePrefs(providers: List<ProviderConfig>, activeId: String, snapshot: AiSettings) {
        val secure = securePrefs ?: plainPrefs
        val editor = plainPrefs.edit()
            .putString(KEY_BASE_URL, snapshot.baseUrl)
            .putString(KEY_MODEL, snapshot.model)
            .putFloat(KEY_TEMPERATURE, snapshot.temperature.toFloat())
            .putInt(KEY_MAX_TOKENS, snapshot.maxTokens)
            .putString(KEY_ACTIVE_PROVIDER, activeId)
        editor.apply()
        secure.edit()
            .putString(KEY_API_KEY, snapshot.apiKey)
            .putString(KEY_PROVIDERS, AppJson.encodeToString(providers))
            .apply()
    }

    /** 新增 / 更新一个 Provider；若是激活项则同步快照。 */
    fun upsertProvider(provider: ProviderConfig) {
        val list = current.providers.filterNot { it.id == provider.id } + provider
        val activeId = if (current.activeProviderId == provider.id) provider.id else current.activeProviderId
        persist(list, activeId, buildSnapshot(list, activeId, current.model, current.temperature, current.maxTokens))
    }

    /** 删除 Provider；删的是激活项则激活切到剩余第一个。 */
    fun removeProvider(id: String) {
        val list = current.providers.filterNot { it.id == id }
        val activeId = when {
            current.activeProviderId != id -> current.activeProviderId
            list.isNotEmpty() -> list.first().id
            else -> ""
        }
        persist(list, activeId, buildSnapshot(list, activeId, current.model, current.temperature, current.maxTokens))
    }

    /** 设为当前使用的接口（同步快照，请求立即生效）。 */
    fun setActiveProvider(id: String) {
        if (current.providers.any { it.id == id }) {
            persist(current.providers, id, buildSnapshot(current.providers, id, current.model, current.temperature, current.maxTokens))
        }
    }

    /** 更新全局生成参数（温度 / 最大 token），对所有 Provider 生效。 */
    fun updateGenerationParams(temperature: Double, maxTokens: Int) {
        persist(current.providers, current.activeProviderId, current.copy(temperature = temperature, maxTokens = maxTokens))
    }

    /** 在指定 Provider 的模型库里选用某个模型（同时记为该库的选中项）。 */
    fun setActiveModel(providerId: String, model: String) {
        val p = current.providers.firstOrNull { it.id == providerId } ?: return
        val updated = p.copy(
            selectedModel = model,
            models = if (model in p.models) p.models else p.models + model
        )
        upsertProvider(updated)
        if (current.activeProviderId != providerId) setActiveProvider(providerId)
    }

    fun isConfigured(): Boolean = current.apiKey.isNotBlank() && current.baseUrl.isNotBlank()

    companion object {
        fun normalizeBaseUrl(raw: String): String = raw.trim().trimEnd('/')

        fun newId(): String = "p_" + java.util.UUID.randomUUID().toString().replace("-", "").take(16)

        // 内置平台模板（名称, 默认接口地址）
        val DEFAULT_PLATFORMS: List<Pair<String, String>> = listOf(
            "DeepSeek" to "https://api.deepseek.com/v1",
            "硅基流动" to "https://api.siliconflow.cn/v1",
            "智谱GLM" to "https://open.bigmodel.cn/api/paas/v4",
            "Kimi" to "https://api.moonshot.cn/v1",
            "通义千问" to "https://dashscope.aliyuncs.com/compatible-mode/v1",
            "OpenAI" to "https://api.openai.com/v1"
        )

        const val KEY_BASE_URL = "base_url"
        const val KEY_API_KEY = "api_key"
        const val KEY_MODEL = "model"
        // 兼容旧版本字段
        const val KEY_TEXT_MODEL = "text_model"
        const val KEY_TEMPERATURE = "temperature"
        const val KEY_MAX_TOKENS = "max_tokens"
        const val KEY_PROVIDER_MODELS = "provider_models"
        const val KEY_PROVIDERS = "providers"
        const val KEY_ACTIVE_PROVIDER = "active_provider"
        const val KEY_REVIEW_REMINDER = "review_reminder_enabled"
    }
}
