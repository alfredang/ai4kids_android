package sg.com.tertiarycourses.ai4kids.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sg.com.tertiarycourses.ai4kids.ui.components.CloseButton
import sg.com.tertiarycourses.ai4kids.ui.components.KidButton
import sg.com.tertiarycourses.ai4kids.ui.components.kidCard
import sg.com.tertiarycourses.ai4kids.ui.theme.Theme

/**
 * Learner sign-in for the online Brain Arcade. Authenticates against the
 * ai4kids backend via [CardApi.login]. The server address is editable so the
 * app can point at production or a local dev server (e.g. http://10.0.2.2:3080).
 */
@Composable
fun LoginScreen(onClose: () -> Unit, onLoggedIn: () -> Unit) {
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun submit() {
        if (busy || identifier.isBlank() || password.isBlank()) return
        busy = true
        error = null
        scope.launch {
            val err = withContext(Dispatchers.IO) { CardApi.login(identifier, password) }
            busy = false
            if (err == null) onLoggedIn() else error = err
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme.Background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                CloseButton(onClick = onClose)
                Spacer(Modifier.weight(1f))
                Text("🕹️ Brain Arcade", color = Theme.Ink, fontSize = 22.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.widthIn(min = 48.dp))
            }

            Spacer(Modifier.weight(1f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
                    .kidCard()
                    .padding(28.dp),
            ) {
                Text("🤖", fontSize = 56.sp)
                Text("Kid sign-in", color = Theme.Ink, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text(
                    "Sign in to play card games with your friends online.",
                    color = Theme.Ink.copy(alpha = 0.6f),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )

                OutlinedTextField(
                    value = identifier,
                    onValueChange = { identifier = it },
                    singleLine = true,
                    label = { Text("Username") },
                    placeholder = { Text("e.g. supernova") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (error != null) {
                    Text(error!!, color = Theme.Red, fontSize = 14.sp, textAlign = TextAlign.Center)
                }

                KidButton(
                    title = if (busy) "Signing in…" else "Sign in",
                    color = if (busy) Theme.Ink.copy(alpha = 0.3f) else Theme.Purple,
                    enabled = !busy && identifier.isNotBlank() && password.isNotBlank(),
                    onClick = { submit() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.weight(1f))
        }
    }
}