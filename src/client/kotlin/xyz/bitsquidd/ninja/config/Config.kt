/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.bitsquidd.ninja.config

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import xyz.bitsquidd.ninja.PacketInterceptorMod
import java.io.IOException
import java.nio.file.Files
import java.time.Duration

const val DEFAULT_PACKET_DELAY: Long = 500L
const val DEFAULT_SHOW_IN_CHAT: Boolean = true

object Config {
    private val GSON = GsonBuilder().setPrettyPrinting().create()
    private val FILE = FabricLoader.getInstance().configDir.resolve("packetninja.json")

    @JvmField
    var packetDelay: Duration = Duration.ofMillis(DEFAULT_PACKET_DELAY)

    @JvmField
    var showInChat: Boolean = DEFAULT_SHOW_IN_CHAT

    @JvmStatic
    fun save() {
        try {
            val data = ConfigData(
                packetDelayMs = packetDelay.toMillis(),
                showInChat = showInChat
            )
            Files.writeString(FILE, GSON.toJson(data))
        } catch (e: IOException) {
            PacketInterceptorMod.LOGGER.error("Failed to save config", e)
        }
    }

    @JvmStatic
    fun load() {
        if (Files.exists(FILE)) {
            try {
                val json = Files.readString(FILE)
                val data = GSON.fromJson(json, ConfigData::class.java)
                if (data != null) {
                    packetDelay = Duration.ofMillis(data.packetDelayMs)
                    showInChat = data.showInChat
                }
            } catch (e: Exception) {
                PacketInterceptorMod.LOGGER.error("Failed to load config, using defaults", e)
            }
        }
    }

    private data class ConfigData(val packetDelayMs: Long = DEFAULT_PACKET_DELAY, val showInChat: Boolean = DEFAULT_SHOW_IN_CHAT)
}
