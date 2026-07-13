package com.lagradost.cloudstream3.providers

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONArray
import org.json.JSONObject
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

private const val MAX_LIMIT = 99999

class CineHub24 : MainAPI() {
    override var mainUrl = "http://www.cinehub24.com"
    override var name = "CineHub24"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val posterBaseUrl = "https://image.tmdb.org/t/p/w500/"

    private fun JSONObject.toSearchResult(isSeries: Boolean = false): SearchResponse {
        val id = optString("id")
        val title = optString("MovieTitle").trim()
        val posterFile = optString("poster")
        val poster = if (posterFile.isNotBlank() && posterFile != "Array") "$posterBaseUrl$posterFile" else null
        val watchLink = optString("MovieWatchLink")
        val plot = optString("MovieStory")
        val year = optString("MovieYear").toIntOrNull()

        val encodedUrl = listOf(id, title, poster ?: "", watchLink, plot.replace("|", " "))
            .joinToString("|||")

        val tvType = if (isSeries) TvType.TvSeries else TvType.Movie

        return newMovieSearchResponse(title, encodedUrl, tvType) {
            this.posterUrl = poster
            this.year = year
        }
    }

    private val apiUrl = "http://203.76.96.50/api/v1"
    private val tvApiUrl = "http://203.76.96.50/api/v1/tv.php" // guess – change if needed

    private suspend fun fetchMovies(url: String): List<JSONObject> {
        return try {
            val text = app.get(
                url,
                headers = mapOf(
                    "Referer" to "$mainUrl/",
                    "Origin" to mainUrl,
                    "X-Requested-With" to "XMLHttpRequest",
                    "Accept" to "application/json, text/plain, */*"
                )
            ).text
            val arr = JSONArray(text)
            (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val baseCategories = listOf(
            "Latest" to "",
            "Hollywood" to "Hollywood",
            "Bollywood" to "Bollywood",
            "Animation" to "Animation",
            "Franch" to "Franch",
            "Indian Bangla" to "Indian+Bangla",
            "Korean" to "Korean",
            "Tamil" to "Tamil",
            "Hindi Dubbed" to "Hindi+Dubbed"
        )

        val movieLists = mutableListOf<HomePageList>()

        baseCategories.forEach { (displayName, categoryParam) ->
            // Normal version
            val normalUrl = if (categoryParam.isBlank()) {
                "$apiUrl/movies.php?limit=$MAX_LIMIT"
            } else {
                "$apiUrl/movies.php?category=$categoryParam&limit=$MAX_LIMIT"
            }
            val normalMovies = fetchMovies(normalUrl)
            if (normalMovies.isNotEmpty()) {
                movieLists.add(
                    HomePageList("Movies - $displayName", normalMovies.map { it.toSearchResult(false) })
                )
            }

            // 4K version – now using quality=4K (more likely to filter actual 4K)
            // Alternative: try resolution=4K, genre=4K, or type=4K if this doesn't work.
            val fourKUrl = if (categoryParam.isBlank()) {
                "$apiUrl/movies.php?quality=4K&limit=$MAX_LIMIT"   // for "Latest 4K"
            } else {
                "$apiUrl/movies.php?category=$categoryParam&quality=4K&limit=$MAX_LIMIT"
            }
            val fourKMovies = fetchMovies(fourKUrl)
            if (fourKMovies.isNotEmpty()) {
                movieLists.add(
                    HomePageList("4K - $displayName", fourKMovies.map { it.toSearchResult(false) })
                )
            }
        }

        // TV Series (no 4K variants)
        val seriesCategories = listOf(
            "Latest" to "",
            "Hollywood" to "Hollywood",
            "Bollywood" to "Bollywood",
            "Animation" to "Animation",
            "Franch" to "Franch",
            "Indian Bangla" to "Indian+Bangla",
            "Korean" to "Korean",
            "Tamil" to "Tamil",
            "Hindi Dubbed" to "Hindi+Dubbed"
        )

        val seriesLists = seriesCategories.mapNotNull { (catName, categoryParam) ->
            val url = if (categoryParam.isBlank()) {
                "$tvApiUrl?limit=$MAX_LIMIT"
            } else {
                "$tvApiUrl?category=$categoryParam&limit=$MAX_LIMIT"
            }
            val series = fetchMovies(url)
            if (series.isEmpty()) null
            else HomePageList("TV Series - $catName", series.map { it.toSearchResult(true) })
        }

        val allLists = movieLists + seriesLists
        return newHomePageResponse(allLists, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val movieResults = fetchMovies("$apiUrl/movies.php?limit=$MAX_LIMIT")
            .filter { it.optString("MovieTitle").contains(query, ignoreCase = true) }
            .map { it.toSearchResult(false) }

        val seriesResults = fetchMovies("$tvApiUrl?limit=$MAX_LIMIT")
            .filter { it.optString("MovieTitle").contains(query, ignoreCase = true) }
            .map { it.toSearchResult(true) }

        return movieResults + seriesResults
    }

    override suspend fun load(url: String): LoadResponse {
        val parts = url.split("|||")
        val title = parts.getOrElse(1) { "Unknown" }
        val poster = parts.getOrElse(2) { "" }.ifBlank { null }
        val watchLink = parts.getOrElse(3) { "" }
        val plot = parts.getOrElse(4) { "" }

        return newMovieLoadResponse(title, url, TvType.Movie, watchLink) {
            this.posterUrl = poster
            this.plot = plot
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // 1. Direct MP4
        if (data.contains(".mp4", ignoreCase = true)) {
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = "Direct MP4",
                    url = data
                ) {
                    this.referer = mainUrl
                    this.quality = Qualities.Unknown.value
                }
            )
            return true
        }

        // 2. Try to extract from the page
        return try {
            val pageHtml = app.get(data, headers = mapOf("Referer" to mainUrl)).text

            val videoUrl = run {
                // <source src="...">
                Regex("""<source\s+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                    .find(pageHtml)?.groupValues?.get(1)
                    ?: // <video src="...">
                    Regex("""<video[^>]*src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                        .find(pageHtml)?.groupValues?.get(1)
                        ?: // <iframe src="...">
                        Regex("""<iframe[^>]*src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                            .find(pageHtml)?.groupValues?.get(1)
                            ?: // JavaScript file/url
                            Regex("""(?:file|url)\s*[:=]\s*["']([^"']+\.(?:mp4|m3u8))["']""", RegexOption.IGNORE_CASE)
                                .find(pageHtml)?.groupValues?.get(1)
            }

            if (videoUrl != null) {
                val absoluteUrl = when {
                    videoUrl.startsWith("http") -> videoUrl
                    videoUrl.startsWith("//") -> "https:$videoUrl"
                    else -> {
                        val base = data.substringBeforeLast('/')
                        "$base/$videoUrl".replace(Regex("(?<!:)//+"), "/")
                    }
                }
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = "Extracted Video",
                        url = absoluteUrl
                    ) {
                        this.referer = mainUrl
                        this.quality = Qualities.Unknown.value
                    }
                )
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}

@CloudstreamPlugin
class CineHub24Plugin : BasePlugin() {
    override fun load() {
        registerMainAPI(CineHub24())
    }
}
