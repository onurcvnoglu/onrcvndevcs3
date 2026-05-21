package com.onurcvnoglu.asyaextra

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

import com.onurcvnoglu.asyaextra.dizikorea.*
import com.onurcvnoglu.asyaextra.koreanturk.*
import com.onurcvnoglu.asyaextra.trasyalog.*

@CloudstreamPlugin
class AsyaExtraPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DiziKorea())
        registerExtractorAPI(VideoSeyred())
        registerExtractorAPI(PlayerKorea())
        registerMainAPI(KoreanTurk())
        registerMainAPI(TRasyalog())
    }
}
