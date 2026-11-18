package com.gadomancyawakened.client;

import com.gadomancyawakened.GDRegistry;
import com.gadomancyawakened.jar.GDJarBlockEntity;
import com.gadomancyawakened.jar.StickyJarBlock;
import com.leclowndu93150.thaumaturge.client.render.blockentity.JarRenderState;
import com.leclowndu93150.thaumaturge.client.render.blockentity.JarRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.jspecify.annotations.Nullable;

/** Client setup: Thaumaturge's jar renderer draws the essentia in Gadomancy's jars, turned round for sticky jars. */
public final class GDClient {
    private GDClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(GDClient::renderers);
        modBus.addListener((net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) ->
                event.register(GDRegistry.ARCANE_PACKAGER_MENU.get(), ArcanePackagerScreen::new));
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(GDRegistry.REMOTE_JAR_BE.get(), JarRenderer::new);
        event.registerBlockEntityRenderer(GDRegistry.STICKY_JAR_BE.get(), StickyJarRenderer::new);
    }

    static final class StickyState extends BlockEntityRenderState {
        final JarRenderState jar;
        Direction mount = Direction.DOWN;

        StickyState(JarRenderState jar) {
            this.jar = jar;
        }
    }

    /** Draws the fluid as an upright jar would, rotated so the jar's bottom lies on its mount face. */
    static final class StickyJarRenderer implements BlockEntityRenderer<GDJarBlockEntity, StickyState> {
        private final JarRenderer jar;

        StickyJarRenderer(BlockEntityRendererProvider.Context context) {
            this.jar = new JarRenderer(context);
        }

        @Override
        public StickyState createRenderState() {
            return new StickyState(jar.createRenderState());
        }

        @Override
        public void extractRenderState(GDJarBlockEntity be, StickyState state, float partialTicks, Vec3 camera,
                                       ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
            BlockEntityRenderer.super.extractRenderState(be, state, partialTicks, camera, breakProgress);
            jar.extractRenderState(be, state.jar, partialTicks, camera, breakProgress);
            state.mount = be.getBlockState().hasProperty(StickyJarBlock.MOUNT) ? be.getBlockState().getValue(StickyJarBlock.MOUNT) : Direction.DOWN;
        }

        @Override
        public void submit(StickyState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
            pose.pushPose();
            pose.rotateAround(state.mount.getOpposite().getRotation(), 0.5F, 0.5F, 0.5F);
            jar.submit(state.jar, pose, collector, camera);
            pose.popPose();
        }
    }
}
