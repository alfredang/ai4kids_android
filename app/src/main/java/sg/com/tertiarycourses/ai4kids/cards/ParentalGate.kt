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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random
import sg.com.tertiarycourses.ai4kids.ui.components.CloseButton
import sg.com.tertiarycourses.ai4kids.ui.components.KidButton
import sg.com.tertiarycourses.ai4kids.ui.components.kidCard
import sg.com.tertiarycourses.ai4kids.ui.theme.Theme

/**
 * A reusable **parental gate / consent form**. It shows a grown-up exactly what
 * the app (or a feature) does with data, then asks them to confirm consent by
 * solving a short arithmetic challenge that a young child can't — satisfying
 * Google Play Families Policy's requirement for verifiable parental consent
 * before a child uses the app or before any data is collected.
 *
 * @param headerTitle  small heading at the top of the screen.
 * @param cardTitle    bold title inside the consent card.
 * @param message      the consent explanation shown to the parent/guardian.
 * @param confirmLabel label for the confirm button.
 * @param onClose      shows a close button when non-null (e.g. the per-feature
 *                     gate can be dismissed); pass null for the first-launch
 *                     consent that must be answered to continue.
 * @param onConsent    invoked once the challenge is solved correctly.
 */
@Composable
fun ParentalGate(
    headerTitle: String,
    cardTitle: String,
    message: String,
    confirmLabel: String,
    onClose: (() -> Unit)? = null,
    onConsent: () -> Unit,
) {
    // A fresh challenge each time the gate is shown, so it can't be memorised.
    var a by remember { mutableIntStateOf(Random.nextInt(3, 10)) }
    var b by remember { mutableIntStateOf(Random.nextInt(11, 20)) }
    var answer by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun verify() {
        if (answer.trim().toIntOrNull() == a * b) {
            onConsent()
        } else {
            error = "That's not quite right. Please ask a parent or guardian to help."
            answer = ""
            a = Random.nextInt(3, 10)
            b = Random.nextInt(11, 20)
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
                if (onClose != null) CloseButton(onClick = onClose)
                Spacer(Modifier.weight(1f))
                Text(headerTitle, color = Theme.Ink, fontSize = 22.sp, fontWeight = FontWeight.Black)
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
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(64.dp)
                        .background(Theme.Purple.copy(alpha = 0.12f), RoundedCornerShape(20.dp)),
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = Theme.Purple, modifier = Modifier.size(32.dp))
                }

                Text(cardTitle, color = Theme.Ink, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text(
                    message,
                    color = Theme.Ink.copy(alpha = 0.65f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )

                Text("$a × $b = ?", color = Theme.Ink, fontSize = 30.sp, fontWeight = FontWeight.Black)

                OutlinedTextField(
                    value = answer,
                    onValueChange = { new -> answer = new.filter { it.isDigit() }; error = null },
                    singleLine = true,
                    label = { Text("Answer") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (error != null) {
                    Text(error!!, color = Theme.Red, fontSize = 14.sp, textAlign = TextAlign.Center)
                }

                KidButton(
                    title = confirmLabel,
                    color = if (answer.isBlank()) Theme.Ink.copy(alpha = 0.3f) else Theme.Purple,
                    enabled = answer.isNotBlank(),
                    onClick = { verify() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.weight(1f))
        }
    }
}
