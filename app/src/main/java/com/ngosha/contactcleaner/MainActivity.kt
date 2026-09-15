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

class MainActivity : AppCompatActivity() {
    data class Entry(val rawId: Long, val contactId: Long, val name: String, val number: String, val normalized: String)
    private var duplicateGroups: List<List<Entry>> = emptyList()
    private lateinit var status: TextView
    private lateinit var details: TextView
    private lateinit var clean: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.statusText)
        details = findViewById(R.id.detailsText)
        clean = findViewById(R.id.cleanButton)
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
        else status.text = "Ruhusa ya Contacts inahitajika ili app iweze kufanya kazi."
    }

    private fun normalizePhone(input: String): String {
        var n = input.replace(Regex("[^0-9+]"), "")
        if (n.startsWith("+")) n = n.substring(1)
        return when {
            n.startsWith("255") && n.length >= 12 -> n
            n.startsWith("0") && n.length == 10 -> "255" + n.substring(1)
            n.length == 9 && (n.startsWith("6") || n.startsWith("7")) -> "255$n"
            else -> n.trimStart('0')
        }
    }

    private fun scan() {
        status.text = "Inasoma contacts..."
        val rows = mutableListOf<Entry>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val raw = c.getLong(0); val contact = c.getLong(1)
                val name = c.getString(2) ?: "Bila jina"; val number = c.getString(3) ?: continue
                val normalized = normalizePhone(number)
                if (normalized.length >= 7) rows += Entry(raw, contact, name, number, normalized)
            }
        }
        duplicateGroups = rows.groupBy { it.normalized }.values
            .map { group -> group.distinctBy { it.rawId } }
            .filter { it.size > 1 }
        val extra = duplicateGroups.sumOf { it.size - 1 }
        status.text = if (extra == 0) "Hakuna duplicate contacts zilizopatikana." else "Duplicate $extra zimepatikana katika makundi ${duplicateGroups.size}."
        clean.isEnabled = extra > 0
        details.text = duplicateGroups.take(30).joinToString("\n\n") { g -> "${g.first().normalized}\n" + g.joinToString("\n") { "• ${it.name}  ${it.number}" } }
    }

    private fun confirmClean() {
        val count = duplicateGroups.sumOf { it.size - 1 }
        AlertDialog.Builder(this)
            .setTitle("Safisha duplicates?")
            .setMessage("App itaacha contact moja kwa kila namba na kufuta raw contact ${count} zilizojirudia. Contacts za akaunti read-only zinaweza kukataliwa na Android. Inashauriwa contacts zako ziwe synced/backup kabla ya kuendelea.")
            .setNegativeButton("GHAIRI", null)
            .setPositiveButton("SAFISHA") { _, _ -> cleanDuplicates() }
            .show()
    }

    private fun cleanDuplicates() {
        var deleted = 0
        duplicateGroups.forEach { group ->
            // Prefer keeping the entry with the most useful visible name, then the lowest raw id.
            val keep = group.sortedWith(compareByDescending<Entry> { it.name.isNotBlank() && it.name != "Bila jina" }.thenBy { it.rawId }).first()
            group.filter { it.rawId != keep.rawId }.forEach { duplicate ->
                try {
                    val ops = arrayListOf(
                        ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI)
                            .withSelection("${ContactsContract.RawContacts._ID}=?", arrayOf(duplicate.rawId.toString()))
                            .build()
                    )
                    contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                    deleted++
                } catch (_: Exception) { }
            }
        }
        status.text = "Imekamilika. Duplicate $deleted zimeondolewa."
        clean.isEnabled = false
        scan()
    }
}
