package com.projecthero.mod.wolverine;

import com.projecthero.mod.attachment.ModAttachments;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

/**
 * v0.13.9: Wolverine's claw guard. Holding right-click with the claws out and both hands empty raises
 * both sets of claws crossed in front of him; while it's up, anything a shield could stop does
 * {@link WolverineConfig#BLOCK_DAMAGE_REDUCTION} less damage (applied in {@link WolverineDamage}), and
 * -- like a raised shield -- he can't swing (the client drops attack clicks while guarding).
 *
 * <p>The client sends {@code BLOCK_START}/{@code BLOCK_STOP} on the press edges; the server re-validates
 * and keeps the synced {@link ModAttachments#WOLVERINE_BLOCKING} flag every viewer poses from. The tick
 * lowers the guard the moment it stops being legal (claws retracted, something picked up, power lost).
 */
public final class WolverineBlock {
	private WolverineBlock() {
	}

	/** Whether a guard is allowed at all right now: the power, claws out, nothing in either hand. */
	public static boolean canBlock(Player player) {
		return Wolverine.clawsOut(player) && player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()
				&& !player.isSpectator();
	}

	/** Safe on the client too (the flag is synced) -- drives the pose; the server's copy decides damage. */
	public static boolean isBlocking(Player player) {
		return player.getAttachedOrElse(ModAttachments.WOLVERINE_BLOCKING, false) && canBlock(player);
	}

	/** Does the guard reduce this hit? Only what a shield could stop (not fall, fire, drowning, magic...). */
	static boolean blocks(ServerPlayer player, DamageSource source) {
		return isBlocking(player) && !source.is(DamageTypeTags.BYPASSES_SHIELD);
	}

	public static void start(ServerPlayer player) {
		if (!canBlock(player) || isBlocking(player)) {
			return;
		}
		player.setAttached(ModAttachments.WOLVERINE_BLOCKING, true);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(),
				SoundSource.PLAYERS, 0.8f, 1.4f);
	}

	public static void stop(ServerPlayer player) {
		if (player.getAttachedOrElse(ModAttachments.WOLVERINE_BLOCKING, false)) {
			player.setAttached(ModAttachments.WOLVERINE_BLOCKING, false);
		}
	}

	/** Every server tick for a Wolverine: a guard that stopped being legal comes down. */
	static void tick(ServerPlayer player) {
		if (player.getAttachedOrElse(ModAttachments.WOLVERINE_BLOCKING, false) && !canBlock(player)) {
			stop(player);
		}
	}

	/** A hit landed on the raised guard: the claws ring. */
	static void onBlockedHit(ServerPlayer player) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK,
				SoundSource.PLAYERS, 0.9f, 1.3f);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND,
				SoundSource.PLAYERS, 0.25f, 1.9f);
	}
}
