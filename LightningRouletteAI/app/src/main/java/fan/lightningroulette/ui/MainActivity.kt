package fan.lightningroulette.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import fan.lightningroulette.engine.Engine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LrTheme { Root() } }
    }

    override fun onStop() {
        super.onStop()
        // Arka plana geçerken Python durumunu kaydet (yalnızca yeni kayıtlar sonra işlenir).
        if (Engine.ui.value.phase == "ready") Engine.scope.launch { try { Engine.py?.save() } catch (_: Exception) { } }
    }
}
