package com.milanbota

import android.os.Build
import androidx.annotation.RequiresApi
import com.lagradost.api.Log

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.M3u8Helper2
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor
import java.util.Base64


data class VideoUrl(
    val url: String,
    val referer: String
)

open class VidExtractor : ExtractorApi() {
    override val name = "VidSrcz"
    override val mainUrl = "https://vidsrc.me"
    private val apiUrl = "https://edgedeliverynetwork.com"
    override val requiresReferer = true
    val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36"
    val  headers = mapOf("user-agent" to userAgent)
    private val interceptor = CloudflareKiller()


    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        val iframedoc = app.get(url, headers = headers).document
        Log.d("FlowzVidsrc", "Loading $url")
        val srcrcpList =
            iframedoc.select("div.serversList > div.server").mapNotNull {
                val datahash = it.attr("data-hash") ?: return@mapNotNull null
                val rcpLink = "$apiUrl/rcp/$datahash"
                val rcpRes = app.get(rcpLink, referer = rcpLink).text
//                Log.d("FlowzVidsrc", "Loaded $rcpLink. Page source: $rcpRes")


                val srcrcpLink =
                    Regex("src:\\s*'(.*)',").find(rcpRes)?.destructured?.component1()
                        ?: return@mapNotNull null

                Log.d("FlowzVidsrc", "Parsed src from page source: $srcrcpLink")


                if (srcrcpLink.startsWith("/prorcp")){
                    VideoUrl("$apiUrl$srcrcpLink", rcpLink)
                }else{
                    VideoUrl(httpsify(srcrcpLink) , rcpLink)
                }
            }

        Log.d("FlowzVidsrc", "List of servers:  $srcrcpList")



        srcrcpList.amap { server ->
            val res = app.get(server.url, referer = server.referer)
            if (res.url.contains("/prorcp")) {
                val encodedElement = res.document.select("div#reporting_content+div")
                val decodedUrl =
                    decodeUrl(encodedElement.attr("id"), encodedElement.text()) ?: return@amap

                Log.d("FlowzVidsrc", "invoking => $decodedUrl")

                callback.invoke(
                    @Suppress("DEPRECATION_ERROR")
                    ExtractorLink(
                        this.name,
                        this.name,
                        decodedUrl,
                        server.referer,
                        Qualities.Unknown.value,
                        isM3u8 = true
                    )
                )
            } else {
                loadExtractor(res.url, url, subtitleCallback, callback)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun decodeUrl(encType: String, url: String): String? {
        return when (encType) {
            "NdonQLf1Tzyx7bMG" -> bMGyx71TzQLfdonN(url)
            "sXnL9MQIry" -> Iry9MQXnLs(url)
            "IhWrImMIGL" -> IGLImMhWrI(url)
            "xTyBxQyGTA" -> GTAxQyTyBx(url)
            "ux8qjPHC66" -> C66jPHx8qu(url)
            "eSfH1IRMyL" -> MyL1IRSfHe(url)
            "KJHidj7det" -> detdj7JHiK(url)
            "o2VSUnjnZl" -> nZlUnj2VSo(url)
            "Oi3v1dAlaM" -> laM1dAi3vO(url)
            "TsA2KGDGux" -> GuxKGDsA2T(url)
            "JoAHUMCLXV" -> LXVUMCoAHJ(url)
            else -> null
        }
    }

    private fun bMGyx71TzQLfdonN(a: String): String {
        val b = 3
        val c = mutableListOf<String>()
        var d = 0
        while (d < a.length) {
            c.add(a.substring(d, minOf(d + b, a.length)))
            d += b
        }
        val e = c.reversed().joinToString("")
        return e
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun Iry9MQXnLs(a: String): String {
        val b = "pWB9V)[*4I`nJpp?ozyB~dbr9yt!_n4u"
        val d = a.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
        var c = ""
        for (e in d.indices) {
            c += (d[e].code xor b[e % b.length].code).toChar()
        }
        var e = ""
        for (ch in c) {
            e += (ch.code - 3).toChar()
        }
        return String(Base64.getDecoder().decode(e))
    }

    private fun IGLImMhWrI(a: String): String {
        val b = a.reversed()
        val c =
            b
                .map {
                    when (it) {
                        in 'a'..'m', in 'A'..'M' -> it + 13
                        in 'n'..'z', in 'N'..'Z' -> it - 13
                        else -> it
                    }
                }
                .joinToString("")
        val d = c.reversed()
        return String(Base64.getDecoder().decode(d))
    }

    private fun GTAxQyTyBx(a: String): String {
        val b = a.reversed()
        val c = b.filterIndexed { index, _ -> index % 2 == 0 }
        return String(Base64.getDecoder().decode(c))
    }

    private fun C66jPHx8qu(a: String): String {
        val b = a.reversed()
        val c = "X9a(O;FMV2-7VO5x;Ao:dN1NoFs?j,"
        val d = b.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
        var e = ""
        for (i in d.indices) {
            e += (d[i].code xor c[i % c.length].code).toChar()
        }
        return e
    }

    private fun MyL1IRSfHe(a: String): String {
        val b = a.reversed()
        val c = b.map { (it.code - 1).toChar() }.joinToString("")
        val d = c.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
        return d
    }

    private fun detdj7JHiK(a: String): String {
        val b = a.substring(10, a.length - 16)
        val c = "3SAY~#%Y(V%>5d/Yg\"\$G[Lh1rK4a;7ok"
        val d = String(Base64.getDecoder().decode(b))
        val e = c.repeat((d.length + c.length - 1) / c.length).substring(0, d.length)
        var f = ""
        for (i in d.indices) {
            f += (d[i].code xor e[i].code).toChar()
        }
        return f
    }

    private fun nZlUnj2VSo(a: String): String {
        val b =
            mapOf(
                'x' to 'a',
                'y' to 'b',
                'z' to 'c',
                'a' to 'd',
                'b' to 'e',
                'c' to 'f',
                'd' to 'g',
                'e' to 'h',
                'f' to 'i',
                'g' to 'j',
                'h' to 'k',
                'i' to 'l',
                'j' to 'm',
                'k' to 'n',
                'l' to 'o',
                'm' to 'p',
                'n' to 'q',
                'o' to 'r',
                'p' to 's',
                'q' to 't',
                'r' to 'u',
                's' to 'v',
                't' to 'w',
                'u' to 'x',
                'v' to 'y',
                'w' to 'z',
                'X' to 'A',
                'Y' to 'B',
                'Z' to 'C',
                'A' to 'D',
                'B' to 'E',
                'C' to 'F',
                'D' to 'G',
                'E' to 'H',
                'F' to 'I',
                'G' to 'J',
                'H' to 'K',
                'I' to 'L',
                'J' to 'M',
                'K' to 'N',
                'L' to 'O',
                'M' to 'P',
                'N' to 'Q',
                'O' to 'R',
                'P' to 'S',
                'Q' to 'T',
                'R' to 'U',
                'S' to 'V',
                'T' to 'W',
                'U' to 'X',
                'V' to 'Y',
                'W' to 'Z'
            )
        return a.map { b[it] ?: it }.joinToString("")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun laM1dAi3vO(a: String): String {
        val b = a.reversed()
        val c = b.replace("-", "+").replace("_", "/")
        val d = String(Base64.getDecoder().decode(c))
        var e = ""
        val f = 5
        for (ch in d) {
            e += (ch.code - f).toChar()
        }
        return e
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun GuxKGDsA2T(a: String): String {
        val b = a.reversed()
        val c = b.replace("-", "+").replace("_", "/")
        val d = String(Base64.getDecoder().decode(c))
        var e = ""
        val f = 7
        for (ch in d) {
            e += (ch.code - f).toChar()
        }
        return e
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun LXVUMCoAHJ(a: String): String {
        val b = a.reversed()
        val c = b.replace("-", "+").replace("_", "/")
        val d = String(Base64.getDecoder().decode(c))
        var e = ""
        val f = 3
        for (ch in d) {
            e += (ch.code - f).toChar()
        }
        return e
    }
}