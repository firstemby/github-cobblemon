plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    maven("https://maven.fabricmc.net/")
    maven("https://repo.codemc.io/repository/relativitymc/")
    maven("https://maven.neoforged.net/releases")
    maven("https://maven.terraformersmc.com/")
}

dependencies {
    implementation(libs.kotlin)

    implementation(libs.licenser)
    implementation(libs.shadow)
    implementation(libs.neoloom)

    implementation(libs.blossom)
    implementation(libs.ideaExt)
    implementation(libs.versioning)
}