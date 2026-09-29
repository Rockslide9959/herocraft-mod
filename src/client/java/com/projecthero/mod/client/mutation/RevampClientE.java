package com.projecthero.mod.client.mutation;

import static com.projecthero.mod.client.mutation.MutationPose.AIM;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.power.p16.SpiderClimbingHandlers;
import com.projecthero.mod.hero.power.p17.ElasticityHandlers;
import com.projecthero.mod.hero.power.p22.PlantEntities;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.22 mutation revamp, batch E -- client-side registration (poses, overlays, entity renderers, particles,
 * payload receivers) for:
 * <ul>
 * <li>16 Spider Climbing / Adhesion -- web-lined palms/soles while gripping, red eyes in Predator Rush, a pulsing
 *     Spider-Sense halo, and (owner only) a faint red threat glow over nearby hostiles</li>
 * <li>17 Elasticity -- the arm visibly stretches (the player's own skin, stretched) toward the target on every
 *     stretch / grab / slingshot move, giant fists for the Hammer Fist, the Rubber Shield balloon, the glide canopy,
 *     plus the client half of Parachute Glide's steering</li>
 * <li>18 Density Manipulation -- a thin shell tinted blue when light / orange when heavy, a cyan shimmer while
 *     intangible, a glowing fist while Crushing Touch is armed</li>
 * <li>22 Plant Manipulation -- the bark-and-leaf skin of Nature's Blessing, green eyes while Overgrowth gathers, the
 *     Thorn Sentry / thorn renderers</li>
 * <li>27 Size Manipulation -- form, ride and carry poses (the size change itself is the attribute ease)</li>
 * </ul>
 */
public final class RevampClientE {
	private static final ResourceLocation WEB = ProjectHeroMod.id("textures/entity/mutation/p16_web.png");
	private static final ResourceLocation RUBBER = ProjectHeroMod.id("textures/entity/mutation/p17_rubber.png");
	private static final ResourceLocation DENSITY = ProjectHeroMod.id("textures/entity/mutation/p18_shell.png");
	private static final ResourceLocation BARK = ProjectHeroMod.id("textures/entity/mutation/p22_bark.png");

	private static final float[] Z = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

	/** Stretched-arm segments / fists: [right wide, right slim, left wide, left slim]. */
	private static ModelPart[] armSeg;
	private static ModelPart[] armFist;
	private static ModelPart senseHalo;
	private static ModelPart balloon;
	private static ModelPart canopy;
	private static ModelPart fistGlow;

	private RevampClientE() {
	}

	/** Called once from {@code ProjectHeroModClient.onInitializeClient}, after the shared pose library. */
	public static void init() {
		registerPoses();
		registerOverlays();
		EntityRendererRegistry.register(PlantEntities.THORN_SENTRY, BatchERenderers.ThornSentryRenderer::new);
		EntityRendererRegistry.register(PlantEntities.THORN, BatchERenderers.ThornRenderer::new);
		ClientTickEvents.END_CLIENT_TICK.register(RevampClientE::clientTick);
	}

	// ================================================================== poses

	private static float[] at(int tick, float[] pose) {
		float[] f = pose.clone();
		f[0] = tick;
		return f;
	}

	private static float[] zero(int tick) {
		return at(tick, Z);
	}

	/** {rArmX, rArmY, rArmZ, lArmX, lArmY, lArmZ, bodyX, bodyY, rLegX, lLegX, headX} -- see {@link MutationPose}. */
	private static float[] p(float rX, float rY, float rZ, float lX, float lY, float lZ, float bX, float bY,
			float rL, float lL, float h) {
		return new float[] { 0, rX, rY, rZ, lX, lY, lZ, bX, bY, rL, lL, h };
	}

	private static void registerPoses() {
		// ---- 16 Spider Adhesion ----
		float[] palmBack = p(0.7f, 0.25f, 0.2f, -0.4f, 0.3f, 0, 0.1f, 0.5f, 0.3f, -0.3f, 0);
		float[] palmOut = p(AIM, AIM, 0, 0.4f, 0.2f, -0.1f, 0.2f, -0.45f, -0.4f, 0.4f, 0);
		MutationPose.register("p16.palm_strike", new float[][] { zero(0), at(3, palmBack), at(5, palmOut), at(10, palmOut), zero(14) });
		float[] pounceCrouch = p(0.35f, 0, 0.35f, 0.35f, 0, -0.35f, 0.55f, 0, 0.8f, 0.8f, 0.3f);
		float[] pounceSpring = p(-2.7f, 0, 0.45f, -2.7f, 0, -0.45f, -0.1f, 0, -0.3f, 0.3f, -0.3f);
		float[] pounceClaw = p(-1.75f, -0.35f, 0.35f, -1.75f, 0.35f, -0.35f, 0.25f, 0, 0.4f, 0.5f, 0.1f);
		MutationPose.register("p16.pounce", new float[][] { zero(0), at(3, pounceCrouch), at(6, pounceSpring), at(11, pounceClaw),
				at(18, pounceClaw), zero(24) });
		float[] roar = p(-0.55f, 0, 1.15f, -0.55f, 0, -1.15f, 0.55f, 0, 0.55f, -0.2f, -0.35f);
		float[] roar2 = p(-0.65f, 0, 1.25f, -0.65f, 0, -1.25f, 0.6f, 0, 0.55f, -0.2f, -0.45f);
		MutationPose.register("p16.crouch_roar", new float[][] { zero(0), at(5, roar), at(12, roar2), at(20, roar2), zero(28) });
		float[] cling = p(-2.8f, 0, 0.4f, -2.8f, 0, -0.4f, -0.1f, 0, -0.25f, 0.2f, -0.3f);
		MutationPose.register("p16.cling", new float[][] { zero(0), at(4, cling), at(10, cling), zero(15) });
		float[] sense = p(-2.3f, -0.75f, 0.25f, 0.15f, 0, -0.2f, 0.05f, 0.2f, 0, 0, -0.2f);
		MutationPose.register("p16.sense", new float[][] { zero(0), at(3, sense), at(12, sense), zero(16) });
		float[] dodge = p(-0.3f, 0, 1.0f, -0.3f, 0, -1.0f, 0.2f, 0.65f, -0.45f, 0.45f, 0.1f);
		MutationPose.register("p16.dodge", new float[][] { zero(0), at(2, dodge), at(7, dodge), zero(12) });
		float[] biteBack = p(0.8f, 0, 0.35f, 0.8f, 0, -0.35f, 0.15f, 0, 0.25f, -0.25f, -0.35f);
		float[] biteIn = p(0.55f, 0, 0.55f, 0.55f, 0, -0.55f, 0.55f, 0, -0.35f, 0.35f, 0.55f);
		MutationPose.register("p16.bite", new float[][] { zero(0), at(3, biteBack), at(5, biteIn), at(9, biteIn), zero(14) });

		// ---- 17 Elasticity ----
		float[] wind = p(0.95f, 0.3f, 0.2f, -1.2f, 0.2f, 0, 0.1f, 0.55f, 0.25f, -0.25f, 0);
		float[] wind2 = p(1.1f, 0.35f, 0.25f, -1.25f, 0.2f, 0, 0.12f, 0.6f, 0.25f, -0.25f, 0);
		MutationPose.registerLoop("p17.windup", 4, new float[][] { zero(0), at(4, wind), at(9, wind2), at(14, wind) });
		float[] stretchR = p(AIM, AIM, 0, 0.35f, 0.2f, -0.1f, 0.15f, -0.4f, -0.35f, 0.35f, 0);
		MutationPose.register("p17.stretch_right", new float[][] { zero(0), at(2, stretchR), at(13, stretchR), zero(17) });
		float[] stretchB = p(AIM, AIM, 0, AIM, AIM, 0, 0.2f, 0, -0.3f, 0.3f, 0);
		MutationPose.register("p17.stretch_both", new float[][] { zero(0), at(2, stretchB), at(13, stretchB), zero(17) });
		float[] hammerUp = p(-3.0f, 0, 0.2f, -3.0f, 0, -0.2f, -0.2f, 0, 0.15f, -0.15f, -0.3f);
		MutationPose.register("p17.hammer", new float[][] { zero(0), at(4, hammerUp), at(7, stretchB), at(18, stretchB), zero(23) });
		float[] reach = p(AIM, AIM, 0, 0.3f, 0, -0.25f, 0.1f, -0.25f, -0.2f, 0.2f, 0);
		float[] reach2 = p(AIM, AIM, 0, 0.35f, 0, -0.3f, 0.12f, -0.3f, -0.2f, 0.2f, 0);
		MutationPose.registerLoop("p17.grab_hold", 3, new float[][] { zero(0), at(3, reach), at(10, reach2), at(17, reach) });
		float[] sling = p(AIM, AIM, 0, 0.6f, 0, -0.5f, 0.35f, 0, 0.45f, 0.7f, 0);
		float[] sling2 = p(AIM, AIM, 0, 0.7f, 0, -0.55f, 0.4f, 0, 0.5f, 0.75f, 0);
		MutationPose.registerLoop("p17.sling", 2, new float[][] { zero(0), at(2, sling), at(6, sling2), at(10, sling) });
		float[] formUp = p(-2.9f, 0, 0.55f, -2.9f, 0, -0.55f, -0.15f, 0, 0.1f, -0.1f, -0.3f);
		MutationPose.register("p17.form_stretch", new float[][] { zero(0), at(6, formUp), at(14, formUp), zero(20) });
		float[] squash = p(-1.0f, -0.75f, 0, -1.0f, 0.75f, 0, 0.4f, 0, 0.6f, 0.6f, 0.25f);
		MutationPose.register("p17.compress", new float[][] { zero(0), at(5, squash), at(12, squash), zero(18) });
		float[] inflate = p(-0.3f, 0, 1.3f, -0.3f, 0, -1.3f, -0.12f, 0, -0.25f, 0.25f, -0.1f);
		float[] inflate2 = p(-0.35f, 0, 1.38f, -0.35f, 0, -1.38f, -0.15f, 0, -0.28f, 0.28f, -0.12f);
		MutationPose.registerLoop("p17.inflate", 4, new float[][] { zero(0), at(4, inflate), at(12, inflate2), at(20, inflate) });
		float[] glide = p(-0.1f, 0, 1.55f, -0.1f, 0, -1.55f, 0.25f, 0, 0.45f, 0.45f, -0.25f);
		float[] glide2 = p(-0.15f, 0, 1.5f, -0.15f, 0, -1.5f, 0.27f, 0, 0.5f, 0.4f, -0.25f);
		MutationPose.registerLoop("p17.glide", 4, new float[][] { zero(0), at(4, glide), at(14, glide2), at(24, glide) });

		// ---- 18 Density ----
		float[] dense = p(0.25f, 0, 0.2f, 0.25f, 0, -0.2f, 0.18f, 0, 0.2f, -0.2f, 0.15f);
		MutationPose.register("p18.dense", new float[][] { zero(0), at(3, dense), at(6, dense), zero(10) });
		float[] light = p(-0.6f, 0, 0.7f, -0.6f, 0, -0.7f, -0.12f, 0, -0.1f, 0.1f, -0.2f);
		MutationPose.register("p18.light", new float[][] { zero(0), at(3, light), at(7, light), zero(11) });
		float[] anchor = p(0.35f, 0, 0.3f, 0.35f, 0, -0.3f, 0.3f, 0, -0.3f, 0.3f, 0.2f);
		float[] anchor2 = p(0.4f, 0, 0.33f, 0.4f, 0, -0.33f, 0.32f, 0, -0.3f, 0.3f, 0.22f);
		MutationPose.registerLoop("p18.anchor", 4, new float[][] { zero(0), at(4, anchor), at(14, anchor2), at(24, anchor) });
		float[] plunge = p(-3.0f, 0, 0.15f, -3.0f, 0, -0.15f, 0.25f, 0, -0.2f, 0.2f, 0.3f);
		MutationPose.register("p18.plunge", new float[][] { zero(0), at(4, plunge), at(34, plunge), zero(38) });
		float[] fistBack = p(0.9f, 0.2f, 0.3f, -0.6f, 0.3f, 0, 0.1f, 0.5f, 0.3f, -0.3f, 0);
		float[] fistClench = p(-1.3f, -0.3f, 0.1f, 0.2f, 0.1f, -0.1f, 0.1f, -0.2f, -0.2f, 0.2f, 0.1f);
		MutationPose.register("p18.charge_fist", new float[][] { zero(0), at(4, fistBack), at(8, fistClench), at(14, fistClench), zero(18) });

		// ---- 22 Plant ----
		float[] swing = p(AIM, AIM, 0, -0.4f, 0, -0.3f, -0.1f, -0.2f, 0.3f, -0.2f, -0.1f);
		MutationPose.register("p22.swing", new float[][] { zero(0), at(2, swing), at(10, swing), zero(14) });
		float[] gather = p(-0.55f, 0, 0.6f, -0.55f, 0, -0.6f, 0.3f, 0, 0.35f, -0.15f, 0.3f);
		float[] gather2 = p(-0.8f, 0, 0.7f, -0.8f, 0, -0.7f, 0.25f, 0, 0.35f, -0.15f, 0.2f);
		MutationPose.registerLoop("p22.gather", 4, new float[][] { zero(0), at(4, gather), at(12, gather2), at(20, gather) });
		float[] cup = p(-1.9f, -0.55f, 0, -1.9f, 0.55f, 0, -0.05f, 0, 0, 0, 0.1f);
		float[] blow = p(AIM, AIM, 0, AIM, AIM, 0, 0.2f, 0, -0.25f, 0.25f, 0);
		MutationPose.register("p22.spore_blow", new float[][] { zero(0), at(3, cup), at(7, blow), at(12, blow), zero(16) });

		// ---- 27 Size ----
		float[] hug = p(-1.25f, -0.9f, 0, -1.25f, 0.9f, 0, 0.35f, 0, 0.3f, 0.3f, 0.2f);
		MutationPose.register("p27.shrink", new float[][] { zero(0), at(4, hug), at(12, hug), zero(18) });
		float[] ride = p(-0.95f, -0.2f, 0, -0.95f, 0.2f, 0, 0.2f, 0, -1.4f, -1.4f, 0);
		float[] ride2 = p(-1.05f, -0.2f, 0, -1.05f, 0.2f, 0, 0.22f, 0, -1.4f, -1.4f, 0.05f);
		MutationPose.registerLoop("p27.ride", 3, new float[][] { at(0, ride), at(3, ride), at(9, ride2), at(15, ride) });
		float[] carry = p(-2.05f, -0.15f, 0.3f, 0.15f, 0, -0.15f, -0.05f, 0, 0, 0, -0.1f);
		float[] carry2 = p(-2.1f, -0.15f, 0.32f, 0.18f, 0, -0.15f, -0.06f, 0, 0, 0, -0.1f);
		MutationPose.registerLoop("p27.carry", 3, new float[][] { zero(0), at(3, carry), at(13, carry2), at(23, carry) });
	}

	// ================================================================== overlays

	private static void registerOverlays() {
		// ---- 16 ----
		MutationOverlays.register("p16.grip", ctx -> MutationRender.shell(ctx, WEB, MutationRender.Shell.THIN, 0xDDFFFFFF, false));
		MutationOverlays.register("p16.rush", ctx -> MutationRender.eyes(ctx, MutationRender.argb(255, 255, 30, 30)));
		MutationOverlays.register("p16.sense", RevampClientE::senseHalo);

		// ---- 17 ----
		MutationOverlays.register("p17.arm", RevampClientE::stretchedArm);
		MutationOverlays.register("p17.form", ctx -> MutationRender.shell(ctx, RUBBER, MutationRender.Shell.THIN, 0x70FFFFFF, false));
		MutationOverlays.register("p17.shield", RevampClientE::rubberBalloon);
		MutationOverlays.register("p17.glide", RevampClientE::glideCanopy);

		// ---- 18 ----
		MutationOverlays.register("p18.shell", ctx -> {
			float d = ctx.state().value("p18.density", 1.0f);
			int argb;
			if (d < 0.995f) {
				int a = (int) Math.min(200, 60 + (1.0f - d) / 0.75f * 130);
				argb = MutationRender.argb(a, 120, 205, 255);
			} else {
				int a = (int) Math.min(210, 70 + (d - 1.0f) / 2.0f * 140);
				argb = MutationRender.argb(a, 255, 150, 55);
			}
			MutationRender.shell(ctx, DENSITY, MutationRender.Shell.THIN, argb, false);
		});
		MutationOverlays.register("p18.intangible", ctx -> {
			int a = (int) (90 + 110 * MutationRender.pulse(ctx, 6));
			MutationRender.shell(ctx, DENSITY, MutationRender.Shell.THIN, MutationRender.argb(a, 140, 255, 255), true);
		});
		MutationOverlays.register("p18.crush", RevampClientE::crushFist);

		// ---- 22 ----
		MutationOverlays.register("p22.blessing", ctx -> MutationRender.shell(ctx, BARK, MutationRender.Shell.THIN, 0xFFFFFFFF, false));
		MutationOverlays.register("p22.overgrowth", ctx -> {
			MutationRender.eyes(ctx, MutationRender.argb(255, 120, 255, 90));
			int a = (int) (80 + 120 * MutationRender.pulse(ctx, 20));
			MutationRender.shell(ctx, BARK, MutationRender.Shell.THIN, MutationRender.argb(a, 255, 255, 255), false);
		});
	}

	private static boolean slim(MutationOverlays.Context ctx) {
		return "slim".equals(ctx.player().getSkin().model().id());
	}

	// ---- 16: a pulsing red halo above the head while Spider-Sense is primed ----
	private static void senseHalo(MutationOverlays.Context ctx) {
		if (senseHalo == null) {
			senseHalo = MutationRender.bake(CubeListBuilder.create().texOffs(0, 0)
					.addBox(-6.0f, 0.0f, -6.0f, 12.0f, 0.4f, 0.6f)
					.addBox(-6.0f, 0.0f, 5.4f, 12.0f, 0.4f, 0.6f)
					.addBox(-6.0f, 0.0f, -5.4f, 0.6f, 0.4f, 10.8f)
					.addBox(5.4f, 0.0f, -5.4f, 0.6f, 0.4f, 10.8f), 4, 4);
		}
		MutationRender.eyes(ctx, MutationRender.argb(255, 255, 70, 40));
		PoseStack pose = ctx.pose();
		float t = (ctx.ageInTicks() + ctx.partialTick()) % 8.0f / 8.0f;
		for (int ring = 0; ring < 2; ring++) {
			float phase = (t + ring * 0.5f) % 1.0f;
			float s = 0.8f + phase * 0.7f;
			int a = (int) (200 * (1.0f - phase));
			pose.pushPose();
			ctx.model().head.translateAndRotate(pose);
			pose.translate(0, (-8.5f - phase * 3.0f) / 16.0f, 0);
			pose.scale(s, 1.0f, s);
			senseHalo.render(pose, ctx.buffers().getBuffer(RenderType.eyes(MutationRender.WHITE)), 0xF000F0,
					OverlayTexture.NO_OVERLAY, MutationRender.argb(a, 255, 60, 40));
			pose.popPose();
		}
	}

	// ---- 17: the stretched arm ----
	private static void stretchedArm(MutationOverlays.Context ctx) {
		float len = ctx.state().value("p17.arm_len", 0.0f);
		float t0 = ctx.state().value("p17.arm_t", 0.0f) - 1.0f;
		int mode = Math.round(ctx.state().value("p17.arm_mode", 0.0f)) - 1;
		if (len <= 0.05f || t0 < 0.0f || mode < 0) {
			return;
		}
		long gt = ctx.player().level().getGameTime();
		float age = (float) Math.floorMod(gt % 8192L - (long) t0, 8192L) + ctx.partialTick();
		float ext;
		if (mode == ElasticityHandlers.ARM_HOLD) {
			ext = Math.min(1.0f, age / 3.0f);
		} else if (age < 3.0f) {
			ext = age / 3.0f;
		} else if (age < 9.0f) {
			ext = 1.0f;
		} else {
			ext = Math.max(0.0f, 1.0f - (age - 9.0f) / 5.0f);
		}
		ext = ext * ext * (3.0f - 2.0f * ext);
		if (ext <= 0.01f || age > 400.0f) {
			return;
		}
		if (armSeg == null) {
			armSeg = new ModelPart[] {
					MutationRender.bake(CubeListBuilder.create().texOffs(40, 22).addBox(-3.0f, 0.0f, -2.0f, 4.0f, 1.0f, 4.0f), 64, 64),
					MutationRender.bake(CubeListBuilder.create().texOffs(40, 22).addBox(-2.0f, 0.0f, -2.0f, 3.0f, 1.0f, 4.0f), 64, 64),
					MutationRender.bake(CubeListBuilder.create().texOffs(32, 54).addBox(-1.0f, 0.0f, -2.0f, 4.0f, 1.0f, 4.0f), 64, 64),
					MutationRender.bake(CubeListBuilder.create().texOffs(32, 54).addBox(-1.0f, 0.0f, -2.0f, 3.0f, 1.0f, 4.0f), 64, 64) };
			armFist = new ModelPart[] {
					MutationRender.bake(CubeListBuilder.create().texOffs(40, 24).addBox(-3.2f, 0.0f, -2.2f, 4.4f, 4.0f, 4.4f), 64, 64),
					MutationRender.bake(CubeListBuilder.create().texOffs(40, 24).addBox(-2.2f, 0.0f, -2.2f, 3.4f, 4.0f, 4.4f), 64, 64),
					MutationRender.bake(CubeListBuilder.create().texOffs(32, 56).addBox(-1.2f, 0.0f, -2.2f, 4.4f, 4.0f, 4.4f), 64, 64),
					MutationRender.bake(CubeListBuilder.create().texOffs(32, 56).addBox(-1.2f, 0.0f, -2.2f, 3.4f, 4.0f, 4.4f), 64, 64) };
		}
		boolean slim = slim(ctx);
		float px = Math.max(0.5f, ext * len * 16.0f);
		boolean giant = mode == ElasticityHandlers.ARM_HAMMER;
		ResourceLocation skin = ctx.player().getSkin().texture();
		drawArm(ctx, ctx.model().rightArm, slim ? 1 : 0, px, giant ? 1.0f + 1.6f * ext : 1.0f, -1.0f / 16.0f, skin);
		if (mode == ElasticityHandlers.ARM_BOTH || giant) {
			drawArm(ctx, ctx.model().leftArm, slim ? 3 : 2, px, giant ? 1.0f + 1.6f * ext : 1.0f, 1.0f / 16.0f, skin);
		}
	}

	private static void drawArm(MutationOverlays.Context ctx, ModelPart bone, int idx, float px, float fistScale, float fistCentreX,
			ResourceLocation skin) {
		PoseStack pose = ctx.pose();
		var buffer = ctx.buffers().getBuffer(RenderType.entityCutoutNoCull(skin));
		pose.pushPose();
		bone.translateAndRotate(pose);
		pose.translate(0.0f, 9.5f / 16.0f, 0.0f);
		pose.pushPose();
		pose.scale(1.0f, px, 1.0f);
		armSeg[idx].render(pose, buffer, ctx.light(), OverlayTexture.NO_OVERLAY, -1);
		pose.popPose();
		pose.translate(0.0f, px / 16.0f - 0.5f / 16.0f, 0.0f);
		pose.translate(fistCentreX, 0.0f, 0.0f);
		pose.scale(fistScale, fistScale, fistScale);
		pose.translate(-fistCentreX, 0.0f, 0.0f);
		armFist[idx].render(pose, buffer, ctx.light(), OverlayTexture.NO_OVERLAY, -1);
		pose.popPose();
	}

	// ---- 17: the Rubber Shield balloon ----
	private static void rubberBalloon(MutationOverlays.Context ctx) {
		if (balloon == null) {
			balloon = MutationRender.bake(CubeListBuilder.create().texOffs(0, 0).addBox(-7.5f, -1.5f, -6.0f, 15.0f, 15.0f, 12.0f), 64, 64);
		}
		MutationRender.shell(ctx, RUBBER, MutationRender.Shell.THICK, 0xC0FFFFFF, false);
		float breathe = 1.0f + 0.05f * MutationRender.pulse(ctx, 14);
		PoseStack pose = ctx.pose();
		pose.pushPose();
		ctx.model().body.translateAndRotate(pose);
		pose.translate(0, 6.0f / 16.0f, 0);
		pose.scale(breathe, breathe, breathe);
		pose.translate(0, -6.0f / 16.0f, 0);
		balloon.render(pose, ctx.buffers().getBuffer(RenderType.entityTranslucent(RUBBER)), ctx.light(), OverlayTexture.NO_OVERLAY,
				0xB0FFFFFF);
		pose.popPose();
	}

	// ---- 17: the glide canopy (a rubbery membrane stretched between the spread arms) ----
	private static void glideCanopy(MutationOverlays.Context ctx) {
		if (canopy == null) {
			canopy = MutationRender.bake(CubeListBuilder.create().texOffs(0, 0).addBox(-17.0f, 0.5f, 0.6f, 34.0f, 11.0f, 0.4f), 128, 128);
		}
		MutationRender.onPart(ctx, ctx.model().body, canopy, RUBBER, 0xD0FFFFFF, false);
	}

	// ---- 18: the glowing, armed fist ----
	private static void crushFist(MutationOverlays.Context ctx) {
		if (fistGlow == null) {
			fistGlow = MutationRender.bake(CubeListBuilder.create().texOffs(0, 0).addBox(-3.6f, 6.6f, -2.6f, 5.2f, 4.2f, 5.2f), 4, 4);
		}
		int a = (int) (110 + 120 * MutationRender.pulse(ctx, 10));
		MutationRender.onPart(ctx, ctx.model().rightArm, fistGlow, MutationRender.WHITE, MutationRender.argb(a, 255, 140, 40), true);
	}

	// ================================================================== client tick

	private static int tick;

	private static void clientTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || mc.isPaused()) {
			return;
		}
		tick++;
		// 16 -- Spider-Sense threat glow: a faint red shimmer over every hostile nearby, only for the owner
		if (tick % 8 == 0 && SpiderClimbingHandlers.owns(p)) {
			boolean primed = SpiderClimbingHandlers.senseActive(p);
			AABB box = p.getBoundingBox().inflate(primed ? 20.0 : 14.0);
			DustParticleOptions dust = new DustParticleOptions(new org.joml.Vector3f(1.0f, 0.12f, 0.12f), primed ? 1.1f : 0.7f);
			int shown = 0;
			for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, box, e -> e instanceof Enemy && e.isAlive())) {
				if (++shown > 24) {
					break;
				}
				int n = primed ? 3 : 1;
				for (int i = 0; i < n; i++) {
					mc.level.addParticle(dust, e.getX() + (mc.level.random.nextDouble() - 0.5) * e.getBbWidth(),
							e.getY() + e.getBbHeight() + 0.15 + mc.level.random.nextDouble() * 0.25,
							e.getZ() + (mc.level.random.nextDouble() - 0.5) * e.getBbWidth(), 0, 0.01, 0);
				}
			}
		}
		// 17 -- Parachute Glide steering (the client simulates, the server allows: fall damage is off for
		// Elasticity and the server adds a hidden Slow Falling as the fallback)
		if (ElasticityHandlers.gliding(p) && !p.onGround() && !p.isInWater() && !p.getAbilities().flying) {
			Vec3 v = p.getDeltaMovement();
			Vec3 look = p.getLookAngle();
			Vec3 flat = new Vec3(look.x, 0, look.z);
			if (flat.lengthSqr() > 1.0e-4) {
				flat = flat.normalize();
				boolean dive = look.y < -0.55;
				double speed = dive ? 0.75 : 0.5;
				double sink = dive ? -0.32 : -0.11;
				Vec3 want = flat.scale(speed);
				double nx = v.x + (want.x - v.x) * 0.18;
				double nz = v.z + (want.z - v.z) * 0.18;
				double ny = Math.max(v.y, sink);
				p.setDeltaMovement(nx, ny, nz);
			}
		}
	}
}
