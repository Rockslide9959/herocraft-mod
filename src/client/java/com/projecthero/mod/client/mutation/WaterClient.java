package com.projecthero.mod.client.mutation;

import static com.projecthero.mod.client.mutation.MutationPose.AIM;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.mutation.water.WaterFxRenderer;
import com.projecthero.mod.client.mutation.water.WaterMesh;
import com.projecthero.mod.hero.power.p25.WaterEntities;
import com.projecthero.mod.hero.power.p25.WaterHandlers;
import com.projecthero.mod.hero.visual.MutationVisualState;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Water Manipulation rework -- the client side: the renderers of the water bodies ({@link WaterFxRenderer}), the
 * bodies drawn round a player from the synced {@code p25.*} flags (the Hydro Jet's beam from the hand, the Water Shield's
 * sphere and orbiting rings, the surf wave under a Wave Ride), the cast poses, the Wave Ride's movement on the riding
 * client, and the mono HUD. Riptide's spin is the vanilla trident spin pose (the server starts a real auto-spin attack).
 */
public final class WaterClient {
	private static final float[] Z = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

	private WaterClient() {
	}

	/** From {@code RevampClientB.init()}. */
	public static void init() {
		// black-and-gray keys / name; the blue Water Hairline is a registered meter
		com.projecthero.mod.client.gui.AbilityHudExtras.mono(WaterHandlers.KEY);
		registerPoses();
		EntityRendererRegistry.register(WaterEntities.WATER_BOLT, WaterFxRenderer::new);
		EntityRendererRegistry.register(WaterEntities.WATER_WHIP, WaterFxRenderer::new);
		EntityRendererRegistry.register(WaterEntities.WATER_PRISON, WaterFxRenderer::new);
		EntityRendererRegistry.register(WaterEntities.TIDAL_WAVE, WaterFxRenderer::new);
		EntityRendererRegistry.register(WaterEntities.GEYSER, WaterFxRenderer::new);
		EntityRendererRegistry.register(WaterEntities.HEALING_WATER, WaterFxRenderer::new);
		// v0.16.6: the hold moves
		EntityRendererRegistry.register(WaterEntities.WATER_DRAGON, WaterFxRenderer::new);
		EntityRendererRegistry.register(WaterEntities.WHIRLPOOL, WaterFxRenderer::new);
		EntityRendererRegistry.register(WaterEntities.TSUNAMI, WaterFxRenderer::new);
		EntityRendererRegistry.register(WaterEntities.RAIN_CALL, WaterFxRenderer::new);
		EntityRendererRegistry.register(WaterEntities.WATER_CLONE, com.projecthero.mod.client.mutation.water.WaterCloneRenderer::new);
		WorldRenderEvents.AFTER_ENTITIES.register(WaterClient::renderPlayerBodies);
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.player != null && !mc.isPaused()) {
				surfTick(mc.player);
			}
		});
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
		// R: the palm thrust that fires the bolt (also the Shift+C placing flick)
		float[] thrust = p(AIM, AIM, 0, 0.45f, 0.1f, -0.15f, 0.12f, -0.35f, -0.3f, 0.3f, 0);
		float[] thrust0 = p(-0.9f, 0.3f, 0.1f, 0.3f, 0.1f, -0.1f, -0.05f, 0.25f, 0.1f, -0.1f, 0);
		MutationPose.register("p25.shot", new float[][] { zero(0), at(2, thrust0), at(4, thrust), at(9, thrust), zero(13) });
		// Shift+R: both palms braced forward pouring the jet, a slight recoil sway
		float[] jet = p(AIM, AIM, 0, AIM, AIM, 0, 0.15f, 0, -0.35f, 0.35f, 0);
		float[] jet2 = p(AIM, AIM, 0, AIM, AIM, 0, 0.1f, 0.04f, -0.38f, 0.38f, 0);
		MutationPose.registerLoop("p25.jet", 3, new float[][] { zero(0), at(3, jet), at(6, jet2), at(9, jet) });
		// G: the lash -- arm wound up over the shoulder, cracked down and forward
		float[] whipUp = p(-2.7f, 0.5f, 0.4f, 0.3f, 0, -0.2f, -0.15f, 0.45f, 0.2f, -0.2f, -0.1f);
		float[] whipCrack = p(-1.0f, -0.35f, 0.1f, 0.4f, 0, -0.25f, 0.3f, -0.45f, -0.4f, 0.4f, 0.1f);
		MutationPose.register("p25.whip", new float[][] { zero(0), at(2, whipUp), at(4, whipCrack), at(9, whipCrack), zero(14) });
		// Shift+G: the reaching hand closes the sphere round the target
		float[] reach = p(AIM, AIM, 0, -0.5f, 0.2f, -0.2f, 0.1f, -0.25f, -0.25f, 0.25f, 0);
		float[] clench = p(AIM, AIM, 0.25f, -0.6f, 0.3f, -0.3f, 0.18f, -0.3f, -0.25f, 0.25f, 0.05f);
		MutationPose.register("p25.prison", new float[][] { zero(0), at(3, reach), at(7, clench), at(16, clench), zero(21) });
		// Z: both palms shove the wall out
		float[] push0 = p(-0.9f, 0.3f, 0.2f, -0.9f, -0.3f, -0.2f, -0.1f, 0, 0.2f, -0.2f, 0);
		float[] push = p(-1.6f, -0.1f, 0, -1.6f, 0.1f, 0, 0.3f, 0, -0.45f, 0.45f, 0.1f);
		MutationPose.register("p25.tidal", new float[][] { zero(0), at(4, push0), at(7, push), at(14, push), zero(20) });
		// Shift+Z: a low scoop, then both arms thrown up as the geyser erupts
		float[] scoop = p(0.35f, 0, 0.25f, 0.35f, 0, -0.25f, 0.35f, 0, -0.3f, 0.3f, 0.3f);
		float[] raise = p(-2.85f, 0, 0.35f, -2.85f, 0, -0.35f, -0.12f, 0, 0, 0, -0.35f);
		MutationPose.register("p25.geyser", new float[][] { zero(0), at(3, scoop), at(7, raise), at(15, raise), zero(21) });
		// X Shift: the surf stance -- crouched, side-on, arms out for balance
		float[] surf = p(-0.35f, 0, 1.05f, -0.35f, 0, -1.05f, 0.35f, 0.5f, -0.45f, 0.55f, -0.15f);
		float[] surf2 = p(-0.25f, 0, 1.2f, -0.45f, 0, -0.9f, 0.4f, 0.55f, -0.5f, 0.6f, -0.2f);
		MutationPose.registerLoop("p25.surf", 4, new float[][] { zero(0), at(4, surf), at(14, surf2), at(24, surf) });
		// V: the arms circling, keeping the shield's water moving
		float[] shield = p(-1.25f, -0.5f, 0.5f, -1.25f, 0.5f, -0.5f, 0.05f, 0, 0, 0, 0);
		float[] shield2 = p(-1.0f, -0.2f, 0.85f, -1.45f, 0.75f, -0.3f, 0.05f, 0.1f, 0, 0, 0);
		float[] shield3 = p(-1.45f, -0.75f, 0.3f, -1.0f, 0.2f, -0.85f, 0.05f, -0.1f, 0, 0, 0);
		MutationPose.registerLoop("p25.shield", 4, new float[][] { zero(0), at(4, shield), at(10, shield2), at(16, shield), at(22, shield3),
				at(28, shield) });
		// Shift+V: water drawn up and poured out over the squad
		float[] heal0 = p(0.3f, 0, 0.4f, 0.3f, 0, -0.4f, 0.2f, 0, 0, 0, 0.2f);
		float[] heal = p(-2.6f, 0, 0.75f, -2.6f, 0, -0.75f, -0.15f, 0, 0, 0, -0.3f);
		MutationPose.register("p25.heal", new float[][] { zero(0), at(4, heal0), at(9, heal), at(18, heal), zero(25) });
		// Shift+C (and Aquatic Form): the hand pulls the water in to the chest
		float[] pull0 = p(AIM, AIM, 0, -0.3f, 0, -0.1f, 0.1f, -0.2f, 0, 0, 0);
		float[] pull = p(-1.2f, -0.7f, 0, -0.9f, 0.5f, 0, -0.05f, 0, 0, 0, 0.1f);
		MutationPose.register("p25.draw", new float[][] { zero(0), at(3, pull0), at(9, pull), at(14, pull), zero(19) });

		// ---- v0.16.6 hold moves ----
		// hold R: both hands drawn low, then flung up and forward as the dragon rises out of them
		float[] drag0 = p(0.4f, 0.2f, 0.3f, 0.4f, -0.2f, -0.3f, 0.25f, 0, -0.2f, 0.2f, 0.2f);
		float[] dragUp = p(-2.9f, 0.1f, 0.2f, -2.9f, -0.1f, -0.2f, -0.2f, 0, 0.1f, -0.1f, -0.4f);
		float[] dragFwd = p(AIM, AIM, 0, -1.7f, 0.3f, -0.1f, 0.2f, -0.2f, -0.4f, 0.4f, 0);
		MutationPose.register("p25.dragon", new float[][] { zero(0), at(3, drag0), at(7, dragUp), at(12, dragUp), at(16, dragFwd), at(24, dragFwd),
				zero(30) });
		// hold G: one hand stirs a wide circle over the vortex
		float[] stir0 = p(-1.3f, -0.6f, 0.2f, 0.2f, 0, -0.2f, 0.15f, 0.2f, -0.2f, 0.2f, 0.1f);
		float[] stir1 = p(-1.5f, 0.3f, 0.6f, 0.2f, 0, -0.2f, 0.15f, -0.15f, -0.2f, 0.2f, 0.1f);
		float[] stir2 = p(-1.1f, 0.6f, -0.1f, 0.2f, 0, -0.2f, 0.15f, -0.3f, -0.2f, 0.2f, 0.1f);
		MutationPose.register("p25.whirl", new float[][] { zero(0), at(3, stir0), at(7, stir1), at(11, stir2), at(15, stir0), at(19, stir1),
				at(23, stir2), zero(30) });
		// hold X: arms held down and back, palms to the ground, legs together -- the jets carry you
		float[] jets = p(0.35f, 0, 0.32f, 0.35f, 0, -0.32f, 0.12f, 0, 0.12f, 0.12f, -0.1f);
		float[] jets2 = p(0.42f, 0, 0.28f, 0.28f, 0, -0.36f, 0.15f, 0.04f, 0.16f, 0.08f, -0.1f);
		MutationPose.registerLoop("p25.hjets", 4, new float[][] { zero(0), at(4, jets), at(12, jets2), at(20, jets) });
		// hold Z: both arms raised high, then brought crashing down and forward as the tsunami rears
		float[] tsu0 = p(-3.0f, 0, 0.45f, -3.0f, 0, -0.45f, -0.25f, 0, 0.15f, -0.15f, -0.35f);
		float[] tsu1 = p(-1.35f, 0, 0.1f, -1.35f, 0, -0.1f, 0.45f, 0, -0.5f, 0.5f, 0.2f);
		MutationPose.register("p25.tsunami", new float[][] { zero(0), at(5, tsu0), at(10, tsu0), at(14, tsu1), at(24, tsu1), zero(32) });
		// hold V: the hand sweeps out to the side where the clone rises
		float[] cl0 = p(-0.6f, 0, 0.3f, 0.2f, 0, -0.2f, 0.05f, 0.3f, 0, 0, 0);
		float[] cl1 = p(-1.4f, 0, 1.35f, 0.2f, 0, -0.2f, 0.05f, -0.25f, 0, 0, 0.1f);
		MutationPose.register("p25.clone", new float[][] { zero(0), at(3, cl0), at(8, cl1), at(16, cl1), zero(22) });
		// hold C: both arms lifted to the sky, head back, calling the rain down
		float[] rain = p(-2.7f, 0, 0.55f, -2.7f, 0, -0.55f, -0.15f, 0, 0, 0, -0.6f);
		MutationPose.register("p25.rain", new float[][] { zero(0), at(6, rain), at(26, rain), zero(34) });
	}

	// ================================================================== hands

	private static float partial() {
		return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
	}

	private static boolean firstPerson(Player p) {
		Minecraft mc = Minecraft.getInstance();
		return p == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
	}

	/**
	 * Where {@code p}'s right (or left) hand is right now, roughly, in world space, held out in front: in first person just
	 * below and to the side of the view, otherwise at the end of an arm raised forward.
	 */
	public static Vec3 handPos(Player p, float partial, boolean right) {
		if (firstPerson(p)) {
			Vec3 eye = p.getEyePosition(partial);
			Vec3 look = p.getViewVector(partial);
			Vec3 r = look.cross(new Vec3(0, 1, 0));
			r = r.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : r.normalize();
			Vec3 up = r.cross(look).normalize();
			return eye.add(look.scale(0.6)).add(r.scale(right ? 0.32 : -0.32)).add(up.scale(-0.26));
		}
		float yaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		Vec3 rgt = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		float pitch = p.getViewXRot(partial) * Mth.DEG_TO_RAD;
		double shoulder = p.isCrouching() ? 1.08 : 1.38;
		return p.getPosition(partial).add(0, shoulder - Mth.sin(pitch) * 0.55, 0).add(rgt.scale(right ? 0.36 : -0.36))
				.add(fwd.scale(0.62 * Mth.cos(pitch)));
	}

	/** The Hydro Jet's end: the first block within 16 along the (interpolated) aim. */
	private static Vec3 jetEnd(Player p, float partial) {
		Vec3 eye = p.getEyePosition(partial);
		Vec3 far = eye.add(p.getViewVector(partial).scale(WaterHandlers.JET_RANGE));
		BlockHitResult hit = p.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.MISS ? far : hit.getLocation();
	}

	// ================================================================== bodies round players (world render)

	private static void renderPlayerBodies(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = context.camera().getPosition();
		PoseStack pose = context.matrixStack();
		for (Player pl : mc.level.players()) {
			MutationVisualState s = MutationVisuals.state(pl);
			if (s.flags().isEmpty()) {
				continue;
			}
			boolean jet = s.has("p25.jet");
			boolean shield = s.has("p25.shield");
			boolean surf = s.has("p25.surf");
			boolean hjets = s.has("p25.hjets");
			if (!jet && !shield && !surf && !hjets) {
				continue;
			}
			float t = pl.tickCount + partial;
			int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(pl.getEyePosition(partial)));
			light = Math.max(light & 0xFFFF, 0x90) | (light & 0xFFFF0000);
			VertexConsumer vc = WaterMesh.buffer(context.consumers());
			if (jet) {
				renderJet(pl, partial, t, cam, pose, vc, light);
			}
			if (shield) {
				renderShield(pl, partial, t, cam, pose, vc, light);
			}
			if (surf) {
				renderSurf(pl, partial, t, cam, pose, vc, light);
			}
			if (hjets) {
				renderHydroJets(pl, partial, t, cam, pose, vc, light);
			}
		}
	}

	/**
	 * v0.16.6 hold X: four jets of water blasting down and back from the palms and the soles -- each a churning nozzle that
	 * flares into a foaming plume, a spray burst at its end.
	 */
	private static void renderHydroJets(Player p, float partial, float t, Vec3 cam, PoseStack pose, VertexConsumer vc, int light) {
		float yaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		Vec3 rgt = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		Vec3 feet = p.getPosition(partial);
		boolean fp = firstPerson(p);
		Vec3[] from = new Vec3[4];
		if (fp) {
			// in first person the palm jets come from low in the view, either side
			from[0] = handPos(p, partial, true).add(0, -0.25, 0);
			from[1] = handPos(p, partial, false).add(0, -0.25, 0);
		} else {
			from[0] = feet.add(0, 0.62, 0).add(rgt.scale(0.44)).add(fwd.scale(-0.08));
			from[1] = feet.add(0, 0.62, 0).add(rgt.scale(-0.44)).add(fwd.scale(-0.08));
		}
		from[2] = feet.add(0, 0.02, 0).add(rgt.scale(0.12));
		from[3] = feet.add(0, 0.02, 0).add(rgt.scale(-0.12));
		Vec3 down = new Vec3(0, -1, 0).add(fwd.scale(-0.3)).normalize();
		TextureAtlasSprite flow = WaterMesh.flow();
		TextureAtlasSprite still = WaterMesh.still();
		for (int j = 0; j < 4; j++) {
			boolean foot = j >= 2;
			float len = (foot ? 2.6f : 1.9f) + 0.3f * Mth.sin(t * 1.3f + j * 1.7f);
			Vec3 side = down.cross(new Vec3(1, 0, 0));
			side = side.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : side.normalize();
			int n = 10;
			Vec3[] pts = new Vec3[n];
			float[] outer = new float[n];
			float[] inner = new float[n];
			for (int i = 0; i < n; i++) {
				float s = i / (float) (n - 1);
				Vec3 jitter = side.scale(Mth.sin(s * 9f - t * 2.4f + j) * 0.05f * s).add(rgt.scale(Mth.cos(s * 7f - t * 2f + j) * 0.05f * s));
				pts[i] = from[j].add(down.scale(len * s)).add(jitter).subtract(cam);
				outer[i] = (foot ? 0.11f : 0.08f) + (foot ? 0.3f : 0.22f) * s * s + 0.03f * Mth.sin(s * 18f - t * 3f);
				inner[i] = outer[i] * 0.45f;
			}
			WaterMesh.tube(vc, pose, pts, outer, 10, 0.16f, t * 3f + j, WaterMesh.fade(WaterMesh.BODY, 0.9f), light, flow);
			WaterMesh.tube(vc, pose, pts, inner, 6, 0.2f, t * 3f + j, WaterMesh.FOAM, light, still);
			// the nozzle ring where the water leaves the palm / sole, and the spray at the end
			pose.pushPose();
			Vec3 a = pts[0];
			pose.translate(a.x, a.y, a.z);
			WaterMesh.ring(vc, pose, foot ? 0.14f : 0.1f, 0.035f, 12, 0.3f, t * 3f, WaterMesh.FOAM, light, flow);
			pose.popPose();
			pose.pushPose();
			Vec3 b = pts[n - 1];
			pose.translate(b.x, b.y, b.z);
			float puff = (foot ? 0.42f : 0.32f) + 0.08f * Mth.sin(t * 2.1f + j);
			WaterMesh.sphere(vc, pose, puff, 0.75f, 0.35f, t * 4f + j, WaterMesh.fade(WaterMesh.FOAM, 0.55f), light, flow);
			pose.popPose();
		}
	}

	/** Shift+R: a churning rope of water from the hand to whatever it hits, a spray blooming where it lands. */
	private static void renderJet(Player p, float partial, float t, Vec3 cam, PoseStack pose, VertexConsumer vc, int light) {
		Vec3 from = handPos(p, partial, true);
		Vec3 to = jetEnd(p, partial);
		Vec3 d = to.subtract(from);
		double len = d.length();
		if (len < 0.3) {
			return;
		}
		Vec3 dir = d.scale(1.0 / len);
		Vec3 side = dir.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3 up = side.cross(dir);
		int n = Math.max(6, (int) (len * 2.5));
		Vec3[] pts = new Vec3[n];
		float[] outer = new float[n];
		float[] inner = new float[n];
		for (int i = 0; i < n; i++) {
			float s = i / (float) (n - 1);
			float jitter = Mth.sin(s * 14f - t * 2.2f) * 0.035f * s;
			pts[i] = from.add(dir.scale(len * s)).add(side.scale(jitter)).add(up.scale(Mth.cos(s * 11f - t * 1.9f) * 0.03f * s)).subtract(cam);
			outer[i] = 0.17f + 0.16f * s + 0.04f * Mth.sin(s * 20f - t * 3f);
			inner[i] = outer[i] * 0.45f;
		}
		TextureAtlasSprite flow = WaterMesh.flow();
		WaterMesh.tube(vc, pose, pts, outer, 10, 0.12f, t * 3f, WaterMesh.BODY, light, flow);
		WaterMesh.tube(vc, pose, pts, inner, 6, 0.15f, t * 3f, WaterMesh.FOAM, light, WaterMesh.still());
		pose.pushPose();
		Vec3 e = to.subtract(cam);
		pose.translate(e.x, e.y, e.z);
		float b = 0.38f + 0.1f * Mth.sin(t * 1.7f);
		WaterMesh.sphere(vc, pose, b, 0.7f, 0.35f, t * 4f, WaterMesh.fade(WaterMesh.FOAM, 0.85f), light, flow);
		WaterMesh.ring(vc, pose, b * 1.5f, 0.07f, 14, 0.4f, t * 3f, WaterMesh.fade(WaterMesh.LIGHT, 0.7f), light, flow);
		pose.popPose();
	}

	/** V: a thin sphere of water round the body with three streams orbiting it (only the streams from inside it). */
	private static void renderShield(Player p, float partial, float t, Vec3 cam, PoseStack pose, VertexConsumer vc, int light) {
		Vec3 c = p.getPosition(partial).add(0, p.getBbHeight() * 0.52, 0).subtract(cam);
		TextureAtlasSprite flow = WaterMesh.flow();
		TextureAtlasSprite still = WaterMesh.still();
		pose.pushPose();
		pose.translate(c.x, c.y, c.z);
		if (!firstPerson(p)) {
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(t * 4f));
			WaterMesh.sphere(vc, pose, 1.45f, 1.12f, 0.05f, t, WaterMesh.SHELL, light, flow);
			pose.popPose();
		}
		for (int k = 0; k < 3; k++) {
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(t * (9f + 3f * k) + k * 120f));
			pose.mulPose(Axis.XP.rotationDegrees(55f + 20f * k + 6f * Mth.sin(t * 0.1f + k)));
			WaterMesh.ring(vc, pose, 1.5f, 0.085f, 28, 0.35f, t * 2f, WaterMesh.LIGHT, light, k == 1 ? still : flow);
			pose.popPose();
		}
		// a few droplets flung round the equator
		for (int k = 0; k < 6; k++) {
			float a = t * 0.22f + k * Mth.TWO_PI / 6f;
			pose.pushPose();
			pose.translate(Mth.cos(a) * 1.55f, Mth.sin(t * 0.3f + k) * 0.5f, Mth.sin(a) * 1.55f);
			WaterMesh.sphere(vc, pose, 0.08f, 1f, 0.1f, t, WaterMesh.FOAM, light, still);
			pose.popPose();
		}
		pose.popPose();
	}

	/** Shift+X: the wave you ride -- a short curling swell under and behind your feet, a wake trailing off it. */
	private static void renderSurf(Player p, float partial, float t, Vec3 cam, PoseStack pose, VertexConsumer vc, int light) {
		Vec3 feet = p.getPosition(partial).subtract(cam);
		float yaw = Mth.rotLerp(partial, p.yRotO, p.getYRot());
		TextureAtlasSprite still = WaterMesh.still();
		TextureAtlasSprite flow = WaterMesh.flow();
		pose.pushPose();
		pose.translate(feet.x, feet.y - 0.15, feet.z);
		pose.mulPose(Axis.YP.rotationDegrees(-yaw));
		// the swell: the crest behind you, curling forward under your feet
		WaterMesh.Profile swell = (x, s) -> {
			float edge = Mth.clamp(Math.abs(x) / 1.3f, 0f, 1f);
			float taper = 1f - 0.8f * edge * edge;
			Vec3 q = WaterFxRenderer.curl(s, 0.55f * taper, 0.55f, 0f);
			float wob = Mth.sin(x * 2.1f + t * 0.8f + s * 4f) * 0.06f;
			return new Vec3(x, q.y + wob, q.z - 0.55f);
		};
		WaterMesh.sheet(vc, pose, 1.3f, 10, 10, swell, WaterMesh.BODY, light, still);
		WaterMesh.Profile foam = (x, s) -> {
			Vec3 q = swell.at(x, 0.55f + 0.45f * s);
			return new Vec3(x, q.y + 0.04f, q.z + 0.03f);
		};
		WaterMesh.sheet(vc, pose, 1.15f, 10, 4, foam, WaterMesh.FOAM, light, flow);
		// the wake: two streams trailing back off the swell's shoulders
		for (int side = -1; side <= 1; side += 2) {
			int n = 10;
			Vec3[] pts = new Vec3[n];
			float[] radii = new float[n];
			for (int i = 0; i < n; i++) {
				float s = i / (float) (n - 1);
				pts[i] = new Vec3(side * (0.9f + s * 0.9f), 0.12f + 0.08f * Mth.sin(s * 8f - t * 1.5f), -0.8f - s * 3.2f);
				radii[i] = 0.2f * (1f - s) + 0.04f;
			}
			WaterMesh.tube(vc, pose, pts, radii, 8, 0.2f, t * 2f, WaterMesh.fade(WaterMesh.LIGHT, 0.85f), light, flow);
		}
		WaterMesh.disc(vc, pose, 1.15f, 0.08f, t * 2f, WaterMesh.fade(WaterMesh.LIGHT, 0.7f), light, flow);
		pose.popPose();
	}

	// ================================================================== Wave Ride on the riding client

	/**
	 * Shift+X: while the wave carries you, you are driven along your look at a surf speed, riding up steps (the server
	 * lends extra step height) and skimming the top of water instead of sinking into it.
	 */
	private static void surfTick(LocalPlayer self) {
		if (!MutationVisuals.state(self).has("p25.surf")) {
			return;
		}
		Vec3 look = self.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		if (flat.lengthSqr() < 1.0e-4) {
			return;
		}
		flat = flat.normalize().scale(WaterHandlers.SURF_SPEED);
		double vy = self.getDeltaMovement().y;
		if (self.isInWater()) {
			vy = Math.max(vy, 0.16); // pop back up onto the surface
		} else if (vy < 0.0 && self.level().getFluidState(BlockPos.containing(self.getX(), self.getY() - 0.12, self.getZ())).is(FluidTags.WATER)) {
			vy = 0.0; // skim the top of it
		}
		self.setDeltaMovement(flat.x, vy, flat.z);
		self.resetFallDistance();
	}
}
