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
        registerMainAPI(AltiYuzAltmisAltiFilmIzle())
        registerMainAPI(BelgeselX())
        registerExtractorAPI(com.onurcvnoglu.filmextra.belgeselx.Odnoklassniki())
        registerMainAPI(CanliTV())
        registerMainAPI(FilmBip())
        registerExtractorAPI(HDPlayerSystem())
        registerMainAPI(FilmMakinesi())
        registerExtractorAPI(CloseLoad())
        registerMainAPI(FilmModu())
        registerMainAPI(FullHDFilm())
        registerExtractorAPI(com.onurcvnoglu.filmextra.fullhdfilm.YildizKisaFilm())
        registerMainAPI(FullHDFilmizlesene())
        registerExtractorAPI(RapidVid())
        registerExtractorAPI(TRsTX())
        registerExtractorAPI(VidMoxy())
        registerExtractorAPI(Sobreatsesuyp())
        registerExtractorAPI(TurboImgz())
        registerExtractorAPI(TurkeyPlayer())
        registerMainAPI(HDFilmCehennemi())
        registerMainAPI(InatBox())
        registerExtractorAPI(DiskYandexComTr())
        registerExtractorAPI(Vk())
        registerExtractorAPI(com.onurcvnoglu.filmextra.inatbox.DzenRu())
        registerExtractorAPI(CDNJWPlayer())
        registerMainAPI(JetFilmizle())
        registerExtractorAPI(PixelDrain())
        registerMainAPI(KultFilmler())
        registerExtractorAPI(com.onurcvnoglu.filmextra.kultfilmler.YildizKisaFilm())
        registerMainAPI(RareFilmm())
        registerExtractorAPI(com.onurcvnoglu.filmextra.rarefilmm.Odnoklassniki())
        registerExtractorAPI(OkRuSSL())
        registerExtractorAPI(OkRuHTTP())
        registerMainAPI(RecTV())
        registerMainAPI(SetFilmIzle())
        registerExtractorAPI(SetPlay())
        registerExtractorAPI(FastPlay())
        registerMainAPI(SinemaCX())
        registerMainAPI(Sinewix())
        registerMainAPI(UgurFilm())
        registerExtractorAPI(MailRu())
        registerExtractorAPI(com.onurcvnoglu.filmextra.ugurfilm.Odnoklassniki())
        registerMainAPI(Watch2Movies())
        registerExtractorAPI(W2MExtractor("https://hanatyury.online/", context))
        registerExtractorAPI(W2MExtractor("https://pepepeyo.xyz/",     context))
        registerExtractorAPI(W2MExtractor("https://zizicoi.online/",   context))
        registerExtractorAPI(W2MExtractor("https://watch2movies.net/", context))
        registerMainAPI(WebteIzle())
        registerExtractorAPI(com.onurcvnoglu.filmextra.webteizle.DzenRu())
    }
}
