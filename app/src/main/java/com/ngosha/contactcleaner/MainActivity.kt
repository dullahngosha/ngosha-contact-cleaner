package com.ngosha.contactcleaner

import android.Manifest
import android.content.ContentProviderOperation
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    data class Entry(val rawId: Long, val contactId: Long, val name: String, val number: String, val normalized: String)
    private var duplicateGroups: List<List<Entry>> = emptyList()
    private lateinit var status: TextView
    private lateinit var details: TextView
    private lateinit var clean: Button
    private lateinit var totalText: TextView
    private lateinit var duplicateText: TextView
    private lateinit var deletedText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.statusText); details = findViewById(R.id.detailsText)
        clean = findViewById(R.id.cleanButton); totalText = findViewById(R.id.totalText)
        duplicateText = findViewById(R.id.duplicateText); deletedText = findViewById(R.id.deletedText)
        findViewById<Button>(R.id.scanButton).setOnClickListener { ensurePermissionAndScan() }
        clean.setOnClickListener { confirmClean() }
    }

    private fun ensurePermissionAndScan() {
        val needed = arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
        if (needed.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) scan()
        else ActivityCompat.requestPermissions(this, needed, 100)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) scan()
        else status.text = "Ruhusa ya Contacts inahitajika."
    }

    private fun normalizePhone(input: String): String {
        var n = input.filter { it.isDigit() }
        if (n.startsWith("00255")) n = n.substring(2)
        return when {
            n.startsWith("255") && n.length >= 12 -> n
            n.startsWith("0") && n.length == 10 -> "255" + n.substring(1)
            n.length == 9 && (n.startsWith("6") || n.startsWith("7")) -> "255$n"
            else -> n.trimStart('0')
        }
    }

    private fun scan() {
        status.text = "Inakagua contacts..."
        thread {
            val rows = mutableListOf<Entry>()
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID, ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
            contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val raw = c.getLong(0); val contact = c.getLong(1); val name = c.getString(2) ?: "Bila jina"; val number = c.getString(3) ?: continue
                    val normalized = normalizePhone(number)
                    if (normalized.length >= 7) rows += Entry(raw, contact, name, number, normalized)
                }
            }
            val groups = rows.groupBy { it.normalized }.values.map { it.distinctBy { e -> e.rawId } }.filter { it.size > 1 }
            duplicateGroups = groups
            val extra = groups.sumOf { it.size - 1 }
            val preview = groups.take(60).joinToString("\n\n") { g -> "${g.first().normalized}\n" + g.joinToString("\n") { "• ${it.name}  ${it.number}" } }
            runOnUiThread {
                totalText.text = "${rows.distinctBy { it.contactId }.size}\nContacts"
                duplicateText.text = "$extra\nDuplicates"
                status.text = if (extra == 0) "✓ Simu yako haina duplicate contacts." else "Duplicate $extra zimepatikana katika makundi ${groups.size}."
                clean.isEnabled = extra > 0
                details.text = if (preview.isBlank()) "Hakuna duplicates zilizopatikana." else preview
            }
        }
    }

    private fun confirmClean() {
        val count = duplicateGroups.sumOf { it.size - 1 }
        AlertDialog.Builder(this)
            .setTitle("Uko tayari kusafisha duplicates?")
            .setMessage("Je, umeshafanya backup ya contacts zako?\n\nNdio: bonyeza NDIO, SAFISHA.\nBado: bonyeza SIJAFANYA BACKUP BADO kisha fanya backup kwanza.\n\nApp itaacha contact moja kwa kila namba na kuondoa duplicates $count.")
            .setNegativeButton("SIJAFANYA BACKUP BADO", null)
            .setPositiveButton("NDIO, SAFISHA") { _, _ -> cleanDuplicates() }
            .show()
    }

    private fun cleanDuplicates() {
        clean.isEnabled = false
        status.text = "Inasafisha duplicates... Tafadhali subiri."
        thread {
            var deleted = 0; var failed = 0
            duplicateGroups.forEachIndexed { index, group ->
                val keep = group.sortedWith(compareByDescending<Entry> { it.name.isNotBlank() && it.name != "Bila jina" }.thenBy { it.rawId }).first()
                group.filter { it.rawId != keep.rawId }.forEach { duplicate ->
                    try {
                        val ops = arrayListOf(ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI).withSelection("${ContactsContract.RawContacts._ID}=?", arrayOf(duplicate.rawId.toString())).build())
                        contentResolver.applyBatch(ContactsContract.AUTHORITY, ops); deleted++
                    } catch (_: Exception) { failed++ }
                }
                if (index % 25 == 0) runOnUiThread { status.text = "Inasafisha... kundi ${index + 1}/${duplicateGroups.size} • zimeondolewa $deleted" }
            }
            runOnUiThread {
                deletedText.text = "$deleted\nZimeondolewa"
                status.text = if (failed == 0) "✓ Imekamilika. Duplicate $deleted zimeondolewa." else "Imekamilika: $deleted zimeondolewa, $failed hazikuweza kuondolewa."
                details.text = "Usafishaji umekamilika. Bonyeza KAGUA CONTACTS kuthibitisha hali mpya."
                duplicateGroups = emptyList(); clean.isEnabled = false
            }
        }
    }
}
