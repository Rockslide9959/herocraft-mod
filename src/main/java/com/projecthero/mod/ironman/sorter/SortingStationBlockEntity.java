package com.projecthero.mod.ironman.sorter;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.SimpleContainer;
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
 *   <li>breaking the station drops {@code items}, {@code carried} <em>and</em> the {@link #supply} slots
 *       ({@link SortingStationBlock#onRemove}), and the bot, finding its dock gone, just vanishes;</li>
 *   <li>if the bot disappears mid-job (chunk unload, {@code /kill}, a reload -- the bot entity is never saved) the
 *       station notices within {@value #LOST_BOT_TICKS} ticks and puts {@code carried} back into {@code items};</li>
 *   <li>nothing is ever copied, so there is nothing to duplicate.</li>
 * </ul>
 * A sort job itself is not persisted: after a reload the station simply returns anything in transit and waits for
 * the next press of Sort.
 *
 * <h2>Tidy (v0.14.20) and the finishing pass (v0.14.21)</h2>
 * The second button. Instead of emptying the station it re-sorts the containers themselves: the plan is built from
 * what the chests already hold ({@link SortPlan#build} with nothing incoming, so every chest keeps the theme it
 * mostly has), then every stack sitting in a chest that is not one of its category's chests is carried, chest to
 * chest, to one that is ({@link SortPlan#tidyDestinationFor}). The bot picks up from the wrong chest
 * ({@link #collectTidyLoad}) into the same {@link #carried} list and deposits with {@link #depositCarried}, so the
 * custody rules above hold unchanged. Since v0.14.21 a Sort ends with the same chest-to-chest pass (with the Sort's
 * own plan), so a Sort leaves the room exactly as tidy as a Tidy would.
 *
 * <h2>Every chest the bot opens (v0.14.21)</h2>
 * It is repacked ({@link Stash#repack}: split stacks merged, no gaps, ordered) and labelled (a Stark sign from the
 * supply, or a re-written Stark sign if its category changed -- see {@link SorterSupply}). After the moves, the bot
 * makes one "service" visit to every other chest that still needs a repack or a label.
 *
 * <h2>Supply (v0.14.21)</h2>
 * {@value #SUPPLY_SLOTS} extra slots: {@value #SIGN_SLOTS} for signs, the rest for chests. Signs label chests; a
 * chest is placed ({@link SorterSupply#chestSpot}) when a stack's own category's chests are all full. What the
 * room still needs (chests short, signs short, chests with no free face for a sign) is recomputed while the
 * screen is open and synced in {@link #data}.
 */
public class SortingStationBlockEntity extends BlockEntity implements Container, ExtendedScreenHandlerFactory<BlockPos> {
	public static final int SIZE = 54;
	/** Scan radius in blocks, measured centre to centre. */
	public static final int RADIUS = 10;
	/** Most stacks in one trip (all bound for the same container). */
	public static final int MAX_LOAD_STACKS = 3;
	/** Most stacks in one Tidy trip (all from one chest, all bound for one chest). */
	public static final int MAX_TIDY_STACKS = 4;
	/** v0.14.21: supply slots -- signs first, then chests. */
	public static final int SUPPLY_SLOTS = 6;
	public static final int SIGN_SLOTS = 3;
	private static final int LOST_BOT_TICKS = 40;
	/** How often the "needs" line is recomputed while someone has the screen open. */
	private static final int NEEDS_INTERVAL = 40;

	private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	/** Stacks currently on their way to a container. Owned by the station, saved with it. */
	private final List<ItemStack> carried = new ArrayList<>();
	/** v0.14.21: signs (slots 0-2) and chests (3-5) for the bot to build with. Saved with the station. */
	private final SimpleContainer supply = new SimpleContainer(SUPPLY_SLOTS) {
		@Override
		public boolean canPlaceItem(int slot, ItemStack stack) {
			return slot < SIGN_SLOTS ? SorterSupply.isSign(stack) : SorterSupply.isChest(stack);
		}
	};
	/** v0.14.21: what each container (by key) was designated for when the last job ended -- the next plan's hints. */
	private final Map<BlockPos, Set<SortCategory>> designations = new LinkedHashMap<>();

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
	/** The bot is on the chest-to-chest phase (a Tidy, or the end of a Sort). */
	private boolean tidying;
	/** The current (or last) job was started with Sort. */
	private boolean sortJob = true;
	/** Containers already repacked / labelled this job (so a service visit happens at most once each). */
	private final Set<BlockPos> serviced = new HashSet<>();
	/** Tidy: trips so far -- a hard cap, so a player shuffling chests mid-job can never keep the bot out forever. */
	private int tidyTrips;
	private int tidyTripCap;
	/** This job wanted to place a chest but found no spot (searched once, reported at the end). */
	private boolean noChestSpot;
	private int chestsPlaced;
	private int signsPlaced;
	/** Test hook: restricts the scan (game tests stay inside their own structure). */
	private Predicate<BlockPos> scanFilter;

	// ---- what the room needs (v0.14.21), synced to the screen ----
	private int needChests;
	private int needSigns;
	private int noSignFace;
	private int lastTargets;
	private boolean needsDirty = true;
	private int viewers;

	public final ContainerData data = new ContainerData() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> running ? 1 : 0;
				case 1 -> done;
				case 2 -> total;
				case 3 -> plan == null ? lastTargets : plan.targets().size();
				case 4 -> sortJob ? 0 : 1;
				case 5 -> shortChests();
				case 6 -> shortSigns();
				case 7 -> needSigns;
				case 8 -> needChests;
				case 9 -> noSignFace;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};
	/**
	 * Synced slots: running, done, total, containers, mode (1 = Tidy), chests short, signs short, signs needed,
	 * chests needed, chests with no free face for a sign.
	 */
	public static final int DATA_COUNT = 10;

	/** One trip: where it goes and how many station slots it emptied. */
	public record Load(SortPlan.Target target, int slotsEmptied) {
	}

	/** One chest-to-chest trip: pick up from {@code source}, carry to {@code dest}. A null {@code dest} is a service visit. */
	public record TidyLoad(SortPlan.Target source, SortPlan.Target dest) {
	}

	/** What the room needs: chests for overflow, signs for unlabelled chests, chests with no face for a sign. */
	public record Needs(int chests, int signs, int noFace, int targets) {
	}

	public SortingStationBlockEntity(BlockPos pos, BlockState state) {
		super(StarkSorter.STATION_BE, pos, state);
		supply.addListener(c -> {
			setChanged();
			needsDirty = true;
		});
	}

	// ------------------------------------------------------------------ state

	public boolean isRunning() {
		return running;
	}

	/** The bot is on the chest-to-chest phase of a job (a Tidy, or the end of a Sort). */
	public boolean isTidying() {
		return running && tidying;
	}

	/** The running (or last) job was started with Tidy. */
	public boolean isTidyJob() {
		return !sortJob;
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

	/** v0.14.21: the sign / chest supply slots. */
	public SimpleContainer supply() {
		return supply;
	}

	public int chestsPlaced() {
		return chestsPlaced;
	}

	public int signsPlaced() {
		return signsPlaced;
	}

	/** The designations saved when the last job ended (container key -> categories). */
	public Map<BlockPos, Set<SortCategory>> designations() {
		return designations;
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

	private List<SortPlan.Target> scan(ServerLevel server) {
		return SortPlan.scan(server, worldPosition, RADIUS, scanFilter);
	}

	private Direction facing() {
		BlockState state = getBlockState();
		return state.hasProperty(SortingStationBlock.FACING) ? state.getValue(SortingStationBlock.FACING) : Direction.NORTH;
	}

	// ------------------------------------------------------------------ supply (v0.14.21)

	private int supplyCount(boolean signs) {
		int n = 0;
		for (int i = signs ? 0 : SIGN_SLOTS; i < (signs ? SIGN_SLOTS : SUPPLY_SLOTS); i++) {
			n += supply.getItem(i).getCount();
		}
		return n;
	}

	public int signsInSupply() {
		return supplyCount(true);
	}

	public int chestsInSupply() {
		return supplyCount(false);
	}

	private int shortChests() {
		return Math.max(0, needChests - chestsInSupply());
	}

	private int shortSigns() {
		return Math.max(0, needSigns - signsInSupply());
	}

	/** The supply slot holding a usable sign, or -1. */
	private int signSlot() {
		for (int i = 0; i < SIGN_SLOTS; i++) {
			if (SorterSupply.isSign(supply.getItem(i))) {
				return i;
			}
		}
		return -1;
	}

	private int chestSlot() {
		for (int i = SIGN_SLOTS; i < SUPPLY_SLOTS; i++) {
			if (SorterSupply.isChest(supply.getItem(i))) {
				return i;
			}
		}
		return -1;
	}

	/** True when the bot would do something to {@code t}'s label right now (re-write ours, or hang a new one). */
	private boolean labelWork(Level level, SortPlan.Target t) {
		if (plan == null) {
			return false;
		}
		SortPlan.Bucket b = plan.bucketOf(t);
		if (b == null && !plan.isSpare(t)) {
			return false;
		}
		SorterSupply.Label label = SorterSupply.findLabel(level, t);
		if (label != null) {
			return SorterSupply.needsRelabel(level, label, b);
		}
		// spares get no new sign; an old Stark sign on one is re-written to "Spare"
		return b != null && signSlot() >= 0 && SorterSupply.signSpot(level, t, worldPosition) != null;
	}

	/** Label {@code t} for its bucket: re-write a Stark sign, or hang a new one from the supply. */
	private void label(Level level, SortPlan.Target t) {
		if (plan == null) {
			return;
		}
		SortPlan.Bucket b = plan.bucketOf(t);
		if (b == null && !plan.isSpare(t)) {
			return;
		}
		SorterSupply.Label label = SorterSupply.findLabel(level, t);
		if (label != null) {
			SorterSupply.relabel(level, label, b);
			return;
		}
		if (b == null) {
			return;
		}
		int slot = signSlot();
		BlockPos spot = slot < 0 ? null : SorterSupply.signSpot(level, t, worldPosition);
		if (spot == null) {
			return;
		}
		if (SorterSupply.placeSign(level, t, spot, supply.getItem(slot).getItem(), b)) {
			supply.removeItem(slot, 1);
			signsPlaced++;
			level.playSound(null, spot, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.6f, 1.2f);
		}
	}

	/** True when {@code t} needs a service visit: a repack or label work. */
	private boolean needsService(Level level, SortPlan.Target t) {
		Container c = t.resolve(level);
		return c != null && (Stash.needsRepack(c, plan.orderFor(t)) || labelWork(level, t));
	}

	/** Repack and label a container the bot has open. */
	private void service(Level level, SortPlan.Target t, Container c) {
		Stash.repack(c, plan.orderFor(t));
		label(level, t);
		serviced.add(t.key());
	}

	/**
	 * More room for {@code bucket}: adopt a spare container in range (one no category needed), else place a chest
	 * from the supply and add it to the plan. Null when there is neither a spare nor a chest in the supply, or no
	 * valid spot (the latter is remembered for the end-of-job report).
	 */
	private SortPlan.Target placeChestFor(SortPlan.Bucket bucket) {
		if (plan == null || bucket == null || !(level instanceof ServerLevel server)) {
			return null;
		}
		SortPlan.Target spare = plan.adoptSpare(server, bucket);
		if (spare != null) {
			return spare;
		}
		if (noChestSpot) {
			return null;
		}
		int slot = chestSlot();
		if (slot < 0) {
			return null;
		}
		SorterSupply.ChestSpot spot = SorterSupply.chestSpot(server, worldPosition, facing(), RADIUS, scanFilter,
				plan.targets(), bucket);
		if (spot == null) {
			noChestSpot = true;
			return null;
		}
		if (!SorterSupply.placeChest(server, spot)) {
			return null;
		}
		supply.removeItem(slot, 1);
		chestsPlaced++;
		SortPlan.Target t = SortPlan.targetAt(server, spot.pos());
		if (t == null) {
			return null;
		}
		plan.addTarget(t, bucket);
		Vec3 c = t.center();
		server.playSound(null, spot.pos(), SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8f, 1.0f);
		server.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 0.4, c.z, 10, 0.3, 0.3, 0.3, 0.08);
		return t;
	}

	private static boolean hasSpareRoom(Level level, SortPlan p) {
		for (SortPlan.Target t : p.spares()) {
			Container c = t.resolve(level);
			if (c != null && Stash.packedSlots(c) < c.getContainerSize()) {
				return true;
			}
		}
		return false;
	}

	/** Place a first chest next to the station when there is nothing in range at all. */
	private boolean placeFirstChest(ServerLevel server) {
		int slot = chestSlot();
		if (slot < 0) {
			return false;
		}
		SorterSupply.ChestSpot spot = SorterSupply.chestSpot(server, worldPosition, facing(), RADIUS, scanFilter,
				List.of(), null);
		if (spot == null || !SorterSupply.placeChest(server, spot)) {
			return false;
		}
		supply.removeItem(slot, 1);
		server.playSound(null, spot.pos(), SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.8f, 1.0f);
		return true;
	}

	// ------------------------------------------------------------------ what the room needs (v0.14.21)

	/**
	 * Work out what the room needs right now: chests for stacks whose own category's containers would be full
	 * (the station's stacks and any misplaced stacks in the containers, simulated against repacked containers),
	 * and signs for every unlabelled container (plus one per needed chest).
	 */
	public Needs computeNeeds() {
		if (!(level instanceof ServerLevel server)) {
			return new Needs(0, 0, 0, 0);
		}
		List<SortPlan.Target> targets = scan(server);
		if (targets.isEmpty()) {
			int chests = (nonEmptySlots() + 26) / 27;
			return new Needs(chests, chests, 0, 0);
		}
		SortPlan p = SortPlan.build(server, targets, items, designations);
		Map<SortPlan.Target, Stash.Sim> sims = new HashMap<>();
		Map<SortPlan.Bucket, Integer> overflow = new HashMap<>();
		List<ItemStack> toFile = new ArrayList<>();
		List<SortPlan.Target> from = new ArrayList<>();
		for (ItemStack s : items) {
			if (!s.isEmpty()) {
				toFile.add(s);
				from.add(null);
			}
		}
		for (SortPlan.Target t : targets) {
			Container c = t.resolve(server);
			if (c == null) {
				continue;
			}
			for (int i = 0; i < c.getContainerSize(); i++) {
				ItemStack s = c.getItem(i);
				if (!s.isEmpty() && p.misplaced(t, s)) {
					toFile.add(s);
					from.add(t);
				}
			}
		}
		for (int k = 0; k < toFile.size(); k++) {
			ItemStack rest = toFile.get(k).copy();
			SortPlan.Bucket home = p.homeOf(rest);
			if (home == null) {
				continue;
			}
			for (SortPlan.Target t : home.targets()) {
				if (rest.isEmpty()) {
					break;
				}
				if (t.equals(from.get(k))) {
					continue;
				}
				Stash.Sim sim = sims.computeIfAbsent(t, tt -> {
					Container c = tt.resolve(server);
					return c == null ? null : Stash.Sim.of(c);
				});
				if (sim != null) {
					sim.insert(rest);
				}
			}
			if (!rest.isEmpty()) {
				int perStack = Math.max(1, rest.getMaxStackSize());
				overflow.merge(home, (rest.getCount() + perStack - 1) / perStack, Integer::sum);
			}
		}
		int over = 0;
		for (int slots : overflow.values()) {
			over += slots;
		}
		for (SortPlan.Target t : p.spares()) {
			Container c = t.resolve(server);
			if (c != null) {
				over -= c.getContainerSize() - Stash.packedSlots(c);
			}
		}
		int chests = Math.max(0, (over + 26) / 27);
		int signs = chests;
		int noFace = 0;
		for (SortPlan.Target t : targets) {
			if (p.isSpare(t) || SorterSupply.findLabel(server, t) != null) {
				continue;
			}
			if (SorterSupply.signSpot(server, t, worldPosition) != null) {
				signs++;
			} else {
				noFace++;
			}
		}
		return new Needs(chests, signs, noFace, targets.size());
	}

	/** Recompute {@link #computeNeeds} into the synced fields. */
	public Needs refreshNeeds() {
		Needs n = computeNeeds();
		needChests = n.chests();
		needSigns = n.signs();
		noSignFace = n.noFace();
		lastTargets = n.targets();
		needsDirty = false;
		return n;
	}

	/** The chat lines telling the player what to add (empty when nothing is short). */
	public List<Component> needsMessages() {
		List<Component> out = new ArrayList<>();
		if (shortChests() > 0) {
			out.add(Component.translatable("message.projecthero.stark_sorting_station.needs_chests", shortChests())
					.withStyle(ChatFormatting.GOLD));
		}
		if (shortSigns() > 0) {
			out.add(Component.translatable("message.projecthero.stark_sorting_station.needs_signs", needSigns, shortSigns())
					.withStyle(ChatFormatting.GOLD));
		}
		if (noSignFace > 0) {
			out.add(Component.translatable("message.projecthero.stark_sorting_station.no_sign_face", noSignFace)
					.withStyle(ChatFormatting.GRAY));
		}
		return out;
	}

	private void tellNeeds(ServerPlayer player) {
		refreshNeeds();
		if (player != null) {
			for (Component c : needsMessages()) {
				player.sendSystemMessage(c);
			}
		}
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
		List<SortPlan.Target> targets = scan(server);
		boolean placedFirst = targets.isEmpty() && placeFirstChest(server);
		if (placedFirst) {
			targets = scan(server);
		}
		if (targets.isEmpty()) {
			return Component.translatable("message.projecthero.stark_sorting_station.no_chests", RADIUS)
					.withStyle(ChatFormatting.RED);
		}
		tellNeeds(requester);
		plan = SortPlan.build(server, targets, items, designations);
		beginJob(true, requester);
		chestsPlaced = placedFirst ? 1 : 0;
		total = nonEmptySlots();

		if (!launch(server)) {
			return Component.empty();
		}
		return Component.translatable("message.projecthero.stark_sorting_station.plan", targets.size(), plan.summary())
				.withStyle(ChatFormatting.AQUA);
	}

	private void beginJob(boolean sort, ServerPlayer requester) {
		sortJob = sort;
		tidying = !sort;
		unsortable.clear();
		serviced.clear();
		noChestSpot = false;
		chestsPlaced = 0;
		signsPlaced = 0;
		tidyTrips = 0;
		tidyTripCap = 0;
		done = 0;
		total = 0;
		pendingSlots = 0;
		missingBotTicks = 0;
		this.requester = requester == null ? null : requester.getUUID();
	}

	/** Spawn the bot for the job just planned in {@link #plan}. False (and the plan dropped) if it could not spawn. */
	private boolean launch(ServerLevel server) {
		SorterBotEntity bot = SorterBotEntity.spawn(server, this);
		if (bot == null) {
			plan = null;
			tidying = false;
			return false;
		}
		botId = bot.getUUID();
		running = true;
		setChanged();

		Vec3 dock = dockPoint();
		server.sendParticles(ParticleTypes.ELECTRIC_SPARK, dock.x, dock.y + 0.3, dock.z, 24, 0.3, 0.3, 0.3, 0.15);
		server.sendParticles(ParticleTypes.END_ROD, dock.x, dock.y, dock.z, 10, 0.25, 0.1, 0.25, 0.04);
		server.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6f, 1.8f);
		return true;
	}

	/**
	 * v0.14.21: the station has nothing left that fits -- switch the Sort's bot to the chest-to-chest pass (misplaced
	 * stacks, repacks, labels) with the Sort's own plan. Returns false if there is no job to continue.
	 */
	public boolean beginFinishingPass() {
		if (!running || plan == null || tidying) {
			return false;
		}
		returnCarried();
		tidying = true;
		tidyTrips = 0;
		tidyTripCap = 64 + plan.targets().size() * 4;
		return true;
	}

	// ------------------------------------------------------------------ the Tidy button (v0.14.20)

	/**
	 * Re-sort the containers in range: plan from their current contents, then launch the bot to carry every
	 * misplaced stack to its category's chest, repack every chest and label it. Refused while any job (Sort or
	 * Tidy) is running. Returns the status line shown to the player; when there is nothing to do the bot is not
	 * launched at all.
	 */
	public Component startTidy(ServerPlayer requester) {
		if (!(level instanceof ServerLevel server)) {
			return Component.empty();
		}
		if (running) {
			return Component.translatable("message.projecthero.stark_sorting_station.already_running", done, total)
					.withStyle(ChatFormatting.GOLD);
		}
		returnCarried();
		List<SortPlan.Target> targets = scan(server);
		if (targets.isEmpty()) {
			return Component.translatable("message.projecthero.stark_sorting_station.no_chests", RADIUS)
					.withStyle(ChatFormatting.RED);
		}
		SortPlan tidyPlan = SortPlan.build(server, targets, List.of(), designations);
		SortPlan previous = plan;
		plan = tidyPlan; // labelWork / needsService read it
		int misplaced = 0;
		int movable = 0;
		int services = 0;
		SortPlan.Bucket stuckHome = null;
		for (SortPlan.Target t : targets) {
			Container c = t.resolve(server);
			if (c == null) {
				continue;
			}
			if (needsService(server, t)) {
				services++;
			}
			for (int i = 0; i < c.getContainerSize(); i++) {
				ItemStack s = c.getItem(i);
				if (!s.isEmpty() && tidyPlan.misplaced(t, s)) {
					misplaced++;
					if (tidyPlan.tidyDestinationFor(server, s, t) != null) {
						movable++;
					} else if (stuckHome == null) {
						stuckHome = tidyPlan.homeOf(s);
					}
				}
			}
		}
		boolean canPlace = stuckHome != null && (hasSpareRoom(server, tidyPlan) || (chestSlot() >= 0
				&& SorterSupply.chestSpot(server, worldPosition, facing(), RADIUS, scanFilter, targets, stuckHome) != null));
		if (movable == 0 && services == 0 && !canPlace) {
			plan = previous;
			tellNeeds(requester);
			if (misplaced > 0) {
				return Component.translatable("message.projecthero.stark_sorting_station.tidy_no_room", misplaced)
						.withStyle(ChatFormatting.GOLD);
			}
			return Component.translatable("message.projecthero.stark_sorting_station.already_tidy")
					.withStyle(ChatFormatting.GREEN);
		}
		tellNeeds(requester);
		beginJob(false, requester);
		tidyTripCap = (misplaced + 1) * 2 + targets.size() * 2 + 16;
		total = movable;
		if (!launch(server)) {
			return Component.empty();
		}
		return Component.translatable("message.projecthero.stark_sorting_station.tidy_plan", targets.size(), movable)
				.withStyle(ChatFormatting.AQUA);
	}

	/**
	 * The bot's next chest-to-chest trip: the first misplaced stack (containers in plan order, nearest first) that
	 * one of its own category's containers has room for -- placing a new chest from the supply for it if they are
	 * all full; once there are none, one service visit to each container that still needs a repack or a label.
	 * Null when the job is done. Nothing moves yet: see {@link #collectTidyLoad}.
	 */
	public TidyLoad nextTidyLoad() {
		if (plan == null || level == null || !tidying) {
			return null;
		}
		returnCarried();
		if (++tidyTrips > tidyTripCap) {
			return null;
		}
		for (SortPlan.Target t : plan.targets()) {
			Container c = t.resolve(level);
			if (c == null) {
				continue;
			}
			for (int i = 0; i < c.getContainerSize(); i++) {
				ItemStack s = c.getItem(i);
				if (s.isEmpty() || !plan.misplaced(t, s)) {
					continue;
				}
				SortPlan.Target dest = plan.tidyDestinationFor(level, s, t);
				if (dest == null) {
					dest = placeChestFor(plan.homeOf(s));
				}
				if (dest != null) {
					return new TidyLoad(t, dest);
				}
			}
		}
		for (SortPlan.Target t : plan.targets()) {
			if (!serviced.contains(t.key()) && needsService(level, t)) {
				return new TidyLoad(t, null);
			}
		}
		return null;
	}

	/**
	 * The bot is at {@code load.source()} with the lid open. Move up to {@value #MAX_TIDY_STACKS} misplaced stacks
	 * that belong in {@code load.dest()} from the chest into {@link #carried} -- never more than the destination
	 * can take (checked on a simulation), so a full destination leaves them where they are -- then repack and
	 * label the chest. Re-checked live: a chest broken or rearranged since the trip was planned just yields a
	 * smaller (or empty) load. Returns the number of stacks picked up.
	 */
	public int collectTidyLoad(TidyLoad load) {
		if (plan == null || level == null || load == null) {
			return 0;
		}
		returnCarried();
		Container source = load.source().resolve(level);
		if (source == null) {
			return 0;
		}
		Container dest = load.dest() == null ? null : load.dest().resolve(level);
		int emptied = 0;
		int picked = 0;
		if (dest != null) {
			Stash.Sim sim = Stash.Sim.of(dest);
			for (int i = 0; i < source.getContainerSize() && carried.size() < MAX_TIDY_STACKS; i++) {
				ItemStack s = source.getItem(i);
				SortPlan.Bucket home = plan.homeOf(s);
				if (s.isEmpty() || !plan.misplaced(load.source(), s) || home == null || !home.targets().contains(load.dest())) {
					continue;
				}
				int fits = sim.insert(s.copy());
				if (fits <= 0) {
					continue;
				}
				carried.add(s.split(fits));
				picked++;
				if (s.isEmpty()) {
					source.setItem(i, ItemStack.EMPTY);
					emptied++;
				}
			}
			if (picked > 0) {
				source.setChanged();
			}
		}
		service(level, load.source(), source);
		if (!sortJob) {
			pendingSlots = emptied;
			total = Math.max(total, done + emptied);
		}
		setChanged();
		return picked;
	}

	/** How many stacks are still in the wrong container (for the end-of-job report). */
	private int countMisplaced() {
		if (plan == null || level == null) {
			return 0;
		}
		int n = 0;
		for (SortPlan.Target t : plan.targets()) {
			Container c = t.resolve(level);
			if (c == null) {
				continue;
			}
			for (int i = 0; i < c.getContainerSize(); i++) {
				ItemStack s = c.getItem(i);
				if (!s.isEmpty() && plan.misplaced(t, s)) {
					n++;
				}
			}
		}
		return n;
	}

	// ------------------------------------------------------------------ the bot's side of the job

	/**
	 * Move the next load from {@link #items} into {@link #carried}: the first sortable stack plus up to
	 * {@value #MAX_LOAD_STACKS}-1 more stacks bound for the same container, never more than that container can take
	 * (checked on a simulation). A stack whose category's containers are all full gets a new chest from the supply
	 * if there is one. Returns null when nothing left in the station fits anywhere.
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
			if (dest == null) {
				dest = placeChestFor(plan.homeOf(stack));
			}
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

	/**
	 * File everything being carried into {@code target} (repacking it first, so the space the load was planned
	 * against is really there, and after), then label it. Anything that no longer fits stays carried (it goes home).
	 */
	public boolean depositCarried(SortPlan.Target target) {
		if (level == null) {
			return false;
		}
		Container container = target.resolve(level);
		if (container != null) {
			if (plan != null) {
				Stash.repack(container, plan.orderFor(target));
			}
			for (ItemStack s : carried) {
				Stash.insert(container, s);
			}
			carried.removeIf(ItemStack::isEmpty);
			if (plan != null) {
				service(level, target, container);
			} else {
				Stash.repack(container);
			}
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

	/** The bot is home with nothing left to do: end the job and tell whoever pressed the button how it went. */
	public void finish() {
		returnCarried();
		boolean wasRunning = running;
		int stuck = countMisplaced();
		if (plan != null) {
			designations.clear();
			designations.putAll(plan.designations());
		}
		running = false;
		tidying = false;
		botId = null;
		plan = null;
		serviced.clear();
		setChanged();
		if (!wasRunning || !(level instanceof ServerLevel server)) {
			return;
		}
		refreshNeeds();
		server.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.5f, 1.8f);
		ServerPlayer player = requester == null ? null : server.getServer().getPlayerList().getPlayer(requester);
		if (player == null) {
			return;
		}
		if (!sortJob) {
			player.sendSystemMessage(Component.translatable("message.projecthero.stark_sorting_station.tidy_done", done)
					.withStyle(ChatFormatting.GREEN));
		} else {
			player.sendSystemMessage(Component.translatable("message.projecthero.stark_sorting_station.done", done)
					.withStyle(ChatFormatting.GREEN));
			int left = nonEmptySlots();
			if (left > 0) {
				player.sendSystemMessage(Component.translatable("message.projecthero.stark_sorting_station.leftover", left)
						.withStyle(ChatFormatting.GOLD));
			}
		}
		if (stuck > 0) {
			player.sendSystemMessage(Component.translatable("message.projecthero.stark_sorting_station.tidy_stuck", stuck)
					.withStyle(ChatFormatting.GOLD));
		}
		if (chestsPlaced > 0 || signsPlaced > 0) {
			player.sendSystemMessage(Component.translatable("message.projecthero.stark_sorting_station.built",
					chestsPlaced, signsPlaced).withStyle(ChatFormatting.AQUA));
		}
		if (noChestSpot) {
			player.sendSystemMessage(Component.translatable("message.projecthero.stark_sorting_station.no_chest_spot")
					.withStyle(ChatFormatting.GOLD));
		}
		for (Component c : needsMessages()) {
			player.sendSystemMessage(c);
		}
	}

	/** Called when the block is broken: stop the job; the block drops {@link #items}, {@link #carried} and the supply. */
	public void onBroken() {
		if (level instanceof ServerLevel server && botId != null) {
			Entity bot = server.getEntity(botId);
			if (bot instanceof SorterBotEntity sorter) {
				sorter.abort();
			}
		}
		running = false;
		tidying = false;
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
			if (be.viewers > 0 && (be.needsDirty || server.getGameTime() % NEEDS_INTERVAL == 0)) {
				be.refreshNeeds();
			}
			return;
		}
		Entity bot = be.botId == null ? null : server.getEntity(be.botId);
		if (bot == null || bot.isRemoved()) {
			if (++be.missingBotTicks >= LOST_BOT_TICKS) {
				be.returnCarried();
				be.running = false;
				be.tidying = false;
				be.botId = null;
				be.plan = null;
				be.needsDirty = true;
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
		needsDirty = true;
		return result;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		needsDirty = true;
		return ContainerHelper.takeItem(items, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		items.set(slot, stack);
		if (stack.getCount() > stack.getMaxStackSize()) {
			stack.setCount(stack.getMaxStackSize());
		}
		unsortable.clear(slot); // a player (or hopper) changed it: worth another look
		needsDirty = true;
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

	@Override
	public void startOpen(Player player) {
		if (!player.isSpectator()) {
			viewers++;
			needsDirty = true;
		}
	}

	@Override
	public void stopOpen(Player player) {
		if (!player.isSpectator()) {
			viewers = Math.max(0, viewers - 1);
		}
	}

	// ------------------------------------------------------------------ menu

	@Override
	public Component getDisplayName() {
		return Component.translatable("container.projecthero.stark_sorting_station");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
		if (!running) {
			refreshNeeds();
		}
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
		supply.getItems().clear();
		if (tag.contains("Supply", Tag.TAG_COMPOUND)) {
			ContainerHelper.loadAllItems(tag.getCompound("Supply"), supply.getItems(), registries);
		}
		designations.clear();
		ListTag des = tag.getList("Designations", Tag.TAG_COMPOUND);
		for (int i = 0; i < des.size(); i++) {
			CompoundTag d = des.getCompound(i);
			Set<SortCategory> cats = EnumSet.noneOf(SortCategory.class);
			int mask = d.getInt("Cats");
			for (SortCategory c : SortCategory.values()) {
				if ((mask & (1 << c.ordinal())) != 0) {
					cats.add(c);
				}
			}
			if (!cats.isEmpty()) {
				designations.put(BlockPos.of(d.getLong("Pos")), cats);
			}
		}
		needsDirty = true;
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
		CompoundTag sup = new CompoundTag();
		ContainerHelper.saveAllItems(sup, supply.getItems(), registries);
		tag.put("Supply", sup);
		ListTag des = new ListTag();
		designations.forEach((pos, cats) -> {
			int mask = 0;
			for (SortCategory c : cats) {
				mask |= 1 << c.ordinal();
			}
			CompoundTag d = new CompoundTag();
			d.putLong("Pos", pos.asLong());
			d.putInt("Cats", mask);
			des.add(d);
		});
		tag.put("Designations", des);
	}
}
