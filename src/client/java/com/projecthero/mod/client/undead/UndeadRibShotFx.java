package com.projecthero.mod.client.undead;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.undead.Undead;
import com.projecthero.mod.undead.UndeadKitConfig;
import com.projecthero.mod.undead.UndeadParts;
import com.projecthero.mod.undead.data.UndeadState;
import com.projecthero.mod.undead.entity.UndeadPartEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.17.3 tap V, Rib Shotgun (server: {@code undead.UndeadRibShot}) -- the client side, for every viewer, all driven by the synced
 * animation ({@link UndeadKitConfig#ANIM_RIB_SHOT} and its start), so no packet:
 * <ul>
 * <li>the wind-up: his chest tears open and his ribs spring out of it ({@link #cageRib}: curved, tapering bands of bone that arc
 * forward and out from the sides of the hole and curl back in at their broken, bloody ends) with a little blood where they tear
 * loose;</li>
 * <li>the shot: a burst of blood, torn flesh and bone bits out of the front of his chest, and the spikes trailing drops;</li>
 * <li>the hole it leaves ({@link #wound}, third person, on the body): torn skin, a ring of raw muscle, a dark red cavity with the
 * backbone deep inside and broken rib stubs, a little blood running down,
 * which closes the way a part regrows -- it waits {@code Undead.regrowDelay}, then over the torso's regrowth time the ribs grow
 * back across, the muscle closes over them and the patch shrinks away (with the regrowth's soft fizz);</li>
 * <li>Shift + hold V (gathering for Reform, {@link UndeadKitConfig#ANIM_REFORM_GATHER}): a faint mist drawn in to him.</li>
 * </ul>
 * The spike itself is {@link #spike}, drawn by {@link UndeadPartRenderer}. Fed by {@link UndeadKitFx} (watch, tick, render).
 */
public final class UndeadRibShotFx {
	private static final RandomSource RND = RandomSource.create();

	/** player -> the start of the shot whose hole is still showing. */
	private static final Map<UUID, Long> WOUNDS = new HashMap<>();
	/** player -> the animation start last seen (a fresh one is a new shot / a Reform). */
	private static final Map<UUID, Long> SEEN = new HashMap<>();

	private UndeadRibShotFx() {
	}

	static void reset() {
		WOUNDS.clear();
		SEEN.clear();
	}

	private static long now() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? 0L : mc.level.getGameTime();
	}

	/** How long the hole lasts for {@code p}: the wind-up, the regrowth wait, the torso's regrowth. */
	static int woundLife(Player p) {
		return UndeadKitConfig.RIB_SHOT_WINDUP + Undead.regrowDelay(p) + Undead.regrowTicks(p, UndeadState.BODY);
	}

	// ---------------------------------------------------------------- watching

	static void watch(Player p, UndeadState s, long now) {
		UUID id = p.getUUID();
		Long seen = SEEN.put(id, s.animStart);
		boolean fresh = seen == null || seen != s.animStart;
		if (fresh && s.animId == UndeadKitConfig.ANIM_RIB_SHOT && now - s.animStart < UndeadKitConfig.RIB_SHOT_WINDUP + 4) {
			WOUNDS.put(id, s.animStart);
		} else if (fresh && s.animId == UndeadState.ANIM_REFORM) {
			WOUNDS.remove(id); // forced back together at once
		}
		Long w = WOUNDS.get(id);
		if (w != null && (s.broken(UndeadState.BODY) || now - w > woundLife(p) + 2 || !s.hasPower)) {
			WOUNDS.remove(id);
		}
	}

	// ---------------------------------------------------------------- ticking

	static void tick(long now) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			reset();
			return;
		}
		for (Iterator<Map.Entry<UUID, Long>> it = WOUNDS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Long> en = it.next();
			Player p = mc.level.getPlayerByUUID(en.getKey());
			if (p == null) {
				it.remove();
				continue;
			}
			long age = now - en.getValue();
			boolean own = p == mc.player && mc.options.getCameraType().isFirstPerson();
			Vec3 carry = p.getDeltaMovement();
			Vec3 fwd = flatLook(p);
			Vec3 right = UndeadParts.right(p);
			Vec3 chest = p.position().add(0, p.getBbHeight() * 0.62, 0).add(fwd.scale(0.18));
			int windup = UndeadKitConfig.RIB_SHOT_WINDUP;
			if (age >= 1 && age < windup && !own) {
				// blood where the ribs tear loose, either side of the breastbone
				for (int side = -1; side <= 1; side += 2) {
					Vec3 at = chest.add(right.scale(0.12 * side));
					BloodFx.spurt(at, fwd.add(right.scale(0.7 * side)).add(0, 0.25, 0), 1, 0.1, 0.35, carry);
				}
			}
			if (age == windup) {
				// the blast out of his chest; seen from his own eyes it is pushed out in front, smaller, so the view stays clear
				Vec3 look = p.getLookAngle();
				if (own) {
					Vec3 at = p.getEyePosition().add(look.scale(1.6)).add(0, -0.55, 0);
					BloodFx.spurt(at, look, 5, 0.34, 0.2, carry);
					UndeadSwordFx.bone(at, look.scale(0.22), 3, 0.07);
				} else {
					BloodFx.eruption(chest.add(fwd.scale(0.12)), look, carry, 0.35f);
					UndeadSwordFx.bone(chest.add(fwd.scale(0.15)), look.scale(0.22), 5, 0.1);
				}
			}
			int delay = Undead.regrowDelay(p);
			if (age > windup && age < windup + delay && age % 5 == 0 && !own) {
				BloodFx.drip(chest.add(fwd.scale(0.06)).add(0, -0.12 - RND.nextDouble() * 0.15, 0).add(right.scale((RND.nextDouble() - 0.5) * 0.2)));
			}
			if (age == windup + delay && p.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition()) < 20 * 20) {
				// the hole starts closing: the regrowth's soft fizz
				mc.level.playLocalSound(chest.x, chest.y, chest.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, UndeadSounds.HISS_VOLUME, UndeadSounds.HISS_PITCH,
						false);
			}
		}
		for (Player p : mc.level.players()) {
			UndeadState s = Undead.peek(p);
			if (s == null || !s.hasPower || s.animId != UndeadKitConfig.ANIM_REFORM_GATHER) {
				continue;
			}
			if (p == mc.player && mc.options.getCameraType().isFirstPerson()) {
				continue;
			}
			// gathering himself: a faint red mist drawn in to his chest
			Vec3 c = p.position().add(0, p.getBbHeight() * 0.6, 0);
			double a = RND.nextDouble() * Math.PI * 2, r = 0.7 + RND.nextDouble() * 0.4;
			Vec3 at = c.add(Math.cos(a) * r, (RND.nextDouble() - 0.4) * 0.8, Math.sin(a) * r);
			Vec3 v = c.subtract(at).scale(0.09);
			BloodFx.drop(at.x, at.y, at.z, v.x, v.y, v.z, 0.05f + RND.nextFloat() * 0.05f, BloodFx.shade(0.3f + RND.nextFloat() * 0.5f), 10, 1);
		}
		// the spikes in flight: a fine trail of drops off their bloody roots
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof UndeadPartEntity d && d.isRib() && !d.stuck() && d.getDeltaMovement().lengthSqr() > 0.25 && RND.nextFloat() < 0.6f) {
				Vec3 v = d.getDeltaMovement();
				Vec3 at = d.position().add(0, d.getBbHeight() * 0.5, 0).subtract(v.normalize().scale(0.3));
				BloodFx.drop(at.x, at.y, at.z, v.x * 0.08 + (RND.nextDouble() - 0.5) * 0.03, v.y * 0.08, v.z * 0.08 + (RND.nextDouble() - 0.5) * 0.03,
						0.018f + RND.nextFloat() * 0.018f, BloodFx.shade(RND.nextFloat()), 16 + RND.nextInt(10), 0);
			}
		}
	}

	private static Vec3 flatLook(Player p) {
		Vec3 look = p.getLookAngle();
		Vec3 f = new Vec3(look.x, 0, look.z);
		return f.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : f.normalize();
	}

	// ---------------------------------------------------------------- drawing

	private static float ease(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	/** A small fixed jitter per band (so the torn edge is ragged, the same every frame). */
	private static float jag(int i) {
		return ((i * 73 + 19) % 11) / 10f;
	}

	/** "Up" in body space (+y is down there). */
	private static final Vector3f UP = new Vector3f(0, -1, 0);

	/** The torn hole's top and bottom (body px down from the neck) and its half-width at {@code y}, before any shrinking. */
	static final float HOLE_TOP = 1.6f, HOLE_BOTTOM = 8.4f;

	static float holeHalf(float y) {
		float lens = Mth.sin(Mth.clamp((y - HOLE_TOP) / (HOLE_BOTTOM - HOLE_TOP), 0f, 1f) * Mth.PI);
		return 1.2f + 1.55f * lens;
	}

	/** Each side's ribs (top to bottom): six. */
	static final int RIBS = 6;

	static float ribY(int i) {
		return 2.35f + i * 1.15f;
	}

	/**
	 * Third person (from the model layer, {@link UndeadBoneSwordRender#thirdPerson}): the hole in his chest and, through the
	 * wind-up, his ribs springing out of it; the ribs gone after the shot (they are the spikes), the hole closing like a regrowing
	 * part.
	 */
	static void wound(PoseStack pose, MultiBufferSource buffers, int light, Player player, PlayerModel<?> model, UndeadState s, float now) {
		Long w = WOUNDS.get(player.getUUID());
		if (w == null || s.broken(UndeadState.BODY)) {
			return;
		}
		float age = now - w;
		int windup = UndeadKitConfig.RIB_SHOT_WINDUP;
		int delay = Undead.regrowDelay(player);
		int grow = Undead.regrowTicks(player, UndeadState.BODY);
		if (age < 0f || age > windup + delay + grow) {
			return;
		}
		float open = age < windup ? ease(age / (windup * 0.6f)) : 1f;
		float c = Mth.clamp((age - windup - delay) / grow, 0f, 1f); // the regrowth: bone, then muscle, then skin
		float bone = Mth.clamp(c / 0.4f, 0f, 1f);
		float muscle = Mth.clamp((c - 0.4f) / 0.45f, 0f, 1f);
		float skin = Mth.clamp((c - 0.85f) / 0.15f, 0f, 1f);
		float size = open * (1f - skin);
		if (size <= 0.02f) {
			return;
		}
		pose.pushPose();
		model.body.translateAndRotate(pose);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(UndeadRender.PALETTE));
		cavity(vc, pose, light, size, muscle);
		if (age < windup) {
			float k = ribsOut(age / windup);
			for (int side = -1; side <= 1; side += 2) {
				for (int i = 0; i < RIBS; i++) {
					UndeadBones.rib(vc, pose, cageRib(side, i, k), UP, 0.95f, 0.6f, 0.5f, 0.36f, 0.84f, false, light);
				}
			}
		} else if (muscle < 1f) {
			stubs(vc, pose, light, size, bone);
		}
		if (age >= windup) {
			// a little blood running down from the bottom of the hole (lengthening after the shot, gone as the muscle closes)
			float run = Mth.clamp((age - windup) / 16f, 0f, 1f) * (1f - muscle);
			float[] xs = { -1.1f, 0.9f };
			for (int k = 0; k < xs.length; k++) {
				float len = (1.2f + 1.3f * jag(k + 7)) * run;
				if (len > 0.05f) {
					UndeadBoneSwordRender.box(vc, pose.last(), xs[k] * size - 0.16f, HOLE_BOTTOM - 0.3f, -2.36f, xs[k] * size + 0.16f, HOLE_BOTTOM + len,
							-2.29f, UndeadBoneSwordRender.BLOOD_WET, light);
					UndeadBoneSwordRender.box(vc, pose.last(), xs[k] * size - 0.2f, HOLE_BOTTOM + len - 0.1f, -2.38f, xs[k] * size + 0.2f,
							HOLE_BOTTOM + len + 0.3f, -2.29f, UndeadBoneSwordRender.BLOOD, light);
				}
			}
		}
		pose.popPose();
	}

	/** How far out the ribs have sprung, {@code t} (0..1) through the wind-up: snapped out past their rest, then settling. */
	static float ribsOut(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return t < 0.5f ? 1.1f * ease(t / 0.5f) : 1.1f - 0.1f * ease((t - 0.5f) / 0.5f);
	}

	/**
	 * Rib {@code i} on {@code side} sprung {@code k} out (body px): rooted at the side of the hole, just inside the chest, it arcs
	 * forward and outward, then curls back in at its broken end -- the two halves of the cage opening like a jaw.
	 */
	static Vector3f[] cageRib(int side, int i, float k) {
		int n = 8;
		Vector3f[] pts = new Vector3f[n + 1];
		float y0 = ribY(i);
		float mid = Mth.sin((i + 0.5f) / RIBS * Mth.PI);
		float len = (5.4f + 3.2f * mid) * (0.45f + 0.55f * Math.min(1f, k));
		float x0 = side * Math.max(0.6f, holeHalf(y0) * 0.78f);
		Vector3f p = new Vector3f(x0, y0, -1.7f);
		pts[0] = new Vector3f(p);
		float phi0 = Mth.lerp(k, -60f, 64f - 8f * (1f - mid)), curl = 62f;
		float step = len / n;
		for (int j = 1; j <= n; j++) {
			float u = (j - 0.5f) / n;
			float phi = (float) Math.toRadians(phi0 - curl * u);
			float dy = 0.07f + 0.1f * u * k; // sloping down a little, a little more toward the end
			Vector3f d = new Vector3f(side * Mth.sin(phi), dy, -Mth.cos(phi)).normalize().mul(step);
			p.add(d);
			pts[j] = new Vector3f(p);
		}
		return pts;
	}

	/**
	 * The open hole in his chest (body space, just proud of the skin): the torn skin edge, a ring of raw muscle, a wet rim, and
	 * the dark red cavity inside it with the backbone deep at the back -- {@code size} open, the muscle closing in from the edges
	 * by {@code muscle}.
	 */
	private static void cavity(VertexConsumer vc, PoseStack pose, int light, float size, float muscle) {
		PoseStack.Pose pp = pose.last();
		int bands = 9;
		float bh = (HOLE_BOTTOM - HOLE_TOP) / bands;
		for (int i = 0; i < bands; i++) {
			float y0 = HOLE_TOP + i * bh, y1 = y0 + bh + 0.02f;
			float hw = (holeHalf(y0 + bh * 0.5f) + 0.25f * (jag(i) - 0.5f)) * size;
			// inside: dark red flesh, darkest in the middle and deepest down
			float depth = 1f - Math.abs(i + 0.5f - bands / 2f) / (bands / 2f);
			UndeadBoneSwordRender.box(vc, pp, -hw, y0, -2.3f, hw, y1, -2.27f, UndeadBones.mix(0x5A0B11, 0x2C0306, depth * 0.8f), light);
			UndeadBoneSwordRender.box(vc, pp, -hw * 0.55f, y0, -2.32f, hw * 0.55f, y1, -2.28f, UndeadBones.mix(0x480810, 0x240205, depth), light);
			// a ring of raw muscle round it, fibres alternating, and the wet glint at its inner edge
			float rim = (0.45f + 0.25f * jag(i + 3)) * Math.max(0.4f, size);
			int m0 = BloodFx.FLESH[1 + (i % 2)], m1 = BloodFx.FLESH[(i + 1) % 2 == 0 ? 1 : 2];
			UndeadBoneSwordRender.box(vc, pp, -hw - rim, y0, -2.4f, -hw + 0.15f, y1, -2.27f, m0, light);
			UndeadBoneSwordRender.box(vc, pp, hw - 0.15f, y0, -2.4f, hw + rim, y1, -2.27f, m1, light);
			UndeadBoneSwordRender.box(vc, pp, -hw - 0.05f, y0, -2.43f, -hw + 0.25f, y1, -2.38f, UndeadBoneSwordRender.BLOOD_GLOSS, light);
			UndeadBoneSwordRender.box(vc, pp, hw - 0.25f, y0, -2.43f, hw + 0.05f, y1, -2.38f, UndeadBoneSwordRender.BLOOD_GLOSS, light);
			// the torn skin edge: ragged flaps curling out
			float flap = 0.22f + 0.3f * jag(i + 5);
			UndeadBoneSwordRender.box(vc, pp, -hw - rim - flap, y0, -2.36f, -hw - rim + 0.05f, y1, -2.27f, UndeadBoneSwordRender.SKIN_DARK, light);
			UndeadBoneSwordRender.box(vc, pp, hw + rim - 0.05f, y0, -2.36f, hw + rim + flap, y1, -2.27f, UndeadBoneSwordRender.SKIN_DARK, light);
			// the muscle closing in from the edges as it heals
			if (muscle > 0f) {
				float m = hw * muscle;
				int mc = BloodFx.FLESH[1 + (i % 3)];
				UndeadBoneSwordRender.box(vc, pp, -hw, y0, -2.46f, -hw + m, y1, -2.3f, mc, light);
				UndeadBoneSwordRender.box(vc, pp, hw - m, y0, -2.46f, hw, y1, -2.3f, mc, light);
			}
		}
		// the top and bottom of the rim
		for (int e = 0; e < 2; e++) {
			float y = e == 0 ? HOLE_TOP : HOLE_BOTTOM;
			float hw = holeHalf(y) * size + 0.4f;
			float y0 = e == 0 ? y - 0.45f : y - 0.05f, y1 = e == 0 ? y + 0.05f : y + 0.45f;
			UndeadBoneSwordRender.box(vc, pp, -hw, y0, -2.38f, hw, y1, -2.27f, BloodFx.FLESH[1], light);
		}
		// the backbone, deep at the back of the hole: a column of vertebrae in shadow
		if (muscle < 0.7f) {
			for (int v = 0; v < 6; v++) {
				float y = HOLE_TOP + 0.55f + v * 1.1f;
				float hw = 0.36f * size;
				UndeadBoneSwordRender.box(vc, pp, -hw, y, -2.34f, hw, y + 0.66f, -2.31f, 0x5A483A, light);
				UndeadBoneSwordRender.box(vc, pp, -hw * 1.7f, y + 0.2f, -2.335f, hw * 1.7f, y + 0.46f, -2.31f, 0x463428, light);
			}
		}
	}

	/** After the shot: what is left of the ribs -- broken stubs out of the sides of the hole, growing back across it as it heals. */
	private static void stubs(VertexConsumer vc, PoseStack pose, int light, float size, float bone) {
		for (int side = -1; side <= 1; side += 2) {
			for (int i = 0; i < RIBS; i++) {
				if (i == 0 || i == RIBS - 1) {
					if (bone < 0.3f) {
						continue; // the short top and bottom ones went whole: they come back last
					}
				}
				float y = ribY(i);
				float hw = holeHalf(y) * size;
				float len = Mth.lerp(bone, 0.7f + 0.6f * jag(i * 2 + side + 4), hw + 0.15f);
				int n = 3;
				Vector3f[] pts = new Vector3f[n + 1];
				for (int j = 0; j <= n; j++) {
					float u = j / (float) n;
					// from the edge of the hole in toward the middle, bowing forward a little
					pts[j] = new Vector3f(side * (hw + 0.3f - len * u), y + 0.15f * u, -2.4f - 0.35f * Mth.sin(u * Mth.PI) - 0.05f * u);
				}
				UndeadBones.rib(vc, pose, pts, UP, 0.75f, 0.6f, 0.35f, 0.3f, bone < 0.9f ? 0.4f : 2f, bone >= 0.9f, light);
			}
		}
	}

	/**
	 * One rib spike (from {@link UndeadPartRenderer}, pose at the spike's middle, +z along the flight): a curved rib torn out whole,
	 * a flat band of bone bowed along its length and narrowing to a sharp point at the front, soaked in blood and splintered at the
	 * root it was torn out by.
	 */
	static void spike(PoseStack pose, MultiBufferSource buffers, int light) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(UndeadRender.PALETTE));
		int n = 8;
		float len = 11f;
		Vector3f[] pts = new Vector3f[n + 1];
		for (int j = 0; j <= n; j++) {
			float u = j / (float) n;
			// root at the back (-z), point at the front; bowed sideways like a rib, and a little up at the point
			float z = len * (u - 0.5f);
			pts[n - j] = new Vector3f(1.7f * Mth.sin(u * Mth.PI) - 0.4f * u, 0.35f * u * u, z);
		}
		// drawn root to point: the rib's blood (bloodFrom) is at its root here, so the points go point-first into rib() reversed
		Vector3f[] fwd = new Vector3f[n + 1];
		for (int j = 0; j <= n; j++) {
			fwd[j] = pts[n - j];
		}
		Vector3f up = new Vector3f(0, 1, 0);
		spikeBand(vc, pose, fwd, up, light);
		// the bloody, splintered root
		Vector3f r = fwd[0];
		Vector3f back = new Vector3f(fwd[0]).sub(fwd[1]).normalize();
		Vector3f s1 = new Vector3f(r).add(new Vector3f(back).mul(0.9f)).add(0.25f, 0.3f, 0f);
		Vector3f s2 = new Vector3f(r).add(new Vector3f(back).mul(0.6f)).add(-0.2f, -0.3f, 0f);
		UndeadBones.bar(vc, pose, r, s1, up, 0.32f, 0.36f, 0.02f, UndeadBones.mix(UndeadBones.BONE_SHADE, UndeadBoneSwordRender.BLOOD_WET, 0.5f), light);
		UndeadBones.bar(vc, pose, r, s2, up, 0.34f, 0.34f, 0.02f, UndeadBones.mix(UndeadBones.BONE_DARK, UndeadBoneSwordRender.BLOOD, 0.55f), light);
		UndeadBones.lump(vc, pose, new Vector3f(r).add(new Vector3f(back).mul(0.15f)), back, up, 0.5f, 0.75f, 0.35f, UndeadBones.MARROW, light);
		UndeadBones.lump(vc, pose, new Vector3f(fwd[1]).add(0f, -0.55f, 0f), new Vector3f(0, -1, 0), up, 0.28f, 0.28f, 0.55f, UndeadBoneSwordRender.BLOOD_WET,
				light);
	}

	/** The spike's band: like {@link UndeadBones#rib} but bloody at the root end (the start) and pointed at the far end. */
	private static void spikeBand(VertexConsumer vc, PoseStack pose, Vector3f[] pts, Vector3f up, int light) {
		int n = pts.length - 1;
		for (int j = 0; j < n; j++) {
			float t = (j + 0.5f) / n;
			float h = Mth.lerp(t, 1.0f, 0.5f), w = Mth.lerp(t, 0.5f, 0.32f);
			int col = UndeadBones.mix(UndeadBones.BONE, UndeadBones.BONE_SHADE, 0.2f + 0.5f * t);
			int hi = UndeadBones.mix(UndeadBones.BONE_HI, UndeadBones.BONE, t * 0.6f);
			int lo = UndeadBones.mix(UndeadBones.BONE_SHADE, UndeadBones.BONE_DARK, 0.4f + 0.3f * t);
			float blood = Mth.clamp(1f - t / 0.3f, 0f, 1f); // the first 30% from the root
			if (blood > 0f) {
				col = UndeadBones.mix(col, UndeadBoneSwordRender.BLOOD_WET, 0.2f + 0.65f * blood);
				hi = UndeadBones.mix(hi, UndeadBoneSwordRender.BLOOD_GLOSS, 0.2f + 0.6f * blood);
				lo = UndeadBones.mix(lo, UndeadBoneSwordRender.BLOOD, 0.3f + 0.6f * blood);
			}
			Vector3f a = pts[j], b = pts[j + 1];
			UndeadBones.bar(vc, pose, a, b, up, w, h, 0.06f, col, light);
			Vector3f top = new Vector3f(0, h * 0.36f, 0), bot = new Vector3f(0, -h * 0.36f, 0);
			UndeadBones.bar(vc, pose, new Vector3f(a).add(top), new Vector3f(b).add(top), up, w * 1.04f, h * 0.22f, 0.04f, hi, light);
			UndeadBones.bar(vc, pose, new Vector3f(a).add(bot), new Vector3f(b).add(bot), up, w * 1.06f, h * 0.26f, 0.04f, lo, light);
		}
		// the point
		Vector3f end = pts[n];
		Vector3f dir = new Vector3f(end).sub(pts[n - 1]).normalize();
		Vector3f p1 = new Vector3f(end).add(new Vector3f(dir).mul(0.7f));
		UndeadBones.bar(vc, pose, end, p1, up, 0.22f, 0.32f, 0.03f, UndeadBones.BONE_SHADE, light);
		Vector3f p2 = new Vector3f(p1).add(new Vector3f(dir).mul(0.6f));
		UndeadBones.bar(vc, pose, p1, p2, up, 0.12f, 0.14f, 0.02f, UndeadBones.BONE_DARK, light);
	}
}
