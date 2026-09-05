package com.herocraft.mod.firearm;

import java.util.function.Supplier;

import net.minecraft.world.item.Item;

/**
 * The four ammunition families. Each firearm consumes exactly one kind; a normal player must carry
 * the matching item in their inventory, a Punisher never does (see {@link FirearmHooks}).
 *
 * <p>The backing {@link Item} is resolved lazily via a supplier so this enum can be referenced from
 * {@link FirearmData} constants that are built before {@code FirearmItems} has registered anything.
 */
public enum AmmoKind {
	PISTOL(() -> com.herocraft.mod.firearm.item.FirearmItems.PISTOL_AMMO),
	RIFLE(() -> com.herocraft.mod.firearm.item.FirearmItems.RIFLE_AMMO),
	SHOTGUN(() -> com.herocraft.mod.firearm.item.FirearmItems.SHOTGUN_SHELL),
	SNIPER(() -> com.herocraft.mod.firearm.item.FirearmItems.SNIPER_AMMO);

	private final Supplier<Item> item;

	AmmoKind(Supplier<Item> item) {
		this.item = item;
	}

	/** The ammo item a normal player must carry. May be null very early in item registration. */
	public Item item() {
		return item.get();
	}

	public String lower() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}
}
