rootProject.name = "cobblemon"

pluginManagement {
    includeBuild("build-logic")
}

listOf(
    "common"
    //"fabric"
    //"neoforge"
).forEach { include(it) }

