package com.qc.aeonis.llm.util;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Utility to create ClientboundPlayerInfoUpdatePacket for fake players
 * without triggering the connection.latency() null crash.
 * 
 * Uses reflection to directly construct the packet with custom Entry objects.
 */
public class FakePlayerPacketUtil {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("aeonis-fake-player");
    
    /**
     * Create a player info update packet for a fake player.
     * 
     * @param uuid The fake player's UUID
     * @param profile The fake player's GameProfile
     * @param displayName The display name
     * @param gameMode The game mode
     * @return The packet, or null if reflection fails
     */
    public static ClientboundPlayerInfoUpdatePacket createAddPlayerPacket(
            UUID uuid,
            GameProfile profile,
            Component displayName,
            GameType gameMode) {
        
        try {
            // Create the Entry record directly
            // Entry(UUID profileId, GameProfile profile, boolean listed, int latency, 
            //       GameType gameMode, Component displayName, boolean showHat, int listOrder, RemoteChatSession.Data chatSession)
            ClientboundPlayerInfoUpdatePacket.Entry entry = new ClientboundPlayerInfoUpdatePacket.Entry(
                uuid,
                profile,
                true,  // listed
                0,     // latency - hardcoded for fake player
                gameMode,
                displayName,
                true,  // showHat
                0,     // listOrder
                null   // chatSession
            );
            
            // Now create the packet using reflection
            // The packet has private fields: actions (EnumSet) and entries (List<Entry>)
            ClientboundPlayerInfoUpdatePacket packet = createPacketReflection(
                EnumSet.of(
                    ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME
                ),
                List.of(entry)
            );
            
            return packet;
            
        } catch (Exception e) {
            LOGGER.error("Failed to create player info packet via reflection: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Create a player info remove packet (set listed to false).
     */
    public static ClientboundPlayerInfoUpdatePacket createRemoveFromListPacket(
            UUID uuid,
            GameProfile profile,
            Component displayName) {
        
        try {
            ClientboundPlayerInfoUpdatePacket.Entry entry = new ClientboundPlayerInfoUpdatePacket.Entry(
                uuid,
                profile,
                false,  // listed = false (remove from tab list)
                0,
                GameType.SURVIVAL,
                displayName,
                true,
                0,
                null
            );
            
            return createPacketReflection(
                EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED),
                List.of(entry)
            );
            
        } catch (Exception e) {
            LOGGER.error("Failed to create player remove packet via reflection: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Create the packet using reflection to set private fields.
     */
    @SuppressWarnings("unchecked")
    private static ClientboundPlayerInfoUpdatePacket createPacketReflection(
            EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions,
            List<ClientboundPlayerInfoUpdatePacket.Entry> entries) throws Exception {
        
        // Get the private constructor or use Unsafe to create instance
        // The packet class has no public constructor that takes (EnumSet, List)
        // So we need to use reflection to create an empty instance and set fields
        
        // First, try to find a constructor we can use
        Constructor<?>[] constructors = ClientboundPlayerInfoUpdatePacket.class.getDeclaredConstructors();
        
        ClientboundPlayerInfoUpdatePacket packet = null;
        
        // Try to find the network constructor (used for deserialization)
        for (Constructor<?> ctor : constructors) {
            Class<?>[] paramTypes = ctor.getParameterTypes();
            if (paramTypes.length == 2) {
                // Check if this is (EnumSet, List) - the internal constructor
                if (paramTypes[0] == EnumSet.class && paramTypes[1] == List.class) {
                    ctor.setAccessible(true);
                    packet = (ClientboundPlayerInfoUpdatePacket) ctor.newInstance(actions, entries);
                    break;
                }
            }
        }
        
        if (packet == null) {
            // Fall back to creating empty instance and setting fields
            // Use sun.misc.Unsafe or similar to allocate without constructor
            packet = createUsingUnsafe();
            if (packet != null) {
                setField(packet, "actions", actions);
                setField(packet, "entries", entries);
            }
        }
        
        return packet;
    }
    
    /**
     * Create instance using Unsafe (bypasses constructors).
     */
    private static ClientboundPlayerInfoUpdatePacket createUsingUnsafe() {
        try {
            // Get the Unsafe instance
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            
            // Allocate instance without calling constructor
            return (ClientboundPlayerInfoUpdatePacket) unsafe.allocateInstance(ClientboundPlayerInfoUpdatePacket.class);
        } catch (Exception e) {
            LOGGER.error("Failed to create instance using Unsafe: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Set a private field value.
     */
    private static void setField(Object obj, String fieldName, Object value) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }
}
