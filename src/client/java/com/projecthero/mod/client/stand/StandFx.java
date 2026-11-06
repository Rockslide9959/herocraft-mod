package com.projecthero.mod.client.stand;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.omnitrix.OmnitrixFxRenderType;
import com.projecthero.mod.stand.network.StandFxPayload;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (user: "i dont want so much purple particle effects for star platinums abilities, can you make it vfx instead"): the
 * Stand's hit effects drawn as geometry, not dust -- a short white-violet flash with light spokes, thin shockwave rings that
 * widen and fade, a glowing Star Finger line, a rising streak for the Uppercut and The World's ground ring. Flat translucent
 * colour quads ({@link OmnitrixFxRenderType#FLAT}: walls hide them, nothing is lit). Received only by Stand users.
 */
public final class StandFx {
	private static final int CORE = 0xF4ECFF;
	private static final int VIOLET = 0xB07CFF;
	private static final int DEEP = 0x7A4CE0;

	private record Fx(int kind, Vec3 at, Vec3 dir, float size, long start, int life, float spin) {
	}

	private static final List<Fx> ACTIVE = new ArrayList<>();

	private StandFx() {
	}

	public static void receive(StandFxPayload p) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		int life = switch (p.kind()) {
			case StandFxPayload.IMPACT -> 7;
			case StandFxPayload.BURST -> 12;
			case StandFxPayload.BEAM -> 9;
			case StandFxPayload.AURA -> 14;
			case StandFxPayload.RISE -> 10;
			case StandFxPayload.BEARING -> 4;
			default -> 26;
		};
		if (ACTIVE.size() > 96) {
			ACTIVE.remove(0);
		}
		ACTIVE.add(new Fx(p.kind(), new Vec3(p.x(), p.y(), p.z()), new Vec3(p.dx(), p.dy(), p.dz()), p.size(), mc.level.getGameTime(), life,
				mc.level.getRandom().nextFloat() * Mth.TWO_PI));
	}

	/** An effect this client starts itself (a fingertip landing, drawn where the finger actually arrives). */
	static void local(int kind, Vec3 at, Vec3 dir, float size) {
		receive(new StandFxPayload(kind, at.x, at.y, at.z, dir.x, dir.y, dir.z, size));
	}

	public static void reset() {
		ACTIVE.clear();
	}

	public static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || ACTIVE.isEmpty() || ctx.consumers() == null || ctx.matrixStack() == null) {
			return;
		}
		float now = mc.level.getGameTime() + ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		ACTIVE.removeIf(f -> now - f.start > f.life || now < f.start - 40);
		if (ACTIVE.isEmpty()) {
			return;
		}
		Vec3 cam = ctx.camera().getPosition();
		Vec3 camUp = new Vec3(ctx.camera().getUpVector());
		Vec3 camLeft = new Vec3(ctx.camera().getLeftVector());
		PoseStack pose = ctx.matrixStack();
		pose.pushPose();
		pose.translate(-cam.x, -cam.y, -cam.z);
		Matrix4f m = pose.last().pose();
		VertexConsumer vc = ctx.consumers().getBuffer(OmnitrixFxRenderType.FLAT);
		for (Fx f : ACTIVE) {
			float t = Mth.clamp((now - f.start) / f.life, 0f, 1f);
			float fade = 1f - t;
			float s = f.size;
			switch (f.kind) {
				case StandFxPayload.IMPACT -> {
					if (f.at.distanceToSqr(cam) < 1.0) {
						break;
					}
					flash(vc, m, f.at, camLeft, camUp, s * (0.45f + 0.5f * t), fade * fade, f.spin, 4);
					ring(vc, m, f.at, facing(f.dir, cam, f.at), s * (0.3f + 1.1f * ease(t)), s * 0.07f * fade + 0.01f, VIOLET, fade * 0.9f);
				}
				case StandFxPayload.BURST -> {
					flash(vc, m, f.at, camLeft, camUp, s * (0.7f + 0.8f * t), fade * fade, f.spin, 6);
					Vec3 n = facing(f.dir, cam, f.at);
					ring(vc, m, f.at, n, s * (0.4f + 2.2f * ease(t)), s * 0.12f * fade + 0.01f, VIOLET, fade);
					ring(vc, m, f.at, n, s * (0.2f + 1.3f * ease(t)), s * 0.07f * fade + 0.01f, CORE, fade * 0.8f);
				}
				case StandFxPayload.BEAM -> beam(vc, m, f.at, f.dir, cam, 0.16f * fade + 0.02f, fade);
				case StandFxPayload.AURA -> {
					Vec3 c = f.at.add(0, 1.0, 0);
					if (c.distanceToSqr(cam) < 1.6 * 1.6) {
						break; // your own summon, right at your shoulder in first person
					}
					float a = Mth.sin(t * Mth.PI);
					soft(vc, m, c, camLeft, camUp, s * 1.1f, a * 0.45f);
					float y = (float) f.dir.y >= 0 ? -0.9f + 2.0f * t : 1.1f - 2.0f * t;
					ring(vc, m, c.add(0, y * s, 0), new Vec3(0, 1, 0), s * (0.75f - 0.25f * t), 0.05f, VIOLET, a * 0.9f);
				}
				case StandFxPayload.RISE -> {
					Vec3 up = new Vec3(0, 1, 0);
					beam(vc, m, f.at.add(0, -0.6, 0), f.at.add(0, 0.6 + 2.4 * ease(t), 0), cam, 0.22f * fade + 0.02f, fade);
					ring(vc, m, f.at.add(0, -0.6, 0), up, s * (0.3f + 1.4f * ease(t)), 0.08f * fade + 0.01f, VIOLET, fade);
					flash(vc, m, f.at, camLeft, camUp, s * 0.6f, fade * fade, f.spin, 4);
				}
				case StandFxPayload.BEARING -> {
					// the steel ball itself, its streak behind it, and a spark where it strikes
					Vec3 ball = f.at.lerp(f.dir, ease(t));
					Vec3 tail = f.at.lerp(f.dir, ease(Math.max(0f, t - 0.3f)));
					beam(vc, m, tail, ball, cam, 0.05f, 0.75f);
					soft(vc, m, ball, camLeft, camUp, 0.11f, 1f);
					if (t > 0.8f) {
						flash(vc, m, f.dir, camLeft, camUp, 0.3f, (1f - t) * 5f, f.spin, 4);
					}
				}
				case StandFxPayload.WORLD -> {
					Vec3 up = new Vec3(0, 1, 0);
					Vec3 g = f.at.add(0, 0.08, 0);
					ring(vc, m, g, up, 1f + 14f * ease(t), 0.35f * fade + 0.05f, VIOLET, fade);
					ring(vc, m, g, up, 0.5f + 9f * ease(t), 0.2f * fade + 0.03f, CORE, fade * 0.7f);
					if (f.at.add(0, 1.2, 0).distanceToSqr(cam) > 2.0 * 2.0) {
						soft(vc, m, f.at.add(0, 1.2, 0), camLeft, camUp, 2.2f * (0.6f + t), fade * 0.5f);
					}
				}
				default -> {
				}
			}
		}
		pose.popPose();
	}

	private static float ease(float t) {
		return 1f - (1f - t) * (1f - t);
	}

	/** A ring's facing: along {@code dir}, or toward the camera when there is none. */
	private static Vec3 facing(Vec3 dir, Vec3 cam, Vec3 at) {
		Vec3 n = dir.lengthSqr() > 1.0e-4 ? dir : cam.subtract(at);
		return n.lengthSqr() > 1.0e-4 ? n.normalize() : new Vec3(0, 1, 0);
	}

	/** A camera-facing flash: a soft glow disc and {@code spokes} tapering light spokes. */
	private static void flash(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 left, Vec3 up, float r, float a, float spin, int spokes) {
		soft(vc, m, c, left, up, r, a * 0.85f);
		soft(vc, m, c, left, up, r * 0.4f, a);
		for (int i = 0; i < spokes; i++) {
			float ang = spin + i * Mth.TWO_PI / spokes;
			float len = r * (i % 2 == 0 ? 1.9f : 1.25f);
			Vec3 d = left.scale(Mth.cos(ang)).add(up.scale(Mth.sin(ang)));
			Vec3 side = left.scale(-Mth.sin(ang)).add(up.scale(Mth.cos(ang))).scale(r * 0.09f);
			Vec3 tip = c.add(d.scale(len));
			quad(vc, m, c.add(side), CORE, a, c.subtract(side), CORE, a, tip, VIOLET, 0f, tip, VIOLET, 0f);
		}
	}

	/** A camera-facing radial glow: bright middle to nothing at {@code r}. */
	private static void soft(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 left, Vec3 up, float r, float a) {
		int seg = 16;
		for (int i = 0; i < seg; i++) {
			float a0 = i * Mth.TWO_PI / seg, a1 = (i + 1) * Mth.TWO_PI / seg;
			Vec3 p0 = c.add(left.scale(Mth.cos(a0) * r)).add(up.scale(Mth.sin(a0) * r));
			Vec3 p1 = c.add(left.scale(Mth.cos(a1) * r)).add(up.scale(Mth.sin(a1) * r));
			quad(vc, m, c, CORE, a, c, CORE, a, p1, DEEP, 0f, p0, DEEP, 0f);
		}
	}

	/** A thin ring of radius {@code r} round {@code n}: bright at its middle, nothing at both edges. */
	private static void ring(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 n, float r, float w, int colour, float a) {
		if (a <= 0.01f) {
			return;
		}
		Vec3 u = Math.abs(n.y) < 0.9 ? n.cross(new Vec3(0, 1, 0)).normalize() : n.cross(new Vec3(1, 0, 0)).normalize();
		Vec3 v = n.cross(u).normalize();
		int seg = 40;
		for (int i = 0; i < seg; i++) {
			float a0 = i * Mth.TWO_PI / seg, a1 = (i + 1) * Mth.TWO_PI / seg;
			Vec3 d0 = u.scale(Mth.cos(a0)).add(v.scale(Mth.sin(a0)));
			Vec3 d1 = u.scale(Mth.cos(a1)).add(v.scale(Mth.sin(a1)));
			Vec3 m0 = c.add(d0.scale(r)), m1 = c.add(d1.scale(r));
			Vec3 o0 = c.add(d0.scale(r + w)), o1 = c.add(d1.scale(r + w));
			Vec3 i0 = c.add(d0.scale(Math.max(0f, r - w))), i1 = c.add(d1.scale(Math.max(0f, r - w)));
			quad(vc, m, m0, colour, a, m1, colour, a, o1, colour, 0f, o0, colour, 0f);
			quad(vc, m, m0, colour, a, m1, colour, a, i1, colour, 0f, i0, colour, 0f);
		}
	}

	/** A camera-facing glowing line from {@code a} to {@code b}: white core, violet edges fading out. */
	private static void beam(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 cam, float w, float alpha) {
		Vec3 along = b.subtract(a);
		if (along.lengthSqr() < 1.0e-4) {
			return;
		}
		Vec3 side = along.cross(a.add(b).scale(0.5).subtract(cam)).normalize();
		Vec3 s1 = side.scale(w), s2 = side.scale(w * 0.35);
		quad(vc, m, a, VIOLET, alpha, b, VIOLET, alpha, b.add(s1), DEEP, 0f, a.add(s1), DEEP, 0f);
		quad(vc, m, a, VIOLET, alpha, b, VIOLET, alpha, b.subtract(s1), DEEP, 0f, a.subtract(s1), DEEP, 0f);
		quad(vc, m, a.add(s2), CORE, alpha, b.add(s2), CORE, alpha, b.subtract(s2), CORE, alpha, a.subtract(s2), CORE, alpha);
	}

	private static void quad(VertexConsumer vc, Matrix4f m, Vec3 p0, int c0, float a0, Vec3 p1, int c1, float a1, Vec3 p2, int c2, float a2,
			Vec3 p3, int c3, float a3) {
		vert(vc, m, p0, c0, a0);
		vert(vc, m, p1, c1, a1);
		vert(vc, m, p2, c2, a2);
		vert(vc, m, p3, c3, a3);
	}

	private static void vert(VertexConsumer vc, Matrix4f m, Vec3 p, int c, float a) {
		vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF,
				Mth.clamp((int) (a * 255f), 0, 255));
	}
}
