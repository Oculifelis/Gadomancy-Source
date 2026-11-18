package com.gadomancyawakened.golem;

import com.leclowndu93150.thaumaturge.content.golem.EntityThaumaturgeGolem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Sneak-using an empty hand on your own bodyguard golem tells it to sit and stay, or to follow again. */
public final class GolemEvents {
    private GolemEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(GolemEvents::onInteract);
    }

    private static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof EntityThaumaturgeGolem golem) || event.getHand() != InteractionHand.MAIN_HAND
                || !event.getEntity().isShiftKeyDown() || !event.getItemStack().isEmpty()
                || !golem.getAccessories().contains(GDGolems.BODYGUARD)
                || !golem.ownerIdentity().map(id -> id.equals(event.getEntity().getUUID())).orElse(false)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getLevel().isClientSide()) {
            return;
        }
        GolemAccessoryBehaviors.Bodyguard behavior = (GolemAccessoryBehaviors.Bodyguard) GDGolems.BODYGUARD.behavior().orElseThrow();
        golem.updateAccessoryState(behavior, sitting -> !sitting);
        boolean sitting = golem.accessoryState(behavior).orElse(false);
        event.getEntity().sendOverlayMessage(Component.translatable(sitting ? "gadomancyawakened.bodyguard.sit" : "gadomancyawakened.bodyguard.follow"));
    }
}
