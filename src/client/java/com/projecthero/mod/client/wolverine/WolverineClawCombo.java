package com.projecthero.mod.client.wolverine;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.1: the claw melee combo, client side -- right slash, left slash, then an overhead cross with both claws, and
 * round again. Every client works the step out from the swings it is told about (vanilla's swing packet carries the
 * hand), so no extra network traffic: an off-hand swing is always the second slash; a main-hand swing straight after
 * it (within {@link #WINDOW} ticks) is the cross finisher; any other main-hand swing starts the combo over. The local
 * player's left click picks the hand that continues the chain ({@code WolverineAttackMixin}). The hit itself is
 * vanilla's, so the damage is the same on every step.
 */
public final class WolverineClawCombo {
	/** Ticks after a swing in which the next one still continues the combo. */
	public static final int WINDOW = 20;
	public static final int STEP_FIRST = 0;
	public static final int STEP_SECOND = 1;
	public static final int STEP_CROSS = 2;

	/** One swing: the game tick it started, its combo step, and the hand it used. */
	public record Swing(long tick, int step, InteractionHand hand) {
	}

	private static final Map<Integer, Swing> LAST = new HashMap<>();

	private WolverineClawCombo() {
	}

	/** A swing just started on this client (called from {@code WolverineSwingMixin}). */
	public static void onSwingStart(LivingEntity entity, InteractionHand hand) {
		if (!(entity instanceof AbstractClientPlayer player) || !WolverineClawsModel.visible(player, 1.0f)) {
			return;
		}
		long now = entity.level().getGameTime();
		Swing prev = LAST.get(entity.getId());
		boolean chained = prev != null && now - prev.tick() <= WINDOW && now >= prev.tick();
		if (chained && now - prev.tick() < WolverineClawPose.commitTicks(prev.step())) {
			return; // a strike is never cut off before it lands (held-down mining re-swings every 3 ticks)
		}
		int step = hand == InteractionHand.OFF_HAND ? STEP_SECOND
				: chained && prev.step() == STEP_SECOND ? STEP_CROSS : STEP_FIRST;
		if (LAST.size() > 64) {
			LAST.values().removeIf(s -> now - s.tick() > 200 || now < s.tick());
		}
		LAST.put(entity.getId(), new Swing(now, step, hand));
	}

	/** The hand the local player's next left click should swing to continue the chain. */
	public static InteractionHand nextHand(Player player) {
		Swing prev = LAST.get(player.getId());
		long now = player.level().getGameTime();
		boolean chained = prev != null && now - prev.tick() <= WINDOW && now >= prev.tick();
		return chained && prev.step() == STEP_FIRST ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
	}

	/** This player's latest claw swing, or null. */
	public static Swing last(Player player) {
		return LAST.get(player.getId());
	}

	public static void clear() {
		LAST.clear();
	}
}
