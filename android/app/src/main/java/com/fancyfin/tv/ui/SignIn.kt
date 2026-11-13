package com.fancyfin.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fancyfin.tv.data.ApiException
import com.fancyfin.tv.data.JellyfinClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    password: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    wide: Boolean = false,
) {
    val focus = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Palette.TextMuted, fontSize = 11.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Palette.Text, fontSize = 15.sp),
            cursorBrush = SolidColor(Palette.Accent),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Next),
            // The tick on the on-screen keyboard moves on to the next control instead of leaving focus stuck here.
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
            modifier = Modifier.width(if (wide) 480.dp else 320.dp).onPreviewKeyEvent { event ->
                // A text field would otherwise swallow up and down on the remote.
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    focus.moveFocus(FocusDirection.Down)
                    true
                } else if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                    focus.moveFocus(FocusDirection.Up)
                    true
                } else {
                    false
                }
            },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .width(if (wide) 480.dp else 320.dp)
                        .height(38.dp)
                        .background(Palette.GlassFill, RoundedCornerShape(8.dp))
                        .border(1.dp, Palette.GlassEdge, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { inner() }
            },
        )
    }
}

private enum class Step { Connect, QuickConnect, Password }

/**
 * First-run screen. Step 1 finds the Fancyfin container (it is the only thing the TV needs to know),
 * step 2 shows a Quick Connect code to approve from Jellyfin on a PC, and the password form is the
 * fallback. Everything is left-aligned.
 */
@Composable
fun SignInScreen(client: JellyfinClient, onSignedIn: () -> Unit) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(Step.Connect) }
    var address by remember { mutableStateOf(client.fancyfin) }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(true) }
    var found by remember { mutableStateOf<List<String>>(emptyList()) }
    var code by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        found = client.discover()
        scanning = false
        if (found.size == 1 && address.isEmpty()) address = found[0]
    }

    // Quick Connect: show a code, then wait for it to be approved.
    LaunchedEffect(step, attempt) {
        if (step != Step.QuickConnect) return@LaunchedEffect
        code = null
        message = null
        try {
            val qc = client.quickConnectStart(address)
            code = qc.code
            val deadline = System.currentTimeMillis() + 4 * 60_000
            while (System.currentTimeMillis() < deadline) {
                delay(3_000)
                if (client.quickConnectApproved(address, qc.secret)) {
                    client.quickConnectFinish(address, qc.secret)
                    onSignedIn()
                    return@LaunchedEffect
                }
            }
            message = "That code ran out of time."
            code = null
        } catch (e: ApiException) {
            message = if (e.code == 401) {
                "Quick Connect is switched off in Jellyfin (Dashboard, General). Use a password instead."
            } else {
                "Quick Connect didn't work (${e.message})."
            }
        } catch (e: Exception) {
            message = "Couldn't reach Fancyfin."
        }
    }

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        Column(
            Modifier.padding(start = 64.dp, top = 56.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Fancyfin", color = Palette.Text, fontSize = 34.sp, fontWeight = FontWeight.Bold)

            when (step) {
                Step.Connect -> {
                    Text("Connect to your Fancyfin server", color = Palette.TextMuted, fontSize = 14.sp)
                    when {
                        scanning -> Text("Looking for Fancyfin on your network…", color = Palette.TextMuted, fontSize = 12.sp)
                        found.isEmpty() -> Text("Couldn't find it automatically. Type the address.", color = Palette.TextMuted, fontSize = 12.sp)
                        else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            found.forEach { GlassButton(it.removePrefix("http://"), { address = it }) }
                        }
                    }
                    Field("Fancyfin address (for example 192.168.1.20:8282)", address, { address = it }, keyboard = KeyboardType.Uri)
                    message?.let { Text(it, color = Palette.Accent, fontSize = 12.sp) }
                    GlassButton(
                        label = if (busy) "Connecting…" else "Connect",
                        primary = true,
                        onClick = {
                            if (busy) return@GlassButton
                            val clean = normalise(address)
                            if (clean.isEmpty()) {
                                message = "Enter the Fancyfin address."
                                return@GlassButton
                            }
                            busy = true
                            message = null
                            scope.launch {
                                try {
                                    address = client.checkFancyfin(clean)
                                    step = Step.QuickConnect
                                } catch (e: Exception) {
                                    message = if (e.message?.contains("no Jellyfin") == true) {
                                        "Fancyfin is running but has no Jellyfin address. Set JELLYFIN_URL on the container."
                                    } else {
                                        "That doesn't look like Fancyfin. Check the address and port."
                                    }
                                }
                                busy = false
                            }
                        },
                    )
                }

                Step.QuickConnect -> {
                    Text("Sign in with Quick Connect", color = Palette.TextMuted, fontSize = 14.sp)
                    if (code != null) {
                        Text(code!!, color = Palette.Accent, fontSize = 56.sp, fontWeight = FontWeight.Bold, letterSpacing = 8.sp)
                        Text(
                            "On your PC, open Jellyfin, click your profile picture, choose Quick Connect and enter this code.",
                            color = Palette.TextMuted, fontSize = 13.sp, modifier = Modifier.width(520.dp),
                        )
                    } else if (message == null) {
                        Text("Getting a code…", color = Palette.TextMuted, fontSize = 12.sp)
                    }
                    message?.let { Text(it, color = Palette.Accent, fontSize = 12.sp, modifier = Modifier.width(520.dp)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (code == null && message != null) GlassButton("New code", { attempt++ }, primary = true)
                        GlassButton("Use a password", { step = Step.Password })
                        GlassButton("Change server", { step = Step.Connect })
                    }
                }

                Step.Password -> {
                    Text("Sign in to Jellyfin", color = Palette.TextMuted, fontSize = 14.sp)
                    Field("Username", user, { user = it })
                    Field("Password", password, { password = it }, password = true)
                    message?.let { Text(it, color = Palette.Accent, fontSize = 12.sp) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton(
                            label = if (busy) "Signing in…" else "Sign in",
                            primary = true,
                            onClick = {
                                if (busy) return@GlassButton
                                if (user.isEmpty()) {
                                    message = "Enter your username."
                                    return@GlassButton
                                }
                                busy = true
                                message = null
                                scope.launch {
                                    try {
                                        client.signIn(address, user, password)
                                        onSignedIn()
                                    } catch (e: Exception) {
                                        message = "Couldn't sign in. Check the username and password."
                                    }
                                    busy = false
                                }
                            },
                        )
                        GlassButton("Use Quick Connect", { step = Step.QuickConnect; attempt++ })
                    }
                }
            }
        }
    }
}

private fun normalise(input: String): String {
    val trimmed = input.trim().trimEnd('/')
    if (trimmed.isEmpty()) return ""
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "http://$trimmed"
}
