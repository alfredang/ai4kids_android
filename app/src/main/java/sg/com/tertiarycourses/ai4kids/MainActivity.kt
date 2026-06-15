package sg.com.tertiarycourses.ai4kids

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import sg.com.tertiarycourses.ai4kids.cards.CardApi
import sg.com.tertiarycourses.ai4kids.data.LocalProgressStore
import sg.com.tertiarycourses.ai4kids.data.ProgressStore
import sg.com.tertiarycourses.ai4kids.ui.RootScreen
import sg.com.tertiarycourses.ai4kids.ui.theme.AI4KidsTheme
import sg.com.tertiarycourses.ai4kids.ui.theme.Theme

/**
 * App entry point. AI4Kids — a fully on-device, no-login activity app for young
 * learners. A single shared [ProgressStore] is provided to every activity to
 * read and award stars. Android counterpart of the iOS `AI4KidsApp`.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        CardApi.init(applicationContext)
        setContent {
            val progress = remember { ProgressStore(applicationContext) }
            AI4KidsTheme {
                CompositionLocalProvider(LocalProgressStore provides progress) {
                    RootScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Theme.Background),
                    )
                }
            }
        }
    }
}
