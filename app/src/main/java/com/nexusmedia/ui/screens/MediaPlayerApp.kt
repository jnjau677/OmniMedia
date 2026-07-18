package com.nexusmedia.ui.screens

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import kotlin.math.cos
import kotlin.math.absoluteValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.nexusmedia.data.*
import com.nexusmedia.ui.screens.rememberIsInPipMode
import com.nexusmedia.ui.screens.enterPipMode
import android.app.Activity
import com.nexusmedia.ui.theme.*
import com.nexusmedia.viewmodel.MediaViewModel
import com.nexusmedia.viewmodel.PlaybackState
import com.nexusmedia.viewmodel.PlaybackRepeatMode
import com.nexusmedia.viewmodel.formatTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// SAF INTEGRATION REFERENCE:
// val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
//     uri?.let { selectedUri ->
//         val mediaItem = viewModel.importSampleLocalTrack() // or scan via VideoScanner
//         // Load selected file from URI using ContentResolver
//     }
// }
// pickerLauncher.launch(SAFFilePicker.supportedMimeTypes())


// SWIPE GESTURE DETECTION STUB:
// Implement detectHorizontalDragGestures / detectVerticalDragGestures
// connected to viewModel.gestureSeekEnabled / gestureVolumeEnabled / gestureBrightnessEnabled
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPlayerApp(viewModel: MediaViewModel) {
    val items by viewModel.mediaItems.collectAsStateWithLifecycle()
    val playlistsList by viewModel.playlists.collectAsStateWithLifecycle()
    val historyList by viewModel.history.collectAsStateWithLifecycle()
    val settings by viewModel.settingsFlow.collectAsStateWithLifecycle()

    var showSplash by remember { mutableStateOf(true) }
    var currentTab by remember { mutableStateOf("home") } // "home", "search", "library", "settings", "profile"
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var showQueueDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf<MediaItemEntity?>(null) }
    var showEqualizerDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }

    val isInPipMode = rememberIsInPipMode()

    // Automatic Splash Screen Timer
    LaunchedEffect(Unit) {
        delay(2000)
        showSplash = false
    }

    if (showSplash && !isInPipMode) {
        SplashScreen { showSplash = false }
    } else if (isInPipMode) {
        viewModel.currentItem?.let { item ->
            if (item.isVideo) {
                VideoPlayerContainer(viewModel = viewModel, current = item)
            } else {
                // Audio-only PiP mode: thumbnail + small play indicator overlay
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = "Interactive element",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    } else {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideScreen = maxWidth >= 720.dp

            if (isWideScreen) {
                // Large screen / Tablet / Desktop layout with persistent navigation rail and playback panel
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    // 1. Sidebar Navigation Rail (Left side)
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxHeight()
                            .testTag("wide_nav_sidebar"),
                        header = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(vertical = 16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MusicNote,
                                    contentDescription = "App Icon",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Cosmic",
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    ) {
                        val tabs = listOf(
                "local" to "Local Library",
                "playlists" to "Playlists",
                "explorer" to "File Explorer"
            ),
                            Triple("search", Icons.Filled.Search, "Search"),
                            Triple("library", Icons.Filled.VideoLibrary, "Library"),
                            Triple("settings", Icons.Filled.Settings, "Settings"),
                            Triple("profile", Icons.Filled.Person, "Profile")
                        )

                        tabs.forEach { (tabId, icon, label) ->
                            NavigationRailItem(
                                selected = currentTab == tabId,
                                onClick = { currentTab = tabId },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("nav_tab_$tabId"),
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                    unselectedTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                    indicatorColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }

                    // 2. Main content router area (Center)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        when (currentTab) {
                            "home" -> HomeScreen(
                                viewModel = viewModel,
                                items = items,
                                onPlay = { item -> viewModel.playMedia(item, items) },
                                onAddToPlaylist = { showAddToPlaylistDialog = it }
                            )
                            "search" -> SearchScreen(
                                viewModel = viewModel,
                                items = items,
                                onPlay = { item -> viewModel.playMedia(item, items) },
                                onAddToPlaylist = { showAddToPlaylistDialog = it }
                            )
                            "library" -> LibraryScreen(
                                viewModel = viewModel,
                                items = items,
                                playlists = playlistsList,
                                onPlay = { item -> viewModel.playMedia(item, items) },
                                onAddToPlaylist = { showAddToPlaylistDialog = it }
                            )
                            "settings" -> SettingsScreen(viewModel = viewModel, settings = settings)
                            "profile" -> ProfileScreen(viewModel = viewModel, history = historyList, items = items)
                        }
                    }

                    // 3. Persistent Playback Side Panel / Supporting Pane (Right side)
                    val currentItem = viewModel.currentItem
                    if (currentItem != null) {
                        Surface(
                            modifier = Modifier
                                .width(360.dp)
                                .fillMaxHeight()
                                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)))
                                .testTag("wide_playback_pane"),
                            color = Color(0xFF070B14)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                // Top header for side-player
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (currentItem.isVideo) "VIDEO PLAYER" else "MUSIC MODE",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 1.5.sp
                                    )
                                    Row {
                                        IconButton(onClick = { showSleepTimerDialog = true }) {
                                            Icon(Icons.Outlined.Timer, contentDescription = "Open sleep timer", tint = MaterialTheme.colorScheme.onBackground)
                                        }
                                        IconButton(onClick = { showQueueDialog = true }) {
                                            Icon(Icons.Filled.QueueMusic, contentDescription = "Play Queue", tint = MaterialTheme.colorScheme.onBackground)
                                        }
                                    }
                                }

                                if (currentItem.isVideo) {
                                    VideoPlayerContainer(viewModel = viewModel, current = currentItem)
                                } else {
                                    MusicPlayerContainer(viewModel = viewModel, current = currentItem, onShowEqualizer = { showEqualizerDialog = true })
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                PlaybackControlsSection(viewModel = viewModel)
                            }
                        }
                    }
                }
            } else {
                // Compact / Phone layout
                Scaffold(
                    bottomBar = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                        ) {
                            // 1. Persistent Mini Player when active media exists
                            if (viewModel.currentItem != null && !isPlayerExpanded) {
                                MiniPlayerBar(
                                    viewModel = viewModel,
                                    onExpand = { isPlayerExpanded = true }
                                )
                            }

                            // 2. Standard Responsive Navigation Bar
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 8.dp,
                                modifier = Modifier.testTag("bottom_nav_bar")
                            ) {
                                val tabs = listOf(
                "local" to "Local Library",
                "playlists" to "Playlists",
                "explorer" to "File Explorer"
            ),
                                    Triple("search", Icons.Filled.Search, "Search"),
                                    Triple("library", Icons.Filled.VideoLibrary, "Library"),
                                    Triple("settings", Icons.Filled.Settings, "Settings"),
                                    Triple("profile", Icons.Filled.Person, "Profile")
                                )

                                tabs.forEach { (tabId, icon, label) ->
                                    NavigationBarItem(
                                        selected = currentTab == tabId,
                                        onClick = { currentTab = tabId },
                                        icon = { Icon(icon, contentDescription = label) },
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.testTag("nav_tab_$tabId")
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        // Screen content router
                        when (currentTab) {
                            "home" -> HomeScreen(
                                viewModel = viewModel,
                                items = items,
                                onPlay = { item -> viewModel.playMedia(item, items) },
                                onAddToPlaylist = { showAddToPlaylistDialog = it }
                            )
                            "search" -> SearchScreen(
                                viewModel = viewModel,
                                items = items,
                                onPlay = { item -> viewModel.playMedia(item, items) },
                                onAddToPlaylist = { showAddToPlaylistDialog = it }
                            )
                            "library" -> LibraryScreen(
                                viewModel = viewModel,
                                items = items,
                                playlists = playlistsList,
                                onPlay = { item -> viewModel.playMedia(item, items) },
                                onAddToPlaylist = { showAddToPlaylistDialog = it }
                            )
                            "settings" -> SettingsScreen(viewModel = viewModel, settings = settings)
                            "profile" -> ProfileScreen(viewModel = viewModel, history = historyList, items = items)
                        }

                        // Drag/Slide transition for full-screen player overlay
                        AnimatedVisibility(
                            visible = isPlayerExpanded,
                            enter = slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                            ),
                            exit = slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                            )
                        ) {
                            FullScreenPlayer(
                                viewModel = viewModel,
                                onCollapse = { isPlayerExpanded = false },
                                onShowQueue = { showQueueDialog = true },
                                onShowEqualizer = { showEqualizerDialog = true },
                                onShowSleepTimer = { showSleepTimerDialog = true }
                            )
                        }
                    }
                }
            }
        }

        // Overlay Dialogs
        if (showQueueDialog) {
            QueueDialog(
                viewModel = viewModel,
                onDismiss = { showQueueDialog = false }
            )
        }

        if (showAddToPlaylistDialog != null) {
            AddToPlaylistDialog(
                viewModel = viewModel,
                mediaItem = showAddToPlaylistDialog!!,
                playlists = playlistsList,
                onDismiss = { showAddToPlaylistDialog = null }
            )
        }

        if (showEqualizerDialog) {
            EqualizerDialog(
                viewModel = viewModel,
                onDismiss = { showEqualizerDialog = false }
            )
        }

        if (showSleepTimerDialog) {
            SleepTimerDialog(
                viewModel = viewModel,
                onDismiss = { showSleepTimerDialog = false }
            )
        }
    }
}

// 1. SPLASH SCREEN
@Composable
fun SplashScreen(onSkip: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF020617))
                )
            )
            .clickable(onClick = onSkip),
        contentAlignment = Alignment.Center
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val scale by infiniteTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(8000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(200.dp)
                    .rotate(rotation)
            ) {
                // Neon glowing orbital rings
                val primary = MaterialTheme.colorScheme.primary
                val secondary = MaterialTheme.colorScheme.secondary
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = primary.copy(alpha = 0.4f),
                        style = Stroke(width = 4.dp.toPx()),
                        radius = size.minDimension / 2.2f
                    )
                    drawCircle(
                        color = secondary.copy(alpha = 0.3f),
                        style = Stroke(width = 2.dp.toPx()),
                        radius = size.minDimension / 1.7f
                    )
                }
                Icon(
                    imageVector = Icons.Filled.PlayCircle,
                    contentDescription = "App logo, cosmic player",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(110.dp)
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "COSMIC PLAYER",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = 4.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Premium Video & Music Hub",
                fontSize = 14.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(48.dp))
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

// 2. MINI PLAYER BAR
@Composable
fun MiniPlayerBar(
    viewModel: MediaViewModel,
    onExpand: () -> Unit
) {
    val current = viewModel.currentItem ?: return
    val progress = if (viewModel.duration > 0) viewModel.currentPosition.toFloat() / viewModel.duration else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onExpand)
            .testTag("mini_player_bar")
    ) {
        // Quick visual mini-progress indicator
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                AsyncImage(
                    model = current.thumbnailUrl,
                    contentDescription = "Thumbnail for ${current.title}",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = current.title,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = current.artist,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier.testTag("mini_play_pause")
                ) {
                    Icon(
                        imageVector = if (viewModel.playbackState is PlaybackState.Playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play or pause media",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                IconButton(onClick = { viewModel.skipToNext() }) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Skip to next track",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                IconButton(onClick = { viewModel.stopPlayback() }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Stop playback",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

// 3. HOME SCREEN
@Composable
fun HomeScreen(
    viewModel: MediaViewModel,
    items: List<MediaItemEntity>,
    onPlay: (MediaItemEntity) -> Unit,
    onAddToPlaylist: (MediaItemEntity) -> Unit
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val recentlyPlayed = remember(history, items) {
        history.mapNotNull { h -> items.find { it.id == h.mediaItemId } }.take(10)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            // Beautiful Home Welcome Banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Good Evening",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Ready for some cosmic beats?",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                }
                IconButton(onClick = { /* Simulated Profile Hub */ }) {
                    Icon(
                        imageVector = Icons.Filled.AccountCircle,
                        contentDescription = "Open profile",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        // Horizontal Continue Playing / Recently Played Section
        item {
            SectionHeader(title = "Recently Played", actionText = if (recentlyPlayed.size > 4) "See All" else null)
            Spacer(modifier = Modifier.height(8.dp))
            if (recentlyPlayed.isEmpty()) {
                EmptyStateCard("No recently played media.")
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(recentlyPlayed) { item ->
                        MediaCompactCard(
                            item = item,
                            onClick = { onPlay(item) },
                            onAddPlaylist = { onAddToPlaylist(item) }
                        )
                    }
                }
            }
        }

        // Favorites and Playlists Section
        item {
            SectionHeader(title = "Curated Playlists", actionText = "Browse")
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    PlaylistGlowCard(
                        title = "Chill Lofi Coding",
                        description = "Perfect lofi vibes for debugging",
                        color = MaterialTheme.colorScheme.primary,
                        icon = Icons.Outlined.Code,
                        onClick = {
                            val target = items.filter { it.genre == "Lofi" }
                            if (target.isNotEmpty()) onPlay(target.first())
                        }
                    )
                }
                item {
                    PlaylistGlowCard(
                        title = "Vibrant Synthwave",
                        description = "High tempo synthesizer runs",
                        color = MaterialTheme.colorScheme.secondary,
                        icon = Icons.Outlined.FlashOn,
                        onClick = {
                            val target = items.filter { it.genre == "Synthwave" }
                            if (target.isNotEmpty()) onPlay(target.first())
                        }
                    )
                }
                item {
                    PlaylistGlowCard(
                        title = "Cinematic Escapes",
                        description = "Acoustic and ambient journeys",
                        color = MaterialTheme.colorScheme.tertiary,
                        icon = Icons.Outlined.MovieFilter,
                        onClick = {
                            val target = items.filter { it.genre == "Nature" || it.genre == "Acoustic" }
                            if (target.isNotEmpty()) onPlay(target.first())
                        }
                    )
                }
            }
        }

        // Recommendations List View
        item {
            SectionHeader(title = "Recommended For You")
        }

        val recs = viewModel.getRecommendations(items)
        if (recs.isEmpty()) {
            item { EmptyStateCard("Add tracks to generate suggestions.") }
        } else {
            items(recs) { item ->
                MediaListRow(
                    item = item,
                    onClick = { onPlay(item) },
                    onFavorite = { viewModel.toggleFavorite(item) },
                    onAddPlaylist = { onAddToPlaylist(item) }
                )
            }
        }
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

// 4. SEARCH SCREEN
@Composable
fun SearchScreen(
    viewModel: MediaViewModel,
    items: List<MediaItemEntity>,
    onPlay: (MediaItemEntity) -> Unit,
    onAddToPlaylist: (MediaItemEntity) -> Unit
) {
    var searchActive by remember { mutableStateOf(false) }
    val searchHistory = remember { mutableStateListOf("Lofi Focus", "Cosmic Synthesizer", "Acoustic") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Search Input Header
        OutlinedTextField(
            value = viewModel.searchQuery,
            onValueChange = { viewModel.searchQuery = it },
            placeholder = { Text("Search songs, videos, genres...", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_input_field"),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Interactive element", tint = MaterialTheme.colorScheme.primary) },
            trailingIcon = {
                if (viewModel.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.searchQuery = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                unfocusedTextColor = MaterialTheme.colorScheme.onBackground
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Advanced Search Filters Segment
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf("All", "Videos", "Music")
            filters.forEach { filter ->
                FilterChip(
                    selected = viewModel.selectedTypeFilter == filter,
                    onClick = { viewModel.selectedTypeFilter = filter },
                    label = { Text(filter) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // History Tags when query is empty
        if (viewModel.searchQuery.isEmpty()) {
            Text(
                text = "Recent Searches",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                searchHistory.forEach { historyTag ->
                    SuggestionChip(
                        onClick = { viewModel.searchQuery = historyTag },
                        label = { Text(historyTag) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Trending Content",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))
            // Quick Trending Lists
            val trending = items.take(3)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(trending) { item ->
                    MediaListRow(
                        item = item,
                        onClick = { onPlay(item) },
                        onFavorite = { viewModel.toggleFavorite(item) },
                        onAddPlaylist = { onAddToPlaylist(item) }
                    )
                }
            }
        } else {
            // Searched items filter
            val filteredItems = items.filter { item ->
                val filename = item.url.substringAfterLast('/')
                val matchesQuery = item.title.contains(viewModel.searchQuery, ignoreCase = true) ||
                        item.artist.contains(viewModel.searchQuery, ignoreCase = true) ||
                        item.genre.contains(viewModel.searchQuery, ignoreCase = true) ||
                        filename.contains(viewModel.searchQuery, ignoreCase = true)
                val matchesType = when (viewModel.selectedTypeFilter) {
                    "Videos" -> item.isVideo
                    "Music" -> !item.isVideo
                    else -> true
                }
                matchesQuery && matchesType
            }

            if (filteredItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No cosmic content matches your search.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(filteredItems) { item ->
                        MediaListRow(
                            item = item,
                            onClick = { onPlay(item) },
                            onFavorite = { viewModel.toggleFavorite(item) },
                            onAddPlaylist = { onAddToPlaylist(item) }
                        )
                    }
                }
            }
        }
    }
}

// 5. LIBRARY SCREEN
@Composable
fun LibraryScreen(
    viewModel: MediaViewModel,
    items: List<MediaItemEntity>,
    playlists: List<PlaylistEntity>,
    onPlay: (MediaItemEntity) -> Unit,
    onAddToPlaylist: (MediaItemEntity) -> Unit
) {
    val selectedPlaylistId = viewModel.selectedPlaylistId
    val playlistItems by viewModel.selectedPlaylistItems.collectAsStateWithLifecycle()

    if (selectedPlaylistId != null) {
        val currentPlaylist = playlists.firstOrNull { it.id == selectedPlaylistId }
        if (currentPlaylist != null) {
            PlaylistDetailScreen(
                playlist = currentPlaylist,
                items = playlistItems,
                onBack = { viewModel.selectedPlaylistId = null },
                onPlayItem = { item ->
                    viewModel.playMedia(item, playlistItems)
                },
                onRemoveItem = { item ->
                    viewModel.removeFromPlaylist(currentPlaylist.id, item.id)
                },
                onReorderItem = { fromIndex, toIndex ->
                    viewModel.reorderPlaylist(currentPlaylist.id, fromIndex, toIndex)
                },
                onDeletePlaylist = {
                    viewModel.deletePlaylist(currentPlaylist)
                    viewModel.selectedPlaylistId = null
                }
            )
            return
        }
    }

    var activeLibraryTab by remember { mutableStateOf("local") } // "cloud", "local", "playlists", "downloads"
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showAddLocalMediaDialog by remember { mutableStateOf(false) }
    var showEditLocalMediaDialog by remember { mutableStateOf<MediaItemEntity?>(null) }
    var showDeleteLocalMediaConfirm by remember { mutableStateOf<MediaItemEntity?>(null) }
    var selectedLocalGenre by remember { mutableStateOf("All") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Library",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (activeLibraryTab == "playlists") {
                IconButton(onClick = { showCreatePlaylistDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Create new playlist", tint = MaterialTheme.colorScheme.primary)
                }
            } else if (activeLibraryTab == "local") {
                IconButton(onClick = { showAddLocalMediaDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add local media file", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Custom library segments
        ScrollableTabRow(
            selectedTabIndex = when (activeLibraryTab) {
                "local" -> 0
                "playlists" -> 1
                "explorer" -> 2
                else -> 0
            },
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            edgePadding = 0.dp
        ) {
            val tabs = listOf(
                "local" to "Local Library",
                "playlists" to "Playlists",
                "explorer" to "File Explorer"
            )
            tabs.forEachIndexed { index, (id, label) ->
                Tab(
                    selected = activeLibraryTab == id,
                    onClick = { activeLibraryTab = id },
                    text = { Text(label, fontWeight = FontWeight.Bold) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (activeLibraryTab) {
            "local" -> {
                val localItems = items.filter { it.isLocal || it.isDownloaded }
                if (localItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.FolderOpen,
                                contentDescription = "Interactive element",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No local files detected.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    viewModel.importSampleLocalTrack()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                            ) {
                                Text("Import Sample Local Media")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    showAddLocalMediaDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onPrimary)
                            ) {
                                Text("Add Custom Media")
                            }
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Category (genre) filter row
                        val genres = listOf("All") + localItems.map { it.genre }.distinct()
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            items(genres) { genre ->
                                FilterChip(
                                    selected = selectedLocalGenre == genre,
                                    onClick = { selectedLocalGenre = genre },
                                    label = { Text(genre) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        labelColor = MaterialTheme.colorScheme.onBackground
                                    )
                                )
                            }
                        }

                        val filteredLocalItems = if (selectedLocalGenre == "All") {
                            localItems
                        } else {
                            localItems.filter { it.genre == selectedLocalGenre }
                        }

                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                            items(filteredLocalItems) { item ->
                                LocalMediaListRow(
                                    item = item,
                                    onClick = { onPlay(item) },
                                    onEdit = { showEditLocalMediaDialog = item },
                                    onDelete = { showDeleteLocalMediaConfirm = item },
                                    onAddPlaylist = { onAddToPlaylist(item) }
                                )
                            }
                        }
                    }
                }
            }
            "playlists" -> {
                if (playlists.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Create your first playlist to begin.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(playlists) { playlist ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectedPlaylistId = playlist.id
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Filled.QueueMusic, contentDescription = "Interactive element", tint = MaterialTheme.colorScheme.primary)
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text(playlist.name, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                            Text(playlist.description, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 12.sp)
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { viewModel.exportPlaylistToM3U(playlist) }) {
                                            Icon(Icons.Filled.Download, contentDescription = "Export to M3U", tint = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(onClick = { viewModel.deletePlaylist(playlist) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete Playlist", tint = MaterialTheme.colorScheme.tertiary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "explorer" -> {
                FileExplorerView(viewModel = viewModel, onPlay = onPlay)
            }
        }

        if (showCreatePlaylistDialog) {
            CreatePlaylistDialog(
                onCreate = { name, desc ->
                    viewModel.createPlaylist(name, desc)
                    showCreatePlaylistDialog = false
                },
                onDismiss = { showCreatePlaylistDialog = false }
            )
        }

        if (showAddLocalMediaDialog) {
            AddLocalMediaDialog(
                onAdd = { item ->
                    viewModel.insertMediaItem(item)
                    showAddLocalMediaDialog = false
                },
                onDismiss = { showAddLocalMediaDialog = false }
            )
        }

        showEditLocalMediaDialog?.let { item ->
            EditLocalMediaDialog(
                item = item,
                onSave = { updated ->
                    viewModel.updateMediaItem(updated)
                    showEditLocalMediaDialog = null
                },
                onDismiss = { showEditLocalMediaDialog = null }
            )
        }

        showDeleteLocalMediaConfirm?.let { item ->
            AlertDialog(
                onDismissRequest = { showDeleteLocalMediaConfirm = null },
                title = { Text("Delete Local Media", color = MaterialTheme.colorScheme.onBackground) },
                text = { Text("Are you sure you want to permanently delete \"${item.title}\"? This action cannot be undone.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)) },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteMediaItem(item)
                            showDeleteLocalMediaConfirm = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onBackground)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteLocalMediaConfirm = null }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}

@Composable
fun FileExplorerView(
    viewModel: MediaViewModel,
    onPlay: (MediaItemEntity) -> Unit
) {
    var currentPath by remember { mutableStateOf("/") }

    val rootItems = listOf(
        ExplorerItem("Music", "/Music", isDirectory = true),
        ExplorerItem("Movies", "/Movies", isDirectory = true),
        ExplorerItem("Downloads", "/Downloads", isDirectory = true)
    )

    val musicItems = listOf(
        ExplorerItem("Lofi Chill Beats.mp3", "/Music/Lofi Chill Beats.mp3", isDirectory = false,
            mediaItem = MediaItemEntity(
                id = "local_lofi_01",
                title = "Lofi Chill Beats",
                artist = "Soothe Waves",
                album = "Explorer Local",
                duration = 105000,
                url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                isVideo = false,
                isLocal = true,
                isDownloaded = false,
                thumbnailUrl = "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=300&auto=format&fit=crop",
                genre = "Lofi"
            )
        ),
        ExplorerItem("Deep Focus Ambient.mp3", "/Music/Deep Focus Ambient.mp3", isDirectory = false,
            mediaItem = MediaItemEntity(
                id = "local_ambient_01",
                title = "Deep Focus Ambient",
                artist = "Zen Space",
                album = "Explorer Local",
                duration = 150000,
                url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                isVideo = false,
                isLocal = true,
                isDownloaded = false,
                thumbnailUrl = "https://images.unsplash.com/photo-1497493292307-31c376b6e479?w=300&auto=format&fit=crop",
                genre = "Ambient"
            )
        ),
        ExplorerItem("Synthwave Odyssey.mp3", "/Music/Synthwave Odyssey.mp3", isDirectory = false,
            mediaItem = MediaItemEntity(
                id = "local_synth_01",
                title = "Synthwave Odyssey",
                artist = "Cyber Runner",
                album = "Explorer Local",
                duration = 195000,
                url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
                isVideo = false,
                isLocal = true,
                isDownloaded = false,
                thumbnailUrl = "https://images.unsplash.com/photo-1515462277126-270d878326e5?w=300&auto=format&fit=crop",
                genre = "Synthwave"
            )
        )
    )

    val moviesItems = listOf(
        ExplorerItem("Sintel Movie Clip.mp4", "/Movies/Sintel Movie Clip.mp4", isDirectory = false,
            mediaItem = MediaItemEntity(
                id = "local_video_01",
                title = "Sintel Movie Clip (Local)",
                artist = "Durian Open Movie Project",
                album = "Local Drive",
                duration = 52000,
                url = "https://storage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                isVideo = true,
                isLocal = true,
                isDownloaded = true,
                thumbnailUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=500&auto=format&fit=crop",
                genre = "Fantasy"
            )
        ),
        ExplorerItem("Big Buck Bunny.mp4", "/Movies/Big Buck Bunny.mp4", isDirectory = false,
            mediaItem = MediaItemEntity(
                id = "local_video_02",
                title = "Big Buck Bunny (Local)",
                artist = "Blender Foundation",
                album = "Local Drive",
                duration = 596000,
                url = "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                isVideo = true,
                isLocal = true,
                isDownloaded = false,
                thumbnailUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=500&auto=format&fit=crop",
                genre = "Animation"
            )
        ),
        ExplorerItem("Cosmic Synthesizer.mp4", "/Movies/Cosmic Synthesizer.mp4", isDirectory = false,
            mediaItem = MediaItemEntity(
                id = "local_video_03",
                title = "Cosmic Synthesizer Wave",
                artist = "Stellar AudioLabs",
                album = "Local Drive",
                duration = 135000,
                url = "https://storage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                isVideo = true,
                isLocal = true,
                isDownloaded = false,
                thumbnailUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=500&auto=format&fit=crop",
                genre = "Synthwave"
            )
        )
    )

    val downloadsItems = listOf(
        ExplorerItem("Acoustic Cover.mp3", "/Downloads/Acoustic Cover.mp3", isDirectory = false,
            mediaItem = MediaItemEntity(
                id = "local_acoustic_01",
                title = "Acoustic Cover",
                artist = "Guitar Breeze",
                album = "Local Drive",
                duration = 125000,
                url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
                isVideo = false,
                isLocal = true,
                isDownloaded = true,
                thumbnailUrl = "https://images.unsplash.com/photo-1510915228340-29c85a43dcfe?w=300&auto=format&fit=crop",
                genre = "Acoustic"
            )
        ),
        ExplorerItem("Nature Soundscapes.mp3", "/Downloads/Nature Soundscapes.mp3", isDirectory = false,
            mediaItem = MediaItemEntity(
                id = "local_nature_01",
                title = "Nature Soundscapes",
                artist = "Eco Forest",
                album = "Local Drive",
                duration = 300000,
                url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3",
                isVideo = false,
                isLocal = true,
                isDownloaded = true,
                thumbnailUrl = "https://images.unsplash.com/photo-1441974231531-c6227db76b6e?w=300&auto=format&fit=crop",
                genre = "Ambient"
            )
        )
    )

    val currentItems = when (currentPath) {
        "/" -> rootItems
        "/Music" -> musicItems
        "/Movies" -> moviesItems
        "/Downloads" -> downloadsItems
        else -> emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
            .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Storage, contentDescription = "Interactive element", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = "Local Storage",
                color = if (currentPath == "/") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier
                    .clickable { currentPath = "/" }
                    .padding(4.dp)
            )

            if (currentPath != "/") {
                Text("/", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f), modifier = Modifier.padding(horizontal = 4.dp))
                Text(
                    text = currentPath.substringAfter("/"),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(4.dp)
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            if (currentPath != "/") {
                IconButton(
                    onClick = { currentPath = "/" },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Up", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(16.dp))
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))

        if (currentItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("Folder is empty", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(currentItems) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.02f), RoundedCornerShape(6.dp))
                            .clickable {
                                if (item.isDirectory) {
                                    currentPath = item.path
                                } else {
                                    item.mediaItem?.let { onPlay(it) }
                                }
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val icon = when {
                            item.isDirectory -> Icons.Filled.Folder
                            item.mediaItem?.isVideo == true -> Icons.Filled.VideoFile
                            else -> Icons.Filled.AudioFile
                        }
                        
                        val iconTint = when {
                            item.isDirectory -> MaterialTheme.colorScheme.secondary
                            item.mediaItem?.isVideo == true -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        }

                        Icon(
                            imageVector = icon,
                            contentDescription = "Interactive element",
                            tint = iconTint,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                            if (!item.isDirectory && item.mediaItem != null) {
                                Text(
                                    text = "${item.mediaItem.artist} • ${item.mediaItem.duration.toLong().formatTime()}",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            } else if (item.isDirectory) {
                                Text(
                                    text = "System Directory",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        if (!item.isDirectory && item.mediaItem != null) {
                            Row {
                                IconButton(
                                    onClick = {
                                        viewModel.addToQueue(item.mediaItem)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.QueueMusic,
                                        contentDescription = "Add to Queue",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                
                                IconButton(
                                    onClick = {
                                        onPlay(item.mediaItem)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "Play",
                                        tint = Color.Green,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        viewModel.insertMediaItem(item.mediaItem)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Save,
                                        contentDescription = "Import to Library",
                                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        } else if (item.isDirectory) {
                            Button(
                                onClick = {
                                    val filesToImport = when (item.path) {
                                        "/Music" -> musicItems
                                        "/Movies" -> moviesItems
                                        "/Downloads" -> downloadsItems
                                        else -> emptyList()
                                    }
                                    filesToImport.forEach { file ->
                                        file.mediaItem?.let { viewModel.insertMediaItem(it) }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                                    contentColor = MaterialTheme.colorScheme.onBackground
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text("Scan & Import", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

data class ExplorerItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val mediaItem: MediaItemEntity? = null
)

// 6. FULL-SCREEN PLAYER SCREEN
@Composable
fun FullScreenPlayer(
    viewModel: MediaViewModel,
    onCollapse: () -> Unit,
    onShowQueue: () -> Unit,
    onShowEqualizer: () -> Unit,
    onShowSleepTimer: () -> Unit
) {
    val current = viewModel.currentItem ?: return

    // Intercept hardware back gesture to collapse the player cleanly!
    BackHandler {
        if (current.isVideo && viewModel.isFullScreenVideo) {
            viewModel.isFullScreenVideo = false
        } else {
            onCollapse()
        }
    }

    if (current.isVideo && viewModel.isFullScreenVideo) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("full_screen_player")
        ) {
            VideoPlayerContainer(viewModel = viewModel, current = current)
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF070B14))
                .verticalScroll(rememberScrollState())
                .testTag("full_screen_player")
        ) {
            // Player Scaffolding Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCollapse, modifier = Modifier.testTag("player_collapse_btn")) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Collapse", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(32.dp))
                }
                Text(
                    text = if (current.isVideo) "VIDEO PLAYER" else "MUSIC MODE",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 2.sp
                )
                Row {
                    IconButton(onClick = onShowSleepTimer) {
                        Icon(Icons.Outlined.Timer, contentDescription = "Open sleep timer", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    IconButton(onClick = onShowQueue) {
                        Icon(Icons.Filled.QueueMusic, contentDescription = "Play Queue", tint = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }

            // Active Player Screen body
            if (current.isVideo) {
                VideoPlayerContainer(viewModel = viewModel, current = current)
            } else {
                MusicPlayerContainer(viewModel = viewModel, current = current, onShowEqualizer = onShowEqualizer)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Playback Controls Panel
            PlaybackControlsSection(viewModel = viewModel)
        }
    }
}

// VIDEO MODE COMPONENT WITH INTUITIVE SWIPE GESTURES & OVERLAYS
@Composable
fun VideoPlayerContainer(viewModel: MediaViewModel, current: MediaItemEntity) {
    val context = LocalContext.current
    val isInPipMode = rememberIsInPipMode()
    var isDragging by remember { mutableStateOf(false) }
    var gestureInfoText by remember { mutableStateOf("") }
    var showVideoControls by remember { mutableStateOf(true) }

    // Auto-hide controls after 5 seconds of play
    LaunchedEffect(showVideoControls, viewModel.playbackState) {
        if (showVideoControls && viewModel.playbackState == PlaybackState.Playing) {
            delay(5000)
            showVideoControls = false
        }
    }

    val playerModifier = if (viewModel.isFullScreenVideo || isInPipMode) {
        Modifier.fillMaxSize()
    } else {
        Modifier.fillMaxWidth().aspectRatio(16 / 9f)
    }

    Box(
        modifier = playerModifier
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        val screenWidth = size.width
                        if (offset.x < screenWidth / 2) {
                            viewModel.rewind10s()
                            gestureInfoText = "Rewind 10s"
                        } else {
                            viewModel.forward10s()
                            gestureInfoText = "Fast Forward 10s"
                        }
                    },
                    onTap = {
                        if (!viewModel.isControlsLocked) {
                            showVideoControls = !showVideoControls
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = {
                        isDragging = false
                        gestureInfoText = ""
                    },
                    onDrag = { change, dragAmount ->
                        val screenWidth = size.width
                        val xPos = change.position.x
                        if (!viewModel.isControlsLocked) {
                            if (xPos < screenWidth / 2) {
                                val delta = -dragAmount.y / 500f
                                val newBright = (viewModel.brightness + delta).coerceIn(0f, 1f)
                                viewModel.updateBrightness(newBright)
                                gestureInfoText = "Brightness: ${(newBright * 100).toInt()}%"
                            } else {
                                val delta = -dragAmount.y / 500f
                                val newVol = (viewModel.volume + delta).coerceIn(0f, 1f)
                                viewModel.updateVolume(newVol)
                                gestureInfoText = "Volume: ${(newVol * 100).toInt()}%"
                            }
                        }
                    }
                )
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    val surfaceView = SurfaceView(ctx)
                    surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            viewModel.setDisplay(holder)
                        }
                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {}
                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            viewModel.setDisplay(null)
                        }
                    })
                    addView(surfaceView)
                }
            }
        )

        if (viewModel.playbackState is PlaybackState.Loading || viewModel.playbackState is PlaybackState.Buffering) {
            AsyncImage(
                model = current.thumbnailUrl,
                contentDescription = "Interactive element",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        if (isDragging && gestureInfoText.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(gestureInfoText, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        if (!isInPipMode) {
            IconButton(
                onClick = { viewModel.isControlsLocked = !viewModel.isControlsLocked },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = if (viewModel.isControlsLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                    contentDescription = "Lock controls",
                    tint = if (viewModel.isControlsLocked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onBackground
                )
            }
            
            IconButton(
                onClick = { enterPipMode(context as? Activity) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.PictureInPictureAlt,
                    contentDescription = "Picture in Picture",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // Custom video player control bar component
        AnimatedVisibility(
            visible = showVideoControls && !viewModel.isControlsLocked && !isInPipMode,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                val durationMs = viewModel.duration
                val positionMs = viewModel.currentPosition
                val progress = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = positionMs.formatTime(),
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Slider(
                        value = progress,
                        onValueChange = { newProgress ->
                            val targetMs = (newProgress * durationMs).toLong()
                            viewModel.seekTo(targetMs)
                        },
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.24f),
                            thumbColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .height(24.dp)
                    )
                    
                    Text(
                        text = durationMs.formatTime(),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.togglePlayPause() }
                        ) {
                            Icon(
                                imageVector = if (viewModel.playbackState == PlaybackState.Playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "Play or pause media",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        IconButton(onClick = { viewModel.rewind10s() }) {
                            Icon(Icons.Filled.Replay10, contentDescription = "Rewind 10s", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
                        }
                        
                        IconButton(onClick = { viewModel.forward10s() }) {
                            Icon(Icons.Filled.Forward10, contentDescription = "Forward 10s", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(20.dp))
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))

                        Icon(
                            imageVector = if (viewModel.volume == 0f) Icons.Filled.VolumeMute else Icons.Filled.VolumeUp,
                            contentDescription = "Volume",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable {
                                    viewModel.updateVolume(if (viewModel.volume > 0f) 0f else 0.8f)
                                }
                        )
                        
                        Slider(
                            value = viewModel.volume,
                            onValueChange = { viewModel.updateVolume(it) },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                activeTrackColor = MaterialTheme.colorScheme.onBackground,
                                inactiveTrackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                                thumbColor = MaterialTheme.colorScheme.onBackground
                            ),
                            modifier = Modifier
                                .width(90.dp)
                                .padding(horizontal = 6.dp)
                                .height(20.dp)
                        )
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                viewModel.selectedResolution = if (viewModel.selectedResolution == "1080p") "720p" else "1080p"
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(viewModel.selectedResolution, fontSize = 8.sp, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                            }
                        }
                        IconButton(
                            onClick = {
                                val nextSpeed = when (viewModel.playbackSpeed) {
                                    1.0f -> 1.5f
                                    1.5f -> 2.0f
                                    2.0f -> 0.5f
                                    else -> 1.0f
                                }
                                viewModel.setSpeed(nextSpeed)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("${viewModel.playbackSpeed}x", fontSize = 8.sp, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                            }
                        }
                        IconButton(
                            onClick = {
                                viewModel.activeSubtitleLanguage = if (viewModel.activeSubtitleLanguage == "English") "Off" else "English"
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ClosedCaption,
                                contentDescription = "Subtitles",
                                tint = if (viewModel.activeSubtitleLanguage == "English") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = { enterPipMode(context as? Activity) }) {
                            Icon(Icons.Filled.PictureInPicture, contentDescription = "PIP", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(18.dp))
                        }
                        
                        IconButton(
                            onClick = { viewModel.isFullScreenVideo = !viewModel.isFullScreenVideo }
                        ) {
                            Icon(
                                imageVector = if (viewModel.isFullScreenVideo) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                contentDescription = "Fullscreen Toggle",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }

        if (viewModel.isPipActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.PictureInPicture, contentDescription = "Interactive element", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Picture-in-Picture Active", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                    Text("The video is floating in background", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }
        }

        if (viewModel.activeSubtitleLanguage != "Off" && viewModel.currentSubtitleText.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (showVideoControls && !viewModel.isControlsLocked) 90.dp else 24.dp)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = viewModel.currentSubtitleText,
                    color = Color.Yellow,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// MUSIC MODE COMPONENTS (ROATING ARTWORK & CANVASED EQUALIZER)
@Composable
fun MusicPlayerContainer(viewModel: MediaViewModel, current: MediaItemEntity, onShowEqualizer: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "disc_rotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Glowing orbital rotating disc artwork
        Box(
            modifier = Modifier
                .size(230.dp)
                .background(Color.Black, CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Rotating Album Art Disc
            AsyncImage(
                model = current.thumbnailUrl,
                contentDescription = "Interactive element",
                modifier = Modifier
                    .size(210.dp)
                    .rotate(if (viewModel.playbackState is PlaybackState.Playing) rotationAngle else 0f)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            // Center vinyl record hole
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFF070B14), CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f), CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Title and artist with Favorite & Playlist triggers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = current.title,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = current.artist,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            IconButton(onClick = { viewModel.toggleFavorite(current) }) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "Favorite",
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Advanced Interactive custom canvas-drawn audio waveform
        CanvasWaveformVisualizer(viewModel = viewModel)

        Spacer(modifier = Modifier.height(16.dp))

        // Dynamic scrolling synced lyrics display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(horizontal = 24.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            val lyric = viewModel.currentSubtitleText.ifEmpty { "Enjoying beautiful high-fidelity playback..." }
            Text(
                text = lyric,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 14.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Quick equalizer button
        OutlinedButton(
            onClick = onShowEqualizer,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        ) {
            Icon(Icons.Filled.Equalizer, contentDescription = "Interactive element", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open Pro Equalizer", fontSize = 12.sp)
        }
    }
}

// PREMIUM CANVAS DRAWN AUDIO WAVEFORM VISUALIZER
@Composable
fun CanvasWaveformVisualizer(viewModel: MediaViewModel) {
    val isPlaying = viewModel.playbackState is PlaybackState.Playing
    val waveAnim = rememberInfiniteTransition(label = "wave_oscillation")
    val phase by waveAnim.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val randomValues = remember { List(40) { Math.random().toFloat() } }
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 24.dp)
    ) {
        val width = size.width
        val height = size.height
        val midY = height / 2f
        val barsCount = 40
        val barWidth = width / (barsCount * 1.5f)

        for (i in 0 until barsCount) {
            val x = i * barWidth * 1.5f
            val baseAmplitude = (height * 0.1f)
            
            // Dynamic frequency simulation: blending sine waves and pre-generated noise
            val amplitude = if (isPlaying) {
                val wave1 = Math.sin(i.toDouble() / barsCount * Math.PI * 2 + phase).toFloat()
                val wave2 = cos(i.toDouble() / barsCount * Math.PI * 4 - phase * 1.5).toFloat()
                val noise = randomValues[i] * Math.sin(phase.toDouble() * 4 + i).toFloat() 
                
                val combined = (wave1 * 0.5f + wave2 * 0.3f + noise * 0.5f).absoluteValue
                (height * 0.45f) * combined + baseAmplitude
            } else {
                baseAmplitude
            }
            val yOffset = amplitude.coerceIn(2.dp.toPx(), height / 2f)

            // Draw balanced symmetric audio visualizer lines
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(secondaryColor, primaryColor, secondaryColor),
                    startY = midY - yOffset,
                    endY = midY + yOffset
                ),
                topLeft = androidx.compose.ui.geometry.Offset(x, midY - yOffset),
                size = androidx.compose.ui.geometry.Size(barWidth, yOffset * 2),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
        }
    }
}

// 7. PLAYBACK STATE PANEL WITH REPEAT, SHUFFLE, SEEK SLIDER
@Composable
fun PlaybackControlsSection(viewModel: MediaViewModel) {
    val durationText = viewModel.duration.formatTime()
    val sliderValue = if (viewModel.duration > 0) viewModel.currentPosition.toFloat() / viewModel.duration else 0f

    var isDragging by remember { mutableStateOf(false) }
    var draggingProgress by remember { mutableStateOf(0f) }

    val activeSliderValue = if (isDragging) draggingProgress else sliderValue
    val activePositionMs = if (isDragging) (draggingProgress * viewModel.duration).toLong() else viewModel.currentPosition
    val progressText = activePositionMs.formatTime()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        // Seek Bar Slider
        Slider(
            value = activeSliderValue,
            onValueChange = { newVal ->
                isDragging = true
                draggingProgress = newVal
            },
            onValueChangeFinished = {
                val target = (draggingProgress * viewModel.duration).toLong()
                viewModel.seekTo(target)
                isDragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
            )
        )

        // Elapsed and remaining duration HUD
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(progressText, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 12.sp)
            Text(durationText, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Center Buttons Row (Shuffle, Prev, Rewind 10s, Play/Pause, Forward 10s, Next, Repeat)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle
            IconButton(onClick = { viewModel.toggleShuffle() }) {
                Icon(
                    imageVector = Icons.Filled.Shuffle,
                    contentDescription = "Shuffle queue",
                    tint = if (viewModel.isShuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }

            // Previous
            IconButton(onClick = { viewModel.skipToPrevious() }) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "Previous",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Skip Backward 10 Seconds
            IconButton(onClick = { viewModel.rewind10s() }) {
                Icon(
                    imageVector = Icons.Filled.Replay10,
                    contentDescription = "Rewind 10 Seconds",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Main Play/Pause Sphere button
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable { viewModel.togglePlayPause() }
                    .testTag("expanded_play_pause"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (viewModel.playbackState is PlaybackState.Playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play or Pause",
                    tint = Color.Black,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Skip Forward 10 Seconds
            IconButton(onClick = { viewModel.forward10s() }) {
                Icon(
                    imageVector = Icons.Filled.Forward10,
                    contentDescription = "Forward 10 Seconds",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Next
            IconButton(onClick = { viewModel.skipToNext() }) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Skip to next track",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Repeat Mode
            IconButton(onClick = { viewModel.toggleRepeatMode() }) {
                val iconColor = if (viewModel.repeatMode != PlaybackRepeatMode.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                val icon = when (viewModel.repeatMode) {
                    PlaybackRepeatMode.ONE -> Icons.Filled.RepeatOne
                    else -> Icons.Filled.Repeat
                }
                Icon(
                    imageVector = icon,
                    contentDescription = "Repeat mode",
                    tint = iconColor
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Speed Selector + Offline Download trigger Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Speed Controller selector
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Speed: ", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 12.sp)
                val speeds = listOf(0.5f, 1.0f, 1.5f, 2.0f)
                speeds.forEach { speed ->
                    Text(
                        text = "${speed}x",
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .clickable { viewModel.setSpeed(speed) },
                        color = if (viewModel.playbackSpeed == speed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                        fontWeight = if (viewModel.playbackSpeed == speed) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                }
            }

            // Download trigger
            val item = viewModel.currentItem
            if (item != null && !item.isLocal) {
                val isSaved = viewModel.downloadTasks.any { it.mediaItemId == item.id && it.status == "Completed" }
                val isDownloading = viewModel.downloadTasks.any { it.mediaItemId == item.id && it.status == "Downloading" }

                OutlinedButton(
                    onClick = {
                        if (!isSaved && !isDownloading) {
                            viewModel.startDownload(item)
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Filled.FileDownloadDone else Icons.Filled.FileDownload,
                        contentDescription = "Download stream",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isSaved) "Offline Saved" else if (isDownloading) "Downloading..." else "Save Offline", fontSize = 10.sp)
                }
            }
        }
    }
}

// 8. SETTINGS SCREEN
@Composable
fun SettingsScreen(viewModel: MediaViewModel, settings: AppSettingsEntity) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Settings",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Visual Theme customization options
        SettingSectionHeader(title = "App Customization")
        SettingRowThemeSelector(
            currentTheme = settings.themeMode,
            onSelect = { viewModel.updateTheme(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Playback defaults setting
        SettingSectionHeader(title = "Playback Defaults")
        SettingRowDropdown(
            title = "Streaming Quality",
            value = settings.defaultStreamingQuality,
            options = listOf("Auto", "Low (480p)", "Medium (720p)", "High (1080p)", "Pro Lossless")
        )
        SettingRowDropdown(
            title = "Download Quality",
            value = settings.defaultDownloadQuality,
            options = listOf("Low", "Medium", "High (1080p)")
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Accessibility settings
        SettingSectionHeader(title = "Accessibility")
        SettingRowSwitch(
            title = "Closed Captions (CC)",
            checked = settings.isClosedCaptionsEnabled,
            onCheckedChange = { viewModel.updateCC(it) }
        )
        SettingRowSwitch(
            title = "High Contrast Mode",
            checked = settings.isHighContrastEnabled,
            onCheckedChange = { viewModel.updateHighContrast(it) }
        )
        SettingRowSlider(
            title = "Text Sizing Multiplier",
            value = settings.textSizeMultiplier,
            range = 0.8f..1.6f,
            onValueChange = { viewModel.updateTextSizeMultiplier(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Offline storage cache settings
        SettingSectionHeader(title = "Storage & Cache")
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Temporary Playback Cache", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                    Text("Currently using: 34.2 MB", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 12.sp)
                }
                Button(
                    onClick = { viewModel.clearCache() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onBackground)
                ) {
                    Text("Clear Cache", fontSize = 12.sp)
                }
            }
        }
    }
}

// 9. PROFILE SCREEN
@Composable
fun ProfileScreen(viewModel: MediaViewModel, history: List<PlaybackHistoryEntity>, items: List<MediaItemEntity>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Profile Info Segment
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.AccountBox, contentDescription = "Interactive element", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Stellar Listener", color = MaterialTheme.colorScheme.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Account Tier: Premium Plus", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Device Synchronization Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Device Synced", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                    Text("Synced with Android TV & Mac", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 12.sp)
                }
                IconButton(onClick = { /* Device sync sync call */ }) {
                    Icon(Icons.Filled.Sync, contentDescription = "Sync", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Historical Activity List
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Watch & Listening History", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = {
                // Clear History list
            }) {
                Text("Clear All", color = MaterialTheme.colorScheme.tertiary)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (history.isEmpty()) {
            EmptyStateCard("History is empty. Stream songs or videos to log history.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                history.take(4).forEach { log ->
                    val matchingItem = items.firstOrNull { it.id == log.mediaItemId }
                    if (matchingItem != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(
                                        model = matchingItem.thumbnailUrl,
                                        contentDescription = "Interactive element",
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(matchingItem.title, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text("Stream count: ${log.playCount} times", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                                    }
                                }
                                Icon(
                                    imageVector = if (matchingItem.isVideo) Icons.Filled.Movie else Icons.Filled.MusicNote,
                                    contentDescription = "Interactive element",
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// 10. REUSABLE ATOM COMPONENT LAYOUTS
@Composable
fun SectionHeader(title: String, actionText: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (actionText != null && onAction != null) {
            Text(
                text = actionText,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onAction)
            )
        }
    }
}

@Composable
fun MediaCompactCard(item: MediaItemEntity, onClick: () -> Unit, onAddPlaylist: () -> Unit) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Box {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = "Interactive element",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentScale = ContentScale.Crop
                )
                // Video vs Audio play badge indicator
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = if (item.isVideo) Icons.Filled.Movie else Icons.Filled.MusicNote,
                        contentDescription = "Interactive element",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(item.title, color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.artist, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun MediaListRow(
    item: MediaItemEntity,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    onAddPlaylist: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = "Interactive element",
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop
                    )
                    // Visual play status mini ripple
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                    ) {
                        val ext = item.url.substringAfterLast('.', "").uppercase().takeIf { it.isNotEmpty() } ?: if (item.isVideo) "VIDEO" else "AUDIO"
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                            Icon(
                                imageVector = if (item.isVideo) Icons.Filled.Movie else Icons.Filled.AudioFile,
                                contentDescription = "Interactive element",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = ext,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(item.title, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.artist, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onFavorite) {
                    Icon(Icons.Filled.FavoriteBorder, contentDescription = "Add to Favorites", tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
                IconButton(onClick = onAddPlaylist) {
                    Icon(Icons.Filled.PlaylistAdd, contentDescription = "Add to Playlist", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun PlaylistGlowCard(title: String, description: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(180.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = "Interactive element", tint = color)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(description, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun EmptyStateCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
    ) {
        Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
            Text(message, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 12.sp)
        }
    }
}

// 11. DIALOGS (EQUALIZER, SLEEP TIMER, PLAYLIST MODALS)
@Composable
fun EqualizerDialog(viewModel: MediaViewModel, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Professional Equalizer", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))

                // Presets Dropdown
                Text("Preset Mode", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), fontSize = 12.sp)
                val presets = listOf("Normal", "Bass Boost", "Vocal Boost", "Classical", "Dance", "Pop", "Rock")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presets) { preset ->
                        FilterChip(
                            selected = false, // simple toggle trigger
                            onClick = { viewModel.applyEqPreset(preset) },
                            label = { Text(preset) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // EQ Bands Sliders
                Text("EQ Bands Control", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))

                EqBandSlider(title = "Bass (60Hz)", value = viewModel.eqBass, onValueChange = { viewModel.eqBass = it })
                EqBandSlider(title = "Vocal (910Hz)", value = viewModel.eqVocal, onValueChange = { viewModel.eqVocal = it })
                EqBandSlider(title = "Treble (14kHz)", value = viewModel.eqTreble, onValueChange = { viewModel.eqTreble = it })

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text("Apply Changes")
                }
            }
        }
    }
}

@Composable
fun EqBandSlider(title: String, value: Float, onValueChange: (Float) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = 12.sp)
            Text("${value.toInt() - 50} dB", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
fun SleepTimerDialog(viewModel: MediaViewModel, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Set Sleep Timer", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))

                val options = listOf(
                    Triple(0, "Disabled", "No timer"),
                    Triple(5, "5 min", "Sleep in 5 minutes"),
                    Triple(15, "15 min", "Sleep in 15 minutes"),
                    Triple(30, "30 min", "Sleep in 30 minutes"),
                    Triple(60, "60 min", "Sleep in 1 hour")
                )

                options.forEach { (minutes, label, desc) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setSleepTimer(minutes)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(label, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                            Text(desc, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 12.sp)
                        }
                        if (viewModel.sleepTimerRemainingSec > 0 && minutes > 0 && (viewModel.sleepTimerRemainingSec / 60).toInt() <= minutes) {
                            Icon(Icons.Filled.Check, contentDescription = "Interactive element", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddToPlaylistDialog(
    viewModel: MediaViewModel,
    mediaItem: MediaItemEntity,
    playlists: List<PlaylistEntity>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Save to Playlist", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))

                if (playlists.isEmpty()) {
                    Text("No playlists found. Create one in the Library section first.", color = MaterialTheme.colorScheme.onBackground)
                } else {
                    playlists.forEach { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.addToPlaylist(playlist.id, mediaItem.id)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.QueueMusic, contentDescription = "Interactive element", tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(playlist.name, color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancel", color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

@Composable
fun CreatePlaylistDialog(onCreate: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Create New Playlist", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist Name") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onBackground, unfocusedTextColor = MaterialTheme.colorScheme.onBackground)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onBackground, unfocusedTextColor = MaterialTheme.colorScheme.onBackground)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = MaterialTheme.colorScheme.tertiary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { if (name.isNotEmpty()) onCreate(name, desc) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                    ) {
                        Text("Create")
                    }
                }
            }
        }
    }
}

@Composable
fun QueueDialog(viewModel: MediaViewModel, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Playing Queue", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    if (viewModel.playQueue.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearQueue() }) {
                            Text("Clear All", color = MaterialTheme.colorScheme.tertiary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (viewModel.playQueue.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Queue is empty", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(viewModel.playQueue) { index, item ->
                            val isPlaying = viewModel.currentQueueIndex == index
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isPlaying) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.02f))
                                    .clickable {
                                        viewModel.playMedia(item, viewModel.playQueue)
                                    }
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("#${index + 1}", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    
                                    var artUrl by remember { mutableStateOf<String?>(null) }
                                    LaunchedEffect(item.url) {
                                        artUrl = if (item.isLocal) {
                                            viewModel.extractAndCacheAlbumArt(item.url, item.id) ?: item.thumbnailUrl
                                        } else {
                                            item.thumbnailUrl
                                        }
                                    }
                                    
                                    AsyncImage(
                                        model = artUrl,
                                        contentDescription = "Thumbnail",
                                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    
                                    Column {
                                        Text(
                                            text = item.title,
                                            color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.artist,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Move Up
                                    IconButton(
                                        onClick = { viewModel.moveQueueItemUp(index) },
                                        enabled = index > 0,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Filled.ArrowUpward, contentDescription = "Move Up", tint = if (index > 0) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f), modifier = Modifier.size(14.dp))
                                    }
                                    // Move Down
                                    IconButton(
                                        onClick = { viewModel.moveQueueItemDown(index) },
                                        enabled = index < viewModel.playQueue.size - 1,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Filled.ArrowDownward, contentDescription = "Move Down", tint = if (index < viewModel.playQueue.size - 1) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f), modifier = Modifier.size(14.dp))
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    // Remove
                                    IconButton(
                                        onClick = { viewModel.removeFromQueue(item) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

// SETTINGS ATOM COMPONENTS
@Composable
fun SettingSectionHeader(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun SettingRowThemeSelector(currentTheme: String, onSelect: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("App Theme", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val themes = listOf("light" to "Light", "dark" to "Dark", "system" to "System")
                themes.forEach { (id, label) ->
                    val isSelected = currentTheme == id
                    Button(
                        onClick = { onSelect(id) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isSelected) Color.Black else MaterialTheme.colorScheme.onBackground
                        )
                    ) {
                        Text(label)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingRowSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            )
        }
    }
}

@Composable
fun SettingRowSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                Text(String.format("%.1fx", value), color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
fun SettingRowDropdown(title: String, value: String, options: List<String>) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(title, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                Text(value, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Interactive element", tint = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
fun PlaylistDetailScreen(
    playlist: PlaylistEntity,
    items: List<MediaItemEntity>,
    onBack: () -> Unit,
    onPlayItem: (MediaItemEntity) -> Unit,
    onRemoveItem: (MediaItemEntity) -> Unit,
    onReorderItem: (Int, Int) -> Unit,
    onDeletePlaylist: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Playlist Details",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            IconButton(onClick = onDeletePlaylist) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete Playlist", tint = MaterialTheme.colorScheme.tertiary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = playlist.name,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = playlist.description.ifEmpty { "No description provided." },
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "${items.size} items",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("This playlist is empty. Add tracks from Home or Search tab!", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), textAlign = TextAlign.Center)
            }
        } else {
            Button(
                onClick = { onPlayItem(items.first()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play all media")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Play All", fontWeight = FontWeight.Bold)
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(items) { index, item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayItem(item) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("#${index + 1}", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                AsyncImage(
                                    model = item.thumbnailUrl,
                                    contentDescription = "Interactive element",
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(item.title, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(item.artist, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onReorderItem(index, index - 1) },
                                    enabled = index > 0,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Move Up", tint = if (index > 0) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f), modifier = Modifier.size(14.dp))
                                }
                                IconButton(
                                    onClick = { onReorderItem(index, index + 1) },
                                    enabled = index < items.size - 1,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Move Down", tint = if (index < items.size - 1) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f), modifier = Modifier.size(14.dp))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(onClick = { onRemoveItem(item) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Filled.RemoveCircle, contentDescription = "Remove", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LocalMediaListRow(
    item: MediaItemEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddPlaylist: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = "Interactive element",
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                    ) {
                        val ext = item.url.substringAfterLast('.', "").uppercase().takeIf { it.isNotEmpty() } ?: if (item.isVideo) "VIDEO" else "AUDIO"
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                            Icon(
                                imageVector = if (item.isVideo) Icons.Filled.Movie else Icons.Filled.AudioFile,
                                contentDescription = "Interactive element",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = ext,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(item.title, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.artist, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(modifier = Modifier.width(8.dp))
                        // Genre Badge
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(item.genre, color = MaterialTheme.colorScheme.primary, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onAddPlaylist) {
                    Icon(Icons.Filled.PlaylistAdd, contentDescription = "Add to Playlist", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit Media", tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete Media", tint = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

@Composable
fun AddLocalMediaDialog(onAdd: (MediaItemEntity) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var album by remember { mutableStateOf("") }
    var isVideo by remember { mutableStateOf(false) }
    var genre by remember { mutableStateOf("Local") }
    var url by remember { mutableStateOf("") }
    var thumbnailUrl by remember { mutableStateOf("") }
    var durationMin by remember { mutableStateOf(3f) } // in minutes

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .verticalScroll(rememberScrollState()),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Add Local Media", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onBackground, unfocusedTextColor = MaterialTheme.colorScheme.onBackground),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist / Creator") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onBackground, unfocusedTextColor = MaterialTheme.colorScheme.onBackground),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text("Album / Collection") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onBackground, unfocusedTextColor = MaterialTheme.colorScheme.onBackground),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Type selection: Audio vs Video
                Text("Media Type", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), fontSize = 12.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !isVideo, onClick = { isVideo = false })
                        Text("Music (Audio)", color = MaterialTheme.colorScheme.onBackground)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = isVideo, onClick = { isVideo = true })
                        Text("Video", color = MaterialTheme.colorScheme.onBackground)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = genre,
                    onValueChange = { genre = it },
                    label = { Text("Category / Genre") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onBackground, unfocusedTextColor = MaterialTheme.colorScheme.onBackground),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Media URL / File Path") },
                    placeholder = { Text("e.g. soundhelix.com or gtv-videos-bucket...") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onBackground, unfocusedTextColor = MaterialTheme.colorScheme.onBackground),
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = {
                        url = if (isVideo) {
                            "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
                        } else {
                            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3"
                        }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Auto-fill Test Stream URL", color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp)
                }

                OutlinedTextField(
                    value = thumbnailUrl,
                    onValueChange = { thumbnailUrl = it },
                    label = { Text("Thumbnail Image URL") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onBackground, unfocusedTextColor = MaterialTheme.colorScheme.onBackground),
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = {
                        thumbnailUrl = "https://images.unsplash.com/photo-1498050108023-c5249f4df085?w=500&auto=format&fit=crop"
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Auto-fill Aesthetic Thumbnail", color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp)
                }

                Text("Duration: ${durationMin.toInt()} min", color = MaterialTheme.colorScheme.onBackground, fontSize = 12.sp)
                Slider(
                    value = durationMin,
                    onValueChange = { durationMin = it },
                    valueRange = 1f..10f,
                    colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = MaterialTheme.colorScheme.tertiary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (title.isNotEmpty()) {
                                val finalUrl = url.ifEmpty {
                                    if (isVideo) "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
                                    else "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3"
                                }
                                val finalThumb = thumbnailUrl.ifEmpty {
                                    "https://images.unsplash.com/photo-1498050108023-c5249f4df085?w=500&auto=format&fit=crop"
                                }
                                onAdd(
                                    MediaItemEntity(
                                        id = "local_" + System.currentTimeMillis().toString(),
                                        title = title,
                                        artist = artist.ifEmpty { "Unknown Artist" },
                                        album = album.ifEmpty { "Local Album" },
                                        duration = (durationMin * 60 * 1000).toLong(),
                                        url = finalUrl,
                                        isVideo = isVideo,
                                        isLocal = true,
                                        isDownloaded = true,
                                        thumbnailUrl = finalThumb,
                                        genre = genre.ifEmpty { "Local" }
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                    ) {
                        Text("Add")
                    }
                }
            }
        }
    }
}

@Composable
fun EditLocalMediaDialog(item: MediaItemEntity, onSave: (MediaItemEntity) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(item.title) }
    var artist by remember { mutableStateOf(item.artist) }
    var album by remember { mutableStateOf(item.album) }
    var genre by remember { mutableStateOf(item.genre) }
    var url by remember { mutableStateOf(item.url) }
    var thumbnailUrl by remember { mutableStateOf(item.thumbnailUrl) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .verticalScroll(rememberScrollState()),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Edit Media Item", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onBackground, unfocusedTextColor = MaterialTheme.colorScheme.onBackground),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist / Creator") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onBackground, unfocusedTextColor = MaterialTheme.colorScheme.onBackground),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text("Album / Collection") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onBackground, unfocusedTextColor = MaterialTheme.colorScheme.onBackground),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = genre,
                    onValueChange = { genre = it },
                    label = { Text("Category / Genre") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onBackground, unfocusedTextColor = MaterialTheme.colorScheme.onBackground),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Media URL / File Path") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onBackground, unfocusedTextColor = MaterialTheme.colorScheme.onBackground),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = thumbnailUrl,
                    onValueChange = { thumbnailUrl = it },
                    label = { Text("Thumbnail Image URL") },
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onBackground, unfocusedTextColor = MaterialTheme.colorScheme.onBackground),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = MaterialTheme.colorScheme.tertiary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (title.isNotEmpty()) {
                                onSave(
                                    item.copy(
                                        title = title,
                                        artist = artist,
                                        album = album,
                                        genre = genre,
                                        url = url,
                                        thumbnailUrl = thumbnailUrl
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
