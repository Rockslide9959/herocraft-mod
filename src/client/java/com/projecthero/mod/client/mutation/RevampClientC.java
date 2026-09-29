package com.projecthero.mod.client.mutation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.mutation.batchc.WindTornadoRenderer;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.hero.power.p20.EnergyAbsorptionHandlers;
import com.projecthero.mod.hero.power.p24.WindEntities;
import com.projecthero.mod.hero.revamp.RevampBatchC;
import com.projecthero.mod.hero.visual.MutationMeters;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.13.22 mutation revamp, batch C -- client-side registration (poses, overlays, entity renderers, particles,
 * payload receivers) for:
 * <ul>
 * <li>07 Electrokinesis -- crackling blue emissive shell + glowing blue eyes in Charged Mode</li>
 * <li>14 Sonic Scream -- pale sound rings pulsing out around the head while Echolocation is on</li>
 * <li>20 Energy Absorption -- an element-tinted glowing lattice shell (Absorption Mode) and glowing fists once the
 *     meter holds a real charge; the HUD Energy bar recolours with the stored element</li>
 * <li>21 Shockwave Manipulation -- a rippling distortion shell that thickens with stored kinetic energy</li>
 * <li>24 Wind Manipulation -- a thin pale air shell while gliding / Tailwind / riding, and the tornado renderer</li>
 * </ul>
 */
public final class RevampClientC {
	private static final ResourceLocation CHARGED_A = ProjectHeroMod.id("textures/entity/mutation/p07_charged_a.png");
	private static final ResourceLocation CHARGED_B = ProjectHeroMod.id("textures/entity/mutation/p07_charged_b.png");
	private static final ResourceLocation ENERGY_SHELL = ProjectHeroMod.id("textures/entity/mutation/p20_shell.png");
	private static final ResourceLocation RIPPLE = ProjectHeroMod.id("textures/entity/mutation/p21_ripple.png");
	private static final ResourceLocation AIR = ProjectHeroMod.id("textures/entity/mutation/p24_air.png");

	private static ModelPart soundRing;
	private static ModelPart fist;
	private static int lastElement = -1;

	private RevampClientC() {
	}

	/** Called once from {@code ProjectHeroModClient.onInitializeClient}, after the shared pose library. */
	public static void init() {
		registerPoses();
		registerOverlays();
		EntityRendererRegistry.register(WindEntities.TORNADO, WindTornadoRenderer::new);
		ClientTickEvents.END_CLIENT_TICK.register(RevampClientC::clientTick);
	}

	// ---------------- poses ----------------

	private static float[] f(int tick, float rX, float rY, float rZ, float lX, float lY, float lZ, float bX, float bY,
			float rL, float lL, float h) {
		return new float[] { tick, rX, rY, rZ, lX, lY, lZ, bX, bY, rL, lL, h };
	}

	private static float[] z(int tick) {
		return new float[] { tick, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
	}

	private static void registerPoses() {
		// 07: both arms thrown up calling the storm down (loop), and the Overcharge "clench and fling"
		MutationPose.registerLoop("p07.storm_call", 4, new float[][] { z(0),
				f(4, -2.9f, 0, 0.35f, -2.9f, 0, -0.35f, -0.15f, 0, 0, 0, -0.45f),
				f(7, -3.05f, 0.1f, 0.5f, -3.05f, -0.1f, -0.5f, -0.18f, 0, 0.05f, -0.05f, -0.5f),
				f(10, -2.9f, 0, 0.35f, -2.9f, 0, -0.35f, -0.15f, 0, 0, 0, -0.45f) });
		MutationPose.register("p07.overcharge", new float[][] { z(0),
				f(4, -1.2f, -0.9f, 0, -1.2f, 0.9f, 0, 0.25f, 0, 0.1f, -0.1f, 0.2f),
				f(8, -0.4f, 0, 1.5f, -0.4f, 0, -1.5f, -0.2f, 0, -0.1f, 0.1f, -0.3f),
				f(14, -0.4f, 0, 1.5f, -0.4f, 0, -1.5f, -0.2f, 0, -0.1f, 0.1f, -0.3f), z(20) });
		// 14: a lunging shout, hands cupped to the mouth, a hand raised to the ear
		MutationPose.register("p14.shout", new float[][] { z(0),
				f(3, 0.6f, 0, 0.45f, 0.6f, 0, -0.45f, 0.22f, 0, -0.2f, 0.2f, -0.15f),
				f(10, 0.65f, 0, 0.5f, 0.65f, 0, -0.5f, 0.25f, 0, -0.2f, 0.2f, -0.2f), z(15) });
		MutationPose.register("p14.focused", new float[][] { z(0),
				f(3, -2.0f, -0.6f, 0, -2.0f, 0.6f, 0, 0.12f, 0, -0.1f, 0.1f, 0),
				f(10, -2.0f, -0.6f, 0, -2.0f, 0.6f, 0, 0.15f, 0, -0.1f, 0.1f, 0), z(14) });
		MutationPose.register("p14.listen", new float[][] { z(0),
				f(4, -2.5f, -0.3f, 0.35f, 0, 0, 0, 0, 0.2f, 0, 0, 0.1f),
				f(16, -2.5f, -0.3f, 0.35f, 0, 0, 0, 0, 0.2f, 0, 0, 0.1f), z(20) });
		// 20: arms spread wide to take the energy in
		MutationPose.register("p20.open", new float[][] { z(0),
				f(5, -0.6f, 0, 1.1f, -0.6f, 0, -1.1f, -0.15f, 0, 0, 0, -0.3f),
				f(14, -0.6f, 0, 1.2f, -0.6f, 0, -1.2f, -0.18f, 0, 0, 0, -0.3f), z(20) });
		// 21: cross-arm catch, then push it back out
		MutationPose.register("p21.parry", new float[][] { z(0),
				f(2, -1.6f, -0.9f, 0, -1.6f, 0.9f, 0, 0.1f, 0, -0.2f, 0.2f, 0.1f),
				f(7, -1.6f, -0.9f, 0, -1.6f, 0.9f, 0, 0.12f, 0, -0.2f, 0.2f, 0.1f),
				f(10, -1.6f, -0.1f, 0, -1.6f, 0.1f, 0, 0.2f, 0, -0.35f, 0.35f, 0), z(14) });
		// 24: arms spread and leaning into the glide; a surfer's stance on the tornado
		MutationPose.registerLoop("p24.glide", 4, new float[][] { z(0),
				f(4, -0.2f, 0, 1.45f, -0.2f, 0, -1.45f, 0.35f, 0, 0.35f, 0.25f, -0.35f),
				f(10, -0.1f, 0, 1.35f, -0.3f, 0, -1.55f, 0.38f, 0, 0.3f, 0.3f, -0.35f),
				f(16, -0.2f, 0, 1.45f, -0.2f, 0, -1.45f, 0.35f, 0, 0.35f, 0.25f, -0.35f) });
		MutationPose.registerLoop("p24.ride", 4, new float[][] { z(0),
				f(4, -0.3f, 0, 1.0f, -0.3f, 0, -1.0f, 0.1f, 0.45f, -0.35f, 0.35f, 0),
				f(14, -0.4f, 0, 1.1f, -0.2f, 0, -0.9f, 0.12f, 0.5f, -0.4f, 0.4f, 0),
				f(24, -0.3f, 0, 1.0f, -0.3f, 0, -1.0f, 0.1f, 0.45f, -0.35f, 0.35f, 0) });
	}

	// ---------------- overlays ----------------

	private static int elementArgb(int alpha, int element) {
		int rgb = EnergyAbsorptionHandlers.ELEMENT_RGB[Math.max(0, Math.min(4, element))];
		return (alpha & 255) << 24 | rgb;
	}

	private static void registerOverlays() {
		// 07 Charged Mode: a crackling blue shell (two frames, flickering) + glowing blue eyes
		MutationOverlays.register(RevampBatchC.FLAG_CHARGED, ctx -> {
			int frame = ((int) ctx.ageInTicks() / 2) % 2;
			float flicker = 0.55f + 0.45f * MutationRender.pulse(ctx, 7f);
			int c = (int) (255 * flicker);
			MutationRender.shell(ctx, frame == 0 ? CHARGED_A : CHARGED_B, MutationRender.Shell.THIN,
					MutationRender.argb(255, c * 110 / 255, c * 200 / 255, c), true);
		});
		MutationOverlays.register(RevampBatchC.FLAG_CHARGED, ctx -> MutationRender.eyes(ctx, MutationRender.argb(255, 120, 215, 255)));

		// 14 Echolocation: two rings of sound pulsing outward around the head
		MutationOverlays.register(RevampBatchC.FLAG_ECHO, ctx -> {
			if (soundRing == null) {
				soundRing = MutationRender.bake(CubeListBuilder.create().texOffs(0, 0)
						.addBox(-6.0f, 0.0f, -6.0f, 12.0f, 0.4f, 0.4f)
						.addBox(-6.0f, 0.0f, 5.6f, 12.0f, 0.4f, 0.4f)
						.addBox(-6.0f, 0.0f, -6.0f, 0.4f, 0.4f, 12.0f)
						.addBox(5.6f, 0.0f, -6.0f, 0.4f, 0.4f, 12.0f), 4, 4);
			}
			PoseStack pose = ctx.pose();
			float t = ctx.ageInTicks() + ctx.partialTick();
			for (int i = 0; i < 2; i++) {
				float phase = ((t / 20f) + i * 0.5f) % 1.0f;
				float s = 0.75f + phase * 0.9f;
				int a = (int) (200 * (1.0f - phase));
				pose.pushPose();
				ctx.model().head.translateAndRotate(pose);
				pose.translate(0, -4.5f / 16f, 0);
				pose.mulPose(Axis.YP.rotationDegrees(t * 3 + i * 45));
				pose.scale(s, 1, s);
				soundRing.render(pose, ctx.buffers().getBuffer(RenderType.eyes(MutationRender.WHITE)), 0xF000F0,
						OverlayTexture.NO_OVERLAY, MutationRender.argb(a, a * 160 / 255, a * 246 / 255, a));
				pose.popPose();
			}
		});

		// 20 Absorption Mode: a lattice of light over the body, tinted by the stored element
		MutationOverlays.register(RevampBatchC.FLAG_ABSORB, ctx -> {
			int el = Math.round(ctx.state().value(RevampBatchC.VALUE_ELEMENT, 0f));
			float glow = 0.6f + 0.4f * MutationRender.pulse(ctx, 24f);
			int rgb = EnergyAbsorptionHandlers.ELEMENT_RGB[Math.max(0, Math.min(4, el))];
			int r = (int) (((rgb >> 16) & 255) * glow);
			int g = (int) (((rgb >> 8) & 255) * glow);
			int b = (int) ((rgb & 255) * glow);
			MutationRender.shell(ctx, ENERGY_SHELL, MutationRender.Shell.THIN, MutationRender.argb(255, r, g, b), true);
		});
		// 20 glowing fists while the meter holds 20%+
		MutationOverlays.register(RevampBatchC.FLAG_HANDS, ctx -> {
			if (fist == null) {
				fist = MutationRender.bake(CubeListBuilder.create().texOffs(0, 0).addBox(-2.1f, 7.6f, -2.1f, 4.2f, 4.2f, 4.2f), 4, 4);
			}
			int el = Math.round(ctx.state().value(RevampBatchC.VALUE_ELEMENT, 0f));
			float fill = ctx.state().value(RevampBatchC.VALUE_FILL, 0.2f);
			int a = (int) (90 + 140 * Math.min(1f, fill) * (0.7f + 0.3f * MutationRender.pulse(ctx, 16f)));
			int argb = elementArgb(a, el);
			MutationRender.onPart(ctx, ctx.model().rightArm, fist, MutationRender.WHITE, dim(argb, a), true);
			MutationRender.onPart(ctx, ctx.model().leftArm, fist, MutationRender.WHITE, dim(argb, a), true);
		});

		// 21: the stored force shimmering over the body -- stronger the fuller the gauge
		MutationOverlays.register(RevampBatchC.FLAG_KINETIC, ctx -> {
			float charge = ctx.state().value(RevampBatchC.VALUE_KINETIC, 0.25f);
			float wobble = 0.85f + 0.15f * MutationRender.pulse(ctx, 10f);
			int a = (int) (Math.min(1f, 0.25f + charge * 0.6f) * 255 * wobble);
			MutationRender.shell(ctx, RIPPLE, MutationRender.Shell.THIN, MutationRender.argb(a, 255, 225, 180), false);
		});

		// 24: a thin, pale, translucent skin of moving air
		MutationOverlays.register(RevampBatchC.FLAG_AIR, ctx -> {
			int a = (int) (110 + 60 * MutationRender.pulse(ctx, 18f));
			MutationRender.shell(ctx, AIR, MutationRender.Shell.THIN, MutationRender.argb(a, 240, 248, 255), false);
		});
	}

	/** Emissive render types ignore alpha, so fade an additive glow by scaling its colour instead. */
	private static int dim(int argb, int alpha) {
		float k = alpha / 255f;
		int r = (int) (((argb >> 16) & 255) * k);
		int g = (int) (((argb >> 8) & 255) * k);
		int b = (int) ((argb & 255) * k);
		return MutationRender.argb(255, r, g, b);
	}

	// ---------------- HUD: the Energy bar follows the stored element ----------------

	private static void clientTick(Minecraft mc) {
		if (mc.player == null) {
			lastElement = -1;
			return;
		}
		ExperimentalState st = mc.player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (st == null || !st.ownedPowers.contains(EnergyAbsorptionHandlers.KEY)) {
			return;
		}
		Float v = st.resources.get(EnergyAbsorptionHandlers.KEY + "/element");
		int el = v == null ? 0 : Math.round(v);
		if (el != lastElement) {
			lastElement = el;
			MutationMeters.register(RevampBatchC.energySpec(el));
		}
	}
}
