package com.onurcvnoglu.filmextra

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

import com.onurcvnoglu.filmextra.altiyuzaltmisaltifilmizle.*
import com.onurcvnoglu.filmextra.belgeselx.*
import com.onurcvnoglu.filmextra.canlitv.*
import com.onurcvnoglu.filmextra.filmbip.*
import com.onurcvnoglu.filmextra.filmmakinesi.*
import com.onurcvnoglu.filmextra.filmmodu.*
import com.onurcvnoglu.filmextra.fullhdfilm.*
import com.onurcvnoglu.filmextra.fullhdfilmizlesene.*
import com.onurcvnoglu.filmextra.hdfilmcehennemi.*
import com.onurcvnoglu.filmextra.inatbox.*
import com.onurcvnoglu.filmextra.jetfilmizle.*
import com.onurcvnoglu.filmextra.kultfilmler.*
import com.onurcvnoglu.filmextra.rarefilmm.*
import com.onurcvnoglu.filmextra.rectv.*
import com.onurcvnoglu.filmextra.setfilmizle.*
import com.onurcvnoglu.filmextra.sinemacx.*
import com.onurcvnoglu.filmextra.sinewix.*
import com.onurcvnoglu.filmextra.ugurfilm.*
import com.onurcvnoglu.filmextra.watch2movies.*
import com.onurcvnoglu.filmextra.webteizle.*

@CloudstreamPlugin
class FilmExtraPlugin: Plugin() {
    override fun load(context: Context) {
        // Sadece tek eklenti olarak FilmExtra'yı kaydediyoruz
        registerMainAPI(FilmExtra())
        registerExtractorAPI(com.onurcvnoglu.filmextra.belgeselx.Odnoklassniki())
        registerExtractorAPI(HDPlayerSystem())
        registerExtractorAPI(CloseLoad())
        registerExtractorAPI(com.onurcvnoglu.filmextra.fullhdfilm.YildizKisaFilm())
        registerExtractorAPI(RapidVid())
        registerExtractorAPI(TRsTX())
        registerExtractorAPI(VidMoxy())
        registerExtractorAPI(Sobreatsesuyp())
        registerExtractorAPI(TurboImgz())
        registerExtractorAPI(TurkeyPlayer())
        registerExtractorAPI(DiskYandexComTr())
        registerExtractorAPI(Vk())
        registerExtractorAPI(com.onurcvnoglu.filmextra.inatbox.DzenRu())
        registerExtractorAPI(CDNJWPlayer())
        registerExtractorAPI(PixelDrain())
        registerExtractorAPI(com.onurcvnoglu.filmextra.kultfilmler.YildizKisaFilm())
        registerExtractorAPI(com.onurcvnoglu.filmextra.rarefilmm.Odnoklassniki())
        registerExtractorAPI(OkRuSSL())
        registerExtractorAPI(OkRuHTTP())
        registerExtractorAPI(SetPlay())
        registerExtractorAPI(FastPlay())
        registerExtractorAPI(MailRu())
        registerExtractorAPI(com.onurcvnoglu.filmextra.ugurfilm.Odnoklassniki())
        registerExtractorAPI(W2MExtractor("https://hanatyury.online/", context))
        registerExtractorAPI(W2MExtractor("https://pepepeyo.xyz/",     context))
        registerExtractorAPI(W2MExtractor("https://zizicoi.online/",   context))
        registerExtractorAPI(W2MExtractor("https://watch2movies.net/", context))
        registerExtractorAPI(com.onurcvnoglu.filmextra.webteizle.DzenRu())
    }
}
