package com.gadomancyawakened.golem;

import com.gadomancyawakened.GDRegistry;
import com.gadomancyawakened.GadomancyAwakened;
import com.leclowndu93150.thaumaturge.api.golems.accessory.GolemAccessories;
import com.leclowndu93150.thaumaturge.api.golems.accessory.GolemAccessory;
import com.leclowndu93150.thaumaturge.api.golems.parts.GolemMaterial;
import com.leclowndu93150.thaumaturge.api.golems.seals.SealFilterMode;
import com.leclowndu93150.thaumaturge.api.golems.seals.SealType;
import com.leclowndu93150.thaumaturge.content.golem.ItemSealPlacer;
import com.leclowndu93150.thaumaturge.registry.TTBlocks;
import com.leclowndu93150.thaumaturge.registry.TTGolemTraits;
import com.leclowndu93150.thaumaturge.registry.TTItems;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Gadomancy's extra golem type as a Thaumaturge golem material, built in the golem press like the others.
 * Silverwood is quick and light with average durability and self-repair (TC4: 20 hp, 9 armour, speed 0.38,
 * strength 1). GTNH 1.5.17 leaves the Obsidian golem commented out, so it is not ported.
 *
 * <p>TC4 golem cores and upgrades map onto Thaumaturge's TC6 golems: the Break core becomes a seal (a tooled breaker),
 * while the Bodyguard core and the Runic Shield upgrade, which belong to the golem rather than a place, become
 * accessories put on by using the item on a golem (bound through Thaumaturge's {@code golem_accessory} data map).
 */
public final class GDGolems {
    public static final DeferredRegister<GolemMaterial> MATERIALS = DeferredRegister.create(GolemMaterial.REGISTRY_KEY, GadomancyAwakened.MODID);

    public static final DeferredHolder<GolemMaterial, GolemMaterial> SILVERWOOD = MATERIALS.register("silverwood",
            () -> new GolemMaterial(List.of(GadomancyAwakened.id("golem_silverwood")),
                    Identifier.parse("thaumaturge:textures/block/plank_silverwood.png"), 0xD9E4E6, 12, 5, 1,
                    () -> new ItemStack(TTBlocks.LOG_SILVERWOOD.get()), () -> new ItemStack(TTItems.MECHANISM_SIMPLE.get()),
                    List.of(TTGolemTraits.LIGHT, TTGolemTraits.REPAIR)));

    public static final DeferredRegister<SealType> SEALS = DeferredRegister.create(SealType.REGISTRY_KEY, GadomancyAwakened.MODID);

    public static final DeferredItem<ItemSealPlacer> SEAL_BREAK = GDRegistry.item("seal_break",
            p -> new ItemSealPlacer(GadomancyAwakened.id("break"), p.stacksTo(16)));
    public static final DeferredHolder<SealType, SealType> BREAK = SEALS.register("break",
            () -> SealType.builder(ToolBreakBehavior::new).filter(9, SealFilterMode.PLAIN).area().placer(SEAL_BREAK).build());

    public static final GolemAccessory RUNIC_SHIELDING = new GolemAccessory(GadomancyAwakened.id("runic_shielding"), GolemAccessory.Group.NONE,
            0, 1.0F, 1.0F, 1.0F, 0, false, Optional.of(new GolemAccessoryBehaviors.RunicShield()));
    public static final GolemAccessory BODYGUARD = new GolemAccessory(GadomancyAwakened.id("bodyguard"), new GolemAccessory.Group(GadomancyAwakened.id("core")),
            0, 1.0F, 1.0F, 1.0F, 0, true, Optional.of(new GolemAccessoryBehaviors.Bodyguard()));

    public static final DeferredItem<Item> RUNIC_SHIELDING_ITEM = GDRegistry.item("runic_shielding", p -> new Item(p.stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<Item> BODYGUARD_ITEM = GDRegistry.item("bodyguard_badge", p -> new Item(p.stacksTo(1).rarity(Rarity.UNCOMMON)));

    private GDGolems() {
    }

    public static void register(IEventBus bus) {
        MATERIALS.register(bus);
        SEALS.register(bus);
    }

    /** Accessories live in a plain map in Thaumaturge, filled during common setup. */
    public static void registerAccessories() {
        GolemAccessories.register(RUNIC_SHIELDING);
        GolemAccessories.register(BODYGUARD);
    }
}
