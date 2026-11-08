package com.fiesta.datasaving

import android.app.DownloadManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import java.net.URL
import java.text.DecimalFormat

private val Ink = Color(0xFF101923)
private val Green = Color(0xFF20C78A)
private val Muted = Color(0xFF81909D)
private val SurfaceDark = Color(0xFF17232E)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DataSavingApp() }
    }
}

private enum class Screen { HOME, BROWSER, DOWNLOADS, USAGE }

@Composable
private fun DataSavingApp() {
    val context = LocalContext.current
    var screen by remember { mutableStateOf(Screen.HOME) }
    var online by remember { mutableStateOf(isNetworkAvailable(context)) }
    var wifi by remember { mutableStateOf(isWifi(context)) }
    var refresh by remember { mutableIntStateOf(0) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var address by remember { mutableStateOf("https://example.com") }
    var pageTitle by remember { mutableStateOf("New page") }

    DisposableEffect(context) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                online = isNetworkAvailable(context); wifi = isWifi(context)
            }
            override fun onLost(network: Network) {
                online = isNetworkAvailable(context); wifi = isWifi(context)
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            }
        }
        runCatching { manager.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }

    val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
    val pagesDir = File(context.filesDir, "offline_pages").apply { mkdirs() }
    val files = remember(refresh) {
        (downloadsDir.listFiles()?.toList().orEmpty() + pagesDir.listFiles()?.toList().orEmpty())
            .filter { it.isFile }.sortedByDescending { it.lastModified() }
    }
    val totalBytes = files.sumOf { it.length() }
    val sessionRx = remember { android.net.TrafficStats.getUidRxBytes(android.os.Process.myUid()).coerceAtLeast(0L) }
    val sessionTx = remember { android.net.TrafficStats.getUidTxBytes(android.os.Process.myUid()).coerceAtLeast(0L) }
    val currentRx = android.net.TrafficStats.getUidRxBytes(android.os.Process.myUid()).coerceAtLeast(sessionRx) - sessionRx
    val currentTx = android.net.TrafficStats.getUidTxBytes(android.os.Process.myUid()).coerceAtLeast(sessionTx) - sessionTx

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Green, background = Ink, surface = SurfaceDark,
        onBackground = Color.White, onSurface = Color.White
    )) {
        Scaffold(
            containerColor = Ink,
            bottomBar = {
                NavigationBar(containerColor = SurfaceDark, contentColor = Color.White) {
                    NavItem("Home", Icons.Default.Home, screen == Screen.HOME) { screen = Screen.HOME }
                    NavItem("Browser", Icons.Default.Language, screen == Screen.BROWSER) { screen = Screen.BROWSER }
                    NavItem("Files", Icons.Default.Folder, screen == Screen.DOWNLOADS) { refresh++; screen = Screen.DOWNLOADS }
                    NavItem("Usage", Icons.Default.DataUsage, screen == Screen.USAGE) { screen = Screen.USAGE }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 18.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).background(Green, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CloudDownload, null, tint = Ink)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("DataSaving", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Your content. Available offline.", color = Muted, fontSize = 12.sp)
                    }
                    StatusPill(online, wifi)
                }
                when (screen) {
                    Screen.HOME -> HomeScreen(online, wifi, totalBytes, files.size,
                        onBrowser = { screen = Screen.BROWSER }, onDownload = { screen = Screen.DOWNLOADS })
                    Screen.BROWSER -> BrowserScreen(
                        address, { address = it }, webView, { webView = it },
                        onGo = { target -> webView?.loadUrl(normalizeUrl(target)) },
                        onSave = {
                            val view = webView
                            if (view == null) Toast.makeText(context, "Open a page first", Toast.LENGTH_SHORT).show()
                            else {
                                val safeName = (pageTitle.ifBlank { "saved_page" }).replace(Regex("[^A-Za-z0-9_-]"), "_").take(40)
                                val out = File(pagesDir, "${safeName}_${System.currentTimeMillis()}.mht")
                                view.saveWebArchive(out.absolutePath, false) { saved ->
                                    if (saved != null) {
                                        pageTitle = "Saved"
                                        refresh++
                                        Toast.makeText(context, "Page saved for offline access", Toast.LENGTH_LONG).show()
                                    } else Toast.makeText(context, "Could not save this page", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        onTitle = { pageTitle = it },
                        savedPages = pagesDir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }.orEmpty(),
                        onOpenSaved = { file -> webView?.loadUrl(Uri.fromFile(file).toString()) }
                    )
                    Screen.DOWNLOADS -> DownloadsScreen(files, onDownload = { url -> enqueueWifiDownload(context, url) { refresh++ } },
                        onRefresh = { refresh++ }, onOpen = { file ->
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.fromFile(file), "*/*")
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching { context.startActivity(intent) }.onFailure {
                                Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
                            }
                        })
                    Screen.USAGE -> UsageScreen(totalBytes, files.size, currentRx, currentTx)
                }
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, click: () -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = click)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) Green else Muted)
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            fontSize = 10.sp,
            color = if (selected) Green else Muted,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun StatusPill(online: Boolean, wifi: Boolean) {
    Row(
        Modifier.background(if (online) Color(0xFF153B32) else Color(0xFF3A2930), RoundedCornerShape(30.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).background(if (online) Green else Color(0xFFFF7D83), CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(if (!online) "Offline" else if (wifi) "Wi-Fi" else "Online", fontSize = 11.sp, color = Color.White)
    }
}

@Composable
private fun HomeScreen(online: Boolean, wifi: Boolean, bytes: Long, count: Int, onBrowser: () -> Unit, onDownload: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B3B35)), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("OFFLINE LIBRARY", color = Color(0xFFB8D8CD), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(formatBytes(bytes), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("$count saved items on this device", color = Color(0xFFC3D5D0), fontSize = 13.sp)
                Spacer(Modifier.height(18.dp))
                LinearProgressIndicator(progress = { if (bytes == 0L) 0f else 0.35f }, modifier = Modifier.fillMaxWidth(), color = Green, trackColor = Color(0xFF42635A))
                Spacer(Modifier.height(8.dp))
                Text("Stored locally • Uses your phone storage", color = Color(0xFFB8D8CD), fontSize = 11.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Connection", if (!online) "Offline" else if (wifi) "Wi-Fi" else "Internet", Icons.Default.Wifi, Modifier.weight(1f))
            StatCard("Saved items", count.toString(), Icons.Default.Inventory2, Modifier.weight(1f))
        }
        Text("Quick actions", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        ActionCard("Offline browser", "Save web pages while connected", Icons.Default.Language, onBrowser)
        ActionCard("Download manager", "Download files using Wi-Fi only", Icons.Default.Download, onDownload)
        Text("Tip: save pages and files before you go offline.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun StatCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(15.dp)) {
            Icon(icon, null, tint = Green)
            Spacer(Modifier.height(12.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(title, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, click: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = click), colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Green, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Muted, fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Muted)
        }
    }
}

@Composable
private fun BrowserScreen(
    address: String, onAddress: (String) -> Unit, currentWebView: WebView?, onWebView: (WebView) -> Unit,
    onGo: (String) -> Unit, onSave: () -> Unit, onTitle: (String) -> Unit,
    savedPages: List<File>, onOpenSaved: (File) -> Unit
) {
    var showSaved by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(address, onAddress, modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Website URL") }, leadingIcon = { Icon(Icons.Default.Language, null) },
            trailingIcon = { IconButton(onClick = { onGo(address) }) { Icon(Icons.Default.ArrowForward, null) } })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSave, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Save, null); Spacer(Modifier.width(6.dp)); Text("Save page") }
            OutlinedButton(onClick = { showSaved = !showSaved }) { Icon(Icons.Default.Bookmarks, null); Text(" Saved") }
        }
        if (showSaved) {
            Text("Saved pages", fontWeight = FontWeight.Bold)
            LazyColumn(Modifier.heightIn(max = 150.dp)) {
                items(savedPages) { file ->
                    Text(file.name, Modifier.fillMaxWidth().clickable { onOpenSaved(file) }.padding(9.dp), color = Green, fontSize = 12.sp)
                }
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxSize().background(Color.White, RoundedCornerShape(14.dp)),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.loadsImagesAutomatically = true
                    settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            onAddress(url); onTitle(view.title ?: "Saved page")
                        }
                    }
                    webChromeClient = WebChromeClient()
                    onWebView(this)
                    loadUrl("https://example.com")
                }
            },
            update = { if (currentWebView == null) onWebView(it) }
        )
    }
}

@Composable
private fun DownloadsScreen(files: List<File>, onDownload: (String) -> Unit, onRefresh: () -> Unit, onOpen: (File) -> Unit) {
    var url by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Wi-Fi downloads", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Downloads are restricted to Wi-Fi by Android's Download Manager.", color = Muted, fontSize = 12.sp)
        OutlinedTextField(url, { url = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Direct file URL") }, singleLine = true)
        Button(onClick = { onDownload(url); url = "" }, modifier = Modifier.fillMaxWidth(), enabled = url.startsWith("https://") || url.startsWith("http://")) {
            Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text("Download on Wi-Fi")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Saved files", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, null) }
        }
        if (files.isEmpty()) Text("No saved files yet.", color = Muted)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(files) { file ->
                Row(Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(14.dp)).clickable { onOpen(file) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.InsertDriveFile, null, tint = Green)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(file.name, maxLines = 1, fontSize = 13.sp)
                        Text(formatBytes(file.length()), color = Muted, fontSize = 11.sp)
                    }
                    Icon(Icons.Default.OpenInNew, null, tint = Muted)
                }
            }
        }
    }
}

@Composable
private fun UsageScreen(saved: Long, count: Int, received: Long, sent: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Storage & usage", fontSize = 21.sp, fontWeight = FontWeight.Bold)
        StatCard("Offline library", formatBytes(saved), Icons.Default.Storage, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("Received this session", formatBytes(received), Icons.Default.South, Modifier.weight(1f))
            StatCard("Sent this session", formatBytes(sent), Icons.Default.North, Modifier.weight(1f))
        }
        StatCard("Saved items", count.toString(), Icons.Default.Folder, Modifier.fillMaxWidth())
        Text("Network usage figures are for DataSaving only, since the app started. Android does not let an ordinary app read unrestricted traffic totals for every other app.", color = Muted, fontSize = 12.sp)
        Text("Saved content uses your device storage. It does not become mobile-data credit or provide live internet.", color = Muted, fontSize = 12.sp)
    }
}

private fun enqueueWifiDownload(context: Context, rawUrl: String, onQueued: () -> Unit) {
    try {
        val uri = Uri.parse(rawUrl.trim())
        require(uri.scheme == "https" || uri.scheme == "http")
        val name = uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: "download_${System.currentTimeMillis()}"
        val request = DownloadManager.Request(uri)
            .setTitle(name)
            .setDescription("DataSaving • Wi-Fi only")
            .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, name)
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        Toast.makeText(context, "Queued. Waiting for Wi-Fi if needed.", Toast.LENGTH_LONG).show()
        onQueued()
    } catch (e: Exception) {
        Toast.makeText(context, "Enter a valid direct download URL.", Toast.LENGTH_LONG).show()
    }
}

private fun normalizeUrl(input: String): String {
    val value = input.trim()
    return if (value.startsWith("http://") || value.startsWith("https://")) value else "https://$value"
}

private fun isNetworkAvailable(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = manager.activeNetwork ?: return false
    val caps = manager.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

private fun isWifi(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = -1
    do { value /= 1024.0; index++ } while (value >= 1024 && index < units.lastIndex)
    return "${DecimalFormat("#,##0.##").format(value)} ${units[index]}"
}
