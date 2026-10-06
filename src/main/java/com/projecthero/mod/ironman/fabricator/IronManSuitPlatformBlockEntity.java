package com.projecthero.mod.ironman.fabricator;

import java.util.Optional;
import java.util.UUID;

import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.data.StarkPlatformRegistry;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;

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
 *   <li>v0.15.4: suiting up and down happens on a Stark Gantry Floor within 20 blocks
 *       ({@link com.projecthero.mod.ironman.gantry.StarkGantry}), which takes the suit off / hands it back to this rack.</li>
 * </ul>
 */
public class IronManSuitPlatformBlockEntity extends BlockEntity
		implements Container, ExtendedScreenHandlerFactory<BlockPos> {
	public static final int SIZE = 4;
	/**
	 * v0.14.27: every docked suit -- any mark -- charges 10 energy and repairs 10 integrity per second. The platform's
	 * own Reactor-Core reserve is gone (an old save's {@code StoredEnergy} key is simply ignored).
	 */
	public static final float REGEN_ENERGY_PER_SECOND = 10f;
	public static final float REGEN_INTEGRITY_PER_SECOND = 10f;
	/** Fallback capacity shown for an empty rack. */
	public static final int MAX_ENERGY = 50_000;

	private final NonNullList<ItemStack> pieces = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	private UUID owner;
	/**
	 * v0.15.8: the racked Mark 5 folding itself into its case -- the game time it started (0 = not packing) and who gets
	 * the case. Saved and synced, so every viewer sees the fold; the rack is locked until it is done.
	 */
	private long packStart;
	private UUID packFor;
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
				default -> 0;
			};
		}

		@Override
		public void set(int i, int v) {
		}

		@Override
		public int getCount() {
			return 3;
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

		if (be.packStart > 0L && level.getGameTime() - be.packStart >= PACK_TICKS) {
			be.finishPack(serverLevel);
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
				// v0.14.27: a flat 10 energy/s + 10 integrity/s for every mark (overrides any per-mark platformRegen)
				be.regenTick(suit);
			}
		}

		if (level.getGameTime() - be.lastRegistrySync >= 40) {
			be.syncRegistry(serverLevel);
			be.setChanged();
		}

		// A suit whose wearer died flies itself home ("changes 10"): the moment this platform's chunk
		// is loaded and ticking, pull in anything the return queue has waiting for this position.
		com.projecthero.mod.ironman.data.StarkSuitReturnQueue returns =
				com.projecthero.mod.ironman.data.StarkSuitReturnQueue.get(serverLevel);
		if (!returns.isEmpty() && returns.hasPendingFor(serverLevel, pos)) {
			returns.absorb(serverLevel, pos, be);
			be.afterContentsChanged();
		}
	}

	/** One server tick of the v0.14.27 flat platform charge + repair (public for the gametests). */
	public void regenTick(IronManSuit suit) {
		String suitId = storedSuitId();
		if (suit == null || suitId == null) {
			return;
		}
		float cap = suit.energyCapacity();
		float cur = suitEnergy();
		float integ = suitIntegrity();
		float maxInteg = IronManEnergy.maxIntegrity(suitId);
		if (cur >= cap && integ >= maxInteg) {
			return;
		}
		stampAllPieces(Math.min(cap, Math.max(cur, cur + REGEN_ENERGY_PER_SECOND / 20f)),
				Math.min(maxInteg, Math.max(integ, integ + REGEN_INTEGRITY_PER_SECOND / 20f)));
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

	public String storedSuitId() {
		if (packStart > 0L) {
			return null; // v0.15.8: folding into its case -- nothing here can be called, deployed or claimed
		}
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

	/**
	 * v0.14.30, explicit user request: a Mark 5 Suitcase handed to the platform (right-click it with the case, Sneak +
	 * right-click "retrieve" while carrying one, or shift-click it in the platform screen) unfolds into the four armour
	 * pieces on the rack. All-or-nothing: nothing moves unless every piece has a free slot on a rack that can hold the
	 * Mark 5. A never-used (legacy) case unfolds a fresh suit. Uses up the case.
	 */
	public boolean storeSuitcase(Player player, ItemStack caseStack) {
		if (!caseStack.is(com.projecthero.mod.ironman.item.IronManItems.MARK_V_SUITCASE)
				|| !matchesStoredSuit("mark_v")) {
			return false;
		}
		java.util.List<ItemStack> contents = new java.util.ArrayList<>();
		if (com.projecthero.mod.ironman.item.SuitcaseContents.isLegacyEmpty(caseStack)) {
			for (ArmorItem.Type t : new ArmorItem.Type[] { ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS,
					ArmorItem.Type.BOOTS }) {
				contents.add(new ItemStack(com.projecthero.mod.ironman.item.IronManItems.armor("mark_v", t)));
			}
		} else {
			contents.addAll(com.projecthero.mod.ironman.item.SuitcaseContents.nonEmpty(caseStack));
		}
		if (contents.isEmpty()) {
			return false;
		}
		for (ItemStack piece : contents) {
			if (!(piece.getItem() instanceof IronManArmorItem a) || slotOf(a.getType()) < 0 || !pieces.get(slotOf(a.getType())).isEmpty()) {
				return false;
			}
		}
		for (ItemStack piece : contents) {
			pieces.set(slotOf(((IronManArmorItem) piece.getItem()).getType()), piece.copyWithCount(1));
		}
		if (owner == null) {
			owner = player.getUUID();
		}
		caseStack.shrink(1);
		afterContentsChanged();
		return true;
	}

	/** v0.15.8: the fold -- the Mark 5 suit-down played backwards on the rack at double speed (frames 134 -> 26)... */
	public static final int PACK_FOLD_TICKS = 54;
	/** ...then the case forms at the chest, drops to the pad and hops to the player. */
	public static final int PACK_TICKS = PACK_FOLD_TICKS + 16;

	/** v0.15.8: is the racked Mark 5 folding into its case right now? */
	public boolean packing() {
		return packStart > 0L;
	}

	/** v0.15.8: ticks into the fold (client: with the partial tick), or -1 when not packing. */
	public float packAge(float partialTick) {
		if (packStart <= 0L || level == null) {
			return -1f;
		}
		return Math.max(0f, level.getGameTime() - packStart + partialTick);
	}

	/**
	 * v0.15.6, explicit user request: the reverse of {@link #storeSuitcase} -- fold every racked Mark 5 piece back into a
	 * Mark 5 Suitcase and hand it to {@code player}. v0.15.8, user request: it is animated -- the suit folds itself up on
	 * the rack and the case is handed over {@link #PACK_TICKS} later ({@link #finishPack}); the rack is locked meanwhile.
	 * The pieces keep their own charge and integrity inside the case. False (nothing happens) unless a Mark 5 is racked
	 * here, nothing is packing yet and the player may use this platform.
	 */
	public boolean packSuitcase(net.minecraft.server.level.ServerPlayer player) {
		if (!"mark_v".equals(storedSuitId()) || owner != null && !owner.equals(player.getUUID()) || level == null) {
			return false;
		}
		packStart = Math.max(1L, level.getGameTime());
		packFor = player.getUUID();
		afterContentsChanged();
		return true;
	}

	/** v0.15.8: the fold is over -- the pieces go into a case for whoever asked (their pack, else dropped on the pad). */
	public void finishPack(ServerLevel level) {
		UUID to = packFor;
		packStart = 0L;
		packFor = null;
		ItemStack caseStack = new ItemStack(com.projecthero.mod.ironman.item.IronManItems.MARK_V_SUITCASE);
		NonNullList<ItemStack> slots = NonNullList.withSize(com.projecthero.mod.ironman.item.SuitcaseContents.SLOTS, ItemStack.EMPTY);
		for (int i = 0; i < SIZE; i++) {
			ItemStack s = pieces.get(i);
			if (s.getItem() instanceof IronManArmorItem a) {
				slots.set(com.projecthero.mod.ironman.item.SuitcaseContents.slotOf(a.getType()), s.copyWithCount(1));
			}
		}
		com.projecthero.mod.ironman.item.SuitcaseContents.write(caseStack, slots);
		for (int i = 0; i < SIZE; i++) {
			pieces.set(i, ItemStack.EMPTY);
		}
		afterContentsChanged();
		ServerPlayer player = to == null ? null : level.getServer().getPlayerList().getPlayer(to);
		if (player != null && player.level() == level && player.blockPosition().closerThan(worldPosition, 24.0)) {
			if (!player.getInventory().add(caseStack)) {
				player.drop(caseStack, false);
			}
			com.projecthero.mod.ironman.IronManSounds.play(player, com.projecthero.mod.ironman.IronManSounds.SERVO, 0.8f, 1.4f);
			return;
		}
		net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(level,
				worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, caseStack);
		drop.setDefaultPickUpDelay();
		level.addFreshEntity(drop);
	}

	// ---------------- v0.15.4: suiting up / down is the Stark Gantry's job ----------------
	//
	// The platform's own deploy / retrieve (v0.14.21 flying pieces, v0.15.1 robotic-arm deploy, v0.15.3 robotic-arm
	// retrieve) is gone. Explicit user request: a 5x5 Stark Gantry Floor within 20 blocks lists every racked suit; picking
	// one moves the whole suit off this rack into the floor in one tick (takeAllPieces) and the gantry's arms fit it.
	// Taking it off on the gantry hands each piece back here (store) once the arms have lowered it into the floor.

	/** The movement lock placed on a wearer while a gantry works on them (transient: never saved, gone on relog). */
	public static final net.minecraft.resources.ResourceLocation FREEZE_ID =
			net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("projecthero", "platform_suit_up_freeze");

	/**
	 * v0.15.4: hand every racked piece to a Stark Gantry in one tick (the real stacks, slot-indexed 0 HEAD .. 3 FEET).
	 * The rack is empty afterwards; nothing is copied.
	 */
	public ItemStack[] takeAllPieces() {
		ItemStack[] out = new ItemStack[SIZE];
		for (int i = 0; i < SIZE; i++) {
			out[i] = pieces.get(i);
			pieces.set(i, ItemStack.EMPTY);
		}
		afterContentsChanged();
		return out;
	}

	/** v0.15.4: could this rack take {@code suitId}'s pieces in the given rack slots (bit i = slot i) right now? */
	public boolean canAccept(String suitId, int rackMask) {
		if (!matchesStoredSuit(suitId)) {
			return false;
		}
		for (int i = 0; i < SIZE; i++) {
			if ((rackMask & (1 << i)) != 0 && !pieces.get(i).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** Lock (or release) a player's walking and jumping -- used by the Stark Gantry. Transient modifiers: never saved. */
	public static void setFrozen(ServerPlayer player, boolean frozen) {
		for (net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> a : java.util.List.of(
				net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,
				net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH)) {
			net.minecraft.world.entity.ai.attributes.AttributeInstance inst = player.getAttribute(a);
			if (inst == null) {
				continue;
			}
			if (!frozen) {
				inst.removeModifier(FREEZE_ID);
			} else if (!inst.hasModifier(FREEZE_ID)) {
				inst.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(FREEZE_ID, -1.0,
						net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		}
	}

	public static boolean isFrozen(ServerPlayer player) {
		net.minecraft.world.entity.ai.attributes.AttributeInstance inst =
				player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
		return inst != null && inst.hasModifier(FREEZE_ID);
	}

	/**
	 * Safety net, called every player tick from {@code IronManSuitUpManager.tick}: a movement lock left behind with no
	 * suit-up running (the gantry's chunk unloaded mid-sequence, a crash between ticks...) is lifted. A gantry sequence
	 * always holds the player's suit-up state, and that hold expires on its own, so this can never strand anyone.
	 */
	public static void releaseStrayFreeze(ServerPlayer player) {
		if (isFrozen(player)) {
			setFrozen(player, false);
		}
	}

	/** Rack slot (0 HEAD, 1 CHEST, 2 LEGS, 3 FEET) of an armour type, or -1. */
	public static int slotOf(ArmorItem.Type type) {
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
		return !takePieceStack(suitId, type).isEmpty();
	}

	/**
	 * v0.14.21: remove and RETURN the real stack in that slot (EMPTY if it is not there). (v0.15.4: a Stark Gantry takes
	 * the whole suit off the rack the moment it starts, so there is never a half-deployed rack to steal from.)
	 */
	public ItemStack takePieceStack(String suitId, ArmorItem.Type type) {
		if (!holds(suitId, type)) {
			return ItemStack.EMPTY;
		}
		ItemStack out = pieces.get(slotOf(type));
		pieces.set(slotOf(type), ItemStack.EMPTY);
		afterContentsChanged();
		return out;
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
		if (packStart > 0L) {
			return ItemStack.EMPTY; // v0.15.8: locked while it folds into its case
		}
		ItemStack r = ContainerHelper.removeItem(pieces, slot, amount);
		afterContentsChanged();
		return r;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		if (packStart > 0L) {
			return ItemStack.EMPTY;
		}
		return ContainerHelper.takeItem(pieces, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		pieces.set(slot, stack);
		afterContentsChanged();
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return packStart <= 0L && stack.getItem() instanceof IronManArmorItem piece && slotOf(piece.getType()) == slot
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
	 * {@link com.projecthero.mod.ironman.suit.IronManSuitCall}, so the C-key picker stopped listing the
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
		return Component.translatable("container.projecthero.iron_man_suit_platform");
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
		owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
		packStart = tag.getLong("PackStart");
		packFor = tag.hasUUID("PackFor") ? tag.getUUID("PackFor") : null;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		ContainerHelper.saveAllItems(tag, pieces, registries);
		if (owner != null) {
			tag.putUUID("Owner", owner);
		}
		if (packStart > 0L) {
			tag.putLong("PackStart", packStart);
			if (packFor != null) {
				tag.putUUID("PackFor", packFor);
			}
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
