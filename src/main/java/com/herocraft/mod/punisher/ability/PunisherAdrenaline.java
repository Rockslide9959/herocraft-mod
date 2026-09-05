package com.herocraft.mod.punisher.ability;

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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Ability 5 (V slot) -- Adrenaline (v0.9.4). For 20 seconds: Regeneration V (first 3 s only),
 * Resistance II, Haste II, Speed II, and the firearm perks (+25% reload speed which
 * <em>supersedes</em> the passive 15%, +15% firearm damage). Game audio is 30% muffled while it
 * runs. When it wears off the player takes a Nausea I crash for 10 seconds. 30-second cooldown.
 */
public final class PunisherAdrenaline {
	public static final String ABILITY = "adrenaline";

	private PunisherAdrenaline() {
	}

	public static void activate(ServerPlayer player) {
		if (!Punisher.hasPower(player) || !Punisher.abilityReady(player, ABILITY)) {
			return;
		}
		long end = player.level().getGameTime() + PunisherConfig.ADRENALINE_DURATION_TICKS;
		PunisherState c = Punisher.state(player).copy();
		c.adrenalineUntil = end;
		c.adrenalineCrashAt = end;
		Punisher.save(player, c);
		PunisherPassives.reconcile(player);
		Punisher.triggerCooldown(player, ABILITY, PunisherConfig.ADRENALINE_COOLDOWN_TICKS);

		int dur = PunisherConfig.ADRENALINE_DURATION_TICKS;
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
				PunisherConfig.ADRENALINE_REGEN_TICKS, PunisherConfig.ADRENALINE_REGEN_AMP, false, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
				dur, PunisherConfig.ADRENALINE_RESISTANCE_AMP, false, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED,
				dur, PunisherConfig.ADRENALINE_HASTE_AMP, false, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
				dur, PunisherConfig.ADRENALINE_SPEED_AMP, false, false, true));

		ServerLevel level = player.serverLevel();
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.7f, 0.6f);
		level.sendParticles(ParticleTypes.ANGRY_VILLAGER, player.getX(), player.getY() + 1.2, player.getZ(),
				6, 0.3, 0.3, 0.3, 0.0);
		PunisherFeedback.message(player, "adrenaline_on");
	}
}
