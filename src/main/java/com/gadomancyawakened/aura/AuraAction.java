package com.gadomancyawakened.aura;

import com.leclowndu93150.thaumaturge.api.capability.KnowledgeType;
import com.leclowndu93150.thaumaturge.api.research.scan.ScanningManager;
import com.leclowndu93150.thaumaturge.content.wands.ItemWand;
import com.leclowndu93150.thaumaturge.content.wands.WandVisHelper;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

/** One thing an aura pulse does. The {@code type} field picks the kind; the rest are that kind's settings. */
public interface AuraAction {
    Codec<AuraAction> CODEC = Kind.CODEC.dispatch("type", AuraAction::kind, Kind::codec);

    Kind kind();

    void apply(ServerLevel level, BlockPos centre, int range, AABB box);

    enum Kind implements StringRepresentable {
        MOB_EFFECT(MobEffectAction.CODEC),
        AGE_UP(AgeUp.CODEC),
        REGROW_WOOL(RegrowWool.CODEC),
        DENY_MONSTER_SPAWNS(DenySpawns.CODEC),
        LIGHTNING(Lightning.CODEC),
        FREEZE_WATER(FreezeWater.CODEC),
        RANDOM_TICK(RandomTick.CODEC),
        SPAWN(Spawn.CODEC),
        SET_FIRE(SetFire.CODEC),
        WAND_VIS(WandVis.CODEC),
        KNOWLEDGE(Knowledge.CODEC);

        static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);
        private final MapCodec<? extends AuraAction> codec;

        Kind(MapCodec<? extends AuraAction> codec) {
            this.codec = codec;
        }

        MapCodec<? extends AuraAction> codec() {
            return codec;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Who a mob effect lands on. */
    enum Targets implements StringRepresentable {
        LIVING, PLAYERS, UNDEAD;

        static final Codec<Targets> CODEC = StringRepresentable.fromEnum(Targets::values);

        boolean accepts(LivingEntity e) {
            return switch (this) {
                case LIVING -> true;
                case PLAYERS -> e instanceof Player;
                case UNDEAD -> e.typeHolder().is(EntityTypeTags.UNDEAD);
            };
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * The original's addOrExtendPotionEffect: when the effect has at least {@code add} ticks of room below {@code cap},
     * its duration grows by {@code add} at the given amplifier.
     */
    record MobEffectAction(Holder<MobEffect> effect, int amplifier, int add, int cap, Targets targets, boolean ambient)
            implements AuraAction {
        static final MapCodec<MobEffectAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BuiltInRegistries.MOB_EFFECT.holderByNameCodec().fieldOf("effect").forGetter(MobEffectAction::effect),
                Codec.intRange(0, 255).optionalFieldOf("amplifier", 0).forGetter(MobEffectAction::amplifier),
                Codec.intRange(1, 72000).fieldOf("add").forGetter(MobEffectAction::add),
                Codec.intRange(1, 720000).fieldOf("cap").forGetter(MobEffectAction::cap),
                Targets.CODEC.optionalFieldOf("targets", Targets.LIVING).forGetter(MobEffectAction::targets),
                Codec.BOOL.optionalFieldOf("ambient", true).forGetter(MobEffectAction::ambient)
        ).apply(i, MobEffectAction::new));

        @Override
        public Kind kind() {
            return Kind.MOB_EFFECT;
        }

        @Override
        public void apply(ServerLevel level, BlockPos centre, int range, AABB box) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, targets::accepts)) {
                MobEffectInstance now = e.getEffect(effect);
                int current = now == null ? 0 : now.getDuration();
                if (cap - current >= add) {
                    e.addEffect(new MobEffectInstance(effect, current + add, amplifier, ambient, true));
                }
            }
        }
    }

    record AgeUp(float chance) implements AuraAction {
        static final MapCodec<AgeUp> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.floatRange(0, 1).fieldOf("chance").forGetter(AgeUp::chance)).apply(i, AgeUp::new));

        @Override
        public Kind kind() {
            return Kind.AGE_UP;
        }

        @Override
        public void apply(ServerLevel level, BlockPos centre, int range, AABB box) {
            for (AgeableMob mob : level.getEntitiesOfClass(AgeableMob.class, box, AgeableMob::isBaby)) {
                if (level.getRandom().nextFloat() < chance) {
                    mob.setAge(0);
                }
            }
        }
    }

    record RegrowWool(float chance) implements AuraAction {
        static final MapCodec<RegrowWool> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.floatRange(0, 1).fieldOf("chance").forGetter(RegrowWool::chance)).apply(i, RegrowWool::new));

        @Override
        public Kind kind() {
            return Kind.REGROW_WOOL;
        }

        @Override
        public void apply(ServerLevel level, BlockPos centre, int range, AABB box) {
            for (Sheep sheep : level.getEntitiesOfClass(Sheep.class, box, Sheep::isSheared)) {
                if (level.getRandom().nextFloat() < chance) {
                    sheep.setSheared(false);
                }
            }
        }
    }

    /** Marks the pylon as a light ward: no monster spawns within {@code range} blocks while it keeps pulsing. */
    record DenySpawns() implements AuraAction {
        static final MapCodec<DenySpawns> CODEC = MapCodec.unit(new DenySpawns());

        @Override
        public Kind kind() {
            return Kind.DENY_MONSTER_SPAWNS;
        }

        @Override
        public void apply(ServerLevel level, BlockPos centre, int range, AABB box) {
            LightWards.mark(level, centre, range);
        }
    }

    record Lightning(float chance) implements AuraAction {
        static final MapCodec<Lightning> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.floatRange(0, 1).fieldOf("chance").forGetter(Lightning::chance)).apply(i, Lightning::new));

        @Override
        public Kind kind() {
            return Kind.LIGHTNING;
        }

        @Override
        public void apply(ServerLevel level, BlockPos centre, int range, AABB box) {
            RandomSource r = level.getRandom();
            if (r.nextFloat() >= chance) {
                return;
            }
            BlockPos column = centre.offset(r.nextInt(range) - r.nextInt(range), 0, r.nextInt(range) - r.nextInt(range));
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
            if (bolt != null) {
                bolt.snapTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5);
                level.addFreshEntity(bolt);
            }
        }
    }

    record FreezeWater(int min, int max) implements AuraAction {
        static final MapCodec<FreezeWater> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.intRange(0, 400).fieldOf("min").forGetter(FreezeWater::min),
                Codec.intRange(0, 400).fieldOf("max").forGetter(FreezeWater::max)).apply(i, FreezeWater::new));

        @Override
        public Kind kind() {
            return Kind.FREEZE_WATER;
        }

        @Override
        public void apply(ServerLevel level, BlockPos centre, int range, AABB box) {
            int n = min + level.getRandom().nextInt(Math.max(1, max - min + 1));
            for (int i = 0; i < n; i++) {
                BlockPos p = AuraAction.randomSpot(level.getRandom(), centre, range);
                BlockState s = level.getBlockState(p);
                if (s.is(Blocks.WATER) && s.getFluidState().isSourceOfType(Fluids.WATER)) {
                    level.setBlockAndUpdate(p, Blocks.ICE.defaultBlockState());
                }
            }
        }
    }

    /** Gives {@code count} random spots a random tick each pulse: crops, saplings and the like grow faster. */
    record RandomTick(int count) implements AuraAction {
        static final MapCodec<RandomTick> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.intRange(1, 1000).fieldOf("count").forGetter(RandomTick::count)).apply(i, RandomTick::new));

        @Override
        public Kind kind() {
            return Kind.RANDOM_TICK;
        }

        @Override
        public void apply(ServerLevel level, BlockPos centre, int range, AABB box) {
            for (int i = 0; i < count; i++) {
                BlockPos p = AuraAction.randomSpot(level.getRandom(), centre, range);
                BlockState s = level.getBlockState(p);
                if (s.isRandomlyTicking()) {
                    s.randomTick(level, p, level.getRandom());
                }
            }
        }
    }

    record Spawn(List<EntityType<?>> entities, float chance, boolean hostile, boolean invisible) implements AuraAction {
        static final MapCodec<Spawn> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BuiltInRegistries.ENTITY_TYPE.byNameCodec().listOf().fieldOf("entities").forGetter(Spawn::entities),
                Codec.floatRange(0, 1).fieldOf("chance").forGetter(Spawn::chance),
                Codec.BOOL.optionalFieldOf("hostile", false).forGetter(Spawn::hostile),
                Codec.BOOL.optionalFieldOf("invisible", false).forGetter(Spawn::invisible)).apply(i, Spawn::new));

        @Override
        public Kind kind() {
            return Kind.SPAWN;
        }

        @Override
        public void apply(ServerLevel level, BlockPos centre, int range, AABB box) {
            RandomSource r = level.getRandom();
            if (entities.isEmpty() || r.nextFloat() >= chance || hostile && level.getDifficulty() == Difficulty.PEACEFUL) {
                return;
            }
            BlockPos p = AuraAction.randomSpot(r, centre, range);
            if (!level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir() || !level.getBlockState(p.below()).isSolid()) {
                return;
            }
            Entity e = entities.get(r.nextInt(entities.size())).spawn(level, p, EntitySpawnReason.MOB_SUMMONED);
            if (e instanceof Mob mob && invisible) {
                mob.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY, 20 * 60 * 60 * 24 * 365, 1));
            }
        }
    }

    record SetFire(float seconds) implements AuraAction {
        static final MapCodec<SetFire> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.floatRange(0, 600).fieldOf("seconds").forGetter(SetFire::seconds)).apply(i, SetFire::new));

        @Override
        public Kind kind() {
            return Kind.SET_FIRE;
        }

        @Override
        public void apply(ServerLevel level, BlockPos centre, int range, AABB box) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> !e.fireImmune())) {
                e.igniteForSeconds(seconds);
            }
        }
    }

    /** Players holding a wand get this much vis of every primal it has room for (Auram). */
    record WandVis(int amount) implements AuraAction {
        static final MapCodec<WandVis> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.intRange(1, 1000).fieldOf("amount").forGetter(WandVis::amount)).apply(i, WandVis::new));

        @Override
        public Kind kind() {
            return Kind.WAND_VIS;
        }

        @Override
        public void apply(ServerLevel level, BlockPos centre, int range, AABB box) {
            for (Player p : level.getEntitiesOfClass(Player.class, box)) {
                ItemStack wand = p.getMainHandItem();
                if (wand.getItem() instanceof ItemWand) {
                    for (var aspect : com.gadomancyawakened.node.NodeManipulatorBlockEntity.PRIMALS) {
                        WandVisHelper.topUpCentivis(wand, aspect, amount, false);
                    }
                }
            }
        }
    }

    /** Players nearby now and then gain a point of observation in a research category (Cognitio). */
    record Knowledge(float chance, Identifier category) implements AuraAction {
        static final MapCodec<Knowledge> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.floatRange(0, 1).fieldOf("chance").forGetter(Knowledge::chance),
                Identifier.CODEC.fieldOf("category").forGetter(Knowledge::category)).apply(i, Knowledge::new));

        @Override
        public Kind kind() {
            return Kind.KNOWLEDGE;
        }

        @Override
        public void apply(ServerLevel level, BlockPos centre, int range, AABB box) {
            for (Player p : level.getEntitiesOfClass(Player.class, box)) {
                if (level.getRandom().nextFloat() < chance) {
                    ScanningManager.addKnowledge(p, KnowledgeType.OBSERVATION, category, 1);
                }
            }
        }
    }

    static BlockPos randomSpot(RandomSource r, BlockPos centre, int range) {
        return centre.offset(r.nextInt(range) - r.nextInt(range), r.nextInt(range) - r.nextInt(range), r.nextInt(range) - r.nextInt(range));
    }

    @SuppressWarnings("unused")
    private static boolean isMonster(Entity e) {
        return e instanceof Enemy;
    }
}
