package com.cobblemon.mod.common.api.cinematic.runtime

import net.minecraft.world.phys.AABB

object ActorFraming {
    val groups = mapOf(
        "@trainers" to "cobblemon.cinematic_editor.actor_group.trainers",
        "@pokemon" to "cobblemon.cinematic_editor.actor_group.pokemon"
    )

    fun expand(ids: List<String>, available: Set<String>): List<String> = ids.flatMap { id ->
        when (id) {
            "@trainers" -> available.filter { it.startsWith("trainer_") }
            "@pokemon" -> available.filter { it.startsWith("pokemon_") }
            else -> listOf(id)
        }
    }.distinct()

    fun bounds(ids: List<String>, director: ActorDirector?): AABB? = expand(ids,
        director?.let { it.scene.actors.keys + it.bindings.keys }.orEmpty()).mapNotNull {
        director?.cameraBounds(it)
    }.reduceOrNull(AABB::minmax)
}
