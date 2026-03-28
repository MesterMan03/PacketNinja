/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.bitsquidd.ninja.ui

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.StringWidget
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import xyz.bitsquidd.ninja.config.ConfigScreen

class OverlayScreen(val parent: Screen?) : Screen(Component.literal("Packet Ninja")) {
    override fun init() {
        addRenderableOnly(StringWidget(title, font))
        addHeaderButtons()
    }

    private fun addHeaderButtons() {
        val settingsButton = Button.builder(Component.literal("Settings")) {
            val configScreen = ConfigScreen.create(this)
            this.minecraft.setScreen(configScreen)
        }.bounds(5, 5, font.width("Settings") + 10, 20).build()

        addRenderableWidget(settingsButton)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(graphics, mouseX, mouseY, delta)
    }

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean {
        return false
    }
}