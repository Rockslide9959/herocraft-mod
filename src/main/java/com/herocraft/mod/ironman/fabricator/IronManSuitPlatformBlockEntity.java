package com.herocraft.mod.ironman.fabricator;

import java.util.Optional;
import java.util.UUID;

import com.herocraft.mod.ironman.IronManArmor;
import com.herocraft.mod.ironman.IronManBlocks;
import com.herocraft.mod.ironman.IronManEnergy;
import com.herocraft.mod.ironman.TonyStark;
import com.herocraft.mod.ironman.data.StarkPlatformRegistry;
import com.herocraft.mod.ironman.item.IronManArmorItem;
import com.herocraft.mod.ironman.suit.IronManSuit;
import com.herocraft.mod.ironman.suit.IronManSuitUpManager;
import com.herocraft.mod.ironman.suit.IronManSuits;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Iron Man Suit Platform's state (spec section 33, extended in "changes 9"):
 *
 * <ul>
 *   <li>holds a suit's four pieces (via the GUI's 4 slots or right-click-with-a-piece);</li>
 *   <li>binds to the player who placed it, so several Iron Man players' platforms don't collide, and
 *       auto-adopts an owner if it was placed before this system existed;</li>
 *   <li>actively charges the <em>suit</em> sitting on it -- its own trickle plus any Reactor-Core
 *       energy in the buffer flows into the stored armour's carried charge, and its integrity is
 *       repaired -- so the GUI shows the armour's real energy, not zero. v0.6.2: both rates are a
 *       flat <b>0.1% of the mark's pool per second</b>
 *       ({@link IronManEnergy#platformEnergyPerSecond} / {@link IronManEnergy#platformIntegrityPerSecond}),
 *       and deploying no longer force-repairs;</li>
 *   <li>mirrors its contents into {@link StarkPlatformRegistry} so the suit can be <b>called from an
 *       unloaded chunk</b>;</li>
 *   <li>deploying / retrieving move the suit between the platform and a Tony Stark player.</li>
 * </ul>
 */
public class IronManSuitPlatformBlockEntity extends BlockEntity
		implements Container, ExtendedScreenHandlerFactory<BlockPos> {
	public static final int SIZE = 4;
	/** Reactor-Core / trickle energy buffer -- the platform's own reserve it pours into the suit. */
	public static final int MAX_ENERGY = 50_000;
	public static final int REACTOR_CORE_ENERGY = 8_000;
	private static final int TRICKLE_PER_TICK = 6;

	private final NonNullList<ItemStack> pieces = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	private int storedEnergy;
	private UUID owner;
	private long lastRegistrySync = Long.MIN_VALUE;

	public final ContainerData data = new ContainerData() {
		@Override
		public int get(int i) {
			return switch (i) {
				case 0 -> Math.round(suitEnergy()) / 8;
				case 1 -> Math.max(1, Math.round(suitCapacity())) / 8;
				// integrity as a 0..100 percentage -- the max differs per mark now ("changes 13"), so the
				// screen can't divide a raw value by a fixed constant any more.
				case 2 -> Math.round(100f * suitIntegrity() / Math.max(1f, IronManEnergy.maxIntegrity(storedSuitId())));
				case 3 -> storedEnergy / 8;
				default -> 0;
			};
		}

		@Override
		public void set(int i, int v) {
			if (i == 3) {
				storedEnergy = v * 8;
			}
		}

		@Override
		public int getCount() {
			return 4;
		}
	};

	public IronManSuitPlatformBlockEntity(BlockPos pos, BlockState state) {
		super(IronManBlocks.SUIT_PLATFORM_BE, pos, state);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, IronManSuitPlatformBlockEntity be) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		if (be.owner == null) {
			be.tryAdoptOwner(serverLevel);
		}

		String suitId = be.storedSuitId();
		if (suitId != null) {
			IronManSuit suit = IronManSuits.byId(suitId);
			// "changes 15"/"changes 16": a suit docked on a platform reloads its one-shot wrist laser
			// (Mark 4, or a Mark 7 that has it bound on the weapon wheel).
			if (be.owner != null) {
				ServerPlayer o = serverLevel.getServer().getPlayerList().getPlayer(be.owner);
				if (o != null) {
					TonyStark.setWristLaserSpent(o, suitId, false);
				}
			}
			if (suit != null) {
				// v0.6.2: the rack charges + repairs at a flat 0.1% of the mark's pool per second.
				float cap = suit.energyCapacity();
				float cur = be.suitEnergy();
				if (cur < cap) {
					float add = Math.min(cap - cur, IronManEnergy.platformEnergyPerSecond(suit) / 20f);
					// The buffer (a Reactor Core's charge) is drawn on first as fuel, up to what this
					// tick actually needs; whatever it can't cover comes free, so running the buffer dry
					// never stalls the charge -- it only means less of it came "from the reactor core"
					// this tick.
					be.storedEnergy -= Math.round(Math.min(be.storedEnergy, add));
					be.stampAllPieces(cur + add, be.suitIntegrity());
				}
				float integ = be.suitIntegrity();
				float maxInteg = IronManEnergy.maxIntegrity(suitId);
				if (integ < maxInteg) {
					be.stampAllPieces(be.suitEnergy(),
							Math.min(maxInteg, integ + IronManEnergy.platformIntegrityPerSecond(suit) / 20f));
				}
			}
		}
		// keep the buffer topped up slowly for free while a suit rests here
		if (suitId != null && be.storedEnergy < MAX_ENERGY) {
			be.storedEnergy = Math.min(MAX_ENERGY, be.storedEnergy + TRICKLE_PER_TICK);
		}

		if (level.getGameTime() - be.lastRegistrySync >= 40) {
			be.syncRegistry(serverLevel);
			be.setChanged();
		}

		// A suit whose wearer died flies itself home ("changes 10"): the moment this platform's chunk
		// is loaded and ticking, pull in anything the return queue has waiting for this position.
		com.herocraft.mod.ironman.data.StarkSuitReturnQueue returns =
				com.herocraft.mod.ironman.data.StarkSuitReturnQueue.get(serverLevel);
		if (!returns.isEmpty() && returns.hasPendingFor(serverLevel, pos)) {
			returns.absorb(serverLevel, pos, be);
			be.afterContentsChanged();
		}
	}

	private void tryAdoptOwner(ServerLevel level) {
		for (ServerPlayer p : level.getPlayers(p -> p.blockPosition().closerThan(worldPosition, 6.0) && TonyStark.hasPower(p))) {
			owner = p.getUUID();
			syncRegistry(level);
			setChanged();
			return;
		}
	}

	public void bindTo(UUID player) {
		this.owner = player;
		setChanged();
		// Push the new owner straight into the registry rather than waiting for the next 40-tick sync --
		// a platform placed and immediately walked away from must still be callable while unloaded.
		if (level instanceof ServerLevel sl) {
			syncRegistry(sl);
		}
	}

	public Optional<UUID> owner() {
		return Optional.ofNullable(owner);
	}

	private void syncRegistry(ServerLevel level) {
		lastRegistrySync = level.getGameTime();
		StarkPlatformRegistry.get(level).put(level, worldPosition, Optional.ofNullable(owner),
				storedSuitId() == null ? "" : storedSuitId(), pieceMask(), suitEnergy(), suitIntegrity());
	}

	public int pieceMask() {
		int mask = 0;
		if (!pieces.get(0).isEmpty()) mask |= 1;   // HELMET
		if (!pieces.get(1).isEmpty()) mask |= 2;   // CHESTPLATE
		if (!pieces.get(2).isEmpty()) mask |= 4;   // LEGGINGS
		if (!pieces.get(3).isEmpty()) mask |= 8;   // BOOTS
		return mask;
	}

	/** The stored suit's carried energy -- read from the chestplate, else any present piece. */
	public float suitEnergy() {
		String suitId = storedSuitId();
		if (suitId == null) {
			return 0f;
		}
		ItemStack ref = referencePiece();
		return ref == null ? 0f : IronManEnergy.stackEnergy(ref, suitId);
	}

	public float suitIntegrity() {
		String suitId = storedSuitId();
		if (suitId == null) {
			return IronManEnergy.MAX_INTEGRITY;
		}
		ItemStack ref = referencePiece();
		return ref == null ? IronManEnergy.maxIntegrity(suitId) : IronManEnergy.stackIntegrity(ref, suitId);
	}

	public float suitCapacity() {
		IronManSuit suit = storedSuitId() == null ? null : IronManSuits.byId(storedSuitId());
		return suit == null ? MAX_ENERGY : suit.energyCapacity();
	}

	private ItemStack referencePiece() {
		if (pieces.get(1).getItem() instanceof IronManArmorItem) {
			return pieces.get(1);
		}
		for (ItemStack s : pieces) {
			if (s.getItem() instanceof IronManArmorItem) {
				return s;
			}
		}
		return null;
	}

	private void stampAllPieces(float energy, float integrity) {
		for (ItemStack s : pieces) {
			if (s.getItem() instanceof IronManArmorItem) {
				IronManEnergy.stampStack(s, energy, integrity);
			}
		}
	}

	public int storedEnergy() {
		return storedEnergy;
	}

	public void addEnergy(int amount) {
		storedEnergy = Math.min(MAX_ENERGY, storedEnergy + amount);
		setChanged();
	}

	public String storedSuitId() {
		for (ItemStack stack : pieces) {
			if (stack.getItem() instanceof IronManArmorItem piece) {
				return piece.suitId();
			}
		}
		return null;
	}

	public boolean isEmptyPlatform() {
		return pieces.stream().allMatch(ItemStack::isEmpty);
	}

	public boolean isFull() {
		return pieces.stream().noneMatch(ItemStack::isEmpty);
	}

	/** Store one Iron Man armour piece; returns true if accepted. */
	public boolean store(ItemStack stack) {
		if (!(stack.getItem() instanceof IronManArmorItem piece)) {
			return false;
		}
		int idx = slotOf(piece.getType());
		if (idx < 0 || !pieces.get(idx).isEmpty() || !matchesStoredSuit(piece.suitId())) {
			return false;
		}
		pieces.set(idx, stack.split(1));
		afterContentsChanged();
		return true;
	}

	/** Deploy the stored suit onto the player, then recharge + repair it. */
	public boolean deployTo(ServerPlayer player) {
		if (isEmptyPlatform() || !TonyStark.hasPower(player)) {
			return false;
		}
		if (owner == null) {
			owner = player.getUUID();
		}
		String suitId = storedSuitId();
		// adopt the armour's carried charge first, then top it up from the platform buffer
		ItemStack ref = referencePiece();
		float storedIntegrity = suitIntegrity();
		if (ref != null) {
			IronManEnergy.loadFromStack(player, suitId, ref);
		}
		for (int i = 0; i < SIZE; i++) {
			ItemStack stack = pieces.get(i);
			if (stack.getItem() instanceof IronManArmorItem piece && piece.suitId().equals(suitId)) {
				IronManSuitUpManager.receivePart(player, suitId, piece.getType());
				// Empty only what was actually handed over. A blanket pieces.clear() here deleted any
				// piece belonging to a DIFFERENT mark that had been racked alongside this one (older
				// saves can still hold such a mix; canPlaceItem/store now refuse to create a new one).
				pieces.set(i, ItemStack.EMPTY);
			}
		}
		IronManSuit suit = IronManSuits.byId(suitId);
		if (suit != null) {
			float cap = suit.energyCapacity();
			IronManEnergy.setEnergy(player, suitId, Math.min(cap, IronManEnergy.energy(player, suitId) + storedEnergy));
		}
		// "changes 22": hand over the integrity the rack has ACTUALLY repaired back into the suit. This
		// used to force it to maximum, which made the repair rate -- whatever it was set to -- entirely
		// cosmetic: any damaged suit came off the rack in perfect condition the instant you clicked
		// Deploy. With the rate now tied to the suit's own reactor, that shortcut has to go.
		IronManEnergy.setIntegrity(player, suitId, storedIntegrity);
		storedEnergy = 0;
		afterContentsChanged();
		return true;
	}

	/** Pull the player's currently worn suit back into the platform. */
	public boolean retrieveFrom(ServerPlayer player) {
		String suitId = IronManArmor.wornSuitId(player);
		if (suitId == null) {
			return false;
		}
		if (owner == null) {
			owner = player.getUUID();
		}
		boolean any = false;
		float energy = IronManEnergy.energy(player, suitId);
		float integrity = IronManEnergy.integrity(player, suitId);
		for (EquipmentSlot slot : new EquipmentSlot[] {
				EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			ItemStack stack = player.getItemBySlot(slot);
			if (stack.getItem() instanceof IronManArmorItem piece && piece.suitId().equals(suitId)) {
				int idx = slotOf(piece.getType());
				if (idx >= 0 && pieces.get(idx).isEmpty()) {
					ItemStack copy = stack.copy();
					IronManEnergy.stampStack(copy, energy, integrity);
					pieces.set(idx, copy);
					player.setItemSlot(slot, ItemStack.EMPTY);
					any = true;
				}
			}
		}
		if (any) {
			TonyStark.setActiveSuit(player, "");
			afterContentsChanged();
		}
		return any;
	}

	private static int slotOf(ArmorItem.Type type) {
		return switch (type) {
			case HELMET -> 0;
			case CHESTPLATE -> 1;
			case LEGGINGS -> 2;
			case BOOTS -> 3;
			default -> -1;
		};
	}

	public NonNullList<ItemStack> pieces() {
		return pieces;
	}

	/** Does this platform hold that exact piece? */
	public boolean holds(String suitId, ArmorItem.Type type) {
		int idx = slotOf(type);
		return idx >= 0 && pieces.get(idx).getItem() instanceof IronManArmorItem p && p.suitId().equals(suitId);
	}

	/** Remove that piece so a courier entity can carry it to its owner. Returns true if it was there. */
	public boolean takePiece(String suitId, ArmorItem.Type type) {
		if (!holds(suitId, type)) {
			return false;
		}
		pieces.set(slotOf(type), ItemStack.EMPTY);
		afterContentsChanged();
		return true;
	}

	/** The carried charge on a still-stored piece (so a courier can hand the real values across). */
	public float pieceEnergy(String suitId) {
		return suitEnergy();
	}

	public float pieceIntegrity() {
		return suitIntegrity();
	}

	private void afterContentsChanged() {
		setChanged();
		if (level instanceof ServerLevel sl) {
			syncRegistry(sl);
			// "changes 16": push the new contents to every client watching this chunk, so the Hall-of-
			// Armor display always matches what is actually racked -- it used to only update the moment
			// the platform GUI was opened (menu slot sync), so a called-away suit stayed on show and a
			// full platform showed empty after a relog.
			sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
					net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
		}
	}

	// ---------------- Container ----------------

	@Override public int getContainerSize() { return SIZE; }
	@Override public boolean isEmpty() { return isEmptyPlatform(); }
	@Override public ItemStack getItem(int slot) { return pieces.get(slot); }

	@Override
	public ItemStack removeItem(int slot, int amount) {
		ItemStack r = ContainerHelper.removeItem(pieces, slot, amount);
		afterContentsChanged();
		return r;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return ContainerHelper.takeItem(pieces, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		pieces.set(slot, stack);
		afterContentsChanged();
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return stack.getItem() instanceof IronManArmorItem piece && slotOf(piece.getType()) == slot
				&& matchesStoredSuit(piece.suitId());
	}

	/**
	 * A rack holds <em>one</em> suit. Letting marks be mixed on a single platform made every
	 * "the stored suit" reading (energy, integrity, the registry entry the call system reads) describe
	 * whichever piece happened to sit in the lowest slot, and made deploying it ambiguous.
	 */
	private boolean matchesStoredSuit(String suitId) {
		String stored = storedSuitId();
		return stored == null || stored.equals(suitId);
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player) && TonyStark.hasPower(player);
	}

	@Override
	public void clearContent() {
		pieces.clear();
	}

	/**
	 * <b>Deliberately does not touch {@link StarkPlatformRegistry}.</b> {@code setRemoved()} does not
	 * mean "this platform was broken" -- vanilla also calls it on every block entity in a chunk when
	 * that chunk <em>unloads</em> ({@code LevelChunk.clearAllBlockEntities}, from
	 * {@code ServerLevel.unload}). This used to drop the registry entry there, which deleted the one
	 * record that exists precisely so a suit can be called off a platform in an unloaded chunk: walk
	 * far enough away for the chunk to unload and the platform became invisible to
	 * {@link com.herocraft.mod.ironman.suit.IronManSuitCall}, so the C-key picker stopped listing the
	 * suit and the double-tap call reported "no suit available" -- the exact opposite of what the
	 * registry is for.
	 *
	 * <p>Genuine removal is handled where it can actually be distinguished: {@code onRemove} on
	 * {@link IronManSuitPlatformBlock}, which only fires when the block itself changes. A stale entry
	 * for a platform that somehow vanished without that hook running is still self-healing -- the call
	 * path force-loads the chunk, finds no platform, and drops the record itself.
	 */
	@Override
	public void setRemoved() {
		super.setRemoved();
	}

	// ---------------- menu ----------------

	@Override
	public Component getDisplayName() {
		return Component.translatable("container.herocraft.iron_man_suit_platform");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
		return new IronManSuitPlatformMenu(syncId, inv, this, data);
	}

	@Override
	public BlockPos getScreenOpeningData(ServerPlayer player) {
		return worldPosition;
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		pieces.clear();
		ContainerHelper.loadAllItems(tag, pieces, registries);
		storedEnergy = tag.getInt("StoredEnergy");
		owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		ContainerHelper.saveAllItems(tag, pieces, registries);
		tag.putInt("StoredEnergy", storedEnergy);
		if (owner != null) {
			tag.putUUID("Owner", owner);
		}
	}

	// "changes 16": send the full state to the client on chunk load AND on every sendBlockUpdated, so
	// the BlockEntityRenderer's stored-suit display is always correct without the GUI ever being opened.

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		saveAdditional(tag, registries);
		return tag;
	}

	@Override
	public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
		return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
	}
}
