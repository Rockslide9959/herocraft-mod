package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.heatblast.HeatblastKit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.1: Heatblast's client kit -- the magma-rock skin, a flame for a head (flickering emissive tongues on the head bone),
 * glowing fists, embers and heat shimmer, flames under the feet while the Fire Jet flies, and a pose (third and first person)
 * for every move.
 */
public final class HeatblastClient implements AlienClientKit {
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/heatblast.png");
	private static final ResourceLocation EXTRAS = ProjectHeroMod.id("textures/entity/omnitrix/heatblast_extras.png");

	// {tick, rArm xyz, lArm xyz, body xy, rLeg xyz, lLeg xyz, headX} (radians)
	private static final float[][] BLAST = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.6f, 0.1f, 0, 0.3f, 0, 0, 0.05f, -0.3f, 0.2f, 0, 0, -0.2f, 0, 0, 0),
			QuirkKeyframes.f(7, -1.55f, 0.05f, 0, 0.2f, 0, 0, 0.05f, -0.2f, 0.1f, 0, 0, -0.1f, 0, 0, 0), QuirkKeyframes.rest(12) };
	private static final float[][] STREAM = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(3, -1.5f, -0.25f, 0, -1.5f, 0.25f, 0, 0.1f, 0, 0.25f, 0, 0, -0.25f, 0, 0, 0),
			QuirkKeyframes.f(48, -1.45f, -0.3f, 0, -1.45f, 0.3f, 0, 0.1f, 0, 0.25f, 0, 0, -0.25f, 0, 0, 0), QuirkKeyframes.rest(54) };
	private static final float[][] BOMB = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -2.8f, 0, 0.2f, 0.3f, 0, 0, -0.15f, 0.3f, 0, 0, 0, 0, 0, 0, -0.2f),
			QuirkKeyframes.f(6, -1.1f, 0, 0, 0.5f, 0, 0, 0.25f, -0.25f, 0.3f, 0, 0, -0.3f, 0, 0, 0), QuirkKeyframes.rest(12) };
	private static final float[][] SHIELD = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.2f, -0.9f, 0, -1.2f, 0.9f, 0, 0.1f, 0, 0, 0, 0, 0, 0, 0, 0.1f),
			QuirkKeyframes.f(8, -0.3f, 0, 1.2f, -0.3f, 0, -1.2f, -0.05f, 0, 0, 0, 0.15f, 0, 0, -0.15f, -0.2f), QuirkKeyframes.rest(14) };
	private static final float[][] DASH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0.9f, 0, 0.3f, 0.9f, 0, -0.3f, 0.5f, 0, 0.3f, 0, 0, 0.2f, 0, 0, -0.3f),
			QuirkKeyframes.f(9, 0.8f, 0, 0.3f, 0.8f, 0, -0.3f, 0.45f, 0, 0.3f, 0, 0, 0.2f, 0, 0, -0.3f), QuirkKeyframes.rest(13) };
	private static final float[][] RING = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.6f, 0, 0.5f, -0.6f, 0, -0.5f, 0.35f, 0, -0.6f, 0, 0.1f, -0.6f, 0, -0.1f, 0.3f),
			QuirkKeyframes.f(8, 0.2f, 0, 0.9f, 0.2f, 0, -0.9f, 0.2f, 0, 0, 0, 0.25f, 0, 0, -0.25f, 0.2f), QuirkKeyframes.rest(14) };
	private static final float[][] FLARE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -2.9f, 0, 0.6f, -2.9f, 0, -0.6f, -0.2f, 0, 0, 0, 0.1f, 0, 0, -0.1f, -0.5f),
			QuirkKeyframes.rest(14) };
	private static final float[][] NOVA = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -0.4f, 0.6f, -0.4f, -0.4f, -0.6f, 0.4f, 0.5f, 0, -0.8f, 0, 0, -0.8f, 0, 0, 0.4f),
			QuirkKeyframes.f(6, -0.6f, 0, 1.7f, -0.6f, 0, -1.7f, -0.3f, 0, 0, 0, 0.3f, 0, 0, -0.3f, -0.4f), QuirkKeyframes.rest(16) };
	private static final float[][] CHARGE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -0.7f, 0.7f, -0.3f, -0.7f, -0.7f, 0.3f, 0.35f, 0, -0.5f, 0, 0.1f, -0.5f, 0, -0.1f, 0.3f),
			QuirkKeyframes.f(25, -0.8f, 0.8f, -0.3f, -0.8f, -0.8f, 0.3f, 0.4f, 0, -0.55f, 0, 0.1f, -0.55f, 0, -0.1f, 0.35f), QuirkKeyframes.f(27, -0.8f, 0.8f, -0.3f, -0.8f, -0.8f, 0.3f, 0.4f, 0, -0.55f, 0, 0.1f, -0.55f, 0, -0.1f, 0.35f) };
	private static final float[][] SUPERNOVA = { QuirkKeyframes.f(0, -0.8f, 0.8f, -0.3f, -0.8f, -0.8f, 0.3f, 0.4f, 0, -0.55f, 0, 0.1f, -0.55f, 0, -0.1f, 0.35f),
			QuirkKeyframes.f(3, -0.5f, 0, 2.2f, -0.5f, 0, -2.2f, -0.35f, 0, 0, 0, 0.4f, 0, 0, -0.4f, -0.6f), QuirkKeyframes.f(14, -0.4f, 0, 2.0f, -0.4f, 0, -2.0f, -0.3f, 0, 0, 0, 0.35f, 0, 0, -0.35f, -0.5f),
			QuirkKeyframes.rest(22) };
	private static final float[][] PUNCH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.7f, 0.25f, 0, 0.4f, 0, 0, 0.1f, -0.4f, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.rest(8) };
	private static final float[][] SMELT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.2f, -0.4f, 0, -1.2f, 0.4f, 0, 0.1f, 0, 0, 0, 0, 0, 0, 0, 0.3f),
			QuirkKeyframes.f(12, -1.15f, -0.45f, 0, -1.15f, 0.45f, 0, 0.1f, 0, 0, 0, 0, 0, 0, 0, 0.3f), QuirkKeyframes.rest(16) };

	private static final FirstPersonClip FP_BLAST = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -25, 18, 0, 0.12f, 0.1f, -0.1f),
			FirstPersonClip.f(7, -22, 16, 0, 0.1f, 0.1f, -0.08f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_STREAM = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -30, 22, 0, 0.18f, 0.16f, -0.12f),
			FirstPersonClip.f(48, -28, 20, 0, 0.17f, 0.15f, -0.12f), FirstPersonClip.f(54, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_BOMB = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -70, 0, 10, 0, 0.35f, 0.15f),
			FirstPersonClip.f(6, -10, 20, 0, 0.1f, 0.05f, -0.2f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_BURST = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -20, -10, 0, -0.05f, 0.1f, 0),
			FirstPersonClip.f(6, -35, -35, 25, -0.2f, 0.2f, 0), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_PUNCH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -20, 25, 0, 0.15f, 0.08f, -0.18f),
			FirstPersonClip.f(8, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_CUP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -20, 30, 0, 0.18f, 0.12f, -0.05f),
			FirstPersonClip.f(12, -20, 30, 0, 0.18f, 0.12f, -0.05f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_CHARGE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -15, 35, 0, 0.2f, 0.0f, 0.05f),
			FirstPersonClip.f(27, -18, 38, 0, 0.22f, 0.0f, 0.05f)).both();

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		VertexConsumer flame = buffers.getBuffer(RenderType.eyes(EXTRAS));
		// the flame head: tongues licking up off the head, each flickering on its own beat
		AlienParts.on(pose, model.head, () -> {
			float[][] tongues = { { -3.0f, -2.5f, 2.2f, 5.5f, 0f }, { -0.8f, -2.0f, 2.6f, 8.0f, 1.7f }, { 1.6f, -2.6f, 2.0f, 6.0f, 3.1f },
					{ -2.2f, 1.0f, 2.2f, 6.5f, 4.2f }, { 0.4f, 0.8f, 2.4f, 7.5f, 5.5f }, { -0.6f, 2.6f, 2.0f, 5.0f, 2.4f }, { 2.4f, 0.6f, 1.8f, 4.5f, 0.9f },
					{ -3.6f, 0.2f, 1.6f, 4.0f, 6.0f } };
			for (float[] t : tongues) {
				float flick = 0.75f + 0.25f * Mth.sin(age * 0.6f + t[4] * 2f) + 0.1f * Mth.sin(age * 1.7f + t[4]);
				float h = t[3] * flick;
				float w = t[2];
				AlienParts.box(pose, flame, t[0], -8f - h, t[1] - w / 2, t[0] + w, -7.2f, t[1] + w / 2, (t[4] % 4) / 4f, 0f, (t[4] % 4) / 4f + 0.25f, 0.5f,
						0xFFFFFFFF, LightTexture.FULL_BRIGHT);
			}
			// a soft glow shell just over the face
			AlienParts.box(pose, flame, -4.3f, -8.4f, -4.3f, 4.3f, 0.2f, 4.3f, 0f, 0.5f, 1f, 1f, 0xFF6A3010, LightTexture.FULL_BRIGHT);
		});
		// glowing fists
		for (ModelPart arm : new ModelPart[] { model.rightArm, model.leftArm }) {
			boolean right = arm == model.rightArm;
			AlienParts.on(pose, arm, () -> AlienParts.box(pose, flame, right ? -3.25f : -1.25f, 7.5f, -2.25f, right ? 1.25f : 3.25f, 10.25f, 2.25f, 0f, 0f,
					0.25f, 0.25f, 0xFF904020, LightTexture.FULL_BRIGHT));
		}
	}

	@Override
	public void renderFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, ModelPart arm, boolean right) {
		VertexConsumer flame = buffers.getBuffer(RenderType.eyes(EXTRAS));
		float age = p.tickCount + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float f = 0.9f + 0.1f * Mth.sin(age * 0.8f);
		AlienParts.box(pose, flame, right ? -3.3f : -1.3f, 7.4f, -2.3f, right ? 1.3f : 3.3f, 10.4f * f + 0.6f, 2.3f, 0f, 0f, 0.25f, 0.25f, 0xFF904020,
				LightTexture.FULL_BRIGHT);
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		float[][] clip = switch (animId) {
			case HeatblastKit.ANIM_BLAST -> BLAST;
			case HeatblastKit.ANIM_STREAM -> STREAM;
			case HeatblastKit.ANIM_BOMB -> BOMB;
			case HeatblastKit.ANIM_SHIELD -> SHIELD;
			case HeatblastKit.ANIM_DASH -> DASH;
			case HeatblastKit.ANIM_RING -> RING;
			case HeatblastKit.ANIM_FLARE -> FLARE;
			case HeatblastKit.ANIM_NOVA -> NOVA;
			case HeatblastKit.ANIM_CHARGE -> CHARGE;
			case HeatblastKit.ANIM_SUPERNOVA -> SUPERNOVA;
			case HeatblastKit.ANIM_PUNCH -> PUNCH;
			case HeatblastKit.ANIM_SMELT -> SMELT;
			default -> null;
		};
		boolean played = clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
		// flying on the jets: arms down and back, legs together, a slight lean into the flight
		if (!played && (Omnitrix.state(p).kitFlags & HeatblastKit.F_FLYING) != 0 && !p.onGround()) {
			m.rightArm.xRot = 0.35f;
			m.leftArm.xRot = 0.35f;
			m.rightArm.zRot = 0.35f;
			m.leftArm.zRot = -0.35f;
			m.rightLeg.xRot = 0.15f + Mth.sin(age * 0.2f) * 0.05f;
			m.leftLeg.xRot = 0.1f - Mth.sin(age * 0.2f) * 0.05f;
			return true;
		}
		return played;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case HeatblastKit.ANIM_BLAST -> FP_BLAST;
			case HeatblastKit.ANIM_STREAM -> FP_STREAM;
			case HeatblastKit.ANIM_BOMB -> FP_BOMB;
			case HeatblastKit.ANIM_NOVA, HeatblastKit.ANIM_SUPERNOVA, HeatblastKit.ANIM_FLARE, HeatblastKit.ANIM_SHIELD, HeatblastKit.ANIM_RING -> FP_BURST;
			case HeatblastKit.ANIM_CHARGE -> FP_CHARGE;
			case HeatblastKit.ANIM_PUNCH -> FP_PUNCH;
			case HeatblastKit.ANIM_SMELT -> FP_CUP;
			default -> null;
		};
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		var level = p.level();
		var r = p.getRandom();
		double top = p.getY() + p.getBbHeight();
		if (p.isInWaterOrBubble()) {
			if (r.nextInt(3) == 0) {
				level.addParticle(ParticleTypes.BUBBLE, p.getX(), top, p.getZ(), 0, 0.1, 0);
			}
			return;
		}
		// flames off the head, embers drifting from the body
		if (r.nextInt(2) == 0) {
			level.addParticle(ParticleTypes.FLAME, p.getX() + (r.nextDouble() - 0.5) * 0.35, top - 0.05, p.getZ() + (r.nextDouble() - 0.5) * 0.35, 0, 0.04, 0);
		}
		if (r.nextInt(5) == 0) {
			level.addParticle(ParticleTypes.SMALL_FLAME, p.getX() + (r.nextDouble() - 0.5) * 0.6, p.getY() + r.nextDouble() * p.getBbHeight(),
					p.getZ() + (r.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
		}
		// the Fire Jet
		if ((Omnitrix.state(p).kitFlags & HeatblastKit.F_FLYING) != 0 && !p.onGround()) {
			for (int i = 0; i < 2; i++) {
				level.addParticle(ParticleTypes.FLAME, p.getX() + (r.nextDouble() - 0.5) * 0.2, p.getY() - 0.05, p.getZ() + (r.nextDouble() - 0.5) * 0.2,
						0, -0.15, 0);
			}
		}
		// the Fire Stream: a gout of flame from the palms (others see it too)
		if ((Omnitrix.state(p).kitFlags & HeatblastKit.F_STREAM) != 0) {
			var look = p.getLookAngle();
			var from = p.getEyePosition().add(look.scale(0.6)).add(0, -0.3, 0);
			for (int i = 0; i < 4; i++) {
				double s = 0.5 + r.nextDouble() * 0.6;
				level.addParticle(ParticleTypes.FLAME, from.x, from.y, from.z, look.x * s + (r.nextDouble() - 0.5) * 0.12, look.y * s + (r.nextDouble() - 0.5) * 0.12,
						look.z * s + (r.nextDouble() - 0.5) * 0.12);
			}
		}
	}

}
