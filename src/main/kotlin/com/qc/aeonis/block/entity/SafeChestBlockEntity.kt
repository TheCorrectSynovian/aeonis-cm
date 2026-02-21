package com.qc.aeonis.block.entity

import com.qc.aeonis.block.SafeChestBlock
import com.qc.aeonis.block.SafeChestType
import net.minecraft.core.BlockPos
import net.minecraft.core.NonNullList
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.ContainerHelper
import net.minecraft.world.entity.ContainerUser
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

class SafeChestBlockEntity(pos: BlockPos, state: BlockState) :
    RandomizableContainerBlockEntity(AeonisBlockEntities.SAFE_CHEST, pos, state) {

    private var items: NonNullList<ItemStack> = NonNullList.withSize(SINGLE_SIZE, ItemStack.EMPTY)

    /** How many players currently have this chest open */
    var openCount: Int = 0
        private set

    /** Drawer open progress for animation (0.0 = closed, 1.0 = fully open) */
    var drawerProgress: Float = 0.0f
        private set
    var prevDrawerProgress: Float = 0.0f
        private set

    private fun getChestType(): SafeChestType {
        val state = blockState
        return if (state.hasProperty(SafeChestBlock.CHEST_TYPE)) {
            state.getValue(SafeChestBlock.CHEST_TYPE)
        } else {
            SafeChestType.SINGLE
        }
    }

    private fun getExpectedSize(): Int {
        return when {
            getChestType().isTriple -> TRIPLE_SIZE
            getChestType().isDouble -> DOUBLE_SIZE
            else -> SINGLE_SIZE
        }
    }

    override fun getContainerSize(): Int = items.size

    override fun getDefaultName(): Component {
        return when {
            getChestType().isTriple -> Component.translatable("container.aeonis.safe_chest_triple")
            getChestType().isDouble -> Component.translatable("container.aeonis.safe_chest_double")
            else -> Component.translatable("container.aeonis.safe_chest")
        }
    }

    override fun loadAdditional(input: ValueInput) {
        super.loadAdditional(input)
        val expectedSize = getExpectedSize()
        items = NonNullList.withSize(expectedSize, ItemStack.EMPTY)
        if (!tryLoadLootTable(input)) {
            ContainerHelper.loadAllItems(input, items)
        }
    }

    override fun saveAdditional(output: ValueOutput) {
        super.saveAdditional(output)
        if (!trySaveLootTable(output)) {
            ContainerHelper.saveAllItems(output, items)
        }
    }

    override fun getItems(): NonNullList<ItemStack> = items

    override fun setItems(list: NonNullList<ItemStack>) {
        items = list
    }

    override fun createMenu(syncId: Int, inventory: Inventory): AbstractContainerMenu {
        // Max chest GUI size is 6 rows (54 slots)
        return when {
            getChestType().isTriple -> ChestMenu.sixRows(syncId, inventory, this)
            getChestType().isDouble -> ChestMenu.sixRows(syncId, inventory, this)
            else -> ChestMenu.threeRows(syncId, inventory, this)
        }
    }

    /**
     * Resize inventory when chest type changes (merge/split).
     * Preserves existing items where possible.
     */
    fun resizeForType(type: SafeChestType) {
        val newSize = when {
            type.isTriple -> TRIPLE_SIZE
            type.isDouble -> DOUBLE_SIZE
            else -> SINGLE_SIZE
        }
        if (items.size == newSize) return

        val newItems = NonNullList.withSize(newSize, ItemStack.EMPTY)
        for (i in 0 until minOf(items.size, newSize)) {
            newItems[i] = items[i]
        }
        // Drop items that don't fit
        if (items.size > newSize) {
            val lvl = level ?: return
            for (i in newSize until items.size) {
                if (!items[i].isEmpty) {
                    net.minecraft.world.Containers.dropItemStack(
                        lvl,
                        worldPosition.x.toDouble(),
                        worldPosition.y.toDouble(),
                        worldPosition.z.toDouble(),
                        items[i]
                    )
                }
            }
        }
        items = newItems
        setChanged()
    }

    // ---- Drawer animation ----

    override fun startOpen(user: ContainerUser) {
        if (!isRemoved) {
            openCount++
            if (openCount == 1) {
                level?.playSound(
                    null, worldPosition,
                    SoundEvents.IRON_DOOR_OPEN,
                    SoundSource.BLOCKS,
                    0.5f, level!!.random.nextFloat() * 0.1f + 0.9f
                )
            }
            setChanged()
        }
    }

    override fun stopOpen(user: ContainerUser) {
        if (!isRemoved) {
            openCount--
            if (openCount < 0) openCount = 0
            if (openCount == 0) {
                level?.playSound(
                    null, worldPosition,
                    SoundEvents.IRON_DOOR_CLOSE,
                    SoundSource.BLOCKS,
                    0.5f, level!!.random.nextFloat() * 0.1f + 0.9f
                )
            }
            setChanged()
        }
    }

    /**
     * Server-side tick for drawer animation progress.
     */
    fun tick(world: Level, pos: BlockPos, state: BlockState) {
        prevDrawerProgress = drawerProgress
        if (openCount > 0 && drawerProgress < 1.0f) {
            drawerProgress = minOf(drawerProgress + 0.1f, 1.0f)
        } else if (openCount == 0 && drawerProgress > 0.0f) {
            drawerProgress = maxOf(drawerProgress - 0.1f, 0.0f)
        }
    }

    companion object {
        const val SINGLE_SIZE = 27   // 3 rows
        const val DOUBLE_SIZE = 54   // 6 rows
        const val TRIPLE_SIZE = 54   // 6 rows (max chest GUI)
    }
}
