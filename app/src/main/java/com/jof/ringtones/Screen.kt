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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale

private fun formatTime(ms: Long): String {
    val total = ms / 1000
    return String.format(Locale.getDefault(), "%d:%02d", total / 60, total % 60)
}

@Composable
fun Screen(vm: RingtoneViewModel = viewModel()) {
    val context = LocalContext.current

    val songs by vm.songs.collectAsState()
    val loading by vm.loading.collectAsState()
    val message by vm.message.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val query by vm.query.collectAsState()
    val onlyFavorites by vm.onlyFavorites.collectAsState()
    val hasPermission by vm.hasPermission.collectAsState()
    val needWriteSettings by vm.needWriteSettings.collectAsState()
    val deleteRequest by vm.deleteRequest.collectAsState()
    val playingId by vm.playingId.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    val positionMs by vm.positionMs.collectAsState()
    val durationMs by vm.durationMs.collectAsState()

    var actionsFor by remember { mutableStateOf<Song?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.onPermissionResult() }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { vm.onPermissionResult() }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vm.message.value = "Файл удалён"
            vm.refresh()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.importFile(uri) }

    LaunchedEffect(message) {
        val text = message
        if (text != null) {
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            vm.message.value = null
        }
    }

    LaunchedEffect(deleteRequest) {
        val request = deleteRequest
        if (request != null) {
            runCatching { deleteLauncher.launch(IntentSenderRequest.Builder(request).build()) }
            vm.deleteRequest.value = null
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Рингтоны",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { vm.refresh() }) {
                Icon(Icons.Rounded.Refresh, "Обновить")
            }
            IconButton(onClick = { runCatching { importLauncher.launch(arrayOf("audio/*")) } }) {
                Icon(Icons.Rounded.Add, "Добавить файл")
            }
        }

        if (!hasPermission) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Rounded.MusicNote,
                    null,
                    Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(16.dp))
                Text("Нужен доступ к аудио", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Разрешите доступ к музыке и аудио, чтобы увидеть список мелодий. Файлы никуда не отправляются.",
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = { permissionLauncher.launch(readPermissions()) }) {
                    Text("Разрешить доступ")
                }
            }
        } else {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                placeholder = { Text("Поиск по названию или исполнителю") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!onlyFavorites) {
                    Button(onClick = { vm.onlyFavorites.value = false }) { Text("Все") }
                } else {
                    OutlinedButton(onClick = { vm.onlyFavorites.value = false }) { Text("Все") }
                }
                if (onlyFavorites) {
                    Button(onClick = { vm.onlyFavorites.value = true }) { Text("Избранное") }
                } else {
                    OutlinedButton(onClick = { vm.onlyFavorites.value = true }) { Text("Избранное") }
                }
            }

            val visible = songs.filter { song ->
                (!onlyFavorites || song.id in favorites) &&
                    (query.isBlank() ||
                        song.title.contains(query, true) ||
                        song.artist.contains(query, true))
            }

            if (loading && songs.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (visible.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Список пуст. Добавьте файл кнопкой «+» сверху.")
                }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(visible, key = { it.id }) { song ->
                        val active = playingId == song.id
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.play(song) }
                                .background(
                                    if (active) MaterialTheme.colorScheme.secondaryContainer
                                    else Color.Transparent
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (active && isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    song.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    song.artist + " • " + formatTime(song.durationMs),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { vm.toggleFavorite(song.id) }) {
                                Icon(
                                    if (song.id in favorites) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    "Избранное",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { actionsFor = song }) {
                                Icon(Icons.Rounded.MoreVert, "Действия")
                            }
                        }
                    }
                }
            }

            if (playingId != null) {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                songs.firstOrNull { it.id == playingId }?.title ?: "",
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(onClick = { if (isPlaying) vm.pause() else vm.resume() }) {
                                Icon(
                                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    "Пуск/пауза"
                                )
                            }
                            IconButton(onClick = { vm.stop() }) {
                                Icon(Icons.Rounded.Close, "Закрыть плеер")
                            }
                        }
                        val max = durationMs.coerceAtLeast(1).toFloat()
                        var dragging by remember { mutableStateOf<Float?>(null) }
                        Slider(
                            value = (dragging ?: positionMs.toFloat()).coerceIn(0f, max),
                            onValueChange = { dragging = it },
                            onValueChangeFinished = {
                                val target = dragging
                                if (target != null) vm.seekTo(target.toInt())
                                dragging = null
                            },
                            valueRange = 0f..max,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    val actionSong = actionsFor
    if (actionSong != null) {
        AlertDialog(
            onDismissRequest = { actionsFor = null },
            title = { Text(actionSong.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            actionsFor = null
                            vm.applyAs(actionSong, RingtoneManager.TYPE_RINGTONE)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Установить как рингтон", modifier = Modifier.fillMaxWidth()) }

                    TextButton(
                        onClick = {
                            actionsFor = null
                            vm.applyAs(actionSong, RingtoneManager.TYPE_NOTIFICATION)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Звук уведомления", modifier = Modifier.fillMaxWidth()) }

                    TextButton(
                        onClick = {
                            actionsFor = null
                            vm.applyAs(actionSong, RingtoneManager.TYPE_ALARM)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Сигнал будильника", modifier = Modifier.fillMaxWidth()) }

                    TextButton(
                        onClick = {
                            actionsFor = null
                            vm.delete(actionSong)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Удалить с устройства",
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionsFor = null }) { Text("Закрыть") }
            }
        )
    }

    if (needWriteSettings) {
        AlertDialog(
            onDismissRequest = { vm.needWriteSettings.value = false },
            title = { Text("Нужно разрешение") },
            text = {
                Text(
                    "Чтобы менять рингтон, разрешите приложению изменять системные настройки. " +
                        "Включите переключатель, вернитесь назад кнопкой «Назад» и нажмите «Установить» ещё раз."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.needWriteSettings.value = false
                    runCatching {
                        settingsLauncher.launch(
                            Intent(
                                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                Uri.parse("package:" + context.packageName)
                            )
                        )
                    }
                }) { Text("Открыть настройки") }
            },
            dismissButton = {
                TextButton(onClick = { vm.needWriteSettings.value = false }) { Text("Отмена") }
            }
        )
    }
}
