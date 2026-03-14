package com.onurcvncs3.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.onurcvncs3.BuildConfig
import com.onurcvncs3.Onurcvncs3Plugin
import com.onurcvncs3.PREF_TMDB_API_KEY

class SettingsFragment(
    plugin: Onurcvncs3Plugin,
    private val sharedPref: SharedPreferences
) : BottomSheetDialogFragment() {

    private val resourcesProvider: Resources =
        plugin.resources ?: throw IllegalStateException("Unable to read plugin resources")

    private fun getLayout(name: String, inflater: LayoutInflater, container: ViewGroup?): View {
        val id = resourcesProvider.getIdentifier(name, "layout", BuildConfig.LIBRARY_PACKAGE_NAME)
        return inflater.inflate(resourcesProvider.getLayout(id), container, false)
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun getDrawable(name: String): Drawable {
        val id = resourcesProvider.getIdentifier(name, "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        return resourcesProvider.getDrawable(id, null)
    }

    private fun <T : View> View.findView(name: String): T {
        val id = resourcesProvider.getIdentifier(name, "id", BuildConfig.LIBRARY_PACKAGE_NAME)
        return findViewById(id)
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    private fun View?.makeTvCompatible() {
        if (this == null) return
        val outlineId = resourcesProvider.getIdentifier("outline", "drawable", BuildConfig.LIBRARY_PACKAGE_NAME)
        if (outlineId != 0) {
            val drawable = resourcesProvider.getDrawable(outlineId, null)
            if (drawable != null) background = drawable
        }
    }

    private fun loadAddonsFromPrefs(): MutableList<String> {
        val addons = mutableListOf<String>()
        var index = 0

        while (true) {
            val key = if (index == 0) "stremio_addon" else "stremio_addon${index + 1}"
            if (!sharedPref.contains(key)) break

            val value = sharedPref.getString(key, "")?.trim().orEmpty()
            if (value.isNotEmpty()) addons.add(value)
            index++
        }

        return addons
    }

    private fun loadTmdbApiKeyFromPrefs(): String {
        return sharedPref.getString(PREF_TMDB_API_KEY, "")?.trim().orEmpty()
    }

    private fun saveSettingsToPrefs(tmdbApiKey: String, addons: List<String>) {
        sharedPref.edit {
            putString(PREF_TMDB_API_KEY, tmdbApiKey)
            sharedPref.all.keys
                .filter { it.startsWith("stremio_addon") }
                .forEach { remove(it) }

            addons.forEachIndexed { index, url ->
                val key = if (index == 0) "stremio_addon" else "stremio_addon${index + 1}"
                putString(key, url)
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = getLayout("settings", inflater, container)

        val tmdbApiKeyInput = root.findView<EditText>("tmdb_api_key_input")
        val stremioAddonInput = root.findView<EditText>("stremio_addon_input")
        val addAddonButton = root.findView<Button>("add_addon_button")
        tmdbApiKeyInput.setText(loadTmdbApiKeyFromPrefs())
        tmdbApiKeyInput.makeTvCompatible()
        stremioAddonInput.makeTvCompatible()
        addAddonButton.makeTvCompatible()

        val addonList = loadAddonsFromPrefs()
        val addonRecyclerView = root.findView<RecyclerView>("stremio_addon_list")
        addonRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        val addonAdapter = AddonAdapter(addonList)
        addonRecyclerView.adapter = addonAdapter

        addAddonButton.setOnClickListener {
            val text = stremioAddonInput.text.toString().trim()
            if (text.isNotEmpty()) {
                addonList.add(text)
                addonAdapter.notifyItemInserted(addonList.size - 1)
                stremioAddonInput.text.clear()
            }
        }

        val saveButton = root.findView<ImageView>("save")
        saveButton.setImageDrawable(getDrawable("save_icon"))
        saveButton.makeTvCompatible()
        saveButton.setOnClickListener {
            val tmdbApiKey = tmdbApiKeyInput.text.toString().trim()
            if (tmdbApiKey.isBlank()) {
                showToast("TMDB API key gerekli.")
                return@setOnClickListener
            }

            saveSettingsToPrefs(tmdbApiKey, addonList)

            AlertDialog.Builder(requireContext())
                .setTitle("Restart Required")
                .setMessage("Changes have been saved. Do you want to restart the app to apply them?")
                .setPositiveButton("Yes") { _, _ ->
                    showToast("Saved and Restarting...")
                    dismiss()
                    restartApp()
                }
                .setNegativeButton("No") { dialog, _ ->
                    showToast("Saved. Restart later to apply changes.")
                    dialog.dismiss()
                    dismiss()
                }
                .show()
        }

        val resetButton = root.findView<View>("delete_img")
        resetButton.makeTvCompatible()
        resetButton.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Reset")
                .setMessage("This will delete the saved TMDB API key and all saved addons.")
                .setPositiveButton("Reset") { _, _ ->
                    sharedPref.edit(commit = true) {
                        remove(PREF_TMDB_API_KEY)
                        sharedPref.all.keys
                            .filter { it.startsWith("stremio_addon") }
                            .forEach { remove(it) }
                    }

                    val size = addonList.size
                    if (size > 0) {
                        addonList.clear()
                        addonAdapter.notifyItemRangeRemoved(0, size)
                    }
                    tmdbApiKeyInput.text.clear()
                    stremioAddonInput.text.clear()
                    restartApp()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        return root
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = Unit

    private fun restartApp() {
        val context = requireContext().applicationContext
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        val componentName = intent?.component
        if (componentName != null) {
            val restartIntent = Intent.makeRestartActivityTask(componentName)
            context.startActivity(restartIntent)
            Runtime.getRuntime().exit(0)
        }
    }

    inner class AddonAdapter(
        private val items: MutableList<String>
    ) : RecyclerView.Adapter<AddonAdapter.AddonViewHolder>() {

        inner class AddonViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val urlText: TextView = view.findViewById(
                resourcesProvider.getIdentifier("addon_url_text", "id", BuildConfig.LIBRARY_PACKAGE_NAME)
            )
            val deleteButton: Button = view.findViewById(
                resourcesProvider.getIdentifier("delete_addon_button", "id", BuildConfig.LIBRARY_PACKAGE_NAME)
            )
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AddonViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val view = getLayout("item_stremio_addon", inflater, parent)
            view.makeTvCompatible()
            return AddonViewHolder(view)
        }

        override fun onBindViewHolder(holder: AddonViewHolder, position: Int) {
            val url = items[position]
            holder.urlText.text = url

            holder.deleteButton.setOnClickListener {
                val adapterPosition = holder.bindingAdapterPosition
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    items.removeAt(adapterPosition)
                    notifyItemRemoved(adapterPosition)
                }
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
