package com.cobblemon.mod.common.client.imgui;

import imgui.ImDrawData;
import imgui.ImFontAtlas;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImVec4;
import imgui.flag.ImGuiBackendFlags;
import imgui.type.ImInt;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL20.glDeleteShader;
import static org.lwjgl.opengl.GL20.glDetachShader;
import static org.lwjgl.opengl.GL20.glGetAttribLocation;
import static org.lwjgl.opengl.GL20.glGetProgramiv;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL32.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL32.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL32.GL_ARRAY_BUFFER_BINDING;
import static org.lwjgl.opengl.GL32.GL_BACK;
import static org.lwjgl.opengl.GL32.GL_BLEND;
import static org.lwjgl.opengl.GL32.GL_BLEND_DST_ALPHA;
import static org.lwjgl.opengl.GL32.GL_BLEND_DST_RGB;
import static org.lwjgl.opengl.GL32.GL_BLEND_EQUATION_ALPHA;
import static org.lwjgl.opengl.GL32.GL_BLEND_EQUATION_RGB;
import static org.lwjgl.opengl.GL32.GL_BLEND_SRC_ALPHA;
import static org.lwjgl.opengl.GL32.GL_BLEND_SRC_RGB;
import static org.lwjgl.opengl.GL32.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL32.GL_CONTEXT_COMPATIBILITY_PROFILE_BIT;
import static org.lwjgl.opengl.GL32.GL_CONTEXT_PROFILE_MASK;
import static org.lwjgl.opengl.GL32.GL_CULL_FACE;
import static org.lwjgl.opengl.GL32.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL32.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL32.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL32.GL_FALSE;
import static org.lwjgl.opengl.GL32.GL_FILL;
import static org.lwjgl.opengl.GL32.GL_FLOAT;
import static org.lwjgl.opengl.GL32.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL32.GL_FRONT;
import static org.lwjgl.opengl.GL32.GL_FRONT_AND_BACK;
import static org.lwjgl.opengl.GL32.GL_FUNC_ADD;
import static org.lwjgl.opengl.GL32.GL_INFO_LOG_LENGTH;
import static org.lwjgl.opengl.GL32.GL_LINEAR;
import static org.lwjgl.opengl.GL32.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL32.GL_MAJOR_VERSION;
import static org.lwjgl.opengl.GL32.GL_MINOR_VERSION;
import static org.lwjgl.opengl.GL32.GL_ONE;
import static org.lwjgl.opengl.GL32.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL32.GL_POLYGON_MODE;
import static org.lwjgl.opengl.GL32.GL_PRIMITIVE_RESTART;
import static org.lwjgl.opengl.GL32.GL_RGBA;
import static org.lwjgl.opengl.GL32.GL_SCISSOR_BOX;
import static org.lwjgl.opengl.GL32.GL_SCISSOR_TEST;
import static org.lwjgl.opengl.GL32.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL32.GL_STENCIL_TEST;
import static org.lwjgl.opengl.GL32.GL_STREAM_DRAW;
import static org.lwjgl.opengl.GL32.GL_TEXTURE0;
import static org.lwjgl.opengl.GL32.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL32.GL_TEXTURE_BINDING_2D;
import static org.lwjgl.opengl.GL32.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL32.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL32.GL_TRIANGLES;
import static org.lwjgl.opengl.GL32.GL_TRUE;
import static org.lwjgl.opengl.GL32.GL_UNPACK_ALIGNMENT;
import static org.lwjgl.opengl.GL32.GL_UNPACK_ROW_LENGTH;
import static org.lwjgl.opengl.GL32.GL_UNPACK_SKIP_PIXELS;
import static org.lwjgl.opengl.GL32.GL_UNPACK_SKIP_ROWS;
import static org.lwjgl.opengl.GL32.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL32.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL32.GL_UNSIGNED_SHORT;
import static org.lwjgl.opengl.GL32.GL_UPPER_LEFT;
import static org.lwjgl.opengl.GL32.GL_VERTEX_ARRAY_BINDING;
import static org.lwjgl.opengl.GL32.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL32.GL_VIEWPORT;
import static org.lwjgl.opengl.GL32.glActiveTexture;
import static org.lwjgl.opengl.GL32.glAttachShader;
import static org.lwjgl.opengl.GL32.glBindBuffer;
import static org.lwjgl.opengl.GL32.glBindTexture;
import static org.lwjgl.opengl.GL32.glBindVertexArray;
import static org.lwjgl.opengl.GL32.glBlendEquation;
import static org.lwjgl.opengl.GL32.glBlendEquationSeparate;
import static org.lwjgl.opengl.GL32.glBlendFuncSeparate;
import static org.lwjgl.opengl.GL32.glBufferData;
import static org.lwjgl.opengl.GL32.glCompileShader;
import static org.lwjgl.opengl.GL32.glCreateProgram;
import static org.lwjgl.opengl.GL32.glCreateShader;
import static org.lwjgl.opengl.GL32.glDeleteBuffers;
import static org.lwjgl.opengl.GL32.glDeleteProgram;
import static org.lwjgl.opengl.GL32.glDeleteTextures;
import static org.lwjgl.opengl.GL32.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL32.glDisable;
import static org.lwjgl.opengl.GL32.glDrawElementsBaseVertex;
import static org.lwjgl.opengl.GL32.glEnable;
import static org.lwjgl.opengl.GL32.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL32.glGenBuffers;
import static org.lwjgl.opengl.GL32.glGenTextures;
import static org.lwjgl.opengl.GL32.glGenVertexArrays;
import static org.lwjgl.opengl.GL32.glGetInteger;
import static org.lwjgl.opengl.GL32.glGetIntegerv;
import static org.lwjgl.opengl.GL32.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL32.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL32.glGetShaderiv;
import static org.lwjgl.opengl.GL32.glIsEnabled;
import static org.lwjgl.opengl.GL32.glIsProgram;
import static org.lwjgl.opengl.GL32.glLinkProgram;
import static org.lwjgl.opengl.GL32.glPixelStorei;
import static org.lwjgl.opengl.GL32.glPolygonMode;
import static org.lwjgl.opengl.GL32.glScissor;
import static org.lwjgl.opengl.GL32.glShaderSource;
import static org.lwjgl.opengl.GL32.glTexImage2D;
import static org.lwjgl.opengl.GL32.glTexParameteri;
import static org.lwjgl.opengl.GL32.glUniform1i;
import static org.lwjgl.opengl.GL32.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL32.glUseProgram;
import static org.lwjgl.opengl.GL32.glVertexAttribPointer;
import static org.lwjgl.opengl.GL32.glViewport;
import static org.lwjgl.opengl.GL33.GL_SAMPLER_BINDING;
import static org.lwjgl.opengl.GL33.glBindSampler;
import static org.lwjgl.opengl.GL45.GL_CLIP_ORIGIN;

public final class CobblemonImGuiGl3 {
    private static final String GLSL_VERSION = "#version 150";

    private static final class Data {
        protected int glVersion = 0;
        protected boolean glProfileIsCompat;
        protected GLCapabilities glCapabilities = null;
        protected int fontTexture = -1;
        protected int shaderHandle = -1;
        protected int attribLocationTex = 0;
        protected int attribLocationProjMtx = 0;
        protected int attribLocationVtxPos = 0;
        protected int attribLocationVtxUV = 0;
        protected int attribLocationVtxColor = 0;
        protected int vboHandle = 0;
        protected int elementsHandle = 0;
        protected boolean hasClipOrigin;
    }

    private static final class Properties {
        private final ImVec4 clipRect = new ImVec4();
        private final float[] orthoProjMatrix = new float[4 * 4];
        private final int[] lastDrawFramebuffer = new int[1];
        private final int[] lastActiveTexture = new int[1];
        private final int[] lastProgram = new int[1];
        private final int[] lastTexture = new int[1];
        private final int[] lastSampler = new int[1];
        private final int[] lastArrayBuffer = new int[1];
        private final int[] lastVertexArrayObject = new int[1];
        private final int[] lastPolygonMode = new int[2];
        private final int[] lastViewport = new int[4];
        private final int[] lastScissorBox = new int[4];
        private final int[] lastBlendSrcRgb = new int[1];
        private final int[] lastBlendDstRgb = new int[1];
        private final int[] lastBlendSrcAlpha = new int[1];
        private final int[] lastBlendDstAlpha = new int[1];
        private final int[] lastBlendEquationRgb = new int[1];
        private final int[] lastBlendEquationAlpha = new int[1];
        private boolean lastEnableBlend = false;
        private boolean lastEnableCullFace = false;
        private boolean lastEnableDepthTest = false;
        private boolean lastEnableStencilTest = false;
        private boolean lastEnableScissorTest = false;
        private boolean lastEnablePrimitiveRestart = false;
    }

    private Data data = null;
    private final Properties props = new Properties();

    public void init() {
        data = new Data();
        data.glVersion = glGetInteger(GL_MAJOR_VERSION) * 100 + glGetInteger(GL_MINOR_VERSION) * 10;
        if (data.glVersion < 320) throw new IllegalStateException("The editor renderer requires OpenGL 3.2");
        data.glProfileIsCompat = (glGetInteger(GL_CONTEXT_PROFILE_MASK) & GL_CONTEXT_COMPATIBILITY_PROFILE_BIT) != 0;
        data.glCapabilities = GL.getCapabilities();
        data.hasClipOrigin = data.glCapabilities.OpenGL45 || data.glCapabilities.GL_ARB_clip_control;

        final ImGuiIO io = ImGui.getIO();
        io.setBackendRendererName("imgui-java_impl_opengl3");
        io.addBackendFlags(ImGuiBackendFlags.RendererHasVtxOffset);
    }

    public void newFrame() {
        if (data.shaderHandle == -1) {
            createDeviceObjects();
        }
        if (data.fontTexture == -1) {
            createFontsTexture();
        }
    }

    public void dispose() {
        if (data == null) return;
        final ImGuiIO io = ImGui.getIO();

        destroyDeviceObjects();

        io.setBackendRendererName(null);
        io.removeBackendFlags(ImGuiBackendFlags.RendererHasVtxOffset);
        data = null;
    }

    private void setupRenderState(final ImDrawData drawData, final int fbWidth, final int fbHeight, final int gVertexArrayObject) {
        glEnable(GL_BLEND);
        glBlendEquation(GL_FUNC_ADD);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_CULL_FACE);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_STENCIL_TEST);
        glEnable(GL_SCISSOR_TEST);

        glDisable(GL_PRIMITIVE_RESTART);
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);

        boolean clipOriginLowerLeft = true;
        if (data.hasClipOrigin) {
            final int[] currentClipOrigin = new int[1];
            glGetIntegerv(GL_CLIP_ORIGIN, currentClipOrigin);
            if (currentClipOrigin[0] == GL_UPPER_LEFT) {
                clipOriginLowerLeft = false;
            }
        }

        glViewport(0, 0, fbWidth, fbHeight);
        float L = drawData.getDisplayPosX();
        float R = drawData.getDisplayPosX() + drawData.getDisplaySizeX();
        float T = drawData.getDisplayPosY();
        float B = drawData.getDisplayPosY() + drawData.getDisplaySizeY();

        if (data.hasClipOrigin && !clipOriginLowerLeft) {
            float tmp = T;
            T = B;
            B = tmp;
        }

        // Map ImGui display coordinates to clip space; T/B also account for an upper-left GL origin.
        props.orthoProjMatrix[0] = 2.0f / (R - L);
        props.orthoProjMatrix[5] = 2.0f / (T - B);
        props.orthoProjMatrix[10] = -1.0f;
        props.orthoProjMatrix[12] = (R + L) / (L - R);
        props.orthoProjMatrix[13] = (T + B) / (B - T);
        props.orthoProjMatrix[15] = 1.0f;

        glUseProgram(data.shaderHandle);
        glUniform1i(data.attribLocationTex, 0);
        glUniformMatrix4fv(data.attribLocationProjMtx, false, props.orthoProjMatrix);

        if (data.glVersion >= 330 || (data.glCapabilities != null && data.glCapabilities.GL_ARB_sampler_objects)) {
            glBindSampler(0, 0);
        }

        glBindVertexArray(gVertexArrayObject);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);

        glBindBuffer(GL_ARRAY_BUFFER, data.vboHandle);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, data.elementsHandle);
        glEnableVertexAttribArray(data.attribLocationVtxPos);
        glEnableVertexAttribArray(data.attribLocationVtxUV);
        glEnableVertexAttribArray(data.attribLocationVtxColor);
        glVertexAttribPointer(data.attribLocationVtxPos, 2, GL_FLOAT, false, ImDrawData.sizeOfImDrawVert(), 0);
        glVertexAttribPointer(data.attribLocationVtxUV, 2, GL_FLOAT, false, ImDrawData.sizeOfImDrawVert(), 8);
        glVertexAttribPointer(data.attribLocationVtxColor, 4, GL_UNSIGNED_BYTE, true, ImDrawData.sizeOfImDrawVert(), 16);
    }

    public void renderDrawData(final ImDrawData drawData) {
        final int fbWidth = (int) (drawData.getDisplaySizeX() * drawData.getFramebufferScaleX());
        final int fbHeight = (int) (drawData.getDisplaySizeY() * drawData.getFramebufferScaleY());
        if (fbWidth <= 0 || fbHeight <= 0) {
            return;
        }

        if (drawData.getCmdListsCount() <= 0) {
            return;
        }

        glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, props.lastDrawFramebuffer);
        glGetIntegerv(GL_ACTIVE_TEXTURE, props.lastActiveTexture);
        glActiveTexture(GL_TEXTURE0);
        glGetIntegerv(GL_CURRENT_PROGRAM, props.lastProgram);
        glGetIntegerv(GL_TEXTURE_BINDING_2D, props.lastTexture);
        if (data.glVersion >= 330 || (data.glCapabilities != null && data.glCapabilities.GL_ARB_sampler_objects)) {
            glGetIntegerv(GL_SAMPLER_BINDING, props.lastSampler);
        }
        glGetIntegerv(GL_ARRAY_BUFFER_BINDING, props.lastArrayBuffer);
        glGetIntegerv(GL_VERTEX_ARRAY_BINDING, props.lastVertexArrayObject);
        glGetIntegerv(GL_POLYGON_MODE, props.lastPolygonMode);
        glGetIntegerv(GL_VIEWPORT, props.lastViewport);
        glGetIntegerv(GL_SCISSOR_BOX, props.lastScissorBox);
        glGetIntegerv(GL_BLEND_SRC_RGB, props.lastBlendSrcRgb);
        glGetIntegerv(GL_BLEND_DST_RGB, props.lastBlendDstRgb);
        glGetIntegerv(GL_BLEND_SRC_ALPHA, props.lastBlendSrcAlpha);
        glGetIntegerv(GL_BLEND_DST_ALPHA, props.lastBlendDstAlpha);
        glGetIntegerv(GL_BLEND_EQUATION_RGB, props.lastBlendEquationRgb);
        glGetIntegerv(GL_BLEND_EQUATION_ALPHA, props.lastBlendEquationAlpha);
        props.lastEnableBlend = glIsEnabled(GL_BLEND);
        props.lastEnableCullFace = glIsEnabled(GL_CULL_FACE);
        props.lastEnableDepthTest = glIsEnabled(GL_DEPTH_TEST);
        props.lastEnableStencilTest = glIsEnabled(GL_STENCIL_TEST);
        props.lastEnableScissorTest = glIsEnabled(GL_SCISSOR_TEST);
        props.lastEnablePrimitiveRestart = glIsEnabled(GL_PRIMITIVE_RESTART);

        final int vertexArrayObject = glGenVertexArrays();
        try {
            setupRenderState(drawData, fbWidth, fbHeight, vertexArrayObject);

            final float clipOffX = drawData.getDisplayPosX();
            final float clipOffY = drawData.getDisplayPosY();
            final float clipScaleX = drawData.getFramebufferScaleX();
            final float clipScaleY = drawData.getFramebufferScaleY();

            for (int n = 0; n < drawData.getCmdListsCount(); n++) {
                glBufferData(GL_ARRAY_BUFFER, drawData.getCmdListVtxBufferData(n), GL_STREAM_DRAW);
                glBufferData(GL_ELEMENT_ARRAY_BUFFER, drawData.getCmdListIdxBufferData(n), GL_STREAM_DRAW);

                for (int cmdIdx = 0; cmdIdx < drawData.getCmdListCmdBufferSize(n); cmdIdx++) {
                    drawData.getCmdListCmdBufferClipRect(props.clipRect, n, cmdIdx);

                    final float clipMinX = (props.clipRect.x - clipOffX) * clipScaleX;
                    final float clipMinY = (props.clipRect.y - clipOffY) * clipScaleY;
                    final float clipMaxX = (props.clipRect.z - clipOffX) * clipScaleX;
                    final float clipMaxY = (props.clipRect.w - clipOffY) * clipScaleY;

                    if (clipMaxX <= clipMinX || clipMaxY <= clipMinY) {
                        continue;
                    }

                    // ImGui clips from the top; OpenGL scissors from the bottom of the framebuffer.
                    glScissor((int) clipMinX, (int) (fbHeight - clipMaxY), (int) (clipMaxX - clipMinX), (int) (clipMaxY - clipMinY));

                    final long textureId = drawData.getCmdListCmdBufferTextureId(n, cmdIdx);
                    final int elemCount = drawData.getCmdListCmdBufferElemCount(n, cmdIdx);
                    final int idxOffset = drawData.getCmdListCmdBufferIdxOffset(n, cmdIdx);
                    final int vtxOffset = drawData.getCmdListCmdBufferVtxOffset(n, cmdIdx);
                    final long indices = idxOffset * (long) ImDrawData.sizeOfImDrawIdx();
                    final int type = ImDrawData.sizeOfImDrawIdx() == 2 ? GL_UNSIGNED_SHORT : GL_UNSIGNED_INT;

                    glBindTexture(GL_TEXTURE_2D, (int) textureId);

                    glDrawElementsBaseVertex(GL_TRIANGLES, elemCount, type, indices, vtxOffset);
                }
            }
        } finally {
            glDeleteVertexArrays(vertexArrayObject);

            // Binding a saved program pending deletion can fail after switching programs (Dear ImGui issue 6220).
            if (props.lastProgram[0] == 0 || glIsProgram(props.lastProgram[0])) {
                glUseProgram(props.lastProgram[0]);
            }
            glBindTexture(GL_TEXTURE_2D, props.lastTexture[0]);
            if (data.glVersion >= 330 || (data.glCapabilities != null && data.glCapabilities.GL_ARB_sampler_objects)) {
                glBindSampler(0, props.lastSampler[0]);
            }
            glActiveTexture(props.lastActiveTexture[0]);
            glBindVertexArray(props.lastVertexArrayObject[0]);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, props.lastDrawFramebuffer[0]);
            glBindBuffer(GL_ARRAY_BUFFER, props.lastArrayBuffer[0]);
            glBlendEquationSeparate(props.lastBlendEquationRgb[0], props.lastBlendEquationAlpha[0]);
            glBlendFuncSeparate(props.lastBlendSrcRgb[0], props.lastBlendDstRgb[0], props.lastBlendSrcAlpha[0], props.lastBlendDstAlpha[0]);
            if (props.lastEnableBlend) glEnable(GL_BLEND);
            else glDisable(GL_BLEND);
            if (props.lastEnableCullFace) glEnable(GL_CULL_FACE);
            else glDisable(GL_CULL_FACE);
            if (props.lastEnableDepthTest) glEnable(GL_DEPTH_TEST);
            else glDisable(GL_DEPTH_TEST);
            if (props.lastEnableStencilTest) glEnable(GL_STENCIL_TEST);
            else glDisable(GL_STENCIL_TEST);
            if (props.lastEnableScissorTest) glEnable(GL_SCISSOR_TEST);
            else glDisable(GL_SCISSOR_TEST);
            if (props.lastEnablePrimitiveRestart) glEnable(GL_PRIMITIVE_RESTART);
            else glDisable(GL_PRIMITIVE_RESTART);
            if (data.glProfileIsCompat) {
                glPolygonMode(GL_FRONT, props.lastPolygonMode[0]);
                glPolygonMode(GL_BACK, props.lastPolygonMode[1]);
            } else {
                glPolygonMode(GL_FRONT_AND_BACK, props.lastPolygonMode[0]);
            }
            glViewport(props.lastViewport[0], props.lastViewport[1], props.lastViewport[2], props.lastViewport[3]);
            glScissor(props.lastScissorBox[0], props.lastScissorBox[1], props.lastScissorBox[2], props.lastScissorBox[3]);
        }
    }

    private void createFontsTexture() {
        final ImFontAtlas fontAtlas = ImGui.getIO().getFonts();
        final ImInt width = new ImInt();
        final ImInt height = new ImInt();
        final ByteBuffer buffer = fontAtlas.getTexDataAsRGBA32(width, height);
        final int lastTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        final int lastUnpackBuffer = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        final int[] options = {GL_UNPACK_ALIGNMENT, GL_UNPACK_ROW_LENGTH, GL_UNPACK_SKIP_PIXELS, GL_UNPACK_SKIP_ROWS};
        final int[] previous = new int[options.length];
        for (int i = 0; i < options.length; i++) previous[i] = glGetInteger(options[i]);
        final int oldTexture = data.fontTexture;
        final int texture = glGenTextures();
        boolean uploaded = false;
        try {
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            // A bound pixel-unpack buffer would interpret the CPU atlas pointer as a buffer offset.
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
            glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
            glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width.get(), height.get(), 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
            fontAtlas.setTexID(texture);
            data.fontTexture = texture;
            uploaded = true;
            if (oldTexture != -1) glDeleteTextures(oldTexture);
        } finally {
            if (!uploaded) glDeleteTextures(texture);
            for (int i = 0; i < options.length; i++) glPixelStorei(options[i], previous[i]);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, lastUnpackBuffer);
            glBindTexture(GL_TEXTURE_2D, uploaded && lastTexture == oldTexture ? texture : lastTexture);
        }
    }

    private void destroyFontsTexture() {
        final ImGuiIO io = ImGui.getIO();
        if (data.fontTexture != -1) {
            glDeleteTextures(data.fontTexture);
            io.getFonts().setTexID(0);
            data.fontTexture = -1;
        }
    }

    private boolean checkShader(final int handle, final String desc) {
        final int[] status = new int[1];
        final int[] logLength = new int[1];
        glGetShaderiv(handle, GL_COMPILE_STATUS, status);
        glGetShaderiv(handle, GL_INFO_LOG_LENGTH, logLength);
        if (status[0] == GL_FALSE) {
            System.err.printf("%s: failed to compile %s! With GLSL: %s\n", this, desc, GLSL_VERSION);
        }
        if (logLength[0] > 1) {
            final String log = glGetShaderInfoLog(handle);
            System.err.println(log);
        }
        return status[0] == GL_TRUE;
    }

    private boolean checkProgram(final int handle, final String desc) {
        final int[] status = new int[1];
        final int[] logLength = new int[1];
        glGetProgramiv(handle, GL_LINK_STATUS, status);
        glGetProgramiv(handle, GL_INFO_LOG_LENGTH, logLength);
        if (status[0] == GL_FALSE) {
            System.err.printf("%s: failed to link %s! With GLSL: %s\n", this, desc, GLSL_VERSION);
        }
        if (logLength[0] > 1) {
            final String log = glGetProgramInfoLog(handle);
            System.err.println(log);
        }
        return status[0] == GL_TRUE;
    }

    private void createDeviceObjects() {
        final int lastTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        final int lastArrayBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        final int lastVertexArray = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int vertHandle = 0;
        int fragHandle = 0;
        boolean created = false;
        try {
            vertHandle = glCreateShader(GL_VERTEX_SHADER);
            glShaderSource(vertHandle, vertexShader());
            glCompileShader(vertHandle);
            if (!checkShader(vertHandle, "vertex shader")) throw new IllegalStateException("Failed to compile editor vertex shader");

            fragHandle = glCreateShader(GL_FRAGMENT_SHADER);
            glShaderSource(fragHandle, fragmentShader());
            glCompileShader(fragHandle);
            if (!checkShader(fragHandle, "fragment shader")) throw new IllegalStateException("Failed to compile editor fragment shader");

            data.shaderHandle = glCreateProgram();
            glAttachShader(data.shaderHandle, vertHandle);
            glAttachShader(data.shaderHandle, fragHandle);
            glLinkProgram(data.shaderHandle);
            if (!checkProgram(data.shaderHandle, "shader program")) throw new IllegalStateException("Failed to link editor shaders");
            glDetachShader(data.shaderHandle, vertHandle);
            glDetachShader(data.shaderHandle, fragHandle);

            data.attribLocationTex = glGetUniformLocation(data.shaderHandle, "Texture");
            data.attribLocationProjMtx = glGetUniformLocation(data.shaderHandle, "ProjMtx");
            data.attribLocationVtxPos = glGetAttribLocation(data.shaderHandle, "Position");
            data.attribLocationVtxUV = glGetAttribLocation(data.shaderHandle, "UV");
            data.attribLocationVtxColor = glGetAttribLocation(data.shaderHandle, "Color");
            data.vboHandle = glGenBuffers();
            data.elementsHandle = glGenBuffers();
            created = true;
        } finally {
            if (vertHandle != 0) glDeleteShader(vertHandle);
            if (fragHandle != 0) glDeleteShader(fragHandle);
            if (!created) destroyDeviceObjects();
            glBindTexture(GL_TEXTURE_2D, lastTexture);
            glBindBuffer(GL_ARRAY_BUFFER, lastArrayBuffer);
            glBindVertexArray(lastVertexArray);
        }
    }

    private void destroyDeviceObjects() {
        if (data.vboHandle != 0) {
            glDeleteBuffers(data.vboHandle);
            data.vboHandle = 0;
        }
        if (data.elementsHandle != 0) {
            glDeleteBuffers(data.elementsHandle);
            data.elementsHandle = 0;
        }
        if (data.shaderHandle != -1) {
            glDeleteProgram(data.shaderHandle);
            data.shaderHandle = -1;
        }
        destroyFontsTexture();
    }

    private String vertexShader() {
        return GLSL_VERSION + "\n"
                + "uniform mat4 ProjMtx;\n"
                + "in vec2 Position;\n"
                + "in vec2 UV;\n"
                + "in vec4 Color;\n"
                + "out vec2 Frag_UV;\n"
                + "out vec4 Frag_Color;\n"
                + "void main()\n"
                + "{\n"
                + "    Frag_UV = UV;\n"
                + "    Frag_Color = Color;\n"
                + "    gl_Position = ProjMtx * vec4(Position.xy,0,1);\n"
                + "}\n";
    }

    private String fragmentShader() {
        return GLSL_VERSION + "\n"
                + "uniform sampler2D Texture;\n"
                + "in vec2 Frag_UV;\n"
                + "in vec4 Frag_Color;\n"
                + "out vec4 Out_Color;\n"
                + "void main()\n"
                + "{\n"
                + "    Out_Color = Frag_Color * texture(Texture, Frag_UV.st);\n"
                + "}\n";
    }

}
