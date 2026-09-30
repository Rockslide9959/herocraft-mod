package com.projecthero.mod.supersoldier;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * What drinking a Super Soldier Serum does (v0.14.8). The <b>unrefined</b> serum (crafted from a Potion of Strength,
 * Swiftness and Leaping) takes 1 time in 10 -- the other 9 the body rejects it and the drinker dies
 * ({@link SuperSoldierDamage#SERUM_REJECTION}). Ten minutes in a Blast Furnace refines it, and the <b>refined</b> serum
 * always works. The roll is passed in (the item uses the server level's RNG) so tests can force either outcome.
 */
public final class SuperSoldierSerum {
	/** Chance the unrefined serum takes. */
	public static final float UNREFINED_SUCCESS_CHANCE = 0.10f;

	public enum Outcome {
		GRANTED, REJECTED, ALREADY
	}

	private SuperSoldierSerum() {
	}

	/** {@code roll} is uniform in [0, 1). */
	public static boolean succeeds(boolean refined, float roll) {
		return refined || roll < UNREFINED_SUCCESS_CHANCE;
	}

	public static Outcome drink(ServerPlayer player, boolean refined, float roll) {
		if (SuperSoldier.hasPower(player)) {
			return Outcome.ALREADY;
		}
		if (succeeds(refined, roll)) {
			SuperSoldier.grant(player);
			return Outcome.GRANTED;
		}
		reject(player);
		return Outcome.REJECTED;
	}

	/** The body rejects the serum: a violent seizure and death, with its own death message. Creative is no protection. */
	private static void reject(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0f, 0.6f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.0f, 0.6f);
		level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.3, 0.5, 0.3, 0.02);
		player.displayClientMessage(Component.translatable("message.projecthero.super_soldier.rejected").withStyle(ChatFormatting.DARK_RED), true);
		player.setAbsorptionAmount(0.0f);
		player.invulnerableTime = 0;
		// bypasses armour, effects, enchantments and invulnerability (creative too); a power's own last-ditch save
		// (a Symbiote, Khonshu's Resurrection) may still pull him back -- that is its call
		player.hurt(SuperSoldierDamage.serumRejection(level), 1.0e6f);
	}
}
