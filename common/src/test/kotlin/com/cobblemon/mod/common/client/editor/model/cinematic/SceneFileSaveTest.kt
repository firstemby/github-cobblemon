/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.model.cinematic

import com.google.gson.JsonParser
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SceneFileSaveTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `save replaces the file with complete UTF-8 scene content`() {
        val target = directory.resolve("scene.json")
        target.writeText("previous scene contents")
        val editor = EditorScene.createDefault().apply {
            id = "Arène des étoiles"
            markDirty()
        }

        SceneSerializer.saveToFile(editor, target)

        assertEquals(editor.id, JsonParser.parseString(target.readText()).asJsonObject.get("sceneId").asString)
        assertEquals(target, editor.sourcePath)
        assertFalse(editor.isDirty)
        assertEquals(listOf(target), Files.list(directory).use { it.toList() })
    }

    @Test
    fun `failed replacement preserves editor state and cleans temporary files`() {
        val target = Files.createDirectory(directory.resolve("scene.json"))
        val existing = target.resolve("keep.json").apply { writeText("existing content") }
        val previous = directory.resolve("previous.json")
        val editor = EditorScene.createDefault().apply {
            sourcePath = previous
            markDirty()
        }

        assertThrows(IOException::class.java) { SceneSerializer.saveToFile(editor, target) }

        assertEquals("existing content", existing.readText())
        assertEquals(previous, editor.sourcePath)
        assertTrue(editor.isDirty)
        assertEquals(listOf(target), Files.list(directory).use { it.toList() })
    }
}
