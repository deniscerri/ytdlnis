package com.deniscerri.ytdlnis.receiver

import android.content.*
import android.os.IBinder
import com.deniscerri.ytdlnis.DownloaderService
import com.deniscerri.ytdlnis.DownloaderService.LocalBinder
import com.deniscerri.ytdlnis.service.IDownloaderService

class NotificationReceiver : BroadcastReceiver() {
    var downloaderService: DownloaderService? = null
    private var iDownloaderService: IDownloaderService? = null
    private var context: Context? = null
    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            downloaderService = (service as LocalBinder).service
            iDownloaderService = service
            cancelDownload()
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            downloaderService = null
            iDownloaderService = null
        }
    }

    override fun onReceive(c: Context, intent: Intent) {
        context = c
        val message = intent.getStringExtra("cancel")
        if (message != null) {
            val serviceIntent = Intent(context!!.applicationContext, DownloaderService::class.java)
            serviceIntent.putExtra("rebind", true)
            context!!.applicationContext.bindService(
                serviceIntent,
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
        }
    }

    private fun cancelDownload() {
        try {
            iDownloaderService!!.cancelDownload(true)
            context!!.applicationContext.unbindService(serviceConnection)
            context!!.applicationContext.stopService(
                Intent(
                    context!!.applicationContext,
                    DownloaderService::class.java
                )
            )
        } catch (ignored: Exception) {
        }
    }
}