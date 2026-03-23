package com.onurcvnoglu.xprime

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.SubtitleHelper

object XPrimeExtractors {

    private const val MOVIE_SUB_API = "https://sub.vdrk.site/v1/movie"
    private const val TV_SUB_API = "https://sub.wyzie.io/search"
    private const val OPEN_SUB_API = "https://opensubtitles-v3.strem.io"

    suspend fun invokeXPrimeSubs(
            tmdbId: Int?,
            imdbId: String? = null,
            season: Int? = null,
            episode: Int? = null,
            subtitleCallback: (SubtitleFile) -> Unit,
    ) {
        if (tmdbId == null) return

        // xPrime'ın kendi altyazı kaynakları
        runCatching {
            if (season == null) {
                // Film altyazıları
                val response =
                        app.get("$MOVIE_SUB_API/$tmdbId", timeout = 15L)
                                .parsedSafe<List<XPrimeSubtitle>>()
                response?.forEach { subtitle ->
                    val lang = subtitle.lang ?: return@forEach
                    val url = subtitle.url ?: return@forEach
                    val langName =
                            SubtitleHelper.fromTwoLettersToLanguage(lang)
                                    ?: SubtitleHelper.fromTagToEnglishLanguageName(lang) ?: lang
                    subtitleCallback.invoke(newSubtitleFile(langName, url))
                }
            } else {
                // Dizi altyazıları
                val response =
                        app.get(
                                        "$TV_SUB_API?id=$tmdbId&season=$season&episode=$episode",
                                        timeout = 15L
                                )
                                .parsedSafe<List<XPrimeSubtitle>>()
                response?.forEach { subtitle ->
                    val lang = subtitle.lang ?: return@forEach
                    val url = subtitle.url ?: return@forEach
                    val langName =
                            SubtitleHelper.fromTwoLettersToLanguage(lang)
                                    ?: SubtitleHelper.fromTagToEnglishLanguageName(lang) ?: lang
                    subtitleCallback.invoke(newSubtitleFile(langName, url))
                }
            }
        }
    }

    suspend fun invokeOpenSubs(
            imdbId: String? = null,
            season: Int? = null,
            episode: Int? = null,
            subtitleCallback: (SubtitleFile) -> Unit,
    ) {
        if (imdbId == null) return

        val slug =
                if (season == null) {
                    "movie/$imdbId"
                } else {
                    "series/$imdbId:$season:$episode"
                }

        runCatching {
            app.get("$OPEN_SUB_API/subtitles/$slug.json", timeout = 120L)
                    .parsedSafe<OpenSubtitleResult>()
                    ?.subtitles
                    ?.forEach { subtitle ->
                        subtitleCallback.invoke(
                                newSubtitleFile(
                                        SubtitleHelper.fromTagToEnglishLanguageName(
                                                subtitle.lang ?: ""
                                        )
                                                ?: subtitle.lang ?: return@forEach,
                                        subtitle.url ?: return@forEach
                                )
                        )
                    }
        }
    }

    data class XPrimeSubtitle(
            @JsonProperty("url") val url: String? = null,
            @JsonProperty("lang") val lang: String? = null,
            @JsonProperty("label") val label: String? = null,
            @JsonProperty("language") val language: String? = null,
    )

    data class OpenSubtitle(
            @JsonProperty("url") val url: String? = null,
            @JsonProperty("lang") val lang: String? = null,
    )

    data class OpenSubtitleResult(
            @JsonProperty("subtitles") val subtitles: ArrayList<OpenSubtitle>? = arrayListOf(),
    )
}
