/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.runner.graal

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.battles.BagItems
import com.cobblemon.mod.common.battles.ShowdownInterpreter
import com.cobblemon.mod.common.battles.runner.ShowdownDebugLog
import com.cobblemon.mod.common.battles.runner.ShowdownService
import com.cobblemon.mod.common.battles.runner.ShowdownStatus
import com.google.gson.Gson
import com.google.gson.JsonArray
import net.minecraft.resources.ResourceLocation
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.AccessMode
import java.nio.file.DirectoryStream
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.FileAttribute
import java.util.UUID
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.HostAccess.Export
import org.graalvm.polyglot.PolyglotAccess
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.io.FileSystem

/**
 * Mediator service for communicating between the Cobblemon Minecraft mod and Cobblemon showdown service via
 * GraalVM. This directly invokes JavaScript functions provided within the showdown service.
 *
 * @property id The unique identifier for this showdown service.
 * @property context The GraalVM context for executing JavaScript.
 * @property sendBattleMessageFunction The JavaScript function for sending battle messages.
 * @property unbundler The unbundler for extracting necessary showdown files.
 * @property gson The Gson instance for JSON operations.
 * @property log The debug log for the showdown service.
 * @property status The current status of the showdown service.
 *
 * @see {@code cobbled-exports/cobbled-index.js} within cobbledmon-showdown repository
 * @see <a href="https://www.graalvm.org/">
 * @since February 27, 2023
 * @author Hiroku, landonjw
 */
class GraalShowdownService(override val id: ResourceLocation) : ShowdownService {

    @Transient
    lateinit var context: Context
    @Transient
    lateinit var sendBattleMessageFunction: Value
    @Transient
    val unbundler = GraalShowdownUnbundler()
    @Transient
    val gson = Gson()

    override val log = ShowdownDebugLog()

    override var status: ShowdownStatus = ShowdownStatus.DISCONNECTED
        private set

    /**
     * Opens a connection to the showdown service.
     */
    override fun openConnection() = openConnection(false)

    /**
     * Opens a connection to the showdown service.
     *
     * @param silent If true, suppresses changing the status of the connection process.
     */
    private fun openConnection(silent: Boolean) {
        log.clear()
        log.add(source = "Minecraft", message = "Opening showdown connection")
        unbundler.attemptUnbundle()
        createContext()
        boot()
        if (!silent) {
            status = ShowdownStatus.CONNECTED
        }
    }

    /**
     * Creates the GraalVM context for executing JavaScript.
     */
    private fun createContext() {
        val wd = Paths.get("./showdown")
        val access = HostAccess.newBuilder(HostAccess.EXPLICIT)
            .allowIterableAccess(true)
            .allowArrayAccess(true)
            .allowListAccess(true)
            .allowMapAccess(true)
            .build()
        context = Context.newBuilder("js")
            .allowIO(true)
            .fileSystem(object : FileSystem
                {
                    val default = FileSystem.newDefaultFileSystem()
                    override fun parsePath(uri: URI) = default.parsePath(uri)
                    override fun parsePath(path: String) = default.parsePath(path)
                    override fun createDirectory(dir: Path, vararg attrs: FileAttribute<*>) = default.createDirectory(dir, *attrs)
                    override fun delete(path: Path) = default.delete(path)
                    override fun newByteChannel(path: Path, options: MutableSet<out OpenOption>, vararg attrs: FileAttribute<*>) = default.newByteChannel(path, options, *attrs)
                    override fun newDirectoryStream(dir: Path, filter: DirectoryStream.Filter<in Path>) = default.newDirectoryStream(dir, filter)
                    override fun toAbsolutePath(path: Path) = default.toAbsolutePath(path)
                    override fun toRealPath(path: Path, vararg linkOptions: LinkOption) = default.toRealPath(path, *linkOptions)
                    override fun readAttributes(path: Path, attributes: String, vararg options: LinkOption) = default.readAttributes(path, attributes, *options)
                    override fun checkAccess(path: Path, modes: MutableSet<out AccessMode>, vararg linkOptions: LinkOption) {
                        if (!path.toRealPath(LinkOption.NOFOLLOW_LINKS).startsWith(wd.toRealPath(LinkOption.NOFOLLOW_LINKS))) {
                            Cobblemon.LOGGER.error("Hacked JS files in datapacks or some weird file system setup that Hiroku failed to anticipate.")
                            throw IOException("Someone has put hacked JS files into datapacks because file access is being attempted outside of controlled folders.")
                        }
                    }
                }
            )
            .allowExperimentalOptions(true)
            .allowPolyglotAccess(PolyglotAccess.ALL)
            .allowHostAccess(access)
            .allowCreateThread(true)
            .logHandler(GraalLogger)
            .option("engine.WarnInterpreterOnly", "false")
            .option("js.commonjs-require", "true")
            .option("js.commonjs-require-cwd", "showdown")
            .option(
                "js.commonjs-core-modules-replacements",
                "buffer:buffer/,crypto:crypto-browserify,path:path-browserify"
            )
            .allowHostClassLoading(true)
            .allowNativeAccess(true)
            .allowCreateProcess(true)
            .build()

        context.eval("js", """
            globalThis.process = {
                cwd: function() {
                    return '';
                }
            }
        """.trimIndent())
    }

    /**
     * Closes the connection to the showdown service.
     */
    override fun closeConnection() = closeConnection(false)

    /**
     * Closes the connection to the showdown service.
     *
     * @param silent If true, suppresses changing the status of the connection process.
     */
    private fun closeConnection(silent: Boolean) {
        log.add(source = "Minecraft", message = "Closing showdown connection")
        context.close()
        if (!silent) {
            this.status = ShowdownStatus.DISCONNECTED
        }
    }

    /**
     * Restarts the connection to the showdown service.
     */
    override fun restartConnection() {
        this.status = ShowdownStatus.RESTARTING
        try {
            closeConnection(true)
            openConnection(true)
            this.status = ShowdownStatus.CONNECTED
        } catch (e: Exception) {
            Cobblemon.LOGGER.error("Failed to restart showdown connection", e)
            this.status = ShowdownStatus.DISCONNECTED
        }
    }

    /**
     * Boots the showdown service by evaluating the JavaScript entry point.
     */
    private fun boot() {
        log.add(source = "Minecraft", message = "Booting showdown")
        context.eval("js", File("showdown/index.js").readText())
        sendBattleMessageFunction = context.getBindings("js").getMember("sendBattleMessage")
    }

    /**
     * Starts a battle in the showdown service.
     *
     * @param battle The battle to start.
     * @param messages The initial messages for the battle.
     */
    override fun startBattle(battle: PokemonBattle, messages: Array<String>) {
        val startBattleFunction = context.getBindings("js").getMember("startBattle")
        log.add(source = "Minecraft", message = "Starting battle ${battle.battleId}")
        messages.forEach { message ->
            log.add(source = "Minecraft", message = "(${battle.battleId}) $message")
        }
        startBattleFunction.execute(this, battle.battleId.toString(), messages)
    }

    /**
     * Sends messages to a specific battle in the showdown service.
     *
     * @param battleId The unique identifier of the battle.
     * @param messages The messages to send.
     */
    override fun send(battleId: UUID, messages: Array<String>) {
        sendToShowdown(battleId, messages)
    }

    /**
     * Sends a message to the showdown service.
     *
     * @param message The message to send.
     */
    override fun send(message: String) {
        log.add("Minecraft", message)
        val regex = Regex("^\\(([^()]*)\\)")
        val match = regex.find(message)
        val battleId = match?.groups?.get(1)?.value ?: return
        val endIndex = match.groups[1]!!.range.last + 1
        sendToShowdown(UUID.fromString(battleId), arrayOf(message.substring(endIndex)))
    }

    /**
     * Retrieves the ability IDs from the showdown service.
     *
     * @return A `JsonArray` of ability IDs.
     */
    override fun getAbilityIds(): JsonArray {
        val getCobbledAbilityIdsFn = context.getBindings("js").getMember("getCobbledAbilityIds")
        val arrayResult = getCobbledAbilityIdsFn.execute().asString()
        log.add(source = "Minecraft", message = "Getting ability ids")
        return gson.fromJson(arrayResult, JsonArray::class.java)
    }

    /**
     * Retrieves the moves from the showdown service.
     *
     * @return A `JsonArray` of moves.
     */
    override fun getMoves(): JsonArray {
        val getCobbledMovesFn = context.getBindings("js").getMember("getCobbledMoves")
        log.add(source = "Minecraft", message = "Getting moves")
        val arrayResult = getCobbledMovesFn.execute().asString()
        return gson.fromJson(arrayResult, JsonArray::class.java)
    }

    /**
     * Retrieves the item IDs from the showdown service.
     *
     * @return A `JsonArray` of item IDs.
     */
    override fun getItemIds(): JsonArray {
        val getCobbledItemIdsFn = context.getBindings("js").getMember("getCobbledItemIds")
        log.add(source = "Minecraft", message = "Getting item ids")
        val arrayResult = getCobbledItemIdsFn.execute().asString()
        return gson.fromJson(arrayResult, JsonArray::class.java)
    }

    /**
     * Registers species in the showdown service.
     */
    override fun registerSpecies() {
        val receiveSpeciesDataFn = this.context.getBindings("js").getMember("receiveSpeciesData")
        val jsArray = this.context.eval("js", "new Array();")
        var index = 0L
        PokemonSpecies.species.forEach { species ->
            jsArray.setArrayElement(index++, this.gson.toJson(PokemonSpecies.ShowdownSpecies(species, null)))
            species.forms.forEach { form ->
                if (form != species.standardForm) {
                    jsArray.setArrayElement(index++, this.gson.toJson(PokemonSpecies.ShowdownSpecies(species, form)))
                }
            }
        }
        log.add(source = "Minecraft", message = "Registering species")
        receiveSpeciesDataFn.execute(jsArray)
    }

    /**
     * Indicates that species have been initialized to the showdown service.
     */
    override fun indicateSpeciesInitialized() {
        val afterCobbledSpeciesInitFn = this.context.getBindings("js").getMember("afterCobbledSpeciesInit")
        log.add(source = "Minecraft", message = "Indicating species initialized")
        afterCobbledSpeciesInitFn.execute()
    }

    /**
     * Registers bag items in the showdown service.
     */
    override fun registerBagItems() {
        val receiveBagItemDataFn = this.context.getBindings("js").getMember("receiveBagItemData")
        for ((itemId, js) in BagItems.bagItemsScripts) {
            log.add(source = "Minecraft", message = "Registering bag item $itemId")
            receiveBagItemDataFn.execute(itemId, js.replace("\n", " "))
        }
    }

    /**
     * Sends messages to the showdown service.
     *
     * @param battleId The unique identifier of the battle.
     * @param messages The messages to send.
     */
    private fun sendToShowdown(battleId: UUID, messages: Array<String>) {
        messages.forEach { message ->
            log.add(source = "Minecraft", message = "($battleId) $message")
        }
        sendBattleMessageFunction.execute(battleId.toString(), messages)
    }

    /**
     * Receives messages from the showdown service.
     *
     * @param battleId The unique identifier of the battle.
     * @param message The message received.
     */
    @Export
    fun sendFromShowdown(battleId: String, message: String) {
        log.add(source = "Showdown", message = "($battleId) $message")
        ShowdownInterpreter.interpretMessage(UUID.fromString(battleId), message)
    }

    /**
     * Logs messages from the showdown service.
     *
     * @param message The message to log.
     */
    @Export
    fun log(message: String) {
        Cobblemon.LOGGER.info(message)
    }
}
