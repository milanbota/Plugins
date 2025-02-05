package com.milanbota

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import java.time.LocalDateTime


open class HiHaExtractor : ExtractorApi() {
    override val name = "Hihihaha"
    override val mainUrl = "https://hihihaha1.xyz"
    override val requiresReferer = false
    val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36"

    private val gson = Gson()


    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url, referer = referer, headers = mapOf("user-agent" to userAgent)).document
        val script = document.selectFirst("script:containsData(SoTrym)")?.data() ?: return
        val encodedData = Regex("""atob\([\"'](.*?)[\"']""").find(script)?.groupValues?.get(1)?:return
        val data = decodeBase64(encodedData)
        val videoData = gson.fromJson(data, VideoData::class.java)

        val videoSources = videoData.getSources()
        Log.d("FlowzHiHeExt", "videoUrls => ${videoSources}")

        videoSources.amap {
            callback(ExtractorLink(
                "aaa",
                "${mainUrl}/z",
                it,
                "https://hihihaha1.xyz/",
                Qualities.Unknown.value,
                INFER_TYPE
            ))
        }

//        if (newm3u8link != null) {
//            callback(ExtractorLink(
//                name,
//                name,
//                newm3u8link,
//                referer?:"",
//                Qualities.Unknown.value,
//                isM3u8 = true))
//        }

    }

}

fun decodeBase64(input: String): String {
    // Clean the input by removing any non-Base64 characters
    val cleanedInput = input.replace(Regex("[^A-Za-z0-9+/=]"), "")

    // Base64 index table
    val base64Chars = "RB0fpH8ZEyVLkv7c2i6MAJ5u3IKFDxlS1NTsnGaqmXYdUrtzjwObCgQP94hoeW+/="

    val output = StringBuilder()
    var i = 0

    // Decode Base64 input manually
    while (i < cleanedInput.length) {
        val firstChar = base64Chars.indexOf(cleanedInput[i++])
        val secondChar = base64Chars.indexOf(cleanedInput[i++])
        val thirdChar = base64Chars.indexOf(cleanedInput[i++])
        val fourthChar = base64Chars.indexOf(cleanedInput[i++])

        val byte1 = (firstChar shl 2) or (secondChar shr 4)
        val byte2 = ((secondChar and 0xF) shl 4) or (thirdChar shr 2)
        val byte3 = ((thirdChar and 0x3) shl 6) or fourthChar

        output.append(byte1.toChar())

        if (thirdChar != 64) {
            output.append(byte2.toChar())
        }

        if (fourthChar != 64) {
            output.append(byte3.toChar())
        }
    }

    // Convert the final output from byte representation to a UTF-8 string
    return output.toString()
}


data class VideoData (

    @SerializedName("width"                     ) var width                     : String?            = null,
    @SerializedName("height"                    ) var height                    : String?            = null,
    @SerializedName("preload"                   ) var preload                   : String?            = null,
    @SerializedName("doNotSaveCookies"          ) var doNotSaveCookies          : Boolean?           = null,
    @SerializedName("fullscreenOrientationLock" ) var fullscreenOrientationLock : String?            = null,
    @SerializedName("pipIcon"                   ) var pipIcon                   : String?            = null,
    @SerializedName("sources"                   ) var sources                   : ArrayList<Sources> = arrayListOf(),
    @SerializedName("id"                        ) var id                        : String?            = null,
    @SerializedName("slug"                      ) var slug                      : String?            = null,
    @SerializedName("md5_id"                    ) var md5Id                     : Int?               = null,
    @SerializedName("user_id"                   ) var userId                    : Int?               = null,
    @SerializedName("domain"                    ) var domain                    : String?            = null,
    @SerializedName("ads"                       ) var ads                       : Ads?               = Ads(),
    @SerializedName("image"                     ) var image                     : String?            = null,
    @SerializedName("preview"                   ) var preview                   : Boolean?           = null

)

data class Sources (

    @SerializedName("label"  ) var label  : String?  = null,
    @SerializedName("res_id" ) var resId  : Int?     = null,
    @SerializedName("size"   ) var size   : Int?     = null,
    @SerializedName("codec"  ) var codec  : String?  = null,
    @SerializedName("status" ) var status : Boolean? = null,
    @SerializedName("type"   ) var type   : String?  = null

)

data class Ads (
    @SerializedName("pop" ) var pop : ArrayList<String> = arrayListOf()

)

@RequiresApi(Build.VERSION_CODES.O)
fun VideoData.getSources() : List<String>{
    val randomString = Math.random().let { it.toString().substring(2).toLongOrNull()?.toString(36) ?: "" }
    val currentDateTime = System.currentTimeMillis()

    return this.sources.map {
        "https://storage.googleapis.com/mediastorage/${currentDateTime}/${randomString}/${it.size}.mp4#${it.type}/${it.size}/${it.label}"

    }

}

