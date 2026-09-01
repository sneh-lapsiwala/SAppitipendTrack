package com.example.spendtrack

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

val CATS = listOf("Cafe / Matcha", "Breakfast", "Lunch", "Dinner", "Local Food / Snacks", "Petrol", "Medicine", "Ganesh / Festival", "Family / Gifts", "Shopping", "Entertainment", "Other")

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E3A8A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = Color(0xFF0D9488),
    onSecondary = Color.White,
    surface = Color(0xFFF8FAFC),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF334155),
    error = Color(0xFFDC2626)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF93C5FD),
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF1E293B),
    onPrimaryContainer = Color(0xFF93C5FD),
    secondary = Color(0xFF2DD4BF),
    onSecondary = Color(0xFF0F172A),
    surface = Color(0xFF0F172A),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFFCBD5E1),
    error = Color(0xFFF87171)
)

class MainActivity : ComponentActivity() {
    private var pendingEditId = mutableStateOf<Long?>(null)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        handleIntent(intent)
        setContent {
            val isDark = isSystemInDarkTheme()
            val colors = if (isDark) DarkColors else LightColors

            MaterialTheme(colorScheme = colors) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    App(pendingEditId = pendingEditId)
                }
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
    val prefs = remember { ctx.getSharedPreferences("spendtrack_prefs", Context.MODE_PRIVATE) }
    val dao = remember { AppDb.get(ctx).dao() }
    val scope = rememberCoroutineScope()

    // 1. Dynamic Budget State (Saved across app restarts)
    var totalBudget by remember { mutableDoubleStateOf(prefs.getFloat("monthly_budget", 9000f).toDouble()) }
    var showBudgetDialog by remember { mutableStateOf(false) }

    // 2. Month Selector State
    var selectedCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    val monthYearFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val monthFilterKey = remember { SimpleDateFormat("MM-yyyy", Locale.getDefault()) }
    val currentMonthKey = monthFilterKey.format(selectedCalendar.time)

    // 3. Filter DB items by selected month
    val allList by dao.all().collectAsState(initial = emptyList())
    val filteredList = remember(allList, currentMonthKey) {
        allList.filter { it.date.endsWith(currentMonthKey) }
    }

    val spent = filteredList.sumOf { it.amount }
    val remaining = totalBudget - spent

    LaunchedEffect(pendingEditId.value, allList) {
        val targetId = pendingEditId.value
        if (targetId != null && allList.isNotEmpty()) {
            val found = allList.find { it.id == targetId }
            if (found != null) {
                pendingEditId.value = null
            }
        }
    }

    var add by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Expense?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SpendTrack") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { p ->
        Column(Modifier.padding(p).padding(16.dp)) {
            // Month Navigation Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    val cal = Calendar.getInstance().apply { time = selectedCalendar.time }
                    cal.add(Calendar.MONTH, -1)
                    selectedCalendar = cal
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Month")
                }

                Text(
                    text = monthYearFormat.format(selectedCalendar.time),
                    style = MaterialTheme.typography.titleMedium
                )

                IconButton(onClick = {
                    val cal = Calendar.getInstance().apply { time = selectedCalendar.time }
                    cal.add(Calendar.MONTH, 1)
                    selectedCalendar = cal
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Month")
                }
            }

            Spacer(Modifier.height(8.dp))

            // Dynamic Budget Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Budget ₹${"%.2f".format(totalBudget)}", style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = { showBudgetDialog = true }) {
                            Text("Edit Limit")
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Spent ₹${"%.2f".format(spent)}", style = MaterialTheme.typography.bodyLarge)
                    Text("Remaining ₹${"%.2f".format(remaining)}", style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth()) {
                Button(
                    onClick = { add = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("+ Expense")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Kotak access")
                }
            }

            Spacer(Modifier.height(12.dp))

            // Filtered Month Expense List
            LazyColumn {
                items(filteredList, key = { it.id }) { e ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Row(Modifier.padding(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("₹${"%.2f".format(e.amount)} • ${e.merchant}", style = MaterialTheme.typography.titleMedium)
                                Text("${e.category} • ${e.date}", style = MaterialTheme.typography.bodySmall)
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

    // Budget Limit Customization Dialog
    if (showBudgetDialog) {
        var tempBudget by remember { mutableStateOf(totalBudget.toInt().toString()) }
        AlertDialog(
            onDismissRequest = { showBudgetDialog = false },
            title = { Text("Set Monthly Budget") },
            text = {
                OutlinedTextField(
                    value = tempBudget,
                    onValueChange = { tempBudget = it },
                    label = { Text("Budget Amount (₹)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = tempBudget.toDoubleOrNull() ?: totalBudget
                    totalBudget = parsed
                    prefs.edit().putFloat("monthly_budget", parsed.toFloat()).apply()
                    showBudgetDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBudgetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Merchant") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (dd-MM-yyyy)") },
                    modifier = Modifier.fillMaxWidth()
                )
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
