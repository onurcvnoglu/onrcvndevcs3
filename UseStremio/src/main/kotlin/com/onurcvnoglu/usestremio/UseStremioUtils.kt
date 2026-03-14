package com.onurcvnoglu.usestremio

import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.getDurationFromString
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.getQualityFromName
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

const val PREFS_NAME = "UseStremio"
const val PREF_MANIFEST_KEY_PREFIX = "stremio_manifest"
private val STREMIO_TYPE_ALIASES = mapOf(
    "movie" to TvType.Movie,
    "series" to TvType.TvSeries,
    "anime.movie" to TvType.AnimeMovie,
    "anime.series" to TvType.Anime,
    "anime" to TvType.Anime,
    "tv" to TvType.Live,
    "other" to TvType.Others,
    "collection" to TvType.Others,
    "trakt" to TvType.Others,
)

fun manifestPreferenceKey(index: Int): String {
    return if (index == 0) PREF_MANIFEST_KEY_PREFIX else "$PREF_MANIFEST_KEY_PREFIX${index + 1}"
}

fun String.normalizeManifestUrl(): String {
    return trim()
        .replace("stremio://", "https://")
        .replace("/manifest.json", "")
        .trimEnd('/')
}

fun String.toManifestUrl(): String = "${normalizeManifestUrl()}/manifest.json"

fun String.toManifestBaseUrl(): String = normalizeManifestUrl()

fun encodePathSegment(value: String): String {
    return URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20")
}

fun buildResourceUrl(baseUrl: String, resource: String, type: String, id: String): String {
    return "$baseUrl/$resource/${encodePathSegment(type)}/${encodePathSegment(id)}.json"
}

fun buildCatalogUrl(
    baseUrl: String,
    type: String,
    id: String,
    extras: List<Pair<String, String>>
): String {
    val extrasPath = extras.joinToString("/") { (name, value) ->
        "${encodePathSegment(name)}=${encodePathSegment(value)}"
    }
    return if (extrasPath.isBlank()) {
        "$baseUrl/catalog/${encodePathSegment(type)}/${encodePathSegment(id)}.json"
    } else {
        "$baseUrl/catalog/${encodePathSegment(type)}/${encodePathSegment(id)}/$extrasPath.json"
    }
}

fun mapStremioType(type: String?): TvType {
    return STREMIO_TYPE_ALIASES[type?.trim()?.lowercase()] ?: TvType.Others
}

fun getDefaultYear(value: String?): Int? {
    if (value.isNullOrBlank()) return null
    return Regex("(19|20)\\d{2}").find(value)?.value?.toIntOrNull()
}

fun getScoreOrNull(value: String?): Score? {
    return value?.toDoubleOrNull()?.let { Score.from10(it.toString()) }
}

fun getDurationOrNull(value: String?): Int? {
    return if (value.isNullOrBlank()) null else getDurationFromString(value)
}

fun getQualityFromStreamHints(vararg values: String?): Int {
    val normalized = values.firstNotNullOfOrNull { candidate ->
        candidate?.let {
            Regex("(\\d{3,4}[pP])").find(it)?.groupValues?.getOrNull(1)
                ?: if (it.contains("4k", ignoreCase = true)) "2160p" else null
        }
    }
    return getQualityFromName(normalized)
}

fun fixSourceName(name: String?, title: String?, description: String?): String {
    val cleanedName = name?.replace("\n", " ")
    val cleanedTitle = title?.replace("\n", " ")
    val cleanedDescription = description?.replace("\n", " ")
    return when {
        !cleanedName.isNullOrBlank() && !cleanedTitle.isNullOrBlank() -> "$cleanedName\n$cleanedTitle"
        !cleanedName.isNullOrBlank() && !cleanedDescription.isNullOrBlank() -> "$cleanedName\n$cleanedDescription"
        !cleanedTitle.isNullOrBlank() -> cleanedTitle
        !cleanedDescription.isNullOrBlank() -> cleanedDescription
        else -> cleanedName.orEmpty()
    }
}

fun parseReleaseDate(value: String?): String? {
    if (value.isNullOrBlank()) return null
    val datePart = value.substringBefore('T')
    return if (datePart.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) datePart else null
}

fun isFutureRelease(value: String?): Boolean {
    if (value.isNullOrBlank()) return false
    return runCatching {
        OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).isAfter(OffsetDateTime.now())
    }.recoverCatching {
        parseReleaseDate(value)?.let { date ->
            OffsetDateTime.parse("${date}T00:00:00Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .isAfter(OffsetDateTime.now())
        } ?: false
    }.getOrElse {
        logError(DateTimeParseException("Invalid release date", value, 0))
        false
    }
}
