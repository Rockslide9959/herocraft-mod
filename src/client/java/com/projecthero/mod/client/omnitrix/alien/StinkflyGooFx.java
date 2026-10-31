package com.projecthero.mod.client.omnitrix.alien;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.OmnitrixRenderers;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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

	private StinkflyGooFx() {
	}

	public static void init() {
		OmnitrixRenderers.projectileLook("stinkfly_slime", (e, partial, pose, buffers) -> slime(e, partial, pose, buffers, 3));
		OmnitrixRenderers.projectileLook("stinkfly_spit", (e, partial, pose, buffers) -> slime(e, partial, pose, buffers, 2));
		OmnitrixRenderers.projectileLook("stinkfly_net", StinkflyGooFx::net);
		ClientTickEvents.END_CLIENT_TICK.register(StinkflyGooFx::tick);
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
			if (!(en instanceof AlienProjectileEntity e) || !e.kind().startsWith("stinkfly_")) {
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
