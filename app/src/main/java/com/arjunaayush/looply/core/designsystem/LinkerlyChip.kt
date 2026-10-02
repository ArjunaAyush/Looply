package com.arjunaayush.looply.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arjunaayush.looply.core.designsystem.theme.LooplyTheme

@Composable
fun LinkerlyChip(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    containerColor: Color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
    border: BorderStroke? = null
) {
    val chipModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }

    Surface(
        modifier = chipModifier,
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        contentColor = contentColor,
        border = border
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LinkerlyChipPreview() {
    LooplyTheme {
        LinkerlyChip(
            text = "@creator",
            modifier = Modifier.padding(16.dp)
        )
    }
}
