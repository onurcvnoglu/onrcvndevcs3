package com.onurcvnoglu.usestremio.settings

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
import com.onurcvnoglu.usestremio.BuildConfig
import com.onurcvnoglu.usestremio.PREF_MANIFEST_KEY_PREFIX
import com.onurcvnoglu.usestremio.UseStremioPlugin
import com.onurcvnoglu.usestremio.manifestPreferenceKey
import com.onurcvnoglu.usestremio.normalizeManifestUrl

class SettingsFragment(
    plugin: UseStremioPlugin,
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

    private fun loadManifestsFromPrefs(): MutableList<String> {
        val manifests = mutableListOf<String>()
        var index = 0

        while (true) {
            val key = manifestPreferenceKey(index)
            if (!sharedPref.contains(key)) break

            val value = sharedPref.getString(key, "")?.normalizeManifestUrl().orEmpty()
            if (value.isNotEmpty()) manifests.add(value)
            index++
        }

        return manifests
    }

    private fun saveSettingsToPrefs(manifests: List<String>) {
        sharedPref.edit {
            sharedPref.all.keys
                .filter { it.startsWith(PREF_MANIFEST_KEY_PREFIX) }
                .forEach { remove(it) }

            manifests.forEachIndexed { index, url ->
                putString(manifestPreferenceKey(index), url.normalizeManifestUrl())
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

        val manifestInput = root.findView<EditText>("stremio_manifest_input")
        val addManifestButton = root.findView<Button>("add_manifest_button")
        manifestInput.makeTvCompatible()
        addManifestButton.makeTvCompatible()

        val manifestList = loadManifestsFromPrefs()
        val manifestRecyclerView = root.findView<RecyclerView>("stremio_manifest_list")
        manifestRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        val manifestAdapter = ManifestAdapter(manifestList)
        manifestRecyclerView.adapter = manifestAdapter

        addManifestButton.setOnClickListener {
            val text = manifestInput.text.toString().normalizeManifestUrl()
            if (text.isNotEmpty()) {
                manifestList.add(text)
                manifestAdapter.notifyItemInserted(manifestList.size - 1)
                manifestInput.text.clear()
            }
        }

        val saveButton = root.findView<ImageView>("save")
        saveButton.setImageDrawable(getDrawable("save_icon"))
        saveButton.makeTvCompatible()
        saveButton.setOnClickListener {
            if (manifestList.isEmpty()) {
                showToast("En az bir Stremio manifest URL ekleyin.")
                return@setOnClickListener
            }

            saveSettingsToPrefs(manifestList)

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
                .setMessage("This will delete all saved Stremio manifest URLs.")
                .setPositiveButton("Reset") { _, _ ->
                    sharedPref.edit(commit = true) {
                        sharedPref.all.keys
                            .filter { it.startsWith(PREF_MANIFEST_KEY_PREFIX) }
                            .forEach { remove(it) }
                    }

                    val size = manifestList.size
                    if (size > 0) {
                        manifestList.clear()
                        manifestAdapter.notifyItemRangeRemoved(0, size)
                    }
                    manifestInput.text.clear()
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

    inner class ManifestAdapter(
        private val items: MutableList<String>
    ) : RecyclerView.Adapter<ManifestAdapter.ManifestViewHolder>() {

        inner class ManifestViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val urlText: TextView = view.findViewById(
                resourcesProvider.getIdentifier("addon_url_text", "id", BuildConfig.LIBRARY_PACKAGE_NAME)
            )
            val deleteButton: Button = view.findViewById(
                resourcesProvider.getIdentifier("delete_addon_button", "id", BuildConfig.LIBRARY_PACKAGE_NAME)
            )
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ManifestViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val view = getLayout("item_stremio_addon", inflater, parent)
            view.makeTvCompatible()
            return ManifestViewHolder(view)
        }

        override fun onBindViewHolder(holder: ManifestViewHolder, position: Int) {
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
