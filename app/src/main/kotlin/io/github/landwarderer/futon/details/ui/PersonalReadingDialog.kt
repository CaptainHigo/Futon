package io.github.landwarderer.futon.details.ui

// Personal fork addition, 2026-10-29: private rating and chapter memories.
import android.app.Dialog
import android.os.Bundle
import android.text.InputFilter
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.landwarderer.futon.R
import io.github.landwarderer.futon.details.ui.model.ChapterListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.NumberFormat

class PersonalReadingDialog : DialogFragment() {
    private var rating = -1
    private var selected = 0
    private var memories = JSONObject()
    private var editor: TextInputEditText? = null
    private var keys = arrayListOf<String>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val args = requireArguments()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val key = "personal_reading_v1_${args.getLong("manga_id")}"
        val raw = savedInstanceState?.getString("document") ?: prefs.getString(key, null)
        val document = try {
            if (raw == null) JSONObject() else JSONObject(raw)
        } catch (e: org.json.JSONException) {
            return MaterialAlertDialogBuilder(context)
                .setMessage(R.string.personal_reading_invalid)
                .setPositiveButton(android.R.string.ok, null).create()
        }
        rating = document.optInt("rating", -1).coerceIn(-1, 20)
        memories = document.optJSONObject("memories") ?: JSONObject()
        keys = args.getStringArrayList("keys") ?: arrayListOf("general")
        val labels = args.getStringArrayList("labels") ?: arrayListOf(getString(R.string.personal_reading_general))
        selected = (savedInstanceState?.getInt("selected") ?: args.getInt("current")).coerceIn(keys.indices)
        val padding = (20 * resources.displayMetrics.density).toInt()
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, padding)
        }
        val valueLabel = TextView(context).apply { textSize = 24f }
        val enabled = SwitchMaterial(context).apply {
            setText(R.string.personal_reading_rated)
            isChecked = rating >= 0
        }
        val slider = Slider(context).apply {
            valueFrom = 0f
            valueTo = 10f
            stepSize = 0.5f
            value = rating.coerceAtLeast(0) / 2f
            contentDescription = getString(R.string.personal_reading_rating)
            setLabelFormatter { NumberFormat.getNumberInstance().format(it) + " / 10" }
        }
        fun refreshRating() {
            slider.isEnabled = enabled.isChecked
            valueLabel.text = if (rating < 0) getString(R.string.personal_reading_unrated)
                else NumberFormat.getNumberInstance().format(rating / 2.0) + " / 10"
        }
        slider.addOnChangeListener { _, value, _ ->
            if (enabled.isChecked) rating = (value * 2).toInt()
            refreshRating()
        }
        enabled.setOnCheckedChangeListener { _, checked ->
            rating = if (checked) (slider.value * 2).toInt() else -1
            refreshRating()
        }
        refreshRating()
        body.addView(enabled)
        body.addView(valueLabel)
        body.addView(slider)
        body.addView(TextView(context).apply { setText(R.string.personal_reading_chapter) })
        val chapters = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, labels)
            contentDescription = getString(R.string.personal_reading_chapter)
            minimumHeight = (48 * resources.displayMetrics.density).toInt()
            setSelection(selected)
        }
        body.addView(chapters)
        val field = TextInputLayout(context).apply { hint = getString(R.string.personal_reading_memo) }
        editor = TextInputEditText(context).apply {
            minLines = 3
            maxLines = 6
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            filters = arrayOf(InputFilter.LengthFilter(2000))
            setText(memories.optString(keys[selected], ""))
            field.addView(this)
        }
        chapters.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (selected == position) return
                captureMemo()
                selected = position
                editor?.setText(memories.optString(keys[selected], ""))
            }
        }
        body.addView(field)
        body.addView(TextView(context).apply {
            setText(R.string.personal_reading_backup)
            setPadding(0, padding / 2, 0, 0)
        })
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(args.getString("title"))
            .setView(ScrollView(context).apply { addView(body) })
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.personal_reading_save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                captureMemo()
                val json = JSONObject().put("rating", rating).put("memories", memories).toString()
                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).isEnabled = false
                lifecycleScope.launch(Dispatchers.Main) {
                    val saved = withContext(Dispatchers.IO) { prefs.edit().putString(key, json).commit() }
                    if (saved) dismiss() else {
                        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).isEnabled = true
                        Toast.makeText(context, R.string.personal_reading_save_error, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        return dialog
    }

    private fun captureMemo() {
        if (keys.isEmpty()) return
        val text = editor?.text?.toString().orEmpty()
        if (text.isBlank()) memories.remove(keys[selected]) else memories.put(keys[selected], text)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        captureMemo()
        outState.putString("document", JSONObject().put("rating", rating).put("memories", memories).toString())
        outState.putInt("selected", selected)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        editor = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance(mangaId: Long, title: String, chapters: List<ChapterListItem>) =
            PersonalReadingDialog().apply {
                arguments = Bundle().apply {
                    putLong("manga_id", mangaId)
                    putString("title", title)
                    putStringArrayList("keys", ArrayList(listOf("general") + chapters.map { it.chapter.id.toString() }))
                    putStringArrayList("labels", ArrayList(listOf("Mémo général") + chapters.map {
                        it.chapter.title ?: ("Chapitre " + (it.chapter.numberString() ?: "?"))
                    }))
                    putInt("current", (chapters.indexOfFirst { it.isCurrent } + 1).coerceAtLeast(0))
                }
            }
    }
}
