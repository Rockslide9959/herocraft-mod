package com.projecthero.mod.punisher.ability;

import com.projecthero.mod.punisher.Punisher;
import com.projecthero.mod.punisher.PunisherConfig;
import com.projecthero.mod.punisher.PunisherFeedback;
import com.projecthero.mod.punisher.PunisherPassives;
import com.projecthero.mod.punisher.data.PunisherState;

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

	/**
	 * V pressed (v0.15.16): he pulls a syringe and stabs himself with it -- the dose goes in
	 * {@link PunisherConfig#ADRENALINE_STAB_TICKS} later ({@link #tick} then calls {@link #inject}). The cooldown starts
	 * now, so the stab can't be spammed.
	 */
	public static void activate(ServerPlayer player) {
		if (!Punisher.hasPower(player) || !Punisher.abilityReady(player, ABILITY) || stabbing(player)) {
			return;
		}
		player.setAttached(com.projecthero.mod.attachment.ModAttachments.PUNISHER_STAB_AT, player.level().getGameTime());
		Punisher.triggerCooldown(player, ABILITY, PunisherConfig.ADRENALINE_COOLDOWN_TICKS);
		player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.6f, 1.6f);
	}

	public static boolean stabbing(ServerPlayer player) {
		return player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.PUNISHER_STAB_AT, 0L) > 0L;
	}

	/** Every server tick: lands the dose once the stab animation has driven the needle home. */
	public static void tick(ServerPlayer player) {
		long at = player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.PUNISHER_STAB_AT, 0L);
		if (at > 0L && player.level().getGameTime() - at >= PunisherConfig.ADRENALINE_STAB_TICKS) {
			player.setAttached(com.projecthero.mod.attachment.ModAttachments.PUNISHER_STAB_AT, 0L);
			inject(player);
		}
	}

	/** The dose itself: the 20-second heightened state (buffs, firearm perks, muffled audio). */
	public static void inject(ServerPlayer player) {
		if (!Punisher.hasPower(player)) {
			return;
		}
		long end = player.level().getGameTime() + PunisherConfig.ADRENALINE_DURATION_TICKS;
		PunisherState c = Punisher.state(player).copy();
		c.adrenalineUntil = end;
		c.adrenalineCrashAt = end;
		Punisher.save(player, c);
		PunisherPassives.reconcile(player);

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
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 0.9f, 0.7f);
		level.sendParticles(ParticleTypes.ANGRY_VILLAGER, player.getX(), player.getY() + 1.2, player.getZ(),
				6, 0.3, 0.3, 0.3, 0.0);
		PunisherFeedback.message(player, "adrenaline_on");
	}
}
