version = 1

android {
    namespace = "com.onurcvnoglu.usestremio"

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

cloudstream {
    description = "Use configured Stremio manifest URLs for catalog, meta, stream, and subtitle resources"
    authors = listOf("onurcvnoglu")
    status = 1
    tvTypes = listOf("TvSeries", "Movie", "AnimeMovie", "Anime", "Live", "Others", "Torrent")
    requiresResources = true
    language = "tr"
    iconUrl = "https://files.catbox.moe/ol63rm.png"
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.13.0")
}
