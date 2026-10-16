package com.projecthero.mod.client.mutation.wind;

import static com.projecthero.mod.client.mutation.MutationPose.AIM;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.mutation.MutationPose;
import com.projecthero.mod.hero.power.p24.WindEntities;
import com.projecthero.mod.hero.power.p24.WindHandlers;
import com.projecthero.mod.hero.visual.MutationVisualState;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Wind Manipulation rework -- the client side: the cast poses, the entity renderers (Wind Blade crescent,
 * tornadoes, the Vacuum / Push / Tailwind / Updraft effects) and the models that ride on the hero, drawn in the world
 * from the synced {@code p24.*} flags ({@link WindHandlers#registerVisuals}):
 * <ul>
 * <li>Hurricane -- a wall of air round the hero: two counter-turning streaked cylinders and racing rings, grown in at
 * the start and fading out at the end;</li>
 * <li>Wind Barrier -- a spinning swirl shield of spiral arms in front of the hero, along the look;</li>
 * <li>Air Bubble -- a translucent sphere of clean air round the body, rim-lit and slowly churning;</li>
 * <li>Glide -- gust streak lines streaming off the body and the spread hands.</li>
 * </ul>
 */
public final class WindClient {
	private static final float[] Z = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

	private WindClient() {
	}

	/** From {@code RevampClientC.init()}. */
	public static void init() {
		// black-and-gray keys / name; the pale-blue Gust Hairline is a registered meter
		com.projecthero.mod.client.gui.AbilityHudExtras.mono(WindHandlers.KEY);
		registerPoses();
		EntityRendererRegistry.register(WindEntities.TORNADO, WindTornadoRenderer::new);
		EntityRendererRegistry.register(WindEntities.BLADE, WindBladeRenderer::new);
		EntityRendererRegistry.register(WindEntities.FX, WindFxRenderer::new);
		WorldRenderEvents.AFTER_ENTITIES.register(WindClient::renderAttached);
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
	private static float[] p(float rX, float rY, float rZ, float lX, float lY, float lZ, float bX, float bY, float rL, float lL, float h) {
		return new float[] { 0, rX, rY, rZ, lX, lY, lZ, bX, bY, rL, lL, h };
	}

	private static void registerPoses() {
		// R: a cutting sweep of the arm, alternating sides -- drawn back high and outside, then slashed across the body
		float[] slashRUp = p(-2.5f, 0.55f, 0.9f, -0.3f, 0, -0.2f, 0, 0.45f, 0.2f, -0.2f, 0);
		float[] slashRCut = p(-1.35f, -1.05f, -0.15f, 0.25f, 0, -0.25f, 0.12f, -0.5f, -0.25f, 0.2f, 0);
		MutationPose.register("p24.slash_r", new float[][] { zero(0), at(2, slashRUp), at(5, slashRCut), at(10, slashRCut), zero(13) });
		float[] slashLUp = p(-0.3f, 0, 0.2f, -2.5f, -0.55f, -0.9f, 0, -0.45f, -0.2f, 0.2f, 0);
		float[] slashLCut = p(0.25f, 0, 0.25f, -1.35f, 1.05f, 0.15f, 0.12f, 0.5f, 0.2f, -0.25f, 0);
		MutationPose.register("p24.slash_l", new float[][] { zero(0), at(2, slashLUp), at(5, slashLCut), at(10, slashLCut), zero(13) });
		// Shift+R: the flurry -- both arms slashing in turn, fast
		MutationPose.registerLoop("p24.flurry", 2, new float[][] { zero(0), at(2, slashRUp), at(4, slashRCut), at(6, slashLUp),
				at(8, slashLCut), at(10, slashRUp) });
		// G: the open hand thrown at the aim point, then clenched and dragged back to the chest
		float[] reach = p(AIM, AIM, 0, -0.4f, 0.2f, -0.15f, 0.12f, -0.3f, -0.3f, 0.3f, 0);
		float[] drag = p(-1.0f, -0.55f, 0.15f, -0.45f, 0.25f, -0.2f, -0.12f, 0.2f, 0.25f, -0.2f, -0.05f);
		MutationPose.register("p24.pull", new float[][] { zero(0), at(2, reach), at(9, reach), at(14, drag), at(20, drag), zero(24) });
		// Shift+G: both palms drawn back, then thrust out in a lunge
		float[] pushBack = p(-0.55f, 0.15f, 0.35f, -0.55f, -0.15f, -0.35f, -0.12f, 0, 0.15f, -0.1f, 0);
		float[] pushOut = p(AIM, AIM, 0, AIM, AIM, 0, 0.28f, 0, -0.45f, 0.35f, 0);
		MutationPose.register("p24.push", new float[][] { zero(0), at(3, pushBack), at(6, pushOut), at(13, pushOut), zero(17) });
		// X: leaning hard into the dash, arms swept back
		float[] dash = p(0.95f, 0, 0.3f, 0.95f, 0, -0.3f, 0.45f, 0, -0.55f, 0.6f, -0.35f);
		MutationPose.register("p24.dash", new float[][] { zero(0), at(2, dash), at(8, dash), zero(12) });
		// Shift+X: both arms flung up as the column throws you
		float[] rise = p(-2.95f, 0, 0.32f, -2.95f, 0, -0.32f, -0.12f, 0, 0.12f, -0.05f, -0.3f);
		MutationPose.register("p24.rise", new float[][] { zero(0), at(3, rise), at(13, rise), zero(17) });
		// Z: the arm circles overhead, winding up the funnel, then points it on its way
		float[] wind1 = p(-2.7f, 0.6f, 0.45f, -0.3f, 0, -0.3f, -0.05f, 0.2f, 0, 0, -0.2f);
		float[] wind2 = p(-2.75f, -0.6f, 0.15f, -0.3f, 0, -0.3f, -0.05f, -0.2f, 0, 0, -0.2f);
		float[] send = p(AIM, AIM, 0, -0.5f, 0.2f, -0.3f, 0.2f, -0.3f, -0.3f, 0.25f, 0);
		MutationPose.register("p24.conjure", new float[][] { zero(0), at(3, wind1), at(6, wind2), at(9, wind1), at(13, send), at(19, send),
				zero(23) });
		// Shift+Z: arms crossed in front, then flung out wide as the wall rises
		float[] cross = p(-1.45f, -0.95f, 0, -1.45f, 0.95f, 0, 0.2f, 0, 0.1f, -0.1f, 0.15f);
		float[] wide = p(-0.35f, 0, 1.65f, -0.35f, 0, -1.65f, -0.15f, 0, -0.25f, 0.25f, -0.2f);
		MutationPose.register("p24.storm", new float[][] { zero(0), at(4, cross), at(9, wide), at(18, wide), zero(23) });
		// V: both palms held out, turning the air in front
		float[] wall = p(AIM, AIM, 0, AIM, AIM, 0, 0.1f, 0, -0.25f, 0.25f, 0);
		float[] wall2 = p(AIM, AIM, 0.1f, AIM, AIM, -0.1f, 0.12f, 0, -0.25f, 0.25f, 0);
		MutationPose.registerLoop("p24.barrier", 3, new float[][] { zero(0), at(3, wall), at(9, wall2), at(15, wall) });
		// Shift+V: arms spread on the wind, leaning into the glide
		float[] glide = p(-0.2f, 0, 1.45f, -0.2f, 0, -1.45f, 0.35f, 0, 0.35f, 0.25f, -0.35f);
		float[] glide2 = p(-0.1f, 0, 1.35f, -0.3f, 0, -1.55f, 0.38f, 0, 0.3f, 0.3f, -0.35f);
		MutationPose.registerLoop("p24.glide", 4, new float[][] { zero(0), at(4, glide), at(10, glide2), at(16, glide) });
		// C: a surfer's stance on the ridden tornado
		float[] ride = p(-0.3f, 0, 1.0f, -0.3f, 0, -1.0f, 0.1f, 0.45f, -0.35f, 0.35f, 0);
		float[] ride2 = p(-0.4f, 0, 1.1f, -0.2f, 0, -0.9f, 0.12f, 0.5f, -0.4f, 0.4f, 0);
		MutationPose.registerLoop("p24.ride", 4, new float[][] { zero(0), at(4, ride), at(14, ride2), at(24, ride) });
		// Shift+C: the hands shape a ball of air, then open as it swells round you
		float[] cup = p(-1.5f, -0.65f, 0.25f, -1.5f, 0.65f, -0.25f, 0.1f, 0, 0, 0, 0.1f);
		float[] open = p(-1.1f, 0.15f, 1.0f, -1.1f, -0.15f, -1.0f, -0.08f, 0, 0, 0, -0.1f);
		MutationPose.register("p24.bubble", new float[][] { zero(0), at(3, cup), at(8, cup), at(13, open), at(17, open), zero(21) });
	}

	// ================================================================== the models on the hero

	private static float partial() {
		return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
	}

	private static void renderAttached(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = context.camera().getPosition();
		PoseStack pose = context.matrixStack();
		boolean firstPerson = mc.options.getCameraType().isFirstPerson();
		VertexConsumer vc = null;
		for (Player pl : mc.level.players()) {
			MutationVisualState s = MutationVisuals.state(pl);
			boolean hurricane = s.flags().contains("p24.hurricane");
			boolean barrier = s.flags().contains("p24.barrier");
			boolean bubble = s.flags().contains("p24.bubble");
			boolean glide = s.flags().contains("p24.glide") && pl.isFallFlying();
			if (!hurricane && !barrier && !bubble && !glide) {
				continue;
			}
			if (vc == null) {
				vc = context.consumers().getBuffer(RenderType.entityTranslucent(WindMesh.STREAK));
			}
			boolean self = pl == mc.player && firstPerson;
			Vec3 at = pl.getPosition(partial);
			float time = pl.tickCount + partial;
			int light = LevelRenderer.getLightColor(mc.level, pl.blockPosition());
			int bright = LightTexture.pack(15, Math.max(10, LightTexture.sky(light)));
			pose.pushPose();
			pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
			if (hurricane) {
				float left = s.value("p24.hurricane_left", WindHandlers.HURRICANE_TICKS);
				float age = WindHandlers.HURRICANE_TICKS - left + partial;
				hurricane(vc, pose, time, age, left, bright);
			}
			if (barrier) {
				barrier(vc, pose, pl, time, partial, self, bright);
			}
			if (bubble && !self) {
				float left = s.value("p24.bubble_left", WindHandlers.BUBBLE_TICKS);
				bubble(vc, pose, pl, time, left, bright);
			}
			if (glide && !self) {
				glideStreaks(vc, pose, pl, time, partial, bright);
			}
			pose.popPose();
		}
	}

	/** The Hurricane: a 4.5-block wall of air round the hero. */
	private static void hurricane(VertexConsumer vc, PoseStack pose, float time, float age, float left, int light) {
		float grow = Mth.clamp(age / 6f, 0, 1);
		float fade = Mth.clamp(left / 10f, 0, 1) * grow;
		if (fade < 0.01f) {
			return;
		}
		float r = (float) WindHandlers.HURRICANE_RADIUS * (0.55f + 0.45f * grow);
		float h = 3.8f * (0.3f + 0.7f * grow);
		// two counter-turning walls, the inner one a little taller and fainter
		pose.pushPose();
		pose.mulPose(Axis.YP.rotation(time * 0.3f));
		WindMesh.band(vc, pose.last(), 0, r * 0.96f, h, r * 1.06f, 40, 6, time * 0.04f, 0.0f, 1.0f, (int) (150 * fade), (int) (25 * fade), light);
		pose.popPose();
		pose.pushPose();
		pose.mulPose(Axis.YP.rotation(-time * 0.42f));
		WindMesh.band(vc, pose.last(), 0, r * 0.88f, h * 1.12f, r * 0.92f, 40, 5, -time * 0.05f, 1.0f, 0.0f, (int) (95 * fade), (int) (15 * fade),
				light);
		pose.popPose();
		// racing rings round the wall
		for (int k = 0; k < 5; k++) {
			float y = ((time * 0.09f + k * 0.21f) % 1f) * h;
			pose.pushPose();
			pose.translate(0, y, 0);
			pose.mulPose(Axis.YP.rotation(time * (0.6f + k * 0.07f) + k));
			pose.mulPose(Axis.XP.rotation(0.06f * Mth.sin(time * 0.2f + k)));
			int a = (int) (170 * fade * (1 - y / (h * 1.1f)));
			WindMesh.band(vc, pose.last(), -0.14f, r * 1.02f, 0.14f, r * 1.05f, 40, 4, 0, 0.25f, 0.75f, a, a, light);
			pose.popPose();
		}
		// the dust skirt on the ground
		pose.pushPose();
		pose.mulPose(Axis.YP.rotation(time * 0.25f));
		WindMesh.flatRing(vc, pose.last(), 0.06f, r * 0.75f, r * 1.25f, 40, time * 0.05f, 0, (int) (90 * fade), light);
		pose.popPose();
	}

	/** Wind Barrier: a swirl shield a block in front of the hero, square to the look. */
	private static void barrier(VertexConsumer vc, PoseStack pose, Player pl, float time, float partial, boolean self, int light) {
		Vec3 look = pl.getViewVector(partial);
		float yaw = pl.getViewYRot(partial);
		float pitch = pl.getViewXRot(partial);
		float dist = self ? 1.6f : 1.05f;
		float rad = self ? 1.35f : 1.25f;
		int alpha = self ? 120 : 210;
		pose.pushPose();
		pose.translate(look.x * dist, pl.getEyeHeight() - 0.15 + look.y * dist, look.z * dist);
		pose.mulPose(Axis.YP.rotationDegrees(-yaw));
		pose.mulPose(Axis.XP.rotationDegrees(pitch));
		pose.mulPose(Axis.ZP.rotation(time * 0.55f));
		int arms = 6;
		for (int i = 0; i < arms; i++) {
			WindMesh.spiralArm(vc, pose.last(), rad, 0.1f, i * Mth.TWO_PI / arms, 0.6f, 0.2f, 18, alpha, alpha / 3, time * 0.2f, light);
		}
		// the rim
		pose.pushPose();
		pose.mulPose(Axis.XP.rotation(Mth.HALF_PI));
		WindMesh.flatRing(vc, pose.last(), 0, rad * 0.9f, rad * 1.04f, 32, time * 0.2f, alpha / 2, alpha, light);
		pose.popPose();
		// a second, counter-turning swirl behind it
		pose.translate(0, 0, -0.12f);
		pose.mulPose(Axis.ZP.rotation(-time * 1.2f));
		for (int i = 0; i < 3; i++) {
			WindMesh.spiralArm(vc, pose.last(), rad * 0.8f, 0.1f, i * Mth.TWO_PI / 3, -0.5f, 0.16f, 14, alpha / 2, alpha / 5, -time * 0.25f,
					light);
		}
		pose.popPose();
	}

	/** Air Bubble: a sphere of clean air round the body. */
	private static void bubble(VertexConsumer vc, PoseStack pose, Player pl, float time, float left, int light) {
		float fade = Mth.clamp(left / 20f, 0, 1);
		float wob = 1 + 0.03f * Mth.sin(time * 0.25f);
		pose.pushPose();
		pose.translate(0, pl.getBbHeight() * 0.52f, 0);
		pose.mulPose(Axis.YP.rotation(time * 0.05f));
		pose.scale(wob, 1 / wob, wob);
		WindMesh.sphere(vc, pose.last(), 1.3f, 12, 22, time * 0.02f, 215, 240, 255, (int) (25 * fade), (int) (150 * fade), light);
		WindMesh.sphere(vc, pose.last(), 1.24f, 8, 16, -time * 0.035f, 240, 250, 255, 0, (int) (60 * fade), light);
		pose.popPose();
	}

	/** Gust streak lines peeling off a gliding hero (body and both spread hands). */
	private static void glideStreaks(VertexConsumer vc, PoseStack pose, Player pl, float time, float partial, int light) {
		Vec3 v = pl.getDeltaMovement();
		if (v.lengthSqr() < 0.04) {
			v = pl.getViewVector(partial);
		}
		Vec3 back = v.normalize().reverse();
		Vec3 side = back.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3 up = side.cross(back).normalize();
		Vector3f toEye = new Vector3f((float) up.x, (float) up.y, (float) up.z);
		float c = pl.getBbHeight() * 0.4f;
		for (int i = 0; i < 7; i++) {
			float lat = (i - 3) * 0.38f;
			float ph = ((time * 0.15f + i * 0.37f) % 1f);
			float len = 1.8f + (i % 3) * 0.7f;
			Vec3 a = side.scale(lat).add(up.scale(0.15 * Mth.sin(i * 1.7f))).add(back.scale(0.3 + ph * 1.2)).add(0, c, 0);
			Vec3 b = a.add(back.scale(len));
			WindMesh.ribbon(vc, pose.last(), new Vector3f((float) a.x, (float) a.y, (float) a.z), new Vector3f((float) b.x, (float) b.y,
					(float) b.z), 0.07f, toEye, (int) (190 * (1 - ph * 0.6f)), time * 0.2f, time * 0.2f + 1, light);
		}
	}
}
