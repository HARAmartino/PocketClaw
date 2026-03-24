package com.pocketclaw.service

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.util.Log

/**
 * Session service for assistant UI/logic, running in a separate process.
 * Handles the actual user interaction when the assistant is triggered.
 */
class AgentVoiceInteractionSessionService : VoiceInteractionSessionService() {

    companion object {
        private const val TAG = "VoiceSessionService"
    }

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        Log.i(TAG, "New voice interaction session started.")
        return object : VoiceInteractionSession(this) {
            override fun onShow(args: Bundle?, showFlags: Int) {
                super.onShow(args, showFlags)
                Log.i(TAG, "Voice interaction session shown.")
            }
        }
    }
}
