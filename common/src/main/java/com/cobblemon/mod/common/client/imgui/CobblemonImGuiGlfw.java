package com.cobblemon.mod.common.client.imgui;

import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImVec2;
import imgui.callback.ImStrConsumer;
import imgui.callback.ImStrSupplier;
import imgui.flag.ImGuiBackendFlags;
import imgui.flag.ImGuiConfigFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import imgui.flag.ImGuiMouseCursor;
import org.lwjgl.glfw.*;

import java.util.function.BiPredicate;

import static org.lwjgl.glfw.GLFW.*;

public class CobblemonImGuiGlfw {
    private long mainWindowPtr;
    private final int[] winWidth = new int[1];
    private final int[] winHeight = new int[1];
    private final int[] fbWidth = new int[1];
    private final int[] fbHeight = new int[1];
    private final long[] mouseCursors = new long[ImGuiMouseCursor.COUNT];
    private final EditorKeyRouter keyRouter = new EditorKeyRouter();
    private BiPredicate<Integer, Integer> gameKeyPredicate = (key, scancode) -> false;
    private final boolean[] mouseJustPressed = new boolean[ImGuiMouseButton.COUNT];
    private final ImVec2 mousePosBackup = new ImVec2();
    private final double[] mouseX = new double[1];
    private final double[] mouseY = new double[1];
    private GLFWWindowFocusCallback prevUserCallbackWindowFocus = null;
    private GLFWMouseButtonCallback prevUserCallbackMouseButton = null;
    private GLFWScrollCallback prevUserCallbackScroll = null;
    private GLFWCursorPosCallback prevUserCallbackCursorPos = null;
    private GLFWKeyCallback prevUserCallbackKey = null;
    private GLFWCharModsCallback prevUserCallbackCharMods = null;
    private GLFWCharCallback prevUserCallbackChar = null;
    private GLFWCursorEnterCallback prevUserCallbackCursorEnter = null;
    private boolean callbacksInstalled = false;
    private double time = 0.0;
    private long mouseWindowPtr;
    private boolean editorActive = false;

    protected static final int glfwVersionCombined = GLFW_VERSION_MAJOR * 1000 + GLFW_VERSION_MINOR * 100 + GLFW_VERSION_REVISION;
    protected static final boolean glfwHasNewCursors = glfwVersionCombined >= 3400; // 3.4+ GLFW_RESIZE_ALL_CURSOR, GLFW_RESIZE_NESW_CURSOR, GLFW_RESIZE_NWSE_CURSOR, GLFW_NOT_ALLOWED_CURSOR

    public enum MouseHandledBy {
        IMGUI,
        GAME,
        BOTH;

        public boolean allowImgui() {
            return this == IMGUI || this == BOTH;
        }

        public boolean allowGame() {
            return this == GAME || this == BOTH;
        }
    }

    public void setEditorActive(boolean active) {
        this.editorActive = active;
    }

    private boolean editorVisible = false;

    private Runnable onTabPressed = null;

    public void setEditorVisible(boolean visible) {
        this.editorVisible = visible;
    }

    public void setOnTabPressed(Runnable callback) {
        this.onTabPressed = callback;
    }

    public MouseHandledBy getMouseHandledBy() {
        if (!this.editorActive) return MouseHandledBy.GAME;
        if (ImGui.getIO().getWantCaptureMouse()) return MouseHandledBy.IMGUI;
        return MouseHandledBy.BOTH;
    }

    protected int glfwKeyToImGuiKey(final int glfwKey) {
        switch (glfwKey) {
            case GLFW_KEY_TAB:
                return ImGuiKey.Tab;
            case GLFW_KEY_LEFT:
                return ImGuiKey.LeftArrow;
            case GLFW_KEY_RIGHT:
                return ImGuiKey.RightArrow;
            case GLFW_KEY_UP:
                return ImGuiKey.UpArrow;
            case GLFW_KEY_DOWN:
                return ImGuiKey.DownArrow;
            case GLFW_KEY_PAGE_UP:
                return ImGuiKey.PageUp;
            case GLFW_KEY_PAGE_DOWN:
                return ImGuiKey.PageDown;
            case GLFW_KEY_HOME:
                return ImGuiKey.Home;
            case GLFW_KEY_END:
                return ImGuiKey.End;
            case GLFW_KEY_INSERT:
                return ImGuiKey.Insert;
            case GLFW_KEY_DELETE:
                return ImGuiKey.Delete;
            case GLFW_KEY_BACKSPACE:
                return ImGuiKey.Backspace;
            case GLFW_KEY_SPACE:
                return ImGuiKey.Space;
            case GLFW_KEY_ENTER:
                return ImGuiKey.Enter;
            case GLFW_KEY_ESCAPE:
                return ImGuiKey.Escape;
            case GLFW_KEY_APOSTROPHE:
                return ImGuiKey.Apostrophe;
            case GLFW_KEY_COMMA:
                return ImGuiKey.Comma;
            case GLFW_KEY_MINUS:
                return ImGuiKey.Minus;
            case GLFW_KEY_PERIOD:
                return ImGuiKey.Period;
            case GLFW_KEY_SLASH:
                return ImGuiKey.Slash;
            case GLFW_KEY_SEMICOLON:
                return ImGuiKey.Semicolon;
            case GLFW_KEY_EQUAL:
                return ImGuiKey.Equal;
            case GLFW_KEY_LEFT_BRACKET:
                return ImGuiKey.LeftBracket;
            case GLFW_KEY_BACKSLASH:
                return ImGuiKey.Backslash;
            case GLFW_KEY_RIGHT_BRACKET:
                return ImGuiKey.RightBracket;
            case GLFW_KEY_GRAVE_ACCENT:
                return ImGuiKey.GraveAccent;
            case GLFW_KEY_CAPS_LOCK:
                return ImGuiKey.CapsLock;
            case GLFW_KEY_SCROLL_LOCK:
                return ImGuiKey.ScrollLock;
            case GLFW_KEY_NUM_LOCK:
                return ImGuiKey.NumLock;
            case GLFW_KEY_PRINT_SCREEN:
                return ImGuiKey.PrintScreen;
            case GLFW_KEY_PAUSE:
                return ImGuiKey.Pause;
            case GLFW_KEY_KP_0:
                return ImGuiKey.Keypad0;
            case GLFW_KEY_KP_1:
                return ImGuiKey.Keypad1;
            case GLFW_KEY_KP_2:
                return ImGuiKey.Keypad2;
            case GLFW_KEY_KP_3:
                return ImGuiKey.Keypad3;
            case GLFW_KEY_KP_4:
                return ImGuiKey.Keypad4;
            case GLFW_KEY_KP_5:
                return ImGuiKey.Keypad5;
            case GLFW_KEY_KP_6:
                return ImGuiKey.Keypad6;
            case GLFW_KEY_KP_7:
                return ImGuiKey.Keypad7;
            case GLFW_KEY_KP_8:
                return ImGuiKey.Keypad8;
            case GLFW_KEY_KP_9:
                return ImGuiKey.Keypad9;
            case GLFW_KEY_KP_DECIMAL:
                return ImGuiKey.KeypadDecimal;
            case GLFW_KEY_KP_DIVIDE:
                return ImGuiKey.KeypadDivide;
            case GLFW_KEY_KP_MULTIPLY:
                return ImGuiKey.KeypadMultiply;
            case GLFW_KEY_KP_SUBTRACT:
                return ImGuiKey.KeypadSubtract;
            case GLFW_KEY_KP_ADD:
                return ImGuiKey.KeypadAdd;
            case GLFW_KEY_KP_ENTER:
                return ImGuiKey.KeypadEnter;
            case GLFW_KEY_KP_EQUAL:
                return ImGuiKey.KeypadEqual;
            case GLFW_KEY_LEFT_SHIFT:
                return ImGuiKey.LeftShift;
            case GLFW_KEY_LEFT_CONTROL:
                return ImGuiKey.LeftCtrl;
            case GLFW_KEY_LEFT_ALT:
                return ImGuiKey.LeftAlt;
            case GLFW_KEY_LEFT_SUPER:
                return ImGuiKey.LeftSuper;
            case GLFW_KEY_RIGHT_SHIFT:
                return ImGuiKey.RightShift;
            case GLFW_KEY_RIGHT_CONTROL:
                return ImGuiKey.RightCtrl;
            case GLFW_KEY_RIGHT_ALT:
                return ImGuiKey.RightAlt;
            case GLFW_KEY_RIGHT_SUPER:
                return ImGuiKey.RightSuper;
            case GLFW_KEY_MENU:
                return ImGuiKey.Menu;
            case GLFW_KEY_0:
                return ImGuiKey._0;
            case GLFW_KEY_1:
                return ImGuiKey._1;
            case GLFW_KEY_2:
                return ImGuiKey._2;
            case GLFW_KEY_3:
                return ImGuiKey._3;
            case GLFW_KEY_4:
                return ImGuiKey._4;
            case GLFW_KEY_5:
                return ImGuiKey._5;
            case GLFW_KEY_6:
                return ImGuiKey._6;
            case GLFW_KEY_7:
                return ImGuiKey._7;
            case GLFW_KEY_8:
                return ImGuiKey._8;
            case GLFW_KEY_9:
                return ImGuiKey._9;
            case GLFW_KEY_A:
                return ImGuiKey.A;
            case GLFW_KEY_B:
                return ImGuiKey.B;
            case GLFW_KEY_C:
                return ImGuiKey.C;
            case GLFW_KEY_D:
                return ImGuiKey.D;
            case GLFW_KEY_E:
                return ImGuiKey.E;
            case GLFW_KEY_F:
                return ImGuiKey.F;
            case GLFW_KEY_G:
                return ImGuiKey.G;
            case GLFW_KEY_H:
                return ImGuiKey.H;
            case GLFW_KEY_I:
                return ImGuiKey.I;
            case GLFW_KEY_J:
                return ImGuiKey.J;
            case GLFW_KEY_K:
                return ImGuiKey.K;
            case GLFW_KEY_L:
                return ImGuiKey.L;
            case GLFW_KEY_M:
                return ImGuiKey.M;
            case GLFW_KEY_N:
                return ImGuiKey.N;
            case GLFW_KEY_O:
                return ImGuiKey.O;
            case GLFW_KEY_P:
                return ImGuiKey.P;
            case GLFW_KEY_Q:
                return ImGuiKey.Q;
            case GLFW_KEY_R:
                return ImGuiKey.R;
            case GLFW_KEY_S:
                return ImGuiKey.S;
            case GLFW_KEY_T:
                return ImGuiKey.T;
            case GLFW_KEY_U:
                return ImGuiKey.U;
            case GLFW_KEY_V:
                return ImGuiKey.V;
            case GLFW_KEY_W:
                return ImGuiKey.W;
            case GLFW_KEY_X:
                return ImGuiKey.X;
            case GLFW_KEY_Y:
                return ImGuiKey.Y;
            case GLFW_KEY_Z:
                return ImGuiKey.Z;
            case GLFW_KEY_F1:
                return ImGuiKey.F1;
            case GLFW_KEY_F2:
                return ImGuiKey.F2;
            case GLFW_KEY_F3:
                return ImGuiKey.F3;
            case GLFW_KEY_F4:
                return ImGuiKey.F4;
            case GLFW_KEY_F5:
                return ImGuiKey.F5;
            case GLFW_KEY_F6:
                return ImGuiKey.F6;
            case GLFW_KEY_F7:
                return ImGuiKey.F7;
            case GLFW_KEY_F8:
                return ImGuiKey.F8;
            case GLFW_KEY_F9:
                return ImGuiKey.F9;
            case GLFW_KEY_F10:
                return ImGuiKey.F10;
            case GLFW_KEY_F11:
                return ImGuiKey.F11;
            case GLFW_KEY_F12:
                return ImGuiKey.F12;
            case GLFW_KEY_F13:
                return ImGuiKey.F13;
            case GLFW_KEY_F14:
                return ImGuiKey.F14;
            case GLFW_KEY_F15:
                return ImGuiKey.F15;
            case GLFW_KEY_F16:
                return ImGuiKey.F16;
            case GLFW_KEY_F17:
                return ImGuiKey.F17;
            case GLFW_KEY_F18:
                return ImGuiKey.F18;
            case GLFW_KEY_F19:
                return ImGuiKey.F19;
            case GLFW_KEY_F20:
                return ImGuiKey.F20;
            case GLFW_KEY_F21:
                return ImGuiKey.F21;
            case GLFW_KEY_F22:
                return ImGuiKey.F22;
            case GLFW_KEY_F23:
                return ImGuiKey.F23;
            case GLFW_KEY_F24:
                return ImGuiKey.F24;
            default:
                return ImGuiKey.None;
        }
    }

    // X11 modifier events omit the modifier that just changed (GLFW issue 1630).
    protected void updateKeyModifiers(final long window) {
        final ImGuiIO io = ImGui.getIO();
        io.addKeyEvent(ImGuiKey.ModCtrl, (glfwGetKey(window, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS) || (glfwGetKey(window, GLFW_KEY_RIGHT_CONTROL) == GLFW_PRESS));
        io.addKeyEvent(ImGuiKey.ModShift, (glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS) || (glfwGetKey(window, GLFW_KEY_RIGHT_SHIFT) == GLFW_PRESS));
        io.addKeyEvent(ImGuiKey.ModAlt, (glfwGetKey(window, GLFW_KEY_LEFT_ALT) == GLFW_PRESS) || (glfwGetKey(window, GLFW_KEY_RIGHT_ALT) == GLFW_PRESS));
        io.addKeyEvent(ImGuiKey.ModSuper, (glfwGetKey(window, GLFW_KEY_LEFT_SUPER) == GLFW_PRESS) || (glfwGetKey(window, GLFW_KEY_RIGHT_SUPER) == GLFW_PRESS));
    }

    public void mouseButtonCallback(final long windowId, final int button, final int action, final int mods) {
        if (!this.editorActive) {
            if (this.prevUserCallbackMouseButton != null && windowId == this.mainWindowPtr) {
                this.prevUserCallbackMouseButton.invoke(windowId, button, action, mods);
            }
            return;
        }

        updateKeyModifiers(windowId);

        MouseHandledBy handledBy = this.getMouseHandledBy();

        if (handledBy.allowGame() && this.prevUserCallbackMouseButton != null && windowId == this.mainWindowPtr) {
            this.prevUserCallbackMouseButton.invoke(windowId, button, action, mods);
        }
        if (handledBy.allowImgui() && action == GLFW_PRESS && button >= 0 && button < this.mouseJustPressed.length) {
            this.mouseJustPressed[button] = true;
        }
    }

    public void scrollCallback(final long windowId, final double xOffset, final double yOffset) {
        if (this.editorActive) {
            var io = ImGui.getIO();
            io.setMouseWheelH(io.getMouseWheelH() + (float) xOffset);
            io.setMouseWheel(io.getMouseWheel() + (float) yOffset);
            if (io.getWantCaptureMouse()) {
                return;
            }
        }

        if (this.prevUserCallbackScroll != null && windowId == this.mainWindowPtr) {
            this.prevUserCallbackScroll.invoke(windowId, 0, yOffset);
        }
    }

    public void keyCallback(final long windowId, int key, final int scancode, final int action, final int mods) {
        if (key < GLFW_KEY_SPACE || key > GLFW_KEY_LAST) {
            if (this.prevUserCallbackKey != null && windowId == this.mainWindowPtr) {
                this.prevUserCallbackKey.invoke(windowId, key, scancode, action, mods);
            }
            return;
        }

        updateKeyModifiers(windowId);

        var io = ImGui.getIO();
        boolean gameShortcut = this.gameKeyPredicate.test(key, scancode)
                || (key >= GLFW_KEY_F1 && key <= GLFW_KEY_F25)
                || glfwGetKey(windowId, GLFW_KEY_F3) != GLFW_RELEASE;
        EditorKeyRouter.Target target = this.keyRouter.route(key, action, this.editorActive,
                this.editorVisible, io.getWantTextInput(), gameShortcut, this.onTabPressed != null);

        switch (target) {
            case GAME -> {
                if (this.prevUserCallbackKey != null && windowId == this.mainWindowPtr) {
                    this.prevUserCallbackKey.invoke(windowId, key, scancode, action, mods);
                }
            }
            case IMGUI -> {
                final int imguiKey = glfwKeyToImGuiKey(key);
                if (imguiKey != ImGuiKey.None) {
                    io.addKeyEvent(imguiKey, action != GLFW_RELEASE);
                    io.setKeyEventNativeData(imguiKey, key, scancode);
                }
            }
            case PASSTHROUGH -> {
                if (action == GLFW_PRESS && this.onTabPressed != null) this.onTabPressed.run();
            }
        }
    }

    public void setGameKeyPredicate(BiPredicate<Integer, Integer> predicate) {
        this.gameKeyPredicate = predicate;
    }

    public void cursorPosCallback(final long windowId, final double xpos, final double ypos) {
        if (!this.editorActive) {
            if (this.prevUserCallbackCursorPos != null && windowId == this.mainWindowPtr) {
                this.prevUserCallbackCursorPos.invoke(windowId, xpos, ypos);
            }
            return;
        }

        MouseHandledBy handledBy = this.getMouseHandledBy();

        if (handledBy.allowGame() && this.prevUserCallbackCursorPos != null && windowId == this.mainWindowPtr) {
            this.prevUserCallbackCursorPos.invoke(windowId, xpos, ypos);
        }
    }

    public void windowFocusCallback(final long windowId, final boolean focused) {
        if (!focused) this.keyRouter.reset();
        if (this.prevUserCallbackWindowFocus != null && windowId == this.mainWindowPtr) {
            this.prevUserCallbackWindowFocus.invoke(windowId, focused);
        }

        ImGui.getIO().addFocusEvent(focused);
    }

    public void cursorEnterCallback(final long windowId, final boolean entered) {
        if (this.prevUserCallbackCursorEnter != null && windowId == this.mainWindowPtr) {
            this.prevUserCallbackCursorEnter.invoke(windowId, entered);
        }

        if (entered) {
            this.mouseWindowPtr = windowId;
        }
        if (!entered && this.mouseWindowPtr == windowId) {
            this.mouseWindowPtr = 0;
        }
    }

    public void charModsCallback(final long windowId, final int c, final int mods) {
        var io = ImGui.getIO();
        boolean pass = !this.editorActive || (!io.getWantCaptureKeyboard() && !io.getWantTextInput());

        if (pass && this.prevUserCallbackCharMods != null && windowId == this.mainWindowPtr) {
            this.prevUserCallbackCharMods.invoke(windowId, c, mods);
        }
    }

    public void charCallback(final long windowId, final int c) {
        if (!this.editorActive) {
            if (this.prevUserCallbackChar != null && windowId == this.mainWindowPtr) {
                this.prevUserCallbackChar.invoke(windowId, c);
            }
            return;
        }

        var io = ImGui.getIO();
        if (!io.getWantCaptureKeyboard() && !io.getWantTextInput() && this.prevUserCallbackChar != null && windowId == this.mainWindowPtr) {
            this.prevUserCallbackChar.invoke(windowId, c);
        }

        io.addInputCharacter(c);
    }

    public boolean init(final long windowHandle) {
        return init(windowHandle, true);
    }

    public boolean init(final long windowId, final boolean installCallbacks) {
        this.mainWindowPtr = windowId;

        final ImGuiIO io = ImGui.getIO();

        io.addBackendFlags(ImGuiBackendFlags.HasMouseCursors | ImGuiBackendFlags.HasSetMousePos);
        io.setBackendPlatformName("imgui_java_impl_glfw");
        io.setGetClipboardTextFn(new ImStrSupplier() {
            @Override
            public String get() {
                final String clipboardString = glfwGetClipboardString(windowId);
                return clipboardString != null ? clipboardString : "";
            }
        });
        io.setSetClipboardTextFn(new ImStrConsumer() {
            @Override
            public void accept(String s) {
                glfwSetClipboardString(windowId, s);
            }
        });
        // Missing user-configured X11 cursors are expected; fall back to the arrow without an error.
        final GLFWErrorCallback prevErrorCallback = glfwSetErrorCallback(null);
        this.mouseCursors[ImGuiMouseCursor.Arrow] = glfwCreateStandardCursor(GLFW_ARROW_CURSOR);
        this.mouseCursors[ImGuiMouseCursor.TextInput] = glfwCreateStandardCursor(GLFW_IBEAM_CURSOR);
        this.mouseCursors[ImGuiMouseCursor.ResizeNS] = glfwCreateStandardCursor(GLFW_RESIZE_NS_CURSOR);
        this.mouseCursors[ImGuiMouseCursor.ResizeEW] = glfwCreateStandardCursor(GLFW_RESIZE_EW_CURSOR);
        this.mouseCursors[ImGuiMouseCursor.Hand] = glfwCreateStandardCursor(GLFW_HAND_CURSOR);
        if (glfwHasNewCursors) {
            this.mouseCursors[ImGuiMouseCursor.ResizeAll] = glfwCreateStandardCursor(GLFW_RESIZE_ALL_CURSOR);
            this.mouseCursors[ImGuiMouseCursor.ResizeNESW] = glfwCreateStandardCursor(GLFW_RESIZE_NESW_CURSOR);
            this.mouseCursors[ImGuiMouseCursor.ResizeNWSE] = glfwCreateStandardCursor(GLFW_RESIZE_NWSE_CURSOR);
            this.mouseCursors[ImGuiMouseCursor.NotAllowed] = glfwCreateStandardCursor(GLFW_NOT_ALLOWED_CURSOR);
        } else {
            this.mouseCursors[ImGuiMouseCursor.ResizeAll] = glfwCreateStandardCursor(GLFW_ARROW_CURSOR);
            this.mouseCursors[ImGuiMouseCursor.ResizeNESW] = glfwCreateStandardCursor(GLFW_ARROW_CURSOR);
            this.mouseCursors[ImGuiMouseCursor.ResizeNWSE] = glfwCreateStandardCursor(GLFW_ARROW_CURSOR);
            this.mouseCursors[ImGuiMouseCursor.NotAllowed] = glfwCreateStandardCursor(GLFW_ARROW_CURSOR);
        }
        glfwSetErrorCallback(prevErrorCallback);
        if (installCallbacks) {
            this.callbacksInstalled = true;
            this.prevUserCallbackWindowFocus = glfwSetWindowFocusCallback(windowId, this::windowFocusCallback);
            this.prevUserCallbackCursorEnter = glfwSetCursorEnterCallback(windowId, this::cursorEnterCallback);
            this.prevUserCallbackMouseButton = glfwSetMouseButtonCallback(windowId, this::mouseButtonCallback);
            this.prevUserCallbackScroll = glfwSetScrollCallback(windowId, this::scrollCallback);
            this.prevUserCallbackCursorPos = glfwSetCursorPosCallback(windowId, this::cursorPosCallback);
            this.prevUserCallbackKey = glfwSetKeyCallback(windowId, this::keyCallback);
            this.prevUserCallbackChar = glfwSetCharCallback(windowId, this::charCallback);
            this.prevUserCallbackCharMods = glfwSetCharModsCallback(windowId, this::charModsCallback);
        }
        glfwGetWindowSize(this.mainWindowPtr, this.winWidth, this.winHeight);
        glfwGetFramebufferSize(this.mainWindowPtr, this.fbWidth, this.fbHeight);

        io.setDisplaySize((float) this.winWidth[0], (float) this.winHeight[0]);
        if (this.winWidth[0] > 0 && this.winHeight[0] > 0) {
            final float scaleX = (float) this.fbWidth[0] / this.winWidth[0];
            final float scaleY = (float) this.fbHeight[0] / this.winHeight[0];
            io.setDisplayFramebufferScale(scaleX, scaleY);
        }

        return true;
    }

    public void newFrame() {
        final ImGuiIO io = ImGui.getIO();

        glfwGetWindowSize(this.mainWindowPtr, this.winWidth, this.winHeight);
        glfwGetFramebufferSize(this.mainWindowPtr, this.fbWidth, this.fbHeight);

        io.setDisplaySize((float) this.winWidth[0], (float) this.winHeight[0]);
        if (this.winWidth[0] > 0 && this.winHeight[0] > 0) {
            final float scaleX = (float) this.fbWidth[0] / this.winWidth[0];
            final float scaleY = (float) this.fbHeight[0] / this.winHeight[0];
            io.setDisplayFramebufferScale(scaleX, scaleY);
        }

        final double currentTime = glfwGetTime();
        io.setDeltaTime(this.time > 0.0 ? (float) (currentTime - this.time) : 1.0f / 60.0f);
        this.time = currentTime;
        boolean shiftDown = GLFW.glfwGetKey(this.mainWindowPtr, GLFW_KEY_LEFT_SHIFT) != 0 || GLFW.glfwGetKey(this.mainWindowPtr, GLFW_KEY_RIGHT_SHIFT) != 0;
        boolean ctrlDown = GLFW.glfwGetKey(this.mainWindowPtr, GLFW_KEY_LEFT_CONTROL) != 0 || GLFW.glfwGetKey(this.mainWindowPtr, GLFW_KEY_RIGHT_CONTROL) != 0;
        boolean altDown = GLFW.glfwGetKey(this.mainWindowPtr, GLFW_KEY_LEFT_ALT) != 0 || GLFW.glfwGetKey(this.mainWindowPtr, GLFW_KEY_RIGHT_ALT) != 0;
        boolean superDown = GLFW.glfwGetKey(this.mainWindowPtr, GLFW_KEY_LEFT_SUPER) != 0 || GLFW.glfwGetKey(this.mainWindowPtr, GLFW_KEY_RIGHT_SUPER) != 0;

        io.setKeyShift(shiftDown);
        io.setKeyCtrl(ctrlDown);
        io.setKeyAlt(altDown);
        io.setKeySuper(superDown);

        this.updateMousePosAndButtons();
        this.updateMouseCursor();
    }

    private void updateMousePosAndButtons() {
        var io = ImGui.getIO();

        var mouseHandledBy = this.getMouseHandledBy();
        if (!mouseHandledBy.allowImgui()) {
            for (int i = 0; i < ImGuiMouseButton.COUNT; i++) {
                io.setMouseDown(i, false);
                this.mouseJustPressed[i] = false;
            }
            return;
        }

        for (int i = 0; i < ImGuiMouseButton.COUNT; i++) {
            // Preserve clicks that press and release between frames.
            io.setMouseDown(i, this.mouseJustPressed[i] || glfwGetMouseButton(this.mainWindowPtr, i) != 0);
            this.mouseJustPressed[i] = false;
        }

        io.getMousePos(this.mousePosBackup);
        io.setMousePos(-Float.MAX_VALUE, -Float.MAX_VALUE);
        io.setMouseHoveredViewport(0);

        final boolean focused = glfwGetWindowAttrib(this.mainWindowPtr, GLFW_FOCUSED) != 0;
        if (focused) {
            for (int i = 0; i < ImGuiMouseButton.COUNT; i++) {
                if (!io.getMouseDown(i)) {
                    io.setMouseDown(i, glfwGetMouseButton(this.mainWindowPtr, i) != 0);
                }
            }
        }
        if (io.getWantSetMousePos() && focused) {
            glfwSetCursorPos(this.mainWindowPtr, this.mousePosBackup.x, this.mousePosBackup.y);
        }
        if (this.mouseWindowPtr == this.mainWindowPtr || focused) {
            glfwGetCursorPos(this.mainWindowPtr, this.mouseX, this.mouseY);
            io.setMousePos((float) this.mouseX[0], (float) this.mouseY[0]);
        }
    }

    private void updateMouseCursor() {
        var io = ImGui.getIO();
        final boolean noCursorChange = io.hasConfigFlags(ImGuiConfigFlags.NoMouseCursorChange);
        final boolean cursorDisabled = glfwGetInputMode(this.mainWindowPtr, GLFW_CURSOR) == GLFW_CURSOR_DISABLED;

        if (noCursorChange || cursorDisabled) {
            return;
        }

        final int imguiCursor = ImGui.getMouseCursor();

        if (imguiCursor == ImGuiMouseCursor.None || io.getMouseDrawCursor()) {
            glfwSetInputMode(this.mainWindowPtr, GLFW_CURSOR, GLFW_CURSOR_HIDDEN);
        } else {
            glfwSetCursor(this.mainWindowPtr, this.mouseCursors[imguiCursor] != 0 ? this.mouseCursors[imguiCursor] : this.mouseCursors[ImGuiMouseCursor.Arrow]);
            glfwSetInputMode(this.mainWindowPtr, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
        }
    }

    public void dispose() {
        if (this.callbacksInstalled) {
            glfwSetWindowFocusCallback(this.mainWindowPtr, this.prevUserCallbackWindowFocus);
            glfwSetCursorEnterCallback(this.mainWindowPtr, this.prevUserCallbackCursorEnter);
            glfwSetMouseButtonCallback(this.mainWindowPtr, this.prevUserCallbackMouseButton);
            glfwSetScrollCallback(this.mainWindowPtr, this.prevUserCallbackScroll);
            glfwSetCursorPosCallback(this.mainWindowPtr, this.prevUserCallbackCursorPos);
            glfwSetKeyCallback(this.mainWindowPtr, this.prevUserCallbackKey);
            glfwSetCharCallback(this.mainWindowPtr, this.prevUserCallbackChar);
            glfwSetCharModsCallback(this.mainWindowPtr, this.prevUserCallbackCharMods);
            this.callbacksInstalled = false;
        }

        for (int i = 0; i < this.mouseCursors.length; i++) {
            if (this.mouseCursors[i] != 0) {
                glfwDestroyCursor(this.mouseCursors[i]);
                this.mouseCursors[i] = 0;
            }
        }
    }
}
