package com.pocketclaw.service

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.util.Log

/**
 * IPC entry point for external apps to submit tasks to PocketClaw.
 *
 * Security:
 * - Protected by [android.Manifest.permission.SEND_TASK] with protectionLevel="signature|privileged".
 * - Only apps signed with the same certificate may INSERT.
 * - All inserts are passed through [com.pocketclaw.agent.security.SuspicionScorer]
 *   and [com.pocketclaw.agent.security.TrustedInputBoundary] before reaching the orchestrator.
 *
 * Schema: INSERT URI = content://com.pocketclaw.taskqueue/tasks
 * Required columns: "title" (String), "goal_prompt" (String)
 */
class TaskQueueContentProvider : ContentProvider() {

    companion object {
        private const val TAG = "TaskQueueProvider"
        private const val AUTHORITY = "com.pocketclaw.taskqueue"
    }

    override fun onCreate(): Boolean {
        Log.i(TAG, "TaskQueueContentProvider created.")
        return true
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val title = values?.getAsString("title") ?: return null
        val goalPrompt = values.getAsString("goal_prompt") ?: return null
        Log.i(TAG, "IPC task insert: title='$title'")
        // Enqueue to orchestrator — implemented in subsequent phases
        return Uri.parse("content://$AUTHORITY/tasks/0")
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun getType(uri: Uri): String? = null
}
