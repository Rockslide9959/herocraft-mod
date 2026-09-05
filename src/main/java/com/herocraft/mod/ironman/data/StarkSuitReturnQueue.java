package com.herocraft.mod.ironman.data;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.herocraft.mod.HeroCraftMod;
import com.herocraft.mod.ironman.IronManEnergy;
import com.herocraft.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.herocraft.mod.ironman.item.IronManItems;

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
 * Persistent queue of Iron Man suits that need to fly themselves home to a Suit Platform after their
 * wearer died ("changes 10": the armour must return to the platform on death <em>even if the platform
 * is in an unloaded chunk</em>). Mirrors {@link StarkPlatformRegistry} / {@code MjolnirRegistry}:
 * server-global {@link SavedData} on the overworld, only written when something changes.
 *
 * <p>On death the worn pieces are removed from the player and a {@link Pending} is recorded here
 * (suit id + which pieces + carried charge + post-crash integrity + target platform). Two things then
 * drain the queue, whichever happens first:
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
	private static final String FILE_ID = HeroCraftMod.MOD_ID + "_stark_suit_returns";
	/** How many queued targets to force-load and process per sweep. */
	private static final int PER_SWEEP = 2;
	private static final int SWEEP_INTERVAL_TICKS = 100;

	public record Pending(UUID owner, GlobalPos platform, String suitId, int pieceMask,
			float energy, float integrity) {

		public static final Codec<Pending> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("owner").forGetter(Pending::owner),
				GlobalPos.CODEC.fieldOf("platform").forGetter(Pending::platform),
				Codec.STRING.fieldOf("suit").forGetter(Pending::suitId),
				Codec.INT.fieldOf("pieces").forGetter(Pending::pieceMask),
				Codec.FLOAT.optionalFieldOf("energy", 0f).forGetter(Pending::energy),
				Codec.FLOAT.optionalFieldOf("integrity", IronManEnergy.MAX_INTEGRITY).forGetter(Pending::integrity)
		).apply(i, Pending::new));

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
			Pending.CODEC.parse(NbtOps.INSTANCE, list.getCompound(i))
					.resultOrPartial(err -> HeroCraftMod.LOGGER.warn("Dropping unreadable suit-return record: {}", err))
					.ifPresent(q.pending::add);
		}
		return q;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (Pending p : pending) {
			Pending.CODEC.encodeStart(NbtOps.INSTANCE, p)
					.resultOrPartial(err -> HeroCraftMod.LOGGER.warn("Failed to save suit-return record: {}", err))
					.ifPresent(list::add);
		}
		tag.put("Pending", list);
		return tag;
	}

	// ---------------- enqueue ----------------

	public void enqueue(UUID owner, GlobalPos platform, String suitId, int pieceMask, float energy, float integrity) {
		if (pieceMask == 0) {
			return;
		}
		pending.add(new Pending(owner, platform, suitId, pieceMask, energy, integrity));
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
			level.getChunk(pos.getX() >> 4, pos.getZ() >> 4); // one-off synchronous load, packet-context style
			processed++;
			if (level.getBlockEntity(pos) instanceof IronManSuitPlatformBlockEntity be) {
				q.deposit(p, be);
				it.remove();
				q.setDirty();
			} else {
				// platform is gone -- drop the reconstructed pieces where it stood so nothing is lost
				for (ArmorItem.Type type : p.piecesPresent()) {
					ItemStack stack = new ItemStack(IronManItems.armor(p.suitId(), type));
					IronManEnergy.stampStack(stack, p.energy(), p.integrity());
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
		for (ArmorItem.Type type : p.piecesPresent()) {
			if (be.holds(p.suitId(), type)) {
				continue;
			}
			ItemStack stack = new ItemStack(IronManItems.armor(p.suitId(), type));
			IronManEnergy.stampStack(stack, p.energy(), p.integrity());
			be.store(stack);
		}
	}
}
