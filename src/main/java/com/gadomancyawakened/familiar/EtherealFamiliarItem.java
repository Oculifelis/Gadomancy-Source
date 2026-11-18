package com.gadomancyawakened.familiar;

import com.gadomancyawakened.GDComponents;
import com.leclowndu93150.thaumaturge.api.aspect.Aspects;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.content.wands.WandVisHelper;
import com.leclowndu93150.thaumaturge.registry.TTMobEffects;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.phys.Vec3;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * Ethereal Familiar: a charm (Curios) whose wisp zaps hostile mobs near its wearer. Without augments it hits one mob
 * for 1 magic damage within 6 blocks every 32 ticks, for free. Augments raise damage (+1.1 per level), range (+1.8),
 * speed (-6 ticks) and add an element; each attack then costs the augments' vis from a wand in the hotbar, paid only
 * one time in three. Numbers from Gadomancy 1.5.17's FamiliarController.
 */
public class EtherealFamiliarItem extends Item implements ICurioItem {
    private static final Map<UUID, Integer> COOLDOWNS = new HashMap<>();

    public EtherealFamiliarItem(Properties properties) {
        super(properties);
    }

    public static FamiliarData data(ItemStack stack) {
        return stack.getOrDefault(GDComponents.FAMILIAR.get(), FamiliarData.EMPTY);
    }

    @Override
    public void curioTick(SlotContext slot, ItemStack stack) {
        if (!(slot.entity() instanceof Player player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        FamiliarData data = data(stack);
        int colour = data.aspect().map(k -> Aspects.resolve(level, k).value().color()).orElse(0xC0C0FF);
        if (player.tickCount % 4 == 0) {
            double a = player.tickCount * 0.15;
            Vec3 at = player.position().add(Math.cos(a) * 0.8, 1.9, Math.sin(a) * 0.8);
            level.sendParticles(new DustParticleOptions(colour, 0.8F), at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
        int cool = COOLDOWNS.getOrDefault(player.getUUID(), 0);
        if (cool > 0) {
            COOLDOWNS.put(player.getUUID(), cool - 1);
            return;
        }
        attack(level, player, data, at -> { });
    }

    private static void attack(ServerLevel level, Player player, FamiliarData data, Consumer<LivingEntity> after) {
        int dmg = data.level(Augment.DAMAGE);
        int range = data.level(Augment.RANGE);
        int speed = data.level(Augment.SPEED);
        Augment element = data.element().orElse(null);
        int el = element == null ? 0 : data.level(element);
        float damage = 1 + 1.1F * dmg;
        double reach = 6 + 1.8 * range;
        int cooldown = 32 - 6 * speed;
        int targets = 1;
        if (element == Augment.SHOCK) {
            damage += 0.8F * el;
        } else if (element == Augment.FIRE) {
            damage += 0.5F * el;
        } else if (element == Augment.POISON) {
            reach += 0.6 * el;
        } else if (element == Augment.WEAKNESS) {
            damage -= 0.3F * el;
            targets = el >= 3 ? 3 : 1;
        }
        List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach),
                e -> e instanceof Enemy && !(e instanceof Player) && e.isAlive());
        if (found.isEmpty()) {
            return;
        }
        Map<ResourceKey<IAspect>, Integer> cost = cost(data);
        boolean free = cost.isEmpty() || player.isCreative();
        if (!free && !WandVisHelper.consumeSpecificFromHotbar(player, cost, true)) {
            return;
        }
        found.sort(Comparator.comparingDouble(player::distanceToSqr));
        for (LivingEntity target : found.subList(0, Math.min(targets, found.size()))) {
            target.hurtServer(level, level.damageSources().indirectMagic(player, player), Math.max(0.1F, damage));
            onHit(level, player, target, element, el);
            Vec3 from = player.position().add(0, 1.9, 0);
            Vec3 to = target.position().add(0, target.getBbHeight() / 2, 0);
            for (int i = 0; i <= 8; i++) {
                Vec3 p = from.lerp(to, i / 8.0);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
            after.accept(target);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.6F);
        if (!free && level.getRandom().nextInt(3) == 0) {
            WandVisHelper.consumeSpecificFromHotbar(player, cost, false);
        }
        COOLDOWNS.put(player.getUUID(), cooldown);
    }

    private static Map<ResourceKey<IAspect>, Integer> cost(FamiliarData data) {
        Map<ResourceKey<IAspect>, Integer> cost = new HashMap<>();
        data.augments().forEach((a, lvl) -> a.costPerLevel().forEach((k, v) -> cost.merge(k, v * lvl, Integer::sum)));
        return cost;
    }

    private static void onHit(ServerLevel level, Player player, LivingEntity target, Augment element, int lvl) {
        if (element == null) {
            return;
        }
        switch (element) {
            case SHOCK -> {
                if (level.getRandom().nextBoolean()) {
                    Vec3 away = target.position().subtract(player.position()).normalize().scale(0.4 * lvl);
                    target.push(away.x, Math.max(0, away.y) + 0.1, away.z);
                }
            }
            case FIRE -> {
                target.igniteForTicks(14 << lvl);
                if (lvl >= 3) {
                    target.addEffect(new MobEffectInstance(TTMobEffects.SUN_SCORNED, 1200, 2));
                }
            }
            case POISON -> {
                target.addEffect(new MobEffectInstance(MobEffects.POISON, 600, lvl >= 2 ? 1 : 0));
                if (lvl >= 3) {
                    target.addEffect(new MobEffectInstance(MobEffects.HUNGER, 2400, 3));
                }
            }
            case WEAKNESS -> {
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, lvl >= 3 ? 1 : 0));
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 600, lvl >= 3 ? 1 : 0));
            }
            default -> {
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        FamiliarData data = data(stack);
        data.aspect().ifPresent(k -> out.accept(Component.translatable("gadomancyawakened.familiar.aspect",
                Component.translatable("aspect." + k.identifier().getNamespace() + "." + k.identifier().getPath()))
                .withStyle(ChatFormatting.AQUA)));
        if (data.aspect().isEmpty()) {
            out.accept(Component.translatable("gadomancyawakened.familiar.unattuned").withStyle(ChatFormatting.GRAY));
        }
        data.sorted().forEach((a, lvl) -> out.accept(Component.translatable("gadomancyawakened.familiar.augment",
                Component.translatable("gadomancyawakened.augment." + a.getSerializedName()), Component.translatable("enchantment.level." + lvl))
                .withStyle(ChatFormatting.GOLD)));
    }

    static Holder<IAspect> holder(ServerLevel level, ResourceKey<IAspect> key) {
        return Aspects.resolve(level, key);
    }
}
