package com.herocraft.mod.punisher.ability;

import com.herocraft.mod.punisher.Punisher;
import com.herocraft.mod.punisher.PunisherConfig;
import com.herocraft.mod.punisher.PunisherFeedback;
import com.herocraft.mod.punisher.entity.FragGrenadeEntity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

/**
 * Ability 2 (G) -- Frag Grenade. Hold to cook (up to {@link PunisherConfig#GRENADE_MAX_COOK_TICKS}),
 * release to throw. Holding past the max cook detonates it in your hand -- you cannot sit on a live
 * grenade forever (spec section 23). 12-second cooldown, started on the throw.
 */
public final class PunisherGrenade {
	public static final String ABILITY = "frag_grenade";

	private PunisherGrenade() {
	}

	/** G pressed: begin the cook if the ability is ready. Returns true if the cook started. */
	public static boolean beginCook(ServerPlayer player) {
		if (!Punisher.hasPower(player) || !Punisher.abilityReady(player, ABILITY)) {
			return false;
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.6f, 1.4f);
		return true;
	}

	/** G released after cooking {@code cookTicks}: throw. */
	public static void throwGrenade(ServerPlayer player, long cookTicks) {
		if (!Punisher.hasPower(player) || !Punisher.abilityReady(player, ABILITY)) {
			return;
		}
		int fuse = (int) Math.max(6, PunisherConfig.GRENADE_FUSE_TICKS - cookTicks);
		spawn(player, fuse, PunisherConfig.GRENADE_THROW_SPEED);
		player.swing(InteractionHand.MAIN_HAND, true);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.8f, 0.8f);
		Punisher.triggerCooldown(player, ABILITY, PunisherConfig.GRENADE_COOLDOWN_TICKS);
	}

	/** Held past the max cook while still pressed: it goes off where the player stands. */
	public static void cookOverflow(ServerPlayer player) {
		if (!Punisher.hasPower(player)) {
			return;
		}
		spawn(player, 1, 0.05f);
		PunisherFeedback.message(player, "grenade_overcooked");
		Punisher.triggerCooldown(player, ABILITY, PunisherConfig.GRENADE_COOLDOWN_TICKS);
	}

	private static void spawn(ServerPlayer player, int fuse, float speed) {
		ServerLevel level = player.serverLevel();
		FragGrenadeEntity g = new FragGrenadeEntity(level, player, fuse);
		Vec3 look = player.getLookAngle();
		g.setPos(player.getX() + look.x * 0.4, player.getEyeY() - 0.15, player.getZ() + look.z * 0.4);
		g.shoot(look.x, look.y + 0.12, look.z, speed, 0.4f);
		g.setDeltaMovement(g.getDeltaMovement().add(player.getDeltaMovement().scale(0.4)));
		level.addFreshEntity(g);
	}
}
