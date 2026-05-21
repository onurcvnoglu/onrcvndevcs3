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
    override var sequentialMainPage = true
    override var sequentialMainPageDelay = 150L
    override var sequentialMainPageScrollDelay = 150L

    // Sağlayıcılar kendi MainAPI bağlamıyla çalıştığı için relative URL'ler doğru domaine çözülür.
    private val providers = listOf<MainAPI>(
        FilmMakinesi(),
        FilmModu(),
        HDFilmCehennemi()
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
        // Cloudstream MainPageData sırası "başlık, veri" olduğu için sağlayıcı bilgisini data alanında saklıyoruz.
        provider.mainPage.map { page ->
            MainPageData("${provider.name} - ${page.name}", "${provider.name}|${page.data}")
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
                    provider.search(query)
                } catch (e: Throwable) {
                    null
                }
            }
        }.awaitAll().filterNotNull().flatten()
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

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
