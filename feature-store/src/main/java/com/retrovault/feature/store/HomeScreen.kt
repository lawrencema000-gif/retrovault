package com.retrovault.feature.store

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.retrovault.core.model.Game
import com.retrovault.core.model.GameSystem
import com.retrovault.download.RomImporter
import com.retrovault.download.RomStorage
import com.retrovault.library.GameIdentifier
import com.retrovault.library.LibraryEntry
import com.retrovault.library.LibraryIndex
import com.retrovault.library.LibraryScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.retrovault.core.ui.coverBrush
import com.retrovault.core.ui.theme.ChakraPetch
import com.retrovault.core.ui.theme.PulsarBlueBrush
import com.retrovault.core.ui.theme.PulsarStroke
import com.retrovault.core.ui.theme.PulsarSurface2
import com.retrovault.core.ui.theme.PulsarText
import com.retrovault.core.ui.theme.PulsarTextDim
import com.retrovault.core.ui.theme.PulsarTextDimmer
import com.retrovault.core.ui.theme.PulsarTextSoft
import com.retrovault.core.ui.component.GameCard

@Composable
fun HomeScreen(
    onGameClick: (String) -> Unit,
    onPlayLocal: (path: String, title: String, system: GameSystem, serial: String) -> Unit = { _, _, _, _ -> },
    viewModel: CatalogViewModel = viewModel(),
) {
    val uiState by viewModel.state.collectAsState()
    val games = when (val s = uiState) {
        is CatalogUiState.Success -> s.games
        is CatalogUiState.Offline -> s.games
        CatalogUiState.Loading -> emptyList()
    }
    val loading = uiState is CatalogUiState.Loading
    val offline = uiState is CatalogUiState.Offline

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // The user's own games (imported files + the onboarding games folder), identified by the
    // library engine and shown in an ON THIS DEVICE section. This is what makes the import
    // button and the onboarding folder pick actually DO something visible.
    val libraryIndex = remember { LibraryIndex(context) }
    val scanner = remember { LibraryScanner(context, libraryIndex) }
    var localGames by remember { mutableStateOf(libraryIndex.all()) }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var preparingSerial by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    suspend fun rescanLocal() {
        scanning = true
        withContext(Dispatchers.IO) {
            runCatching { scanner.scanLocalDir(RomStorage.importsDir(context), GameSystem.PSP) }
            val tree = com.retrovault.core.ui.AppPrefs.gamesFolderUri
            if (tree.isNotEmpty()) runCatching { scanner.scanTree(Uri.parse(tree)) }
        }
        localGames = libraryIndex.all()
        scanning = false
    }
    LaunchedEffect(Unit) { rescanLocal() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        importMessage = "Importing…"
        scope.launch {
            val message = withContext(Dispatchers.IO) {
                val file = RomImporter.import(context, uri)
                    ?: return@withContext "Import failed — couldn't read that file."
                val meta = GameIdentifier.identify(context, file, GameSystem.PSP)
                if (meta == null) {
                    file.delete()
                    "That doesn't look like a PSP game. Supported: .iso, .cso, .pbp, .chd"
                } else {
                    "Added \"${meta.title}\""
                }
            }
            rescanLocal()
            importMessage = message
        }
    }

    /** Launch a local entry; SAF-tree entries are copied into app storage once, then played. */
    fun playLocal(entry: LibraryEntry) {
        val src = Uri.parse(entry.sourceUri)
        if (src.scheme == "file") {
            onPlayLocal(src.path ?: return, entry.title, entry.system, entry.serial)
            return
        }
        if (preparingSerial != null) return
        preparingSerial = entry.serial
        scope.launch {
            val copied = withContext(Dispatchers.IO) {
                runCatching {
                    val dest = java.io.File(RomStorage.importsDir(context), entry.displayName)
                    if (!dest.exists() || dest.length() == 0L) {
                        context.contentResolver.openInputStream(src)!!.use { input ->
                            dest.outputStream().use { input.copyTo(it) }
                        }
                    }
                    dest
                }.getOrNull()
            }
            preparingSerial = null
            if (copied != null) {
                rescanLocal()
                onPlayLocal(copied.absolutePath, entry.title, entry.system, entry.serial)
            } else {
                importMessage = "Couldn't read \"${entry.displayName}\" from your games folder."
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 56.dp, bottom = 110.dp)
    ) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                // "Welcome back" on a first launch + calling the store "Your Library" both read
                // as lies to a new user — greet neutrally; sections below are labeled honestly.
                Text("Welcome", fontSize = 12.sp, color = PulsarTextDimmer, fontWeight = FontWeight.SemiBold)
                Text(
                    "Pulsar",
                    style = MaterialTheme.typography.headlineSmall,
                    color = PulsarText,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconTile(Icons.Filled.FileUpload) { importLauncher.launch(arrayOf("*/*")) }
                // (The decorative avatar tile is gone — it promised an account that doesn't exist.)
                IconTile(Icons.Filled.Search) {
                    searchOpen = !searchOpen
                    if (!searchOpen) query = ""
                }
            }
        }

        if (searchOpen) {
            Spacer(Modifier.height(12.dp))
            HomeSearchField(
                query = query,
                onChange = { query = it },
                modifier = Modifier.padding(horizontal = 22.dp),
            )
        }

        if (offline) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Offline — tap to retry",
                fontSize = 12.sp,
                color = PulsarTextDim,
                modifier = Modifier
                    .padding(horizontal = 22.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { viewModel.refresh() }
                    .padding(vertical = 4.dp)
            )
        }

        importMessage?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                it, fontSize = 12.sp, color = PulsarTextDim,
                modifier = Modifier.padding(horizontal = 22.dp)
            )
        }

        // The user's own games — imported files + the onboarding games folder. PSP plays today;
        // PS1/PS2 files are recognized but held until their cores land (P23/P25).
        val playableLocal = localGames.filter {
            it.system == GameSystem.PSP && (query.isBlank() || it.title.contains(query, ignoreCase = true))
        }
        val futureLocal = localGames.count { it.system != GameSystem.PSP }
        if (scanning && localGames.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Scanning your games folder…", fontSize = 12.sp, color = PulsarTextDim,
                modifier = Modifier.padding(horizontal = 22.dp)
            )
        }
        if (playableLocal.isNotEmpty() || futureLocal > 0) {
            Spacer(Modifier.height(26.dp))
            SectionLabel("ON THIS DEVICE", Modifier.padding(horizontal = 22.dp))
            Spacer(Modifier.height(14.dp))
            if (playableLocal.isNotEmpty()) {
                Column(
                    Modifier.padding(horizontal = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    playableLocal.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            row.forEach { entry ->
                                LocalGameTile(
                                    entry = entry,
                                    preparing = preparingSerial == entry.serial,
                                    onClick = { playLocal(entry) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            if (futureLocal > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "$futureLocal PS1/PS2 file${if (futureLocal == 1) "" else "s"} found — " +
                        "those systems arrive in a future update.",
                    fontSize = 11.sp, color = PulsarTextDimmer,
                    modifier = Modifier.padding(horizontal = 22.dp)
                )
            }
        }

        // Store catalog (honestly labeled — these are downloadable titles, not the user's).
        val shownGames =
            if (query.isBlank()) games
            else games.filter { it.title.contains(query, ignoreCase = true) }

        // Featured carousel
        Spacer(Modifier.height(26.dp))
        SectionLabel("FEATURED", Modifier.padding(horizontal = 22.dp))
        Spacer(Modifier.height(14.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(shownGames.take(6), key = { it.id }) { game ->
                FeaturedCard(game) { onGameClick(game.id) }
            }
        }

        // All games grid
        Spacer(Modifier.height(24.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel("STORE")
            Text("${shownGames.size} titles", fontSize = 12.sp, color = PulsarTextDimmer)
        }
        Spacer(Modifier.height(14.dp))

        if (loading) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
        } else if (games.isEmpty() && localGames.isEmpty()) {
            LibraryEmptyState(offline = offline, onImport = { importLauncher.launch(arrayOf("*/*")) })
        } else if (games.isEmpty()) {
            Text(
                "Store catalog unavailable right now — your own games above still play.",
                fontSize = 12.sp, color = PulsarTextDimmer,
                modifier = Modifier.padding(horizontal = 22.dp)
            )
        } else {
            Column(
                Modifier.padding(horizontal = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                shownGames.chunked(3).forEach { rowGames ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        rowGames.forEach { game ->
                            GameCard(
                                game = game,
                                onClick = { onGameClick(game.id) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(3 - rowGames.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** A user-owned game (imported or from the games folder): ICON0 art when extracted, else a
 *  deterministic gradient with the title initial. Tap plays directly. */
@Composable
private fun LocalGameTile(
    entry: LibraryEntry,
    preparing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.clickable(enabled = !preparing) { onClick() }) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(coverBrush(entry.serial))
                .border(1.dp, PulsarStroke, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            val icon = entry.iconPath
            if (icon != null && java.io.File(icon).exists()) {
                coil.compose.AsyncImage(
                    model = java.io.File(icon),
                    contentDescription = entry.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            } else {
                Text(
                    entry.title.take(1).uppercase(),
                    fontFamily = ChakraPetch, fontWeight = FontWeight.Bold,
                    fontSize = 26.sp, color = Color.White.copy(alpha = 0.85f)
                )
            }
            if (preparing) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (preparing) "Preparing…" else entry.title,
            fontSize = 11.sp, color = PulsarTextDim,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

/** Teaching empty state: explains what to do rather than showing a blank grid. */
@Composable
private fun LibraryEmptyState(offline: Boolean, onImport: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 34.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.VideogameAsset, contentDescription = null,
            tint = PulsarTextDimmer, modifier = Modifier.size(52.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (offline) "Can't reach the store" else "Your library is empty",
            fontFamily = ChakraPetch, fontWeight = FontWeight.Bold, fontSize = 18.sp,
            color = PulsarText, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (offline)
                "Check your connection to browse the catalog. You can still import and play " +
                    "your own game backups from this device."
            else
                "Browse the store for free, legal homebrew, or import your own PSP game backups " +
                    "from your device to get started.",
            fontSize = 13.sp, lineHeight = 20.sp, color = PulsarTextDim, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(PulsarSurface2)
                .border(1.dp, PulsarStroke, RoundedCornerShape(16.dp))
                .clickable(onClick = onImport)
                .padding(horizontal = 22.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Icon(Icons.Filled.FolderOpen, null, tint = PulsarText, modifier = Modifier.size(19.dp))
            Text("Import a game", color = PulsarText, fontFamily = ChakraPetch, fontSize = 13.sp)
        }
    }
}

@Composable
private fun IconTile(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(PulsarSurface2)
            .border(1.dp, PulsarStroke, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = PulsarTextSoft, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        fontFamily = ChakraPetch,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 2.sp,
        color = PulsarTextDim
    )
}

/** Search across the store catalog AND the user's own games (both visible lists filter live). */
@Composable
private fun HomeSearchField(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.text.BasicTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(color = PulsarText, fontSize = 13.sp),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier,
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(PulsarSurface2)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Filled.Search, contentDescription = "Search",
                    tint = PulsarTextDim, modifier = Modifier.size(17.dp)
                )
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search games…", fontSize = 13.sp, color = PulsarTextDimmer)
                    inner()
                }
            }
        }
    )
}

@Composable
private fun FeaturedCard(game: Game, onClick: () -> Unit) {
    Column(
        Modifier
            .width(210.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(128.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(coverBrush(game.id))
        ) {
            // Real box art when the catalog has it (mirrors GameCard) — otherwise the gradient.
            if (game.boxArtUrl != null) {
                coil.compose.AsyncImage(
                    model = game.boxArtUrl,
                    contentDescription = game.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to Color(0xE604060C)
                        )
                    )
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Text(
                game.title,
                fontFamily = ChakraPetch,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "${game.system.displayName} · ${game.license}",
            fontSize = 11.sp,
            color = PulsarTextDimmer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
