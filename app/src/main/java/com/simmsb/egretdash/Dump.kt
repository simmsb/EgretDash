package com.simmsb.egretdash

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat.startActivityForResult
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.IOException
import java.util.Date
import kotlin.time.Clock
import kotlin.time.Instant

@Composable
fun DumpScreen(db: DashboardDatabase) {
    val coroutineScope = rememberCoroutineScope()
    val appContext = LocalContext.current

    val fileSaverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-sqlite3"),
        onResult = { uri ->
            // The onResult lambda is executed when the user selects a file location or cancels.
            // The 'uri' will be null if the user cancels the operation.
            if (uri != null) {
                coroutineScope.launch {
                    try {
                        // Use the ContentResolver to open an OutputStream to the selected URI.
                        appContext.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            val dump = db.dump(appContext)
                            outputStream.write(dump)
                            Toast.makeText(appContext, "Saved dump", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: IOException) {
                        // Handle potential I/O errors, e.g., show a toast to the user.
                        e.printStackTrace()
                    }
                }
            }
        }
    )
    Button(onClick = {
        coroutineScope.launch {
            val now = Clock.System.now()
            val tz = TimeZone.currentSystemDefault()
            val today = now.toLocalDateTime(tz)
            val destFileName = "egret-dash-${today.format(LocalDateTime.Formats.ISO)}.db"
            fileSaverLauncher.launch(destFileName)
        }
    }) {
        Text("Save")
    }
}