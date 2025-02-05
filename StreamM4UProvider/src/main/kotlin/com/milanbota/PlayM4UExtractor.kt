package com.milanbota

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities


open class PlayM4UExtractorF : PlayM4UExtractor(){
    override val name = "PlayM4UF"
    override val mainUrl = "https://play.playm4u.xyz"
    override val referrerDomain = "https://my.playhq.net"

}

open class PlayM4UExtractor : ExtractorApi() {
    override val name = "PlayM4UF"
    override val mainUrl = "https://play9str.playm4u.xyz"
    open val referrerDomain = "https://my.9stream.net"
    override val requiresReferer = true
    val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36"



    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url, referer = referer, headers = mapOf("user-agent" to userAgent)).document
        val script = document.selectFirst("script:containsData(idfile_enc =)")?.data() ?: return

        val idfile_enc = "idfile_enc".findIn(script)
        val idUser_enc = "idUser_enc".findIn(script)
        val DOMAIN_API = "DOMAIN_API".findIn(script)
        val DOMAIN_API_VIEW = "DOMAIN_API_VIEW".findIn(script)
        val VerLoad = "VerLoad".findIn(script)
        val DEV_PLAY = "DEV_PLAY".findIn(script)

        val decryptedFileId = Crypto.decrypt(idfile_enc, "jcLycoRJT6OWjoWspgLMOZwS3aSS0lEn");
        val decryptedUserId = Crypto.decrypt(idUser_enc, "PZZ3J3LDbLT0GY7qSA5wW5vchqgpO36O");
        // send get to the following
//        val url = "$DOMAIN_API_VIEW$decryptedFileId"

        val platformVersion = if (VerLoad == "") {
            "noplf"
        } else {
            VerLoad
        }

        val playerData = V4RequestData(
            decryptedFileId,
            decryptedUserId,
            referrerDomain,
            platformVersion,
            true,
        )

        val objectMapper = jacksonObjectMapper()
        val jsonString = objectMapper.writeValueAsString(playerData)

        val encryptedPlayerData = Crypto.encrypt("vlVbUQhkOhoSfyteyzGeeDzU0BHoeTyZ", jsonString);
        Log.d("Extractorz", "encryptedPlayerData => ${encryptedPlayerData.toString()}")

        val checksum = Crypto.generateChecksum(encryptedPlayerData)
        val jsonData = "$encryptedPlayerData|$checksum"

        val videoUrl = "$DOMAIN_API/playiframe"
        val source = app.post(videoUrl, data = mapOf("data" to jsonData), headers = mapOf("user-agent" to userAgent)).parsedSafe<V4Response>()

        if(source?.status == 1){
            val video = Crypto.decrypt(source.data, "oJwmvmVBajMaRCTklxbfjavpQO7SZpsL")
            Log.d("FlowzM4U", "videoUrl => ${video}")

            M3u8Helper.generateM3u8(this.name, video, "$mainUrl/").forEach(callback)

//            callback(
//                ExtractorLink(
//                    this.name,
//                    this.name,
//                    video,
//                    "$mainUrl/",
//                    Qualities.Unknown.value,
//                    INFER_TYPE
//                )
//            )

        }




    }

    private fun String.findIn(data: String): String {
        return "$this\\s*=\\s*[\"'](\\S+)[\"'];".toRegex().find(data)?.groupValues?.get(1) ?: ""
    }
}

data class V4Response(
    val status: Int,
    val type: String,
    val data: String,
    val cache: Boolean,
    val checkhls: Boolean,
)

data class V4RequestData(
    val idfile: String,
    val iduser: String,
    val domain_play: String,
    val platform: String,
    val hlsSupport: Boolean,
    val jwplayer: Map<Any, Any> = mapOf(
        "Browser" to mapOf(
            "androidNative" to false,
            "chrome" to true,
            "edge" to false,
            "facebook" to false,
            "firefox" to false,
            "ie" to false,
            "msie" to false,
            "safari" to false,
            "version" to mapOf(
                "version" to "129.0.0.0",
                "major" to 129,
                "minor" to 0
            )
        ),
        "OS" to mapOf(
            "android" to false,
            "iOS" to false,
            "mobile" to false,
            "mac" to true,
            "iPad" to false,
            "iPhone" to false,
            "windows" to false,
            "tizen" to false,
            "tizenApp" to false,
            "version" to mapOf(
                "version" to "10_15_7",
                "major" to 10,
                "minor" to 15
            )

        ),
        "Features" to mapOf(
            "iframe" to false,
            "passiveEvents" to true,
            "backgroundLoading" to true
        )
    )
)