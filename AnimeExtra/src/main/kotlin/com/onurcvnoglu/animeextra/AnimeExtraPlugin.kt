package com.onurcvnoglu.animeextra

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

import com.onurcvnoglu.animeextra.animecix.*
import com.onurcvnoglu.animeextra.asyaanimeleri.*
import com.onurcvnoglu.animeextra.cizgimax.*
import com.onurcvnoglu.animeextra.tranimaci.*
import com.onurcvnoglu.animeextra.turkanime.*

@CloudstreamPlugin
class AnimeExtraPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AnimeciX())
        registerExtractorAPI(TauVideo())
        registerMainAPI(AsyaAnimeleri())
        registerMainAPI(CizgiMax())
        registerExtractorAPI(SibNet())
        registerExtractorAPI(CizgiDuo())
        registerExtractorAPI(CizgiPass())
        registerExtractorAPI(Drive())
        registerMainAPI(TRanimaci())
        registerMainAPI(TurkAnime())
    }
}
