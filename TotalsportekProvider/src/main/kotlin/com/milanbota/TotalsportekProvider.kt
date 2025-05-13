package com.milanbota

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.fasterxml.jackson.annotation.*
import com.google.gson.Gson
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import org.jsoup.nodes.Document
import java.net.URI
import java.net.URL
import java.net.URLDecoder

data class Match(
    val date: String,
    val match: String,
    val competition: String,
    val url : String,
    val image: String?
)

data class Channel(
    val source: String,
    val name: String,
    val url: String,
    val language: String,
    val reputation: String
)


class TotalsportekProvider : MainAPI() {
    override var lang = "en"
    override var mainUrl = "https://www.totalsportek.to"
    override var name = "TotalSportek"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(
        TvType.Live,
    )
    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36"
    private val headers = mapOf("user-agent" to userAgent)

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Latest",
        "$mainUrl/soccerstreams" to "Football",
        "$mainUrl/nbastreams" to "NBA",

    )

    private fun Match.toSearchResponse() : SearchResponse{
        @Suppress("DEPRECATION_ERROR")
        return LiveSearchResponse(
            "${this.date} - ${this.match}",
            url = this.url ,
            apiName = "TotalSportek",
            type = TvType.Live,
            posterUrl = this.image,
            id = 1,
        )
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data, headers=headers).document
        val events = mutableListOf<SearchResponse>()
        document.select("body .top-tournament").forEach{ tournament ->
            val leagueName = tournament.selectFirst(".league-name")?.text() ?: "Others"
            val poster = tournament.selectFirst("img")?.attr("src") ?: ""
            tournament.select("li").forEach{ match ->
                val date = match.selectFirst("a > div > div:nth-of-type(1)")?.text() ?: ""
                val url = match.selectFirst("a")?.attr("href")
                val matchData = match.selectFirst("a > div > div:nth-of-type(2)")?.text() ?: ""
                if (url != null) {
                    events.add(Match(
                        date = date,
                        match = matchData,
                        competition = leagueName,
                        url = url,
                        image = poster
                    ).toSearchResponse())
                }
            }


        }

        return newHomePageResponse(request.name, events, hasNext = false)
    }


    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, headers = headers).document
        val homeTeam = document.selectFirst(".match-view-head-side1")?.text()?: ""
        val AwayTeam = document.selectFirst(".match-view-head-side2")?.text()?: ""
        val status = document.selectFirst(".match-view-headSS")?.text()
        val images = document.select(".match-view-headT img").map {
            it.attr("src")
        }


        val channels = document.select("#streams tbody > tr").map {
            val videoUrl = it.selectFirst(".watch-btn")?.attr("href") ?: return@map null
            val rep = listOf("\uD83E\uDD47","\uD83E\uDD48", "\uD83E\uDD49" , "\uD83D\uDC8E")
            val source = it.selectFirst("td.streamer-name")?.text()
            val name = it.selectFirst(".badge-channel")?.text()
            val language = it.selectFirst(".badge-language")?.text()
            val reputation = it.selectFirst("td:nth-of-type(3)")?.text()
            val reputationLevel = if(reputation?.lowercase()?.contains("gold") == true){
                rep[0]
            }else if(reputation?.lowercase()?.contains("silver") == true) {
                rep[1]
            }else if(reputation?.lowercase()?.contains("bronze") == true) {
                rep[2]
            }else rep[3]

            Channel(
                source = source ?: "Unknown",
                name = name?: "N/A",
                url = videoUrl,
                language = language?:"oth",
                reputation = reputationLevel
            )
        }.filterNotNull()

        val posterUrl = if(images.size>0){
            images[0]
        }else ""

        @Suppress("DEPRECATION_ERROR")
        return LiveStreamLoadResponse(
            name = "${homeTeam} - ${AwayTeam}",
            url = url,
            dataUrl = channels.toJson(),
            apiName = name,
            backgroundPosterUrl = posterUrl,
//            plot = " $status <br/> $chList"
            plot = " $status <br/>"
        )
    }

    private suspend fun getVideoLink(channel:Channel) : ExtractorLink? {
        for (extractor in extractors){
            Log.d("TotalSportekLoadLink", "Loading $channel, $extractor")

            if (channel.url.contains(extractor.baseUrl))
                return extractor.loadVideoLink(channel)
        }
        return Generic().loadVideoLink(channel)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val links = parseJson<List<Channel>>(data)
        Log.d("TotalSportekData", "Loading links $links")

        links.amap {
            getVideoLink(it)
        }.forEach{url ->
            if (url != null) {
                callback(url)
            }
        }
        return true
    }

}

val extractors = listOf<Extractor>()

val finalExtractors = listOf<FinalExtractor>(
    ProcessBigger(),
    Googlapisapi(),
    Givemereddit(),
    Vivosoccer(),
    Techabal(),
    Redditf(),
    Forgepattern(),
    CookieWebPlay(),
    Nativesurge(),
    PapaPlay(),
//    Gameavenue(),
    Freelivestreamhd(),
//    Flstvonline(),
    Livestreams(),
    Kingstreamz(),
    Techydeals(),
    Foreverquote(),
    Dynamicfantasy(),
//    Livesnow(),
    Newembedplay(),
//    Sportsstreamlives()

)

class Newembedplay:  SimpleSourceExtractor(){
    override val baseUrl = "newembedplay.xyz"
}

class Foreverquote:  SimpleSourceExtractor(){
    override val baseUrl = "foreverquote.xyz"
}

class Kingstreamz:  SimpleSourceExtractor(){
    override val baseUrl = "kingstreamz.site"
}

class Livestreams:  SimpleSourceExtractor(){
    override val baseUrl = "livestreams.sbs"
}

class PapaPlay: SimpleAtobExtractor(){
    override val baseUrl = "live.papahd-player.click"
}

class CookieWebPlay: SimpleSourceExtractor() {
    override val baseUrl = "cookiewebplay.xyz"
}

class Dynamicfantasy(): Forgepattern() {
    override val baseUrl= "dynamicfantasy.net"
}

open class Forgepattern: FinalExtractorImpl() {
    override val baseUrl = "forgepattern.net"

    override suspend fun getExtractorLink(iFrameData:WebData): VideoLink? {

        val script = iFrameData.document.select("script:containsData(p,a,c,k,e)").find{ it.html().contains("hls")}?:return null
        val unpacked = JsUnpacker(script.data()).unpack()?:""
        val videoUrl = """src\s*=\s*[\"'](.*?)[\"']""".toRegex().find(unpacked)?.groupValues?.get(1)?:return null


        return VideoLink(
            videoUrl,
            "${baseUrl}",
            iFrameData.finalUrl,
            iFrameData.finalUrl
        )

    }
}

class Nativesurge : SimpleAtobExtractor(){
    override val baseUrl = "nativesurge.pro"
}

class Redditf: SimpleAtobExtractor() {
    override val baseUrl = "redditf"

    override suspend fun getExtractorLink(iFrameData:WebData): VideoLink? {

        val videoUrl = VideoLinkParser.findUrlAtob(iFrameData.document.data()) ?: return null

        return VideoLink(
            videoUrl,
            baseUrl,
            iFrameData.finalUrl,
            iFrameData.finalUrl
        )

    }
}

class Techabal: FinalExtractorImpl() {
    override val baseUrl = "techcabal.net"

    override suspend fun getExtractorLink(iFrameData:WebData): VideoLink? {
        val data = iFrameData.document.data()
        val urls = """servs.*?=.*?\[(.*?)\]""".toRegex(RegexOption.DOT_MATCHES_ALL).find(data)?.groupValues?.get(1) ?: return null
        val finalUrls = urls.split(",").map{it.trim().replace("\"", "").replace("'", "")}
        var baseUrl = """source:.*?'(.*)'""".toRegex().find(data)?.groupValues?.get(1)?:return null

        val videoUrl = baseUrl.replace(Regex("""'.*'"""), finalUrls.get(0))

        return VideoLink(
            videoUrl,
            "${this.baseUrl}",
            iFrameData.finalUrl,
            iFrameData.finalUrl
        )

    }
}

class Vivosoccer: SimpleSourceExtractor() {
    override val baseUrl = "vivosoccer.xyz"
}

class Sportsstreamlives: SimpleSourceExtractor(){
    override val baseUrl = "sportsstreamlives.net"
}

class Givemereddit: FinalExtractorImpl() {
    override val baseUrl = "givemereddit.eu"

    override suspend fun getExtractorLink(iFrameData:WebData): VideoLink? {

        val script = iFrameData.document.selectFirst("script:containsData(h,u,n,t,e,r)")?.data()?:return null
        val dehuntedScript = JsHunter(script).dehunt()?:return null
        Log.d("TotalSportekVideoReddit", "Loading script $dehuntedScript")

        val videoUrl = VideoLinkParser.findUrlDirectSource(dehuntedScript)?: return null

        return VideoLink(
            videoUrl,
            "${baseUrl}",
            iFrameData.finalUrl,
            iFrameData.finalUrl
        )

    }
}

class Googlapisapi: SimpleAtobExtractor() {
    override val baseUrl = "googlapisapi.com"
}

class Freelivestreamhd : ProcessBigger(){
    override val baseUrl = "freelivestreamhd.com"
}

class Techydeals: FinalExtractorImpl() {
    override val baseUrl = "techydeals.online"

    override suspend fun getExtractorLink(iFrameData:WebData): VideoLink? {

        val script = iFrameData.document.selectFirst("script:containsData(fid)")?:return null
        val fid = """fid\s*=\s*[\"'](.*?)[\"']""".toRegex().find(script.data())?.groupValues?.get(1)?: return null
        val embedUrl = "https://vodkapr3mium.com/embed.php?player=desktop&live=${fid}"

        headers.put("Referer", iFrameData.finalUrl)
        headers.put("Origin", iFrameData.finalUrl)
        val dataa = app.get(embedUrl, headers=headers)

        val videoUrl = VideoLinkParser.findUrlFunction(dataa.document.data()) ?: return null

        return VideoLink(
            videoUrl,
            "${baseUrl}",
            "https://vodkapr3mium.com/",
            "https://vodkapr3mium.com"
        )

    }
}

open class ProcessBigger: FinalExtractorImpl() {
    override val baseUrl = "processbigger.com"

    override suspend fun getExtractorLink(iFrameData:WebData): VideoLink? {

        val script = iFrameData.document.selectFirst("script:containsData(fid)")?:return null
        val fid = """fid\s*=\s*[\"'](.*?)[\"']""".toRegex().find(script.data())?.groupValues?.get(1)?: return null
        val embedUrl = "https://processbigger.com/maestrohd2.php?player=desktop&live=${fid}"

        headers.put("Referer", iFrameData.finalUrl)
        headers.put("Origin", iFrameData.finalUrl)
        val dataa = app.get(embedUrl, headers=headers)

        val videoUrl = VideoLinkParser.findUrlFunction(dataa.document.data()) ?: return null

        return VideoLink(
            videoUrl,
            "${baseUrl}",
            "https://processbigger.com/",
            "https://processbigger.com"
        )

    }
}

abstract class SimpleAtobExtractor: FinalExtractorImpl() {
    override val baseUrl = "sample"

    override suspend fun getExtractorLink(iFrameData:WebData): VideoLink? {
        val videoUrl = VideoLinkParser.findUrlAtob(iFrameData.document.data()) ?: return null

        return VideoLink(
            videoUrl,
            "${baseUrl}",
            iFrameData.finalUrl,
            iFrameData.finalUrl
        )

    }
}

abstract class SimpleSourceExtractor: FinalExtractorImpl() {
    override val baseUrl = "sample"

    override suspend fun getExtractorLink(iFrameData:WebData): VideoLink? {

        val videoUrl = VideoLinkParser.findUrlDirectSource(iFrameData.document.data()) ?: return null

        return VideoLink(
            videoUrl,
            "${baseUrl}",
            iFrameData.finalUrl,
            iFrameData.finalUrl
        )

    }
}

abstract class FinalExtractorImpl : FinalExtractor {
    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36"
    override val headers = mutableMapOf("user-agent" to userAgent)
}

interface FinalExtractor {
    val baseUrl: String
    val headers: Map<String,String>

    suspend fun getExtractorLink(iFrameData:WebData) : VideoLink?
}

class Generic: ExtractorImpl() {
    override val name = "generic"
    override val baseUrl = "generic"

    override suspend fun loadVideoLink(channel: Channel): ExtractorLink? {
        Log.d("TotalSportekPapa", "Loading videolink from ${channel.url}")

        val iframe =try {
            loadNestedIFrames(url = channel.url)
        }
        catch (ex: Exception){
            return null
        }

        val videoLink = getExtractorVideoLink(iframe)?: return null

        val referer = if (videoLink.referrer != null) {
            val uri = URI(videoLink.referrer)
            "${uri.scheme}://${uri.host}/"
        } else null

        val headers = videoLink.getHeaders()

        @Suppress("DEPRECATION_ERROR")
        val elink =  ExtractorLink(
            channel.source,
            "${channel.name} (${channel.language}) ${channel.reputation} - ${videoLink.parentUrl}",
            videoLink.url,
            referer?:"",
            0,
            isM3u8 = true,
            headers = headers
        )
        Log.d("TotalSportekPapa", "Loaded videolink from ${channel.url} => ${elink} & ${headers}")

        return elink
    }
}

data class WebData(
    val document: Document,
    val lastReferer:String?,
    val finalUrl:String,
)

abstract class ExtractorImpl : Extractor{
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36"
    override val headers = mutableMapOf("user-agent" to userAgent)

    fun findIFrameUrl(parentUrl: String, source: Document) : String? {
        val iframeUrl = source.selectFirst("iframe")?.attr("src") ?: return null

        return if(iframeUrl.startsWith("http")){
            iframeUrl
        }
        else if (iframeUrl.startsWith("//")) {
            return "https:$iframeUrl"
        }
        else if (iframeUrl.startsWith("../")) {
            val url = URI(parentUrl)
            val path = url.path
            val pathParts = path.split("/").filter { it.isNotEmpty() }

            val newUrl = if (pathParts.size > 2) {
                val newPath = pathParts.dropLast(2).joinToString("/")
                url.resolve("/$newPath").toString() + "/"
            } else if (pathParts.size > 1){
                val newPath = pathParts.dropLast(2).joinToString("/")
                url.resolve("/$newPath").toString()
            } else {
                url.scheme + "://" + url.host + "/"
            }

            return newUrl + iframeUrl.replace("../", "")
        }
        else if (iframeUrl.startsWith("/")) {
            val url = URL(parentUrl)
            val baseUrl = "${url.protocol}://${url.host}${if (url.port != -1) ":${url.port}" else ""}"
            return "$baseUrl$iframeUrl"
        }
        else null
    }


    suspend fun loadNestedIFrames(url:String, parentUrl: String? = null) : WebData {
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36"
        val headers = mutableMapOf("user-agent" to userAgent)
        if (parentUrl != null) {
            headers.put("referer", parentUrl)
            headers.put("origin", parentUrl)
        }
        val document = app.get(url, headers).document

        val iframeUrl = findIFrameUrl(url, document)
        if (iframeUrl == null) {
            return WebData(document, parentUrl, finalUrl = url)
        }
        else {
            return loadNestedIFrames(iframeUrl,url)
        }

    }

    suspend fun getExtractorVideoLink(iFrameData:WebData) : VideoLink?{
        for (extractor in finalExtractors){
            if (iFrameData.finalUrl.contains(extractor.baseUrl)){
                try {
                    return extractor.getExtractorLink(iFrameData)
                } catch (ex: Exception){
                    Log.e("TotalSportekProvider", "Failed to load data for $iFrameData" )
                }

            }
        }
        return null
    }
}

interface Extractor{
    val name: String
    val baseUrl: String

    val headers: Map<String, String>

    suspend fun loadVideoLink(channel: Channel) : ExtractorLink?
}


data class VideoLink(
    val url: String,
    val parentUrl: String,
    val referrer: String? = null,
    val origin: String? = null
)

fun VideoLink.getHeaders(): Map<String,String>{
    val headers = mutableMapOf<String,String>()
    headers.put("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36" )
    val uri = URI(this.origin)
    val strippedOrigin = "${uri.scheme}://${uri.host}"
    if (origin != null) {
        headers.put("origin", strippedOrigin)
    }
    return headers
}

object VideoLinkParser {
    //player.load({source: window.atob('sdasd')});
    private val sourceAtob = """source:.*atob\([\"'](.*?)[\"']""".toRegex()

    //player.load({source: 'src'});
    private val sourcePlain = """(?:source|src|file)\s*?:\s*?[\"'](.*)[\"']""".toRegex()


    fun findUrlAtob(source:String) : String? {
        val matchResult = sourceAtob.find(source)
//        Log.d("TotalSportekPapa", "mmmm=> $matchResult => $source")
        if (matchResult !=null) {
            val videoUrl = base64Decode(matchResult.groupValues[1])
            return httpsify(videoUrl)
        }
        else return null
    }

    fun findUrlDirectSource(source:String) : String? {
        val matchResult = sourcePlain.find(source)
        if (matchResult !=null) {
            val videoUrl = matchResult.groupValues[1]
            return httpsify(videoUrl)
        }
        else return null
    }

    fun findUrlFunction(source: String): String?{
        val regex = """source:\s+([A-Za-z0-9]+\(\)),""".toRegex()
        val matchResult = regex.find(source)

        val functionName = matchResult?.groupValues?.get(1)?.dropLast(2)
        val functionDefinitionRegex = Regex("""function $functionName.*return\((\[.*?\])""",RegexOption.DOT_MATCHES_ALL)
        val f = functionDefinitionRegex.find(source)
        val linkData = f?.groupValues?.get(1) ?: return null

        val chars: List<String> = Gson().fromJson(linkData, Array<String>::class.java).toList()
        val videoUrl = chars.joinToString("")
        return httpsify(videoUrl)
    }
}
