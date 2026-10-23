package dev.pishi.player.app

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import dev.pishi.player.android.Media3PlaybackEngine
import dev.pishi.player.api.*
import dev.pishi.player.capabilities.DecoderCapability
import dev.pishi.player.capabilities.DecoderCapabilityScanner
import dev.pishi.player.input.RemoteAction
import dev.pishi.player.input.RemoteActionMapper
import dev.pishi.player.storage.FileBookmarkStore
import dev.pishi.player.storage.PlaybackBookmark
import dev.pishi.player.subtitle.SubtitleCue
import dev.pishi.player.subtitle.SubtitleDecoder
import dev.pishi.player.subtitle.SubtitleParser
import dev.pishi.player.subtitle.android.ComposeOverlayRenderingPlan
import dev.pishi.player.ui.PlaybackControlState
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { UniversalPlayerScreen() } }
    }
}

private data class LoadedSubtitle(
    val fileName: String,
    val cues: List<SubtitleCue>,
    val charsetName: String,
    val usedFallback: Boolean,
    val format: SubtitleParser.Format,
)

@OptIn(UnstableApi::class)
@Composable
private fun UniversalPlayerScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val engine = remember { Media3PlaybackEngine(context) }
    val keyMapper = remember { RemoteActionMapper() }
    val bookmarkStore = remember { FileBookmarkStore(File(context.filesDir, "bookmarks.tsv")) }
    val subtitleSettingsStore = remember { SubtitleSettingsStore(context) }

    var snapshot by remember { mutableStateOf(engine.snapshot()) }
    var tracks by remember { mutableStateOf(engine.tracks()) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var title by remember { mutableStateOf("No media selected") }
    var error by remember { mutableStateOf<String?>(null) }
    var showTracks by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showSubtitleSettings by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var seekDraft by remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }
    var subtitleSettings by remember { mutableStateOf(subtitleSettingsStore.load()) }
    var loadedSubtitle by remember { mutableStateOf<LoadedSubtitle?>(null) }
    var subtitleError by remember { mutableStateOf<String?>(null) }
    var subtitleBytes by remember { mutableStateOf<ByteArray?>(null) }
    var subtitleFileName by remember { mutableStateOf<String?>(null) }
    var pendingResume by remember { mutableStateOf<PlaybackBookmark?>(null) }
    val videoFocusRequester = remember { FocusRequester() }

    fun handleAction(action: RemoteAction) {
        when (action) {
            RemoteAction.TogglePlayback -> if (snapshot.isPlaying) engine.pause() else engine.play()
            RemoteAction.Play -> engine.play()
            RemoteAction.Pause -> engine.pause()
            is RemoteAction.Seek -> engine.seekBy(action.deltaMs)
            RemoteAction.Menu -> showTracks = !showTracks
            else -> Unit // Up/Down/Left/Right/Confirm/Back are focus-system or BackHandler concerns.
        }
    }

    fun decodeAndParseSubtitle(bytes: ByteArray, fileName: String, encodingOverride: String?) {
        try {
            val decoded = SubtitleDecoder.decode(bytes, encodingOverride)
            val format = SubtitleParser.detectFormat(decoded.text, fileName)
            val cues = SubtitleParser.parse(decoded.text, format)
            if (cues.isEmpty()) {
                loadedSubtitle = null
                subtitleError = "No subtitle cues found in $fileName (detected format: $format)."
            } else {
                loadedSubtitle = LoadedSubtitle(fileName, cues, decoded.charsetName, decoded.usedFallback, format)
                subtitleError = null
            }
        } catch (e: IllegalArgumentException) {
            loadedSubtitle = null
            subtitleError = e.message ?: "Could not decode subtitle file."
        }
    }

    fun updateSubtitleSettings(next: SubtitleSettings) {
        val encodingChanged = next.encodingOverride != subtitleSettings.encodingOverride
        subtitleSettings = next
        subtitleSettingsStore.save(next)
        if (encodingChanged) {
            val bytes = subtitleBytes
            val name = subtitleFileName
            if (bytes != null && name != null) decodeAndParseSubtitle(bytes, name, next.encodingOverride)
        }
    }

    DisposableEffect(engine) {
        engine.setListener(object : PlaybackEngine.Listener {
            override fun onSnapshot(value: PlaybackSnapshot) { snapshot = value }
            override fun onTracksChanged(value: List<MediaTrack>) { tracks = value }
            override fun onError(code: String, message: String, cause: Throwable?) { error = "$code: $message" }
        })
        onDispose {
            // Persist the resume point for the currently loaded media.
            val uri = selectedUri
            val snap = engine.snapshot()
            if (uri != null && snap.positionMs > 3_000) {
                runCatching {
                    bookmarkStore.save(PlaybackBookmark(uri.toString(), snap.positionMs, snap.durationMs, System.currentTimeMillis()))
                }
            }
            engine.setListener(null)
            engine.release()
        }
    }

    // Position refresh only. Track lists arrive through the listener; they are not polled.
    LaunchedEffect(engine) {
        var lastBookmarkSaveMs = 0L
        while (true) {
            if (!isSeeking) snapshot = engine.snapshot()
            val uri = selectedUri
            val now = System.currentTimeMillis()
            if (uri != null && snapshot.isPlaying && snapshot.positionMs > 3_000 && now - lastBookmarkSaveMs > 5_000) {
                lastBookmarkSaveMs = now
                val snap = snapshot
                runCatching {
                    bookmarkStore.save(PlaybackBookmark(uri.toString(), snap.positionMs, snap.durationMs, now))
                }
            }
            delay(250)
        }
    }

    LaunchedEffect(selectedUri) {
        if (selectedUri != null) runCatching { videoFocusRequester.requestFocus() }
    }

    BackHandler {
        when {
            showDiagnostics -> showDiagnostics = false
            showSubtitleSettings -> showSubtitleSettings = false
            showSpeed -> showSpeed = false
            showTracks -> showTracks = false
            pendingResume != null -> { pendingResume = null; engine.play() }
            else -> activity?.finish()
        }
    }

    val openSubtitle = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) { /* Provider may grant access only for this session. */ }
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "subtitle"
            val bytes = try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > SubtitleDecoder.MAX_SUBTITLE_BYTES) {
                            throw IllegalArgumentException("Subtitle exceeds the 16 MiB safety limit")
                        }
                        out.write(buffer, 0, read)
                    }
                    out.toByteArray()
                }
            } catch (e: Exception) {
                subtitleError = e.message ?: "Could not read subtitle file."
                null
            }
            if (bytes != null) {
                subtitleBytes = bytes
                subtitleFileName = fileName
                decodeAndParseSubtitle(bytes, fileName, subtitleSettings.encodingOverride)
            }
        }
    }

    val openMedia = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) { /* Some document providers do not offer persistable grants. */ }
            // Save the outgoing media's resume point before switching.
            val previousUri = selectedUri
            if (previousUri != null && previousUri != uri) {
                val snap = engine.snapshot()
                if (snap.positionMs > 3_000) {
                    runCatching {
                        bookmarkStore.save(PlaybackBookmark(previousUri.toString(), snap.positionMs, snap.durationMs, System.currentTimeMillis()))
                    }
                }
            }
            selectedUri = uri
            title = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "Selected media" } ?: "Selected media"
            error = null
            loadedSubtitle = null
            subtitleError = null
            subtitleBytes = null
            subtitleFileName = null
            val bookmark = runCatching { bookmarkStore.get(uri.toString()) }.getOrNull()
            val bookmarkDuration = bookmark?.durationMs
            val resumable = bookmark != null && bookmark.positionMs > 5_000 &&
                (bookmarkDuration == null || bookmark.positionMs < bookmarkDuration - 5_000)
            engine.prepare(MediaSource(URI(uri.toString()), mimeType = context.contentResolver.getType(uri), title = title))
            if (resumable) {
                pendingResume = bookmark
            } else {
                pendingResume = null
                engine.play()
            }
        }
    }

    val duration = (snapshot.durationMs ?: 0L).coerceAtLeast(0L)
    val position = if (isSeeking) seekDraft.toLong() else snapshot.positionMs
    val controlState = PlaybackControlState(
        snapshot = snapshot,
        seekPreviewMs = if (isSeeking) seekDraft.toLong() else null,
        controlsVisible = true,
    )
    val overlayCue = if (subtitleSettings.enabled) {
        loadedSubtitle?.let { activeCue(it.cues, position - subtitleSettings.delayMs) }
    } else {
        null
    }

    Surface(Modifier.fillMaxSize().background(Color.Black), color = Color.Black) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 14.dp)
                .onPreviewKeyEvent { event ->
                    // Only dedicated media-transport keys and Menu are intercepted globally.
                    // DPAD keys are NEVER consumed here: they belong to the focused control
                    // (buttons activate, slider adjusts, focus moves) or, when the video
                    // surface holds focus, to its own onKeyEvent handler below.
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.nativeKeyEvent.keyCode) {
                        AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        AndroidKeyEvent.KEYCODE_MEDIA_PLAY,
                        AndroidKeyEvent.KEYCODE_MEDIA_PAUSE,
                        AndroidKeyEvent.KEYCODE_MEDIA_REWIND,
                        AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                        AndroidKeyEvent.KEYCODE_MEDIA_NEXT,
                        AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS,
                        AndroidKeyEvent.KEYCODE_MENU,
                        AndroidKeyEvent.KEYCODE_INFO,
                        -> {
                            handleAction(keyMapper.map(event.nativeKeyEvent.keyCode, event.nativeKeyEvent.repeatCount))
                            true
                        }
                        else -> false
                    }
                },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("UNIVERSAL PLAYER", style = MaterialTheme.typography.labelLarge, color = Color(0xFF80CBC4))
                    Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
                }
                Button(onClick = { openMedia.launch(arrayOf("video/*", "audio/*")) }) { Text("Open file") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { openSubtitle.launch(arrayOf("text/*", "application/x-subrip", "application/octet-stream")) }, enabled = selectedUri != null) { Text("Subtitle") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { showSubtitleSettings = true }) { Text("Sub settings") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { showTracks = !showTracks }, enabled = tracks.isNotEmpty()) { Text("Tracks (${tracks.size})") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { showSpeed = true }, enabled = selectedUri != null) { Text("${trimSpeed(controlState.snapshot.speed)}×") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { showDiagnostics = true }) { Text("Device") }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    Modifier.weight(if (showTracks) 0.72f else 1f).fillMaxHeight().background(Color.Black)
                        .focusRequester(videoFocusRequester)
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            val action = keyMapper.map(event.nativeKeyEvent.keyCode, event.nativeKeyEvent.repeatCount)
                            when (action) {
                                RemoteAction.TogglePlayback, RemoteAction.Play, RemoteAction.Pause, RemoteAction.Menu -> {
                                    handleAction(action); true
                                }
                                is RemoteAction.Seek -> { handleAction(action); true }
                                // Up/Down/Back/Unknown fall through so focus can leave the surface.
                                else -> false
                            }
                        }
                        .focusable(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selectedUri == null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text("Choose a local video to begin", color = Color.LightGray, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text("Remote: OK on the video = play/pause · ←/→ = seek · Menu = tracks", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { viewContext -> PlayerView(viewContext).apply {
                                player = engine.nativePlayer
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                                setKeepContentOnPlayerReset(true)
                            } },
                            update = {
                                it.player = engine.nativePlayer
                                // Media3 renders embedded tracks itself. External files are
                                // rendered by the subtitle-core overlay, so hide Media3's
                                // subtitle view while an external subtitle is loaded.
                                it.subtitleView?.visibility = if (loadedSubtitle != null) View.GONE else View.VISIBLE
                            },
                        )
                        if (overlayCue != null) {
                            SubtitleOverlayCue(
                                text = overlayCue.text,
                                settings = subtitleSettings,
                                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp, vertical = 18.dp),
                            )
                        }
                    }
                }
                if (showTracks) {
                    Column(Modifier.weight(0.28f).fillMaxHeight().background(Color(0xFF171717)).padding(10.dp)) {
                        Text("Available tracks", color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (tracks.any { it.type == TrackType.TEXT }) {
                                item(key = "subtitles-off") {
                                    OutlinedButton(
                                        onClick = { runCatching { engine.selectTrack(TRACK_ID_SUBTITLES_OFF) }.onFailure { error = it.message } },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) { Text("Subtitles: Off") }
                                }
                            }
                            items(tracks, key = { it.id }) { track ->
                                OutlinedButton(onClick = { runCatching { engine.selectTrack(track.id) }.onFailure { error = it.message } }, modifier = Modifier.fillMaxWidth()) {
                                    Text("${if (track.selected) "✓ " else ""}${track.type}: ${track.label}", maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
            if (loadedSubtitle != null) {
                val sub = loadedSubtitle!!
                Text(
                    "Subtitle: ${sub.fileName} · ${sub.charsetName}${if (sub.usedFallback) " (fallback)" else ""} · ${sub.format} · ${sub.cues.size} cues · overlay renderer",
                    color = Color(0xFFB0BEC5), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp),
                )
            }
            if (subtitleError != null) {
                Text(subtitleError!!, color = Color(0xFFFF8A80), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(formatTime(position), color = Color.White, style = MaterialTheme.typography.labelMedium)
                Slider(
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    value = if (duration > 0) position.coerceIn(0L, duration).toFloat() else 0f,
                    onValueChange = { seekDraft = it; isSeeking = true },
                    onValueChangeFinished = { engine.seekTo(seekDraft.toLong()); isSeeking = false },
                    valueRange = 0f..(duration.coerceAtLeast(1L).toFloat()),
                    enabled = duration > 0,
                )
                Text(formatTime(duration), color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { engine.seekBy(-30_000) }, enabled = selectedUri != null) { Text("−30s") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { engine.seekBy(-10_000) }, enabled = selectedUri != null) { Text("−10s") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { if (controlState.snapshot.isPlaying) engine.pause() else engine.play() }, enabled = selectedUri != null) { Text(if (controlState.snapshot.isPlaying) "Pause" else "Play") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { engine.seekBy(10_000) }, enabled = selectedUri != null) { Text("+10s") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { engine.seekBy(30_000) }, enabled = selectedUri != null) { Text("+30s") }
                Spacer(Modifier.width(16.dp))
                Text(
                    "${controlState.snapshot.state} · ${trimSpeed(controlState.snapshot.speed)}× · ${controlState.snapshot.decodePath}" +
                        (controlState.snapshot.videoDecoderName?.let { " · $it" } ?: ""),
                    color = Color.LightGray, style = MaterialTheme.typography.labelMedium,
                )
            }
            if (error != null) Text(error!!, color = Color(0xFFFF8A80), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
    }

    if (pendingResume != null) {
        val bookmark = pendingResume!!
        AlertDialog(
            onDismissRequest = { pendingResume = null; engine.play() },
            title = { Text("Resume playback?") },
            text = { Text("You stopped at ${formatTime(bookmark.positionMs)} of ${bookmark.durationMs?.let { formatTime(it) } ?: "?"}.") },
            confirmButton = {
                TextButton(onClick = { pendingResume = null; engine.seekTo(bookmark.positionMs); engine.play() }) { Text("Resume") }
            },
            dismissButton = {
                TextButton(onClick = { pendingResume = null; engine.play() }) { Text("Start over") }
            },
        )
    }

    if (showSpeed) {
        AlertDialog(
            onDismissRequest = { showSpeed = false },
            title = { Text("Playback speed") },
            text = {
                Column {
                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                        TextButton(
                            onClick = { runCatching { engine.setPlaybackSpeed(speed) }.onFailure { error = it.message }; showSpeed = false },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("${trimSpeed(speed)}×") }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showSpeed = false }) { Text("Close") } },
        )
    }

    if (showSubtitleSettings) {
        SubtitleSettingsDialog(
            settings = subtitleSettings,
            hasLoadedSubtitle = loadedSubtitle != null,
            onChange = { updateSubtitleSettings(it) },
            onDismiss = { showSubtitleSettings = false },
        )
    }

    if (showDiagnostics) {
        DiagnosticsDialog(snapshot = snapshot, onDismiss = { showDiagnostics = false })
    }
}

@Composable
private fun SubtitleOverlayCue(text: String, settings: SubtitleSettings, modifier: Modifier = Modifier) {
    val fontFamily = when (settings.fontFamilyChoice) {
        "sans" -> FontFamily.SansSerif
        "serif" -> FontFamily.Serif
        "mono" -> FontFamily.Monospace
        else -> FontFamily.Default
    }
    Box(modifier.background(Color.Black.copy(alpha = settings.backgroundAlpha), RoundedCornerShape(6.dp))) {
        Text(
            text = text,
            color = Color(settings.textColorArgb),
            fontSize = settings.fontSizeSp.sp,
            lineHeight = (settings.fontSizeSp * 1.35f).sp,
            fontFamily = fontFamily,
            fontWeight = if (settings.bold) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun SubtitleSettingsDialog(
    settings: SubtitleSettings,
    hasLoadedSubtitle: Boolean,
    onChange: (SubtitleSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Subtitle settings") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show subtitles", Modifier.weight(1f))
                    Switch(checked = settings.enabled, onCheckedChange = { onChange(settings.copy(enabled = it)) })
                }
                Text("Font size: ${settings.fontSizeSp.toInt()}sp", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = settings.fontSizeSp,
                    onValueChange = { onChange(settings.copy(fontSizeSp = it)) },
                    valueRange = 12f..30f,
                )
                Text("Background opacity: ${(settings.backgroundAlpha * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = settings.backgroundAlpha,
                    onValueChange = { onChange(settings.copy(backgroundAlpha = it)) },
                    valueRange = 0f..0.9f,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Bold", Modifier.weight(1f))
                    Switch(checked = settings.bold, onCheckedChange = { onChange(settings.copy(bold = it)) })
                }
                Text("Font", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SubtitleSettings.FONT_CHOICES.forEach { choice ->
                        if (choice == settings.fontFamilyChoice) {
                            Button(onClick = {}) { Text(choice) }
                        } else {
                            OutlinedButton(onClick = { onChange(settings.copy(fontFamilyChoice = choice)) }) { Text(choice) }
                        }
                    }
                }
                Text("Text color", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SubtitleSettings.COLOR_CHOICES.forEach { (name, argb) ->
                        if (argb == settings.textColorArgb) {
                            Button(onClick = {}) { Text(name) }
                        } else {
                            OutlinedButton(onClick = { onChange(settings.copy(textColorArgb = argb)) }) { Text(name) }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Delay", Modifier.weight(1f))
                    OutlinedButton(onClick = { onChange(settings.copy(delayMs = settings.delayMs - 250)) }) { Text("−0.25s") }
                    Text("${settings.delayMs / 1000.0}s")
                    OutlinedButton(onClick = { onChange(settings.copy(delayMs = settings.delayMs + 250)) }) { Text("+0.25s") }
                }
                Text("Encoding (applies on load${if (hasLoadedSubtitle) ", re-applies now" else ""})", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SubtitleSettings.ENCODING_CHOICES.forEach { (name, charset) ->
                        if (charset == settings.encodingOverride) {
                            Button(onClick = {}) { Text(name) }
                        } else {
                            OutlinedButton(onClick = { onChange(settings.copy(encodingOverride = charset)) }) { Text(name) }
                        }
                    }
                }
                if (!ComposeOverlayRenderingPlan.outlineSupported || !ComposeOverlayRenderingPlan.perCueAlignmentSupported) {
                    Text(
                        "Overlay renderer limits: no text outline, no per-cue ASS positioning. ASS styles/effects are not reproduced; embedded tracks use the player's built-in renderer.",
                        style = MaterialTheme.typography.labelSmall, color = Color(0xFFB0BEC5),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onChange(SubtitleSettings()) }) { Text("Reset defaults") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun DiagnosticsDialog(snapshot: PlaybackSnapshot, onDismiss: () -> Unit) {
    var capabilities by remember { mutableStateOf<List<DecoderCapability>?>(null) }
    LaunchedEffect(Unit) {
        capabilities = runCatching { DecoderCapabilityScanner.scan() }.getOrDefault(emptyList())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Device decoders") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "Now playing: video=${snapshot.videoDecoderName ?: "—"} · audio=${snapshot.audioDecoderName ?: "—"} · path=${snapshot.decodePath}",
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(8.dp))
                val caps = capabilities
                if (caps == null) {
                    Text("Scanning…")
                } else if (caps.isEmpty()) {
                    Text("No decoder inventory available.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(caps, key = { it.mimeType + "|" + it.codecName }) { cap ->
                            Text(
                                "${cap.mimeType} — ${cap.codecName} · " +
                                    (when (cap.hardwareAccelerated) { true -> "HW"; false -> "SW"; null -> "?" }) +
                                    (if (cap.securePlayback) " · secure" else ""),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Inventory only: a listed decoder does not guarantee smooth playback of every profile, resolution, or bit depth.",
                    style = MaterialTheme.typography.labelSmall, color = Color(0xFFB0BEC5),
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun activeCue(cues: List<SubtitleCue>, positionMs: Long): SubtitleCue? {
    if (cues.isEmpty() || positionMs < 0) return null
    var lo = 0
    var hi = cues.size - 1
    var candidate: SubtitleCue? = null
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        val cue = cues[mid]
        if (positionMs < cue.startMs) {
            hi = mid - 1
        } else {
            candidate = cue
            lo = mid + 1
        }
    }
    // For overlapping cues this returns the latest-started cue that covers the position.
    return candidate?.takeIf { positionMs <= it.endMs }
}

private fun trimSpeed(speed: Float): String =
    if (speed == speed.toLong().toFloat()) speed.toLong().toString() else speed.toString()

private fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L).toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
