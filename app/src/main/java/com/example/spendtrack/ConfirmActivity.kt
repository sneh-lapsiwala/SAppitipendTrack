package com.example.spendtrack
import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import kotlinx.coroutines.*

class ConfirmActivity:Activity(){
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 override fun onCreate(b:Bundle?){super.onCreate(b)
  val amount=intent.getDoubleExtra("amount",0.0);val merchant=intent.getStringExtra("merchant")?:"Kotak UPI";val hash=intent.getStringExtra("hash")
  val box=LinearLayout(this);box.orientation=LinearLayout.VERTICAL;box.setPadding(40,30,40,20)
  fun tv(s:String)=TextView(this).apply{text=s;textSize=18f}
  box.addView(tv("New expense detected"))
  box.addView(tv("₹${"%.2f".format(amount)}"))
  box.addView(tv(merchant))
  val cats=Spinner(this);cats.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,CATS);box.addView(cats)
  val row=LinearLayout(this);row.gravity=Gravity.END
  val ignore=Button(this).apply{text="Ignore";setOnClickListener{finish()}}
  val add=Button(this).apply{text="Add";setOnClickListener{
    val e=Expense(amount=amount,merchant=merchant,category=cats.selectedItem.toString(),date="Today",source="KOTAK",notificationHash=hash)
    scope.launch(Dispatchers.IO){AppDb.get(this@ConfirmActivity).dao().insert(e);runOnUiThread{finish()}}
  }}
  row.addView(ignore);row.addView(add);box.addView(row);setContentView(box)
 }
 override fun onDestroy(){scope.cancel();super.onDestroy()}
}
