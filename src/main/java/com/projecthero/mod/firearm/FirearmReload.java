package com.projecthero.mod.firearm;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * The reload state machine, entirely on the {@link ItemStack} via {@link FirearmStack}. Two shapes:
 *
 * <ul>
 *   <li><b>Magazine reload</b> (pistol / rifle / sniper): one timed step; on completion the magazine
 *       is topped off from the reserve (as far as the reserve allows -- partial reloads are fine).</li>
 *   <li><b>Shell reload</b> (shotgun): one shell per step, re-armed after each until the tube is full
 *       or the reserve runs out. Firing calls {@link #cancel} which stops it immediately, keeping the
 *       shells already loaded (spec section 6).</li>
 * </ul>
 */
public final class FirearmReload {
	private FirearmReload() {
	}

	/** Begin a reload if it makes sense to (not already reloading, magazine not full, ammo available). */
	public static void start(ServerPlayer player, ItemStack stack, FirearmData data) {
		if (FirearmStack.isReloading(stack)) {
			return;
		}
		int mag = FirearmStack.magazine(stack, data);
		if (mag >= data.magazineSize) {
			return;
		}
		if (FirearmAmmo.reserveCount(player, data.ammo) <= 0) {
			// No reserve to load. Do NOT play a sound here -- this method is polled from the auto-reload
			// path every tick, and a click per tick is the "constantly plays a sound" bug. The dry-fire
			// click already comes from FirearmShooting when the trigger is actually pulled.
			return;
		}
		long now = player.level().getGameTime();
		int step = stepTicks(player, data);
		FirearmStack.setReloadEnd(stack, now + step);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				data.reloadSound, SoundSource.PLAYERS, 0.6f, data.shellReload ? 1.3f : 1.0f);
	}

	/** Stop a reload in progress, keeping whatever is already in the magazine. */
	public static void cancel(ItemStack stack) {
		FirearmStack.setReloadEnd(stack, 0L);
	}

	/** Called every tick for the held firearm; advances or completes an active reload. */
	public static void tick(ServerPlayer player, ItemStack stack, FirearmData data) {
		long end = FirearmStack.reloadEnd(stack);
		if (end <= 0L) {
			return;
		}
		long now = player.level().getGameTime();
		if (now < end) {
			return;
		}
		int mag = FirearmStack.magazine(stack, data);

		if (data.shellReload) {
			int taken = FirearmAmmo.take(player, data.ammo, 1);
			if (taken >= 1) {
				mag += 1;
				FirearmStack.setMagazine(stack, mag);
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
						data.reloadSound, SoundSource.PLAYERS, 0.5f, 1.4f);
			}
			boolean more = taken >= 1 && mag < data.magazineSize
					&& FirearmAmmo.reserveCount(player, data.ammo) > 0;
			if (more) {
				FirearmStack.setReloadEnd(stack, now + stepTicks(player, data));
			} else {
				FirearmStack.setReloadEnd(stack, 0L);
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
						data.cycleSound, SoundSource.PLAYERS, 0.5f, 0.9f);
			}
			return;
		}

		int need = data.magazineSize - mag;
		int taken = FirearmAmmo.take(player, data.ammo, need);
		FirearmStack.setMagazine(stack, mag + taken);
		FirearmStack.setReloadEnd(stack, 0L);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				data.cycleSound, SoundSource.PLAYERS, 0.5f, 1.1f);
	}

	/** Duration of one reload step, with the Punisher's faster-handling passive folded in. */
	public static int stepTicks(ServerPlayer player, FirearmData data) {
		float factor = FirearmHooks.get().reloadSpeedFactor(player);
		return Math.max(2, Math.round(data.reloadTicks * factor));
	}

	/** Fraction 0..1 of the current reload step elapsed (for the HUD bar). */
	public static float progress(ItemStack stack, FirearmData data, ServerPlayer player, long now) {
		long end = FirearmStack.reloadEnd(stack);
		if (end <= 0L) {
			return 0f;
		}
		int step = stepTicks(player, data);
		float remaining = end - now;
		return Math.max(0f, Math.min(1f, 1f - remaining / step));
	}
}
