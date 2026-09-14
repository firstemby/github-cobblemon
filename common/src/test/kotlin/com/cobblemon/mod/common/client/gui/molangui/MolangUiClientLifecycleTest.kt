package com.cobblemon.mod.common.client.gui.molangui

import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.api.molangui.GuiMode
import com.cobblemon.mod.common.api.molangui.MolangUiDefinition
import com.cobblemon.mod.common.api.molangui.MolangUiRegistry
import com.cobblemon.mod.common.api.molangui.ParameterType
import com.cobblemon.mod.common.api.molangui.container.MolangContainerMenu
import com.cobblemon.mod.common.api.molangui.widget.WidgetTree
import com.cobblemon.mod.common.junit.BootstrapMinecraft
import com.cobblemon.mod.common.net.messages.server.molangui.MolangUiClosedPacket
import com.google.gson.JsonParser
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import net.minecraft.client.Minecraft
import net.minecraft.client.MouseHandler
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.NonNullList
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

@BootstrapMinecraft
class MolangUiClientLifecycleTest {
    private val guiId = ResourceLocation.fromNamespaceAndPath("cobblemon", "lifecycle")
    private lateinit var client: Minecraft
    private var definition: MolangUiDefinition? = null
    private val closed = mutableListOf<UUID>()

    @BeforeEach
    fun setup() {
        mockkStatic(Minecraft::class, CobblemonNetwork::class)
        client = mockk(relaxed = true)
        client.screen = null
        client.player = null
        every { client.connection } returns mockk(relaxed = true)
        every { client.execute(any<Runnable>()) } answers { firstArg<Runnable>().run() }
        every { client.setScreen(any()) } answers {
            val old = client.screen
            client.screen = firstArg()
            old?.removed()
        }
        mockkObject(MolangUiRegistry)
        every { MolangUiRegistry.getDefinition(guiId) } answers { definition }
        every { CobblemonNetwork.sendToServer(any()) } answers {
            (firstArg<Any>() as? MolangUiClosedPacket)?.let { closed += it.sessionId }
        }
        MolangUiClientManager.cleanup()
        definition = view()
        every { Minecraft.getInstance() } returns client
        assertSame(client, Minecraft.getInstance())
        var executed = false
        client.execute { executed = true }
        assertTrue(executed)
    }

    @AfterEach
    fun cleanup() {
        try {
            MolangUiClientManager.cleanup()
        } finally {
            unmockkObject(MolangUiRegistry)
            unmockkStatic(Minecraft::class, CobblemonNetwork::class)
        }
    }

    @Test
    fun `replacing a screen cleans its tree and notifies the server exactly once`() {
        val id = open()
        val screen = client.screen as MolangUiScreen
        client.setScreen(mockk(relaxed = true))
        screen.removed()
        assertFalse(screen.widgetTree.prepare(200, 100))
        assertEquals(listOf(id), closed)
    }

    @Test
    fun `server closure cleans the screen without echoing another close packet`() {
        val id = open()
        val screen = client.screen as MolangUiScreen
        MolangUiClientManager.close(id)
        assertNull(client.screen)
        assertFalse(screen.widgetTree.prepare(200, 100))
        assertTrue(closed.isEmpty())
    }

    @Test
    fun `pending containers receive parameter and variable updates before the native screen opens`() {
        definition = view("""{"type":"panel","size":[{"molang":"q.width"},{"molang":"v.height"}]}""")
            .copy(parameters = mapOf("width" to ParameterType.NUMBER))
        val id = open(container = true)
        MolangUiClientManager.updateArgs(id, mapOf("width" to "42"), mapOf("height" to 35.0))
        val screen = containerScreen() as MolangUiContainerScreen
        assertTrue(screen.widgetTree.prepare(200, 100))
        assertEquals(42f, screen.widgetTree.root.computedWidth)
        assertEquals(35f, screen.widgetTree.root.computedHeight)
    }

    @Test
    fun `a cancelled pending container rejects the late native open and closes its menu`() {
        val id = open(container = true)
        MolangUiClientManager.close(id)
        val screen = containerScreen()
        assertTrue(screen is RejectedMolangUiContainerScreen)
        val player = mockk<LocalPlayer>(relaxed = true)
        every { player.isAlive } returns true
        client.player = player
        Screen::class.java.getDeclaredField("minecraft").apply { isAccessible = true }.set(screen, client)
        screen.tick()
        verify(exactly = 1) { player.closeContainer() }
        assertTrue(closed.isEmpty())
    }

    @Test
    fun `pending containers expire on ticks without rendering the HUD`() {
        val id = open(container = true)
        MolangUiClientManager.tick()
        assertTrue(closed.isEmpty())
        val expiredAt = System.nanoTime() + 6_000_000_000L
        MolangUiClientManager.tick(expiredAt)
        MolangUiClientManager.tick(expiredAt)
        assertEquals(listOf(id), closed)
        assertTrue(containerScreen() is RejectedMolangUiContainerScreen)

        val screenId = open()
        val screen = client.screen as MolangUiScreen
        MolangUiClientManager.tick(System.nanoTime() + 6_000_000_000L)
        assertSame(screen, client.screen)
        assertEquals(screenId, screen.sessionId)
        assertEquals(listOf(id), closed)
    }

    @Test
    fun `overlay rendering skips zero sized windows and resumes with scaled mouse coordinates`() {
        val tree = mockk<MolangUiWidgetTree>(relaxed = true)
        val graphics = mockk<GuiGraphics>(relaxed = true)
        val mouse = mockk<MouseHandler>(relaxed = true)
        Minecraft::class.java.getDeclaredField("mouseHandler").apply { isAccessible = true }.set(client, mouse)
        val overlay = MolangUiOverlay(tree, false)
        every { client.window.guiScaledWidth } returns 200
        every { client.window.guiScaledHeight } returns 100
        every { client.window.screenWidth } returns 0
        every { client.window.screenHeight } returns 0
        overlay.render(graphics, 0.5f)
        every { client.window.screenWidth } returns 800
        overlay.render(graphics, 0.5f)
        every { client.window.screenHeight } returns 400
        every { client.window.guiScaledHeight } returns 0
        overlay.render(graphics, 0.5f)
        verify(exactly = 0) { tree.render(any(), any(), any(), any(), any(), any()) }

        every { client.window.guiScaledHeight } returns 100
        every { mouse.xpos() } returns 400.0
        every { mouse.ypos() } returns 100.0
        overlay.render(graphics, 0.5f)
        verify(exactly = 1) { tree.render(graphics, 200, 100, 100.0, 25.0, 0.5f) }
    }

    @Test
    fun `disconnect cancels queued screens and pending containers without sending packets`() {
        val queued = mutableListOf<Runnable>()
        every { client.execute(any<Runnable>()) } answers { queued += firstArg<Runnable>() }
        open()
        MolangUiClientManager.cleanup()
        queued.forEach(Runnable::run)
        assertNull(client.screen)
        open(container = true)
        MolangUiClientManager.cleanup()
        assertTrue(containerScreen() is RejectedMolangUiContainerScreen)
        assertTrue(closed.isEmpty())
    }

    @Test
    fun `duplicate opens preserve the live screen and its local state`() {
        val id = open()
        val screen = client.screen as MolangUiScreen
        screen.widgetTree.runtime.environment.setSimpleVariable("counter", DoubleValue(9.0))
        open(id)
        assertSame(screen, client.screen)
        assertEquals(9.0, screen.widgetTree.runtime.environment.variable.map.getValue("counter").asDouble())
        assertTrue(closed.isEmpty())
    }

    @Test
    fun `opening a new screen disposes a pending container and its server session`() {
        val pending = open(container = true)
        val replacement = open()
        assertEquals(replacement, (client.screen as MolangUiScreen).sessionId)
        assertEquals(listOf(pending), closed)
        assertTrue(containerScreen() is RejectedMolangUiContainerScreen)
    }

    @Test
    fun `resource reload preserves scripted variables and adds only missing defaults`() {
        definition = view().copy(variables = mapOf("v.counter" to 1.0))
        open()
        val tree = (client.screen as MolangUiScreen).widgetTree
        tree.runtime.environment.setSimpleVariable("counter", DoubleValue(9.0))
        definition = definition!!.copy(variables = mapOf("counter" to 2.0, "added" to 3.0))
        MolangUiClientManager.onResourceReload()
        assertEquals(9.0, tree.runtime.environment.variable.map.getValue("counter").asDouble())
        assertEquals(3.0, tree.runtime.environment.variable.map.getValue("added").asDouble())
        assertTrue(closed.isEmpty())
    }

    @Test
    fun `incompatible resource reload closes active and pending views`() {
        val screenId = open()
        val tree = (client.screen as MolangUiScreen).widgetTree
        definition = definition!!.copy(contractVersion = "2")
        MolangUiClientManager.onResourceReload()
        assertNull(client.screen)
        assertFalse(tree.prepare(200, 100))
        assertEquals(listOf(screenId), closed)
        val pendingId = open(container = true)
        definition = definition!!.copy(exclusive = true)
        MolangUiClientManager.onResourceReload()
        assertTrue(containerScreen() is RejectedMolangUiContainerScreen)
        assertEquals(listOf(screenId, pendingId), closed)
    }

    @Test
    fun `invalid initial expansion rejects the session without opening a broken screen`() {
        definition = view("""{"type":"list","source_type":"count","source":-1,"item_template":{"type":"label"}}""")
        val id = open()
        assertNull(client.screen)
        assertEquals(listOf(id), closed)
    }

    @Test
    fun `failed close notification still releases the old view and permits a replacement`() {
        open()
        val tree = (client.screen as MolangUiScreen).widgetTree
        every { CobblemonNetwork.sendToServer(any()) } throws IllegalStateException("Disconnected")
        val replacement = open()
        assertFalse(tree.prepare(200, 100))
        assertEquals(replacement, (client.screen as MolangUiScreen).sessionId)
    }

    @Test
    fun `container initialization failure rejects the view and leaves no pending session`() {
        val id = open(container = true)
        val menu = mockk<MolangContainerMenu>(relaxed = true)
        every { menu.initializeClient(any()) } throws IllegalArgumentException("Invalid layout")
        assertTrue(containerScreen(menu) is RejectedMolangUiContainerScreen)
        assertEquals(listOf(id), closed)
        assertTrue(containerScreen() is RejectedMolangUiContainerScreen)
    }

    @Test
    fun `widget button press drag and release never fall through to inventory handling`() {
        definition = view("""{"type":"button","size":[100,50],"on_click_client":{"molang":"v.clicked = 1"}}""")
        val id = open(container = true)
        val menu = mockk<MolangContainerMenu>(relaxed = true)
        val carried = ItemStack(Items.DIAMOND, 5)
        every { menu.carried } returns carried
        AbstractContainerMenu::class.java.getDeclaredField("slots").apply { isAccessible = true }
            .set(menu, NonNullList.create<Slot>())
        val screen = containerScreen(menu) as MolangUiContainerScreen
        assertEquals(id, screen.sessionId)
        assertTrue(screen.widgetTree.prepare(200, 100))
        assertTrue(screen.mouseClicked(10.0, 10.0, 0))
        assertTrue(screen.mouseDragged(20.0, 20.0, 0, 10.0, 10.0))
        assertTrue(screen.mouseReleased(20.0, 20.0, 0))
        assertEquals(1.0, screen.widgetTree.runtime.environment.variable.map.getValue("clicked").asDouble())
        assertEquals(5, carried.count)
        verify(exactly = 0) { menu.setCarried(any()) }
    }

    @Test
    fun `invalid initial parameters reject a view before opening a screen`() {
        definition = view().copy(parameters = mapOf("amount" to ParameterType.NUMBER))
        val id = open(args = mapOf("amount" to "NaN"))
        assertNull(client.screen)
        assertEquals(listOf(id), closed)
    }

    @Test
    fun `invalid parameter update closes the view without applying accompanying variables`() {
        definition = view().copy(parameters = mapOf("amount" to ParameterType.NUMBER), variables = mapOf("counter" to 1.0))
        val id = open(args = mapOf("amount" to "5"))
        val tree = (client.screen as MolangUiScreen).widgetTree
        MolangUiClientManager.updateArgs(id, mapOf("amount" to "Infinity"), mapOf("counter" to 2.0))
        assertNull(client.screen)
        assertFalse(tree.prepare(200, 100))
        assertEquals(1.0, tree.runtime.environment.variable.map.getValue("counter").asDouble())
        assertEquals(listOf(id), closed)
    }

    private fun open(id: UUID = UUID.randomUUID(), container: Boolean = false, args: Map<String, String> = emptyMap()): UUID {
        val view = requireNotNull(definition)
        MolangUiClientManager.open(id, guiId, view.contractVersion, view.mode, args, view.exclusive, container)
        return id
    }

    private fun containerScreen(menu: MolangContainerMenu = mockk(relaxed = true)) =
        MolangUiClientManager.createContainerScreen(menu, Inventory(mockk<Player>(relaxed = true)))

    private fun view(root: String = """{"type":"panel","size":[100,50]}""") = MolangUiDefinition(
        guiId, 1, "1", GuiMode.SCREEN, emptyMap(), emptySet(), false,
        WidgetTree.parseWidget("root", JsonParser.parseString(root).asJsonObject), emptyMap()
    )
}
