package com.milanbota

import android.util.Log
import com.fasterxml.jackson.annotation.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lagradost.cloudstream3.extractors.YoutubeExtractor
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.cookies
import org.jsoup.nodes.Document
import java.net.URL

data class Event(
    val id: String,
    val sport: String,
    val date: String,
    val match: String,
    val competition: String,
    val country: String,
    val channels : List<String>
)


data class EventResponse(
    val id: String,
    @JsonProperty("id_sport") val idSport: Long,
    val sport: String,
    val date: String,
    val match: String,
    val competition: String,
    val country: String,
)

data class ChannelResponse(
    val type: String,
    val link: String,
    @JsonProperty("id_web")
    val idWeb: Any?,
)



class JokerStreamProvider : MainAPI() {
    override var lang = "en"
    override var mainUrl = "https://widget.streamsthunder.tv/"
    override var name = "JokerStream"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(
        TvType.Live,
    )
    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36"
    private val headers = mapOf("user-agent" to userAgent)

    override val mainPage = mainPageOf(
        "$mainUrl/list.php" to "Sports",
    )

    private fun Event.toSearchResponse() : SearchResponse{
        return LiveSearchResponse(
            "${this.date.split(" ")[1].dropLast(3)} - ${this.match}",
            url = "$mainUrl${this.id}",
            apiName = "Jokerstreamz",
            type = TvType.Live,
            posterUrl = "https://streamsthunder.tv/img/countries/${this.country}",
            id = this.id.toInt()
        )
    }

    private suspend fun loadMatches(): List<Event>{
        val document = app.get("$mainUrl/list.php", headers=headers).document

        val scriptText = document.selectFirst("div#accordion")?.data() ?: ""

        val channelsRegex= """chan_arr\s+=\s+(\{.*\});""".toRegex()
        val matchesRegex= """ev_arr\s+=\s+(\[.*\]);""".toRegex()

        val matchResult = matchesRegex.find(scriptText)
        val channelsResult = channelsRegex.find(scriptText)

        val gson = Gson()

        val allChannels = if (channelsResult != null) {
            val channels = channelsResult.groupValues.get(1)
            val channelsType = object : TypeToken<Map<String, List<ChannelResponse>>>() {}.type
            val channelsList: Map<String,List<ChannelResponse>> = gson.fromJson(channels, channelsType)
            channelsList
        }else{
            emptyMap()
        }

        if (matchResult != null) {
            val matches = matchResult.groupValues.get(1)
            val personListType = object : TypeToken<List<EventResponse>>() {}.type
            val eventsList: List<EventResponse> = gson.fromJson(matches, personListType)
            val events: List<Event> = eventsList.map {
                val channels = allChannels.get(it.id)?.map { httpsify(it.link.replace("\n", "").replace("\r", "")) }
                if (!channels.isNullOrEmpty()) {
                    return@map Event(
                        it.id,
                        it.sport,
                        it.date,
                        it.match,
                        it.competition,
                        it.country,
                        channels
                    )
                } else {
                    return@map null
                }
            }.filterNotNull()
            return events
        }
        else return emptyList()

    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val events = loadMatches()

        val footballMatches = events.filter { it.sport == "Football" }.sortedBy { it.date }.map { it.toSearchResponse() }
        val basketballMatches = events.filter { it.sport == "Basketball" }.sortedBy { it.date }.map { it.toSearchResponse() }
        val volleyballMatches = events.filter { it.sport == "Volleyball" }.sortedBy { it.date }.map { it.toSearchResponse() }
        val tennisMatches = events.filter { it.sport == "Tennis" }.sortedBy { it.date }.map { it.toSearchResponse() }

        val football = HomePageList("Football", footballMatches, isHorizontalImages = true)
        val basketball = HomePageList("Basketball", basketballMatches)
        val volleyball = HomePageList("Volleyball", volleyballMatches)
        val tennis = HomePageList("Tennis", tennisMatches)


        return newHomePageResponse(listOf(football,basketball,volleyball, tennis), hasNext = false)

    }



    override suspend fun search(query: String): List<SearchResponse> {
        val matches = loadMatches()
        return matches.filter { it.match.lowercase().contains(query.lowercase()) }
            .map { it.toSearchResponse() }
    }



    override suspend fun load(url: String): LoadResponse {
        val id = url.split("/").lastOrNull()
        val match = loadMatches().find { it.id == id }
        Log.d("JokerStreamMFlowz", "Loading ${match?.channels?.size} channels for ${match?.match}")
        val filteredChannelsRegex = Regex(".*(adv.media|acestream).*")
        val filteredChannels = match?.channels?.filterNot {  it.matches(filteredChannelsRegex) } ?: emptyList()
        Log.d("JokerStreamLoadLink", "Loading ${match?.channels?.size} channels $filteredChannels")

        return LiveStreamLoadResponse(
            name = match?.match?: "No match name",
            url = "$mainUrl/${match?.id}",
            dataUrl = filteredChannels.toJson().toString(),
            apiName = name,
            backgroundPosterUrl = "https://streamsthunder.tv/img/countries/${match?.country}",
            plot = filteredChannels.joinToString("<br/>")
        )
    }

    private suspend fun getVideoLink(url:String) :ExtractorLink? {
        for (extractor in extractors){
            if (url.contains(extractor.baseUrl))
                return extractor.loadVideoLink(url)
        }
        return null

    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val links = parseJson<List<String>>(data)
        Log.d("JokerStreamLoadLink", "Loading links $links")

        links.amap {
            getVideoLink(it)
        }.filterNotNull().forEach(callback)

        return true
    }

}

val extractors = listOf(
    AntenaTime(),
    Apl374(),
    Lavents(),
    Onstream(),
    Antenasport(),
    Daddylinve(),
    Topembed(),
    WikiSport(),
    Livestreamhd247()
)

class Apl374 : ExtractorImpl(){
    override val name= "apl374"
    override val baseUrl = "emb.apl374"

    override suspend fun loadVideoLink(url:String): ExtractorLink? {
        Log.d("JokerStreamLoadLink", "Loading videolink from $url")
        val data = app.get(url, headers = headers)

        val regex = """pl.init\('(.*)'\);""".toRegex()
        val matchResult = regex.find(data.document.data())
        if (matchResult !=null){
            val videoUrl = httpsify(matchResult.groupValues[1])
            Log.d("JokerStreamLoadLink", "Found videolink from $url => $videoUrl")

            return ExtractorLink(
                "Joker",
                this.name,
                videoUrl,
                url,
                0,
                isM3u8 = true
            )
        }
        else{
            return null
        }

    }
}

class Topembed : AntenaTime(){
    override val baseUrl = "topembed"
    override val name = "topembed"

}

class Daddylinve : AntenaTime(){
    override val baseUrl = "daddylive1"
    override val name = "daddylive1"
}

class Antenasport : AntenaTime(){
    override val baseUrl = "antenasport"
    override val name = "antenasport"
}

class WikiSport : AntenaTime(){
    override val baseUrl = "wikisport"
    override val name = "wikisport"
}

class Livestreamhd247 : AntenaTime(){
    override val baseUrl = "livestreamhd247"
    override val name = "livestreamhd247"
}

open class AntenaTime : ExtractorImpl(){
    override val name= "antennatime"
    override val baseUrl = "antenatime"

    override suspend fun loadVideoLink(url:String): ExtractorLink? {
        val data = loadNestedIFrames(url)

        val sourcePlainRegex = """source:\s+[\"'](.*)[\"']""".toRegex()
        val videoUrl =sourcePlainRegex.find(data.document.data())?.groupValues?.get(1) ?:return null

        val urlz = URL(data.finalUrl)
        val origin = "${urlz.protocol}://${urlz.host}${if (urlz.port != -1) ":${urlz.port}" else ""}"
        Log.d("JokerLoadLink", "videoUrl => $url $videoUrl ${data.finalUrl} ${data.lastReferer} $origin")


        return ExtractorLink(
            name,
            url,
            videoUrl,
            "$origin/",
            0,
            isM3u8 = true,
            headers = mapOf("origin" to origin, "user-agent" to userAgent)
        )

    }
}

class Lavents: ExtractorImpl() {
    override val name = "lavents"
    override val baseUrl = "lavents"

    override suspend fun loadVideoLink(url: String): ExtractorLink? {
        Log.d("JokerStreamLoadLink", "Loading videolink from $url")
        val data = app.get(url, headers = headers).document
        val iframeUrl = findIFrameUrl(url, data) ?: return null

        val iframe = app.get(iframeUrl, headers = headers).document.data()
        val regex = """source:\s+[\"'](.*)[\"']""".toRegex()
        val matchResult = regex.find(iframe)

        if (matchResult !=null){
            val videoUrl = httpsify(matchResult.groupValues[1])
            Log.d("JokerStreamLoadLink", "Found videolink from $url => $videoUrl")

            return ExtractorLink(
                name,
                url,
                videoUrl,
                "",
                0,
                isM3u8 = true
            )
        }
        else{
            return null
        }
    }

}

class Onstream: ExtractorImpl() {
    override val name = "onstream"
    override val baseUrl = "on-stream"

    override suspend fun loadVideoLink(url: String): ExtractorLink? {
        Log.d("JokerStreamLoadLink", "Loading videolink from $url")
        val document = app.get(url, headers = headers).document
        val iframeUrl = findIFrameUrl(url, document) ?: return null

        val document2 = app.get(iframeUrl, headers = headers).document
        val iframe2Url = findIFrameUrl(iframeUrl, document2) ?: return null

        val document3 = app.get(iframe2Url, headers = headers).document
        val iframe3Url = findIFrameUrl(iframe2Url, document3) ?: return null

        headers.put("referer", iframe2Url)
        headers.put("origin", iframe2Url)
        val iframe = app.get(iframe3Url, headers = headers).document.data()

        val regex = """source:\s+([A-Za-z0-9]+\(\)),""".toRegex()
        val matchResult = regex.find(iframe)

        val functionName = matchResult?.groupValues?.get(1)?.dropLast(2)
        val functionDefinitionRegex = Regex("""function $functionName.*return\((\[.*?\])""",RegexOption.DOT_MATCHES_ALL)
        val f = functionDefinitionRegex.find(iframe)
        val linkData = f?.groupValues?.get(1) ?: return null

        val chars: List<String> = Gson().fromJson(linkData, Array<String>::class.java).toList()
        val videoUrl = chars.joinToString("")
        Log.d("JokerStreamLoadLink", "Found videolink from $url => $videoUrl")

            return ExtractorLink(
                "Joker",
                this.name,
                videoUrl,
                "",
                Qualities.Unknown.value,
                isM3u8 = true
            )
    }

}


data class WebData(
    val document: Document,
    val lastReferer:String?,
    val finalUrl:String,
)

abstract class ExtractorImpl : Extractor{
    val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36"
    override val headers = mutableMapOf("user-agent" to userAgent)

    fun findIFrameUrl(parentUrl: String, source: Document) : String? {
        val iframeUrl = source.selectFirst("iframe")?.attr("src") ?: return null

        return if(iframeUrl.startsWith("http")){
            iframeUrl
        }
        else if (iframeUrl.startsWith("//")) {
            return "https:$iframeUrl"
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
        println("Loading $url, triggered from ${parentUrl?:"none"}")
        val document = app.get(url, headers).document
        val iframeUrl = findIFrameUrl(url, document)
        if (iframeUrl == null) {
            return WebData(document, parentUrl, finalUrl = url)
        }
        else {
            return loadNestedIFrames(iframeUrl,url)
        }

    }
}

interface Extractor{
    val name: String
    val baseUrl: String

    val headers: Map<String, String>

    suspend fun loadVideoLink(url:String) : ExtractorLink?
}


