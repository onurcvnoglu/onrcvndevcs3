package com.onurcvncs3

import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

const val PREF_TMDB_API_KEY = "tmdb_api_key"
const val TMDB_LANGUAGE = "tr-TR"

fun String.fixSourceUrl(): String {
    return replace("/manifest.json", "").replace("stremio://", "https://")
}

fun fixSourceName(name: String?, title: String?, description: String?): String {
    val cleanedName = name?.replace("\n", " ")
    val cleanedTitle = title?.replace("\n", " ")

    return when {
        !cleanedName.isNullOrEmpty() && !cleanedTitle.isNullOrEmpty() -> "$cleanedName\n$cleanedTitle"
        !cleanedName.isNullOrEmpty() && !description.isNullOrEmpty() -> "$cleanedName\n$description"
        else -> cleanedTitle ?: description ?: cleanedName ?: ""
    }
}

fun getQuality(qualities: List<String?>): Int {
    fun String.getNormalizedQuality(): String? {
        val detected = Regex("(\\d{3,4}[pP])").find(this)?.groupValues?.getOrNull(1)
        if (detected != null) return detected
        if (contains("4k", ignoreCase = true)) return "2160p"
        return null
    }

    val quality = qualities.firstNotNullOfOrNull { value -> value?.getNormalizedQuality() }
    return getQualityFromName(quality)
}

fun getEpisodeSlug(season: Int? = null, episode: Int? = null): Pair<String, String> {
    return if (season == null && episode == null) {
        "" to ""
    } else {
        (if (season!! < 10) "0$season" else "$season") to
            (if (episode!! < 10) "0$episode" else "$episode")
    }
}

fun isUpcoming(dateString: String?): Boolean {
    return try {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateTime = dateString?.let { format.parse(it)?.time } ?: return false
        unixTimeMS < dateTime
    } catch (throwable: Throwable) {
        logError(throwable)
        false
    }
}

fun fixUrl(url: String, domain: String): String {
    if (url.startsWith("http")) return url
    if (url.isEmpty()) return ""

    return if (url.startsWith("//")) {
        "https:$url"
    } else if (url.startsWith('/')) {
        domain + url
    } else {
        "$domain/$url"
    }
}

suspend fun fetchTmdbLogoUrl(
    tmdbApi: String,
    apiKey: String,
    type: TvType,
    tmdbId: Int?,
    appLangCode: String?
): String? {
    if (tmdbId == null) return null

    val url = if (type == TvType.Movie) {
        "$tmdbApi/movie/$tmdbId/images?api_key=$apiKey"
    } else {
        "$tmdbApi/tv/$tmdbId/images?api_key=$apiKey"
    }

    val json = runCatching { JSONObject(app.get(url).text) }.getOrNull() ?: return null
    val logos = json.optJSONArray("logos") ?: return null
    if (logos.length() == 0) return null

    val lang = appLangCode?.trim()?.lowercase()?.substringBefore("-")

    fun path(value: JSONObject) = value.optString("file_path")
    fun isSvg(value: JSONObject) = path(value).endsWith(".svg", true)
    fun urlOf(value: JSONObject) = "https://image.tmdb.org/t/p/w500${path(value)}"

    var svgFallback: JSONObject? = null
    for (index in 0 until logos.length()) {
        val logo = logos.optJSONObject(index) ?: continue
        val logoPath = path(logo)
        if (logoPath.isBlank()) continue

        val logoLang = logo.optString("iso_639_1").trim().lowercase()
        if (logoLang == lang) {
            if (!isSvg(logo)) return urlOf(logo)
            if (svgFallback == null) svgFallback = logo
        }
    }
    svgFallback?.let { return urlOf(it) }

    var best: JSONObject? = null
    var bestSvg: JSONObject? = null

    fun voted(value: JSONObject) =
        value.optDouble("vote_average", 0.0) > 0 && value.optInt("vote_count", 0) > 0

    fun better(current: JSONObject?, candidate: JSONObject): Boolean {
        if (current == null) return true
        val currentAvg = current.optDouble("vote_average", 0.0)
        val currentCount = current.optInt("vote_count", 0)
        val candidateAvg = candidate.optDouble("vote_average", 0.0)
        val candidateCount = candidate.optInt("vote_count", 0)
        return candidateAvg > currentAvg || (candidateAvg == currentAvg && candidateCount > currentCount)
    }

    for (index in 0 until logos.length()) {
        val logo = logos.optJSONObject(index) ?: continue
        if (!voted(logo)) continue

        if (isSvg(logo)) {
            if (better(bestSvg, logo)) bestSvg = logo
        } else {
            if (better(best, logo)) best = logo
        }
    }

    best?.let { return urlOf(it) }
    bestSvg?.let { return urlOf(it) }
    return null
}
