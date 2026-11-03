package sg.com.tertiarycourses.ai4kids

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import sg.com.tertiarycourses.ai4kids.cards.CardApi
import sg.com.tertiarycourses.ai4kids.cards.ParentalGate
import sg.com.tertiarycourses.ai4kids.data.ConsentStore
import sg.com.tertiarycourses.ai4kids.data.LocalProgressStore
import sg.com.tertiarycourses.ai4kids.data.ParentAuth
import android.os.SystemClock
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
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
        ParentAuth.init(applicationContext)
        setContent {
            // AI Workshops session: auto-lock once its 30 minutes are up.
            val unlockedUntil = ParentAuth.unlockedUntil
            LaunchedEffect(unlockedUntil) {
                if (unlockedUntil > 0) {
                    delay((unlockedUntil - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
                    ParentAuth.lock()
                }
            }
            val progress = remember { ProgressStore(applicationContext) }
            val consent = remember { ConsentStore(applicationContext) }
            AI4KidsTheme {
                CompositionLocalProvider(LocalProgressStore provides progress) {
                    if (!consent.granted) {
                        // First-launch parental consent — a grown-up must confirm
                        // before the child can use the app (Families Policy).
                        ParentalGate(
                            headerTitle = "Welcome to AI4Kids",
                            cardTitle = "Parental consent",
                            message = "AI4Kids offers offline learning games with no ads or tracking. " +
                                "Optional online card games and co-op rooms need a parent sign-in " +
                                "and a linked child profile. Online sign-in sends the parent email " +
                                "and gameplay to our secure server. Optional AI Workshops stay off " +
                                "until a parent sets a PIN and adds their own DeepSeek key.\n\n" +
                                "A parent or guardian: please confirm before your child plays.",
                            confirmLabel = "I consent — let my child play",
                            onConsent = { consent.grant() },
                        )
                    } else {
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

    /** Leaving the app (background, another app, or the Escape Room activity)
     *  ends the AI Workshops parent session — the PIN is needed again. */
    override fun onStop() {
        super.onStop()
        ParentAuth.lock()
    }
}
