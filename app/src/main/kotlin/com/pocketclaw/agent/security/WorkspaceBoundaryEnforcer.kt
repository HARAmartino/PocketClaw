package com.pocketclaw.agent.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enforces that all agent file I/O stays within the designated workspace directory.
 *
 * Workspace root: [Context.getExternalFilesDir]/PocketClaw_Workspace/
 *
 * Uses [File.canonicalPath] to resolve symlinks and path traversal sequences
 * (e.g., `../../etc/passwd`). Any path resolving outside the workspace
 * throws [SecurityException] — never bypassed.
 */
@Singleton
class WorkspaceBoundaryEnforcer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val workspaceRoot: File by lazy {
        File(context.getExternalFilesDir(null), "PocketClaw_Workspace").also { it.mkdirs() }
    }

    /**
     * Validates [path] is within the workspace root.
     * @throws SecurityException if the resolved canonical path is outside the workspace.
     */
    fun validate(path: String) {
        val targetFile = File(path)
        val canonical = targetFile.canonicalPath
        val workspaceCanonical = workspaceRoot.canonicalPath

        if (!canonical.startsWith(workspaceCanonical + File.separator) &&
            canonical != workspaceCanonical
        ) {
            throw SecurityException(
                "WorkspaceBoundaryEnforcer: Path '$path' resolves to '$canonical' " +
                    "which is outside the workspace '$workspaceCanonical'. " +
                    "File operation blocked.",
            )
        }
    }

    /**
     * Returns a validated [File] within the workspace.
     * @throws SecurityException if outside workspace.
     */
    fun safeFile(relativePath: String): File {
        val file = File(workspaceRoot, relativePath)
        validate(file.path)
        return file
    }
}
