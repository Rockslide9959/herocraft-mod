package com.herocraft.mod.punisher;

import com.herocraft.mod.punisher.data.PunisherState;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.raid.Raider;

/**
 * The route into the Punisher power: no potion, no accident -- a trained human works through a set
 * of objectives (spec sections 31-32). Started by using the Vigilante Training Manual found in an
 * Abandoned Vigilante Safehouse (added in Phase 4); the objectives themselves and the grant on
 * completion live here.
 *
 * <p>Objectives: defeat 25 hostiles, 10 of them at range, land 5 firearm headshots, craft a firearm,
 * and defeat a Pillager Captain (a patrol leader). When all are done the player permanently gains
 * the Punisher Hero-Tier power through {@link Punisher#grant}.
 */
public final class VigilanteTraining {
	private VigilanteTraining() {
	}

	/** Begin training. Refused if the player already has a power of either family, or is training. */
	public static boolean begin(ServerPlayer player) {
		if (Punisher.hasPower(player)) {
			player.displayClientMessage(Component.translatable("message.herocraft.punisher.already")
					.withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (com.herocraft.mod.hero.HeroTiers.hasHeroTier(player)
				|| com.herocraft.mod.hero.HeroTiers.hasExperimental(player)) {
			player.displayClientMessage(Component.translatable("message.herocraft.punisher.blocked_power")
					.withStyle(ChatFormatting.RED), false);
			return false;
		}
		PunisherState s = Punisher.state(player);
		if (s.trainingActive) {
			player.displayClientMessage(Component.translatable("message.herocraft.punisher.training_active")
					.withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		PunisherState c = s.copy();
		c.trainingActive = true;
		c.killCount = 0;
		c.rangedKillCount = 0;
		c.headshotCount = 0;
		c.craftedFirearm = false;
		c.defeatedCaptain = false;
		Punisher.save(player, c);
		player.displayClientMessage(Component.translatable("message.herocraft.punisher.training_begin")
				.withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), false);
		player.displayClientMessage(objectivesSummary(c).withStyle(ChatFormatting.GRAY), false);
		return true;
	}

	/** Give up training (from the "Your Power" screen). Progress is wiped; the Manual is not returned. */
	public static void abandon(ServerPlayer player) {
		PunisherState s = Punisher.state(player);
		if (!s.trainingActive) {
			return;
		}
		PunisherState c = s.copy();
		c.trainingActive = false;
		c.killCount = 0;
		c.rangedKillCount = 0;
		c.headshotCount = 0;
		c.craftedFirearm = false;
		c.defeatedCaptain = false;
		Punisher.save(player, c);
		player.displayClientMessage(Component.translatable("message.herocraft.punisher.training_abandoned")
				.withStyle(ChatFormatting.GRAY), false);
	}

	public static void onKill(ServerPlayer player, LivingEntity victim, DamageSource source) {
		PunisherState s = Punisher.state(player);
		if (!s.trainingActive) {
			return;
		}
		PunisherState c = s.copy();
		boolean changed = false;
		if (victim instanceof Enemy) {
			c.killCount++;
			changed = true;
		}
		if (source.is(DamageTypeTags.IS_PROJECTILE) && victim instanceof Enemy) {
			c.rangedKillCount++;
			changed = true;
		}
		if (victim instanceof Raider raider && raider.isPatrolLeader() && !c.defeatedCaptain) {
			c.defeatedCaptain = true;
			changed = true;
		}
		if (changed) {
			Punisher.save(player, c);
			progress(player, c);
			checkComplete(player, c);
		}
	}

	public static void onFirearmKill(ServerPlayer player, LivingEntity victim) {
		PunisherState s = Punisher.state(player);
		if (!s.trainingActive || !(victim instanceof Enemy)) {
			return;
		}
		PunisherState c = s.copy();
		c.rangedKillCount++;
		Punisher.save(player, c);
		progress(player, c);
		checkComplete(player, c);
	}

	public static void onHeadshot(ServerPlayer player) {
		PunisherState s = Punisher.state(player);
		if (!s.trainingActive) {
			return;
		}
		PunisherState c = s.copy();
		c.headshotCount++;
		Punisher.save(player, c);
		progress(player, c);
		checkComplete(player, c);
	}

	public static void onCraftFirearm(ServerPlayer player) {
		PunisherState s = Punisher.state(player);
		if (!s.trainingActive || s.craftedFirearm) {
			return;
		}
		PunisherState c = s.copy();
		c.craftedFirearm = true;
		Punisher.save(player, c);
		progress(player, c);
		checkComplete(player, c);
	}

	/** Action-bar tick showing the current objective totals, so progress is visible without menus. */
	private static void progress(ServerPlayer player, PunisherState s) {
		player.displayClientMessage(objectivesSummary(s).withStyle(ChatFormatting.GRAY), true);
	}

	private static void checkComplete(ServerPlayer player, PunisherState s) {
		if (!s.trainingDone()) {
			return;
		}
		PunisherState c = s.copy();
		c.trainingActive = false;
		Punisher.save(player, c);
		ServerLevel level = player.serverLevel();
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1.0f);
		Punisher.grant(player);
	}

	/** For the HUD progress block while training is active. */
	public static net.minecraft.network.chat.MutableComponent objectivesSummary(PunisherState s) {
		return Component.translatable("message.herocraft.punisher.objectives",
				Math.min(s.killCount, PunisherConfig.TRAIN_KILLS), PunisherConfig.TRAIN_KILLS,
				Math.min(s.rangedKillCount, PunisherConfig.TRAIN_RANGED_KILLS), PunisherConfig.TRAIN_RANGED_KILLS,
				Math.min(s.headshotCount, PunisherConfig.TRAIN_HEADSHOTS), PunisherConfig.TRAIN_HEADSHOTS,
				s.craftedFirearm ? "[x]" : "[ ]",
				s.defeatedCaptain ? "[x]" : "[ ]");
	}
}
