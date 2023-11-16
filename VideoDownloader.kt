package com.kiwe.app.ak.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import com.kiwe.app.utils.Constant
import com.kiwe.app.utils.Log
import com.kiwe.app.utils.Sharedpre
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException


/**
 * @Created by akash on 11/16/2023.
 * Know more about author on <a href="https://akash.cloudemy.in">...</a>
 */
class VideoDownloader(private val context: Context) {
    private var downloadID: Long = 0
    fun downloadVideo(url: String): Long {

        val fileName: String = url.substring(url.lastIndexOf("/") + 1)
        val file = File(
            context.filesDir,
            fileName
        )
        if (file.exists()) {
            Log.w("VideoDownloader", "downloadVideo: Already downloaded")
            return 0L
        }
        val downloadUri = Uri.parse(url)
        val request = DownloadManager.Request(downloadUri)
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        request.setTitle("Downloading")
        request.setDescription("KiWE Promo Video")
        request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        context.registerReceiver(
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        );
        downloadID = downloadManager.enqueue(request) // enqueue puts the download request in the queue.
        return downloadID
    }

    @OptIn(DelicateCoroutinesApi::class)
    private val downloadReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == downloadID) {
                // Download completed
                GlobalScope.launch(Dispatchers.IO) {
                    val url = Sharedpre.getPrefrence(context, Constant.INTRO_VIDEO_URL)
                    val fileName: String = url.substring(url.lastIndexOf("/") + 1)
                    // Move the downloaded file to internal storage
                    moveFileToInternalStorage(downloadID, fileName)
                }

                // Unregister the receiver to avoid memory leaks
                context.unregisterReceiver(this)
            }
        }
    }

    // Example of how you might move the file to internal storage
    fun moveFileToInternalStorage(downloadId: Long, fileName: String) {
        // Assuming this method is called in a BroadcastReceiver on download completion
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor =
            (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).query(query)

        if (cursor.moveToFirst()) {
            val columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (DownloadManager.STATUS_SUCCESSFUL == cursor.getInt(columnIndex)) {
                val index = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                val uri = Uri.parse(cursor.getString(index))
                val sourceFile = File(uri.path!!)

                // Move the file to internal storage
                val destinationFile = File(context.filesDir, fileName)
                try {
                    copyFile(sourceFile, destinationFile)
                    // Delete the source file if needed
                    sourceFile.delete()

                    // File moved successfully
                    Log.w("VideoDownloader", "Video downloaded and saved")
                } catch (e: IOException) {
                    e.printStackTrace()
                    // Error moving file
                    Log.w("VideoDownloader", "Error moving file")
                }

            }
        }
        cursor.close()
    }

    @Throws(IOException::class)
    private fun copyFile(sourceFile: File, destinationFile: File) {
        FileInputStream(sourceFile).use { inputStream ->
            FileOutputStream(destinationFile).use { outputStream ->
                val buffer = ByteArray(1024)
                var length: Int
                while (inputStream.read(buffer).also { length = it } > 0) {
                    outputStream.write(buffer, 0, length)
                }
            }
        }
    }
}