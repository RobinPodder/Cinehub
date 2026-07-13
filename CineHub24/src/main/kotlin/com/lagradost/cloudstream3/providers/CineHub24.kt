package com.lagradost.cloudstream3.providers

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONArray
import org.json.JSONObject
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

class CineHub24 : MainAPI() {
    override var mainUrl = "http://www.cinehub24.com"
    override var name = "CineHub24"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries) // now supports both

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

        // Determine type based on parameter
        val tvType = if (isSeries) TvType.TvSeries else TvType.Movie

        return newMovieSearchResponse(title, encodedUrl, tvType) {
            this.posterUrl = poster
            this.year = year
        }
    }

    private val apiUrl = "http://203.76.96.50/api/v1"

    // Guessed TV Series endpoint – CHANGE THIS if the actual one is different
    private val tvApiUrl = "http://203.76.96.50/api/v1/tv.php" // might be series.php or with a parameter

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
            "Latest" to "$apiUrl/movies.php",
            "Hollywood" to "$apiUrl/movies.php?category=Hollywood",
            "Bollywood" to "$apiUrl/movies.php?category=Bollywood",
            "Animation" to "$apiUrl/movies.php?category=Animation",
            "Franch" to "$apiUrl/movies.php?category=Franch",         // added
            "Indian Bangla" to "$apiUrl/movies.php?category=Indian+Bangla", // added
            "Korean" to "$apiUrl/movies.php?category=Korean",
            "Tamil" to "$apiUrl/movies.php?category=Tamil",
            "Hindi Dubbed" to "$apiUrl/movies.php?category=Hindi+Dubbed"
        )

        val movieLists = movieCategories.mapNotNull { (catName, url) ->
            val movies = fetchMovies(url)
            if (movies.isEmpty()) null
            else HomePageList("Movies - $catName", movies.map { it.toSearchResult(isSeries = false) })
        }

        // ---- TV SERIES ----
        // Using the same categories but with the tvApiUrl – verify this endpoint!
        val seriesCategories = listOf(
            "Latest" to "$tvApiUrl",
            "Hollywood" to "$tvApiUrl?category=Hollywood",
            "Bollywood" to "$tvApiUrl?category=Bollywood",
            "Animation" to "$tvApiUrl?category=Animation",
            "Franch" to "$tvApiUrl?category=Franch",
            "Indian Bangla" to "$tvApiUrl?category=Indian+Bangla",
            "Korean" to "$tvApiUrl?category=Korean",
            "Tamil" to "$tvApiUrl?category=Tamil",
            "Hindi Dubbed" to "$tvApiUrl?category=Hindi+Dubbed"
        )

        val seriesLists = seriesCategories.mapNotNull { (catName, url) ->
            val series = fetchMovies(url)
            if (series.isEmpty()) null
            else HomePageList("TV Series - $catName", series.map { it.toSearchResult(isSeries = true) })
        }

        // Combine both lists
        val allLists = movieLists + seriesLists
        return newHomePageResponse(allLists, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // Search both movies and series (optional)
        val movieResults = fetchMovies("$apiUrl/movies.php")
            .filter { it.optString("MovieTitle").contains(query, ignoreCase = true) }
            .map { it.toSearchResult(isSeries = false) }

        // Also try searching TV series – but might return same results if endpoint is wrong
        val seriesResults = fetchMovies("$tvApiUrl")
            .filter { it.optString("MovieTitle").contains(query, ignoreCase = true) }
            .map { it.toSearchResult(isSeries = true) }

        return movieResults + seriesResults
    }

    override suspend fun load(url: String): LoadResponse {
        val parts = url.split("|||")
        val title = parts.getOrElse(1) { "Unknown" }
        val poster = parts.getOrElse(2) { "" }.ifBlank { null }
        val watchLink = parts.getOrElse(3) { "" }
        val plot = parts.getOrElse(4) { "" }

        // We can't differentiate type from the URL, but we can assume movie or series.
        // You could store the type in the encoded URL as well. For simplicity, we treat as Movie.
        // But since we have watchLink, loadLinks will handle it.
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
