package com.milanbota

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.APIHolder
import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

@CloudstreamPlugin
class StreamM4UPlugin: Plugin() {
    var activity: AppCompatActivity? = null

    override fun load(context: Context) {
        activity = context as AppCompatActivity
        // All providers should be added in this manner
        registerMainAPI(StreamM4UProvider())
        registerExtractorAPI(PlayM4UExtractor())
        registerExtractorAPI(PlayM4UExtractorF())
        registerExtractorAPI(HiHiHeHeExtractor())
    }
}