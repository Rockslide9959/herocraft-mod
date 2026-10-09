package com.projecthero.mod.client.punisher;

import java.util.HashMap;
import java.util.Map;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.firearm.FirearmClient;
import com.projecthero.mod.firearm.FirearmStack;
import com.projecthero.mod.firearm.item.FirearmItem;
import com.projecthero.mod.firearm.item.FirearmItems;
import com.projecthero.mod.punisher.PunisherConfig;
import com.projecthero.mod.punisher.data.PunisherState;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * v0.15.16: the shared clock for every gun animation -- third person ({@link PunisherGunPose}, the item-in-hand and
 * body-roll mixins) and first person ({@link GunFirstPerson}) read the same eased weights and progress values from here,
 * all derived from synced state (the aiming flag, the gun's reload-end / last-fired components, the Punisher state's
 * roll window and the stab attachment), so every viewer sees the same thing.
 *
 * <p>Eased weights are advanced by elapsed render time, keyed by entity id, so asking twice in one frame is free.
 */
public final class GunAnim {
	public enum Kind {
		PISTOL, RIFLE, SHOTGUN, SNIPER;

		public boolean longGun() {
			return this != PISTOL;
		}
	}

	/** Ticks after the stab starts that the whole stab animation (raise, plunge, press, pull out) takes. */
	public static final int STAB_ANIM_TICKS = PunisherConfig.ADRENALINE_STAB_TICKS + 10;

	/** id -> [aim, sprint, recoil, lastTime] */
	private static final Map<Integer, float[]> EASE = new HashMap<>();
	/** id -> [reloadEnd, reloadStart] (game ticks) */
	private static final Map<Integer, long[]> RELOAD = new HashMap<>();
	/** id -> stab start (game ticks); outlives the synced flag, which clears the moment the dose lands */
	private static final Map<Integer, Long> STAB = new HashMap<>();
	/** id -> [rollUntil, direction] -- 0 forward, 1 back, 2 right, 3 left */
	private static final Map<Integer, long[]> ROLL = new HashMap<>();
	/** id -> last-fired tick seen */
	private static final Map<Integer, Long> SHOT = new HashMap<>();
	/** id -> {render time of the last shot seen, shot count} for the muzzle flash */
	private static final Map<Integer, float[]> FLASH = new HashMap<>();

	private GunAnim() {
	}

	public static void clear() {
		EASE.clear();
		RELOAD.clear();
		STAB.clear();
		ROLL.clear();
		SHOT.clear();
		FLASH.clear();
	}

	/** The gun in {@code player}'s main hand, or null. */
	public static Kind kind(Player player) {
		return kind(player.getMainHandItem());
	}

	public static Kind kind(ItemStack stack) {
		if (!(stack.getItem() instanceof FirearmItem)) {
			return null;
		}
		if (stack.is(FirearmItems.PUNISHER_PISTOL)) {
			return Kind.PISTOL;
		}
		if (stack.is(FirearmItems.PUNISHER_SHOTGUN)) {
			return Kind.SHOTGUN;
		}
		if (stack.is(FirearmItems.PUNISHER_SNIPER)) {
			return Kind.SNIPER;
		}
		return Kind.RIFLE;
	}

	private static float now(Player player, float partialTick) {
		return player.level().getGameTime() + partialTick;
	}

	private static boolean aimingFlag(Player player) {
		if (player == Minecraft.getInstance().player) {
			return FirearmClient.aiming(); // the local player's own input, a tick ahead of the round trip
		}
		return player.getAttachedOrElse(ModAttachments.FIREARM_AIMING, false);
	}

	private static float[] ease(Player player, float partialTick) {
		float t = now(player, partialTick);
		float[] e = EASE.computeIfAbsent(player.getId(), k -> new float[] { 0f, 0f, 0f, t });
		float dt = Mth.clamp(t - e[3], 0f, 3f);
		e[3] = t;
		if (dt > 0f) {
			boolean gun = kind(player) != null;
			boolean busy = rolling(player, partialTick) >= 0f || stab(player, partialTick) >= 0f;
			float aimTarget = gun && aimingFlag(player) && !busy ? 1f : 0f;
			float sprintTarget = gun && player.isSprinting() && aimTarget == 0f ? 1f : 0f;
			e[0] = approach(e[0], aimTarget, dt / 3.5f);
			e[1] = approach(e[1], sprintTarget, dt / 4.5f);
			e[2] = Math.max(0f, e[2] - dt / 3.0f);
		}
		return e;
	}

	private static float approach(float v, float target, float step) {
		return v < target ? Math.min(target, v + step) : Math.max(target, v - step);
	}

	/** 0..1 how far the gun is raised to the eye (aim down sights). */
	public static float aim(Player player, float partialTick) {
		return smooth(ease(player, partialTick)[0]);
	}

	/** 0..1 how far into the sprint carry. */
	public static float sprint(Player player, float partialTick) {
		return smooth(ease(player, partialTick)[1]);
	}

	/**
	 * 0..1 recoil kick, jumping to 1 on every shot (seen through the gun's synced last-fired tick) and decaying over a
	 * few ticks.
	 */
	public static float recoil(Player player, float partialTick) {
		float[] e = ease(player, partialTick);
		ItemStack s = player.getMainHandItem();
		if (kind(s) != null) {
			long fired = FirearmStack.lastFired(s);
			Long seen = SHOT.put(player.getId(), fired);
			if (seen != null && fired > seen) {
				e[2] = 1f;
				float[] fl = FLASH.computeIfAbsent(player.getId(), k -> new float[2]);
				fl[0] = now(player, partialTick);
				fl[1]++;
			}
		}
		return e[2] * e[2];
	}

	/** 1 -> 0 over the two ticks after a shot (the muzzle flash), else 0. Call after {@link #recoil} this frame. */
	public static float flash(Player player, float partialTick) {
		float[] fl = FLASH.get(player.getId());
		if (fl == null) {
			return 0f;
		}
		float t = now(player, partialTick) - fl[0];
		return t < 0f || t > 2f ? 0f : 1f - t / 2f;
	}

	/** A number that changes every shot, to vary each flash. */
	public static int shotSeed(Player player) {
		float[] fl = FLASH.get(player.getId());
		return fl == null ? 0 : (int) fl[1] * 7919 + player.getId();
	}

	/** Progress 0..1 through the current reload step, or -1 when not reloading. Each shotgun shell is its own step. */
	public static float reload(Player player, float partialTick) {
		ItemStack s = player.getMainHandItem();
		if (kind(s) == null) {
			RELOAD.remove(player.getId());
			return -1f;
		}
		long end = FirearmStack.reloadEnd(s);
		if (end <= 0L) {
			RELOAD.remove(player.getId());
			return -1f;
		}
		long gt = player.level().getGameTime();
		long[] r = RELOAD.get(player.getId());
		if (r == null || r[0] != end) {
			r = new long[] { end, Math.min(gt, end - 1) };
			RELOAD.put(player.getId(), r);
		}
		float dur = Math.max(1f, end - r[1]);
		return Mth.clamp((gt + partialTick - r[1]) / dur, 0f, 1f);
	}

	/** Progress 0..1 through the Tactical Roll, or -1 when not rolling. */
	public static float rolling(Player player, float partialTick) {
		PunisherState s = player.getAttachedOrElse(ModAttachments.PUNISHER_STATE, null);
		if (s == null || s.rollUntil <= 0L) {
			ROLL.remove(player.getId());
			return -1f;
		}
		float left = s.rollUntil - now(player, partialTick);
		if (left <= 0f || left > PunisherConfig.ROLL_DURATION_TICKS) {
			if (left <= 0f) {
				ROLL.remove(player.getId());
			}
			return -1f;
		}
		long[] r = ROLL.get(player.getId());
		if (r == null || r[0] != s.rollUntil) {
			ROLL.put(player.getId(), new long[] { s.rollUntil, rollDirection(player) });
		}
		return 1f - left / PunisherConfig.ROLL_DURATION_TICKS;
	}

	/** Which way the roll goes relative to the body: 0 forward, 1 back, 2 right, 3 left. */
	public static int rollDirectionOf(Player player) {
		long[] r = ROLL.get(player.getId());
		return r == null ? 0 : (int) r[1];
	}

	private static long rollDirection(Player player) {
		double dx = player.getX() - player.xo;
		double dz = player.getZ() - player.zo;
		if (player == Minecraft.getInstance().player) {
			dx = player.getDeltaMovement().x;
			dz = player.getDeltaMovement().z;
		}
		if (dx * dx + dz * dz < 1.0e-4) {
			return 0;
		}
		double yaw = Math.toRadians(player.yBodyRot);
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double fwd = dx * fx + dz * fz;
		double right = dx * -Math.cos(yaw) + dz * -Math.sin(yaw); // the body's right is (-cos, -sin) of its yaw
		if (Math.abs(fwd) >= Math.abs(right)) {
			return fwd >= 0 ? 0 : 1;
		}
		return right >= 0 ? 2 : 3;
	}

	/** Ticks into the Adrenaline stab (0 .. {@link #STAB_ANIM_TICKS}), or -1. */
	public static float stab(Player player, float partialTick) {
		long at = player.getAttachedOrElse(ModAttachments.PUNISHER_STAB_AT, 0L);
		if (at > 0L) {
			STAB.put(player.getId(), at);
		}
		Long start = STAB.get(player.getId());
		if (start == null) {
			return -1f;
		}
		float t = now(player, partialTick) - start;
		if (t < 0f || t > STAB_ANIM_TICKS) {
			if (t > STAB_ANIM_TICKS) {
				STAB.remove(player.getId());
			}
			return -1f;
		}
		return t;
	}

	public static float smooth(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}

	/** Eased 0..1 of {@code x} between {@code a} and {@code b}. */
	public static float seg(float x, float a, float b) {
		return smooth((x - a) / (b - a));
	}
}
