package com.gadomancyawakened.machine;

import com.gadomancyawakened.GDTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Book of Knowledge item. Pedestals take any held item into their slot on use, so the book claims the click first when
 * the target is one of its supports (block tag {@code gadomancyawakened:knowledge_book_supports}) and stands itself on
 * top instead of being swallowed. Any other target places as a normal block item.
 */
public class KnowledgeBookItem extends BlockItem {
    public KnowledgeBookItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        BlockPos support = context.getClickedPos();
        if (!context.getLevel().getBlockState(support).is(GDTags.KNOWLEDGE_BOOK_SUPPORTS)) {
            return InteractionResult.PASS;
        }
        BlockHitResult top = new BlockHitResult(Vec3.atCenterOf(support).add(0, 0.5, 0), Direction.UP, support, false);
        InteractionResult result = place(new BlockPlaceContext(context.getLevel(), context.getPlayer(), context.getHand(), stack, top));
        return result.consumesAction() ? result : InteractionResult.PASS;
    }
}
