package com.onurcvncs3

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.SubtitleHelper

const val OPEN_SUB_API = "https://opensubtitles-v3.strem.io"
const val WATCHSOMUCH_API = "https://watchsomuch.tv"

object SubsExtractors {
    suspend fun invokeOpenSubs(
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
    ) {
        val slug = if (season == null) {
            "movie/$imdbId"
        } else {
            "series/$imdbId:$season:$episode"
        }

        app.get("$OPEN_SUB_API/subtitles/$slug.json", timeout = 120L)
            .parsedSafe<OpenSubtitleResult>()
            ?.subtitles
            ?.map { subtitle ->
                subtitleCallback.invoke(
                    newSubtitleFile(
                        SubtitleHelper.fromTagToEnglishLanguageName(subtitle.lang ?: "") ?: subtitle.lang
                        ?: return@map,
                        subtitle.url ?: return@map
                    )
                )
            }
    }

    suspend fun invokeWatchsomuch(
        imdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
    ) {
        val id = imdbId?.removePrefix("tt")
        val episodeId = app.post(
            "$WATCHSOMUCH_API/Watch/ajMovieTorrents.aspx",
            data = mapOf(
                "index" to "0",
                "mid" to "$id",
                "wsk" to "30fb68aa-1c71-4b8c-b5d4-4ca9222cfb45",
                "lid" to "",
                "liu" to ""
            ),
            headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        ).parsedSafe<WatchsomuchResponses>()?.movie?.torrents?.let { torrents ->
            if (season == null) {
                torrents.firstOrNull()?.id
            } else {
                torrents.find { it.episode == episode && it.season == season }?.id
            }
        } ?: return

        val (seasonSlug, episodeSlug) = getEpisodeSlug(season, episode)

        val subtitleUrl = if (season == null) {
            "$WATCHSOMUCH_API/Watch/ajMovieSubtitles.aspx?mid=$id&tid=$episodeId&part="
        } else {
            "$WATCHSOMUCH_API/Watch/ajMovieSubtitles.aspx?mid=$id&tid=$episodeId&part=S${seasonSlug}E${episodeSlug}"
        }

        app.get(subtitleUrl).parsedSafe<WatchsomuchSubtitleResponses>()?.subtitles?.map { subtitle ->
            subtitleCallback.invoke(
                newSubtitleFile(
                    subtitle.label ?: "",
                    fixUrl(subtitle.url ?: return@map null, WATCHSOMUCH_API)
                )
            )
        }
    }

    data class OpenSubtitle(
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("lang") val lang: String? = null,
    )

    data class OpenSubtitleResult(
        @JsonProperty("subtitles") val subtitles: ArrayList<OpenSubtitle>? = arrayListOf(),
    )

    data class WatchsomuchTorrent(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("movieId") val movieId: Int? = null,
        @JsonProperty("season") val season: Int? = null,
        @JsonProperty("episode") val episode: Int? = null,
    )

    data class WatchsomuchMovie(
        @JsonProperty("torrents") val torrents: ArrayList<WatchsomuchTorrent>? = arrayListOf(),
    )

    data class WatchsomuchResponses(
        @JsonProperty("movie") val movie: WatchsomuchMovie? = null,
    )

    data class WatchsomuchSubtitle(
        @JsonProperty("url") val url: String? = null,
        @JsonProperty("label") val label: String? = null,
    )

    data class WatchsomuchSubtitleResponses(
        @JsonProperty("subtitles") val subtitles: ArrayList<WatchsomuchSubtitle>? = arrayListOf(),
    )
}
