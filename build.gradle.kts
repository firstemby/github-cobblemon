import utilities.gitBranch
import utilities.gitShortCommit

plugins {
    id("cobblemon.root-conventions")
}

version = "${project.property("mod_version")}+${project.property("mc_version")}"

val isSnapshot = project.property("snapshot")?.equals("true") == true
if (isSnapshot) {
    val fixedBranchName = gitBranch().substringAfter("/")
    version = "$version-${fixedBranchName}-${gitShortCommit()}"
}
