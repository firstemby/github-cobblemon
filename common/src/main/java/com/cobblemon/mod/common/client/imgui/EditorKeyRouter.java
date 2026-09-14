package com.cobblemon.mod.common.client.imgui;

import java.util.Arrays;

import static org.lwjgl.glfw.GLFW.*;

final class EditorKeyRouter {
    enum Target { GAME, IMGUI, PASSTHROUGH }

    private final Target[] owners = new Target[GLFW_KEY_LAST + 1];

    Target route(int key, int action, boolean active, boolean visible, boolean textInput,
                 boolean gameShortcut, boolean canTogglePassthrough) {
        if (key < 0 || key >= owners.length) return Target.GAME;

        Target owner = owners[key];
        if (action == GLFW_RELEASE) {
            owners[key] = null;
            return owner != null ? owner : Target.GAME;
        }
        if (owner != null) return owner;

        boolean typing = active && textInput;
        if (visible && !typing && gameShortcut) {
            owner = Target.GAME;
        } else if (visible && !typing && key == GLFW_KEY_TAB && canTogglePassthrough) {
            owner = Target.PASSTHROUGH;
        } else {
            owner = active ? Target.IMGUI : Target.GAME;
        }
        owners[key] = owner;
        return owner;
    }

    void reset() {
        Arrays.fill(owners, null);
    }
}
