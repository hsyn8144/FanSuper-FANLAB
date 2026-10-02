package fan.lightningroulette

import android.app.Application
import fan.lightningroulette.engine.Engine

class LrApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        Engine.init(this)          // Room + (isteğe bağlı) Python + kilitli tahmin: arka plan motor iş parçacığında
    }
    companion object { lateinit var instance: LrApp; private set }
}
