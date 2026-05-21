package com.onurcvnoglu.diziextra

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import com.onurcvnoglu.diziextra.ddizi.*
import com.onurcvnoglu.diziextra.dizibox.*
import com.onurcvnoglu.diziextra.dizimom.*
import com.onurcvnoglu.diziextra.dizipal.*
import com.onurcvnoglu.diziextra.dizipaloriginal.*
import com.onurcvnoglu.diziextra.diziyou.*
import com.onurcvnoglu.diziextra.dizilla.*
import com.onurcvnoglu.diziextra.sezonlukdizi.*
import com.onurcvnoglu.diziextra.tlctr.*

class DiziExtra : MainAPI() {
    override var name = "Dizi Extra"
    override var mainUrl = "https://dizilla.com"
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = true

    private val providers = listOf(
        DDizi(),
        DiziBox(),
        DiziMom(),
        DiziPal(),
        DiziPalOriginal(),
        DiziYou(),
        Dizilla(),
        SezonlukDizi(),
        Tlctr()
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
