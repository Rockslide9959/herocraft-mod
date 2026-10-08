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
 * <p>v0.11.4: a ring of green hard-light particles travels along the body while suiting up/down, read each
 * tick straight off {@link GreenLanternState#suitAnimStartTick} rather than a separate counter, so it can't
 * drift out of sync with the animation it's decorating.
 *
 * <p>v0.13.21: the suit now sweeps on from the shoulders down to the feet one pixel row at a time (and off again
 * from the feet back up), drawn client-side by {@code GreenLanternSuitReveal} off the same synced clock. For that to
 * be visible the armour pieces are put on at the START of a suit-up (the renderer hides the rows the sweep has not
 * reached yet) instead of popping on at the end; suit-down still takes them off at the end. The particle ring rides
 * the sweep's leading edge. The transition is 1.5s ({@link GreenLanternConfig#SUIT_UP_TICKS}, was 0.8s).
 *
 * <p>v0.15.15: the suit no longer sweeps down the body -- it spreads outward from the ring on the right hand (up the
 * ring arm, then over the body) while the ring blazes, and recedes back into the ring on suit-down. Four suit styles
 * ({@link GreenLanternSuitStyle}), picked on N ({@link #selectStyle}).
 */
public final class GreenLanternSuit {
	/** Lantern-Corps green, matching every other hard-light effect's dust colour in this power. */
	private static final ParticleOptions SUIT_DUST = new DustParticleOptions(new Vector3f(0.45f, 1.0f, 0.6f), 0.55f);
	/** v0.15.15: the bright burst at the ring while the suit pours out of it. */
	private static final ParticleOptions RING_FLARE = new DustParticleOptions(new Vector3f(0.75f, 1.0f, 0.8f), 0.9f);
	private static final int RING_POINTS = 8;

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
		// v0.13.21: on now, revealed row by row by the client over the transition (see the class javadoc)
		GreenLanternSuitArmor.equip(player);
	}

	/**
	 * v0.15.15: N suit screen pick. Stored at once (the next suit-up forms it); if the suit is already on and settled, the
	 * ring re-forms it in the new style straight away -- the suit-up sweep plays again out of the ring hand, free of charge
	 * (the armour stays on, so nothing about the suit's protection blinks). Returns whether the style changed.
	 */
	public static boolean selectStyle(ServerPlayer player, int ordinal) {
		if (!GreenLanternAbilityManager.hasContext(player) || ordinal < 0 || ordinal >= GreenLanternSuitStyle.values().length) {
			return false;
		}
		GreenLanternState s = GreenLantern.state(player);
		GreenLanternSuitStyle style = GreenLanternSuitStyle.byOrdinal(ordinal);
		if (s.suitStyle == ordinal) {
			player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.suit_style_same",
					Component.translatable(style.nameKey())), true);
			return false;
		}
		GreenLanternState c = s.copy();
		c.suitStyle = ordinal;
		boolean reform = c.suited && c.suitAnimDir == GreenLanternState.SUIT_IDLE;
		if (reform) {
			c.suitAnimDir = GreenLanternState.SUIT_SUITING_UP;
			c.suitAnimStartTick = player.level().getGameTime();
		}
		GreenLantern.save(player, c);
		if (reform) {
			player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE,
					SoundSource.PLAYERS, 0.5f, 1.6f);
		}
		player.displayClientMessage(Component.translatable(reform
				? "message.projecthero.green_lantern.suit_style_reform" : "message.projecthero.green_lantern.suit_style_set",
				Component.translatable(style.nameKey())), true);
		return true;
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
		if (elapsed % 2 == 0) {
			emitSuitRing(player, s.suitAnimDir, elapsed); // v0.13.21: every other tick, finer dust -- it used to bury the sweep
		}
		if (elapsed < GreenLanternConfig.SUIT_UP_TICKS) {
			return;
		}
		boolean suitingUp = s.suitAnimDir == GreenLanternState.SUIT_SUITING_UP;
		GreenLanternState c = s.copy();
		c.suited = suitingUp;
		c.suitAnimDir = GreenLanternState.SUIT_IDLE;
		GreenLantern.save(player, c);
		if (suitingUp) {
			GreenLanternSuitArmor.reequipMissing(player);
		} else {
			GreenLanternSuitArmor.strip(player);
			// Flight/shield no longer belong to the suit (the ring's powers work unsuited too), so
			// suiting down must not touch either -- forcing flight to stop here used to be safe only
			// because flight required the suit in the first place; now it would end a legitimate
			// unsuited flight with no controlled-descent grace, an instant plummet from whatever height
			// the player suited down at (though GreenLanternDamage's fall-damage immunity still covers
			// the landing itself as long as the ring has any charge left).
		}
		player.displayClientMessage(Component.translatable(suitingUp
				? "message.projecthero.green_lantern.suited_up" : "message.projecthero.green_lantern.suited_down"), true);
	}

	/**
	 * A ring of green particles riding the leading edge of the suit's pixel-row sweep, so the hard-light suit reads
	 * as materialising/dissolving along the body rather than just popping on. v0.13.21: shoulders -> feet on suit-up,
	 * feet -> shoulders on suit-down, matching the client-side reveal (it used to climb feet -> head on suit-up).
	 */
	private static void emitSuitRing(ServerPlayer player, int dir, long elapsed) {
		// v0.15.15: the suit pours out of the ring (and back into it) -- the ring flares, and a scatter of light rides the
		// sweep's front as it spreads over the body from the ring hand (GreenLanternSuitReveal draws the same front)
		float progress = Mth.clamp(elapsed / (float) GreenLanternConfig.SUIT_UP_TICKS, 0f, 1f);
		float coverage = dir == GreenLanternState.SUIT_SUITING_UP ? progress : 1f - progress;
		ServerLevel level = player.serverLevel();
		net.minecraft.world.phys.Vec3 ring = ringHand(player);
		// not to the Lantern themself: in first person the raised ring is right in front of the camera and the dust
		// buried the view (they see the ring blaze on their own hand instead)
		send(level, player, RING_FLARE, ring.x, ring.y, ring.z, 3, 0.05);
		// the farthest point of the suit from the raised ring is about 1.9 blocks away (the far foot)
		double front = coverage * 1.9;
		net.minecraft.util.RandomSource r = player.getRandom();
		int placed = 0;
		for (int tries = 0; tries < 60 && placed < RING_POINTS; tries++) {
			double x = player.getX() + (r.nextDouble() - 0.5) * player.getBbWidth() * 1.1;
			double y = player.getY() + r.nextDouble() * player.getBbHeight() * (24.5 / 32.0 + 0.2);
			double z = player.getZ() + (r.nextDouble() - 0.5) * player.getBbWidth() * 1.1;
			double d = ring.distanceTo(new net.minecraft.world.phys.Vec3(x, y, z));
			if (Math.abs(d - front) < 0.18) {
				send(level, player, SUIT_DUST, x, y, z, 1, 0.0);
				placed++;
			}
		}
	}

	private static void send(ServerLevel level, ServerPlayer owner, ParticleOptions particle, double x, double y, double z, int count,
			double spread) {
		for (ServerPlayer viewer : level.players()) {
			if (viewer != owner) {
				level.sendParticles(viewer, particle, false, x, y, z, count, spread, spread, spread, 0.0);
			}
		}
	}

	/**
	 * Where the ring is while the suit forms: the right fist raised out in front ({@code GreenLanternPose}'s suit-up pose),
	 * worked out from the body's facing -- right shoulder, then most of an arm's length forward and a little up.
	 */
	public static net.minecraft.world.phys.Vec3 ringHand(ServerPlayer player) {
		double yaw = Math.toRadians(player.yBodyRot);
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		// the player's right is (-fz, fx) rotated: right = (-cos, -sin)
		double rx = -Math.cos(yaw);
		double rz = -Math.sin(yaw);
		double scale = player.getBbHeight() / 1.8;
		return new net.minecraft.world.phys.Vec3(
				player.getX() + (fx * 0.58 + rx * 0.3) * scale,
				player.getY() + 1.4 * scale,
				player.getZ() + (fz * 0.58 + rz * 0.3) * scale);
	}
}
