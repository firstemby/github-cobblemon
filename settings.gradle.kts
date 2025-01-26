pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.architectury.dev/")
        maven("https://maven.fabricmc.net/")
    }
}

rootProject.name = "cobblemon"

includeBuild("build-logic")
includeBuild("common") {
    dependencySubstitution {
        substitute(module("com.cobblemon:mod")).using(project(":"))
        substitute(module("com.cobblemon:mod-resources")).using(project(":"))
        substitute(module("com.cobblemon:mod-accesswideners")).using(project(":"))
    }
}

includeBuild("neoforge")
includeBuild("fabric")
