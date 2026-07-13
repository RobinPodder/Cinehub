package com.lagradost.cloudstream3.providers

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONArray
import org.json.JSONObject
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

// Moved to top level – allows const val
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
        // ---- MOVIES ----
        val movieCategories = listOf(
            "Latest" to "$apiUrl/movies.php?limit=$MAX_LIMIT",
            "Hollywood" to "$apiUrl/movies.php?category=Hollywood&limit=$MAX_LIMIT",
            "Bollywood" to "$apiUrl/movies.php?category=Bollywood&limit=$MAX_LIMIT",
            "Animation" to "$apiUrl/movies.php?category=Animation&limit=$MAX_LIMIT",
            "Franch" to "$apiUrl/movies.php?category=Franch&limit=$MAX_LIMIT",
            "Indian Bangla" to "$apiUrl/movies.php?category=Indian+Bangla&limit=$MAX_LIMIT",
            "Korean" to "$apiUrl/movies.php?category=Korean&limit=$MAX_LIMIT",
            "Tamil" to "$apiUrl/movies.php?category=Tamil&limit=$MAX_LIMIT",
            "Hindi Dubbed" to "$apiUrl/movies.php?category=Hindi+Dubbed&limit=$MAX_LIMIT"
        )

        val movieLists = movieCategories.mapNotNull { (catName, url) ->
            val movies = fetchMovies(url)
            if (movies.isEmpty()) null
            else HomePageList("Movies - $catName", movies.map { it.toSearchResult(false) })
        }

        // ---- TV SERIES ----
        val seriesCategories = listOf(
            "Latest" to "$tvApiUrl?limit=$MAX_LIMIT",
            "Hollywood" to "$tvApiUrl?category=Hollywood&limit=$MAX_LIMIT",
            "Bollywood" to "$tvApiUrl?category=Bollywood&limit=$MAX_LIMIT",
            "Animation" to "$tvApiUrl?category=Animation&limit=$MAX_LIMIT",
            "Franch" to "$tvApiUrl?category=Franch&limit=$MAX_LIMIT",
            "Indian Bangla" to "$tvApiUrl?category=Indian+Bangla&limit=$MAX_LIMIT",
            "Korean" to "$tvApiUrl?category=Korean&limit=$MAX_LIMIT",
            "Tamil" to "$tvApiUrl?category=Tamil&limit=$MAX_LIMIT",
            "Hindi Dubbed" to "$tvApiUrl?category=Hindi+Dubbed&limit=$MAX_LIMIT"
        )

        val seriesLists = seriesCategories.mapNotNull { (catName, url) ->
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
        if (data.contains(".mp4")) {
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
        return false
    }
}

@CloudstreamPlugin
class CineHub24Plugin : BasePlugin() {
    override fun load() {
        registerMainAPI(CineHub24())
    }
}
