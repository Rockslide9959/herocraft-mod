package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.upgrade.UpgradeKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.1: Upgrade's client kit -- the black Mechamorph skin with its green circuit lines GLOWING (an emissive copy of the body,
 * pulsing slowly), the one round green eye, an arm that stretches out like liquid metal for the grab / lash / grapple, a flat
 * slick puddle pose while he slides, and dripping liquid. A pose for every move.
 */
public final class UpgradeClient implements AlienClientKit {
	// v0.17.1 (user: "add abilities to the V key for every alien"): the V move's pose and first-person clip
	private static final float[][] OVERCLOCK = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.5f, 0.1f, 0f, 0.2f, 0f, 0f, 0.1f, -0.3f, 0f, 0f, 0f, 0f, 0f, 0f, 0.1f),
			QuirkKeyframes.f(8, -1.6f, 0.1f, -0.2f, 0.2f, 0f, 0f, 0.1f, -0.3f, 0f, 0f, 0f, 0f, 0f, 0f, 0.15f), QuirkKeyframes.rest(14) };
	private static final FirstPersonClip FP_OVERCLOCK = FirstPersonClip.of(FirstPersonClip.f(0, 0f, 0f, 0f, 0f, 0f, 0f), FirstPersonClip.f(3, -30f, 15f, -20f, 0.05f, 0.12f, -0.2f),
			FirstPersonClip.f(8, -32f, 15f, 20f, 0.05f, 0.12f, -0.2f), FirstPersonClip.f(14, 0f, 0f, 0f, 0f, 0f, 0f));
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/upgrade.png");
	private static final ResourceLocation GLOW_SKIN = ProjectHeroMod.id("textures/entity/omnitrix/upgrade_glow.png");
	private static final ResourceLocation EXTRAS = ProjectHeroMod.id("textures/entity/omnitrix/upgrade_extras.png");
	private static final DustParticleOptions DRIP = new DustParticleOptions(new Vector3f(0.05f, 0.12f, 0.06f), 0.9f);

	private static final float[] BODY = { 0f, 0f, 0.5f, 0.5f };
	private static final float[] EYE = { 0.5f, 0f, 1f, 0.5f };
	private static final float[] PUDDLE = { 0f, 0.5f, 0.5f, 1f };

	private static final float[][] BEAM = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, 0.3f, 0, 0.3f, 0.3f, 0, -0.3f, 0.2f, 0, 0, 0, 0, 0, 0, 0, -0.1f),
			QuirkKeyframes.f(7, 0.3f, 0, 0.3f, 0.3f, 0, -0.3f, 0.2f, 0, 0, 0, 0, 0, 0, 0, -0.1f), QuirkKeyframes.rest(11) };
	private static final float[][] CHARGE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(4, -0.5f, 0.8f, -0.2f, -0.5f, -0.8f, 0.2f, 0.4f, 0, -0.3f, 0, 0.15f, -0.3f, 0, -0.15f, 0.5f),
			QuirkKeyframes.f(20, -0.6f, 0.9f, -0.2f, -0.6f, -0.9f, 0.2f, 0.45f, 0, -0.35f, 0, 0.15f, -0.35f, 0, -0.15f, 0.55f),
			QuirkKeyframes.f(22, -0.6f, 0.9f, -0.2f, -0.6f, -0.9f, 0.2f, 0.45f, 0, -0.35f, 0, 0.15f, -0.35f, 0, -0.15f, 0.55f) };
	private static final float[][] CHARGED = { QuirkKeyframes.f(0, -0.6f, 0.9f, -0.2f, -0.6f, -0.9f, 0.2f, 0.45f, 0, -0.35f, 0, 0.15f, -0.35f, 0, -0.15f, 0.55f),
			QuirkKeyframes.f(3, 0.6f, 0, 0.6f, 0.6f, 0, -0.6f, -0.1f, 0, 0.3f, 0, 0.1f, -0.4f, 0, -0.1f, -0.2f),
			QuirkKeyframes.f(12, 0.6f, 0, 0.6f, 0.6f, 0, -0.6f, -0.1f, 0, 0.3f, 0, 0.1f, -0.4f, 0, -0.1f, -0.2f), QuirkKeyframes.rest(18) };
	private static final float[][] GRAB = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.55f, -0.2f, 0, -1.55f, 0.2f, 0, 0.25f, 0, -0.3f, 0, 0, 0.3f, 0, 0, 0),
			QuirkKeyframes.f(10, -1.5f, -0.25f, 0, -1.5f, 0.25f, 0, 0.25f, 0, -0.3f, 0, 0, 0.3f, 0, 0, 0), QuirkKeyframes.rest(14) };
	private static final float[][] LASH = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -2.6f, 0.3f, 0.4f, 0.2f, 0, 0, -0.1f, 0.3f, 0, 0, 0, 0, 0, 0, -0.2f),
			QuirkKeyframes.f(5, -1.5f, -0.1f, 0, 0.3f, 0, 0, 0.3f, -0.3f, 0.2f, 0, 0, -0.3f, 0, 0, 0.1f),
			QuirkKeyframes.f(8, -0.4f, -0.3f, 0, 0.2f, 0, 0, 0.1f, 0, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.rest(12) };
	private static final float[][] SLIDE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -2.9f, 0, 0.2f, -2.9f, 0, -0.2f, 1.25f, 0, 0.9f, 0, 0.1f, 0.9f, 0, -0.1f, -1.2f),
			QuirkKeyframes.f(40, -2.9f, 0, 0.2f, -2.9f, 0, -0.2f, 1.25f, 0, 0.9f, 0, 0.1f, 0.9f, 0, -0.1f, -1.2f), QuirkKeyframes.rest(44) };
	private static final float[][] GRAPPLE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -2.5f, -0.2f, 0, 0.4f, 0, 0, 0.15f, 0, -0.2f, 0, 0, 0.3f, 0, 0, -0.3f),
			QuirkKeyframes.f(12, -2.5f, -0.2f, 0, 0.6f, 0, -0.3f, 0.3f, 0, 0.5f, 0, 0, 0.3f, 0, 0, -0.3f), QuirkKeyframes.rest(16) };
	private static final float[][] OVERLOAD = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.6f, 0.1f, 0, -1.2f, 0.8f, 0, 0.15f, -0.2f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.f(10, -1.55f, 0.1f, 0, -1.15f, 0.8f, 0, 0.15f, -0.2f, 0, 0, 0, 0, 0, 0, 0), QuirkKeyframes.rest(14) };
	private static final float[][] EMP = { QuirkKeyframes.rest(0), QuirkKeyframes.fc(3, -0.6f, 0.6f, -0.3f, -0.6f, -0.6f, 0.3f, 0.4f, 0, -0.4f, 0, 0.2f, -0.4f, 0, -0.2f, 0.4f, 0.6f),
			QuirkKeyframes.fc(6, -0.3f, 0, 1.8f, -0.3f, 0, -1.8f, -0.2f, 0, 0, 0, 0.35f, 0, 0, -0.35f, -0.5f, 0),
			QuirkKeyframes.restC(16) };
	private static final float[][] MERGE = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.4f, -0.3f, 0, -1.4f, 0.3f, 0, 0.5f, 0, -0.5f, 0, 0, 0.4f, 0, 0, 0.3f),
			QuirkKeyframes.f(9, -1.6f, -0.1f, 0, -1.6f, 0.1f, 0, 0.7f, 0, -0.6f, 0, 0, 0.5f, 0, 0, 0.4f), QuirkKeyframes.rest(12) };
	private static final float[][] REPAIR = { QuirkKeyframes.rest(0), QuirkKeyframes.f(3, -1.0f, -0.5f, 0, -1.1f, 0.6f, 0, 0.2f, 0, 0, 0, 0, 0, 0, 0, 0.5f),
			QuirkKeyframes.f(14, -1.05f, -0.45f, 0, -1.15f, 0.65f, 0, 0.2f, 0, 0, 0, 0, 0, 0, 0, 0.5f), QuirkKeyframes.rest(18) };
	private static final float[][] JOLT = { QuirkKeyframes.rest(0), QuirkKeyframes.f(2, -1.7f, 0.25f, 0, 0.4f, 0, 0, 0.15f, -0.4f, 0, 0, 0, 0, 0, 0, 0),
			QuirkKeyframes.rest(8) };

	private static final FirstPersonClip FP_BEAM = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 20, -10, 0, -0.05f, -0.15f, 0.05f),
			FirstPersonClip.f(7, 20, -10, 0, -0.05f, -0.15f, 0.05f), FirstPersonClip.f(11, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_CHARGE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -15, 35, 0, 0.2f, 0.0f, 0.05f),
			FirstPersonClip.f(22, -18, 38, 0, 0.22f, 0.0f, 0.05f)).both();
	private static final FirstPersonClip FP_REACH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -25, 22, 0, 0.15f, 0.12f, -0.35f),
			FirstPersonClip.f(10, -25, 22, 0, 0.15f, 0.12f, -0.35f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_LASH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -75, 10, 10, 0.05f, 0.45f, 0.2f),
			FirstPersonClip.f(5, -20, 25, 0, 0.15f, 0.1f, -0.35f), FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_GRAPPLE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -55, 20, 0, 0.12f, 0.35f, -0.25f),
			FirstPersonClip.f(12, -55, 20, 0, 0.12f, 0.35f, -0.25f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_POINT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -25, 25, 0, 0.18f, 0.12f, -0.3f),
			FirstPersonClip.f(10, -25, 25, 0, 0.18f, 0.12f, -0.3f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_WIDE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -20, -10, 0, -0.05f, 0.1f, 0),
			FirstPersonClip.f(6, -35, -40, 30, -0.25f, 0.2f, 0), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_DIVE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -60, 10, 0, 0.1f, 0.3f, -0.3f),
			FirstPersonClip.f(40, -60, 10, 0, 0.1f, 0.3f, -0.3f), FirstPersonClip.f(44, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_CUP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -20, 30, 0, 0.18f, 0.12f, -0.05f),
			FirstPersonClip.f(14, -20, 30, 0, 0.18f, 0.12f, -0.05f), FirstPersonClip.f(18, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_JOLT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -20, 25, 0, 0.15f, 0.08f, -0.18f),
			FirstPersonClip.f(8, 0, 0, 0, 0, 0, 0));

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	/** How far (pixels) the right arm stretches out right now (the grab, the lash, the grapple), 0 for not at all. */
	private static float stretch(AbstractClientPlayer p, float partial) {
		OmnitrixState s = Omnitrix.state(p);
		float t = p.level().getGameTime() + partial - s.animStart;
		float reach = switch (s.animId) {
			case UpgradeKit.ANIM_GRAB -> 10f;
			case UpgradeKit.ANIM_LASH -> 18f;
			case UpgradeKit.ANIM_GRAPPLE -> 22f;
			case UpgradeKit.ANIM_MERGE -> 6f;
			default -> 0f;
		};
		if (reach <= 0f || t < 0 || t > 12) {
			return 0f;
		}
		float k = t < 3 ? t / 3f : t < 8 ? 1f : 1f - (t - 8) / 4f;
		return reach * Mth.clamp(k, 0f, 1f);
	}

	/** The stretched arm; each buffer type is fetched right before its draws (fetching another type ends the previous one). */
	private static void arm(PoseStack pose, MultiBufferSource buffers, boolean right, float len, int light) {
		float x0 = right ? -2.6f : -1.4f;
		float x1 = right ? 0.6f : 2.6f;
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(EXTRAS));
		AlienParts.box(pose, vc, x0 + 0.3f, 9.5f, -1.3f, x1 - 0.3f, 10f + len, 1.3f, BODY[0], BODY[1], BODY[2], BODY[3], 0xFFFFFFFF, light);
		AlienParts.box(pose, vc, x0, 9.5f + len, -1.8f, x1, 12.5f + len, 1.8f, BODY[0], BODY[1], BODY[2], BODY[3], 0xFFFFFFFF, light);
		VertexConsumer glow = buffers.getBuffer(RenderType.eyes(EXTRAS));
		AlienParts.box(pose, glow, (x0 + x1) / 2 - 0.25f, 10f, -1.35f, (x0 + x1) / 2 + 0.25f, 10f + len, -1.3f, EYE[0], EYE[1], EYE[0] + 0.05f, EYE[1] + 0.05f,
				0xFFFFFFFF, LightTexture.FULL_BRIGHT);
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		// the circuits glow (the whole body again, emissive, only the lines and the eye lit), pulsing slowly
		int g = (int) (200 + 55 * Mth.sin(age * 0.12f));
		int colour = 0xFF000000 | (g << 16) | (g << 8) | g;
		model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(GLOW_SKIN)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, colour);
		if ((Omnitrix.state(p).kitFlags & UpgradeKit.F_SLIDE) != 0) {
			// the slick he slides on
			VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(EXTRAS));
			AlienParts.on(pose, model.body, () -> AlienParts.box(pose, vc, -9f, 23.7f, -9f, 9f, 23.95f, 9f, PUDDLE[0], PUDDLE[1], PUDDLE[2], PUDDLE[3], 0xE0FFFFFF,
					light));
		}
		float len = stretch(p, partial);
		if (len > 0.1f) {
			AlienParts.on(pose, model.rightArm, () -> arm(pose, buffers, true, len, light));
		}
		// v0.17.1 (user's minecraftskins.com Upgrade skin): the skin has its own eye -- it glows through the glow sheet above, so
		// the old round eye prop is gone
	}

	@Override
	public void renderFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, ModelPart arm, boolean right) {
		float len = right ? stretch(p, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)) : 0f;
		if (len > 0.1f) {
			arm(pose, buffers, true, len, light);
		}
		// the circuit line down the arm, glowing
		VertexConsumer eyes = buffers.getBuffer(RenderType.eyes(EXTRAS));
		AlienParts.box(pose, eyes, right ? -1.25f : 0.75f, -1.5f, -2.05f, right ? -0.75f : 1.25f, 9.5f, -2.0f, EYE[0], EYE[1], EYE[0] + 0.05f, EYE[1] + 0.05f,
				0xFFFFFFFF, LightTexture.FULL_BRIGHT);
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		float[][] clip = switch (animId) {
			case UpgradeKit.ANIM_BEAM -> BEAM;
			case UpgradeKit.ANIM_CHARGE -> CHARGE;
			case UpgradeKit.ANIM_CHARGED -> CHARGED;
			case UpgradeKit.ANIM_GRAB -> GRAB;
			case UpgradeKit.ANIM_LASH -> LASH;
			case UpgradeKit.ANIM_SLIDE -> null;
			case UpgradeKit.ANIM_GRAPPLE -> GRAPPLE;
			case UpgradeKit.ANIM_OVERLOAD -> OVERLOAD;
			case UpgradeKit.ANIM_EMP -> EMP;
			case UpgradeKit.ANIM_MERGE -> MERGE;
			case UpgradeKit.ANIM_REPAIR -> REPAIR;
			case UpgradeKit.ANIM_JOLT -> JOLT;
			case UpgradeKit.ANIM_OVERCLOCK -> OVERCLOCK;
			default -> null;
		};
		if ((Omnitrix.state(p).kitFlags & UpgradeKit.F_SLIDE) != 0) {
			// a slick: flat out, arms ahead
			QuirkKeyframes.play(m, SLIDE, Math.min(ticks, 20f), 1f);
			return true;
		}
		boolean played = clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
		if (!played) {
			// liquid: a slow sway
			m.rightArm.zRot += Mth.sin(age * 0.09f) * 0.03f;
			m.leftArm.zRot -= Mth.sin(age * 0.09f + 1f) * 0.03f;
		}
		return played;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case UpgradeKit.ANIM_BEAM, UpgradeKit.ANIM_CHARGED -> FP_BEAM;
			case UpgradeKit.ANIM_CHARGE -> FP_CHARGE;
			case UpgradeKit.ANIM_GRAB, UpgradeKit.ANIM_MERGE -> FP_REACH;
			case UpgradeKit.ANIM_LASH -> FP_LASH;
			case UpgradeKit.ANIM_GRAPPLE -> FP_GRAPPLE;
			case UpgradeKit.ANIM_OVERLOAD -> FP_POINT;
			case UpgradeKit.ANIM_EMP -> FP_WIDE;
			case UpgradeKit.ANIM_SLIDE -> FP_DIVE;
			case UpgradeKit.ANIM_REPAIR -> FP_CUP;
			case UpgradeKit.ANIM_JOLT -> FP_JOLT;
			case UpgradeKit.ANIM_OVERCLOCK -> FP_OVERCLOCK;
			default -> null;
		};
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		var r = p.getRandom();
		if (r.nextInt(8) == 0) {
			p.level().addParticle(DRIP, p.getX() + (r.nextDouble() - 0.5) * 0.5, p.getY() + r.nextDouble() * p.getBbHeight() * 0.8,
					p.getZ() + (r.nextDouble() - 0.5) * 0.5, 0, -0.05, 0);
		}
		int flags = Omnitrix.state(p).kitFlags;
		if ((flags & UpgradeKit.F_CHARGING) != 0 && r.nextInt(2) == 0) {
			var eye = p.getEyePosition().add(p.getLookAngle().scale(0.5));
			p.level().addParticle(ParticleTypes.ELECTRIC_SPARK, eye.x, eye.y, eye.z, (r.nextDouble() - 0.5) * 0.2, (r.nextDouble() - 0.5) * 0.2,
					(r.nextDouble() - 0.5) * 0.2);
		}
	}

	@Override
	public float[] badge() {
		// v0.17.1 (user: "position our alien badges more on top of those alien badges"): over the skin's own symbol (now painted
		// out), the middle of his chest
		return new float[] { 0f, 5.0f, -2.06f, 2.6f };
	}
}
