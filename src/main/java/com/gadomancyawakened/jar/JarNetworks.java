package com.gadomancyawakened.jar;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.content.essentia.jar.AbstractBlockEntityJar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Remote jar networks, as in the original: linked jars re-register themselves while loaded, and every 3 server ticks
 * each network moves one essentia from its fullest jar to its emptiest (when the emptiest can take that aspect and they
 * differ by more than one). Jars therefore even out at about 6.7 essentia a second per network, across any distance or
 * dimension, while their chunks are loaded. The network map is not saved: the jars themselves carry their network id.
 */
public final class JarNetworks {
    private static final int PERIOD = 3;
    private static final Map<UUID, Set<AbstractBlockEntityJar>> NETWORKS = new HashMap<>();

    private JarNetworks() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(JarNetworks::onServerTick);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> NETWORKS.clear());
    }

    static void join(UUID network, AbstractBlockEntityJar jar) {
        NETWORKS.computeIfAbsent(network, k -> new LinkedHashSet<>()).add(jar);
    }

    static void leave(UUID network, AbstractBlockEntityJar jar) {
        Set<AbstractBlockEntityJar> jars = NETWORKS.get(network);
        if (jars != null) {
            jars.remove(jar);
            if (jars.isEmpty()) {
                NETWORKS.remove(network);
            }
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % PERIOD != 0) {
            return;
        }
        for (Iterator<Set<AbstractBlockEntityJar>> it = NETWORKS.values().iterator(); it.hasNext(); ) {
            Set<AbstractBlockEntityJar> jars = it.next();
            jars.removeIf(jar -> jar.isRemoved() || jar.getLevel() == null || !jar.getLevel().isLoaded(jar.getBlockPos()));
            if (jars.isEmpty()) {
                it.remove();
                continue;
            }
            equalise(jars);
        }
    }

    private static void equalise(Set<AbstractBlockEntityJar> jars) {
        AbstractBlockEntityJar low = null;
        AbstractBlockEntityJar high = null;
        for (AbstractBlockEntityJar jar : jars) {
            if (low == null || jar.amount() < low.amount()) {
                low = jar;
            }
            if (high == null || jar.amount() > high.amount()) {
                high = jar;
            }
        }
        if (low == null || low == high || low.amount() + 1 >= high.amount()) {
            return;
        }
        Holder<IAspect> aspect = high.getEssentiaType(Direction.UP);
        if (aspect == null || !low.accepts(aspect)) {
            return;
        }
        if (high.takeEssentia(aspect, 1, Direction.UP) == 1 && low.fill(aspect, 1) < 1) {
            high.fill(aspect, 1); // the low jar refused after all: put it back
        }
    }
}
