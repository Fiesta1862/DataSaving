package com.fiesta.datasaving

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val SMART_APP_PACKAGE = "com.smart.consumer.app"
private const val SMART_APP_URL = "https://play.google.com/store/apps/details?id=com.smart.consumer.app"
private const val SMART_ROLLOVER_URL = "https://help.smart.com.ph/how-to-keep-your-unused-data-with-big-data-rollover/"
private const val SMART_PASADATA_URL = "https://help.smart.com.ph/how-to-do-pasadata/"

@Composable
fun SmartCarrierScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("smart_carrier", Context.MODE_PRIVATE) }
    var promoName by remember { mutableStateOf(prefs.getString("promo_name", "") ?: "") }
    var promoFamily by remember { mutableStateOf(prefs.getString("promo_family", "") ?: "") }
    var totalGb by remember { mutableStateOf(prefs.getString("total_gb", "") ?: "") }
    var remainingGb by remember { mutableStateOf(prefs.getString("remaining_gb", "") ?: "") }
    var expiryDate by remember { mutableStateOf(prefs.getString("expiry_date", "") ?: "") }
    var confirmedBigData by remember { mutableStateOf(prefs.getBoolean("confirmed_big_data", false)) }
    var receiver by remember { mutableStateOf("") }
    var shareMb by remember { mutableStateOf("50") }
    var showShareConfirm by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        message = if (granted) {
            scheduleSmartExpiryReminder(context, expiryDate)
            "Expiry reminder enabled for the saved promo."
        } else "Notifications are off. You can still see the expiry reminder inside DataSaving."
    }

    fun savePromo() {
        val total = totalGb.toDoubleOrNull()
        val remaining = remainingGb.toDoubleOrNull()
        val validDate = parseSmartDate(expiryDate) != null
        if (promoName.isBlank() || total == null || remaining == null || total <= 0.0 ||
            remaining < 0.0 || remaining > total || !validDate
        ) {
            message = "Enter a promo name, valid GB amounts, and expiry date as YYYY-MM-DD."
            return
        }
        prefs.edit()
            .putString("promo_name", promoName.trim())
            .putString("promo_family", promoFamily.trim())
            .putString("total_gb", totalGb)
            .putString("remaining_gb", remainingGb)
            .putString("expiry_date", expiryDate.trim())
            .putBoolean("confirmed_big_data", confirmedBigData)
            .apply()
        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            scheduleSmartExpiryReminder(context, expiryDate)
        }
        message = "Promo details saved on this device."
    }

    val remaining = remainingGb.toDoubleOrNull()
    val total = totalGb.toDoubleOrNull()
    val expiry = parseSmartDate(expiryDate)
    val daysUntilExpiry = expiry?.let { ((it.time - System.currentTimeMillis()) / 86_400_000L).toInt() }
    val expiryNotice = when {
        daysUntilExpiry == null -> "Add an expiry date to track your promo."
        daysUntilExpiry < 0 -> "This recorded promo expiry date has passed. Verify your current status in the Smart App."
        daysUntilExpiry == 0 -> "Your recorded promo expires today. Check the Smart App now."
        daysUntilExpiry <= 3 -> "Your recorded promo expires in $daysUntilExpiry day(s). Check renewal eligibility soon."
        else -> "Recorded expiry: $expiryDate ($daysUntilExpiry days remaining)."
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Smart / TNT account tools", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Manual tracking plus shortcuts to Smart's official services. DataSaving does not sign in to your carrier account or read your live balance.",
            color = Color(0xFF81909D),
            style = MaterialTheme.typography.bodySmall
        )

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B3B35)), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("MY RECORDED DATA", color = Color(0xFFB8D8CD), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (remaining != null) "${remaining} GB" else "No balance entered",
                    color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold
                )
                if (total != null && total > 0) {
                    Text("of ${total} GB recorded", color = Color(0xFFC3D5D0))
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { ((remaining ?: 0.0) / total).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF20C78A),
                        trackColor = Color(0xFF42635A)
                    )
                }
                Text(expiryNotice, color = Color(0xFFC3D5D0), style = MaterialTheme.typography.bodySmall)
            }
        }

        Text("Manual promo tracker", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(promoName, { promoName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Promo name") }, singleLine = true)
        OutlinedTextField(promoFamily, { promoFamily = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Promo family (e.g. Big Data)") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(totalGb, { totalGb = it }, modifier = Modifier.weight(1f), label = { Text("Total GB") }, singleLine = true)
            OutlinedTextField(remainingGb, { remainingGb = it }, modifier = Modifier.weight(1f), label = { Text("Remaining GB") }, singleLine = true)
        }
        OutlinedTextField(expiryDate, { expiryDate = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Expiry date (YYYY-MM-DD)") }, singleLine = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = confirmedBigData, onCheckedChange = { confirmedBigData = it })
            Text("I verified this is an eligible Smart Big Data promo", style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = { savePromo() }, modifier = Modifier.fillMaxWidth()) {
            androidx.compose.material3.Icon(Icons.Default.Save, contentDescription = null)
            Spacer(Modifier.padding(start = 3.dp))
            Text("Save promo details")
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17232E)), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(Icons.Default.Refresh, null, tint = Color(0xFF20C78A))
                    Text(" Big Data rollover", fontWeight = FontWeight.Bold)
                }
                Text(
                    "Smart says eligible Big Data base data can roll over when you renew or auto-renew within the same Big Data promo family before expiry. Daily app-specific allocations do not roll over. Confirm your exact promo in the Smart App.",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFB7C3CC)
                )
                Text("DataSaving only tracks your dates and details; it cannot renew a promo or guarantee rollover.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF81909D))
                OutlinedButton(onClick = { openExternal(context, SMART_ROLLOVER_URL) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Read Smart's rollover rules")
                    Spacer(Modifier.weight(1f))
                    androidx.compose.material3.Icon(Icons.Default.OpenInNew, null)
                }
                OutlinedButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        scheduleSmartExpiryReminder(context, expiryDate)
                        message = "Expiry reminder scheduled if a valid future date is saved."
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.Icon(Icons.Default.NotificationsActive, null)
                    Text(" Enable expiry notification")
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17232E)), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(Icons.Default.Send, null, tint = Color(0xFF20C78A))
                    Text(" PasaData assistant", fontWeight = FontWeight.Bold)
                }
                Text(
                    "For eligible shareable data only. Smart's guide describes a 50 MB minimum, a ₱1 fee per successful transfer, and 24-hour validity. Daily app bundles and received PasaData cannot be passed on.",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFB7C3CC)
                )
                OutlinedTextField(receiver, { receiver = it.filter(Char::isDigit).take(11) }, modifier = Modifier.fillMaxWidth(), label = { Text("Recipient Smart/TNT number") }, singleLine = true)
                OutlinedTextField(shareMb, { shareMb = it.filter(Char::isDigit).take(6) }, modifier = Modifier.fillMaxWidth(), label = { Text("Amount in MB (minimum 50)") }, singleLine = true)
                Button(
                    onClick = { showShareConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = receiver.length == 11 && receiver.startsWith("09") && (shareMb.toIntOrNull() ?: 0) >= 50
                ) {
                    Text("Review PasaData SMS")
                }
                OutlinedButton(onClick = { openExternal(context, SMART_PASADATA_URL) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Read official PasaData instructions")
                    Spacer(Modifier.weight(1f))
                    androidx.compose.material3.Icon(Icons.Default.OpenInNew, null)
                }
            }
        }

        Button(onClick = { openSmartApp(context) }, modifier = Modifier.fillMaxWidth()) {
            Text("Open official Smart App")
            Spacer(Modifier.weight(1f))
            androidx.compose.material3.Icon(Icons.Default.OpenInNew, null)
        }
        Text("Smart App is used for live balances, promo registration, and carrier-side account actions. DataSaving never stores your Smart password.", color = Color(0xFF81909D), style = MaterialTheme.typography.bodySmall)
        if (message.isNotBlank()) Text(message, color = Color(0xFF20C78A), style = MaterialTheme.typography.bodySmall)
    }

    if (showShareConfirm) {
        AlertDialog(
            onDismissRequest = { showShareConfirm = false },
            title = { Text("Review PasaData request") },
            text = {
                Text("DataSaving will open your SMS app with this draft addressed to 808:\n\nPASADATA $receiver ${shareMb}MB\n\nSmart may charge ₱1 for a successful transaction. Review the number, amount, and current Smart rules before sending.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showShareConfirm = false
                    val sms = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:808")).apply {
                        putExtra("sms_body", "PASADATA $receiver ${shareMb}MB")
                    }
                    runCatching { context.startActivity(sms) }
                        .onFailure { message = "No SMS app is available to prepare the PasaData draft." }
                }) { Text("Open SMS draft") }
            },
            dismissButton = { TextButton(onClick = { showShareConfirm = false }) { Text("Cancel") } }
        )
    }
}

private fun openSmartApp(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(SMART_APP_PACKAGE)
    if (launch != null) {
        runCatching { context.startActivity(launch) }
    } else {
        openExternal(context, SMART_APP_URL)
    }
}

private fun openExternal(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

private fun parseSmartDate(value: String): Date? = runCatching {
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(value.trim())
}.getOrNull()

private fun scheduleSmartExpiryReminder(context: Context, dateText: String) {
    val expiry = parseSmartDate(dateText) ?: return
    val calendar = Calendar.getInstance().apply {
        time = expiry
        set(Calendar.HOUR_OF_DAY, 9)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val reminderAt = calendar.timeInMillis - TimeUnit.DAYS.toMillis(1)
    val delay = (reminderAt - System.currentTimeMillis()).coerceAtLeast(TimeUnit.MINUTES.toMillis(1))
    val request = OneTimeWorkRequestBuilder<SmartPromoExpiryWorker>()
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
        "smart_promo_expiry_reminder",
        androidx.work.ExistingWorkPolicy.REPLACE,
        request
    )
}
