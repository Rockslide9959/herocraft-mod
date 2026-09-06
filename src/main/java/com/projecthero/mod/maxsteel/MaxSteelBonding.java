package com.projecthero.mod.maxsteel;

import com.projecthero.mod.maxsteel.entity.SteelEntity;
import com.projecthero.mod.maxsteel.item.MaxSteelItems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * The Steel bonding flow. There are two valid routes and the player needs only one:
 * <ul>
 *   <li><b>Experience Level 30+</b> -- bonding consumes <b>5</b> levels (not all 30; the 30 is a
 *       compatibility threshold, not the price).</li>
 *   <li><b>A T.U.R.B.O. Stabilizer</b> anywhere in the inventory -- bonding consumes exactly one and
 *       there is no level requirement.</li>
 * </ul>
 *
 * <p>Everything is validated and the cost is taken atomically <em>before</em> the power is granted, so
 * there is nothing to refund and no half-bonded state. The ~4-second "cut-scene" is simply the
 * first-bond suit-up ({@link MaxSteelConfig#FIRST_BOND_TICKS}), which the transform state machine
 * already runs longer than an ordinary transformation.
 *
 * <p>Multiplayer-safe: {@link SteelEntity#claimBond} is a single-claim soft lock, so two players
 * cannot bond with the same Steel at once, and the Steel is discarded the instant a bond succeeds.
 */
public final class MaxSteelBonding {
	/** XP levels consumed by the Level-30 route. */
	public static final int LEVEL_COST = 5;
	/** Level required for the no-stabilizer route. */
	public static final int LEVEL_REQUIREMENT = 30;

	private MaxSteelBonding() {
	}

	public static void attempt(ServerPlayer player, SteelEntity steel) {
		if (MaxSteel.hasPower(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.max_steel.already_bonded")
					.withStyle(ChatFormatting.GRAY), true);
			return;
		}

		// Experimental mutations and Hero-Tier powers cannot be mixed: Steel rejects a mutated host.
		if (com.projecthero.mod.hero.HeroTiers.hasExperimental(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.max_steel.blocked_experimental")
					.withStyle(ChatFormatting.AQUA), true);
			return;
		}

		boolean hasStabilizer = findStabilizerSlot(player) >= 0;
		boolean hasLevels = player.experienceLevel >= LEVEL_REQUIREMENT;

		if (!hasStabilizer && !hasLevels) {
			refuse(player);
			return;
		}

		if (!steel.claimBond(player, MaxSteelConfig.FIRST_BOND_TICKS + 40)) {
			player.displayClientMessage(Component.translatable("message.projecthero.max_steel.steel_busy"), true);
			return;
		}

		// take the cost -- stabilizer first so a level-30 player carrying one keeps their XP
		if (hasStabilizer) {
			int slot = findStabilizerSlot(player);
			player.getInventory().getItem(slot).shrink(1);
		} else {
			player.giveExperienceLevels(-LEVEL_COST);
		}

		MaxSteel.bond(player);
		MaxSteelTransform.beginSuitUp(player, true);

		ServerLevel level = player.serverLevel();
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, steel.getX(), steel.getY() + 1.0, steel.getZ(),
				60, 0.3, 0.6, 0.3, 0.08);
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(),
				40, 0.4, 0.9, 0.4, 0.05);
		level.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0, 0, 0, 0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.4f);

		steel.discard();
	}

	private static void refuse(ServerPlayer player) {
		player.displayClientMessage(Component.translatable("message.projecthero.max_steel.steel_low_energy")
				.withStyle(ChatFormatting.AQUA), false);
		player.displayClientMessage(Component.translatable("message.projecthero.max_steel.bond_requirement")
				.withStyle(ChatFormatting.GRAY), false);
		ServerLevel level = player.serverLevel();
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.6f, 0.7f);
	}

	private static int findStabilizerSlot(ServerPlayer player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack s = player.getInventory().getItem(i);
			if (s.is(MaxSteelItems.TURBO_STABILIZER)) {
				return i;
			}
		}
		return -1;
	}
}
