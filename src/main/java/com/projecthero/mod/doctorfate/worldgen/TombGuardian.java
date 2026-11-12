package com.projecthero.mod.doctorfate.worldgen;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

/**
 * v0.17.3 Doctor Fate: a Tomb Guardian, one of the mummy-wrapped husks set to watch the Helmet of Fate (a husk on a player-shaped
 * zombie body with a bandaged skin, client {@code TombGuardianRenderer}). A little tougher than a husk; never turns into a zombie
 * in water; stays put (persistent) so the tomb is still guarded when someone finds it.
 */
public class TombGuardian extends Husk {
	public TombGuardian(EntityType<? extends Husk> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Zombie.createAttributes().add(Attributes.MAX_HEALTH, 30.0).add(Attributes.ATTACK_DAMAGE, 4.0)
				.add(Attributes.ARMOR, 4.0).add(Attributes.FOLLOW_RANGE, 24.0);
	}

	@Override
	protected boolean convertsInWater() {
		return false;
	}
}
