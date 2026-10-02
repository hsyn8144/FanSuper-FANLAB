package fan.lightningroulette

import android.app.Application

class LrApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
    companion object { lateinit var instance: LrApp; private set }
}
