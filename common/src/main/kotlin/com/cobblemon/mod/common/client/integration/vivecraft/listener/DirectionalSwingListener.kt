package com.cobblemon.mod.common.client.integration.vivecraft.listener

import com.cobblemon.mod.common.Cobblemon
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import org.vivecraft.api.client.VRClientAPI
import org.vivecraft.api.client.tracker.swing.SwingContext
import org.vivecraft.api.client.tracker.swing.SwingListener
import org.vivecraft.api.data.VRBodyPart
import kotlin.math.abs

/**
 * Listener that classifies directional swings (up, down, forward, backward, left, right)
 * using Vivecraft VR pose history and invokes handler methods for each direction.
 *
 * @param forwardDotThreshold Dot product threshold used to classify forward/back/left/right directions.
 * @param historyTicks Number of ticks used to compute velocity from historical poses.
 */
abstract class DirectionalSwingListener(
    private val forwardDotThreshold: Double = 0.3,
    private val historyTicks: Int = 10
) : SwingListener {

    protected val minecraft: Minecraft = Minecraft.getInstance()
    protected val clientData: VRClientAPI = VRClientAPI.instance()

    /**
     * Compute the net movement vector for the specified body part over the given number of ticks.
     * Returns null if historical pose data is unavailable, insufficient, or movement is negligible.
     *
     * @param bodyPart The VR body part to compute movement for.
     * @param ticks The number of ticks of history to consider.
     * @return The movement vector or null if unavailable/insignificant.
     */
    private fun changeForVelocity(bodyPart: VRBodyPart, ticks: Int = historyTicks): Vec3? {
        val history = clientData.historicalVRPoses ?: return null
        if (history.ticksOfHistory() < ticks) return null

        val delta = history.netMovement(bodyPart, ticks)
        delta?.lengthSqr()?.let { if (it < 1e-6) return null }

        return delta
    }

    /**
     * Called when an upward swing is detected.
     *
     * @param useHand The hand used for the swing.
     * @param handVel The velocity vector of the hand during the swing.
     * @param player The player performing the swing.
     * @return True if the upward action was handled, false otherwise.
     */
    protected open fun onUpwardSwing(
        useHand: InteractionHand,
        player: Player
    ): Boolean {
        Cobblemon.LOGGER.info(
            "Ignoring swing: upward movement. player={}, hand={}",
            player, useHand
        )
        return true
    }

    /**
     * Called when a downward swing is detected.
     *
     * @param useHand The hand used for the swing.
     * @param handVel The velocity vector of the hand during the swing.
     * @param player The player performing the swing.
     * @return True if the downward action was handled, false otherwise.
     */
    protected open fun onDownwardSwing(
        useHand: InteractionHand,
        handVel: Vec3,
        player: Player
    ): Boolean {
        Cobblemon.LOGGER.info(
            "Ignoring swing: downward movement. handVel={}, player={}, hand={}",
            handVel, player, useHand
        )
        return true
    }

    /**
     * Called when a forward swing is detected.
     *
     * @param useHand The hand used for the swing.
     * @param handVel The velocity vector of the hand during the swing.
     * @param player The player performing the swing.
     * @return True if the forward action was handled, false otherwise.
     */
    protected open fun onForwardSwing(
        useHand: InteractionHand,
        handVel: Vec3,
        player: Player
    ): Boolean {
        return true
    }

    /**
     * Called when a backward swing is detected.
     *
     * @param useHand The hand used for the swing.
     * @param handVel The velocity vector of the hand during the swing.
     * @param player The player performing the swing.
     * @return True if the backward action was handled, false otherwise.
     */
    protected open fun onBackwardSwing(
        useHand: InteractionHand,
        handVel: Vec3,
        player: Player
    ): Boolean {
        Cobblemon.LOGGER.info(
            "Ignoring swing: backward movement. handVel={}, player={}, hand={}",
            handVel, player, useHand
        )
        return true
    }

    /**
     * Called when a leftward swing is detected.
     *
     * @param useHand The hand used for the swing.
     * @param handVel The velocity vector of the hand during the swing.
     * @param player The player performing the swing.
     * @return True if the leftward action was handled, false otherwise.
     */
    protected open fun onLeftSwing(
        useHand: InteractionHand,
        handVel: Vec3,
        player: Player
    ): Boolean {
        Cobblemon.LOGGER.info(
            "Ignoring swing: leftward movement. handVel={}, player={}, hand={}",
            handVel, player, useHand
        )
        return true
    }

    /**
     * Called when a rightward swing is detected.
     *
     * @param useHand The hand used for the swing.
     * @param handVel The velocity vector of the hand during the swing.
     * @param player The player performing the swing.
     * @return True if the rightward action was handled, false otherwise.
     */
    protected open fun onRightSwing(
        useHand: InteractionHand,
        handVel: Vec3,
        player: Player
    ): Boolean {
        Cobblemon.LOGGER.info(
            "Ignoring swing: rightward movement. handVel={}, player={}, hand={}",
            handVel, player, useHand
        )
        return true
    }


    /**
     * Checks if the given item stack is valid for processing by this swing listener.
     * Subclasses must implement this to specify which items they handle.
     * @param itemStack The item stack to validate.
     * @return True if the item stack is valid, false otherwise.
     */
    protected abstract fun isValidItem(itemStack: ItemStack): Boolean

    /**
     * Called when a swing ends. Validates the held item, computes hand velocity from historical poses,
     * handles vertical swings first (upward/downward) and then classifies horizontal direction:
     * forward/backward by dot with player look direction; left/right by projecting onto a right vector
     * computed from world up and forward. Ambiguous swings are ignored.
     *
     * @param context Vivecraft swing context containing body part information.
     */
    override fun onSwingEnd(context: SwingContext?) {
        val player = minecraft.player ?: return
        val ctx = context ?: return
        val useHand: InteractionHand = VRBodyPart.toInteractionHand(ctx.bodyPart) ?: return
        val itemStack: ItemStack = player.getItemInHand(useHand)

        if (!isValidItem(itemStack)) return

        val handVel = changeForVelocity(ctx.bodyPart) ?: run {
            Cobblemon.LOGGER.info(
                "Ignoring swing: insufficient historical pose data for velocity. bodyPart={}, player={}",
                ctx.bodyPart, player
            )
            return
        }

        val vertical = handVel.y
        if (vertical > 0.0) {
            if (onUpwardSwing(useHand, player)) return
        } else if (vertical < 0.0) {
            if (onDownwardSwing(useHand, handVel, player)) return
        }

        val forward = player.lookAngle.normalize()
        val handDir = handVel.normalize()
        val forwardDot = handDir.dot(forward)

        val worldUp = Vec3(0.0, 1.0, 0.0)
        val right = forward.cross(worldUp).let {
            if (it.lengthSqr() < 1e-6) Vec3(1.0, 0.0, 0.0) else it.normalize()
        }
        val rightDot = handDir.dot(right)

        if (forwardDot > forwardDotThreshold) {
            if (onForwardSwing(useHand, handVel, player)) return
            return
        }

        if (forwardDot < -forwardDotThreshold) {
            if (onBackwardSwing(useHand, handVel, player)) return
            return
        }

        if (abs(rightDot) > forwardDotThreshold) {
            if (rightDot > 0.0) {
                if (onRightSwing(useHand, handVel, player)) return
            } else {
                if (onLeftSwing(useHand, handVel, player)) return
            }
            return
        }

        Cobblemon.LOGGER.info(
            "Ignoring swing: ambiguous direction. forwardDot={}, rightDot={}, handVel={}, player={}",
            forwardDot, rightDot, handVel, player
        )
    }
}