package com.jof.ringtones

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private fun fmtTime(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.getDefault(), "%d:%02d", t / 60, t % 60)
}

private fun fmtSize(b: Long): String = when {
    b >= 1073741824L -> String.format(Locale.getDefault(), "%.1f ГБ", b / 1073741824.0)
    b >= 1048576L -> String.format(Locale.getDefault(), "%.1f МБ", b / 1048576.0)
    b > 0 -> String.format(Locale.getDefault(), "%.0f КБ", b / 1024.0)
    else -> "—"
}

private fun fmtTotal(ms: Long): String {
    val m = ms / 60000
    return if (m >= 60) "${m / 60} ч ${m % 60} м" else "$m мин"
}

@Composable
fun Screen(vm: RingtoneViewModel) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val songs by vm.songs.collectAsState()
    val aliases by vm.aliases.collectAsState()
    val favs by vm.favorites.collectAsState()
    val tab by vm.tab.collectAsState()
    val lq by vm.libraryQuery.collectAsState()
    val sq by vm.searchQuery.collectAsState()
    val sort by vm.sort.collectAsState()
    val filter by vm.filter.collectAsState()
    val scopeF by vm.searchScope.collectAsState()
    val minDur by vm.minDuration.collectAsState()
    val scanFs by vm.scanFs.collectAsState()
    val hideSys by vm.hideSystem.collectAsState()
    val showPaths by vm.showPaths.collectAsState()
    val vol by vm.volumeLevel.collectAsState()
    val muted by vm.muted.collectAsState()
    val rep by vm.repeat.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val phase by vm.scanPhase.collectAsState()
    val nFolders by vm.scanFolders.collectAsState()
    val nFound by vm.scanFound.collectAsState()
    val stats by vm.stats.collectAsState()
    val loading by vm.loading.collectAsState()
    val msg by vm.message.collectAsState()
    val perm by vm.hasPermission.collectAsState()
    val needSet by vm.needWriteSettings.collectAsState()
    val delReq by vm.deleteRequest.collectAsState()
    val pickReq by vm.contactPickerRequest.collectAsState()
    val cPermReq by vm.contactsPermissionRequest.collectAsState()
    val playKey by vm.playingKey.collectAsState()
    val playing by vm.isPlaying.collectAsState()
    val pos by vm.positionMs.collectAsState()
    val dur by vm.durationMs.collectAsState()
    val p by vm.themeProgress.collectAsState()

    var actFor by remember { mutableStateOf<Song?>(null) }
    var renFor by remember { mutableStateOf<Song?>(null) }
    var renText by remember { mutableStateOf("") }
    var sortDlg by remember { mutableStateOf(false) }
    var clearDlg by remember { mutableStateOf(false) }
    val time by rememberInfiniteTransition(label = "ui").animateFloat(
        0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart), label = "t")

    val permL = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.onPermissionResult() }
    val setL = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.onPermissionResult() }
    val delL = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == Activity.RESULT_OK) { vm.message.value = "Файл удалён"; vm.quickRefresh() }
    }
    val impL = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { if (it != null) vm.importFile(it) }
    val pickL = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { vm.onContactPicked(it) }
    val cPermL = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r -> vm.onContactsPermission(r.values.all { it }) }

    LaunchedEffect(msg) { val t = msg; if (t != null) { Toast.makeText(ctx, t, Toast.LENGTH_LONG).show(); vm.message.value = null } }
    LaunchedEffect(delReq) { val r = delReq; if (r != null) { runCatching { delL.launch(IntentSenderRequest.Builder(r).build()) }; vm.deleteRequest.value = null } }
    LaunchedEffect(pickReq) { if (pickReq) { runCatching { pickL.launch(null) }; vm.contactPickerRequest.value = false } }
    LaunchedEffect(cPermReq) {
        if (cPermReq) {
            runCatching { cPermL.launch(arrayOf(android.Manifest.permission.READ_CONTACTS, android.Manifest.permission.WRITE_CONTACTS)) }
            vm.contactsPermissionRequest.value = false
        }
    }

    fun title(s: Song) = aliases[s.key] ?: s.title
    fun srt(l: List<Song>) = when (sort) {
        1 -> l.sortedByDescending { it.durationMs }
        2 -> l.sortedByDescending { it.dateAdded }
        3 -> l.sortedWith(compareBy({ it.folder.lowercase() }, { title(it).lowercase() }))
        else -> l.sortedBy { title(it).lowercase() }
    }
    val minMs = minDur * 1000L
    val lib = remember(songs, aliases, sort, filter, lq, minMs) {
        srt(songs.filter { s -> s.durationMs >= minMs &&
            (filter != 1 || s.isRingtone || s.isNotification || s.isAlarm) &&
            (filter != 2 || s.durationMs >= 60000) && (filter != 3 || s.artist == "Без тегов") &&
            (lq.isBlank() || title(s).contains(lq, true) || s.artist.contains(lq, true) || s.folder.contains(lq, true)) })
    }
    val res = remember(songs, aliases, sort, scopeF, sq, minMs) {
        srt(songs.filter { s -> s.durationMs >= minMs &&
            (scopeF != 1 || s.durationMs >= 15000) && (scopeF != 2 || s.artist == "Без тегов") && (scopeF != 3 || !s.indexed) &&
            (sq.isBlank() || title(s).contains(sq, true) || s.artist.contains(sq, true) || s.path.contains(sq, true)) })
    }
    val fav = remember(songs, aliases, sort, favs) { srt(songs.filter { it.key in favs }) }
    val nFold = remember(songs) { songs.map { it.folder }.distinct().size }
    val totMs = remember(songs) { songs.sumOf { it.durationMs } }
    val unidx = remember(songs) { songs.count { !it.indexed } }
    val cur = songs.firstOrNull { it.key == playKey }

    Box(Modifier.fillMaxSize()) {
        SkyBackground(p, time, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Рингтоны", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = pal.text)
                    Text("${songs.size} треков · $nFold папок · " + (if (scanning) "сканирование…" else if (p < .5f) "ночь" else "день"), fontSize = 11.sp, color = pal.textDim)
                }
                IconButton(onClick = { vm.setMuted(!muted) }) { Icon(if (muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp, "Звук", tint = if (muted) pal.danger else pal.secondary) }
                IconButton(onClick = { runCatching { impL.launch(arrayOf("audio/*")) } }) { Icon(Icons.Rounded.Add, "Добавить", tint = pal.primary) }
                Spacer(Modifier.width(6.dp))
                ThemeSwitch(p, 104.dp, { f -> scope.launch { vm.dragTheme(f) } }, { vm.setDay(it) })
            }
            Box(Modifier.weight(1f)) {
                if (!perm) {
                    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Radar(time, Modifier.size(150.dp)); Spacer(Modifier.height(18.dp))
                        Text("Нужен доступ к аудио", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = pal.text)
                        Spacer(Modifier.height(8.dp))
                        Text("Приложение ищет мелодии по всем папкам устройства и ничего не отправляет в сеть.", textAlign = TextAlign.Center, color = pal.textDim, fontSize = 13.sp)
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = { permL.launch(readPermissions()) }) { Text("Разрешить доступ") }
                    }
                } else when (tab) {
                    0 -> {
                        val ls = rememberLazyListState()
                        Column(Modifier.fillMaxSize()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatTile(songs.size.toString(), "треков", pal.primary, Modifier.weight(1f))
                                StatTile(fmtTotal(totMs), "длительность", pal.secondary, Modifier.weight(1f))
                                StatTile(nFold.toString(), "папок", pal.tertiary, Modifier.weight(1f))
                            }
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(lq, { vm.libraryQuery.value = it }, placeholder = { Text("Фильтр: название, тег, папка", fontSize = 13.sp) }, singleLine = true,
                                    leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(18.dp)) }, modifier = Modifier.weight(1f).height(56.dp))
                                IconButton(onClick = { sortDlg = true }) { Icon(Icons.Rounded.Sort, "Сортировка", tint = pal.primary) }
                                IconButton(onClick = { vm.quickRefresh() }) { Icon(Icons.Rounded.Refresh, "Обновить", tint = pal.secondary) }
                            }
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Chip("Все", filter == 0, { vm.filter.value = 0 })
                                Chip("Рингтоны", filter == 1, { vm.filter.value = 1 }, Icons.Rounded.RingVolume)
                                Chip("Длинные", filter == 2, { vm.filter.value = 2 })
                                Chip("Без тегов", filter == 3, { vm.filter.value = 3 })
                            }
                            Spacer(Modifier.height(6.dp))
                            if (loading && songs.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = pal.primary) }
                            else if (lib.isEmpty()) EmptyHint("Ничего не найдено", "Измените фильтр или выполните глобальный поиск во вкладке «Поиск».")
                            else Box(Modifier.weight(1f)) {
                                LazyColumn(ls, Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp, 4.dp, 18.dp, 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(lib, key = { it.key }) { s ->
                                        SongRow(s, title(s), playKey == s.key, playing, if (playKey == s.key && dur > 0) pos / dur.toFloat() else 0f, s.key in favs, showPaths, time,
                                            { vm.play(s, lib) }, { vm.toggleFavorite(s.key) }, { actFor = s })
                                    }
                                }
                                ScrollThumb(ls, lib.size, Modifier.align(Alignment.CenterEnd).width(4.dp).fillMaxHeight().padding(vertical = 10.dp))
                            }
                        }
                    }
                    1 -> {
                        val ls = rememberLazyListState()
                        Column(Modifier.fillMaxSize()) {
                            Panel(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                                SectionLabel("Глобальный поиск", if (scanning) phase else "все тома и папки")
                                OutlinedTextField(sq, { vm.searchQuery.value = it }, placeholder = { Text("Название, тег или путь: Download, Telegram…", fontSize = 13.sp) }, singleLine = true,
                                    leadingIcon = { Icon(Icons.Rounded.TravelExplore, null, Modifier.size(18.dp)) },
                                    trailingIcon = { if (sq.isNotEmpty()) IconButton(onClick = { vm.searchQuery.value = "" }) { Icon(Icons.Rounded.Close, "Очистить", Modifier.size(18.dp)) } },
                                    modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (scanning) Button({ vm.cancelScan() }, Modifier.weight(1f)) { Icon(Icons.Rounded.Stop, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Остановить") }
                                    else Button({ vm.deepScan() }, Modifier.weight(1f)) { Icon(Icons.Rounded.TravelExplore, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Искать везде") }
                                    Spacer(Modifier.width(8.dp))
                                    IconButton(onClick = { vm.indexUnindexed() }) { Icon(Icons.Rounded.SaveAlt, "Индекс", tint = pal.secondary) }
                                }
                                if (scanning) {
                                    Spacer(Modifier.height(10.dp)); IndeterminateBar(time, Modifier.fillMaxWidth().height(6.dp)); Spacer(Modifier.height(6.dp))
                                    Text("$phase · папок: $nFolders · найдено: $nFound", fontSize = 11.sp, color = pal.textDim)
                                }
                                Spacer(Modifier.height(10.dp))
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Chip("Всё", scopeF == 0, { vm.searchScope.value = 0 })
                                    Chip("≥ 15 c", scopeF == 1, { vm.searchScope.value = 1 })
                                    Chip("Без тегов", scopeF == 2, { vm.searchScope.value = 2 })
                                    Chip("Вне медиатеки", scopeF == 3, { vm.searchScope.value = 3 })
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            val st = stats
                            if (st != null) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StatTile(st.files.toString(), "файлов", pal.primary, Modifier.weight(1f))
                                    StatTile(st.folders.toString(), "папок", pal.secondary, Modifier.weight(1f))
                                    StatTile(st.volumes.toString(), "томов", pal.tertiary, Modifier.weight(1f))
                                    StatTile(String.format(Locale.getDefault(), "%.1f с", st.seconds), "скан", pal.success, Modifier.weight(1f))
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            if (unidx > 0) {
                                Panel(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Warning, null, Modifier.size(18.dp), tint = pal.warning); Spacer(Modifier.width(8.dp))
                                        Text("$unidx файлов вне медиатеки", fontSize = 12.sp, color = pal.text, modifier = Modifier.weight(1f))
                                        TextButton(onClick = { vm.indexUnindexed() }) { Text("Индекс") }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            if (res.isEmpty()) Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Radar(time, Modifier.size(160.dp)); Spacer(Modifier.height(16.dp))
                                Text("Поиск по всему устройству", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = pal.text)
                                Spacer(Modifier.height(6.dp))
                                Text("Сканируются все тома MediaStore и папки памяти: Download, Music, Telegram, WhatsApp, Recordings и другие. Найденные файлы читаются по тегам и добавляются в медиатеку.", fontSize = 12.sp, color = pal.textDim, textAlign = TextAlign.Center)
                            } else Box(Modifier.weight(1f)) {
                                LazyColumn(ls, Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp, 0.dp, 18.dp, 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(res, key = { it.key }) { s ->
                                        SongRow(s, title(s), playKey == s.key, playing, if (playKey == s.key && dur > 0) pos / dur.toFloat() else 0f, s.key in favs, true, time,
                                            { vm.play(s, res) }, { vm.toggleFavorite(s.key) }, { actFor = s })
                                    }
                                }
                                ScrollThumb(ls, res.size, Modifier.align(Alignment.CenterEnd).width(4.dp).fillMaxHeight().padding(vertical = 10.dp))
                            }
                        }
                    }
                    2 -> if (fav.isEmpty()) EmptyHint("Избранное пусто", "Нажмите на сердце у любой мелодии — она появится здесь.")
                    else {
                        val ls = rememberLazyListState()
                        Box(Modifier.fillMaxSize()) {
                            LazyColumn(ls, Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp, 8.dp, 18.dp, 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("${fav.size} в избранном", fontSize = 13.sp, color = pal.textDim, modifier = Modifier.weight(1f))
                                        TextButton(onClick = { clearDlg = true }) { Text("Очистить") }
                                    }
                                }
                                items(fav, key = { it.key }) { s ->
                                    SongRow(s, title(s), playKey == s.key, playing, if (playKey == s.key && dur > 0) pos / dur.toFloat() else 0f, true, showPaths, time,
                                        { vm.play(s, fav) }, { vm.toggleFavorite(s.key) }, { actFor = s })
                                }
                            }
                            ScrollThumb(ls, fav.size, Modifier.align(Alignment.CenterEnd).width(4.dp).fillMaxHeight().padding(vertical = 10.dp))
                        }
                    }
                    else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Panel(Modifier.fillMaxWidth()) {
                            SectionLabel("Оформление", if (p < .5f) "ночь" else "день")
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(if (p < .5f) "Ночная сцена" else "Дневная сцена", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = pal.text)
                                    Text("Перетащите светило пальцем — палитра и фон меняются в реальном времени.", fontSize = 11.sp, color = pal.textDim)
                                }
                                Spacer(Modifier.width(10.dp))
                                ThemeSwitch(p, 150.dp, { f -> scope.launch { vm.dragTheme(f) } }, { vm.setDay(it) })
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Chip("Ночь", p < .5f, { vm.setDay(false) }, Icons.Rounded.NightsStay)
                                Chip("День", p >= .5f, { vm.setDay(true) }, Icons.Rounded.WbSunny)
                            }
                        }
                        Panel(Modifier.fillMaxWidth()) {
                            SectionLabel("Поиск и сканирование")
                            SettingRow("Сканировать файловую систему", "Обход всех папок памяти", scanFs) { vm.setScanFs(it) }
                            SettingRow("Пропускать служебные папки", "Android, cache, temp, скрытые", hideSys) { vm.setHideSystem(it) }
                            SettingRow("Показывать папку в списке", "Под названием трека", showPaths) { vm.setShowPaths(it) }
                            Spacer(Modifier.height(8.dp))
                            Text("Минимальная длительность: $minDur с", fontSize = 13.sp, color = pal.text)
                            Slider(minDur.toFloat(), { vm.setMinDuration(it.toInt()) }, valueRange = 0f..60f, steps = 11,
                                colors = SliderDefaults.colors(thumbColor = pal.primary, activeTrackColor = pal.primary, inactiveTrackColor = pal.border))
                            Button({ vm.tab.value = 1; vm.deepScan() }, Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.TravelExplore, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Полное сканирование")
                            }
                        }
                        Panel(Modifier.fillMaxWidth()) {
                            SectionLabel("Прослушивание")
                            SettingRow("Без звука", "Отключает звук предпрослушивания", muted) { vm.setMuted(it) }
                            SettingRow("Повтор трека", "Иначе — переход к следующему", rep) { vm.setRepeat(it) }
                            Spacer(Modifier.height(8.dp))
                            Text("Громкость предпрослушивания: $vol%", fontSize = 13.sp, color = pal.text)
                            Slider(vol.toFloat(), { vm.setVolume(it.toInt()) }, valueRange = 0f..100f,
                                colors = SliderDefaults.colors(thumbColor = pal.secondary, activeTrackColor = pal.secondary, inactiveTrackColor = pal.border))
                        }
                        Panel(Modifier.fillMaxWidth()) {
                            SectionLabel("Сортировка и данные")
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Chip("Имя", sort == 0, { vm.setSort(0) }); Chip("Длина", sort == 1, { vm.setSort(1) })
                                Chip("Дата", sort == 2, { vm.setSort(2) }); Chip("Папка", sort == 3, { vm.setSort(3) })
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Chip("Очистить избранное", false, { clearDlg = true }, Icons.Rounded.FavoriteBorder)
                                Chip("Сбросить имена", false, { vm.clearAliases() }, Icons.Rounded.Edit)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Версия 2.1 · переименования хранятся локально и не меняют файлы. Избранное: ${favs.size}, псевдонимов: ${aliases.size}, вне медиатеки: $unidx.", fontSize = 11.sp, color = pal.textDim)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            val c = cur
            if (c != null) {
                val pr = if (dur > 0) pos / dur.toFloat() else 0f
                Panel(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (playing) Equalizer(time, pal.primary, Modifier.size(18.dp, 16.dp)) else Icon(Icons.Rounded.MusicNote, null, Modifier.size(18.dp), tint = pal.textDim)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(title(c), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = pal.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${c.artist} · ${c.folder}", fontSize = 10.sp, color = pal.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { vm.setRepeat(!rep) }) { Icon(Icons.Rounded.RepeatOne, "Повтор", Modifier.size(20.dp), tint = if (rep) pal.tertiary else pal.textDim) }
                        IconButton(onClick = { vm.skip(-1) }) { Icon(Icons.Rounded.SkipPrevious, "Назад", tint = pal.text) }
                        IconButton(onClick = { if (playing) vm.pause() else vm.resume() }) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Пуск", tint = pal.primary) }
                        IconButton(onClick = { vm.skip(1) }) { Icon(Icons.Rounded.SkipNext, "Вперёд", tint = pal.text) }
                        IconButton(onClick = { vm.stop() }) { Icon(Icons.Rounded.Close, "Закрыть", Modifier.size(20.dp), tint = pal.textDim) }
                    }
                    Waveform(c.key.hashCode().toLong(), pr, Modifier.fillMaxWidth().height(34.dp)
                        .pointerInput(dur) { detectTapGestures { vm.seekFraction(it.x / size.width) } }
                        .pointerInput(dur) { detectHorizontalDragGestures { ch, _ -> vm.seekFraction(ch.position.x / size.width) } })
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(fmtTime(pos.toLong()), fontSize = 10.sp, color = pal.textDim, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.applyAs(c, RingtoneManager.TYPE_RINGTONE) }) { Text("Сделать рингтоном", fontSize = 12.sp) }
                        Text(fmtTime(dur.toLong()), fontSize = 10.sp, color = pal.textDim)
                    }
                }
            }
            MenuBar(tab, listOf(songs.size, res.size, favs.size, 0)) { vm.tab.value = it }
        }
    }

    val a = actFor
    if (a != null) AlertDialog(onDismissRequest = { actFor = null },
        title = { Text(title(a), maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 17.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("${a.folder} · ${fmtTime(a.durationMs)} · ${fmtSize(a.sizeBytes)}" + (if (!a.indexed) " · вне медиатеки" else ""), fontSize = 11.sp, color = pal.textDim)
                Spacer(Modifier.height(6.dp))
                ActionRow(Icons.Rounded.RingVolume, "Установить как рингтон") { actFor = null; vm.applyAs(a, RingtoneManager.TYPE_RINGTONE) }
                ActionRow(Icons.Rounded.Notifications, "Звук уведомления") { actFor = null; vm.applyAs(a, RingtoneManager.TYPE_NOTIFICATION) }
                ActionRow(Icons.Rounded.Alarm, "Сигнал будильника") { actFor = null; vm.applyAs(a, RingtoneManager.TYPE_ALARM) }
                ActionRow(Icons.Rounded.Person, "Назначить контакту") { actFor = null; vm.assignToContact(a) }
                ActionRow(Icons.Rounded.Edit, "Переименовать") { renText = title(a); renFor = a; actFor = null }
                if (!a.indexed) ActionRow(Icons.Rounded.SaveAlt, "Добавить в медиатеку") { actFor = null; vm.indexUnindexed() }
                if (a.uri != null) ActionRow(Icons.Rounded.Share, "Поделиться") {
                    actFor = null
                    runCatching { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "audio/*"; putExtra(Intent.EXTRA_STREAM, a.uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Поделиться")) }
                }
                ActionRow(Icons.Rounded.Delete, "Удалить с устройства", true) { actFor = null; vm.delete(a) }
            }
        }, confirmButton = { TextButton(onClick = { actFor = null }) { Text("Закрыть") } })

    val r = renFor
    if (r != null) AlertDialog(onDismissRequest = { renFor = null }, title = { Text("Переименовать") },
        text = { Column {
            Text(File(r.path).name, fontSize = 11.sp, color = pal.textDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(renText, { renText = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        } },
        confirmButton = { TextButton(onClick = { vm.setAlias(r.key, renText); renFor = null }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = { vm.setAlias(r.key, ""); renFor = null }) { Text("Сбросить") } })

    if (sortDlg) AlertDialog(onDismissRequest = { sortDlg = false }, title = { Text("Сортировка") },
        text = { Column {
            ActionRow(Icons.Rounded.Sort, "По имени") { vm.setSort(0); sortDlg = false }
            ActionRow(Icons.Rounded.GraphicEq, "По длительности") { vm.setSort(1); sortDlg = false }
            ActionRow(Icons.Rounded.Schedule, "По дате") { vm.setSort(2); sortDlg = false }
            ActionRow(Icons.Rounded.FolderOpen, "По папке") { vm.setSort(3); sortDlg = false }
        } }, confirmButton = { TextButton(onClick = { sortDlg = false }) { Text("Закрыть") } })

    if (clearDlg) AlertDialog(onDismissRequest = { clearDlg = false }, title = { Text("Очистить избранное?") },
        text = { Text("Список избранного будет очищен. Сами файлы останутся на устройстве.") },
        confirmButton = { TextButton(onClick = { vm.clearFavorites(); clearDlg = false }) { Text("Очистить") } },
        dismissButton = { TextButton(onClick = { clearDlg = false }) { Text("Отмена") } })

    if (needSet) AlertDialog(onDismissRequest = { vm.needWriteSettings.value = false }, title = { Text("Нужно разрешение") },
        text = { Text("Разрешите приложению изменять системные настройки, вернитесь назад и повторите установку мелодии.") },
        confirmButton = { TextButton(onClick = {
            vm.needWriteSettings.value = false
            runCatching { setL.launch(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + ctx.packageName))) }
        }) { Text("Открыть настройки") } },
        dismissButton = { TextButton(onClick = { vm.needWriteSettings.value = false }) { Text("Отмена") } })
}

@Composable
private fun SettingRow(t: String, h: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val pal = LocalPal.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(t, fontSize = 13.sp, color = pal.text, fontWeight = FontWeight.Medium)
            Text(h, fontSize = 10.sp, color = pal.textDim)
        }
        Spacer(Modifier.width(10.dp)); MiniSwitch(checked, onChange)
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val pal = LocalPal.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 11.dp, horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(19.dp), tint = if (danger) pal.danger else pal.primary)
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 14.sp, color = if (danger) pal.danger else pal.text)
    }
}

@Composable
private fun EmptyHint(t: String, h: String) {
    val pal = LocalPal.current
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Rounded.MusicOff, null, Modifier.size(52.dp), tint = pal.border)
        Spacer(Modifier.height(12.dp))
        Text(t, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = pal.text)
        Spacer(Modifier.height(6.dp))
        Text(h, fontSize = 12.sp, color = pal.textDim, textAlign = TextAlign.Center)
    }
}

@Composable
private fun TagChip(text: String, tint: Color) {
    val s = RoundedCornerShape(50)
    Box(Modifier.clip(s).background(tint.copy(alpha = .18f)).border(1.dp, tint.copy(alpha = .55f), s).padding(horizontal = 7.dp, vertical = 1.dp)) {
        Text(text, fontSize = 9.sp, color = tint, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SongRow(
    song: Song, name: String, active: Boolean, playing: Boolean, progress: Float,
    favorite: Boolean, showPath: Boolean, time: Float,
    onPlay: () -> Unit, onFav: () -> Unit, onMenu: () -> Unit
) {
    val pal = LocalPal.current
    val sh = RoundedCornerShape(20.dp)
    Row(Modifier.fillMaxWidth().clip(sh)
        .background(if (active) Brush.horizontalGradient(listOf(pal.primary.copy(alpha = .22f), pal.tertiary.copy(alpha = .16f)))
            else Brush.horizontalGradient(listOf(pal.surfaceHigh.copy(alpha = .86f), pal.surface.copy(alpha = .78f))))
        .border(1.dp, if (active) pal.primary.copy(alpha = .55f) else pal.border, sh)
        .clickable(onClick = onPlay).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f - 2f
                drawCircle(pal.primary.copy(alpha = if (active) .22f else .12f), r, center)
                drawCircle(pal.border, r, center, style = Stroke(1.4f))
                if (active && progress > 0f) drawArc(pal.primary, -90f, 360f * progress.coerceIn(0f, 1f), false,
                    topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2f, r * 2f), style = Stroke(3f))
            }
            if (active && playing) Equalizer(time, pal.primary, Modifier.size(16.dp, 14.dp))
            else Icon(if (active) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Играть", Modifier.size(20.dp), tint = pal.primary)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = pal.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist + " · " + fmtTime(song.durationMs) + (if (showPath) " · " + song.folder else ""),
                fontSize = 10.sp, color = pal.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Waveform(song.key.hashCode().toLong(), if (active) progress else 0f, Modifier.fillMaxWidth().height(16.dp), 30)
            if (song.isRingtone || !song.indexed) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (song.isRingtone) TagChip("рингтон", pal.success)
                    if (!song.indexed) TagChip("вне медиатеки", pal.warning)
                }
            }
        }
        IconButton(onFav, Modifier.size(36.dp)) { Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Избранное", Modifier.size(19.dp), tint = if (favorite) pal.tertiary else pal.textDim) }
        IconButton(onMenu, Modifier.size(32.dp)) { Icon(Icons.Rounded.MoreVert, "Действия", Modifier.size(19.dp), tint = pal.textDim) }
    }
}
