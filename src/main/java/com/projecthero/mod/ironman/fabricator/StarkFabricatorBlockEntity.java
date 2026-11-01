package com.projecthero.mod.ironman.fabricator;

import java.util.UUID;

import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.TonyStark;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The Stark Workbench's state (it is still the Stark Fabricator underneath): 9 component-input slots, 1 output slot,
 * 1 blueprint slot and a fabrication job (spec sections 8-11).
 *
 * <p>v0.17.2 (user): no energy any more, and nothing starts by itself -- a job runs only once the operator presses
 * Fabricate ({@link #tryStart}; the menu's button). The inputs are consumed on <b>completion</b>: until then they stay
 * in the grid, so Cancel ({@link #cancel}) loses nothing, and a job whose inputs, blueprint, piece or output room go away
 * mid-build cancels itself the same way. The per-piece build time is unchanged ({@link FabricationRecipe#timeTicks}).
 *
 * <p>Operating restriction (spec section 6): it only makes progress while a player with the Tony Stark power is within
 * {@value #OPERATOR_RADIUS} blocks (it pauses, keeping its progress, while nobody is), and only such a player in range
 * can start it. That is what stops a player without the power from driving it with hoppers or other automation.
 *
 * <p>v0.16.7 (user): the client draws the holograms ({@code StarkWorkbenchRenderer}) from a small synced "job" (which
 * suit / piece / item, progress, whether it is advancing, which recipe slots are filled, a completion counter for the
 * finish flourish; see {@link #getUpdateTag}). The block entity lives on the main half of the two-block desk (or the one
 * block of an old {@link WorkbenchPart#SINGLE} fabricator, which claims its side half here once that block is air).
 */
public class StarkFabricatorBlockEntity extends BlockEntity implements Container, ExtendedScreenHandlerFactory<BlockPos> {
	public static final int SIZE = 11;
	public static final int OUTPUT_SLOT = 9;
	public static final int BLUEPRINT_SLOT = 10;
	public static final double OPERATOR_RADIUS = 6.0;

	private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	private int progress;
	private int maxProgress;
	/** The running job's recipe id; empty = idle. */
	private String activeRecipeId = "";
	/** Who pressed Fabricate (credited with the piece when they are still in range at the end). */
	private UUID starter;
	/** "changes 18": which suit piece the operator picked (0 = none, 1 = helmet, 2 = chest, 3 = legs, 4 = boots). */
	private int selectedPiece;
	/** Did the job make progress this tick (an operator in range)? */
	private boolean advancing;

	// ---- v0.16.7 (user): the hologram job, synced to every client in range (not the inventory)
	/** Bit i set = recipe input i of the selected piece is satisfied by the grid. */
	private int slotMask;
	private int slotCount;
	/** v0.17.2: bumped on every finished job, with what it made -- the client plays the finish flourish when it changes. */
	private int completions;
	private String doneItem = "";
	/** Client only: the game time the last job sync arrived (the renderer runs the progress bar on from it). */
	private long syncedAt;
	private int syncCountdown;
	private int syncChecks;
	private int claimCountdown;
	private boolean holoDirty = true;

	public final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> progress;
				case 1 -> maxProgress;
				case 2 -> selectedPiece;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
			switch (index) {
				case 0 -> progress = value;
				case 1 -> maxProgress = value;
				case 2 -> selectedPiece = value;
				default -> { }
			}
		}

		@Override
		public int getCount() {
			return 3;
		}
	};

	public StarkFabricatorBlockEntity(BlockPos pos, BlockState state) {
		super(IronManBlocks.STARK_FABRICATOR_BE, pos, state);
	}

	/**
	 * How long the WORKING state outlasts the last progress tick, so the block state (and the chunk mesh) does not
	 * flicker when a job pauses for a tick.
	 */
	public static final int WORKING_LINGER_TICKS = 10;
	/** Ticks since progress was last made; starts "long ago" so a freshly loaded idle machine stays idle. */
	private int idleTicks = WORKING_LINGER_TICKS;

	/** Mirror "fabricating right now" into the block state (with a short linger, see {@link #WORKING_LINGER_TICKS}). */
	private void syncWorkingState(Level level, BlockPos pos, BlockState state) {
		idleTicks = advancing ? 0 : Math.min(WORKING_LINGER_TICKS, idleTicks + 1);
		boolean working = idleTicks < WORKING_LINGER_TICKS;
		if (state.hasProperty(StarkFabricatorBlock.WORKING) && state.getValue(StarkFabricatorBlock.WORKING) != working) {
			level.setBlock(pos, state.setValue(StarkFabricatorBlock.WORKING, working), Block.UPDATE_ALL);
			holoDirty = true;
		}
	}

	// ---------------- selected suit piece ("changes 18") ----------------

	public int selectedPiece() {
		return selectedPiece;
	}

	/** Pick the piece to fabricate. Changing it while a job runs cancels the job (nothing is lost). */
	public void setSelectedPiece(int piece) {
		int clamped = Math.max(0, Math.min(4, piece));
		if (clamped == selectedPiece) {
			return;
		}
		selectedPiece = clamped;
		if (running()) {
			cancel();
		}
		holoDirty = true;
		setChanged();
	}

	// ---------------- v0.17.2 (user): the Fabricate button ----------------

	/** Is a job running (started with Fabricate, not finished or cancelled)? */
	public boolean running() {
		return !activeRecipeId.isEmpty();
	}

	/** The running job's recipe, or null when idle. */
	public FabricationRecipe activeRecipe() {
		return running() ? FabricatorRecipes.byId(activeRecipeId) : null;
	}

	/**
	 * Fabricate: start the job the grid + blueprint + selected piece make right now. Refused (with the reason, for the
	 * action bar) unless {@code player} has the Tony Stark power and stands within {@value #OPERATOR_RADIUS} blocks, nothing
	 * is running, the recipe is complete in the grid and the output slot has room. Returns null when it started.
	 */
	public Component tryStart(ServerPlayer player) {
		if (!TonyStark.hasPower(player)) {
			return Component.translatable("message.projecthero.stark_fabricator.no_understanding");
		}
		if (!new AABB(worldPosition).inflate(OPERATOR_RADIUS).contains(player.position())) {
			return Component.translatable("screen.projecthero.stark_workbench.why.too_far");
		}
		String why = StarkWorkbench.blocker(this, TonyStark.techLevel(player), selectedPiece, running());
		if (why != null) {
			return Component.translatable("screen.projecthero.stark_workbench.why." + why);
		}
		FabricationRecipe recipe = FabricatorRecipes.find(this, TonyStark.techLevel(player), selectedPiece);
		activeRecipeId = recipe.id();
		progress = 0;
		maxProgress = recipe.timeTicks();
		starter = player.getUUID();
		advancing = false;
		holoDirty = true;
		syncCountdown = 0;
		setChanged();
		if (level != null) {
			level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.7f, 1.5f);
		}
		return null;
	}

	/** Stop the running job. The inputs were never taken, so nothing needs refunding. */
	public void cancel() {
		if (!running()) {
			return;
		}
		clearJob();
		if (level != null && !level.isClientSide()) {
			level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.6f, 1.4f);
		}
	}

	private void clearJob() {
		activeRecipeId = "";
		progress = 0;
		maxProgress = 0;
		starter = null;
		advancing = false;
		holoDirty = true;
		setChanged();
	}

	// ---------------- v0.16.7 (user): the job the hologram shows ----------------

	/** The suit whose blueprint sits in the blueprint slot (the suit being worked on), or null when idle. */
	public String jobSuit() {
		return items.get(BLUEPRINT_SLOT).getItem() instanceof com.projecthero.mod.ironman.item.BlueprintItem bp ? bp.suitId() : null;
	}

	private String clientJobSuit;
	private Item clientJobItem;
	private boolean clientAdvancing;
	private Item clientDoneItem;

	/** Client: the synced job suit (the client block entity does not know the inventory unless a menu is open). */
	public String holoSuit() {
		return level != null && level.isClientSide() ? clientJobSuit : jobSuit();
	}

	/** What the running job makes (synced to clients), or null when idle. */
	public Item jobItem() {
		if (level != null && level.isClientSide()) {
			return clientJobItem;
		}
		FabricationRecipe r = activeRecipe();
		return r == null ? null : r.result().getItem();
	}

	/** Is the job advancing right now (synced: an operator is in range)? */
	public boolean advancing() {
		return level != null && level.isClientSide() ? clientAdvancing : advancing;
	}

	/** Finished jobs so far (synced); {@link #doneItem} is what the last one made. */
	public int completions() {
		return completions;
	}

	public Item doneItem() {
		if (level != null && level.isClientSide()) {
			return clientDoneItem;
		}
		return doneItem.isEmpty() ? null : itemById(doneItem);
	}

	public int progress() {
		return progress;
	}

	public int maxProgress() {
		return maxProgress;
	}

	public int slotMask() {
		return slotMask;
	}

	public int slotCount() {
		return slotCount;
	}

	public long syncedAt() {
		return syncedAt;
	}

	private void updateSlotMask() {
		FabricationRecipe recipe = StarkWorkbench.recipe(jobSuit(), selectedPiece);
		int mask = 0;
		int count = 0;
		if (recipe != null) {
			count = Math.min(recipe.inputs().size(), 9);
			for (int i = 0; i < count; i++) {
				FabricationRecipe.Input in = recipe.inputs().get(i);
				int have = 0;
				for (int s = 0; s < 9; s++) {
					if (items.get(s).is(in.item())) {
						have += items.get(s).getCount();
					}
				}
				if (have >= in.count()) {
					mask |= 1 << i;
				}
			}
		}
		if (mask != slotMask || count != slotCount) {
			slotMask = mask;
			slotCount = count;
			holoDirty = true;
		}
	}

	/** Send the job to clients when it changed (checked twice a second), and every 2 s while working to keep the bar true. */
	private void syncHologram(Level level, BlockPos pos, BlockState state) {
		if (--syncCountdown > 0) {
			return;
		}
		syncCountdown = 10;
		updateSlotMask();
		syncChecks++;
		if (holoDirty || (advancing && syncChecks % 4 == 0)) {
			level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
			holoDirty = false;
		}
	}

	// ---------------- tick ----------------

	public static void serverTick(Level level, BlockPos pos, BlockState state, StarkFabricatorBlockEntity be) {
		state = be.claimSideHalf(level, pos, state);
		be.tickFabrication(level, pos);
		be.syncWorkingState(level, pos, state);
		be.syncHologram(level, pos, level.getBlockState(pos));
	}

	/**
	 * v0.16.7 (user): an old one-block Fabricator (loaded as {@link WorkbenchPart#SINGLE}) grows its side half as soon as
	 * the block to its right is plain air -- never replacing anything, checked once a second. Until then it works as a
	 * complete one-block desk.
	 */
	private BlockState claimSideHalf(Level level, BlockPos pos, BlockState state) {
		if (!state.hasProperty(StarkFabricatorBlock.PART) || state.getValue(StarkFabricatorBlock.PART) != WorkbenchPart.SINGLE
				|| --claimCountdown > 0) {
			return state;
		}
		claimCountdown = 20;
		Direction sideDir = StarkFabricatorBlock.sideDirection(state.getValue(StarkFabricatorBlock.FACING));
		BlockPos side = pos.relative(sideDir);
		if (!level.isLoaded(side) || !level.getBlockState(side).isAir() || !level.getWorldBorder().isWithinBounds(side)) {
			return state;
		}
		BlockState main = state.setValue(StarkFabricatorBlock.PART, WorkbenchPart.MAIN);
		level.setBlock(pos, main, Block.UPDATE_ALL);
		level.setBlock(side, main.setValue(StarkFabricatorBlock.PART, WorkbenchPart.SIDE), Block.UPDATE_ALL);
		holoDirty = true;
		return level.getBlockState(pos);
	}

	/** Is the running job still the one the slots make (inputs all there, its blueprint / piece unchanged, output room)? */
	private boolean jobStillValid(FabricationRecipe recipe) {
		if (recipe == null || !recipe.matches(this) || !canOutput(recipe)) {
			return false;
		}
		if (recipe.result().getItem() instanceof com.projecthero.mod.ironman.item.IronManArmorItem) {
			return FabricatorRecipes.selectedArmorRecipe(this, selectedPiece) == recipe;
		}
		return true;
	}

	private void tickFabrication(Level level, BlockPos pos) {
		boolean wasAdvancing = advancing;
		advancing = false;
		if (!running()) {
			if (wasAdvancing) {
				holoDirty = true;
			}
			return;
		}
		FabricationRecipe recipe = activeRecipe();
		if (!jobStillValid(recipe)) {
			cancel(); // a component, the blueprint or the piece changed under it: stop, nothing was taken
			return;
		}
		ServerPlayer operator = operator(level, pos);
		if (operator == null) {
			if (wasAdvancing) {
				holoDirty = true; // paused: tell the clients so the hologram stops running the bar on
			}
			return;
		}
		advancing = true;
		if (!wasAdvancing) {
			holoDirty = true;
		}
		progress++;
		if (progress >= recipe.timeTicks()) {
			finish(level, pos, recipe, operator);
		}
		setChanged();
	}

	private void finish(Level level, BlockPos pos, FabricationRecipe recipe, ServerPlayer operator) {
		recipe.consume(this);
		ItemStack out = items.get(OUTPUT_SLOT);
		if (out.isEmpty()) {
			items.set(OUTPUT_SLOT, recipe.result().copy());
		} else {
			out.grow(recipe.result().getCount());
		}
		// "changes 21": record every armour piece the Fabricator turns out. When the fourth piece of a
		// mark lands, TonyStark.recordSuitPiece promotes the mark into builtSuits -- which is what
		// unlocks the next mark's blueprint in the Blank Blueprint picker (linear progression).
		if (recipe.result().getItem() instanceof com.projecthero.mod.ironman.item.IronManArmorItem armour) {
			TonyStark.recordSuitPiece(operator, armour.suitId(), armour.getType());
		}
		completions++;
		doneItem = BuiltInRegistries.ITEM.getKey(recipe.result().getItem()).toString();
		clearJob();
		syncCountdown = 0; // the finish flourish goes out this tick
		level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0f, 1.2f);
		level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.45f, 1.6f);
	}

	private boolean canOutput(FabricationRecipe recipe) {
		return StarkWorkbench.canOutput(this, recipe);
	}

	/** The player driving the job: whoever pressed Fabricate if they are still in range, else any Tony Stark in range. */
	private ServerPlayer operator(Level level, BlockPos pos) {
		AABB box = new AABB(pos).inflate(OPERATOR_RADIUS);
		ServerPlayer any = null;
		for (Player player : level.getEntitiesOfClass(Player.class, box)) {
			if (player instanceof ServerPlayer sp && TonyStark.hasPower(sp)) {
				if (sp.getUUID().equals(starter)) {
					return sp;
				}
				if (any == null) {
					any = sp;
				}
			}
		}
		return any;
	}

	private static Item itemById(String id) {
		ResourceLocation rl = ResourceLocation.tryParse(id);
		return rl == null ? null : BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
	}

	// ---------------- Container ----------------

	@Override
	public int getContainerSize() {
		return SIZE;
	}

	@Override
	public boolean isEmpty() {
		return items.stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getItem(int slot) {
		return items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		ItemStack result = ContainerHelper.removeItem(items, slot, amount);
		if (slot == BLUEPRINT_SLOT) {
			holoDirty = true;
		}
		setChanged();
		return result;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		if (slot == BLUEPRINT_SLOT) {
			holoDirty = true;
		}
		return ContainerHelper.takeItem(items, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		items.set(slot, stack);
		if (stack.getCount() > stack.getMaxStackSize()) {
			stack.setCount(stack.getMaxStackSize());
		}
		if (slot == BLUEPRINT_SLOT) {
			holoDirty = true;
		}
		setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player) && TonyStark.hasPower(player);
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		if (slot == OUTPUT_SLOT) {
			return false;
		}
		if (slot == BLUEPRINT_SLOT) {
			return stack.getItem() instanceof com.projecthero.mod.ironman.item.BlueprintItem;
		}
		return !(stack.getItem() instanceof com.projecthero.mod.ironman.item.BlueprintItem);
	}

	@Override
	public void clearContent() {
		items.clear();
	}

	// ---------------- menu ----------------

	@Override
	public Component getDisplayName() {
		return Component.translatable("container.projecthero.stark_fabricator");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
		return new StarkFabricatorMenu(syncId, inv, this, data);
	}

	@Override
	public BlockPos getScreenOpeningData(ServerPlayer player) {
		return worldPosition;
	}

	// ---------------- nbt + client sync ----------------

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		if (tag.contains("Items")) { // the client job sync carries no inventory -- keep the menu's copy
			items.clear();
			ContainerHelper.loadAllItems(tag, items, registries);
		}
		// v0.17.2: an old save's "Energy" is simply ignored (the Workbench has no charge any more)
		progress = tag.getInt("Progress");
		maxProgress = tag.getInt("MaxProgress");
		if (tag.contains("ActiveRecipe")) {
			activeRecipeId = tag.getString("ActiveRecipe");
		}
		starter = tag.hasUUID("Starter") ? tag.getUUID("Starter") : null;
		completions = tag.getInt("Completions");
		doneItem = tag.getString("DoneItem");
		selectedPiece = tag.getInt("SelectedPiece");
		if (tag.contains("Holo")) {
			CompoundTag h = tag.getCompound("Holo");
			clientJobSuit = h.getString("Suit").isEmpty() ? null : h.getString("Suit");
			clientJobItem = h.getString("Job").isEmpty() ? null : itemById(h.getString("Job"));
			clientAdvancing = h.getBoolean("Adv");
			clientDoneItem = doneItem.isEmpty() ? null : itemById(doneItem);
			slotMask = h.getInt("Mask");
			slotCount = h.getInt("Slots");
			if (level != null) {
				syncedAt = level.getGameTime();
			}
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		ContainerHelper.saveAllItems(tag, items, registries);
		tag.putInt("Progress", progress);
		tag.putInt("MaxProgress", maxProgress);
		tag.putString("ActiveRecipe", activeRecipeId);
		if (starter != null) {
			tag.putUUID("Starter", starter);
		}
		tag.putInt("Completions", completions);
		tag.putString("DoneItem", doneItem);
		tag.putInt("SelectedPiece", selectedPiece);
	}

	/** The job only (suit, piece, item, progress, filled recipe slots, the finish counter) -- never the inventory. */
	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("Progress", progress);
		tag.putInt("MaxProgress", maxProgress);
		tag.putInt("SelectedPiece", selectedPiece);
		tag.putInt("Completions", completions);
		tag.putString("DoneItem", doneItem);
		CompoundTag h = new CompoundTag();
		String suit = jobSuit();
		h.putString("Suit", suit == null ? "" : suit);
		Item job = jobItem();
		h.putString("Job", job == null ? "" : BuiltInRegistries.ITEM.getKey(job).toString());
		h.putBoolean("Adv", advancing);
		h.putInt("Mask", slotMask);
		h.putInt("Slots", slotCount);
		tag.put("Holo", h);
		return tag;
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
