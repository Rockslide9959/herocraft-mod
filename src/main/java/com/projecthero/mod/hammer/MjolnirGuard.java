package com.projecthero.mod.hammer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.entity.MjolnirEntity;
import com.projecthero.mod.item.ModDataComponents;
import com.projecthero.mod.item.ModItems;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * v0.14.16 -- "calling the hammer from an unloaded chunk / a chest / someone else's inventory duplicates
 * it". The generation scheme on {@link MjolnirRegistry} already made every superseded copy a <em>ghost</em>,
 * but a ghost only ever deleted itself when it ticked in a player's inventory or as an entity. Anything that
 * does not tick -- a chest, barrel, shulker box (and a shulker <em>item</em>'s contents), an ender chest, a
 * hopper, a minecart, an item frame, an armour stand's hand, an entity sitting in a lazy border chunk --
 * kept showing the old hammer indefinitely, which is exactly the duplicate the player saw.
 *
 * <p>This class retires ghosts everywhere they can be <em>observed</em>:
 * <ul>
 *   <li><b>any open menu</b> (every tick for its top-level slots and the cursor; shulker/bundle contents and
 *   the inventory crafting grid every {@link #DEEP_SCAN_INTERVAL} ticks) -- so opening a chest/ender chest/
 *   minecart that holds a ghost deletes it before it can be taken out;</li>
 *   <li><b>block entity load</b> (a container's chunk loading) and <b>entity load</b> (an item entity, a
 *   {@link MjolnirEntity}, an item frame, a container entity, a mob's equipment) -- deferred one tick via a
 *   {@link TickTask}, never mutated inside the chunk-load callback itself;</li>
 *   <li><b>player join</b> -- their inventory, cursor and ender chest (the inventory also self-cleans on
 *   tick via {@code MjolnirItem#inventoryTick}, the ender chest never would).</li>
 * </ul>
 * It also records where a <em>valid</em> hammer is when it is seen in an open container
 * ({@link MjolnirRegistry#noteContainer}), so {@link MjolnirRecall} can move the real hammer out of a loaded
 * chest rather than minting a new one.
 *
 * <p>Holds no static state of its own (deferred work goes through the server's task queue), so nothing
 * here needs a {@code ServerStateReset} hook.
 */
public final class MjolnirGuard {
	/** How often the slower parts of the menu sweep (nested shulker/bundle contents, location notes) run. */
	static final int DEEP_SCAN_INTERVAL = 10;

	private MjolnirGuard() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(MjolnirGuard::tick);
		ServerEntityEvents.ENTITY_LOAD.register(MjolnirGuard::onEntityLoad);
		ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register(MjolnirGuard::onBlockEntityLoad);
		ServerPlayerEvents.JOIN.register(player -> purgePlayer(MjolnirRegistry.get(player.serverLevel()), player));
	}

	// ---------------- stale tests ----------------

	/** A Mjolnir stack a newer copy has already superseded. */
	public static boolean isStaleHammer(MjolnirRegistry registry, ItemStack stack) {
		return stack.is(ModItems.MJOLNIR) && registry.isStale(stack);
	}

	/** Whether {@code stack} is, or (one level deep: a shulker box / bundle item) contains, a ghost hammer. */
	public static boolean holdsStale(MjolnirRegistry registry, ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (stack.is(ModItems.MJOLNIR)) {
			return registry.isStale(stack);
		}
		ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
		if (contents != null) {
			for (ItemStack inner : contents.nonEmptyItems()) {
				if (isStaleHammer(registry, inner)) {
					return true;
				}
			}
		}
		BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
		if (bundle != null) {
			for (ItemStack inner : bundle.items()) {
				if (isStaleHammer(registry, inner)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * The ghost-free version of {@code stack}: {@link ItemStack#EMPTY} if it is itself a ghost hammer, a copy
	 * with the ghosts stripped out of its shulker/bundle contents, or {@code stack} itself (same instance) if
	 * there was nothing to remove.
	 */
	public static ItemStack clean(MjolnirRegistry registry, ItemStack stack) {
		if (!holdsStale(registry, stack)) {
			return stack;
		}
		if (stack.is(ModItems.MJOLNIR)) {
			return ItemStack.EMPTY;
		}
		ItemStack copy = stack.copy();
		ItemContainerContents contents = copy.get(DataComponents.CONTAINER);
		if (contents != null) {
			List<ItemStack> items = new ArrayList<>();
			contents.stream().forEach(inner -> items.add(isStaleHammer(registry, inner) ? ItemStack.EMPTY : inner.copy()));
			copy.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
		}
		BundleContents bundle = copy.get(DataComponents.BUNDLE_CONTENTS);
		if (bundle != null) {
			List<ItemStack> items = new ArrayList<>();
			for (ItemStack inner : bundle.items()) {
				if (!isStaleHammer(registry, inner)) {
					items.add(inner.copy());
				}
			}
			copy.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(items));
		}
		return copy;
	}

	/**
	 * Reading a container that still has an unrolled loot table would roll it (with no player, so no luck)
	 * just because we looked -- and such a container cannot hold a placed hammer anyway, since inserting
	 * anything unpacks the table first.
	 */
	static boolean hasPendingLoot(Object container) {
		if (container instanceof RandomizableContainer randomizable && randomizable.getLootTable() != null) {
			return true;
		}
		return container instanceof ContainerEntity containerEntity && containerEntity.getLootTable() != null;
	}

	public static boolean containsStale(MjolnirRegistry registry, Container container) {
		if (hasPendingLoot(container)) {
			return false;
		}
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (holdsStale(registry, container.getItem(i))) {
				return true;
			}
		}
		return false;
	}

	// ---------------- purges ----------------

	/** @return how many slots were cleaned. */
	public static int purgeContainer(MjolnirRegistry registry, Container container) {
		if (hasPendingLoot(container)) {
			return 0;
		}
		int removed = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			ItemStack cleaned = clean(registry, stack);
			if (cleaned != stack) {
				container.setItem(i, cleaned);
				removed++;
			}
		}
		if (removed > 0) {
			container.setChanged();
		}
		return removed;
	}

	/** Inventory, ender chest, cursor and whatever menu is open. */
	public static int purgePlayer(MjolnirRegistry registry, ServerPlayer player) {
		int removed = purgeContainer(registry, player.getInventory());
		removed += purgeContainer(registry, player.getEnderChestInventory());
		removed += scanMenu(registry, player, player.containerMenu, true);
		if (player.containerMenu != player.inventoryMenu) {
			removed += scanMenu(registry, player, player.inventoryMenu, true);
		}
		return removed;
	}

	/** Read-only: does this (non-player) entity carry a ghost anywhere {@link #purgeEntity} would look? */
	public static boolean entityHoldsStale(MjolnirRegistry registry, Entity entity) {
		if (entity instanceof Player) {
			return false;
		}
		if (entity instanceof MjolnirEntity hammer) {
			return isStaleHammer(registry, hammer.getItem());
		}
		if (entity instanceof ItemEntity item) {
			return holdsStale(registry, item.getItem());
		}
		if (entity instanceof ItemFrame frame) {
			return holdsStale(registry, frame.getItem());
		}
		if (entity instanceof ContainerEntity container && containsStale(registry, container)) {
			return true;
		}
		if (entity instanceof InventoryCarrier carrier && containsStale(registry, carrier.getInventory())) {
			return true;
		}
		if (entity instanceof LivingEntity living) {
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				if (holdsStale(registry, living.getItemBySlot(slot))) {
					return true;
				}
			}
		}
		return false;
	}

	/** @return true if anything was removed (or the whole entity discarded). */
	public static boolean purgeEntity(MjolnirRegistry registry, Entity entity) {
		if (entity.isRemoved() || entity instanceof Player) {
			return false;
		}
		if (entity instanceof MjolnirEntity hammer) {
			if (isStaleHammer(registry, hammer.getItem())) {
				hammer.discard();
				return true;
			}
			return false;
		}
		if (entity instanceof ItemEntity item) {
			ItemStack cleaned = clean(registry, item.getItem());
			if (cleaned == item.getItem()) {
				return false;
			}
			if (cleaned.isEmpty()) {
				item.discard();
			} else {
				item.setItem(cleaned);
			}
			return true;
		}
		if (entity instanceof ItemFrame frame) {
			ItemStack cleaned = clean(registry, frame.getItem());
			if (cleaned == frame.getItem()) {
				return false;
			}
			frame.setItem(cleaned, false);
			return true;
		}
		boolean removed = false;
		if (entity instanceof ContainerEntity container) {
			removed |= purgeContainer(registry, container) > 0;
		}
		if (entity instanceof InventoryCarrier carrier) {
			removed |= purgeContainer(registry, carrier.getInventory()) > 0;
		}
		if (entity instanceof LivingEntity living) {
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				ItemStack stack = living.getItemBySlot(slot);
				ItemStack cleaned = clean(registry, stack);
				if (cleaned != stack) {
					living.setItemSlot(slot, cleaned);
					removed = true;
				}
			}
		}
		return removed;
	}

	// ---------------- hooks ----------------

	private static void tick(MinecraftServer server) {
		MjolnirRegistry registry = null;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			AbstractContainerMenu menu = player.containerMenu;
			boolean deep = player.tickCount % DEEP_SCAN_INTERVAL == 0;
			// The player's own inventory self-cleans on inventoryTick; with no other menu open only the
			// crafting grid and cursor are left, and those are swept on the slower cadence.
			if (menu == null || (menu == player.inventoryMenu && !deep)) {
				continue;
			}
			if (registry == null) {
				registry = MjolnirRegistry.get(server);
			}
			scanMenu(registry, player, menu, deep);
		}
	}

	/**
	 * Deletes ghosts from every slot of {@code menu} plus the cursor, and (on a deep pass) records where a
	 * valid hammer sitting in a non-player container is, for {@link MjolnirRecall}.
	 */
	static int scanMenu(MjolnirRegistry registry, ServerPlayer player, AbstractContainerMenu menu, boolean deep) {
		int removed = 0;
		for (Slot slot : menu.slots) {
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) {
				continue;
			}
			if (stack.is(ModItems.MJOLNIR)) {
				if (registry.isStale(stack)) {
					slot.set(ItemStack.EMPTY);
					removed++;
				} else if (deep && isWorldContainer(slot.container) && stack.get(ModDataComponents.HAMMER_ID) != null) {
					registry.noteContainer(stack, player.serverLevel(), containerPos(slot.container, player));
				}
				continue;
			}
			if (deep) {
				ItemStack cleaned = clean(registry, stack);
				if (cleaned != stack) {
					slot.set(cleaned);
					removed++;
				}
			}
		}
		ItemStack carried = menu.getCarried();
		if (!carried.isEmpty() && (carried.is(ModItems.MJOLNIR) || deep)) {
			ItemStack cleaned = clean(registry, carried);
			if (cleaned != carried) {
				menu.setCarried(cleaned);
				removed++;
			}
		}
		if (removed > 0) {
			menu.broadcastChanges();
		}
		return removed;
	}

	/** A container that lives in the world (not the viewer's own inventory or ender chest). */
	private static boolean isWorldContainer(Container container) {
		return !(container instanceof Inventory) && !(container instanceof PlayerEnderChestContainer);
	}

	private static BlockPos containerPos(Container container, ServerPlayer viewer) {
		if (container instanceof BlockEntity blockEntity) {
			return blockEntity.getBlockPos();
		}
		if (container instanceof Entity entity) {
			return entity.blockPosition();
		}
		// A double chest (CompoundContainer) or a modded wrapper: the viewer is standing right next to it.
		return viewer.blockPosition();
	}

	private static void onEntityLoad(Entity entity, ServerLevel level) {
		if (entity instanceof Player) {
			return;
		}
		MinecraftServer server = level.getServer();
		if (!server.isSameThread()) {
			defer(server, () -> purgeEntity(MjolnirRegistry.get(server), entity));
			return;
		}
		if (!entityHoldsStale(MjolnirRegistry.get(server), entity)) {
			return;
		}
		defer(server, () -> purgeEntity(MjolnirRegistry.get(server), entity));
	}

	private static void onBlockEntityLoad(BlockEntity blockEntity, ServerLevel level) {
		if (!(blockEntity instanceof Container container)) {
			return;
		}
		MinecraftServer server = level.getServer();
		if (server.isSameThread() && !containsStale(MjolnirRegistry.get(server), container)) {
			return;
		}
		BlockPos pos = blockEntity.getBlockPos();
		defer(server, () -> {
			if (!blockEntity.isRemoved() && level.isLoaded(pos) && level.getBlockEntity(pos) == blockEntity) {
				purgeContainer(MjolnirRegistry.get(server), container);
			}
		});
	}

	/** Runs on the server thread between ticks -- never inside a chunk/entity load callback itself. */
	private static void defer(MinecraftServer server, Runnable task) {
		server.tell(new TickTask(server.getTickCount(), () -> {
			try {
				task.run();
			} catch (RuntimeException e) {
				ProjectHeroMod.LOGGER.warn("Mjolnir ghost sweep failed", e);
			}
		}));
	}

	// ---------------- recall support ----------------

	/** Whether {@code stack} is the live (non-ghost) copy of hammer {@code hammerId}. */
	static boolean isLiveCopy(MjolnirRegistry registry, ItemStack stack, UUID hammerId) {
		return hammerId != null && stack.is(ModItems.MJOLNIR)
				&& hammerId.equals(stack.get(ModDataComponents.HAMMER_ID))
				&& !registry.isStale(stack);
	}
}
