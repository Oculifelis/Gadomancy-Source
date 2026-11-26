package com.gadomancyawakened;

import com.gadomancyawakened.machine.ArcanePackagerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Save/load round trip of the Arcane Packager cycle, run once the dev server has started when the JVM has
 * {@code -Dgadomancyawakened.selftest=true} (only the Gradle server run sets it). Logs one {@code SELFTEST PASS} or
 * {@code SELFTEST FAIL} line per check.
 */
final class SelfTest {
    private static final BlockPos POS = new BlockPos(0, 64, 0);

    private SelfTest() {
    }

    static void register() {
        if (Boolean.getBoolean("gadomancyawakened.selftest")) {
            NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> run(e.getServer().registryAccess()));
        }
    }

    private static void check(String name, boolean ok, String detail) {
        GadomancyAwakened.LOG.info("SELFTEST {} {}: {}", ok ? "PASS" : "FAIL", name, detail);
    }

    private static ArcanePackagerBlockEntity packager() {
        return new ArcanePackagerBlockEntity(POS, GDRegistry.ARCANE_PACKAGER.get().defaultBlockState());
    }

    private static void roundTrip(HolderLookup.Provider registries, String name, ArcanePackagerBlockEntity.Cycle c, int ticks,
            int settings, int fabrico, boolean powered) {
        ArcanePackagerBlockEntity be = packager();
        be.selfTestSet(c, ticks, settings, fabrico, powered);
        CompoundTag tag = be.saveWithoutMetadata(registries);
        ArcanePackagerBlockEntity loaded = packager();
        loaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
        check(name, loaded.cycle() == c && be.snapshot().equals(loaded.snapshot()), loaded.snapshot());
    }

    /** The shipped Essentia Compressor cost, or the value the test datapack in tools/selftest_datapack sets. */
    private static void formCost() {
        java.util.Map<?, Integer> cost = GDDataMaps.formCost(GDRegistry.ESSENTIA_COMPRESSOR.get());
        Integer ordo = cost.get(com.leclowndu93150.thaumaturge.api.aspect.TTAspects.ORDO);
        int pylon = GDDataMaps.formCost(GDRegistry.AURA_PYLON.get()).size();
        if (ordo != null && ordo == 77) {
            check("form_cost_override", cost.size() == 1, "compressor " + cost + " pylon aspects=" + pylon);
        } else {
            check("form_cost_default", ordo != null && ordo == 20 && cost.size() == 5 && pylon == 6,
                    "compressor " + cost + " pylon aspects=" + pylon + " (test pack not loaded)");
        }
    }

    private static void run(HolderLookup.Provider registries) {
        try {
            roundTrip(registries, "packager_roundtrip_idle", ArcanePackagerBlockEntity.Cycle.IDLE, 0, 0, 0, false);
            roundTrip(registries, "packager_roundtrip_packing", ArcanePackagerBlockEntity.Cycle.PACKING, 31,
                    ArcanePackagerBlockEntity.AUTO_START | ArcanePackagerBlockEntity.DISGUISE, 6, true);
            ArcanePackagerBlockEntity be = packager();
            be.selfTestSet(ArcanePackagerBlockEntity.Cycle.IDLE, 0, 0, 0, false);
            String before = be.snapshot();
            be.selfTestMenuWrite(999);
            check("packager_menu_write_ignored", before.equals(be.snapshot()) && be.cycle() == ArcanePackagerBlockEntity.Cycle.IDLE,
                    be.snapshot());
            formCost();
        } catch (Exception ex) {
            GadomancyAwakened.LOG.error("SELFTEST FAIL crashed", ex);
        }
    }
}
