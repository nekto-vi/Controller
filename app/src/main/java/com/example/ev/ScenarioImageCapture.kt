package com.example.ev

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object ScenarioImageCapture {

    fun createImageUri(context: Context): Uri {
        val dir = File(context.cacheDir, "scenario_photos").apply { mkdirs() }
        val file = File.createTempFile("capture_", ".jpg", dir)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}
