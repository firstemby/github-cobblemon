package com.cobblemon.mod.common.compat.vivecraft;

import org.vivecraft.api.data.VRBodyPart;

import java.util.Objects;

public record ViveControllerMapping(int controllerId, VRBodyPart bodyPart) {
	public ViveControllerMapping(int controllerId, VRBodyPart bodyPart) {
		this.controllerId = controllerId;
		this.bodyPart = Objects.requireNonNull(bodyPart);
	}
}
