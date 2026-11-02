package com.projecthero.mod.client.omnitrix.alien;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import org.joml.Quaternionf;

import com.projecthero.mod.client.omnitrix.OmnitrixRenderers;
import com.projecthero.mod.client.quirk.fx.QuirkFxDraw;
import com.projecthero.mod.omnitrix.alien.stinkfly.StinkflyKit;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.core.Direction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (user: "Give his powers actual models and vfx instead of just glowing orbs please"): Stinkfly's goo in flight.
 * <ul>
 *   <li><b>Slime Shot / Slime Barrage / goo spit</b>: a wobbling blob of translucent yellow-green slime, stretched along its flight
 *       and squashing as it goes, a darker green core showing through, a glossy highlight on top and a string of smaller droplets
 *       trailing behind;</li>
 *   <li><b>Slime Net</b>: a web of goo strands -- eight spokes, two rings and a scalloped rim with blob knots where they cross and a
 *       fat glob at the hub -- that springs open as it leaves his mouth, spins, and bows back like a parachute;</li>
 *   <li>both shed goo as they fly: slime drips falling off, green flecks left in the wake.</li>
 * </ul>
 */
public final class StinkflyGooFx {
	private static final ResourceLocation GOO = ProjectHeroMod.id("textures/entity/omnitrix/stinkfly_goo.png");
	private static final DustParticleOptions FLECK = new DustParticleOptions(new Vector3f(0.74f, 0.9f, 0.32f), 0.8f);
	private static final DustParticleOptions DARK_FLECK = new DustParticleOptions(new Vector3f(0.36f, 0.52f, 0.1f), 0.7f);

	private static final int SHELL = 0xA0C8EC5A;
	private static final int CORE = 0xE04A7212;
	private static final int DROP = 0xB8C0E650;
	private static final int STRAND = 0xE8D8F070;
	private static final int KNOT = 0xF0B0DA48;
	private static final java.util.Set<String> FLYING = java.util.Set.of("stinkfly_slime", "stinkfly_spit", "stinkfly_net");

	private StinkflyGooFx() {
	}

	public static void init() {
		OmnitrixRenderers.projectileLook("stinkfly_slime", (e, partial, pose, buffers) -> slime(e, partial, pose, buffers, 3));
		OmnitrixRenderers.projectileLook("stinkfly_spit", (e, partial, pose, buffers) -> slime(e, partial, pose, buffers, 2));
		OmnitrixRenderers.projectileLook("stinkfly_net", StinkflyGooFx::net);
		// v0.17.2 (the Stinkfly revamp): the goo that stays -- patches / pools on any face, the stench cloud and the goo rain as
		// rendered volumes (not particle spam), the cocoon shell, the goo wall; and the cocoon stream drawn in the world
		for (Direction face : Direction.values()) {
			OmnitrixRenderers.projectileLook("stinkfly_goo_" + face.getName(), (e, partial, pose, buffers) -> patch(e, partial, pose, buffers, face));
		}
		OmnitrixRenderers.projectileLook("stinkfly_cloud", (e, partial, pose, buffers) -> cloud(e, partial, pose, buffers, false));
		OmnitrixRenderers.projectileLook("stinkfly_rain", (e, partial, pose, buffers) -> cloud(e, partial, pose, buffers, true));
		OmnitrixRenderers.projectileLook("stinkfly_cocoon", (e, partial, pose, buffers) -> cocoon(e, partial, pose, buffers, StinkflyKit.COCOON_TICKS));
		OmnitrixRenderers.projectileLook("stinkfly_cocoon_boss", (e, partial, pose, buffers) -> cocoon(e, partial, pose, buffers, StinkflyKit.COCOON_BOSS_TICKS));
		OmnitrixRenderers.projectileLook("stinkfly_wall_x", (e, partial, pose, buffers) -> wall(e, partial, pose, buffers, true));
		OmnitrixRenderers.projectileLook("stinkfly_wall_z", (e, partial, pose, buffers) -> wall(e, partial, pose, buffers, false));
		ClientTickEvents.END_CLIENT_TICK.register(StinkflyGooFx::tick);
		WorldRenderEvents.AFTER_ENTITIES.register(StinkflyGooFx::streams);
	}

	// ---------------------------------------------------------------- v0.17.2 the goo that stays

	private static final ResourceLocation POOL = QuirkFxDraw.DECALS[QuirkFx.DECAL_POOL];

	/** 0..1 in over the first {@code in} ticks, out over the last {@code out} of {@code life}. */
	private static float fade(float t, float life, float in, float out) {
		return Mth.clamp(t / in, 0f, 1f) * Mth.clamp((life - t) / out, 0f, 1f);
	}

	/**
	 * A goo patch / pool: a flat glossy decal on its block face (floor, wall or ceiling), wobbling a little, a darker heart and a
	 * wet highlight, fading at the end; on a floor, stretched goo strings up to the feet of whatever stands in it.
	 */
	private static void patch(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers, Direction face) {
		float t = e.tickCount + partial;
		float life = Math.max(20, e.colour());
		float a = fade(t, life, 3f, 20f);
		if (a <= 0.01f) {
			return;
		}
		float grow = 0.55f + 0.45f * Mth.clamp(t / 5f, 0f, 1f);
		float half = e.size() * 0.5f * grow;
		int light = light(e);
		float spin = (e.getId() * 37 % 360) * Mth.DEG_TO_RAD;
		pose.pushPose();
		Vector3f n = face.step();
		pose.mulPose(new Quaternionf().rotationTo(0f, 1f, 0f, n.x, n.y, n.z));
		pose.mulPose(Axis.YP.rotation(spin));
		VertexConsumer vc = QuirkFxDraw.matter(buffers, POOL);
		PoseStack.Pose last = pose.last();
		float wx = 1f + 0.05f * Mth.sin(t * 0.15f), wz = 1f - 0.05f * Mth.sin(t * 0.15f + 1f);
		flat(vc, last, half * wx, half * wz, 0.012f, 0xA8D848, 0.82f * a, light);
		flat(vc, last, half * 0.62f * wz, half * 0.58f * wx, 0.018f, 0x5E8A1C, 0.7f * a, light);
		pose.pushPose();
		pose.translate(-half * 0.25f, 0f, -half * 0.2f);
		flat(vc, pose.last(), half * 0.22f, half * 0.14f, 0.024f, 0xF4FFE0, 0.55f * a, LightTexture.FULL_BRIGHT);
		pose.popPose();
		pose.popPose();
		if (face == Direction.UP && e.level() != null) {
			// the strings: two stretched strands from the goo up to the feet of each creature stuck in it
			VertexConsumer goo = buffers.getBuffer(RenderType.entityTranslucent(GOO));
			PoseStack.Pose p0 = pose.last();
			Vec3 at = e.getPosition(partial);
			for (Entity o : e.level().getEntities(e, new net.minecraft.world.phys.AABB(at, at).inflate(half, 0.6, half),
					o -> o instanceof net.minecraft.world.entity.LivingEntity && o.isAlive())) {
				Vec3 f = o.getPosition(partial).subtract(at);
				float w = o.getBbWidth() * 0.3f;
				for (int s = -1; s <= 1; s += 2) {
					float sway = Mth.sin(t * 0.2f + s) * 0.05f;
					Vector3f base = new Vector3f((float) f.x + s * w * 1.4f, 0.02f, (float) f.z + sway);
					Vector3f top = new Vector3f((float) f.x + s * w, (float) f.y + 0.25f, (float) f.z);
					ribbon(p0, goo, base, top, 0.06f, STRAND, light);
				}
			}
		}
	}

	/** A flat quad in the XZ plane at height {@code y}, half-sizes {@code hx} / {@code hz}, both faces. */
	private static void flat(VertexConsumer vc, PoseStack.Pose last, float hx, float hz, float y, int rgb, float alpha, int light) {
		QuirkFxDraw.mv(vc, last, -hx, y, -hz, 0f, 0f, rgb, alpha, light, 0, 1, 0);
		QuirkFxDraw.mv(vc, last, -hx, y, hz, 0f, 1f, rgb, alpha, light, 0, 1, 0);
		QuirkFxDraw.mv(vc, last, hx, y, hz, 1f, 1f, rgb, alpha, light, 0, 1, 0);
		QuirkFxDraw.mv(vc, last, hx, y, -hz, 1f, 0f, rgb, alpha, light, 0, 1, 0);
	}

	/**
	 * The Stench Cloud (low, round where it sits) and the Goo Rain (a cloud 5.5 up raining globs): 8-9 big soft green puffs that
	 * slowly roll round and swell, a darker core inside; the rain's globs falling out of it. A few rendered shapes instead of the
	 * old particle spray (the user dislikes lots of particles).
	 */
	private static void cloud(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers, boolean rain) {
		float t = e.tickCount + partial;
		float life = Math.max(20, e.colour());
		float a = fade(t, life, 8f, 20f);
		if (a <= 0.01f) {
			return;
		}
		float r = e.size();
		int light = light(e);
		float base = rain ? 5.5f : 0.7f;
		Quaternionf cam = Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation();
		int puffs = rain ? 9 : 8;
		VertexConsumer smoke = QuirkFxDraw.matter(buffers, QuirkFxDraw.SMOKE);
		PoseStack.Pose last = pose.last();
		for (int i = 0; i < puffs; i++) {
			float h1 = QuirkFxDraw.hash(e.getId(), i, 1), h2 = QuirkFxDraw.hash(e.getId(), i, 2);
			float ang = i * Mth.TWO_PI / puffs + h1 + t * (rain ? 0.006f : 0.012f);
			float rr = r * (i == 0 ? 0f : 0.35f + 0.35f * h2);
			float y = base + (rain ? (h1 - 0.5f) * 0.6f : 0.15f + h2 * 0.9f) + 0.12f * Mth.sin(t * 0.05f + i);
			float swell = 0.85f + 0.25f * Mth.clamp(t / 30f, 0f, 1f) + 0.08f * Mth.sin(t * 0.07f + i * 1.3f);
			float size = r * (rain ? 0.42f : 0.5f) * (0.75f + 0.4f * h1) * swell;
			int rgb = (i % 3 == 0) ? 0x5A7A20 : (i % 3 == 1) ? 0x9CC048 : 0xC8E070;
			QuirkFxDraw.billboard(smoke, last, cam, new Vector3f(Mth.cos(ang) * rr, y, Mth.sin(ang) * rr), size, t * 0.01f + h2 * 6f, 0f, 1f, rgb,
					(rain ? 0.75f : 0.6f) * a, false, light);
		}
		// the darker core
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(GOO));
		int core = ((int) (0x70 * a) << 24) | 0x3E5A12;
		blob(pose.last(), vc, 0f, base + (rain ? 0f : 0.45f), 0f, r * 0.45f, r * (rain ? 0.18f : 0.32f), r * 0.45f, core, light, t, 0.12f);
		if (rain) {
			// the globs: a dozen falling out of the cloud at random spots, stretched by their fall
			for (int k = 0; k < 12; k++) {
				float ph = t * 0.07f + QuirkFxDraw.hash(e.getId(), k, 3) * 7f;
				int cycle = Mth.floor(ph);
				float f = ph - cycle;
				float h = QuirkFxDraw.hash(e.getId() + cycle * 31L, k, 4), h2 = QuirkFxDraw.hash(e.getId() + cycle * 31L, k, 5);
				float ang = h * Mth.TWO_PI, rr = Mth.sqrt(h2) * r * 0.85f;
				float y = base - 0.3f - f * f * (base - 0.2f);
				float s = 0.09f + 0.05f * h;
				blob(pose.last(), vc, Mth.cos(ang) * rr, y, Mth.sin(ang) * rr, s, s * (1.3f + f), s, ((int) (0xC0 * a) << 24) | 0xB8E04A, light, t, 0.1f);
			}
		}
	}

	/**
	 * The Goo Cocoon: a lumpy translucent shell round the hitbox (width from the "colour" field, height from the size), the
	 * darker core texture showing the creature inside; it bulges in its last second.
	 */
	private static void cocoon(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers, int life) {
		float t = e.tickCount + partial;
		float h = Math.max(0.4f, e.size()), w = Math.max(0.3f, e.colour() / 1000f);
		float wrap = Mth.clamp(t / 6f, 0f, 1f);
		float bulge = t > life - 20 ? 1f + 0.12f * ((t - (life - 20)) / 20f) + 0.04f * Mth.sin(t * 1.6f) : 1f;
		int light = light(e);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(GOO));
		PoseStack.Pose last = pose.last();
		float rx = (w * 0.5f + 0.18f) * bulge, ry = (h * 0.5f + 0.15f) * bulge * (0.4f + 0.6f * wrap);
		float cy = ry;
		blob(last, vc, 0f, cy, 0f, rx * 0.85f, ry * 0.88f, rx * 0.85f, 0x903E5A12, light, t, 0.06f);
		blob(last, vc, 0f, cy, 0f, rx, ry, rx, 0xB0B8E04A, light, t, 0.14f);
		// the lumps
		for (int i = 0; i < 7; i++) {
			float h1 = QuirkFxDraw.hash(e.getId(), i, 7), h2 = QuirkFxDraw.hash(e.getId(), i, 8);
			float ang = h1 * Mth.TWO_PI, yy = (h2 - 0.5f) * 1.5f * ry;
			float k = Mth.sqrt(Math.max(0f, 1f - (yy / ry) * (yy / ry)));
			float lr = rx * (0.28f + 0.15f * h2);
			blob(last, vc, Mth.cos(ang) * rx * k * 0.9f, cy + yy, Mth.sin(ang) * rx * k * 0.9f, lr, lr * 0.8f, lr, 0xC0A8D848, light, t + i * 3f, 0.15f);
		}
		blob(last, vc, -rx * 0.35f, cy + ry * 0.45f, -rx * 0.3f, rx * 0.22f, ry * 0.1f, rx * 0.18f, 0xD8FFFFF0, LightTexture.FULL_BRIGHT, t, 0f);
	}

	/**
	 * The Goo Wall: one translucent goo sheet across the gap (3 x 3), sagging in the middle and breathing a little, with goo
	 * strands along it and knots where they cross (the Slime Net's strands); {@code alongX}: its face points along x.
	 */
	private static void wall(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers, boolean alongX) {
		float t = e.tickCount + partial;
		float life = Math.max(20, e.colour());
		float a = fade(t, life, 4f, 20f);
		if (a <= 0.01f) {
			return;
		}
		float open = Mth.clamp(t / 5f, 0f, 1f);
		int light = light(e);
		pose.pushPose();
		if (alongX) {
			pose.mulPose(Axis.YP.rotation(Mth.HALF_PI));
		}
		final int n = 5;
		Vector3f[][] g = new Vector3f[n][n];
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				float u = i / (n - 1f) * 2f - 1f, v = j / (n - 1f) * 2f - 1f;
				float sag = -0.22f * (1f - u * u) * (v + 1f) * 0.5f;
				float breathe = 0.1f * (1f - u * u) * (1f - v * v) * Mth.sin(t * 0.09f + u);
				g[i][j] = new Vector3f(u * 1.5f * open, v * 1.5f * open + sag, breathe);
			}
		}
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(GOO));
		PoseStack.Pose last = pose.last();
		int sheet = ((int) (0x78 * a) << 24) | 0xB8E04A;
		for (int i = 0; i < n - 1; i++) {
			for (int j = 0; j < n - 1; j++) {
				Vector3f p00 = g[i][j], p10 = g[i + 1][j], p11 = g[i + 1][j + 1], p01 = g[i][j + 1];
				quadV(last, vc, p00.x, p00.y, p00.z, p10.x, p10.y, p10.z, p11.x, p11.y, p11.z, p01.x, p01.y, p01.z, new Vector3f(0, 0, 1), sheet, light);
			}
		}
		int strand = ((int) (0xE8 * a) << 24) | (STRAND & 0xFFFFFF);
		int knot = ((int) (0xF0 * a) << 24) | (KNOT & 0xFFFFFF);
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n; j++) {
				if (i + 1 < n) {
					ribbon(last, vc, g[i][j], g[i + 1][j], 0.07f, strand, light);
				}
				if (j + 1 < n) {
					ribbon(last, vc, g[i][j], g[i][j + 1], 0.07f, strand, light);
				}
				blob(last, vc, g[i][j].x, g[i][j].y, g[i][j].z, 0.09f, 0.09f, 0.09f, knot, light, t + i * 3 + j, 0.15f);
			}
		}
		pose.popPose();
	}

	/**
	 * The Goo Cocoon's stream: a wobbling tube of goo from Stinkfly's eyes to the target, thick and pumping at his face and
	 * tapering where it splashes on (drawn for every Stinkfly pumping one, {@code StinkflyKit.cocoonTarget}).
	 */
	private static void streams(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		if (!(com.projecthero.mod.omnitrix.alien.Aliens.kit(StinkflyKit.ID) instanceof StinkflyKit kit)) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = context.camera().getPosition();
		PoseStack pose = context.matrixStack();
		for (net.minecraft.world.entity.player.Player p : mc.level.players()) {
			if (!StinkflyKit.ID.equals(com.projecthero.mod.omnitrix.Omnitrix.active(p))) {
				continue;
			}
			int id = kit.cocoonTarget(p);
			Entity target = id < 0 ? null : mc.level.getEntity(id);
			if (target == null) {
				continue;
			}
			boolean fp = p == mc.player && mc.options.getCameraType().isFirstPerson();
			Vec3 look = p.getViewVector(partial);
			Vec3 from = p.getEyePosition(partial).add(look.scale(fp ? 0.6 : 0.35)).add(0, fp ? -0.25 : -0.02, 0);
			Vec3 to = target.getPosition(partial).add(0, target.getBbHeight() * 0.5, 0);
			float t = mc.level.getGameTime() + partial;
			int light = LightTexture.pack(12, 15);
			pose.pushPose();
			pose.translate(from.x - cam.x, from.y - cam.y, from.z - cam.z);
			PoseStack.Pose last = pose.last();
			VertexConsumer vc = context.consumers().getBuffer(RenderType.entityTranslucent(GOO));
			Vec3 span = to.subtract(from);
			Vec3 side = span.cross(new Vec3(0, 1, 0));
			side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
			Vec3 up = side.cross(span).normalize();
			final int segs = 12;
			Vector3f prev = new Vector3f();
			for (int i = 1; i <= segs; i++) {
				float f = i / (float) segs;
				float wob = Mth.sin(t * 0.9f - f * 9f) * 0.08f * Mth.sin(f * Mth.PI);
				Vec3 q = span.scale(f).add(side.scale(wob)).add(up.scale(wob * 0.6));
				Vector3f cur = new Vector3f((float) q.x, (float) q.y, (float) q.z);
				float pump = 1f + 0.25f * Mth.sin(t * 1.2f - f * 12f);
				float w = (0.26f - 0.12f * f) * pump;
				ribbon(last, vc, prev, cur, w, 0xC8C8EC5A, light);
				ribbon(last, vc, prev, cur, w * 0.45f, 0xE04A7212, light);
				prev = cur;
			}
			pose.popPose();
		}
	}

	// ---------------------------------------------------------------- the looks

	private static void slime(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers, int drops) {
		float t = e.tickCount + partial;
		Vec3 v = e.getDeltaMovement();
		pose.pushPose();
		pose.translate(0.0, e.getBbHeight() * 0.5, 0.0);
		face(pose, v);
		float r = e.size() * 0.55f;
		float stretch = 1.05f + Mth.clamp((float) v.length() * 0.12f, 0f, 0.22f) + 0.1f * Mth.sin(t * 1.1f);
		float squash = 1f / Mth.sqrt(stretch);
		float wx = squash * (1f + 0.08f * Mth.sin(t * 1.7f)), wy = squash * (1f - 0.08f * Mth.sin(t * 1.7f));
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(GOO));
		int light = light(e);
		PoseStack.Pose last = pose.last();
		// the darker core, the clear shell round it, the glossy highlight (lit as if wet)
		blob(last, vc, 0f, -r * 0.05f, r * 0.12f, r * 0.55f * wx, r * 0.5f * wy, r * 0.62f * stretch, CORE, light, t, 0.14f);
		blob(last, vc, 0f, 0f, 0f, r * wx, r * wy, r * stretch, SHELL, light, t, 0.09f);
		blob(last, vc, -r * 0.3f * wx, r * 0.5f * wy, r * 0.3f, r * 0.2f, r * 0.13f, r * 0.26f, 0xD8FFFFF0, LightTexture.FULL_BRIGHT, t, 0f);
		// the droplets strung out behind, sagging a little and wobbling apart
		float z = -r * stretch;
		float prevY = 0f, prevZ = z + r * 0.3f;
		for (int k = 1; k <= drops; k++) {
			float dr = r * (0.48f - k * 0.12f);
			z -= dr + r * 0.35f + 0.05f * Mth.sin(t * 0.8f + k);
			float y = -0.012f * k * k + Mth.sin(t * 1.3f + k * 1.7f) * r * 0.08f;
			ribbon(last, vc, new Vector3f(0f, prevY, prevZ), new Vector3f(0f, y, z), r * 0.08f, DROP, light);
			blob(last, vc, Mth.sin(t * 0.9f + k) * r * 0.1f, y, z, dr, dr, dr * 1.25f, DROP, light, t + k * 5f, 0.12f);
			prevY = y;
			prevZ = z - dr;
			z -= dr;
		}
		pose.popPose();
	}

	private static void net(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		float t = e.tickCount + partial;
		Vec3 v = e.getDeltaMovement();
		pose.pushPose();
		pose.translate(0.0, e.getBbHeight() * 0.5, 0.0);
		face(pose, v);
		float radius = netRadius(e.size(), t);
		pose.mulPose(Axis.ZP.rotation(t * 0.22f));
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(GOO));
		int light = light(e);
		PoseStack.Pose last = pose.last();
		final int spokes = 8;
		final float[] rings = { 0.4f, 0.75f, 1f };
		Vector3f[][] pts = new Vector3f[spokes][rings.length];
		for (int k = 0; k < spokes; k++) {
			for (int i = 0; i < rings.length; i++) {
				float f = rings[i];
				float a = k * Mth.TWO_PI / spokes + 0.08f * Mth.sin(t * 0.7f + k * 2.1f) * f;
				float rr = radius * f * (1f + 0.05f * Mth.sin(t * 0.9f + k + i));
				// bowed back like a parachute: the rim trails behind the hub
				pts[k][i] = new Vector3f(Mth.cos(a) * rr, Mth.sin(a) * rr, -radius * 0.4f * f * f);
			}
		}
		float w = 0.022f + radius * 0.01f;
		Vector3f hub = new Vector3f();
		for (int k = 0; k < spokes; k++) {
			// the spoke: hub -> each ring
			Vector3f prev = hub;
			for (int i = 0; i < rings.length; i++) {
				ribbon(last, vc, prev, pts[k][i], w, STRAND, light);
				prev = pts[k][i];
			}
			// the rings: each span sagging in toward the hub (the rim sags most -- a scalloped edge)
			for (int i = 0; i < rings.length; i++) {
				Vector3f a = pts[k][i], b = pts[(k + 1) % spokes][i];
				float sag = i == rings.length - 1 ? 0.8f : 0.9f;
				Vector3f mid = new Vector3f(a).add(b).mul(0.5f);
				mid.x *= sag;
				mid.y *= sag;
				ribbon(last, vc, a, mid, w * 0.85f, STRAND, light);
				ribbon(last, vc, mid, b, w * 0.85f, STRAND, light);
			}
			// the knots where the strands cross
			for (int i = 0; i < rings.length; i++) {
				float kr = (i == rings.length - 1 ? 0.07f : 0.05f) * (0.6f + 0.4f * Math.min(1f, radius));
				Vector3f p = pts[k][i];
				blob(last, vc, p.x, p.y, p.z, kr, kr, kr, KNOT, light, t + k * 3f + i, 0.15f);
			}
		}
		// the fat glob at the hub
		float hr = 0.12f + e.size() * 0.08f;
		blob(last, vc, 0f, 0f, 0f, hr * 0.6f, hr * 0.6f, hr * 0.6f, CORE, light, t, 0.12f);
		blob(last, vc, 0f, 0f, 0f, hr, hr, hr * 1.15f, SHELL, light, t, 0.1f);
		blob(last, vc, -hr * 0.3f, hr * 0.5f, hr * 0.35f, hr * 0.22f, hr * 0.15f, hr * 0.22f, 0xD8FFFFF0, LightTexture.FULL_BRIGHT, t, 0f);
		pose.popPose();
	}

	/** The net's radius: it springs open over its first 7 ticks (with a little overshoot), to 1.55 x its size. */
	static float netRadius(float size, float t) {
		float x = Mth.clamp(t / 7f, 0f, 1f);
		float c1 = 1.4f, c3 = c1 + 1f, u = x - 1f;
		float open = 1f + c3 * u * u * u + c1 * u * u;
		return 0.12f + size * 1.55f * open;
	}

	// ---------------------------------------------------------------- the drips

	private static void tick(Minecraft mc) {
		if (mc.level == null || mc.isPaused()) {
			return;
		}
		RandomSource r = mc.level.random;
		for (Entity en : mc.level.entitiesForRendering()) {
			// (only the flying goo: the patches, clouds, cocoons and walls are held in place -- "held" is not synced, so by kind)
			if (!(en instanceof AlienProjectileEntity e) || !FLYING.contains(e.kind())) {
				continue;
			}
			boolean net = e.kind().equals("stinkfly_net");
			float s = e.size();
			Vec3 v = e.getDeltaMovement();
			double x = e.getX(), y = e.getY() + e.getBbHeight() * 0.5, z = e.getZ();
			// flecks left in the wake
			mc.level.addParticle(r.nextInt(3) == 0 ? DARK_FLECK : FLECK, x - v.x * 0.5 + (r.nextDouble() - 0.5) * s * 0.4,
					y - v.y * 0.5 + (r.nextDouble() - 0.5) * s * 0.4, z - v.z * 0.5 + (r.nextDouble() - 0.5) * s * 0.4, 0, 0, 0);
			// drips falling off underneath
			if (r.nextInt(net ? 1 : 3) == 0) {
				double ox = 0, oz = 0;
				if (net) {
					// off the rim: a random point round the ring, in the plane across the flight
					Vec3 d = v.lengthSqr() > 1e-6 ? v.normalize() : new Vec3(0, 0, 1);
					Vec3 side = d.cross(new Vec3(0, 1, 0));
					side = side.lengthSqr() > 1e-6 ? side.normalize() : new Vec3(1, 0, 0);
					double rad = netRadius(s, e.tickCount) * (0.6 + r.nextDouble() * 0.4);
					double a = r.nextDouble() * Math.PI * 2;
					Vec3 up = side.cross(d).normalize();
					Vec3 off = side.scale(Math.cos(a) * rad).add(up.scale(Math.sin(a) * rad));
					ox = off.x;
					oz = off.z;
					y += off.y;
				}
				mc.level.addParticle(ParticleTypes.ITEM_SLIME, x + ox + (r.nextDouble() - 0.5) * s * 0.3, y - s * 0.3, z + oz + (r.nextDouble() - 0.5) * s * 0.3,
						v.x * 0.15, -0.06, v.z * 0.15);
			}
		}
	}

	// ---------------------------------------------------------------- drawing helpers

	/** Turns the pose so +z runs along {@code v} (the blob stretches along its flight). */
	private static void face(PoseStack pose, Vec3 v) {
		if (v.lengthSqr() < 1e-6) {
			return;
		}
		double h = Math.sqrt(v.x * v.x + v.z * v.z);
		pose.mulPose(Axis.YP.rotation((float) Math.atan2(v.x, v.z)));
		pose.mulPose(Axis.XP.rotation((float) -Math.atan2(v.y, h)));
	}

	private static int light(AlienProjectileEntity e) {
		int l = LevelRenderer.getLightColor(e.level(), e.blockPosition());
		// the goo is a little luminous: never darker than block light 9
		return LightTexture.pack(Math.max(LightTexture.block(l), 9), LightTexture.sky(l));
	}

	/** A wobbling ellipsoid (radii rx, ry, rz) at (cx, cy, cz), tinted {@code argb}; {@code wob} the surface wobble. */
	private static void blob(PoseStack.Pose last, VertexConsumer vc, float cx, float cy, float cz, float rx, float ry, float rz, int argb, int light,
			float t, float wob) {
		final int lat = 7, lon = 12;
		for (int i = 0; i < lat; i++) {
			for (int j = 0; j < lon; j++) {
				vert(last, vc, cx, cy, cz, rx, ry, rz, i, j, lat, lon, argb, light, t, wob);
				vert(last, vc, cx, cy, cz, rx, ry, rz, i, j + 1, lat, lon, argb, light, t, wob);
				vert(last, vc, cx, cy, cz, rx, ry, rz, i + 1, j + 1, lat, lon, argb, light, t, wob);
				vert(last, vc, cx, cy, cz, rx, ry, rz, i + 1, j, lat, lon, argb, light, t, wob);
			}
		}
	}

	private static void vert(PoseStack.Pose last, VertexConsumer vc, float cx, float cy, float cz, float rx, float ry, float rz, int i, int j, int lat,
			int lon, int argb, int light, float t, float wob) {
		float phi = Mth.PI * i / lat, lam = Mth.TWO_PI * j / lon;
		float nx = Mth.sin(phi) * Mth.cos(lam), ny = Mth.cos(phi), nz = Mth.sin(phi) * Mth.sin(lam);
		float w = 1f + wob * Mth.sin(3f * lam + t * 0.8f) * Mth.sin(2f * phi + t * 0.6f);
		int a = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
		vc.addVertex(last, cx + nx * rx * w, cy + ny * ry * w, cz + nz * rz * w).setColor(r, g, b, a).setUv((float) j / lon, (float) i / lat)
				.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, nx, ny, nz);
	}

	/** A goo strand from a to b: two crossed ribbons {@code w} wide, so it reads from every side. */
	private static void ribbon(PoseStack.Pose last, VertexConsumer vc, Vector3f a, Vector3f b, float w, int argb, int light) {
		Vector3f d = new Vector3f(b).sub(a);
		if (d.lengthSquared() < 1e-8f) {
			return;
		}
		Vector3f p1 = new Vector3f(d).cross(0f, 0f, 1f);
		if (p1.lengthSquared() < 1e-8f) {
			p1.set(1f, 0f, 0f);
		}
		p1.normalize().mul(w * 0.5f);
		Vector3f p2 = new Vector3f(d).cross(p1).normalize().mul(w * 0.5f);
		for (Vector3f p : new Vector3f[] { p1, p2 }) {
			Vector3f n = new Vector3f(d).cross(p).normalize();
			quadV(last, vc, a.x - p.x, a.y - p.y, a.z - p.z, b.x - p.x, b.y - p.y, b.z - p.z, b.x + p.x, b.y + p.y, b.z + p.z, a.x + p.x, a.y + p.y,
					a.z + p.z, n, argb, light);
		}
	}

	private static void quadV(PoseStack.Pose last, VertexConsumer vc, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy,
			float cz, float dx, float dy, float dz, Vector3f n, int argb, int light) {
		int a = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
		vc.addVertex(last, ax, ay, az).setColor(r, g, b, a).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, n.x, n.y, n.z);
		vc.addVertex(last, bx, by, bz).setColor(r, g, b, a).setUv(1f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, n.x, n.y, n.z);
		vc.addVertex(last, cx, cy, cz).setColor(r, g, b, a).setUv(1f, 0.25f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, n.x, n.y, n.z);
		vc.addVertex(last, dx, dy, dz).setColor(r, g, b, a).setUv(0f, 0.25f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, n.x, n.y, n.z);
	}
}
