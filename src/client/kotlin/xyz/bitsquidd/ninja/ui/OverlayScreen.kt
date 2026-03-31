/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.bitsquidd.ninja.ui

import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import xyz.bitsquidd.ninja.PacketCache
import xyz.bitsquidd.ninja.PacketFilter
import xyz.bitsquidd.ninja.PacketInterceptorMod
import xyz.bitsquidd.ninja.PacketRegistry
import xyz.bitsquidd.ninja.config.ConfigScreen
import xyz.bitsquidd.ninja.format.PacketInfoBundle
import xyz.bitsquidd.ninja.handler.PacketHandler
import xyz.bitsquidd.ninja.handler.PacketType
import kotlin.jvm.optionals.getOrNull
import kotlin.math.max
import kotlin.math.roundToInt

class OverlayScreen(val parent: Screen?) : Screen(Component.literal("Packet Ninja")) {
    private enum class ViewMode {
        TIMELINE,
        CONTROLS
    }

    private var cachedVersion: Long = -1L
    private var cachedPackets: List<PacketInfoBundle> = emptyList()
    private var cachedPacketNames: List<Component> = emptyList()

    private val packetNameWidgets = mutableListOf<StringWidget>()
    private val adventureSerializer = GsonComponentSerializer.gson()

    private var previousVisibleRows = -1
    private var previousStartIndex = Int.MIN_VALUE
    private var viewMode = ViewMode.TIMELINE

    // 0 = newest packet at top of the viewport
    private var scrollOffset: Int = 0
    private var controlsScrollOffset: Int = 0

    private val handlers: List<PacketHandler<*>> = PacketRegistry.getAllHandlers().toList()
    private val handlerToggleButtons = mutableListOf<Button>()
    private val handlerStateCache = mutableMapOf<String, Boolean>()

    private lateinit var settingsButton: Button
    private lateinit var timelineButton: Button
    private lateinit var controlsButton: Button
    private lateinit var startLoggingButton: Button
    private lateinit var stopLoggingButton: Button

    private val panelLeft = 6
    private val headerHeight = font.lineHeight + 10
    private val panelTop = panelLeft + headerHeight + 4
    private val rowHeight = 18
    private val centerLineWidth = 2
    private val scrollbarWidth = 3

    override fun init() {
        addHeaderButtons()
        addControlsWidgets()
        refreshCache()
        updateHeaderButtonStates()
    }

    private fun addHeaderButtons() {
        settingsButton = Button.builder(Component.literal("Settings")) {
            val configScreen = ConfigScreen.create(this)
            minecraft.setScreen(configScreen)
        }.bounds(panelLeft, panelLeft, font.width("Settings") + 16, headerHeight).build()

        timelineButton = Button.builder(Component.literal("Timeline")) {
            setViewMode(ViewMode.TIMELINE)
        }.bounds(settingsButton.right + 6, panelLeft, font.width("Timeline") + 16, headerHeight).build()

        controlsButton = Button.builder(Component.literal("Controls")) {
            setViewMode(ViewMode.CONTROLS)
        }.bounds(timelineButton.right + 6, panelLeft, font.width("Controls") + 16, headerHeight).build()

        addRenderableWidget(settingsButton)
        addRenderableWidget(timelineButton)
        addRenderableWidget(controlsButton)
    }

    private fun addControlsWidgets() {
        startLoggingButton = Button.builder(Component.literal("Start Logging")) {
            PacketInterceptorMod.logPackets = true
        }.bounds(0, 0, 120, 20).build()

        stopLoggingButton = Button.builder(Component.literal("Stop Logging")) {
            PacketInterceptorMod.logPackets = false
        }.bounds(0, 0, 120, 20).build()

        addRenderableWidget(startLoggingButton)
        addRenderableWidget(stopLoggingButton)

        for (handler in handlers) {
            val button = Button.builder(Component.literal(handler.friendlyName)) {
                packetFilter().togglePacketFilter(handler.friendlyName)
                updateSingleHandlerButtonState(handler, button = null)
            }.bounds(0, 0, 120, 20).build()

            handlerToggleButtons += button
            addRenderableWidget(button)
        }
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        refreshCache()
        updateHeaderButtonStates()

        val panelRight = timelineRight()
        val panelBottom = height - 6
        val centerX = timelineCenterX()

        updateControlsLayoutAndVisibility()

        // Background panel
        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xB0181818.toInt())

        if (viewMode == ViewMode.CONTROLS) {
            graphics.fill(controlsPanelLeft(), panelTop, controlsPanelRight(), panelBottom, 0xC0101010.toInt())
            graphics.drawString(font, Component.literal("Interception Controls"), controlsPanelLeft() + 10, panelTop + 8, 0xFFFFFF, false)
        }

        // Central timeline line
        graphics.fill(
            centerX - (centerLineWidth / 2),
            panelTop,
            centerX + (centerLineWidth / 2),
            panelBottom,
            0xFF666666.toInt()
        )

        val visibleRows = max(1, (panelBottom - panelTop) / rowHeight)
        val maxScroll = max(0, cachedPackets.size - visibleRows)
        scrollOffset = scrollOffset.coerceIn(0, maxScroll)
        ensurePacketNameWidgetPool(visibleRows)

        // newest at top
        val startIndex = cachedPackets.size - 1 - scrollOffset
        val endIndex = max(-1, startIndex - visibleRows + 1)
        val layoutChanged = previousVisibleRows != visibleRows || previousStartIndex != startIndex

        var row = 0
        for (i in startIndex downTo endIndex) {
            if (i !in cachedPackets.indices) continue

            val packet = cachedPackets[i]
            val packetName = cachedPacketNames[i]
            val y = panelTop + row * rowHeight

            drawPacketRow(graphics, packet, packetName, centerX, y, row, layoutChanged, mouseX, mouseY, delta)
            row++
        }

        hideUnusedPacketNameWidgets(row)
        previousVisibleRows = visibleRows
        previousStartIndex = startIndex

        renderTimelineScrollbar(graphics, panelBottom, visibleRows, maxScroll)

        graphics.drawString(
            font,
            Component.literal("Scroll to browse packet history"),
            10,
            height - 14,
            0xA0A0A0,
            false
        )

        super.render(graphics, mouseX, mouseY, delta)
    }

    private fun drawPacketRow(
        graphics: GuiGraphics,
        packet: PacketInfoBundle,
        packetName: Component,
        centerX: Int,
        y: Int,
        row: Int,
        layoutChanged: Boolean,
        mouseX: Int,
        mouseY: Int,
        delta: Float
    ) {
        val isIncoming = packet.type == PacketType.CLIENTBOUND
        val rowColor = packet.type.primaryColor.value() or 0xFF000000.toInt()
        val widget = packetNameWidgets[row]

        // Connection dot at the center line
        graphics.fill(centerX - 2, y + (rowHeight / 2) - 2, centerX + 3, y + (rowHeight / 2) + 3, rowColor)

        // Connector line from center to each side
        if (isIncoming) {
            graphics.fill(centerX + 2, y + (rowHeight / 2) - 1, centerX + 28, y + (rowHeight / 2) + 1, rowColor)
            if (layoutChanged || widget.message != packetName) {
                widget.setMessage(packetName)
                widget.setPosition(centerX + 34, y + 4)
            }
        } else {
            val textWidth = font.width(packetName)
            graphics.fill(centerX - 28, y + (rowHeight / 2) - 1, centerX - 2, y + (rowHeight / 2) + 1, rowColor)
            if (layoutChanged || widget.message != packetName) {
                widget.setMessage(packetName)
                widget.setPosition(centerX - 34 - textWidth, y + 4)
            }
        }

        widget.visible = true
        widget.render(graphics, mouseX, mouseY, delta)
    }

    private fun refreshCache() {
        val currentVersion = PacketCache.version()
        if (currentVersion != cachedVersion) {
            val snapshot = PacketCache.snapshot()
            cachedPackets = snapshot.packets
            cachedPacketNames = snapshot.packets.map { toNativeComponent(it.name) }
            cachedVersion = snapshot.version
            previousStartIndex = Int.MIN_VALUE

            val visibleRows = max(1, (height - 6 - panelTop) / rowHeight)
            val maxScroll = max(0, cachedPackets.size - visibleRows)
            scrollOffset = scrollOffset.coerceIn(0, maxScroll)
        }
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (viewMode == ViewMode.CONTROLS && isMouseOverControlsPanel(mouseX, mouseY)) {
            return scrollControlsList(verticalAmount)
        }

        return scrollTimeline(verticalAmount)
    }

    private fun scrollTimeline(verticalAmount: Double): Boolean {
        val visibleRows = max(1, (height - 6 - panelTop) / rowHeight)
        val maxScroll = max(0, cachedPackets.size - visibleRows)

        if (maxScroll == 0) return false

        // positive verticalAmount is natural scrolling (scroll up to see older packets)
        // negative is how other scrollable elements behave in-game
        // TODO: perhaps configurable scroll direction?
        scrollOffset = (scrollOffset - verticalAmount.roundToInt()).coerceIn(0, maxScroll)
        return true
    }

    private fun scrollControlsList(verticalAmount: Double): Boolean {
        val rows = controlsVisibleRows()
        val maxScroll = max(0, handlers.size - rows)
        if (maxScroll == 0) return false

        controlsScrollOffset = (controlsScrollOffset - verticalAmount.roundToInt()).coerceIn(0, maxScroll)
        return true
    }

    private fun renderTimelineScrollbar(graphics: GuiGraphics, panelBottom: Int, visibleRows: Int, maxScroll: Int) {
        if (maxScroll <= 0 || cachedPackets.isEmpty()) return

        val trackLeft = timelineRight() - 6
        val trackRight = trackLeft + scrollbarWidth
        val trackTop = panelTop + 2
        val trackBottom = panelBottom - 2
        val trackHeight = trackBottom - trackTop
        if (trackHeight <= 0) return

        val rawThumbHeight = ((visibleRows.toFloat() / cachedPackets.size.toFloat()) * trackHeight).roundToInt()
        val thumbHeight = rawThumbHeight.coerceIn(10, trackHeight)
        val thumbTravel = trackHeight - thumbHeight
        val progress = scrollOffset.toFloat() / maxScroll.toFloat()
        val thumbTop = trackTop + (thumbTravel * progress).roundToInt()
        val thumbBottom = thumbTop + thumbHeight

        graphics.fill(trackLeft, trackTop, trackRight, trackBottom, 0x60303030)
        graphics.fill(trackLeft, thumbTop, trackRight, thumbBottom, 0xC0CFCFCF.toInt())
    }

    private fun ensurePacketNameWidgetPool(requiredSize: Int) {
        while (packetNameWidgets.size < requiredSize) {
            packetNameWidgets += StringWidget(Component.empty(), font)
        }

        while (packetNameWidgets.size > requiredSize) {
            packetNameWidgets.removeLast()
        }
    }

    private fun hideUnusedPacketNameWidgets(fromIndex: Int) {
        for (i in fromIndex until packetNameWidgets.size) {
            packetNameWidgets[i].visible = false
        }
    }

    private fun toNativeComponent(component: net.kyori.adventure.text.Component): Component {
        val json = adventureSerializer.serialize(component)
        return ComponentSerialization.CODEC.decode(JsonOps.INSTANCE, JsonParser.parseString(json))
            .resultOrPartial()
            .getOrNull()
            ?.first
            ?: Component.literal("Unknown")
    }

    private fun updateHeaderButtonStates() {
        timelineButton.active = viewMode != ViewMode.TIMELINE
        controlsButton.active = viewMode != ViewMode.CONTROLS
    }

    private fun setViewMode(mode: ViewMode) {
        viewMode = mode
        // Force row widget positions to recompute after layout changes (timeline vs split view).
        previousVisibleRows = -1
        previousStartIndex = Int.MIN_VALUE
        updateHeaderButtonStates()
        updateControlsLayoutAndVisibility()
    }

    private fun updateControlsLayoutAndVisibility() {
        val inControlsMode = viewMode == ViewMode.CONTROLS

        startLoggingButton.visible = inControlsMode
        stopLoggingButton.visible = inControlsMode
        startLoggingButton.active = inControlsMode && !PacketInterceptorMod.logPackets
        stopLoggingButton.active = inControlsMode && PacketInterceptorMod.logPackets

        if (!inControlsMode) {
            for (button in handlerToggleButtons) {
                button.visible = false
                button.active = false
            }
            return
        }

        val left = controlsPanelLeft() + 10
        val right = controlsPanelRight() - 10
        val width = max(80, right - left)

        startLoggingButton.setPosition(left, panelTop + 24)
        startLoggingButton.setWidth((width - 6) / 2)
        stopLoggingButton.setPosition(left + startLoggingButton.width + 6, panelTop + 24)
        stopLoggingButton.setWidth(width - startLoggingButton.width - 6)

        val rows = controlsVisibleRows()
        val maxScroll = max(0, handlers.size - rows)
        controlsScrollOffset = controlsScrollOffset.coerceIn(0, maxScroll)

        for ((index, handler) in handlers.withIndex()) {
            val localRow = index - controlsScrollOffset
            val button = handlerToggleButtons[index]
            val visible = localRow in 0 until rows

            button.visible = visible
            button.active = visible
            if (!visible) continue

            val y = controlsListTop() + localRow * controlsRowHeight
            button.setPosition(left, y)
            button.setWidth(width)
            updateSingleHandlerButtonState(handler, button)
        }
    }

    private fun updateSingleHandlerButtonState(handler: PacketHandler<*>, button: Button?) {
        val enabled = packetFilter().isPacketEnabled(handler.packetClass)
        val cached = handlerStateCache[handler.friendlyName]
        if (cached == enabled && button != null) return

        handlerStateCache[handler.friendlyName] = enabled

        val targetButton = button ?: handlerToggleButtons[handlers.indexOf(handler)]
        val prefix = if (enabled) "[ON] " else "[OFF] "
        targetButton.setMessage(Component.literal(prefix + handler.friendlyName))
    }

    private fun packetFilter(): PacketFilter = PacketInterceptorMod.getInstance().packetFilter

    private fun timelineCenterX(): Int = if (viewMode == ViewMode.CONTROLS) width / 4 else width / 2

    private fun timelineRight(): Int = if (viewMode == ViewMode.CONTROLS) (width / 2) - 4 else width - 6

    private fun controlsPanelLeft(): Int = (width / 2) + 4

    private fun controlsPanelRight(): Int = width - 6

    private val controlsRowHeight: Int
        get() = 20

    private fun controlsListTop(): Int = panelTop + 52

    private fun controlsVisibleRows(): Int {
        val bottom = height - 14
        return max(1, (bottom - controlsListTop()) / controlsRowHeight)
    }

    private fun isMouseOverControlsPanel(mouseX: Double, mouseY: Double): Boolean =
        mouseX >= controlsPanelLeft() && mouseX <= controlsPanelRight() && mouseY >= panelTop && mouseY <= height - 6

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false
}
