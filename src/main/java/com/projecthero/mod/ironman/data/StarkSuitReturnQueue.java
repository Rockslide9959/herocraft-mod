package com.projecthero.mod.ironman.data;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.item.IronManItems;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persistent queue of Iron Man suits that need to fly themselves home to a Suit Platform that is out of
 * reach -- in an unloaded chunk or another dimension. Mirrors {@link StarkPlatformRegistry} /
 * {@code MjolnirRegistry}: server-global {@link SavedData} on the overworld, only written when something
 * changes.
 *
 * <p>History: "changes 10" sent a dead wearer's suit home through this queue. That death recovery was
 * removed in v0.15.11 (explicit user request) and confirmed again in v0.15.19: a suit worn (or carried)
 * on death now simply drops at the death spot like any vanilla armour, and stays with the player under
 * keepInventory -- nothing on the death path writes here any more. What still does: send-home (Sneak + C
 * picker), a Sentry-mode suit or a loose suit part heading back to its platform, when
 * {@code IronManPlatformReturn#depositNow} cannot rack the stacks at once. Records written by an older
 * version (death returns included) still load and are still delivered exactly as before, so an
 * existing world loses nothing.
 *
 * <p>Each {@link Pending} holds the suit id + which pieces + carried charge + integrity + the target
 * platform (and, since v0.14.21, the real stacks). Two things drain the queue, whichever happens first:
 * <ul>
 *   <li>{@link #tick} runs a slow server-wide sweep that force-loads a couple of target chunks per
 *       pass (the same one-off synchronous load {@code IronManSuitCall.tickPending} already uses) and
 *       docks the suit;</li>
 *   <li>{@link IronManSuitPlatformBlockEntity#serverTick} calls {@link #absorb} when a platform ticks,
 *       so a suit also docks the instant its destination chunk loads for any other reason.</li>
 * </ul>
 * Pieces are reconstructed deterministically from the suit id (exactly like the suit-down path and
 * the courier's safe-drop), so nothing here needs to serialize an {@link ItemStack}.
 */
public final class StarkSuitReturnQueue extends SavedData {
	private static final String FILE_ID = ProjectHeroMod.MOD_ID + "_stark_suit_returns";
	/** How many queued targets to force-load and process per sweep. */
	private static final int PER_SWEEP = 2;
	private static final int SWEEP_INTERVAL_TICKS = 100;

	/**
	 * v0.14.21: {@code stacks} carries the real armour stacks (enchantments, names, every component) home; it is empty
	 * only for a record written by an older version, which still rebuilds fresh pieces from {@code pieceMask}.
	 */
	public record Pending(UUID owner, GlobalPos platform, String suitId, int pieceMask,
			float energy, float integrity, List<ItemStack> stacks) {

		public static final Codec<Pending> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("owner").forGetter(Pending::owner),
				GlobalPos.CODEC.fieldOf("platform").forGetter(Pending::platform),
				Codec.STRING.fieldOf("suit").forGetter(Pending::suitId),
				Codec.INT.fieldOf("pieces").forGetter(Pending::pieceMask),
				Codec.FLOAT.optionalFieldOf("energy", 0f).forGetter(Pending::energy),
				Codec.FLOAT.optionalFieldOf("integrity", IronManEnergy.MAX_INTEGRITY).forGetter(Pending::integrity),
				ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("stacks", List.of()).forGetter(Pending::stacks)
		).apply(i, Pending::new));

		/**
		 * The stacks to put on the platform: the real ones (v0.14.21), or -- for an older record -- fresh pieces rebuilt
		 * from the mask (an unknown suit id rebuilds nothing rather than crashing on {@code new ItemStack(null)}).
		 */
		public List<ItemStack> stacksToReturn() {
			List<ItemStack> out = new ArrayList<>();
			if (!stacks.isEmpty()) {
				for (ItemStack s : stacks) {
					if (!s.isEmpty()) {
						out.add(s.copy());
					}
				}
				return out;
			}
			for (ArmorItem.Type type : piecesPresent()) {
				IronManArmorItem item = IronManItems.armor(suitId, type);
				if (item == null) {
					ProjectHeroMod.LOGGER.warn("[ProjectHero] dropping unreturnable Stark suit piece: unknown suit '{}'", suitId);
					continue;
				}
				ItemStack stack = new ItemStack(item);
				IronManEnergy.stampStack(stack, energy, integrity);
				out.add(stack);
			}
			return out;
		}

		/** The armour types this record still owes the platform (bit 0 HELMET .. bit 3 BOOTS). */
		public List<ArmorItem.Type> piecesPresent() {
			List<ArmorItem.Type> out = new ArrayList<>(4);
			for (int bit = 0; bit < BY_BIT.length; bit++) {
				if ((pieceMask & (1 << bit)) != 0) {
					out.add(BY_BIT[bit]);
				}
			}
			return out;
		}
	}

	private final List<Pending> pending = new ArrayList<>();

	private static final SavedData.Factory<StarkSuitReturnQueue> FACTORY = new SavedData.Factory<>(
			StarkSuitReturnQueue::new, StarkSuitReturnQueue::load, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	public static StarkSuitReturnQueue get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
	}

	public static StarkSuitReturnQueue get(ServerLevel level) {
		return get(level.getServer());
	}

	private static StarkSuitReturnQueue load(CompoundTag tag, HolderLookup.Provider registries) {
		StarkSuitReturnQueue q = new StarkSuitReturnQueue();
		ListTag list = tag.getList("Pending", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			Pending.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), list.getCompound(i))
					.resultOrPartial(err -> ProjectHeroMod.LOGGER.warn("Dropping unreadable suit-return record: {}", err))
					.ifPresent(q.pending::add);
		}
		return q;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (Pending p : pending) {
			Pending.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), p)
					.resultOrPartial(err -> ProjectHeroMod.LOGGER.warn("Failed to save suit-return record: {}", err))
					.ifPresent(list::add);
		}
		tag.put("Pending", list);
		return tag;
	}

	// ---------------- enqueue ----------------

	public void enqueue(UUID owner, GlobalPos platform, String suitId, int pieceMask, float energy, float integrity) {
		enqueue(owner, platform, suitId, pieceMask, energy, integrity, List.of());
	}

	/** v0.14.21: queue the real stacks (copied) to fly home. */
	public void enqueue(UUID owner, GlobalPos platform, String suitId, int pieceMask, float energy, float integrity,
			List<ItemStack> stacks) {
		if (pieceMask == 0 && stacks.isEmpty()) {
			return;
		}
		List<ItemStack> copies = new ArrayList<>();
		for (ItemStack s : stacks) {
			if (!s.isEmpty()) {
				copies.add(s.copy());
			}
		}
		pending.add(new Pending(owner, platform, suitId, pieceMask, energy, integrity, List.copyOf(copies)));
		setDirty();
	}

	public boolean isEmpty() {
		return pending.isEmpty();
	}

	// ---------------- drain ----------------

	private static final ArmorItem.Type[] BY_BIT = {
			ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };

	/**
	 * Docks any pending suit whose destination is this platform. Called from the platform's own
	 * server tick, so no chunk force-load is involved on this path.
	 */
	public void absorb(ServerLevel level, BlockPos platformPos, IronManSuitPlatformBlockEntity be) {
		GlobalPos key = GlobalPos.of(level.dimension(), platformPos.immutable());
		boolean changed = false;
		for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
			Pending p = it.next();
			if (!p.platform().equals(key)) {
				continue;
			}
			deposit(p, be);
			it.remove();
			changed = true;
		}
		if (changed) {
			setDirty();
		}
	}

	/** True if any queued suit is destined for this platform position. */
	public boolean hasPendingFor(ServerLevel level, BlockPos platformPos) {
		GlobalPos key = GlobalPos.of(level.dimension(), platformPos.immutable());
		for (Pending p : pending) {
			if (p.platform().equals(key)) {
				return true;
			}
		}
		return false;
	}

	/** Slow server-wide sweep: force-load a couple of target chunks per pass and dock those suits. */
	public static void tick(MinecraftServer server) {
		if (server.getTickCount() % SWEEP_INTERVAL_TICKS != 0) {
			return;
		}
		StarkSuitReturnQueue q = get(server);
		if (q.pending.isEmpty()) {
			return;
		}
		int processed = 0;
		for (Iterator<Pending> it = q.pending.iterator(); it.hasNext() && processed < PER_SWEEP; ) {
			Pending p = it.next();
			ServerLevel level = server.getLevel(p.platform().dimension());
			if (level == null) {
				continue; // dimension not loaded this run -- try again next sweep
			}
			BlockPos pos = p.platform().pos();
			// v0.14.29: loaded + held by a short IronManChunkTickets ticket, so the deposit is saved with the chunk
			IronManSuitPlatformBlockEntity be = com.projecthero.mod.ironman.suit.IronManChunkTickets.loadPlatform(level, pos);
			processed++;
			if (be != null) {
				q.deposit(p, be);
				it.remove();
				q.setDirty();
			} else {
				// platform is gone -- drop the pieces where it stood so nothing is lost
				for (ItemStack stack : p.stacksToReturn()) {
					net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
							level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, stack);
					level.addFreshEntity(drop);
				}
				it.remove();
				q.setDirty();
			}
		}
	}

	private void deposit(Pending p, IronManSuitPlatformBlockEntity be) {
		if (be.owner().isEmpty()) {
			be.bindTo(p.owner());
		}
		for (ItemStack stack : p.stacksToReturn()) {
			if (!be.store(stack) && be.getLevel() != null) {
				// that slot is already taken on this rack (or a different mark is racked): drop it on top, never lose it
				BlockPos pos = be.getBlockPos();
				be.getLevel().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(be.getLevel(),
						pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, stack));
			}
		}
	}
}
