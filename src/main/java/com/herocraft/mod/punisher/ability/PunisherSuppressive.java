package com.herocraft.mod.punisher.ability;

import com.herocraft.mod.firearm.Firearms;
import com.herocraft.mod.punisher.Punisher;
import com.herocraft.mod.punisher.PunisherConfig;
import com.herocraft.mod.punisher.PunisherFeedback;
import com.herocraft.mod.punisher.PunisherPassives;
import com.herocraft.mod.punisher.data.PunisherState;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Ability 4 (Z) -- Suppressive Fire. Needs the Assault Rifle unlocked. For 4 seconds: much less
 * recoil and spread, a faster rifle fire rate, and enemies you hit are briefly Slowed -- but you
 * move slower yourself while it is up. It is a sustained-fire control tool, not a damage steroid
 * (spec section 25). 20-second cooldown.
 */
public final class PunisherSuppressive {
	public static final String ABILITY = "suppressive_fire";

	private PunisherSuppressive() {
	}

	public static void activate(ServerPlayer player) {
		if (!Punisher.hasPower(player)) {
			return;
		}
		if (!Punisher.weaponUnlocked(player, Firearms.RIFLE)) {
			PunisherFeedback.message(player, "need_rifle");
			return;
		}
		if (!Punisher.abilityReady(player, ABILITY)) {
			return;
		}
		PunisherState c = Punisher.state(player).copy();
		c.suppressiveUntil = player.level().getGameTime() + PunisherConfig.SUPPRESSIVE_DURATION_TICKS;
		Punisher.save(player, c);
		PunisherPassives.reconcile(player);
		Punisher.triggerCooldown(player, ABILITY, PunisherConfig.SUPPRESSIVE_COOLDOWN_TICKS);

		ServerLevel level = player.serverLevel();
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 0.6f, 0.8f);
		level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1.0, player.getZ(),
				12, 0.4, 0.5, 0.4, 0.1);
		PunisherFeedback.message(player, "suppressive_on");
	}
}
