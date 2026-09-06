package com.projecthero.mod.armor;

import net.minecraft.world.entity.Entity;

/**
 * A tiny client-side hand-off: the shared GeckoLib armour renderer stashes the entity it is currently
 * rendering armour for here, so a {@link SuperheroArmorItem} whose visual identity depends on the
 * <em>wearer</em> (not just the item) -- Max Steel, whose model swaps with the active Turbo Mode --
 * can resolve it inside {@code armorSetId()}.
 *
 * <p>Lives in {@code main} so the item (common code) can read it without depending on the client
 * renderer. It is only ever written by the client renderer and only meaningful mid-render; on a
 * dedicated server it is never touched.
 */
public final class ArmorRenderContext {
	private static final ThreadLocal<Entity> WEARER = new ThreadLocal<>();

	private ArmorRenderContext() {
	}

	public static void set(Entity entity) {
		WEARER.set(entity);
	}

	public static void clear() {
		WEARER.remove();
	}

	public static Entity wearer() {
		return WEARER.get();
	}
}
