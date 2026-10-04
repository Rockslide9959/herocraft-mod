package com.projecthero.mod.client.ironman;

import com.mojang.blaze3d.vertex.PoseStack;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.entity.IronManDeliveryPodEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * v0.14.21: the Mark VII delivery pod -- a red-and-gold armoured capsule with a clamshell front, thrusters underneath and
 * a glowing interior (glowmask). Clips: {@code fly} (thruster flicker + sway), {@code open} / {@code close} (the two
 * doors swing out round their side hinges as the top cap lifts). All assets from
 * {@code scratchpad/gen_v01421_ironman_suitup.js}.
 *
 * <p>GeckoLib only turns {@code LivingEntity}s to their body yaw and passes 0 for a plain {@code Entity} (see
 * {@code SorterBotRenderer}), so this turns the pod to its own yaw -- its doors face the owner.
 */
public class IronManDeliveryPodRenderer extends GeoEntityRenderer<IronManDeliveryPodEntity> {
	public IronManDeliveryPodRenderer(EntityRendererProvider.Context context) {
		super(context, new Model());
		this.shadowRadius = 0.5f;
		addRenderLayer(new AutoGlowingGeoLayer<>(this));
	}

	@Override
	protected void applyRotations(IronManDeliveryPodEntity pod, PoseStack pose, float ageInTicks, float rotationYaw,
			float partialTick, float nativeScale) {
		super.applyRotations(pod, pose, ageInTicks, Mth.rotLerp(partialTick, pod.yRotO, pod.getYRot()), partialTick, nativeScale);
	}

	static final class Model extends GeoModel<IronManDeliveryPodEntity> {
		private static final ResourceLocation GEO = ProjectHeroMod.id("geo/iron_man_delivery_pod.geo.json");
		private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/iron_man_delivery_pod.png");
		private static final ResourceLocation ANIMATION = ProjectHeroMod.id("animations/iron_man_delivery_pod.animation.json");

		@Override
		public ResourceLocation getModelResource(IronManDeliveryPodEntity pod) {
			return GEO;
		}

		@Override
		public ResourceLocation getTextureResource(IronManDeliveryPodEntity pod) {
			return TEXTURE;
		}

		@Override
		public ResourceLocation getAnimationResource(IronManDeliveryPodEntity pod) {
			return ANIMATION;
		}
	}
}
