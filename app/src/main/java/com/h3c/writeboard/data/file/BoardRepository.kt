package com.h3c.writeboard.data.file

import android.content.Context
import android.net.Uri
import com.h3c.writeboard.domain.model.DrawingPage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class BoardFile(
    val version: Int = 1,
    val pages: List<DrawingPage>
)

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * 白板文件存储仓库
 *
 * 文件格式：JSON，扩展名 .pb（PaintBoard）
 * 存储路径：通过 SAF（Storage Access Framework）由用户选择
 */
@Singleton
class BoardRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** 将页面列表序列化并写入用户选择的 URI */
    fun save(pages: List<DrawingPage>, uri: Uri) {
        val content = json.encodeToString(BoardFile(pages = pages))
        context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
            stream.write(content.toByteArray(Charsets.UTF_8))
        }
    }

    /** 从 URI 读取并反序列化页面列表，失败返回 null */
    fun load(uri: Uri): List<DrawingPage>? {
        return try {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.readText()
                ?: return null
            json.decodeFromString<BoardFile>(text).pages
        } catch (e: Exception) {
            null
        }
    }

    // ===== 自动存盘（内部存储）=====

    private val autosaveFile get() = File(context.filesDir, "autosave.pb")

    /** 将页面列表序列化写入内部存储（自动存盘） */
    fun saveAutosave(pages: List<DrawingPage>) {
        val content = json.encodeToString(BoardFile(pages = pages))
        autosaveFile.writeText(content, Charsets.UTF_8)
    }

    /** 从内部存储读取自动存盘，失败或不存在返回 null */
    fun loadAutosave(): List<DrawingPage>? {
        if (!autosaveFile.exists()) return null
        return try {
            json.decodeFromString<BoardFile>(autosaveFile.readText(Charsets.UTF_8)).pages
        } catch (e: Exception) {
            null
        }
    }
}
