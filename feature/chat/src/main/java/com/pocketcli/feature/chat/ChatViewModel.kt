package com.pocketcli.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketcli.core.model.*
import com.pocketcli.data.opencode.adapter.OpenCodeAdapter
import com.pocketcli.data.opencode.connection.ActiveConnectionManager
import com.pocketcli.data.opencode.repository.AgentSessionRepository
import com.pocketcli.feature.chat.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: AgentSessionRepository,
    private val connectionManager: ActiveConnectionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var activeAdapter: OpenCodeAdapter? = null

    fun initialize(sessionId: String, profileId: String? = null, adapter: OpenCodeAdapter? = null) {
        val effectiveAdapter = adapter ?: connectionManager.getAdapter()
        activeAdapter = effectiveAdapter

        viewModelScope.launch {
            val resolvedProfileId = profileId?.takeIf { it.isNotEmpty() }
                ?: connectionManager.activeProfile.value?.id
                ?: repository.getProfileIdForSession(sessionId)
                ?: connectionManager.getActiveProfileId()
                ?: ""

 val session = repository.getSession(resolvedProfileId, sessionId)
 val title = session?.title?.ifBlank { "Чат сессии" } ?: "Чат сессии"

 _uiState.update { current ->
 val nextState = current.copy(
 sessionId = sessionId,
 profileId = resolvedProfileId,
 sessionTitle = title
 )
 syncNodesAndComposer(nextState)
 }

 // 1. Observe hybrid message stream (Room + in-flight StateFlow)
 launch {
 repository.getMessages(resolvedProfileId, sessionId).collect { messages ->
 _uiState.update { current ->
 val nextState = current.copy(messages = messages)
 syncNodesAndComposer(nextState)
 }
 }
 }

 // 2. Load available models from the server
 launch {
 effectiveAdapter?.getModels()?.onSuccess { models ->
 _uiState.update { current ->
 val nextState = current.copy(availableModels = models)
 syncNodesAndComposer(nextState)
 }
 }
 }

 // 3. Reconcile existing server messages if available
 val apiClient = effectiveAdapter?.apiClient
 if (apiClient != null && resolvedProfileId.isNotEmpty()) {
 launch {
 repository.reconcile(apiClient, resolvedProfileId, sessionId)
 }
 launch {
 apiClient.getTodos(sessionId).onSuccess { todosDto ->
 val items = todosDto.map {
 TodoItem(content = it.content, status = it.status, priority = it.priority)
 }
 _uiState.update { current ->
 val nextState = current.copy(todos = items)
 syncNodesAndComposer(nextState)
 }
 }
 }
 }

 // 4. Observe SSE events for this session
 if (effectiveAdapter != null) {
 launch {
 effectiveAdapter.events(sessionId).collect { event ->
 repository.handleAgentEvent(resolvedProfileId, sessionId, event)

 when (event) {
 is AgentEvent.SessionStatus -> {
 _uiState.update { current ->
 val nextState = current.copy(sessionState = event.state)
 syncNodesAndComposer(nextState)
 }
 }
 is AgentEvent.PermissionRequested -> {
 _uiState.update { current ->
 val nextState = current.copy(pendingPermission = event)
 syncNodesAndComposer(nextState)
 }
 }
 is AgentEvent.TodoUpdate -> {
 _uiState.update { current ->
 val nextState = current.copy(todos = event.todos)
 syncNodesAndComposer(nextState)
 }
 }
 is AgentEvent.FileDiff -> {
 _uiState.update { current ->
 val nextState = current.copy(activeDiffFile = Pair(event.path, event.unifiedDiff))
 syncNodesAndComposer(nextState)
 }
 }
 is AgentEvent.TextDelta -> {
 _uiState.update { current ->
 val tail = StreamingTailUi(
 messageId = event.messageId,
 visibleText = event.text,
 phase = AgentPhase.STREAMING_TEXT
 )
 val nextState = current.copy(streamingTail = tail)
 syncNodesAndComposer(nextState)
 }
 }
 is AgentEvent.ReasoningDelta -> {
 _uiState.update { current ->
 val tail = StreamingTailUi(
 messageId = event.messageId,
 visibleText = event.text,
 phase = AgentPhase.THINKING
 )
 val nextState = current.copy(streamingTail = tail)
 syncNodesAndComposer(nextState)
 }
 }
 is AgentEvent.ToolCallUpdate -> {
 _uiState.update { current ->
 val tail = StreamingTailUi(
 messageId = event.messageId,
 visibleText = "",
 phase = AgentPhase.EXECUTING_TOOL,
 currentTool = ToolSummary(
 callId = event.callId,
 name = event.name,
 status = event.status
 )
 )
 val nextState = current.copy(streamingTail = tail)
 syncNodesAndComposer(nextState)
 }
 }
 else -> Unit
 }
 }
 }
 }
 }
 }

 fun onDraftChange(text: String) {
 _uiState.update { current ->
 val nextState = current.copy(composerDraft = text)
 syncNodesAndComposer(nextState)
 }
 }

 fun addAttachment(name: String) {
 _uiState.update { current ->
 val nextState = current.copy(attachments = current.attachments + name)
 syncNodesAndComposer(nextState)
 }
 }

 fun removeAttachment(index: Int) {
 _uiState.update { current ->
 val list = current.attachments.toMutableList()
 if (index in list.indices) {
 list.removeAt(index)
 }
 val nextState = current.copy(attachments = list)
 syncNodesAndComposer(nextState)
 }
 }

 fun openModelPicker() {
 _uiState.update { it.copy(isModelPickerOpen = true) }
 }

 fun dismissModelPicker() {
 _uiState.update { it.copy(isModelPickerOpen = false) }
 }

 fun selectModel(model: ModelInfo?) {
 _uiState.update { current ->
 val nextState = current.copy(selectedModel = model, isModelPickerOpen = false)
 syncNodesAndComposer(nextState)
 }
 }

 fun openPermissionDetails() {
 _uiState.update { it.copy(showPermissionDetails = true) }
 }

 fun dismissPermissionDetails() {
 _uiState.update { it.copy(showPermissionDetails = false) }
 }

 fun openDiff(path: String, diff: String) {
 _uiState.update { current ->
 val nextState = current.copy(activeDiffFile = Pair(path, diff))
 syncNodesAndComposer(nextState)
 }
 }

 fun closeDiff() {
 _uiState.update { current ->
 val nextState = current.copy(activeDiffFile = null)
 syncNodesAndComposer(nextState)
 }
 }

 fun sendPrompt() {
 val state = _uiState.value
 val text = state.composerDraft.trim()
 if (text.isBlank() || state.sessionState == SessionState.BUSY) return

 viewModelScope.launch {
 _uiState.update { current ->
 val nextState = current.copy(
 composerDraft = "",
 attachments = emptyList(),
 sessionState = SessionState.BUSY,
 errorMessage = null
 )
 syncNodesAndComposer(nextState)
 }

 // 1. Record user message in DB
 repository.recordUserMessage(state.profileId, state.sessionId, text)

 // 2. Dispatch to agent adapter
 val adapter = activeAdapter ?: connectionManager.getAdapter()
 if (adapter == null) {
 _uiState.update { current ->
 val nextState = current.copy(
 sessionState = SessionState.IDLE,
 errorMessage = "Нет активного подключения. Проверьте настройки."
 )
 syncNodesAndComposer(nextState)
 }
 return@launch
 }

 val modelInput = state.selectedModel?.let { ModelIdentifier(it.providerId, it.modelId) }
 val result = adapter.sendPrompt(state.sessionId, Prompt(text = text, model = modelInput))
 result.onFailure { err ->
 _uiState.update { current ->
 val nextState = current.copy(
 sessionState = SessionState.IDLE,
 errorMessage = "Не удалось отправить сообщение: ${err.message}"
 )
 syncNodesAndComposer(nextState)
 }
 return@launch
 }

 // 3. Reconcile with server to guarantee response is stored even if SSE dropped
 val apiClient = adapter.apiClient
 if (state.profileId.isNotEmpty()) {
 repository.reconcile(apiClient, state.profileId, state.sessionId)
 }
 _uiState.update { current ->
 val nextState = current.copy(sessionState = SessionState.IDLE)
 syncNodesAndComposer(nextState)
 }
 }
 }

 fun dismissError() {
 _uiState.update { it.copy(errorMessage = null) }
 }

 fun stop() {
 val state = _uiState.value
 viewModelScope.launch {
 val adapter = activeAdapter ?: connectionManager.getAdapter()
 adapter?.cancel(state.sessionId)
 _uiState.update { current ->
 val nextState = current.copy(sessionState = SessionState.IDLE)
 syncNodesAndComposer(nextState)
 }
 repository.flushInFlightToDb(state.sessionId)
 }
 }

 fun respondPermission(requestId: String, option: PermissionOption) {
 viewModelScope.launch {
 val adapter = activeAdapter ?: connectionManager.getAdapter()
 adapter?.respondPermission(requestId, option)
 _uiState.update { current ->
 val nextState = current.copy(pendingPermission = null, showPermissionDetails = false)
 syncNodesAndComposer(nextState)
 }
 }
 }

 fun openToolDetails(toolCall: ToolCall) {
 _uiState.update { it.copy(selectedToolForDetails = toolCall) }
 }

 fun dismissToolDetails() {
 _uiState.update { it.copy(selectedToolForDetails = null) }
 }

 private fun syncNodesAndComposer(state: ChatUiState): ChatUiState {
 val nodes = mutableListOf<ChatNode>()
 val seenKeys = mutableSetOf<String>()

 fun uniqueKey(prefix: String, rawId: String): String {
 val base = if (rawId.isNotBlank()) "${prefix}_$rawId" else "${prefix}_${System.identityHashCode(rawId)}"
 var key = base
 var counter = 1
 while (!seenKeys.add(key)) {
 key = "${base}_${counter++}"
 }
 return key
 }

 for (msg in state.messages) {
 if (msg.role == MessageRole.USER) {
 if (msg.text.isNotBlank()) {
 nodes.add(ChatNode.UserNode(id = uniqueKey("user", msg.id), text = msg.text, timestamp = msg.timestamp))
 }
 } else {
 nodes.add(ChatNode.AssistantNode(id = uniqueKey("asst", msg.id), message = msg, isStreaming = false))
 for (tool in msg.toolCalls) {
 nodes.add(ChatNode.ToolNode(id = uniqueKey("tool", tool.callId), toolCall = tool))
 }
 }
 }

 state.activeDiffFile?.let { (path, diff) ->
 nodes.add(ChatNode.DiffNode(id = uniqueKey("diff", path), filePath = path, diffContent = diff))
 }

 state.pendingPermission?.let { perm ->
 nodes.add(ChatNode.PermissionNode(id = uniqueKey("perm", perm.requestId), request = perm))
 }

 if (state.todos.isNotEmpty()) {
 nodes.add(ChatNode.TodoNode(id = uniqueKey("todos", "agent_todos"), todos = state.todos))
 }

 if (state.streamingTail != null && state.sessionState == SessionState.BUSY) {
 nodes.add(ChatNode.StreamingTailNode(id = uniqueKey("tail", state.streamingTail.messageId), tail = state.streamingTail))
 }

 val mode = when {
 state.sessionState == SessionState.BUSY -> ComposerMode.Running
 state.pendingPermission != null -> ComposerMode.AwaitingPermission
 state.attachments.isNotEmpty() -> ComposerMode.WithAttachments
 state.composerDraft.isNotBlank() -> ComposerMode.Typing
 else -> ComposerMode.Empty
 }

 val composer = ComposerState(
 text = state.composerDraft,
 attachments = state.attachments,
 mode = mode,
 selectedModelName = state.selectedModel?.name,
 isSendEnabled = state.composerDraft.isNotBlank() || state.attachments.isNotEmpty()
 )

 return state.copy(nodes = nodes, composer = composer)
 }
}