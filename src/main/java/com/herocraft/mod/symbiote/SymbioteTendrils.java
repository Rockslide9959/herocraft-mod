package com.herocraft.mod.symbiote;

import com.herocraft.mod.hero.power.AbilityHelpers;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The tendril pull -- shared by the Normal host's Tendril Grab ability and Black Suit Spider-Man's
 * "Symbiote Web Tendrils" (a free-of-web-reserve alternative to Web Yank). One implementation so the
 * two don't quietly drift apart.
 */
final class SymbioteTendrils {
	private SymbioteTendrils() {
	}

	/** Pull a light target to the player, or pull the player to a heavy one. Returns false on a whiff. */
	static boolean pull(ServerPlayer player, double range) {
		LivingEntity target = AbilityHelpers.raycastEntity(player, range);
		if (target == null) {
			return false;
		}
		ServerLevel level = AbilityHelpers.level(player);
		Vec3 hand = player.getEyePosition().add(player.getLookAngle().scale(0.6)).add(0, -0.3, 0);
		AbilityHelpers.line(level, hand, target.position().add(0, target.getBbHeight() * 0.5, 0),
				ParticleTypes.SQUID_INK, 3.0);
		AbilityHelpers.sound(player, SoundEvents.SLIME_SQUISH, 0.8f, 0.6f);

		if (target.getMaxHealth() <= 200.0f) {
			Vec3 toPlayer = player.position().subtract(target.position()).normalize().scale(1.4).add(0, 0.25, 0);
			AbilityHelpers.push(target, toPlayer);
		} else {
			Vec3 toTarget = target.position().subtract(player.position()).normalize().scale(1.1).add(0, 0.2, 0);
			AbilityHelpers.addImpulse(player, toTarget);
		}
		return true;
	}
}
