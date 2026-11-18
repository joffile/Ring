val ren = renameFor
    if (ren != null) {
        AlertDialog(
            onDismissRequest = { renameFor = null },
            title = { Text("Переименовать") },
            text = {
                Column {
                    Text(File(ren.path).name, fontSize = 11.sp, color = LocalPal.current.textDim, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { vm.setAlias(ren.key, renameText); renameFor = null }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { vm.setAlias(ren.key, ""); renameFor = null }) { Text("Сбросить") } }
        )
    }

    if (showSort) {
        AlertDialog(
            onDismissRequest = { showSort = false },
            title = { Text("Сортировка") },
            text = {
                Column {
                    ActionRow(Icons.Rounded.Sort, "По имени") { vm.setSort(0); showSort = false }
                    ActionRow(Icons.Rounded.GraphicEq, "По длительности") { vm.setSort(1); showSort = false }
                    ActionRow(Icons.Rounded.Schedule, "По дате") { vm.setSort(2); showSort = false }
                    ActionRow(Icons.Rounded.FolderOpen, "По папке") { vm.setSort(3); showSort = false }
                }
            },
            confirmButton = { TextButton(onClick = { showSort = false }) { Text("Закрыть") } }
        )
    }

    if (confirmClearFav) {
        AlertDialog(
            onDismissRequest = { confirmClearFav = false },
            title = { Text("Очистить избранное?") },
            text = { Text("Список избранного будет очищен. Сами файлы останутся на устройстве.") },
            confirmButton = { TextButton(onClick = { vm.clearFavorites(); confirmClearFav = false }) { Text("Очистить") } },
            dismissButton = { TextButton(onClick = { confirmClearFav = false }) { Text("Отмена") } }
        )
    }

    if (needSettings) {
        AlertDialog(
            onDismissRequest = { vm.needWriteSettings.value = false },
            title = { Text("Нужно разрешение") },
            text = { Text("Разрешите приложению изменять системные настройки, вернитесь назад и повторите установку мелодии.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.needWriteSettings.value = false
                    runCatching {
                        settingsLauncher.launch(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName)))
                    }
                }) { Text("Открыть настройки") }
            },
            dismissButton = { TextButton(onClick = { vm.needWriteSettings.value = false }) { Text("Отмена") } }
        )
    }
}

private fun Modifier.horizontalScrollSafe(): Modifier = this

@Composable
private fun SettingRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val pal = LocalPal.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, color = pal.text, fontWeight = FontWeight.Medium)
            Text(hint, fontSize = 10.sp, color = pal.textDim)
        }
        Spacer(Modifier.width(10.dp))
        MiniSwitch(checked, onChange)
    }
}

@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    val pal = LocalPal.current
    val tint = if (danger) pal.danger else pal.text
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 11.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(19.dp), tint = if (danger) pal.danger else pal.primary)
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = 14.sp, color = tint)
    }
}

@Composable
private fun EmptyHint(title: String, hint: String) {
    val pal = LocalPal.current
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Rounded.MusicOff, null, Modifier.size(52.dp), tint = pal.border)
        Spacer(Modifier.height(12.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = pal.text)
        Spacer(Modifier.height(6.dp))
        Text(hint, fontSize = 12.sp, color = pal.textDim, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SongRow(
    song: Song,
    name: String,
    active: Boolean,
    playing: Boolean,
    progress: Float,
    favorite: Boolean,
    showPath: Boolean,
    time: Float,
    onPlay: () -> Unit,
    onFav: () -> Unit,
    onMenu: () -> Unit
) {
    val pal = LocalPal.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (active) Brush.horizontalGradient(listOf(pal.primary.copy(alpha = 0.22f), pal.tertiary.copy(alpha = 0.16f)))
                else Brush.horizontalGradient(listOf(pal.surfaceHigh.copy(alpha = 0.86f), pal.surface.copy(alpha = 0.78f)))
            )
            .border(1.dp, if (active) pal.primary.copy(alpha = 0.55f) else pal.border, shape)
            .clickable(onClick = onPlay)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f - 2f
                drawCircle(pal.primary.copy(alpha = if (active) 0.22f else 0.12f), r, center)
                drawCircle(pal.border, r, center, style = Stroke(1.4f))
                if (active && progress > 0f) {
                    drawArc(
                        pal.primary, -90f, 360f * progress.coerceIn(0f, 1f), false,
                        topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2f, r * 2f),
                        style = Stroke(3f)
                    )
                }
            }
            if (active && playing) Equalizer(time, pal.primary, Modifier.size(16.dp, 14.dp))
            else Icon(
                if (active) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                "Играть", Modifier.size(20.dp), tint = pal.primary
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = pal.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildString {
                    append(song.artist)
                    append(" · ")
                    append(fmtTime(song.durationMs))
                    if (showPath) { append(" · "); append(song.folder) }
                },
                fontSize = 10.sp, color = pal.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Waveform(song.key.hashCode().toLong(), if (active) progress else 0f, Modifier.fillMaxWidth().height(16.dp), bars = 30)
            if (song.isRingtone || !song.indexed) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (song.isRingtone) TagChip("рингтон", pal.success)
                    if (!song.indexed) TagChip("вне медиатеки", pal.warning)
                }
            }
        }
        IconButton(onClick = onFav, modifier = Modifier.size(36.dp)) {
            Icon(
                if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                "Избранное", Modifier.size(19.dp), tint = if (favorite) pal.tertiary else pal.textDim
            )
        }
        IconButton(onClick = onMenu, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.MoreVert, "Действия", Modifier.size(19.dp), tint = pal.textDim)
        }
    }
}

@Composable
private fun TagChip(text: String, tint: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.18f))
            .border(1.dp, tint.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 1.dp)
    ) {
        Text(text, fontSize = 9.sp, color = tint, fontWeight = FontWeight.Medium)
    }
}
