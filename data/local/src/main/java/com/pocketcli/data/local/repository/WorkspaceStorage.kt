package com.pocketcli.data.local.repository

import android.content.Context
import com.pocketcli.core.model.WorkspaceGitStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

interface WorkspaceStorage {
    fun getBaseDirectory(): File
    fun getWorkspaceDirectory(workspaceId: String): File
    fun createWorkspaceDirectory(workspaceId: String, initReadme: Boolean = true, title: String = ""): File
    fun deleteWorkspaceDirectory(workspaceId: String): Boolean
    fun getGitStatus(directory: File): WorkspaceGitStatus
}

@Singleton
class DefaultWorkspaceStorage @Inject constructor(
    @ApplicationContext private val context: Context
) : WorkspaceStorage {

    override fun getBaseDirectory(): File {
        val dir = File(context.filesDir, "runtime/workspaces")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    override fun getWorkspaceDirectory(workspaceId: String): File {
        return File(getBaseDirectory(), workspaceId)
    }

    override fun createWorkspaceDirectory(workspaceId: String, initReadme: Boolean, title: String): File {
        val dir = getWorkspaceDirectory(workspaceId)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        if (initReadme) {
            val readme = File(dir, "README.md")
            if (!readme.exists()) {
                val header = if (title.isNotBlank()) "# $title\n\n" else "# Project\n\n"
                readme.writeText("${header}Created with PocketCLI.\n")
            }
        }
        return dir
    }

    override fun deleteWorkspaceDirectory(workspaceId: String): Boolean {
        val dir = getWorkspaceDirectory(workspaceId)
        return if (dir.exists()) {
            dir.deleteRecursively()
        } else {
            true
        }
    }

    override fun getGitStatus(directory: File): WorkspaceGitStatus {
        if (!directory.exists() || !directory.isDirectory) {
            return WorkspaceGitStatus()
        }

        val gitDir = File(directory, ".git")
        val isGit = gitDir.exists()
        var branch: String? = null
        var isDirty = false

        if (isGit) {
            val headFile = File(gitDir, "HEAD")
            if (headFile.exists()) {
                val content = try {
                    headFile.readText().trim()
                } catch (_: Exception) {
                    ""
                }
                branch = when {
                    content.startsWith("ref: refs/heads/") -> content.removePrefix("ref: refs/heads/").trim()
                    content.startsWith("ref: ") -> content.removePrefix("ref: ").trim()
                    content.isNotBlank() -> content.take(7)
                    else -> null
                }
            }

            val indexFile = File(gitDir, "index")
            if (indexFile.exists()) {
                val indexTime = indexFile.lastModified()
                isDirty = hasFilesModifiedAfter(directory, indexTime)
            }
        }

        val count = countFiles(directory)
        return WorkspaceGitStatus(
            branch = branch,
            isGitRepo = isGit,
            isDirty = isDirty,
            fileCount = count
        )
    }

    private fun hasFilesModifiedAfter(dir: File, threshold: Long): Boolean {
        val files = dir.listFiles() ?: return false
        for (f in files) {
            if (f.name == ".git") continue
            if (f.lastModified() > threshold) return true
            if (f.isDirectory) {
                if (hasFilesModifiedAfter(f, threshold)) return true
            }
        }
        return false
    }

    private fun countFiles(dir: File, maxCount: Int = 1000): Int {
        var count = 0
        fun scan(current: File) {
            if (count >= maxCount) return
            val children = current.listFiles() ?: return
            for (c in children) {
                if (c.name == ".git") continue
                if (c.isFile) {
                    count++
                    if (count >= maxCount) return
                } else if (c.isDirectory) {
                    scan(c)
                }
            }
        }
        scan(dir)
        return count
    }
}
