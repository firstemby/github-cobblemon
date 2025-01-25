rootProject.name = "cobblemon"

includeBuild("build-logic")
includeBuild("common") {
    dependencySubstitution {
        substitute(module("com.cobblemon:mod")).using(project(":"))
        substitute(module("com.cobblemon:mod-resources")).using(project(":"))
    }
}

include("neoforge")
