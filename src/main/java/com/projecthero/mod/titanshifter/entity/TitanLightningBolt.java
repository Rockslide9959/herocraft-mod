package com.projecthero.mod.titanshifter.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;

/**
 * v0.13.11: the bolt that strikes when a shifter transforms. Behaves exactly like a vanilla
 * {@link LightningBolt} (always spawned visual-only: no fire, no damage, no terrain) -- it only exists as
 * its own entity type so the client can draw it yellow ({@code TitanLightningRenderer}) instead of
 * vanilla's white-blue.
 */
public class TitanLightningBolt extends LightningBolt {
	public TitanLightningBolt(EntityType<? extends LightningBolt> type, Level level) {
		super(type, level);
	}
}
