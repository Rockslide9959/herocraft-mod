package com.projecthero.mod.event.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

/**
 * Shared base for every Zombie Raid zombie variant. It exists to make the same three corrections in
 * one place rather than five:
 *
 * <ul>
 *   <li><b>No daylight burning.</b> A raid is scheduled by a 20-minute curse timer, so it very often
 *       starts in broad daylight. Letting the wave immolate itself would turn the headline event into
 *       a no-op depending on what time the player happened to be cursed.</li>
 *   <li><b>No drowned conversion.</b> A wave mob that wanders into water must stay the mob the wave
 *       counter is tracking; converting would silently drop it out of the raid.</li>
 *   <li><b>No reinforcements.</b> Vanilla zombies can summon more zombies when they hurt a player.
 *       During a 12-wave raid that compounds into an unbounded entity count -- precisely the kind of
 *       thing the spec's performance section forbids -- and it would also spawn mobs the raid does
 *       not own and therefore never cleans up.</li>
 * </ul>
 */
public abstract class RaidUndead extends Zombie {
	protected RaidUndead(EntityType<? extends Zombie> type, Level level) {
		super(type, level);
	}

	@Override
	protected boolean isSunSensitive() {
		return false;
	}

	@Override
	protected boolean convertsInWater() {
		return false;
	}

	@Override
	protected void randomizeReinforcementsChance() {
		var attr = this.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
		if (attr != null) {
			attr.setBaseValue(0.0);
		}
	}
}
