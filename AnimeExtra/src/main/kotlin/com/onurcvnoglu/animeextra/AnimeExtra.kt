package com.onurcvnoglu.animeextra

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import com.onurcvnoglu.animeextra.animecix.*
import com.onurcvnoglu.animeextra.asyaanimeleri.*
import com.onurcvnoglu.animeextra.cizgimax.*
import com.onurcvnoglu.animeextra.tranimaci.*
import com.onurcvnoglu.animeextra.turkanime.*

class AnimeExtra : MainAPI() {
    override var name = "Anime Extra"
    override var mainUrl = "https://turkanime.co"
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = true

    private val providers = listOf(
        AnimeciX(),
        AsyaAnimeleri(),
        CizgiMax(),
        TRanimaci(),
        TurkAnime()
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
