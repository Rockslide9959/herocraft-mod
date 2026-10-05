package com.projecthero.mod.ironman.fabricator;

import java.util.Optional;
import java.util.UUID;

import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.data.StarkPlatformRegistry;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
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
		if (be.seqMode != SEQ_NONE) {
			be.tickSequence(serverLevel);
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

	// ---------------- v0.14.21: animated deploy / retrieve ----------------
	//
	// Both used to be instant. They are now a ~1.5 s server-timed sequence: on deploy each piece lifts off the rack,
	// flies onto the player and locks on (boots -> legs -> chest -> helmet); on retrieve each piece breaks away from the
	// player and settles back on the rack (helmet first). A piece moves between rack and body in exactly one tick, so at
	// every instant it is in exactly one place: walking away, logging out, dying or breaking the platform mid-sequence
	// just stops it -- never a loss, never a duplicate. The client BER reads the synced sequence (mode, start tick,
	// player) and the same timing functions below to draw the pieces in flight.

	public static final int SEQ_NONE = 0;
	public static final int SEQ_DEPLOY = 1;
	public static final int SEQ_RETRIEVE = 2;
	/** Ticks before the first piece lifts off. */
	public static final int SEQ_LEAD = 2;
	/** Ticks between one piece and the next. */
	public static final int SEQ_STEP = 6;
	/** Ticks a piece spends flying between rack and body. */
	public static final int SEQ_FLIGHT = 8;
	/** The player must stay this close for the sequence to continue. */
	public static final double SEQ_RANGE = 5.0;
	private static final int[] DEPLOY_ORDER = { 3, 2, 1, 0 };   // boots, legs, chest, helmet (bits = rack slots)
	private static final int[] RETRIEVE_ORDER = { 0, 1, 2, 3 }; // helmet first

	private int seqMode = SEQ_NONE;
	private long seqStart;
	private UUID seqPlayer;
	private int seqPlayerEntity = -1;
	private String seqSuit = "";
	/** rack slots taking part, in order */
	private int[] seqSlots = new int[0];
	/** rack slots already handed over (deploy) / released (retrieve) / moved onto the rack (retrieve) */
	private int seqDoneMask;
	private int seqReleasedMask;

	public int seqMode() { return seqMode; }
	public long seqStart() { return seqStart; }
	public int seqPlayerEntity() { return seqPlayerEntity; }
	public int[] seqSlots() { return seqSlots; }

	/** Tick (after the start) at which the i-th piece of a deploy lifts off the rack. */
	public static int deployLiftTick(int i) {
		return SEQ_LEAD + i * SEQ_STEP;
	}

	/** Tick at which the i-th piece of a deploy reaches the body and is equipped. */
	public static int deployEquipTick(int i) {
		return deployLiftTick(i) + SEQ_FLIGHT;
	}

	/** Tick at which the i-th piece of a retrieve starts breaking away from the body. */
	public static int retrieveReleaseTick(int i) {
		return i * SEQ_STEP;
	}

	/** Tick at which the i-th piece of a retrieve leaves the body (onto the rack, drawn flying home for SEQ_FLIGHT). */
	public static int retrieveMoveTick(int i) {
		return retrieveReleaseTick(i) + com.projecthero.mod.ironman.suit.IronManSuitFx.RELEASE_TICKS;
	}

	public static int sequenceLength(int mode, int pieces) {
		int last = Math.max(0, pieces - 1);
		return mode == SEQ_DEPLOY ? deployEquipTick(last) : retrieveMoveTick(last) + SEQ_FLIGHT;
	}

	public boolean sequenceRunning() {
		return seqMode != SEQ_NONE;
	}

	/** Start deploying the stored suit onto the player (recharged from the buffer). False if it cannot start. */
	public boolean deployTo(ServerPlayer player) {
		if (isEmptyPlatform() || !TonyStark.hasPower(player) || sequenceRunning()
				|| IronManSuitUpManager.inTransition(player) || !(level instanceof ServerLevel)) {
			return false;
		}
		if (owner == null) {
			owner = player.getUUID();
		}
		String suitId = storedSuitId();
		// adopt the armour's carried charge -- what the rack has actually charged / repaired (v0.14.27: no reserve top-up)
		ItemStack ref = referencePiece();
		float storedIntegrity = suitIntegrity();
		if (ref != null) {
			IronManEnergy.loadFromStack(player, suitId, ref);
		}
		// "changes 22": hand over the integrity the rack has ACTUALLY repaired back into the suit (never a free repair).
		IronManEnergy.setIntegrity(player, suitId, storedIntegrity);

		java.util.List<Integer> slots = new java.util.ArrayList<>();
		for (int idx : DEPLOY_ORDER) {
			if (pieces.get(idx).getItem() instanceof IronManArmorItem p && p.suitId().equals(suitId)) {
				slots.add(idx);
			}
		}
		beginSequence(player, SEQ_DEPLOY, suitId, slots);
		return true;
	}

	/** Start pulling the player's worn suit back onto the platform. False if it cannot start. */
	public boolean retrieveFrom(ServerPlayer player) {
		String suitId = IronManArmor.wornSuitId(player);
		if (suitId == null || sequenceRunning() || IronManSuitUpManager.inTransition(player)
				|| !(level instanceof ServerLevel) || !matchesStoredSuit(suitId)) {
			return false;
		}
		if (owner == null) {
			owner = player.getUUID();
		}
		java.util.List<Integer> slots = new java.util.ArrayList<>();
		for (int idx : RETRIEVE_ORDER) {
			EquipmentSlot slot = equipSlotOf(idx);
			if (player.getItemBySlot(slot).getItem() instanceof IronManArmorItem p && p.suitId().equals(suitId)
					&& pieces.get(idx).isEmpty()) {
				slots.add(idx);
			}
		}
		if (slots.isEmpty()) {
			return false;
		}
		if (com.projecthero.mod.ironman.IronManFlight.isFlying(player)) {
			com.projecthero.mod.ironman.IronManFlight.setFlying(player, false);
		}
		beginSequence(player, SEQ_RETRIEVE, suitId, slots);
		return true;
	}

	private void beginSequence(ServerPlayer player, int mode, String suitId, java.util.List<Integer> slots) {
		seqMode = mode;
		seqStart = level.getGameTime();
		seqPlayer = player.getUUID();
		seqPlayerEntity = player.getId();
		seqSuit = suitId;
		seqSlots = slots.stream().mapToInt(Integer::intValue).toArray();
		seqDoneMask = 0;
		seqReleasedMask = 0;
		int len = sequenceLength(mode, seqSlots.length);
		// Hold the player's suit-up state for the whole sequence so no other suit-up / suit-down can start over it.
		var s = TonyStark.state(player);
		s.transitionSuit = suitId;
		s.transitionUp = mode == SEQ_DEPLOY;
		s.transitionTotal = len + 2;
		s.transitionTicks = s.transitionTotal;
		s.transitionMask = 0;
		s.transitionReleaseMask = 0;
		com.projecthero.mod.ironman.suit.IronManSuitFx.startPose(player,
				mode == SEQ_DEPLOY ? com.projecthero.mod.ironman.suit.IronManSuitFx.POSE_SUIT_UP
						: com.projecthero.mod.ironman.suit.IronManSuitFx.POSE_SUIT_DOWN,
				len + com.projecthero.mod.ironman.suit.IronManSuitFx.LOCK_TICKS + 2,
				com.projecthero.mod.ironman.suit.IronManSuitFx.STYLE_PLATES);
		com.projecthero.mod.ironman.IronManSounds.play(player, com.projecthero.mod.ironman.IronManSounds.SERVO, 1.0f, 0.9f);
		afterContentsChanged();
	}

	/** Server tick of a running deploy / retrieve. */
	private void tickSequence(ServerLevel sl) {
		ServerPlayer player = seqPlayer == null ? null : sl.getServer().getPlayerList().getPlayer(seqPlayer);
		if (player == null || player.level() != sl || !player.isAlive() || !TonyStark.hasPower(player)
				|| player.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(worldPosition)) > SEQ_RANGE + 1.5) {
			abortSequence(player);
			return;
		}
		int t = (int) (sl.getGameTime() - seqStart);
		boolean finished = true;
		for (int i = 0; i < seqSlots.length; i++) {
			int idx = seqSlots[i];
			int bit = 1 << idx;
			EquipmentSlot slot = equipSlotOf(idx);
			if (seqMode == SEQ_DEPLOY) {
				if ((seqDoneMask & bit) != 0) {
					continue;
				}
				finished = false;
				if (t == deployLiftTick(i)) {
					com.projecthero.mod.ironman.IronManSounds.play(player, com.projecthero.mod.ironman.IronManSounds.SERVO,
							0.6f, 1.2f + 0.1f * i);
				}
				if (t >= deployEquipTick(i)) {
					seqDoneMask |= bit;
					ItemStack stack = pieces.get(idx);
					if (stack.getItem() instanceof IronManArmorItem p && p.suitId().equals(seqSuit)) {
						// one tick: off the rack and onto the body -- the real stack
						pieces.set(idx, ItemStack.EMPTY);
						if (!IronManSuitUpManager.receivePart(player, stack)) {
							pieces.set(idx, stack); // the slot already holds this suit's piece: it stays racked
						}
						afterContentsChanged();
					}
				}
			} else {
				if ((seqDoneMask & bit) != 0) {
					if (t < retrieveMoveTick(i) + SEQ_FLIGHT) {
						finished = false; // still flying home on the client
					}
					continue;
				}
				finished = false;
				if ((seqReleasedMask & bit) == 0 && t >= retrieveReleaseTick(i)) {
					seqReleasedMask |= bit;
					if (player.getItemBySlot(slot).getItem() instanceof IronManArmorItem) {
						com.projecthero.mod.ironman.suit.IronManSuitFx.markPiece(player, slot, false);
						com.projecthero.mod.ironman.IronManSounds.play(player, com.projecthero.mod.ironman.IronManSounds.RELEASE,
								0.7f, 1.0f);
					}
				}
				if (t >= retrieveMoveTick(i)) {
					seqDoneMask |= bit;
					ItemStack worn = player.getItemBySlot(slot);
					if (worn.getItem() instanceof IronManArmorItem p && p.suitId().equals(seqSuit) && pieces.get(idx).isEmpty()) {
						// one tick: off the body and onto the rack -- the real stack, charge stamped on
						ItemStack copy = worn.copy();
						IronManEnergy.stampStack(copy, IronManEnergy.energy(player, seqSuit), IronManEnergy.integrity(player, seqSuit));
						player.setItemSlot(slot, ItemStack.EMPTY);
						pieces.set(idx, copy);
						afterContentsChanged();
					}
				}
			}
		}
		if (finished) {
			endSequence(player);
		}
	}

	private void endSequence(ServerPlayer player) {
		if (seqMode == SEQ_RETRIEVE && player != null) {
			com.projecthero.mod.ironman.IronManSounds.play(player, com.projecthero.mod.ironman.IronManSounds.CLAMP, 0.6f, 0.8f);
			if (!IronManArmor.wearingAnyIronMan(player)) {
				TonyStark.setActiveSuit(player, "");
			}
		}
		releasePlayerHold(player);
		seqMode = SEQ_NONE;
		seqSlots = new int[0];
		seqPlayer = null;
		seqPlayerEntity = -1;
		afterContentsChanged();
	}

	/** Interrupted (walked away / logged out / died / power lost / block broken): stop where it is -- nothing moves. */
	public void abortSequence(ServerPlayer player) {
		if (seqMode == SEQ_NONE) {
			return;
		}
		if (player == null && seqPlayer != null && level instanceof ServerLevel sl) {
			player = sl.getServer().getPlayerList().getPlayer(seqPlayer);
		}
		if (player != null) {
			com.projecthero.mod.ironman.suit.IronManSuitFx.endPose(player);
		}
		endSequence(player);
	}

	/** Hand the player's suit-up state back (only if it is still the hold this sequence placed). */
	private void releasePlayerHold(ServerPlayer player) {
		if (player == null) {
			return;
		}
		var s = TonyStark.state(player);
		if (seqSuit.equals(s.transitionSuit) && s.transitionMask == 0 && s.transitionTicks > 1) {
			s.transitionTicks = 1; // IronManSuitUpManager.tick finishes it next tick (faceplate beat / active suit)
		}
	}

	private static EquipmentSlot equipSlotOf(int rackSlot) {
		return switch (rackSlot) {
			case 0 -> EquipmentSlot.HEAD;
			case 1 -> EquipmentSlot.CHEST;
			case 2 -> EquipmentSlot.LEGS;
			default -> EquipmentSlot.FEET;
		};
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
		return !takePieceStack(suitId, type).isEmpty();
	}

	/**
	 * v0.14.21: remove and RETURN the real stack in that slot (EMPTY if it is not there, or while a deploy / retrieve is
	 * running -- a call never steals a piece out of the middle of a sequence).
	 */
	public ItemStack takePieceStack(String suitId, ArmorItem.Type type) {
		if (!holds(suitId, type) || sequenceRunning()) {
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
		loadSequence(tag);
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		ContainerHelper.saveAllItems(tag, pieces, registries);
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
		// v0.14.21: the running deploy / retrieve, for the BER only (never written to disk -- see the sequence notes)
		tag.putInt("SeqMode", seqMode);
		tag.putLong("SeqStart", seqStart);
		tag.putInt("SeqPlayer", seqPlayerEntity);
		tag.putIntArray("SeqSlots", seqSlots);
		return tag;
	}

	/** Client: read the synced sequence alongside the contents (the server never reads these keys from disk). */
	private void loadSequence(CompoundTag tag) {
		if (level != null && level.isClientSide()) {
			seqMode = tag.getInt("SeqMode");
			seqStart = tag.getLong("SeqStart");
			seqPlayerEntity = tag.contains("SeqPlayer") ? tag.getInt("SeqPlayer") : -1;
			seqSlots = tag.getIntArray("SeqSlots");
		}
	}

	@Override
	public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
		return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
	}
}
