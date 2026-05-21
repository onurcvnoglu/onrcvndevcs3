package com.onurcvnoglu.diziextra

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

import com.onurcvnoglu.diziextra.ddizi.*
import com.onurcvnoglu.diziextra.dizibox.*
import com.onurcvnoglu.diziextra.dizimom.*
import com.onurcvnoglu.diziextra.dizipal.*
import com.onurcvnoglu.diziextra.dizipaloriginal.*
import com.onurcvnoglu.diziextra.diziyou.*
import com.onurcvnoglu.diziextra.dizilla.*
import com.onurcvnoglu.diziextra.sezonlukdizi.*
import com.onurcvnoglu.diziextra.tlctr.*

@CloudstreamPlugin
class DiziExtraPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DDizi())
        registerMainAPI(DiziBox())
        registerMainAPI(DiziMom())
        registerExtractorAPI(HDMomPlayer())
        registerExtractorAPI(HDPlayerSystem())
        registerExtractorAPI(VideoSeyred())
        registerExtractorAPI(PeaceMakerst())
        registerExtractorAPI(HDStreamAble())
        registerMainAPI(DiziPal())
        registerExtractorAPI(DizipalPlayer())
        registerMainAPI(DiziPalOriginal())
        registerMainAPI(DiziYou())
        registerMainAPI(Dizilla())
        registerExtractorAPI(ContentX())
        registerExtractorAPI(Hotlinger())
        registerExtractorAPI(FourCX())
        registerExtractorAPI(PlayRu())
        registerExtractorAPI(FourPlayRu())
        registerExtractorAPI(FourPichive())
        registerExtractorAPI(Pichive())
        registerExtractorAPI(SNplayer())
        registerMainAPI(SezonlukDizi())
        registerMainAPI(Tlctr())
    }
}
