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
 *
 * <p>v0.17.2 (user: "edit and update animations on all moves please"): every move has a wind-up, a blow that lands on the tick the
 * server's hit does ({@link WayBigKit#FIST_DELAY} and friends) and a recovery, third person and first person; the cyclone turns his whole
 * body inside a funnel of wind, Size Shift grows and shrinks him smoothly, and a hit on the head fin makes him clutch his head.
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
			if (!WayBigKit.is(pl)) {
				continue;
			}
			int flags = Omnitrix.state(pl).kitFlags;
			if ((flags & WayBigKit.F_CYCLONE) != 0) {
				if (vc == null) {
					vc = ctx.consumers().getBuffer(net.minecraft.client.renderer.RenderType.debugQuads());
				}
				funnel(vc, m, cam, pl, partial, time);
			}
			if ((flags & WayBigKit.F_RAY) == 0 || com.projecthero.mod.client.beamclash.BeamClashClient.hides(pl)) {
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

	/** The To'kustar Cyclone: six bands of wind winding up round him, wider the higher they go, gathering as he spins up. */
	private static void funnel(com.mojang.blaze3d.vertex.VertexConsumer vc, org.joml.Matrix4f m, net.minecraft.world.phys.Vec3 cam,
			net.minecraft.world.entity.player.Player pl, float partial, float time) {
		float h = WayBigKit.height(pl);
		float reach = (float) (WayBigKit.CYCLONE_RADIUS * WayBigKit.reachScale(pl));
		float in = Mth.clamp((time - Omnitrix.state(pl).readyAt(WayBigKit.ID + "." + WayBigKit.CYCLONE_START)) / 14f, 0f, 1f);
		net.minecraft.world.phys.Vec3 c = pl.getPosition(partial);
		int steps = 26;
		for (int band = 0; band < 6; band++) {
			net.minecraft.world.phys.Vec3 last = null;
			for (int i = 0; i <= steps; i++) {
				float s = i / (float) steps;
				float ang = -time * 0.55f + band * Mth.TWO_PI / 6f + s * 7.5f;
				float rad = reach * (0.16f + 0.84f * s * s) * in;
				net.minecraft.world.phys.Vec3 at = c.add(Mth.cos(ang) * rad, h * 0.72f * s, Mth.sin(ang) * rad);
				if (last != null) {
					beam(vc, m, cam, last, at, 1.2f + reach * 0.14f * s, 0.86f, 0.95f, 1.0f, 0.42f * in * (1f - 0.4f * s));
				}
				last = at;
			}
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

	// ---------------------------------------------------------------- poses (QuirkKeyframes: tick, rArm xyz, lArm xyz, body xy, rLeg x, lLeg x, head x, crouch)
	private static float[] g(float t, float rax, float ray, float raz, float lax, float lay, float laz, float bx, float by, float rlx, float llx, float hx,
			float crouch) {
		return QuirkKeyframes.fc(t, rax, ray, raz, lax, lay, laz, bx, by, rlx, 0, 0, llx, 0, 0, hx, crouch);
	}

	private static float[] rest(float t) {
		return QuirkKeyframes.restC(t);
	}

	/** Titan Fist: the right fist hauled up behind his head, then driven down and forward, his whole weight behind it. Lands on tick 6. */
	private static final float[][] FIST = { rest(0), g(3, -3.0f, 0.25f, 0.3f, 0.2f, 0, -0.25f, -0.2f, 0.3f, 0, 0, -0.15f, 0.1f),
			g(6, -1.15f, -0.1f, 0.05f, 0.55f, 0, -0.3f, 0.5f, -0.25f, 0.25f, -0.45f, 0.25f, 0.45f),
			g(11, -1.05f, -0.1f, 0.05f, 0.45f, 0, -0.3f, 0.45f, -0.2f, 0.25f, -0.4f, 0.2f, 0.4f), rest(22) };
	/** Colossal Chop: both hands high and together, then down edge-first, doubling him over. Lands on tick 9. */
	private static final float[][] CHOP = { rest(0), g(6, -3.05f, 0.25f, 0, -3.05f, -0.25f, 0, -0.3f, 0, -0.1f, 0.1f, -0.2f, 0.1f),
			g(9, -0.75f, 0.3f, 0, -0.75f, -0.3f, 0, 0.7f, 0, 0.35f, -0.5f, 0.2f, 0.6f), g(15, -0.7f, 0.3f, 0, -0.7f, -0.3f, 0, 0.62f, 0, 0.35f, -0.5f, 0.2f, 0.55f),
			rest(26) };
	/** The Cosmic Ray: the right forearm held upright, the left laid across it -- a plus sign, held while he fires. */
	private static final float[] RAY_HELD = g(0, -2.55f, 0.35f, 0, -1.5f, 1.05f, 0, 0.05f, 0.12f, 0.12f, -0.12f, 0, 0.12f);
	private static final float[][] RAY = { rest(0), at(5, RAY_HELD), at(400, RAY_HELD) };
	private static final float[][] RAY_END = { at(0, RAY_HELD), at(3, RAY_HELD), rest(12) };
	/** Quake Stomp: the right leg raised high, arms out for balance, then the foot driven down. Lands on tick 8. */
	private static final float[][] STOMP = { rest(0), g(5, 0.35f, 0, 0.55f, -0.35f, 0, -0.55f, -0.12f, 0.1f, -1.55f, 0.05f, -0.1f, 0.2f),
			g(8, -0.25f, 0, 0.75f, -0.25f, 0, -0.75f, 0.3f, -0.05f, 0.1f, -0.25f, 0.2f, 0.55f), g(12, -0.2f, 0, 0.7f, -0.2f, 0, -0.7f, 0.25f, 0, 0.1f, -0.2f, 0.15f, 0.45f),
			rest(22) };
	/** Thunder Clap: the arms thrown wide, then the hands slammed together out in front. They meet on tick 8. */
	private static final float[][] CLAP = { rest(0), g(5, -1.5f, -1.15f, 0, -1.5f, 1.15f, 0, -0.18f, 0, 0, 0, -0.1f, 0),
			g(8, -1.55f, 0.12f, 0, -1.55f, -0.12f, 0, 0.28f, 0, 0.1f, -0.15f, 0.15f, 0.2f), g(13, -1.5f, 0.12f, 0, -1.5f, -0.12f, 0, 0.24f, 0, 0.1f, -0.15f, 0.1f, 0.15f),
			rest(22) };
	/** Giant Stride: the leading leg swung out, the trailing one behind, arms pumping -- held until he comes down. */
	private static final float[] STRIDE_AIR = g(0, 0.7f, 0, 0.25f, -0.9f, 0, -0.25f, 0.25f, 0, -1.0f, 0.6f, 0, 0);
	private static final float[][] STRIDE = { rest(0), g(3, 0.9f, 0, 0.2f, -1.1f, 0, -0.2f, 0.35f, 0, -1.35f, 0.75f, 0, 0), at(12, STRIDE_AIR), at(400, STRIDE_AIR) };
	/** The landing: his knees take it, arms flung out, then he straightens up. */
	private static final float[][] LAND = { at(0, STRIDE_AIR), g(3, 0.5f, 0, 0.9f, 0.5f, 0, -0.9f, 0.55f, 0, -0.35f, 0.35f, 0.3f, 1f),
			g(8, 0.4f, 0, 0.8f, 0.4f, 0, -0.8f, 0.5f, 0, -0.3f, 0.3f, 0.25f, 0.9f), rest(18) };
	/** Size Shift, growing: arms lifted and spread as he rises. */
	private static final float[][] GROW = { rest(0), g(5, -0.6f, 0, 1.2f, -0.6f, 0, -1.2f, -0.2f, 0, 0, 0, -0.25f, 0), g(10, -0.6f, 0, 1.25f, -0.6f, 0, -1.25f, -0.22f, 0, 0, 0, -0.25f, 0),
			rest(16) };
	/** Size Shift, shrinking: he folds in on himself, arms drawn across his chest. */
	private static final float[][] SHRINK = { rest(0), g(5, -1.0f, 0.75f, 0, -1.0f, -0.75f, 0, 0.45f, 0, 0, 0, 0.25f, 0.6f), g(10, -1.0f, 0.8f, 0, -1.0f, -0.8f, 0, 0.42f, 0, 0, 0, 0.2f, 0.55f),
			rest(16) };
	private static final float[][] ROAR = { rest(0), g(8, -0.55f, 0, 1.35f, -0.55f, 0, -1.35f, -0.35f, 0, 0.15f, -0.15f, -0.35f, 0.2f),
			g(26, -0.55f, 0, 1.35f, -0.55f, 0, -1.35f, -0.35f, 0, 0.15f, -0.15f, -0.35f, 0.2f), rest(36) };
	/** The cyclone's wind-up: one arm thrown across him, ready to unwind into the spin. */
	private static final float[][] CYCLONE_END = { g(0, 0, 0, 1.45f, 0, 0, -1.45f, 0, 0, 0, 0, 0, 0), g(5, -0.5f, 0, 1.5f, -0.5f, 0, -1.5f, -0.25f, 0, 0.1f, -0.1f, -0.15f, 0.3f), rest(18) };
	/** The head fin struck: both hands clapped to his head. */
	private static final float[][] FIN = { rest(0), g(3, -2.7f, 0.5f, -0.15f, -2.7f, -0.5f, 0.15f, 0.25f, 0, 0, 0, 0.35f, 0.2f),
			g(14, -2.7f, 0.5f, -0.15f, -2.7f, -0.5f, 0.15f, 0.2f, 0, 0, 0, 0.3f, 0.15f), rest(24) };

	private static float[] at(float t, float[] frame) {
		float[] o = frame.clone();
		o[0] = t;
		return o;
	}

	/** A clip that picks up where a held pose left off: no fade in (it starts on the held pose), its own fade out. */
	private static boolean follow(HumanoidModel<?> m, float[][] clip, float ticks) {
		float[] p = QuirkKeyframes.sample(clip, ticks);
		if (p == null) {
			return false;
		}
		QuirkKeyframes.apply(m, p, Math.min(1f, (QuirkKeyframes.length(clip) - ticks) / QuirkKeyframes.FADE_OUT));
		return true;
	}

	private static final FirstPersonClip FP_FIST = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -65, 12, 0, 0.1f, 0.45f, 0.15f),
			FirstPersonClip.f(6, 32, 6, 0, 0.1f, -0.32f, -0.25f), FirstPersonClip.f(11, 26, 5, 0, 0.08f, -0.28f, -0.2f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0));
	private static final FirstPersonClip FP_CHOP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(6, -75, 14, 0, 0.12f, 0.55f, 0.15f),
			FirstPersonClip.f(9, 42, 14, 0, 0.12f, -0.45f, -0.25f), FirstPersonClip.f(15, 36, 12, 0, 0.1f, -0.4f, -0.2f), FirstPersonClip.f(24, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_RAY = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(5, -20, 40, 30, -0.1f, 0.15f, -0.1f),
			FirstPersonClip.f(400, -20, 40, 30, -0.1f, 0.15f, -0.1f)).both();
	private static final FirstPersonClip FP_RAY_END = FirstPersonClip.of(FirstPersonClip.f(0, -20, 40, 30, -0.1f, 0.15f, -0.1f), FirstPersonClip.f(3, -20, 40, 30, -0.1f, 0.15f, -0.1f),
			FirstPersonClip.f(12, 0, 0, 0, 0, 0, 0)).both();
	/** Stomp: the hands dip with the blow. */
	private static final FirstPersonClip FP_STOMP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(5, -12, -18, 0, -0.1f, 0.12f, 0.05f),
			FirstPersonClip.f(8, 20, -14, 0, -0.1f, -0.3f, 0.05f), FirstPersonClip.f(12, 16, -12, 0, -0.08f, -0.24f, 0.05f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();
	/** Clap: the arms out wide, then slammed together in the middle of the view. */
	private static final FirstPersonClip FP_CLAP = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(5, -28, -40, 0, -0.35f, 0.2f, 0.05f),
			FirstPersonClip.f(8, -30, 34, 0, 0.32f, 0.2f, -0.2f), FirstPersonClip.f(13, -28, 30, 0, 0.28f, 0.18f, -0.18f), FirstPersonClip.f(20, 0, 0, 0, 0, 0, 0)).both();
	/** The Stride: arms driving back as he leaps, held in the air. */
	private static final FirstPersonClip FP_STRIDE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, 26, 0, 0, 0, -0.32f, 0.2f),
			FirstPersonClip.f(400, 22, 0, 0, 0, -0.28f, 0.16f)).both();
	private static final FirstPersonClip FP_LAND = FirstPersonClip.of(FirstPersonClip.f(0, 22, 0, 0, 0, -0.28f, 0.16f), FirstPersonClip.f(3, -24, -24, 0, -0.2f, 0.05f, 0f),
			FirstPersonClip.f(8, -20, -20, 0, -0.16f, 0.03f, 0f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	/** Size Shift: the hands raised in front of him as the world changes size. */
	private static final FirstPersonClip FP_SHIFT = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(5, -38, 22, 0, 0.14f, 0.2f, 0f),
			FirstPersonClip.f(10, -38, 22, 0, 0.14f, 0.2f, 0f), FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	/** Arms flung wide for the cyclone and the roar: they sweep out of view at the edges. */
	private static final FirstPersonClip FP_WIDE = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(7, -18, -46, 0, -0.42f, 0.14f, 0.05f),
			FirstPersonClip.f(100, -18, -46, 0, -0.42f, 0.14f, 0.05f), FirstPersonClip.f(112, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_WIDE_END = FirstPersonClip.of(FirstPersonClip.f(0, -18, -46, 0, -0.42f, 0.14f, 0.05f), FirstPersonClip.f(5, -30, -50, 0, -0.5f, 0.2f, 0.05f),
			FirstPersonClip.f(16, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_ROAR = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(8, -18, -46, 0, -0.42f, 0.14f, 0.05f),
			FirstPersonClip.f(26, -18, -46, 0, -0.42f, 0.14f, 0.05f), FirstPersonClip.f(36, 0, 0, 0, 0, 0, 0)).both();
	private static final FirstPersonClip FP_FIN = FirstPersonClip.of(FirstPersonClip.f(0, 0, 0, 0, 0, 0, 0), FirstPersonClip.f(3, -60, 20, 0, 0.16f, 0.4f, 0.05f),
			FirstPersonClip.f(14, -60, 20, 0, 0.16f, 0.4f, 0.05f), FirstPersonClip.f(22, 0, 0, 0, 0, 0, 0)).both();

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

	/** How tall he is DRAWN now: Size Shift eases from one size to the other. */
	private static float drawnHeight(AbstractClientPlayer p) {
		var s = Omnitrix.state(p);
		boolean compact = (s.kitFlags & WayBigKit.F_COMPACT) != 0;
		float to = compact ? WayBigKit.COMPACT_HEIGHT : WayBigKit.COLOSSAL_HEIGHT;
		if (s.animId != WayBigKit.ANIM_SHIFT) {
			return to;
		}
		float partial = net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float t = Mth.clamp((p.level().getGameTime() + partial - s.animStart) / SHIFT_TICKS, 0f, 1f);
		t = t * t * (3f - 2f * t);
		// by the eye the size change is a ratio, so it eases along the logarithm
		float from = compact ? WayBigKit.COLOSSAL_HEIGHT : WayBigKit.COMPACT_HEIGHT;
		return (float) Math.exp(Mth.lerp(t, Math.log(from), Math.log(to)));
	}

	private static final float SHIFT_TICKS = 12f;

	@Override
	public float renderScale(AbstractClientPlayer p) {
		return drawnHeight(p) / 1.8f;
	}

	@Override
	public float cameraDistance(AbstractClientPlayer p) {
		// far enough back to see a 100-block giant from the head down (the eyes are near the top)
		return drawnHeight(p) * 0.8f;
	}

	/** Degrees a tick at full spin, ticks to reach it, ticks to wind down. */
	private static final float SPIN = 30f, SPIN_UP = 12f, SPIN_DOWN = 14f;

	private static float spunBy(float t) {
		return t < SPIN_UP ? SPIN * t * t / (2f * SPIN_UP) : SPIN * (t - SPIN_UP * 0.5f);
	}

	@Override
	public float bodySpin(AbstractClientPlayer p, float partial) {
		var s = Omnitrix.state(p);
		float now = p.level().getGameTime() + partial;
		if ((s.kitFlags & WayBigKit.F_CYCLONE) != 0) {
			return -spunBy(now - s.readyAt(WayBigKit.ID + "." + WayBigKit.CYCLONE_START)) % 360f;
		}
		if (s.animId == WayBigKit.ANIM_CYCLONE_END) {
			// winding down, onto a whole turn so he ends facing where he looks
			float dt = now - s.animStart;
			if (dt >= SPIN_DOWN) {
				return 0f;
			}
			float a0 = spunBy(WayBigKit.CYCLONE_TICKS);
			float target = Mth.ceil((a0 + SPIN * SPIN_DOWN * 0.5f) / 360f) * 360f;
			float k = (target - a0) / (SPIN_DOWN * 0.5f);
			return -(a0 + k * (dt - dt * dt / (2f * SPIN_DOWN))) % 360f;
		}
		return 0f;
	}

	@Override
	public boolean pose(AbstractClientPlayer p, HumanoidModel<?> m, int animId, float ticks, float age) {
		int flags = Omnitrix.state(p).kitFlags;
		if ((flags & WayBigKit.F_CYCLONE) != 0) {
			// the cyclone: arms flung wide, opening out as he spins up; the whole body turns (bodySpin)
			float w = Mth.clamp((p.level().getGameTime() - Omnitrix.state(p).readyAt(WayBigKit.ID + "." + WayBigKit.CYCLONE_START)) / 8f, 0f, 1f);
			m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, 0f);
			m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, 0f);
			m.rightArm.zRot = 1.45f * w;
			m.leftArm.zRot = -1.45f * w;
			m.rightLeg.xRot = Mth.lerp(w, m.rightLeg.xRot, 0f);
			m.leftLeg.xRot = Mth.lerp(w, m.leftLeg.xRot, 0f);
			m.rightLeg.zRot = 0.14f * w;
			m.leftLeg.zRot = -0.14f * w;
			return true;
		}
		if ((flags & WayBigKit.F_RAY) != 0) {
			// held for as long as he fires, whatever else plays; a faint strain runs through him
			boolean played = QuirkKeyframes.play(m, RAY, animId == WayBigKit.ANIM_RAY ? ticks : Math.max(ticks, 5f), 1f);
			m.rightArm.xRot += Mth.sin(age * 2.1f) * 0.012f;
			m.leftArm.yRot += Mth.sin(age * 2.6f) * 0.012f;
			return played;
		}
		boolean played = switch (animId) {
			case WayBigKit.ANIM_RAY_END -> follow(m, RAY_END, ticks);
			case WayBigKit.ANIM_LAND -> follow(m, LAND, ticks);
			case WayBigKit.ANIM_CYCLONE_END -> follow(m, CYCLONE_END, ticks);
			default -> {
				float[][] clip = switch (animId) {
					case WayBigKit.ANIM_FIST -> FIST;
					case WayBigKit.ANIM_CHOP -> CHOP;
					case WayBigKit.ANIM_STOMP -> STOMP;
					case WayBigKit.ANIM_CLAP -> CLAP;
					case WayBigKit.ANIM_STRIDE -> (flags & WayBigKit.F_STRIDE) != 0 ? STRIDE : null;
					case WayBigKit.ANIM_SHIFT -> (flags & WayBigKit.F_COMPACT) != 0 ? SHRINK : GROW;
					case WayBigKit.ANIM_ROAR -> ROAR;
					case WayBigKit.ANIM_FIN -> FIN;
					default -> null;
				};
				yield clip != null && QuirkKeyframes.play(m, clip, ticks, 1f);
			}
		};
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
			case WayBigKit.ANIM_CHOP -> FP_CHOP;
			case WayBigKit.ANIM_RAY -> FP_RAY;
			case WayBigKit.ANIM_RAY_END -> FP_RAY_END;
			case WayBigKit.ANIM_STOMP -> FP_STOMP;
			case WayBigKit.ANIM_CLAP -> FP_CLAP;
			case WayBigKit.ANIM_STRIDE -> FP_STRIDE;
			case WayBigKit.ANIM_LAND -> FP_LAND;
			case WayBigKit.ANIM_SHIFT -> FP_SHIFT;
			case WayBigKit.ANIM_ROAR -> FP_ROAR;
			case WayBigKit.ANIM_CYCLONE -> FP_WIDE;
			case WayBigKit.ANIM_CYCLONE_END -> FP_WIDE_END;
			case WayBigKit.ANIM_FIN -> FP_FIN;
			default -> null;
		};
	}
}
