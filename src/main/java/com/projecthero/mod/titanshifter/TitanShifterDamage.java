package com.projecthero.mod.titanshifter;

import com.projecthero.mod.titanshifter.entity.TitanFormEntity;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Mob;

/**
 * While a shifter is inside their Titan, the <em>Titan</em> takes the hits (its own health pool), never the
 * rider: every damage source is vetoed against the rider except the ones that bypass invulnerability (the
 * void, /kill), so a shifter can always be removed by an operator.
 */
public final class TitanShifterDamage {
	private TitanShifterDamage() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer p && TitanFormEntity.isOwnerRider(p)) {
				TitanFormEntity f = (TitanFormEntity) p.getVehicle();
				if (source.getEntity() instanceof Mob && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
					f.hurt(source, amount); // v0.12.34: a mob that somehow reached the rider hits the Titan instead
				}
				return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
			}
			return true;
		});
	}
}
