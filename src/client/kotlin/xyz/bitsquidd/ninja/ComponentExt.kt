/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.bitsquidd.ninja

import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.chat.MutableComponent
import kotlin.jvm.optionals.getOrNull

fun Component.edit(block: MutableComponent.() -> Unit): MutableComponent {
    return this.copy().apply(block)
}

val net.kyori.adventure.text.Component.native: Component get() {
    val json = GsonComponentSerializer.gson().serialize(this)
    return ComponentSerialization.CODEC.decode(JsonOps.INSTANCE, JsonParser.parseString(json))
        .resultOrPartial()
        .getOrNull()
        ?.first
        ?: Component.literal("Unknown")
}