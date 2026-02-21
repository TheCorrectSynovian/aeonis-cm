package com.qc.aeonis.mixin;

import com.qc.aeonis.dimension.AncardPortalShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(FlintAndSteelItem.class)
public class FlintAndSteelMixin {

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void aeonis$tryLightAncardPortal(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        BlockState state = level.getBlockState(clickedPos);
        Player player = context.getPlayer();

        // Only intercept if we're NOT lighting a campfire/candle (let vanilla handle those)
        if (CampfireBlock.canLight(state) || CandleBlock.canLight(state) || CandleCakeBlock.canLight(state)) {
            return;
        }

        BlockPos placePos = clickedPos.relative(context.getClickedFace());
        if (!level.getBlockState(placePos).isAir()) {
            return;
        }

        // Determine axis from clicked face or horizontal direction
        Direction.Axis axis;
        if (context.getClickedFace().getAxis().isHorizontal()) {
            axis = context.getClickedFace().getAxis();
        } else {
            axis = context.getHorizontalDirection().getAxis();
        }

        // Try to light an Ancard portal
        if (!level.isClientSide()) {
            Optional<AncardPortalShape> shape = AncardPortalShape.Companion.findEmptyPortalShape(level, placePos, axis);
            if (shape.isPresent()) {
                shape.get().createPortalBlocks(level);
                level.playSound(player, placePos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS,
                        1.0F, 0.8F + level.getRandom().nextFloat() * 0.2F);
                if (player != null) {
                    context.getItemInHand().hurtAndBreak(1, player, context.getHand().asEquipmentSlot());
                }
                cir.setReturnValue(InteractionResult.SUCCESS);
            }
        }
    }
}
