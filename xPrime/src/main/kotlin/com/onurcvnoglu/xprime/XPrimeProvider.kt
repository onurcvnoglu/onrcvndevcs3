package com.onurcvnoglu.xprime

import android.util.Base64
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.runAllAsync
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.onurcvnoglu.xprime.XPrimeExtractors.invokeOpenSubs
import com.onurcvnoglu.xprime.XPrimeExtractors.invokeXPrimeSubs
import java.security.MessageDigest

class XPrimeProvider : MainAPI() {
    override var mainUrl = "https://xprime.su"
    override var name = "xPrime"
    override var lang = "tr"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes =
            setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.AnimeMovie)

    companion object {
        private const val TMDB_API_KEY = "84259f99204eeb7d45c7e3d8e36c6123"
        private const val TMDB_API_BASE = "https://api.themoviedb.org/3"
        private const val TMDB_LANGUAGE = "tr-TR"
        private const val XPRIME_API_BASE = "https://mznxiwqjdiq00239q.space"

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

    override val mainPage =
            mainPageOf(
                    "/trending/movie/week" to "Trend Filmler",
                    "/trending/tv/week" to "Trend Diziler",
                    "/movie/popular" to "Popüler Filmler",
                    "/tv/popular" to "Popüler Diziler",
                    "/movie/top_rated" to "En Yüksek Puanlı Filmler",
                    "/tv/top_rated" to "En Yüksek Puanlı Diziler",
                    "/movie/now_playing" to "Vizyondaki Filmler",
                    "/tv/airing_today" to "Bugün Yayında",
                    "/discover/tv?with_networks=213" to "Netflix",
                    "/discover/tv?with_networks=1024" to "Amazon Prime",
                    "/discover/tv?with_networks=2739" to "Disney+",
                    "/discover/tv?with_networks=49" to "HBO",
                    "/discover/movie?with_genres=16" to "Animasyon Filmleri",
                    "/discover/tv?with_genres=16" to "Anime Diziler",
                    "/movie/upcoming" to "Yaklaşan Filmler",
            )

    private fun buildTmdbUrl(path: String, vararg params: Pair<String, String?>): String {
        val base = "$TMDB_API_BASE$path"
        val separator = if (path.contains("?")) "&" else "?"
        val query =
                buildList {
                            add("api_key=$TMDB_API_KEY")
                            add("language=$TMDB_LANGUAGE")
                            params.forEach { (k, v) -> if (!v.isNullOrBlank()) add("$k=$v") }
                        }
                        .joinToString("&")
        return "$base${separator}$query"
    }

    private fun getImageUrl(path: String?): String? {
        if (path == null) return null
        return if (path.startsWith("/")) "https://image.tmdb.org/t/p/w500$path" else path
    }

    private fun getOriginalImageUrl(path: String?): String? {
        if (path == null) return null
        return if (path.startsWith("/")) "https://image.tmdb.org/t/p/original$path" else path
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val type = if (request.data.contains("/movie")) "movie" else "tv"
        val response =
                app.get(buildTmdbUrl(request.data, "page" to page.toString()))
                        .parsedSafe<TmdbResults>()
                        ?: throw ErrorLoadingException("Geçersiz yanıt")

        val home =
                response.results?.mapNotNull { media -> media.toSearchResponse(type) }
                        ?: emptyList()

        return newHomePageResponse(request.name, home)
    }

    private fun TmdbMedia.toSearchResponse(type: String? = null): SearchResponse? {
        return newMovieSearchResponse(
                title ?: name ?: originalTitle ?: return null,
                XPrimeData(id = id, type = mediaType ?: type).toJson(),
                TvType.Movie,
        ) { posterUrl = getImageUrl(posterPath) }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun search(query: String): List<SearchResponse>? {
        return app.get(buildTmdbUrl("/search/multi", "query" to query, "page" to "1"))
                .parsedSafe<TmdbResults>()
                ?.results
                ?.mapNotNull { media -> media.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val data = parseJson<XPrimeData>(url)
        val type = getType(data.type)

        val tmdbType = if (type == TvType.Movie) "movie" else "tv"
        val responseUrl =
                buildTmdbUrl(
                        "/$tmdbType/${data.id}",
                        "append_to_response" to
                                "keywords,credits,external_ids,videos,recommendations"
                )

        val response =
                app.get(responseUrl).parsedSafe<TmdbMediaDetail>()
                        ?: throw ErrorLoadingException("İçerik yüklenemedi")

        val title = response.title ?: response.name ?: return null
        val poster = getOriginalImageUrl(response.posterPath)
        val backdrop = getOriginalImageUrl(response.backdropPath)
        val releaseDate = response.releaseDate ?: response.firstAirDate
        val releaseYear = releaseDate?.split("-")?.firstOrNull()?.toIntOrNull()
        val genres = response.genres?.mapNotNull { it.name }
        val isAnime =
                genres?.contains("Animation") == true &&
                        (response.originalLanguage == "ja" || response.originalLanguage == "zh")
        val keywords =
                response.keywords?.results?.mapNotNull { it.name }.orEmpty().ifEmpty {
                    response.keywords?.keywords?.mapNotNull { it.name }
                }
        val actors =
                response.credits?.cast?.mapNotNull { cast ->
                    ActorData(
                            Actor(
                                    cast.name ?: cast.originalName ?: return@mapNotNull null,
                                    getImageUrl(cast.profilePath)
                            ),
                            roleString = cast.character
                    )
                }
        val recommendations =
                response.recommendations?.results?.mapNotNull { it.toSearchResponse() }
        val trailer =
                response.videos
                        ?.results
                        ?.map { "https://www.youtube.com/watch?v=${it.key}" }
                        ?.randomOrNull()

        return if (type == TvType.TvSeries) {
            val episodes =
                    response.seasons
                            ?.mapNotNull { season ->
                                app.get(
                                                buildTmdbUrl(
                                                        "/$tmdbType/${data.id}/season/${season.seasonNumber}"
                                                )
                                        )
                                        .parsedSafe<TmdbSeasonDetail>()
                                        ?.episodes
                                        ?.map { episode ->
                                            newEpisode(
                                                    XPrimeLoadData(
                                                                    tmdbId = data.id,
                                                                    imdbId =
                                                                            response.externalIds
                                                                                    ?.imdbId,
                                                                    title = title,
                                                                    year = releaseYear,
                                                                    season = episode.seasonNumber,
                                                                    episode = episode.episodeNumber,
                                                                    type = tmdbType,
                                                            )
                                                            .toJson()
                                            ) {
                                                this.name = episode.name
                                                this.season = episode.seasonNumber
                                                this.episode = episode.episodeNumber
                                                posterUrl = getImageUrl(episode.stillPath)
                                                score = Score.from10(episode.voteAverage.toString())
                                                description = episode.overview
                                                runTime = episode.runtime
                                                addDate(episode.airDate)
                                            }
                                        }
                            }
                            ?.flatten()
                            ?: emptyList()

            newTvSeriesLoadResponse(
                    title,
                    url,
                    if (isAnime) TvType.Anime else TvType.TvSeries,
                    episodes
            ) {
                posterUrl = poster
                backgroundPosterUrl = backdrop
                year = releaseYear
                plot = response.overview
                tags = keywords.takeIf { !it.isNullOrEmpty() } ?: genres
                score = Score.from10(response.voteAverage.toString())
                showStatus = getStatus(response.status)
                this.recommendations = recommendations
                this.actors = actors
                addTrailer(trailer)
                addImdbId(response.externalIds?.imdbId)
            }
        } else {
            newMovieLoadResponse(
                    title,
                    url,
                    TvType.Movie,
                    XPrimeLoadData(
                                    tmdbId = data.id,
                                    imdbId = response.externalIds?.imdbId,
                                    title = title,
                                    year = releaseYear,
                                    type = tmdbType,
                            )
                            .toJson()
            ) {
                posterUrl = poster
                backgroundPosterUrl = backdrop
                year = releaseYear
                plot = response.overview
                duration = response.runtime
                tags = keywords.takeIf { !it.isNullOrEmpty() } ?: genres
                score = Score.from10(response.voteAverage.toString())
                this.recommendations = recommendations
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
        val loadData = parseJson<XPrimeLoadData>(data)

        runAllAsync(
                // xPrime akış kaynakları
                suspend { invokeXPrimeServers(loadData, callback) },
                // xPrime altyazıları
                suspend {
                    invokeXPrimeSubs(
                            loadData.tmdbId,
                            loadData.imdbId,
                            loadData.season,
                            loadData.episode,
                            subtitleCallback
                    )
                },
                // OpenSubtitles altyazıları
                suspend {
                    invokeOpenSubs(
                            loadData.imdbId,
                            loadData.season,
                            loadData.episode,
                            subtitleCallback
                    )
                }
        )

        return true
    }

    // ==================== Altcha PoW Solver ====================

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    private suspend fun solveAltchaChallenge(): String? {
        val challengeResponse =
                runCatching {
                            app.get("$XPRIME_API_BASE/altcha/challenge", timeout = 10L)
                                    .parsedSafe<AltchaChallenge>()
                        }
                        .getOrNull()
                        ?: return null

        val algorithm = challengeResponse.algorithm ?: "SHA-256"
        val challenge = challengeResponse.challenge ?: return null
        val salt = challengeResponse.salt ?: return null
        val signature = challengeResponse.signature ?: return null
        val maxNumber = challengeResponse.maxnumber ?: 100000

        // SHA-256 brute-force: SHA256(salt + number) == challenge
        var solvedNumber: Int? = null
        for (n in 0..maxNumber) {
            val hash = sha256Hex("$salt$n")
            if (hash == challenge) {
                solvedNumber = n
                break
            }
        }

        if (solvedNumber == null) {
            Log.e("xPrime", "Altcha challenge çözülemedi (max: $maxNumber)")
            return null
        }

        // Base64 encoded JSON payload oluştur
        val payload =
                """{"algorithm":"$algorithm","challenge":"$challenge","number":$solvedNumber,"salt":"$salt","signature":"$signature"}"""
        return Base64.encodeToString(payload.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    // ==================== Stream Fetching ====================

    private suspend fun invokeXPrimeServers(
            loadData: XPrimeLoadData,
            callback: (ExtractorLink) -> Unit
    ) {
        // Altcha challenge çöz
        val altchaToken = solveAltchaChallenge()
        if (altchaToken == null) {
            Log.e("xPrime", "Altcha token alınamadı")
            return
        }

        // Sunucu listesini al
        val servers =
                runCatching {
                            app.get("$XPRIME_API_BASE/servers", timeout = 10L)
                                    .parsedSafe<XPrimeServersResponse>()
                                    ?.servers
                                    ?.filter { it.status == "ok" }
                                    ?.mapNotNull { it.name }
                        }
                        .getOrNull()
                        ?: listOf(
                                "primenet",
                                "bomber",
                                "primebox",
                                "fed",
                                "blackout",
                                "eek",
                                "mary"
                        )

        for (serverName in servers) {
            // Her sunucu için farklı bir altcha challenge çözülmeli
            val token =
                    if (serverName == servers.first()) altchaToken
                    else solveAltchaChallenge() ?: continue

            runCatching { invokeXPrimeServer(serverName, loadData, token, callback) }
                    .onFailure { Log.e("xPrime", "Server $serverName hatası: ${it.message}") }
        }
    }

    private suspend fun invokeXPrimeServer(
            serverName: String,
            loadData: XPrimeLoadData,
            altchaToken: String,
            callback: (ExtractorLink) -> Unit
    ) {
        val queryParams =
                buildList {
                            add("name=${loadData.title ?: ""}")
                            loadData.year?.let { add("year=$it") }
                            loadData.tmdbId?.let { add("id=$it") }
                            loadData.imdbId?.let { add("imdb=$it") }
                            loadData.season?.let { add("season=$it") }
                            loadData.episode?.let { add("episode=$it") }
                            add("altcha=$altchaToken")
                        }
                        .joinToString("&")

        val url = "$XPRIME_API_BASE/$serverName?$queryParams"

        val response =
                app.get(
                        url,
                        timeout = 30L,
                        headers =
                                mapOf(
                                        "Referer" to mainUrl,
                                        "Origin" to mainUrl,
                                )
                )

        val text = response.text
        if (text.isBlank()) return

        Log.d("xPrime", "Server $serverName yanıt (${text.length} char): ${text.take(50)}")

        // Yanıt şifreli olabilir (AQAA ile başlar) veya düz JSON
        if (text.startsWith("{") || text.startsWith("[")) {
            // Düz JSON yanıt
            parseJsonResponse(serverName, text, callback)
        } else {
            // Şifreli yanıt - worker URL'lerini doğrudan bulmayı dene
            // Yanıt base64 encoded olabilir, decode edip içinde URL arayalım
            runCatching {
                val decoded = String(Base64.decode(text, Base64.DEFAULT), Charsets.UTF_8)
                if (decoded.contains("http")) {
                    // URL bulundu
                    val urlRegex = Regex("""(https?://[^\s"'<>]+\.m3u8[^\s"'<>]*)""")
                    val workerRegex =
                            Regex("""(https?://[^\s"'<>]*workers\.dev[^\s"'<>]*)""")

                    urlRegex.findAll(decoded).forEach { match ->
                        callback.invoke(
                                newExtractorLink(
                                        "xPrime",
                                        "xPrime - $serverName",
                                        match.value,
                                        INFER_TYPE,
                                ) {
                                    quality = Qualities.Unknown.value
                                    headers = mapOf("Referer" to mainUrl)
                                }
                        )
                    }

                    workerRegex.findAll(decoded).forEach { match ->
                        callback.invoke(
                                newExtractorLink(
                                        "xPrime",
                                        "xPrime - $serverName [Worker]",
                                        match.value,
                                        INFER_TYPE,
                                ) {
                                    quality = Qualities.Unknown.value
                                    headers = mapOf("Referer" to mainUrl)
                                }
                        )
                    }
                }

                // JSON olarak parse etmeyi dene
                if (decoded.startsWith("{")) {
                    parseJsonResponse(serverName, decoded, callback)
                }
            }

            // Şifreli yanıt için alternatif: XOR / simple cipher denemesi
            runCatching {
                val bytes = Base64.decode(text, Base64.DEFAULT)
                // Versiyon byte'ını atla (ilk 3 byte: 01 00 00)
                if (bytes.size > 3 && bytes[0].toInt() == 1) {
                    val payload = bytes.copyOfRange(3, bytes.size)
                    // Basit XOR denemesi - ilk birkaç key ile
                    for (key in listOf(0x42, 0x58, 0x50)) {  // B, X, P
                        val decoded2 = String(payload.map { (it.toInt() xor key).toByte() }.toByteArray())
                        if (decoded2.contains("http") || decoded2.contains("url")) {
                            Log.d("xPrime", "XOR decoded ($key): ${decoded2.take(100)}")
                            if (decoded2.startsWith("{")) {
                                parseJsonResponse(serverName, decoded2, callback)
                            }
                            break
                        }
                    }
                }
            }
        }
    }

    private suspend fun parseJsonResponse(
            serverName: String,
            jsonText: String,
            callback: (ExtractorLink) -> Unit
    ) {
        runCatching {
            val serverResponse = parseJson<XPrimeServerResponse>(jsonText)

            // Doğrudan URL
            serverResponse.url?.let { streamUrl ->
                if (streamUrl.isNotBlank()) {
                    callback.invoke(
                            newExtractorLink(
                                    "xPrime",
                                    "xPrime - $serverName",
                                    streamUrl,
                                    INFER_TYPE,
                            ) {
                                quality = getQualityFromServer(serverResponse.quality)
                                headers = mapOf("Referer" to mainUrl)
                            }
                    )
                }
            }

            // Birden fazla kaynak
            serverResponse.sources?.forEach { source ->
                val sourceUrl = source.url ?: return@forEach
                callback.invoke(
                        newExtractorLink(
                                "xPrime",
                                "xPrime - $serverName [${source.quality ?: "Auto"}]",
                                sourceUrl,
                                INFER_TYPE,
                        ) {
                            quality = getQualityFromServer(source.quality)
                            headers = buildMap {
                                put("Referer", mainUrl)
                                source.headers?.forEach { (k, v) -> put(k, v) }
                            }
                        }
                )
            }

            // Embed URL
            serverResponse.embed?.let { embedUrl ->
                if (embedUrl.isNotBlank()) {
                    callback.invoke(
                            newExtractorLink(
                                    "xPrime",
                                    "xPrime - $serverName [Embed]",
                                    embedUrl,
                                    INFER_TYPE,
                            ) {
                                quality = Qualities.Unknown.value
                                headers = mapOf("Referer" to mainUrl)
                            }
                    )
                }
            }
        }
    }

    private fun getQualityFromServer(quality: String?): Int {
        if (quality != null) {
            return getQualityFromName(quality)
        }
        return Qualities.Unknown.value
    }

    // ==================== Data Classes ====================

    data class XPrimeData(
            @JsonProperty("id") val id: Int? = null,
            @JsonProperty("type") val type: String? = null,
    )

    data class XPrimeLoadData(
            @JsonProperty("tmdbId") val tmdbId: Int? = null,
            @JsonProperty("imdbId") val imdbId: String? = null,
            @JsonProperty("title") val title: String? = null,
            @JsonProperty("year") val year: Int? = null,
            @JsonProperty("season") val season: Int? = null,
            @JsonProperty("episode") val episode: Int? = null,
            @JsonProperty("type") val type: String? = null,
    )

    // Altcha
    data class AltchaChallenge(
            @JsonProperty("algorithm") val algorithm: String? = null,
            @JsonProperty("challenge") val challenge: String? = null,
            @JsonProperty("maxnumber") val maxnumber: Int? = null,
            @JsonProperty("salt") val salt: String? = null,
            @JsonProperty("signature") val signature: String? = null,
    )

    // xPrime API Responses
    data class XPrimeServersResponse(
            @JsonProperty("servers") val servers: List<XPrimeServer>? = null,
    )

    data class XPrimeServer(
            @JsonProperty("name") val name: String? = null,
            @JsonProperty("status") val status: String? = null,
            @JsonProperty("language") val language: String? = null,
    )

    data class XPrimeServerResponse(
            @JsonProperty("url") val url: String? = null,
            @JsonProperty("quality") val quality: String? = null,
            @JsonProperty("embed") val embed: String? = null,
            @JsonProperty("sources") val sources: List<XPrimeSource>? = null,
            @JsonProperty("subtitles") val subtitles: List<XPrimeSubtitle>? = null,
            @JsonProperty("type") val type: String? = null,
    )

    data class XPrimeSource(
            @JsonProperty("url") val url: String? = null,
            @JsonProperty("quality") val quality: String? = null,
            @JsonProperty("headers") val headers: Map<String, String>? = null,
    )

    data class XPrimeSubtitle(
            @JsonProperty("url") val url: String? = null,
            @JsonProperty("lang") val lang: String? = null,
            @JsonProperty("label") val label: String? = null,
    )

    // TMDB Data Classes
    data class TmdbResults(
            @JsonProperty("results") val results: ArrayList<TmdbMedia>? = arrayListOf(),
            @JsonProperty("total_pages") val totalPages: Int? = null,
    )

    data class TmdbMedia(
            @JsonProperty("id") val id: Int? = null,
            @JsonProperty("name") val name: String? = null,
            @JsonProperty("title") val title: String? = null,
            @JsonProperty("original_title") val originalTitle: String? = null,
            @JsonProperty("media_type") val mediaType: String? = null,
            @JsonProperty("poster_path") val posterPath: String? = null,
    )

    data class TmdbGenre(
            @JsonProperty("id") val id: Int? = null,
            @JsonProperty("name") val name: String? = null,
    )

    data class TmdbKeyword(
            @JsonProperty("id") val id: Int? = null,
            @JsonProperty("name") val name: String? = null,
    )

    data class TmdbKeywordResults(
            @JsonProperty("results") val results: ArrayList<TmdbKeyword>? = arrayListOf(),
            @JsonProperty("keywords") val keywords: ArrayList<TmdbKeyword>? = arrayListOf(),
    )

    data class TmdbSeason(
            @JsonProperty("id") val id: Int? = null,
            @JsonProperty("name") val name: String? = null,
            @JsonProperty("season_number") val seasonNumber: Int? = null,
            @JsonProperty("air_date") val airDate: String? = null,
    )

    data class TmdbCast(
            @JsonProperty("id") val id: Int? = null,
            @JsonProperty("name") val name: String? = null,
            @JsonProperty("original_name") val originalName: String? = null,
            @JsonProperty("character") val character: String? = null,
            @JsonProperty("profile_path") val profilePath: String? = null,
    )

    data class TmdbEpisode(
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

    data class TmdbSeasonDetail(
            @JsonProperty("episodes") val episodes: ArrayList<TmdbEpisode>? = arrayListOf(),
    )

    data class TmdbTrailer(
            @JsonProperty("key") val key: String? = null,
    )

    data class TmdbTrailerResults(
            @JsonProperty("results") val results: ArrayList<TmdbTrailer>? = arrayListOf(),
    )

    data class TmdbExternalIds(
            @JsonProperty("imdb_id") val imdbId: String? = null,
            @JsonProperty("tvdb_id") val tvdbId: String? = null,
    )

    data class TmdbCredits(
            @JsonProperty("cast") val cast: ArrayList<TmdbCast>? = arrayListOf(),
    )

    data class TmdbRecommendations(
            @JsonProperty("results") val results: ArrayList<TmdbMedia>? = arrayListOf(),
    )

    data class TmdbMediaDetail(
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
            @JsonProperty("genres") val genres: ArrayList<TmdbGenre>? = arrayListOf(),
            @JsonProperty("keywords") val keywords: TmdbKeywordResults? = null,
            @JsonProperty("seasons") val seasons: ArrayList<TmdbSeason>? = arrayListOf(),
            @JsonProperty("videos") val videos: TmdbTrailerResults? = null,
            @JsonProperty("external_ids") val externalIds: TmdbExternalIds? = null,
            @JsonProperty("credits") val credits: TmdbCredits? = null,
            @JsonProperty("recommendations") val recommendations: TmdbRecommendations? = null,
    )
}
