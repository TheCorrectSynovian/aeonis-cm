package com.qc.aeonis.llm.network.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Packet sent from client to server to spawn or despawn the Aeonis bot.
 */
public record LlmSpawnBotPayload(
    boolean spawn  // true = spawn, false = despawn
) implements CustomPacketPayload {
    
    public static final Type<LlmSpawnBotPayload> ID = new Type<>(
        Identifier.fromNamespaceAndPath("aeonis-manager", "llm_spawn_bot")
    );
    
    public static final StreamCodec<FriendlyByteBuf, LlmSpawnBotPayload> CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeBoolean(payload.spawn),
        buf -> new LlmSpawnBotPayload(buf.readBoolean())
    );
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
