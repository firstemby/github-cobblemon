/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.npc

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.struct.VariableStruct
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonEntities
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.CobblemonNetwork.sendPacket
import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.entity.PokemonSender
import com.cobblemon.mod.common.api.molang.MoLangFunctions.addFunctions
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMoLangValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import com.cobblemon.mod.common.api.moves.animations.ActionEffectContext
import com.cobblemon.mod.common.api.net.serializers.IdentifierDataSerializer
import com.cobblemon.mod.common.api.net.serializers.NPCPlayerTextureSerializer
import com.cobblemon.mod.common.api.net.serializers.PoseTypeDataSerializer
import com.cobblemon.mod.common.api.net.serializers.StringSetDataSerializer
import com.cobblemon.mod.common.api.net.serializers.UUIDSetDataSerializer
import com.cobblemon.mod.common.api.npc.NPCClasses
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.api.npc.configuration.NPCBattleConfiguration
import com.cobblemon.mod.common.api.npc.configuration.NPCBehaviourConfiguration
import com.cobblemon.mod.common.api.npc.configuration.NPCInteractConfiguration
import com.cobblemon.mod.common.api.permission.CobblemonPermissions.SEE_HIDDEN_NPCS
import com.cobblemon.mod.common.api.scheduling.Schedulable
import com.cobblemon.mod.common.api.scheduling.SchedulingTracker
import com.cobblemon.mod.common.api.storage.party.NPCPartyStore
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.entity.BehaviourEditingTracker
import com.cobblemon.mod.common.entity.EntityCallbacks
import com.cobblemon.mod.common.entity.MoLangScriptingEntity
import com.cobblemon.mod.common.entity.OmniPathingEntity
import com.cobblemon.mod.common.entity.PosableEntity
import com.cobblemon.mod.common.entity.PoseType
import com.cobblemon.mod.common.entity.ai.OmniPathNavigation
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.npc.CloseNPCEditorPacket
import com.cobblemon.mod.common.net.messages.client.npc.OpenNPCEditorPacket
import com.cobblemon.mod.common.net.messages.client.spawn.SpawnNPCPacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.DataKeys
import com.cobblemon.mod.common.util.getBattleState
import com.cobblemon.mod.common.util.getPlayer
import com.cobblemon.mod.common.util.makeEmptyBrainDynamic
import com.cobblemon.mod.common.util.withNPCValue
import com.mojang.authlib.ProfileLookupCallback
import com.mojang.serialization.Codec
import com.mojang.serialization.Dynamic
import java.net.URI
import java.util.UUID
import java.util.concurrent.CompletableFuture
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component.nbt
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.AgeableMob
import net.minecraft.world.entity.EntityDimensions
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.ai.Brain
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.sensing.Sensor
import net.minecraft.world.entity.ai.sensing.SensorType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import kotlin.jvm.optionals.getOrNull

class NPCEntity(world: Level) : AgeableMob(CobblemonEntities.NPC, world), PosableEntity, PokemonSender, Schedulable, MoLangScriptingEntity, OmniPathingEntity {
    override val schedulingTracker = SchedulingTracker()

    override val struct = this.asMoLangValue()

    val runtime = MoLangRuntime().setup().withNPCValue(value = this)

    var npc = NPCClasses.dummy()
        set(value) {
            entityData.set(NPC_CLASS, value.id)
            val valueChanged = field != value
            field = value
            if (valueChanged) {
                customName = value.names.randomOrNull() ?: "NPC".text()
                if (!level().isClientSide) {
                    entityData.set(RESOURCE_IDENTIFIER, forcedResourceIdentifier ?: value.resourceIdentifier)
                    remakeBrain()
                }
            }

            this.refreshDimensions()
        }

    val level: Int
        get() = entityData.get(LEVEL)

    var hideNameTag: Boolean
        get() = entityData.get(HIDE_NAME_TAG)
        set(value) {
            entityData.set(HIDE_NAME_TAG, value)
        }

    var renderScale: Float
        get() = entityData.get(RENDER_SCALE)
        set(value) {
            entityData.set(RENDER_SCALE, value)
        }

    var hitboxScale: Float
        get() = entityData.get(HITBOX_SCALE)
        set(value) {
            entityData.set(HITBOX_SCALE, value)
            refreshDimensions()
        }

    var hitboxEyesHeight: Float
        get() = entityData.get(HITBOX_EYES_HEIGHT)
        set(value) {
            if (!level().isClientSide && entityData.get(HITBOX_EYES_HEIGHT) == value) {
                return
            }
            entityData.set(HITBOX_EYES_HEIGHT, value)
            hitbox = (hitbox ?: npc.hitbox).let { EntityDimensions.scalable(it.width, it.height).withEyeHeight(value) }
            refreshDimensions()
        }

    var hitboxWidth: Float
        get() = entityData.get(HITBOX_WIDTH)
        set(value) {
            if (!level().isClientSide && entityData.get(HITBOX_WIDTH) == value) {
                return
            }
            entityData.set(HITBOX_WIDTH, value)
            hitbox = (hitbox ?: npc.hitbox).let { EntityDimensions.scalable(value, it.height).withEyeHeight(it.eyeHeight) }
            refreshDimensions()
        }

    var hitboxHeight: Float
        get() = entityData.get(HITBOX_HEIGHT)
        set(value) {
            if (!level().isClientSide && entityData.get(HITBOX_HEIGHT) == value) {
                return
            }
            entityData.set(HITBOX_HEIGHT, value)
            hitbox = (hitbox ?: npc.hitbox).let { EntityDimensions.scalable(it.width, value).withEyeHeight(it.eyeHeight) }
            refreshDimensions()
        }

    var hitbox: EntityDimensions? = null
        set(value) {
            val comparison = value ?: npc.hitbox
            field = value
            entityData.set(HITBOX_HEIGHT, comparison.height)
            entityData.set(HITBOX_WIDTH, comparison.width)
            entityData.set(HITBOX_EYES_HEIGHT, comparison.eyeHeight)
        }

    var resourceIdentifier: Identifier
        get() = entityData.get(RESOURCE_IDENTIFIER)
        private set(value) {
            entityData.set(RESOURCE_IDENTIFIER, value)
        }

    var forcedResourceIdentifier: Identifier? = null
        set(value) {
            field = value
            if (value != null) {
                entityData.set(RESOURCE_IDENTIFIER, value)
            } else {
                entityData.set(RESOURCE_IDENTIFIER, npc.resourceIdentifier)
            }
        }

    var skill: Int? = null // range from 0 - 5

    var party: NPCPartyStore? = null

    var isMovable: Boolean? = null

    var isInvulnerable: Boolean? = null

    var isLeashable: Boolean? = null

    var allowProjectileHits: Boolean? = null

    fun getPartyForChallenge(players: List<ServerPlayer>): NPCPartyStore? {
        val party = this.party
        return if (party != null) {
            party
        } else if (npc.party?.isStatic == false) {
            npc.party?.provide(this, level, players)
        } else {
            null
        }
    }

    /** Oi, dev, you no touch this one. This one is for [com.cobblemon.mod.common.api.npc.variation.NPCVariationProvider]s. */
    val variationAspects = mutableSetOf<String>()
    /** You can add to this one if you want, that's ok. */
    val appliedAspects = mutableSetOf<String>()

    override val delegate = if (world.isClientSide) {
        com.cobblemon.mod.common.client.entity.NPCClientDelegate()
    } else {
        NPCServerDelegate()
    }

    var battle: NPCBattleConfiguration? = null
    var behaviour: NPCBehaviourConfiguration? = null

    var interaction: NPCInteractConfiguration? = null

    override var behavioursAreCustom = false
    override val behaviours = mutableListOf<Identifier>()
    override val registeredVariables: MutableList<MoLangConfigVariable> = mutableListOf()
    override var data = VariableStruct()
    override var config = VariableStruct()
    override var callbacks = EntityCallbacks(this)

    val aspects: Set<String>
        get() = entityData.get(ASPECTS)

    val battleIds: Set<UUID>
        get() = entityData.get(BATTLE_IDS)

    var actionEffect: ActionEffectContext? = null

    /** Essentially a cached form of what was serialized to make memory reloads still work despite dynamic brain activities on class change. */
    private var brainDynamic: Dynamic<*>? = null


    /* TODO NPC Valuables to add:
     *
     * -- An 'interaction' configuration. This can be loaded from a JSON or API or even a .js (ambitious). Handles what happens
     * -- when you right click. Can be a dialogue tree with some complexity, or provides options to open a shopkeeper GUI,
     * -- that sort of deal. As extensible as we can manage it (and we can manage a lot).
     *
     * A 'party provider' configuration. This is for an NPC that's going to be used as a trainer. A stack of configuration
     * planning has been done by Vera and Design, get it from them and tweak to be clean.
     *
     * A pathing configuration. Another one that could be loaded from JSON or .js or API. Controls AI.
     *
     * npcs should be able to sleep lol
     */

    init {
        delegate.initialize(this)
        addPosableFunctions(struct)
        runtime.environment.query.addFunctions(struct.functions)
        refreshDimensions()
        navigation.setCanFloat(false)
        if (!world.isClientSide) {
            remakeBrain()
        }
    }

    // This has to be below constructor and entity tracker fields otherwise initialization order is weird and breaks them syncing
    companion object {
        fun createAttributes(): AttributeSupplier.Builder = createMobAttributes()
            .add(Attributes.ATTACK_DAMAGE, 1.0)
            .add(Attributes.ATTACK_KNOCKBACK)

        val NPC_CLASS = SynchedEntityData.defineId(NPCEntity::class.java, IdentifierDataSerializer)
        val RESOURCE_IDENTIFIER = SynchedEntityData.defineId(NPCEntity::class.java, IdentifierDataSerializer)
        val ASPECTS = SynchedEntityData.defineId(NPCEntity::class.java, StringSetDataSerializer)
        val POSE_TYPE = SynchedEntityData.defineId(NPCEntity::class.java, PoseTypeDataSerializer)
        val BATTLE_IDS = SynchedEntityData.defineId(NPCEntity::class.java, UUIDSetDataSerializer)
        val NPC_PLAYER_TEXTURE = SynchedEntityData.defineId(NPCEntity::class.java, NPCPlayerTextureSerializer)
        val LEVEL = SynchedEntityData.defineId(NPCEntity::class.java, EntityDataSerializers.INT)
        val HIDE_NAME_TAG = SynchedEntityData.defineId(NPCEntity::class.java, EntityDataSerializers.BOOLEAN)
        val RENDER_SCALE = SynchedEntityData.defineId(NPCEntity::class.java, EntityDataSerializers.FLOAT)
        val HITBOX_SCALE = SynchedEntityData.defineId(NPCEntity::class.java, EntityDataSerializers.FLOAT)
        val HITBOX_WIDTH = SynchedEntityData.defineId(NPCEntity::class.java, EntityDataSerializers.FLOAT)
        val HITBOX_HEIGHT = SynchedEntityData.defineId(NPCEntity::class.java, EntityDataSerializers.FLOAT)
        val HITBOX_EYES_HEIGHT = SynchedEntityData.defineId(NPCEntity::class.java, EntityDataSerializers.FLOAT)

//        val BATTLING = Activity.register("npc_battling")

        const val SEND_OUT_ANIMATION = "send_out"
        const val RECALL_ANIMATION = "recall"
        const val LOSE_ANIMATION = "lose"
        const val WIN_ANIMATION = "win"
    }

    override fun brainProvider() = Brain.provider<NPCEntity>(emptySet(), emptySet())
    override fun getBreedOffspring(world: ServerLevel, entity: AgeableMob) = null // No lovemaking! Unless...
    override fun getCurrentPoseType() = this.entityData.get(POSE_TYPE)

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        super.defineSynchedData(builder)
        builder.define(NPC_CLASS, NPCClasses.classes.first().id)
        builder.define(RESOURCE_IDENTIFIER, NPCClasses.classes.first().resourceIdentifier)
        builder.define(ASPECTS, emptySet())
        builder.define(POSE_TYPE, PoseType.STAND)
        builder.define(BATTLE_IDS, setOf())
        builder.define(NPC_PLAYER_TEXTURE, NPCPlayerTexture(ByteArray(1), NPCPlayerModelType.NONE))
        builder.define(LEVEL, 1)
        builder.define(HIDE_NAME_TAG, false)
        builder.define(RENDER_SCALE, 1F)
        builder.define(HITBOX_SCALE, 1F)
        builder.define(HITBOX_WIDTH, 0.6F)
        builder.define(HITBOX_HEIGHT, 1.8F)
        builder.define(HITBOX_EYES_HEIGHT, 1.7F)
    }

    override fun getAddEntityPacket(serverEntity: ServerEntity) = ClientboundCustomPayloadPacket(
        SpawnNPCPacket(
            this,
            super.getAddEntityPacket(serverEntity) as ClientboundAddEntityPacket
        )
    ) as Packet<ClientGamePacketListener>

    override fun remakeBrain() {
        makeBrain(this.brainDynamic ?: makeEmptyBrainDynamic())
    }

    override fun assignNewBrainWithMemoriesAndSensors(
        dynamic: Dynamic<*>,
        memories: Set<MemoryModuleType<*>>,
        sensors: Set<SensorType<*>>
    ): Brain<out NPCEntity> {
        val allSensors = BuiltInRegistries.SENSOR_TYPE.toSet().filterIsInstance<SensorType<Sensor<in NPCEntity>>>()
        val brain = Brain.provider(
            memories.toSet(),
            allSensors.filter { it in sensors }.toSet()
        ).makeBrain(dynamic)
        this.brain = brain
        return brain
    }

    override fun makeBrain(dynamic: Dynamic<*>): Brain<out NPCEntity> {
        this.brainDynamic = dynamic
        val brain = brainProvider().makeBrain(dynamic)
        this.brain = brain
        if (npc != null) {
            NPCBrain.configure(this, npc, dynamic)
        }
        return brain
    }

    override fun getBrain() = super.getBrain() as Brain<NPCEntity>

    fun updateAspects() {
        entityData.set(ASPECTS, appliedAspects + variationAspects)
    }

    fun isInBattle() = battleIds.isNotEmpty()
    fun getBattleConfiguration() = battle ?: npc.battleConfiguration

    /** Retrieves the battle theme associated with this Pokemon's Species/Form, or the default PVW theme if not found. */
    fun getBattleTheme() = this.npc.battleTheme ?: CobblemonSounds.PVN_BATTLE.location

    override fun tick() {
        super.tick()
        delegate.tick(this)
        schedulingTracker.update(1/20F)
    }

    override fun customServerAiStep(serverLevel: ServerLevel) {
        super.customServerAiStep(serverLevel)
        getBrain().tick(serverLevel, this)
    }

    override fun broadcastToPlayer(player: ServerPlayer): Boolean {
        if (shouldHideFrom(player)) {
            return false
        }
        return super.broadcastToPlayer(player)
    }

    fun shouldHideFrom(player: ServerPlayer): Boolean {
        if (Cobblemon.permissionValidator.hasPermission(player, permission = SEE_HIDDEN_NPCS)) {
            return false
        }
        val value = Cobblemon.molangData.load(player.uuid).map[stringUUID]
        if (value is VariableStruct) {
            val hide = value.map["hide"] as? DoubleValue
            return hide?.asDouble() == 1.0
        }
        return false
    }

    override fun saveWithoutId(valueOutput: ValueOutput) {
        super.saveWithoutId(valueOutput)
        saveScriptingToNBT(valueOutput)
        valueOutput.putInt(DataKeys.NPC_LEVEL, level)
        valueOutput.putBoolean(DataKeys.NPC_HIDE_NAME_TAG, hideNameTag)
        valueOutput.store(DataKeys.NPC_CLASS, Identifier.CODEC, npc.id)
        valueOutput.storeNullable(DataKeys.NPC_FORCED_RESOURCE_IDENTIFIER, Identifier.CODEC, forcedResourceIdentifier)
        val aspectOutput = valueOutput.list(DataKeys.NPC_ASPECTS, Codec.STRING)
        appliedAspects.forEach {
            aspectOutput.add(it)
        }
        val variationAspectOutput = valueOutput.list(DataKeys.NPC_VARIATION_ASPECTS, Codec.STRING)
        variationAspects.forEach {
            variationAspectOutput.add(it)
        }
        valueOutput.storeNullable(DataKeys.NPC_INTERACTION, NPCInteractConfiguration.CODEC, interaction)
        val battle = battle
        battle?.save(valueOutput.child(DataKeys.NPC_BATTLE_CONFIGURATION))
        if (skill != null) {
            valueOutput.putInt(DataKeys.NPC_SKILL, skill ?: 0)
        }
        val party = party
        if (party != null) {
            val partyNBT = valueOutput.child(DataKeys.NPC_PARTY)
            party.save(partyNBT)
        }
        val playerTexture = entityData.get(NPC_PLAYER_TEXTURE)
        if (playerTexture.model != NPCPlayerModelType.NONE) {
            val texture = valueOutput.child(DataKeys.NPC_PLAYER_TEXTURE)
            texture.also {
                it.putString(DataKeys.NPC_PLAYER_TEXTURE_MODEL, playerTexture.model.name)
                val byteArray = it.list(DataKeys.NPC_PLAYER_TEXTURE, Codec.BYTE)
                playerTexture.texture.forEach { byte -> byteArray.add(byte) }
            }
        }
        valueOutput.putFloat(DataKeys.NPC_BOX_SCALE, hitboxScale)
        valueOutput.putFloat(DataKeys.NPC_RENDER_SCALE, renderScale)
        val hitbox = hitbox
        if (hitbox != null) {
            val hitboxOutput = valueOutput.child(DataKeys.NPC_HITBOX)
            hitboxOutput.also {
                it.putFloat(DataKeys.NPC_HITBOX_WIDTH, hitbox.width)
                it.putFloat(DataKeys.NPC_HITBOX_HEIGHT, hitbox.height)
                it.putBoolean(DataKeys.NPC_HITBOX_FIXED, hitbox.fixed)
            }
        }
        val isMovable = isMovable
        if (isMovable != null) {
            valueOutput.putBoolean(DataKeys.NPC_IS_MOVABLE, isMovable)
        }
        val isInvulnerable = isInvulnerable
        if (isInvulnerable != null) {
            valueOutput.putBoolean(DataKeys.NPC_IS_INVULNERABLE, isInvulnerable)
        }
        val isLeashable = isLeashable
        if (isLeashable != null) {
            valueOutput.putBoolean(DataKeys.NPC_IS_LEASHABLE, isLeashable)
        }
        val allowProjectileHits = allowProjectileHits
        if (allowProjectileHits != null) {
            valueOutput.putBoolean(DataKeys.NPC_ALLOW_PROJECTILE_HITS, allowProjectileHits)
        }
    }

    override fun load(valueInput: ValueInput) {
        npc = valueInput.read(DataKeys.NPC_CLASS, Identifier.CODEC).map { NPCClasses.getByIdentifier(it) ?: NPCClasses.classes.first() }.orElse(
            NPCClasses.classes.first())
        valueInput.read(DataKeys.NPC_FORCED_RESOURCE_IDENTIFIER, Identifier.CODEC).ifPresent { forcedResourceIdentifier = it }
        valueInput.getIntOr(DataKeys.NPC_LEVEL, 1)
        entityData.set(LEVEL, valueInput.getIntOr(DataKeys.NPC_LEVEL, 1))
        entityData.set(HIDE_NAME_TAG, valueInput.getBooleanOr(DataKeys.NPC_HIDE_NAME_TAG, false))
        super.load(valueInput)
        loadScriptingFromNBT(valueInput)
        appliedAspects.addAll(valueInput.listOrEmpty(DataKeys.NPC_ASPECTS, Codec.STRING))
        variationAspects.addAll(valueInput.listOrEmpty(DataKeys.NPC_VARIATION_ASPECTS, Codec.STRING))
        valueInput.read(DataKeys.NPC_INTERACTION, NPCInteractConfiguration.CODEC).ifPresent { interaction = it }
        val battleNBT = valueInput.child(DataKeys.NPC_BATTLE_CONFIGURATION)
        if (battleNBT.isPresent) {
            battle = NPCBattleConfiguration().also { it.load(battleNBT.get()) }
        }
        this.skill = valueInput.getIntOr(DataKeys.NPC_SKILL, 0)
        val partyNBT = valueInput.child(DataKeys.NPC_PARTY)
        if (partyNBT.isPresent) {
            party = NPCPartyStore(this).also {
                it.load(partyNBT.get())
                it.initialize()
            }
        }
        val textureInput = valueInput.child(DataKeys.NPC_PLAYER_TEXTURE)
        if (textureInput.isPresent) {
            val textureNBT = textureInput.get()
            val model = NPCPlayerModelType.valueOf(textureNBT.getString(DataKeys.NPC_PLAYER_TEXTURE_MODEL).get())
            val texture = textureNBT.listOrEmpty(DataKeys.NPC_PLAYER_TEXTURE_TEXTURE, Codec.BYTE).toList().toByteArray()
            entityData.set(NPC_PLAYER_TEXTURE, NPCPlayerTexture(texture, model))
        }
        //TODO THIS MIGHT CAUSE ISSUES
        this.isMovable = valueInput.getBooleanOr(DataKeys.NPC_IS_MOVABLE, npc.isMovable)
        this.isInvulnerable = valueInput.getBooleanOr(DataKeys.NPC_IS_INVULNERABLE, npc.isInvulnerable)
        this.isLeashable = valueInput.getBooleanOr(DataKeys.NPC_IS_LEASHABLE, npc.isLeashable)
        this.allowProjectileHits = valueInput.getBooleanOr(DataKeys.NPC_ALLOW_PROJECTILE_HITS, npc.allowProjectileHits)

        valueInput.getFloatOr(DataKeys.NPC_BASE_SCALE, -1000F).takeIf { it != -1000F }?.let { hitboxScale = it }
        hitboxScale = valueInput.getFloatOr(DataKeys.NPC_BOX_SCALE, 1F)
        renderScale = valueInput.getFloatOr(DataKeys.NPC_RENDER_SCALE, 1F)
        valueInput.child(DataKeys.NPC_HITBOX).ifPresent { hitboxNBT ->
            val width = hitboxNBT.getFloatOr(DataKeys.NPC_HITBOX_WIDTH, 1F)
            val height = hitboxNBT.getFloatOr(DataKeys.NPC_HITBOX_HEIGHT, 1F)
            val fixed = hitboxNBT.getBooleanOr(DataKeys.NPC_HITBOX_FIXED, false)

            if (fixed) EntityDimensions.fixed(width, height) else EntityDimensions.scalable(width, height)
        }
        updateAspects()
        remakeBrain()
    }

    fun loadTextureFromGameProfileName(username: String) {
        val server = level().server ?: return
        server.services().profileRepository().findProfilesByNames(arrayOf(username), object : ProfileLookupCallback {
            override fun onProfileLookupSucceeded(string: String, uuid: UUID) {
                val deepProfile = server.services().profileResolver().fetchById(uuid).getOrNull() ?: return Cobblemon.LOGGER.error("Failed to fetch profile for game profile name: $username")
                deepProfile.properties
                val textures = server.services().sessionService().getTextures(deepProfile)
                val skin = textures.skin!!
                val url = skin.url
                val model = NPCPlayerModelType.valueOf((skin.getMetadata("model") ?: "default").uppercase())
                loadTexture(URI(url), model)
                data.setDirectly("player_texture_username", StringValue(username))
            }

            override fun onProfileLookupFailed(profileName: String, exception: Exception) {
                Cobblemon.LOGGER.error("Unable to load texture for game profile name: $username")
            }
        })
    }

    fun loadTexture(uri: URI, model: NPCPlayerModelType) {
        appliedAspects -= "model-default"
        appliedAspects -= "model-slim"
        appliedAspects += "model-${model.name.lowercase()}"
        entityData.set(NPC_PLAYER_TEXTURE, NPCPlayerTexture(uri.toURL().openStream().readBytes(), model))
        updateAspects()
    }

    fun unloadTexture() {
        appliedAspects -= "model-default"
        appliedAspects -= "model-slim"
        entityData.set(NPC_PLAYER_TEXTURE, NPCPlayerTexture(ByteArray(1), NPCPlayerModelType.NONE))
        data.map.remove("player_texture_username")
        updateAspects()
    }

    override fun hasCustomName() = true
    override fun isCustomNameVisible() = true
    override fun isPersistenceRequired() = super.isPersistenceRequired() || !npc.canDespawn

    override fun getDimensions(pose: Pose): EntityDimensions {
        val hitbox = hitbox ?: npc.hitbox
        val scaledHitbox = hitbox.scale(hitboxScale)
        return scaledHitbox
    }

    override fun isPushable() = isMovable ?: npc.isMovable
    override fun isInvulnerableTo(serverLevel: ServerLevel, source: DamageSource): Boolean = (isInvulnerable ?: npc.isInvulnerable) &&
            !source.`is`(BYPASSES_INVULNERABILITY)
    override fun canBeLeashed() = isLeashable ?: npc.isLeashable
    override fun canBeHitByProjectile() = allowProjectileHits ?: npc.allowProjectileHits

    fun initialize(level: Int) {
        variationAspects.clear()
        entityData.set(HIDE_NAME_TAG, npc.hideNameTag)
        entityData.set(LEVEL, level)
        remakeBrain()
        npc.variations.values.forEach { this.variationAspects.addAll(it.provideAspects(this)) }
        if (party == null || npc.party != null) {
            party = npc.party?.takeIf { it.isStatic }?.provide(this, level)
        }
        updateAspects()
    }

    override fun mobInteract(player: Player, hand: InteractionHand): InteractionResult {
        if (player is ServerPlayer) {
            if (player.isCreative && player.getItemInHand(hand).item.toString() == CobblemonItems.NPC_EDITOR.toString()) {
                edit(player)
            } else if (hand == InteractionHand.MAIN_HAND) {
                if (player.getBattleState()?.first?.getActor(this) != null) {
                    return InteractionResult.PASS
                }

                (interaction ?: npc.interaction)?.interact(this, player)
//                val battle = getBattleConfiguration()
//                if (battle.canChallenge) {
//                    val provider = battle.party
//                    if (provider != null) {
//                        val party = provider.provide(this, listOf(player))
//                        val result = BattleBuilder.pvn(
//                            player = player,
//                            npcEntity = this
//                        )
//                    }
//                }
            }

        }
        return InteractionResult.SUCCESS
    }

    override fun recalling(pokemonEntity: PokemonEntity): CompletableFuture<Unit> {
        playAnimation(RECALL_ANIMATION, pokemonExpressions(pokemonEntity.pokemon))
        return delayedFuture(seconds = 1.6F)
    }

    override fun sendingOut(pokemon: Pokemon): CompletableFuture<Unit> {
        playAnimation(SEND_OUT_ANIMATION, pokemonExpressions(pokemon))
        return delayedFuture(seconds = 1.6F)
    }

    private fun pokemonExpressions(pokemon: Pokemon): List<String> {
        return listOf(
            "v.actioning_pokemon_name=\'${pokemon.species.name}\';",
            "v.actioning_pokemon_level=${pokemon.level};",
            "v.actioning_pokemon_ball=\'${pokemon.caughtBall.name}\';",
            "v.actioning_pokemon_shiny=\'${pokemon.shiny}\';",
        )
    }

    override fun onSyncedDataUpdated(data: EntityDataAccessor<*>) {
        super.onSyncedDataUpdated(data)
        if (delegate != null) {
            delegate.onSyncedDataUpdated(data)
        }
    }

    fun edit(player: ServerPlayer) {
        val lastEditing = BehaviourEditingTracker.getPlayerIdEditing(this)?.getPlayer()
        if (lastEditing != null) {
            BehaviourEditingTracker.stopEditing(lastEditing.uuid)
            lastEditing.sendPacket(CloseNPCEditorPacket())
        }
        player.sendPacket(OpenNPCEditorPacket(this))
        BehaviourEditingTracker.startEditing(player, this)
    }

    override fun getNavigation() = navigation as OmniPathNavigation
    override fun createNavigation(level: Level) = OmniPathNavigation(level, this)

    // At some point these need to be changeable from MoLang or something.
    override fun canWalk(): Boolean {
        return true
    }

    override fun canSwimInWater(): Boolean {
        return true
    }

    override fun canSwimInLava(): Boolean {
        return false
    }

    override fun canWalkOnLava(): Boolean {
        return false
    }

    override fun canSwimUnderFluid(fluidState: FluidState): Boolean {
        return false
    }

    override fun canWalkOnWater(): Boolean {
        return false
    }

    override fun canPathThroughSaccLeaves(): Boolean {
        return false
    }

    override fun canFly(): Boolean {
        return false
    }

    override fun couldStopFlying(): Boolean {
        return false
    }

    override fun isFlying(): Boolean {
        return false
    }

    override fun setFlying(state: Boolean) {
        // NPCs cannot fly (yet)
    }

    override fun entityOnGround(): Boolean = onGround()
}
