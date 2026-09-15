package com.cobblemon.mod.common.client.gui.molangui

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.api.molangui.widget.WidgetDefinition
import com.cobblemon.mod.common.api.molangui.widget.WidgetInstance
import com.cobblemon.mod.common.api.molangui.widget.WidgetProperty
import com.cobblemon.mod.common.api.molangui.widget.WidgetType
import com.cobblemon.mod.common.api.molangui.widget.WidgetTree
import com.cobblemon.mod.common.api.molangui.ParameterValue
import com.cobblemon.mod.common.client.gui.molangui.renderer.LabelRenderer
import com.cobblemon.mod.common.client.gui.molangui.renderer.WidgetRenderer
import com.cobblemon.mod.common.client.gui.molangui.renderer.WidgetRendererRegistry
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.resolve
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.google.gson.JsonParser
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation
import org.joml.Matrix4f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

@BootstrapMinecraft
class MolangUiRenderingTest {
    private val graphics = mockk<GuiGraphics>(relaxed = true)
    private val color = floatArrayOf(.4f, .5f, .6f, .8f)
    private val draws = mutableListOf<Pair<String, Float>>()
    private var nanos = 0L
    private var partialTick = 0f
    private var failOn: String? = null
    private var failedY: Float? = null

    @BeforeEach
    fun setup() {
        mockkStatic(RenderSystem::class)
        mockkObject(WidgetRendererRegistry)
        every { RenderSystem.getShaderColor() } returns color
        every { graphics.setColor(any(), any(), any(), any()) } answers {
            for (i in 0..3) color[i] = arg(i)
        }
        val renderer = object : WidgetRenderer {
            override fun render(widget: WidgetInstance, guiGraphics: GuiGraphics, runtime: MoLangRuntime, mouseX: Float, mouseY: Float, delta: Float) {
                draws += widget.definition.id to color[3]
                partialTick = delta
                if (widget.definition.id == failOn) {
                    failedY = widget.absoluteY
                    throw IllegalStateException("render failed")
                }
            }
        }
        every { WidgetRendererRegistry.get(any()) } returns renderer
    }

    @AfterEach
    fun cleanup() {
        unmockkObject(WidgetRendererRegistry)
        unmockkStatic(RenderSystem::class)
    }

    @Test
    fun `animation queries use seconds while model renderers receive partial ticks`() {
        val tree = tree(widget("root"))
        nanos = 200_000_000L
        tree.render(graphics, 100, 100, 0.0, 0.0, .95f)
        assertEquals(.2, query(tree, "q.gui_time"), .000001)
        assertEquals(.2, query(tree, "q.delta"), .000001)
        assertEquals(.95f, partialTick)
        nanos = 300_000_000L
        tree.render(graphics, 100, 100, 0.0, 0.0, .15f)
        assertEquals(.3, query(tree, "q.gui_time"), .000001)
        assertEquals(.1, query(tree, "q.delta"), .000001)
        assertEquals(.15f, partialTick)
    }

    @Test
    fun `elapsed animation time is independent of the number of rendered frames`() {
        val frequent = tree(widget("frequent"))
        val infrequent = tree(widget("infrequent"))
        for (frame in 1..120) {
            nanos = frame * 2_000_000_000L / 120
            frequent.render(graphics, 100, 100, 0.0, 0.0, .75f)
        }
        infrequent.render(graphics, 100, 100, 0.0, 0.0, .1f)
        assertEquals(2.0, query(frequent, "q.gui_time"))
        assertEquals(query(frequent, "q.gui_time"), query(infrequent, "q.gui_time"))
    }

    @Test
    fun `nested opacity multiplies alpha without affecting siblings or the surrounding HUD`() {
        val root = widget("parent", .5f, listOf(widget("child", .25f), widget("sibling")))
        tree(root).render(graphics, 100, 100, 0.0, 0.0, 0f)
        assertEquals(listOf("parent" to .4f, "child" to .1f, "sibling" to .4f), draws)
        assertEquals(listOf(.4f, .5f, .6f, .8f), color.toList())
    }

    @Test
    fun `zero or invalid opacity skips a subtree and opacity above one is clamped`() {
        val children = listOf(widget("hidden", 0f, listOf(widget("hidden_child"))), widget("invalid", Float.NaN), widget("bright", 2f))
        tree(widget("root", children = children)).render(graphics, 100, 100, 0.0, 0.0, 0f)
        assertEquals(listOf("root" to .8f, "bright" to .8f), draws)
    }

    @Test
    fun `render failure restores alpha clipping and scroll coordinates`() {
        val child = widget("failure", .5f).let { it.copy(properties = it.properties + ("size" to WidgetProperty.literal(listOf(100, 120)))) }
        val tree = tree(widget("scroll", .5f, listOf(child)).copy(type = WidgetType.SCROLL_PANEL))
        tree.root.scrollOffset = -7f
        failOn = "failure"
        assertThrows(IllegalStateException::class.java) { tree.render(graphics, 100, 100, 0.0, 0.0, 0f) }
        assertEquals(-7f, failedY)
        assertEquals(0f, tree.root.children.single().absoluteY)
        assertEquals(listOf(.4f, .5f, .6f, .8f), color.toList())
        verify(exactly = 1) { graphics.disableScissor() }
    }

    @Test
    fun `bundled battle intro fades its versus label at the authored time`() {
        val definition = javaClass.getResourceAsStream("/assets/cobblemon/molang_ui/battle_intro.json")!!.bufferedReader().use {
            WidgetTree.parse(JsonParser.parseReader(it).asJsonObject)
        }
        val tree = MolangUiWidgetTree(ResourceLocation.fromNamespaceAndPath("cobblemon", "battle_intro"), UUID.randomUUID(), definition,
            mapOf("player_name" to ParameterValue.StringValue("Player"), "opponent_name" to ParameterValue.StringValue("Opponent"),
                "player_party_count" to ParameterValue.NumberValue(2.0), "opponent_party_count" to ParameterValue.NumberValue(2.0)),
            emptyMap(), nanoTime = { nanos })
        tree.render(graphics, 400, 200, 0.0, 0.0, .9f)
        assertTrue(draws.none { it.first == "vs_text" })
        draws.clear()
        nanos = 1_050_000_000L
        tree.render(graphics, 400, 200, 0.0, 0.0, .1f)
        assertEquals(.4f, draws.single { it.first == "vs_text" }.second, .00001f)
    }

    @Test
    fun `scaled labels stay aligned and restore their pose even when drawing fails`() {
        mockkStatic(Minecraft::class)
        try {
            val client = mockk<Minecraft>(relaxed = true)
            val font = mockk<Font>(relaxed = true)
            Minecraft::class.java.getDeclaredField("font").apply { isAccessible = true }.set(client, font)
            every { font.width(any<String>()) } returns 20
            every { Minecraft.getInstance() } returns client
            val pose = PoseStack()
            every { graphics.pose() } returns pose
            var drawnPose = Matrix4f()
            every { graphics.drawString(font, "label", 0, 0, any(), false) } answers {
                drawnPose = Matrix4f(pose.last().pose())
                throw IllegalStateException("font failed")
            }
            for ((alignment, x) in listOf("left" to 10f, "center" to 40f, "right" to 70f)) {
                val definition = widget("label").copy(type = WidgetType.LABEL, properties = mapOf(
                    "text" to WidgetProperty.literal("label"), "scale" to WidgetProperty.literal(2f),
                    "alignment" to WidgetProperty.literal(alignment)
                ))
                val instance = WidgetInstance(definition).apply { absoluteX = 10f; absoluteY = 15f; computedWidth = 100f }
                assertThrows(IllegalStateException::class.java) { LabelRenderer.render(instance, graphics, MoLangRuntime(), 0f, 0f, 0f) }
                assertEquals(x, drawnPose.m30())
                assertEquals(15f, drawnPose.m31())
                assertEquals(2f, drawnPose.m00())
                assertTrue(pose.clear())
                assertEquals(Matrix4f(), pose.last().pose())
            }
        } finally {
            unmockkStatic(Minecraft::class)
        }
    }

    private fun tree(root: WidgetDefinition) = MolangUiWidgetTree(
        ResourceLocation.fromNamespaceAndPath("cobblemon", "render_test"), UUID.randomUUID(), root,
        emptyMap(), emptyMap(), nanoTime = { nanos }
    )

    private fun widget(id: String, opacity: Float = 1f, children: List<WidgetDefinition> = emptyList()) = WidgetDefinition(
        id, WidgetType.PANEL, mapOf("opacity" to WidgetProperty.literal(opacity), "size" to WidgetProperty.literal(listOf(100, 100))), children
    )

    private fun query(tree: MolangUiWidgetTree, expression: String) = tree.runtime.resolve(expression.asExpression()).asDouble()
}
