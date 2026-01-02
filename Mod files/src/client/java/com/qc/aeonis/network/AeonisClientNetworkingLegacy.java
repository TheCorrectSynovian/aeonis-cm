package com.qc.aeonis.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import com.qc.aeonis.network.ControlModePayload;

public class AeonisClientNetworkingLegacy {
    // Legacy stub: the real client networking is implemented in Kotlin (AeonisClientNetworking object)
    public static volatile boolean controlling = false;
    public static volatile int controlledMobId = -1;

    public static boolean isControlling() { return controlling; }
    public static void setControlling(boolean enabled, int mobId) { controlling = enabled; controlledMobId = enabled ? mobId : -1; }
    public static void registerClient() { /* no-op legacy stub */ }
}
