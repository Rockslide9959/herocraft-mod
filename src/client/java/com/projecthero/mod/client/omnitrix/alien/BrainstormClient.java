package com.projecthero.mod.client.omnitrix.alien;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.AlienParts;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.omnitrix.alien.brainstorm.BrainstormFx;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.brainstorm.BrainstormKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

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
 * v0.17.2 (Recalibrated Omnitrix, basic version): Brainstorm's client kit -- the player body at 0.9 with his generated crab skin,
 * plus extras from {@code brainstorm_extras.png}: the domed head shell in two halves that swing open on the pink brain while he
 * uses his powers (the brain crackles), a back shell, big pincer claws on both hands and two pairs of small crab legs at his sides.
 * A pose and a first-person clip for every move; his lightning is {@link BrainstormFx}.
 */
public final class BrainstormClient implements AlienClientKit {
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/brainstorm.png");
	private static final ResourceLocation EXTRAS = ProjectHeroMod.id("textures/entity/omnitrix/brainstorm_extras.png");
	/** Extras sheet rectangles (gen_brainstorm.js), in 0..1 UVs. */
	private static final float[] SHELL = uv(0, 0, 16, 16), BRAIN = uv(16, 0, 16, 16), CLAW = uv(32, 0, 16, 16), LEG = uv(48, 0, 8, 8),
			SHELL_IN = uv(0, 16, 16, 16);

	private static float[] uv(int x, int y, int w, int h) {
		return new float[] { x / 64f, y / 64f, (x + w) / 64f, (y + h) / 64f };
	}

	public static void register() {
		AlienClients.register(Aliens.BRAINSTORM.id(), new BrainstormClient());
		BrainstormFx.register();
	}

	// ---------------------------------------------------------------- poses (QuirkKeyframes: tick, rArm xyz, lArm xyz, body xy, rLeg xyz, lLeg xyz, headX)
	private static float[] f(float t, float rax, float ray, float raz, float lax, float lay, float laz, float bx, float hx) {
		return QuirkKeyframes.f(t, rax, ray, raz, lax, lay, laz, bx, 0, 0, 0, 0, 0, 0, 0, hx);
	}

	private static final float[][] BOLT = { QuirkKeyframes.rest(0), f(2, -1.6f, 0.1f, 0, 0.2f, 0, 0, 0.05f, 0), f(6, -1.55f, 0.1f, 0, 0.2f, 0, 0, 0.05f, 0),
			QuirkKeyframes.rest(10) };
	private static final float[][] CHAIN = { QuirkKeyframes.rest(0), f(3, -1.5f, -0.35f, 0, -1.5f, 0.35f, 0, 0.1f, 0),
			f(10, -1.45f, -0.25f, 0, -1.45f, 0.25f, 0, 0.1f, 0), QuirkKeyframes.rest(15) };
	private static final float[][] PINCER = { QuirkKeyframes.rest(0), f(2, -2.3f, 0, 0.3f, 0, 0, 0, -0.1f, 0), f(5, -1.1f, 0, 0, 0, 0, 0, 0.25f, 0.2f),
			QuirkKeyframes.rest(11) };
	private static final float[][] ORB = { QuirkKeyframes.rest(0), f(3, -1.3f, -0.45f, 0, -1.3f, 0.45f, 0, 0, 0), f(7, -1.6f, -0.2f, 0, -1.6f, 0.2f, 0, 0.1f, 0),
			QuirkKeyframes.rest(13) };
	private static final float[][] HOP = { QuirkKeyframes.rest(0),
			QuirkKeyframes.f(2, 0.6f, 0, 0.4f, 0.6f, 0, -0.4f, 0.2f, 0, -0.6f, 0, 0.1f, -0.6f, 0, -0.1f, 0),
			QuirkKeyframes.f(12, 0.4f, 0, 0.5f, 0.4f, 0, -0.5f, 0.1f, 0, -0.4f, 0, 0.1f, -0.4f, 0, -0.1f, 0), QuirkKeyframes.rest(16) };
	private static final float[][] DASH = { QuirkKeyframes.rest(0), f(1, 0.9f, 0, 0.2f, 0.9f, 0, -0.2f, 0.55f, -0.3f), f(6, 0.9f, 0, 0.2f, 0.9f, 0, -0.2f, 0.5f, -0.3f),
			QuirkKeyframes.rest(10) };
	private static final float[][] DISCHARGE = { QuirkKeyframes.rest(0), f(3, -0.4f, 0, 1.5f, -0.4f, 0, -1.5f, -0.15f, -0.4f),
			f(9, -0.5f, 0, 1.4f, -0.5f, 0, -1.4f, -0.15f, -0.4f), QuirkKeyframes.rest(14) };
	private static final float[][] MEGA = { QuirkKeyframes.rest(0), f(4, -1.6f, -0.2f, 0, -1.6f, 0.2f, 0, -0.1f, 0), f(20, -1.55f, -0.15f, 0, -1.55f, 0.15f, 0, -0.1f, 0),
			f(23, -1.7f, -0.1f, 0, -1.7f, 0.1f, 0, -0.3f, -0.1f), f(30, -1.6f, -0.1f, 0, -1.6f, 0.1f, 0, -0.1f, 0), QuirkKeyframes.rest(35) };
	private static final float[][] ANALYZE = { QuirkKeyframes.rest(0), f(3, -2.4f, -0.6f, 0, 0.1f, 0, 0, 0, -0.1f), f(20, -2.4f, -0.6f, 0, 0.1f, 0, 0, 0, -0.1f),
			QuirkKeyframes.rest(26) };
	private static final float[][] FIELD = { QuirkKeyframes.rest(0), f(3, -0.2f, 0, 1.3f, -0.2f, 0, -1.3f, 0, -0.2f), f(10, -0.25f, 0, 1.25f, -0.25f, 0, -1.25f, 0, -0.2f),
			QuirkKeyframes.rest(16) };
	private static final float[][] STORM = { QuirkKeyframes.rest(0), f(4, -2.9f, 0, 0.35f, -2.9f, 0, -0.35f, -0.2f, -0.6f),
			f(30, -2.95f, 0, 0.3f, -2.95f, 0, -0.3f, -0.2f, -0.6f), QuirkKeyframes.rest(36) };
	private static final float[][] CAGE = { QuirkKeyframes.rest(0), f(2, -1.6f, 0.35f, 0, -0.3f, 0, 0, 0.1f, 0), f(6, -2.3f, -0.2f, 0, -0.3f, 0, 0, 0.05f, -0.1f),
			QuirkKeyframes.rest(13) };

	// ---------------------------------------------------------------- first person (tick, pitch, yaw, roll, x, y, z)
	private static final FirstPersonClip FP_POINT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -18, 12, 0, 0.08f, 0.15f, -0.25f),
			FirstPersonClip.f(7, -16, 10, 0, 0.08f, 0.14f, -0.24f), FirstPersonClip.f(11, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_BOTH = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -15, 18, 0, 0.12f, 0.12f, -0.25f),
			FirstPersonClip.f(10, -14, 16, 0, 0.12f, 0.12f, -0.24f), FirstPersonClip.f(15, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_SNAP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, -60, 10, 10, 0.05f, 0.45f, 0.1f),
			FirstPersonClip.f(5, 15, 15, 0, 0.1f, -0.15f, -0.35f), FirstPersonClip.f(11, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_CHARGE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -10, 22, 0, 0.16f, 0.1f, -0.15f),
			FirstPersonClip.f(20, -12, 22, 0, 0.17f, 0.12f, -0.16f), FirstPersonClip.f(23, -20, 18, 0, 0.12f, 0.15f, -0.35f), FirstPersonClip.f(35, 0, 0, 0, 0, 0, 0))
			.both();
	private static final FirstPersonClip FP_WIDE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -25, -25, 20, -0.15f, 0.25f, 0.05f),
			FirstPersonClip.f(9, -25, -25, 20, -0.15f, 0.25f, 0.05f), FirstPersonClip.f(14, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_UP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -75, 10, 0, 0.05f, 0.6f, 0.1f),
			FirstPersonClip.f(30, -78, 10, 0, 0.05f, 0.62f, 0.1f), FirstPersonClip.f(36, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_TEMPLE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -55, 40, 15, 0.25f, 0.45f, 0.1f),
			FirstPersonClip.f(20, -55, 40, 15, 0.25f, 0.45f, 0.1f), FirstPersonClip.f(26, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_LOW = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(2, 35, 10, 0, 0.05f, -0.4f, 0.2f),
			FirstPersonClip.f(10, 35, 10, 0, 0.05f, -0.4f, 0.2f), FirstPersonClip.f(15, 0, 0, 0, 0, 0, 0)).both();

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	/** Whether his shell is open on the brain right now (a power pose running, the field up, the bolt charging). */
	private static float shellOpen(AbstractClientPlayer p, float partial) {
		OmnitrixState s = Omnitrix.state(p);
		if ((s.kitFlags & (BrainstormKit.F_FIELD | BrainstormKit.F_CHARGE)) != 0) {
			return 1f;
		}
		if (s.animId <= 0 || s.animId >= 1000 || s.animId == BrainstormKit.ANIM_HOP || s.animId == BrainstormKit.ANIM_DASH) {
			return 0f;
		}
		float t = p.level().getGameTime() + partial - s.animStart;
		if (t < 0f || t > 40f) {
			return 0f;
		}
		return Mth.clamp(t / 3f, 0f, 1f) * Mth.clamp((40f - t) / 8f, 0f, 1f);
	}

	@Override
	public void renderExtras(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, PlayerModel<AbstractClientPlayer> model, float partial,
			float age) {
		float open = shellOpen(p, partial);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(EXTRAS));
		int white = 0xFFFFFFFF;
		// the head: two shell halves over the top that swing open on the brain
		AlienParts.on(pose, model.head, () -> {
			for (int side = -1; side <= 1; side += 2) {
				pose.pushPose();
				pose.translate(side * 4.5f / 16f, -7f / 16f, 0);
				pose.mulPose(Axis.ZP.rotation(side * open * 1.0f));
				float x0 = side < 0 ? 0f : -5f, x1 = side < 0 ? 5f : 0f;
				AlienParts.box(pose, vc, x0, -4.5f, -5.2f, x1, 0.5f, 5.2f, SHELL[0], SHELL[1], SHELL[2], SHELL[3], white, light);
				pose.popPose();
			}
			if (open > 0.05f) {
				AlienParts.box(pose, vc, -3.8f, -11.0f, -3.8f, 3.8f, -7.5f, 3.8f, BRAIN[0], BRAIN[1], BRAIN[2], BRAIN[3], white, light);
			}
		});
		// the back shell
		AlienParts.on(pose, model.body, () -> {
			AlienParts.box(pose, vc, -5.5f, -0.5f, 1.8f, 5.5f, 11f, 4.6f, SHELL[0], SHELL[1], SHELL[2], SHELL[3], white, light);
			AlienParts.box(pose, vc, -4.5f, 0.5f, 4.6f, 4.5f, 9.5f, 5.6f, SHELL[0], SHELL[1], SHELL[2], SHELL[3], white, light);
			// two pairs of small crab legs at his sides
			for (int side = -1; side <= 1; side += 2) {
				for (int k = 0; k < 2; k++) {
					pose.pushPose();
					pose.translate(side * 4f / 16f, (7.5f + k * 2.5f) / 16f, (0.5f + k) / 16f);
					float wig = Mth.sin(age * 0.35f + k * 1.6f + side) * 0.15f;
					pose.mulPose(Axis.ZP.rotation(side * (-0.9f + wig)));
					pose.mulPose(Axis.YP.rotation(side * (0.35f - k * 0.5f)));
					AlienParts.box(pose, vc, -0.6f, 0f, -0.6f, 0.6f, 5.5f, 0.6f, LEG[0], LEG[1], LEG[2], LEG[3], white, light);
					pose.popPose();
				}
			}
		});
		// the claws: two jaws on each hand that snap with the pincer pose
		OmnitrixState s = Omnitrix.state(p);
		float snap = 0.35f;
		if (s.animId == BrainstormKit.ANIM_PINCER) {
			float t = p.level().getGameTime() + partial - s.animStart;
			snap = t < 4f ? 0.6f : Mth.clamp((t - 4f) / 2f, 0f, 1f) < 1f ? 0.05f : 0.35f;
		}
		final float jaw = snap;
		for (ModelPart arm : new ModelPart[] { model.rightArm, model.leftArm }) {
			AlienParts.on(pose, arm, () -> {
				float cx = arm == model.rightArm ? -1f : 1f;
				AlienParts.box(pose, vc, cx - 2.6f, 7.5f, -2.6f, cx + 2.6f, 11f, 2.6f, SHELL[0], SHELL[1], SHELL[2], SHELL[3], white, light);
				for (int j = -1; j <= 1; j += 2) {
					pose.pushPose();
					pose.translate(cx / 16f, 11f / 16f, j * 1.2f / 16f);
					pose.mulPose(Axis.XP.rotation(j * jaw));
					AlienParts.box(pose, vc, -1.8f, 0f, -1.0f, 1.8f, 6.5f, 1.0f, CLAW[0], CLAW[1], CLAW[2], CLAW[3], white, light);
					pose.popPose();
				}
			});
		}
		// the brain crackles while open
		if (open > 0.3f) {
			AlienParts.on(pose, model.head, () -> {
				pose.pushPose();
				pose.translate(0, -9.3f / 16f, 0);
				VertexConsumer glow = PyroDraw.additive(buffers);
				float flick = 0.5f + 0.5f * Mth.sin(age * 2.7f);
				AlienParts.box(pose, glow, -4.3f, -2.2f, -4.3f, 4.3f, 2.2f, 4.3f, 0f, 0f, 1f, 1f, PyroDraw.dim(0x8A6A20, open * flick), LightTexture.FULL_BRIGHT);
				pose.popPose();
			});
		}
	}

	@Override
	public void renderFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, ModelPart arm, boolean right) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(EXTRAS));
		float cx = right ? -1f : 1f;
		AlienParts.box(pose, vc, cx - 2.6f, 7.5f, -2.6f, cx + 2.6f, 11f, 2.6f, SHELL[0], SHELL[1], SHELL[2], SHELL[3], 0xFFFFFFFF, light);
		for (int j = -1; j <= 1; j += 2) {
			pose.pushPose();
			pose.translate(cx / 16f, 11f / 16f, j * 1.2f / 16f);
			pose.mulPose(Axis.XP.rotation(j * 0.3f));
			AlienParts.box(pose, vc, -1.8f, 0f, -1.0f, 1.8f, 6.5f, 1.0f, CLAW[0], CLAW[1], CLAW[2], CLAW[3], 0xFFFFFFFF, light);
			pose.popPose();
		}
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		float[][] clip = switch (animId) {
			case BrainstormKit.ANIM_BOLT -> BOLT;
			case BrainstormKit.ANIM_CHAIN -> CHAIN;
			case BrainstormKit.ANIM_PINCER -> PINCER;
			case BrainstormKit.ANIM_ORB -> ORB;
			case BrainstormKit.ANIM_HOP -> HOP;
			case BrainstormKit.ANIM_DASH -> DASH;
			case BrainstormKit.ANIM_DISCHARGE -> DISCHARGE;
			case BrainstormKit.ANIM_MEGA -> MEGA;
			case BrainstormKit.ANIM_ANALYZE -> ANALYZE;
			case BrainstormKit.ANIM_FIELD -> FIELD;
			case BrainstormKit.ANIM_STORM -> STORM;
			case BrainstormKit.ANIM_CAGE -> CAGE;
			default -> null;
		};
		boolean played = clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
		if (!played) {
			// a crab's stance: claws held a little out and forward
			m.rightArm.xRot -= 0.25f;
			m.leftArm.xRot -= 0.25f;
			m.rightArm.zRot += 0.15f;
			m.leftArm.zRot -= 0.15f;
		}
		return played;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case BrainstormKit.ANIM_BOLT, BrainstormKit.ANIM_CAGE -> FP_POINT;
			case BrainstormKit.ANIM_CHAIN, BrainstormKit.ANIM_ORB -> FP_BOTH;
			case BrainstormKit.ANIM_PINCER -> FP_SNAP;
			case BrainstormKit.ANIM_MEGA -> FP_CHARGE;
			case BrainstormKit.ANIM_DISCHARGE, BrainstormKit.ANIM_FIELD -> FP_WIDE;
			case BrainstormKit.ANIM_STORM -> FP_UP;
			case BrainstormKit.ANIM_ANALYZE -> FP_TEMPLE;
			case BrainstormKit.ANIM_HOP, BrainstormKit.ANIM_DASH -> FP_LOW;
			default -> null;
		};
	}

	@Override
	public void tick(AbstractClientPlayer p) {
		// a few sparks off the open brain (accents only; the bolts themselves are rendered)
		if (shellOpen(p, 0f) > 0.5f && p.getRandom().nextInt(3) == 0) {
			var r = p.getRandom();
			p.level().addParticle(ParticleTypes.ELECTRIC_SPARK, p.getX() + (r.nextDouble() - 0.5) * 0.5, p.getY() + p.getBbHeight() + 0.1,
					p.getZ() + (r.nextDouble() - 0.5) * 0.5, (r.nextDouble() - 0.5) * 0.2, 0.1, (r.nextDouble() - 0.5) * 0.2);
		}
	}

	@Override
	public float[] badge() {
		return new float[] { 0f, 3.5f, -2.1f, 3.0f };
	}
}
