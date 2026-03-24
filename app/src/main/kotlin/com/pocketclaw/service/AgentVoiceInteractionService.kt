package com.pocketclaw.service

import android.service.voice.VoiceInteractionService
import android.util.Log

/**
 * VoiceInteractionService — the manifest entry point for OS assistant integration.
 * This service is always-running and lightweight. Heavy logic is deferred to
 * [AgentVoiceInteractionSessionService].
 *
 * Triggered by: Home button long-press or system assistant gesture.
 * The user must manually set PocketClaw as the default assistant in Settings.
 * No programmatic override is possible or attempted.
 */
class AgentVoiceInteractionService : VoiceInteractionService() {

    companion object {
        private const val TAG = "AgentVoiceService"
    }

    override fun onReady() {
        super.onReady()
        Log.i(TAG, "VoiceInteractionService ready.")
    }
}
