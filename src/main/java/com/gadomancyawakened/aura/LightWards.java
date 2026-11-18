package com.gadomancyawakened.aura;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

/**
 * Lux pylons: monster spawns within the ward's radius (64 in the shipped data) are refused. A ward lapses 16 ticks
 * after its last pulse, as the original cleared its list every 16 server ticks.
 */
public final class LightWards {
    private static final int LAPSE = 16;
    private static final Map<Ward, Long> WARDS = new HashMap<>();

    private record Ward(ResourceKey<Level> level, BlockPos pos, int range) {
    }

    private LightWards() {
    }

    static void mark(ServerLevel level, BlockPos pos, int range) {
        WARDS.put(new Ward(level.dimension(), pos.immutable(), range), level.getGameTime());
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(LightWards::onSpawn);
    }

    private static void onSpawn(FinalizeSpawnEvent event) {
        if (WARDS.isEmpty() || event.getEntity().getType().getCategory() != MobCategory.MONSTER
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        WARDS.values().removeIf(t -> now - t > LAPSE);
        for (Ward w : WARDS.keySet()) {
            if (w.level() == level.dimension() && w.pos().distToCenterSqr(event.getX(), event.getY(), event.getZ()) <= (double) w.range() * w.range()) {
                event.setSpawnCancelled(true);
                return;
            }
        }
    }
}
