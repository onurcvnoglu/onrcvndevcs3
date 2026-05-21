package com.onurcvnoglu.asyaextra

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import com.onurcvnoglu.asyaextra.dizikorea.*
import com.onurcvnoglu.asyaextra.koreanturk.*
import com.onurcvnoglu.asyaextra.trasyalog.*

class AsyaExtra : MainAPI() {
    override var name = "Asya Extra"
    override var mainUrl = "https://koreanturk.com"
    override val supportedTypes = setOf(TvType.AsianDrama, TvType.TvSeries, TvType.Movie)
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = true

    private val providers = listOf(
        DiziKorea(),
        KoreanTurk(),
        TRasyalog()
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
            val pages = provider.mainPage
            if (pages.isEmpty()) {
                // Eğer provider'ın mainPage listesi boşsa varsayılan bir sayfa oluştur
                listOf(MainPageData("${provider.name}|", provider.name))
            } else {
                // Sağlayıcı sayısı sınırlandırıldığı için tüm kategorileri yükle
                pages.map { page ->
                    MainPageData("${provider.name}|${page.data}", "${provider.name} - ${page.name}")
                }
            }
        } else {
            emptyList()
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val parts = request.data.split("|", limit = 2)
        if (parts.isEmpty()) return null
        val providerName = parts[0]
        val originalData = if (parts.size > 1) parts[1] else ""
        
        val provider = providers.find { it.name == providerName } ?: return null
        
        val cleanName = request.name.substringAfter(" - ")
        val newRequest = MainPageRequest(cleanName, originalData, request.horizontalImages)
        
        return try {
            val response = provider.getMainPage(page, newRequest)
            if (response != null) {
                // Eğer response birden fazla kategori satırı içeriyorsa tek satırda birleştirip düzleştiriyoruz
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
            // Hata mesajını ana sayfada liste elemanı olarak göstererek debug yapıyoruz
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
