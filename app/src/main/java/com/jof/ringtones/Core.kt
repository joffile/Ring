package com.jof.ringtones

import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

data class Song(
    val key: String,
    val id: Long,
    val uri: Uri?,
    val path: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val folder: String,
    val volume: String,
    val owner: String?,
    val isRingtone: Boolean,
    val isNotification: Boolean,
    val isAlarm: Boolean,
    val indexed: Boolean,
    val dateAdded: Long
)

data class ScanStats(
    val files: Int,
    val folders: Int,
    val volumes: Int,
    val diskOnly: Int,
    val seconds: Float
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
    val aliases = MutableStateFlow<Map<String, String>>(emptyMap())
    val favorites = MutableStateFlow<Set<String>>(emptySet())
    val tab = MutableStateFlow(0)
    val libraryQuery = MutableStateFlow("")
    val searchQuery = MutableStateFlow("")
    val sort = MutableStateFlow(0)
    val filter = MutableStateFlow(0)
    val searchScope = MutableStateFlow(0)
    val minDuration = MutableStateFlow(0)
    val scanFs = MutableStateFlow(true)
    val hideSystem = MutableStateFlow(true)
    val showPaths = MutableStateFlow(true)
    val volumeLevel = MutableStateFlow(80)
    val muted = MutableStateFlow(false)
    val repeat = MutableStateFlow(false)
    val scanning = MutableStateFlow(false)
    val scanPhase = MutableStateFlow("")
    val scanFolders = MutableStateFlow(0)
    val scanFound = MutableStateFlow(0)
    val stats = MutableStateFlow<ScanStats?>(null)
    val loading = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val hasPermission = MutableStateFlow(false)
    val needWriteSettings = MutableStateFlow(false)
    val deleteRequest = MutableStateFlow<IntentSender?>(null)
    val playingKey = MutableStateFlow<String?>(null)
    val isPlaying = MutableStateFlow(false)
    val positionMs = MutableStateFlow(0)
    val durationMs = MutableStateFlow(0)
    val isDay = MutableStateFlow(false)
    val themeProgress = MutableStateFlow(0f)

    private val prefs = app.getSharedPreferences("ringtones", Context.MODE_PRIVATE)
    private val aliasPrefs = app.getSharedPreferences("aliases", Context.MODE_PRIVATE)
    private val resolver get() = getApplication<Application>().contentResolver
    private val themeAnim: Animatable<Float, *>
    private var player: MediaPlayer? = null
    private var ticker: Job? = null
    private var scanJob: Job? = null
    private var prepared = false
    private var queue: List<String> = emptyList()

    private val audioExt = setOf(
        "mp3", "m4a", "m4b", "aac", "ogg", "oga", "opus", "wav", "flac", "amr", "awb",
        "mid", "midi", "imy", "ota", "rtttl", "rtx", "wma", "mka", "aiff", "aif", "ape", "3ga", "mp2", "ac3", "caf"
    )
    private val skipDirs = setOf("android", "cache", "caches", "node_modules", "lost+found", ".thumbnails", "temp", "tmp")

    init {
        val night = (app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val day = prefs.getBoolean("day", !night)
        isDay.value = day
        themeAnim = Animatable(if (day) 1f else 0f)
        themeProgress.value = themeAnim.value
        favorites.value = prefs.getStringSet("fav", emptySet()) ?: emptySet()
        sort.value = prefs.getInt("sort", 0)
        minDuration.value = prefs.getInt("minDur", 0)
        scanFs.value = prefs.getBoolean("scanFs", true)
        hideSystem.value = prefs.getBoolean("hideSystem", true)
        showPaths.value = prefs.getBoolean("showPaths", true)
        volumeLevel.value = prefs.getInt("vol", 80)
        muted.value = prefs.getBoolean("muted", false)
        repeat.value = prefs.getBoolean("repeat", false)
        aliases.value = aliasPrefs.all.mapNotNull { e -> (e.value as? String)?.let { e.key to it } }.toMap()
        hasPermission.value = checkPermission()
        if (hasPermission.value) quickRefresh()
    }

    // ---------- тема ----------

    fun setDay(day: Boolean) {
        isDay.value = day
        prefs.edit().putBoolean("day", day).apply()
        viewModelScope.launch {
            themeAnim.animateTo(if (day) 1f else 0f, tween(680, easing = FastOutSlowInEasing)) {
                themeProgress.value = value
            }
        }
    }

    suspend fun dragTheme(f: Float) {
        themeAnim.snapTo(f.coerceIn(0f, 1f))
        themeProgress.value = themeAnim.value
    }

    // ---------- настройки ----------

    fun setSort(v: Int) { sort.value = v; prefs.edit().putInt("sort", v).apply() }
    fun setMinDuration(v: Int) { minDuration.value = v; prefs.edit().putInt("minDur", v).apply() }
    fun setScanFs(v: Boolean) { scanFs.value = v; prefs.edit().putBoolean("scanFs", v).apply() }
    fun setHideSystem(v: Boolean) { hideSystem.value = v; prefs.edit().putBoolean("hideSystem", v).apply() }
    fun setShowPaths(v: Boolean) { showPaths.value = v; prefs.edit().putBoolean("showPaths", v).apply() }
    fun setVolume(v: Int) {
        volumeLevel.value = v
        prefs.edit().putInt("vol", v).apply()
        applyVolume()
    }
    fun setMuted(v: Boolean) {
        muted.value = v
        prefs.edit().putBoolean("muted", v).apply()
        applyVolume()
    }
    fun setRepeat(v: Boolean) { repeat.value = v; prefs.edit().putBoolean("repeat", v).apply() }

    private fun applyVolume() {
        val level = if (muted.value) 0f else volumeLevel.value / 100f
        runCatching { player?.setVolume(level, level) }
    }

    fun displayTitle(s: Song): String = aliases.value[s.key] ?: s.title

    fun setAlias(key: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) aliasPrefs.edit().remove(key).apply()
        else aliasPrefs.edit().putString(key, trimmed).apply()
        aliases.value = aliasPrefs.all.mapNotNull { e -> (e.value as? String)?.let { e.key to it } }.toMap()
        message.value = if (trimmed.isEmpty()) "Имя сброшено" else "Переименовано"
    }

    fun clearAliases() {
        aliasPrefs.edit().clear().apply()
        aliases.value = emptyMap()
        message.value = "Все имена сброшены"
    }

    fun toggleFavorite(key: String) {
        val next = if (key in favorites.value) favorites.value - key else favorites.value + key
        favorites.value = next
        prefs.edit().putStringSet("fav", next).apply()
    }

    fun clearFavorites() {
        favorites.value = emptySet()
        prefs.edit().putStringSet("fav", emptySet()).apply()
        message.value = "Избранное очищено"
    }

    // ---------- разрешения ----------

    fun checkPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_AUDIO
        else android.Manifest.permission.READ_EXTERNAL_STORAGE
        return getApplication<Application>().checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    fun onPermissionResult() {
        hasPermission.value = checkPermission()
        if (hasPermission.value) quickRefresh()
    }

    // ---------- быстрое чтение медиатеки ----------

    fun quickRefresh() {
        if (!checkPermission()) { hasPermission.value = false; return }
        hasPermission.value = true
        viewModelScope.launch {
            loading.value = true
            val list = withContext(Dispatchers.IO) {
                val map = LinkedHashMap<String, Song>()
                volumeNames().forEach { v ->
                    runCatching { queryAudio(v) }.getOrNull()?.forEach { map[it.path.lowercase()] = it }
                }
                map.values.toList()
            }
            if (list.isNotEmpty() || songs.value.isEmpty()) songs.value = list
            loading.value = false
        }
    }

    private fun volumeNames(): List<String> =
        if (Build.VERSION.SDK_INT >= 29) runCatching { MediaStore.getExternalVolumeNames(getApplication()).toList() }.getOrDefault(listOf("external"))
        else listOf("external")

    private fun audioUri(volume: String): Uri =
        if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.getContentUri(volume)
        else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    private fun filesUri(volume: String): Uri =
        if (Build.VERSION.SDK_INT >= 29) MediaStore.Files.getContentUri(volume)
        else MediaStore.Files.getContentUri("external")

    private fun queryAudio(volume: String): List<Song> {
        val base = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.IS_RINGTONE, MediaStore.Audio.Media.IS_NOTIFICATION, MediaStore.Audio.Media.IS_ALARM
        )
        val projection = if (Build.VERSION.SDK_INT >= 29) base + MediaStore.Audio.Media.OWNER_PACKAGE_NAME else base
        val out = ArrayList<Song>()
        resolver.query(audioUri(volume), projection, null, null, null)?.use { c ->
            val ownerIdx = c.getColumnIndex(MediaStore.Audio.Media.OWNER_PACKAGE_NAME)
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val path = c.getString(4) ?: continue
                val display = c.getString(5) ?: File(path).name
                val artist = c.getString(2)
                out.add(
                    Song(
                        key = path, id = id,
                        uri = ContentUris.withAppendedId(audioUri(volume), id),
                        path = path,
                        title = c.getString(1)?.takeIf { it.isNotBlank() } ?: display.substringBeforeLast('.'),
                        artist = if (artist.isNullOrBlank() || artist == "<unknown>") "Без тегов" else artist,
                        durationMs = c.getLong(3), sizeBytes = c.getLong(6),
                        folder = File(path).parentFile?.name ?: "/", volume = volume,
                        owner = if (ownerIdx >= 0) c.getString(ownerIdx) else null,
                        isRingtone = c.getInt(8) != 0, isNotification = c.getInt(9) != 0, isAlarm = c.getInt(10) != 0,
                        indexed = true, dateAdded = c.getLong(7)
                    )
                )
            }
        }
        return out
    }

    private fun queryFiles(volume: String, known: Set<String>): List<Pair<String, Long>> {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME, MediaStore.Files.FileColumns.SIZE
        )
        val selection = "mime_type LIKE 'audio/%' OR mime_type = 'application/ogg'"
        val out = ArrayList<Pair<String, Long>>()
        resolver.query(filesUri(volume), projection, selection, null, null)?.use { c ->
            while (c.moveToNext()) {
                val path = c.getString(1) ?: continue
                if (known.contains(path.lowercase())) continue
                out.add(path to c.getLong(3))
            }
        }
        return out
    }

    private fun storageRoots(): List<File> {
        val roots = LinkedHashSet<File>()
        runCatching { roots.add(Environment.getExternalStorageDirectory()) }
        runCatching {
            getApplication<Application>().getExternalFilesDirs(null)?.forEach { f ->
                if (f != null) {
                    val p = f.absolutePath
                    val i = p.indexOf("/Android/data")
                    if (i > 0) roots.add(File(p.substring(0, i)))
                }
            }
        }
        runCatching {
            File("/storage").listFiles()?.forEach { f ->
                if (f.isDirectory && f.name.matches(Regex("[0-9A-Za-z]{4}-[0-9A-Za-z]{4}"))) roots.add(f)
            }
        }
        return roots.filter { it.isDirectory && it.canRead() }
    }

    private fun readMeta(path: String): Triple<Long, String?, String?> {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(path)
            val d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            Triple(d, mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE), mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST))
        } catch (e: Exception) {
            Triple(0L, null, null)
        } finally {
            runCatching { mmr.release() }
        }
    }

    private fun diskSong(path: String, size: Long, meta: Triple<Long, String?, String?>): Song {
        val f = File(path)
        return Song(
            key = path, id = -(kotlin.math.abs(path.hashCode().toLong()) + 1L), uri = null, path = path,
            title = meta.second?.takeIf { it.isNotBlank() } ?: f.nameWithoutExtension,
            artist = meta.third?.takeIf { it.isNotBlank() } ?: "Без тегов",
            durationMs = meta.first, sizeBytes = if (size > 0) size else f.length(),
            folder = f.parentFile?.name ?: "/", volume = "диск", owner = null,
            isRingtone = false, isNotification = false, isAlarm = false, indexed = false,
            dateAdded = f.lastModified() / 1000L
        )
    }

    // ---------- глобальный поиск ----------

    fun deepScan() {
        if (scanning.value) return
        if (!checkPermission()) { hasPermission.value = false; needWriteSettings.value = false; message.value = "Нет доступа к аудио"; return }
        scanJob = viewModelScope.launch {
            scanning.value = true
            scanFolders.value = 0
            scanFound.value = 0
            scanPhase.value = "Подготовка"
            val started = System.currentTimeMillis()
            try {
                val result = withContext(Dispatchers.IO) {
                    val map = LinkedHashMap<String, Song>()
                    val volumes = volumeNames()
                    volumes.forEach { v ->
                        scanPhase.value = "Медиатека · $v"
                        runCatching { queryAudio(v) }.getOrNull()?.forEach { map[it.path.lowercase()] = it }
                        scanFound.value = map.size
                    }
                    val extra = ArrayList<Pair<String, Long>>()
                    volumes.forEach { v ->
                        scanPhase.value = "Файлы · $v"
                        runCatching { queryFiles(v, map.keys) }.getOrNull()?.forEach { p ->
                            if (extra.none { it.first == p.first }) extra.add(p)
                        }
                    }
                    var folders = 0
                    val disk = ArrayList<Pair<String, Long>>()
                    if (scanFs.value) {
                        val stack = ArrayDeque<File>()
                        storageRoots().forEach { stack.addLast(it) }
                        var visited = 0
                        while (stack.isNotEmpty() && visited < 30000 && isActive) {
                            val dir = stack.removeLast()
                            visited++
                            folders = visited
                            if (visited % 8 == 0) {
                                scanFolders.value = visited
                                scanPhase.value = "Папки · " + (dir.name.ifEmpty { "/" })
                                yield()
                            }
                            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
                            for (f in children) {
                                val name = f.name
                                if (f.isDirectory) {
                                    if (name.startsWith(".")) continue
                                    if (hideSystem.value && skipDirs.contains(name.lowercase())) continue
                                    stack.addLast(f)
                                } else {
                                    if (name.startsWith(".")) continue
                                    if (!audioExt.contains(f.extension.lowercase())) continue
                                    val k = f.absolutePath.lowercase()
                                    if (map.containsKey(k)) continue
                                    if (disk.none { it.first.lowercase() == k }) disk.add(f.absolutePath to f.length())
                                }
                            }
                        }
                        scanFolders.value = folders
                    }
                    val pending = extra + disk
                    pending.forEachIndexed { i, pair ->
                        if (!isActive) return@forEachIndexed
                        if (i % 6 == 0) {
                            scanPhase.value = "Теги · ${i + 1}/${pending.size}"
                            scanFound.value = map.size + i
                            yield()
                        }
                        val meta = if (i < 900) readMeta(pair.first) else Triple(0L, null, null)
                        map[pair.first.lowercase()] = diskSong(pair.first, pair.second, meta)
                    }
                    if (pending.isNotEmpty()) {
                        scanPhase.value = "Индексация"
                        runCatching {
                            pending.take(3000).chunked(180).forEach { chunk ->
                                MediaScannerConnection.scanFile(
                                    getApplication(), chunk.map { it.first }.toTypedArray(), null, null
                                )
                            }
                        }
                    }
                    val list = map.values.toList()
                    Triple(list, folders, volumes.size)
                }
                songs.value = result.first
                val diskOnly = result.first.count { !it.indexed }
                stats.value = ScanStats(
                    files = result.first.size, folders = result.second, volumes = result.third,
                    diskOnly = diskOnly, seconds = (System.currentTimeMillis() - started) / 1000f
                )
                scanFound.value = result.first.size
                scanPhase.value = "Готово"
                message.value = "Найдено ${result.first.size} аудиофайлов"
            } catch (e: Exception) {
                scanPhase.value = "Остановлено"
            } finally {
                scanning.value = false
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        scanning.value = false
        scanPhase.value = "Остановлено"
        message.value = "Сканирование остановлено"
    }

    fun indexUnindexed() {
        val paths = songs.value.filter { !it.indexed }.map { it.path }
        if (paths.isEmpty()) { message.value = "Все файлы уже в медиатеке"; return }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    paths.chunked(180).forEach {
                        MediaScannerConnection.scanFile(getApplication(), it.toTypedArray(), null, null)
                    }
                }
            }
            message.value = "Проиндексировано ${paths.size} файлов"
            delay(1200)
            quickRefresh()
        }
    }

    // ---------- плеер ----------

    fun play(song: Song, visible: List<Song>) {
        if (playingKey.value == song.key && player != null) {
            if (isPlaying.value) pause() else resume()
            return
        }
        queue = visible.map { it.key }
        stopPlayer()
        playingKey.value = song.key
        positionMs.value = 0
        durationMs.value = song.durationMs.toInt()
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            )
            val u = song.uri
            if (u != null) mp.setDataSource(getApplication(), u) else mp.setDataSource(song.path)
            mp.setOnPreparedListener {
                prepared = true
                durationMs.value = it.duration
                applyVolume()
                runCatching { it.start() }
                isPlaying.value = true
                startTicker()
            }
            mp.setOnCompletionListener {
                if (repeat.value) {
                    runCatching { it.seekTo(0); it.start() }
                    positionMs.value = 0
                } else {
                    val idx = queue.indexOf(song.key)
                    val nextKey = queue.getOrNull(idx + 1)
                    val nextSong = songs.value.firstOrNull { s -> s.key == nextKey }
                    if (nextSong != null) play(nextSong, songs.value.filter { q -> queue.contains(q.key) })
                    else { isPlaying.value = false; positionMs.value = 0; ticker?.cancel() }
                }
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

    fun skip(delta: Int) {
        val current = playingKey.value ?: return
        val idx = queue.indexOf(current)
        if (idx < 0) return
        val target = queue.getOrNull(idx + delta) ?: return
        val song = songs.value.firstOrNull { it.key == target } ?: return
        val keep = queue
        playingKey.value = null
        play(song, songs.value.filter { keep.contains(it.key) })
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

    fun seekFraction(f: Float) {
        val total = durationMs.value
        if (total <= 0) return
        val ms = (f.coerceIn(0f, 1f) * total).toInt()
        if (prepared) runCatching { player?.seekTo(ms) }
        positionMs.value = ms
    }

    fun stop() {
        stopPlayer()
        playingKey.value = null
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
                if (prepared && mp != null) positionMs.value = runCatching { mp.currentPosition }.getOrDefault(0)
                delay(240)
            }
        }
    }

    // ---------- установка мелодии ----------

    fun applyAs(song: Song, type: Int) {
        val app = getApplication<Application>()
        if (!Settings.System.canWrite(app)) { needWriteSettings.value = true; return }
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
            } else "Не удалось установить мелодию"
        }
    }

    private fun ensureSettable(song: Song, type: Int): Uri {
        val dir: String
        val column: String
        when (type) {
            RingtoneManager.TYPE_NOTIFICATION -> { dir = Environment.DIRECTORY_NOTIFICATIONS; column = MediaStore.Audio.Media.IS_NOTIFICATION }
            RingtoneManager.TYPE_ALARM -> { dir = Environment.DIRECTORY_ALARMS; column = MediaStore.Audio.Media.IS_ALARM }
            else -> { dir = Environment.DIRECTORY_RINGTONES; column = MediaStore.Audio.Media.IS_RINGTONE }
        }
        if (Build.VERSION.SDK_INT >= 29 && song.uri != null) {
            if (song.owner == getApplication<Application>().packageName) return song.uri
            if (type == RingtoneManager.TYPE_RINGTONE && song.isRingtone) return song.uri
            if (type == RingtoneManager.TYPE_NOTIFICATION && song.isNotification) return song.uri
            if (type == RingtoneManager.TYPE_ALARM && song.isAlarm) return song.uri
        }
        if (Build.VERSION.SDK_INT < 29 && song.uri != null) return song.uri

        val key = "copy_" + song.key.hashCode() + "_" + type
        val cached = prefs.getLong(key, -1L)
        if (cached > 0) {
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cached)
            val ok = runCatching {
                resolver.query(uri, arrayOf(MediaStore.Audio.Media._ID), null, null, null)?.use { it.moveToFirst() } ?: false
            }.getOrDefault(false)
            if (ok) return uri
        }
        val mime = mimeOf(song)
        val target = insertCopy(displayTitle(song), dir, column, mime) { openSource(song) }
        prefs.edit().putLong(key, ContentUris.parseId(target)).apply()
        return target
    }

    private fun mimeOf(song: Song): String {
        val fromResolver = song.uri?.let { runCatching { resolver.getType(it) }.getOrNull() }
        if (fromResolver != null) return fromResolver
        val ext = File(song.path).extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "audio/mpeg"
    }

    private fun openSource(song: Song): InputStream? =
        if (song.uri != null) runCatching { resolver.openInputStream(song.uri) }.getOrNull()
        else runCatching { FileInputStream(song.path) }.getOrNull()

    private fun insertCopy(title: String, dir: String, column: String, mime: String, open: () -> InputStream?): Uri {
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "mp3"
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, safeName(title) + "." + ext)
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
            put(column, 1)
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Audio.Media.RELATIVE_PATH, dir)
        }
        val target = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Не удалось создать файл")
        try {
            val input = open() ?: throw IllegalStateException("Источник недоступен")
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

    // ---------- импорт ----------

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
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val mime = resolver.getType(source) ?: "audio/mpeg"
                    if (Build.VERSION.SDK_INT >= 29) {
                        insertCopy(title, Environment.DIRECTORY_RINGTONES, MediaStore.Audio.Media.IS_RINGTONE, mime) {
                            resolver.openInputStream(source)
                        }
                    } else {
                        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "mp3"
                        @Suppress("DEPRECATION")
                        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES)
                        dir.mkdirs()
                        val file = File(dir, safeName(title) + "." + ext)
                        val input = resolver.openInputStream(source) ?: throw IllegalStateException("Источник недоступен")
                        input.use { i -> FileOutputStream(file).use { o -> i.copyTo(o) } }
                        MediaScannerConnection.scanFile(app, arrayOf(file.absolutePath), arrayOf(mime), null)
                        Uri.fromFile(file)
                    }
                }
            }
            loading.value = false
            if (result.isSuccess) { message.value = "Файл добавлен"; delay(700); quickRefresh() }
            else message.value = "Не удалось добавить файл"
        }
    }

    // ---------- удаление ----------

    fun delete(song: Song) {
        val u = song.uri
        if (u != null) {
            if (Build.VERSION.SDK_INT >= 30) {
                runCatching {
                    deleteRequest.value = MediaStore.createDeleteRequest(resolver, listOf(u)).intentSender
                }.onFailure { message.value = "Не удалось удалить файл" }
                return
            }
            viewModelScope.launch {
                val r = withContext(Dispatchers.IO) { runCatching { resolver.delete(u, null, null) } }
                r.onSuccess {
                    if (playingKey.value == song.key) stop()
                    message.value = "Файл удалён"
                    quickRefresh()
                }
                r.onFailure { e ->
                    if (Build.VERSION.SDK_INT >= 29 && e is android.app.RecoverableSecurityException) {
                        deleteRequest.value = e.userAction.actionIntent.intentSender
                    } else message.value = "Не удалось удалить файл"
                }
            }
            return
        }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { File(song.path).delete() }.getOrDefault(false) }
            if (ok) {
                if (playingKey.value == song.key) stop()
                songs.value = songs.value.filter { it.key != song.key }
                message.value = "Файл удалён"
            } else message.value = "Файл защищён системой. Проиндексируйте его и удалите снова."
        }
    }

    override fun onCleared() {
        stopPlayer()
        scanJob?.cancel()
        super.onCleared()
    }
}
