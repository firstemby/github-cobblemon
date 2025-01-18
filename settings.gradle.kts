import org.gradle.internal.impldep.com.google.common.collect.TreeTraverser.using

rootProject.name = "cobblemon"

includeBuild("build-logic")
include("common")
include("neoforge")