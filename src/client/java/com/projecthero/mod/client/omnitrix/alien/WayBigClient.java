package com.projecthero.mod.client.omnitrix.alien;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.omnitrix.AlienClientKit;
import com.projecthero.mod.client.omnitrix.AlienClients;
import com.projecthero.mod.client.omnitrix.FirstPersonClip;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.waybig.WayBigKit;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.2 (user: "add waybig too please ... Make him 100 blocks tall please"): Way Big's client kit -- the user's planetminecraft skin
 * on the player body, drawn {@link WayBigKit#COLOSSAL_SCALE} times over (100 blocks; {@link WayBigKit#COMPACT_SCALE} Compact) on top
 * of his small feet-and-shins hitbox, the third-person camera pulled back to see all of him, and his poses: the Titan Fist, the
 * two-handed Colossal Chop, the Cosmic Ray's crossed arms (the show's plus sign: the right forearm upright, the left across it),
 * the Quake Stomp, the Thunder Clap, the Giant Stride and the cyclone's spin.
 */
public final class WayBigClient implements AlienClientKit {
	private static final ResourceLocation SKIN = ProjectHeroMod.id("textures/entity/omnitrix/way_big.png");

	public static void register() {
		AlienClients.register(Aliens.WAY_BIG.id(), new WayBigClient());
		net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_ENTITIES.register(WayBigClient::renderWorld);
	}

	// ---------------------------------------------------------------- the Cosmic Ray, drawn every frame (it follows his look smoothly)

	private static void renderWorld(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext ctx) {
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		if (mc.level == null || ctx.consumers() == null || ctx.matrixStack() == null) {
			return;
		}
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		net.minecraft.world.phys.Vec3 cam = ctx.camera().getPosition();
		org.joml.Matrix4f m = ctx.matrixStack().last().pose();
		com.mojang.blaze3d.vertex.VertexConsumer vc = null;
		float time = mc.level.getGameTime() + partial;
		for (net.minecraft.world.entity.player.Player pl : mc.level.players()) {
			if (!WayBigKit.is(pl) || (Omnitrix.state(pl).kitFlags & WayBigKit.F_RAY) == 0) {
				continue;
			}
			float h = WayBigKit.height(pl);
			float yaw = Mth.rotLerp(partial, pl.yBodyRotO, pl.yBodyRot);
			net.minecraft.world.phys.Vec3 from = pl.getPosition(partial).add(0, h * 0.7, 0)
					.add(net.minecraft.world.phys.Vec3.directionFromRotation(0, yaw).scale(h * 0.09));
			net.minecraft.world.phys.Vec3 eye = pl.getEyePosition(partial);
			net.minecraft.world.phys.Vec3 far = eye.add(pl.getViewVector(partial).scale(WayBigKit.RAY_RANGE));
			var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(eye, far, net.minecraft.world.level.ClipContext.Block.COLLIDER,
					net.minecraft.world.level.ClipContext.Fluid.NONE, pl));
			net.minecraft.world.phys.Vec3 to = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? far : hit.getLocation();
			float r = (float) (WayBigKit.RAY_RADIUS * Math.max(0.5, WayBigKit.reachScale(pl)));
			float flick = 0.85f + 0.15f * Mth.sin(time * 1.9f);
			if (vc == null) {
				vc = ctx.consumers().getBuffer(net.minecraft.client.renderer.RenderType.debugQuads()); // alpha-blended: keeps its colour in daylight (additive washes out)
			}
			beam(vc, m, cam, from, to, r * 2.2f * flick, 0.20f, 0.70f, 1.0f, 0.35f);
			beam(vc, m, cam, from, to, r * 1.2f, 0.45f, 0.88f, 1.0f, 0.75f);
			beam(vc, m, cam, from, to, r * 0.45f, 1.0f, 1.0f, 1.0f, 0.95f);
			// the strike: a flare where it lands, and one at his crossed arms
			flare(vc, m, cam, to, r * 3.5f * flick, 0.55f, 0.9f, 1.0f, 0.7f);
			flare(vc, m, cam, from, r * 1.8f * flick, 0.7f, 0.95f, 1.0f, 0.6f);
		}
	}

	/** A camera-facing strip from {@code a} to {@code b}, {@code w} blocks wide (both windings: never culled). */
	private static void beam(com.mojang.blaze3d.vertex.VertexConsumer vc, org.joml.Matrix4f m, net.minecraft.world.phys.Vec3 cam,
			net.minecraft.world.phys.Vec3 a, net.minecraft.world.phys.Vec3 b, float w, float r, float g, float bl, float al) {
		net.minecraft.world.phys.Vec3 dir = b.subtract(a);
		net.minecraft.world.phys.Vec3 side = dir.cross(cam.subtract(a.add(b).scale(0.5)));
		if (side.lengthSqr() < 1.0e-6) {
			return;
		}
		side = side.normalize().scale(w * 0.5);
		quad(vc, m, cam, a.add(side), a.subtract(side), b.subtract(side), b.add(side), r, g, bl, al);
	}

	/** A camera-facing square at {@code at}. */
	private static void flare(com.mojang.blaze3d.vertex.VertexConsumer vc, org.joml.Matrix4f m, net.minecraft.world.phys.Vec3 cam,
			net.minecraft.world.phys.Vec3 at, float size, float r, float g, float bl, float al) {
		net.minecraft.world.phys.Vec3 view = at.subtract(cam).normalize();
		net.minecraft.world.phys.Vec3 up = Math.abs(view.y) > 0.95 ? new net.minecraft.world.phys.Vec3(1, 0, 0) : new net.minecraft.world.phys.Vec3(0, 1, 0);
		net.minecraft.world.phys.Vec3 x = view.cross(up).normalize().scale(size * 0.5), y = view.cross(x).normalize().scale(size * 0.5);
		quad(vc, m, cam, at.add(x).add(y), at.subtract(x).add(y), at.subtract(x).subtract(y), at.add(x).subtract(y), r, g, bl, al);
	}

	private static void quad(com.mojang.blaze3d.vertex.VertexConsumer vc, org.joml.Matrix4f m, net.minecraft.world.phys.Vec3 cam,
			net.minecraft.world.phys.Vec3 p0, net.minecraft.world.phys.Vec3 p1, net.minecraft.world.phys.Vec3 p2, net.minecraft.world.phys.Vec3 p3,
			float r, float g, float b, float a) {
		net.minecraft.world.phys.Vec3[] ps = { p0, p1, p2, p3, p3, p2, p1, p0 };
		for (net.minecraft.world.phys.Vec3 p : ps) {
			vc.addVertex(m, (float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z)).setColor(r, g, b, a);
		}
	}

	// ---------------------------------------------------------------- poses (QuirkKeyframes: tick, rArm xyz, lArm xyz, body xy, rLeg xyz, lLeg xyz, headX)
	private static float[] f(float t, float rax, float ray, float raz, float lax, float lay, float laz, float bx, float rlx, float llx) {
		return QuirkKeyframes.f(t, rax, ray, raz, lax, lay, laz, bx, 0, rlx, 0, 0, llx, 0, 0, 0);
	}

	private static final float[][] FIST = { QuirkKeyframes.rest(0), f(3, -2.9f, 0, 0.2f, 0.3f, 0, 0, -0.15f, 0, 0), f(6, -1.1f, 0, 0.1f, 0.4f, 0, 0, 0.45f, 0, 0),
			f(12, -1.0f, 0, 0.1f, 0.3f, 0, 0, 0.4f, 0, 0), QuirkKeyframes.rest(20) };
	private static final float[][] CHOP = { QuirkKeyframes.rest(0), f(5, -3.0f, 0.3f, 0, -3.0f, -0.3f, 0, -0.25f, 0, 0),
			f(9, -0.9f, 0.3f, 0, -0.9f, -0.3f, 0, 0.6f, 0, 0), f(16, -0.8f, 0.3f, 0, -0.8f, -0.3f, 0, 0.55f, 0, 0), QuirkKeyframes.rest(24) };
	/** The Cosmic Ray: the right forearm held upright, the left laid across it -- a plus sign, held while he fires. */
	private static final float[][] RAY = { QuirkKeyframes.rest(0), f(4, -2.55f, 0.35f, 0, -1.5f, 1.05f, 0, 0.05f, 0, 0),
			f(400, -2.55f, 0.35f, 0, -1.5f, 1.05f, 0, 0.05f, 0, 0) };
	private static final float[][] STOMP = { QuirkKeyframes.rest(0), f(5, 0.3f, 0, 0.5f, 0.3f, 0, -0.5f, 0, -1.4f, 0), f(8, 0.3f, 0, 0.6f, 0.3f, 0, -0.6f, 0.15f, 0.2f, 0),
			QuirkKeyframes.rest(18) };
	private static final float[][] CLAP = { QuirkKeyframes.rest(0), f(4, -1.4f, -0.9f, 0, -1.4f, 0.9f, 0, -0.1f, 0, 0), f(7, -1.5f, 0.2f, 0, -1.5f, -0.2f, 0, 0.2f, 0, 0),
			f(12, -1.5f, 0.2f, 0, -1.5f, -0.2f, 0, 0.2f, 0, 0), QuirkKeyframes.rest(20) };
	private static final float[][] STRIDE = { QuirkKeyframes.rest(0), f(4, 0.8f, 0, 0.2f, -0.8f, 0, -0.2f, 0.3f, -1.2f, 0.6f),
			f(30, 0.8f, 0, 0.2f, -0.8f, 0, -0.2f, 0.2f, -0.9f, 0.6f), QuirkKeyframes.rest(40) };
	private static final float[][] SHIFT = { QuirkKeyframes.rest(0), f(5, -0.6f, 0, 1.2f, -0.6f, 0, -1.2f, -0.2f, 0, 0), QuirkKeyframes.rest(14) };
	private static final float[][] ROAR = { QuirkKeyframes.rest(0), f(8, -0.5f, 0, 1.3f, -0.5f, 0, -1.3f, -0.3f, 0, 0), f(26, -0.5f, 0, 1.3f, -0.5f, 0, -1.3f, -0.3f, 0, 0),
			QuirkKeyframes.rest(36) };

	private static final FirstPersonClip FP_FIST = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -60, 10, 0, 0.1f, 0.4f, 0.1f),
			FirstPersonClip.f(6, 30, 5, 0, 0.1f, -0.3f, -0.2f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_RAY = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(4, -20, 40, 30, -0.1f, 0.15f, -0.1f),
			FirstPersonClip.f(400, -20, 40, 30, -0.1f, 0.15f, -0.1f)).both();
	private static final FirstPersonClip FP_DOWN = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(5, -70, 10, 0, 0.05f, 0.5f, 0.1f),
			FirstPersonClip.f(9, 40, 10, 0, 0.05f, -0.45f, 0.2f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();

	@Override
	public ResourceLocation skin() {
		return SKIN;
	}

	// ---------------------------------------------------------------- the fins

	/**
	 * v0.17.2 (user: "add a fin to the top of waybigs head and his shoulders please, small ones on his shoulders, look at these
	 * reference images, make the fins smooth looking please"): the show's tall head fin -- a crest rising from his brow and sweeping
	 * back -- and a small curved crescent fin on top of each shoulder, built as smooth curved blades (many thin segments, both faces
	 * and their edges), not boxes. Coloured from the skin's own dark fin stripe.
	 */
	@Override
	public void renderExtras(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light,
			AbstractClientPlayer p, net.minecraft.client.model.PlayerModel<AbstractClientPlayer> model, float partial, float age) {
		com.mojang.blaze3d.vertex.VertexConsumer vc = buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(SKIN));
		// the head fin: on the head's centre line (x 0), a blade from the brow (front edge nearly upright, then curving back) to a tip
		// up and behind; thin across x
		pose.pushPose();
		model.head.translateAndRotate(pose);
		blade(pose, vc, light, new float[][] { { 0, -7.6f, -2.8f }, { 0, -14.5f, -2.6f }, { 0, -17.5f, 3.4f } },
				new float[][] { { 0, -7.6f, 3.4f }, { 0, -10.4f, 4.0f }, { 0, -17.5f, 3.4f } }, new float[] { 1, 0, 0 }, 1.3f, FIN_RED);
		// v0.17.2 (user: "make waybigs fin black in the front and red and the back"): the front half black over the red blade
		blade(pose, vc, light, new float[][] { { 0, -7.6f, -2.95f }, { 0, -14.7f, -2.75f }, { 0, -17.6f, 3.35f } },
				new float[][] { { 0, -7.6f, 0.3f }, { 0, -12.8f, 0.7f }, { 0, -17.3f, 3.3f } }, new float[] { 1, 0, 0 }, 1.45f, FIN_BLACK);
		pose.popPose();
		// the shoulder fins -- v0.17.2 (user: "make his shoulder spikes the same as his fin but make them fully black and make them as long
		// as the length of his shoulder please"): the head fin's swept crest, all black, its base running the whole length of the
		// shoulder top (front to back, z -2..2) and its tip swept up and back
		for (int side = -1; side <= 1; side += 2) {
			var arm = side < 0 ? model.rightArm : model.leftArm;
			float c = side * 1f; // the arm cube's centre (right arm x -3..1, left -1..3)
			pose.pushPose();
			arm.translateAndRotate(pose);
			blade(pose, vc, light, new float[][] { { c, -1.8f, -2.2f }, { c, -6.2f, -2.0f }, { c, -8.4f, 2.4f } },
					new float[][] { { c, -1.8f, 2.2f }, { c, -3.7f, 2.6f }, { c, -8.4f, 2.4f } }, new float[] { 1, 0, 0 }, 1.1f, FIN_BLACK);
			pose.popPose();
		}
	}

	/** Solid colours picked from the skin itself (pixel centres on the 64x64 sheet): the fin stripe's navy and his red. */
	private static final float[] FIN_BLACK = { 16.5f / 64f, 7.5f / 64f }, FIN_RED = { 10.5f / 64f, 0.5f / 64f };
	private static final int SEG = 18;

	/**
	 * A smooth curved blade between two quadratic edges {@code a} and {@code b} (each three control points in bone pixels; they share
	 * their last point, the tip), {@code thick} pixels thick along {@code across}: both faces and the edge walls.
	 */
	private static void blade(com.mojang.blaze3d.vertex.PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer vc, int light, float[][] a,
			float[][] b, float[] across, float thick, float[] uv) {
		var last = pose.last();
		float h = thick * 0.5f;
		float[][] ea = new float[SEG + 1][], eb = new float[SEG + 1][];
		for (int i = 0; i <= SEG; i++) {
			float t = i / (float) SEG;
			ea[i] = quad(a, t);
			eb[i] = quad(b, t);
		}
		for (int i = 0; i < SEG; i++) {
			// the blade thins towards the tip
			float h0 = h * (1f - 0.7f * i / SEG), h1 = h * (1f - 0.7f * (i + 1) / SEG);
			for (int s = -1; s <= 1; s += 2) {
				float[] n = { across[0] * s, across[1] * s, across[2] * s };
				quadV(vc, last, light, uv, off(ea[i], across, s * h0), off(eb[i], across, s * h0), off(eb[i + 1], across, s * h1),
						off(ea[i + 1], across, s * h1), n);
			}
			// the two edges' walls
			for (float[][] e : new float[][][] { ea, eb }) {
				float[] d = sub(e[i + 1], e[i]);
				float[] n = norm(cross(d, across));
				quadV(vc, last, light, uv, off(e[i], across, -h0), off(e[i], across, h0), off(e[i + 1], across, h1), off(e[i + 1], across, -h1), n);
			}
		}
	}

	private static float[] quad(float[][] p, float t) {
		float u = 1 - t;
		float[] o = new float[3];
		for (int k = 0; k < 3; k++) {
			o[k] = u * u * p[0][k] + 2 * u * t * p[1][k] + t * t * p[2][k];
		}
		return o;
	}

	private static float[] off(float[] v, float[] d, float s) {
		return new float[] { v[0] + d[0] * s, v[1] + d[1] * s, v[2] + d[2] * s };
	}

	private static float[] sub(float[] x, float[] y) {
		return new float[] { x[0] - y[0], x[1] - y[1], x[2] - y[2] };
	}

	private static float[] cross(float[] x, float[] y) {
		return new float[] { x[1] * y[2] - x[2] * y[1], x[2] * y[0] - x[0] * y[2], x[0] * y[1] - x[1] * y[0] };
	}

	private static float[] norm(float[] v) {
		float l = Mth.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
		return l < 1.0e-5f ? new float[] { 0, 1, 0 } : new float[] { v[0] / l, v[1] / l, v[2] / l };
	}

	/** One quad (pixels to blocks), drawn both ways round so its facing never culls it. */
	private static void quadV(com.mojang.blaze3d.vertex.VertexConsumer vc, com.mojang.blaze3d.vertex.PoseStack.Pose last, int light, float[] uv,
			float[] p0, float[] p1, float[] p2, float[] p3, float[] n) {
		float[][] ps = { p0, p1, p2, p3 };
		for (float[] q : ps) {
			vc.addVertex(last, q[0] / 16f, q[1] / 16f, q[2] / 16f).setColor(255, 255, 255, 255).setUv(uv[0], uv[1])
					.setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, n[0], n[1], n[2]);
		}
	}

	@Override
	public float[] badge() {
		// the middle of his chest, where the show puts his Omnitrix symbol (the skin paints none -- see gen_way_big.js); just outside
		// the jacket layer
		return new float[] { 0f, 4.0f, -2.3f, 3.0f };
	}

	@Override
	public float renderScale(AbstractClientPlayer p) {
		return (Omnitrix.state(p).kitFlags & WayBigKit.F_COMPACT) != 0 ? WayBigKit.COMPACT_SCALE : WayBigKit.COLOSSAL_SCALE;
	}

	@Override
	public float cameraDistance(AbstractClientPlayer p) {
		// far enough back to see a 100-block giant from the head down (the eyes are near the top)
		return WayBigKit.height(p) * 0.8f;
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		boolean ray = (Omnitrix.state(p).kitFlags & WayBigKit.F_RAY) != 0;
		float[][] clip = ray ? RAY : switch (animId) {
			case WayBigKit.ANIM_FIST -> FIST;
			case WayBigKit.ANIM_CHOP -> CHOP;
			case WayBigKit.ANIM_RAY -> RAY;
			case WayBigKit.ANIM_STOMP -> STOMP;
			case WayBigKit.ANIM_CLAP -> CLAP;
			case WayBigKit.ANIM_STRIDE -> STRIDE;
			case WayBigKit.ANIM_SHIFT -> SHIFT;
			case WayBigKit.ANIM_ROAR -> ROAR;
			default -> null;
		};
		boolean played = clip != null && QuirkKeyframes.play(m, clip, ray && animId != WayBigKit.ANIM_RAY ? Math.max(ticks, 4f) : ticks, 1f);
		if ((Omnitrix.state(p).kitFlags & WayBigKit.F_CYCLONE) != 0) {
			// the cyclone: arms flung wide while the body spins
			m.rightArm.zRot = 1.45f;
			m.leftArm.zRot = -1.45f;
			m.body.yRot = age * 0.9f;
			m.rightArm.yRot = age * 0.9f;
			m.leftArm.yRot = age * 0.9f;
			return true;
		}
		if (!played) {
			// a giant's slow sway: the arms hang a little out from his broad body
			m.rightArm.zRot += 0.08f + Mth.sin(age * 0.05f) * 0.02f;
			m.leftArm.zRot -= 0.08f + Mth.sin(age * 0.05f) * 0.02f;
		}
		return played;
	}

	@Override
	public FirstPersonClip firstPerson(int animId) {
		return switch (animId) {
			case WayBigKit.ANIM_FIST -> FP_FIST;
			case WayBigKit.ANIM_RAY -> FP_RAY;
			case WayBigKit.ANIM_CHOP, WayBigKit.ANIM_CLAP, WayBigKit.ANIM_STOMP -> FP_DOWN;
			default -> null;
		};
	}
}
