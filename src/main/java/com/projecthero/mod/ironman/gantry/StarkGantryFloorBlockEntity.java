package com.projecthero.mod.ironman.gantry;

import java.util.List;
import java.util.UUID;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManFaceplate;
import com.projecthero.mod.ironman.IronManSounds;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.suit.IronManSuitFx;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.4: the state of one Stark Gantry Floor tile. Only the <b>centre</b> tile of a running gantry uses it: it owns the
 * sequence (server-timed off {@link GantryTimeline}, synced to every watching client for the renderer) and the
 * <b>floor buffer</b> -- the suit pieces that are "inside the floor" between the Suit Platform and the wearer.
 *
 * <p>No-loss / no-dupe rule, same as every other suit transfer in the mod: a piece is always in exactly one place.
 * Putting a suit on moves the whole suit platform -> buffer in one tick when it starts, then each piece buffer -> armour
 * slot in one tick when its arm reaches the body. Taking it off moves each piece armour slot -> buffer in one tick, and
 * the buffer -> a Suit Platform when it ends. However a sequence stops (sneak, death, logout, walking off, a tile or
 * the source platform broken, the chunk unloading), whatever is still in the buffer goes back to a Suit Platform
 * ({@link #flushBuffer}), else to the wearer's inventory, else drops here. The buffer is saved with the chunk; the
 * sequence itself is not, so a reloaded centre just flushes its buffer and closes its hatches.
 */
public class StarkGantryFloorBlockEntity extends BlockEntity {
	public static final int MODE_NONE = 0;
	public static final int MODE_EQUIP = 1;
	public static final int MODE_UNEQUIP = 2;
	/** Boots, legs, chest, helmet (rack slots: 0 HEAD, 1 CHEST, 2 LEGS, 3 FEET -- the Suit Platform's layout). */
	static final int[] ORDER = { 3, 2, 1, 0 };

	private final NonNullList<ItemStack> buffer = NonNullList.withSize(4, ItemStack.EMPTY);
	/** The Suit Platform the suit came from (putting on) / goes to (taking off). Saved with the buffer. */
	private BlockPos platformPos;
	/** Whose suit the buffer holds -- pieces only ever go back to that player's (or an unowned) Suit Platform. Saved. */
	private UUID owner;

	private int mode = MODE_NONE;
	private long start;
	private UUID playerId;
	private int playerEntity = -1;
	private float yaw;
	private String suit = "";
	private int[] slots = new int[0];
	private int doneMask;
	private boolean openedFaceplate;

	public StarkGantryFloorBlockEntity(BlockPos pos, BlockState state) {
		super(IronManBlocks.GANTRY_FLOOR_BE, pos, state);
	}

	// ---------------- reading (both sides) ----------------

	public int mode() { return mode; }
	public boolean running() { return mode != MODE_NONE; }
	public long start() { return start; }
	public int playerEntity() { return playerEntity; }
	public float yaw() { return yaw; }
	public int[] slots() { return slots; }
	public String suit() { return suit; }
	public BlockPos platformPos() { return platformPos; }
	public ItemStack buffered(int rackSlot) { return buffer.get(rackSlot); }
	public boolean bufferEmpty() { return buffer.stream().allMatch(ItemStack::isEmpty); }
	public UUID playerId() { return playerId; }

	/** The way the wearer faces (snapped to the four directions) -- the arms stand on their left and right. */
	public Direction facing() {
		return Direction.fromYRot(yaw);
	}

	/** The point the wearer stands on: the middle of this tile's top. */
	public Vec3 standAt() {
		return Vec3.atBottomCenterOf(worldPosition.above());
	}

	/** Ticks since the sequence started. */
	public int age() {
		return level == null ? 0 : (int) (level.getGameTime() - start);
	}

	/** The world positions of the three hatch tiles: wearer's right, left, front. */
	public BlockPos[] hatches() {
		Direction f = facing();
		return new BlockPos[] { worldPosition.relative(f.getClockWise()), worldPosition.relative(f.getCounterClockWise()),
				worldPosition.relative(f) };
	}

	// ---------------- server tick ----------------

	public static void serverTick(Level level, BlockPos pos, BlockState state, StarkGantryFloorBlockEntity be) {
		if (!(level instanceof ServerLevel sl)) {
			return;
		}
		if (be.mode != MODE_NONE) {
			be.tickSequence(sl);
			return;
		}
		if (!be.bufferEmpty()) {
			// a centre reloaded mid-sequence (or anything else that left pieces in the floor): send them home
			be.flushBuffer(null);
		}
		if (state.getValue(StarkGantryFloorBlock.OPEN) && (level.getGameTime() + pos.asLong()) % 10 == 0
				&& !be.openedByRunningNeighbour(sl)) {
			// an open hatch nobody is using (its sequence's chunk unloaded / a crash): shut it
			sl.setBlock(pos, state.setValue(StarkGantryFloorBlock.OPEN, false), Block.UPDATE_ALL);
		}
	}

	private boolean openedByRunningNeighbour(ServerLevel sl) {
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (sl.getBlockEntity(worldPosition.relative(d)) instanceof StarkGantryFloorBlockEntity n && n.running()) {
				return true;
			}
		}
		return false;
	}

	// ---------------- starting ----------------

	/** Start putting the suit racked on {@code platform} onto {@code player}. Checks are done by {@link StarkGantry}. */
	void beginEquip(ServerPlayer player, IronManSuitPlatformBlockEntity platform) {
		String suitId = platform.storedSuitId();
		// adopt the armour's carried charge + the integrity the rack has actually repaired (as the platform deploy did)
		float integ = platform.suitIntegrity();
		ItemStack ref = platform.getItem(1).getItem() instanceof IronManArmorItem ? platform.getItem(1) : null; // chestplate first
		for (ItemStack s : platform.pieces()) {
			if (ref == null && s.getItem() instanceof IronManArmorItem) {
				ref = s;
			}
		}
		if (ref != null) {
			IronManEnergy.loadFromStack(player, suitId, ref);
		}
		IronManEnergy.setIntegrity(player, suitId, integ);
		// the whole suit goes off the rack and into the floor in one tick
		ItemStack[] taken = platform.takeAllPieces();
		java.util.List<Integer> order = new java.util.ArrayList<>();
		for (int idx : ORDER) {
			buffer.set(idx, taken[idx]);
			if (taken[idx].getItem() instanceof IronManArmorItem p && p.suitId().equals(suitId)) {
				order.add(idx);
			}
		}
		platformPos = platform.getBlockPos();
		begin(player, MODE_EQUIP, suitId, order);
	}

	/** Start taking {@code player}'s worn suit off, to be racked on {@code target}. */
	void beginUnequip(ServerPlayer player, String suitId, IronManSuitPlatformBlockEntity target) {
		java.util.List<Integer> order = new java.util.ArrayList<>();
		for (int idx : ORDER) { // put-on order, played backwards: the helmet comes off first
			if (player.getItemBySlot(equipSlotOf(idx)).getItem() instanceof IronManArmorItem p && p.suitId().equals(suitId)) {
				order.add(idx);
			}
		}
		platformPos = target.getBlockPos();
		if (com.projecthero.mod.ironman.IronManFlight.isFlying(player)) {
			com.projecthero.mod.ironman.IronManFlight.setFlying(player, false);
		}
		begin(player, MODE_UNEQUIP, suitId, order);
	}

	private void begin(ServerPlayer player, int newMode, String suitId, List<Integer> order) {
		ServerLevel sl = (ServerLevel) level;
		mode = newMode;
		start = sl.getGameTime();
		playerId = player.getUUID();
		owner = playerId;
		playerEntity = player.getId();
		suit = suitId;
		slots = order.stream().mapToInt(Integer::intValue).toArray();
		doneMask = 0;
		openedFaceplate = false;
		yaw = Direction.fromYRot(player.getYRot()).toYRot();

		// onto the middle of the lift, facing along the floor's grid
		player.stopRiding();
		Vec3 at = standAt();
		player.teleportTo(sl, at.x, at.y, at.z, yaw, Mth.clamp(player.getXRot(), -10f, 25f));
		player.setDeltaMovement(Vec3.ZERO);
		player.fallDistance = 0f;
		lockFacing(player, true);
		IronManSuitPlatformBlockEntity.setFrozen(player, true);

		// hold the player's suit-up state for the whole sequence so no other suit-up / suit-down can start over it
		// (while transitionUp is set the suit is "assembling": abilities and flight stay locked and nothing hurts them)
		var s = TonyStark.state(player);
		s.transitionSuit = suitId;
		s.transitionUp = newMode == MODE_EQUIP;
		s.transitionTotal = GantryTimeline.TOTAL + 2;
		s.transitionTicks = s.transitionTotal;
		s.transitionMask = 0;
		s.transitionReleaseMask = 0;
		s.transitionPlan = 0;
		IronManSuitFx.startPose(player, newMode == MODE_EQUIP ? IronManSuitFx.POSE_PLATFORM : IronManSuitFx.POSE_PLATFORM_OFF,
				GantryTimeline.TOTAL, IronManSuitFx.STYLE_PLATES, Math.max(0, slots.length - 1));
		IronManSounds.play(player, IronManSounds.SERVO, 1.0f, 0.7f);
		if (newMode == MODE_EQUIP) {
			IronManSounds.play(player, IronManSounds.HUD_ON, 0.6f, 0.9f);
		}
		player.displayClientMessage(Component.translatable(newMode == MODE_EQUIP ? "message.projecthero.gantry.equipping"
				: "message.projecthero.gantry.unequipping").withStyle(ChatFormatting.AQUA), true);
		sl.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.7f, 0.7f);
		setOpen(sl, true);
		changed();
	}

	// ---------------- running ----------------

	private void tickSequence(ServerLevel sl) {
		ServerPlayer player = playerId == null ? null : sl.getServer().getPlayerList().getPlayer(playerId);
		if (player == null || player.level() != sl || !player.isAlive() || player.isSpectator() || !TonyStark.hasPower(player)
				|| strayed(player)) {
			abort(player);
			return;
		}
		int t = age();
		if (t % 10 == 0 && !StarkGantry.complete(sl, worldPosition)) {
			player.displayClientMessage(Component.translatable("message.projecthero.gantry.broken").withStyle(ChatFormatting.RED), true);
			abort(player);
			return;
		}
		if (t >= GantryTimeline.CANCEL_GRACE && player.isShiftKeyDown()) {
			player.displayClientMessage(Component.translatable(mode == MODE_EQUIP ? "message.projecthero.gantry.equip_cancelled"
					: "message.projecthero.gantry.unequip_cancelled").withStyle(ChatFormatting.GRAY), true);
			abort(player);
			return;
		}
		hold(sl, player, t);
		tickSounds(sl, t);
		if (mode == MODE_EQUIP) {
			tickEquip(sl, player, t);
		} else {
			tickUnequip(sl, player, t);
		}
		if (t >= GantryTimeline.TOTAL) {
			end(player);
		}
	}

	/** Off the lift: teleported away, knocked far off, ... (the lift itself keeps them within a few centimetres). */
	private boolean strayed(ServerPlayer player) {
		Vec3 at = standAt();
		double dx = player.getX() - at.x;
		double dz = player.getZ() - at.z;
		double dy = player.getY() - at.y;
		return dx * dx + dz * dz > 2.25 || dy < -1.0 || dy > 2.5;
	}

	/** Keep the wearer still, on the lift and facing along the gantry. */
	private void hold(ServerLevel sl, ServerPlayer player, int t) {
		IronManSuitPlatformBlockEntity.setFrozen(player, true); // re-asserted every tick (cheap)
		lockFacing(player, false);
		player.fallDistance = 0f;
		Vec3 at = standAt();
		double dx = player.getX() - at.x;
		double dz = player.getZ() - at.z;
		if (dx * dx + dz * dz > 0.36) {
			// drifted (lag, a push): put them back on the lift at its current height
			float f = GantryTimeline.frame(mode == MODE_EQUIP, t);
			player.teleportTo(sl, at.x, at.y + GantryTimeline.lift(f), at.z, yaw, player.getXRot());
			player.setDeltaMovement(Vec3.ZERO);
		}
	}

	/** Pistons, hatches and servos -- the same beats on and off (the timetable is symmetric). */
	private void tickSounds(ServerLevel sl, int t) {
		if (t == GantryTimeline.HATCH_FROM || t == GantryTimeline.TOTAL - GantryTimeline.HATCH_TO) {
			sl.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.6f, 1.25f);
		}
		if (t == GantryTimeline.HATCH_TO || t == GantryTimeline.TOTAL - GantryTimeline.HATCH_FROM) {
			sl.playSound(null, worldPosition, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.6f, 1.1f);
		}
		if (t == GantryTimeline.RISE_FROM || t == GantryTimeline.TOTAL - GantryTimeline.RISE_TO) {
			sl.playSound(null, worldPosition, IronManSounds.SERVO, SoundSource.BLOCKS, 0.9f, 0.75f);
		}
		if (t == GantryTimeline.TOTAL - 1) {
			sl.playSound(null, worldPosition, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.7f, 0.7f);
		}
	}

	/** One server tick of putting the suit on: each piece floor -> armour slot in exactly one tick. */
	private void tickEquip(ServerLevel sl, ServerPlayer player, int t) {
		int n = slots.length;
		for (int i = 0; i < n; i++) {
			int idx = slots[i];
			int bit = 1 << idx;
			if (t == GantryTimeline.pieceStart(i, n)) {
				IronManSounds.play(player, IronManSounds.SERVO, 0.7f, 1.0f + 0.08f * i);
			}
			if (t == GantryTimeline.liftTick(i, n)) {
				IronManSounds.play(player, IronManSounds.CLAMP, 0.55f, 1.45f);
				Vec3 e = elevatorTop();
				sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.x, e.y + 0.3, e.z, 5, 0.15, 0.1, 0.15, 0.04);
			}
			if ((doneMask & bit) == 0 && t >= GantryTimeline.equipTick(i, n)) {
				doneMask |= bit;
				ItemStack stack = buffer.get(idx);
				if (stack.getItem() instanceof IronManArmorItem p && p.suitId().equals(suit)) {
					// one tick: out of the floor and onto the body -- the real stack (the arm fitted it: no plate build-on)
					buffer.set(idx, ItemStack.EMPTY);
					if (!IronManSuitUpManager.receivePart(player, stack, false)) {
						buffer.set(idx, stack); // that slot already holds this suit's piece: it stays in the floor
					} else if (idx == 0) {
						// the helmet goes on with the faceplate up -- it closes last, when the suit comes online
						if (!IronManFaceplate.isOpen(player)) {
							player.setAttached(ModAttachments.IRON_MAN_FACEPLATE_OPEN, true);
							openedFaceplate = true;
						}
						IronManSounds.play(player, IronManSounds.FACEPLATE_OPEN, 0.6f, 1.0f);
					}
					changed();
				}
			}
			if (t == GantryTimeline.letGoTick(i, n)) {
				IronManSounds.play(player, IronManSounds.RELEASE, 0.5f, 1.35f);
			}
		}
	}

	/** One server tick of taking the suit off: each piece armour slot -> floor in exactly one tick. */
	private void tickUnequip(ServerLevel sl, ServerPlayer player, int t) {
		int n = slots.length;
		for (int i = n - 1; i >= 0; i--) {
			int idx = slots[i];
			int bit = 1 << idx;
			EquipmentSlot slot = equipSlotOf(idx);
			if (t == GantryTimeline.clampTick(i, n)) {
				IronManSounds.play(player, IronManSounds.CLAMP, 0.55f, 1.25f);
				if (idx == 0 && !IronManFaceplate.isOpen(player) && player.getItemBySlot(slot).getItem() instanceof IronManArmorItem) {
					// the faceplate lifts before the helmet comes off
					player.setAttached(ModAttachments.IRON_MAN_FACEPLATE_OPEN, true);
					IronManSuitFx.faceplateMoved(player);
					IronManSounds.play(player, IronManSounds.FACEPLATE_OPEN, 0.6f, 1.0f);
					openedFaceplate = true;
				}
			}
			if ((doneMask & bit) == 0 && t >= GantryTimeline.removeTick(i, n)) {
				doneMask |= bit;
				ItemStack worn = player.getItemBySlot(slot);
				if (worn.getItem() instanceof IronManArmorItem p && p.suitId().equals(suit) && buffer.get(idx).isEmpty()) {
					// one tick: off the body and into the clamp -- the real stack, its charge stamped on
					ItemStack copy = worn.copy();
					IronManEnergy.stampStack(copy, IronManEnergy.energy(player, suit), IronManEnergy.integrity(player, suit));
					player.setItemSlot(slot, ItemStack.EMPTY);
					buffer.set(idx, copy);
					IronManSounds.play(player, IronManSounds.RELEASE, 0.6f, 1.1f);
					if (idx == 0) {
						player.setAttached(ModAttachments.IRON_MAN_FACEPLATE_OPEN, false);
						openedFaceplate = false;
					}
					changed();
				}
			}
			if (t == GantryTimeline.stowTick(i, n)) {
				IronManSounds.play(player, IronManSounds.CLAMP, 0.5f, 0.85f);
				Vec3 e = elevatorTop();
				sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.x, e.y + 0.3, e.z, 5, 0.15, 0.1, 0.15, 0.04);
			}
		}
	}

	/** World point at the top middle of the front (elevator) hatch. */
	private Vec3 elevatorTop() {
		return Vec3.atBottomCenterOf(worldPosition.relative(facing()).above());
	}

	// ---------------- ending ----------------

	private void end(ServerPlayer player) {
		if (player != null) {
			IronManSuitPlatformBlockEntity.setFrozen(player, false);
			if (openedFaceplate && IronManFaceplate.isOpen(player)) {
				boolean shut = mode == MODE_EQUIP ? !IronManArmor.wearingFullSuit(player, suit) : IronManArmor.hasHelmet(player, suit);
				if (shut) {
					// a partial suit / a cancelled sequence never gets the "online" beat -- shut the visor the gantry lifted
					player.setAttached(ModAttachments.IRON_MAN_FACEPLATE_OPEN, false);
					IronManSuitFx.faceplateMoved(player);
					IronManSounds.play(player, IronManSounds.FACEPLATE_SEAL, 0.6f, 1.0f);
				}
			}
			if (mode == MODE_EQUIP && doneMask != 0) {
				StarkGantry.rememberOrigin(player, platformPos);
			}
			if (mode == MODE_UNEQUIP) {
				IronManSounds.play(player, IronManSounds.CLAMP, 0.6f, 0.8f);
				if (!IronManArmor.wearingAnyIronMan(player)) {
					TonyStark.setActiveSuit(player, "");
				}
			}
			releasePlayerHold(player);
		}
		mode = MODE_NONE;
		slots = new int[0];
		playerEntity = -1;
		flushBuffer(player);
		playerId = null;
		if (level instanceof ServerLevel sl) {
			setOpen(sl, false);
		}
		changed();
	}

	/** Interrupted (walked off / logged out / died / power lost / floor broken / sneak): stop where it is. */
	public void abort(ServerPlayer player) {
		if (mode == MODE_NONE) {
			return;
		}
		if (player == null && playerId != null && level instanceof ServerLevel sl) {
			player = sl.getServer().getPlayerList().getPlayer(playerId);
		}
		if (player != null) {
			IronManSuitFx.endPose(player);
		}
		end(player);
	}

	/** Hand the player's suit-up state back (only if it is still the hold this sequence placed). */
	private void releasePlayerHold(ServerPlayer player) {
		var s = TonyStark.state(player);
		if (suit.equals(s.transitionSuit) && s.transitionMask == 0 && s.transitionTicks > 1) {
			s.transitionTicks = 1; // IronManSuitUpManager.tick finishes it next tick (faceplate beat / suit online)
		}
	}

	/**
	 * Everything still in the floor goes home: the platform it came from / was meant for, else any Suit Platform within
	 * range that can take it, else the wearer's inventory (if they are still here), else it drops on the gantry.
	 */
	void flushBuffer(ServerPlayer player) {
		if (!(level instanceof ServerLevel sl) || bufferEmpty()) {
			return;
		}
		for (int idx = 0; idx < buffer.size(); idx++) {
			ItemStack stack = buffer.get(idx);
			if (stack.isEmpty()) {
				continue;
			}
			buffer.set(idx, ItemStack.EMPTY);
			if (StarkGantry.rackPiece(sl, worldPosition, platformPos, owner, stack)) {
				continue;
			}
			if (player != null && player.isAlive() && !player.hasDisconnected() && player.level() == sl
					&& player.position().distanceTo(standAt()) < 8.0) {
				IronManSuitUpManager.giveBack(player, stack);
			} else {
				Vec3 at = standAt();
				net.minecraft.world.Containers.dropItemStack(sl, at.x, at.y + 0.5, at.z, stack);
			}
		}
		changed();
	}

	/** Drop whatever the floor still holds (the centre tile itself was broken). */
	void dropBuffer(ServerLevel sl) {
		for (int idx = 0; idx < buffer.size(); idx++) {
			if (!buffer.get(idx).isEmpty()) {
				net.minecraft.world.Containers.dropItemStack(sl, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0,
						worldPosition.getZ() + 0.5, buffer.get(idx));
				buffer.set(idx, ItemStack.EMPTY);
			}
		}
	}

	/** Open (or shut) this tile and its three hatch tiles. Shutting also shuts any stray open tile round the centre. */
	private void setOpen(ServerLevel sl, boolean open) {
		if (open) {
			setTile(sl, worldPosition, true);
			for (BlockPos h : hatches()) {
				setTile(sl, h, true);
			}
			return;
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				setTile(sl, worldPosition.offset(dx, 0, dz), false);
			}
		}
	}

	private static void setTile(ServerLevel sl, BlockPos pos, boolean open) {
		BlockState st = sl.getBlockState(pos);
		if (st.getBlock() instanceof StarkGantryFloorBlock && st.getValue(StarkGantryFloorBlock.OPEN) != open) {
			sl.setBlock(pos, st.setValue(StarkGantryFloorBlock.OPEN, open), Block.UPDATE_CLIENTS);
		}
	}

	/** Hold the wearer's body and head at {@link #yaw}; {@code tellClient} also pushes it to their client once. */
	private void lockFacing(ServerPlayer player, boolean tellClient) {
		player.setYRot(yaw);
		player.yRotO = yaw;
		player.setYHeadRot(yaw);
		player.setYBodyRot(yaw);
		player.yBodyRotO = yaw;
		if (tellClient && player.connection != null) {
			player.connection.teleport(player.getX(), player.getY(), player.getZ(), yaw, player.getXRot());
		}
	}

	/**
	 * The chunk is unloading with a sequence running: let the wearer go (their suit-up hold expires on its own). The
	 * buffer is left exactly as it was saved -- the reloaded centre flushes it -- so nothing can be duplicated.
	 */
	@Override
	public void setRemoved() {
		if (mode != MODE_NONE && level instanceof ServerLevel sl && playerId != null) {
			ServerPlayer player = sl.getServer().getPlayerList().getPlayer(playerId);
			if (player != null) {
				IronManSuitPlatformBlockEntity.setFrozen(player, false);
				IronManSuitFx.endPose(player);
				releasePlayerHold(player);
			}
		}
		super.setRemoved();
	}

	static EquipmentSlot equipSlotOf(int rackSlot) {
		return switch (rackSlot) {
			case 0 -> EquipmentSlot.HEAD;
			case 1 -> EquipmentSlot.CHEST;
			case 2 -> EquipmentSlot.LEGS;
			default -> EquipmentSlot.FEET;
		};
	}

	private void changed() {
		setChanged();
		if (level instanceof ServerLevel sl) {
			sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	// ---------------- save / sync ----------------

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		buffer.clear();
		ContainerHelper.loadAllItems(tag, buffer, registries);
		platformPos = tag.contains("Platform") ? NbtUtils.readBlockPos(tag, "Platform").orElse(null) : null;
		owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
		if (level != null && level.isClientSide()) {
			// the running sequence, for the renderer only (never read from disk -- see the class notes)
			mode = tag.getInt("SeqMode");
			start = tag.getLong("SeqStart");
			playerEntity = tag.contains("SeqPlayer") ? tag.getInt("SeqPlayer") : -1;
			yaw = tag.getFloat("SeqYaw");
			slots = tag.getIntArray("SeqSlots");
			suit = tag.getString("SeqSuit");
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		ContainerHelper.saveAllItems(tag, buffer, registries);
		if (platformPos != null) {
			tag.put("Platform", NbtUtils.writeBlockPos(platformPos));
		}
		if (owner != null) {
			tag.putUUID("Owner", owner);
		}
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		saveAdditional(tag, registries);
		tag.putInt("SeqMode", mode);
		tag.putLong("SeqStart", start);
		tag.putInt("SeqPlayer", playerEntity);
		tag.putFloat("SeqYaw", yaw);
		tag.putIntArray("SeqSlots", slots);
		tag.putString("SeqSuit", suit);
		return tag;
	}

	@Override
	public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
		return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
	}
}
