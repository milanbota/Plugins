package com.milanbota

import android.util.Log
import com.fasterxml.jackson.annotation.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.*
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.cookies
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class PropsGson(
    val titles: List<TitleGson>,
    val sliders: List<SliderGson> = emptyList()
)


data class FullResponseGson(
    val props: PropsGson
)

data class ImageGson(
    @SerializedName("imageable_id") val imageableId: Int,
    @SerializedName("imageable_type") val imageableType: String?,
    val filename: String,
    val type: String,
    @SerializedName("original_url_field") val originalUrlField: String? = null
)

data class SliderGson(
    val name: String,
    val label: String,
    val titles: List<TitleGson>
)

data class ActorGson(
    val name: String
)

data class TitleGson(
    val id: Int,
    val slug: String,
    val name: String,
    val type: String,
    val score: String,
    @SerializedName("sub_ita") val subIta: Int,
    @SerializedName("last_air_date") val lastAirDate: String? = null,
    @SerializedName("seasons_count") val seasonsCount: Int,
    val images: List<ImageGson>,
    @SerializedName("main_actors") val actors: List<ActorGson> = emptyList(),

    )

data class ResponseDataGson(
    val titles: List<TitleGson>
)

fun getImageUrl(mainUrl: String, url: String): String {
    val cdnprefix = mainUrl.replace("streamingcommunity", "cdn.streamingcommunity")
    val poster = "$cdnprefix/images/$url"
    return poster
}
class StreamingCommunityProvider : MainAPI() {
    override var lang = "it"
    override var mainUrl = "https://streamingcommunity.lu"
    override var name = "StreamingCommunity"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    private val cloudflare = CloudflareKiller()
    private val userAgent =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Safari/605.1.15"
    private val gson = Gson()
    private val TAG = "StreamingCommunity"

    override val mainPage = mainPageOf(
        "$mainUrl/film" to "Movies",
        "$mainUrl/serie-tv/" to "Series",
        "$mainUrl/api/archive?genre[]=24" to "Documentaries",
        "$mainUrl/api/archive?type=movie&genre[]=2" to "Crime Movies",
        "$mainUrl/api/archive?type=movie&genre[]=4" to "Action Movies",
        "$mainUrl/api/archive?type=movie&genre[]=5" to "Thriller Movies",
        "$mainUrl/api/archive?type=movie&genre[]=6" to "Mystery Movies",
        "$mainUrl/api/archive?genre[]=22" to "History",
//        "$mainUrl/archivio?genre[]=4&type=movie" to "Action"
    )

    suspend fun loadMainPage(page: Int, request: MainPageRequest): HomePageResponse{
        val document = app.get(request.data, headers = mapOf("user-agent" to userAgent), interceptor = cloudflare).document
        val resultsJson = document.select("#app").attr("data-page")
        val trendingResults = gson.fromJson(resultsJson, FullResponseGson::class.java).props.sliders[0].titles
        val trendingList = mutableListOf<SearchResponse>()
        for (title in trendingResults) {
            trendingList.add(title.toSearchResult()!!)
        }

        val top10 = gson.fromJson(resultsJson, FullResponseGson::class.java).props.sliders[2].titles
        val top10List = mutableListOf<SearchResponse>()
        for (title in top10) {
            top10List.add(title.toSearchResult()!!)
        }

        val trendingPage = HomePageList("Trending ${request.name}", trendingList)
        val top10Page = HomePageList("Top 10 ${request.name}", top10List)

        return newHomePageResponse(listOf(trendingPage,top10Page), hasNext = false)
    }

    suspend fun loadApiData(page: Int, request: MainPageRequest): HomePageResponse{
        val offset = (page-1) * 60
        val url = "${request.data}${if (offset==0) "" else "&offset="+offset}"
        val document = app.get(url, headers = mapOf("user-agent" to userAgent), interceptor = cloudflare)
        val titles = document.parsed<PropsGson>()

        val events = mutableListOf<SearchResponse>()
        titles.titles.forEach {
            it.toSearchResult()?.let { it1 -> events.add(it1) }
        }

        return newHomePageResponse(request, events, hasNext = true)
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (request.data.contains("/api/") == true){
            return loadApiData(page,request)
        } else return loadMainPage(page,request)
    }

    private fun TitleGson.toSearchResult(): SearchResponse? {
        val title = this.name
        val link = "$mainUrl/titles/${this.id}-${this.slug}"
        val cdnprefix = mainUrl.replace("streamingcommunity", "cdn.streamingcommunity")
        val posterImage = this.images.find { it.type == "poster" }
        val posterUrl = if (posterImage != null) {
            "${cdnprefix}/images/${posterImage.filename}"
        }else "${cdnprefix}/images/${this.images[0].filename}"
        val response= when (this.type){
            "movie"->{
                newMovieSearchResponse(title, link, TvType.Movie) {
                    addPoster(posterUrl)
                }
            }
            "tv"->{

                newTvSeriesSearchResponse(title, link, TvType.TvSeries) {
                    addPoster(posterUrl)
                }
            }
            else->{
                return null
            }
        }
        return response
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val queryFormatted = query.replace(" ", "%20")
        val url = "$mainUrl/search?q=$queryFormatted"
        val document = app.get(url, headers = mapOf("user-agent" to userAgent), interceptor = cloudflare).document
        val resultsJson = document.select("#app").attr("data-page")
        val results = gson.fromJson(resultsJson, FullResponseGson::class.java).props.titles
        val resultsList = mutableListOf<SearchResponse>()
        for (title in results) {
            resultsList.add(title.toSearchResult()!!)
        }
        return resultsList
    }

    //RESPONSE DATACLASSES
    data class Image(
        val id: Int,
        val filename: String
    )

    data class Title(
        val id: Int,
        val images: List<Image>?,
        val type: String,
        val release_date: String,
        val name: String,
        val plot:String,
        val seasons: List<Season>,
        val seasons_count: Int,
        val actors: List<Actor>
    )

    data class Actor(
        val name:String
    )

    data class LoadedSeason(
        val episodes: List<LoadedEpisode>,
        val number: Int
    )
    data class LoadedEpisode(
        val id: Int,
        val name: String,
        val plot: String,
        val number: Int,
        val images: List<Image>
    )
    data class Props(
        val title: Title,
        val loadedSeason: LoadedSeason,
    )

    data class Response(
        val props: Props
    )

    data class Season(
        val id: Int,
        val number: Int,
        val episodes_count: Int
    )


    override suspend fun load(url: String): LoadResponse? {
        Log.d(TAG, "load('${url})'")
        val document = app.get(url, headers = mapOf("user-agent" to userAgent), interceptor = cloudflare).document
        val resultJson = document.select("#app").attr("data-page")
        val response = gson.fromJson(resultJson, Response::class.java)

        val plot= response.props.title.plot
        val poster =getImageUrl(mainUrl, response.props.title.images?.get(3)?.filename ?: "")
//        val actors = response.props.title.actors.map {
//            ActorData(com.lagradost.cloudstream3.Actor(it.name))
//        }

        when (response.props.title.type) {
            "movie" -> {
                return newMovieLoadResponse(
                    response.props.title.name,
                    mainUrl+"/watch/"+response.props.title.id,
                    TvType.Movie,
                    mainUrl+"/watch/"+response.props.title.id
                ) {
                    posterUrl = poster
                    this.plot =plot
//                    this.actors = actors
                    this.year= response.props.title.release_date.substringBefore("-").toInt()

                }

            }
            "tv" -> {
                val name = response.props.title.name
                val episodesList = mutableListOf<Episode>()

                val seasons = response.props.title.seasons
                seasons.amap { season ->
                    val seasonDocument = app.get(url+"/stagione-"+season.number.toString(), headers = mapOf("user-agent" to userAgent), interceptor = cloudflare).document
                    val resultJsons = seasonDocument.select("#app").attr("data-page")
                    val episodeResponse = gson.fromJson(resultJsons, Response::class.java)
                    for (episode in episodeResponse.props.loadedSeason.episodes) {
                        val href = mainUrl+"/watch/"+response.props.title.id+"?episode_id="+episode.id
                        val postImage = getImageUrl(mainUrl, episode.images.firstOrNull()?.filename?: "")
                        episodesList.add(newEpisode(href) {
                            this.name = episode.name
                            this.season = season.number
                            this.episode = episode.number
                            this.description = episode.plot
                            this.posterUrl = postImage
                        })
                    }
                }
                return newTvSeriesLoadResponse(name,url,TvType.TvSeries,episodesList){
                    this.posterUrl=poster
                    this.plot =plot
//                    this.actors = actors
                }
            }

            else -> {
                return null
            }
        }
    }



    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val headers = mutableMapOf("accept" to "*/*",
            "accept-language" to "en-GB,en;q=0.9",
            "cache-control" to "no-cache",
            "pragma" to "no-cache",
            "priority" to "u=1, i",
            "sec-ch-ua" to "\"Google Chrome\";v=\"129\", \"Not=A?Brand\";v=\"8\", \"Chromium\";v=\"129\"",
            "sec-ch-ua-mobile" to "?0",
            "sec-ch-ua-platform" to "\"macOS\"",
            "sec-fetch-dest" to "empty",
            "sec-fetch-mode" to "cors",
            "sec-fetch-site" to "same-origin",
            "user-agent" to userAgent,
        )
        Log.d(TAG,"loadLinks('$data')")

        val dataLink= data.replace("watch","iframe")
        Log.d(TAG,"LINK ORIGINALE "+dataLink)
        val response = app.get(dataLink, headers=headers, interceptor = cloudflare)
        val links = response.document.select("iframe").attr("src")
        Log.d(TAG,"LINK IFRAME "+links)
        val queryParams = getQueryParams(links)

        headers.put("referer", "https://streamingcommunity.ooo/")
        val vixUrl= app.get(links, headers= headers, interceptor = cloudflare).document.select("script")[4]

        val reg = Regex("""window.masterPlaylist\s+=\s+\{.*'token':\s+'(.*?)'.*expires':\s+'(.*?)'.*url:\s+'(.*?)'.*\}.*window""", RegexOption.DOT_MATCHES_ALL)
        val matchResult = reg.find(vixUrl.toString())

        if (matchResult != null) {
            val token = matchResult.groups[1]?.value
            val expires = matchResult.groups[2]?.value
            val linkurlFinal = matchResult.groups[3]?.value

            var finalUr = "${linkurlFinal}${if (linkurlFinal?.contains("?") == true) "&" else "?"}token=${token}&expires=${expires}"
            if (queryParams.get("canPlayFHD")?.isNotEmpty() == true) finalUr+="&h=1"
            Log.d(TAG,"Video url "+finalUr)

            callback.invoke(
                ExtractorLink(
                    name,
                    name,
                    finalUr,
                    isM3u8 = true,
                    referer = links,
                    quality = Qualities.Unknown.value,
                    headers = headers
                )
            )
            return true
        }
        return false
    }


}


data class Genre(
    @JsonProperty("name") val name: String,
    @JsonProperty("pivot") val pivot: Pivot,
)

data class Pivot(
    @JsonProperty("titleID") val titleID: Long,
    @JsonProperty("genreID") val genreID: Long,
)

data class Vote(
    @JsonProperty("title_id") val title_id: Long,
    @JsonProperty("average") val average: String,
    @JsonProperty("count") val count: Long,
    @JsonProperty("type") val type: String,
)


data class Image(
    @JsonProperty("imageable_id") val imageableID: Long,
    @JsonProperty("imageable_type") val imageableType: String?,
    @JsonProperty("server_id") val serverID: Long,
    @JsonProperty("proxy_id") val proxyID: Long,
    @JsonProperty("url") val url: String,
    @JsonProperty("type") val type: String,
)


data class Season(
    @JsonProperty("id") val id: Long,
    @JsonProperty("name") val name: String? = "",
    @JsonProperty("plot") val plot: String? = "",
    @JsonProperty("date") val date: String? = "",
    @JsonProperty("number") val number: Long,
    @JsonProperty("title_id") val title_id: Long,
    @JsonProperty("createdAt") val createdAt: String? = "",
    @JsonProperty("updated_at") val updatedAt: String? = "",
    @JsonProperty("episodes") val episodes: List<Episodejson>
)

data class Episodejson(
    @JsonProperty("id") val id: Long,
    @JsonProperty("number") val number: Long,
    @JsonProperty("name") val name: String? = "",
    @JsonProperty("plot") val plot: String? = "",
    @JsonProperty("season_id") val seasonID: Long,
    @JsonProperty("images") val images: List<ImageSeason>
)

data class ImageSeason(
    @JsonProperty("imageable_id") val imageableID: Long,
    @JsonProperty("imageable_type") val imageableType: String?,
    @JsonProperty("server_id") val serverID: Long,
    @JsonProperty("proxy_id") val proxyID: Long,
    @JsonProperty("url") val url: String,
    @JsonProperty("type") val type: String,
    @JsonProperty("original_url") val originalURL: String
)


fun getQueryParams(url: String): Map<String, String> {
    val uri = URI(url)
    val query = uri.query ?: return emptyMap()

    return query.split("&")
        .map {
            val (key, value) = it.split("=")
            URLDecoder.decode(key, StandardCharsets.UTF_8.name()) to URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        }
        .toMap()
}