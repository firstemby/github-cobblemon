/*
 *
 *  * Copyright (C) 2023 Cobblemon Contributors
 *  *
 *  * This Source Code Form is subject to the terms of the Mozilla Public
 *  * License, v. 2.0. If a copy of the MPL was not distributed with this
 *  * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 */

import java.net.URI

configurations.all {
    resolutionStrategy {
        force(libs.fabric.loader)
    }
}

plugins {
    id("cobblemon.platform-conventions")
    id("cobblemon.publish-conventions")
}

architectury {
    platformSetupLoomIde()
    fabric()
}

val generatedResources = file("src/generated/resources")

sourceSets.main {
    resources {
        srcDir(generatedResources)
    }
}

repositories {
    maven(url = "${rootProject.projectDir}/deps")
    mavenLocal()
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven(url = "https://api.modrinth.com/maven")
    maven(url = "https://maven.terraformersmc.com/")
    maven(url = "https://maven.gegy.dev") // LambDynamicLights + spruceui/pridelib (dev runtime)
}

dependencies {
    implementation(project(":common", configuration = "namedElements")) {
        isTransitive = false
    }
    bundle(project(path = ":common", configuration = "transformProductionFabric")) {
        isTransitive = false
    }
    modLocalRuntime(libs.fabric.debugutils)

    // Shader-compat testing (DEV RUNTIME ONLY — never shipped, never a real dependency).
    // Lets `runClient` load Iris + Sodium so shaderpacks actually run, which is required to
    // exercise ParticleMaterials' depthMask branch (see ShaderCompat). isTransitive = false so
    // these don't drag in a second copy of Fabric API / FLK (already provided above). If you bump
    // these, keep the Iris<->Sodium pairing valid (Iris 1.8.x needs Sodium 0.6.x for MC 1.21.1).
    modLocalRuntime("maven.modrinth:iris:1.8.8+1.21.1-fabric") { isTransitive = false }
    modLocalRuntime("maven.modrinth:sodium:mc1.21.1-0.6.13-fabric") { isTransitive = false }
    // Iris bundles these as jar-in-jar in production, but loom doesn't add a mod's nested *libraries*
    // to the dev classpath, so the dev client crashes with NoClassDefFoundError (e.g. org.anarres.cpp
    // / glsl-transformer) when Iris loads a shaderpack. Supply them as plain dev-runtime libs.
    // Versions are taken verbatim from iris-1.8.8's META-INF/jars — keep them in lockstep if Iris is
    // bumped. localRuntime = dev runtime only, not remapped, not exported to the published jar.
    localRuntime("org.anarres:jcpp:1.4.14")
    localRuntime("io.github.douira:glsl-transformer:2.0.1")
    localRuntime("org.antlr:antlr4-runtime:4.13.1")

    // Dynamic lighting (DEV RUNTIME ONLY). Cobblemon compiles against the LambDynamicLights API
    // (modCompileOnly, mojmap — see common/build.gradle.kts); this provides the running mod in dev so
    // `runClient` actually exercises the move-particle dynamic light. Pinned to 4.1.6 to match that
    // API AND fabric-loader 0.17.2 (4.8.9 needs loader >=0.18.4). Loom does NOT surface a mod's JIJ'd
    // dependency mods to the dev run, so its four bundled deps are listed explicitly (from gegy +
    // Maven Central). Bump all five together if LDL is updated.
    modLocalRuntime("maven.modrinth:lambdynamiclights:4.1.6+1.21.1")
    modLocalRuntime("dev.lambdaurora.lambdynamiclights:lambdynamiclights-api:4.1.6+1.21.1")
    modLocalRuntime("dev.lambdaurora:spruceui:6.2.1+1.21")
    modLocalRuntime("dev.yumi.mc.core:yumi-mc-foundation:1.0.0-alpha.7+1.21.1")
    modLocalRuntime("io.github.queerbric:pridelib:1.2.1+1.21")

    modImplementation(libs.fabric.loader)
    modApi(libs.fabric.api)
    modApi(libs.bundles.fabric)

    modCompileOnly(libs.bundles.common.integrations.compileOnly) {
        isTransitive = false
    }

    modImplementation(libs.bundles.fabric.integrations.implementation)
    modRuntimeOnly(libs.bundles.fabric.integrations.runtimeOnly)
    modRuntimeOnly(libs.bundles.mongo)

//    modImplementation(libs.flywheelFabric)
//    include(libs.flywheelFabric)

    listOf(
        libs.fabric.kotlin,
        libs.bundles.graal,
        libs.bundles.mongo
    ).forEach {
        include(it)
    }

    // Added to make graal available in dev
    modRuntimeOnly(libs.bundles.graal)

    include(libs.fabric.kotlin)

    listOf(
        libs.molang
    ).forEach {
        bundle(it)
        runtimeOnly(it)
    }

    minecraftServerLibraries(libs.icu4j)

}

publishing {
    publications.named<MavenPublication>(project.name) {
        pom.withXml {
            val root = asNode()
            val depsNode = (root["dependencies"] as? groovy.util.NodeList)
                ?.firstOrNull() as? groovy.util.Node
                ?: root.appendNode("dependencies")

            configurations.getByName("include").allDependencies
                .filterIsInstance<ExternalModuleDependency>()
                .forEach { dep ->
                    depsNode.appendNode("dependency").apply {
                        appendNode("groupId", dep.group)
                        appendNode("artifactId", dep.name)
                        appendNode("version", dep.version)
                        appendNode("scope", "compile")
                    }
                }
        }
    }
}

tasks {
    // Downloads the Complementary Unbound shaderpack into the dev client's shaderpacks/ folder and
    // enables Iris by default, so `runClient` comes up shader-ready with zero manual setup. Both
    // writes are idempotent: existing files are left untouched, so your in-game shader choice and
    // any pack you swap in are preserved across runs. Run dir is "runClient" (see platform-conventions).
    val setupShaderTesting by registering {
        group = "cobblemon"
        description = "Provisions the test shaderpacks + enables Iris for the dev runClient."
        // The four Iris packs we want particles to look correct under. Swap between them in-game
        // (Video Settings -> Shader Packs) to A/B the premultiplied-alpha fix in ParticleMaterials.
        // <filename> to <direct Modrinth CDN url>.
        //
        // VERSIONS ARE PINNED FOR MC 1.21.1. The newest releases of these packs reference the
        // BIOME_PALE_GARDEN uniform (Pale Garden is a 1.21.4 biome), which Iris cannot resolve on
        // 1.21.1 -> hard crash building the render pipeline ("Unknown variable: BIOME_PALE_GARDEN").
        // Each pin below was confirmed Pale-Garden-free by grepping the archive. If this project ever
        // targets >= 1.21.4, bump these to the latest versions.
        val packs = mapOf(
            "ComplementaryUnbound_r5.3.zip" to "https://cdn.modrinth.com/data/R6NEzAwj/versions/CVu3AGAd/ComplementaryUnbound_r5.3.zip",
            "ComplementaryReimagined_r5.3.zip" to "https://cdn.modrinth.com/data/HVnmMxH1/versions/71Hn3myZ/ComplementaryReimagined_r5.3.zip",
            "Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip" to "https://cdn.modrinth.com/data/ZvMtQlho/versions/kC2Y8q1P/Bliss_v2.1.2_%28Chocapic13_Shaders_edit%29.zip",
            "photon_v1.1.zip" to "https://cdn.modrinth.com/data/lLqFfGNs/versions/6FdrBtKp/photon_v1.1.zip"
        )
        val shaderpacksDir = layout.projectDirectory.dir("runClient/shaderpacks")
        val irisProps = layout.projectDirectory.file("runClient/config/iris.properties")
        // Default the in-game selection to Complementary Unbound (the originally-reported pack); idempotent.
        val defaultPack = "ComplementaryUnbound_r5.3.zip"
        outputs.dir(shaderpacksDir)
        doLast {
            packs.forEach { (filename, url) ->
                val zip = shaderpacksDir.file(filename).asFile
                if (!zip.exists()) {
                    zip.parentFile.mkdirs()
                    logger.lifecycle("Downloading shaderpack -> ${zip.relativeTo(rootProject.projectDir)}")
                    URI(url).toURL().openStream().use { input ->
                        zip.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
            val props = irisProps.asFile
            if (!props.exists()) {
                props.parentFile.mkdirs()
                props.writeText("enableShaders=true\nshaderPack=$defaultPack\n")
                logger.lifecycle("Wrote ${props.relativeTo(rootProject.projectDir)} (Iris auto-enabled with $defaultPack).")
            }
        }
    }

    named("runClient") {
        dependsOn(setupShaderTesting)
    }

    // The AW file is needed in :fabric project resources when the game is run.
    val copyAccessWidener by registering(Copy::class) {
        from(loom.accessWidenerPath)
        into(generatedResources)
        dependsOn(checkLicenseMain)
    }

    processResources {
        dependsOn(copyAccessWidener)
        inputs.property("version", rootProject.version)
        inputs.property("fabric_loader_version", libs.fabric.loader.get().version)
        inputs.property("fabric_api_version", libs.fabric.api.get().version)
        inputs.property("minecraft_version", rootProject.property("mc_version").toString())
        inputs.property("java_version", rootProject.property("java_version").toString())

        filesMatching("fabric.mod.json") {
            expand(
                "version" to rootProject.version,
                "fabric_loader_version" to libs.fabric.loader.get().version,
                "fabric_api_version" to libs.fabric.api.get().version,
                "minecraft_version" to rootProject.property("mc_version").toString(),
                "java_version" to rootProject.property("java_version").toString()
            )
        }
    }

    sourcesJar {
        dependsOn(copyAccessWidener)
    }
}
