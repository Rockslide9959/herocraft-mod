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

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.AABB;
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
 * <p><b>v0.14.16 ("calling it from a chest / an unloaded chunk / someone's inventory duplicates it").</b>
 * Between steps 3 and 4 the hammer is now also looked for -- and <em>moved</em> out of -- every other
 * loaded place it can sit: another player's cursor, open menu or ender chest, the caller's own ender
 * chest, its recorded entity in another dimension or a non-ticking border chunk, and every loaded
 * container block entity / item frame / container entity / mob hand around where the registry last saw
 * it and around the caller (one level into shulker boxes and bundles). Every move re-stamps the stack at
 * a freshly bumped generation. Reconstruction (step 4) now starts from the registry's full snapshot of
 * the stack, and the ghost it leaves behind is deleted by {@link MjolnirGuard} the instant it is next
 * observed anywhere -- not only when it happens to tick in a player's inventory.
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
		int ownSlot = findInInventory(player, hammerId);
		if (ownSlot >= 0) {
			equipFromInventory(player, ownSlot);
			return true;
		}
		// v0.14.16: ...including on our own cursor (mid-drag in a menu) -- it is in our hand already.
		if (matches(registry, player.containerMenu.getCarried(), player.getUUID(), hammerId)) {
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

		// v0.14.16: every branch below MOVES the one real hammer (removes it from wherever it is, then sends
		// that exact stack -- every component intact -- home at a freshly bumped generation, so even a copy
		// whose removal somehow never got saved is retired as a ghost). Only step 6 ever has to rebuild one.

		// 2b. Its recorded entity is loaded, just not in the caller's dimension.
		ItemStack fromOtherDimension = takeLoadedEntityElsewhere(player, registry, hammerId, record);
		if (fromOtherDimension != null) {
			flyIn(player, restamp(fromOtherDimension, registry, hammerId));
			ThorFeedback.recallStartedFar(player);
			return true;
		}

		// 3. In somebody else's hand, inventory, cursor, open menu or ender chest (or our own ender chest).
		StolenHammer stolen = takeFromOtherPlayers(player, registry, hammerId);
		if (stolen != null) {
			ItemStack moving = restamp(stolen.stack(), registry, hammerId);
			if (stolen.holder() == player) {
				flyIn(player, moving);
			} else {
				flyInFromHolder(player, stolen.holder(), moving);
				ThorFeedback.hammerTakenByOwner(stolen.holder());
			}
			ThorFeedback.recallStartedFar(player);
			return true;
		}

		// 4-5. v0.14.16: in a LOADED container (chest, barrel, hopper, a shulker box inside one, a minecart),
		// an item frame, an armour stand's hand, or an entity in a lazy border chunk -- searched around where
		// the registry last saw it and around the caller. Previously this fell straight through to
		// reconstruction and left the original sitting in the chest: the duplicate.
		FoundInWorld found = takeFromLoadedWorld(player, registry, hammerId, record);
		if (found != null) {
			ItemStack moving = restamp(found.stack(), registry, hammerId);
			if (found.level() == player.serverLevel()) {
				flyInFrom(player, found.level(), found.origin(), moving);
			} else {
				flyIn(player, moving);
			}
			ThorFeedback.recallStartedFar(player);
			return true;
		}

		// 6. Unreachable (an unloaded chunk, an offline player) -- reconstruct, retiring every older copy.
		// reconstruct() has already blanked the record's whereabouts, and the new entity records its own on
		// its first tick. v0.14.16: rebuilt from the registry's last full snapshot of the stack, so the
		// recalled copy keeps its custom name, enchantments and the rest; the ghost left behind is deleted by
		// MjolnirGuard the moment its chunk / container / owner is next seen.
		ItemStack rebuilt = rebuildStack(record, registry.snapshot(hammerId).orElse(null),
				registry.reconstruct(hammerId));
		flyIn(player, rebuilt);
		ThorFeedback.recallStartedFar(player);
		return true;
	}

	/** v0.14.16: the moved stack goes home at a new generation, so any lingering copy of it is a ghost. */
	private static ItemStack restamp(ItemStack stack, MjolnirRegistry registry, UUID hammerId) {
		ItemStack moving = stack.copyWithCount(1);
		moving.set(ModDataComponents.HAMMER_GENERATION, registry.reconstruct(hammerId));
		return moving;
	}

	/**
	 * v0.13.5: force-unbinds {@code hammerId} from {@code player} wherever the physical hammer actually
	 * is right now -- their own inventory first, then a loaded-entity sweep, exactly the same two steps
	 * {@link #call} itself tries first -- clearing the stack's {@link ModDataComponents#BOUND_OWNER}
	 * component and the registry's own owner record. Used when a Primary power is revoked or replaced
	 * out from under a player (an admin command, or {@code HeroTiers} evicting the oldest Primary power
	 * for a new one) so the hammer does not stay permanently bound to someone who no longer has the
	 * power -- unlike an ordinary {@link com.projecthero.mod.power.ThorPowers#toggleBinding}, this does
	 * not require the player to be the one holding it, or even for it to be reachable at all: if it is
	 * genuinely unloaded/far away right now, the caller's own state is still cleared (see
	 * {@code ThorPowers#revokePower}), and this residual stale component on the one specific physical
	 * item is a bounded, rare edge case rather than a permanent leak -- the item still answers a fresh
	 * bind attempt from anyone once {@link com.projecthero.mod.item.MjolnirItem#use} reaches it, since
	 * that path only refuses a bind when the *current* owner differs from the caller.
	 *
	 * @return true if the physical hammer was found and its own binding cleared.
	 */
	public static boolean forceUnbind(ServerPlayer player, UUID hammerId) {
		if (hammerId == null) {
			return false;
		}
		MjolnirRegistry registry = MjolnirRegistry.get(player.serverLevel());

		int slot = findInInventory(player, hammerId);
		if (slot >= 0) {
			ItemStack stack = player.getInventory().getItem(slot);
			clearBinding(stack, registry);
			return true;
		}

		MjolnirEntity loaded = findLoadedEntity(player, registry, hammerId);
		if (loaded != null) {
			ItemStack stack = loaded.getItem().copy();
			clearBinding(stack, registry);
			loaded.setItem(stack);
			return true;
		}

		return false;
	}

	private static void clearBinding(ItemStack stack, MjolnirRegistry registry) {
		stack.remove(ModDataComponents.BOUND_OWNER);
		stack.remove(ModDataComponents.BOUND_OWNER_NAME);
		registry.setOwner(stack, Optional.empty(), "");
	}

	/**
	 * v0.14.0: a hammer found anywhere in the backpack that isn't already the main hand is swapped
	 * straight into it -- whatever was in the main hand takes the hammer's old slot -- so pressing R
	 * mid-fight actually gets it into your hand instead of just confirming you technically have it
	 * somewhere. This is always possible with no capacity check: it relocates two stacks that already
	 * exist, it never needs a free slot. Only the ordinary 36-slot hotbar+storage range is eligible;
	 * a hammer sitting in an armor or offhand slot (not a normal landing spot, but reachable by
	 * manually shift-clicking one there) is left alone rather than risk swapping the displaced item
	 * into an armor slot.
	 */
	private static void equipFromInventory(ServerPlayer player, int slot) {
		Inventory inventory = player.getInventory();
		int mainHandSlot = inventory.selected;
		if (slot == mainHandSlot) {
			ThorFeedback.recallAlreadyHeld(player);
			return;
		}
		if (slot < 0 || slot >= 36) {
			ThorFeedback.recallAlreadyHeld(player);
			return;
		}
		ItemStack hammer = inventory.getItem(slot);
		ItemStack heldItem = inventory.getItem(mainHandSlot);
		inventory.setItem(mainHandSlot, hammer);
		inventory.setItem(slot, heldItem);
		ThorFeedback.recallEquipped(player);
	}

	// ---------------- resolution steps ----------------

	/** @return the inventory slot the caller's own hammer is in, or -1. */
	private static int findInInventory(ServerPlayer player, UUID hammerId) {
		MjolnirRegistry registry = MjolnirRegistry.get(player.serverLevel());
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (matches(registry, stack, player.getUUID(), hammerId)) {
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
						&& hammer.level() == player.level()
						&& !registry.isStale(hammer.getItem())
						// v0.14.16: one sitting in a lazy border chunk never ticks, so it could never fly
						// home -- takeLoadedEntityElsewhere moves it instead.
						&& player.serverLevel().isPositionEntityTicking(hammer.blockPosition())) {
					return hammer;
				}
			}
		}

		return player.serverLevel().getEntitiesOfClass(MjolnirEntity.class,
						player.getBoundingBox().inflate(LOADED_SEARCH_RADIUS),
						entity -> !entity.isRemoved() && !registry.isStale(entity.getItem())
								&& player.serverLevel().isPositionEntityTicking(entity.blockPosition())
								&& answersTo(entity, player, hammerId)).stream()
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
	private static StolenHammer takeFromOtherPlayers(ServerPlayer caller, MjolnirRegistry registry, UUID hammerId) {
		for (ServerPlayer other : caller.getServer().getPlayerList().getPlayers()) {
			if (other == caller) {
				continue;
			}

			for (InteractionHand hand : InteractionHand.values()) {
				ItemStack held = other.getItemInHand(hand);
				if (matches(registry, held, caller.getUUID(), hammerId)) {
					ItemStack taken = held.copy();
					other.setItemInHand(hand, ItemStack.EMPTY);
					return new StolenHammer(other, taken);
				}
			}

			ItemStack taken = takeFromContainer(other.getInventory(), registry, hammerId);
			if (taken == null) {
				// v0.14.16: the cursor (mid-drag) and whatever world container they have open right now.
				taken = takeFromMenu(other, registry, hammerId);
			}
			if (taken == null) {
				// v0.14.16: their ender chest -- a container that never ticks, the classic ghost hideout.
				taken = takeFromContainer(other.getEnderChestInventory(), registry, hammerId);
			}
			if (taken != null) {
				other.containerMenu.broadcastChanges();
				return new StolenHammer(other, taken);
			}
		}

		// v0.14.16: the caller's own ender chest (the open menu / cursor were checked as step 1).
		ItemStack own = takeFromContainer(caller.getEnderChestInventory(), registry, hammerId);
		if (own == null) {
			own = takeFromMenu(caller, registry, hammerId);
		}
		if (own != null) {
			caller.containerMenu.broadcastChanges();
			return new StolenHammer(caller, own);
		}
		return null;
	}

	/** The cursor, then any non-inventory slot of the player's open menu (a chest they are looking into). */
	private static ItemStack takeFromMenu(ServerPlayer player, MjolnirRegistry registry, UUID hammerId) {
		ItemStack carried = player.containerMenu.getCarried();
		if (MjolnirGuard.isLiveCopy(registry, carried, hammerId)) {
			player.containerMenu.setCarried(ItemStack.EMPTY);
			return carried.copy();
		}
		for (net.minecraft.world.inventory.Slot slot : player.containerMenu.slots) {
			if (slot.container instanceof Inventory) {
				continue;
			}
			ItemStack stack = slot.getItem();
			if (MjolnirGuard.isLiveCopy(registry, stack, hammerId)) {
				ItemStack taken = stack.copy();
				slot.set(ItemStack.EMPTY);
				return taken;
			}
		}
		return null;
	}

	private static boolean matches(MjolnirRegistry registry, ItemStack stack, UUID owner, UUID hammerId) {
		if (!stack.is(ModItems.MJOLNIR)) {
			return false;
		}
		// v0.14.16: a superseded ghost is never "the" hammer -- moving one would resurrect it.
		if (registry.isStale(stack)) {
			return false;
		}
		if (hammerId != null) {
			return hammerId.equals(stack.get(ModDataComponents.HAMMER_ID));
		}
		return owner.equals(stack.get(ModDataComponents.BOUND_OWNER));
	}

	// ---------------- v0.14.16: moving the real hammer out of the loaded world ----------------

	/** Where a hammer found loose in the loaded world was, and the exact stack taken out of it. */
	record FoundInWorld(ServerLevel level, Vec3 origin, ItemStack stack) {
	}

	/** How many chunks around the registry's last-known position are searched. */
	private static final int RECORD_SEARCH_CHUNKS = 2;
	/** How many chunks around the caller are searched (covers a chest they just walked away from). */
	private static final int CALLER_SEARCH_CHUNKS = 4;

	/**
	 * The hammer's recorded entity is loaded but cannot simply be told to fly home: it is in another
	 * dimension, or in a lazy border chunk where it never ticks. Taken and discarded; the caller spawns
	 * the moved stack.
	 */
	private static ItemStack takeLoadedEntityElsewhere(ServerPlayer player, MjolnirRegistry registry, UUID hammerId,
			HammerRecord record) {
		if (record.entityId().isEmpty()) {
			return null;
		}
		ServerLevel recordLevel = player.getServer().getLevel(record.dimension());
		if (recordLevel == null
				|| !(recordLevel.getEntity(record.entityId().get()) instanceof MjolnirEntity hammer)
				|| hammer.isRemoved()
				|| !MjolnirGuard.isLiveCopy(registry, hammer.getItem(), hammerId)) {
			return null;
		}
		ItemStack taken = hammer.getItem().copy();
		hammer.discard();
		return taken;
	}

	/**
	 * Searches the LOADED chunks around where the registry last saw the hammer, then around the caller:
	 * every container block entity (one level into shulker boxes / bundles inside it), and every loaded
	 * non-player entity that can hold an item. Never loads a chunk: {@code getChunkNow} simply misses for
	 * one that is not loaded, and {@code getEntities} only ever sees loaded entity sections.
	 */
	private static FoundInWorld takeFromLoadedWorld(ServerPlayer caller, MjolnirRegistry registry, UUID hammerId,
			HammerRecord record) {
		ServerLevel recordLevel = caller.getServer().getLevel(record.dimension());
		if (recordLevel != null && !BlockPos.ZERO.equals(record.lastPos())) {
			FoundInWorld found = searchArea(recordLevel, record.lastPos(), RECORD_SEARCH_CHUNKS, registry, hammerId);
			if (found != null) {
				return found;
			}
		}
		return searchArea(caller.serverLevel(), caller.blockPosition(), CALLER_SEARCH_CHUNKS, registry, hammerId);
	}

	private static FoundInWorld searchArea(ServerLevel level, BlockPos center, int chunkRadius,
			MjolnirRegistry registry, UUID hammerId) {
		int centerX = center.getX() >> 4;
		int centerZ = center.getZ() >> 4;
		for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
			for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
				net.minecraft.world.level.chunk.LevelChunk chunk =
						level.getChunkSource().getChunkNow(centerX + dx, centerZ + dz);
				if (chunk == null) {
					continue;
				}
				for (net.minecraft.world.level.block.entity.BlockEntity blockEntity : chunk.getBlockEntities().values()) {
					if (blockEntity.isRemoved() || !(blockEntity instanceof net.minecraft.world.Container container)) {
						continue;
					}
					ItemStack taken = takeFromContainer(container, registry, hammerId);
					if (taken != null) {
						return new FoundInWorld(level, Vec3.atCenterOf(blockEntity.getBlockPos()).add(0.0, 0.7, 0.0), taken);
					}
				}
			}
		}

		double reach = chunkRadius * 16.0 + 8.0;
		AABB box = new AABB(center).inflate(reach, Math.max(reach, 64.0), reach);
		for (Entity entity : level.getEntities((Entity) null, box,
				entity -> !(entity instanceof net.minecraft.world.entity.player.Player) && !entity.isRemoved())) {
			ItemStack taken = takeFromEntity(entity, registry, hammerId);
			if (taken != null) {
				return new FoundInWorld(level, entity.position().add(0.0, entity.getBbHeight() * 0.6, 0.0), taken);
			}
		}
		return null;
	}

	/**
	 * Removes the live copy of {@code hammerId} from {@code container} (directly, or out of a shulker box /
	 * bundle sitting in it) and returns it; ghosts met on the way are deleted on the spot.
	 */
	static ItemStack takeFromContainer(net.minecraft.world.Container container, MjolnirRegistry registry, UUID hammerId) {
		if (MjolnirGuard.hasPendingLoot(container)) {
			return null;
		}
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			Extraction extraction = extract(stack, registry, hammerId);
			if (extraction != null) {
				container.setItem(i, extraction.remaining());
				container.setChanged();
				return extraction.hammer();
			}
			ItemStack cleaned = MjolnirGuard.clean(registry, stack);
			if (cleaned != stack) {
				container.setItem(i, cleaned);
				container.setChanged();
			}
		}
		return null;
	}

	private static ItemStack takeFromEntity(Entity entity, MjolnirRegistry registry, UUID hammerId) {
		if (entity instanceof MjolnirEntity hammer) {
			if (MjolnirGuard.isLiveCopy(registry, hammer.getItem(), hammerId)) {
				ItemStack taken = hammer.getItem().copy();
				hammer.discard();
				return taken;
			}
			return null;
		}
		if (entity instanceof net.minecraft.world.entity.item.ItemEntity item) {
			Extraction extraction = extract(item.getItem(), registry, hammerId);
			if (extraction == null) {
				return null;
			}
			if (extraction.remaining().isEmpty()) {
				item.discard();
			} else {
				item.setItem(extraction.remaining());
			}
			return extraction.hammer();
		}
		if (entity instanceof net.minecraft.world.entity.decoration.ItemFrame frame) {
			Extraction extraction = extract(frame.getItem(), registry, hammerId);
			if (extraction == null) {
				return null;
			}
			frame.setItem(extraction.remaining(), false);
			return extraction.hammer();
		}
		if (entity instanceof net.minecraft.world.entity.vehicle.ContainerEntity container) {
			ItemStack taken = takeFromContainer(container, registry, hammerId);
			if (taken != null) {
				return taken;
			}
		}
		if (entity instanceof net.minecraft.world.entity.npc.InventoryCarrier carrier) {
			ItemStack taken = takeFromContainer(carrier.getInventory(), registry, hammerId);
			if (taken != null) {
				return taken;
			}
		}
		if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
			for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
				Extraction extraction = extract(living.getItemBySlot(slot), registry, hammerId);
				if (extraction != null) {
					living.setItemSlot(slot, extraction.remaining());
					return extraction.hammer();
				}
			}
		}
		return null;
	}

	/** The hammer pulled out of a stack, and what is left of that stack (EMPTY if it was the hammer). */
	private record Extraction(ItemStack hammer, ItemStack remaining) {
	}

	private static Extraction extract(ItemStack stack, MjolnirRegistry registry, UUID hammerId) {
		if (stack.isEmpty()) {
			return null;
		}
		if (MjolnirGuard.isLiveCopy(registry, stack, hammerId)) {
			return new Extraction(stack.copy(), ItemStack.EMPTY);
		}
		net.minecraft.world.item.component.ItemContainerContents contents =
				stack.get(net.minecraft.core.component.DataComponents.CONTAINER);
		if (contents != null) {
			java.util.List<ItemStack> items = new java.util.ArrayList<>();
			ItemStack found = null;
			for (ItemStack inner : contents.stream().toList()) {
				if (found == null && MjolnirGuard.isLiveCopy(registry, inner, hammerId)) {
					found = inner.copy();
					items.add(ItemStack.EMPTY);
				} else {
					items.add(inner.copy());
				}
			}
			if (found != null) {
				ItemStack remaining = stack.copy();
				remaining.set(net.minecraft.core.component.DataComponents.CONTAINER,
						net.minecraft.world.item.component.ItemContainerContents.fromItems(items));
				return new Extraction(found, remaining);
			}
		}
		net.minecraft.world.item.component.BundleContents bundle =
				stack.get(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS);
		if (bundle != null) {
			java.util.List<ItemStack> items = new java.util.ArrayList<>();
			ItemStack found = null;
			for (ItemStack inner : bundle.items()) {
				if (found == null && MjolnirGuard.isLiveCopy(registry, inner, hammerId)) {
					found = inner.copy();
				} else {
					items.add(inner.copy());
				}
			}
			if (found != null) {
				ItemStack remaining = stack.copy();
				remaining.set(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS,
						new net.minecraft.world.item.component.BundleContents(items));
				return new Extraction(found, remaining);
			}
		}
		return null;
	}

	// ---------------- reconstruction ----------------

	/**
	 * Rebuilds the item the record describes, at the generation that retires all older copies. v0.14.16:
	 * starts from the registry's last full snapshot of the stack when there is one, so the custom name,
	 * enchantments, damage and every other component survive; identity and ownership are then
	 * re-stamped from the authoritative record.
	 */
	private static ItemStack rebuildStack(HammerRecord record, ItemStack snapshot, int generation) {
		ItemStack stack = snapshot != null && snapshot.is(ModItems.MJOLNIR)
				? snapshot.copyWithCount(1)
				: new ItemStack(ModItems.MJOLNIR);
		stack.remove(ModDataComponents.BOUND_OWNER);
		stack.remove(ModDataComponents.BOUND_OWNER_NAME);
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
	 * v0.14.16: the hammer bursts out of the chest / item frame / minecart it was sitting in and flies home
	 * from there -- the visible proof that the one real hammer moved rather than a second one appearing.
	 */
	private static void flyInFrom(ServerPlayer owner, ServerLevel level, Vec3 origin, ItemStack stack) {
		MjolnirEntity hammer = MjolnirEntity.createReturning(level, owner, stack, origin);
		level.addFreshEntity(hammer);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, origin.x, origin.y, origin.z, 14, 0.3, 0.3, 0.3, 0.06);
		level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.6f, 0.8f);
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
