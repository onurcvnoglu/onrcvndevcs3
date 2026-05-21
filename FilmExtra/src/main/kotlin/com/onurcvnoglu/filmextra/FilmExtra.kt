package com.onurcvnoglu.filmextra

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import com.onurcvnoglu.filmextra.filmmakinesi.*
import com.onurcvnoglu.filmextra.filmmodu.*
import com.onurcvnoglu.filmextra.hdfilmcehennemi.*

class FilmExtra : MainAPI() {
    override var name = "Film Extra"
    override var mainUrl = "https://www.filmmodu.one" // Varsayılan url
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = true

    // Sağlayıcı nesnelerini Scraper helper olarak api referansıyla başlatıyoruz
    private val providers = listOf(
        FilmMakinesi(this),
        FilmModu(this),
        HDFilmCehennemi(this)
    )

    // compile-time casting için generic bir bulma
    private fun getAnyProviderForUrl(url: String): Any? {
        val cleanUrl = url.lowercase()
        if (cleanUrl.contains("filmmakinesi")) return providers[0]
        if (cleanUrl.contains("filmmodu")) return providers[1]
        if (cleanUrl.contains("hdfilmcehennemi") || cleanUrl.contains("hdhd")) return providers[2]
        return providers.firstOrNull()
    }

    override val mainPage = providers.flatMap { provider ->
        // Her sağlayıcının mainPage listesini benzersiz bir ön ek ile birleştiriyoruz
        val name = when (provider) {
            is FilmMakinesi -> provider.name
            is FilmModu -> provider.name
            is HDFilmCehennemi -> provider.name
            else -> ""
        }
        val pages = when (provider) {
            is FilmMakinesi -> provider.mainPage
            is FilmModu -> provider.mainPage
            is HDFilmCehennemi -> provider.mainPage
            else -> emptyList()
        }
        pages.map { page ->
            MainPageData("$name|${page.data}", "$name - ${page.name}")
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val parts = request.data.split("|", limit = 2)
        if (parts.isEmpty()) return null
        val providerName = parts[0]
        val originalData = if (parts.size > 1) parts[1] else ""
        
        val provider = providers.find { 
            val name = when (it) {
                is FilmMakinesi -> it.name
                is FilmModu -> it.name
                is HDFilmCehennemi -> it.name
                else -> ""
            }
            name == providerName
        } ?: return null
        
        val cleanName = request.name.substringAfter(" - ")
        val newRequest = MainPageRequest(cleanName, originalData, request.horizontalImages)
        
        return try {
            val response = when (provider) {
                is FilmMakinesi -> provider.getMainPage(page, newRequest)
                is FilmModu -> provider.getMainPage(page, newRequest)
                is HDFilmCehennemi -> provider.getMainPage(page, newRequest)
                else -> null
            }
            if (response != null) {
                if (response.items.size > 1) {
                    val allResults = response.items.flatMap { it.list }
                    newHomePageResponse(
                        listOf(HomePageList(cleanName, allResults, isHorizontalImages = response.items.firstOrNull()?.isHorizontalImages ?: true)),
                        response.hasNext
                    )
                } else {
                    response
                }
            } else {
                null
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            val errorMsg = "Hata (${providerName}): ${e.message ?: e.toString()}"
            val debugList = listOf(
                newMovieSearchResponse(errorMsg, "https://error.com", TvType.Movie) {}
            )
            newHomePageResponse(
                listOf(HomePageList(cleanName, debugList, isHorizontalImages = true)),
                false
            )
        }
    }

    override suspend fun search(query: String): List<SearchResponse>? = coroutineScope {
        providers.map { provider ->
            async {
                try {
                    when (provider) {
                        is FilmMakinesi -> provider.search(query)
                        is FilmModu -> provider.search(query)
                        is HDFilmCehennemi -> provider.search(query)
                        else -> emptyList()
                    }
                } catch (e: Throwable) {
                    null
                }
            }
        }.awaitAll().filterNotNull().flatten()
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val provider = getAnyProviderForUrl(url) ?: throw ErrorLoadingException("Sağlayıcı bulunamadı")
        return when (provider) {
            is FilmMakinesi -> provider.load(url)
            is FilmModu -> provider.load(url)
            is HDFilmCehennemi -> provider.load(url)
            else -> null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val provider = getAnyProviderForUrl(data) ?: return false
        return when (provider) {
            is FilmMakinesi -> provider.loadLinks(data, isCasting, subtitleCallback, callback)
            is FilmModu -> provider.loadLinks(data, isCasting, subtitleCallback, callback)
            is HDFilmCehennemi -> provider.loadLinks(data, isCasting, subtitleCallback, callback)
            else -> false
        }
    }
}
