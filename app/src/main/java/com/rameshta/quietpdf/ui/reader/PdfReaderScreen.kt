package com.rameshta.quietpdf.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.rameshta.quietpdf.R
import com.rameshta.quietpdf.pdf.PageRenderResult
import com.rameshta.quietpdf.pdf.PdfOpenState
import com.rameshta.quietpdf.pdf.PdfSearchResult
import com.rameshta.quietpdf.pdf.PdfSearchMatch
import com.rameshta.quietpdf.pdf.PdfOutlineEntry
import com.rameshta.quietpdf.pdf.PdfTableOfContentsResult
import com.rameshta.quietpdf.pdf.PdfHealthReport
import com.rameshta.quietpdf.pdf.PdfHealthResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.roundToInt
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    document: PdfOpenState.Opened,
    onOpenAnother: () -> Unit,
    renderPage: suspend (pageIndex: Int, targetWidth: Int) -> PageRenderResult,
    onPageChanged: (pageIndex: Int) -> Unit,
    searchDocument: suspend (query: String) -> PdfSearchResult,
    onToggleBookmark: (pageIndex: Int) -> Unit,
    loadTableOfContents: suspend () -> PdfTableOfContentsResult,
    inspectHealth: suspend () -> PdfHealthResult,
    onToggleFavorite: () -> Unit = {},
) {
    val initialPage = document.initialPageIndex.coerceIn(0, document.pageCount - 1)
    var readerMode by remember(document.uri) { mutableStateOf(ReaderMode.VerticalContinuous) }
    var currentPage by remember(document.uri) { mutableIntStateOf(initialPage) }
    val verticalState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)
    val horizontalState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { document.pageCount },
    )
    var isFullscreen by remember(document.uri) { mutableStateOf(false) }
    var chromeVisible by remember(document.uri) { mutableStateOf(true) }
    var nightAppearance by remember(document.uri) { mutableStateOf(false) }
    var searchActive by remember(document.uri) { mutableStateOf(false) }
    var searchQuery by remember(document.uri) { mutableStateOf("") }
    var searchMatches by remember(document.uri) { mutableStateOf(emptyList<PdfSearchMatch>()) }
    var selectedSearchIndex by remember(document.uri) { mutableIntStateOf(-1) }
    var searchMessage by remember(document.uri) { mutableStateOf<Int?>(null) }
    var searchInProgress by remember(document.uri) { mutableStateOf(false) }
    var searchJob by remember(document.uri) { mutableStateOf<Job?>(null) }
    var bookmarkDialogVisible by remember(document.uri) { mutableStateOf(false) }
    var tableOfContentsDialogVisible by remember(document.uri) { mutableStateOf(false) }
    var tableOfContentsResult by remember(document.uri) {
        mutableStateOf<PdfTableOfContentsResult?>(null)
    }
    var tableOfContentsJob by remember(document.uri) { mutableStateOf<Job?>(null) }
    var healthDialogVisible by remember(document.uri) { mutableStateOf(false) }
    var healthResult by remember(document.uri) { mutableStateOf<PdfHealthResult?>(null) }
    var healthJob by remember(document.uri) { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val inheritedColors = MaterialTheme.colorScheme
    val readerColors = if (nightAppearance) {
        darkColorScheme(
            primary = inheritedColors.primary,
            secondary = inheritedColors.secondary,
            tertiary = inheritedColors.tertiary,
            background = ReaderNightBackground,
            surface = ReaderNightSurface,
            surfaceVariant = ReaderNightBackground,
        )
    } else {
        inheritedColors
    }
    val closeSearch = {
        searchJob?.cancel()
        searchActive = false
        searchMatches = emptyList()
        selectedSearchIndex = -1
        searchMessage = null
        searchInProgress = false
    }

    LaunchedEffect(readerMode, document.uri) {
        val page = currentPage.coerceIn(0, document.pageCount - 1)
        when (readerMode) {
            ReaderMode.VerticalContinuous -> verticalState.scrollToItem(page)
            ReaderMode.HorizontalContinuous -> horizontalState.scrollToItem(page)
            ReaderMode.SinglePage -> pagerState.scrollToPage(page)
        }

        snapshotFlow {
            when (readerMode) {
                ReaderMode.VerticalContinuous -> verticalState.firstVisibleItemIndex
                ReaderMode.HorizontalContinuous -> horizontalState.firstVisibleItemIndex
                ReaderMode.SinglePage -> pagerState.currentPage
            }
        }.distinctUntilChanged().collect {
            currentPage = it
            onPageChanged(it)
        }
    }

    DisposableEffect(isFullscreen, context) {
        val activity = context.findActivity()
        val controller = activity?.let {
            WindowCompat.getInsetsController(it.window, it.window.decorView)
        }
        if (isFullscreen) {
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (isFullscreen) controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(isFullscreen, chromeVisible, searchActive) {
        if (isFullscreen && chromeVisible && !searchActive) {
            delay(ChromeAutoHideMillis)
            chromeVisible = false
        }
    }

    BackHandler(
        enabled = healthDialogVisible || tableOfContentsDialogVisible || bookmarkDialogVisible ||
            isFullscreen || searchActive,
    ) {
        if (healthDialogVisible) {
            healthJob?.cancel()
            healthDialogVisible = false
        } else if (tableOfContentsDialogVisible) {
            tableOfContentsJob?.cancel()
            tableOfContentsDialogVisible = false
        } else if (bookmarkDialogVisible) {
            bookmarkDialogVisible = false
        } else if (searchActive) {
            closeSearch()
        } else {
            isFullscreen = false
            chromeVisible = true
        }
    }

    val onPageTap = {
        if (isFullscreen && !searchActive) chromeVisible = !chromeVisible
    }
    val navigateToPage: (Int) -> Unit = { requestedPage ->
        val page = requestedPage.coerceIn(0, document.pageCount - 1)
        currentPage = page
        coroutineScope.launch {
            when (readerMode) {
                ReaderMode.VerticalContinuous -> verticalState.animateScrollToItem(page)
                ReaderMode.HorizontalContinuous -> horizontalState.animateScrollToItem(page)
                ReaderMode.SinglePage -> pagerState.animateScrollToPage(page)
            }
        }
    }
    val showTableOfContents: () -> Unit = {
        tableOfContentsJob?.cancel()
        tableOfContentsResult = null
        tableOfContentsDialogVisible = true
        tableOfContentsJob = coroutineScope.launch {
            tableOfContentsResult = loadTableOfContents()
        }
    }
    val showHealth: () -> Unit = {
        healthJob?.cancel()
        healthResult = null
        healthDialogVisible = true
        healthJob = coroutineScope.launch { healthResult = inspectHealth() }
    }
    val selectSearchMatch: (Int) -> Unit = { requestedIndex ->
        if (searchMatches.isNotEmpty()) {
            val index = requestedIndex.mod(searchMatches.size)
            selectedSearchIndex = index
            navigateToPage(searchMatches[index].pageIndex)
        }
    }
    val submitSearch: () -> Unit = {
        val query = searchQuery.trim()
        searchJob?.cancel()
        searchMatches = emptyList()
        selectedSearchIndex = -1
        searchMessage = null
        if (query.isNotEmpty()) {
            searchInProgress = true
            searchJob = coroutineScope.launch {
                when (val result = searchDocument(query)) {
                    is PdfSearchResult.Matches -> {
                        searchMatches = result.matches
                        if (result.matches.isEmpty()) {
                            searchMessage = R.string.search_no_matches
                        } else {
                            selectedSearchIndex = 0
                            navigateToPage(result.matches.first().pageIndex)
                        }
                    }
                    PdfSearchResult.NoSearchableText -> {
                        searchMessage = R.string.search_no_text
                    }
                    PdfSearchResult.Failed -> {
                        searchMessage = R.string.search_failed
                    }
                }
                searchInProgress = false
            }
        }
    }
    val matchesByPage = remember(searchMatches) { searchMatches.groupBy(PdfSearchMatch::pageIndex) }
    val selectedSearchMatch = searchMatches.getOrNull(selectedSearchIndex)

    MaterialTheme(colorScheme = readerColors) {
        if (isFullscreen) {
            Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("reader_fullscreen"),
            ) {
                ReaderPages(
                    document = document,
                    readerMode = readerMode,
                    verticalState = verticalState,
                    horizontalState = horizontalState,
                    pagerState = pagerState,
                    renderPage = renderPage,
                    onPageTap = onPageTap,
                    nightAppearance = nightAppearance,
                    searchMatchesByPage = matchesByPage,
                    selectedSearchMatch = selectedSearchMatch,
                )
                if (chromeVisible) {
                    if (searchActive) SearchTopBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        inProgress = searchInProgress,
                        resultCount = searchMatches.size,
                        selectedIndex = selectedSearchIndex,
                        messageResource = searchMessage,
                        onSubmit = submitSearch,
                        onPrevious = { selectSearchMatch(selectedSearchIndex - 1) },
                        onNext = { selectSearchMatch(selectedSearchIndex + 1) },
                        onClose = closeSearch,
                        modifier = Modifier.align(Alignment.TopCenter),
                    ) else ReaderTopBar(
                        document = document,
                        readerMode = readerMode,
                        onModeSelected = { readerMode = it },
                        onOpenAnother = onOpenAnother,
                        isFullscreen = true,
                        onFullscreenChange = {
                            isFullscreen = it
                            chromeVisible = true
                        },
                        nightAppearance = nightAppearance,
                        onNightAppearanceChange = { nightAppearance = it },
                        onSearchRequested = { searchActive = true },
                        currentPage = currentPage,
                        bookmarkedPages = document.bookmarkedPages,
                        onToggleBookmark = onToggleBookmark,
                        onShowBookmarks = { bookmarkDialogVisible = true },
                        onShowTableOfContents = showTableOfContents,
                        onShowHealth = showHealth,
                        isFavorite = document.isFavorite,
                        onToggleFavorite = onToggleFavorite,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }
        } else {
            Scaffold(
                topBar = {
                    if (searchActive) SearchTopBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        inProgress = searchInProgress,
                        resultCount = searchMatches.size,
                        selectedIndex = selectedSearchIndex,
                        messageResource = searchMessage,
                        onSubmit = submitSearch,
                        onPrevious = { selectSearchMatch(selectedSearchIndex - 1) },
                        onNext = { selectSearchMatch(selectedSearchIndex + 1) },
                        onClose = closeSearch,
                    ) else ReaderTopBar(
                        document = document,
                        readerMode = readerMode,
                        onModeSelected = { readerMode = it },
                        onOpenAnother = onOpenAnother,
                        isFullscreen = false,
                        onFullscreenChange = {
                            isFullscreen = it
                            chromeVisible = true
                        },
                        nightAppearance = nightAppearance,
                        onNightAppearanceChange = { nightAppearance = it },
                        onSearchRequested = { searchActive = true },
                        currentPage = currentPage,
                        bookmarkedPages = document.bookmarkedPages,
                        onToggleBookmark = onToggleBookmark,
                        onShowBookmarks = { bookmarkDialogVisible = true },
                        onShowTableOfContents = showTableOfContents,
                        onShowHealth = showHealth,
                        isFavorite = document.isFavorite,
                        onToggleFavorite = onToggleFavorite,
                    )
                },
            ) { innerPadding ->
                ReaderPages(
                    document = document,
                    readerMode = readerMode,
                    verticalState = verticalState,
                    horizontalState = horizontalState,
                    pagerState = pagerState,
                    renderPage = renderPage,
                    onPageTap = onPageTap,
                    nightAppearance = nightAppearance,
                    searchMatchesByPage = matchesByPage,
                    selectedSearchMatch = selectedSearchMatch,
                    modifier = Modifier.padding(innerPadding),
                )
            }
        }
        if (bookmarkDialogVisible) {
            BookmarksDialog(
                bookmarkedPages = document.bookmarkedPages,
                onNavigate = { pageIndex ->
                    bookmarkDialogVisible = false
                    navigateToPage(pageIndex)
                },
                onRemove = onToggleBookmark,
                onClose = { bookmarkDialogVisible = false },
            )
        }
        if (tableOfContentsDialogVisible) {
            TableOfContentsDialog(
                result = tableOfContentsResult,
                onNavigate = { pageIndex ->
                    tableOfContentsDialogVisible = false
                    navigateToPage(pageIndex)
                },
                onClose = {
                    tableOfContentsJob?.cancel()
                    tableOfContentsDialogVisible = false
                },
            )
        }
        if (healthDialogVisible) {
            PdfHealthDialog(
                result = healthResult,
                onClose = {
                    healthJob?.cancel()
                    healthDialogVisible = false
                },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ReaderTopBar(
    document: PdfOpenState.Opened,
    readerMode: ReaderMode,
    onModeSelected: (ReaderMode) -> Unit,
    onOpenAnother: () -> Unit,
    isFullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    nightAppearance: Boolean,
    onNightAppearanceChange: (Boolean) -> Unit,
    onSearchRequested: () -> Unit,
    currentPage: Int,
    bookmarkedPages: Set<Int>,
    onToggleBookmark: (Int) -> Unit,
    onShowBookmarks: () -> Unit,
    onShowTableOfContents: () -> Unit,
    onShowHealth: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        title = {
            Text(
                text = document.displayName ?: stringResource(R.string.selected_pdf),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        actions = {
            ReaderModeMenu(
                selectedMode = readerMode,
                onModeSelected = onModeSelected,
                nightAppearance = nightAppearance,
                onNightAppearanceChange = onNightAppearanceChange,
                onSearchRequested = onSearchRequested,
                currentPage = currentPage,
                bookmarkedPages = bookmarkedPages,
                onToggleBookmark = onToggleBookmark,
                onShowBookmarks = onShowBookmarks,
                onShowTableOfContents = onShowTableOfContents,
                onShowHealth = onShowHealth,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
            )
            TextButton(
                onClick = { onFullscreenChange(!isFullscreen) },
                modifier = Modifier.testTag("fullscreen_button"),
            ) {
                Text(
                    stringResource(
                        if (isFullscreen) R.string.exit_fullscreen else R.string.enter_fullscreen,
                    ),
                )
            }
            TextButton(onClick = onOpenAnother) {
                Text(stringResource(R.string.reader_open_another))
            }
        },
        modifier = modifier.testTag("reader_top_bar"),
    )
}

@Composable
private fun PdfHealthDialog(result: PdfHealthResult?, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.pdf_health)) },
        text = {
            when (result) {
                null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp).testTag("pdf_health_progress"),
                    )
                    Text(
                        text = stringResource(R.string.pdf_health_checking),
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
                is PdfHealthResult.Healthy -> PdfHealthReportContent(result.report)
                is PdfHealthResult.UnreadablePage -> Text(
                    stringResource(R.string.pdf_health_unreadable_page, result.pageIndex + 1),
                    color = MaterialTheme.colorScheme.error,
                )
                PdfHealthResult.PermissionDenied -> Text(
                    stringResource(R.string.pdf_health_permission_denied),
                    color = MaterialTheme.colorScheme.error,
                )
                PdfHealthResult.Failed -> Text(
                    stringResource(R.string.pdf_health_failed),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onClose, modifier = Modifier.testTag("pdf_health_close")) {
                Text(stringResource(R.string.close))
            }
        },
        modifier = Modifier.testTag("pdf_health_dialog"),
    )
}

@Composable
private fun PdfHealthReportContent(report: PdfHealthReport) {
    val context = LocalContext.current
    Column {
        Text(
            text = stringResource(R.string.pdf_health_healthy, report.pageCount),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("pdf_health_healthy"),
        )
        HealthRow(stringResource(R.string.pdf_health_pages), report.pageCount.toString())
        HealthRow(
            stringResource(R.string.pdf_health_file_size),
            report.fileSizeBytes?.let { Formatter.formatShortFileSize(context, it) }
                ?: stringResource(R.string.unknown),
        )
        HealthRow(
            stringResource(R.string.pdf_health_searchable_text),
            stringResource(if (report.hasSearchableText) R.string.yes else R.string.no),
        )
        HealthRow(
            stringResource(R.string.pdf_health_table_of_contents),
            stringResource(if (report.hasTableOfContents) R.string.yes else R.string.no),
        )
        report.title?.let { HealthRow(stringResource(R.string.pdf_health_title), it) }
        report.author?.let { HealthRow(stringResource(R.string.pdf_health_author), it) }
    }
}

@Composable
private fun HealthRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TableOfContentsDialog(
    result: PdfTableOfContentsResult?,
    onNavigate: (Int) -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.table_of_contents)) },
        text = {
            when (result) {
                null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("table_of_contents_progress"),
                    )
                    Text(
                        text = stringResource(R.string.table_of_contents_loading),
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
                PdfTableOfContentsResult.Empty -> Text(
                    stringResource(R.string.table_of_contents_empty),
                )
                PdfTableOfContentsResult.Failed -> Text(
                    stringResource(R.string.table_of_contents_failed),
                    color = MaterialTheme.colorScheme.error,
                )
                is PdfTableOfContentsResult.Entries -> LazyColumn(
                    modifier = Modifier.heightIn(max = 440.dp),
                ) {
                    items(result.entries) { entry ->
                        TableOfContentsEntry(entry, onNavigate)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose, modifier = Modifier.testTag("table_of_contents_close")) {
                Text(stringResource(R.string.close))
            }
        },
        modifier = Modifier.testTag("table_of_contents_dialog"),
    )
}

@Composable
private fun TableOfContentsEntry(entry: PdfOutlineEntry, onNavigate: (Int) -> Unit) {
    TextButton(
        onClick = { onNavigate(entry.pageIndex) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (entry.depth.coerceAtMost(6) * 20).dp)
            .testTag("table_of_contents_page_${entry.pageIndex + 1}"),
    ) {
        Text(
            text = entry.title,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(stringResource(R.string.page_label, entry.pageIndex + 1))
    }
}

@Composable
private fun BookmarksDialog(
    bookmarkedPages: Set<Int>,
    onNavigate: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.bookmarks)) },
        text = {
            if (bookmarkedPages.isEmpty()) {
                Text(stringResource(R.string.no_bookmarks))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(bookmarkedPages.sorted(), key = { it }) { pageIndex ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                onClick = { onNavigate(pageIndex) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("bookmark_page_${pageIndex + 1}"),
                            ) {
                                Text(stringResource(R.string.page_label, pageIndex + 1))
                            }
                            TextButton(
                                onClick = { onRemove(pageIndex) },
                                modifier = Modifier.testTag("remove_bookmark_${pageIndex + 1}"),
                            ) {
                                Text(stringResource(R.string.remove))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose, modifier = Modifier.testTag("bookmarks_close")) {
                Text(stringResource(R.string.close))
            }
        },
        modifier = Modifier.testTag("bookmarks_dialog"),
    )
}

@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    inProgress: Boolean,
    resultCount: Int,
    selectedIndex: Int,
    messageResource: Int?,
    onSubmit: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val previousDescription = stringResource(R.string.search_previous)
    val nextDescription = stringResource(R.string.search_next)
    val closeDescription = stringResource(R.string.search_close)
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("search_top_bar"),
        tonalElevation = 3.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .testTag("search_query"),
                    singleLine = true,
                    label = { Text(stringResource(R.string.search_query)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                )
                TextButton(
                    onClick = onSubmit,
                    enabled = query.isNotBlank() && !inProgress,
                    modifier = Modifier.testTag("search_submit"),
                ) { Text(stringResource(R.string.search_submit)) }
                TextButton(
                    onClick = onClose,
                    modifier = Modifier
                        .semantics { contentDescription = closeDescription }
                        .testTag("search_close"),
                ) { Text("×") }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    inProgress -> CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("search_progress"),
                    )
                    messageResource != null -> Text(
                        text = stringResource(messageResource),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    resultCount > 0 -> Text(
                        text = stringResource(
                            R.string.search_result_count,
                            selectedIndex + 1,
                            resultCount,
                        ),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (resultCount > 0) {
                    TextButton(
                        onClick = onPrevious,
                        modifier = Modifier
                            .semantics { contentDescription = previousDescription }
                            .testTag("search_previous"),
                    ) { Text(previousDescription) }
                    TextButton(
                        onClick = onNext,
                        modifier = Modifier
                            .semantics { contentDescription = nextDescription }
                            .testTag("search_next"),
                    ) { Text(nextDescription) }
                }
            }
        }
    }
}

@Composable
private fun ReaderPages(
    document: PdfOpenState.Opened,
    readerMode: ReaderMode,
    verticalState: LazyListState,
    horizontalState: LazyListState,
    pagerState: PagerState,
    renderPage: suspend (pageIndex: Int, targetWidth: Int) -> PageRenderResult,
    onPageTap: () -> Unit,
    nightAppearance: Boolean,
    searchMatchesByPage: Map<Int, List<PdfSearchMatch>>,
    selectedSearchMatch: PdfSearchMatch?,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        when (readerMode) {
            ReaderMode.VerticalContinuous -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("reader_vertical"),
                state = verticalState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(count = document.pageCount, key = { it }) { pageIndex ->
                    PdfPageItem(
                        pageIndex = pageIndex,
                        pageCount = document.pageCount,
                        documentIdentity = document.uri,
                        renderPage = renderPage,
                        onPageTap = onPageTap,
                        nightAppearance = nightAppearance,
                        searchMatches = searchMatchesByPage[pageIndex].orEmpty(),
                        selectedSearchMatch = selectedSearchMatch,
                    )
                }
            }

            ReaderMode.HorizontalContinuous -> LazyRow(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("reader_horizontal"),
                state = horizontalState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(count = document.pageCount, key = { it }) { pageIndex ->
                    PdfPageItem(
                        pageIndex = pageIndex,
                        pageCount = document.pageCount,
                        documentIdentity = document.uri,
                        renderPage = renderPage,
                        modifier = Modifier
                            .width(maxWidth - 24.dp)
                            .fillMaxHeight(),
                        fitToViewport = true,
                        onPageTap = onPageTap,
                        nightAppearance = nightAppearance,
                        searchMatches = searchMatchesByPage[pageIndex].orEmpty(),
                        selectedSearchMatch = selectedSearchMatch,
                    )
                }
            }

            ReaderMode.SinglePage -> HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("reader_single_page"),
                key = { it },
            ) { pageIndex ->
                PdfPageItem(
                    pageIndex = pageIndex,
                    pageCount = document.pageCount,
                    documentIdentity = document.uri,
                    renderPage = renderPage,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    fitToViewport = true,
                    onPageTap = onPageTap,
                    nightAppearance = nightAppearance,
                    searchMatches = searchMatchesByPage[pageIndex].orEmpty(),
                    selectedSearchMatch = selectedSearchMatch,
                )
            }
        }
    }
}

@Composable
private fun ReaderModeMenu(
    selectedMode: ReaderMode,
    onModeSelected: (ReaderMode) -> Unit,
    nightAppearance: Boolean,
    onNightAppearanceChange: (Boolean) -> Unit,
    onSearchRequested: () -> Unit,
    currentPage: Int,
    bookmarkedPages: Set<Int>,
    onToggleBookmark: (Int) -> Unit,
    onShowBookmarks: () -> Unit,
    onShowTableOfContents: () -> Unit,
    onShowHealth: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val controlDescription = stringResource(R.string.reader_mode)
    val appearanceDescription = stringResource(R.string.page_appearance)
    val selectedLabel = stringResource(selectedMode.labelResource)
    val appearanceState = stringResource(
        if (nightAppearance) R.string.night_appearance_enabled
        else R.string.night_appearance_disabled,
    )
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier
                .semantics {
                    contentDescription = controlDescription
                    stateDescription = "$selectedLabel, $appearanceState"
                }
                .testTag("reader_mode_button"),
        ) {
            Text(selectedLabel)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            ReaderMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(stringResource(mode.labelResource)) },
                    onClick = {
                        expanded = false
                        onModeSelected(mode)
                    },
                    modifier = Modifier
                        .semantics { selected = mode == selectedMode }
                        .testTag("reader_mode_${mode.name}"),
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (isFavorite) R.string.remove_from_favorites
                            else R.string.add_to_favorites,
                        ),
                    )
                },
                onClick = {
                    expanded = false
                    onToggleFavorite()
                },
                modifier = Modifier.testTag("toggle_favorite_file"),
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.pdf_health)) },
                onClick = {
                    expanded = false
                    onShowHealth()
                },
                modifier = Modifier.testTag("pdf_health_button"),
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.table_of_contents)) },
                onClick = {
                    expanded = false
                    onShowTableOfContents()
                },
                modifier = Modifier.testTag("table_of_contents_button"),
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.search_document)) },
                onClick = {
                    expanded = false
                    onSearchRequested()
                },
                modifier = Modifier.testTag("search_button"),
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (currentPage in bookmarkedPages) R.string.remove_bookmark
                            else R.string.bookmark_page,
                            currentPage + 1,
                        ),
                    )
                },
                onClick = {
                    expanded = false
                    onToggleBookmark(currentPage)
                },
                modifier = Modifier.testTag("toggle_bookmark_button"),
            )
            DropdownMenuItem(
                text = {
                    Text(stringResource(R.string.bookmarks_count, bookmarkedPages.size))
                },
                onClick = {
                    expanded = false
                    onShowBookmarks()
                },
                modifier = Modifier.testTag("bookmarks_button"),
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (nightAppearance) R.string.original_appearance
                            else R.string.night_appearance,
                        ),
                    )
                },
                onClick = {
                    expanded = false
                    onNightAppearanceChange(!nightAppearance)
                },
                modifier = Modifier
                    .semantics {
                        contentDescription = appearanceDescription
                        stateDescription = appearanceState
                    }
                    .testTag("night_appearance_button"),
            )
        }
    }
}

@Composable
private fun PdfPageItem(
    pageIndex: Int,
    pageCount: Int,
    documentIdentity: Any,
    renderPage: suspend (pageIndex: Int, targetWidth: Int) -> PageRenderResult,
    modifier: Modifier = Modifier,
    fitToViewport: Boolean = false,
    onPageTap: () -> Unit = {},
    nightAppearance: Boolean = false,
    searchMatches: List<PdfSearchMatch> = emptyList(),
    selectedSearchMatch: PdfSearchMatch? = null,
) {
    val pageNumber = pageIndex + 1
    var zoomState by remember(documentIdentity, pageIndex) { mutableStateOf(PageZoomState()) }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = if (fitToViewport) {
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            } else {
                Modifier.fillMaxWidth()
            },
            shadowElevation = 2.dp,
            color = if (nightAppearance) ReaderNightSurface else MaterialTheme.colorScheme.surface,
        ) {
            BoxWithConstraints(
                modifier = if (fitToViewport) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
            ) {
                val density = LocalDensity.current
                val baseWidth = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
                var attempt by remember(documentIdentity, pageIndex) { mutableIntStateOf(0) }
                var qualityScale by remember(documentIdentity, pageIndex) { mutableFloatStateOf(1f) }
                var pageState by remember(documentIdentity, pageIndex, baseWidth) {
                    mutableStateOf<PageRenderResult?>(null)
                }
                val targetWidth = (baseWidth * qualityScale).roundToInt().coerceAtLeast(1)

                LaunchedEffect(zoomState.scale) {
                    delay(180)
                    qualityScale = zoomState.scale
                }

                LaunchedEffect(documentIdentity, pageIndex, targetWidth, attempt) {
                    val result = renderPage(pageIndex, targetWidth)
                    if (result is PageRenderResult.Ready || pageState == null) {
                        pageState = result
                    }
                }

                when (val result = pageState) {
                    null -> PageLoading(pageNumber, fitToViewport)
                    is PageRenderResult.Failed -> PageFailure(
                        pageNumber = pageNumber,
                        messageResource = result.reason.messageResource,
                        onRetry = { attempt++ },
                    )
                    is PageRenderResult.Ready -> RenderedPage(
                        bitmap = result.bitmap,
                        pageNumber = pageNumber,
                        pageCount = pageCount,
                        zoomState = zoomState,
                        onZoomStateChange = { zoomState = it },
                        fitToViewport = fitToViewport,
                        onPageTap = onPageTap,
                        nightAppearance = nightAppearance,
                        searchMatches = searchMatches,
                        selectedSearchMatch = selectedSearchMatch,
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.page_number, pageNumber, pageCount),
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PageLoading(pageNumber: Int, fillViewport: Boolean) {
    Box(
        modifier = (if (fillViewport) Modifier.fillMaxSize() else Modifier
            .fillMaxWidth()
            .heightIn(min = 280.dp))
            .testTag("page_loading_$pageNumber"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(40.dp))
            Text(
                text = stringResource(R.string.page_loading, pageNumber),
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PageFailure(pageNumber: Int, messageResource: Int, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 280.dp)
            .testTag("page_error_$pageNumber"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(messageResource, pageNumber),
                color = MaterialTheme.colorScheme.error,
            )
            Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun RenderedPage(
    bitmap: Bitmap,
    pageNumber: Int,
    pageCount: Int,
    zoomState: PageZoomState,
    onZoomStateChange: (PageZoomState) -> Unit,
    fitToViewport: Boolean,
    onPageTap: () -> Unit,
    nightAppearance: Boolean,
    searchMatches: List<PdfSearchMatch>,
    selectedSearchMatch: PdfSearchMatch?,
) {
    val description = stringResource(R.string.page_content_description, pageNumber, pageCount)
    val zoomDescription = stringResource(R.string.zoom_state, (zoomState.scale * 100).roundToInt())
    val zoomInLabel = stringResource(R.string.zoom_in)
    val zoomOutLabel = stringResource(R.string.zoom_out)
    val resetLabel = stringResource(R.string.reset_zoom)
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val viewportSize = Size(viewport.width.toFloat(), viewport.height.toFloat())
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        onZoomStateChange(
            zoomState.transform(
                zoomChange = zoomChange,
                panChange = panChange,
                viewportSize = viewportSize,
            ),
        )
    }

    Box(
        modifier = (if (fitToViewport) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
            .clipToBounds()
            .onSizeChanged { viewport = it },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = (if (fitToViewport) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .graphicsLayer {
                    scaleX = zoomState.scale
                    scaleY = zoomState.scale
                    translationX = zoomState.offset.x
                    translationY = zoomState.offset.y
                }
                .transformable(
                    state = transformableState,
                    canPan = { zoomState.isZoomed },
                    lockRotationOnZoomPan = true,
                )
                .pointerInput(viewport, zoomState.isZoomed) {
                    detectTapGestures(
                        onTap = { onPageTap() },
                        onDoubleTap = { position ->
                            onZoomStateChange(zoomState.toggle(viewportSize, position))
                        },
                    )
                }
                .semantics {
                    contentDescription = description
                    stateDescription = zoomDescription
                    customActions = listOf(
                        CustomAccessibilityAction(zoomInLabel) {
                            onZoomStateChange(
                                zoomState.transform(1.5f, Offset.Zero, viewportSize),
                            )
                            true
                        },
                        CustomAccessibilityAction(zoomOutLabel) {
                            onZoomStateChange(
                                zoomState.transform(1f / 1.5f, Offset.Zero, viewportSize),
                            )
                            true
                        },
                        CustomAccessibilityAction(resetLabel) {
                            onZoomStateChange(PageZoomState())
                            true
                        },
                    )
                }
                .testTag("pdf_page_$pageNumber"),
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = if (fitToViewport) ContentScale.Fit else ContentScale.FillWidth,
                colorFilter = if (nightAppearance) NightPageColorFilter else null,
                modifier = if (fitToViewport) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
            )
            if (searchMatches.isNotEmpty()) {
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .testTag("search_highlights_$pageNumber"),
                ) {
                    val contentScale = min(
                        size.width / bitmap.width.toFloat(),
                        size.height / bitmap.height.toFloat(),
                    )
                    val contentWidth = bitmap.width * contentScale
                    val contentHeight = bitmap.height * contentScale
                    val contentLeft = (size.width - contentWidth) / 2f
                    val contentTop = (size.height - contentHeight) / 2f
                    searchMatches.forEach { match ->
                        val highlightColor = if (match == selectedSearchMatch) {
                            SelectedSearchHighlight
                        } else {
                            SearchHighlight
                        }
                        match.bounds.forEach { bounds ->
                            drawRect(
                                color = highlightColor,
                                topLeft = Offset(
                                    contentLeft + bounds.left * contentWidth,
                                    contentTop + bounds.top * contentHeight,
                                ),
                                size = Size(
                                    (bounds.right - bounds.left) * contentWidth,
                                    (bounds.bottom - bounds.top) * contentHeight,
                                ),
                            )
                        }
                    }
                }
            }
        }

        if (zoomState.isZoomed) {
            TextButton(
                onClick = { onZoomStateChange(PageZoomState()) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .testTag("reset_zoom_$pageNumber"),
            ) {
                Text(resetLabel)
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val ChromeAutoHideMillis = 3_000L
private val ReaderNightBackground = Color(0xFF0E0E0E)
private val ReaderNightSurface = Color(0xFF121212)
private val SearchHighlight = Color(0xFFFFEB3B).copy(alpha = 0.38f)
private val SelectedSearchHighlight = Color(0xFFFF9800).copy(alpha = 0.58f)
private val NightPageColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)
