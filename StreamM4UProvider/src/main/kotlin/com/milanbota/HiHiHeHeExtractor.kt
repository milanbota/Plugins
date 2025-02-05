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
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities


open class HiHiHeHeExtractor : ExtractorApi() {
    override val name = "Hihihehe"
    override val mainUrl = "https://hihihehe01.xyz"
    override val requiresReferer = false
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
        val script = document.selectFirst("script:containsData(p,a,c,k,e,d)")?.data() ?: return
        val packedData = Regex("""eval\(function\(p,a,c,k,e,.*\)\)""").find(script)?.value
        val packedText =  JsUnpacker(packedData).unpack()
        val newm3u8link = packedText?.substringAfter("file:\"")?.substringBefore("\"")


        Log.d("FlowzHiHeExt", "videoUrl => ${newm3u8link}")

        if (newm3u8link != null) {
            callback(ExtractorLink(
                name,
                name,
                newm3u8link,
                referer?:"",
                Qualities.Unknown.value,
                isM3u8 = true))
        }

    }

}