package com.gadomancyawakened.jar;

import com.gadomancyawakened.GDComponents;
import com.gadomancyawakened.GDRegistry;
import com.leclowndu93150.thaumaturge.content.essentia.jar.AbstractBlockEntityJar;
import com.leclowndu93150.thaumaturge.content.essentia.jar.AbstractBlockJar;
import com.mojang.serialization.MapCodec;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The Remote Jar: a warded jar tied to a network. Using a remote jar item on a placed one links them: an unlinked jar
 * starts a new network, which the item copies. Placing a linked item joins its network. Linked jars share essentia.
 */
public class RemoteJarBlock extends AbstractBlockJar {
    public static final MapCodec<RemoteJarBlock> CODEC = simpleCodec(RemoteJarBlock::new);

    public RemoteJarBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<RemoteJarBlock> codec() {
        return CODEC;
    }

    @Override
    protected BlockEntityType<? extends AbstractBlockEntityJar> jarType() {
        return GDRegistry.REMOTE_JAR_BE.get();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return GDJarBlockEntity.remote(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, GDRegistry.REMOTE_JAR_BE.get(), GDJarBlockEntity::tick);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(GDRegistry.REMOTE_JAR.get().asItem()) || player.isShiftKeyDown()) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof GDJarBlockEntity jar)) {
            return InteractionResult.SUCCESS;
        }
        link(jar, stack, player);
        return InteractionResult.SUCCESS_SERVER;
    }

    static void link(GDJarBlockEntity jar, ItemStack stack, Player player) {
        UUID network = jar.network();
        boolean fresh = network == null;
        if (fresh) {
            network = UUID.randomUUID();
            jar.setNetwork(network);
        }
        stack.set(GDComponents.JAR_NETWORK.get(), network);
        player.sendOverlayMessage(Component.translatable(fresh ? "gadomancyawakened.remote_jar.new" : "gadomancyawakened.remote_jar.connected",
                RemoteJarItem.networkName(network)));
    }
}
