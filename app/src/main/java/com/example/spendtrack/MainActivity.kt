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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val CATS = listOf("Cafe / Matcha", "Breakfast", "Lunch", "Dinner", "Local Food / Snacks", "Petrol", "Medicine", "Ganesh / Festival", "Family / Gifts", "Shopping", "Entertainment", "Other")

class MainActivity : ComponentActivity() {
    private var pendingEditId = mutableStateOf<Long?>(null)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        handleIntent(intent)
        setContent {
            MaterialTheme {
                App(pendingEditId = pendingEditId)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val id = intent?.getLongExtra("EDIT_EXPENSE_ID", -1L) ?: -1L
        if (id != -1L) {
            pendingEditId.value = id
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(pendingEditId: MutableState<Long?>) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val dao = remember { AppDb.get(ctx).dao() }
    val scope = rememberCoroutineScope()
    
    // 1. Reactive DB stream - updates automatically on new insertions
    val list by dao.all().collectAsState(initial = emptyList())
    
    // 2. Real-time balance calculations
    val totalBudget = 9000.0
    val spent = list.sumOf { it.amount }
    val remaining = totalBudget - spent

    // Auto-open edit dialog if triggered from the notification banner
    LaunchedEffect(pendingEditId.value, list) {
        val targetId = pendingEditId.value
        if (targetId != null && list.isNotEmpty()) {
            val found = list.find { it.id == targetId }
            if (found != null) {
                pendingEditId.value = null
            }
        }
    }

    var add by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Expense?>(null) }
    val currentMonth = remember { SimpleDateFormat("MMMM", Locale.getDefault()).format(Date()) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("SpendTrack • $currentMonth") })
        }
    ) { p ->
        Column(Modifier.padding(p).padding(16.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text("Budget ₹${"%.2f".format(totalBudget)}", style = MaterialTheme.typography.titleLarge)
                    Text("Spent ₹${"%.2f".format(spent)}")
                    Text("Remaining ₹${"%.2f".format(remaining)}")
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                Button(onClick = { add = true }, modifier = Modifier.weight(1f)) {
                    Text("+ Expense")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Kotak access")
                }
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn {
                items(list, key = { it.id }) { e ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(Modifier.padding(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("₹${"%.2f".format(e.amount)} • ${e.merchant}")
                                Text("${e.category} • ${e.date}")
                            }
                            TextButton(onClick = { edit = e }) {
                                Text("Edit")
                            }
                            TextButton(
                                onClick = { scope.launch { dao.delete(e) } },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Delete")
                            }
                        }
                    }
                }
            }
        }
    }

    if (add) {
        val today = remember { SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date()) }
        ExpenseDialog(
            old = null,
            defaultDate = today,
            close = { add = false },
            onDelete = null
        ) { e ->
            scope.launch {
                dao.insert(e)
                add = false
            }
        }
    }

    edit?.let { old ->
        ExpenseDialog(
            old = old,
            defaultDate = old.date,
            close = { edit = null },
            onDelete = {
                scope.launch {
                    dao.delete(old)
                    edit = null
                }
            }
        ) { e ->
            scope.launch {
                dao.update(e)
                edit = null
            }
        }
    }
}

@Composable
fun ExpenseDialog(
    old: Expense?,
    defaultDate: String,
    close: () -> Unit,
    onDelete: (() -> Unit)? = null,
    save: (Expense) -> Unit
) {
    var amount by remember { mutableStateOf(old?.amount?.toString() ?: "") }
    var merchant by remember { mutableStateOf(old?.merchant ?: "") }
    var cat by remember { mutableStateOf(old?.category ?: "Other") }
    var date by remember { mutableStateOf(old?.date ?: defaultDate) }
    var open by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (old == null) "Add expense" else "Edit expense") },
        text = {
            Column {
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount") })
                OutlinedTextField(value = merchant, onValueChange = { merchant = it }, label = { Text("Merchant") })
                OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date") })
                Spacer(Modifier.height(8.dp))
                Box {
                    OutlinedButton(onClick = { open = true }) {
                        Text(cat)
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        CATS.forEach { c ->
                            DropdownMenuItem(text = { Text(c) }, onClick = { cat = c; open = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                }
                TextButton(onClick = {
                    save(Expense(old?.id ?: 0, amount.toDoubleOrNull() ?: 0.0, merchant, cat, date, old?.source ?: "MANUAL", old?.notificationHash))
                }) {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = close) {
                Text("Cancel")
            }
        }
    )
}
