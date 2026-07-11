package com.lagradost.cloudstream3.providers

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.net.URLEncoder

class CineHub24 : MainAPI() {
    override var mainUrl = "https://www.cinehub24.com"
    override var name = "CineHub24"
    override val hasMainPage = true
    override val hasSearch = true
    override val supportedTypes = setOf(TvType.Movie)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(mainUrl).document
        val featured = doc.select("a[href*='/movies/']").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(
            list = HomePageList("Featured", featured.take(15)),
            hasNext = false
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("h3, .sc-crXcEl, small")?.text()?.trim() ?: return null
        val href = fixUrlNull(selectFirst("a")?.attr("href")) ?: return null
        val poster = selectFirst("img")?.attr("src")?.let { fixUrlNull(it) }
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "$mainUrl/movies.php?search=$encodedQuery"
        val doc = app.get(url).document
        return doc.select("a[href*='/movies/']").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1, h3.sc-crXcEl, .sc-crXcEl")?.text()?.trim() ?: "Unknown"
        val poster = doc.selectFirst("img")?.attr("src")?.let { fixUrlNull(it) }
        val plot = doc.selectFirst("p.text-in-two, .description, p")?.text()
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
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
        // Case 1: `data` is already a direct video file URL
        if (data.contains(".mp4")) {
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = "Direct MP4",
                    url = data
                ) {
                    this.referer = mainUrl
                    this.quality = Qualities.Unknown.value // we don't actually know the quality here
                }
            )
            return true
        }

        // Case 2: `data` is a page we need to scrape for embedded/linked video sources
        val doc = app.get(data).document
        var found = false

        doc.select("iframe[src], video source[src], a[href*='.m3u8'], a[href*='embed'], a[href*='.mp4']")
            .forEach { el ->
                // attr() never returns null in Jsoup, so use ifBlank instead of ?:
                val link = el.attr("src").ifBlank { el.attr("href") }
                if (link.isBlank()) return@forEach

                if (link.contains(".mp4") || link.contains(".m3u8") || link.contains("embed")) {
                    val fixedLink = fixUrlNull(link) ?: return@forEach
                    val type = if (link.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO

                    callback.invoke(
                        newExtractorLink(
                            source = name,
                            name = name,
                            url = fixedLink,
                            type = type
                        ) {
                            this.referer = mainUrl
                            this.quality = Qualities.Unknown.value
                        }
                    )
                    found = true
                }
            }

        return found
    }
}
