package com.projecthero.mod.client.omnitrix.alien.upchuck;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.mutation.MutationRender;
import com.projecthero.mod.client.mutation.pyro.PyroDraw;
import com.projecthero.mod.client.omnitrix.OmnitrixFxRenderType;
import com.projecthero.mod.client.omnitrix.OmnitrixRenderers;
import com.projecthero.mod.client.omnitrix.alien.UpchuckClient;
import com.projecthero.mod.client.omnitrix.alien.brainstorm.BrainstormFx;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.upchuck.UpchuckKit;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (user: "upgrade his abilities vfx and models please, make it actually look like his spit bombs and stuff from the show"):
 * Upchuck's spit, as the show draws it. A loogie is a smooth glowing ball of green energy: a bright core shading to saturated lime at its
 * edge, a soft halo, drawn out into a teardrop by its flight with a tapering tail and a few drops strung behind it. It goes off as a
 * green energy explosion (a flash, a swelling shell, gobs thrown out on arcs) and leaves a wet splat. All of it is real geometry shaded
 * by where the camera is, on the flat unlit render type so the colour holds in daylight, and every piece is synced: the bursts and
 * puddles are held projectiles the server spawns ({@code UpchuckKit.fx}), so everybody sees them.
 *
 * <p>Round the player: the suction streams of an open Gobble, the Vomit Rocket's jet and the Mega Loogie swelling in his mouth.
 */
public final class UpchuckFx {
	private UpchuckFx() {
	}

	private static boolean registered;
	/** White-hot middle, the lime body, the saturated edge. */
	private static final int HOT = 0xF6FFD0, LIME = 0xA8FF3C, EDGE = 0x2FC414;

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		OmnitrixRenderers.projectileLook("upchuck_spit", (e, partial, pose, buffers) -> loogie(e, partial, pose, buffers, 0.62f, true));
		OmnitrixRenderers.projectileLook("upchuck_curve", (e, partial, pose, buffers) -> loogie(e, partial, pose, buffers, 0.62f, true));
		OmnitrixRenderers.projectileLook("upchuck_mega", (e, partial, pose, buffers) -> loogie(e, partial, pose, buffers, 0.75f, true));
		OmnitrixRenderers.projectileLook("upchuck_slime_spit", (e, partial, pose, buffers) -> loogie(e, partial, pose, buffers, 0.55f, false));
		OmnitrixRenderers.projectileLook("upchuck_slime", (e, partial, pose, buffers) -> loogie(e, partial, pose, buffers, 0.6f, false));
		OmnitrixRenderers.projectileLook("upchuck_burst", UpchuckFx::burst);
		OmnitrixRenderers.projectileLook("upchuck_puddle", UpchuckFx::puddle);
		WorldRenderEvents.AFTER_ENTITIES.register(UpchuckFx::world);
	}

	// ---------------------------------------------------------------- the smooth mesh

	/** How a shell's alpha falls off across it: solid, a soft halo (fades to its silhouette) or a hollow shell (brightest at its rim). */
	private static final int SOLID = 0, HALO = 1, HOLLOW = 2;

	/**
	 * A smooth ball round the pose origin, drawn out behind it into a teardrop ({@code tail} radii past the back; along {@code dir}),
	 * shaded by the view: {@code core} facing the camera, through {@code mid}, to {@code rim} at its silhouette. Only the faces towards the
	 * camera are drawn: {@link OmnitrixFxRenderType#FLAT} writes no depth, so the back would paint over the front.
	 */
	private static void drop(PoseStack pose, VertexConsumer vc, Vec3 view, Vec3 dir, float r, float tail, float time, int seed, int core, int mid, int rim,
			float alpha, int mode, int rings, int segs) {
		Vec3 w = dir.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : dir.normalize();
		Vec3 u = w.cross(Math.abs(w.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
		Vec3 v = w.cross(u);
		float[][] px = new float[rings + 1][segs + 1], py = new float[rings + 1][segs + 1], pz = new float[rings + 1][segs + 1], f = new float[rings + 1][segs + 1];
		for (int i = 0; i <= rings; i++) {
			float a = Mth.PI * i / rings;
			float cos = Mth.cos(a), sin = Mth.sin(a);
			float back = Math.max(0f, -cos); // 0 at the waist, 1 at the very back
			float axial = cos * (cos > 0f ? 1f : 1f + tail);
			float radial = sin * (1f - 0.35f * Math.min(1f, tail) * back);
			for (int j = 0; j <= segs; j++) {
				float b = Mth.TWO_PI * j / segs;
				float cb = Mth.cos(b), sb = Mth.sin(b);
				float bump = 1f + 0.03f * Mth.sin(b * 2f + time * 0.45f + seed) * Mth.sin(a * 2f + time * 0.31f);
				double x = u.x * radial * cb + v.x * radial * sb + w.x * axial;
				double y = u.y * radial * cb + v.y * radial * sb + w.y * axial;
				double z = u.z * radial * cb + v.z * radial * sb + w.z * axial;
				px[i][j] = (float) x * r * bump;
				py[i][j] = (float) y * r * bump;
				pz[i][j] = (float) z * r * bump;
				// the surface normal of the stretched shell (close enough to shade by): the radial part, and the axial part squeezed by the stretch
				double ax = cos > 0f ? cos : cos / (1f + tail);
				double nx = u.x * sin * cb + v.x * sin * sb + w.x * ax, ny = u.y * sin * cb + v.y * sin * sb + w.y * ax, nz = u.z * sin * cb + v.z * sin * sb + w.z * ax;
				double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
				f[i][j] = l < 1.0e-5 ? 0f : (float) ((nx * view.x + ny * view.y + nz * view.z) / l);
			}
		}
		org.joml.Matrix4f m = pose.last().pose();
		for (int i = 0; i < rings; i++) {
			for (int j = 0; j < segs; j++) {
				if (Math.max(Math.max(f[i][j], f[i + 1][j]), Math.max(f[i + 1][j + 1], f[i][j + 1])) < 0f) {
					continue; // faces away
				}
				vert(vc, m, px[i][j], py[i][j], pz[i][j], f[i][j], core, mid, rim, alpha, mode);
				vert(vc, m, px[i + 1][j], py[i + 1][j], pz[i + 1][j], f[i + 1][j], core, mid, rim, alpha, mode);
				vert(vc, m, px[i + 1][j + 1], py[i + 1][j + 1], pz[i + 1][j + 1], f[i + 1][j + 1], core, mid, rim, alpha, mode);
				vert(vc, m, px[i][j + 1], py[i][j + 1], pz[i][j + 1], f[i][j + 1], core, mid, rim, alpha, mode);
			}
		}
	}

	private static void vert(VertexConsumer vc, org.joml.Matrix4f m, float x, float y, float z, float facing, int core, int mid, int rim, float alpha, int mode) {
		float d = Mth.clamp(facing, 0f, 1f);
		int c = d > 0.55f ? PyroDraw.lerpRgb(mid, core, (d - 0.55f) / 0.45f) : PyroDraw.lerpRgb(rim, mid, d / 0.55f);
		// a hollow shell is brightest just inside its rim, and fades out at the very edge so the rim has no hard line
		float al = mode == SOLID ? alpha : mode == HALO ? alpha * d * d : alpha * (1f - d) * (1f - d) * Math.min(1f, d * 7f);
		vc.addVertex(m, x, y, z).setColor(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f, al);
	}

	/** Mesh rings for a ball of radius {@code r}: fine enough to stay round when it fills the view (segments: 1.7 times this). */
	private static int detail(float r) {
		return Mth.clamp((int) (16f + r * 30f), 16, 40);
	}

	private static Vec3 toCamera(Vec3 from) {
		Vec3 d = BrainstormFx.cam().subtract(from);
		return d.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : d.normalize();
	}

	// ---------------------------------------------------------------- a loogie in flight

	/** A loogie: {@code glow} for an energy bomb, off for plain slime (duller, no halo). {@code colour} is lime, gold the richer his Gut. */
	private static void loogie(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers, float k, boolean glow) {
		float t = e.tickCount + partial;
		float r = e.size() * k;
		Vec3 at = e.getPosition(partial).add(0, e.getBbHeight() * 0.5, 0);
		Vec3 vel = e.getDeltaMovement();
		Vec3 dir = vel.lengthSqr() < 1.0e-5 ? new Vec3(0, 1, 0) : vel.normalize();
		float grow = Mth.clamp(t / 3f, 0.35f, 1f); // it swells as it leaves his mouth
		r *= grow;
		float pulse = 1f + 0.05f * Mth.sin(t * 0.9f);
		Vec3 view = toCamera(at);
		int body = e.colour();
		int core = glow ? PyroDraw.lerpRgb(body, HOT, 0.8f) : PyroDraw.lerpRgb(body, 0xE8FFC0, 0.35f);
		int rim = glow ? PyroDraw.lerpRgb(body, EDGE, 0.75f) : PyroDraw.lerpRgb(body, 0x3E8A1E, 0.75f);
		int id = e.getId();
		pose.pushPose();
		pose.translate(0, e.getBbHeight() * 0.5, 0);
		VertexConsumer vc = buffers.getBuffer(OmnitrixFxRenderType.FLAT);
		float tail = 1.35f + 0.25f * Mth.sin(t * 0.7f);
		int fine = detail(r);
		if (glow) {
			drop(pose, vc, view, dir, r * 2.4f * pulse, tail, t, id, PyroDraw.lerpRgb(body, HOT, 0.3f), body, rim, 0.2f, HALO, fine, fine * 2);
			drop(pose, vc, view, dir, r * 1.45f * pulse, tail, t, id, PyroDraw.lerpRgb(body, HOT, 0.5f), body, rim, 0.55f, HALO, fine, fine * 2);
		}
		drop(pose, vc, view, dir, r * pulse, tail * 0.9f, t, id, core, body, rim, 1f, SOLID, fine, fine * 2);
		// drops strung out behind it, wandering a little
		Vec3 side = dir.cross(Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
		Vec3 up = dir.cross(side);
		int drops = 4;
		double flown = t * vel.length(); // never strung out further than it has come: a big loogie's would reach back past his camera
		for (int i = 0; i < drops; i++) {
			// spaced by a small loogie's size even on the Mega: big drops strung out far behind would swing past the camera
			float gap = Math.min(r, 0.3f);
			float s = r / gap + 1.2f + i * 1.15f + 0.3f * Mth.sin(t * 0.8f + i);
			if (s * gap + 2f > flown) {
				break;
			}
			float ph = i * 2.4f + id;
			Vec3 off = dir.scale(-s * gap).add(side.scale(Mth.sin(t * 0.5f + ph) * gap * 0.4f)).add(up.scale(Mth.cos(t * 0.43f + ph) * gap * 0.4f));
			float dr = gap * 0.36f * (1f - i / (float) (drops + 1));
			pose.pushPose();
			pose.translate(off.x, off.y, off.z);
			drop(pose, vc, view, dir, dr, 0.6f, t, id + i, core, body, rim, glow ? 0.95f : 0.85f, SOLID, 7, 10);
			pose.popPose();
		}
		pose.popPose();
	}

	// ---------------------------------------------------------------- the explosion

	/** A loogie going off (size = radius in blocks, colour = its colour): a flash, a swelling shell, gobs thrown out on arcs. */
	private static void burst(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		float t = (e.tickCount + partial) / UpchuckKit.BURST_TICKS;
		if (t >= 1f) {
			return;
		}
		float ease = 1f - (1f - t) * (1f - t) * (1f - t);
		float fade = 1f - t * t;
		float size = e.size();
		int body = e.colour();
		Vec3 at = e.getPosition(partial).add(0, e.getBbHeight() * 0.5, 0);
		Vec3 view = toCamera(at);
		Vec3 up = new Vec3(0, 1, 0);
		float time = e.tickCount + partial;
		int id = e.getId();
		pose.pushPose();
		pose.translate(0, e.getBbHeight() * 0.5, 0);
		VertexConsumer vc = buffers.getBuffer(OmnitrixFxRenderType.FLAT);
		int fine = detail(size * 0.5f);
		// the swelling shell of energy, brightest at its edge, in a soft glow
		drop(pose, vc, view, up, size * (0.5f + 1.1f * ease), 0f, time, id, HOT, body, EDGE, 0.3f * fade, HALO, fine, fine * 2);
		drop(pose, vc, view, up, size * (0.3f + 0.75f * ease), 0f, time, id, HOT, body, EDGE, 0.75f * fade, HOLLOW, fine, fine * 2);
		drop(pose, vc, view, up, size * (0.25f + 0.62f * ease), 0f, time * 1.3f, id + 1, HOT, LIME, body, 0.5f * fade * fade, HALO, fine, fine * 2);
		// the flash, gone by a third of the way through
		float flash = 1f - t * 3f;
		if (flash > 0f) {
			drop(pose, vc, view, up, size * (0.2f + 0.3f * (1f - flash)), 0f, time, id, 0xFFFFFF, HOT, LIME, flash, SOLID, fine, fine * 2);
		}
		// gobs thrown out on arcs: fly, fall, shrink
		int n = Math.min(24, 8 + (int) (size * 4f));
		for (int i = 0; i < n; i++) {
			float h1 = hash(i * 12.9898f + id), h2 = hash(i * 78.233f + id), h3 = hash(i * 37.719f + id);
			float yaw = h1 * Mth.TWO_PI, rise = h2 * 1.5f - 0.35f;
			float speed = 0.75f + 0.75f * h3;
			float dist = size * speed * ease;
			Vec3 off = new Vec3(Mth.cos(yaw) * Mth.cos(rise), Mth.sin(rise), Mth.sin(yaw) * Mth.cos(rise)).scale(dist).add(0, -size * 0.55f * t * t, 0);
			float gob = (0.09f + 0.07f * size) * (0.6f + 0.6f * h3) * (1f - t * 0.75f);
			Vec3 way = off.lengthSqr() < 1.0e-5 ? up : off;
			pose.pushPose();
			pose.translate(off.x, off.y, off.z);
			drop(pose, vc, view, way, gob, 0.9f, time, id + i, PyroDraw.lerpRgb(body, HOT, 0.6f), body, EDGE, fade, SOLID, 7, 10);
			pose.popPose();
		}
		pose.popPose();
	}

	private static float hash(float x) {
		return Mth.frac(Mth.sin(x) * 43758.547f);
	}

	// ---------------------------------------------------------------- the splat

	/** Slime on the ground (radius = size; colour = its life in ticks): a wet pool with a darker, soft, lobed edge, bubbling. */
	private static void puddle(AlienProjectileEntity e, float partial, PoseStack pose, MultiBufferSource buffers) {
		float t = e.tickCount + partial;
		float life = Math.max(20, e.colour());
		float in = Mth.clamp(t / 4f, 0f, 1f) * Mth.clamp((life - t) / 18f, 0f, 1f);
		if (in <= 0.01f) {
			return;
		}
		float r = e.size() * (0.55f + 0.45f * Mth.clamp(t / 5f, 0f, 1f));
		int alpha = (int) (205 * in);
		int id = e.getId();
		int segs = 40;
		pose.pushPose();
		pose.translate(0, 0.035, 0);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(MutationRender.WHITE));
		int light = LevelRenderer.getLightColor(e.level(), BlockPos.containing(e.position().add(0, 0.3, 0)));
		float[] edge = new float[segs + 1];
		for (int j = 0; j <= segs; j++) {
			float a = Mth.TWO_PI * j / segs;
			edge[j] = r * (0.84f + 0.1f * Mth.sin(a * 3f + id) + 0.07f * Mth.sin(a * 7f + id * 2f) + 0.04f * Mth.sin(a * 13f + id * 3f));
		}
		fan(pose, vc, edge, 0f, 0.72f, 0x86D83A, 0x58B82A, alpha, alpha, light, 0f);
		fan(pose, vc, edge, 0.72f, 1f, 0x58B82A, 0x2E7A14, alpha, 0, light, 0f);
		pose.popPose();
		// bubbles that swell and pop, each on its own clock
		VertexConsumer solid = PyroDraw.solidBuffer(buffers);
		int n = (int) Math.min(9, 2 + r * 1.5f);
		for (int i = 0; i < n; i++) {
			float ph = (t * 0.06f + i * 0.37f) % 1f;
			float h1 = hash(i * 12.9898f + id), h2 = hash(i * 78.233f + id);
			double ang = h1 * Math.PI * 2, d = Math.sqrt(h2) * r * 0.7;
			pose.pushPose();
			pose.translate(Math.cos(ang) * d, 0.05, Math.sin(ang) * d);
			PyroDraw.sphere(pose, solid, (0.03f + 0.07f * ph) * in, 0xFFC8F8A0, 0xFF6CD430, 0f, t, light);
			pose.popPose();
		}
	}

	/** One band of a flat, wavy-edged disc: from {@code s0} to {@code s1} of the way out to {@code edge[angle]}. */
	private static void fan(PoseStack pose, VertexConsumer vc, float[] edge, float s0, float s1, int c0, int c1, int a0, int a1, int light, float lift) {
		PoseStack.Pose last = pose.last();
		int segs = edge.length - 1;
		for (int j = 0; j < segs; j++) {
			float b0 = Mth.TWO_PI * j / segs, b1 = Mth.TWO_PI * (j + 1) / segs;
			float x00 = Mth.cos(b0) * edge[j] * s0, z00 = Mth.sin(b0) * edge[j] * s0, x01 = Mth.cos(b1) * edge[j + 1] * s0, z01 = Mth.sin(b1) * edge[j + 1] * s0;
			float x10 = Mth.cos(b0) * edge[j] * s1, z10 = Mth.sin(b0) * edge[j] * s1, x11 = Mth.cos(b1) * edge[j + 1] * s1, z11 = Mth.sin(b1) * edge[j + 1] * s1;
			flat(vc, last, x00, lift, z00, c0, a0, light);
			flat(vc, last, x01, lift, z01, c0, a0, light);
			flat(vc, last, x11, lift, z11, c1, a1, light);
			flat(vc, last, x10, lift, z10, c1, a1, light);
		}
	}

	private static void flat(VertexConsumer vc, PoseStack.Pose last, float x, float y, float z, int rgb, int a, int light) {
		vc.addVertex(last, x, y, z).setColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, a).setUv(0.5f, 0.5f)
				.setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, 0f, 1f, 0f);
	}

	// ---------------------------------------------------------------- round the player

	private static void world(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || ctx.consumers() == null || ctx.matrixStack() == null) {
			return;
		}
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = ctx.camera().getPosition();
		float time = mc.level.getGameTime() + partial;
		for (Player p : mc.level.players()) {
			if (!UpchuckKit.isUpchuck(p)) {
				continue;
			}
			var s = Omnitrix.state(p);
			int flags = s.kitFlags;
			boolean mega = s.animId == UpchuckKit.ANIM_MEGA && time - s.animStart < UpchuckKit.MEGA_WINDUP;
			boolean rocket = (flags & UpchuckKit.F_ROCKET) != 0;
			boolean gobble = (flags & UpchuckKit.F_GOBBLE) != 0;
			boolean frenzy = (flags & UpchuckKit.F_FRENZY) != 0;
			if (!mega && !rocket && !gobble && !frenzy) {
				continue;
			}
			PoseStack pose = ctx.matrixStack();
			if (frenzy) {
				UpchuckClient.frenzyTongues(pose, ctx.consumers(), p, partial, time, cam);
			}
			VertexConsumer vc = ctx.consumers().getBuffer(OmnitrixFxRenderType.FLAT);
			if (gobble) {
				suction(pose, vc, p, partial, time, cam);
			}
			if (mega) {
				charge(pose, vc, p, partial, (time - s.animStart) / UpchuckKit.MEGA_WINDUP, time, cam);
			}
			if (rocket) {
				jet(pose, vc, p, partial, time - (s.readyAt(UpchuckKit.ID + "." + UpchuckKit.ROCKET_UNTIL) - UpchuckKit.ROCKET_SAFE), time, cam);
			}
		}
	}

	/** Gobble: streams of air racing in a spiral to his open mouth from the cone in front of it. */
	private static void suction(PoseStack pose, VertexConsumer vc, Player p, float partial, float time, Vec3 cam) {
		Vec3 mouth = UpchuckClient.mouth(p, partial);
		Vec3 look = p.getViewVector(partial);
		Vec3 u = look.cross(Math.abs(look.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
		Vec3 v = look.cross(u);
		float len = (float) UpchuckKit.GOBBLE_RADIUS * 0.6f;
		int streams = 12;
		org.joml.Matrix4f m = pose.last().pose();
		for (int i = 0; i < streams; i++) {
			float seed = hash(i * 12.9898f);
			float head = (time * 0.075f + seed + i * 0.37f) % 1f; // 0 far out .. 1 at the mouth
			float spread = 0.55f + 0.4f * hash(i * 78.233f);
			float base = i * Mth.TWO_PI / streams;
			Vec3 last = null;
			int steps = 7;
			for (int k = 0; k <= steps; k++) {
				float s = Math.max(0f, head - 0.28f * (1f - k / (float) steps)); // this point of the dash, trailing further out
				float out = 1f - s; // 1 far out .. 0 at the mouth
				float ang = base + (1f - out) * 2.4f + time * 0.05f;
				float rad = out * out * len * spread + 0.03f;
				Vec3 at = mouth.add(look.scale(out * len)).add(u.scale(Mth.cos(ang) * rad)).add(v.scale(Mth.sin(ang) * rad));
				if (last != null) {
					// fading out close to the camera, where a stream would fill the view
					float a = 0.55f * Mth.sin(head * Mth.PI) * (k / (float) steps) * Mth.clamp(((float) at.distanceTo(cam) - 1.5f) / 2.5f, 0f, 1f);
					strip(vc, m, cam, last, at, 0.03f + 0.05f * out, 0.85f, 1f, 0.72f, a);
				}
				last = at;
			}
		}
	}

	/** The Mega Loogie gathering in his mouth, {@code k} 0..1 of the heave. */
	private static void charge(PoseStack pose, VertexConsumer vc, Player p, float partial, float k, float time, Vec3 cam) {
		Vec3 at = UpchuckClient.mouth(p, partial);
		float r = (0.05f + 0.3f * k * k) * (1f + 0.08f * Mth.sin(time * 2.2f));
		pose.pushPose();
		pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
		Vec3 view = cam.subtract(at);
		view = view.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : view.normalize();
		Vec3 up = new Vec3(0, 1, 0);
		drop(pose, vc, view, up, r * 1.7f, 0f, time, 3, HOT, LIME, EDGE, 0.45f, HALO, 16, 28);
		drop(pose, vc, view, up, r, 0f, time, 3, HOT, LIME, EDGE, 1f, SOLID, 18, 30);
		pose.popPose();
	}

	/** The Vomit Rocket: a tapering jet of glowing vomit streaming back from him, dying away. */
	private static void jet(PoseStack pose, VertexConsumer vc, Player p, float partial, float age, float time, Vec3 cam) {
		float fade = 1f - Mth.clamp(age / 16f, 0f, 1f);
		if (fade <= 0.02f) {
			return;
		}
		Vec3 vel = p.getDeltaMovement();
		Vec3 w = vel.lengthSqr() < 1.0e-4 ? new Vec3(0, 1, 0) : vel.normalize();
		Vec3 at = p.getPosition(partial).add(0, p.getBbHeight() * 0.45, 0).subtract(w.scale(0.35));
		float r = 0.2f * (0.5f + 0.5f * fade);
		pose.pushPose();
		pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
		Vec3 view = cam.subtract(at);
		view = view.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : view.normalize();
		drop(pose, vc, view, w, r * 1.5f, 4f * fade + 1f, time, 5, LIME, LIME, EDGE, 0.45f * fade, HALO, 16, 28);
		drop(pose, vc, view, w, r, 5f * fade + 1f, time, 5, HOT, LIME, EDGE, 0.85f * fade, SOLID, 18, 30);
		pose.popPose();
	}

	/** A camera-facing strip from {@code a} to {@code b}. */
	private static void strip(VertexConsumer vc, org.joml.Matrix4f m, Vec3 cam, Vec3 a, Vec3 b, float width, float r, float g, float bl, float alpha) {
		Vec3 dir = b.subtract(a);
		Vec3 side = dir.cross(cam.subtract(a));
		if (side.lengthSqr() < 1.0e-8) {
			return;
		}
		side = side.normalize().scale(width * 0.5f);
		Vec3[] ps = { a.add(side), a.subtract(side), b.subtract(side), b.add(side) };
		for (Vec3 q : ps) {
			vc.addVertex(m, (float) (q.x - cam.x), (float) (q.y - cam.y), (float) (q.z - cam.z)).setColor(r, g, bl, alpha);
		}
	}
}
