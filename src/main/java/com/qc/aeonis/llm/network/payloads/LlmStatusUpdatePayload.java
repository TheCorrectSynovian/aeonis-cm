package com.qc.aeonis.llm.network.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Packet sent from server to client with status updates.
 * Used for test results, spawn results, and general status.
 */
public record LlmStatusUpdatePayload(
    StatusType statusType,
    boolean success,
    String message
) implements CustomPacketPayload {
    
    public enum StatusType {
        TEST_RESULT,
        SPAWN_RESULT,
        CONFIG_SAVED,
        ERROR,
        INFO
    }
    
    public static final Type<LlmStatusUpdatePayload> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath("aeonis-manager", "llm_status_update")
    );
    
    public static final StreamCodec<FriendlyByteBuf, LlmStatusUpdatePayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeEnum(payload.statusType);
            buf.writeBoolean(payload.success);
            buf.writeUtf(payload.message);
        },
        buf -> new LlmStatusUpdatePayload(
            buf.readEnum(StatusType.class),
            buf.readBoolean(),
            buf.readUtf()
        )
    );
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
    
    // Factory methods
    public static LlmStatusUpdatePayload testSuccess(String response) {
        return new LlmStatusUpdatePayload(StatusType.TEST_RESULT, true, "Connection successful! Response: " + response);
    }
    
    public static LlmStatusUpdatePayload testFailure(String error) {
        return new LlmStatusUpdatePayload(StatusType.TEST_RESULT, false, "Connection failed: " + error);
    }
    
    public static LlmStatusUpdatePayload spawnSuccess() {
        return new LlmStatusUpdatePayload(StatusType.SPAWN_RESULT, true, "Aeonis spawned successfully!");
    }
    
    public static LlmStatusUpdatePayload spawnFailure(String reason) {
        return new LlmStatusUpdatePayload(StatusType.SPAWN_RESULT, false, "Failed to spawn Aeonis: " + reason);
    }
    
    public static LlmStatusUpdatePayload configSaved() {
        return new LlmStatusUpdatePayload(StatusType.CONFIG_SAVED, true, "Configuration saved!");
    }
    
    public static LlmStatusUpdatePayload error(String message) {
        return new LlmStatusUpdatePayload(StatusType.ERROR, false, message);
    }
    
    public static LlmStatusUpdatePayload info(String message) {
        return new LlmStatusUpdatePayload(StatusType.INFO, true, message);
    }
}
