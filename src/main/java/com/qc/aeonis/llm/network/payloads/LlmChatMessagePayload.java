package com.qc.aeonis.llm.network.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Packet sent from client to server when a player sends a chat message
 * that should be processed by the Aeonis LLM.
 */
public record LlmChatMessagePayload(
    String message
) implements CustomPacketPayload {
    
    public static final Type<LlmChatMessagePayload> ID = new Type<>(
        Identifier.fromNamespaceAndPath("aeonis-manager", "llm_chat_message")
    );
    
    public static final StreamCodec<FriendlyByteBuf, LlmChatMessagePayload> CODEC = StreamCodec.of(
        (buf, payload) -> buf.writeUtf(payload.message, 1024),
        buf -> new LlmChatMessagePayload(buf.readUtf(1024))
    );
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
