package com.theveloper.pixelplay.presentation.screens

import com.theveloper.pixelplay.presentation.navigation.navigateSafely

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import coil.size.Size
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.PlaylistBottomSheet
import com.theveloper.pixelplay.presentation.components.PlaylistCover
import com.theveloper.pixelplay.presentation.components.QueuePlaylistSongItem
import com.theveloper.pixelplay.presentation.components.SongPickerBottomSheet
import com.theveloper.pixelplay.presentation.components.ExpressiveScrollBar
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.SongInfoBottomSheet
import com.theveloper.pixelplay.presentation.components.resolveNavBarOccupiedHeight
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel.Companion.FOLDER_PLAYLIST_PREFIX
import com.theveloper.pixelplay.presentation.utils.LocalAppHapticsConfig
import com.theveloper.pixelplay.presentation.utils.performAppCompatHapticFeedback
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistSongsOrderMode
import com.theveloper.pixelplay.utils.formatTotalDuration
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.theveloper.pixelplay.presentation.components.LibrarySortBottomSheet
import com.theveloper.pixelplay.data.model.SortOption
import com.theveloper.pixelplay.data.model.PlaylistShapeType
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(
    ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    onBackClick: () -> Unit,
    onDeletePlayListClick: () -> Unit,
    playerViewModel: PlayerViewModel,
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    navController: NavController
) {
    val uiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
    val playerStableState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentPlaylist = uiState.currentPlaylistDetails
    val isFolderPlaylist = currentPlaylist?.id?.startsWith(FOLDER_PLAYLIST_PREFIX) == true
    val songsInPlaylist = uiState.currentPlaylistSongs
    val isYtmPlaylist = currentPlaylist?.source == "YTM"

    LaunchedEffect(playlistId) {
        playlistViewModel.loadPlaylistDetails(playlistId)
    }

    var showAddSongsSheet by remember { mutableStateOf(false) }

    var isReorderModeEnabled by remember { mutableStateOf(false) }
    var isSelectionModeEnabled by remember { mutableStateOf(false) }
    val selectedSongIds = remember { mutableStateMapOf<String, Boolean>() }
    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    var showPlaylistOptionsSheet by remember { mutableStateOf(false) }
    var showEditPlaylistDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val m3uExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri ->
        uri?.let {
            currentPlaylist?.let { playlist ->
                playlistViewModel.exportM3u(playlist, it, context)
            }
        }
    }

    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()
    val favoriteIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle() // Reintroducir favoriteIds aquí
    val stableOnMoreOptionsClick: (Song) -> Unit = remember {
        { song ->
            playerViewModel.selectSongForInfo(song)
            showSongInfoBottomSheet = true
        }
    }
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val bottomBarHeightDp = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    var localReorderableSongs by remember(songsInPlaylist) { mutableStateOf(songsInPlaylist) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val appHapticsConfig = LocalAppHapticsConfig.current
    var lastMovedFrom by remember { mutableStateOf<Int?>(null) }
    var lastMovedTo by remember { mutableStateOf<Int?>(null) }

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            localReorderableSongs = localReorderableSongs.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
            if (lastMovedFrom == null) {
                lastMovedFrom = from.index
            }
            lastMovedTo = to.index
        }
    )

    LaunchedEffect(reorderableState.isAnyItemDragging, isFolderPlaylist) {
        if (!isFolderPlaylist && !reorderableState.isAnyItemDragging && lastMovedFrom != null && lastMovedTo != null) {
            currentPlaylist?.let {
                playlistViewModel.reorderSongsInPlaylist(it.id, lastMovedFrom!!, lastMovedTo!!)
            }
            lastMovedFrom = null
            lastMovedTo = null
        } else if (isFolderPlaylist && !reorderableState.isAnyItemDragging) {
            lastMovedFrom = null
            lastMovedTo = null
        }
    }

    // ── Collapsing Hero Setup ──────────────────────────────────────
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val screenHeightDp = configuration.screenHeightDp.dp
    val maxTopBarHeight = screenHeightDp // Hero fully covers screen

    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }

    val topBarHeight = remember { Animatable(maxTopBarHeightPx) }
    val collapseFraction by remember(minTopBarHeightPx, maxTopBarHeightPx) {
        derivedStateOf {
            1f - ((topBarHeight.value - minTopBarHeightPx) / (maxTopBarHeightPx - minTopBarHeightPx)).coerceIn(0f, 1f)
        }
    }

    LaunchedEffect(collapseFraction) {
        // Hide MiniPlayer when fully expanded (at the top)
        playerViewModel.setMiniPlayerDynamicallyHidden(collapseFraction < 0.1f)
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            playerViewModel.setMiniPlayerDynamicallyHidden(false)
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val isScrollingDown = delta < 0
                if (!isScrollingDown && (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)) {
                    return Offset.Zero
                }
                val previousHeight = topBarHeight.value
                val newHeight = (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = newHeight - previousHeight
                if (consumed.roundToInt() != 0) {
                    scope.launch { topBarHeight.snapTo(newHeight) }
                }
                val canConsumeScroll = !(isScrollingDown && newHeight == minTopBarHeightPx)
                return if (canConsumeScroll) Offset(0f, consumed) else Offset.Zero
            }
        }
    }

    // Snap on fling release
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val shouldExpand = topBarHeight.value > (minTopBarHeightPx + maxTopBarHeightPx) / 2
            val canExpand = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
            val targetValue = if (shouldExpand && canExpand) maxTopBarHeightPx else minTopBarHeightPx
            if (topBarHeight.value != targetValue) {
                scope.launch { topBarHeight.animateTo(targetValue, spring(stiffness = Spring.StiffnessMedium)) }
            }
        }
    }

    // ── Derived animation values ─────────────────────────────────
    val surfaceColor = MaterialTheme.colorScheme.surface
    val solidAlpha = (collapseFraction * 2f).coerceIn(0f, 1f)
    val expandedContentAlpha = 1f - solidAlpha
    val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }

    // Resolve cover art: playlist cover > first song art > null
    val heroImageUrl = remember(currentPlaylist?.coverImageUri, songsInPlaylist) {
        currentPlaylist?.coverImageUri
            ?: songsInPlaylist.firstOrNull()?.albumArtUriString
    }

    // ── Main Layout ──────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection)) {
        if (uiState.isLoading && currentPlaylist == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        } else if (uiState.playlistNotFound) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text(stringResource(id = R.string.playlist_not_found)) }
        } else if (currentPlaylist == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        } else {
            // ── Song List (behind the hero, offset by header height) ──
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, (topBarHeight.value - minTopBarHeightPx).roundToInt()) },
                contentPadding = PaddingValues(
                    top = minTopBarHeight + 8.dp,
                    bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
                )
            ) {
                // ── Action buttons (Play/Shuffle + Add/Remove/Reorder) ──
                item(key = "playlist_actions", contentType = "actions") {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(62.dp).padding(bottom = if (isFolderPlaylist) 8.dp else 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (localReorderableSongs.isNotEmpty()) {
                                        playerViewModel.playSongs(localReorderableSongs, localReorderableSongs.first(), currentPlaylist.name)
                                        if (playerStableState.isShuffleEnabled) playerViewModel.toggleShuffle()
                                    }
                                },
                                modifier = Modifier.weight(1f).height(76.dp),
                                enabled = localReorderableSongs.isNotEmpty(),
                                shape = AbsoluteSmoothCornerShape(cornerRadiusTL = 60.dp, smoothnessAsPercentTR = 60, cornerRadiusTR = 14.dp, smoothnessAsPercentTL = 60, cornerRadiusBL = 60.dp, smoothnessAsPercentBR = 60, cornerRadiusBR = 14.dp, smoothnessAsPercentBL = 60)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", modifier = Modifier.size(20.dp))
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text("Play it")
                            }
                            FilledTonalButton(
                                onClick = {
                                    if (localReorderableSongs.isNotEmpty()) {
                                        playerViewModel.playSongsShuffled(songsToPlay = localReorderableSongs, queueName = currentPlaylist.name, playlistId = currentPlaylist.id, startAtZero = true)
                                    }
                                },
                                modifier = Modifier.weight(1f).height(76.dp),
                                enabled = localReorderableSongs.isNotEmpty(),
                                shape = AbsoluteSmoothCornerShape(cornerRadiusTL = 14.dp, smoothnessAsPercentTR = 60, cornerRadiusTR = 60.dp, smoothnessAsPercentTL = 60, cornerRadiusBL = 14.dp, smoothnessAsPercentBR = 60, cornerRadiusBR = 60.dp, smoothnessAsPercentBL = 60)
                            ) {
                                Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", modifier = Modifier.size(ButtonDefaults.IconSize))
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text("Shuffle")
                            }
                        }
                    }
                }

                // ── Song items ──
                if (localReorderableSongs.isEmpty()) {
                    item(key = "empty_state", contentType = "empty") {
                        Box(Modifier.fillParentMaxHeight(0.5f).fillMaxWidth(), Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.MusicOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(8.dp))
                                Text("This playlist is empty.", style = MaterialTheme.typography.titleMedium)
                                Text(if (isFolderPlaylist) "This folder doesn't contain songs." else "Tap on 'Add Songs' to begin.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    itemsIndexed(localReorderableSongs, key = { _, item -> item.id }, contentType = { _, _ -> "playlist_song" }) { _, song ->
                        ReorderableItem(state = reorderableState, key = song.id) { isDragging ->
                            val scale by animateFloatAsState(if (isDragging) 1.05f else 1f, label = "scale")
                            QueuePlaylistSongItem(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp).graphicsLayer { scaleX = scale; scaleY = scale },
                                onClick = { 
                                    if (isSelectionModeEnabled) {
                                        val currentSelection = selectedSongIds[song.id] ?: false
                                        selectedSongIds[song.id] = !currentSelection
                                    } else {
                                        playerViewModel.playSongs(localReorderableSongs, song, currentPlaylist.name, currentPlaylist.id) 
                                    }
                                },
                                song = song, isCurrentSong = playerStableState.currentSong?.id == song.id, isPlaying = playerStableState.isPlaying, isDragging = isDragging,
                                onRemoveClick = { if (!isFolderPlaylist) playlistViewModel.removeSongFromPlaylist(currentPlaylist.id, song.id) },
                                isFromPlaylist = true, isReorderModeEnabled = isReorderModeEnabled, isDragHandleVisible = isReorderModeEnabled, isRemoveButtonVisible = isSelectionModeEnabled && (selectedSongIds[song.id] == true),
                                onMoreOptionsClick = stableOnMoreOptionsClick,
                                enableSwipeToDismiss = !isFolderPlaylist,
                                swipeStateIdentity = song.id.hashCode().toLong(),
                                onDismissSong = { if (!isFolderPlaylist) playlistViewModel.removeSongFromPlaylist(currentPlaylist.id, song.id) },
                                dragHandle = {
                                    IconButton(onClick = {}, modifier = Modifier.draggableHandle(
                                        onDragStarted = { performAppCompatHapticFeedback(view, appHapticsConfig, HapticFeedbackConstantsCompat.GESTURE_START) },
                                        onDragStopped = { performAppCompatHapticFeedback(view, appHapticsConfig, HapticFeedbackConstantsCompat.GESTURE_END) }
                                    ).size(40.dp)) {
                                        Icon(Icons.Rounded.DragIndicator, contentDescription = "Reorder song", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            )
                        }
                    }

                    // YTM lazy-load footer
                    if (isYtmPlaylist && uiState.isLoadingMoreSongs) {
                        item(key = "ytm_load_more_spinner", contentType = "spinner") {
                            Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                            }
                        }
                    }
                }
            }

            // YTM pagination trigger
            if (isYtmPlaylist && uiState.hasMoreSongs) {
                val shouldLoadMore by remember {
                    derivedStateOf {
                        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        val total = listState.layoutInfo.totalItemsCount
                        total > 0 && lastVisible >= total - 5
                    }
                }
                LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) playlistViewModel.loadMorePlaylistSongs() }
            }

            // Scrollbar
            if (collapseFraction > 0.95f) {
                ExpressiveScrollBar(
                    listState = listState,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(
                        top = minTopBarHeight + 12.dp,
                        bottom = if (playerStableState.currentSong != null) MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 20.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp,
                        end = 14.dp
                    )
                )
            }

            // ── Collapsing Hero Header (drawn on top) ────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(currentTopBarHeightDp)
                    .clipToBounds()
            ) {
                // Phase 1/2: Expanded hero artwork
                if (expandedContentAlpha > 0.01f) {
                    if (heroImageUrl != null) {
                        SmartImage(
                            model = heroImageUrl,
                            contentDescription = currentPlaylist.name,
                            contentScale = ContentScale.Crop,
                            targetSize = Size(
                                with(density) { configuration.screenWidthDp.dp.roundToPx() },
                                with(density) { maxTopBarHeight.roundToPx() }
                            ),
                            allowHardware = true,
                            crossfadeDurationMillis = 0,
                            alpha = expandedContentAlpha,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Gradient fallback when no cover art
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            MaterialTheme.colorScheme.surface
                                        )
                                    )
                                )
                                .graphicsLayer { alpha = expandedContentAlpha }
                        )
                    }

                    // Dark gradient overlay at bottom of hero
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        surfaceColor.copy(alpha = 0.2f),
                                        surfaceColor.copy(alpha = 0.7f),
                                        surfaceColor
                                    ),
                                    startY = with(density) { currentTopBarHeightDp.toPx() * 0.3f }
                                )
                            )
                    )

                    // Playlist name + subtitle on expanded hero
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
                            .graphicsLayer { alpha = expandedContentAlpha }
                    ) {
                        Text(
                            text = currentPlaylist.name,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${songsInPlaylist.size} songs • ${formatTotalDuration(songsInPlaylist)}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = GoogleSansRounded),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Phase 3: Compact header background
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(minTopBarHeight)
                        .background(surfaceColor.copy(alpha = solidAlpha))
                        .align(Alignment.TopCenter)
                )

                // Status bar scrim
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(statusBarHeight + 40.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.4f * expandedContentAlpha),
                                    Color.Transparent
                                )
                            )
                        )
                        .align(Alignment.TopCenter)
                )

                // ── Compact header content (visible when collapsed) ──
                if (solidAlpha > 0.01f) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(minTopBarHeight)
                            .statusBarsPadding()
                            .padding(start = 56.dp, end = 56.dp)
                            .graphicsLayer { alpha = solidAlpha },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Small square cover art
                        if (heroImageUrl != null) {
                            SmartImage(
                                model = heroImageUrl,
                                contentDescription = currentPlaylist.name,
                                contentScale = ContentScale.Crop,
                                targetSize = Size(128, 128),
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                            }
                        }
                        // Playlist name + count
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentPlaylist.name,
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = GoogleSansRounded, fontWeight = FontWeight.SemiBold),
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${songsInPlaylist.size} songs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // ── Back button (always visible) ──
                FilledIconButton(
                    modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 8.dp, top = 4.dp),
                    onClick = onBackClick,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f), contentColor = MaterialTheme.colorScheme.onSurface)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                }

                // ── Search + Sort + Options buttons (fade out as collapsed) ──
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(end = 8.dp, top = 4.dp).graphicsLayer { alpha = expandedContentAlpha }
                ) {
                    IconButton(onClick = { /* TODO: Toggle Search UI */ }) {
                        Icon(Icons.Rounded.Search, contentDescription = "Search Songs", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { playerViewModel.showSortingSheet() }) {
                        Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort Songs", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    if (!isFolderPlaylist) {
                        FilledTonalIconButton(
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f), contentColor = MaterialTheme.colorScheme.onSurface),
                            onClick = { showPlaylistOptionsSheet = true }
                        ) { Icon(Icons.Filled.MoreVert, "More Options") }
                    }
                }
            }

            // ── Selection Mode Bottom Bar ──
            androidx.compose.animation.AnimatedVisibility(
                visible = isSelectionModeEnabled,
                enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp)
            ) {
                val selectedCount = selectedSongIds.count { it.value }
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$selectedCount selected",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                isSelectionModeEnabled = false
                                selectedSongIds.clear()
                            }
                        ) {
                            Text("Cancel", color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Button(
                            onClick = {
                                val idsToDelete = selectedSongIds.filter { it.value }.keys.toList()
                                if (currentPlaylist != null && idsToDelete.isNotEmpty()) {
                                    idsToDelete.forEach { songId ->
                                        playlistViewModel.removeSongFromPlaylist(currentPlaylist.id, songId)
                                    }
                                }
                                isSelectionModeEnabled = false
                                selectedSongIds.clear()
                            },
                            enabled = selectedCount > 0,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Selected", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }


    if (showAddSongsSheet && currentPlaylist != null && !isFolderPlaylist) {
        SongPickerBottomSheet(
            initiallySelectedSongIds = currentPlaylist.songIds.toSet(),
            onDismiss = { showAddSongsSheet = false },
            onConfirm = { selectedIds ->
                playlistViewModel.addSongsToPlaylist(currentPlaylist.id, selectedIds.toList())
                showAddSongsSheet = false
            }
        )
    }
    if (showPlaylistOptionsSheet && !isFolderPlaylist) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { showPlaylistOptionsSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 4.dp,
//            dragHandle = {
//                SheetDefaults.DragHandle(
//                    color = MaterialTheme.colorScheme.onSurfaceVariant
//                )
//            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Playlist options",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    currentPlaylist?.name?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                PlaylistActionItem(
                    icon = painterResource(R.drawable.rounded_edit_24),
                    label = "Edit playlist",
                    onClick = {
                        showPlaylistOptionsSheet = false
                        showEditPlaylistDialog = true
                    }
                )
                PlaylistActionItem(
                    icon = painterResource(R.drawable.drag_order_icon),
                    label = "Reorder mode",
                    onClick = {
                        showPlaylistOptionsSheet = false
                        isReorderModeEnabled = !isReorderModeEnabled
                    }
                )
                PlaylistActionItem(
                    icon = painterResource(R.drawable.outline_graph_1_24), // Placeholder icon for select mode
                    label = "Select mode",
                    onClick = {
                        showPlaylistOptionsSheet = false
                        isSelectionModeEnabled = !isSelectionModeEnabled
                        if (!isSelectionModeEnabled) selectedSongIds.clear()
                    }
                )
                PlaylistActionItem(
                    icon = painterResource(R.drawable.rounded_delete_24),
                    label = "Delete playlist",
                    onClick = {
                        showPlaylistOptionsSheet = false
                        showDeleteConfirmation = true
                    }
                )
                PlaylistActionItem(
                    icon = painterResource(R.drawable.outline_graph_1_24),
                    label = "Set default transition",
                    onClick = {
                        showPlaylistOptionsSheet = false
                        navController.navigateSafely(Screen.EditTransition.createRoute(playlistId))
                    }
                )
                PlaylistActionItem(
                    icon = painterResource(R.drawable.rounded_attach_file_24),
                    label = "Export Playlist",
                    onClick = {
                        showPlaylistOptionsSheet = false
                        m3uExportLauncher.launch("${currentPlaylist?.name ?: "playlist"}.m3u")
                    }
                )
            }
        }
    }
    
    if (showEditPlaylistDialog && currentPlaylist != null) {
        val initialShapeType = try {
            currentPlaylist.coverShapeType?.let { PlaylistShapeType.valueOf(it) } ?: PlaylistShapeType.Circle
        } catch (e: Exception) {
            PlaylistShapeType.Circle
        }
        
        EditPlaylistDialog(
            visible = showEditPlaylistDialog,
            currentName = currentPlaylist.name,
            currentImageUri = currentPlaylist.coverImageUri,
            currentColor = currentPlaylist.coverColorArgb,
            currentIconName = currentPlaylist.coverIconName,
            currentShapeType = initialShapeType,
            currentShapeDetail1 = currentPlaylist.coverShapeDetail1,
            currentShapeDetail2 = currentPlaylist.coverShapeDetail2,
            currentShapeDetail3 = currentPlaylist.coverShapeDetail3,
            currentShapeDetail4 = currentPlaylist.coverShapeDetail4,
            onDismiss = { showEditPlaylistDialog = false },
            onSave = { name, imageUri, color, icon, scale, panX, panY, shapeType, d1, d2, d3, d4 ->
                playlistViewModel.updatePlaylistParameters(
                    playlistId = currentPlaylist.id,
                    name = name,
                    coverImageUri = imageUri,
                    coverColor = color,
                    coverIcon = icon,
                    cropScale = scale,
                    cropPanX = panX,
                    cropPanY = panY,
                    coverShapeType = shapeType,
                    coverShapeDetail1 = d1,
                    coverShapeDetail2 = d2,
                    coverShapeDetail3 = d3,
                    coverShapeDetail4 = d4
                )
                showEditPlaylistDialog = false
            }
        )
    }
    if (showDeleteConfirmation && currentPlaylist != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete playlist?") },
            text = {
                Text("Are you sure you want to delete this playlist?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        playlistViewModel.deletePlaylist(currentPlaylist.id)
                        onDeletePlayListClick()
                        showDeleteConfirmation = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSongInfoBottomSheet && selectedSongForInfo != null) {
        val currentSong = selectedSongForInfo
        val isFavorite = remember(currentSong?.id, favoriteIds) {
            derivedStateOf {
                currentSong?.let {
                    favoriteIds.contains(
                        it.id
                    )
                }
            }
        }.value ?: false

        if (currentSong != null) {
            SongInfoBottomSheet(
                song = currentSong,
                isFavorite = isFavorite,
                onToggleFavorite = {
                    // Directly use PlayerViewModel's method to toggle, which should handle UserPreferencesRepository
                    playerViewModel.toggleFavoriteSpecificSong(currentSong) // Assumes such a method exists or will be added to PlayerViewModel
                },
                onDismiss = { showSongInfoBottomSheet = false },
                onPlaySong = {
                    playerViewModel.showAndPlaySong(currentSong)
                    showSongInfoBottomSheet = false
                },
                onAddToQueue = {
                    playerViewModel.addSongToQueue(currentSong) // Assumes such a method exists or will be added
                    showSongInfoBottomSheet = false
                    playerViewModel.sendToast("Added to the queue")
                },
                onAddNextToQueue = {
                    playerViewModel.addSongNextToQueue(currentSong)
                    showSongInfoBottomSheet = false
                    playerViewModel.sendToast("Will play next")
                },
                onAddToPlayList = {
                    showPlaylistBottomSheet = true;
                },
                onDeleteFromDevice = playerViewModel::deleteFromDevice,
                onNavigateToAlbum = {
                    navController.navigateSafely(Screen.AlbumDetail.createRoute(currentSong.albumId))
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtist = {
                    navController.navigateSafely(Screen.ArtistDetail.createRoute(currentSong.artistId, currentSong.artist))
                    showSongInfoBottomSheet = false
                },
                onNavigateToGenre = {
                    currentSong.genre?.let {
                        navController.navigateSafely(Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8")))
                    }
                    showSongInfoBottomSheet = false
                },
                onEditSong = { newTitle, newArtist, newAlbum, newGenre, newLyrics, newTrackNumber, newDiscNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArtUpdate ->
                    playerViewModel.editSongMetadata(
                        currentSong,
                        newTitle,
                        newArtist,
                        newAlbum,
                        newGenre,
                        newLyrics,
                        newTrackNumber,
                        newDiscNumber,
                        replayGainTrackGainDb,
                        replayGainAlbumGainDb,
                        coverArtUpdate
                    )
                },
                generateAiMetadata = { fields ->
                    playerViewModel.generateAiMetadata(currentSong, fields)
                },
                removeFromListTrigger = {
                    playlistViewModel.removeSongFromPlaylist(playlistId, currentSong.id)
                }
            )
            if (showPlaylistBottomSheet) {
                val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()

                PlaylistBottomSheet(
                    playlistUiState = playlistUiState,
                    songs = listOf(currentSong),
                    onDismiss = {
                        showPlaylistBottomSheet = false
                    },
                    currentPlaylistId = playlistId,
                    bottomBarHeight = bottomBarHeightDp,
                    playerViewModel = playerViewModel,
                )
            }
        }
    }

    val isSortSheetVisible by playerViewModel.isSortingSheetVisible.collectAsStateWithLifecycle()

    if (isSortSheetVisible) {
        // Check if playlist is in Manual mode (which corresponds to Default Order)
        val isManualMode = uiState.playlistSongsOrderMode is PlaylistSongsOrderMode.Manual
        val rawOption = uiState.currentPlaylistSongsSortOption
        // If in Manual mode, show SongDefaultOrder as selected; otherwise use the stored sort option
        val currentSortOption = if (isManualMode) {
            SortOption.SongDefaultOrder
        } else if (currentPlaylist != null) {
            rawOption
        } else {
            SortOption.SongTitleAZ
        }

        // Build options list inline to avoid potential static initialization issues
        val songSortOptions = listOf(
            SortOption.SongDefaultOrder,
            SortOption.SongTitleAZ,
            SortOption.SongTitleZA,
            SortOption.SongArtist,
            SortOption.SongArtistDesc,
            SortOption.SongAlbum,
            SortOption.SongAlbumDesc,
            SortOption.SongDateAdded,
            SortOption.SongDateAddedAsc,
            SortOption.SongDuration,
            SortOption.SongDurationAsc
        )

        LibrarySortBottomSheet(
            title = "Sort Songs",
            options = songSortOptions,
            selectedOption = currentSortOption,
            onDismiss = { playerViewModel.hideSortingSheet() },
            onOptionSelected = { option ->
                 playlistViewModel.sortPlaylistSongs(option)
                 playerViewModel.hideSortingSheet()
                 // Auto-scroll to first item after sorting (delay to allow list to update)
                 scope.launch {
                     kotlinx.coroutines.delay(100)
                     listState.animateScrollToItem(0)
                 }
            },
            onDirectionToggle = { option ->
                playlistViewModel.sortPlaylistSongs(option)
                scope.launch {
                    kotlinx.coroutines.delay(100)
                    listState.animateScrollToItem(0)
                }
            },
            showViewToggle = false 
        )
    }
}


@Composable
private fun PlaylistActionItem(
    icon: Painter,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
// SongPickerBottomSheet moved to com.theveloper.pixelplay.presentation.components
fun RenamePlaylistDialog(currentName: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var newName by remember { mutableStateOf(TextFieldValue(currentName)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Playlist") },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("New name") },
                shape = CircleShape,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (newName.text.isNotBlank()) onRename(newName.text) },
                enabled = newName.text.isNotBlank() && newName.text != currentName
            ) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
