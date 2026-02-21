package com.qc.aeonis.block

import net.minecraft.util.StringRepresentable

/**
 * Type of safe chest: single, double (2 wide), or triple (3 wide).
 */
enum class SafeChestType(private val serialized: String, val slots: Int, val rows: Int, val width: Int) :
    StringRepresentable {

    SINGLE("single", 27, 3, 1),
    LEFT("left", 54, 6, 2),      // Left part of double
    RIGHT("right", 54, 6, 2),    // Right part of double
    TRIPLE_LEFT("triple_left", 54, 6, 3),    // Left part of triple
    TRIPLE_MIDDLE("triple_middle", 54, 6, 3), // Middle part of triple
    TRIPLE_RIGHT("triple_right", 54, 6, 3);   // Right part of triple

    override fun getSerializedName(): String = serialized

    val isSingle: Boolean get() = this == SINGLE
    val isDouble: Boolean get() = this == LEFT || this == RIGHT
    val isTriple: Boolean get() = this == TRIPLE_LEFT || this == TRIPLE_MIDDLE || this == TRIPLE_RIGHT

    companion object {
        val CODEC: com.mojang.serialization.Codec<SafeChestType> = StringRepresentable.fromEnum(SafeChestType::values)
    }
}
