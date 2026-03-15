package com.onurcvnoglu.usestremio

import android.content.SharedPreferences
import android.webkit.URLUtil
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPage as buildMainPage
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class UseStremioProvider(private val sharedPref: SharedPreferences) : MainAPI() {
    override var mainUrl = "https://www.stremio.com"
    override var name = "UseStremio"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.AnimeMovie,
        TvType.Anime,
        TvType.Live,
        TvType.Others,
        TvType.Torrent,
    )

    override val mainPage: List<MainPageData> = listOf(
        buildMainPage(HOME_PAGE_DATA, "Catalogs", false)
    )

    private val manifestMutex = Mutex()
    private val trackerMutex = Mutex()
    private val catalogPageSizeOverrides = ConcurrentHashMap<String, Int>()

    @Volatile
    private var manifestState: ManifestState? = null

    @Volatile
    private var trackerSuffix: String? = null

    private fun getSavedManifestUrls(): List<String> {
        val urls = mutableListOf<String>()
        var index = 0

        while (true) {
            val key = manifestPreferenceKey(index)
            if (!sharedPref.contains(key)) break

            val value = sharedPref.getString(key, null)?.normalizeManifestUrl()
            if (!value.isNullOrBlank()) {
                urls += value.toManifestUrl()
            }
            index++
        }

        return urls.distinct()
    }

    private suspend fun ensureManifestState(): ManifestState {
        val urls = getSavedManifestUrls()
        val signature = urls.joinToString(separator = "|")
        manifestState?.takeIf { it.signature == signature }?.let { return it }

        return manifestMutex.withLock {
            manifestState?.takeIf { it.signature == signature }?.let { return@withLock it }

            val manifests = coroutineScope {
                urls.map { manifestUrl ->
                    async { fetchManifest(manifestUrl) }
                }.awaitAll()
            }.filterNotNull()

            val homeCatalogs = manifests.flatMap { manifest ->
                manifest.catalogs.filter { canResolveCatalogExtras(it, search = null) }
            }.sortedByDescending { it.showInHome }

            val searchableCatalogs = manifests.flatMap { manifest ->
                manifest.catalogs.filter { it.supportsSearch && canResolveCatalogExtras(it, search = "test") }
            }

            val state = ManifestState(
                signature = signature,
                manifests = manifests,
                homeCatalogs = homeCatalogs,
                searchableCatalogs = searchableCatalogs,
            )

            catalogPageSizeOverrides.clear()
            manifestState = state
            state
        }
    }

    private suspend fun fetchManifest(manifestUrl: String): ResolvedManifest? {
        return runCatching {
            val text = app.get(manifestUrl, timeout = 15L).text
            parseManifest(manifestUrl, JSONObject(text))
        }.getOrElse {
            Log.e(name, "Failed to fetch manifest: $manifestUrl")
            null
        }
    }

    private fun parseManifest(manifestUrl: String, json: JSONObject): ResolvedManifest {
        val topTypes = json.optJSONArray("types").toStringList().map { it.lowercase() }
        val topIdPrefixes = json.optJSONArray("idPrefixes").toStringList()
        val resourceCapabilities = mutableListOf<ResourceCapability>()

        json.optJSONArray("resources")?.let { resources ->
            for (index in 0 until resources.length()) {
                when (val resource = resources.opt(index)) {
                    is String -> resourceCapabilities += ResourceCapability(
                        name = resource.lowercase(),
                        types = topTypes,
                        idPrefixes = topIdPrefixes,
                    )

                    is JSONObject -> resourceCapabilities += ResourceCapability(
                        name = resource.optString("name").trim().lowercase(),
                        types = resource.optJSONArray("types")?.toStringList()?.map { it.lowercase() } ?: topTypes,
                        idPrefixes = resource.optJSONArray("idPrefixes")?.toStringList() ?: topIdPrefixes,
                    )
                }
            }
        }

        val addonName = json.optString("name").ifBlank { manifestUrl }
        val catalogs = json.optJSONArray("catalogs")?.toCatalogList(manifestUrl, addonName) ?: emptyList()

        return ResolvedManifest(
            addonName = addonName,
            manifestUrl = manifestUrl,
            baseUrl = manifestUrl.toManifestBaseUrl(),
            logoUrl = json.optString("logo").takeIf { it.isNotBlank() },
            backgroundUrl = json.optString("background").takeIf { it.isNotBlank() },
            resourceCapabilities = resourceCapabilities,
            catalogs = catalogs,
        )
    }

    private fun canResolveCatalogExtras(catalog: ResolvedCatalog, search: String?): Boolean {
        return buildCatalogExtras(catalog, page = 1, search = search) != null
    }

    private fun buildCatalogExtras(
        catalog: ResolvedCatalog,
        page: Int,
        search: String?
    ): List<Pair<String, String>>? {
        val extras = mutableListOf<Pair<String, String>>()
        val skipValue = ((page - 1) * getEffectivePageSize(catalog)).coerceAtLeast(0).toString()

        for (extra in catalog.extras) {
            when (extra.name) {
                "skip" -> extras += "skip" to skipValue
                "search" -> {
                    val query = search?.trim()
                    if (query.isNullOrBlank()) return null
                    extras += "search" to query
                }

                else -> {
                    if (!extra.isRequired) continue

                    val chosenValue = when {
                        !extra.defaultValue.isNullOrBlank() -> extra.defaultValue
                        extra.options.isNotEmpty() -> extra.options.first()
                        else -> null
                    }

                    when {
                        !chosenValue.isNullOrBlank() -> extras += extra.name to chosenValue
                        extra.isRequired -> return null
                    }
                }
            }
        }

        return extras
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val state = ensureManifestState()
        if (state.homeCatalogs.isEmpty()) {
            throw ErrorLoadingException("No browseable catalogs found in the configured manifests.")
        }

        val sections = coroutineScope {
            state.homeCatalogs.map { catalog ->
                async {
                    val metas = fetchCatalogMetas(catalog, page, search = null)
                    val items = metas.mapNotNull { meta ->
                        meta.toLoadPreview(
                            preferredMetaManifest = catalog.manifestUrl,
                            fallbackType = catalog.catalogType,
                            fallbackAddonName = catalog.addonName,
                        )?.toSearchResponse()
                    }

                    ResolvedHomePageSection(
                        list = HomePageList(catalog.sectionTitle, items),
                        hasNext = metas.size >= getEffectivePageSize(catalog),
                    )
                }
            }.awaitAll()
        }.filter { it.list.list.isNotEmpty() }

        if (sections.isEmpty()) {
            throw ErrorLoadingException("Configured manifests returned no catalog items.")
        }

        return newHomePageResponse(
            sections.map { it.list },
            hasNext = sections.any { it.hasNext }
        )
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query, 1)?.items

    override suspend fun search(query: String, page: Int): SearchResponseList {
        if (query.isBlank()) return emptyList<SearchResponse>().toNewSearchResponseList(false)

        val state = ensureManifestState()
        if (state.searchableCatalogs.isEmpty()) return emptyList<SearchResponse>().toNewSearchResponseList(false)

        val searchResults = coroutineScope {
            state.searchableCatalogs.map { catalog ->
                async { fetchCatalogMetas(catalog, page, search = query) to catalog }
            }.awaitAll()
        }

        val merged = LinkedHashMap<String, LoadPreview>()
        var hasNext = false

        searchResults.forEach { (metas, catalog) ->
            hasNext = hasNext || metas.size >= getEffectivePageSize(catalog)
            metas.forEach { meta ->
                val preview = meta.toLoadPreview(
                    preferredMetaManifest = catalog.manifestUrl,
                    fallbackType = catalog.catalogType,
                    fallbackAddonName = catalog.addonName,
                ) ?: return@forEach
                val key = "${preview.itemId}|${preview.stremioType.lowercase()}"
                merged[key] = merged[key]?.mergeWith(preview) ?: preview
            }
        }

        val items = merged.values.mapNotNull { preview -> preview.toSearchResponse() }
        return items.toNewSearchResponseList(hasNext)
    }

    override suspend fun load(url: String): LoadResponse? {
        val state = ensureManifestState()
        val preview = parseJson<LoadPreview>(url)
        val mappedType = mapStremioType(preview.stremioType)
        val metaCandidate = resolveMeta(preview, state)
        val loadData = LoadData(
            itemId = preview.itemId,
            stremioType = preview.stremioType,
            preferredMetaManifest = metaCandidate?.sourceManifestUrl ?: preview.preferredMetaManifest
        )

        if (metaCandidate == null) {
            if (mappedType in setOf(TvType.TvSeries, TvType.Anime, TvType.Live)) {
                throw ErrorLoadingException("Meta or videos could not be loaded for this Stremio item.")
            }
            return buildMovieLikeLoadResponse(
                title = preview.title ?: preview.itemId,
                sourceUrl = url,
                type = mappedType,
                loadData = loadData,
                preview = preview,
                meta = null,
            )
        }

        val meta = metaCandidate.meta
        val videos = meta.videos.orEmpty().filter { !it.id.isNullOrBlank() }

        if (videos.isNotEmpty()) {
            return buildEpisodeBasedLoadResponse(
                title = meta.displayTitle ?: preview.title ?: preview.itemId,
                sourceUrl = url,
                type = mappedType,
                loadData = loadData,
                preview = preview,
                meta = meta,
                sourceManifestUrl = metaCandidate.sourceManifestUrl,
                videos = videos,
            )
        }

        if (mappedType == TvType.Live) {
            return buildLiveLoadResponse(
                title = meta.displayTitle ?: preview.title ?: preview.itemId,
                sourceUrl = url,
                loadData = loadData,
                preview = preview,
                meta = meta,
            )
        }

        if (mappedType in setOf(TvType.TvSeries, TvType.Anime)) {
            throw ErrorLoadingException("Meta videos could not be loaded for this Stremio item.")
        }

        return buildMovieLikeLoadResponse(
            title = meta.displayTitle ?: preview.title ?: preview.itemId,
            sourceUrl = url,
            type = mappedType,
            loadData = loadData,
            preview = preview,
            meta = meta,
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val state = ensureManifestState()
        val loadData = parseJson<LoadData>(data)
        val resourceId = loadData.videoId ?: loadData.itemId

        val streamManifests = state.manifests.filter {
            it.supportsResource("stream", loadData.stremioType, resourceId)
        }
        val subtitleManifests = state.manifests.filter {
            it.supportsResource("subtitles", loadData.stremioType, resourceId)
        }

        val seenLinks = mutableSetOf<String>()
        val seenSubtitles = mutableSetOf<String>()
        var foundAny = false

        suspend fun emitSubtitle(url: String?, language: String?) {
            if (url.isNullOrBlank()) return
            val resolvedLanguage = SubtitleHelper.fromTagToEnglishLanguageName(language ?: "")
                ?: language
                ?: "Unknown"
            val key = "$resolvedLanguage|$url"
            if (!seenSubtitles.add(key)) return
            subtitleCallback.invoke(newSubtitleFile(resolvedLanguage, url))
            foundAny = true
        }

        suspend fun emitLink(link: ExtractorLink) {
            if (!seenLinks.add(link.url)) return
            callback.invoke(link)
            foundAny = true
        }

        coroutineScope {
            val jobs = mutableListOf<kotlinx.coroutines.Deferred<Unit>>()

            streamManifests.forEach { manifest ->
                jobs += async {
                    val streamUrl = buildResourceUrl(manifest.baseUrl, "stream", loadData.stremioType, resourceId)
                    if (!URLUtil.isValidUrl(streamUrl)) return@async

                    runCatching {
                        app.get(streamUrl, timeout = 15L).parsedSafe<StremioStreamResponse>()
                    }.onSuccess { response ->
                        response?.streams.orEmpty().forEach { stream ->
                            stream.emit(subtitleEmitter = ::emitSubtitle, linkEmitter = ::emitLink)
                        }
                    }.onFailure {
                        Log.e(name, "Error loading stream resource from ${manifest.manifestUrl}")
                    }
                }
            }

            subtitleManifests.forEach { manifest ->
                jobs += async {
                    val subtitleUrl = buildResourceUrl(manifest.baseUrl, "subtitles", loadData.stremioType, resourceId)
                    if (!URLUtil.isValidUrl(subtitleUrl)) return@async

                    runCatching {
                        parseSubtitleResponse(app.get(subtitleUrl, timeout = 15L).text)
                    }.onSuccess { subtitles ->
                        subtitles.forEach { subtitle ->
                            emitSubtitle(subtitle.url, subtitle.language)
                        }
                    }.onFailure {
                        Log.e(name, "Error loading subtitle resource from ${manifest.manifestUrl}")
                    }
                }
            }

            jobs.awaitAll()
        }

        return foundAny
    }

    private suspend fun fetchCatalogMetas(
        catalog: ResolvedCatalog,
        page: Int,
        search: String?
    ): List<StremioMeta> {
        val extras = buildCatalogExtras(catalog, page, search) ?: return emptyList()
        val url = buildCatalogUrl(catalog.baseUrl, catalog.catalogType, catalog.catalogId, extras)

        return runCatching {
            app.get(url, timeout = 15L).parsedSafe<StremioCatalogResponse>()?.metas.orEmpty().also { metas ->
                if (metas.isNotEmpty()) {
                    catalogPageSizeOverrides[catalog.cacheKey] = metas.size
                }
            }
        }.getOrElse {
            Log.e(name, "Error loading catalog resource from ${catalog.manifestUrl}")
            emptyList()
        }
    }

    private fun getEffectivePageSize(catalog: ResolvedCatalog): Int {
        return catalogPageSizeOverrides[catalog.cacheKey]
            ?: catalog.pageSize
            ?: DEFAULT_PAGE_SIZE
    }

    private suspend fun resolveMeta(preview: LoadPreview, state: ManifestState): MetaCandidate? {
        val candidates = buildList {
            val preferred = preview.preferredMetaManifest?.let { manifestUrl ->
                state.manifests.firstOrNull { it.manifestUrl == manifestUrl && it.hasResource("meta") }
            }
            if (preferred != null) add(preferred)
            state.manifests
                .filter {
                    it.manifestUrl != preferred?.manifestUrl &&
                        it.supportsResource("meta", preview.stremioType, preview.itemId)
                }
                .forEach { add(it) }
        }

        for (manifest in candidates) {
            val metaUrl = buildResourceUrl(manifest.baseUrl, "meta", preview.stremioType, preview.itemId)
            val response = runCatching {
                app.get(metaUrl, timeout = 15L).parsedSafe<StremioMetaResponse>()?.meta
            }.getOrElse {
                Log.e(name, "Error loading meta resource from ${manifest.manifestUrl}")
                null
            }
            if (response != null) {
                return MetaCandidate(response, manifest.manifestUrl)
            }
        }

        return null
    }

    private suspend fun buildMovieLikeLoadResponse(
        title: String,
        sourceUrl: String,
        type: TvType,
        loadData: LoadData,
        preview: LoadPreview,
        meta: StremioMeta?
    ): LoadResponse {
        return newMovieLoadResponse(title, sourceUrl, type, loadData.toJson()) {
            val data = meta ?: preview.toMinimalMeta()
            posterUrl = data.poster ?: preview.poster
            backgroundPosterUrl = data.background ?: preview.background
            logoUrl = data.logo
            year = data.yearOrReleasedYear
            plot = data.description ?: preview.description
            duration = data.runtimeMinutes
            tags = data.genres.orEmpty().ifEmpty { preview.genres.orEmpty() }
            score = data.scoreValue ?: preview.scoreValue
            contentRating = data.certification
            comingSoon = isFutureRelease(data.released)
            data.trailerUrls.forEach { trailerUrl -> addTrailer(trailerUrl) }
            actors = data.toActorData()
        }
    }

    private suspend fun buildLiveLoadResponse(
        title: String,
        sourceUrl: String,
        loadData: LoadData,
        preview: LoadPreview,
        meta: StremioMeta
    ): LoadResponse {
        return newLiveStreamLoadResponse(title, sourceUrl, loadData.toJson()) {
            posterUrl = meta.poster ?: preview.poster
            backgroundPosterUrl = meta.background ?: preview.background
            logoUrl = meta.logo
            year = meta.yearOrReleasedYear
            plot = meta.description ?: preview.description
            tags = meta.genres.orEmpty()
            score = meta.scoreValue ?: preview.scoreValue
            contentRating = meta.certification
            meta.trailerUrls.forEach { trailerUrl -> addTrailer(trailerUrl) }
            actors = meta.toActorData()
        }
    }

    private suspend fun buildEpisodeBasedLoadResponse(
        title: String,
        sourceUrl: String,
        type: TvType,
        loadData: LoadData,
        preview: LoadPreview,
        meta: StremioMeta,
        sourceManifestUrl: String,
        videos: List<StremioVideo>
    ): LoadResponse {
        val episodes = videos.mapIndexed { index, video ->
            val episodeLoadData = loadData.copy(
                videoId = video.id,
                preferredMetaManifest = sourceManifestUrl
            )
            newEpisode(episodeLoadData.toJson()) {
                name = video.title ?: "Item ${index + 1}"
                season = video.season
                episode = video.episode
                posterUrl = video.thumbnail ?: meta.poster ?: preview.poster
                description = video.overview ?: meta.description ?: preview.description
                runTime = getDurationOrNull(video.runtime ?: meta.runtime)
                addDate(parseReleaseDate(video.released))
            }
        }

        return newTvSeriesLoadResponse(title, sourceUrl, type, episodes) {
            posterUrl = meta.poster ?: preview.poster
            backgroundPosterUrl = meta.background ?: preview.background
            logoUrl = meta.logo
            year = meta.yearOrReleasedYear
            plot = meta.description ?: preview.description
            tags = meta.genres.orEmpty().ifEmpty { preview.genres.orEmpty() }
            score = meta.scoreValue ?: preview.scoreValue
            showStatus = mapShowStatus(meta.status)
            contentRating = meta.certification
            duration = meta.runtimeMinutes
            meta.trailerUrls.forEach { trailerUrl -> addTrailer(trailerUrl) }
            actors = meta.toActorData()
        }
    }

    private fun mapShowStatus(status: String?): ShowStatus {
        val normalized = status?.trim()?.lowercase().orEmpty()
        return when {
            normalized.contains("returning") -> ShowStatus.Ongoing
            normalized.contains("continuing") -> ShowStatus.Ongoing
            normalized.contains("production") -> ShowStatus.Ongoing
            normalized.contains("planned") -> ShowStatus.Ongoing
            else -> ShowStatus.Completed
        }
    }

    private suspend fun getTrackerSuffix(): String {
        trackerSuffix?.let { return it }
        return trackerMutex.withLock {
            trackerSuffix?.let { return@withLock it }

            val suffix = runCatching {
                app.get(TRACKER_LIST_URL, timeout = 15L).text
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .joinToString(separator = "") { tracker -> "&tr=$tracker" }
            }.getOrDefault("")

            trackerSuffix = suffix
            suffix
        }
    }

    private fun parseSubtitleResponse(text: String): List<ResolvedSubtitle> {
        val json = JSONObject(text)
        val subtitles = mutableListOf<ResolvedSubtitle>()
        val array = json.optJSONArray("subtitles") ?: return emptyList()

        for (index in 0 until array.length()) {
            val subtitle = array.optJSONObject(index) ?: continue
            val url = subtitle.optString("url").takeIf { it.isNotBlank() }
            val language = subtitle.optString("lang").takeIf { it.isNotBlank() }
                ?: subtitle.optString("label").takeIf { it.isNotBlank() }
                ?: subtitle.optString("name").takeIf { it.isNotBlank() }
            if (!url.isNullOrBlank()) {
                subtitles += ResolvedSubtitle(url = url, language = language)
            }
        }

        return subtitles
    }

    private suspend fun StremioStream.emit(
        subtitleEmitter: suspend (String?, String?) -> Unit,
        linkEmitter: suspend (ExtractorLink) -> Unit
    ) {
        if (!url.isNullOrBlank()) {
            linkEmitter(
                newExtractorLink(
                    name ?: "Stremio",
                    fixSourceName(name, title, description),
                    url,
                    INFER_TYPE,
                ) {
                    quality = getQualityFromStreamHints(name, title, description, behaviorHints?.filename)
                    headers = behaviorHints?.proxyHeaders?.request ?: behaviorHints?.headers ?: emptyMap()
                }
            )

            subtitles.orEmpty().forEach { subtitle ->
                subtitleEmitter(subtitle.url, subtitle.language)
            }
        }

        if (!ytId.isNullOrBlank()) {
            loadExtractor(
                "https://www.youtube.com/watch?v=$ytId",
                { subtitle -> runBlocking { subtitleEmitter(subtitle.url, subtitle.lang) } },
                { link -> runBlocking { linkEmitter(link) } }
            )
        }

        if (!externalUrl.isNullOrBlank()) {
            loadExtractor(
                externalUrl,
                { subtitle -> runBlocking { subtitleEmitter(subtitle.url, subtitle.lang) } },
                { link -> runBlocking { linkEmitter(link) } }
            )
        }

        if (!infoHash.isNullOrBlank()) {
            val sourceTrackers = sources.orEmpty()
                .filter { it.startsWith("tracker:") }
                .map { it.removePrefix("tracker:") }
                .filter { it.isNotBlank() }
                .joinToString(separator = "") { tracker -> "&tr=$tracker" }

            val magnet = "magnet:?xt=urn:btih:$infoHash$sourceTrackers${getTrackerSuffix()}"
            linkEmitter(
                newExtractorLink(
                    name ?: "Stremio",
                    title ?: name ?: "Magnet stream",
                    magnet,
                ) {
                    quality = Qualities.Unknown.value
                }
            )
        }
    }

    private fun StremioMeta.toLoadPreview(
        preferredMetaManifest: String,
        fallbackType: String,
        fallbackAddonName: String
    ): LoadPreview? {
        val itemId = id?.takeIf { it.isNotBlank() } ?: return null
        return LoadPreview(
            itemId = itemId,
            stremioType = type?.takeIf { it.isNotBlank() } ?: fallbackType,
            preferredMetaManifest = preferredMetaManifest,
            title = displayTitle ?: return null,
            poster = poster,
            background = background,
            description = description,
            year = yearOrReleasedYear,
            scoreText = imdbRating,
            genres = genres.orEmpty(),
            addonName = fallbackAddonName,
        )
    }

    private fun LoadPreview.toSearchResponse(): SearchResponse? {
        val mappedType = mapStremioType(stremioType)
        val title = title ?: return null

        return when (mappedType) {
            TvType.TvSeries -> newTvSeriesSearchResponse(title, toJson(), mappedType) {
                posterUrl = poster
                year = this@toSearchResponse.year
                score = scoreValue
            }

            TvType.Anime, TvType.AnimeMovie -> newAnimeSearchResponse(title, toJson(), mappedType) {
                posterUrl = poster
                year = this@toSearchResponse.year
                score = scoreValue
            }

            TvType.Live -> newLiveSearchResponse(title, toJson(), mappedType) {
                posterUrl = poster
                score = scoreValue
            }

            else -> newMovieSearchResponse(title, toJson(), mappedType) {
                posterUrl = poster
                year = this@toSearchResponse.year
                score = scoreValue
            }
        }
    }

    private fun LoadPreview.mergeWith(other: LoadPreview): LoadPreview {
        return copy(
            preferredMetaManifest = preferredMetaManifest.ifBlank { other.preferredMetaManifest },
            title = title ?: other.title,
            poster = poster ?: other.poster,
            background = background ?: other.background,
            description = description ?: other.description,
            year = year ?: other.year,
            scoreText = scoreText ?: other.scoreText,
            genres = if (genres.isNotEmpty()) genres else other.genres,
            addonName = addonName.ifBlank { other.addonName },
        )
    }

    private fun LoadPreview.toMinimalMeta(): StremioMeta {
        return StremioMeta(
            id = itemId,
            type = stremioType,
            name = title,
            description = description,
            poster = poster,
            background = background,
            year = year?.toString(),
            imdbRating = scoreText,
            genres = genres,
        )
    }

    private val StremioMeta.displayTitle: String?
        get() = name?.takeIf { it.isNotBlank() } ?: id

    private val StremioMeta.yearOrReleasedYear: Int?
        get() = getDefaultYear(year) ?: getDefaultYear(released)

    private val StremioMeta.runtimeMinutes: Int?
        get() = getDurationOrNull(runtime)

    private val StremioMeta.scoreValue: Score?
        get() = getScoreOrNull(imdbRating)

    private val LoadPreview.scoreValue: Score?
        get() = getScoreOrNull(scoreText)

    private val StremioMeta.certification: String?
        get() = appExtras?.certification

    private val StremioMeta.trailerUrls: List<String>
        get() = trailers.orEmpty().mapNotNull { trailer ->
            when {
                !trailer.ytId.isNullOrBlank() -> "https://www.youtube.com/watch?v=${trailer.ytId}"
                !trailer.source.isNullOrBlank() -> "https://www.youtube.com/watch?v=${trailer.source}"
                else -> null
            }
        }.distinct()

    private fun StremioMeta.toActorData(): List<ActorData> {
        return appExtras?.cast.orEmpty().mapNotNull { person ->
            val name = person.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ActorData(
                Actor(name, person.photo),
                roleString = person.character
            )
        }
    }

    private fun ResolvedManifest.hasResource(name: String): Boolean {
        return resourceCapabilities.any { it.name == name }
    }

    private fun ResolvedManifest.supportsResource(name: String, type: String, itemId: String?): Boolean {
        return resourceCapabilities.any { capability ->
            capability.name == name && capability.matches(type, itemId)
        }
    }

    private fun ResourceCapability.matches(type: String, itemId: String?): Boolean {
        val normalizedType = type.trim().lowercase()
        val typeMatches = types.isEmpty() ||
            types.contains(normalizedType) ||
            (normalizedType.startsWith("anime.") && types.contains("anime")) ||
            (mapStremioType(normalizedType) == TvType.Others && types.contains("other"))

        if (!typeMatches) return false
        if (itemId.isNullOrBlank()) return true
        if (idPrefixes.isEmpty()) return true
        return idPrefixes.any { prefix -> itemId.startsWith(prefix) }
    }

    private fun org.json.JSONArray.toStringList(): List<String> {
        return buildList {
            for (index in 0 until length()) {
                optString(index).takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
    }

    private fun org.json.JSONArray.toCatalogList(manifestUrl: String, addonName: String): List<ResolvedCatalog> {
        return buildList {
            for (index in 0 until length()) {
                val catalog = optJSONObject(index) ?: continue
                val catalogId = catalog.optString("id").takeIf { it.isNotBlank() } ?: continue
                val catalogType = catalog.optString("type").takeIf { it.isNotBlank() } ?: continue
                val catalogName = catalog.optString("name").takeIf { it.isNotBlank() } ?: continue
                val extras = catalog.optJSONArray("extra")?.let { extraArray ->
                    buildList {
                        for (extraIndex in 0 until extraArray.length()) {
                            val extra = extraArray.optJSONObject(extraIndex) ?: continue
                            val extraName = extra.optString("name").takeIf { it.isNotBlank() } ?: continue
                            add(
                                CatalogExtra(
                                    name = extraName,
                                    isRequired = extra.optBoolean("isRequired", false),
                                    defaultValue = extra.optString("default").takeIf { it.isNotBlank() },
                                    options = extra.optJSONArray("options").toStringList(),
                                )
                            )
                        }
                    }
                } ?: emptyList()

                add(
                    ResolvedCatalog(
                        manifestUrl = manifestUrl,
                        baseUrl = manifestUrl.toManifestBaseUrl(),
                        addonName = addonName,
                        catalogId = catalogId,
                        catalogType = catalogType,
                        catalogName = catalogName,
                        showInHome = catalog.optBoolean("showInHome", false),
                        pageSize = catalog.optInt("pageSize").takeIf { it > 0 },
                        extras = extras,
                    )
                )
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class StremioCatalogResponse(
        @JsonProperty("metas") val metas: List<StremioMeta>? = emptyList(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class StremioMetaResponse(
        @JsonProperty("meta") val meta: StremioMeta? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class StremioStreamResponse(
        @JsonProperty("streams") val streams: List<StremioStream>? = emptyList(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class StremioStream(
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("description") val description: String? = null,
        @JsonProperty("ytId") val ytId: String? = null,
        @JsonProperty("externalUrl") val externalUrl: String? = null,
        @JsonProperty("behaviorHints") val behaviorHints: StreamBehaviorHints? = null,
        @JsonProperty("infoHash") val infoHash: String? = null,
        @JsonProperty("sources") val sources: List<String>? = emptyList(),
        @JsonProperty("subtitles") val subtitles: List<StreamSubtitle>? = emptyList(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class StreamBehaviorHints(
        @JsonProperty("proxyHeaders") val proxyHeaders: StreamProxyHeaders? = null,
        @JsonProperty("headers") val headers: Map<String, String>? = null,
        @JsonProperty("filename") val filename: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class StreamProxyHeaders(
        @JsonProperty("request") val request: Map<String, String>? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class StreamSubtitle(
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("lang") val language: String? = null,
        @JsonProperty("id") val id: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class StremioMeta(
        @JsonProperty("id") val id: String? = null,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("description") val description: String? = null,
        @JsonProperty("poster") val poster: String? = null,
        @JsonProperty("background") val background: String? = null,
        @JsonProperty("logo") val logo: String? = null,
        @JsonProperty("year") val year: String? = null,
        @JsonProperty("released") val released: String? = null,
        @JsonProperty("runtime") val runtime: String? = null,
        @JsonProperty("imdbRating") val imdbRating: String? = null,
        @JsonProperty("genres") val genres: List<String>? = emptyList(),
        @JsonProperty("trailers") val trailers: List<StremioTrailer>? = emptyList(),
        @JsonProperty("videos") val videos: List<StremioVideo>? = emptyList(),
        @JsonProperty("app_extras") val appExtras: StremioAppExtras? = null,
        @JsonProperty("status") val status: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class StremioTrailer(
        @JsonProperty("source") val source: String? = null,
        @JsonProperty("ytId") val ytId: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class StremioVideo(
        @JsonProperty("id") val id: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("season") val season: Int? = null,
        @JsonProperty("episode") val episode: Int? = null,
        @JsonProperty("thumbnail") val thumbnail: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("released") val released: String? = null,
        @JsonProperty("runtime") val runtime: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class StremioAppExtras(
        @JsonProperty("cast") val cast: List<StremioPerson>? = emptyList(),
        @JsonProperty("directors") val directors: List<StremioPerson>? = emptyList(),
        @JsonProperty("writers") val writers: List<StremioPerson>? = emptyList(),
        @JsonProperty("certification") val certification: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class StremioPerson(
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("character") val character: String? = null,
        @JsonProperty("photo") val photo: String? = null,
    )

    private data class ManifestState(
        val signature: String,
        val manifests: List<ResolvedManifest>,
        val homeCatalogs: List<ResolvedCatalog>,
        val searchableCatalogs: List<ResolvedCatalog>,
    )

    private data class ResolvedHomePageSection(
        val list: HomePageList,
        val hasNext: Boolean,
    )

    private data class ResolvedManifest(
        val addonName: String,
        val manifestUrl: String,
        val baseUrl: String,
        val logoUrl: String?,
        val backgroundUrl: String?,
        val resourceCapabilities: List<ResourceCapability>,
        val catalogs: List<ResolvedCatalog>,
    )

    private data class ResourceCapability(
        val name: String,
        val types: List<String>,
        val idPrefixes: List<String>,
    )

    private data class CatalogExtra(
        val name: String,
        val isRequired: Boolean,
        val defaultValue: String?,
        val options: List<String>,
    )

    private data class ResolvedCatalog(
        val manifestUrl: String,
        val baseUrl: String,
        val addonName: String,
        val catalogId: String,
        val catalogType: String,
        val catalogName: String,
        val showInHome: Boolean,
        val pageSize: Int?,
        val extras: List<CatalogExtra>,
    ) {
        val sectionTitle: String
            get() = "$addonName • $catalogName"

        val cacheKey: String
            get() = "$manifestUrl|${catalogType.lowercase()}|$catalogId"

        val supportsSearch: Boolean
            get() = extras.any { it.name == "search" }
    }

    private data class MetaCandidate(
        val meta: StremioMeta,
        val sourceManifestUrl: String,
    )

    private data class LoadPreview(
        val itemId: String,
        val stremioType: String,
        val preferredMetaManifest: String,
        val title: String?,
        val poster: String? = null,
        val background: String? = null,
        val description: String? = null,
        val year: Int? = null,
        val scoreText: String? = null,
        val genres: List<String> = emptyList(),
        val addonName: String = "",
    )

    private data class LoadData(
        val itemId: String,
        val videoId: String? = null,
        val stremioType: String,
        val preferredMetaManifest: String? = null,
    )

    private data class ResolvedSubtitle(
        val url: String?,
        val language: String?,
    )

    companion object {
        private const val TRACKER_LIST_URL =
            "https://raw.githubusercontent.com/ngosang/trackerslist/master/trackers_best.txt"
        private const val DEFAULT_PAGE_SIZE = 50
        private const val HOME_PAGE_DATA = "usestremio://catalogs"
    }
}
