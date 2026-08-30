package com.example.spendtrack
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

val CATS=listOf("Cafe / Matcha","Breakfast","Lunch","Dinner","Local Food / Snacks","Petrol","Medicine","Ganesh / Festival","Family / Gifts","Shopping","Entertainment","Other")

class MainActivity:ComponentActivity(){
 override fun onCreate(b:Bundle?){super.onCreate(b); setContent{App()}}
}
@Composable fun App(){
 val ctx=androidx.compose.ui.platform.LocalContext.current
 val dao=remember{AppDb.get(ctx).dao()}; val scope=rememberCoroutineScope()
 val list by dao.all().collectAsState(initial=emptyList())
 var add by remember{mutableStateOf(false)}; var edit by remember{mutableStateOf<Expense?>(null)}
 val spent=list.sumOf{it.amount}
 Scaffold(topBar={TopAppBar(title={Text("SpendTrack • September")})}){p->Column(Modifier.padding(p).padding(16.dp)){
  Card(Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp)){Text("Budget ₹9,000",style=MaterialTheme.typography.titleLarge);Text("Spent ₹${"%.2f".format(spent)}");Text("Remaining ₹${"%.2f".format(9000-spent)}")}}
  Spacer(Modifier.height(10.dp))
  Row(Modifier.fillMaxWidth()){Button({add=true},Modifier.weight(1f)){Text("+ Expense")};Spacer(Modifier.width(8.dp));Button({ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))},Modifier.weight(1f)){Text("Kotak access")}}
  Spacer(Modifier.height(10.dp));LazyColumn{items(list,key={it.id}){e->Card(Modifier.fillMaxWidth().padding(3.dp)){Row(Modifier.padding(12.dp)){Column(Modifier.weight(1f)){Text("₹${"%.2f".format(e.amount)} • ${e.merchant}");Text("${e.category} • ${e.date}")};TextButton({edit=e}){Text("Edit")}}}}}
 }}
 if(add) ExpenseDialog(null,{add=false}){e->scope.launch{dao.insert(e);add=false}}
 edit?.let{old->ExpenseDialog(old,{edit=null}){e->scope.launch{dao.update(e);edit=null}}}
}
@Composable fun ExpenseDialog(old:Expense?,close:()->Unit,save:(Expense)->Unit){
 var amount by remember{mutableStateOf(old?.amount?.toString()?:"")};var merchant by remember{mutableStateOf(old?.merchant?:"")}
 var cat by remember{mutableStateOf(old?.category?:"Other")};var date by remember{mutableStateOf(old?.date?:"Today")};var open by remember{mutableStateOf(false)}
 AlertDialog(onDismissRequest=close,title={Text(if(old==null)"Add expense" else "Edit expense")},text={Column{
  OutlinedTextField(amount,{amount=it},label={Text("Amount")});OutlinedTextField(merchant,{merchant=it},label={Text("Merchant")})
  OutlinedTextField(date,{date=it},label={Text("Date")});Box{OutlinedButton({open=true}){Text(cat)};DropdownMenu(open,{open=false}){CATS.forEach{c->DropdownMenuItem(text={Text(c)},onClick={cat=c;open=false})}}}
 }},confirmButton={TextButton({save(Expense(old?.id?:0,amount.toDoubleOrNull()?:0.0,merchant,cat,date,old?.source?:"MANUAL",old?.notificationHash))}){Text("Save")}},dismissButton={TextButton(close){Text("Cancel")}})
}
