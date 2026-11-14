package com.projecthero.mod.doctorfate.helmet;

import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * v0.17.4 the Helmet of Fate's ghost sweeper (the {@code hammer.MjolnirGuard} pattern, slimmed down). A ghost is a copy whose
 * generation its owner's summon has moved past ({@link FateHelmetRegistry#isStale}); it goes wherever it can be <em>seen</em>:
 * <ul>
 *   <li>in a player's inventory: every tick ({@link HelmetOfFateItem#inventoryTick});</li>
 *   <li>in any open menu (a chest, barrel, ender chest, minecart, another mod's container) and on the cursor: every tick, so it's gone
 *   before it can be taken out;</li>
 *   <li>a dropped item, a placed helmet, an item frame, a mob's hands, a minecart or a villager's inventory: as the entity loads;</li>
 *   <li>a container block: as its chunk loads;</li>
 *   <li>a player's inventory and ender chest: as they join.</li>
 * </ul>
 * Load-time work is deferred one tick through the server's task queue (never done inside the load callback). It also keeps the
 * registry told where the <em>live</em> helmet is (a dropped item, the chest it's in, who carries it), so a summon can pull it
 * straight out of there and draw it flying to its owner. A dropped helmet item never despawns. No static state.
 */
public final class FateHelmetGuard {
	static final int DEEP_SCAN_INTERVAL = 10;

	private FateHelmetGuard() {
	}

	static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(FateHelmetGuard::tick);
		ServerEntityEvents.ENTITY_LOAD.register(FateHelmetGuard::onEntityLoad);
		ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register(FateHelmetGuard::onBlockEntityLoad);
		ServerPlayerEvents.JOIN.register(p -> purgePlayer(FateHelmetRegistry.get(p.serverLevel()), p));
	}

	// ---------------------------------------------------------------- inventories

	/** A helmet stack ticking in {@code holder}'s inventory: a ghost goes; the live one tells the registry who has it. */
	static void inventoryTick(ServerLevel level, ServerPlayer holder, ItemStack stack) {
		FateHelmetRegistry reg = FateHelmetRegistry.get(level);
		if (reg.isStale(stack)) {
			stack.setCount(0);
			return;
		}
		FateHelmetTag t = stack.get(FateHelmet.TAG);
		if (t != null && holder.tickCount % 20 == 0) {
			reg.note(t.owner(), FateHelmetRegistry.Where.INVENTORY, level, holder.blockPosition(), holder.getUUID());
		}
	}

	/** Removes ghosts from a container; returns how many. Never reads a container whose loot table hasn't been rolled. */
	public static int purgeContainer(FateHelmetRegistry reg, Container c) {
		if (pendingLoot(c)) {
			return 0;
		}
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack s = c.getItem(i);
			if (FateHelmet.is(s) && reg.isStale(s)) {
				c.setItem(i, ItemStack.EMPTY);
				n++;
			}
		}
		if (n > 0) {
			c.setChanged();
		}
		return n;
	}

	/** Removes {@code owner}'s helmet (live or not) from a container; true if one was there. Used by the summon. */
	public static boolean takeFrom(Container c, UUID owner) {
		if (pendingLoot(c)) {
			return false;
		}
		boolean found = false;
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (FateHelmet.belongsTo(c.getItem(i), owner)) {
				c.setItem(i, ItemStack.EMPTY);
				found = true;
			}
		}
		if (found) {
			c.setChanged();
		}
		return found;
	}

	public static int purgePlayer(FateHelmetRegistry reg, ServerPlayer p) {
		int n = purgeContainer(reg, p.getInventory()) + purgeContainer(reg, p.getEnderChestInventory());
		n += scanMenu(reg, p, p.containerMenu, true);
		return n;
	}

	static boolean pendingLoot(Object c) {
		return (c instanceof RandomizableContainer r && r.getLootTable() != null) || (c instanceof ContainerEntity ce && ce.getLootTable() != null);
	}

	private static void tick(MinecraftServer server) {
		FateHelmetRegistry reg = null;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			AbstractContainerMenu menu = p.containerMenu;
			boolean deep = p.tickCount % DEEP_SCAN_INTERVAL == 0;
			if (menu == null || (menu == p.inventoryMenu && !deep)) {
				continue;
			}
			if (reg == null) {
				reg = FateHelmetRegistry.get(server);
			}
			scanMenu(reg, p, menu, deep);
		}
	}

	/** Ghosts out of every slot of an open menu and the cursor; on a deep pass, notes which world container holds a live helmet. */
	static int scanMenu(FateHelmetRegistry reg, ServerPlayer p, AbstractContainerMenu menu, boolean deep) {
		int n = 0;
		for (Slot slot : menu.slots) {
			ItemStack s = slot.getItem();
			if (!FateHelmet.is(s)) {
				continue;
			}
			if (reg.isStale(s)) {
				slot.set(ItemStack.EMPTY);
				n++;
			} else if (deep && !(slot.container instanceof Inventory) && !(slot.container instanceof PlayerEnderChestContainer)) {
				FateHelmetTag t = s.get(FateHelmet.TAG);
				if (t != null) {
					BlockPos at = slot.container instanceof BlockEntity be ? be.getBlockPos()
							: slot.container instanceof Entity e ? e.blockPosition() : p.blockPosition();
					reg.note(t.owner(), FateHelmetRegistry.Where.CONTAINER, p.serverLevel(), at, null);
				}
			}
		}
		ItemStack carried = menu.getCarried();
		if (FateHelmet.is(carried) && reg.isStale(carried)) {
			menu.setCarried(ItemStack.EMPTY);
			n++;
		}
		if (n > 0) {
			menu.broadcastChanges();
		}
		return n;
	}

	// ---------------------------------------------------------------- entities and blocks loading

	/** True if anything went (or the whole entity). Public for the gametests. */
	public static boolean purgeEntity(FateHelmetRegistry reg, Entity entity) {
		if (entity.isRemoved() || entity instanceof Player) {
			return false;
		}
		if (entity instanceof FateHelmetEntity h) {
			if (reg.isStale(h.tag())) {
				h.vanish((ServerLevel) h.level());
				return true;
			}
			return false;
		}
		if (entity instanceof ItemEntity item) {
			if (FateHelmet.is(item.getItem()) && reg.isStale(item.getItem())) {
				item.discard();
				return true;
			}
			return false;
		}
		if (entity instanceof ItemFrame frame) {
			if (FateHelmet.is(frame.getItem()) && reg.isStale(frame.getItem())) {
				frame.setItem(ItemStack.EMPTY, false);
				return true;
			}
			return false;
		}
		boolean removed = false;
		if (entity instanceof ContainerEntity c) {
			removed |= purgeContainer(reg, c) > 0;
		}
		if (entity instanceof InventoryCarrier carrier) {
			removed |= purgeContainer(reg, carrier.getInventory()) > 0;
		}
		if (entity instanceof LivingEntity living) {
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				ItemStack s = living.getItemBySlot(slot);
				if (FateHelmet.is(s) && reg.isStale(s)) {
					living.setItemSlot(slot, ItemStack.EMPTY);
					removed = true;
				}
			}
		}
		return removed;
	}

	private static void onEntityLoad(Entity entity, ServerLevel level) {
		if (entity instanceof Player) {
			return;
		}
		MinecraftServer server = level.getServer();
		if (entity instanceof ItemEntity item && FateHelmet.is(item.getItem())) {
			defer(server, () -> {
				FateHelmetRegistry reg = FateHelmetRegistry.get(server);
				if (item.isRemoved() || purgeEntity(reg, item)) {
					return;
				}
				item.setUnlimitedLifetime(); // a dropped helmet never despawns
				FateHelmetTag t = item.getItem().get(FateHelmet.TAG);
				if (t != null) {
					reg.note(t.owner(), FateHelmetRegistry.Where.ITEM_ENTITY, level, item.blockPosition(), item.getUUID());
				}
			});
			return;
		}
		if (entity instanceof FateHelmetEntity || carriesHelmet(entity)) {
			defer(server, () -> purgeEntity(FateHelmetRegistry.get(server), entity));
		}
	}

	/** Cheap first look (no loot rolled): does this entity hold any Helmet of Fate at all? */
	private static boolean carriesHelmet(Entity entity) {
		if (entity instanceof ItemFrame frame) {
			return FateHelmet.is(frame.getItem());
		}
		if (entity instanceof ContainerEntity c && !pendingLoot(c) && holds(c)) {
			return true;
		}
		if (entity instanceof InventoryCarrier carrier && holds(carrier.getInventory())) {
			return true;
		}
		if (entity instanceof LivingEntity living) {
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				if (FateHelmet.is(living.getItemBySlot(slot))) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean holds(Container c) {
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (FateHelmet.is(c.getItem(i))) {
				return true;
			}
		}
		return false;
	}

	private static void onBlockEntityLoad(BlockEntity blockEntity, ServerLevel level) {
		if (!(blockEntity instanceof Container container) || pendingLoot(container)) {
			return;
		}
		MinecraftServer server = level.getServer();
		if (server.isSameThread() && !holds(container)) {
			return;
		}
		BlockPos pos = blockEntity.getBlockPos();
		defer(server, () -> {
			if (!blockEntity.isRemoved() && level.isLoaded(pos) && level.getBlockEntity(pos) == blockEntity) {
				purgeContainer(FateHelmetRegistry.get(server), container);
			}
		});
	}

	/** Public for the gametests: what a container block's chunk loading does. */
	public static void blockEntityLoadedForTest(BlockEntity blockEntity, ServerLevel level) {
		onBlockEntityLoad(blockEntity, level);
	}

	private static void defer(MinecraftServer server, Runnable task) {
		server.tell(new TickTask(server.getTickCount(), () -> {
			try {
				task.run();
			} catch (RuntimeException e) {
				ProjectHeroMod.LOGGER.warn("Helmet of Fate ghost sweep failed", e);
			}
		}));
	}
}
