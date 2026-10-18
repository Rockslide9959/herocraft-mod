package com.projecthero.mod.client.quirk.darkshadow;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.darkshadow.DarkShadow;
import com.projecthero.mod.quirk.darkshadow.DarkShadowCombat;
import com.projecthero.mod.quirk.data.QuirkState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7: Dark Shadow in the world, for every viewer (the state is synced). It grows out of its user's upper back on
 * a tendril neck, its beaked head floating at the left shoulder looking where they look, its clawed arms hanging under
 * it. Its size follows the Shadow meter (small and meek in daylight, big in the dark; huge and spiked while berserk).
 * The moves stretch it: a Strike / Flurry / Counter / frenzy hit lunges the head and claws at the synced reach point,
 * a Grab holds on to the creature, Abyssal Swallow opens its maw round the swallowed one, Shadow Guard puts it in front
 * with the wings wrapped round as a shield, Black Fallen Angel spreads the wings from the back. Black Ankh turns it into
 * armour instead ({@link DarkShadowAnkhLayer}): the creature is not drawn then.
 *
 * <p>First person, your own Dark Shadow only shows while it reaches out (an idle head at the shoulder would sit in
 * the camera).
 */
public final class DarkShadowRender {
	private static final int NECK_SEGMENTS = 7;
	private static final Map<Integer, Long> WISPS = new ConcurrentHashMap<>();

	private DarkShadowRender() {
	}

	static void reset() {
		WISPS.clear();
	}

	/** How far it is stretched to the reach point (0..1) for the playing move. */
	static float extension(Player p, QuirkState s, float partial) {
		float age = p.level().getGameTime() - s.animStart + partial;
		int len = switch (s.animId) {
			case DarkShadow.ANIM_STRIKE, DarkShadow.ANIM_COUNTER, DarkShadow.ANIM_THROW -> 11;
			case DarkShadow.ANIM_FLURRY -> 5;
			case DarkShadow.ANIM_FRENZY_HIT -> 7;
			case DarkShadow.ANIM_GRAB -> 12;
			case DarkShadow.ANIM_SWALLOW -> 10;
			default -> 0;
		};
		if (len == 0 || age < 0f || age >= len) {
			return 0f;
		}
		float out = Math.max(1f, len * 0.3f);
		float hold = len * 0.5f;
		if (age < out) {
			return ease(age / out);
		}
		if (age < hold) {
			return 1f;
		}
		return 1f - ease((age - hold) / (len - hold));
	}

	private static float ease(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	/** How big it is, from the meter and its moods. */
	static float size(Player p, float partial) {
		float sh = DarkShadow.shadow(p);
		float s = 0.7f + 0.6f * sh;
		if (DarkShadow.berserk(p)) {
			s += 0.65f;
		} else if (DarkShadow.rampaging(p)) {
			s += 0.4f;
		}
		QuirkState st = Quirks.peek(p);
		if (st != null && st.animId == DarkShadow.ANIM_CALM) {
			float age = p.level().getGameTime() - st.animStart + partial;
			if (age >= 0f && age < 20f) {
				s *= 0.55f + 0.45f * ease(age / 20f);
			}
		}
		return s;
	}

	public static void render(WorldRenderContext ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || ctx.consumers() == null || ctx.matrixStack() == null) {
			return;
		}
		float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 cam = ctx.camera().getPosition();
		for (Player p : mc.level.players()) {
			if (!DarkShadow.is(p) || p.isInvisible() || p.isSpectator() || p.distanceToSqr(cam) > 96 * 96) {
				continue;
			}
			QuirkState s = Quirks.peek(p);
			if (s == null) {
				continue;
			}
			boolean firstPerson = p == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
			renderOne(ctx.matrixStack(), ctx.consumers(), p, s, partial, cam, firstPerson);
		}
	}

	private static Vec3 yawDir(float yawDeg) {
		float r = yawDeg * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(r), 0, Mth.cos(r));
	}

	private static void renderOne(PoseStack pose, MultiBufferSource buffers, Player p, QuirkState s, float partial, Vec3 cam, boolean firstPerson) {
		Minecraft mc = Minecraft.getInstance();
		long now = p.level().getGameTime();
		float t = now + partial;
		Vec3 pos = p.getPosition(partial);
		float bodyYaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
		float headYaw = p.getViewYRot(partial);
		float pitch = p.getViewXRot(partial);
		Vec3 up = new Vec3(0, 1, 0);
		Vec3 fwd = yawDir(firstPerson ? headYaw : bodyYaw);
		Vec3 left = leftOf(fwd);
		float size = size(p, partial);
		boolean berserk = DarkShadow.berserk(p);
		boolean frenzy = berserk || DarkShadow.rampaging(p);
		boolean guard = DarkShadow.guarding(p) || DarkShadow.running(p, DarkShadow.V_COUNTER);
		boolean angel = DarkShadow.angel(p);
		boolean ankh = DarkShadow.ankh(p);
		float crouch = p.isCrouching() ? -0.3f : 0f;

		// where it grows from
		Vec3 base = firstPerson
				? p.getEyePosition(partial).add(left.scale(-0.55)).add(0, -0.6, 0).add(fwd.scale(0.15))
				: pos.add(0, 1.3 + crouch, 0).subtract(fwd.scale(0.22));

		// ---------------------------------------------------------------- the wings (Angel, or wrapped round as a guard)
		VertexConsumer body = buffers.getBuffer(RenderType.entityCutoutNoCull(DarkShadowModel.TEXTURE));
		int light = light(p, pos.add(0, 1.4, 0));
		int tint = berserk ? 0xFFFFE0E8 : 0xFFFFFFFF;
		if (!firstPerson && (angel || guard)) {
			pose.pushPose();
			Vec3 root = pos.add(0, 1.38 + crouch, 0).subtract(fwd.scale(0.2));
			pose.translate(root.x - cam.x, root.y - cam.y, root.z - cam.z);
			DarkShadowModel.flip(pose, bodyYaw);
			float ws = (angel ? 1.25f : 1.0f) * Math.max(0.9f, size);
			pose.scale(ws, ws, ws);
			boolean flying = p.getAbilities().flying || !p.onGround();
			for (int side = -1; side <= 1; side += 2) {
				float sweep, lift, fan;
				if (guard) {
					// wrapped forward round the body as a shield
					sweep = 1.25f;
					lift = 0.05f;
					fan = 0.35f;
				} else if (flying) {
					float flap = Mth.sin(t * 0.22f);
					sweep = -0.25f + 0.1f * flap;
					lift = 0.35f + 0.35f * flap;
					fan = 1f;
				} else {
					sweep = -0.55f;
					lift = 0.15f + 0.04f * Mth.sin(t * 0.08f);
					fan = 0.6f;
				}
				pose.pushPose();
				pose.translate(side * 1.5f / 16f, 0f, 2.5f / 16f);
				DarkShadowModel.wing(pose, body, light, side, sweep, lift, fan, tint);
				pose.popPose();
			}
			pose.popPose();
		}
		if (ankh) {
			return; // it is the armour now
		}

		// ---------------------------------------------------------------- where the head goes
		float bob = 0.05f * Mth.sin(t * 0.11f);
		Vec3 idle = base.add(up.scale(0.78 * size + bob)).add(left.scale(0.58 * size)).subtract(fwd.scale(0.12));
		Vec3 head = idle;
		float lookYaw = headYaw - 12f;
		float lookPitch = pitch * 0.6f * Mth.DEG_TO_RAD;
		float jaw = 0.12f + 0.06f * Mth.sin(t * 0.07f);
		float reachAmt = 0f;
		Vec3 target = null;
		Vec3 reach = DarkShadow.reach(p);
		float ext = extension(p, s, partial);
		Entity grabbed = entity(p, s.val(DarkShadow.V_GRAB));
		Entity swallowed = entity(p, s.val(DarkShadow.V_SWALLOW));
		if (swallowed != null) {
			Vec3 c = swallowed.getPosition(partial).add(0, swallowed.getBbHeight() * 0.5, 0);
			target = c;
			head = c.add(0, 0.25 * size, 0).subtract(fwd.scale(0.15));
			reachAmt = 1f;
			jaw = 0.95f;
			size *= 1.25f;
		} else if (grabbed != null) {
			target = grabbed.getPosition(partial).add(0, grabbed.getBbHeight() * 0.55, 0);
			reachAmt = 1f;
			jaw = 0.7f;
		} else if (reach != null && ext > 0f) {
			target = reach;
			reachAmt = ext;
			jaw = 0.15f + 0.75f * ext;
		}
		if (target != null && swallowed == null) {
			Vec3 d = target.subtract(idle);
			double len = d.length();
			if (len > 14.0) {
				target = idle.add(d.scale(14.0 / len));
				d = target.subtract(idle);
				len = 14.0;
			}
			Vec3 stop = len > 0.6 ? target.subtract(d.normalize().scale(0.45 * size)) : target;
			head = idle.lerp(stop, reachAmt);
		}
		if (target != null) {
			Vec3 d = target.subtract(head);
			if (d.lengthSqr() < 0.04) {
				d = target.subtract(base);
			}
			float ty = (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
			float tp = (float) Math.atan2(-d.y, Math.sqrt(d.x * d.x + d.z * d.z));
			lookYaw = Mth.rotLerp(Math.max(reachAmt, 0.4f), lookYaw, ty);
			lookPitch = Mth.lerp(Math.max(reachAmt, 0.4f), lookPitch, tp);
		} else if (guard) {
			head = base.add(fwd.scale(0.7)).add(up.scale(0.25 * size));
			lookYaw = headYaw;
			jaw = 0.5f;
		} else if (frenzy) {
			// thrashing, out of control
			head = idle.add(up.scale(0.35 * size)).add(left.scale(0.5 * Mth.sin(t * 0.37f)))
					.add(fwd.scale(0.35 * Mth.sin(t * 0.23f + 1.3f)));
			lookYaw = headYaw + 70f * Mth.sin(t * 0.19f);
			lookPitch = 0.35f * Mth.sin(t * 0.31f);
			jaw = 0.55f + 0.4f * Mth.sin(t * 0.6f);
		} else if (DarkShadow.running(p, DarkShadow.V_DIVE)) {
			head = base.add(p.getViewVector(partial).scale(1.1)).add(up.scale(0.1));
			lookYaw = headYaw;
			lookPitch = pitch * Mth.DEG_TO_RAD;
			jaw = 0.6f;
		}
		boolean show = !firstPerson || reachAmt > 0.08f || guard || frenzy || swallowed != null || grabbed != null;
		if (!show) {
			return;
		}

		// ---------------------------------------------------------------- the neck: a curve from the back to the head
		Vec3 ctrl = base.add(up.scale(0.5 * size)).add(head.subtract(base).scale(0.25));
		Vec3[] pts = new Vec3[NECK_SEGMENTS + 1];
		for (int i = 0; i <= NECK_SEGMENTS; i++) {
			float k = (float) i / NECK_SEGMENTS;
			Vec3 a = base.lerp(ctrl, k);
			Vec3 b = ctrl.lerp(head, k);
			pts[i] = a.lerp(b, k);
		}
		for (int i = 0; i < NECK_SEGMENTS; i++) {
			if (firstPerson && pts[i].distanceToSqr(cam) < 0.5 * 0.5) {
				continue;
			}
			float k = (float) i / NECK_SEGMENTS;
			float thick = (0.26f - 0.08f * k) * size;
			drawSegment(pose, body, light, cam, pts[i], pts[i + 1], thick, tint);
			if (berserk && i % 2 == 1) {
				// spines along the neck
				Vec3 mid = pts[i].lerp(pts[i + 1], 0.5);
				drawSpike(pose, body, light, cam, mid, mid.add(up.scale(0.4 * size)).add(left.scale(0.15 * (i % 4 == 1 ? 1 : -1))), 1.2f * size, tint);
			}
		}

		// ---------------------------------------------------------------- the arms
		Vec3 shoulderAt = pts[NECK_SEGMENTS - 2];
		Vec3 hl = leftOf(yawDir(lookYaw));
		for (int side = -1; side <= 1; side += 2) {
			Vec3 shoulder = shoulderAt.add(hl.scale(side * 0.2 * size));
			Vec3 hand;
			if (target != null) {
				hand = shoulder.lerp(target.add(hl.scale(side * 0.25 * size)), Math.min(1f, reachAmt * 0.95f));
			} else if (guard) {
				hand = head.add(hl.scale(side * 0.45 * size)).add(0, -0.35 * size, 0);
			} else if (frenzy) {
				hand = shoulder.add(yawDir(lookYaw + side * 50f).scale(0.7 * size)).add(0, 0.25 * size * Mth.sin(t * 0.5f + side), 0);
			} else {
				hand = head.add(hl.scale(side * 0.32 * size)).add(yawDir(lookYaw).scale(0.12 * size)).add(0, -0.42 * size, 0);
			}
			Vec3 mid = shoulder.lerp(hand, 0.5).add(hl.scale(side * 0.22 * size)).add(0, -0.12 * size, 0);
			float th = 0.12f * size;
			drawSegment(pose, body, light, cam, shoulder, mid, th, tint);
			drawSegment(pose, body, light, cam, mid, hand, th * 0.85f, tint);
			Vec3 dir = hand.subtract(mid);
			pose.pushPose();
			pose.translate(hand.x - cam.x, hand.y - cam.y, hand.z - cam.z);
			orient(pose, dir);
			DarkShadowModel.claw(pose, body, light, target != null ? (grabbed != null || swallowed != null ? 0.9f : 0.2f) : 0.65f, 1.15f * size, tint);
			pose.popPose();
		}

		// ---------------------------------------------------------------- the head
		pose.pushPose();
		pose.translate(head.x - cam.x, head.y - cam.y, head.z - cam.z);
		DarkShadowModel.flip(pose, lookYaw);
		pose.scale(size, size, size);
		float spikes = berserk ? 1f : Mth.clamp((DarkShadow.shadow(p) - 0.75f) / 0.25f, 0f, 1f) * 0.6f;
		DarkShadowModel.head(pose, body, light, lookPitch, jaw, spikes, tint);
		DarkShadowModel.eyes(pose, buffers.getBuffer(RenderType.eyes(DarkShadowModel.TEXTURE)), lookPitch, berserk ? 0xFFFF7060 : 0xFFFFFFFF);
		pose.popPose();

		// a wisp of shadow now and then (client-side, once a tick)
		Long last = WISPS.get(p.getId());
		if ((last == null || last != now) && !mc.isPaused()) {
			WISPS.put(p.getId(), now);
			if (now % 3 == 0) {
				Vec3 w = pts[p.getRandom().nextInt(pts.length)];
				p.level().addParticle(berserk ? DarkShadowCombat.SHADOW_BIG : DarkShadowCombat.SHADOW, w.x, w.y, w.z, 0, 0.01, 0);
			}
		}
	}

	private static Vec3 leftOf(Vec3 fwd) {
		// facing +z (yaw 0) the left is +x: (cos yaw, 0, sin yaw) == (fwd.z, 0, -fwd.x)
		return new Vec3(fwd.z, 0, -fwd.x);
	}

	private static Entity entity(Player p, long idPlus1) {
		if (idPlus1 == 0L) {
			return null;
		}
		Entity e = p.level().getEntity((int) (idPlus1 - 1L));
		return e != null && e.isAlive() ? e : null;
	}

	private static int light(Player p, Vec3 at) {
		int packed = LevelRenderer.getLightColor(p.level(), BlockPos.containing(at));
		// never pitch black: its violet edges always show a little
		int block = Math.max(LightTexture.block(packed), 5);
		return LightTexture.pack(block, LightTexture.sky(packed));
	}

	/** Turns the pose so +z points along {@code dir}. */
	private static void orient(PoseStack pose, Vec3 dir) {
		if (dir.lengthSqr() < 1.0e-6) {
			return;
		}
		Vector3f d = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z).normalize();
		pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 0, 1), d));
	}

	private static void drawSegment(PoseStack pose, VertexConsumer vc, int light, Vec3 cam, Vec3 a, Vec3 b, float thick, int argb) {
		Vec3 d = b.subtract(a);
		float len = (float) d.length();
		if (len < 1.0e-3) {
			return;
		}
		pose.pushPose();
		pose.translate(a.x - cam.x, a.y - cam.y, a.z - cam.z);
		orient(pose, d);
		// a touch longer so the joints overlap with no gap
		DarkShadowModel.segment(pose, vc, light, len + thick * 0.5f, thick, argb);
		pose.popPose();
	}

	private static void drawSpike(PoseStack pose, VertexConsumer vc, int light, Vec3 cam, Vec3 a, Vec3 b, float len, int argb) {
		pose.pushPose();
		pose.translate(a.x - cam.x, a.y - cam.y, a.z - cam.z);
		orient(pose, b.subtract(a));
		pose.scale(1.4f, 1.4f, 1f);
		DarkShadowModel.spike(pose, vc, light, len, argb);
		pose.popPose();
	}
}
