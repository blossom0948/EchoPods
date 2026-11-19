package me.kavishdevar.librepods.data.updates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

data class ReleaseHistoryEntry(
    val tag: String,
    val title: String,
    val publishedAt: String,
    val isPrerelease: Boolean,
    val notes: String? = null,
)

/** Reads the public GitHub release archive; APK signing and installation stay in UpdateManager. */
object GitHubReleaseHistoryRepository {
    private const val API = "https://api.github.com/repos/blossom0948/EchoPods/releases"
    private const val LIST_CACHE_MILLIS = 10 * 60 * 1000L

    private val lock = Any()
    private val detailCache = ConcurrentHashMap<String, ReleaseHistoryEntry>()

    @Volatile
    private var listCache: List<ReleaseHistoryEntry>? = null

    @Volatile
    private var listCacheTime = 0L

    suspend fun loadAll(forceRefresh: Boolean = false): List<ReleaseHistoryEntry> {
        val now = System.currentTimeMillis()
        listCache?.takeIf { !forceRefresh && now - listCacheTime < LIST_CACHE_MILLIS }?.let { return it }

        return withContext(Dispatchers.IO) {
            synchronized(lock) {
                val cached = listCache
                if (!forceRefresh && cached != null && System.currentTimeMillis() - listCacheTime < LIST_CACHE_MILLIS) {
                    return@synchronized cached
                }

                val releases = JSONArray(request("$API?per_page=100"))
                    .let { array ->
                        buildList {
                            for (index in 0 until array.length()) {
                                val item = array.optJSONObject(index) ?: continue
                                if (item.optBoolean("draft", false)) continue
                                parseEntry(item, includeNotes = false)?.let(::add)
                            }
                        }
                    }
                    .sortedWith(compareByDescending<ReleaseHistoryEntry> { it.publishedAt }.thenByDescending { it.tag })

                listCache = releases
                listCacheTime = System.currentTimeMillis()
                releases
            }
        }
    }

    suspend fun load(tag: String): ReleaseHistoryEntry {
        detailCache[tag]?.let { return it }
        require(tag.matches(Regex("v?[0-9]+(?:\\.[0-9A-Za-z-]+)*"))) { "Invalid release tag" }

        return withContext(Dispatchers.IO) {
            detailCache[tag]?.let { return@withContext it }
            val encodedTag = URLEncoder.encode(tag, StandardCharsets.UTF_8.name())
            val entry = parseEntry(JSONObject(request("$API/tags/$encodedTag")), includeNotes = true)
                ?: throw IOException("GitHub returned an invalid release entry")
            detailCache[tag] = entry
            entry
        }
    }

    private fun parseEntry(json: JSONObject, includeNotes: Boolean): ReleaseHistoryEntry? {
        val tag = json.optString("tag_name").takeIf { it.isNotBlank() } ?: return null
        return ReleaseHistoryEntry(
            tag = tag,
            title = json.optString("name").takeIf { it.isNotBlank() && it != "null" } ?: tag,
            publishedAt = json.optString("published_at").takeIf { it.isNotBlank() && it != "null" }.orEmpty(),
            isPrerelease = json.optBoolean("prerelease", false),
            notes = if (includeNotes) json.optString("body").takeUnless { it == "null" } else null,
        )
    }

    private fun request(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 12_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "EchoPods-Android")

            val status = connection.responseCode
            if (status !in 200..299) throw IOException("GitHub returned HTTP $status")
            return connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
