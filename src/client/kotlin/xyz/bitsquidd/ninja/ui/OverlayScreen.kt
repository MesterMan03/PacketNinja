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
import xyz.bitsquidd.ninja.config.ConfigScreen
import xyz.bitsquidd.ninja.format.PacketInfoBundle
import xyz.bitsquidd.ninja.handler.PacketType
import kotlin.jvm.optionals.getOrNull
import kotlin.math.max
import kotlin.math.roundToInt

class OverlayScreen(val parent: Screen?) : Screen(Component.literal("Packet Ninja")) {
    private var cachedVersion: Long = -1L
    private var cachedPackets: List<PacketInfoBundle> = emptyList()
    private var cachedPacketNames: List<Component> = emptyList()

    private val packetNameWidgets = mutableListOf<StringWidget>()
    private val adventureSerializer = GsonComponentSerializer.gson()

    private var previousVisibleRows = -1
    private var previousStartIndex = Int.MIN_VALUE

    // 0 = newest packet at top of the viewport
    private var scrollOffset: Int = 0

    private val panelLeft = 6
    private val headerHeight = font.lineHeight + 10
    private val panelTop = panelLeft + headerHeight + 4
    private val rowHeight = 18
    private val centerLineWidth = 2

    override fun init() {
        addHeaderButtons()
        refreshCache()
    }

    private fun addHeaderButtons() {
        val settingsButton = Button.builder(Component.literal("Settings")) {
            val configScreen = ConfigScreen.create(this)
            minecraft.setScreen(configScreen)
        }.bounds(panelLeft, panelLeft, font.width("Settings") + 10, headerHeight).build()

        addRenderableWidget(settingsButton)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        refreshCache()

        val panelRight = width - 6
        val panelBottom = height - 6
        val centerX = width / 2

        // Background panel
        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xB0181818.toInt())

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
        val visibleRows = max(1, (height - 6 - panelTop) / rowHeight)
        val maxScroll = max(0, cachedPackets.size - visibleRows)

        if (maxScroll == 0) return false

        // positive verticalAmount is natural scrolling (scroll up to see older packets)
        // negative is how other scrollable elements behave in-game
        // TODO: perhaps configurable scroll direction?
        scrollOffset = (scrollOffset - verticalAmount.roundToInt()).coerceIn(0, maxScroll)
        return true
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

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean = false
}
