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
    override val supportedTypes = setOf(TvType.Movie)

    private val posterBaseUrl = "https://image.tmdb.org/t/p/w500/"

    private fun JSONObject.toSearchResult(): SearchResponse {
        val id = optString("id")
        val title = optString("MovieTitle").trim()
        val posterFile = optString("poster")
        val poster = if (posterFile.isNotBlank() && posterFile != "Array") "$posterBaseUrl$posterFile" else null
        val watchLink = optString("MovieWatchLink")
        val plot = optString("MovieStory")
        val year = optString("MovieYear").toIntOrNull()

        val encodedUrl = listOf(id, title, poster ?: "", watchLink, plot.replace("|", " "))
            .joinToString("|||")

        return newMovieSearchResponse(title, encodedUrl, TvType.Movie) {
            this.posterUrl = poster
            this.year = year
        }
    }

    private val apiUrl = "http://203.76.96.50/api/v1"

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
        val categories = listOf(
            "Latest" to "$apiUrl/movies.php",                     // no limit
            "Hollywood" to "$apiUrl/movies.php?category=Hollywood",
            "Bollywood" to "$apiUrl/movies.php?category=Bollywood",
            "Animation" to "$apiUrl/movies.php?category=Animation",
            "Korean" to "$apiUrl/movies.php?category=Korean",
            "Tamil" to "$apiUrl/movies.php?category=Tamil",
            "Hindi Dubbed" to "$apiUrl/movies.php?category=Hindi+Dubbed"
        )

        val lists = categories.mapNotNull { (catName, url) ->
            val movies = fetchMovies(url)
            if (movies.isEmpty()) null
            else HomePageList(catName, movies.map { it.toSearchResult() })
        }

        return newHomePageResponse(lists, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        // Also without a limit
        val movies = fetchMovies("$apiUrl/movies.php")
        return movies
            .filter { it.optString("MovieTitle").contains(query, ignoreCase = true) }
            .map { it.toSearchResult() }
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
