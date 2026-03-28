package xyz.bitsquidd.ninja

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen
import net.minecraft.resources.Identifier
import net.minecraft.util.Util
import org.lwjgl.glfw.GLFW
import xyz.bitsquidd.ninja.ui.OverlayScreen

val KeyMapping.key get(): InputConstants.Key {
    val field = KeyMapping::class.java.declaredFields
        // we use lastOrNull as KeyMapping has two Key fields, the defaultKey and key, which is what we're looking for
        .lastOrNull { it.type == InputConstants.Key::class.java }
        ?: error("Key field not found")

    field.isAccessible = true
    return field.get(this) as InputConstants.Key
}

object KotlinEntry {
    private var lastOpened = 0L

    val keyCategory = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(PacketInterceptorMod.MOD_ID, "general"))

    @JvmStatic
    fun initialize() {
        val overlayKey = KeyBindingHelper.registerKeyBinding(KeyMapping(
            "key.packet-interceptor.overlay",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            keyCategory
        ))

        // we can't use the built-in KeyMapping API as it only works during the play state
        // instead we directly use GLFW functions
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val currentScreen = client.screen
            // don't check if we're in the key binds settings screen
            if(currentScreen is KeyBindsScreen) {
                return@register
            }

            val window = client.window.handle()
            val isDown = GLFW.glfwGetKey(window, overlayKey.key.value) == GLFW.GLFW_PRESS

            if(isDown) {
                val now = Util.getMillis()
                val delta = now - lastOpened
                if(delta < 150) {
                    // too early, abort
                    return@register
                }
                lastOpened = now

                if(currentScreen is OverlayScreen) {
                    client.setScreen(currentScreen.parent)
                } else {
                    client.setScreen(OverlayScreen(currentScreen))
                }
            }
        }
    }
}