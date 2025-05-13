package com.milanbota

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.fasterxml.jackson.annotation.*
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import java.net.URLDecoder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID

data class MatchResponse(
    val id: String,
    @SerializedName("page_id") val pageId: String,
    val page: Long,
    val category: String,
    val sitemap: Long,
    @SerializedName("api_matche_id") val apiMatcheId: String,
    val status: Long,
    val date: String,
    val time: String,
    val score: String,
    @SerializedName("home_score") val homeScore: String,
    @SerializedName("away_score") val awayScore: String,
    val league: String,
    @SerializedName("league_en") val leagueEn: String,
    @SerializedName("league_logo") val leagueLogo: String,
    val home: String,
    @SerializedName("home_en") val homeEn: String,
    @SerializedName("home_logo")
    val homeLogo: String,
    val away: String,
    @SerializedName("away_en") val awayEn: String,
    @SerializedName("away_logo") val awayLogo: String,
    val tv: String,
    val selected: String,
    val english: String,
    @SerializedName("has_channels") val hasChannels: String,
    val event: String,
    @SerializedName("event_desc") val eventDesc: String,
    val active: String,
    @SerializedName("redirect_url") val redirectUrl: String,
    @SerializedName("redirect_domain_ids") val redirectDomainIds: String,
)



data class SingleMatchResponse(
    val id: String,
    @JsonProperty("api_matche_id")
    val apiMatcheId: String,
    val event: String,
    @JsonProperty("event_desc")
    val eventDesc: String,
    val desc: String,
    @JsonProperty("league_id")
    val leagueId: String,
    val date: String,
    val time: String,
    val score: String,
    val active: String,
    val league: String,
    @JsonProperty("league_en")
    val leagueEn: String,
    @JsonProperty("league_logo")
    val leagueLogo: String,
    val home: String,
    @JsonProperty("home_en")
    val homeEn: String,
    @JsonProperty("home_logo")
    val homeLogo: String,
    val away: String,
    @JsonProperty("away_en")
    val awayEn: String,
    @JsonProperty("away_logo")
    val awayLogo: String,
    val tv: String,
    val selected: String,
    @JsonProperty("redirect_url")
    val redirectUrl: String,
    val channels: List<ChannelResponse>,
    @JsonProperty("has_channels")
    val hasChannels: Long,
    val edges: List<Any?>,
)

data class ChannelResponse(
    val id: String,
    val type: String,
    val key: String,
    val edge: String,
    val link: String,
    @JsonProperty("have_second_link")
    val haveSecondLink: String,
    val ch: String,
    @JsonProperty("server_name")
    val serverName: String,
    @JsonProperty("server_name_en")
    val serverNameEn: String,
)

data class Channel (
    val ch: String,
    val name: String,
    val baseUrl: String,
    val pParam: String = "12"
)

@RequiresApi(Build.VERSION_CODES.O)
fun Channel.getIframeUrl(): String{
    return "${this.baseUrl}?ch=${this.ch}&p=${this.pParam}&token=${generateUuid()}&kt=${Instant.now().toEpochMilli()}"
}

@RequiresApi(Build.VERSION_CODES.O)
fun decodeToken(token: String): String {
    val b64DecodedToken = String(Base64.getDecoder().decode(token))
    val percentEncoded = buildString {
        for (i in b64DecodedToken.indices step 2) {
            append("%")
            append(b64DecodedToken.slice(i..i + 1).take(2))
        }
    }
    return URLDecoder.decode(percentEncoded, "UTF-8")
}

fun generateUuid(): String {
    return UUID.randomUUID().toString()
}

class YallaProvider : MainAPI() {
    override var lang = "en"
    override var mainUrl = "https://ws.kora-api.top/api/"
    override var name = "Yalla"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(
        TvType.Live,
    )
    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36"
    private val headers = mapOf("user-agent" to userAgent)

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Live Matches",
    )

    @RequiresApi(Build.VERSION_CODES.O)
    private fun MatchResponse.toSearchResponse() : SearchResponse{
        val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"))
        return LiveSearchResponse(
            "${this.time} - ${this.homeEn} - ${this.awayEn}",
            url = "https://ws.kora-api.top/api/matche/${this.id}/en?t=$ts" ,
            apiName = "Yalla",
            type = TvType.Live,
            posterUrl = "https://ws.kora-api.top/uploads/league/${this.leagueLogo}",
            id = 1,
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val dt = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmm"))
        val url = "$mainUrl/matches/$dt?t=$time"
        val eventz = app.get(url, headers=headers)

        val listMatchResponses = object : TypeToken<List<MatchResponse>>() {}.type
        val events : List<MatchResponse> = Gson().fromJson(eventz.text, listMatchResponses)
        Log.d("YallaProviderz", "event => $events")
        val matches = events.map {
            if (it.hasChannels != "0"){
                return@map it.toSearchResponse()
            }else{
                return@map null
            }
        }.filterNotNull()

        return newHomePageResponse(request.name, matches, hasNext = false)
    }


    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun load(url: String): LoadResponse {
        val match = app.get(url, headers = headers).parsedSafe<SingleMatchResponse>()?: return LiveStreamLoadResponse("", "", "", "")
        val newurl = "https://shoot-yalla.co/live/${match.id}/${match.apiMatcheId}/${match.desc.replace(' ', '-').lowercase()}"

        val matchs = app.get(newurl, headers = headers).document
        val d = matchs.selectFirst("script:containsData(u_key)")?.data()?:""

        val key = """var\s+u_key\s+=\s*[\"'](.*?)[\"']""".toRegex().find(d)
        val iframeUrlEncoded = key?.groupValues?.get(1)?:""
        val iframeUrl = decodeToken(iframeUrlEncoded)
//        val frameUrl = iframeUrl.ifEmpty { "https://yalla.kora-tv.vip/frame.php" }
        val frameUrl = iframeUrl.ifEmpty { "https://yalla.kora-plus.top/frame.php" }

        val pData = """var\s+p\s+=\s*(.*?);""".toRegex().find(d)
        val p = pData?.groupValues?.get(1)?:""
        Log.d("YallaProviderzz", "Loading pParam $p")


        val channels = match.channels.map {
            Channel(
                it.ch,
                it.serverNameEn,
                frameUrl,
                pParam = p
            )
        }



        return LiveStreamLoadResponse(
            name = "${match.homeEn} - ${match.awayEn}",
            url = url,
            dataUrl = channels.toJson(),
            apiName = name,
            backgroundPosterUrl = "https://ws.kora-api.top/uploads/league/${match.leagueLogo}",
            plot = "Score: ${match.score}"
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getVideoLink(channel: Channel) : ExtractorLink? {
        val headerz = headers + mapOf("Referer" to "https://shoot-yalla.me/")
        val data = app.get(channel.getIframeUrl(), headers=headerz).text

        val key = """var\s+token\s+=\s*[\"'](.*?)[\"']""".toRegex().find(data)
        val encodedUrl = key?.groupValues?.get(1)?: return null
        val url = decodeToken(encodedUrl)

        return ExtractorLink(
            "s",
            "${channel.name}",
            "$url",
            "https://shoot-yalla.co/",
            Qualities.Unknown.value,
            INFER_TYPE
        )

    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val links = parseJson<List<Channel>>(data)


        links.amap {
            getVideoLink(it)
        }.forEach{extractorLink ->
            if (extractorLink != null) {
                callback(extractorLink)
            }
        }
        return true
    }

}