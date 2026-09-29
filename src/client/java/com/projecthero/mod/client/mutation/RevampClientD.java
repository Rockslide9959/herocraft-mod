package com.projecthero.mod.client.mutation;

import static com.projecthero.mod.client.mutation.MutationPose.AIM;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.mutation.d.EmissiveItemModels;
import com.projecthero.mod.client.mutation.d.MirrorImageRenderer;
import com.projecthero.mod.client.mutation.d.ShadowServantRenderer;
import com.projecthero.mod.hero.revamp.d.BatchDContent;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.13.22 mutation revamp, batch D -- client-side registration (poses, overlays, entity renderers, particles,
 * payload receivers) for:
 * <ul>
 * <li>10 Telekinesis</li>
 * <li>11 Teleportation</li>
 * <li>15 Invisibility / Light</li>
 * <li>19 Shadow Manipulation</li>
 * <li>23 Gravity Manipulation</li>
 * <li>26 Magnetic Manipulation</li>
 * </ul>
 */
public final class RevampClientD {
	private static final ResourceLocation SHADOW_FORM = ProjectHeroMod.id("textures/entity/mutation/p19_shadow_form.png");
	private static final ResourceLocation GRAVITY_FIELD = ProjectHeroMod.id("textures/entity/mutation/p23_gravity_field.png");
	private static final ResourceLocation MAGNET_FIELD = ProjectHeroMod.id("textures/entity/mutation/p26_field.png");

	private static ModelPart prismPane;
	private static ModelPart bladeGlow;

	private RevampClientD() {
	}

	/** Called once from {@code ProjectHeroModClient.onInitializeClient}, after the shared pose library. */
	public static void init() {
		registerPoses();
		registerOverlays();
		EntityRendererRegistry.register(BatchDContent.MIRROR_IMAGE, MirrorImageRenderer::new);
		EntityRendererRegistry.register(BatchDContent.SHADOW_SERVANT, ShadowServantRenderer::new);
		EmissiveItemModels.init();
	}

	// ---------------------------------------------------------------- poses

	private static float[] p(float rX, float rY, float rZ, float lX, float lY, float lZ, float bX, float bY,
			float rL, float lL, float h) {
		return new float[] { 0, rX, rY, rZ, lX, lY, lZ, bX, bY, rL, lL, h };
	}

	private static float[] at(int tick, float[] pose) {
		float[] f = pose.clone();
		f[0] = tick;
		return f;
	}

	private static float[] zero(int tick) {
		return at(tick, p(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
	}

	private static void registerPoses() {
		// ---- 10 Telekinesis ----
		float[] shove = p(-1.55f, -0.15f, 0, -1.55f, 0.15f, 0, 0.15f, 0, -0.25f, 0.25f, 0);
		MutationPose.register("p10.barrier", new float[][] { zero(0),
				at(4, p(-1.2f, -0.45f, 0, -1.2f, 0.45f, 0, 0.05f, 0, 0, 0, 0)), at(8, shove), at(12, shove), zero(17) });
		float[] gather = p(-0.9f, -0.9f, 0.2f, -0.9f, 0.9f, -0.2f, 0.35f, 0, 0.25f, -0.25f, 0.35f);
		float[] gather2 = p(-1.0f, -0.95f, 0.25f, -1.0f, 0.95f, -0.25f, 0.4f, 0, 0.25f, -0.25f, 0.4f);
		MutationPose.registerLoop("p10.detonate_charge", 6, new float[][] { zero(0), at(6, gather), at(11, gather2), at(16, gather) });
		float[] burst = p(-2.2f, 0, 1.2f, -2.2f, 0, -1.2f, -0.25f, 0, -0.1f, 0.1f, -0.45f);
		MutationPose.register("p10.detonate", new float[][] { at(0, gather), at(3, burst), at(12, burst), zero(20) });
		MutationPose.register("p10.set_down", new float[][] { zero(0),
				at(4, p(-1.3f, -0.2f, 0.1f, -1.3f, 0.2f, -0.1f, 0.1f, 0, 0, 0, 0.1f)),
				at(12, p(-0.6f, -0.2f, 0.1f, -0.6f, 0.2f, -0.1f, 0.2f, 0, 0, 0, 0.3f)), zero(18) });
		float[] crush = p(AIM, AIM, 0, -2.3f, 0.7f, 0, 0.05f, -0.2f, 0, 0, 0);
		float[] crush2 = p(AIM, AIM, 0.1f, -2.4f, 0.75f, 0, 0.07f, -0.2f, 0, 0, 0.05f);
		MutationPose.registerLoop("p10.crush", 3, new float[][] { zero(0), at(3, crush), at(8, crush2), at(13, crush) });
		float[] thrust = p(AIM, AIM, 0, AIM, AIM, 0, 0.25f, 0, -0.3f, 0.3f, 0);
		MutationPose.register("p10.launch", new float[][] { zero(0),
				at(4, p(0.8f, 0, 0.3f, 0.8f, 0, -0.3f, -0.1f, 0, 0.2f, -0.2f, 0)), at(7, thrust), at(13, thrust), zero(18) });
		MutationPose.register("p10.mind_lock", new float[][] { zero(0), at(3, crush), at(14, crush), zero(19) });

		// ---- 15 Light ----
		float[] holy = p(-2.9f, 0.3f, 0.25f, -2.9f, -0.3f, -0.25f, -0.15f, 0, 0, 0, -0.45f);
		float[] holy2 = p(-3.0f, 0.35f, 0.3f, -3.0f, -0.35f, -0.3f, -0.18f, 0, 0, 0, -0.5f);
		MutationPose.registerLoop("p15.holy_charge", 6, new float[][] { zero(0), at(6, holy), at(12, holy2), at(18, holy) });
		MutationPose.register("p15.cloak", new float[][] { zero(0),
				at(4, p(-2.8f, 0, 0.4f, -2.8f, 0, -0.4f, -0.1f, 0, 0, 0, -0.2f)),
				at(10, p(-0.3f, 0, 0.3f, -0.3f, 0, -0.3f, 0.15f, 0, 0, 0, 0.2f)), zero(16) });
		float[] fanOut = p(-1.3f, 0.8f, 0.6f, -1.3f, -0.8f, -0.6f, -0.05f, 0, 0, 0, -0.1f);
		MutationPose.register("p15.refract", new float[][] { zero(0),
				at(4, p(-1.4f, -0.9f, 0, -1.4f, 0.9f, 0, 0.1f, 0, 0, 0, 0)), at(9, fanOut), at(14, fanOut), zero(19) });
		float[] bladeOut = p(-0.8f, -0.8f, 0, 0.3f, 0.2f, 0, 0.2f, -0.5f, -0.3f, 0.3f, 0.05f);
		MutationPose.register("p15.blade_summon", new float[][] { zero(0),
				at(5, p(-3.0f, 0.2f, 0.2f, 0.2f, 0, -0.1f, -0.1f, 0.2f, 0, 0, -0.3f)), at(9, bladeOut), at(13, bladeOut), zero(17) });

		// ---- 19 Shadow ----
		float[] pull = p(-0.4f, 0, 0.9f, -0.4f, 0, -0.9f, 0.4f, 0, 0.4f, -0.3f, 0.3f);
		float[] pull2 = p(-0.5f, 0, 1.0f, -0.5f, 0, -1.0f, 0.42f, 0, 0.4f, -0.3f, 0.32f);
		MutationPose.registerLoop("p19.gather", 6, new float[][] { zero(0), at(6, pull), at(11, pull2), at(16, pull) });
		float[] spread = p(-0.3f, 0, 1.2f, -0.3f, 0, -1.2f, -0.1f, 0, 0, 0, -0.2f);
		MutationPose.register("p19.cloak", new float[][] { zero(0),
				at(5, p(-2.4f, -0.5f, 0, -2.4f, 0.5f, 0, 0.05f, 0, 0, 0, 0.2f)), at(11, spread), at(15, spread), zero(20) });
		float[] sunk = p(0.3f, 0, 0.3f, 0.3f, 0, -0.3f, 0.6f, 0, 1.0f, 1.0f, 0.4f);
		MutationPose.register("p19.sink", new float[][] { zero(0), at(6, sunk), at(14, sunk), zero(18) });
		float[] risen = p(-2.6f, 0, 0.3f, -2.6f, 0, -0.3f, -0.15f, 0, 0, 0, -0.3f);
		MutationPose.register("p19.emerge", new float[][] { at(0, sunk), at(5, risen), at(12, risen), zero(18) });

		// ---- 23 Gravity ----
		float[] press = p(AIM, AIM, 0, -0.4f, 0, -0.3f, 0.1f, -0.25f, -0.2f, 0.2f, 0);
		float[] press2 = p(AIM, AIM, 0.1f, -0.45f, 0, -0.32f, 0.12f, -0.25f, -0.2f, 0.2f, 0);
		MutationPose.registerLoop("p23.crush", 3, new float[][] { zero(0), at(3, press), at(8, press2), at(13, press) });
		float[] cup = p(-1.3f, -0.45f, 0, -1.3f, 0.45f, 0, 0.1f, 0, -0.1f, 0.1f, 0.1f);
		float[] cup2 = p(-1.4f, -0.55f, 0, -1.4f, 0.55f, 0, 0.12f, 0, -0.1f, 0.1f, 0.12f);
		MutationPose.registerLoop("p23.well_charge", 4, new float[][] { zero(0), at(4, cup), at(10, cup2), at(16, cup) });
		float[] raised = p(-2.4f, 0, 0.2f, 0.2f, 0, -0.1f, -0.1f, 0, 0, 0, -0.25f);
		MutationPose.register("p23.lift", new float[][] { zero(0),
				at(4, p(-0.6f, 0, 0.2f, 0.2f, 0, -0.1f, 0.05f, 0, 0, 0, 0)), at(9, raised), at(13, raised), zero(18) });
		float[] flick = p(-2.9f, -0.2f, 0.1f, 0.2f, 0, -0.1f, -0.15f, -0.2f, 0, 0, -0.3f);
		MutationPose.register("p23.invert", new float[][] { zero(0),
				at(3, p(AIM, AIM, 0, 0.2f, 0, -0.1f, 0.05f, -0.2f, 0, 0, 0)), at(7, flick), at(12, flick), zero(17) });

		// ---- 26 Magnetic ----
		float[] clench = p(-1.0f, 0.3f, 0.3f, 0.3f, 0, -0.2f, -0.1f, 0.3f, 0.2f, -0.2f, 0);
		MutationPose.register("p26.clench", new float[][] { zero(0),
				at(3, p(AIM, AIM, 0, 0.2f, 0, -0.2f, 0.1f, -0.2f, 0, 0, 0)),
				at(6, p(AIM, AIM, 0.4f, 0.2f, 0, -0.2f, 0.12f, -0.2f, 0, 0, 0)), at(10, clench), zero(15) });
		float[] temples = p(-2.3f, -0.7f, 0, -2.3f, 0.7f, 0, 0, 0, 0, 0, 0.1f);
		MutationPose.register("p26.sense", new float[][] { zero(0), at(4, temples), at(12, temples), zero(17) });
	}

	// ---------------------------------------------------------------- overlays

	private static void registerOverlays() {
		// 10: purple glowing eyes while channelling / holding anything
		MutationOverlays.register("p10.eyes", ctx -> {
			float pulse = 0.75f + 0.25f * MutationRender.pulse(ctx, 30);
			MutationRender.eyes(ctx, MutationRender.argb(255, (int) (200 * pulse), (int) (95 * pulse), 255));
		});
		// 11: violet eyes while charging a portal / aiming a blink / mid-Bamf
		MutationOverlays.register("p11.charge", ctx -> MutationRender.eyes(ctx, MutationRender.argb(255, 205, 150, 255)));

		// 15: the Prism Shield -- three thin panes of refracted light in front of the body (cyan / magenta / gold fringes)
		MutationOverlays.register("p15.prism", ctx -> {
			if (prismPane == null) {
				prismPane = MutationRender.bake(CubeListBuilder.create().texOffs(0, 0)
						.addBox(-8.0f, -3.0f, -9.0f, 16.0f, 17.0f, 0.2f), 4, 4);
			}
			float pulse = MutationRender.pulse(ctx, 16);
			int a = 70 + (int) (40 * pulse);
			MutationRender.onPart(ctx, ctx.model().body, prismPane, MutationRender.WHITE,
					MutationRender.argb(a, 150, 240, 255), true);
			var pose = ctx.pose();
			pose.pushPose();
			pose.translate(0.02f, 0.0f, -0.03f);
			MutationRender.onPart(ctx, ctx.model().body, prismPane, MutationRender.WHITE,
					MutationRender.argb(a / 2, 255, 120, 230), true);
			pose.translate(-0.04f, 0.0f, -0.03f);
			MutationRender.onPart(ctx, ctx.model().body, prismPane, MutationRender.WHITE,
					MutationRender.argb(a / 2, 255, 230, 120), true);
			pose.popPose();
		});
		// 15: a soft halo of light around the blade hand
		MutationOverlays.register("p15.blade", ctx -> {
			if (bladeGlow == null) {
				bladeGlow = MutationRender.bake(CubeListBuilder.create().texOffs(0, 0)
						.addBox(-2.6f, 7.4f, -2.6f, 5.2f, 5.2f, 5.2f), 4, 4);
			}
			int a = 60 + (int) (50 * MutationRender.pulse(ctx, 12));
			MutationRender.onPart(ctx, ctx.model().rightArm, bladeGlow, MutationRender.WHITE,
					MutationRender.argb(a, 255, 235, 150), true);
		});

		// 19: Shadow Form -- a thick black silhouette over the whole body, with glowing purple eyes
		MutationOverlays.register("p19.shadow_form", ctx -> {
			MutationRender.shell(ctx, SHADOW_FORM, MutationRender.Shell.THICK, MutationRender.argb(236, 255, 255, 255), false);
			float pulse = 0.8f + 0.2f * MutationRender.pulse(ctx, 24);
			MutationRender.eyes(ctx, MutationRender.argb(255, (int) (180 * pulse), (int) (70 * pulse), 255));
		});

		// 23: a thin translucent violet field shell while Gravitational Nexus is on; dark eyes while a black hole charges
		MutationOverlays.register("p23.field", ctx -> {
			int a = 110 + (int) (60 * MutationRender.pulse(ctx, 40));
			MutationRender.shell(ctx, GRAVITY_FIELD, MutationRender.Shell.THIN, MutationRender.argb(a, 255, 255, 255), false);
		});
		MutationOverlays.register("p23.well", ctx -> MutationRender.eyes(ctx, MutationRender.argb(255, 150, 60, 220)));

		// 26: magnetic field lines over the body while hovering
		MutationOverlays.register("p26.hover", ctx -> {
			int a = 140 + (int) (80 * MutationRender.pulse(ctx, 10));
			MutationRender.shell(ctx, MAGNET_FIELD, MutationRender.Shell.THIN, MutationRender.argb(a, 170, 205, 255), true);
		});
	}
}
