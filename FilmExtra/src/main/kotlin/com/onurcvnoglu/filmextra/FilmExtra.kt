package com.onurcvnoglu.filmextra

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

class FilmExtra : MainAPI() {
    override var name = "Film Extra"
    override var mainUrl = "https://filmmodu.org"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = true

    private val providers = listOf(
        AltiYuzAltmisAltiFilmIzle(),
        BelgeselX(),
        CanliTV(),
        FilmBip(),
        FilmMakinesi(),
        FilmModu(),
        FullHDFilm(),
        FullHDFilmizlesene(),
        HDFilmCehennemi(),
        InatBox(),
        JetFilmizle(),
        KultFilmler(),
        RareFilmm(),
        RecTV(),
        SetFilmIzle(),
        SinemaCX(),
        Sinewix(),
        UgurFilm(),
        Watch2Movies(),
        WebteIzle()
    )

    private fun getProviderForUrl(url: String): MainAPI? {
        val cleanUrl = url.lowercase()
        return providers.find { provider ->
            val cleanMain = provider.mainUrl.lowercase().replace("https://", "").replace("http://", "").replace("www.", "")
            val domain = cleanMain.split("/").firstOrNull() ?: ""
            if (domain.isNotEmpty()) {
                val cleanDomain = domain.replace(Regex("\\.[a-z]+$"), "")
                cleanUrl.contains(cleanDomain)
            } else {
                false
            }
        } ?: providers.firstOrNull()
    }

    override val mainPage = providers.flatMap { provider ->
        if (provider.hasMainPage) {
            provider.mainPage.map { page ->
                MainPageData("${provider.name}|${page.data}", "${provider.name} - ${page.name}")
            }
        } else {
            emptyList()
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val parts = request.data.split("|", limit = 2)
        if (parts.size < 2) return null
        val providerName = parts[0]
        val originalData = parts[1]
        
        val provider = providers.find { it.name == providerName } ?: return null
        
        val cleanName = request.name.substringAfter(" - ")
        val newRequest = MainPageRequest(cleanName, originalData, request.horizontalImages)
        
        return try {
            provider.getMainPage(page, newRequest)
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun search(query: String): List<SearchResponse>? = coroutineScope {
        providers.map { provider ->
            async {
                try {
                    provider.search(query)
                } catch (e: Throwable) {
                    null
                }
            }
        }.awaitAll().filterNotNull().flatten()
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = coroutineScope {
        providers.map { provider ->
            async {
                try {
                    provider.quickSearch(query)
                } catch (e: Throwable) {
                    null
                }
            }
        }.awaitAll().filterNotNull().flatten()
    }

    override suspend fun load(url: String): LoadResponse? {
        val provider = getProviderForUrl(url) ?: throw ErrorLoadingException("Sağlayıcı bulunamadı")
        return provider.load(url)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val provider = getProviderForUrl(data) ?: return false
        return provider.loadLinks(data, isCasting, subtitleCallback, callback)
    }
}
