package com.projecthero.mod.client.titanshifter;

import java.util.Map;
import java.util.WeakHashMap;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.projecthero.mod.titanshifter.entity.TitanCorpseEntity;
import com.projecthero.mod.titanshifter.entity.TitanFormEntity;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user, Titan revamp) -- the client side of a Titan's body changes: build / dissolve progress, limb
 * regrowth bookkeeping and the per-texel particle windows the renderers emit from.
 * <p>
 * A renderer {@link Window#prepare prepares} a window once per frame (the texels whose build or dissolve time fell
 * between the last frame and this one, sampled down to the particle budget and grouped by bone), then, as GeckoLib
 * draws each bone with its animated pose on the stack, turns that bone's texels into world positions -- so every
 * particle leaves from the exact spot on the posed body where a pixel just appeared or vanished.
 */
public final class TitanFx {
	/** Seconds-equivalent of the regrowth of a severed limb. */
	public static final int REGROW_TICKS = 160;
	/** The corpse kneels for this long before the skin starts to go. */
	public static final int KNEEL_TICKS = 50;
	private static final Map<Entity, State> STATES = new WeakHashMap<>();
	private static boolean hooked;

	private TitanFx() {
	}

	/** Per-entity client bookkeeping. */
	public static final class State {
		int prevBits = -1;
		final long[] regrowStart = { -1, -1, -1, -1 };
		float lastBuild = -1.0f;
		float lastDissolve = -1.0f;
		long lastFxTick = Long.MIN_VALUE;
		public final Window window = new Window();
		boolean fxTick;
	}

	public static State state(Entity e) {
		hook();
		return STATES.computeIfAbsent(e, k -> new State());
	}

	private static void hook() {
		if (hooked) {
			return;
		}
		hooked = true;
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.level == null) {
				STATES.clear();
				TitanSkinFrames.clear();
				TitanDustParticle.resetSprite();
			}
		});
	}

	public static float partialTick() {
		return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
	}

	/** 1 / 0.4 / 0.1 for the All / Decreased / Minimal particle setting. */
	public static float budget() {
		ParticleStatus s = Minecraft.getInstance().options.particles().get();
		return s == ParticleStatus.ALL ? 1.0f : s == ParticleStatus.DECREASED ? 0.4f : 0.1f;
	}

	// ---------------- the shift

	/** 0..1 through the transformation (the build), or -1 when the Titan is not transforming. */
	public static float buildProgress(TitanFormEntity e, float partialTick) {
		if (e.formState() != TitanFormEntity.FORM_TRANSFORMING) {
			return -1.0f;
		}
		return e.formStateProgress(partialTick);
	}

	// ---------------- severed limbs

	/** Updates the regrowth clocks from the synced bits: a bit that clears starts that limb regrowing. */
	public static void observeLimbs(TitanFormEntity e) {
		State s = state(e);
		int bits = e.severedLimbs();
		long now = e.level().getGameTime();
		if (s.prevBits < 0) {
			s.prevBits = bits;
			return;
		}
		int cleared = s.prevBits & ~bits, cut = bits & ~s.prevBits;
		for (int slot = 0; slot < 4; slot++) {
			int bit = 1 << slot;
			if ((cleared & bit) != 0) {
				s.regrowStart[slot] = now;
			}
			if ((cut & bit) != 0) {
				s.regrowStart[slot] = -1;
			}
		}
		s.prevBits = bits;
	}

	/** Per limb slot: 0..1 while it regrows, NaN when whole (or gone). */
	public static float[] regrowProgress(TitanFormEntity e, float partialTick) {
		State s = state(e);
		float[] out = new float[4];
		long now = e.level().getGameTime();
		for (int slot = 0; slot < 4; slot++) {
			long st = s.regrowStart[slot];
			if (st < 0) {
				out[slot] = Float.NaN;
				continue;
			}
			float p = (now - st + partialTick) / REGROW_TICKS;
			if (p >= 1.0f) {
				s.regrowStart[slot] = -1;
				out[slot] = Float.NaN;
			} else {
				out[slot] = Math.max(0.0f, p);
			}
		}
		return out;
	}

	public static boolean anyRegrowing(float[] p) {
		for (float v : p) {
			if (!Float.isNaN(v)) {
				return true;
			}
		}
		return false;
	}

	// ---------------- the corpse

	/** 0..1 through the corpse's dissolve (0 while it is still sinking to its knees). */
	public static float dissolveProgress(TitanCorpseEntity e, float partialTick) {
		float age = e.dissolveAge(partialTick);
		float total = Math.max(KNEEL_TICKS + 20, TitanCorpseEntity.dissolveTicks() - 10);
		return Math.max(0.0f, Math.min(1.0f, (age - KNEEL_TICKS) / (total - KNEEL_TICKS)));
	}

	/** Once per client tick per entity: true on the first frame of a new tick (for tick-rate effects). */
	public static boolean fxTick(Entity e) {
		State s = state(e);
		long now = e.level().getGameTime();
		s.fxTick = now != s.lastFxTick;
		s.lastFxTick = now;
		return s.fxTick;
	}

	// ---------------- per-bone texel windows

	/** The texels a frame emits particles from, grouped by bone. */
	public static final class Window {
		private IntArrayList[] perBone = new IntArrayList[0];
		private boolean any;

		public void clear() {
			if (any) {
				for (IntArrayList l : perBone) {
					if (l != null) {
						l.clear();
					}
				}
				any = false;
			}
		}

		/** Texels of {@code order} whose time is in (t0, t1], at most {@code budget} of them, evenly spread. */
		public void prepare(TitanTexels tx, int[] order, float[] sorted, float t0, float t1, int budget) {
			clear();
			if (budget <= 0 || t1 <= t0) {
				return;
			}
			int a = TitanTexels.upper(sorted, t0), b = TitanTexels.upper(sorted, t1);
			int n = b - a;
			if (n <= 0) {
				return;
			}
			if (perBone.length != tx.boneNames.length) {
				perBone = new IntArrayList[tx.boneNames.length];
			}
			int stride = Math.max(1, (n + budget - 1) / budget);
			int start = a + (int) (Math.random() * stride);
			for (int k = start; k < b; k += stride) {
				int t = order[k];
				int bone = tx.bone[t];
				if (perBone[bone] == null) {
					perBone[bone] = new IntArrayList();
				}
				perBone[bone].add(t);
				any = true;
			}
		}

		public IntArrayList of(int bone) {
			return any && bone >= 0 && bone < perBone.length ? perBone[bone] : null;
		}
	}

	/** World position of a rest-pose point (model px, geo space) on a bone whose GeckoLib pose is {@code pose}. */
	public static Vec3 world(Matrix4f pose, float x, float y, float z) {
		Vector3f v = pose.transformPosition(-x / 16.0f, y / 16.0f, z / 16.0f, new Vector3f());
		Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		return new Vec3(cam.x + v.x, cam.y + v.y, cam.z + v.z);
	}
}
