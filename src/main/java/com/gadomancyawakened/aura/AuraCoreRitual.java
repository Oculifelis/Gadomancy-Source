package com.gadomancyawakened.aura;

import com.gadomancyawakened.GDComponents;
import com.gadomancyawakened.GDDataMaps;
import com.gadomancyawakened.GDRegistry;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.research.scan.ScanningManager;
import com.leclowndu93150.thaumaturge.content.wands.ItemWand;
import com.leclowndu93150.thaumaturge.content.wands.WandVisHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Attuning an aura core. Drop a blank core within 10 blocks of a vis crystal cluster and strike the cluster with a
 * wand: the wand pays the cluster's cost, the cluster bursts, and over the next 500 ticks the core reads every block
 * in the 9x9x9 cube round it, adding up their aspects broken down to primals. It needs at least 109 blocks with
 * aspects; then one primal at or above the average is chosen, weighted by its total, and the core takes it. Clusters
 * and their starting weights and costs come from the {@code gadomancyawakened:aura_core_source} block data map.
 * Numbers from Gadomancy 1.5.17.
 */
public final class AuraCoreRitual {
    private static final int RANGE = 4;
    private static final int GATHER_START = 50;
    private static final int GATHER_END = 500;
    private static final int REQUIRED = Math.round(729 * 0.15F);

    private static final List<Ritual> RUNNING = new ArrayList<>();

    private static final class Ritual {
        final ServerLevel level;
        final ItemEntity core;
        final BlockPos centre;
        final List<BlockPos> cells;
        final Map<Holder<IAspect>, Integer> totals = new HashMap<>();
        int blocks;
        int age;

        Ritual(ServerLevel level, ItemEntity core, Map<Holder<IAspect>, Integer> start) {
            this.level = level;
            this.core = core;
            this.centre = core.blockPosition();
            this.totals.putAll(start);
            List<BlockPos> all = new ArrayList<>();
            BlockPos.betweenClosed(centre.offset(-RANGE, -RANGE, -RANGE), centre.offset(RANGE, RANGE, RANGE)).forEach(p -> all.add(p.immutable()));
            Collections.shuffle(all, new Random(centre.asLong()));
            this.cells = all;
        }
    }

    private AuraCoreRitual() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(AuraCoreRitual::onUse);
        NeoForge.EVENT_BUS.addListener(AuraCoreRitual::onTick);
    }

    private static void onUse(PlayerInteractEvent.RightClickBlock event) {
        ItemStack wand = event.getItemStack();
        Level level = event.getLevel();
        BlockState state = level.getBlockState(event.getPos());
        GDDataMaps.CoreSource source = state.typeHolder().getData(GDDataMaps.AURA_CORE_SOURCE);
        if (source == null || !(wand.getItem() instanceof ItemWand)) {
            return;
        }
        List<ItemEntity> cores = level.getEntitiesOfClass(ItemEntity.class, new AABB(event.getPos()).inflate(10),
                e -> e.getItem().is(GDRegistry.AURA_CORE.get()) && !e.getItem().has(GDComponents.CORE_ASPECT.get())
                        && RUNNING.stream().noneMatch(r -> r.core == e));
        if (cores.isEmpty()) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (!WandVisHelper.payCosts(wand, event.getEntity(), source.cost(), false, false)) {
            return;
        }
        BlockPos at = event.getPos();
        level.destroyBlock(at, false);
        level.explode(null, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 1.5F, Level.ExplosionInteraction.NONE);
        ItemEntity core = cores.get(0);
        core.setUnlimitedLifetime();
        core.setNeverPickUp();
        Map<Holder<IAspect>, Integer> start = new HashMap<>();
        source.weights().forEach((k, v) -> start.put(server.registryAccess().lookupOrThrow(IAspect.REGISTRY_KEY).getOrThrow(k), v));
        RUNNING.add(new Ritual(server, core, start));
    }

    private static void onTick(ServerTickEvent.Post event) {
        for (Iterator<Ritual> it = RUNNING.iterator(); it.hasNext(); ) {
            Ritual r = it.next();
            if (!r.core.isAlive()) {
                it.remove();
                continue;
            }
            r.age++;
            if (r.age % 5 == 0) {
                r.level.sendParticles(ParticleTypes.ENCHANT, r.core.getX(), r.core.getY() + 0.5, r.core.getZ(), 6, 0.6, 0.6, 0.6, 0.4);
            }
            if (r.age > GATHER_START && r.age <= GATHER_END) {
                int from = (r.age - GATHER_START - 1) * r.cells.size() / (GATHER_END - GATHER_START);
                int to = (r.age - GATHER_START) * r.cells.size() / (GATHER_END - GATHER_START);
                for (int i = from; i < to; i++) {
                    gather(r, r.cells.get(i));
                }
            }
            if (r.age > GATHER_END) {
                finish(r);
                it.remove();
            }
        }
    }

    private static void gather(Ritual r, BlockPos p) {
        BlockState s = r.level.getBlockState(p);
        if (s.isAir()) {
            return;
        }
        AspectList aspects = ScanningManager.itemAspects(new ItemStack(s.getBlock()));
        if (aspects.isEmpty()) {
            return;
        }
        r.blocks++;
        for (AspectInstance a : aspects.entries()) {
            addPrimals(r.totals, a.aspect(), a.amount(), 0);
        }
    }

    private static void addPrimals(Map<Holder<IAspect>, Integer> into, Holder<IAspect> aspect, int amount, int depth) {
        if (aspect.value().isPrimal() || depth > 8) {
            into.merge(aspect, amount, Integer::sum);
            return;
        }
        for (Holder<IAspect> part : aspect.value().components()) {
            addPrimals(into, part, amount, depth + 1);
        }
    }

    private static void finish(Ritual r) {
        ItemStack result = r.core.getItem().copy();
        if (r.blocks >= REQUIRED && !r.totals.isEmpty()) {
            double avg = r.totals.values().stream().mapToInt(Integer::intValue).average().orElse(0);
            List<Map.Entry<Holder<IAspect>, Integer>> pool = r.totals.entrySet().stream().filter(e -> e.getValue() >= avg).toList();
            int total = pool.stream().mapToInt(Map.Entry::getValue).sum();
            int roll = r.level.getRandom().nextInt(Math.max(1, total));
            for (Map.Entry<Holder<IAspect>, Integer> e : pool) {
                roll -= e.getValue();
                if (roll < 0) {
                    ResourceKey<IAspect> key = e.getKey().unwrapKey().orElseThrow();
                    result.set(GDComponents.CORE_ASPECT.get(), key);
                    break;
                }
            }
            r.level.sendParticles(ParticleTypes.END_ROD, r.core.getX(), r.core.getY() + 0.5, r.core.getZ(), 30, 0.4, 0.4, 0.4, 0.1);
        } else {
            r.level.sendParticles(ParticleTypes.LARGE_SMOKE, r.core.getX(), r.core.getY() + 0.5, r.core.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
        }
        r.core.discard();
        ItemEntity out = new ItemEntity(r.level, r.core.getX(), r.core.getY(), r.core.getZ(), result);
        r.level.addFreshEntity(out);
    }
}
