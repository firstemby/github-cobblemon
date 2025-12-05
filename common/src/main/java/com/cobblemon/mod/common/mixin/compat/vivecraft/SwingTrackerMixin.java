package com.cobblemon.mod.common.mixin.compat.vivecraft;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonNetwork;
import com.cobblemon.mod.common.compat.vivecraft.ViveControllerMapping;
import com.cobblemon.mod.common.net.messages.server.SendOutPokeballPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.api.data.VRBodyPart;
import org.vivecraft.client.network.ClientNetworking;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.Vector3fHistory;
import org.vivecraft.client_vr.gameplay.trackers.SwingTracker;

import java.util.Arrays;

@Mixin(SwingTracker.class)
public class SwingTrackerMixin {
	@Final
	@Shadow
	private boolean[] lastWeaponSolid;

	@Final
	@Shadow
	private Vector3fHistory[] tipHistory;

	@Final
	@Shadow
	private Minecraft mc;

	@Final
	@Shadow
	private ClientDataHolderVR dh;

	@Unique
	private boolean[] prevLastWeaponSolid;

	@Unique
	private boolean[] prevTipActive;

	private static final float BASE_SPEED_THRESHOLD = 3.0F;

	@Inject(method = "activeProcess", at = @At("TAIL"))
	private void onActiveProcess(LocalPlayer player, CallbackInfo ci) {
		if (this.lastWeaponSolid == null || this.tipHistory == null) return;

		float speedThreshold = computeSpeedThreshold(player);

		if (needsInitialization()) {
			initializePrevState(speedThreshold);
			return;
		}

		int len = Math.min(this.lastWeaponSolid.length, this.tipHistory.length);
		for (int i = 0; i < len; i++) {
			if (!this.prevLastWeaponSolid[i] && this.lastWeaponSolid[i]) {
				Cobblemon.LOGGER.info("SWING HAPPENEDDDDDDD in a solid block");
			}

			Vector3fHistory hist = this.tipHistory[i];
			float speed = hist != null ? hist.averageSpeed(0.33F) : 0.0F;
			boolean currentTipActive = speed > speedThreshold;

			if (!this.prevTipActive[i] && currentTipActive) {
				Cobblemon.LOGGER.info("SWING HAPPENEDDDDDDD (movement)");
				CobblemonNetwork.sendToServer(new SendOutPokeballPacket());

				ViveControllerMapping mapping = mapIndexToController(i);
				ClientNetworking.sendActiveBodyPart(mapping.bodyPart(), true);

				if (this.dh != null && this.dh.vr != null) {
					this.dh.vr.triggerHapticPulse(mapping.bodyPart(), 1000.0F, 160.0F, 1.0F, 0.0F);
				}

				this.lastWeaponSolid[i] = true;
			}

			this.prevTipActive[i] = currentTipActive;
		}

		this.prevLastWeaponSolid = Arrays.copyOf(this.lastWeaponSolid, this.lastWeaponSolid.length);
	}

	private float computeSpeedThreshold(LocalPlayer player) {
		float threshold = BASE_SPEED_THRESHOLD;
		if (player != null && player.isCreative()) threshold *= 1.5F;
		return threshold;
	}

	private boolean needsInitialization() {
		return this.prevLastWeaponSolid == null
				|| this.prevLastWeaponSolid.length != this.lastWeaponSolid.length
				|| this.prevTipActive == null
				|| this.prevTipActive.length != this.tipHistory.length;
	}

	private void initializePrevState(float speedThreshold) {
		this.prevLastWeaponSolid = Arrays.copyOf(this.lastWeaponSolid, this.lastWeaponSolid.length);

		this.prevTipActive = new boolean[this.tipHistory.length];
		for (int i = 0; i < this.tipHistory.length; i++) {
			Vector3fHistory h = this.tipHistory[i];
			this.prevTipActive[i] = (h != null && h.averageSpeed(0.33F) > speedThreshold);
		}
	}

	private ViveControllerMapping mapIndexToController(int i) {
		return switch (i) {
			case 0 -> new ViveControllerMapping(0, VRBodyPart.MAIN_HAND);
			case 1 -> new ViveControllerMapping(1, VRBodyPart.OFF_HAND);
			case 2 -> new ViveControllerMapping(4, VRBodyPart.RIGHT_FOOT);
			default -> new ViveControllerMapping(5, VRBodyPart.LEFT_FOOT);
		};
	}
}