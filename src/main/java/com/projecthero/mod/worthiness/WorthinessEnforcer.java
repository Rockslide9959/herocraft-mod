package com.projecthero.mod.worthiness;

import java.util.WeakHashMap;
import java.util.Map;

import com.projecthero.mod.entity.MjolnirEntity;
import com.projecthero.mod.item.ModItems;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Makes worthiness gating airtight (THOR_DESIGN.md section 2 + this phase's fixes), rather than
 * only checked at the point of use:
 * <ul>
 *   <li>{@link #serverTick} runs every tick for every player and ejects Mjolnir from an unworthy
 *   player's hands/inventory the instant it's found there, however it got there (command, chest,
 *   trade, another player, physics pickup, or their worthiness simply changing while holding it).</li>
 *   <li>The physical "walk over a dropped Mjolnir to pick it up" path is additionally short-circuited
 *   so it never even briefly enters an unworthy player's inventory in the first place -- for a
 *   resting {@code MjolnirEntity} (the common case; see {@link MjolnirEntity#createResting}) that's a
 *   worthiness check built into the entity's own pickup logic, and for the rarer cases where Mjolnir
 *   still ends up as a genuine vanilla {@code ItemEntity} (e.g. a death drop, or a dispenser) it's a
 *   mixin ({@code ItemEntityMixin}).</li>
 * </ul>
 * Creative-mode players bypass both checks (per user decision during this phase), mirroring how
 * flight already leaves creative/spectator players alone.
 */
public final class WorthinessEnforcer {
	private static final int FEEDBACK_THROTTLE_TICKS = 20;
	private static final Map<Player, Long> lastFeedbackTick = new WeakHashMap<>();

	private WorthinessEnforcer() {
	}

	/** Creative (and by extension spectator, which can't hold items anyway) players skip worthiness entirely. */
	public static boolean bypassesWorthiness(Player player) {
		return player.getAbilities().instabuild;
	}

	public static void serverTick(ServerPlayer player) {
		if (bypassesWorthiness(player) || Worthiness.isWorthy(player)) {
			return;
		}

		Inventory inventory = player.getInventory();
		boolean ejected = ejectAll(player, inventory.items);
		ejected |= ejectAll(player, inventory.offhand);

		if (ejected) {
			playRejectionFeedback(player, player.position());
		}
	}

	private static boolean ejectAll(ServerPlayer player, NonNullList<ItemStack> slots) {
		boolean any = false;
		for (int i = 0; i < slots.size(); i++) {
			ItemStack stack = slots.get(i);
			if (stack.is(ModItems.MJOLNIR)) {
				slots.set(i, ItemStack.EMPTY);
				dropAtFeet(player, stack);
				any = true;
			}
		}
		return any;
	}

	private static void dropAtFeet(ServerPlayer player, ItemStack stack) {
		// Per THOR_DESIGN.md section 3, Mjolnir should always remain a tracked MjolnirEntity on the
		// ground rather than reverting to a plain ItemEntity -- so it's still summon-able (by its
		// bound owner, if any) after being ejected here, exactly like a hammer dropped via the drop
		// key. MjolnirEntity#tryPickup's own worthiness check is what stops the same unworthy player
		// immediately re-triggering a pickup while standing on top of it.
		Vec3 pos = player.position();
		MjolnirEntity entity = MjolnirEntity.createResting(player.level(), player, stack, pos, Vec3.ZERO);
		player.level().addFreshEntity(entity);
	}

	/** Throttled "it won't budge" feedback -- shared by the continuous eject and the pickup-cancel mixin. */
	public static void playRejectionFeedback(Player player, Vec3 at) {
		long now = player.level().getGameTime();
		Long last = lastFeedbackTick.get(player);
		if (last != null && now - last < FEEDBACK_THROTTLE_TICKS) {
			return;
		}
		lastFeedbackTick.put(player, now);

		if (player.level() instanceof ServerLevel serverLevel) {
			serverLevel.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6f, 0.6f);
			serverLevel.sendParticles(ParticleTypes.SMOKE, at.x, at.y + 0.3, at.z, 12, 0.25, 0.2, 0.25, 0.02);
		}
	}
}
