package com.projecthero.mod.client.frostgiant;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.frostgiant.entity.FrostGiantEntity;
import com.projecthero.mod.frostgiant.entity.KingLaufeyEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.Color;

/**
 * v0.16.1: one renderer for every Frost Giant (hound, Raider, Shaman, Brute, Warlord, Laufey). Each draws its own GeckoLib
 * model ({@code geo/frost_giant/<model>.geo.json}, texture, glowmask for the red eyes / ridges / crystal edges, animations)
 * and is scaled so its rest pose fills its hit-box ({@link FrostModelFit}); the scale is applied on the main pass only,
 * since the glow layer re-renders inside the already-scaled stack.
 * <ul>
 *   <li>The head follows its look target through the never-keyed {@code neck} bone (set absolutely each frame, so it can't
 *       accumulate), faded out during moves.</li>
 *   <li>A hurt flinch on the never-keyed {@code flinch} bone.</li>
 *   <li>Laufey: the held {@code casket} bone is hidden once the Casket floats above him (phase 3); dying, he turns to ice
 *       (a pale tint creeping in over the 4 s collapse).</li>
 *   <li>No vanilla tip-over while dying -- the death clip is the collapse.</li>
 * </ul>
 */
public class FrostGiantRenderer<T extends FrostGiantEntity> extends GeoEntityRenderer<T> {
	public FrostGiantRenderer(EntityRendererProvider.Context context, float shadow) {
		super(context, new Model<>());
		this.shadowRadius = shadow;
		addRenderLayer(new AutoGlowingGeoLayer<>(this));
		addRenderLayer(new FrostHaloLayer<>(this, 0.55f)); // additive bloom + halo shells
	}

	@Override
	public void preRender(PoseStack poseStack, T animatable, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
			boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
		super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
		if (!isReRender) {
			float s = FrostModelFit.fit(model, animatable.getBbHeight());
			poseStack.scale(s, s, s);
		}
	}

	@Override
	protected float getDeathMaxRotation(T animatable) {
		return 0.0f;
	}

	@Override
	public int getPackedOverlay(T animatable, float u, float partialTick) {
		boolean flash = animatable.hurtTime > 0 && !animatable.isDeadOrDying();
		return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(flash));
	}

	@Override
	public Color getRenderColor(T animatable, float partialTick, int packedLight) {
		Color base = super.getRenderColor(animatable, partialTick, packedLight);
		if (animatable instanceof KingLaufeyEntity && animatable.isDeadOrDying()) {
			// turning to ice from the feet up: the whole body pales toward frost-white over the collapse
			float t = Mth.clamp((animatable.deathTime + partialTick) / 80f, 0f, 1f);
			int r = (int) Mth.lerp(t, 255, 200);
			int g = (int) Mth.lerp(t, 255, 235);
			return Color.ofARGB(base.getAlpha(), r, g, 255);
		}
		return base;
	}

	static final class Model<T extends FrostGiantEntity> extends GeoModel<T> {
		@Override
		public ResourceLocation getModelResource(T animatable) {
			return FrostModelFit.geo(animatable.model());
		}

		@Override
		public ResourceLocation getTextureResource(T animatable) {
			return FrostModelFit.texture(animatable.model());
		}

		@Override
		public ResourceLocation getAnimationResource(T animatable) {
			return FrostModelFit.animation(animatable.model());
		}

		@Override
		public void setCustomAnimations(T giant, long instanceId, AnimationState<T> state) {
			GeoBone neck = getAnimationProcessor().getBone("neck");
			if (neck != null) {
				float target = giant.isDeadOrDying() || giant.clientClip() != null ? 0.35f : 1.0f;
				giant.clientHeadWeight += (target - giant.clientHeadWeight) * 0.12f;
				EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
				float yaw = data == null ? 0.0f : Mth.clamp(data.netHeadYaw(), -55.0f, 55.0f);
				float pitch = data == null ? 0.0f : Mth.clamp(data.headPitch(), -35.0f, 30.0f);
				float w = giant.isDeadOrDying() ? 0f : giant.clientHeadWeight;
				neck.setRotY(yaw * w * Mth.DEG_TO_RAD);
				neck.setRotX(pitch * w * Mth.DEG_TO_RAD);
			}
			GeoBone flinch = getAnimationProcessor().getBone("flinch");
			if (flinch != null) {
				float k = 0.0f;
				if (giant.hurtTime > 0 && !giant.isDeadOrDying()) {
					float t = (10.0f - giant.hurtTime + state.getPartialTick()) / 10.0f;
					k = Mth.sin(Mth.clamp(t, 0.0f, 1.0f) * Mth.PI) * (1.0f - t * 0.5f);
				}
				flinch.setRotX(k * 7.0f * Mth.DEG_TO_RAD);
				flinch.setRotZ(k * 2.5f * Mth.DEG_TO_RAD);
			}
			if (giant instanceof KingLaufeyEntity laufey) {
				GeoBone casket = getAnimationProcessor().getBone("casket");
				if (casket != null) {
					casket.setHidden(laufey.casketFloating());
				}
			}
		}
	}
}
