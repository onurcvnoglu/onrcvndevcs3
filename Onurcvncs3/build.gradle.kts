version = 1

android {
    namespace = "com.onurcvncs3"

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

cloudstream {
    description = "[!] Requires Setup\n- Add your TMDB API key and Stremio manifest.json URLs from plugin settings"
    authors = listOf("onrcvndev", "Hexated", "phisher98", "erynith")
    status = 1
    tvTypes = listOf("TvSeries", "Movie", "Torrent")
    requiresResources = true
    language = "tr"
    iconUrl = "https://files.catbox.moe/ol63rm.png"
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.13.0")
}
