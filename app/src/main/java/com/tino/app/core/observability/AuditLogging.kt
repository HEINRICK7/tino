package com.tino.app.core.observability

import android.util.Log
import com.tino.app.core.common.UuidV7
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

enum class AuditEventType {
    APP_START,
    VOICE_START,
    VOICE_TRANSCRIPT_PARTIAL,
    VOICE_TRANSCRIPT_REVISED,
    VOICE_TRANSCRIPT_COMMITTED,
    VOICE_AGENT_SUBMITTED,
    VOICE_STAGE,
    VOICE_CORRECTION_QUEUED,
    VOICE_CORRECTION_EVENT,
    VOICE_FAILURE,
    INTENT_RESOLUTION_FAILURE,
    TOOL_FAILURE,
    MUTATION_FAILURE,
    SYNC_FAILURE,
    FISCAL_CAPTURE_FAILURE,
    COMMAND_RECEIVED,
    INTENT_DETECTED,
    TOOL_SELECTED,
    CONFIRMATION,
    DOMAIN_OPERATION,
    SYNC_STATUS,
    ML_RECOMMENDATION,
    USER_DECISION,
    ENTITY_RESOLUTION_STARTED,
    ENTITY_RESOLUTION_EXACT,
    ENTITY_RESOLUTION_ALIAS,
    ENTITY_RESOLUTION_FUZZY,
    ENTITY_RESOLUTION_AMBIGUOUS,
    ENTITY_RESOLUTION_NOT_FOUND,
    AGENT_STATE_CHANGED,
    AGENT_PROGRESS,
}

interface AuditLogger {
    fun record(type: AuditEventType, metadata: Map<String, String> = emptyMap())
}

object NoOpAuditLogger : AuditLogger {
    override fun record(type: AuditEventType, metadata: Map<String, String>) = Unit
}

@Singleton
class RedactedAuditLogger @Inject constructor() : AuditLogger {
    private val allowedKeys = setOf(
        "event_id",
        "intent",
        "tool",
        "status",
        "sync_state",
        "recommendation_type",
        "entity_type",
        "match_strategy",
        "candidate_count",
        "build_id",
        "build_channel",
        "android_api",
        "device_model",
        "stage",
        "duration_ms",
        "timeout_ms",
        "route",
        "speech_provider",
        "recognizer_available",
        "on_device_available",
        "locale",
        "fast_path",
        "reason_code",
        "partial_count",
        "revised_count",
        "committed_count",
        "agent_execution_count",
        "agent_executions_before_send",
        "transcript_state",
        "correction_status",
        "vertical",
        "module_count",
        "capability_count",
        "profile_action",
        "state_version",
        "voice_state",
        "pending_action_stage",
        "confirmation_state",
        "progress_event",
        "progress_sequence",
        "terminal_state",
        "run_id",
        "execution_id",
        "capability",
        "risk",
        "changed_slots",
        "invalidated_slots",
        "patch_status",
        "patch_rejection",
    )

    override fun record(type: AuditEventType, metadata: Map<String, String>) {
        val safe = JSONObject().put("audit_id", UuidV7.new()).put("type", type.name)
        metadata.filterKeys { it in allowedKeys }.forEach { (key, value) -> safe.put(key, value) }
        Log.i("TINO_AUDIT", safe.toString())
    }
}
