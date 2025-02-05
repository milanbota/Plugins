package com.milanbota

import android.util.Log
import com.fasterxml.jackson.annotation.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.*
import com.google.gson.Gson
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.cookies


class StreamM4UProvider : MainAPI() {
    override var lang = "en"
    override var mainUrl = "https://streamm4u.com.co"
    override var name = "StreamM4U"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )
    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36"

    override val mainPage = mainPageOf(
        "$mainUrl/movies?sort=desc" to "Latest Movies Added",
        "$mainUrl/movies/all/all/all?rate=view&sort=desc" to "Most Viewed Movies",
        "$mainUrl/movies/all/all/all?rate=view_day&sort=desc" to "Current Most Viewed Movies",
        "$mainUrl/tv-series?sort=desc" to "Latest Series Added",
        "$mainUrl/tv-series/all/all/all?rate=view&sort=desc" to "Most Viewed TV Series",
        "$mainUrl/tv-series/all/all/all?rate=view_day&sort=desc" to "Current Most Viewed TV Series",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val type = if (request.data.contains("series")) "tv" else "movie"
        val url = "${request.data}&page=${page}"
        val document = app.get(url, headers = mapOf("user-agent" to userAgent)).document
        Log.d("Flowz", "Getting mainpage $page, request: ${request.toJson()}")

        val elements = document.select("div.item").map {
            val posterUrl = it.selectFirst("img")?.attr("src")
            val urlShow = it.selectFirst("a")?.attr("href")
            val fullUrlShow = "${mainUrl}/${urlShow}"
            val title = it.selectFirst("a")?.attr("title")

            Media(title!!, fullUrlShow, posterUrl!!, type).toSearchResponse()
        }

        return newHomePageResponse(request.name, elements)
    }


    data class Media(
        val title: String,
        val url: String,
        val posterImg: String,
        val type: String
    )

    private fun Media.toSearchResponse(): SearchResponse {
        if (type == "movie") {
            return newMovieSearchResponse(
                title,
                url,
                TvType.Movie
            ) {
                this.posterUrl = posterImg
            }
        }
        return newTvSeriesSearchResponse(
            title,
            url,
            TvType.TvSeries
        ) {
            this.posterUrl = posterImg
        }

    }


    override suspend fun search(query: String): List<SearchResponse> {
        val queryFormatted = query.replace(" ", "-")
        val url = "$mainUrl/search/$queryFormatted"
        val document = app.get(url, headers = mapOf("user-agent" to userAgent)).document

        Log.d("Flowz", "Searching for $query")

        return document.select("div.item").map {
            val posterUrl = it.selectFirst("img")?.attr("src")
            val urlShow = it.selectFirst("a")?.attr("href")
            val fullUrlShow = "${mainUrl}/${urlShow}"
            val title = it.selectFirst("a")?.attr("title")
            val type = if (fullUrlShow.contains("series")) "tv" else "movie"


            Media(title!!, fullUrlShow, posterUrl!!, type).toSearchResponse()
        }

    }


    data class StreamM4UData(
        val mainUrl: String,
        val id: String? = null,
    )


    override suspend fun load(url: String): LoadResponse? {
        Log.d("Flowz", "Loading $url from load function")
        
        val document = app.get(url, headers = mapOf("user-agent" to userAgent)).document
        val poster = document.selectFirst(".mvinfo")?.attr("src")
        val plot = document.selectFirst("pre")?.lastChild()?.toString()
        val title = document.selectFirst(".breadcrumb > .active")?.text() ?: "No Title"

        if (url.contains("/movies/")) {
            return newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                StreamM4UData(url).toJson(),
            ) {
                posterUrl = poster
                this.plot = plot
            }
        } else if (url.contains("tv-series")) {
            val episodesList = mutableListOf<Episode>()
            val regex = """S.*?(\d+).*E.*?(\d+)""".toRegex()

            document.select("button.episode").forEach {
                val idEpisode = it.attr("idepisode")
                val matchResult = regex.find(it.text())

                if (matchResult != null) {
                    val season = matchResult.groups[1]?.value
                    val episodeNr = matchResult.groups[2]?.value

                    episodesList.add(newEpisode(url) {
                        this.name = "${title} S${season}E${episodeNr}"
                        this.season = season?.toInt()
                        this.episode = episodeNr?.toInt()
                        this.data = StreamM4UData(url, idEpisode).toJson()
                        this.posterUrl = fixUrl(poster!!)

                    })
                }
            }

            return newTvSeriesLoadResponse(title, mainUrl, TvType.TvSeries, episodesList) {
                this.posterUrl = poster
                this.plot = plot
            }
        } else {
            return null
        }

    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("Flowz", "Loading links for $data, callback: ${callback.toJson()}")
        
        val linkData = parseJson<StreamM4UData>(data)
        val documentz = app.get(linkData.mainUrl, headers = mapOf("user-agent" to userAgent))
        val cookies = documentz.cookies
        val document = documentz.document

        val token = document.selectFirst("meta[name='csrf-token']")?.attr("content") ?: return false

        var docData = linkData.id.let {
            if (it != null) {
                app.post(
                    "${mainUrl}/ajaxtv",
                    data = mapOf("idepisode" to it, "_token" to token),
                    cookies = cookies
                ).document
            } else {
                document
            }
        }

        docData.select(".le-server > span.singlemv").amap {
            val srcData = it.attr("data")
            val doc = app.post(
                "${mainUrl}/ajax",
                data = mapOf("m4u" to srcData, "_token" to token),
                cookies = cookies,
                headers = mapOf("Referer" to linkData.mainUrl)
            )
            val videoDocument = doc.document

            val videoUrl = videoDocument.selectFirst("iframe")?.attr("src")
            Log.d("Flowz", "videoUrl => ${videoUrl.toString()}")


            if(videoUrl.toString().contains("vidsrc") || videoUrl.toString().contains("playm4u") || videoUrl.toString().contains("hihihaha") || videoUrl.toString().contains("hihihehe")){
                val currentUrl = unshortenLinkSafe(videoUrl!!)
                val compareUrl = currentUrl.lowercase().replace(schemaStripRegex, "")
                for (extractor in extractorApis) {
                    if (compareUrl.startsWith(extractor.mainUrl.replace(schemaStripRegex, ""))) {
                        extractor.getSafeUrl(currentUrl, mainUrl, subtitleCallback, callback)
                    }
                }

            }else{
                loadExtractor(videoUrl.toString(), referer = linkData.mainUrl, subtitleCallback,callback)

            }
        }

        return true
    }

    val extractorApis: MutableList<ExtractorApi> = arrayListOf(
        VidExtractor(),
        PlayM4UExtractor(),
        PlayM4UExtractorF(),
        HiHiHeHeExtractor(),
//        HiHaExtractor()
    )


}
