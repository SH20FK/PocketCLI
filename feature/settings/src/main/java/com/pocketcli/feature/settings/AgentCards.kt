package com.pocketcli.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pocketcli.core.ui.theme.ToolSuccessColor

data class AgentSpec(
    val name: String,
    val protocol: String,
    val isSupported: Boolean,
    val version: String,
    val description: String
)

@Composable
fun AgentCardsList(
    onConfigureOpenCode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val agents = listOf(
        AgentSpec(
            name = "OpenCode",
            protocol = "HTTP REST + SSE (Port 4096)",
            isSupported = true,
            version = "v1.2.27",
            description = "Автономный рантайм в PRoot и подключение к удалённым серверам"
        ),
        AgentSpec(
            name = "Claude Code",
            protocol = "ACP JSON-RPC 2.0 (stdio)",
            isSupported = false,
            version = "Запланировано (Этап 3)",
            description = "Адаптер Zed для Claude Code агента"
        ),
        AgentSpec(
            name = "Gemini / Antigravity",
            protocol = "ACP JSON-RPC 2.0 (stdio)",
            isSupported = false,
            version = "Запланировано (Этап 3)",
            description = "Google DeepMind Advanced Agentic Coding"
        ),
        AgentSpec(
            name = "Codex",
            protocol = "ACP JSON-RPC 2.0 (stdio)",
            isSupported = false,
            version = "Запланировано (Этап 3)",
            description = "Адаптер Zed для OpenAI Codex CLI"
        )
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        for (agent in agents) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = if (agent.isSupported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = agent.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        if (agent.isSupported) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ToolSuccessColor.copy(alpha = 0.15f),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = ToolSuccessColor,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Активен",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ToolSuccessColor
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.height(24.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Этап 3",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = agent.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Протокол: ${agent.protocol} · ${agent.version}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
