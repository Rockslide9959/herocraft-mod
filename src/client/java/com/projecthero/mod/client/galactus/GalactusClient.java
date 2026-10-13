package com.projecthero.mod.client.galactus;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.frostgiant.FrostHaloLayer;
import com.projecthero.mod.galactus.GalactusAnims;
import com.projecthero.mod.galactus.GalactusSkyPayload;
import com.projecthero.mod.galactus.entity.GalactusEntityTypes;
import com.projecthero.mod.galactus.entity.SurferBoardEntity;
import com.projecthero.mod.galactus.item.UltimateNullifierItem;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * v0.16.1: Galactus: The Devourer's client side -- every renderer ({@link GalactusRenderers}), the sky packet
 * ({@link GalactusSkyClient}, daytime stars), the Silver Surfer's Board controls (sprint = Power Cosmic boost, jump =
 * climb) and the Ultimate Nullifier's GeckoLib item renderer (a flat sprite in the inventory).
 */
public final class GalactusClient {
	public static final ResourceLocation NULLIFIER_ICON = ProjectHeroMod.id("item/ultimate_nullifier_icon");
	private static NullifierRenderer nullifierRenderer;

	private GalactusClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(GalactusEntityTypes.GALACTUS, GalactusRenderers.Colossus::new);
		EntityRendererRegistry.register(GalactusEntityTypes.SILVER_SURFER, GalactusRenderers.Surfer::new);
		EntityRendererRegistry.register(GalactusEntityTypes.HAND, GalactusRenderers.Hand::new);
		// the converter is drawn at its designed 25 blocks (its hit-box is the 10-block core column)
		EntityRendererRegistry.register(GalactusEntityTypes.ELEMENT_CONVERTER, ctx -> new GalactusRenderers.Mob<>(ctx, 3.5f, 25f));
		EntityRendererRegistry.register(GalactusEntityTypes.PUNISHER, ctx -> new GalactusRenderers.Mob<>(ctx, 1.6f, 0f));
		EntityRendererRegistry.register(GalactusEntityTypes.WEAK_POINT, GalactusRenderers.Invisible::new);
		EntityRendererRegistry.register(GalactusEntityTypes.POWER_PYLON, GalactusRenderers.Pylon::new);
		EntityRendererRegistry.register(GalactusEntityTypes.WORLDSHIP, GalactusRenderers.Worldship::new);
		EntityRendererRegistry.register(GalactusEntityTypes.SURFER_BOARD, GalactusRenderers.Board::new);
		ClientPlayNetworking.registerGlobalReceiver(GalactusSkyPayload.TYPE, (payload, ctx) -> ctx.client().execute(() -> GalactusSkyClient.accept(payload)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> GalactusSkyClient.reset());
		SurferBoardEntity.clientBoost = () -> {
			Minecraft mc = Minecraft.getInstance();
			return mc.options != null && mc.options.keySprint.isDown();
		};
		SurferBoardEntity.clientClimb = () -> {
			Minecraft mc = Minecraft.getInstance();
			return mc.options != null && mc.options.keyJump.isDown();
		};
		UltimateNullifierItem.rendererFactory = consumer -> consumer.accept(new GeoRenderProvider() {
			@Override
			public BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
				if (nullifierRenderer == null) {
					nullifierRenderer = new NullifierRenderer();
				}
				return nullifierRenderer;
			}
		});
		ModelLoadingPlugin.register(plugin -> plugin.addModels(NULLIFIER_ICON));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level == null) {
				GalactusSkyClient.reset();
			}
		});
	}

	/** The Ultimate Nullifier in hand: its GeckoLib model; in the inventory, the flat sprite. */
	static final class NullifierRenderer extends GeoItemRenderer<UltimateNullifierItem> {
		NullifierRenderer() {
			super(new GalactusRenderers.Fixed<>(GalactusAnims.NULLIFIER));
			addRenderLayer(new AutoGlowingGeoLayer<>(this));
			addRenderLayer(new FrostHaloLayer<>(this, 0.55f)); // additive bloom + the halo shells around the glowing parts
		}

		@Override
		protected void renderInGui(ItemDisplayContext transformType, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
				int packedOverlay, float partialTick) {
			BakedModel icon = Minecraft.getInstance().getModelManager().getModel(NULLIFIER_ICON);
			if (icon == null || icon == Minecraft.getInstance().getModelManager().getMissingModel()) {
				super.renderInGui(transformType, poseStack, bufferSource, packedLight, packedOverlay, partialTick);
				return;
			}
			poseStack.pushPose();
			poseStack.translate(0.5f, 0.5f, 0.5f);
			Minecraft.getInstance().getItemRenderer().render(getCurrentItemStack(), transformType, false, poseStack, bufferSource, packedLight,
					packedOverlay, icon);
			poseStack.popPose();
		}
	}
}
