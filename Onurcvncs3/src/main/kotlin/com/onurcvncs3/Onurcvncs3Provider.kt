package com.onurcvncs3

import android.content.SharedPreferences
import android.os.Build
import android.webkit.URLUtil
import androidx.annotation.RequiresApi
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.runAllAsync
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.onurcvncs3.SubsExtractors.invokeOpenSubs
import com.onurcvncs3.SubsExtractors.invokeWatchsomuch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate

class Onurcvncs3Provider(private val sharedPref: SharedPreferences) : TmdbProvider() {
    override var mainUrl = "https://example.com"
    override var name = "Stremio"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Torrent)

    companion object {
        const val TRACKER_LIST_URL =
            "https://raw.githubusercontent.com/ngosang/trackerslist/master/trackers_best.txt"
        private const val TMDB_API_BASE = "https://api.themoviedb.org/3"

        fun getType(type: String?): TvType {
            return when (type) {
                "movie" -> TvType.Movie
                else -> TvType.TvSeries
            }
        }

        fun getStatus(status: String?): ShowStatus {
            return when (status) {
                "Returning Series" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }

    private fun getTmdbApiKey(): String? {
        return sharedPref.getString(PREF_TMDB_API_KEY, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun requireTmdbApiKey(): String {
        return getTmdbApiKey()
            ?: throw ErrorLoadingException("TMDB API key ayarlanmamis. Eklenti ayarlarindan ekleyin.")
    }

    private fun buildTmdbUrl(path: String, vararg params: Pair<String, String?>): String {
        val encodedParams = buildList {
            add("api_key=${encodeQueryValue(requireTmdbApiKey())}")
            add("language=${encodeQueryValue(TMDB_LANGUAGE)}")
            params.forEach { (key, value) ->
                if (!value.isNullOrBlank()) {
                    add("${encodeQueryValue(key)}=${encodeQueryValue(value)}")
                }
            }
        }.joinToString("&")

        val separator = if (path.contains("?")) "&" else "?"
        return "$TMDB_API_BASE$path$separator$encodedParams"
    }

    private fun encodeQueryValue(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override val mainPage = run {
        val categories = mutableListOf<Pair<String, String>>()
        val currentMonth = LocalDate.now().monthValue

        categories += "/trending/all/day?region=US" to "Trendler"
        categories += "/movie/popular?region=US" to "Populer Filmler"
        if (currentMonth == 11 || currentMonth == 12) {
            categories += "/discover/movie?with_keywords=207317&region=US" to "Noel Filmleri"
        }
        if (currentMonth == 10) {
            categories += "/discover/movie?with_genres=27&region=US" to "Cadilar Bayrami Korku Filmleri"
        }
        categories += "/tv/popular?region=US&with_original_language=en" to "Populer Diziler"
        categories += "/tv/airing_today?region=US&with_original_language=en" to "Bugun Yayinda Olan Diziler"
        categories += "/discover/tv?with_networks=213" to "Netflix"
        categories += "/discover/tv?with_networks=1024" to "Amazon Prime"
        categories += "/discover/tv?with_networks=2739" to "Disney+"
        categories += "/discover/tv?with_networks=453" to "Hulu"
        categories += "/discover/tv?with_networks=2552" to "Apple TV+"
        categories += "/discover/tv?with_networks=49" to "HBO Max"
        categories += "/discover/tv?with_networks=4330" to "Paramount+"
        categories += "/movie/top_rated?region=US" to "En Yuksek Puanli Filmler"
        categories += "/tv/top_rated?region=US" to "En Yuksek Puanli Diziler"
        categories += "/movie/upcoming?region=US" to "Yaklasan Filmler"

        mainPageOf(*categories.toTypedArray())
    }

    private fun getImageUrl(link: String?, fallback: String?): String? {
        if (link == null) return fallback
        return if (link.startsWith("/")) "https://image.tmdb.org/t/p/original/$link" else link
    }

    private fun getOriginalImageUrl(link: String?, fallback: String?): String? {
        if (link == null) return fallback
        return if (link.startsWith("/")) "https://image.tmdb.org/t/p/original/$link" else link
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val adultQuery =
            if (settingsForProvider.enableAdult) "" else "&without_keywords=190370|13059|226161|195669|190370"
        val type = if (request.data.contains("/movie")) "movie" else "tv"
        val home = app.get(buildTmdbUrl("${request.data}$adultQuery", "page" to page.toString()))
            .parsedSafe<Results>()
            ?.results
            ?.mapNotNull { media ->
                media.toSearchResponse(type)
            } ?: throw ErrorLoadingException("Invalid JSON response")
        return newHomePageResponse(request.name, home)
    }

    private fun Media.toSearchResponse(type: String? = null): SearchResponse? {
        return newMovieSearchResponse(
            title ?: name ?: originalTitle ?: return null,
            Data(id = id, type = mediaType ?: type).toJson(),
            TvType.Movie,
        ) {
            posterUrl = getImageUrl(posterPath, "https://files.catbox.moe/90n81c.jpg")
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query, 1)?.items

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        return app.get(
            buildTmdbUrl(
                "/search/multi",
                "query" to query,
                "page" to page.toString(),
                "include_adult" to settingsForProvider.enableAdult.toString()
            )
        ).parsedSafe<Results>()?.results?.mapNotNull { media ->
            media.toSearchResponse()
        }?.toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse? {
        val data = parseJson<Data>(url)
        val type = getType(data.type)
        val responseUrl = if (type == TvType.Movie) {
            buildTmdbUrl(
                "/movie/${data.id}",
                "append_to_response" to "keywords,credits,external_ids,videos,recommendations"
            )
        } else {
            buildTmdbUrl(
                "/tv/${data.id}",
                "append_to_response" to "keywords,credits,external_ids,videos,recommendations"
            )
        }

        val response = app.get(responseUrl).parsedSafe<MediaDetail>()
            ?: throw ErrorLoadingException("Invalid JSON response")

        val title = response.title ?: response.name ?: return null
        val poster = getOriginalImageUrl(response.posterPath, "https://files.catbox.moe/32gthr.jpg")
        val backgroundPoster = getOriginalImageUrl(
            response.backdropPath,
            "https://files.catbox.moe/9qao8w.jpg"
        )
        val releaseDate = response.releaseDate ?: response.firstAirDate
        val releaseYear = releaseDate?.split("-")?.first()?.toIntOrNull()
        val genres = response.genres?.mapNotNull { it.name }
        val isAnime = genres?.contains("Animation") == true &&
            (response.originalLanguage == "zh" || response.originalLanguage == "ja")
        val keywords = response.keywords?.results?.mapNotNull { it.name }.orEmpty()
            .ifEmpty { response.keywords?.keywords?.mapNotNull { it.name } }
        val actors = response.credits?.cast?.mapNotNull { cast ->
            ActorData(
                Actor(
                    cast.name ?: cast.originalName ?: return@mapNotNull null,
                    getImageUrl(cast.profilePath, "https://files.catbox.moe/90n81c.jpg")
                ),
                roleString = cast.character
            )
        } ?: return null
        val recommendationItems = response.recommendations?.results?.mapNotNull { media ->
            media.toSearchResponse()
        }
        val trailer = response.videos?.results
            ?.map { "https://www.youtube.com/watch?v=${it.key}" }
            ?.randomOrNull()
        val logoUrl = fetchTmdbLogoUrl(
            tmdbApi = TMDB_API_BASE,
            apiKey = requireTmdbApiKey(),
            type = type,
            tmdbId = response.id,
            appLangCode = TMDB_LANGUAGE
        )

        return if (type == TvType.TvSeries) {
            val episodes = response.seasons?.mapNotNull { season ->
                app.get(
                    buildTmdbUrl(
                        "/${data.type}/${data.id}/season/${season.seasonNumber}"
                    )
                )
                    .parsedSafe<MediaDetailEpisodes>()?.episodes?.map { episode ->
                        newEpisode(
                            LoadData(
                                response.externalIds?.imdbId,
                                episode.seasonNumber,
                                episode.episodeNumber
                            ).toJson()
                        ) {
                            name = episode.name + if (isUpcoming(episode.airDate)) " • [UPCOMING]" else ""
                            this.season = episode.seasonNumber
                            this.episode = episode.episodeNumber
                            posterUrl = getImageUrl(episode.stillPath, "https://files.catbox.moe/qbz6xd.jpg")
                            score = Score.from10(episode.voteAverage)
                            description = episode.overview
                            runTime = episode.runtime
                            addDate(episode.airDate)
                        }
                    }
            }?.flatten() ?: emptyList()

            newTvSeriesLoadResponse(
                title,
                url,
                if (isAnime) TvType.Anime else TvType.TvSeries,
                episodes
            ) {
                posterUrl = poster
                this.backgroundPosterUrl = backgroundPoster
                try {
                    this.logoUrl = logoUrl
                } catch (_: Throwable) {
                }
                this.year = releaseYear
                plot = response.overview
                tags = keywords.takeIf { !it.isNullOrEmpty() } ?: genres
                score = Score.from10(response.voteAverage.toString())
                showStatus = getStatus(response.status)
                this.recommendations = recommendationItems
                this.actors = actors
                addTrailer(trailer)
                addImdbId(response.externalIds?.imdbId)
            }
        } else {
            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                LoadData(response.externalIds?.imdbId).toJson()
            ) {
                posterUrl = poster
                try {
                    this.logoUrl = logoUrl
                } catch (_: Throwable) {
                }
                comingSoon = isUpcoming(releaseDate)
                backgroundPosterUrl = backgroundPoster
                year = releaseYear
                plot = response.overview
                duration = response.runtime
                tags = keywords.takeIf { !it.isNullOrEmpty() } ?: genres
                score = Score.from10(response.voteAverage.toString())
                recommendations = recommendationItems
                this.actors = actors
                addTrailer(trailer)
                addImdbId(response.externalIds?.imdbId)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val response = parseJson<LoadData>(data)

        runAllAsync(
            suspend {
                invokeMainSource(
                    response.imdbId,
                    response.season,
                    response.episode,
                    subtitleCallback,
                    callback
                )
            },
            suspend { invokeWatchsomuch(response.imdbId, response.season, response.episode, subtitleCallback) },
            suspend { invokeOpenSubs(response.imdbId, response.season, response.episode, subtitleCallback) }
        )

        return true
    }

    private suspend fun invokeMainSource(
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val addonKeys = buildList {
            var index = 0
            while (true) {
                val key = if (index == 0) "stremio_addon" else "stremio_addon${index + 1}"
                if (!sharedPref.contains(key)) break
                add(key)
                index++
            }
        }

        for (addonKey in addonKeys) {
            val fixedMainUrl = sharedPref.getString(addonKey, "")?.fixSourceUrl()
            if (fixedMainUrl.isNullOrBlank()) continue

            val streamUrl = if (season == null) {
                "$fixedMainUrl/stream/movie/$imdbId.json"
            } else {
                "$fixedMainUrl/stream/series/$imdbId:$season:$episode.json"
            }

            if (!URLUtil.isValidUrl(streamUrl)) continue

            runCatching {
                app.get(streamUrl, timeout = 10L).parsedSafe<StreamsResponse>()
            }.onSuccess { result ->
                result?.streams?.forEach { stream ->
                    stream.runCallback(subtitleCallback, callback)
                }
            }.onFailure {
                Log.e(name, "Error loading from $addonKey")
            }
        }
    }

    private data class StreamsResponse(val streams: List<Stream>)

    private data class Subtitle(
        val url: String?,
        val lang: String?,
        val id: String?,
    )

    private data class ProxyHeaders(
        val request: Map<String, String>?,
    )

    private data class BehaviorHints(
        val proxyHeaders: ProxyHeaders?,
        val headers: Map<String, String>?,
    )

    private data class Stream(
        val name: String?,
        val title: String?,
        val url: String?,
        val description: String?,
        val ytId: String?,
        val externalUrl: String?,
        val behaviorHints: BehaviorHints?,
        val infoHash: String?,
        val sources: List<String> = emptyList(),
        val subtitles: List<Subtitle> = emptyList(),
    ) {
        suspend fun runCallback(
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
        ) {
            if (url != null) {
                callback.invoke(
                    newExtractorLink(
                        name ?: "",
                        fixSourceName(name, title, description),
                        url,
                        INFER_TYPE,
                    ) {
                        quality = getQuality(listOf(name, title, description))
                        headers = behaviorHints?.proxyHeaders?.request ?: behaviorHints?.headers ?: emptyMap()
                    }
                )

                subtitles.map { subtitle ->
                    subtitleCallback.invoke(
                        newSubtitleFile(
                            SubtitleHelper.fromTagToEnglishLanguageName(subtitle.lang ?: "") ?: subtitle.lang ?: "",
                            subtitle.url ?: return@map
                        )
                    )
                }
            }

            if (ytId != null) {
                loadExtractor("https://www.youtube.com/watch?v=$ytId", subtitleCallback, callback)
            }

            if (externalUrl != null) {
                loadExtractor(externalUrl, subtitleCallback, callback)
            }

            if (infoHash != null) {
                val response = app.get(TRACKER_LIST_URL).text
                val otherTrackers = response
                    .split("\n")
                    .filterIndexed { index, _ -> index % 2 == 0 }
                    .filter { tracker -> tracker.isNotEmpty() }
                    .joinToString("") { tracker -> "&tr=$tracker" }

                val sourceTrackers = sources
                    .filter { it.startsWith("tracker:") }
                    .map { it.removePrefix("tracker:") }
                    .filter { tracker -> tracker.isNotEmpty() }
                    .joinToString("") { tracker -> "&tr=$tracker" }

                val magnet = "magnet:?xt=urn:btih:$infoHash$sourceTrackers$otherTrackers"
                callback.invoke(
                    newExtractorLink(
                        name ?: "",
                        title ?: name ?: "",
                        magnet,
                    ) {
                        quality = Qualities.Unknown.value
                    }
                )
            }
        }
    }

    data class LoadData(
        val imdbId: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
    )

    data class Data(
        val id: Int? = null,
        val type: String? = null,
        val aniId: String? = null,
        val malId: Int? = null,
    )

    data class Results(
        @JsonProperty("results") val results: ArrayList<Media>? = arrayListOf(),
    )

    data class Media(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("original_title") val originalTitle: String? = null,
        @JsonProperty("media_type") val mediaType: String? = null,
        @JsonProperty("poster_path") val posterPath: String? = null,
    )

    data class Genres(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
    )

    data class Keywords(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
    )

    data class KeywordResults(
        @JsonProperty("results") val results: ArrayList<Keywords>? = arrayListOf(),
        @JsonProperty("keywords") val keywords: ArrayList<Keywords>? = arrayListOf(),
    )

    data class Seasons(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("season_number") val seasonNumber: Int? = null,
        @JsonProperty("air_date") val airDate: String? = null,
    )

    data class Cast(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("original_name") val originalName: String? = null,
        @JsonProperty("character") val character: String? = null,
        @JsonProperty("known_for_department") val knownForDepartment: String? = null,
        @JsonProperty("profile_path") val profilePath: String? = null,
    )

    data class Episodes(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("air_date") val airDate: String? = null,
        @JsonProperty("still_path") val stillPath: String? = null,
        @JsonProperty("runtime") val runtime: Int? = null,
        @JsonProperty("vote_average") val voteAverage: Double? = null,
        @JsonProperty("episode_number") val episodeNumber: Int? = null,
        @JsonProperty("season_number") val seasonNumber: Int? = null,
    )

    data class MediaDetailEpisodes(
        @JsonProperty("episodes") val episodes: ArrayList<Episodes>? = arrayListOf(),
    )

    data class Trailers(
        @JsonProperty("key") val key: String? = null,
    )

    data class ResultsTrailer(
        @JsonProperty("results") val results: ArrayList<Trailers>? = arrayListOf(),
    )

    data class ExternalIds(
        @JsonProperty("imdb_id") val imdbId: String? = null,
        @JsonProperty("tvdb_id") val tvdbId: String? = null,
    )

    data class Credits(
        @JsonProperty("cast") val cast: ArrayList<Cast>? = arrayListOf(),
    )

    data class ResultsRecommendations(
        @JsonProperty("results") val results: ArrayList<Media>? = arrayListOf(),
    )

    data class LastEpisodeToAir(
        @JsonProperty("episode_number") val episodeNumber: Int? = null,
        @JsonProperty("season_number") val seasonNumber: Int? = null,
    )

    data class MediaDetail(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("imdb_id") val imdbId: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("original_title") val originalTitle: String? = null,
        @JsonProperty("original_name") val originalName: String? = null,
        @JsonProperty("poster_path") val posterPath: String? = null,
        @JsonProperty("backdrop_path") val backdropPath: String? = null,
        @JsonProperty("release_date") val releaseDate: String? = null,
        @JsonProperty("first_air_date") val firstAirDate: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("runtime") val runtime: Int? = null,
        @JsonProperty("vote_average") val voteAverage: Any? = null,
        @JsonProperty("original_language") val originalLanguage: String? = null,
        @JsonProperty("status") val status: String? = null,
        @JsonProperty("genres") val genres: ArrayList<Genres>? = arrayListOf(),
        @JsonProperty("keywords") val keywords: KeywordResults? = null,
        @JsonProperty("last_episode_to_air") val lastEpisodeToAir: LastEpisodeToAir? = null,
        @JsonProperty("seasons") val seasons: ArrayList<Seasons>? = arrayListOf(),
        @JsonProperty("videos") val videos: ResultsTrailer? = null,
        @JsonProperty("external_ids") val externalIds: ExternalIds? = null,
        @JsonProperty("credits") val credits: Credits? = null,
        @JsonProperty("recommendations") val recommendations: ResultsRecommendations? = null,
    )
}
