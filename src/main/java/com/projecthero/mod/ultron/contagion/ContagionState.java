package com.projecthero.mod.ultron.contagion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * v0.16.1: everything the Ultron Contagion keeps for a world ({@code data/projecthero_contagion.dat}, overworld storage):
 * the stage, the world-wide <b>Saturation</b>, the three <b>Traces</b>, Ultron's <b>version</b> (Resurgence), every
 * <b>Nexus</b> with the exact original state of each block its corruption changed, the queue of blocks still to put back,
 * every <b>Firewall Beacon</b> (and how long its fuel lasts), every <b>Compromised</b> Stark machine, the finale's own
 * state ({@link SokoviaFinale}) and a crash crater's decal blocks.
 *
 * <p>Recorded blocks are stored as a shared block-state palette plus {@code long} positions -> palette index, so a fully
 * grown Nexus (a few thousand blocks) is a few tens of kilobytes. Nothing here is ever a static cache.
 */
public final class ContagionState extends SavedData {
	private static final String FILE_ID = ProjectHeroMod.MOD_ID + "_contagion";
	private static final SavedData.Factory<ContagionState> FACTORY = new SavedData.Factory<>(ContagionState::new, ContagionState::load,
			DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	public enum Stage {
		/** Nothing yet (Ultron never beaten, or the Contagion is off). */
		IDLE,
		/** Ultron was beaten; the Contagion begins at {@link #startAt}. */
		SCHEDULED,
		/** Patient zero is flickering red; the first Nexus grows from it at {@link #patientZeroAt} + 20 s. */
		PATIENT_ZERO,
		/** The infection is running. */
		ACTIVE,
		/** The Sokovia Protocol (the sky-citadel and Ultron Omega). */
		FINALE,
		/** Beaten: only Resurgence can bring him back. */
		DORMANT
	}

	/** One Nexus spire: where, how far its corruption has spread, and every block it changed. */
	public static final class NexusRecord {
		public final UUID id;
		public final BlockPos pos;
		public long born;
		public int radius;
		public int cursor;
		public boolean captured;
		public final Long2IntOpenHashMap blocks = new Long2IntOpenHashMap();

		NexusRecord(UUID id, BlockPos pos) {
			this.id = id;
			this.pos = pos.immutable();
		}
	}

	/** A Stark machine that a Nexus has taken over. */
	public static final class Machine {
		public UUID nexus;
		public long nextDroneAt;
		public final List<UUID> drones = new ArrayList<>();
	}

	public Stage stage = Stage.IDLE;
	public long startAt = -1L;
	public double saturation;
	public int traces;
	public int version = 1;
	public boolean omegaBeaten;
	public long lastRise = -1L;
	public long lastBeaconDrop = -1L;
	public long nextNexusAt = -1L;
	public long nextHuntersAt = -1L;
	public long nextResurgenceRoll = -1L;
	public BlockPos patientZero;
	public long patientZeroAt = -1L;
	public final Map<UUID, NexusRecord> nexus = new LinkedHashMap<>();
	public final Map<BlockPos, Long> beacons = new HashMap<>();
	public final Map<BlockPos, Machine> compromised = new HashMap<>();
	public SokoviaFinale finale;
	/** A crashed citadel's crater decal: positions / original states, reverted at {@link #craterRevertAt}. */
	public final Long2IntOpenHashMap crater = new Long2IntOpenHashMap();
	public long craterRevertAt = -1L;

	private final List<BlockState> palette = new ArrayList<>();
	private final Map<BlockState, Integer> paletteIndex = new HashMap<>();
	/** Blocks waiting to be put back (their chunk was unloaded, or the revert is spread over ticks). */
	public final LongArrayList pendingPos = new LongArrayList();
	public final IntArrayList pendingState = new IntArrayList();

	private ContagionState() {
	}

	public static ContagionState get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
	}

	public static ContagionState get(ServerLevel level) {
		return get(level.getServer());
	}

	// ---------------------------------------------------------------- palette

	public int stateId(BlockState state) {
		Integer i = paletteIndex.get(state);
		if (i != null) {
			return i;
		}
		palette.add(state);
		paletteIndex.put(state, palette.size() - 1);
		return palette.size() - 1;
	}

	public BlockState state(int id) {
		return id >= 0 && id < palette.size() ? palette.get(id) : null;
	}

	// ---------------------------------------------------------------- nexus records

	public NexusRecord addNexus(UUID id, BlockPos pos, long now) {
		NexusRecord r = new NexusRecord(id, pos);
		r.born = now;
		nexus.put(id, r);
		setDirty();
		return r;
	}

	/** Every recorded block of {@code id} goes into the revert queue and the record is dropped. */
	public void queueRevert(UUID id) {
		NexusRecord r = nexus.remove(id);
		if (r == null) {
			return;
		}
		for (Long2IntMap.Entry e : r.blocks.long2IntEntrySet()) {
			pendingPos.add(e.getLongKey());
			pendingState.add(e.getIntValue());
		}
		compromised.values().removeIf(m -> id.equals(m.nexus));
		setDirty();
	}

	/** The record that changed the block at {@code pos}, or null. */
	public NexusRecord recordAt(long pos) {
		for (NexusRecord r : nexus.values()) {
			if (r.blocks.containsKey(pos)) {
				return r;
			}
		}
		return null;
	}

	public int liveNexusCount() {
		return nexus.size();
	}

	public int totalRecorded() {
		int n = 0;
		for (NexusRecord r : nexus.values()) {
			n += r.blocks.size();
		}
		return n + pendingPos.size();
	}

	// ---------------------------------------------------------------- beacons

	/** Is a Firewall Beacon at {@code pos} fuelled at {@code now}? */
	public boolean beaconFuelled(BlockPos pos, long now) {
		Long until = beacons.get(pos);
		return until != null && until > now;
	}

	public int fuelledBeacons(long now) {
		int n = 0;
		for (long until : beacons.values()) {
			if (until > now) {
				n++;
			}
		}
		return n;
	}

	/** Is {@code pos} inside the radius of any fuelled Firewall Beacon? */
	public boolean protectedByBeacon(BlockPos pos, long now) {
		double r = ContagionConfig.beacon().radius;
		double r2 = r * r;
		for (Map.Entry<BlockPos, Long> e : beacons.entrySet()) {
			if (e.getValue() > now && e.getKey().distSqr(pos) <= r2) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- save / load

	private static ContagionState load(CompoundTag tag, HolderLookup.Provider registries) {
		ContagionState s = new ContagionState();
		try {
			s.stage = Stage.valueOf(tag.getString("Stage"));
		} catch (IllegalArgumentException e) {
			s.stage = Stage.IDLE;
		}
		s.startAt = tag.getLong("StartAt");
		s.saturation = tag.getDouble("Saturation");
		s.traces = tag.getInt("Traces");
		s.version = Math.max(1, tag.getInt("Version"));
		s.omegaBeaten = tag.getBoolean("OmegaBeaten");
		s.lastRise = tag.getLong("LastRise");
		s.lastBeaconDrop = tag.getLong("LastBeaconDrop");
		s.nextNexusAt = tag.getLong("NextNexusAt");
		s.nextHuntersAt = tag.getLong("NextHuntersAt");
		s.nextResurgenceRoll = tag.contains("NextResurgenceRoll") ? tag.getLong("NextResurgenceRoll") : -1L;
		if (tag.contains("PatientZero")) {
			s.patientZero = BlockPos.of(tag.getLong("PatientZero"));
			s.patientZeroAt = tag.getLong("PatientZeroAt");
		}
		ListTag pal = tag.getList("Palette", Tag.TAG_COMPOUND);
		for (int i = 0; i < pal.size(); i++) {
			BlockState st = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), pal.getCompound(i));
			s.palette.add(st);
			s.paletteIndex.putIfAbsent(st, i);
		}
		ListTag nl = tag.getList("Nexus", Tag.TAG_COMPOUND);
		for (int i = 0; i < nl.size(); i++) {
			CompoundTag t = nl.getCompound(i);
			if (!t.hasUUID("Id")) {
				continue;
			}
			NexusRecord r = new NexusRecord(t.getUUID("Id"), BlockPos.of(t.getLong("Pos")));
			r.born = t.getLong("Born");
			r.radius = t.getInt("Radius");
			r.cursor = t.getInt("Cursor");
			r.captured = t.getBoolean("Captured");
			long[] ps = t.getLongArray("Blocks");
			int[] ids = t.getIntArray("States");
			for (int k = 0; k < Math.min(ps.length, ids.length); k++) {
				r.blocks.put(ps[k], ids[k]);
			}
			s.nexus.put(r.id, r);
		}
		long[] pp = tag.getLongArray("PendingPos");
		int[] pst = tag.getIntArray("PendingState");
		for (int k = 0; k < Math.min(pp.length, pst.length); k++) {
			s.pendingPos.add(pp[k]);
			s.pendingState.add(pst[k]);
		}
		ListTag bl = tag.getList("Beacons", Tag.TAG_COMPOUND);
		for (int i = 0; i < bl.size(); i++) {
			CompoundTag t = bl.getCompound(i);
			s.beacons.put(BlockPos.of(t.getLong("Pos")), t.getLong("Until"));
		}
		ListTag ml = tag.getList("Machines", Tag.TAG_COMPOUND);
		for (int i = 0; i < ml.size(); i++) {
			CompoundTag t = ml.getCompound(i);
			Machine m = new Machine();
			m.nexus = t.hasUUID("Nexus") ? t.getUUID("Nexus") : null;
			m.nextDroneAt = t.getLong("Next");
			ListTag dl = t.getList("Drones", Tag.TAG_INT_ARRAY);
			for (int k = 0; k < dl.size(); k++) {
				m.drones.add(NbtUtils.loadUUID(dl.get(k)));
			}
			s.compromised.put(BlockPos.of(t.getLong("Pos")), m);
		}
		if (tag.contains("Finale", Tag.TAG_COMPOUND)) {
			s.finale = SokoviaFinale.load(tag.getCompound("Finale"));
		}
		long[] cp = tag.getLongArray("CraterPos");
		int[] cs = tag.getIntArray("CraterState");
		for (int k = 0; k < Math.min(cp.length, cs.length); k++) {
			s.crater.put(cp[k], cs[k]);
		}
		s.craterRevertAt = tag.contains("CraterRevertAt") ? tag.getLong("CraterRevertAt") : -1L;
		return s;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		tag.putString("Stage", stage.name());
		tag.putLong("StartAt", startAt);
		tag.putDouble("Saturation", saturation);
		tag.putInt("Traces", traces);
		tag.putInt("Version", version);
		tag.putBoolean("OmegaBeaten", omegaBeaten);
		tag.putLong("LastRise", lastRise);
		tag.putLong("LastBeaconDrop", lastBeaconDrop);
		tag.putLong("NextNexusAt", nextNexusAt);
		tag.putLong("NextHuntersAt", nextHuntersAt);
		tag.putLong("NextResurgenceRoll", nextResurgenceRoll);
		if (patientZero != null) {
			tag.putLong("PatientZero", patientZero.asLong());
			tag.putLong("PatientZeroAt", patientZeroAt);
		}
		ListTag pal = new ListTag();
		for (BlockState st : palette) {
			pal.add(NbtUtils.writeBlockState(st));
		}
		tag.put("Palette", pal);
		ListTag nl = new ListTag();
		for (NexusRecord r : nexus.values()) {
			CompoundTag t = new CompoundTag();
			t.putUUID("Id", r.id);
			t.putLong("Pos", r.pos.asLong());
			t.putLong("Born", r.born);
			t.putInt("Radius", r.radius);
			t.putInt("Cursor", r.cursor);
			t.putBoolean("Captured", r.captured);
			long[] ps = new long[r.blocks.size()];
			int[] ids = new int[r.blocks.size()];
			int k = 0;
			for (Long2IntMap.Entry e : r.blocks.long2IntEntrySet()) {
				ps[k] = e.getLongKey();
				ids[k] = e.getIntValue();
				k++;
			}
			t.putLongArray("Blocks", ps);
			t.putIntArray("States", ids);
			nl.add(t);
		}
		tag.put("Nexus", nl);
		tag.putLongArray("PendingPos", pendingPos.toLongArray());
		tag.putIntArray("PendingState", pendingState.toIntArray());
		ListTag bl = new ListTag();
		for (Map.Entry<BlockPos, Long> e : beacons.entrySet()) {
			CompoundTag t = new CompoundTag();
			t.putLong("Pos", e.getKey().asLong());
			t.putLong("Until", e.getValue());
			bl.add(t);
		}
		tag.put("Beacons", bl);
		ListTag ml = new ListTag();
		for (Map.Entry<BlockPos, Machine> e : compromised.entrySet()) {
			CompoundTag t = new CompoundTag();
			t.putLong("Pos", e.getKey().asLong());
			if (e.getValue().nexus != null) {
				t.putUUID("Nexus", e.getValue().nexus);
			}
			t.putLong("Next", e.getValue().nextDroneAt);
			ListTag dl = new ListTag();
			for (UUID u : e.getValue().drones) {
				dl.add(NbtUtils.createUUID(u));
			}
			t.put("Drones", dl);
			ml.add(t);
		}
		tag.put("Machines", ml);
		if (finale != null) {
			tag.put("Finale", finale.save());
		}
		long[] cp = new long[crater.size()];
		int[] cs = new int[crater.size()];
		int k = 0;
		for (Long2IntMap.Entry e : crater.long2IntEntrySet()) {
			cp[k] = e.getLongKey();
			cs[k] = e.getIntValue();
			k++;
		}
		tag.putLongArray("CraterPos", cp);
		tag.putIntArray("CraterState", cs);
		tag.putLong("CraterRevertAt", craterRevertAt);
		return tag;
	}
}
