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
        // Seçili sağlayıcıları doğrudan Cloudstream'e kaydediyoruz (SDK başlatma gereksinimi)
        registerMainAPI(Dizilla())
        registerMainAPI(DiziPalOriginal())
        registerMainAPI(SezonlukDizi())
        registerExtractorAPI(HDMomPlayer())
        registerExtractorAPI(HDPlayerSystem())
        registerExtractorAPI(VideoSeyred())
        registerExtractorAPI(PeaceMakerst())
        registerExtractorAPI(HDStreamAble())
        registerExtractorAPI(DizipalPlayer())
        registerExtractorAPI(ContentX())
        registerExtractorAPI(Hotlinger())
        registerExtractorAPI(FourCX())
        registerExtractorAPI(PlayRu())
        registerExtractorAPI(FourPlayRu())
        registerExtractorAPI(FourPichive())
        registerExtractorAPI(Pichive())
        registerExtractorAPI(SNplayer())
    }
}
