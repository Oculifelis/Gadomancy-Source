package com.gadomancyawakened;

import com.gadomancyawakened.block.ArcaneDropperBlock;
import com.gadomancyawakened.block.ArcaneDropperBlockEntity;
import com.gadomancyawakened.jar.GDJarBlockEntity;
import com.gadomancyawakened.jar.RemoteJarBlock;
import com.gadomancyawakened.jar.RemoteJarItem;
import com.gadomancyawakened.jar.StickyJarBlock;
import com.gadomancyawakened.jar.StickyJarRecipe;
import com.gadomancyawakened.aura.AuraCoreItem;
import com.gadomancyawakened.familiar.AugmentRune;
import com.gadomancyawakened.familiar.EtherealFamiliarItem;
import com.gadomancyawakened.familiar.FamiliarRecipe;
import com.gadomancyawakened.item.ArmorRecipe;
import com.gadomancyawakened.aura.AuraPylonBlock;
import com.gadomancyawakened.aura.AuraPylonBlockEntity;
import com.gadomancyawakened.item.VoidElementItem;
import com.gadomancyawakened.machine.ArcanePackageItem;
import com.gadomancyawakened.machine.ArcanePackagerBlock;
import com.gadomancyawakened.machine.ArcanePackagerBlockEntity;
import com.gadomancyawakened.machine.ArcanePackagerMenu;
import com.gadomancyawakened.machine.BlockProtectorBlock;
import com.gadomancyawakened.machine.BlockProtectorBlockEntity;
import com.gadomancyawakened.machine.EssentiaCompressorBlock;
import com.gadomancyawakened.machine.EssentiaCompressorBlockEntity;
import com.gadomancyawakened.machine.KnowledgeBookBlock;
import com.gadomancyawakened.machine.KnowledgeBookBlockEntity;
import com.gadomancyawakened.machine.InfusionClawBlock;
import com.gadomancyawakened.machine.InfusionClawBlockEntity;
import com.gadomancyawakened.node.NodeManipulatorBlock;
import com.gadomancyawakened.node.NodeManipulatorBlockEntity;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class GDRegistry {
    private static final String MODID = GadomancyAwakened.MODID;

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    private static final List<Supplier<? extends Item>> TAB_ITEMS = new ArrayList<>();

    public static final DeferredBlock<ArcaneDropperBlock> ARCANE_DROPPER = block("arcane_dropper", ArcaneDropperBlock::new,
            p -> p.mapColor(MapColor.STONE).strength(3.5f).sound(SoundType.STONE).requiresCorrectToolForDrops());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ArcaneDropperBlockEntity>> ARCANE_DROPPER_BE = BLOCK_ENTITIES.register("arcane_dropper",
            () -> new BlockEntityType<>(ArcaneDropperBlockEntity::new, ARCANE_DROPPER.get()));

    public static final DeferredBlock<RemoteJarBlock> REMOTE_JAR = BLOCKS.registerBlock("remote_jar", RemoteJarBlock::new,
            p -> p.mapColor(MapColor.NONE).strength(0.3f).sound(SoundType.GLASS).noOcclusion().lightLevel(s -> 0));
    public static final DeferredItem<RemoteJarItem> REMOTE_JAR_ITEM = item("remote_jar",
            p -> new RemoteJarItem(REMOTE_JAR.get(), p.stacksTo(1).rarity(Rarity.UNCOMMON).useBlockDescriptionPrefix()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GDJarBlockEntity>> REMOTE_JAR_BE = BLOCK_ENTITIES.register("remote_jar",
            () -> new BlockEntityType<>(GDJarBlockEntity::remote, REMOTE_JAR.get()));

    /** Sticky jars are placed by sticky jar items, never on their own, so the block has no item. */
    public static final DeferredBlock<StickyJarBlock> STICKY_JAR = BLOCKS.registerBlock("sticky_jar", StickyJarBlock::new,
            p -> p.mapColor(MapColor.NONE).strength(0.3f).sound(SoundType.GLASS).noOcclusion());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GDJarBlockEntity>> STICKY_JAR_BE = BLOCK_ENTITIES.register("sticky_jar",
            () -> new BlockEntityType<>(GDJarBlockEntity::sticky, STICKY_JAR.get()));

    public static final DeferredBlock<BlockProtectorBlock> BLOCK_PROTECTOR = block("block_protector", BlockProtectorBlock::new,
            p -> p.mapColor(MapColor.COLOR_PURPLE).strength(3.0f).sound(SoundType.STONE).noOcclusion().requiresCorrectToolForDrops());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockProtectorBlockEntity>> BLOCK_PROTECTOR_BE = BLOCK_ENTITIES.register("block_protector",
            () -> new BlockEntityType<>(BlockProtectorBlockEntity::new, BLOCK_PROTECTOR.get()));

    public static final DeferredBlock<EssentiaCompressorBlock> ESSENTIA_COMPRESSOR = block("essentia_compressor", EssentiaCompressorBlock::new,
            p -> p.mapColor(MapColor.COLOR_PURPLE).strength(4.0f).sound(SoundType.STONE).noOcclusion().requiresCorrectToolForDrops());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EssentiaCompressorBlockEntity>> ESSENTIA_COMPRESSOR_BE = BLOCK_ENTITIES.register("essentia_compressor",
            () -> new BlockEntityType<>(EssentiaCompressorBlockEntity::new, ESSENTIA_COMPRESSOR.get()));
    public static final DeferredItem<VoidElementItem> VOID_ELEMENT = item("void_element",
            p -> new VoidElementItem(p.stacksTo(1).rarity(Rarity.EPIC)));

    public static final DeferredBlock<KnowledgeBookBlock> KNOWLEDGE_BOOK = block("knowledge_book", KnowledgeBookBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(1.0f).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<KnowledgeBookBlockEntity>> KNOWLEDGE_BOOK_BE = BLOCK_ENTITIES.register("knowledge_book",
            () -> new BlockEntityType<>(KnowledgeBookBlockEntity::new, KNOWLEDGE_BOOK.get()));

    public static final DeferredBlock<ArcanePackagerBlock> ARCANE_PACKAGER = block("arcane_packager", ArcanePackagerBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ArcanePackagerBlockEntity>> ARCANE_PACKAGER_BE = BLOCK_ENTITIES.register("arcane_packager",
            () -> new BlockEntityType<>(ArcanePackagerBlockEntity::new, ARCANE_PACKAGER.get()));
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredHolder<MenuType<?>, MenuType<ArcanePackagerMenu>> ARCANE_PACKAGER_MENU = MENUS.register("arcane_packager",
            () -> new MenuType<>(ArcanePackagerMenu::new, FeatureFlags.VANILLA_SET));
    public static final DeferredItem<ArcanePackageItem> ARCANE_PACKAGE = ITEMS.registerItem("arcane_package",
            p -> new ArcanePackageItem(p.stacksTo(1)));
    public static final DeferredItem<ArcanePackageItem> ARCANE_PACKAGE_ESSENTIA = ITEMS.registerItem("arcane_package_essentia",
            p -> new ArcanePackageItem(p.stacksTo(1).rarity(Rarity.UNCOMMON)));

    public static final DeferredItem<AuraCoreItem> AURA_CORE = item("aura_core", p -> new AuraCoreItem(p.stacksTo(1).rarity(Rarity.UNCOMMON)));

    public static final DeferredBlock<InfusionClawBlock> INFUSION_CLAW = block("infusion_claw", InfusionClawBlock::new,
            p -> p.mapColor(MapColor.STONE).strength(3.0f).sound(SoundType.STONE).noOcclusion().requiresCorrectToolForDrops());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InfusionClawBlockEntity>> INFUSION_CLAW_BE = BLOCK_ENTITIES.register("infusion_claw",
            () -> new BlockEntityType<>(InfusionClawBlockEntity::new, INFUSION_CLAW.get()));

    public static final DeferredBlock<NodeManipulatorBlock> NODE_MANIPULATOR = block("node_manipulator", NodeManipulatorBlock::new,
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(4.0f).sound(SoundType.STONE).noOcclusion().requiresCorrectToolForDrops());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NodeManipulatorBlockEntity>> NODE_MANIPULATOR_BE = BLOCK_ENTITIES.register("node_manipulator",
            () -> new BlockEntityType<>(NodeManipulatorBlockEntity::new, NODE_MANIPULATOR.get()));
    public static final DeferredBlock<NodeManipulatorBlock.Focus> RANDOMIZATION_FOCUS = block("randomization_focus", NodeManipulatorBlock.Focus::new,
            p -> p.mapColor(MapColor.COLOR_PURPLE).strength(2.0f).sound(SoundType.STONE).noOcclusion().lightLevel(s -> 6));
    public static final DeferredBlock<NodeManipulatorBlock.Focus> PORTAL_FOCUS = block("portal_focus", NodeManipulatorBlock.Focus::new,
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(2.0f).sound(SoundType.STONE).noOcclusion().lightLevel(s -> 4));

    public static final DeferredBlock<AuraPylonBlock> AURA_PYLON = block("aura_pylon", AuraPylonBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AuraPylonBlockEntity>> AURA_PYLON_BE = BLOCK_ENTITIES.register("aura_pylon",
            () -> new BlockEntityType<>(AuraPylonBlockEntity::new, AURA_PYLON.get()));
    public static final DeferredBlock<AuraPylonBlock.Peak> AURA_PYLON_PEAK = block("aura_pylon_peak", AuraPylonBlock.Peak::new,
            p -> p.mapColor(MapColor.GOLD).strength(2.5f).sound(SoundType.METAL).noOcclusion().lightLevel(s -> 10));

    public static final DeferredItem<EtherealFamiliarItem> ETHEREAL_FAMILIAR = item("ethereal_familiar",
            p -> new EtherealFamiliarItem(p.stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredItem<AugmentRune> FAMILIAR_AUGMENT = item("familiar_augment",
            p -> new AugmentRune(p.stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<Item> REVEALING_LENS = item("revealing_lens", p -> new Item(p.rarity(Rarity.UNCOMMON)));

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MODID);
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<StickyJarRecipe>> STICKY_JAR_RECIPE = SERIALIZERS.register("sticky_jar",
            () -> StickyJarRecipe.SERIALIZER);
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FamiliarRecipe>> FAMILIAR_RECIPE = SERIALIZERS.register("familiar",
            () -> FamiliarRecipe.SERIALIZER);
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ArmorRecipe>> ARMOR_RECIPE = SERIALIZERS.register("armor",
            () -> ArmorRecipe.SERIALIZER);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.gadomancyawakened"))
            .icon(() -> new ItemStack(ARCANE_DROPPER.get()))
            .displayItems((params, out) -> TAB_ITEMS.forEach(i -> out.accept(i.get())))
            .build());

    private GDRegistry() {
    }

    private static <B extends Block> DeferredBlock<B> block(String name, Function<Block.Properties, B> factory,
                                                           UnaryOperator<Block.Properties> props) {
        DeferredBlock<B> block = BLOCKS.registerBlock(name, factory, props);
        TAB_ITEMS.add(ITEMS.registerSimpleBlockItem(block));
        return block;
    }

    /** Registers an item that shows in the Gadomancy tab. */
    public static <I extends Item> DeferredItem<I> item(String name, Function<Item.Properties, I> factory) {
        DeferredItem<I> item = ITEMS.registerItem(name, factory);
        TAB_ITEMS.add(item);
        return item;
    }

    static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        TABS.register(bus);
        SERIALIZERS.register(bus);
        MENUS.register(bus);
        bus.addListener(GDRegistry::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, ARCANE_DROPPER_BE.get(), (be, side) -> be.handler());
        for (BlockEntityType<GDJarBlockEntity> jar : List.of(REMOTE_JAR_BE.get(), STICKY_JAR_BE.get())) {
            event.registerBlockEntity(EssentiaCapabilities.TRANSPORT, jar, (be, side) -> be);
            event.registerBlockEntity(EssentiaCapabilities.STORAGE, jar, (be, side) -> be.storage(side));
        }
        event.registerBlockEntity(EssentiaCapabilities.TRANSPORT, BLOCK_PROTECTOR_BE.get(), (be, side) -> be);
        event.registerBlockEntity(EssentiaCapabilities.TRANSPORT, ESSENTIA_COMPRESSOR_BE.get(), (be, side) -> be);
        event.registerBlockEntity(EssentiaCapabilities.TRANSPORT, KNOWLEDGE_BOOK_BE.get(), (be, side) -> be);
        event.registerBlockEntity(EssentiaCapabilities.TRANSPORT, ARCANE_PACKAGER_BE.get(), (be, side) -> be);
        event.registerBlockEntity(EssentiaCapabilities.TRANSPORT, AURA_PYLON_BE.get(), (be, side) -> be);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ARCANE_PACKAGER_BE.get(),
                (be, side) -> side == null ? null : new WorldlyContainerWrapper(be, side));
    }
}
