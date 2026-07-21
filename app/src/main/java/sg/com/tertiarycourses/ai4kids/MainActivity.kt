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
            val consent = remember { ConsentStore(applicationContext) }
            AI4KidsTheme {
                CompositionLocalProvider(LocalProgressStore provides progress) {
                    if (!consent.granted) {
                        // First-launch parental consent — a grown-up must confirm
                        // before the child can use the app (Families Policy).
                        ParentalGate(
                            headerTitle = "Welcome to AI4Kids",
                            cardTitle = "Parental consent",
                            message = "AI4Kids is a learning app for ages 4–16. The activities " +
                                "play fully offline with no ads and no personal data collected. " +
                                "The optional online \"Brain Arcade\" lets children play card games " +
                                "with friends using a username-only kid account — no name, email, " +
                                "phone number, or location is ever collected.\n\n" +
                                "A parent or guardian: please confirm you consent to your child " +
                                "using AI4Kids by solving the problem below.",
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
}
