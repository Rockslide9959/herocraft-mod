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

/**
 * v0.17.3 tap V, Rib Shotgun (server: {@code undead.UndeadRibShot}) -- the client side, for every viewer, all driven by the synced
 * animation ({@link UndeadKitConfig#ANIM_RIB_SHOT} and its start), so no packet:
 * <ul>
 * <li>the wind-up: his ribs spring half open out of his chest (the Rib Cage Guard's ribs, {@link #opening}) with a little blood
 * where they tear loose;</li>
 * <li>the shot: a burst of blood, torn flesh and bone bits out of the front of his chest, and the spikes trailing drops;</li>
 * <li>the hole it leaves ({@link #wound}, third person, on the body): a raw cavity with broken rib stubs and blood running down,
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

	/** 0..1 through the rib shot's wind-up as the ribs spring half out (the Rib Cage Guard's ribs), 0 outside it. */
	static float opening(Player p, float partial) {
		Long w = WOUNDS.get(p.getUUID());
		Minecraft mc = Minecraft.getInstance();
		if (w == null || (p == mc.player && mc.options.getCameraType().isFirstPerson())) {
			return 0f; // (from his own eyes the ribs right under the camera would fill the bottom of the view)
		}
		float age = now() - w + partial;
		if (age < 0f || age >= UndeadKitConfig.RIB_SHOT_WINDUP) {
			return 0f;
		}
		float k = age / UndeadKitConfig.RIB_SHOT_WINDUP;
		return 0.12f + 0.5f * k * k * (3f - 2f * k);
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
					BloodFx.eruption(chest.add(fwd.scale(0.12)), look, carry, 0.55f);
					UndeadSwordFx.bone(chest.add(fwd.scale(0.15)), look.scale(0.22), 9, 0.1);
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

	/**
	 * Third person (from the model layer, {@link UndeadBoneSwordRender#thirdPerson}): the hole in his chest -- torn open through
	 * the wind-up, raw after the shot (dark cavity, ragged wet rim, broken rib stubs, blood running down), then closing like a
	 * regrowing part: the ribs grow back across, the muscle closes over them and the patch shrinks away.
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
		float open = age < windup ? ease(age / windup) : 1f;
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
		PoseStack.Pose pp = pose.last();
		// (body px: x across, y down from the neck, front face at z = -2, the jacket layer out to -2.25)
		float top = 1.6f, bottom = 8.2f;
		int bands = 6;
		float bh = (bottom - top) / bands;
		float zc = -2.3f, zr = -2.42f;
		for (int i = 0; i < bands; i++) {
			float y0 = top + i * bh, y1 = y0 + bh;
			// a lens: widest in the middle, narrower at the top and bottom, the edge ragged
			float lens = Mth.sin((i + 0.5f) / bands * Mth.PI);
			float hw = (1.3f + 1.4f * lens + 0.35f * jag(i)) * size;
			// the dark cavity
			UndeadBoneSwordRender.box(vc, pp, -hw, y0, zc - 0.04f, hw, y1, zc + 0.02f, UndeadBoneSwordRender.BLOOD_DARK, light);
			// the wet torn rim
			float rim = 0.38f + 0.2f * jag(i + 3);
			int rc = i % 2 == 0 ? UndeadBoneSwordRender.BLOOD_GLOSS : UndeadBoneSwordRender.BLOOD_WET;
			UndeadBoneSwordRender.box(vc, pp, -hw - rim, y0, zr, -hw + 0.1f, y1, zc, rc, light);
			UndeadBoneSwordRender.box(vc, pp, hw - 0.1f, y0, zr, hw + rim, y1, zc, rc, light);
			// the muscle closing in from the edges
			if (muscle > 0f) {
				float m = hw * muscle;
				int mc = BloodFx.FLESH[1 + (i % 3)];
				UndeadBoneSwordRender.box(vc, pp, -hw, y0, zr - 0.06f, -hw + m, y1, zr + 0.02f, mc, light);
				UndeadBoneSwordRender.box(vc, pp, hw - m, y0, zr - 0.06f, hw, y1, zr + 0.02f, mc, light);
			}
		}
		// the broken rib stubs, four a side, growing back across the hole as it heals
		for (int r = 0; r < 4; r++) {
			float y = top + 0.9f + r * 1.55f;
			float lens = Mth.sin((y - top) / (bottom - top) * Mth.PI);
			float hw = (1.3f + 1.4f * lens) * size;
			float len = Mth.lerp(bone, 0.45f + 0.35f * jag(r + 5), hw + 0.1f);
			if (muscle >= 1f) {
				continue;
			}
			for (int side = -1; side <= 1; side += 2) {
				float x0 = side * hw, x1 = side * (hw - len);
				UndeadBoneSwordRender.box(vc, pp, Math.min(x0, x1), y - 0.32f, zc - 0.12f, Math.max(x0, x1), y + 0.32f, zc - 0.02f,
						(r + side) % 2 == 0 ? UndeadBoneSwordRender.BONE : UndeadBoneSwordRender.BONE_SHADE, light);
				// a splintered end while they are still broken
				if (bone < 0.9f) {
					UndeadBoneSwordRender.box(vc, pp, x1 - 0.18f, y - 0.2f, zc - 0.14f, x1 + 0.18f, y + 0.2f, zc - 0.06f, UndeadBoneSwordRender.BONE_DARK, light);
				}
			}
		}
		// blood running down from the hole (lengthening after the shot, gone as the skin closes)
		if (age >= windup) {
			float run = Mth.clamp((age - windup) / 14f, 0f, 1f) * (1f - muscle);
			float[] xs = { -1.4f, 0.3f, 1.7f };
			for (int k = 0; k < xs.length; k++) {
				float len = (1.6f + 1.4f * jag(k + 7)) * run;
				if (len > 0.05f) {
					UndeadBoneSwordRender.box(vc, pp, xs[k] * size - 0.22f, bottom - 0.2f, zr + 0.02f, xs[k] * size + 0.22f, bottom + len, zr + 0.1f,
							UndeadBoneSwordRender.BLOOD_WET, light);
				}
			}
		}
		pose.popPose();
	}

	/**
	 * One rib spike (from {@link UndeadPartRenderer}, pose at the spike's middle, +z along the flight): a slender, slightly bowed
	 * length of rib, sharp at the front, splintered and soaked in blood at the root it was torn out by.
	 */
	static void spike(PoseStack pose, MultiBufferSource buffers, int light) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(UndeadRender.PALETTE));
		PoseStack.Pose p = pose.last();
		int segs = 6;
		float len = 11f;
		float px = 0f, pz = -len / 2f;
		for (int i = 1; i <= segs; i++) {
			float t = i / (float) segs;
			float z = -len / 2f + len * t;
			float x = 1.5f * Mth.sin(Mth.PI * t);
			float th = Mth.lerp(t, 0.62f, 0.26f);
			int col = i == segs ? UndeadBoneSwordRender.BONE_SHADE : i % 2 == 0 ? UndeadBoneSwordRender.BONE : UndeadBoneSwordRender.BONE_SHADE;
			UndeadBoneSwordRender.box(vc, p, Math.min(px, x) - th, -th * 0.8f, pz, Math.max(px, x) + th, th * 0.8f, z, col, light);
			px = x;
			pz = z;
		}
		// the sharp point
		UndeadBoneSwordRender.box(vc, p, px - 0.16f, -0.16f, pz, px + 0.16f, 0.16f, pz + 1.0f, UndeadBoneSwordRender.BONE_DARK, light);
		// the bloody, splintered root
		float r0 = -len / 2f;
		UndeadBoneSwordRender.box(vc, p, -0.8f, -0.7f, r0 - 0.9f, 0.8f, 0.7f, r0 + 0.6f, UndeadBoneSwordRender.BLOOD, light);
		UndeadBoneSwordRender.box(vc, p, -0.45f, 0.3f, r0 - 1.3f, 0.2f, 0.85f, r0 - 0.5f, UndeadBoneSwordRender.BLOOD_WET, light);
		UndeadBoneSwordRender.box(vc, p, 0.1f, -0.75f, r0 + 0.4f, 0.9f, -0.35f, r0 + 2.2f, UndeadBoneSwordRender.BLOOD_GLOSS, light);
	}
}
