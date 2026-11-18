package com.jof.ringtones

import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaScannerConnection
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class Song(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val isRingtone: Boolean,
    val owner: String?
)

fun readPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 33 -> arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO)
    Build.VERSION.SDK_INT <= 28 -> arrayOf(
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    else -> arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
}

class RingtoneViewModel(app: Application) : AndroidViewModel(app) {

    val songs = MutableStateFlow<List<Song>>(emptyList())
    val loading = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val favorites = MutableStateFlow<Set<Long>>(emptySet())
    val query = MutableStateFlow("")
    val onlyFavorites = MutableStateFlow(false)
    val hasPermission = MutableStateFlow(false)
    val needWriteSettings = MutableStateFlow(false)
    val deleteRequest = MutableStateFlow<IntentSender?>(null)
    val playingId = MutableStateFlow<Long?>(null)
    val isPlaying = MutableStateFlow(false)
    val positionMs = MutableStateFlow(0)
    val durationMs = MutableStateFlow(0)

    private val prefs = app.getSharedPreferences("ringtones", Context.MODE_PRIVATE)
    private val resolver get() = getApplication<Application>().contentResolver
    private var player: MediaPlayer? = null
    private var ticker: Job? = null
    private var prepared = false

    init {
        favorites.value = (prefs.getStringSet("fav", emptySet()) ?: emptySet())
            .mapNotNull { it.toLongOrNull() }.toSet()
        hasPermission.value = checkPermission()
        if (hasPermission.value) refresh()
    }

    fun checkPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33)
            android.Manifest.permission.READ_MEDIA_AUDIO
        else
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        return getApplication<Application>()
            .checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    fun onPermissionResult() {
        hasPermission.value = checkPermission()
        if (hasPermission.value) refresh()
    }

    fun refresh() {
        if (!checkPermission()) {
            hasPermission.value = false
            return
        }
        hasPermission.value = true
        viewModelScope.launch {
            loading.value = true
            val result = withContext(Dispatchers.IO) { runCatching { querySongs() } }
            result.onSuccess { songs.value = it }
            result.onFailure { message.value = "Не удалось прочитать медиатеку" }
            loading.value = false
        }
    }

    private fun querySongs(): List<Song> {
        val base = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.IS_RINGTONE
        )
        val projection =
            if (Build.VERSION.SDK_INT >= 29) base + MediaStore.Audio.Media.OWNER_PACKAGE_NAME else base
        val list = ArrayList<Song>()
        resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Audio.Media.DURATION} > 0",
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { c ->
            val ownerIdx = c.getColumnIndex(MediaStore.Audio.Media.OWNER_PACKAGE_NAME)
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val artist = c.getString(2)
                list.add(
                    Song(
                        id = id,
                        uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                        title = c.getString(1) ?: "Без названия",
                        artist = if (artist.isNullOrBlank() || artist == "<unknown>") "Неизвестный исполнитель" else artist,
                        durationMs = c.getLong(3),
                        isRingtone = c.getInt(4) != 0,
                        owner = if (ownerIdx >= 0) c.getString(ownerIdx) else null
                    )
                )
            }
        }
        return list
    }

    // ---------- Плеер ----------

    fun play(song: Song) {
        if (playingId.value == song.id && player != null) {
            if (isPlaying.value) pause() else resume()
            return
        }
        stopPlayer()
        playingId.value = song.id
        positionMs.value = 0
        durationMs.value = song.durationMs.toInt()
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.setDataSource(getApplication(), song.uri)
            mp.setOnPreparedListener {
                prepared = true
                durationMs.value = it.duration
                runCatching { it.start() }
                isPlaying.value = true
                startTicker()
            }
            mp.setOnCompletionListener {
                isPlaying.value = false
                positionMs.value = 0
                ticker?.cancel()
            }
            mp.setOnErrorListener { _, _, _ ->
                message.value = "Не удалось воспроизвести файл"
                stop()
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            message.value = "Файл недоступен"
            stop()
        }
    }

    fun pause() {
        val mp = player ?: return
        if (prepared) runCatching { mp.pause() }
        isPlaying.value = false
        ticker?.cancel()
    }

    fun resume() {
        val mp = player ?: return
        if (!prepared) return
        runCatching { mp.start() }
        isPlaying.value = true
        startTicker()
    }

    fun seekTo(ms: Int) {
        if (prepared) runCatching { player?.seekTo(ms) }
        positionMs.value = ms
    }

    fun stop() {
        stopPlayer()
        playingId.value = null
        positionMs.value = 0
        durationMs.value = 0
    }

    private fun stopPlayer() {
        ticker?.cancel()
        ticker = null
        prepared = false
        player?.let { mp ->
            runCatching { mp.reset() }
            runCatching { mp.release() }
        }
        player = null
        isPlaying.value = false
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                val mp = player
                if (prepared && mp != null) {
                    positionMs.value = runCatching { mp.currentPosition }.getOrDefault(0)
                }
                delay(300)
            }
        }
    }

    // ---------- Избранное ----------

    fun toggleFavorite(id: Long) {
        val current = favorites.value
        val next = if (id in current) current - id else current + id
        favorites.value = next
        prefs.edit().putStringSet("fav", next.map { it.toString() }.toSet()).apply()
    }

    // ---------- Установка мелодии ----------

    fun applyAs(song: Song, type: Int) {
        val app = getApplication<Application>()
        if (!Settings.System.canWrite(app)) {
            needWriteSettings.value = true
            return
        }
        viewModelScope.launch {
            loading.value = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val uri = ensureSettable(song, type)
                    RingtoneManager.setActualDefaultRingtoneUri(app, type, uri)
                }
            }
            loading.value = false
            message.value = if (result.isSuccess) {
                when (type) {
                    RingtoneManager.TYPE_NOTIFICATION -> "Звук уведомления установлен"
                    RingtoneManager.TYPE_ALARM -> "Сигнал будильника установлен"
                    else -> "Рингтон установлен"
                }
            } else {
                "Не удалось установить мелодию"
            }
        }
    }

    private fun ensureSettable(song: Song, type: Int): Uri {
        if (Build.VERSION.SDK_INT < 29) return song.uri
        val dir: String
        val column: String
        when (type) {
            RingtoneManager.TYPE_NOTIFICATION -> {
                dir = Environment.DIRECTORY_NOTIFICATIONS
                column = MediaStore.Audio.Media.IS_NOTIFICATION
            }
            RingtoneManager.TYPE_ALARM -> {
                dir = Environment.DIRECTORY_ALARMS
                column = MediaStore.Audio.Media.IS_ALARM
            }
            else -> {
                dir = Environment.DIRECTORY_RINGTONES
                column = MediaStore.Audio.Media.IS_RINGTONE
            }
        }
        if (song.owner == getApplication<Application>().packageName) return song.uri
        if (type == RingtoneManager.TYPE_RINGTONE && song.isRingtone) return song.uri

        val key = "copy_" + song.id + "_" + type
        val cached = prefs.getLong(key, -1L)
        if (cached > 0) {
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cached)
            val exists = runCatching {
                resolver.query(uri, arrayOf(MediaStore.Audio.Media._ID), null, null, null)
                    ?.use { it.moveToFirst() } ?: false
            }.getOrDefault(false)
            if (exists) return uri
        }
        val target = insertCopy(song.uri, song.title, dir, column)
        prefs.edit().putLong(key, ContentUris.parseId(target)).apply()
        return target
    }

    private fun insertCopy(source: Uri, title: String, dir: String, column: String): Uri {
        val mime = resolver.getType(source) ?: "audio/mpeg"
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "mp3"
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, safeName(title) + "." + ext)
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.RELATIVE_PATH, dir)
            put(column, 1)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val target = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Не удалось создать файл")
        try {
            val input = resolver.openInputStream(source) ?: throw IllegalStateException("Файл недоступен")
            val output = resolver.openOutputStream(target) ?: throw IllegalStateException("Нет доступа на запись")
            input.use { i -> output.use { o -> i.copyTo(o) } }
        } catch (t: Throwable) {
            runCatching { resolver.delete(target, null, null) }
            throw t
        }
        values.clear()
        values.put(MediaStore.Audio.Media.IS_PENDING, 0)
        resolver.update(target, values, null, null)
        return target
    }

    private fun safeName(raw: String): String {
        val cleaned = raw.replace(Regex("[^A-Za-zА-Яа-яЁё0-9 ._\\-]"), "_").trim()
        return if (cleaned.isEmpty()) "ringtone" else cleaned.take(60)
    }

    // ---------- Импорт ----------

    fun importFile(source: Uri) {
        val app = getApplication<Application>()
        val rawName = runCatching {
            resolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: ("ringtone_" + System.currentTimeMillis())
        val title = rawName.substringBeforeLast('.')

        viewModelScope.launch {
            loading.value = true
            if (Build.VERSION.SDK_INT >= 29) {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        insertCopy(source, title, Environment.DIRECTORY_RINGTONES, MediaStore.Audio.Media.IS_RINGTONE)
                    }
                }
                loading.value = false
                result.onSuccess {
                    message.value = "Файл добавлен"
                    refresh()
                }
                result.onFailure { message.value = "Не удалось добавить файл" }
            } else {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val mime = resolver.getType(source) ?: "audio/mpeg"
                        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "mp3"
                        @Suppress("DEPRECATION")
                        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES)
                        dir.mkdirs()
                        val file = File(dir, safeName(title) + "." + ext)
                        val input = resolver.openInputStream(source) ?: throw IllegalStateException("Файл недоступен")
                        input.use { i -> FileOutputStream(file).use { o -> i.copyTo(o) } }
                        MediaScannerConnection.scanFile(app, arrayOf(file.absolutePath), arrayOf(mime)) { _, _ ->
                            message.value = "Файл добавлен"
                            refresh()
                        }
                    }
                }
                loading.value = false
                result.onFailure { message.value = "Не удалось добавить файл" }
            }
        }
    }

    // ---------- Удаление ----------

    fun delete(song: Song) {
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching {
                deleteRequest.value = MediaStore.createDeleteRequest(resolver, listOf(song.uri)).intentSender
            }.onFailure { message.value = "Не удалось удалить файл" }
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { resolver.delete(song.uri, null, null) } }
            result.onSuccess {
                if (playingId.value == song.id) stop()
                message.value = "Файл удалён"
                refresh()
            }
            result.onFailure { e ->
                if (Build.VERSION.SDK_INT >= 29 && e is android.app.RecoverableSecurityException) {
                    deleteRequest.value = e.userAction.actionIntent.intentSender
                } else {
                    message.value = "Не удалось удалить файл"
                }
            }
        }
    }

    override fun onCleared() {
        stopPlayer()
        super.onCleared()
    }
}
