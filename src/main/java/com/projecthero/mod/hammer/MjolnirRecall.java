package com.projecthero.mod.hammer;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.entity.MjolnirEntity;
import com.projecthero.mod.item.ModDataComponents;
import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.power.ThorFeedback;
import com.projecthero.mod.worthiness.Worthiness;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * "Call Mjolnir" -- resolving where the player's hammer actually is and starting it home.
 *
 * <h2>Resolution order</h2>
 * Deliberately cheapest-and-most-certain first, and it never creates a hammer while an existing one
 * could still be found:
 * <ol>
 *   <li><b>The caller's own inventory.</b> Nothing to do.</li>
 *   <li><b>A loaded entity.</b> Looked up by the recorded entity UUID (an O(1) lookup that only
 *   succeeds for loaded entities), then by a bounded search around the player as a fallback for
 *   hammers that predate the registry. The real hammer is recalled -- no new one is made.</li>
 *   <li><b>Another (online) player's hand or inventory.</b> Worthiness gates who can physically
 *   <em>pick up</em> a hammer, but not who it is bound to -- see
 *   {@link com.projecthero.mod.entity.MjolnirEntity#tryPickup} -- so a worthy player who is not the
 *   owner can be temporarily wielding it when the owner calls. Ownership wins: the exact stack is
 *   removed from wherever it is (main hand, off hand, or the backpack) and starts flying to its
 *   real owner, same-dimension holders get the "it visibly leaves them" treatment via
 *   {@link #flyInFromHolder}, and this can never duplicate the item since it is a single {@code
 *   ItemStack} reference moved, not copied, out of the holder's inventory.</li>
 *   <li><b>Reconstruction.</b> Only once the above have all failed -- i.e. the hammer is genuinely
 *   unreachable (unloaded chunk, another dimension with nobody holding it, offline holder, a chest
 *   in a chunk nobody has loaded). The registry {@linkplain MjolnirRegistry#reconstruct bumps the
 *   generation}, which retires every older physical copy on sight -- including one sitting in an
 *   <em>offline</em> player's saved inventory, which is exactly why this has to be safe: that stack
 *   comes back stamped with the now-stale generation, and {@code MjolnirItem#inventoryTick} deletes
 *   it the moment its owner reconnects and it next ticks, so the reconnect can never produce a
 *   duplicate. A fresh entity flies in from a distance.</li>
 * </ol>
 *
 * <p>Nothing here force-loads a chunk, and nothing here scans entities in dimensions the player is
 * not in; step 3 only ever looks at <em>online</em> players, which is what makes the offline case
 * above fall safely through to reconstruction rather than reaching into someone's saved data.
 */
public final class MjolnirRecall {
	/**
	 * How far around the player the fallback entity sweep looks -- only reached for a hammer with no
	 * registry entity-id record (predates the registry, or the record's dimension didn't match).
	 * Every hammer created through the current system (thrown, dropped, or a natural crater's) is
	 * registered with an exact O(1) entity lookup on its first tick, so this sweep is a legacy path,
	 * not the common case; kept modest rather than world-spanning now that natural craters mean many
	 * more MjolnirEntity instances can exist in a loaded world simultaneously.
	 */
	private static final double LOADED_SEARCH_RADIUS = 64.0;
	/** Beyond this, the "answers your call" (rather than "is returning") line is used. */
	private static final double FAR_DISTANCE = 64.0;

	/** How far out a reconstructed hammer starts, so it visibly flies in rather than popping in. */
	private static final double ARRIVAL_DISTANCE = 26.0;
	/** How high above the player it comes in from -- it should arrive out of the sky. */
	private static final double ARRIVAL_HEIGHT = 12.0;

	private MjolnirRecall() {
	}

	/**
	 * @return true if a hammer is now on its way (or already in hand), false if the call went
	 *         unanswered. The caller does not need to report anything -- every branch below has
	 *         already given the player feedback.
	 */
	public static boolean call(ServerPlayer player) {
		if (!Worthiness.isWorthy(player)) {
			ThorFeedback.recallBlocked(player);
			return false;
		}

		ServerLevel level = player.serverLevel();
		MjolnirRegistry registry = MjolnirRegistry.get(level);
		UUID hammerId = player.getAttachedOrElse(ModAttachments.BOUND_HAMMER_ID, null);

		// 1. Already ours.
		if (findInInventory(player, hammerId) >= 0) {
			ThorFeedback.recallAlreadyHeld(player);
			return true;
		}

		// 2. A loaded entity.
		MjolnirEntity loaded = findLoadedEntity(player, registry, hammerId);
		if (loaded != null) {
			double distance = loaded.distanceTo(player);
			loaded.recall(player);
			if (distance > FAR_DISTANCE) {
				ThorFeedback.recallStartedFar(player);
			} else {
				ThorFeedback.recallStartedNear(player);
			}
			return true;
		}

		// Everything past here needs a known identity: without one there is no record to work from
		// and no way to tell one hammer from another.
		if (hammerId == null) {
			ThorFeedback.recallNoHammer(player);
			return false;
		}

		Optional<HammerRecord> maybeRecord = registry.record(hammerId);
		if (maybeRecord.isEmpty() || !maybeRecord.get().isOwnedBy(player.getUUID())) {
			ThorFeedback.recallNoHammer(player);
			return false;
		}
		HammerRecord record = maybeRecord.get();

		// 3. In somebody else's hand or inventory.
		StolenHammer stolen = takeFromOtherPlayers(player, hammerId);
		if (stolen != null) {
			flyInFromHolder(player, stolen.holder(), stolen.stack());
			ThorFeedback.hammerTakenByOwner(stolen.holder());
			ThorFeedback.recallStartedFar(player);
			return true;
		}

		// 4. Unreachable -- reconstruct, retiring every older copy. reconstruct() has already blanked
		// the record's whereabouts, and the new entity records its own on its first tick.
		ItemStack rebuilt = rebuildStack(record, registry.reconstruct(hammerId));
		flyIn(player, rebuilt);
		ThorFeedback.recallStartedFar(player);
		return true;
	}

	// ---------------- resolution steps ----------------

	/** @return the inventory slot the caller's own hammer is in, or -1. */
	private static int findInInventory(ServerPlayer player, UUID hammerId) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (matches(stack, player.getUUID(), hammerId)) {
				return slot;
			}
		}
		return -1;
	}

	/**
	 * The recorded entity first (exact, and cheap -- {@code getEntity(UUID)} is a map lookup that
	 * simply misses for anything unloaded), then a bounded sweep of the player's own level for
	 * hammers that have no record yet.
	 */
	private static MjolnirEntity findLoadedEntity(ServerPlayer player, MjolnirRegistry registry, UUID hammerId) {
		if (hammerId != null) {
			Optional<HammerRecord> record = registry.record(hammerId);
			if (record.isPresent() && record.get().entityId().isPresent()) {
				ServerLevel recordLevel = player.getServer().getLevel(record.get().dimension());
				if (recordLevel != null
						&& recordLevel.getEntity(record.get().entityId().get()) instanceof MjolnirEntity hammer
						&& !hammer.isRemoved()
						&& hammer.level() == player.level()) {
					return hammer;
				}
			}
		}

		return player.serverLevel().getEntitiesOfClass(MjolnirEntity.class,
						player.getBoundingBox().inflate(LOADED_SEARCH_RADIUS),
						entity -> !entity.isRemoved() && answersTo(entity, player, hammerId)).stream()
				.min(Comparator.comparingDouble(entity -> entity.distanceToSqr(player)))
				.orElse(null);
	}

	/**
	 * A hammer explicitly bound to this player always answers, regardless of who last threw it. An
	 * unbound hammer falls back to the transient per-entity {@code Projectile} owner (whoever most
	 * recently threw or dropped that specific instance). One with neither -- e.g. a death drop,
	 * which vanilla spawns with no thrower -- answers any worthy caller, so it can never become
	 * permanently uncallable.
	 *
	 * <p>Crucially, a hammer bound to <em>someone else</em> matches none of these: one player's
	 * call can never pull another player's Mjolnir.
	 */
	private static boolean answersTo(MjolnirEntity entity, ServerPlayer player, UUID hammerId) {
		ItemStack stack = entity.getItem();
		UUID boundOwner = stack.get(ModDataComponents.BOUND_OWNER);
		if (boundOwner != null) {
			return boundOwner.equals(player.getUUID());
		}
		if (hammerId != null && hammerId.equals(stack.get(ModDataComponents.HAMMER_ID))) {
			return true;
		}
		Entity owner = entity.getOwner();
		return owner == null || owner == player;
	}

	/** Who a stolen-back hammer was taken from, and the exact stack that was removed. */
	private record StolenHammer(ServerPlayer holder, ItemStack stack) {
	}

	/**
	 * Pulls the player's bound hammer out of whoever else is currently holding it -- hands checked
	 * first (so a hammer actively in someone's grip visibly empties that hand, per the "it leaves
	 * them" requirement, rather than being found by the general inventory scan below and looking
	 * like it silently vanished from their hotbar), then the rest of their inventory. Only ever
	 * looks at online players -- see the class javadoc for why that is exactly what makes the
	 * offline-holder case safe.
	 */
	private static StolenHammer takeFromOtherPlayers(ServerPlayer caller, UUID hammerId) {
		for (ServerPlayer other : caller.getServer().getPlayerList().getPlayers()) {
			if (other == caller) {
				continue;
			}

			for (InteractionHand hand : InteractionHand.values()) {
				ItemStack held = other.getItemInHand(hand);
				if (matches(held, caller.getUUID(), hammerId)) {
					ItemStack taken = held.copy();
					other.setItemInHand(hand, ItemStack.EMPTY);
					return new StolenHammer(other, taken);
				}
			}

			Inventory inventory = other.getInventory();
			for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
				ItemStack stack = inventory.getItem(slot);
				if (matches(stack, caller.getUUID(), hammerId)) {
					ItemStack taken = stack.copy();
					inventory.setItem(slot, ItemStack.EMPTY);
					return new StolenHammer(other, taken);
				}
			}
		}
		return null;
	}

	private static boolean matches(ItemStack stack, UUID owner, UUID hammerId) {
		if (!stack.is(ModItems.MJOLNIR)) {
			return false;
		}
		if (hammerId != null) {
			return hammerId.equals(stack.get(ModDataComponents.HAMMER_ID));
		}
		return owner.equals(stack.get(ModDataComponents.BOUND_OWNER));
	}

	// ---------------- reconstruction ----------------

	/** Rebuilds the item the record describes, at the generation that retires all older copies. */
	private static ItemStack rebuildStack(HammerRecord record, int generation) {
		ItemStack stack = new ItemStack(ModItems.MJOLNIR);
		stack.set(ModDataComponents.HAMMER_ID, record.hammerId());
		stack.set(ModDataComponents.HAMMER_GENERATION, generation);
		record.owner().ifPresent(owner -> {
			stack.set(ModDataComponents.BOUND_OWNER, owner);
			if (!record.ownerName().isEmpty()) {
				stack.set(ModDataComponents.BOUND_OWNER_NAME, record.ownerName());
			}
		});
		return stack;
	}

	/**
	 * Starts the visible return: the hammer appears well away from the player, high and behind them,
	 * and flies the rest of the way under its own power. Deliberately not dropped into the inventory
	 * -- crossing a dimension should still look like the hammer came to you.
	 */
	private static void flyIn(ServerPlayer player, ItemStack stack) {
		ServerLevel level = player.serverLevel();
		Vec3 origin = arrivalPoint(player);
		MjolnirEntity hammer = MjolnirEntity.createReturning(level, player, stack, origin);
		level.addFreshEntity(hammer);
	}

	/**
	 * The "torn from another player's grip" version of {@link #flyIn}: when the holder is in the
	 * <em>same</em> dimension as the owner, the hammer visibly leaves from right where they were
	 * standing rather than popping in near the owner -- this is what actually sells "Mjolnir left
	 * Player B and flew to Player A" instead of just "Player A's hammer came back."
	 *
	 * <p>{@link MjolnirEntity#tickReturning} settles the hammer the instant its owner's dimension
	 * stops matching its own, so spawning it in the holder's dimension only works when that already
	 * matches the owner's; if the holder is elsewhere, this falls back to {@link #flyIn}'s ordinary
	 * behind-the-owner arrival rather than spawning somewhere it could never actually fly home from.
	 */
	private static void flyInFromHolder(ServerPlayer owner, ServerPlayer holder, ItemStack stack) {
		if (holder.serverLevel() != owner.serverLevel()) {
			flyIn(owner, stack);
			return;
		}

		ServerLevel level = holder.serverLevel();
		Vec3 origin = holder.position().add(0.0, holder.getBbHeight() * 0.6, 0.0);
		MjolnirEntity hammer = MjolnirEntity.createReturning(level, owner, stack, origin);
		level.addFreshEntity(hammer);

		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, origin.x, origin.y, origin.z, 14, 0.3, 0.3, 0.3, 0.06);
		level.playSound(null, holder.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.6f, 1.3f);
	}

	/**
	 * Picks somewhere clear to come in from: behind the player, high up, and nudged out of any
	 * terrain it would otherwise start inside. It phases through blocks on the way home anyway, so
	 * this is about the arrival <em>reading</em> well rather than about collision.
	 */
	private static Vec3 arrivalPoint(ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		Vec3 behind = new Vec3(-look.x, 0.0, -look.z);
		if (behind.lengthSqr() < 1.0E-6) {
			behind = new Vec3(0.0, 0.0, 1.0);
		} else {
			behind = behind.normalize();
		}

		Vec3 candidate = player.position()
				.add(behind.scale(ARRIVAL_DISTANCE))
				.add(0.0, ARRIVAL_HEIGHT, 0.0);

		// Keep it inside the world's height limits, and off the ground if the player is in a cave.
		ServerLevel level = player.serverLevel();
		double maxY = level.getMaxBuildHeight() - 2.0;
		double minY = level.getMinBuildHeight() + 2.0;
		double y = Math.max(minY, Math.min(maxY, candidate.y));
		Vec3 clamped = new Vec3(candidate.x, y, candidate.z);

		// If the straight line from there to the player is blocked at the very start (it spawned
		// inside a hill), pull it back toward the player until it is in open air.
		HitResult hit = level.clip(new ClipContext(player.getEyePosition(), clamped,
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (hit.getType() == HitResult.Type.BLOCK) {
			Vec3 open = hit.getLocation();
			// Back off a little from the surface so it is not embedded in the block it hit.
			clamped = open.subtract(open.subtract(player.getEyePosition()).normalize().scale(0.5));
		}
		return clamped;
	}
}
