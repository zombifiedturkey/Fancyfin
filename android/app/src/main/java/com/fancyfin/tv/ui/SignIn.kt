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
import androidx.compose.foundation.text.KeyboardOptions
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
import com.fancyfin.tv.data.JellyfinClient
import kotlinx.coroutines.launch

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    password: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Palette.TextMuted, fontSize = 11.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Palette.Text, fontSize = 15.sp),
            cursorBrush = SolidColor(Palette.Accent),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.width(320.dp),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .width(320.dp)
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

/** First-run screen: find or type the Jellyfin address, then sign in. Everything is left-aligned. */
@Composable
fun SignInScreen(client: JellyfinClient, onSignedIn: () -> Unit) {
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf(client.server) }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) { found = client.discover() }

    Box(Modifier.fillMaxSize().background(Palette.Background)) {
        Column(
            Modifier.padding(start = 64.dp, top = 56.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Fancyfin", color = Palette.Text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text("Sign in to your Jellyfin server", color = Palette.TextMuted, fontSize = 14.sp)

            if (found.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    found.forEach { GlassButton(it.removePrefix("http://"), { address = it }) }
                }
            }
            Field("Server address (for example 192.168.1.20:8096)", address, { address = it }, keyboard = KeyboardType.Uri)
            Field("Username", user, { user = it })
            Field("Password", password, { password = it }, password = true)

            message?.let { Text(it, color = Palette.Accent, fontSize = 12.sp) }

            GlassButton(
                label = if (busy) "Signing in…" else "Sign in",
                primary = true,
                onClick = {
                    if (busy) return@GlassButton
                    val clean = normalise(address)
                    if (clean.isEmpty() || user.isEmpty()) {
                        message = "Enter the server address and your username."
                        return@GlassButton
                    }
                    busy = true
                    message = null
                    scope.launch {
                        try {
                            client.probe(clean)
                            client.signIn(clean, user, password)
                            onSignedIn()
                        } catch (e: Exception) {
                            message = "Couldn't sign in. Check the address, username and password."
                        }
                        busy = false
                    }
                },
            )
        }
    }
}

private fun normalise(input: String): String {
    val trimmed = input.trim().trimEnd('/')
    if (trimmed.isEmpty()) return ""
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "http://$trimmed"
}
