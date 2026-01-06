package com.qc.aeonis.llm.network.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Packet sent from client to server to submit LLM configuration.
 * Contains provider, model, and optionally a new API key.
 * 
 * SECURITY: API key is only sent once during configuration.
 * After that, server stores it securely and client never receives it back.
 */
public record LlmConfigSubmitPayload(
    String provider,
    String model,
    String apiKey,  // Empty string if not changing
    double temperature,
    int maxTokens,
    int maxBlocksPerMinute,
    int maxEditRadius,
    boolean griefProtection
) implements CustomPacketPayload {
    
    public static final Type<LlmConfigSubmitPayload> ID = new Type<>(
        Identifier.fromNamespaceAndPath("aeonis-manager", "llm_config_submit")
    );
    
    public static final StreamCodec<FriendlyByteBuf, LlmConfigSubmitPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUtf(payload.provider);
            buf.writeUtf(payload.model);
            buf.writeUtf(payload.apiKey);
            buf.writeDouble(payload.temperature);
            buf.writeInt(payload.maxTokens);
            buf.writeInt(payload.maxBlocksPerMinute);
            buf.writeInt(payload.maxEditRadius);
            buf.writeBoolean(payload.griefProtection);
        },
        buf -> new LlmConfigSubmitPayload(
            buf.readUtf(),
            buf.readUtf(),
            buf.readUtf(),
            buf.readDouble(),
            buf.readInt(),
            buf.readInt(),
            buf.readInt(),
            buf.readBoolean()
        )
    );
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
    
    /**
     * Check if this payload includes a new API key
     */
    public boolean hasNewApiKey() {
        return apiKey != null && !apiKey.isEmpty();
    }
}
