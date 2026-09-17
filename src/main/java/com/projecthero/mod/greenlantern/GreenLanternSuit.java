package com.projecthero.mod.greenlantern;

import com.projecthero.mod.greenlantern.data.GreenLanternState;
import com.projecthero.mod.greenlantern.item.GreenLanternSuitArmor;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import org.joml.Vector3f;

/**
 * Suit Up / Suit Down (V, tap). 0.8s animation, 10 charge on activation (v0.11.5, was 100), plus 1
 * charge every 5 seconds while worn (ticked in {@code GreenLanternAbilityManager#serverTick}) -- the
 * suit is no longer free to keep on. A 0.25s debounce stops a double-tap from immediately reversing the
 * animation, and suit-down is refused while battery-recharging (Phase 4).
 *
 * <p>v0.11.4: a ring of green hard-light particles travels up the body while suiting up (and back down
 * while suiting down), read each tick straight off {@link GreenLanternState#suitAnimStartTick} rather
 * than a separate counter, so it can't drift out of sync with the animation it's decorating.
 */
public final class GreenLanternSuit {
	/** Lantern-Corps green, matching every other hard-light effect's dust colour in this power. */
	private static final ParticleOptions SUIT_DUST = new DustParticleOptions(new Vector3f(0.208f, 0.941f, 0.459f), 1.3f);
	private static final int RING_POINTS = 8;
	private static final double RING_RADIUS = 0.4;

	private GreenLanternSuit() {
	}

	public static void toggle(ServerPlayer player) {
		GreenLanternState s = GreenLantern.state(player);
		long now = player.level().getGameTime();

		if (s.suitAnimDir != GreenLanternState.SUIT_IDLE) {
			return; // mid-animation -- debounce
		}
		if (now - s.suitAnimStartTick < GreenLanternConfig.SUIT_DOWN_DEBOUNCE_TICKS && s.suitAnimStartTick != 0L) {
			return;
		}

		if (s.suited) {
			if (GreenLanternBattery.isRecitingOath(player)) {
				GreenLanternEnergy.feedback(player, "message.projecthero.green_lantern.cannot_suit_down_recharging");
				return;
			}
			beginTransition(player, GreenLanternState.SUIT_SUITING_DOWN);
			return;
		}

		if (!GreenLanternEnergy.spend(player, GreenLanternConfig.SUIT_UP_COST)) {
			GreenLanternEnergy.feedback(player, "message.projecthero.ability.low_charge");
			return;
		}
		GreenLanternBattery.onAbilityUsed(player);
		beginTransition(player, GreenLanternState.SUIT_SUITING_UP);
	}

	/**
	 * Retracts the suit immediately when its 5-second upkeep charge can't be paid (v0.11.5) -- unlike
	 * {@link #toggle}, this bypasses the mid-animation debounce and the oath-recharging refusal, since
	 * an unpayable debt must not be able to keep the suit on indefinitely.
	 */
	public static void forceSuitDown(ServerPlayer player) {
		GreenLanternState s = GreenLantern.state(player);
		if (!s.suited || s.suitAnimDir == GreenLanternState.SUIT_SUITING_DOWN) {
			return;
		}
		beginTransition(player, GreenLanternState.SUIT_SUITING_DOWN);
		GreenLanternEnergy.feedback(player, "message.projecthero.green_lantern.suit_upkeep_depleted");
	}

	private static void beginTransition(ServerPlayer player, int dir) {
		GreenLanternState c = GreenLantern.state(player).copy();
		c.suitAnimDir = dir;
		c.suitAnimStartTick = player.level().getGameTime();
		GreenLantern.save(player, c);
		ServerLevel level = player.serverLevel();
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				dir == GreenLanternState.SUIT_SUITING_UP ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE,
				SoundSource.PLAYERS, 0.5f, 1.6f);
	}

	/** Per-player server tick -- progresses the suit-up/suit-down animation. */
	public static void tick(ServerPlayer player) {
		GreenLanternState s = GreenLantern.state(player);
		if (s.suitAnimDir == GreenLanternState.SUIT_IDLE) {
			return;
		}
		long elapsed = player.level().getGameTime() - s.suitAnimStartTick;
		emitSuitRing(player, s.suitAnimDir, elapsed);
		if (elapsed < GreenLanternConfig.SUIT_UP_TICKS) {
			return;
		}
		boolean suitingUp = s.suitAnimDir == GreenLanternState.SUIT_SUITING_UP;
		GreenLanternState c = s.copy();
		c.suited = suitingUp;
		c.suitAnimDir = GreenLanternState.SUIT_IDLE;
		GreenLantern.save(player, c);
		if (suitingUp) {
			GreenLanternSuitArmor.equip(player);
		} else {
			GreenLanternSuitArmor.strip(player);
			// Flight/shield no longer belong to the suit (the ring's powers work unsuited too), so
			// suiting down must not touch either -- forcing flight to stop here used to be safe only
			// because flight required the suit in the first place; now it would end a legitimate
			// unsuited flight with no controlled-descent grace (GreenLanternDamage's Emergency Catch is
			// itself suit-gated), an instant plummet from whatever height the player suited down at.
		}
		player.displayClientMessage(Component.translatable(suitingUp
				? "message.projecthero.green_lantern.suited_up" : "message.projecthero.green_lantern.suited_down"), true);
	}

	/**
	 * A ring of green particles at {@code player}'s feet climbing to head height over the suit-up
	 * animation (and the mirror image sinking back down on suit-down), so the hard-light suit reads as
	 * materialising/dissolving up the body rather than just popping on.
	 */
	private static void emitSuitRing(ServerPlayer player, int dir, long elapsed) {
		float progress = Mth.clamp(elapsed / (float) GreenLanternConfig.SUIT_UP_TICKS, 0f, 1f);
		float ringHeight = dir == GreenLanternState.SUIT_SUITING_UP ? progress : 1f - progress;
		ServerLevel level = player.serverLevel();
		double y = player.getY() + ringHeight * player.getBbHeight();
		for (int i = 0; i < RING_POINTS; i++) {
			double angle = (2 * Math.PI * i) / RING_POINTS;
			double x = player.getX() + Math.cos(angle) * RING_RADIUS;
			double z = player.getZ() + Math.sin(angle) * RING_RADIUS;
			level.sendParticles(SUIT_DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}
}
