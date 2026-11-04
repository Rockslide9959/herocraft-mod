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
