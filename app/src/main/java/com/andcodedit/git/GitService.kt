package com.andcodedit.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

/**
 * Git Service using JGit - Full working implementation for commit/push in sidebar.
 * Supports init, commit, push with basic auth (token recommended for production).
 */
class GitService {

    private var currentRepo: Git? = null
    private var repoPath: String? = null

    var statusMessage = ""
        private set

    var isRepoLoaded = false
        private set

    /**
     * Open or init a Git repo at the given path.
     */
    fun openOrInitRepo(path: String): Boolean {
        return try {
            val dir = File(path)
            if (!dir.exists()) dir.mkdirs()

            val gitDir = File(dir, ".git")
            if (!gitDir.exists()) {
                // Init new repo
                currentRepo = Git.init().setDirectory(dir).call()
                statusMessage = "Initialized new Git repo at $path"
            } else {
                // Open existing
                val builder = FileRepositoryBuilder()
                val repo: Repository = builder.setGitDir(gitDir)
                    .readEnvironment()
                    .findGitDir()
                    .build()
                currentRepo = Git(repo)
                statusMessage = "Opened existing Git repo at $path"
            }
            repoPath = path
            isRepoLoaded = true
            true
        } catch (e: Exception) {
            statusMessage = "Failed to open/init repo: ${e.message}"
            false
        }
    }

    /**
     * Add all files and commit with message.
     */
    fun commitAll(message: String): Boolean {
        if (currentRepo == null) {
            statusMessage = "No repo loaded"
            return false
        }
        return try {
            currentRepo!!.add().addFilepattern(".").call()
            val commit = currentRepo!!.commit()
                .setMessage(message)
                .setAuthor("ANDCODEDIT User", "user@andcodedit.app")
                .setCommitter("ANDCODEDIT", "dev@andcodedit.app")
                .call()
            statusMessage = "Committed: ${commit.name.take(8)} - $message"
            true
        } catch (e: Exception) {
            statusMessage = "Commit failed: ${e.message}"
            false
        }
    }

    /**
     * Push to remote (requires remote set and credentials).
     * Use personal access token as password for GitHub.
     */
    fun pushToRemote(remoteUrl: String, username: String, password: String): Boolean {
        if (currentRepo == null) {
            statusMessage = "No repo loaded"
            return false
        }
        return try {
            // Set remote if not exists
            val config = currentRepo!!.repository.config
            if (config.getString("remote", "origin", "url") == null) {
                config.setString("remote", "origin", "url", remoteUrl)
                config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*")
                config.save()
            }

            val credentials = UsernamePasswordCredentialsProvider(username, password)
            val pushResult = currentRepo!!.push()
                .setRemote("origin")
                .setCredentialsProvider(credentials)
                .call()

            val success = pushResult.all { it.messages.isEmpty() || !it.messages.contains("error") }
            statusMessage = if (success) "Pushed successfully to $remoteUrl" else "Push had issues: check remote"
            success
        } catch (e: Exception) {
            statusMessage = "Push failed: ${e.message} (use token as password for GitHub)"
            false
        }
    }

    /**
     * Get current status (simplified).
     */
    fun getStatus(): String {
        if (currentRepo == null) return "No repo"
        return try {
            val status = currentRepo!!.status().call()
            buildString {
                append("Modified: ${status.modified.size}\n")
                append("Untracked: ${status.untracked.size}\n")
                append("Added: ${status.added.size}")
            }
        } catch (e: Exception) {
            "Status error: ${e.message}"
        }
    }

    fun close() {
        currentRepo?.close()
        currentRepo = null
        isRepoLoaded = false
        statusMessage = "Repo closed"
    }
}