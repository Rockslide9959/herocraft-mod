package com.projecthero.mod.ironman.sorter;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.16: the Stark Sorting Station -- a 54-slot store and the Sorter Bot's dock.
 *
 * <h2>Item custody</h2>
 * Items are never "inside the robot". A load the bot picks up moves from {@link #items} into {@link #carried}, which
 * is part of this block entity and saved with it; the bot only renders a copy. A deposit moves {@code carried} into
 * the chest; a homecoming moves whatever did not fit back into {@code items}. So:
 * <ul>
 *   <li>breaking the station drops {@code items} <em>and</em> {@code carried} ({@link SortingStationBlock#onRemove}),
 *       and the bot, finding its dock gone, just vanishes;</li>
 *   <li>if the bot disappears mid-job (chunk unload, {@code /kill}, a reload -- the bot entity is never saved) the
 *       station notices within {@value #LOST_BOT_TICKS} ticks and puts {@code carried} back into {@code items};</li>
 *   <li>nothing is ever copied, so there is nothing to duplicate.</li>
 * </ul>
 * A sort job itself is not persisted: after a reload the station simply returns anything in transit and waits for
 * the next press of Sort.
 */
public class SortingStationBlockEntity extends BlockEntity implements Container, ExtendedScreenHandlerFactory<BlockPos> {
	public static final int SIZE = 54;
	/** Scan radius in blocks, measured centre to centre. */
	public static final int RADIUS = 10;
	/** Most stacks in one trip (all bound for the same container). */
	public static final int MAX_LOAD_STACKS = 3;
	private static final int LOST_BOT_TICKS = 40;

	private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	/** Stacks currently on their way to a container. Owned by the station, saved with it. */
	private final List<ItemStack> carried = new ArrayList<>();

	// ---- the running job (transient) ----
	private boolean running;
	private SortPlan plan;
	private UUID botId;
	private UUID requester;
	private int done;
	private int total;
	private int pendingSlots;
	private int missingBotTicks;
	private final BitSet unsortable = new BitSet(SIZE);
	/** Test hook: restricts the scan (game tests stay inside their own structure). */
	private Predicate<BlockPos> scanFilter;

	public final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> running ? 1 : 0;
				case 1 -> done;
				case 2 -> total;
				case 3 -> plan == null ? 0 : plan.targets().size();
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int getCount() {
			return 4;
		}
	};

	/** One trip: where it goes and how many station slots it emptied. */
	public record Load(SortPlan.Target target, int slotsEmptied) {
	}

	public SortingStationBlockEntity(BlockPos pos, BlockState state) {
		super(StarkSorter.STATION_BE, pos, state);
	}

	// ------------------------------------------------------------------ state

	public boolean isRunning() {
		return running;
	}

	public int done() {
		return done;
	}

	public int total() {
		return total;
	}

	public SortPlan plan() {
		return plan;
	}

	public UUID botId() {
		return botId;
	}

	/** A copy of the stacks currently in transit. */
	public List<ItemStack> carried() {
		return carried.stream().map(ItemStack::copy).toList();
	}

	public ItemStack firstCarried() {
		return carried.isEmpty() ? ItemStack.EMPTY : carried.get(0);
	}

	public void setScanFilterForTests(Predicate<BlockPos> filter) {
		this.scanFilter = filter;
	}

	/** Where the bot docks: hovering over the station's launch pad. */
	public Vec3 dockPoint() {
		return Vec3.atBottomCenterOf(worldPosition).add(0, 1.02, 0);
	}

	private int nonEmptySlots() {
		int n = 0;
		for (ItemStack s : items) {
			if (!s.isEmpty()) {
				n++;
			}
		}
		return n;
	}

	// ------------------------------------------------------------------ the Sort button

	/**
	 * Plan a job and launch the bot. Returns the status line shown to the player. {@code requester} may be null
	 * (game tests); without a level that can spawn entities nothing happens.
	 */
	public Component startSort(ServerPlayer requester) {
		if (!(level instanceof ServerLevel server)) {
			return Component.empty();
		}
		if (running) {
			return Component.translatable("message.projecthero.stark_sorting_station.already_running", done, total)
					.withStyle(ChatFormatting.GOLD);
		}
		returnCarried();
		if (isEmpty()) {
			return Component.translatable("message.projecthero.stark_sorting_station.empty").withStyle(ChatFormatting.GRAY);
		}
		List<SortPlan.Target> targets = SortPlan.scan(server, worldPosition, RADIUS, scanFilter);
		if (targets.isEmpty()) {
			return Component.translatable("message.projecthero.stark_sorting_station.no_chests", RADIUS)
					.withStyle(ChatFormatting.RED);
		}
		plan = SortPlan.build(server, targets, items);
		unsortable.clear();
		done = 0;
		total = nonEmptySlots();
		pendingSlots = 0;
		missingBotTicks = 0;
		this.requester = requester == null ? null : requester.getUUID();

		SorterBotEntity bot = SorterBotEntity.spawn(server, this);
		if (bot == null) {
			plan = null;
			return Component.empty();
		}
		botId = bot.getUUID();
		running = true;
		setChanged();

		Vec3 dock = dockPoint();
		server.sendParticles(ParticleTypes.ELECTRIC_SPARK, dock.x, dock.y + 0.3, dock.z, 24, 0.3, 0.3, 0.3, 0.15);
		server.sendParticles(ParticleTypes.END_ROD, dock.x, dock.y, dock.z, 10, 0.25, 0.1, 0.25, 0.04);
		server.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6f, 1.8f);
		return Component.translatable("message.projecthero.stark_sorting_station.plan", targets.size(), plan.summary())
				.withStyle(ChatFormatting.AQUA);
	}

	// ------------------------------------------------------------------ the bot's side of the job

	/**
	 * Move the next load from {@link #items} into {@link #carried}: the first sortable stack plus up to
	 * {@value #MAX_LOAD_STACKS}-1 more stacks bound for the same container, never more than that container can take
	 * (checked on a simulation). Returns null when nothing left in the station fits anywhere.
	 */
	public Load takeLoad() {
		if (plan == null || level == null) {
			return null;
		}
		returnCarried();
		for (int slot = 0; slot < SIZE; slot++) {
			ItemStack stack = items.get(slot);
			if (stack.isEmpty() || unsortable.get(slot)) {
				continue;
			}
			SortPlan.Target dest = plan.destinationFor(level, stack);
			Container container = dest == null ? null : dest.resolve(level);
			if (container == null) {
				unsortable.set(slot);
				continue;
			}
			Stash.Sim sim = Stash.Sim.of(container);
			int emptied = 0;
			int fits = sim.insert(stack.copy());
			if (fits <= 0) {
				unsortable.set(slot);
				continue;
			}
			carried.add(stack.split(fits));
			if (stack.isEmpty()) {
				items.set(slot, ItemStack.EMPTY);
				emptied++;
			}
			for (int j = slot + 1; j < SIZE && carried.size() < MAX_LOAD_STACKS; j++) {
				ItemStack other = items.get(j);
				if (other.isEmpty() || unsortable.get(j) || !dest.equals(plan.destinationFor(level, other))) {
					continue;
				}
				int more = sim.insert(other.copy());
				if (more <= 0) {
					continue;
				}
				carried.add(other.split(more));
				if (other.isEmpty()) {
					items.set(j, ItemStack.EMPTY);
					emptied++;
				}
			}
			pendingSlots = emptied;
			total = Math.max(total, done + nonEmptySlots() + emptied);
			setChanged();
			return new Load(dest, emptied);
		}
		return null;
	}

	/** File everything being carried into {@code target}. Anything that no longer fits stays carried (it goes home). */
	public boolean depositCarried(SortPlan.Target target) {
		if (level == null) {
			return false;
		}
		Container container = target.resolve(level);
		if (container != null) {
			for (ItemStack s : carried) {
				Stash.insert(container, s);
			}
			carried.removeIf(ItemStack::isEmpty);
		}
		boolean all = carried.isEmpty();
		if (all) {
			done += pendingSlots;
		}
		pendingSlots = 0;
		setChanged();
		return all;
	}

	/** Put anything still in transit back into the station; if the station is full too, drop it on top of it. */
	public void returnCarried() {
		if (carried.isEmpty()) {
			return;
		}
		for (ItemStack s : carried) {
			for (int i = 0; i < SIZE && !s.isEmpty(); i++) {
				ItemStack there = items.get(i);
				if (!there.isEmpty() && ItemStack.isSameItemSameComponents(there, s)) {
					int add = Math.min(s.getCount(), there.getMaxStackSize() - there.getCount());
					there.grow(add);
					s.shrink(add);
				}
			}
			for (int i = 0; i < SIZE && !s.isEmpty(); i++) {
				if (items.get(i).isEmpty()) {
					items.set(i, s.copyAndClear());
				}
			}
			if (!s.isEmpty() && level != null) {
				Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.1,
						worldPosition.getZ() + 0.5, s.copyAndClear());
			}
		}
		carried.clear();
		pendingSlots = 0;
		unsortable.clear();
		setChanged();
	}

	/** The bot is home with nothing left to do: end the job and tell whoever pressed Sort how it went. */
	public void finish() {
		returnCarried();
		boolean wasRunning = running;
		running = false;
		botId = null;
		plan = null;
		setChanged();
		if (!wasRunning || !(level instanceof ServerLevel server)) {
			return;
		}
		server.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.5f, 1.8f);
		ServerPlayer player = requester == null ? null : server.getServer().getPlayerList().getPlayer(requester);
		if (player != null) {
			player.sendSystemMessage(Component.translatable("message.projecthero.stark_sorting_station.done", done)
					.withStyle(ChatFormatting.GREEN));
			int left = nonEmptySlots();
			if (left > 0) {
				player.sendSystemMessage(Component.translatable("message.projecthero.stark_sorting_station.leftover", left)
						.withStyle(ChatFormatting.GOLD));
			}
		}
	}

	/** Called when the block is broken: stop the job; the block drops {@link #items} and {@link #carried}. */
	public void onBroken() {
		if (level instanceof ServerLevel server && botId != null) {
			Entity bot = server.getEntity(botId);
			if (bot instanceof SorterBotEntity sorter) {
				sorter.abort();
			}
		}
		running = false;
		botId = null;
		plan = null;
	}

	/** Everything the station is responsible for: its slots plus anything in transit. */
	public List<ItemStack> allContents() {
		List<ItemStack> out = new ArrayList<>();
		for (ItemStack s : items) {
			if (!s.isEmpty()) {
				out.add(s);
			}
		}
		out.addAll(carried);
		return out;
	}

	public void clearCarried() {
		carried.clear();
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, SortingStationBlockEntity be) {
		if (!(level instanceof ServerLevel server)) {
			return;
		}
		if (!be.running) {
			if (!be.carried.isEmpty()) {
				be.returnCarried(); // a reload mid-trip: the job is gone, the cargo comes home
			}
			return;
		}
		Entity bot = be.botId == null ? null : server.getEntity(be.botId);
		if (bot == null || bot.isRemoved()) {
			if (++be.missingBotTicks >= LOST_BOT_TICKS) {
				be.returnCarried();
				be.running = false;
				be.botId = null;
				be.plan = null;
				be.setChanged();
				ServerPlayer player = be.requester == null ? null : server.getServer().getPlayerList().getPlayer(be.requester);
				if (player != null) {
					player.sendSystemMessage(Component.translatable("message.projecthero.stark_sorting_station.lost")
							.withStyle(ChatFormatting.GOLD));
				}
			}
		} else {
			be.missingBotTicks = 0;
		}
	}

	// ------------------------------------------------------------------ Container

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
		setChanged();
		return result;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return ContainerHelper.takeItem(items, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		items.set(slot, stack);
		if (stack.getCount() > stack.getMaxStackSize()) {
			stack.setCount(stack.getMaxStackSize());
		}
		unsortable.clear(slot); // a player (or hopper) changed it: worth another look
		setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	@Override
	public void clearContent() {
		items.clear();
	}

	// ------------------------------------------------------------------ menu

	@Override
	public Component getDisplayName() {
		return Component.translatable("container.projecthero.stark_sorting_station");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
		return new SortingStationMenu(syncId, inv, this, data);
	}

	@Override
	public BlockPos getScreenOpeningData(ServerPlayer player) {
		return worldPosition;
	}

	// ------------------------------------------------------------------ nbt

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		items.clear();
		ContainerHelper.loadAllItems(tag, items, registries);
		carried.clear();
		ListTag list = tag.getList("Carried", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			ItemStack.parse(registries, list.getCompound(i)).ifPresent(carried::add);
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		ContainerHelper.saveAllItems(tag, items, registries);
		ListTag list = new ListTag();
		for (ItemStack s : carried) {
			if (!s.isEmpty()) {
				list.add(s.save(registries));
			}
		}
		tag.put("Carried", list);
	}
}
