package com.arjunaayush.looply.feature.feed.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.arjunaayush.looply.core.designsystem.LinkerlyButton
import com.arjunaayush.looply.core.designsystem.LinkerlyIcon
import com.arjunaayush.looply.core.designsystem.LinkerlyIcons
import com.arjunaayush.looply.core.designsystem.LinkerlyOutlinedButton
import com.arjunaayush.looply.core.designsystem.LinkerlyTextButton
import com.arjunaayush.looply.core.util.HapticEffectType
import com.arjunaayush.looply.core.util.HapticsManager
import com.arjunaayush.looply.features.download.DownloadReelWorker

@Composable
fun ImportReelDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = remember { HapticsManager(context) }
    var urlText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Save Instagram Reel",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("Instagram Reel Link") },
                    placeholder = { Text("https://www.instagram.com/reel/...") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinkerlyOutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clipData = clipboard?.primaryClip
                            if (clipData != null && clipData.itemCount > 0) {
                                val text = clipData.getItemAt(0).text?.toString() ?: ""
                                if (text.isNotBlank()) {
                                    urlText = text
                                    haptics.playHaptic(HapticEffectType.TICK)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        LinkerlyIcon(
                            imageVector = LinkerlyIcons.Buttons.Paste,
                            contentDescription = null,
                            size = 16.dp
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Paste")
                    }

                    LinkerlyButton(
                        onClick = {
                            if (urlText.isNotBlank()) {
                                haptics.playHaptic(HapticEffectType.CONFIRM)
                                DownloadReelWorker.enqueue(context, urlText.trim())
                                onDismiss()
                            }
                        },
                        enabled = urlText.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Download")
                    }
                }

                LinkerlyTextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}
