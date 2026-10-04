package com.pocketcli.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class AgentType(
    val id: String,
    val displayName: String,
    val protocol: String,
    val description: String,
    val defaultModel: String,
    val providerKeyEnv: String,
    val isAvailable: Boolean = true,
    val statusBadge: String = "Готов"
) {
    OPENCODE(
        id = "opencode",
        displayName = "OpenCode",
        protocol = "HTTP REST + SSE",
        description = "Автономный рантайм в PRoot и подключение к удалённым серверам",
        defaultModel = "default",
        providerKeyEnv = "",
        isAvailable = true,
        statusBadge = "Готов"
    ),
    CLAUDE_CODE(
        id = "claude",
        displayName = "Claude Code",
        protocol = "ACP JSON-RPC 2.0 (stdio / remote)",
        description = "Anthropic Claude Code агент через открытый протокол ACP (в разработке)",
        defaultModel = "claude-3-7-sonnet",
        providerKeyEnv = "ANTHROPIC_API_KEY",
        isAvailable = false,
        statusBadge = "В разработке"
    ),
    ANTIGRAVITY(
        id = "antigravity",
        displayName = "Gemini / Antigravity",
        protocol = "ACP JSON-RPC 2.0 (stdio / remote)",
        description = "Google DeepMind Advanced Agentic Coding",
        defaultModel = "gemini-2.5-pro",
        providerKeyEnv = "GEMINI_API_KEY",
        isAvailable = true,
        statusBadge = "Экспериментальный"
    ),
    CODEX(
        id = "codex",
        displayName = "Codex",
        protocol = "ACP JSON-RPC 2.0 (stdio / remote)",
        description = "OpenAI Codex CLI агент через адаптер Zed (в разработке)",
        defaultModel = "gpt-4o",
        providerKeyEnv = "OPENAI_API_KEY",
        isAvailable = false,
        statusBadge = "В разработке"
    );

    companion object {
        fun fromId(id: String?): AgentType =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: OPENCODE
    }
}
