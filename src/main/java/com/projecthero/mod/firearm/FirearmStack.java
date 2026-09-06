package com.projecthero.mod.firearm;

import com.projecthero.mod.item.ModDataComponents;

import net.minecraft.world.item.ItemStack;

/**
 * Reads and writes the per-stack firearm state (magazine, reload clock, last-fired tick) held in the
 * {@link ModDataComponents} components. Everything lives on the {@link ItemStack} so it survives
 * dropping, death, relog and inventory transfer with no extra bookkeeping, and the server is always
 * authoritative -- these are only ever mutated server-side.
 */
public final class FirearmStack {
	private FirearmStack() {
	}

	/** Loaded rounds. A stack with no component reads as a full magazine (fresh gun ships loaded). */
	public static int magazine(ItemStack stack, FirearmData data) {
		Integer m = stack.get(ModDataComponents.FIREARM_MAGAZINE);
		return m == null ? data.magazineSize : Math.max(0, Math.min(data.magazineSize, m));
	}

	public static void setMagazine(ItemStack stack, int rounds) {
		stack.set(ModDataComponents.FIREARM_MAGAZINE, Math.max(0, rounds));
	}

	public static long reloadEnd(ItemStack stack) {
		Long v = stack.get(ModDataComponents.FIREARM_RELOAD_END);
		return v == null ? 0L : v;
	}

	public static void setReloadEnd(ItemStack stack, long gameTime) {
		if (gameTime <= 0L) {
			stack.remove(ModDataComponents.FIREARM_RELOAD_END);
		} else {
			stack.set(ModDataComponents.FIREARM_RELOAD_END, gameTime);
		}
	}

	public static boolean isReloading(ItemStack stack) {
		return reloadEnd(stack) > 0L;
	}

	public static long lastFired(ItemStack stack) {
		Long v = stack.get(ModDataComponents.FIREARM_LAST_FIRED);
		return v == null ? 0L : v;
	}

	public static void setLastFired(ItemStack stack, long gameTime) {
		stack.set(ModDataComponents.FIREARM_LAST_FIRED, gameTime);
	}

	/** Fire rate + pump/bolt cycle gate. */
	public static boolean canCycleFire(ItemStack stack, FirearmData data, long now) {
		long since = now - lastFired(stack);
		return since >= data.fireIntervalTicks && since >= data.cycleTicks;
	}
}
