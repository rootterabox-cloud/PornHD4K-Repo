package com.lagradost.cloudstream3.plugins

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Element

class PornHD4KProvider : MainAPI() {
    override var mainUrl = "https://www.pornhd4k.net"
    override var name = "PornHD4K"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val url = if (page <= 1) mainUrl else "$mainUrl/page/$page/"
        val document = app.get(url).document
        val items = document.select("div.video-item, div.item").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, items)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("a")?.attr("hint") ?: this.selectFirst("h2, .title")?.text() ?: return null
        val href = fixUrl(this.selectFirst("a")?.attr("href") ?: return null)
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src") ?: this.selectFirst("img")?.attr("data-src"))
        
        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url).document
        return document.select("div.video-item, div.item").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1")?.text() ?: return null
        val poster = fixUrlNull(document.selectFirst("video")?.attr("poster") ?: document.selectFirst("meta[property=og:image]")?.attr("content"))
        val tags = document.select("div.video-tags a, .category a").map { it.text() }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isDataJob: Boolean,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val scripts = document.select("script")
        
        for (script in scripts) {
            val scriptText = script.data()
            if (scriptText.contains("jwplayer") && scriptText.contains("file")) {
                val m3u8Regex = """file\s*:\s*["'](https?://[^"']+\.m3u8[^"']* )["']""".toRegex()
                val match = m3u8Regex.find(scriptText)
                if (match != null) {
                    val m3u8Url = match.groupValues[1]
                    callback.invoke(
                        ExtractorLink(
                            this.name,
                            this.name,
                            m3u8Url,
                            "",
                            getQualityFromName("720p"),
                            isM3u8 = true
                        )
                    )
                }
            }
        }
        return true
    }
}
