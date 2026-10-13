package com.projecthero.mod.client.render.raid;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.event.entity.EmpoweredZombie;
import com.projecthero.mod.event.entity.PoweredBossAnims;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

import org.jetbrains.annotations.Nullable;

import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * v0.16.1: the Powered Zombie Boss (an ordinary Zombie Raid boss -- {@link EmpoweredZombie#variant()} null; the Supervillain
 * variants keep {@code SupervillainBossRenderer}). Everything that glows on the model is painted white, and both glow passes
 * are tinted with the power's colour ({@link PoweredBossAnims#look}); only the power's accent set ({@code acc_<set>*}
 * bones: shards, horns, coils, vines, orbit stones, wings, spines) is shown. A dual-power final boss shows both sets, in
 * the first power's colour. The orbit stones circle the shoulders (code-driven spin).
 */
public class PoweredZombieRenderer extends RaidUndeadRenderer<EmpoweredZombie> {
	public PoweredZombieRenderer(EntityRendererProvider.Context context) {
		super(context, 1.0f, b -> scaled(tint(b), 0.7f));
	}

	static int scaled(int rgb, float k) {
		int r = Math.round(((rgb >> 16) & 255) * k), g = Math.round(((rgb >> 8) & 255) * k), bl = Math.round((rgb & 255) * k);
		return (r << 16) | (g << 8) | bl;
	}

	static int tint(EmpoweredZombie boss) {
		return PoweredBossAnims.look(boss.powerKey()).rgb();
	}

	@Override
	protected AutoGlowingGeoLayer<EmpoweredZombie> glowLayer() {
		return new AutoGlowingGeoLayer<>(this) {
			@Override
			public void render(PoseStack poseStack, EmpoweredZombie animatable, BakedGeoModel bakedModel, @Nullable RenderType renderType,
					MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
				RenderType rt = getRenderType(animatable, bufferSource);
				if (rt != null) {
					getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, rt, bufferSource.getBuffer(rt), partialTick,
							LightTexture.FULL_BRIGHT, packedOverlay, 0xFF000000 | tint(animatable));
				}
			}
		};
	}

	@Override
	protected void customBones(EmpoweredZombie boss, GeoModel<EmpoweredZombie> model, AnimationState<EmpoweredZombie> state) {
		String a = PoweredBossAnims.look(boss.powerKey()).accent();
		String b = boss.secondPowerKey().isEmpty() ? a : PoweredBossAnims.look(boss.secondPowerKey()).accent();
		for (GeoBone bone : model.getAnimationProcessor().getRegisteredBones()) {
			String n = bone.getName();
			if (n.startsWith("acc_")) {
				boolean show = n.startsWith("acc_" + a) || n.startsWith("acc_" + b);
				bone.setHidden(!show);
			}
		}
		GeoBone orbit = model.getAnimationProcessor().getBone("acc_orbit");
		if (orbit != null && !orbit.isHidden()) {
			orbit.setRotY((boss.tickCount + state.getPartialTick()) * 0.06f);
		}
	}
}
