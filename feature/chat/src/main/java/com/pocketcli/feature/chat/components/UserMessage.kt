package com.pocketcli.feature.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pocketcli.core.ui.theme.PocketCLITheme
import com.pocketcli.core.ui.theme.PocketSpacing
import java.text.SimpleDateFormat
import java.util.*

/**
 * UserMessage aligned to the right.
 * - Width: min(intrinsic, 88% of screen)
 * - Container: secondaryContainer
 * - Radius: 18 dp
 * - Body: bodyLarge (16sp/24sp)
 * - Timestamp: labelMedium (12sp)
 */
@Composable
fun UserMessage(
    text: String,
    timestamp: Long,
    modifier: Modifier = Modifier
) {
    val timeFormatter = remember { SimpleDateFormat(HH:mm, Locale.getDefault()) }
    val formattedTime = remember(timestamp) { timeFormatter.format(Date(timestamp)) }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .wrapContentWidth(Alignment.End)
                .clip(RoundedCornerShape(18.dp))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = PocketSpacing.md, vertical = PocketSpacing.sm)
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(PocketSpacing.xxs))
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Preview(name = UserMessage Light)
@Composable
fun UserMessagePreview() {
    PocketCLITheme(darkTheme = false) {
        Surface {
            UserMessage(
                text = Напиши юнит-тесты для ProotEnvironment,
                timestamp = System.currentTimeMillis(),
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}