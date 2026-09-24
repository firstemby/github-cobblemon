package utilities

import org.gradle.api.Project

fun Project.version(): String {
    return rootProject.property("mod_version").toString()
}

fun Project.isSnapshot(): Boolean {
    return rootProject.property("snapshot") == "true"
}

fun Project.writeVersion(type: VersionType = VersionType.FULL): String {
    val version = "${rootProject.property("mod_version")}+${rootProject.property("mc_version")}"
    return when (type) {
        VersionType.PUBLISHING -> if(this.isSnapshot()) "$version-SNAPSHOT" else version
        VersionType.FULL -> if(this.isSnapshot()) rootProject.version.toString() else version
    }
}

enum class VersionType {
    PUBLISHING,
    FULL
}
// Null when git isn't installed or this isn't a git checkout (e.g. a source zip download)
private fun Project.git(vararg args: String): String? = runCatching {
    providers.exec {
        commandLine("git", *args)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
}.getOrNull()?.takeIf { it.isNotEmpty() }

// git CLI rather than the versioning plugin, whose JGit status scan costs ~30s per Gradle run. CI provides these.
fun Project.gitCommit(): String = System.getenv("CI_COMMIT_SHA") ?: git("rev-parse", "HEAD") ?: "unknown"
fun Project.gitShortCommit(): String = gitCommit().take(7)
fun Project.gitBranch(): String = System.getenv("CI_COMMIT_REF_NAME") ?: git("rev-parse", "--abbrev-ref", "HEAD") ?: "unknown"
