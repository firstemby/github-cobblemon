/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime

import com.cobblemon.mod.common.api.cinematic.model.ActorBinding
import com.cobblemon.mod.common.api.cinematic.model.ActorDeclaration
import com.cobblemon.mod.common.api.cinematic.model.ActorKind
import com.cobblemon.mod.common.api.cinematic.model.Beat
import com.cobblemon.mod.common.api.cinematic.model.Scene
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class ActorDirectorPreviewModeTest {

    @Test
    fun `previewMode director with null serverLevel does not spawn a server entity`() {
        val beat = Beat(id = "main")
        val scene = Scene(
            id = "preview.test",
            actors = mapOf("user" to ActorDeclaration(id = "user", kind = ActorKind.SynthesizedPokemon)),
            beats = mapOf("main" to beat),
            startBeatId = "main"
        )
        val bindings = mapOf(
            "user" to ActorBinding.SynthPokemon(
                properties = PokemonProperties(),
                aspects = emptySet()
            )
        )
        val director = ActorDirector(
            scene = scene,
            bindings = bindings,
            networkSync = null,
            serverLevel = null,
            sceneId = UUID.randomUUID(),
            previewMode = true
        )

        assertNull(director.resolveEntityPublic("user"))
        assertFalse(director.hasTrackedPreviewEntity("user"))
    }
}
