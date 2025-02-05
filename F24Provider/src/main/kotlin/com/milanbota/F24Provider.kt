package com.milanbota

import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor


class F24Provider(val plugin: F24Plugin) : MainAPI() { // all providers must be an intstance of MainAPI
    override var mainUrl = "https://www.filma24.band"
    override var name = "Filma24"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override var lang = "sq"

    // enable this when your provider has a main page
    override val hasMainPage = true

    val userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"
    val headers = mapOf("user-agent" to userAgent)

    // main page
    override val mainPage = mainPageOf(
        "$mainUrl/" to "Movies",
        "$mainUrl/seriale/" to "Series"
    )


    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val type = if (request.data.contains("/seriale")) "tv" else "movie"
        val document = app.get("${request.data}page/$page/").document
        val elements = document.selectFirst("div.container-lg > div.row")?.select(".movie-thumb")?.map{
            val title = it.select("div.under-thumb a h4").text()
            val posterUrl = it.select(".thumb-overlay img").attr("src")
            val url = it.select("a").attr("href")

            Media(title,url,posterUrl).toSearchResponse(type)
        }

        return newHomePageResponse(request.name, elements ?: emptyList())
    }


    data class Media(
        val title: String,
        val url: String,
        val posterImg: String,
    )

    private fun Media.toSearchResponse(type: String? = null): SearchResponse {
        if (type == "movie"){
            return newMovieSearchResponse(
                title ,
                Data(url, TvType.Movie, posterImg).toJson(),
                TvType.Movie
            ) {
                this.posterUrl = posterImg
            }
        }
        return newTvSeriesSearchResponse(
            title,
            Data(url, TvType.TvSeries, posterImg).toJson(),
            TvType.TvSeries
        ){
            this.posterUrl = posterImg
        }

    }

    data class Data(
        val url: String,
        val type: TvType? = TvType.TvSeries,
        val posterImg: String? = null
    )

    suspend fun findAllLinks(urls: List<String>): List<String>{
        Log.d("Filma24z", "Titles => $urls")

        return urls.amap {
            val doc = app.get(it, headers=headers).document
            val link = doc.selectFirst("iframe")?.attr("src")
            return@amap link
        }.filterNotNull()
    }

    override suspend fun load(url: String): LoadResponse {
        val data = parseJson<Data>(url)
        Log.d("Filma24z", "Links => load($url)")

        val document = app.get(data.url, headers = headers).document

        val title = document.selectFirst("h4")?.text()


        if (data.type == TvType.TvSeries) {

            val episodes = mutableListOf<Episode>()
            document.select(".movie-thumb")?.map {
                val episodeUrl = it.selectFirst("a")?.attr("href")
                val episodePoster = it.selectFirst("img")?.attr("src")
                val seasonText = it.select("span").text()
                val regex = """S.*?(\d+).*E.*?(\d+)""".toRegex()

                val matchResult = regex.find(seasonText)

                if (matchResult != null) {
                    val season = matchResult.groups[1]?.value
                    val episodeNr = matchResult.groups[2]?.value
                    val doc = app.get(episodeUrl!!).document
                    val links = doc.select(".movie-links li > a").map { it.attr("href") }

                    episodes.add(newEpisode(episodeUrl) {
                        this.name = "${title} S${season}E${episodeNr}"
                        this.season = season?.toInt()
                        this.episode = episodeNr?.toInt()
                        this.data = links.toJson().toString()
                        this.posterUrl = fixUrl(episodePoster!!)

                    })
                }
            }

            return newTvSeriesLoadResponse(
                title!!,
                url,
                TvType.TvSeries,
                episodes
            )

        } else {
            val allLinks = mutableListOf<String>()
            val primaryLink = document.selectFirst("iframe")?.attr("src")
            if (primaryLink != null) allLinks.add(primaryLink)

            val otherPrimaryLinks = document.select(".servers-list > a").map { it.attr("href") }.map { "${data.url}/$it" }
            val otherLinks = findAllLinks(otherPrimaryLinks.subList(1,otherPrimaryLinks.size))
            if (otherLinks.isNotEmpty()) allLinks += otherLinks

            val additionalLinks = document.select(".movie-links li > a").map { it.attr("href") }
            allLinks += additionalLinks
            val plot = document.select(".movie-intro").text()

            return newMovieLoadResponse(
                title!!,
                url,
                TvType.Movie,
                allLinks.map{ httpsify(it) }.toJson()
            ) {
                this.actors = listOf(ActorData(Actor("Bledi")))
                this.posterUrl = data.posterImg
                this.plot = plot
            }
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val links = parseJson<List<String>>(data)
        Log.d("Filma24z", "Links => $links")
        links.amap{
            val videoUrl = if (it.contains("avjosa")){
                unshortenAvjosa(it)?:return@amap
            }else{
                it
            }
            Log.d("Filma24z", "Loading extractor => $videoUrl")

            val page = app.get(videoUrl, headers= mapOf("user-agent" to userAgent)).document
            val regex = """attr\("href","(http.*?)"\)""".toRegex()
            val matchResult = regex.find(page.toString())

            if (matchResult != null) {
                val url = matchResult.groups[1]?.value
                loadExtractor(url!!,subtitleCallback,callback)
            }
        }

        return true
    }

    // this function gets called when you search for something
    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/search/${query.replace(" ", "+")}").document
        val elements = document.selectFirst("div.container-lg > div.row")?.select(".movie-thumb")?.map{
            val title = it.select("div.under-thumb a h4").text()
            val posterUrl = it.select(".thumb-overlay img").attr("src")
            val url = it.select("a").attr("href")

            Media(title,url,posterUrl).toSearchResponse("movie")
        }

        return elements!!
    }
}


suspend fun unshortenAvjosa(url:String) : String? {
    val userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"
    val document = app.get(url, headers = mapOf("user-agent" to userAgent)).document

    val scriptData = document.selectFirst("script:containsData(#pleasewait)")?.data()?: return null

    val finalUrl = Regex("""[\"']href[\"'].*?[\"'](https:.*?)[\"']""").find(scriptData)?.groupValues?.get(1)

    return finalUrl

}