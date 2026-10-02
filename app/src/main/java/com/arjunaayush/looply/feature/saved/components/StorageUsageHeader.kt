package com.arjunaayush.looply.feature.saved.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.arjunaayush.looply.core.designsystem.LinkerlyButton
import com.arjunaayush.looply.core.designsystem.LinkerlyCard
import com.arjunaayush.looply.core.designsystem.LinkerlyIcon
import com.arjunaayush.looply.core.designsystem.LinkerlyIcons
import com.arjunaayush.looply.core.designsystem.LinkerlyOutlinedButton
import com.arjunaayush.looply.core.designsystem.LinkerlyTextButton
import com.arjunaayush.looply.core.util.HapticEffectType
import com.arjunaayush.looply.core.util.HapticsManager

@Composable
fun StorageUsageHeader(
    storageFormatted: String,
    onClearWatched: () -> Unit,
    onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hapticsManager = remember { HapticsManager(context) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    LinkerlyCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Storage Used",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = storageFormatted,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinkerlyOutlinedButton(
                    onClick = {
                        hapticsManager.playHaptic(HapticEffectType.HEAVY_DELETE)
                        onClearWatched()
                    }
                ) {
                    Text("Clear Watched")
                }

                LinkerlyOutlinedButton(
                    onClick = {
                        hapticsManager.playHaptic(HapticEffectType.TICK)
                        showDeleteAllDialog = true
                    }
                ) {
                    LinkerlyIcon(
                        imageVector = LinkerlyIcons.Buttons.Delete,
                        contentDescription = null,
                        size = 15.dp,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = "Delete All",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (showDeleteAllDialog) {
        Dialog(onDismissRequest = { showDeleteAllDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Delete All Downloaded Reels?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "This will permanently delete all downloaded video files and free up your local device storage. This action cannot be undone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LinkerlyTextButton(onClick = { showDeleteAllDialog = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.size(8.dp))
                        LinkerlyButton(
                            onClick = {
                                showDeleteAllDialog = false
                                hapticsManager.playHaptic(HapticEffectType.HEAVY_DELETE)
                                onDeleteAll()
                            }
                        ) {
                            LinkerlyIcon(
                                imageVector = LinkerlyIcons.Buttons.Delete,
                                contentDescription = null,
                                size = 16.dp
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Delete All")
                        }
                    }
                }
            }
        }
    }
}
