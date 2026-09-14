package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import com.cobblemon.mod.common.api.cinematic.registry.SceneBuiltinSources
import com.cobblemon.mod.common.api.cinematic.registry.SceneSourceRegistry
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ShotSourceDefaultsTest {

    @BeforeEach
    fun setUp() {
        SceneSourceRegistry.clear()
        SceneBuiltinSources.register()
    }

    private fun parse(channel: SourceChannel, entry: SourceTypeEntry): Any? {
        val json = entry.defaultJson()
        val type = json.get("type").asString
        assertEquals(entry.typeId, type)
        assertSame(entry, ShotSourceTypes.entryFor(channel, type))
        return when (channel) {
            SourceChannel.POSITION -> SceneSourceRegistry.deserializePositionSource(type, json)
            SourceChannel.ROTATION -> SceneSourceRegistry.deserializeRotationSource(type, json)
            SourceChannel.FOV -> SceneSourceRegistry.deserializeFovSource(type, json)
            SourceChannel.ROLL -> SceneSourceRegistry.deserializeRollSource(type, json)
            SourceChannel.MODIFIER -> SceneSourceRegistry.deserializeModifier(type, json)
        }
    }

    @Test
    fun `every default template parses and remains selectable by its saved type`() {
        SourceChannel.entries.forEach { channel ->
            ShotSourceTypes.forChannel(channel).forEach { entry ->
                val parsed = parse(channel, entry)
                assertNotNull(parsed, "$channel default '${entry.typeId}' did not deserialize")
            }
        }
    }

    @Test
    fun `every actor track template parses through the engine registry`() {
        ActorTrackTypes.entries().forEach { entry ->
            assertNotNull(SceneSourceRegistry.deserializeActorTrack(entry.trackType, entry.defaultBody()), entry.trackType)
        }
    }

    @Test
    fun `the channels a new shot must seed have a usable fixed default`() {
        val position = ShotSourceTypes.defaultJsonFor(SourceChannel.POSITION, "fixed")
        val rotation = ShotSourceTypes.defaultJsonFor(SourceChannel.ROTATION, "fixed")
        assertNotNull(SceneSourceRegistry.deserializePositionSource("fixed", position))
        assertNotNull(SceneSourceRegistry.deserializeRotationSource("fixed", rotation))
    }
}
