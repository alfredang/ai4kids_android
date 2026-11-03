package sg.com.tertiarycourses.ai4kids.ui.workshops

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sg.com.tertiarycourses.ai4kids.ai.DeepSeekClient
import sg.com.tertiarycourses.ai4kids.cards.ParentalGate
import sg.com.tertiarycourses.ai4kids.data.ParentAuth
import sg.com.tertiarycourses.ai4kids.data.PinHasher
import sg.com.tertiarycourses.ai4kids.ui.components.CloseButton
import sg.com.tertiarycourses.ai4kids.ui.components.KidButton
import sg.com.tertiarycourses.ai4kids.ui.components.KidSecondaryButton
import sg.com.tertiarycourses.ai4kids.ui.components.SunnySticker
import sg.com.tertiarycourses.ai4kids.ui.components.kidCard
import sg.com.tertiarycourses.ai4kids.ui.theme.Theme

/** What the parent is logging in to do. */
enum class ParentTask { UNLOCK, REPLACE_KEY, CHANGE_PIN }

private const val KEY_URL = "https://platform.deepseek.com/api_keys"

/**
 * **Parent Login** for the AI Workshops.
 *
 * - First time: a math parental gate, then one scrolling setup page — what is
 *   sent where, a 4–6 digit PIN (+ confirm), the parent's own DeepSeek key
 *   (validated with `GET /models`) and a required consent checkbox.
 * - Returning: an on-screen PIN pad. 5 wrong PINs → 60 s cooldown (persisted).
 *   "Forgot PIN?" wipes PIN + key + consent and returns to setup.
 * - [ParentTask.REPLACE_KEY] / [ParentTask.CHANGE_PIN] ask for the PIN first.
 *
 * Calls [onDone] once the task is complete (session unlocked). Mirrors the iOS
 * `ParentLoginView`.
 */
@Composable
fun ParentLoginScreen(task: ParentTask, onClose: () -> Unit, onDone: () -> Unit) {
    BackHandler(onBack = onClose)
    val configured = ParentAuth.isConfigured
    // Reading unlockedUntil subscribes us to lock/unlock changes.
    val unlocked = ParentAuth.unlockedUntil > 0 && ParentAuth.isUnlocked()

    when {
        !configured -> SetupFlow(onClose = onClose, onDone = onDone)
        !unlocked -> PinEntry(onClose = onClose)
        task == ParentTask.REPLACE_KEY || ParentAuth.keyNeedsReplacing ->
            ParentPage(title = "Replace DeepSeek key", onClose = onClose) { KeyForm(replace = true, onSaved = onDone) }
        task == ParentTask.CHANGE_PIN ->
            ParentPage(title = "Change parent PIN", onClose = onClose) { ChangePinForm(onSaved = onDone) }
        else -> LaunchedEffect(Unit) { onDone() }
    }
}

/* ------------------------------------------------------------------ */
/* Shared page chrome                                                  */
/* ------------------------------------------------------------------ */

@Composable
private fun ParentPage(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Theme.Background)
            .systemBarsPadding(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                CloseButton(onClick = onClose)
                Spacer(Modifier.weight(1f))
                Text(title, color = Theme.Ink, fontSize = 22.sp, fontWeight = FontWeight.Black, fontFamily = Theme.DisplayFont)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(56.dp))
            }
            Spacer(Modifier.height(18.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .widthIn(max = 620.dp)
                    .fillMaxWidth()
                    .kidCard()
                    .padding(26.dp),
            ) { content() }
        }
    }
}

@Composable
private fun StepTitle(text: String) {
    Text(text, color = Theme.Purple, fontSize = 20.sp, fontWeight = FontWeight.Black, fontFamily = Theme.DisplayFont)
}

@Composable
private fun Body(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Theme.BodyInk, fontSize = 16.sp, lineHeight = 22.sp, modifier = modifier)
}

@Composable
private fun ErrorText(text: String?) {
    if (text != null) Text(text, color = Theme.Red, fontSize = 15.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Theme.Purple,
    unfocusedBorderColor = Theme.InputBorder,
    focusedContainerColor = Theme.CardFill,
    unfocusedContainerColor = Theme.CardFill,
)

@Composable
private fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    numeric: Boolean,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.NumberPassword else KeyboardType.Password,
            autoCorrectEnabled = false,
        ),
        shape = Theme.InputShape,
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Opens platform.deepseek.com in the browser — only ever shown to a parent. */
@Composable
private fun GetKeyLink() {
    val context = LocalContext.current
    Text(
        "Get a key at platform.deepseek.com",
        color = Theme.Teal,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(KEY_URL)))
            } catch (_: ActivityNotFoundException) {
                // No browser — the text itself tells the parent where to go.
            }
        },
    )
}

private fun keyCheckError(result: DeepSeekClient.KeyCheck): String? = when (result) {
    DeepSeekClient.KeyCheck.Ok -> null
    DeepSeekClient.KeyCheck.Unauthorized ->
        "That key didn't work. Check you copied the whole key from platform.deepseek.com."
    is DeepSeekClient.KeyCheck.NetworkError ->
        "Couldn't reach DeepSeek. Check the internet connection and try again."
    is DeepSeekClient.KeyCheck.HttpError ->
        "DeepSeek couldn't check the key right now (error ${result.code}). Please try again in a moment."
}

/* ------------------------------------------------------------------ */
/* First-time setup                                                    */
/* ------------------------------------------------------------------ */

@Composable
private fun SetupFlow(onClose: () -> Unit, onDone: () -> Unit) {
    var gatePassed by remember { mutableStateOf(false) }
    if (!gatePassed) {
        ParentalGate(
            headerTitle = "AI Workshops",
            cardTitle = "Grown-ups only",
            message = "AI Workshops are switched off until a parent or guardian sets them up " +
                "with a PIN and their own DeepSeek API key.",
            confirmLabel = "I'm a grown-up — set up",
            onClose = onClose,
            onConsent = { gatePassed = true },
        )
        return
    }

    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun save() {
        error = when {
            !PinHasher.isValidPin(pin) -> "Your PIN needs 4 to 6 digits."
            pin != pin2 -> "The two PINs don't match."
            !DeepSeekClient.looksLikeKey(key) -> "Paste your DeepSeek API key — it starts with \"sk-\"."
            !consent -> "Please tick the consent box to continue."
            else -> null
        }
        if (error != null) return
        busy = true
        scope.launch {
            val result = DeepSeekClient.checkKey(key)
            busy = false
            error = keyCheckError(result)
            if (result == DeepSeekClient.KeyCheck.Ok) {
                ParentAuth.completeSetup(pin, key)
                pin = ""; pin2 = ""; key = ""
                onDone()
            }
        }
    }

    ParentPage(title = "Parent Login", onClose = onClose) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SunnySticker("🔒", 64)
            Column(Modifier.weight(1f)) {
                Text("Set up AI Workshops", color = Theme.Ink, fontSize = 26.sp, fontWeight = FontWeight.Black, fontFamily = Theme.DisplayFont)
                Body("Big ideas, together! This takes about two minutes.")
            }
        }

        StepTitle("How it works")
        Body(
            "AI Workshops send what your child types to DeepSeek (api.deepseek.com) to write replies. " +
                "Nothing else leaves this device. No account is created. Your key and PIN stay " +
                "encrypted on this device, and we never see them or your child's messages.",
        )

        StepTitle("1 · Create a parent PIN")
        SecretField(pin, { pin = it.filter(Char::isDigit).take(PinHasher.MAX_LENGTH); error = null }, "Parent PIN (4–6 digits)", numeric = true, enabled = !busy)
        SecretField(pin2, { pin2 = it.filter(Char::isDigit).take(PinHasher.MAX_LENGTH); error = null }, "Confirm PIN", numeric = true, enabled = !busy)

        StepTitle("2 · Add your DeepSeek API key")
        Body("Use your own key — AI4Kids doesn't include one. You pay DeepSeek directly for what the workshops use.")
        SecretField(key, { key = it.trim().take(200); error = null }, "DeepSeek API key (sk-…)", numeric = false, enabled = !busy)
        GetKeyLink()

        StepTitle("3 · Your consent")
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = !busy) { consent = !consent; error = null }
                .padding(vertical = 4.dp),
        ) {
            Checkbox(
                checked = consent,
                onCheckedChange = { consent = it; error = null },
                enabled = !busy,
                colors = CheckboxDefaults.colors(checkedColor = Theme.Purple),
            )
            Body(
                "I'm the parent/guardian. I agree my child's typed messages are sent to DeepSeek " +
                    "to generate replies, and I will supervise.",
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        ErrorText(error)
        KidButton(
            title = if (busy) "Checking key…" else "Check key & save",
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            onClick = { save() },
        )
    }
}

/* ------------------------------------------------------------------ */
/* Returning: PIN pad                                                  */
/* ------------------------------------------------------------------ */

@Composable
private fun PinEntry(onClose: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var cooldownMs by remember { mutableLongStateOf(ParentAuth.cooldownRemainingMs()) }
    var confirmForgot by remember { mutableStateOf(false) }

    // Count the cooldown down once a second.
    LaunchedEffect(cooldownMs > 0) {
        while (cooldownMs > 0) {
            delay(1000)
            cooldownMs = ParentAuth.cooldownRemainingMs()
        }
    }

    fun submit() {
        if (pin.length < PinHasher.MIN_LENGTH) return
        when (val r = ParentAuth.verifyPin(pin)) {
            ParentAuth.PinResult.Ok -> error = null
            is ParentAuth.PinResult.Wrong ->
                error = "That PIN isn't right. ${r.attemptsLeft} ${if (r.attemptsLeft == 1) "try" else "tries"} left."
            is ParentAuth.PinResult.CoolingDown -> {
                error = "Too many tries."
                cooldownMs = r.remainingMs
            }
        }
        pin = ""
    }

    val coolingDown = cooldownMs > 0
    ParentPage(title = "Parent Login", onClose = onClose) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            SunnySticker("🔒", 72)
            Spacer(Modifier.height(8.dp))
            Text("Grown-ups only", color = Theme.Ink, fontSize = 26.sp, fontWeight = FontWeight.Black, fontFamily = Theme.DisplayFont)
            Body("Enter your parent PIN to open AI Workshops.", modifier = Modifier.padding(top = 4.dp))
            if (ParentAuth.keyNeedsReplacing) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "The DeepSeek key stopped working. After the PIN you can paste a new key.",
                    color = Theme.Red,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(18.dp))

            // PIN dots.
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.semantics { contentDescription = "${pin.length} digits entered" },
            ) {
                repeat(PinHasher.MAX_LENGTH) { i ->
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(if (i < pin.length) Theme.Purple else Theme.CardFill)
                            .border(2.dp, Theme.InputBorder, CircleShape),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            if (coolingDown) {
                Text(
                    "Too many tries. Please wait ${(cooldownMs + 999) / 1000} s.",
                    color = Theme.Red,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            } else {
                ErrorText(error)
            }
            Spacer(Modifier.height(14.dp))

            val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "OK"))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { k ->
                            val enabled = !coolingDown && when (k) {
                                "⌫" -> pin.isNotEmpty()
                                "OK" -> pin.length >= PinHasher.MIN_LENGTH
                                else -> pin.length < PinHasher.MAX_LENGTH
                            }
                            KidSecondaryButton(
                                title = k,
                                fontSize = if (k == "OK") 20.sp else 24.sp,
                                enabled = enabled,
                                modifier = Modifier.width(88.dp),
                                onClick = {
                                    error = null
                                    when (k) {
                                        "⌫" -> pin = pin.dropLast(1)
                                        "OK" -> submit()
                                        else -> pin += k
                                    }
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { confirmForgot = true }) {
                Text("Forgot PIN?", color = Theme.Purple, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (confirmForgot) {
        AlertDialog(
            onDismissRequest = { confirmForgot = false },
            title = { Text("Forgot your PIN?") },
            text = {
                Text(
                    "This removes the parent PIN, your DeepSeek key and your consent from this device " +
                        "and turns AI Workshops off. You can set them up again straight away.",
                )
            },
            confirmButton = {
                TextButton(onClick = { ParentAuth.wipe(); confirmForgot = false }) {
                    Text("Remove & start again", color = Theme.Red)
                }
            },
            dismissButton = { TextButton(onClick = { confirmForgot = false }) { Text("Cancel") } },
        )
    }
}

/* ------------------------------------------------------------------ */
/* Replace key / change PIN (after the PIN)                            */
/* ------------------------------------------------------------------ */

@Composable
private fun KeyForm(replace: Boolean, onSaved: () -> Unit) {
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    if (ParentAuth.keyNeedsReplacing) {
        Text(
            "DeepSeek didn't accept the saved key (it may have been deleted or replaced). Paste a new key to keep using AI Workshops.",
            color = Theme.Red,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
    Body("Paste a new DeepSeek API key. We check it with DeepSeek before saving.")
    SecretField(key, { key = it.trim().take(200); error = null }, "DeepSeek API key (sk-…)", numeric = false, enabled = !busy)
    GetKeyLink()
    ErrorText(error)
    KidButton(
        title = if (busy) "Checking key…" else "Check key & save",
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            if (!DeepSeekClient.looksLikeKey(key)) {
                error = "Paste your DeepSeek API key — it starts with \"sk-\"."
                return@KidButton
            }
            busy = true
            scope.launch {
                val result = DeepSeekClient.checkKey(key)
                busy = false
                error = keyCheckError(result)
                if (result == DeepSeekClient.KeyCheck.Ok) {
                    if (replace) ParentAuth.replaceKey(key)
                    key = ""
                    onSaved()
                }
            }
        },
    )
}

@Composable
private fun ChangePinForm(onSaved: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Body("Choose a new 4–6 digit parent PIN.")
    SecretField(pin, { pin = it.filter(Char::isDigit).take(PinHasher.MAX_LENGTH); error = null }, "New PIN", numeric = true)
    SecretField(pin2, { pin2 = it.filter(Char::isDigit).take(PinHasher.MAX_LENGTH); error = null }, "Confirm new PIN", numeric = true)
    ErrorText(error)
    KidButton(
        title = "Save new PIN",
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            error = when {
                !PinHasher.isValidPin(pin) -> "Your PIN needs 4 to 6 digits."
                pin != pin2 -> "The two PINs don't match."
                else -> null
            }
            if (error == null) {
                ParentAuth.changePin(pin)
                onSaved()
            }
        },
    )
}
