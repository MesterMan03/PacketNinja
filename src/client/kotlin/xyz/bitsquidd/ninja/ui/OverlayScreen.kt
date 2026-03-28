/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.bitsquidd.ninja.ui

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.toasts.SystemToast
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class OverlayScreen(val parent: Screen?) : Screen(Component.empty()) {
    override fun init() {
        val button = Button.builder(Component.literal("Hi there")) {
            this.minecraft.toastManager.addToast(
                SystemToast.multiline(
                    this.minecraft,
                    SystemToast.SystemToastId.NARRATOR_TOGGLE,
                    Component.literal("Hello World!"),
                    Component.literal("This is a toast!")
                )
            )
        }.bounds(40, 40, 120, 20).build()

        addRenderableWidget(button)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(graphics, mouseX, mouseY, delta)

        graphics.drawString(font, "Special Button", 40, 40 - font.lineHeight, 0x80FFFFFF.toInt(), true)
    }

    override fun onClose() {
        minecraft.setScreen(parent)
    }

    override fun isPauseScreen(): Boolean {
        return false
    }
}