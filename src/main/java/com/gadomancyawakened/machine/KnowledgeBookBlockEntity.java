package com.gadomancyawakened.machine;

import com.gadomancyawakened.GDDataMaps;
import com.gadomancyawakened.GDRegistry;
import com.leclowndu93150.thaumaturge.api.aspect.Aspects;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TTAspects;
import com.leclowndu93150.thaumaturge.api.capability.IPlayerKnowledge;
import com.leclowndu93150.thaumaturge.api.capability.KnowledgeAccess;
import com.leclowndu93150.thaumaturge.api.capability.KnowledgeType;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import com.leclowndu93150.thaumaturge.api.research.IResearchCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The original book finished TC4 research notes. Thaumaturge has no notes, so the port studies a research category
 * instead and pays its owner in theory knowledge: each point the original would have worked off a note is one raw
 * theory point here (32 make a level). The pace is the original's: with fuel, each tick has a 1 in
 * (max(0, 200 - knowledge) + 100) chance of a point, where knowledge sums the {@code gadomancyawakened:knowledge} data map
 * values of blocks within 4 across and 3 up or down (rescanned every 200 ticks). One Cognitio is 150 ticks of fuel.
 */
public class KnowledgeBookBlockEntity extends BlockEntity implements IEssentiaTransport {
    private static final int TICKS_PER_ESSENTIA = 150;
    private static final int MAX_FUEL = 8 * TICKS_PER_ESSENTIA;

    private @Nullable UUID owner;
    private @Nullable Identifier category;
    private int fuel;
    private int knowledge;
    private int pending;
    private int clock;

    public KnowledgeBookBlockEntity(BlockPos pos, BlockState state) {
        super(GDRegistry.KNOWLEDGE_BOOK_BE.get(), pos, state);
    }

    void setOwner(UUID id) {
        owner = id;
        setChanged();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, KnowledgeBookBlockEntity be) {
        if (be.clock++ % 200 == 0) {
            be.knowledge = be.scanKnowledge(level, pos);
        }
        if (be.category == null || be.owner == null || be.fuel <= 0) {
            return;
        }
        be.fuel--;
        if (level.getRandom().nextInt(Math.max(0, 200 - be.knowledge) + 100) == 0) {
            be.pending++;
            ((ServerLevel) level).sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5,
                    8, 0.3, 0.3, 0.3, 0.5);
        }
        if (be.pending > 0 && be.clock % 20 == 0 && level.getServer() != null) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(be.owner);
            if (player != null) {
                IPlayerKnowledge k = KnowledgeAccess.of(player);
                k.addKnowledge(KnowledgeType.THEORY, ResourceKey.create(IResearchCategory.REGISTRY_KEY, be.category), be.pending);
                k.sync(player);
                be.pending = 0;
            }
        }
        be.setChanged();
    }

    private int scanKnowledge(Level level, BlockPos pos) {
        int total = 0;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-4, -3, -4), pos.offset(4, 3, 4))) {
            if (!p.equals(pos)) {
                Integer v = level.getBlockState(p).typeHolder().getData(GDDataMaps.KNOWLEDGE);
                total += v == null ? 0 : v;
            }
        }
        return total;
    }

    void cycleCategory(ServerPlayer player) {
        List<Identifier> ids = new ArrayList<>(player.level().registryAccess().lookupOrThrow(IResearchCategory.REGISTRY_KEY)
                .listElementIds().map(ResourceKey::identifier).sorted().toList());
        if (ids.isEmpty()) {
            return;
        }
        int at = category == null ? -1 : ids.indexOf(category);
        category = ids.get((at + 1) % ids.size());
        setChanged();
        player.sendOverlayMessage(Component.translatable("gadomancyawakened.knowledge_book.category", categoryName()));
    }

    void report(ServerPlayer player) {
        player.sendOverlayMessage(Component.translatable("gadomancyawakened.knowledge_book.status", categoryName(), knowledge,
                fuel / TICKS_PER_ESSENTIA));
    }

    private Component categoryName() {
        return category == null ? Component.translatable("gadomancyawakened.knowledge_book.none")
                : Component.translatable("research_category." + category.getNamespace() + "." + category.getPath());
    }

    int signal() {
        return category == null ? 0 : fuel > 0 ? 15 : 1;
    }

    // ---- essentia: Cognitio in through the sides ----

    private boolean side(@Nullable Direction face) {
        return face != null && face.getAxis().isHorizontal();
    }

    @Override
    public boolean isConnectable(Direction face) {
        return side(face);
    }

    @Override
    public boolean canInputFrom(Direction face) {
        return side(face);
    }

    @Override
    public boolean canOutputTo(Direction face) {
        return false;
    }

    @Override
    public void setSuction(Holder<IAspect> type, int amount) {
    }

    @Override
    public @Nullable Holder<IAspect> getSuctionType(Direction face) {
        return level == null || fuel > MAX_FUEL - TICKS_PER_ESSENTIA ? null : Aspects.resolve(level, TTAspects.COGNITIO);
    }

    @Override
    public int getSuctionAmount(Direction face) {
        return side(face) && fuel <= MAX_FUEL - TICKS_PER_ESSENTIA ? 64 : 0;
    }

    @Override
    public int takeEssentia(Holder<IAspect> type, int amount, Direction face) {
        return 0;
    }

    @Override
    public int addEssentia(Holder<IAspect> type, int amount, Direction face) {
        if (!side(face) || !type.is(TTAspects.COGNITIO)) {
            return 0;
        }
        int added = Math.min(amount, (MAX_FUEL - fuel) / TICKS_PER_ESSENTIA);
        fuel += added * TICKS_PER_ESSENTIA;
        if (added > 0) {
            setChanged();
        }
        return added;
    }

    @Override
    public @Nullable Holder<IAspect> getEssentiaType(Direction face) {
        return null;
    }

    @Override
    public int getEssentiaAmount(Direction face) {
        return 0;
    }

    @Override
    public int getMinimumSuction() {
        return 0;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
        category = input.read("category", Identifier.CODEC).orElse(null);
        fuel = input.getIntOr("fuel", 0);
        pending = input.getIntOr("pending", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("owner", UUIDUtil.CODEC, owner);
        output.storeNullable("category", Identifier.CODEC, category);
        output.putInt("fuel", fuel);
        output.putInt("pending", pending);
    }
}
