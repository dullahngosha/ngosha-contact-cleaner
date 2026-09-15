package com.ngosha.contactcleaner

import android.Manifest
import android.content.ContentProviderOperation
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
 data class Entry(val rawId:Long,val contactId:Long,val name:String,val number:String,val normalized:String)
 private var groups:List<List<Entry>> = emptyList()
 private lateinit var status:TextView; private lateinit var details:TextView; private lateinit var clean:Button
 private lateinit var totalText:TextView; private lateinit var duplicateText:TextView; private lateinit var deletedText:TextView; private lateinit var progress:ProgressBar
 override fun onCreate(b:Bundle?){super.onCreate(b);setContentView(R.layout.activity_main);status=findViewById(R.id.statusText);details=findViewById(R.id.detailsText);clean=findViewById(R.id.cleanButton);totalText=findViewById(R.id.totalText);duplicateText=findViewById(R.id.duplicateText);deletedText=findViewById(R.id.deletedText);progress=findViewById(R.id.progressBar);findViewById<Button>(R.id.scanButton).setOnClickListener{permission()};clean.setOnClickListener{confirm()}}
 private fun permission(){val p=arrayOf(Manifest.permission.READ_CONTACTS,Manifest.permission.WRITE_CONTACTS);if(p.all{ContextCompat.checkSelfPermission(this,it)==PackageManager.PERMISSION_GRANTED})scan() else ActivityCompat.requestPermissions(this,p,10)}
 override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray){super.onRequestPermissionsResult(r,p,g);if(r==10&&g.isNotEmpty()&&g.all{it==PackageManager.PERMISSION_GRANTED})scan() else status.text="Ruhusu Contacts ili Ngosha Cleaner ifanye kazi."}
 private fun norm(s:String):String{var n=s.filter{it.isDigit()};if(n.startsWith("00255"))n=n.drop(2);return when{n.startsWith("255")&&n.length==12->n;n.startsWith("0")&&n.length==10->"255"+n.drop(1);n.length==9&&(n[0]=='6'||n[0]=='7')->"255$n";else->n}}
 private fun scan(){status.text="Inachambua contacts...";progress.visibility=View.VISIBLE;progress.isIndeterminate=true;clean.isEnabled=false;thread{val a=mutableListOf<Entry>();val pr=arrayOf(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID,ContactsContract.CommonDataKinds.Phone.CONTACT_ID,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,ContactsContract.CommonDataKinds.Phone.NUMBER);contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,pr,null,null,null)?.use{c->while(c.moveToNext()){val num=c.getString(3)?:continue;val n=norm(num);if(n.length>=7)a+=Entry(c.getLong(0),c.getLong(1),c.getString(2)?:"Bila jina",num,n)}};groups=a.groupBy{it.normalized}.values.map{it.distinctBy{e->e.rawId}}.filter{it.size>1};val extra=groups.sumOf{it.size-1};val preview=groups.take(80).joinToString("\n\n"){x->"${x.first().name}  •  ${x.first().normalized}\n${x.size} entries zimepatikana"};runOnUiThread{progress.visibility=View.GONE;totalText.text="${a.distinctBy{it.contactId}.size}\nContacts";duplicateText.text="$extra\nDuplicates";status.text=if(extra==0)"✓ Hakuna duplicates zilizopatikana." else "Tumepata duplicates $extra katika makundi ${groups.size}. Kagua kisha safisha.";details.text=if(preview.isBlank())"Simu yako iko safi." else preview;clean.isEnabled=extra>0}}}
 private fun confirm(){val n=groups.sumOf{it.size-1};AlertDialog.Builder(this).setTitle("Je, umeshafanya backup?").setMessage("Tumepata duplicates $n. Ngosha Cleaner itabaki na contact moja kwa kila namba.\n\nUmeshafanya backup ya contacts zako?").setNegativeButton("BADO, NITAFANYA BACKUP",null).setPositiveButton("NDIO, ENDELEA"){_,_->cleanSmart()}.show()}
 private fun cleanSmart(){clean.isEnabled=false;progress.visibility=View.VISIBLE;progress.isIndeterminate=false;progress.max=groups.size.coerceAtLeast(1);status.text="Usafishaji umeanza...";thread{var removed=0;var failed=0;groups.forEachIndexed{i,g->val keep=g.maxWithOrNull(compareBy<Entry>{if(it.name!="Bila jina")1 else 0}.thenBy{-it.rawId})?:g.first();g.filter{it.rawId!=keep.rawId}.forEach{d->try{val op=ContentProviderOperation.newDelete(ContactsContract.RawContacts.CONTENT_URI).withSelection("${ContactsContract.RawContacts._ID}=?",arrayOf(d.rawId.toString())).build();contentResolver.applyBatch(ContactsContract.AUTHORITY,arrayListOf(op));removed++}catch(_:Exception){failed++}};if(i%10==0||i==groups.lastIndex)runOnUiThread{progress.progress=i+1;val pct=((i+1)*100/groups.size.coerceAtLeast(1));status.text="Inasafisha... $pct%  •  $removed zimeondolewa"}};runOnUiThread{progress.visibility=View.GONE;deletedText.text="$removed\nZimeondolewa";status.text=if(failed==0)"✓ Imekamilika. $removed duplicates zimeondolewa." else "Imekamilika. $removed zimeondolewa, $failed hazikubadilishwa.";details.text="Usafishaji umekamilika. Bonyeza KAGUA CONTACTS kufanya verification.";groups=emptyList();clean.isEnabled=false}}}
}
