package com.projecthero.mod.ironman;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.item.RepulsorItem;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

/**
 * "changes 22": flight from a bare {@link RepulsorItem} strapped to the boots slot -- no suit, no
 * Tony Stark power, no blueprint. The single Repulsor component is a working thruster, and wearing a
 * pair of them lets anyone fly.
 *
 * <p>Deliberately a <b>separate system</b> from {@link IronManFlight} rather than another branch
 * inside it. Suit flight is defined entirely in terms of a worn {@code IronManSuit} -- it reads the
 * mark's speed, acceleration, drain multiplier, altitude ceiling and manual-flight flag, spends suit
 * energy every tick, and cuts out on integrity failure or a systems lockout. None of that exists for
 * a pair of boots, and threading "or maybe there is no suit" through every one of those checks would
 * have made the suit path harder to read for the sake of a much simpler feature. The two share a
 * gesture (double-tap jump) and nothing else; they use separate attachments so neither can end the
 * other's flight.
 *
 * <h2>Speed</h2>
 * Exactly <b>half the Mark 2's</b>: the Mark 2 flies at {@code flight(1.0f, 0.08f)}, so the boots use
 * {@value #FLIGHT_SPEED} / {@value #FLIGHT_ACCELERATION} and cap horizontal speed at
 * {@value #MAX_SPEED_MPS} m/s. v0.14.16: both now fly through the client's directional flight
 * ({@code flight.DirectionalFlightModel#ironMan} / {@code #repulsorBoots}), so the two feel like the same thruster at
 * different power levels.
 *
 * <h2>Wearing a real suit wins</h2>
 * If a genuine Iron Man boot is on, this system stands down entirely -- the suit's own flight is
 * strictly better and owns the gesture.
 */
public final class RepulsorBoots {
	/** Half of the Mark 2's {@code flightSpeed} (1.0). */
	public static final float FLIGHT_SPEED = 0.5f;
	/** Half of the Mark 2's {@code flightAcceleration} (0.08). */
	public static final float FLIGHT_ACCELERATION = 0.04f;
	/**
	 * The Mark 2 has no explicit speed cap, so "50% of the Mark 2" is expressed here as an explicit
	 * ceiling at half of the 30 m/s the fastest marks are clamped to. Without one, the forward assist
	 * compounds and a pair of boots ends up outrunning the suits.
	 */
	public static final double MAX_SPEED_MPS = 15.0;

	private RepulsorBoots() {
	}

	/** True if the player has a bare Repulsor in the boots slot (and no real Iron Man boot). */
	public static boolean worn(Player player) {
		return player.getItemBySlot(EquipmentSlot.FEET).is(IronManItems.REPULSOR);
	}

	public static boolean isFlying(ServerPlayer player) {
		return player.getAttachedOrElse(ModAttachments.REPULSOR_BOOTS_FLYING, false);
	}

	/**
	 * The double-tap-jump gesture, once {@link IronManFlight} has declined it. Returns true if the
	 * boots handled the input.
	 */
	public static boolean toggle(ServerPlayer player) {
		if (isFlying(player)) {
			setFlying(player, false);
			return true;
		}
		if (!worn(player) || player.getAbilities().instabuild) {
			return false;
		}
		setFlying(player, true);
		return true;
	}

	public static void setFlying(ServerPlayer player, boolean flying) {
		player.setAttached(ModAttachments.REPULSOR_BOOTS_FLYING, flying);
		if (player.getAbilities().instabuild) {
			return;
		}
		player.getAbilities().mayfly = flying;
		player.getAbilities().flying = flying;
		player.onUpdateAbilities();
		player.resetFallDistance();
		// v0.14.21: the take-off / power-down sounds are client-side now (client.IronManFlightFxClient)
	}

	/** Per-tick while flying: end conditions (movement is the client's directional flight, the jets client FX). */
	public static void tick(ServerPlayer player) {
		if (!isFlying(player)) {
			return;
		}
		if (player.getAbilities().instabuild || !worn(player) || player.onGround()) {
			setFlying(player, false);
			return;
		}
		player.getAbilities().flying = true;
		// v0.14.16: the movement (and the MAX_SPEED_MPS ceiling) is the client's directional flight now --
		// flight.DirectionalFlightModel#repulsorBoots -- with W / S forward / back along the look like every other flight.
		// v0.14.21: the boot jets are drawn client-side at the real boot soles (client.IronManFlightFxClient)
	}

	/**
	 * Take the ability bit back if the boots came off (or a relog left it set) while nothing else owns
	 * flight. Mirrors {@link IronManFlight#clearStale} -- without it, unequipping mid-air leaves a
	 * player with permanent creative flight.
	 */
	public static void clearStale(ServerPlayer player) {
		if (isFlying(player) || player.getAbilities().instabuild || !player.getAbilities().mayfly) {
			return;
		}
		if (!player.getAttachedOrElse(ModAttachments.FLYING, false)
				&& !player.getAttachedOrElse(ModAttachments.HERO_FLYING, false)
				&& !player.getAttachedOrElse(ModAttachments.IRON_MAN_FLYING, false)) {
			player.getAbilities().mayfly = false;
			player.getAbilities().flying = false;
			player.onUpdateAbilities();
		}
	}
}
