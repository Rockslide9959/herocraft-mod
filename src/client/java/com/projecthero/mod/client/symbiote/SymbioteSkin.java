package com.projecthero.mod.client.symbiote;

import com.mojang.blaze3d.vertex.PoseStack;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;

import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.CatRenderer;
import net.minecraft.client.renderer.entity.CowRenderer;
import net.minecraft.client.renderer.entity.MushroomCowRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.14.4: how a creature carrying a Symbiote looks. Two pieces:
 * <ul>
 *   <li>the whole body is tinted to the organism's glossy purple-black -- {@code LivingEntitySymbioteSkinMixin}
 *       multiplies the body's render colour by {@link #TINT}, for <em>every</em> host (a Symbiote Host zombie as
 *       much as an infested cow; v0.14.4 pet hosts: <em>not</em> a Symbiote Pet, whose skin spreads pixel by pixel
 *       when it transforms for combat -- {@link SymbiotePetSkin}), and</li>
 *   <li>on wolves, cats and cows -- the models whose texture layouts we have eye maps for -- the Symbiote's
 *       white, slanted eye patches ({@link EyesLayer}), drawn emissive so they read in the dark.</li>
 * </ul>
 * A transformed Symbiote Pet is also bigger, but that is the server-side scale attribute (eased over the transform),
 * which vanilla already renders.
 */
public final class SymbioteSkin {
	/** RGB the body texture is multiplied by: deep purple-black, dark enough to read as goo, light enough to keep
	 *  the texture's shading. */
	public static final int TINT = 0x2A2433;

	private static final ResourceLocation WOLF_EYES = ProjectHeroMod.id("textures/entity/symbiote/symbiote_eyes_wolf.png");
	private static final ResourceLocation CAT_EYES = ProjectHeroMod.id("textures/entity/symbiote/symbiote_eyes_cat.png");
	private static final ResourceLocation COW_EYES = ProjectHeroMod.id("textures/entity/symbiote/symbiote_eyes_cow.png");

	private SymbioteSkin() {
	}

	/**
	 * Should the Symbiote's white eyes show? Always on a hostile host; on a Symbiote Pet (v0.14.4 pet hosts) only once
	 * its combat transform has spread far enough ({@link SymbiotePetSkin#EYES_AT}). Reads the synced attachments.
	 */
	public static boolean covered(LivingEntity entity) {
		if (tinted(entity)) {
			return true;
		}
		return isPet(entity) && SymbiotePetSkin.coverage(entity) >= SymbiotePetSkin.EYES_AT;
	}

	/**
	 * Is the whole body tinted black? Hostile hosts only: a Symbiote Pet is drawn with its pixel-by-pixel skin instead
	 * ({@link SymbiotePetSkin}), which is its normal self out of combat.
	 */
	public static boolean tinted(LivingEntity entity) {
		return entity.getAttachedOrElse(ModAttachments.SYMBIOTE_HOST, false);
	}

	public static boolean isPet(LivingEntity entity) {
		return entity.getAttachedOrElse(ModAttachments.SYMBIOTE_PET, 0) > 0;
	}

	/** Multiply a packed ARGB render colour by {@link #TINT}, keeping its alpha. */
	public static int tint(int color) {
		int a = color >>> 24;
		int r = ((color >> 16) & 0xFF) * ((TINT >> 16) & 0xFF) / 255;
		int g = ((color >> 8) & 0xFF) * ((TINT >> 8) & 0xFF) / 255;
		int b = (color & 0xFF) * (TINT & 0xFF) / 255;
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	/** Hook the eye layers onto the three renderers that have eye maps. */
	public static void registerLayers() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof WolfRenderer wolf) {
				helper.register(new EyesLayer<>(wolf, WOLF_EYES));
			} else if (renderer instanceof CatRenderer cat) {
				helper.register(new EyesLayer<>(cat, CAT_EYES));
			} else if (renderer instanceof CowRenderer cow) {
				helper.register(new EyesLayer<>(cow, COW_EYES));
			} else if (renderer instanceof MushroomCowRenderer mooshroom) {
				helper.register(new EyesLayer<>(mooshroom, COW_EYES));
			}
		});
	}

	/** The white Symbiote eyes, emissive, over the tinted body. Transparent everywhere else on the texture. */
	public static final class EyesLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
		private final RenderType type;

		public EyesLayer(RenderLayerParent<T, M> parent, ResourceLocation texture) {
			super(parent);
			this.type = RenderType.eyes(texture);
		}

		@Override
		public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity, float limbSwing,
				float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
			if (entity.isInvisible() || !covered(entity)) {
				return;
			}
			getParentModel().renderToBuffer(poseStack, buffer.getBuffer(type), 0xF000F0, OverlayTexture.NO_OVERLAY, -1);
		}
	}
}
