package com.example

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.ArticleEntity
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ArticleViewModel
import com.example.viewmodel.DictionaryType
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class BrowserTab { BROWSER, READING, HISTORY, LIBRARY }

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: ArticleViewModel = viewModel()
                val context = LocalContext.current
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val coroutineScope = rememberCoroutineScope()

                // Observe Toast messages from the ViewModel
                LaunchedEffect(Unit) {
                    viewModel.toastMessage.collectLatest { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }

                val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
                val history by viewModel.history.collectAsStateWithLifecycle()
                val webBookmarks by viewModel.webBookmarks.collectAsStateWithLifecycle()
                val currentArticle by viewModel.currentArticle.collectAsStateWithLifecycle()
                val selectedWord by viewModel.selectedWord.collectAsStateWithLifecycle()

                var addressBarText by remember { mutableStateOf("") }
                val isLoadingWebPage by viewModel.isLoading.collectAsStateWithLifecycle()
                var activeTab by remember { mutableStateOf(BrowserTab.BROWSER) }
                var browserUrl by remember { mutableStateOf("") }
                var extractionTriggerCount by remember { mutableStateOf(0) }

                val navigateToUrl: (String) -> Unit = { urlOrQuery ->
                    val clean = urlOrQuery.trim()
                    if (clean.isNotEmpty()) {
                        val target = if (clean.contains(".") && !clean.contains(" ")) {
                            if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
                                "https://$clean"
                            } else {
                                clean
                            }
                        } else {
                            try {
                                "https://www.google.com/search?q=${java.net.URLEncoder.encode(clean, "UTF-8")}"
                            } catch (e: Exception) {
                                "https://www.google.com/search?q=$clean"
                            }
                        }
                        browserUrl = target
                        addressBarText = target
                        activeTab = BrowserTab.BROWSER
                    }
                }

                LaunchedEffect(currentArticle) {
                    currentArticle?.let {
                        addressBarText = it.url ?: it.title
                        activeTab = BrowserTab.READING
                    }
                }

                // Register Back Button behaviour: Return home or return to browser first
                BackHandler(enabled = activeTab != BrowserTab.BROWSER || currentArticle != null) {
                    if (activeTab != BrowserTab.BROWSER) {
                        activeTab = BrowserTab.BROWSER
                    } else if (currentArticle != null) {
                        viewModel.goHome()
                    }
                }

                ModalNavigationDrawer(
                    gesturesEnabled = false,
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(
                            drawerContainerColor = Color(0xFFF8FAFD)
                        ) {
                            Spacer(modifier = Modifier.statusBarsPadding())
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SMART DICT BROWSER",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.sp
                                    ),
                                    color = Color(0xFF1B1B1F)
                                )
                                IconButton(
                                    onClick = { coroutineScope.launch { drawerState.close() } },
                                    modifier = Modifier.background(Color(0xFFEDF1F9), CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Đóng", tint = Color(0xFF1B1B1F))
                                }
                            }
                            HorizontalDivider(color = Color(0xFFE1E3E1))

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(horizontal = 16.dp)
                            ) {
                                // Bookmarks section (Đã đánh dấu ⭐)
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp, start = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Star,
                                                contentDescription = null,
                                                tint = Color(0xFFFFD700),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "ĐÃ ĐÁNH DẤU ⭐",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = Color(0xFF74777F)
                                            )
                                        }
                                        if (bookmarks.isNotEmpty()) {
                                            IconButton(
                                                onClick = { viewModel.clearAllBookmarks() },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Xóa tất cả đánh dấu",
                                                    tint = Color(0xFFBA1A1A),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                if (bookmarks.isEmpty()) {
                                    item {
                                        Text(
                                            text = "Nhấn nút ⭐ khi đang đọc để lưu các bài học yêu thích tại đây.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color(0xFF74777F),
                                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 16.dp)
                                        )
                                    }
                                } else {
                                    items(bookmarks, key = { "b_${it.id}" }) { article ->
                                        SidebarItem(
                                            article = article,
                                            onItemClick = {
                                                viewModel.loadArticle(article)
                                                coroutineScope.launch { drawerState.close() }
                                            },
                                            onDeleteClick = { viewModel.deleteBookmark(article) },
                                            modifier = Modifier.testTag("bookmark_item_${article.id}")
                                        )
                                    }
                                }

                                item {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 16.dp),
                                        color = Color(0xFFE1E3E1)
                                    )
                                }

                                // History section (Lịch sử đọc ⏳)
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp, start = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Refresh,
                                                contentDescription = null,
                                                tint = Color(0xFF03CF5D),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "LỊCH SỬ ĐÃ ĐỌC ⏳",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = Color(0xFF74777F)
                                            )
                                        }
                                        if (history.isNotEmpty()) {
                                            IconButton(
                                                onClick = { viewModel.clearAllHistory() },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Xóa tất cả lịch sử",
                                                    tint = Color(0xFFBA1A1A),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                if (history.isEmpty()) {
                                    item {
                                        Text(
                                            text = "Chưa có bài viết nào trong lịch sử.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color(0xFF74777F),
                                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                                        )
                                    }
                                } else {
                                    items(history, key = { "h_${it.id}" }) { article ->
                                        SidebarItem(
                                            article = article,
                                            onItemClick = {
                                                if (!article.url.isNullOrBlank()) {
                                                    browserUrl = article.url
                                                    activeTab = BrowserTab.BROWSER
                                                } else {
                                                    viewModel.loadArticle(article)
                                                    activeTab = BrowserTab.READING
                                                }
                                                coroutineScope.launch { drawerState.close() }
                                            },
                                            onDeleteClick = { viewModel.deleteHistory(article) },
                                            modifier = Modifier.testTag("history_item_${article.id}")
                                        )
                                    }
                                }
                            }
                        }
                    }
                ) {
                    val keyboardController = LocalSoftwareKeyboardController.current

                    Scaffold(
                        containerColor = Color(0xFFF8FAFD),
                        topBar = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White)
                                    .statusBarsPadding()
                            ) {
                                // Address Bar layout
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IconButton(
                                        onClick = { coroutineScope.launch { drawerState.open() } },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(Color(0xFFEDF1F9), CircleShape)
                                            .testTag("sidebar_toggle_button")
                                    ) {
                                        Icon(
                                            Icons.Default.Menu,
                                            contentDescription = "Menu chính",
                                            tint = Color(0xFF1B1B1F),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Search & Address Row
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFEDF1F9))
                                            .padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Tìm kiếm",
                                            tint = Color(0xFF74777F),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        
                                        BasicTextField(
                                            value = addressBarText,
                                            onValueChange = { addressBarText = it },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("address_bar_input"),
                                            singleLine = true,
                                            textStyle = TextStyle(
                                                color = Color(0xFF1B1B1F),
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 14.sp
                                            ),
                                            keyboardOptions = KeyboardOptions(
                                                imeAction = ImeAction.Go
                                            ),
                                            keyboardActions = KeyboardActions(
                                                onGo = {
                                                    keyboardController?.hide()
                                                    if (addressBarText.trim().isNotEmpty()) {
                                                        navigateToUrl(addressBarText)
                                                    }
                                                }
                                            ),
                                            decorationBox = { innerTextField ->
                                                if (addressBarText.isEmpty()) {
                                                    Text(
                                                        text = "Nhập URL (ví dụ: naver.com) để tra từ...",
                                                        color = Color(0xFF74777F),
                                                        fontSize = 13.sp
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        )

                                        if (addressBarText.isNotEmpty()) {
                                            IconButton(
                                                onClick = { addressBarText = "" },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Clear,
                                                    contentDescription = "Xóa địa chỉ",
                                                    tint = Color(0xFF74777F),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Refresh/Load
                                    IconButton(
                                        onClick = {
                                            keyboardController?.hide()
                                            if (addressBarText.trim().isNotEmpty()) {
                                                navigateToUrl(addressBarText)
                                            }
                                        },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(Color(0xFF03CF5D), RoundedCornerShape(12.dp))
                                            .testTag("go_button")
                                    ) {
                                        Icon(
                                            Icons.Default.ArrowForward,
                                            contentDescription = "Truy cập",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    if (currentArticle != null) {
                                        val isBookmarked = currentArticle?.isBookmarked == true
                                        IconButton(
                                            onClick = { viewModel.toggleBookmark() },
                                            modifier = Modifier
                                                .size(44.dp)
                                                .background(Color(0xFFEDF1F9), RoundedCornerShape(12.dp))
                                                .testTag("bookmark_toggle_button")
                                        ) {
                                            Icon(
                                                Icons.Default.Star,
                                                contentDescription = "Thêm dấu trang",
                                                tint = if (isBookmarked) Color(0xFF03CF5D) else Color(0xFF74777F),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    } else if (activeTab == BrowserTab.BROWSER && browserUrl.isNotEmpty()) {
                                        val isWebBookmarked = webBookmarks.any { it.url == browserUrl }
                                        IconButton(
                                            onClick = { viewModel.toggleWebBookmark(addressBarText, browserUrl) },
                                            modifier = Modifier
                                                .size(44.dp)
                                                .background(Color(0xFFEDF1F9), RoundedCornerShape(12.dp))
                                                .testTag("web_bookmark_toggle_indicator")
                                        ) {
                                            Icon(
                                                imageVector = if (isWebBookmarked) Icons.Default.Star else Icons.Outlined.Star,
                                                contentDescription = "Lưu dấu trang web",
                                                tint = if (isWebBookmarked) Color(0xFF03CF5D) else Color(0xFF74777F),
                                                modifier = Modifier.size(20.dp)
                                             )
                                        }
                                    }
                                }

                                // Interactive Category Navigation Tabs matching HTML
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    BrowserTab.values().forEach { tab ->
                                        val isSelected = activeTab == tab
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(
                                                    if (isSelected) Color(0xFF03CF5D)
                                                    else Color.White
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isSelected) Color.Transparent else Color(0xFFC4C7C5),
                                                    shape = RoundedCornerShape(20.dp)
                                                )
                                                .clickable { activeTab = tab }
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                                .testTag("tab_button_${tab.name.lowercase()}"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = when (tab) {
                                                    BrowserTab.BROWSER -> "TRÌNH DUYỆT 🌐"
                                                    BrowserTab.READING -> "ĐỌC CHỮ 📖"
                                                    BrowserTab.HISTORY -> "LỊCH SỬ ⏳"
                                                    BrowserTab.LIBRARY -> "LƯU TRỮ ⭐"
                                                },
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black,
                                                letterSpacing = 0.5.sp,
                                                color = if (isSelected) Color.White else Color(0xFF44474E)
                                            )
                                        }
                                    }
                                }
                                
                                HorizontalDivider(color = Color(0xFFE1E3E1))
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            if (isLoadingWebPage) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFFF8FAFD))
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(color = Color(0xFF03CF5D))
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Text(
                                        text = "ĐANG TẢI WEB VÀ PHÂN TÍCH TỪ VỰNG...",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                        color = Color(0xFF1B1B1F)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Trình duyệt đang dịch và tách các từ tiếng Hàn trong trang để click tra cứu trực tiếp.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF74777F),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                when (activeTab) {
                                    BrowserTab.BROWSER -> {
                                        if (browserUrl.isEmpty()) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color(0xFFF8FAFD))
                                                    .verticalScroll(rememberScrollState())
                                                    .padding(24.dp),
                                                verticalArrangement = Arrangement.spacedBy(20.dp)
                                            ) {
                                                Card(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                                    shape = RoundedCornerShape(32.dp),
                                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                                ) {
                                                    Column(modifier = Modifier.padding(24.dp)) {
                                                        Text(
                                                            text = "Trình duyệt Hàn ngữ thông minh",
                                                            style = MaterialTheme.typography.displayMedium,
                                                            color = Color(0xFF1B1B1F)
                                                        )
                                                        Spacer(modifier = Modifier.height(10.dp))
                                                        Text(
                                                            text = "Nhập bất kỳ liên kết tin tức báo chí Hàn Quốc nào ở trên. Trình duyệt thông minh sẽ tải văn bản hiển thị chế độ đọc từ vựng một-chạm tra từ điển Naver tức thì.",
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            color = Color(0xFF44474E),
                                                            lineHeight = 24.sp
                                                        )
                                                    }
                                                }

                                                if (webBookmarks.isNotEmpty()) {
                                                    Text(
                                                        text = "TRANG ĐÃ ĐÁNH DẤU ⭐",
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color = Color(0xFF02873D),
                                                        fontWeight = FontWeight.Bold
                                                    )

                                                    webBookmarks.forEach { bookmark ->
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clip(RoundedCornerShape(16.dp))
                                                                .background(Color.White)
                                                                .border(1.dp, Color(0xFFE1E3E1), RoundedCornerShape(16.dp))
                                                                .clickable {
                                                                    navigateToUrl(bookmark.url)
                                                                 }
                                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.weight(1f),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(40.dp)
                                                                        .clip(CircleShape)
                                                                        .background(Color(0xFFE2FBE9)),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Star,
                                                                        contentDescription = null,
                                                                        tint = Color(0xFF02873D),
                                                                        modifier = Modifier.size(18.dp)
                                                                    )
                                                                }
                                                                Spacer(modifier = Modifier.width(12.dp))
                                                                Column(modifier = Modifier.weight(1f)) {
                                                                    Text(
                                                                        text = bookmark.title,
                                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                                        color = Color(0xFF1B1B1F),
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis
                                                                    )
                                                                    Text(
                                                                        text = bookmark.url,
                                                                        style = MaterialTheme.typography.bodySmall,
                                                                        color = Color(0xFF74777F),
                                                                        maxLines = 1,
                                                                        overflow = TextOverflow.Ellipsis
                                                                    )
                                                                }
                                                            }

                                                            IconButton(
                                                                onClick = { viewModel.deleteWebBookmark(bookmark) },
                                                                modifier = Modifier.size(36.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Delete,
                                                                    contentDescription = "Xóa dấu trang",
                                                                    tint = Color(0xFFBA1A1A),
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                Text(
                                                    text = "ĐỀ XUẤT TRANG QUEN THUỘC 🌐",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = Color(0xFF74777F),
                                                    fontWeight = FontWeight.Bold
                                                )

                                                listOf(
                                                    "n.news.naver.com" to "Naver News Portal 📰",
                                                    "korean.dict.naver.com" to "Naver Dictionary 📚",
                                                    "chosun.com" to "Chosun Ilbo (Báo Chosun) 🗞️",
                                                    "daum.net" to "Daum Portal 💻",
                                                    "yna.co.kr" to "Yonhap News 📣",
                                                    "donga.com" to "DongA Daily News 📰"
                                                ).forEach { (url, label) ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(16.dp))
                                                            .background(Color.White)
                                                            .border(1.dp, Color(0xFFE1E3E1), RoundedCornerShape(16.dp))
                                                            .clickable {
                                                                navigateToUrl(url)
                                                            }
                                                            .padding(horizontal = 20.dp, vertical = 14.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(8.dp)
                                                                    .clip(CircleShape)
                                                                    .background(Color(0xFF03CF5D))
                                                            )
                                                            Spacer(modifier = Modifier.width(12.dp))
                                                            Text(
                                                                text = label,
                                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                                color = Color(0xFF1B1B1F)
                                                            )
                                                        }
                                                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color(0xFF74777F), modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                        } else {
                                            var browserDictWeight by remember { mutableStateOf(0.0f) }
                                            val dictionaryUrl = viewModel.getDictionaryUrl()
                                            
                                            LaunchedEffect(selectedWord) {
                                                if (selectedWord != null && browserDictWeight == 0f) {
                                                    browserDictWeight = 0.48f
                                                }
                                            }
                                            
                                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                                val isWideScreen = maxWidth > 600.dp
                                                
                                                if (isWideScreen) {
                                                    Row(modifier = Modifier.fillMaxSize()) {
                                                        Box(
                                                            modifier = Modifier
                                                                .weight(if (browserDictWeight == 0f) 1f else 1f - browserDictWeight)
                                                                .fillMaxHeight()
                                                        ) {
                                                            BrowserWebView(
                                                                url = browserUrl,
                                                                onUrlChanged = { newUrl ->
                                                                    browserUrl = newUrl
                                                                    addressBarText = newUrl
                                                                },
                                                                onWordSelected = { word ->
                                                                    viewModel.selectWord(word)
                                                                },
                                                                extractionTriggerCount = extractionTriggerCount,
                                                                onContentExtracted = { title, content ->
                                                                    viewModel.loadCustomArticle(title, content)
                                                                },
                                                                modifier = Modifier.fillMaxSize()
                                                            )
                                                            
                                                            ExtendedFloatingActionButton(
                                                                text = { Text("ĐỌC CHỮ 📖", fontWeight = FontWeight.Bold, color = Color.White) },
                                                                icon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White) },
                                                                containerColor = Color(0xFF03CF5D),
                                                                onClick = {
                                                                    if (browserUrl.isNotEmpty()) {
                                                                        viewModel.extractAndLoadArticleFromUrl(browserUrl)
                                                                    } else {
                                                                        extractionTriggerCount++
                                                                    }
                                                                },
                                                                modifier = Modifier
                                                                    .align(Alignment.BottomEnd)
                                                                    .padding(24.dp)
                                                                    .testTag("reading_mode_button")
                                                            )
                                                        }
                                                        
                                                        if (browserDictWeight > 0f) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .width(1.dp)
                                                                    .fillMaxHeight()
                                                                    .background(Color(0xFFE1E3E1))
                                                            )
                                                            
                                                            DictionaryView(
                                                                selectedWord = selectedWord,
                                                                url = dictionaryUrl,
                                                                dictWeight = browserDictWeight,
                                                                viewModel = viewModel,
                                                                onWeightChange = { browserDictWeight = it },
                                                                modifier = Modifier
                                                                    .weight(browserDictWeight)
                                                                    .fillMaxHeight()
                                                            )
                                                        }
                                                    }
                                                } else {
                                                    Column(modifier = Modifier.fillMaxSize()) {
                                                        Box(
                                                            modifier = Modifier
                                                                .weight(if (browserDictWeight == 0f) 1f else 1f - browserDictWeight)
                                                                .fillMaxWidth()
                                                        ) {
                                                            BrowserWebView(
                                                                url = browserUrl,
                                                                onUrlChanged = { newUrl ->
                                                                    browserUrl = newUrl
                                                                    addressBarText = newUrl
                                                                },
                                                                onWordSelected = { word ->
                                                                    viewModel.selectWord(word)
                                                                },
                                                                extractionTriggerCount = extractionTriggerCount,
                                                                onContentExtracted = { title, content ->
                                                                    viewModel.loadCustomArticle(title, content)
                                                                },
                                                                modifier = Modifier.fillMaxSize()
                                                            )
                                                            
                                                            ExtendedFloatingActionButton(
                                                                text = { Text("ĐỌC CHỮ 📖", fontWeight = FontWeight.Bold, color = Color.White) },
                                                                icon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White) },
                                                                containerColor = Color(0xFF03CF5D),
                                                                onClick = {
                                                                    if (browserUrl.isNotEmpty()) {
                                                                        viewModel.extractAndLoadArticleFromUrl(browserUrl)
                                                                    } else {
                                                                        extractionTriggerCount++
                                                                    }
                                                                },
                                                                modifier = Modifier
                                                                    .align(Alignment.BottomEnd)
                                                                    .padding(24.dp)
                                                                    .testTag("reading_mode_button")
                                                            )
                                                        }
                                                        
                                                        if (browserDictWeight > 0f) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .height(1.dp)
                                                                    .fillMaxWidth()
                                                                    .background(Color(0xFFE1E3E1))
                                                            )
                                                            
                                                            DictionaryView(
                                                                selectedWord = selectedWord,
                                                                url = dictionaryUrl,
                                                                dictWeight = browserDictWeight,
                                                                viewModel = viewModel,
                                                                onWeightChange = { browserDictWeight = it },
                                                                modifier = Modifier
                                                                    .weight(browserDictWeight)
                                                                    .fillMaxWidth()
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    BrowserTab.READING -> {
                                        val article = currentArticle
                                        if (article == null) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color(0xFFF8FAFD))
                                                    .padding(24.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = Color(0xFFC4C7C5),
                                                    modifier = Modifier.size(64.dp)
                                                )
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Text(
                                                    text = "CHƯA CÓ BÀI ĐỌC",
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                                    color = Color(0xFF74777F)
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = "Hãy mở TRÌNH DUYỆT 🌐 để truy cập các trang báo tin tức Hàn Quốc, sau đó bấm nút ĐỌC CHỮ 📖 để phân tích sang chế độ một-chạm tra từ điển Naver kovidict.",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = Color(0xFF74777F),
                                                    textAlign = TextAlign.Center
                                                )
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Button(
                                                    onClick = { activeTab = BrowserTab.BROWSER },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03CF5D))
                                                ) {
                                                    Text("MỞ TRÌNH DUYỆT NGAY", color = Color.White, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        } else {
                                            ReadingWorkspace(
                                                article = article,
                                                selectedWord = selectedWord,
                                                viewModel = viewModel,
                                                onNavigateToBrowser = { url ->
                                                    browserUrl = url
                                                    activeTab = BrowserTab.BROWSER
                                                }
                                            )
                                        }
                                    }

                                    BrowserTab.HISTORY -> {
                                        if (history.isEmpty()) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(24.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFFC4C7C5), modifier = Modifier.size(64.dp))
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Text(
                                                    text = "LỊCH SỬ TRỐNG",
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                                    color = Color(0xFF74777F)
                                                )
                                                Text(
                                                    text = "Lịch sử của các bài báo đã tải sẽ hiển thị tại đây để đọc lại.",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = Color(0xFF74777F),
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        } else {
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
                                                         text = "Lịch sử đã đọc (${history.size})",
                                                         style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                         color = Color(0xFF1B1B1F)
                                                     )
                                                     
                                                     TextButton(
                                                         onClick = { viewModel.clearAllHistory() },
                                                         colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFBA1A1A))
                                                     ) {
                                                         Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                                         Spacer(modifier = Modifier.width(4.dp))
                                                         Text("XÓA TẤT CẢ", style = MaterialTheme.typography.labelLarge)
                                                     }
                                                 }
                                                 Spacer(modifier = Modifier.height(12.dp))
                                                 LazyColumn(
                                                     modifier = Modifier.weight(1f)
                                                 ) {
                                                     items(history, key = { "tab_h_${it.id}" }) { article ->
                                                         SidebarItem(
                                                             article = article,
                                                             onItemClick = {
                                                                 if (!article.url.isNullOrBlank()) {
                                                                     browserUrl = article.url
                                                                     activeTab = BrowserTab.BROWSER
                                                                 } else {
                                                                     viewModel.loadArticle(article)
                                                                     activeTab = BrowserTab.READING
                                                                 }
                                                             },
                                                             onDeleteClick = { viewModel.deleteHistory(article) }
                                                         )
                                                     }
                                                 }
                                            }
                                        }
                                    }
                                    BrowserTab.LIBRARY -> {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .verticalScroll(rememberScrollState())
                                                .padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            var isCreatorExpanded by remember { mutableStateOf(false) }

                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                                shape = RoundedCornerShape(24.dp),
                                                border = BorderStroke(1.dp, Color(0xFFE1E3E1))
                                            ) {
                                                Column(modifier = Modifier.padding(16.dp)) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable { isCreatorExpanded = !isCreatorExpanded },
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF03CF5D), modifier = Modifier.size(20.dp))
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Text(
                                                                text = "TỰ DÁN VĂN BẢN THỦ CÔNG 📝",
                                                                style = MaterialTheme.typography.labelLarge,
                                                                color = Color(0xFF1B1B1F)
                                                            )
                                                        }
                                                        Text(
                                                            text = if (isCreatorExpanded) "▲" else "▼",
                                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                                            color = Color(0xFF74777F)
                                                        )
                                                    }
                                                    
                                                    if (isCreatorExpanded) {
                                                        Spacer(modifier = Modifier.height(16.dp))
                                                        ArticleCreatorView(
                                                            onLoadClick = { title, content ->
                                                                viewModel.loadCustomArticle(title, content)
                                                                activeTab = BrowserTab.READING
                                                            }
                                                        )
                                                    }
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "CÁC TRANG ĐÃ ĐÁNH DẤU ⭐",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = Color(0xFF74777F),
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (bookmarks.isNotEmpty()) {
                                                    TextButton(
                                                        onClick = { viewModel.clearAllBookmarks() },
                                                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFBA1A1A)),
                                                        contentPadding = PaddingValues(0.dp),
                                                        modifier = Modifier.height(28.dp)
                                                    ) {
                                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("XÓA TẤT CẢ", style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp))
                                                    }
                                                }
                                            }

                                            if (bookmarks.isEmpty()) {
                                                Card(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                                    shape = RoundedCornerShape(20.dp),
                                                    border = BorderStroke(1.dp, Color(0xFFE1E3E1))
                                                ) {
                                                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = "Đánh dấu trống. Hãy lưu ⭐ những trang hữu ích khi đang đọc để xuất hiện nhanh lại tại đây.",
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = Color(0xFF74777F),
                                                            textAlign = TextAlign.Center
                                                        )
                                                    }
                                                }
                                            } else {
                                                bookmarks.forEach { article ->
                                                    SidebarItem(
                                                        article = article,
                                                        onItemClick = {
                                                            viewModel.loadArticle(article)
                                                            activeTab = BrowserTab.READING
                                                        },
                                                        onDeleteClick = { viewModel.deleteBookmark(article) }
                                                    )
                                                }
                                            }
                                        }
                                    }
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
fun SidebarItem(
    article: ArticleEntity,
    onItemClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable { onItemClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = article.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF1B1B1F),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            if (article.content.isNotBlank()) {
                Text(
                    text = article.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF74777F),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (!article.url.isNullOrBlank()) {
                Text(
                    text = article.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF03BB4A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(
            onClick = onDeleteClick,
            modifier = Modifier.size(28.dp).background(Color(0xFFF1F3F4), CircleShape)
        ) {
            Icon(
                Icons.Default.Clear,
                contentDescription = "Xóa",
                tint = Color(0xFFCC0000),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun ArticleCreatorView(
    onLoadClick: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFD))
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(32.dp),
            border = BorderStroke(1.dp, Color(0xFFE1E3E1)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Tạo bài học tiếng Hàn mới",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color(0xFF1B1B1F)
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("TIÊU ĐỀ BÀI HỌC", style = MaterialTheme.typography.labelLarge, color = Color(0xFF74777F)) },
                    placeholder = { Text("Ví dụ: Thời tiết mùa thu Hàn Quốc") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_title_field"),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF03CF5D),
                        unfocusedBorderColor = Color(0xFFDADCE0)
                    )
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("DÁN VĂN BẢN TIẾNG HÀN", style = MaterialTheme.typography.labelLarge, color = Color(0xFF74777F)) },
                    placeholder = { Text("Dán câu/đoạn văn tiếng Hàn cần luyện tập vào đây...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .testTag("input_content_field"),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF03CF5D),
                        unfocusedBorderColor = Color(0xFFDADCE0)
                    )
                )

                Button(
                    onClick = { onLoadClick(title, content) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF03CF5D),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("load_content_button"),
                    shape = RoundedCornerShape(30.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Bắt đầu đọc & tra từ".uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }
            }
        }

        // Informative guide block matching stylized design Card pattern
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFFE1E3E1))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF03CF5D))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "HƯỚNG DẪN SỬ DỤNG",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF74777F)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                BulletPoint(text = "Dán đoạn văn bản hoặc tiêu đề bạn yêu thích.")
                BulletPoint(text = "Hệ thống tự động phân tách văn bản thành các từ tiếng Hàn riêng biệt.")
                BulletPoint(text = "Chạm từ bất kỳ để tức tốc tìm nghĩa trên từ điển Naver đa ngữ.")
                BulletPoint(text = "Đánh dấu ⭐ để lưu trữ bài học quan trọng hoặc học lại thông qua Lịch sử.")
            }
        }
    }
}

@Composable
fun BulletPoint(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text("• ", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF03CF5D))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF44474E))
    }
}

@Composable
fun ReadingWorkspace(
    article: ArticleEntity,
    selectedWord: String?,
    viewModel: ArticleViewModel,
    onNavigateToBrowser: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var dictWeight by remember { mutableStateOf(0.48f) } // Split size constraint: default near 50-50
    val dictionaryUrl = viewModel.getDictionaryUrl()

    // Automatically expand dictionary view when a word is selected
    LaunchedEffect(selectedWord) {
        if (selectedWord != null && dictWeight == 0f) {
            dictWeight = 0.48f
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideScreen = maxWidth > 600.dp

        if (isWideScreen) {
            // Tablet or Landscape View: Side-by-side
            Row(modifier = Modifier.fillMaxSize()) {
                ArticleView(
                    article = article,
                    selectedWord = selectedWord,
                    viewModel = viewModel,
                    dictWeight = dictWeight,
                    onOpenDict = { dictWeight = 0.48f },
                    onNavigateToBrowser = onNavigateToBrowser,
                    modifier = Modifier
                        .weight(if (dictWeight == 0f) 1f else 1f - dictWeight)
                        .fillMaxHeight()
                )

                if (dictWeight > 0f) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(Color(0xFFE1E3E1))
                    )

                    DictionaryView(
                        selectedWord = selectedWord,
                        url = dictionaryUrl,
                        dictWeight = dictWeight,
                        viewModel = viewModel,
                        onWeightChange = { dictWeight = it },
                        modifier = Modifier
                            .weight(dictWeight)
                            .fillMaxHeight()
                    )
                }
            }
        } else {
            // Portrait Mobile: Vertical Split View
            Column(modifier = Modifier.fillMaxSize()) {
                ArticleView(
                    article = article,
                    selectedWord = selectedWord,
                    viewModel = viewModel,
                    dictWeight = dictWeight,
                    onOpenDict = { dictWeight = 0.48f },
                    onNavigateToBrowser = onNavigateToBrowser,
                    modifier = Modifier
                        .weight(if (dictWeight == 0f) 1f else 1f - dictWeight)
                        .fillMaxWidth()
                )

                if (dictWeight > 0f) {
                    Box(
                        modifier = Modifier
                            .height(1.dp)
                            .fillMaxWidth()
                            .background(Color(0xFFE1E3E1))
                    )

                    DictionaryView(
                        selectedWord = selectedWord,
                        url = dictionaryUrl,
                        dictWeight = dictWeight,
                        viewModel = viewModel,
                        onWeightChange = { dictWeight = it },
                        modifier = Modifier
                            .weight(dictWeight)
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ArticleView(
    article: ArticleEntity,
    selectedWord: String?,
    viewModel: ArticleViewModel,
    dictWeight: Float,
    onOpenDict: () -> Unit,
    onNavigateToBrowser: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val chatHistory by viewModel.aiChatHistory.collectAsStateWithLifecycle()
    val isAnswering by viewModel.isAnsweringQuestion.collectAsStateWithLifecycle()
    var questionInput by remember { mutableStateOf("") }

    val context = LocalContext.current
    val ttsManager = remember { com.example.utils.TtsManager(context) }
    val speakingMsgId by ttsManager.speakingMessageId.collectAsStateWithLifecycle()
    val isTtsPlaying by ttsManager.isPlaying.collectAsStateWithLifecycle()

    val bilingualParagraphs by viewModel.bilingualParagraphs.collectAsStateWithLifecycle()
    val currentPlayingParaIndex by ttsManager.currentPlayingParagraphIndex.collectAsStateWithLifecycle()
    val activeTtsMode by ttsManager.readingPlayMode.collectAsStateWithLifecycle()

    val koFontSize by viewModel.koFontSize.collectAsStateWithLifecycle()
    val viFontSize by viewModel.viFontSize.collectAsStateWithLifecycle()

    val currentSpeechRate by ttsManager.speechRate.collectAsStateWithLifecycle()
    val availableKoVoices by ttsManager.availableKoVoices.collectAsStateWithLifecycle()
    val availableViVoices by ttsManager.availableViVoices.collectAsStateWithLifecycle()
    val selectedKoVoice by ttsManager.selectedKoVoice.collectAsStateWithLifecycle()
    val selectedViVoice by ttsManager.selectedViVoice.collectAsStateWithLifecycle()
    val excludeCharacters by ttsManager.excludeCharacters.collectAsStateWithLifecycle()
    var showTtsSettings by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            ttsManager.shutdown()
        }
    }

    Column(
        modifier = modifier
            .background(Color(0xFFF8FAFD))
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(32.dp),
            border = BorderStroke(1.dp, Color(0xFFE1E3E1)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            val listState = rememberLazyListState()
            val isExtracting by viewModel.isExtractingVocab.collectAsStateWithLifecycle()

            LaunchedEffect(currentPlayingParaIndex, isTtsPlaying, dictWeight) {
                if (isTtsPlaying && currentPlayingParaIndex != null) {
                    val targetIndex = 1 + currentPlayingParaIndex!!
                    val halfViewport = if (listState.layoutInfo.viewportEndOffset > 0) {
                        listState.layoutInfo.viewportEndOffset / 2
                    } else {
                        400
                    }
                    val itemVisible = listState.layoutInfo.visibleItemsInfo.find { it.index == targetIndex }
                    val itemSize = itemVisible?.size ?: 300
                    val offset = halfViewport - (itemSize / 2)
                    try {
                        listState.animateScrollToItem(targetIndex, -offset)
                    } catch (e: Exception) {
                        try {
                            listState.animateScrollToItem(targetIndex)
                        } catch (ex: Exception) {
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Column {
                        Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { viewModel.goHome() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEDF1F9),
                            contentColor = Color(0xFF1B1B1F)
                        ),
                        shape = RoundedCornerShape(30.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Trang chủ".uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            fontSize = 11.sp
                        )
                    }

                    if (dictWeight == 0f) {
                        Button(
                            onClick = onOpenDict,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE2FBE9),
                                contentColor = Color(0xFF02873D)
                            ),
                            shape = RoundedCornerShape(30.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Tra từ điển".uppercase(),
                                style = MaterialTheme.typography.labelLarge,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = article.title,
                    style = MaterialTheme.typography.displayMedium,
                    color = Color(0xFF1B1B1F)
                )

                if (!article.url.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { onNavigateToBrowser(article.url) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE2FBE9),
                            contentColor = Color(0xFF02873D)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search, 
                            contentDescription = null, 
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Xem trang web gốc 🌐",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    color = Color(0xFFE1E3E1)
                )

                // TTS Playback & Google Translate Configuration Header Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F3F9)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFE1E3E1))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Nghe đọc & Dịch bài học".uppercase(),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
                                color = Color(0xFF1B1B1F)
                            )
                            
                            if (bilingualParagraphs.any { it.isTranslating }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = Color(0xFF03CF5D),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Đang dịch...", style = MaterialTheme.typography.labelSmall, color = Color(0xFF02873D))
                                }
                            } else {
                                IconButton(
                                    onClick = { viewModel.translateAllParagraphs() },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Dịch lại toàn bộ",
                                        tint = Color(0xFF02873D),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Chế độ phát TTS:",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF74777F)
                            )
                            
                            TextButton(
                                onClick = { showTtsSettings = !showTtsSettings },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = Color(0xFF02873D)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (showTtsSettings) "Ẩn cài đặt ▲" else "Cài đặt giọng đọc ▼",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF02873D)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val modes = listOf(
                                Pair(com.example.utils.ReadingPlayMode.KO_ONLY, "Chỉ Hàn 🇰🇷"),
                                Pair(com.example.utils.ReadingPlayMode.VI_ONLY, "Chỉ Việt 🇻🇳"),
                                Pair(com.example.utils.ReadingPlayMode.KO_THEN_VI, "Hàn ➔ Việt"),
                                Pair(com.example.utils.ReadingPlayMode.VI_THEN_KO, "Việt ➔ Hàn")
                            )
                            
                            modes.forEach { (mode, label) ->
                                val isSelected = activeTtsMode == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color(0xFF02873D) else Color(0xFFE2E4E9))
                                        .clickable { ttsManager.setReadingPlayMode(mode) }
                                        .padding(vertical = 8.dp, horizontal = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isSelected) Color.White else Color(0xFF1B1B1F),
                                        maxLines = 1,
                                        textAlign = TextAlign.Center,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = Color(0xFFD1D3D9))
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Cỡ chữ hiển thị:".uppercase(),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF1B1B1F)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Korean Font Size Column
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Tiếng Hàn 🇰🇷: ${koFontSize.toInt()}sp",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF44474E)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    FilledIconButton(
                                        onClick = { viewModel.updateKoFontSize(koFontSize - 1f) },
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFE2E4E9), contentColor = Color(0xFF1B1B1F)),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Text("-", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    }
                                    
                                    FilledIconButton(
                                        onClick = { viewModel.updateKoFontSize(koFontSize + 1f) },
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF02873D), contentColor = Color.White),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Text("+", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }

                            // Vietnamese Font Size Column
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Tiếng Việt 🇻🇳: ${viFontSize.toInt()}sp",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF44474E)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    FilledIconButton(
                                        onClick = { viewModel.updateViFontSize(viFontSize - 1f) },
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFE2E4E9), contentColor = Color(0xFF1B1B1F)),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Text("-", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    }
                                    
                                    FilledIconButton(
                                        onClick = { viewModel.updateViFontSize(viFontSize + 1f) },
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF02873D), contentColor = Color.White),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Text("+", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }
                        }

                        if (showTtsSettings) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = Color(0xFFE1E3E1))
                            Spacer(modifier = Modifier.height(12.dp))

                            // 1. Speed Control
                            Text(
                                text = "Tốc độ phát: ${String.format("%.2f", currentSpeechRate)}x",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF1B1B1F)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val rates = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(rates) { rate ->
                                        val isSelected = currentSpeechRate == rate
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) Color(0xFF02873D) else Color(0xFFE2E4E9))
                                                .clickable { ttsManager.setSpeechRate(rate) }
                                                .padding(vertical = 6.dp, horizontal = 10.dp)
                                        ) {
                                            Text(
                                                text = "${rate}x",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = if (isSelected) Color.White else Color(0xFF1B1B1F)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // 2. Korean Voice Selection
                            Text(
                                text = "Giọng đọc tiếng Hàn:",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF1B1B1F)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            if (availableKoVoices.isEmpty()) {
                                Text(
                                    text = "Sử dụng giọng mặc định tiếng Hàn (hệ thống)...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            } else {
                                var koDropdownExpanded by remember { mutableStateOf(false) }
                                Box {
                                    Button(
                                        onClick = { koDropdownExpanded = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFE2E4E9),
                                            contentColor = Color(0xFF1B1B1F)
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = selectedKoVoice?.substringAfter("ko-kr-") ?: "Mặc định hệ thống",
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = null
                                            )
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = koDropdownExpanded,
                                        onDismissRequest = { koDropdownExpanded = false },
                                        modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp)
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Mặc định hệ thống", fontWeight = if (selectedKoVoice == null) FontWeight.Bold else FontWeight.Normal) },
                                            onClick = {
                                                ttsManager.setKoVoice(null)
                                                koDropdownExpanded = false
                                            }
                                        )
                                        availableKoVoices.forEach { voiceName ->
                                            val isVoiceSelected = selectedKoVoice == voiceName
                                            DropdownMenuItem(
                                                text = { 
                                                    Text(
                                                        text = voiceName.substringAfter("ko-kr-"), 
                                                        fontWeight = if (isVoiceSelected) FontWeight.Bold else FontWeight.Normal
                                                    ) 
                                                },
                                                onClick = {
                                                    ttsManager.setKoVoice(voiceName)
                                                    koDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // 3. Vietnamese Voice Selection
                            Text(
                                text = "Giọng đọc tiếng Việt:",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF1B1B1F)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            if (availableViVoices.isEmpty()) {
                                Text(
                                    text = "Sử dụng giọng mặc định tiếng Việt (hệ thống)...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            } else {
                                var viDropdownExpanded by remember { mutableStateOf(false) }
                                Box {
                                    Button(
                                        onClick = { viDropdownExpanded = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFE2E4E9),
                                            contentColor = Color(0xFF1B1B1F)
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = selectedViVoice?.substringAfter("vi-vn-") ?: "Mặc định hệ thống",
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = null
                                            )
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = viDropdownExpanded,
                                        onDismissRequest = { viDropdownExpanded = false },
                                        modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp)
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Mặc định hệ thống", fontWeight = if (selectedViVoice == null) FontWeight.Bold else FontWeight.Normal) },
                                            onClick = {
                                                ttsManager.setViVoice(null)
                                                viDropdownExpanded = false
                                            }
                                        )
                                        availableViVoices.forEach { voiceName ->
                                            val isVoiceSelected = selectedViVoice == voiceName
                                            DropdownMenuItem(
                                                text = { 
                                                    Text(
                                                        text = voiceName.substringAfter("vi-vn-"), 
                                                        fontWeight = if (isVoiceSelected) FontWeight.Bold else FontWeight.Normal
                                                    ) 
                                                },
                                                onClick = {
                                                    ttsManager.setViVoice(voiceName)
                                                    viDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // 4. Excluded Characters Filter
                            Text(
                                text = "Ký tự loại trừ không phát (phần giải đáp AI):",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF1B1B1F)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Nhập các ký tự phân tách bằng dấu cách. Ví dụ: * # $ ( )",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF74777F)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = excludeCharacters,
                                onValueChange = { ttsManager.setExcludeCharacters(it) },
                                placeholder = { Text("Ví dụ: * # $ ( )") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("exclude_characters_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF03CF5D),
                                    unfocusedBorderColor = Color(0xFFDADCE0)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (isTtsPlaying) {
                                    ttsManager.stopReading()
                                } else {
                                    ttsManager.startReading(
                                        paragraphs = bilingualParagraphs.mapIndexed { index, p ->
                                            com.example.utils.ReadingParagraph(id = index, korean = p.korean, vietnamese = p.vietnamese)
                                        },
                                        mode = activeTtsMode,
                                        startIndex = 0
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTtsPlaying) Color.DarkGray else Color(0xFF03CF5D)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isTtsPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isTtsPlaying) "Dừng phát" else "Phát toàn bộ bài đọc",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                }
                }

                itemsIndexed(bilingualParagraphs) { index, p ->
                        val isCurrentPlaying = (currentPlayingParaIndex == index) && isTtsPlaying
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrentPlaying) Color(0xFFEDF8EE) else Color.White
                            ),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                width = if (isCurrentPlaying) 2.dp else 1.dp,
                                color = if (isCurrentPlaying) Color(0xFF03CF5D) else Color(0xFFE1E3E1)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                if (isCurrentPlaying) {
                                                    ttsManager.stopReading()
                                                } else {
                                                    ttsManager.startReading(
                                                        paragraphs = bilingualParagraphs.mapIndexed { idx, para ->
                                                            com.example.utils.ReadingParagraph(id = idx, korean = para.korean, vietnamese = para.vietnamese)
                                                        },
                                                        mode = activeTtsMode,
                                                        startIndex = index
                                                    )
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isCurrentPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                                                contentDescription = if (isCurrentPlaying) "Dừng đọc" else "Đọc đoạn từ đây",
                                                tint = Color(0xFF02873D),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Đoạn ${index + 1}",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFF74777F)
                                        )
                                    }

                                    if (p.isTranslating) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            color = Color(0xFF02873D),
                                            strokeWidth = 2.dp
                                        )
                                    } else if (p.error != null) {
                                        IconButton(
                                            onClick = { viewModel.translateSingleParagraph(index) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Thử dịch lại",
                                                tint = Color.Red,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Korean Segment with complete Word Touch lookup
                                InteractiveBodyText(
                                    text = p.korean,
                                    selectedWord = selectedWord,
                                    fontSize = koFontSize,
                                    onWordClicked = { word ->
                                        viewModel.selectWord(word)
                                    }
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Vietnamese Translation
                                if (p.isTranslating && p.vietnamese.isEmpty()) {
                                    Text(
                                        text = "Đang dịch sang tiếng Việt...",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                        ),
                                        color = Color(0xFF74777F)
                                    )
                                } else if (p.error != null && p.vietnamese.isEmpty()) {
                                    Text(
                                        text = "Lỗi dịch: ${p.error}. Vui lòng thử lại.",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                        ),
                                        color = Color.Red
                                    )
                                } else if (p.vietnamese.isNotEmpty()) {
                                    Text(
                                        text = p.vietnamese,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            fontSize = viFontSize.sp,
                                            lineHeight = (viFontSize * 1.5f).sp
                                        ),
                                        color = Color(0xFF44474E)
                                    )
                                }
                            }
                        }
                }

                item {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color(0xFFE1E3E1))
                        Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFE2FBE9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFF02873D),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Trích từ vựng bằng AI",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF1B1B1F)
                        )
                    }

                    if (isExtracting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color(0xFF03CF5D),
                            strokeWidth = 2.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (article.importantVocab != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F9F4)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFFD0EAD5))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = article.importantVocab ?: "",
                                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                                color = Color(0xFF1B1B1F)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { viewModel.extractVocabWithAI(article) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFEDF1F9),
                                        contentColor = Color(0xFF1B1B1F)
                                    ),
                                    shape = RoundedCornerShape(20.dp),
                                    enabled = !isExtracting,
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Phân tích lại bằng AI", style = MaterialTheme.typography.labelLarge)
                                    }
                                }

                                val isVocabSpeaking = speakingMsgId == "vocab_${article.id}" && isTtsPlaying
                                Button(
                                    onClick = {
                                        if (isVocabSpeaking) {
                                            ttsManager.stop()
                                        } else {
                                            ttsManager.speak("vocab_${article.id}", article.importantVocab ?: "")
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isVocabSpeaking) Color(0xFFFCE8E6) else Color(0xFFE2F5E9),
                                        contentColor = if (isVocabSpeaking) Color(0xFFC5221F) else Color(0xFF0F7C3B)
                                    ),
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    modifier = Modifier.testTag("vocab_tts_button")
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (isVocabSpeaking) Icons.Default.Close else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isVocabSpeaking) "Dừng" else "Phát âm từ vựng",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFD)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFFE1E3E1))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Phân tích và học từ vựng với AI",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF1B1B1F),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Hệ thống AI sẽ quét nội dung bài viết tiếng Hàn này, tự động trích xuất các từ vựng nổi bật nhất kèm ý nghĩa tiếng Việt rõ ràng, ngắn gọn.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF44474E),
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.extractVocabWithAI(article) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF03CF5D),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(24.dp),
                                enabled = !isExtracting,
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isExtracting) "Đang phân tích..." else "BẮT ĐẦU TRÍCH XUẤT AI",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = Color(0xFFE1E3E1))
                Spacer(modifier = Modifier.height(24.dp))

                // Chat title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFE2FBE9)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Face,
                            contentDescription = null,
                            tint = Color(0xFF02873D),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Giải đáp thắc mắc với AI",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF1B1B1F)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Intro text
                Text(
                    text = "Nhập câu hỏi và gửi giáo viên AI để được giải đáp ngữ pháp, cấu trúc câu hoặc từ vựng trong bài học Hàn ngữ này.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF74777F)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Chat history bubble list
                if (chatHistory.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF1F3F9), RoundedCornerShape(20.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        chatHistory.forEach { chatMessage ->
                            val isUser = chatMessage.isUser
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(0.9f),
                                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                                ) {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isUser) Color(0xFFD2F3FC) else Color.White
                                        ),
                                        shape = RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = if (isUser) 16.dp else 4.dp,
                                            bottomEnd = if (isUser) 4.dp else 16.dp
                                        ),
                                        border = if (!isUser) BorderStroke(1.dp, Color(0xFFE1E3E1)) else null,
                                        modifier = Modifier.testTag(if (isUser) "user_chat_bubble" else "ai_chat_bubble")
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                        ) {
                                            if (!isUser) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(bottom = 6.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "GIÁO VIÊN AI 🎓",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF02873D)
                                                    )
                                                    
                                                    val isCurrentSpeaking = speakingMsgId == chatMessage.id && isTtsPlaying
                                                    IconButton(
                                                        onClick = {
                                                            if (isCurrentSpeaking) {
                                                                ttsManager.stop()
                                                            } else {
                                                                ttsManager.speak(chatMessage.id, chatMessage.text)
                                                            }
                                                        },
                                                        modifier = Modifier.size(24.dp).testTag("play_tts_button_${chatMessage.id}")
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isCurrentSpeaking) Icons.Default.Close else Icons.Default.PlayArrow,
                                                            contentDescription = if (isCurrentSpeaking) "Dừng phát âm" else "Phát âm AI",
                                                            tint = if (isCurrentSpeaking) Color(0xFFBA1A1A) else Color(0xFF02873D),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = chatMessage.text,
                                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                                color = Color(0xFF1B1B1F)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (isAnswering) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color(0xFF03CF5D),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "AI đang suy nghĩ câu trả lời...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF02873D)
                                )
                            }
                        }
                    }
                } else if (isAnswering) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color(0xFF03CF5D),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "AI đang phân tích câu hỏi...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF02873D)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Input box & Send button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFFEDF1F9))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = questionInput,
                        onValueChange = { questionInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("ai_question_input_field"),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF1B1B1F)),
                        singleLine = false,
                        maxLines = 4,
                        decorationBox = { innerTextField ->
                            if (questionInput.isEmpty()) {
                                Text(
                                    text = "Hỏi AI (ngữ pháp, dịch nghĩa, từ vựng)...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF74777F)
                                )
                            }
                            innerTextField()
                        }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            val msg = questionInput.trim()
                            if (msg.isNotEmpty()) {
                                viewModel.askAIAboutArticle(msg)
                                questionInput = ""
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (questionInput.trim().isNotEmpty() && !isAnswering) Color(0xFF03CF5D) else Color(0xFFE1E3E1))
                            .testTag("ai_send_question_button"),
                        enabled = questionInput.trim().isNotEmpty() && !isAnswering
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Gửi thắc mắc",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BÀI HỌC THÔNG MINH",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF74777F)
                    )
                    Text(
                        text = "${article.content.split("\\s+".toRegex()).size} TỪ".uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF74777F)
                    )
                }
            }
        }
    }
}
}
}

@Composable
fun InteractiveBodyText(
    text: String,
    selectedWord: String?,
    fontSize: Float = 19f,
    onWordClicked: (String) -> Unit
) {
    val annotatedString = buildInteractiveKoreanText(text, selectedWord)
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = annotatedString,
        style = MaterialTheme.typography.bodyLarge.copy(
            lineHeight = (fontSize * 1.5f).sp,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Medium
        ),
        onTextLayout = { layoutResult = it },
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(text, selectedWord) {
                detectTapGestures { offset ->
                    layoutResult?.let { layout ->
                        val pos = layout.getOffsetForPosition(offset)
                        annotatedString.getStringAnnotations(tag = "WORD", start = pos, end = pos)
                            .firstOrNull()?.let { annotation ->
                                onWordClicked(annotation.item)
                            }
                    }
                }
            }
            .testTag("interactive_body_text")
    )
}

@Composable
fun DictionaryView(
    selectedWord: String?,
    url: String?,
    dictWeight: Float,
    viewModel: ArticleViewModel,
    onWeightChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeType by viewModel.dictionaryType.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .padding(start = 8.dp, end = 16.dp, bottom = 16.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF1B1B1F))
    ) {
        // Dark premium control bar header matching "Naver Quick Dict" in HTML
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LED Glowing Indicator + Title
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF03CF5D))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (selectedWord != null) selectedWord else "NAVER QUICK DICT",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Window Split size options
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        0f to "Ẩn",
                        0.32f to "30%",
                        0.48f to "50%",
                        0.72f to "70%"
                    ).forEach { (weight, label) ->
                        val isSelected = (weight == 0f && dictWeight == 0f) || 
                                         (weight > 0f && Math.abs(dictWeight - weight) < 0.08f)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isSelected) Color(0xFF03CF5D) 
                                    else Color(0xFF2E2F33)
                                )
                                .clickable { onWeightChange(weight) }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isSelected) Color.White else Color(0xFFC4C7C5)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dictionary Language Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DictionaryType.values().forEach { dType ->
                    val isTabSelected = activeType == dType
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isTabSelected) Color(0xFF03CF5D) 
                                else Color(0xFF2E2F33)
                            )
                            .clickable { viewModel.setDictionaryType(dType) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dType.label.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            color = if (isTabSelected) Color.White else Color(0xFFC4C7C5)
                        )
                    }
                }
            }
        }

        // Live WebView Dictionary Content or placeholder image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.White)
        ) {
            if (url == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1B1B1F))
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Chọn một từ tiếng Hàn trong bài viết hoặc bài học để tra cứu thông minh trên Từ điển Naver ngay lập tức.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                DictionaryWebView(
                    url = url,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("naver_dict_webview")
                )
            }
        }
    }
}

@Composable
fun DictionaryWebView(
    url: String,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                
                webViewClient = object : WebViewClient() {
                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        url?.let { view?.loadUrl(it) }
                        return true
                    }
                }
                loadUrl(url)
            }
        },
        update = { webView ->
            webView.loadUrl(url)
        },
        modifier = modifier
    )
}

class WebAppInterface(
    private val onWordSelected: (String) -> Unit,
    private val onContentExtracted: (String, String) -> Unit
) {
    @android.webkit.JavascriptInterface
    fun onSelectionChanged(text: String) {
        onWordSelected(text)
    }

    @android.webkit.JavascriptInterface
    fun onTextExtracted(title: String, bodyText: String) {
        onContentExtracted(title, bodyText)
    }
}

@android.annotation.SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun BrowserWebView(
    url: String,
    onUrlChanged: (String) -> Unit,
    onWordSelected: (String) -> Unit,
    extractionTriggerCount: Int,
    onContentExtracted: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
                
                addJavascriptInterface(
                    WebAppInterface(
                        onWordSelected = { selected ->
                            mainHandler.post { onWordSelected(selected) }
                        },
                        onContentExtracted = { title, content ->
                            mainHandler.post { onContentExtracted(title, content) }
                        }
                    ),
                    "Android"
                )

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        url?.let { onUrlChanged(it) }
                    }
                    
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        url?.let { onUrlChanged(it) }
                        
                        view?.evaluateJavascript(
                            """
                            (function() {
                                if (window.hasAddedSelectionListener) return;
                                window.hasAddedSelectionListener = true;
                                
                                function getSelectedText() {
                                    var text = "";
                                    if (window.getSelection) {
                                        text = window.getSelection().toString();
                                    } else if (document.selection && document.selection.type != "Control") {
                                        text = document.selection.createRange().text;
                                    }
                                    return text.trim();
                                }
                                
                                document.addEventListener('selectionchange', function() {
                                    var selected = getSelectedText();
                                    if (selected.length > 0 && selected.length < 200) {
                                        Android.onSelectionChanged(selected);
                                    }
                                });
                            })();
                            """.trimIndent(),
                            null
                        )
                    }

                    @Deprecated("Deprecated in Java", ReplaceWith("true"))
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        url?.let { view?.loadUrl(it) }
                        return true
                    }
                }
                loadUrl(url)
            }
        },
        update = { webView ->
            if (webView.url != url && url.isNotEmpty()) {
                webView.loadUrl(url)
            }
            
            val currentTag = webView.tag as? Int ?: 0
            if (extractionTriggerCount > currentTag) {
                webView.tag = extractionTriggerCount
                webView.evaluateJavascript(
                    """
                    (function() {
                        var title = document.title || "Bài viết thu thập";
                        var articleContent = "";
                        
                        var selectors = [
                            "article", "main", ".article_body", ".article", ".news_end", 
                            "#articleBody", "#article_body", "#newsct_article", "#articleBodyContents",
                            ".article-body", ".post-content", ".content", "div[itemprop='articleBody']",
                            ".news_body", ".news_content", ".view_content", ".view_content_text"
                        ];
                        
                        for (var i = 0; i < selectors.length; i++) {
                            var el = document.querySelector(selectors[i]);
                            if (el) {
                                var clone = el.cloneNode(true);
                                var garbage = clone.querySelectorAll("script, style, iframe, footer, nav, header, .comment, .reply, noscript");
                                for (var j = 0; j < garbage.length; j++) {
                                    garbage[j].parentNode.removeChild(garbage[j]);
                                }
                                var txt = clone.innerText || clone.textContent;
                                if (txt && txt.trim().length > 100) {
                                    articleContent = txt;
                                    break;
                                }
                            }
                        }
                        
                        if (!articleContent) {
                            var clone = document.body.cloneNode(true);
                            var garbage = clone.querySelectorAll("script, style, iframe, footer, nav, header, .comment, .reply, noscript");
                            for (var j = 0; j < garbage.length; j++) {
                                garbage[j].parentNode.removeChild(garbage[j]);
                            }
                            articleContent = clone.innerText || clone.textContent;
                        }
                        
                        Android.onTextExtracted(title, articleContent || "");
                    })()
                    """.trimIndent(),
                    null
                )
            }
        },
        modifier = modifier
    )
}

// Word splitting parser matching HTML split logic
fun splitKoreanText(text: String): List<String> {
    val result = mutableListOf<String>()
    val currentWord = StringBuilder()
    if (text.isEmpty()) return emptyList()

    var isCurrentSpace = text[0].isWhitespace()
    currentWord.append(text[0])

    for (i in 1 until text.length) {
        val char = text[i]
        val isSpace = char.isWhitespace()
        if (isSpace == isCurrentSpace) {
            currentWord.append(char)
        } else {
            result.add(currentWord.toString())
            currentWord.setLength(0)
            currentWord.append(char)
            isCurrentSpace = isSpace
        }
    }
    result.add(currentWord.toString())
    return result
}

// Clean words from Vietnamese/Korean punctuation to lookup on Naver Dict accurately
fun sanitizeKoreanWord(word: String): String {
    val regex = Regex("[.,/#!$%^&*\\;;:{}=\\-_`~()?\"']")
    return word.replace(regex, "")
}

@Composable
fun buildInteractiveKoreanText(
    text: String,
    selectedWord: String?
): AnnotatedString {
    return buildAnnotatedString {
        val parts = splitKoreanText(text)
        val cleanSelected = selectedWord?.let { sanitizeKoreanWord(it) }

        for (part in parts) {
            if (part.trim().isEmpty()) {
                append(part)
            } else {
                val cleanWord = sanitizeKoreanWord(part)
                val isSelected = cleanSelected != null && cleanSelected == cleanWord
                
                val start = length
                append(part)
                val end = length
                
                addStringAnnotation(
                    tag = "WORD",
                    annotation = cleanWord,
                    start = start,
                    end = end
                )
                
                if (isSelected) {
                    addStyle(
                        style = SpanStyle(
                            color = Color(0xFF03CF5D),
                            background = Color(0xFFE2FBE9),
                            fontWeight = FontWeight.Bold,
                            textDecoration = TextDecoration.Underline
                        ),
                        start = start,
                        end = end
                    )
                }
            }
        }
    }
}
