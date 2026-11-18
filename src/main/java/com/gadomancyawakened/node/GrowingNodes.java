package com.gadomancyawakened.node;

import com.gadomancyawakened.GadomancyAwakened;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aura.AuraHelper;
import com.leclowndu93150.thaumaturge.content.aura.node.BlockEntityNode;
import com.leclowndu93150.thaumaturge.content.aura.node.NodeData;
import com.leclowndu93150.thaumaturge.registry.TTDataComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Growing nodes. The state rides on Thaumaturge's node block entity as a data attachment. A growing node pulls vis
 * crystals from 15 blocks round and eats them: it refills what it has lost, then grows a permanent point of that
 * aspect, unless it is saturated with it. Feeding the same aspect over and over makes it unhappy; an unhappy node
 * zaps creatures within 6 blocks and, past the cap, bursts (explosion and flux) and loses much of what it had. It also
 * feeds on smaller nodes within 8 blocks, one point every 3 ticks. Constants from Gadomancy 1.5.17
 * (GrowingNodeBehavior); the drawn-out vortex explosion is shortened to a single burst.
 */
public final class GrowingNodes {
    private static final float SATURATION_CAP = 110;
    private static final float HAPPINESS_CAP = 400;

    public record State(Map<String, Float> saturation, float unhappiness, Optional<String> last, int row, boolean saturated,
                        Optional<BlockPos> prey) {
        static final State NEW = new State(Map.of(), 0, Optional.empty(), 0, false, Optional.empty());
        static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.unboundedMap(Codec.STRING, Codec.FLOAT).fieldOf("saturation").forGetter(State::saturation),
                Codec.FLOAT.fieldOf("unhappiness").forGetter(State::unhappiness),
                Codec.STRING.optionalFieldOf("last").forGetter(State::last),
                Codec.INT.fieldOf("row").forGetter(State::row),
                Codec.BOOL.fieldOf("saturated").forGetter(State::saturated),
                BlockPos.CODEC.optionalFieldOf("prey").forGetter(State::prey)
        ).apply(i, State::new));
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, GadomancyAwakened.MODID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<State>> GROWING = ATTACHMENTS.register("growing_node",
            () -> AttachmentType.builder(() -> State.NEW).serialize(State.CODEC.fieldOf("state")).build());

    private static final Set<BlockEntityNode> LOADED = Collections.newSetFromMap(new WeakHashMap<>());

    private GrowingNodes() {
    }

    public static void register(IEventBus bus) {
        ATTACHMENTS.register(bus);
        NeoForge.EVENT_BUS.addListener(GrowingNodes::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(GrowingNodes::onLevelTick);
    }

    public static boolean isGrowing(BlockEntityNode node) {
        return node.hasData(GROWING);
    }

    public static void makeGrowing(BlockEntityNode node) {
        if (!node.hasData(GROWING)) {
            node.setData(GROWING, State.NEW);
            node.setChanged();
        }
        LOADED.add(node);
    }

    private static void onChunkLoad(ChunkEvent.Load event) {
        for (BlockEntity be : event.getChunk().getBlockEntities().values()) {
            if (be instanceof BlockEntityNode node && node.hasData(GROWING)) {
                LOADED.add(node);
            }
        }
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || LOADED.isEmpty()) {
            return;
        }
        for (BlockEntityNode node : new ArrayList<>(LOADED)) {
            if (node.isRemoved() || node.getLevel() != level) {
                if (node.isRemoved()) {
                    LOADED.remove(node);
                }
                continue;
            }
            tick(level, node);
        }
    }

    private static String key(Holder<IAspect> a) {
        return a.unwrapKey().map(k -> k.identifier().toString()).orElse("?");
    }

    private static void tick(ServerLevel level, BlockEntityNode node) {
        BlockPos pos = node.getBlockPos();
        Vec3 centre = Vec3.atCenterOf(pos);
        long t = level.getGameTime();
        // Vortex: draw in vis crystals and eat those that arrive.
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(15),
                e -> e.getItem().has(TTDataComponents.CRYSTAL_ASPECT.get()))) {
            Vec3 to = centre.subtract(item.position());
            double d = to.length() / 15;
            if (to.length() < 0.5) {
                AspectInstance a = item.getItem().get(TTDataComponents.CRYSTAL_ASPECT.get());
                item.getItem().shrink(1);
                if (item.getItem().isEmpty()) {
                    item.discard();
                }
                feed(level, node, a.aspect(), 1.4F);
            } else if (d < 1) {
                double f = (1 - d) * (1 - d);
                item.setDeltaMovement(item.getDeltaMovement().add(to.normalize().scale(f * 0.1)));
                item.hurtMarked = true;
            }
        }
        State s = node.getData(GROWING);
        if (t % 8 == 0 && level.getDifficulty() != Difficulty.PEACEFUL && s.unhappiness() > 0) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(6),
                    e -> !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
                if (s.unhappiness() > level.getRandom().nextInt(160000)) {
                    float frac = s.unhappiness() / HAPPINESS_CAP;
                    float dmg = frac <= 0.3F ? 2 : frac <= 0.6F ? 5 : frac <= 0.9F ? 8 : 12;
                    e.hurtServer(level, level.damageSources().magic(), dmg);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + 1, e.getZ(), 6, 0.2, 0.4, 0.2, 0.1);
                }
            }
        }
        hunt(level, node, s, t);
    }

    private static void hunt(ServerLevel level, BlockEntityNode node, State s, long t) {
        BlockPos pos = node.getBlockPos();
        if (s.prey().isEmpty()) {
            BlockPos p = pos.offset(level.getRandom().nextInt(8) - level.getRandom().nextInt(8),
                    level.getRandom().nextInt(8) - level.getRandom().nextInt(8), level.getRandom().nextInt(8) - level.getRandom().nextInt(8));
            if (!p.equals(pos) && level.getBlockEntity(p) instanceof BlockEntityNode prey
                    && prey.capacity().totalAmount() <= node.capacity().totalAmount()) {
                node.setData(GROWING, new State(s.saturation(), s.unhappiness(), s.last(), s.row(), s.saturated(), Optional.of(p)));
            }
            return;
        }
        if (t % 3 != 0) {
            return;
        }
        BlockPos at = s.prey().get();
        if (!(level.getBlockEntity(at) instanceof BlockEntityNode prey) || prey.capacity().isEmpty()) {
            node.setData(GROWING, new State(s.saturation(), s.unhappiness(), s.last(), s.row(), s.saturated(), Optional.empty()));
            if (level.getBlockEntity(at) instanceof BlockEntityNode) {
                level.removeBlock(at, false);
            }
            return;
        }
        var entries = prey.capacity().entries();
        Holder<IAspect> bite = entries.get(level.getRandom().nextInt(entries.size())).aspect();
        prey.applyNodeData(new NodeData(prey.kind(), Optional.ofNullable(prey.trait()),
                prey.getAspects().reduce(bite, 1), prey.capacity().reduce(bite, 1)));
        prey.invalidateRefill();
        Vec3 from = Vec3.atCenterOf(at);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y, from.z, 4, 0.1, 0.1, 0.1, 0.05);
        feed(level, node, bite, 1.0F);
    }

    /** One point of an aspect reaches the node: refill, then maybe grow, as the original's accept check allows. */
    private static void feed(ServerLevel level, BlockEntityNode node, Holder<IAspect> aspect, float weight) {
        State s = node.getData(GROWING);
        String k = key(aspect);
        AspectList now = node.getAspects();
        AspectList base = node.capacity();
        if (now.amountOf(aspect) < base.amountOf(aspect)) {
            set(node, now.add(aspect, 1), base);
            return;
        }
        if (s.saturated()) {
            return;
        }
        Map<String, Float> sat = new HashMap<>(s.saturation());
        float mine = sat.getOrDefault(k, 0F);
        float unhappy = s.unhappiness();
        int row = s.last().map(k::equals).orElse(false) ? s.row() + 1 : 1;
        boolean known = base.amountOf(aspect) > 0;
        if (known) {
            if (level.getRandom().nextFloat() >= 1 - mine / SATURATION_CAP) {
                return;
            }
            float u = 1 / Math.max(0.01F, 1 - mine / SATURATION_CAP);
            unhappy += row > 1 ? u * (float) (Math.log(row) / 6 + 1) : -10 * u;
            unhappy = Math.max(0, unhappy);
        } else {
            unhappy /= 15;
        }
        float add = (aspect.value().isPrimal() ? 1.4F : 1.0F) * weight;
        sat.replaceAll((key, v) -> Math.max(0, v - 1 / 15F));
        sat.put(k, mine + add);
        set(node, now.add(aspect, 1), base.add(aspect, 1));
        boolean saturated = sat.values().stream().mapToDouble(Float::doubleValue).average().orElse(0) > SATURATION_CAP * 0.85;
        if (unhappy > HAPPINESS_CAP) {
            unhappy /= 10;
            burst(level, node);
        }
        node.setData(GROWING, new State(Map.copyOf(sat), unhappy, Optional.of(k), row, saturated, s.prey()));
        node.setChanged();
    }

    /** The original's vortex explosion, shortened: a blast, flux, and the node keeps about half of everything. */
    private static void burst(ServerLevel level, BlockEntityNode node) {
        BlockPos pos = node.getBlockPos();
        level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3.0F, Level.ExplosionInteraction.BLOCK);
        AuraHelper.addFlux(level, pos, 20.0F);
        AspectList base = AspectList.EMPTY;
        AspectList now = AspectList.EMPTY;
        for (AspectInstance a : node.capacity().entries()) {
            if (level.getRandom().nextInt(4) != 0) {
                base = base.add(a.aspect(), Math.max(1, a.amount() / 2));
                now = now.add(a.aspect(), Math.max(1, node.getAspects().amountOf(a.aspect()) / 2));
            }
        }
        set(node, now, base);
    }

    private static void set(BlockEntityNode node, AspectList now, AspectList base) {
        node.applyNodeData(new NodeData(node.kind(), Optional.ofNullable(node.trait()), now, base));
        node.invalidateRefill();
    }

    @SuppressWarnings("unused")
    private static ResourceKey<IAspect> keyOf(Holder<IAspect> h) {
        return h.unwrapKey().orElseThrow();
    }
}
