package com.gadomancyawakened.effect;

import com.gadomancyawakened.GadomancyAwakened;
import com.leclowndu93150.thaumaturge.registry.TTAttributes;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.enchanting.GetEnchantmentLevelEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Gadomancy's potion effects. Mining Luck adds (level) to Fortune and Looting; Vis Affinity adds 8% per level to
 * Thaumaturge's vis discount attribute; Achromatic turns arrows and other projectiles aside by pushing them past the
 * wearer; Eldritch is a stacking mark the Alienis aura counts up. All values from Gadomancy 1.5.17.
 */
public final class GDEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, GadomancyAwakened.MODID);

    public static final DeferredHolder<MobEffect, MobEffect> MINING_LUCK = EFFECTS.register("mining_luck",
            () -> new Plain(MobEffectCategory.BENEFICIAL, 0xD6B65A));
    public static final DeferredHolder<MobEffect, MobEffect> VIS_AFFINITY = EFFECTS.register("vis_affinity",
            () -> new Plain(MobEffectCategory.BENEFICIAL, 0xCF5BE8).addAttributeModifier(TTAttributes.VIS_DISCOUNT,
                    GadomancyAwakened.id("vis_affinity"), 0.08, AttributeModifier.Operation.ADD_VALUE));
    public static final DeferredHolder<MobEffect, MobEffect> ACHROMATIC = EFFECTS.register("achromatic", Achromatic::new);
    public static final DeferredHolder<MobEffect, MobEffect> ELDRITCH = EFFECTS.register("eldritch",
            () -> new Plain(MobEffectCategory.NEUTRAL, 0x6A2C8E));

    private GDEffects() {
    }

    public static void register(IEventBus bus) {
        EFFECTS.register(bus);
        NeoForge.EVENT_BUS.addListener(GDEffects::onEnchantmentLevel);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.block.BreakBlockEvent e) -> remember(e.getPlayer()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.BlockDropsEvent e) -> LUCKY.remove());
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingDeathEvent e) -> remember(e.getSource().getEntity()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingDropsEvent e) -> LUCKY.remove());
    }

    /**
     * Enchantment level queries do not say who is asking, so the lucky entity is remembered from the moment a block
     * starts breaking or a mob dies until its drops are made.
     */
    private static final ThreadLocal<LivingEntity> LUCKY = new ThreadLocal<>();

    private static void remember(@org.jspecify.annotations.Nullable Entity e) {
        LUCKY.set(e instanceof LivingEntity l && l.hasEffect(MINING_LUCK) ? l : null);
    }

    private static void onEnchantmentLevel(GetEnchantmentLevelEvent event) {
        LivingEntity living = LUCKY.get();
        if (living == null || !(event.isTargetting(Enchantments.FORTUNE) || event.isTargetting(Enchantments.LOOTING))) {
            return;
        }
        var luck = living.getEffect(MINING_LUCK);
        if (luck != null) {
            Holder<net.minecraft.world.item.enchantment.Enchantment> e = event.getTargetEnchant();
            event.getEnchantments().upgrade(e, event.getEnchantments().getLevel(e) + luck.getAmplifier() + 1);
        }
    }

    static class Plain extends MobEffect {
        Plain(MobEffectCategory category, int colour) {
            super(category, colour);
        }
    }

    /** Projectiles within 4 blocks are carried on along their own path, twice as far as they are from the wearer. */
    static class Achromatic extends MobEffect {
        Achromatic() {
            super(MobEffectCategory.BENEFICIAL, 0xBFE8F2);
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return true;
        }

        @Override
        public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
            AABB box = entity.getBoundingBox().inflate(4, 3, 4);
            for (Projectile p : level.getEntitiesOfClass(Projectile.class, box, p -> p.getOwner() != entity)) {
                Vec3 motion = p.getDeltaMovement();
                if (motion.lengthSqr() < 1.0E-4) {
                    continue;
                }
                double dist = p.position().distanceTo(entity.position());
                p.setPos(p.position().add(motion.normalize().scale(dist * 2)));
            }
            return true;
        }
    }
}
