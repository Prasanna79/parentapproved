package com.kidswatch.tv

import android.app.Application
import com.kidswatch.tv.util.NewPipeDownloader
import org.schabi.newpipe.extractor.NewPipe

class KidsWatchApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NewPipe.init(NewPipeDownloader.instance)
        ServiceLocator.init(this)
        // Only connect relay if user has enabled remote access
        if (ServiceLocator.isRelayEnabled()) {
            ServiceLocator.relayConnector.connect()
        }
    }
}
