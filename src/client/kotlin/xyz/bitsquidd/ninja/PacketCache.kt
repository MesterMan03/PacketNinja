/*
 * This file is part of a Bit libraries package.
 * Licensed under the GNU Lesser General Public License v3.0.
 *
 * Copyright (c) 2023-2026 ImBit
 */

package xyz.bitsquidd.ninja

import xyz.bitsquidd.ninja.format.PacketInfoBundle
import java.util.concurrent.atomic.AtomicLong

// TODO: config
private const val CACHE_SIZE: Int = 1000

data class PacketSnapshot(val version: Long, val packets: List<PacketInfoBundle>)

object PacketCache {
    private val lock = Any()
    private val cache = ArrayDeque<PacketInfoBundle>(CACHE_SIZE)
    private val version = AtomicLong(0L)

    @JvmStatic
    fun addPacket(packet: PacketInfoBundle) {
        synchronized(lock) {
            if (cache.size >= CACHE_SIZE) {
                cache.removeFirst()
            }
            cache.addLast(packet)
            version.incrementAndGet()
        }
    }

    fun version(): Long = version.get()

    fun snapshot(): PacketSnapshot {
        synchronized(lock) {
            return PacketSnapshot(version.get(), cache.toList())
        }
    }
}
