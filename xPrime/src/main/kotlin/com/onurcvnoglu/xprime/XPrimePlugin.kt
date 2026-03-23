package com.onurcvnoglu.xprime

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class XPrimePlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(XPrimeProvider())
    }
}
