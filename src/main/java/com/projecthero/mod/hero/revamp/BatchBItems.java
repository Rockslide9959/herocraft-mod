package com.projecthero.mod.hero.revamp;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.power.p09.IceBladeItem;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.item.ItemEntity;

/** v0.13.22 batch B items: the Cryokinesis Ice Blade (never craftable, never in a creative tab). */
public final class BatchBItems {
	public static final IceBladeItem ICE_BLADE = Registry.register(BuiltInRegistries.ITEM,
			ProjectHeroMod.id("ice_blade"), new IceBladeItem());

	private BatchBItems() {
	}

	static void initialize() {
		// An Ice Blade can never lie in the world as an item -- dropped, thrown out of a death pile or spilled from a
		// container, it simply melts away.
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof ItemEntity item && IceBladeItem.isBlade(item.getItem())) {
				item.discard();
			}
		});
	}
}
