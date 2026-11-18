package com.gadomancyawakened.machine;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * The fields of loaded emitters. Inside one, creepers cannot swell, explosions break no blocks (they still hurt), and
 * mobs other than players and bosses cannot teleport. The original stretched entity boxes by mistake when testing;
 * this tests the entity's own box.
 */
public final class Protectors {
    private static final Set<BlockProtectorBlockEntity> LOADED = Collections.newSetFromMap(new WeakHashMap<>());

    private Protectors() {
    }

    static void track(BlockProtectorBlockEntity be) {
        LOADED.add(be);
    }

    static void untrack(BlockProtectorBlockEntity be) {
        LOADED.remove(be);
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(Protectors::onExplosion);
        NeoForge.EVENT_BUS.addListener(Protectors::onTeleport);
        NeoForge.EVENT_BUS.addListener(Protectors::onEntityTick);
    }

    public static boolean protects(Level level, AABB box) {
        for (BlockProtectorBlockEntity be : LOADED) {
            AABB field = be.field();
            if (be.getLevel() == level && !be.isRemoved() && field != null && field.intersects(box)) {
                return true;
            }
        }
        return false;
    }

    private static void onExplosion(ExplosionEvent.Detonate event) {
        Entity source = event.getExplosion().getDirectSourceEntity();
        Vec3 c = event.getExplosion().center();
        AABB at = source != null ? source.getBoundingBox() : new AABB(c, c);
        if (protects(event.getLevel(), at)) {
            event.getAffectedBlocks().clear();
        }
    }

    private static void onTeleport(EntityTeleportEvent.EnderEntity event) {
        LivingEntity mob = event.getEntityLiving();
        if (!(mob instanceof Player) && !mob.typeHolder().is(Tags.EntityTypes.BOSSES) && protects(mob.level(), mob.getBoundingBox())) {
            event.setCanceled(true);
        }
    }

    private static void onEntityTick(EntityTickEvent.Pre event) {
        if (!LOADED.isEmpty() && event.getEntity() instanceof Creeper creeper && !creeper.level().isClientSide()
                && protects(creeper.level(), creeper.getBoundingBox())) {
            creeper.setSwellDir(-1);
        }
    }
}
