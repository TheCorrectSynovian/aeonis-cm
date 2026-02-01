package com.qc.aeonis.llm.network.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Packet sent from client to server to test the LLM connection.
 * Server will attempt a small test request and send back the result.
 */
public record LlmTestConnectionPayload(
    String provider,
    String model,
    String apiKey,  // Temporary key for testing (not stored)
    double temperature
) implements CustomPacketPayload {
    
    public static final Type<LlmTestConnectionPayload> ID = new Type<>(
        Identifier.fromNamespaceAndPath("aeonis-manager", "llm_test_connection")
    );
    
    public static final StreamCodec<FriendlyByteBuf, LlmTestConnectionPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUtf(payload.provider);
            buf.writeUtf(payload.model);
            buf.writeUtf(payload.apiKey);
            buf.writeDouble(payload.temperature);
        },
        buf -> new LlmTestConnectionPayload(
            buf.readUtf(),
            buf.readUtf(),
            buf.readUtf(),
            buf.readDouble()
        )
    );
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
