package com.onurcvncs3

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.onurcvncs3.settings.SettingsFragment

@CloudstreamPlugin
class Onurcvncs3Plugin : Plugin() {
    override fun load(context: Context) {
        val sharedPref = context.getSharedPreferences("Onurcvncs3", Context.MODE_PRIVATE)
        registerMainAPI(Onurcvncs3Provider(sharedPref))

        openSettings = { ctx ->
            val activity = ctx as AppCompatActivity
            val frag = SettingsFragment(this, sharedPref)
            frag.show(activity.supportFragmentManager, "Frag")
        }
    }
}
