package com.qc.aeonis.llm.network.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Packet sent from client to server to issue a task command to Aeonis.
 */
public record LlmTaskPayload(
    TaskType taskType,
    String targetPlayer,  // For FOLLOW task
    double x, double y, double z,  // For WALK_TO task
    String buildPreset  // For BUILD task
) implements CustomPacketPayload {
    
    public enum TaskType {
        FOLLOW,
        WALK_TO,
        BUILD,
        STOP
    }
    
    public static final Type<LlmTaskPayload> ID = new Type<>(
        Identifier.fromNamespaceAndPath("aeonis-manager", "llm_task")
    );
    
    public static final StreamCodec<FriendlyByteBuf, LlmTaskPayload> CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeEnum(payload.taskType);
            buf.writeUtf(payload.targetPlayer != null ? payload.targetPlayer : "");
            buf.writeDouble(payload.x);
            buf.writeDouble(payload.y);
            buf.writeDouble(payload.z);
            buf.writeUtf(payload.buildPreset != null ? payload.buildPreset : "");
        },
        buf -> new LlmTaskPayload(
            buf.readEnum(TaskType.class),
            buf.readUtf(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readDouble(),
            buf.readUtf()
        )
    );
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
    
    // Factory methods for convenience
    public static LlmTaskPayload follow(String playerName) {
        return new LlmTaskPayload(TaskType.FOLLOW, playerName, 0, 0, 0, "");
    }
    
    public static LlmTaskPayload walkTo(double x, double y, double z) {
        return new LlmTaskPayload(TaskType.WALK_TO, "", x, y, z, "");
    }
    
    public static LlmTaskPayload build(String preset) {
        return new LlmTaskPayload(TaskType.BUILD, "", 0, 0, 0, preset);
    }
    
    public static LlmTaskPayload stop() {
        return new LlmTaskPayload(TaskType.STOP, "", 0, 0, 0, "");
    }
}
