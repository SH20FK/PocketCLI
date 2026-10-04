package com.pocketcli.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.pocketcli.core.model.AgentType
import com.pocketcli.core.ui.theme.ToolSuccessColor

data class AgentSpec(
    val type: AgentType,
    val name: String,
    val icon: ImageVector,
    val protocol: String,
    val isSupported: Boolean,
    val version: String,
    val description: String,
    val isConfigured: Boolean = false,
    val onConfigure: () -> Unit
)

@Composable
fun AgentCardsList(
    onConfigureOpenCode: () -> Unit,
    modifier: Modifier = Modifier
) {
    AgentCardsList(
        onConfigureOpenCode = onConfigureOpenCode,
        onConfigureClaudeCode = {},
        onConfigureAntigravity = {},
        onConfigureCodex = {},
        hasAnthropicKey = false,
        hasGeminiKey = false,
        hasOpenAiKey = false,
        modifier = modifier
    )
}

@Composable
fun AgentCardsList(
    onConfigureOpenCode: () -> Unit,
    onConfigureClaudeCode: () -> Unit,
    onConfigureAntigravity: () -> Unit,
    onConfigureCodex: () -> Unit,
    hasAnthropicKey: Boolean,
    hasGeminiKey: Boolean,
    hasOpenAiKey: Boolean,
    isAntigravityAuthenticated: Boolean = false,
    modifier: Modifier = Modifier
) {
    val agents = listOf(
        AgentSpec(
            type = AgentType.OPENCODE,
            name = "OpenCode",
            icon = Icons.Default.SmartToy,
            protocol = "HTTP REST + SSE (Port 4096)",
            isSupported = true,
            version = "v1.2.27",
            description = "Автономный рантайм в PRoot и подключение к удалённым серверам",
            isConfigured = true,
            onConfigure = onConfigureOpenCode
        ),
        AgentSpec(
            type = AgentType.CLAUDE_CODE,
            name = "Claude Code",
            icon = Icons.Default.Bolt,
            protocol = "ACP JSON-RPC 2.0",
            isSupported = false,
            version = "В разработке (Этап 3)",
            description = "Anthropic Claude Code агент через открытый протокол ACP (адаптер Zed / локальный процесс)",
            isConfigured = hasAnthropicKey,
            onConfigure = onConfigureClaudeCode
        ),
        AgentSpec(
            type = AgentType.ANTIGRAVITY,
            name = "Gemini / Antigravity",
            icon = Icons.Default.AutoAwesome,
            protocol = "ACP JSON-RPC 2.0 (stdio / remote)",
            isSupported = true,
            version = "ACP v1.0",
            description = "Google DeepMind Advanced Agentic Coding агент с контекстным окном до 2M токенов",
            isConfigured = hasGeminiKey || isAntigravityAuthenticated,
            onConfigure = onConfigureAntigravity
        ),
        AgentSpec(
            type = AgentType.CODEX,
            name = "Codex",
            icon = Icons.Default.Code,
            protocol = "ACP JSON-RPC 2.0",
            isSupported = false,
            version = "В разработке (Этап 3)",
            description = "OpenAI Codex CLI агент через адаптер Zed",
            isConfigured = hasOpenAiKey,
            onConfigure = onConfigureCodex
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
                                imageVector = agent.icon,
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
                            val badgeColor = if (agent.isConfigured) ToolSuccessColor else MaterialTheme.colorScheme.primary
                            val badgeText = when {
                                agent.type == AgentType.ANTIGRAVITY && isAntigravityAuthenticated -> "Google OAuth"
                                agent.isConfigured -> "Активен"
                                else -> "Готов"
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = badgeColor.copy(alpha = 0.15f),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = badgeColor,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = badgeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = badgeColor
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
                                        text = "Скоро",
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
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Протокол: ${agent.protocol} · ${agent.version}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = agent.onConfigure,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Настроить",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
