package com.qc.aeonis.util

import net.minecraft.core.Holder
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource

/**
 * Extension function to replace the removed playNotifySound method.
 * Plays a sound only to this specific player (not broadcast to others).
 * 
 * This is the 1.21.11 replacement for the old ServerPlayer.playNotifySound()
 */
fun ServerPlayer.playNotifySound(sound: SoundEvent, source: SoundSource, volume: Float, pitch: Float) {
    this.connection.send(
        ClientboundSoundPacket(
            Holder.direct(sound),
            source,
            this.x,
            this.y,
            this.z,
            volume,
            pitch,
            this.level().random.nextLong()
        )
    )
}

/**
 * Overload for Holder<SoundEvent> which some methods return (e.g. SoundEvents.NOTE_BLOCK_HAT)
 */
fun ServerPlayer.playNotifySound(sound: Holder<SoundEvent>, source: SoundSource, volume: Float, pitch: Float) {
    this.connection.send(
        ClientboundSoundPacket(
            sound,
            source,
            this.x,
            this.y,
            this.z,
            volume,
            pitch,
            this.level().random.nextLong()
        )
    )
}
