package com.gadomancyawakened.golem;

import com.gadomancyawakened.GadomancyAwakened;
import com.leclowndu93150.thaumaturge.api.golems.IGolemAPI;
import com.leclowndu93150.thaumaturge.api.golems.accessory.GolemAccessoryBehavior;
import com.leclowndu93150.thaumaturge.api.golems.accessory.GolemAccessoryContext;
import com.leclowndu93150.thaumaturge.content.golem.EntityThaumaturgeGolem;
import com.mojang.serialization.Codec;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/** Server behaviour of Gadomancy's two golem accessories. */
public final class GolemAccessoryBehaviors {
    private GolemAccessoryBehaviors() {
    }

    /**
     * Runic Shielding (the original's runic shield upgrade): a recharging shield that soaks up damage before health.
     * It is vanilla absorption, raised by 0.02 points a tick (one point every 2.5 s) up to the golem's limit: its full
     * max health for a silverwood golem, max health / 2.6 for any other (the original's numbers).
     */
    public static final class RunicShield implements GolemAccessoryBehavior<Boolean> {
        static final Identifier MODIFIER = GadomancyAwakened.id("runic_shielding");
        private static final float RECHARGE_PER_TICK = 0.02F;
        private static final float OTHER_MATERIAL_DIVISOR = 2.6F;

        @Override
        public Codec<Boolean> stateCodec() {
            return Codec.BOOL;
        }

        @Override
        public Boolean initialState() {
            return Boolean.TRUE;
        }

        static float limit(IGolemAPI golem) {
            LivingEntity entity = golem.asEntity();
            boolean silverwood = golem.properties().material() == GDGolems.SILVERWOOD.get();
            return silverwood ? entity.getMaxHealth() : (float) Math.floor(entity.getMaxHealth() / OTHER_MATERIAL_DIVISOR);
        }

        @Override
        public Boolean onAttach(GolemAccessoryContext context, Boolean state, ItemStack attachedStack) {
            setCap(context.golem().asEntity(), limit(context.golem()));
            return state;
        }

        @Override
        public void onRemove(GolemAccessoryContext context, Boolean state, ItemStack returnedStack) {
            LivingEntity entity = context.golem().asEntity();
            AttributeInstance cap = entity.getAttribute(Attributes.MAX_ABSORPTION);
            if (cap != null) {
                cap.removeModifier(MODIFIER);
            }
            entity.setAbsorptionAmount(0.0F);
        }

        @Override
        public Boolean serverTick(GolemAccessoryContext context, Boolean state) {
            LivingEntity entity = context.golem().asEntity();
            float limit = limit(context.golem());
            setCap(entity, limit); // the golem's health can change with its parts and rank
            float now = entity.getAbsorptionAmount();
            if (now < limit) {
                entity.setAbsorptionAmount(Math.min(limit, now + RECHARGE_PER_TICK));
            }
            return state;
        }

        private static void setCap(LivingEntity entity, float limit) {
            AttributeInstance cap = entity.getAttribute(Attributes.MAX_ABSORPTION);
            if (cap == null) {
                return;
            }
            AttributeModifier current = cap.getModifier(MODIFIER);
            if (current == null || current.amount() != limit) {
                cap.addOrUpdateTransientModifier(new AttributeModifier(MODIFIER, limit, AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    /**
     * Bodyguard (the original's bodyguard core): the golem trails its owner wherever they go, with no home to return to,
     * blinks back to them when left far behind, turns on anything that targets them, and can be told to sit and stay
     * (sneak and use an empty hand on it). A golem needs the Fighter trait to actually strike.
     */
    public static final class Bodyguard implements GolemAccessoryBehavior<Boolean> {
        private static final double TELEPORT_DISTANCE = 24.0D;
        private static final double GUARD_RADIUS = 12.0D;
        private static final int GUARD_PERIOD = 10;

        @Override
        public Codec<Boolean> stateCodec() {
            return Codec.BOOL;
        }

        /** The state is whether the golem sits. */
        @Override
        public Boolean initialState() {
            return Boolean.FALSE;
        }

        @Override
        public @Nullable StreamCodec<RegistryFriendlyByteBuf, Boolean> syncCodec() {
            return ByteBufCodecs.BOOL.cast();
        }

        @Override
        public Boolean onAttach(GolemAccessoryContext context, Boolean state, ItemStack attachedStack) {
            if (context.golem() instanceof EntityThaumaturgeGolem golem) {
                golem.setTrailingOwner(true);
            }
            return Boolean.FALSE;
        }

        @Override
        public void onRemove(GolemAccessoryContext context, Boolean state, ItemStack returnedStack) {
            if (context.golem() instanceof EntityThaumaturgeGolem golem) {
                golem.setTrailingOwner(false);
            }
        }

        @Override
        public Boolean serverTick(GolemAccessoryContext context, Boolean sitting) {
            if (!(context.golem() instanceof EntityThaumaturgeGolem golem) || !(golem.level() instanceof ServerLevel level)) {
                return sitting;
            }
            if (sitting) {
                golem.getNavigation().stop();
                golem.setTarget(null);
                golem.setXRot(45.0F);
                return sitting;
            }
            if (!golem.isTrailingOwner()) {
                golem.setTrailingOwner(true);
            }
            UUID ownerId = golem.ownerIdentity().orElse(null);
            ServerPlayer owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
            if (owner == null || owner.level() != level || owner.isSpectator()) {
                return sitting;
            }
            if (golem.distanceToSqr(owner) > TELEPORT_DISTANCE * TELEPORT_DISTANCE && owner.onGround()) {
                level.sendParticles(ParticleTypes.PORTAL, golem.getX(), golem.getY() + 0.5, golem.getZ(), 16, 0.3, 0.5, 0.3, 0.1);
                golem.teleportTo(owner.getX(), owner.getY(), owner.getZ());
                golem.getNavigation().stop();
                level.playSound(null, golem.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.5F, 1.0F);
            }
            if (golem.tickCount % GUARD_PERIOD == 0 && golem.getTarget() == null) {
                AABB around = owner.getBoundingBox().inflate(GUARD_RADIUS);
                for (Mob mob : level.getEntitiesOfClass(Mob.class, around, m -> m.getTarget() == owner && m.isAlive() && m != golem)) {
                    golem.setTarget(mob);
                    break;
                }
            }
            return sitting;
        }
    }
}
