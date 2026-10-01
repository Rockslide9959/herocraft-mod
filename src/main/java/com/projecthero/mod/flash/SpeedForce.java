package com.projecthero.mod.flash;

import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.PowerPassives;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;

/**
 * v0.14.13: <b>Super Speed is a Hero-Tier power</b>, awakened by the Speed Force rather than brewed. Its moves still run
 * on the mutation engine ({@link SuperSpeedHandlers}, owned through {@link ExperimentalPowers}), but it is held as the
 * Primary power {@code "super_speed"} ({@link HeroTiers#HERO_KEYS}): gaining it replaces whatever the player had, and
 * gaining anything else replaces it.
 *
 * <h2>Origin</h2>
 * With <b>Strength, Speed and Jump Boost</b> all active at once, either activate a block of <b>Charged Copper Plates</b>
 * (within 4 blocks) or be <b>struck by lightning</b>. The surge burns the three effects off either way, and it takes
 * only half the time ({@link #SUCCESS_CHANCE}) -- otherwise the Speed Force rejects you and you gather them again.
 */
public final class SpeedForce {
	public static final String HERO_KEY = "super_speed";
	public static final float SUCCESS_CHANCE = 0.5f;

	public enum Outcome { NOT_READY, ALREADY, REJECTED, AWAKENED }

	private SpeedForce() {
	}

	static void initialize() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer p && p.isAlive() && source.is(DamageTypes.LIGHTNING_BOLT)) {
				tryAwaken(p);
			}
		});
	}

	public static Power power() {
		return Powers.byKey(SuperSpeedHandlers.KEY);
	}

	public static boolean hasPower(ServerPlayer player) {
		return ExperimentalPowers.owns(player, SuperSpeedHandlers.KEY);
	}

	/** Strength, Speed and Jump Boost, all at once. */
	public static boolean hasCatalysts(ServerPlayer player) {
		return player.hasEffect(MobEffects.DAMAGE_BOOST) && player.hasEffect(MobEffects.MOVEMENT_SPEED)
				&& player.hasEffect(MobEffects.JUMP);
	}

	private static boolean hasAnyCatalyst(ServerPlayer player) {
		return player.hasEffect(MobEffects.DAMAGE_BOOST) || player.hasEffect(MobEffects.MOVEMENT_SPEED)
				|| player.hasEffect(MobEffects.JUMP);
	}

	/** A surge (Charged Copper Plates or a lightning strike) hits {@code player}: roll the Speed Force. */
	public static Outcome tryAwaken(ServerPlayer player) {
		return tryAwaken(player, player.getRandom().nextFloat() < SUCCESS_CHANCE);
	}

	/** {@link #tryAwaken(ServerPlayer)} with the 50% roll already made (tests). */
	public static Outcome tryAwaken(ServerPlayer player, boolean success) {
		if (power() == null || !Powers.isEnabled(power())) {
			return Outcome.NOT_READY;
		}
		if (hasPower(player)) {
			return Outcome.ALREADY;
		}
		if (!hasCatalysts(player)) {
			if (hasAnyCatalyst(player)) {
				player.displayClientMessage(Component.translatable("message.projecthero.speed_force.missing")
						.withStyle(ChatFormatting.GRAY), true);
			}
			return Outcome.NOT_READY;
		}
		player.removeEffect(MobEffects.DAMAGE_BOOST);
		player.removeEffect(MobEffects.MOVEMENT_SPEED);
		player.removeEffect(MobEffects.JUMP);
		if (!success) {
			reject(player);
			return Outcome.REJECTED;
		}
		grant(player);
		announce(player);
		return Outcome.AWAKENED;
	}

	/** Gives Super Speed as the player's one Primary power (replacing what they had). False if already held. */
	public static boolean grant(ServerPlayer player) {
		Power speed = power();
		if (speed == null || hasPower(player)) {
			return false;
		}
		HeroTiers.claimPrimary(player, HERO_KEY);
		boolean ok = ExperimentalPowers.grant(player, speed);
		if (ok) {
			ExperimentalPowers.setActive(player, speed);
			PowerPassives.reconcileActive(player);
		}
		return ok;
	}

	public static void revoke(ServerPlayer player) {
		Power speed = power();
		if (speed != null && ExperimentalPowers.forget(player, speed)) {
			PowerPassives.reconcileActive(player);
		}
	}

	private static void reject(ServerPlayer player) {
		player.displayClientMessage(Component.translatable("message.projecthero.speed_force.rejected")
				.withStyle(ChatFormatting.RED), false);
		if (player.level() instanceof ServerLevel level) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE,
					SoundSource.PLAYERS, 1.0f, 0.6f);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.0, player.getZ(),
					20, 0.4, 0.6, 0.4, 0.02);
		}
	}

	private static void announce(ServerPlayer player) {
		player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
		player.connection.send(new ClientboundSetSubtitleTextPacket(
				Component.translatable("message.projecthero.speed_force.awakened.sub").withStyle(ChatFormatting.YELLOW)));
		player.connection.send(new ClientboundSetTitleTextPacket(
				Component.translatable("message.projecthero.speed_force.awakened.title")
						.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
		player.sendSystemMessage(Component.translatable("message.projecthero.speed_force.awakened.hint")
				.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		if (player.level() instanceof ServerLevel level) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER,
					SoundSource.PLAYERS, 0.8f, 1.6f);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT,
					SoundSource.PLAYERS, 1.0f, 1.8f);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(),
					60, 0.5, 0.9, 0.5, 0.4);
			level.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0, 0, 0, 0);
		}
	}
}
