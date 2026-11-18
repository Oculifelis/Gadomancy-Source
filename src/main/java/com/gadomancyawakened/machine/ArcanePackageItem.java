package com.gadomancyawakened.machine;

import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

/** An arcane package: use it to unpack every stack it holds (vanilla's container component) at your feet. */
public class ArcanePackageItem extends Item {
    public ArcanePackageItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream()
                    .forEach(s -> player.spawnAtLocation((net.minecraft.server.level.ServerLevel) level, s));
            level.playSound(null, player.blockPosition(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 0.75F, 1.0F);
            stack.consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }
}
