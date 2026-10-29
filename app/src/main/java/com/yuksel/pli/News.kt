package com.yuksel.pli

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class Article(val title: String, val source: String, val link: String, val image: String?, val summary: String, val published: Instant?)

/** A tab: Google News for the topic, plus English-language newsrooms whose feeds carry photos. */
class NewsTopic(val label: String, val feeds: List<Pair<String, String>>)

private const val GOOGLE_NEWS = "Google News"

private fun google(section: String?) = GOOGLE_NEWS to (if (section == null) "https://news.google.com/rss?hl=en-US&gl=US&ceid=US:en"
    else "https://news.google.com/rss/headlines/section/topic/$section?hl=en-US&gl=US&ceid=US:en")

// Feeds that don't load are simply skipped, so a wrong address here is harmless.
val NEWS_TOPICS = listOf(
    NewsTopic("Top stories", listOf(google(null), "BBC News" to "https://feeds.bbci.co.uk/news/rss.xml", "NPR" to "https://feeds.npr.org/1001/rss.xml")),
    NewsTopic("U.S.", listOf(google("NATION"), "BBC News" to "https://feeds.bbci.co.uk/news/politics/rss.xml", "NPR" to "https://feeds.npr.org/1003/rss.xml")),
    NewsTopic("World", listOf(google("WORLD"), "BBC News" to "https://feeds.bbci.co.uk/news/world/rss.xml", "NPR" to "https://feeds.npr.org/1004/rss.xml")),
    NewsTopic("Business", listOf(google("BUSINESS"), "BBC News" to "https://feeds.bbci.co.uk/news/business/rss.xml", "NPR" to "https://feeds.npr.org/1006/rss.xml")),
    NewsTopic("Tech", listOf(google("TECHNOLOGY"), "BBC News" to "https://feeds.bbci.co.uk/news/technology/rss.xml", "NPR" to "https://feeds.npr.org/1019/rss.xml")),
    NewsTopic("Sports", listOf(google("SPORTS"), "BBC Sport" to "https://feeds.bbci.co.uk/sport/rss.xml")),
    NewsTopic("Entertainment", listOf(google("ENTERTAINMENT"), "BBC News" to "https://feeds.bbci.co.uk/news/entertainment_and_arts/rss.xml", "NPR" to "https://feeds.npr.org/1008/rss.xml")),
    NewsTopic("Science", listOf(google("SCIENCE"), "BBC News" to "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml", "NPR" to "https://feeds.npr.org/1007/rss.xml")),
    NewsTopic("Health", listOf(google("HEALTH"), "BBC News" to "https://feeds.bbci.co.uk/news/health/rss.xml", "NPR" to "https://feeds.npr.org/1128/rss.xml")),
)

private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
    connectTimeout = 8_000; readTimeout = 10_000; instanceFollowRedirects = true
    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16) Pli")
}

private val dateFormats = listOf(DateTimeFormatter.RFC_1123_DATE_TIME)
private fun parseDate(text: String?): Instant? {
    if (text.isNullOrBlank()) return null
    for (f in dateFormats) runCatching { return ZonedDateTime.parse(text.trim(), f).toInstant() }
    return runCatching { Instant.parse(text.trim()) }.getOrNull()
}

private fun stripHtml(text: String) = android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_COMPACT).toString()
    .replace('￼', ' ').replace(Regex("\\s+"), " ").trim()

/** Reads an RSS feed. Unknown or broken feeds just return nothing. */
private fun readFeed(sourceName: String, url: String): List<Article> = runCatching {
    val connection = open(url)
    try {
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        parser.setInput(connection.inputStream, null)
        val out = ArrayList<Article>()
        var inItem = false
        var title = ""; var link = ""; var image: String? = null; var summary = ""; var date: String? = null; var source = sourceName
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && out.size < 40) {
            when (event) {
                XmlPullParser.START_TAG -> when {
                    parser.name == "item" -> { inItem = true; title = ""; link = ""; image = null; summary = ""; date = null; source = sourceName }
                    !inItem -> {}
                    parser.name == "title" -> title = parser.nextText()
                    parser.name == "link" -> link = parser.nextText()
                    parser.name == "pubDate" -> date = parser.nextText()
                    parser.name == "description" -> summary = parser.nextText()
                    parser.name == "source" -> source = parser.nextText().ifBlank { sourceName }
                    parser.name == "content" || parser.name == "thumbnail" -> if (image == null) image = parser.getAttributeValue(null, "url")
                    parser.name == "enclosure" -> if (image == null && parser.getAttributeValue(null, "type")?.startsWith("image") != false)
                        image = parser.getAttributeValue(null, "url")
                }
                XmlPullParser.END_TAG -> if (parser.name == "item" && inItem) {
                    inItem = false
                    var clean = stripHtml(title)
                    // Google News ends titles with " - Source".
                    if (source != sourceName && clean.endsWith(" - $source")) clean = clean.removeSuffix(" - $source")
                    val text = if (sourceName == GOOGLE_NEWS) "" else stripHtml(summary)
                    if (clean.isNotBlank() && link.isNotBlank()) out += Article(clean, source, link.trim(), image, text.take(220), parseDate(date))
                }
            }
            event = parser.next()
        }
        out
    } finally { connection.disconnect() }
}.getOrDefault(emptyList())

object NewsRepository {
    private val cache = HashMap<String, Pair<Long, List<Article>>>()

    suspend fun load(topic: NewsTopic, force: Boolean): List<Article> = withContext(Dispatchers.IO) {
        val cached = synchronized(cache) { cache[topic.label] }
        if (!force && cached != null && System.currentTimeMillis() - cached.first < 15 * 60_000L) return@withContext cached.second
        val all = coroutineScope { topic.feeds.map { (name, url) -> async { readFeed(name, url) } }.awaitAll() }
        // Interleave: newest first, pictures favoured, no duplicate titles.
        val seen = HashSet<String>()
        val merged = all.flatten().sortedByDescending { it.published ?: Instant.EPOCH }
            .filter { seen.add(it.title.lowercase().take(60)) }.take(80)
        if (merged.isNotEmpty()) synchronized(cache) { cache[topic.label] = System.currentTimeMillis() to merged }
        merged.ifEmpty { cached?.second ?: emptyList() }
    }
}

private object ImageCache {
    val memory = LruCache<String, ImageBitmap>(60)

    suspend fun load(url: String, maxWidth: Int): ImageBitmap? = memory.get(url) ?: withContext(Dispatchers.IO) {
        runCatching {
            val bytes = open(url).let { c -> try { c.inputStream.readBytes() } finally { c.disconnect() } }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 })
                ?.asImageBitmap()?.also { memory.put(url, it) }
        }.getOrNull()
    }
}

@Composable
private fun NetImage(url: String, modifier: Modifier) {
    val image by produceState<ImageBitmap?>(ImageCache.memory.get(url), url) { if (value == null) value = ImageCache.load(url, 900) }
    Box(modifier.background(Brush.linearGradient(listOf(Holo.Cyan.copy(alpha = .12f), Holo.Violet.copy(alpha = .12f))))) {
        image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

fun ago(instant: Instant?): String {
    instant ?: return ""
    val minutes = Duration.between(instant, Instant.now()).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        else -> "${minutes / (24 * 60)}d ago"
    }
}

/** The far-left page: news in holographic cards, read inside Pli. */
@Composable
fun NewsPage(modifier: Modifier, onRead: (Article) -> Unit) {
    var topicIndex by rememberSaveable { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    val topic = NEWS_TOPICS[topicIndex]
    val state by produceState<List<Article>?>(null, topicIndex, refresh) {
        value = null
        value = NewsRepository.load(topic, refresh > 0)
    }
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IridescentText("News", 32.sp, Modifier.weight(1f), letterSpacing = (-.5).sp)
            IconButton(onClick = { refresh++ }, Modifier.size(40.dp).glass(CircleShape, .06f)) {
                Icon(Icons.Rounded.Refresh, "Refresh", tint = Holo.Ink, modifier = Modifier.size(20.dp))
            }
        }
        Row(Modifier.padding(vertical = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NEWS_TOPICS.forEachIndexed { i, t -> PillButton(t.label, highlighted = i == topicIndex) { topicIndex = i } }
        }
        val articles = state
        when {
            articles == null -> Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Holo.Violet, strokeWidth = 2.dp)
            }
            articles.isEmpty() -> Text("No news right now. Check your connection, then refresh.", color = Holo.Muted, fontSize = 14.sp,
                modifier = Modifier.padding(top = 30.dp))
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(articles, key = { it.link }) { a -> ArticleCard(a, onRead) }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun ArticleCard(article: Article, onRead: (Article) -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    if (article.image != null) {
        Column(Modifier.fillMaxWidth().glass(shape, .05f).clickable { onRead(article) }) {
            NetImage(article.image, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)))
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Meta(article)
                Text(article.title, color = Holo.Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (article.summary.isNotBlank()) Text(article.summary, color = Holo.Soft.copy(alpha = .8f), fontSize = 13.sp, lineHeight = 18.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    } else {
        Column(Modifier.fillMaxWidth().glass(shape, .05f).clickable { onRead(article) }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Meta(article)
            Text(article.title, color = Holo.Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Meta(article: Article) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(Brush.linearGradient(Holo.Iridescent), CircleShape))
        Text(article.source, color = Holo.Cyan, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp).weight(1f, fill = false))
        val time = ago(article.published)
        if (time.isNotEmpty()) Text("  ·  $time", color = Holo.Muted, fontSize = 12.sp, maxLines = 1)
    }
}

/** Reads an article inside Pli (web page in a holographic frame). */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ArticleReader(article: Article, modifier: Modifier, onClose: () -> Unit) {
    val context = LocalContext.current
    var web by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    BackHandler { if (web?.canGoBack() == true) web?.goBack() else onClose() }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(article.source, color = Holo.Cyan, fontSize = 12.sp, maxLines = 1)
                Text(article.title, color = Holo.Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = {
                runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(web?.url ?: article.link))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }, Modifier.size(40.dp)) { Icon(Icons.Rounded.OpenInBrowser, "Open in browser", tint = Holo.Ink) }
            IconButton(onClick = onClose, Modifier.padding(start = 4.dp).size(40.dp).glass(CircleShape, .08f)) { Icon(Icons.Rounded.Close, "Close", tint = Holo.Ink) }
        }
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(Color.White)) {
            AndroidView(factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val scheme = request.url.scheme ?: return true
                            if (scheme == "http" || scheme == "https") return false
                            runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, request.url)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            return true
                        }
                        override fun onPageFinished(view: WebView, url: String) { loading = false }
                    }
                    loadUrl(article.link)
                    web = this
                }
            }, modifier = Modifier.fillMaxSize(), onRelease = { it.destroy() })
            if (loading) Box(Modifier.fillMaxWidth().height(3.dp).background(Brush.linearGradient(Holo.Iridescent)))
        }
    }
}
