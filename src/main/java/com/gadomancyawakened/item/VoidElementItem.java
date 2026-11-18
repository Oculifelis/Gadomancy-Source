package com.gadomancyawakened.item;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Element: Darkness. Every 16 ticks, held anywhere in an inventory or lying on the ground, it blinds every living thing
 * within 8 blocks for 5 seconds, the holder included. Dropped, it never despawns. Feeds the Essentia Compressor.
 */
public class VoidElementItem extends Item {
    private static final int PERIOD = 16;

    public VoidElementItem(Properties properties) {
        super(properties);
    }

    private static void darken(ServerLevel level, Entity at) {
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(at.blockPosition()).inflate(8.5))) {
            living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0, true, true));
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity holder, @Nullable EquipmentSlot slot) {
        if (holder.tickCount % PERIOD == 0) {
            darken(level, holder);
        }
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (entity.level() instanceof ServerLevel level) {
            if (entity.tickCount == 1) {
                entity.setUnlimitedLifetime();
            }
            if (entity.tickCount % PERIOD == 0) {
                darken(level, entity);
                level.sendParticles(ParticleTypes.SQUID_INK, entity.getX(), entity.getY() + 0.3, entity.getZ(), 6, 0.2, 0.2, 0.2, 0.01);
            }
        }
        return false;
    }
}
