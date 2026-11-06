package com.ueiol.yixue.ui.notebook

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.runtime.Composable
import com.ueiol.yixue.YixueApp
import com.ueiol.yixue.data.db.NoteDao
import com.ueiol.yixue.data.db.NoteEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class NotebookViewModel(private val noteDao: NoteDao) : ViewModel() {

    val notes: StateFlow<List<NoteEntity>> = noteDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    fun setQuery(q: String) { _query.value = q }

    fun filtered(): List<NoteEntity> {
        val q = _query.value.trim()
        val list = notes.value
        return if (q.isEmpty()) list else list.filter {
            it.title.contains(q, true) || it.contentMarkdown.contains(q, true)
        }
    }

    // ---------- 删除 + 撤销（右滑删除防误删：不弹确认框，删完 10s 内可撤销） ----------

    private var lastDeleted: NoteEntity? = null

    fun delete(note: NoteEntity) {
        viewModelScope.launch {
            lastDeleted = note
            noteDao.delete(note)
        }
    }

    /** 撤销上一次删除：原样写回（保留原 id 与内容）。 */
    fun undoDelete() {
        val note = lastDeleted ?: return
        lastDeleted = null
        viewModelScope.launch { noteDao.insert(note) }
    }

    companion object {
        @Composable
        fun create(): NotebookViewModel = viewModel(factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as YixueApp
                NotebookViewModel(app.container.noteDao)
            }
        })
    }
}

class NoteEditorViewModel(
    private val noteDao: NoteDao,
    private val app: YixueApp,
    noteId: Long
) : ViewModel() {

    var noteId: Long = noteId
        private set

    val title = MutableStateFlow("")
    val content = MutableStateFlow("")
    val saving = MutableStateFlow(false)
    val savedTick = MutableStateFlow(0)

    private var saveJob: Job? = null
    private var loaded = false

    init {
        viewModelScope.launch {
            if (noteId > 0) {
                noteDao.getById(noteId)?.let {
                    title.value = it.title
                    content.value = it.contentMarkdown
                    loaded = true
                }
            } else {
                loaded = true
            }
        }
    }

    fun onTitleChange(t: String) { title.value = t; scheduleSave() }
    fun onContentChange(c: String) { content.value = c; scheduleSave() }

    /** 防抖自动保存（规划书 3.7）。 */
    private fun scheduleSave() {
        if (!loaded) return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(800)
            persist()
        }
    }

    private suspend fun persist() {
        saving.value = true
        val now = System.currentTimeMillis()
        val entity = NoteEntity(
            id = noteId,
            title = title.value.trim().ifEmpty { "无标题笔记" },
            contentMarkdown = content.value,
            updatedAt = now
        )
        if (noteId == 0L) {
            noteId = noteDao.insert(entity)
        } else {
            noteDao.update(entity)
        }
        saving.value = false
        savedTick.value = savedTick.value + 1
    }

    fun flush() {
        saveJob?.cancel()
        viewModelScope.launch { persist() }
    }

    /** 拷贝选中的图片到应用私有目录，返回 Markdown 引用文本。 */
    suspend fun importImage(uri: Uri): String? = try {
        val dir = File(app.filesDir, "pictures").apply { mkdirs() }
        val file = File(dir, "img_${System.currentTimeMillis()}.jpg")
        app.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        "![图片](${file.absolutePath})"
    } catch (t: Throwable) {
        null
    }

    companion object {
        @Composable
        fun create(noteId: Long): NoteEditorViewModel = viewModel(
            key = "note$noteId",
            factory = viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as YixueApp
                    NoteEditorViewModel(app.container.noteDao, app, noteId)
                }
            }
        )
    }
}