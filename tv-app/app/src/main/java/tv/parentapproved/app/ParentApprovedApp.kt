package tv.parentapproved.app

import android.app.Application
import tv.parentapproved.app.util.NewPipeDownloader
import org.schabi.newpipe.extractor.NewPipe

class ParentApprovedApp : Application() {
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
