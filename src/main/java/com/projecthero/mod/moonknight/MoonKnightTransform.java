package com.projecthero.mod.moonknight;

import com.projecthero.mod.moonknight.ability.MoonKnightAbilityManager;
import com.projecthero.mod.moonknight.ability.MoonKnightAlters;
import com.projecthero.mod.moonknight.data.MoonKnightAction;
import com.projecthero.mod.moonknight.data.MoonKnightState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * H: the suit. Pressing it (with the pact) starts a 1.5 s transformation: the suit goes into the armour slots at once
 * (whatever armour they were wearing is stowed, and handed back exactly as it was when they press H again) and
 * materialises on the player <b>one pixel at a time</b> over the 1.5 s (the client draws a pixel-dissolve copy of the
 * suit texture matching the clock -- see {@code SuperheroArmorRenderer#getRenderType}), while white bandages spiral up
 * the body and the player can't be hurt. H again dissolves it away pixel by pixel before it is stripped.
 */
public final class MoonKnightTransform {
	private static final DustParticleOptions BANDAGE = new DustParticleOptions(new org.joml.Vector3f(0.95f, 0.93f, 0.88f), 1.1f);
	private static final DustParticleOptions BANDAGE_SHADE = new DustParticleOptions(new org.joml.Vector3f(0.78f, 0.76f, 0.70f), 0.9f);

	private MoonKnightTransform() {
	}

	public static boolean isTransforming(ServerPlayer player) {
		return MoonKnightAnim.flag(player, MoonKnightAction.FLAG_TRANSFORMING);
	}

	/** True while the suit is still on the player but coming or going (it must not be audited away). */
	public static boolean inTransition(ServerPlayer player) {
		MoonKnightAction a = MoonKnightAnim.action(player);
		return a.has(MoonKnightAction.FLAG_TRANSFORMING) || a.has(MoonKnightAction.FLAG_UNTRANSFORMING);
	}

	/** The H key (server side, re-validated). */
	public static void toggle(ServerPlayer player) {
		MoonKnightState s = MoonKnight.state(player);
		if (!s.hasPact) {
			return;
		}
		long now = player.level().getGameTime();
		if (inTransition(player) || now - s.transformStart < MoonKnightConfig.TOGGLE_DEBOUNCE_TICKS) {
			return;
		}
		if (s.transformed) {
			suitDown(player, true);
			return;
		}
		// the suit goes on now, invisible -- the client reveals it pixel by pixel over the transformation clock
		MoonKnightSuit.suitUp(player);
		MoonKnightState c = MoonKnight.state(player).copy();
		c.transformStart = now;
		MoonKnight.saveState(player, c);
		MoonKnightAnim.setFlag(player, MoonKnightAction.FLAG_TRANSFORMING, true);
		MoonKnightAnim.play(player, MoonKnightAnim.TRANSFORM);
		ServerLevel level = player.serverLevel();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(),
				SoundSource.PLAYERS, 1.0f, 0.7f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT,
				SoundSource.PLAYERS, 0.6f, 1.6f);
	}

	/** Instantly on (tests / commands): skip the 1.5 s wrap. */
	public static void suitUpNow(ServerPlayer player) {
		MoonKnightState s = MoonKnight.state(player);
		if (!s.hasPact || s.transformed) {
			return;
		}
		finish(player);
	}

	/**
	 * Take the suit off. {@code animate} (the H key) dissolves it away pixel by pixel over
	 * {@link MoonKnightConfig#UNTRANSFORM_TICKS} before it is stripped and the stowed armour handed back; otherwise
	 * (death, revoke, commands) it comes off at once.
	 */
	public static void suitDown(ServerPlayer player, boolean animate) {
		MoonKnightState s = MoonKnight.state(player);
		MoonKnightAbilityManager.onUntransform(player);
		if (!s.transformed && !MoonKnightSuit.wearing(player)) {
			MoonKnightAnim.setFlag(player, MoonKnightAction.FLAG_TRANSFORMING, false);
			MoonKnightAnim.setFlag(player, MoonKnightAction.FLAG_UNTRANSFORMING, false);
			return;
		}
		MoonKnightState c = MoonKnight.state(player).copy();
		c.transformed = false;
		c.transformStart = player.level().getGameTime();
		MoonKnight.saveState(player, c);
		MoonKnightAlters.reconcile(player);
		// every combat flag drops with the suit (abilities are off from this moment)
		MoonKnightAction a = MoonKnightAnim.action(player).copy();
		a.flags = animate ? MoonKnightAction.FLAG_UNTRANSFORMING : 0;
		a.lineStart = -1L;
		a.lineTargetId = -1;
		MoonKnightAnim.save(player, a);
		if (!animate) {
			MoonKnightSuit.suitDown(player);
		}
		if (animate) {
			MoonKnightAnim.play(player, MoonKnightAnim.UNTRANSFORM);
			ServerLevel level = player.serverLevel();
			for (int i = 0; i < 40; i++) {
				double y = player.getY() + 2.0 - i * 0.05;
				double ang = i * 0.6;
				level.sendParticles(BANDAGE, player.getX() + Math.cos(ang) * 0.6, y, player.getZ() + Math.sin(ang) * 0.6,
						1, 0.05, 0.02, 0.05, 0.02);
			}
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(),
					SoundSource.PLAYERS, 0.8f, 1.3f);
		}
	}

	/** Per tick: advance a transformation in progress (or finish dissolving the suit away). */
	public static void tick(ServerPlayer player) {
		if (MoonKnightAnim.flag(player, MoonKnightAction.FLAG_UNTRANSFORMING)) {
			long gone = player.level().getGameTime() - MoonKnight.state(player).transformStart;
			if (gone >= MoonKnightConfig.UNTRANSFORM_TICKS || !MoonKnight.state(player).hasPact) {
				MoonKnightSuit.suitDown(player);
				MoonKnightAnim.setFlag(player, MoonKnightAction.FLAG_UNTRANSFORMING, false);
			}
			return;
		}
		if (!isTransforming(player)) {
			return;
		}
		MoonKnightState s = MoonKnight.state(player);
		if (!s.hasPact) {
			MoonKnightAnim.setFlag(player, MoonKnightAction.FLAG_TRANSFORMING, false);
			return;
		}
		long elapsed = player.level().getGameTime() - s.transformStart;
		ServerLevel level = player.serverLevel();
		// the bandages: two strips spiralling up the body, the band rising with the clock
		double frac = Math.min(1.0, elapsed / (double) MoonKnightConfig.TRANSFORM_TICKS);
		double h = player.getBbHeight();
		for (int strip = 0; strip < 2; strip++) {
			for (int k = 0; k < 4; k++) {
				double t = frac - k * 0.03;
				if (t < 0) {
					continue;
				}
				double ang = t * Math.PI * 7 + strip * Math.PI;
				double y = player.getY() + t * h * 1.05;
				double r = 0.45 + 0.08 * Math.sin(t * 20);
				level.sendParticles(k == 0 ? BANDAGE : BANDAGE_SHADE, player.getX() + Math.cos(ang) * r, y,
						player.getZ() + Math.sin(ang) * r, 1, 0.01, 0.01, 0.01, 0.0);
			}
		}
		if (elapsed % 6 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOOL_PLACE,
					SoundSource.PLAYERS, 0.5f, 0.8f + (float) frac * 0.8f);
		}
		if (elapsed >= MoonKnightConfig.TRANSFORM_TICKS) {
			finish(player);
		}
	}

	private static void finish(ServerPlayer player) {
		MoonKnightSuit.suitUp(player);
		MoonKnightState c = MoonKnight.state(player).copy();
		c.transformed = true;
		c.transformStart = player.level().getGameTime();
		MoonKnight.saveState(player, c);
		MoonKnightAnim.setFlag(player, MoonKnightAction.FLAG_TRANSFORMING, false);
		MoonKnightAlters.reconcile(player);
		ServerLevel level = player.serverLevel();
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
		level.sendParticles(BANDAGE, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.5, 0.9, 0.5, 0.1);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(),
				SoundSource.PLAYERS, 1.0f, 0.8f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE,
				SoundSource.PLAYERS, 1.0f, 0.6f);
		player.displayClientMessage(Component.translatable("message.projecthero.moon_knight.suited")
				.withStyle(ChatFormatting.WHITE), true);
	}
}
