package com.projecthero.mod.greenlantern;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.power.PowerToggles;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import org.joml.Vector3f;

/**
 * X (hold) -- "Green Lantern's Light!" (v0.11.7, replaces Ring Grapple). Hold X to recite the classic
 * Oath, reusing the same four lines and per-line cadence as the Power Battery's own recharge Oath (see
 * {@link GreenLanternBattery}) -- release before the fourth line finishes and the recitation is
 * cancelled outright, no cost, no cooldown. A completed recitation empowers the caster for
 * {@link GreenLanternConfig#OATH_MODE_DURATION_TICKS}: double melee (a transient attribute modifier
 * applied here), double ability damage ({@link GreenLanternCombat} reads {@link #multiplier}), and every
 * Ring Charge cost doubled ({@link GreenLanternEnergy#spend} calls {@link #multiplier} itself, so this
 * class does not need to touch every ability/construct call site) -- on top of its own flat
 * {@link GreenLanternConfig#OATH_MODE_UPKEEP_PER_SEC} drain (spent via {@link GreenLanternEnergy#spendRaw}
 * so that flat tax is not itself doubled by the multiplier it causes). Ends on its own timer or the
 * instant charge can no longer cover that flat drain, either way applying
 * {@link GreenLanternConfig#OATH_MODE_COOLDOWN_TICKS} once it is over.
 *
 * <p>Both {@link ModAttachments#GREEN_LANTERN_OATH_UNTIL} and
 * {@link ModAttachments#GREEN_LANTERN_OATH_RECITING_SINCE} are the live, synced source of truth --
 * exactly the pattern {@link GreenLanternShield}'s barrier HP already uses -- so no separate
 * server-only bookkeeping map is needed, and the HUD can read them directly client-side.
 */
public final class GreenLanternOath {
	private static final String OATH_MODE_CD = "oath_mode";
	private static final ResourceLocation MELEE_ID =
			com.projecthero.mod.ProjectHeroMod.id("green_lantern_oath_melee");
	private static final ParticleOptions GREEN_DUST = new DustParticleOptions(new Vector3f(0.208f, 0.941f, 0.459f), 1.6f);
	private static final String[] OATH_LINES = {
			"message.projecthero.green_lantern.oath.line1",
			"message.projecthero.green_lantern.oath.line2",
			"message.projecthero.green_lantern.oath.line3",
			"message.projecthero.green_lantern.oath.line4",
	};

	private GreenLanternOath() {
	}

	public static boolean isActive(ServerPlayer player) {
		return player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_OATH_UNTIL, 0L) > player.level().getGameTime();
	}

	public static boolean isReciting(ServerPlayer player) {
		return player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_OATH_RECITING_SINCE, 0L) > 0L;
	}

	/** {@link GreenLanternConfig#OATH_MODE_MULTIPLIER} while empowered, else 1 -- every Ring Charge spend
	 *  and every combat ability's damage reads this. */
	public static float multiplier(ServerPlayer player) {
		return isActive(player) ? GreenLanternConfig.OATH_MODE_MULTIPLIER : 1f;
	}

	public static int remainingTicks(ServerPlayer player) {
		return (int) Math.max(0L,
				player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_OATH_UNTIL, 0L) - player.level().getGameTime());
	}

	/** X pressed. */
	public static void onPress(ServerPlayer player) {
		if (isActive(player) || isReciting(player) || !GreenLantern.abilityReady(player, OATH_MODE_CD)) {
			return;
		}
		player.setAttached(ModAttachments.GREEN_LANTERN_OATH_RECITING_SINCE, player.level().getGameTime());
		player.displayClientMessage(Component.translatable(OATH_LINES[0]).withStyle(ChatFormatting.GREEN), true);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.4f, 1.8f);
	}

	/** X released -- cancels an in-progress (not-yet-complete) recitation outright, no penalty. */
	public static void onRelease(ServerPlayer player) {
		if (isReciting(player) && !isActive(player)) {
			player.setAttached(ModAttachments.GREEN_LANTERN_OATH_RECITING_SINCE, 0L);
			player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.oath_mode.cancelled"), true);
		}
	}

	/** Per-player server tick. */
	public static void tick(ServerPlayer player) {
		long now = player.level().getGameTime();
		if (isReciting(player)) {
			long since = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_OATH_RECITING_SINCE, 0L);
			long elapsed = now - since;
			if (elapsed >= GreenLanternConfig.OATH_MODE_RECITE_TICKS) {
				activate(player, now);
				return;
			}
			if (elapsed % GreenLanternConfig.OATH_MODE_LINE_TICKS == 0) {
				int line = (int) (elapsed / GreenLanternConfig.OATH_MODE_LINE_TICKS);
				if (line > 0 && line < OATH_LINES.length) {
					player.displayClientMessage(Component.translatable(OATH_LINES[line]).withStyle(ChatFormatting.GREEN), true);
					player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
							SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.4f, 1.8f);
				}
			}
			return;
		}
		if (!isActive(player)) {
			return;
		}
		ServerLevel level = player.serverLevel();
		// v0.11.8: a full-body aura, not a burst centred on the player's own eye line -- the old
		// player.getY()+1 spawn point sat almost exactly at the first-person camera and read as
		// particles in the player's face rather than an ambient glow (see AbilityHelpers#handPosition).
		double h = player.getBbHeight();
		level.sendParticles(GREEN_DUST, player.getX(), player.getY() + h * 0.5, player.getZ(), 6, 0.4, h * 0.5, 0.4, 0.02);
		if (now % 20 == 0 && !GreenLanternEnergy.spendRaw(player, GreenLanternConfig.OATH_MODE_UPKEEP_PER_SEC)) {
			end(player);
			return;
		}
		if (now >= player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_OATH_UNTIL, 0L)) {
			end(player);
		}
	}

	private static void activate(ServerPlayer player, long now) {
		player.setAttached(ModAttachments.GREEN_LANTERN_OATH_RECITING_SINCE, 0L);
		player.setAttached(ModAttachments.GREEN_LANTERN_OATH_UNTIL, now + GreenLanternConfig.OATH_MODE_DURATION_TICKS);
		PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, MELEE_ID, 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.oath_mode.activated")
				.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
		ServerLevel level = player.serverLevel();
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.0f);
		double h = player.getBbHeight();
		level.sendParticles(GREEN_DUST, player.getX(), player.getY() + h * 0.5, player.getZ(), 40, 0.5, h * 0.5, 0.5, 0.05);
	}

	/** Ends an active mode -- called on natural expiry, on failing to pay its own upkeep, or lifecycle cleanup. */
	private static void end(ServerPlayer player) {
		boolean wasActive = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_OATH_UNTIL, 0L) > 0L;
		player.setAttached(ModAttachments.GREEN_LANTERN_OATH_UNTIL, 0L);
		player.setAttached(ModAttachments.GREEN_LANTERN_OATH_RECITING_SINCE, 0L);
		if (!wasActive) {
			return;
		}
		PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, MELEE_ID);
		GreenLantern.triggerCooldown(player, OATH_MODE_CD, GreenLanternConfig.OATH_MODE_COOLDOWN_TICKS);
		player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.oath_mode.ended"), true);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6f, 0.8f);
	}

	/** Lifecycle cleanup -- death/respawn/logout/dimension-change/power-loss must not leave the mode running. */
	public static void clearFor(ServerPlayer player) {
		if (player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_OATH_UNTIL, 0L) > 0L) {
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, MELEE_ID);
		}
		player.setAttached(ModAttachments.GREEN_LANTERN_OATH_UNTIL, 0L);
		player.setAttached(ModAttachments.GREEN_LANTERN_OATH_RECITING_SINCE, 0L);
	}
}
