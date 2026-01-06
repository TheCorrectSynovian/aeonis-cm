package com.qc.aeonis.llm.network.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Packet sent from server to client to open the LLM configuration GUI.
 * Contains current config state (but NOT the API key).
 */
public record LlmOpenGuiPayload(
    String currentProvider,
    String currentModel,
    boolean hasApiKey,
    double temperature,
    int maxTokens,
    int maxBlocksPerMinute,
    int maxEditRadius,
    boolean griefProtection
) implements CustomPacketPayload {
    
    public static final Type<LlmOpenGuiPayload> ID = new Type<>(
        Identifier.fromNamespaceAndPath("aeonis-manager", "llm_open_gui")
    );
    
    public static final StreamCodec<FriendlyByteBuf, LlmOpenGuiPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUtf(payload.currentProvider);
            buf.writeUtf(payload.currentModel);
            buf.writeBoolean(payload.hasApiKey);
            buf.writeDouble(payload.temperature);
            buf.writeInt(payload.maxTokens);
            buf.writeInt(payload.maxBlocksPerMinute);
            buf.writeInt(payload.maxEditRadius);
            buf.writeBoolean(payload.griefProtection);
        },
        buf -> new LlmOpenGuiPayload(
            buf.readUtf(),
            buf.readUtf(),
            buf.readBoolean(),
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
}
