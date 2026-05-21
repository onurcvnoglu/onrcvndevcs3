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
        // Seçili sağlayıcıları doğrudan Cloudstream'e kaydediyoruz (SDK başlatma gereksinimi)
        registerMainAPI(DiziKorea())
        registerMainAPI(KoreanTurk())
        registerMainAPI(TRasyalog())
        registerExtractorAPI(VideoSeyred())
        registerExtractorAPI(PlayerKorea())
    }
}
