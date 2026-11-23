package com.projecthero.mod.alarm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.alarm.block.AlarmBlocks;
import com.projecthero.mod.alarm.block.AlarmPanelBlock;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * v0.17.7: one guarded place -- a box in one dimension running an {@link AlarmProfile}. Kept in {@link AlarmZones}
 * (world storage). Saved: the box, the profile, the state, whether it was ever Alert (the Ghost bonus), the wave, the
 * panel / lamp / door / spawn-point positions found by scanning, and who already took the Ghost bonus. Everything
 * per-guard (suspicion meters, who is alerted, the guard list, wave mobs) is transient: it lives with the level
 * (this object is owned by the level's SavedData), never in a static map.
 */
public final class AlarmZone {
	/** 60 s of no guard seeing anyone after the last wave dies, then the zone calms down. */
	public static final int CALM_DOWN_TICKS = 1200;
	/** Ticks between waves once one is cleared. */
	public static final int WAVE_GAP_TICKS = 100;

	public final long id;
	public final BoundingBox box;
	public String profileId;
	public AlarmState state = AlarmState.CALM;
	public boolean everAlert;
	/** 0 = no waves; 1..WAVES = that wave is out; WAVES + 1 = all waves done. */
	public int wave;
	/** Alert shut the Blast Doors (cleared when a wave dies). */
	public boolean doorsShut;
	public final LongLinkedOpenHashSet panels = new LongLinkedOpenHashSet();
	public final LongLinkedOpenHashSet lamps = new LongLinkedOpenHashSet();
	public final LongLinkedOpenHashSet doors = new LongLinkedOpenHashSet();
	public final LongLinkedOpenHashSet spawns = new LongLinkedOpenHashSet();
	/** Doors opened with the keycard during this alert (they stay open). */
	public final LongOpenHashSet keyOpened = new LongOpenHashSet();
	/** Chunk columns already scanned for alarm blocks. */
	public final LongOpenHashSet scanned = new LongOpenHashSet();
	/** Players who already took this place's Ghost bonus. */
	public final Set<UUID> ghosts = new HashSet<>();

	// ---------------------------------------------------------------- transient (lives with the level)
	final Map<UUID, Float> suspicion = new HashMap<>();
	final Set<UUID> alerted = new HashSet<>();
	final Set<UUID> waveMobs = new HashSet<>();
	List<Mob> guards = new ArrayList<>();
	int quietTicks;
	int nextWaveIn = -1;
	int sirenIn;
	int age;
	boolean runnerWarned;
	/** Sniper -> ticks aimed at its current target. */
	final Map<UUID, Integer> aim = new HashMap<>();
	/** player -> {panel pos, last use tick, progress ticks} for the hold-to-sabotage. */
	final Map<UUID, long[]> sabotage = new HashMap<>();

	public AlarmZone(long id, BoundingBox box, String profileId) {
		this.id = id;
		this.box = box;
		this.profileId = profileId;
	}

	public AlarmProfile profile() {
		return AlarmProfiles.get(profileId);
	}

	public int tier() {
		AlarmProfile p = profile();
		return p == null ? 2 : p.tier();
	}

	public boolean contains(BlockPos pos) {
		return box.isInside(pos);
	}

	public AABB aabb() {
		return new AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1);
	}

	/** This guard's suspicion meter (0..1). */
	public float suspicion(Mob guard) {
		return suspicion.getOrDefault(guard.getUUID(), 0f);
	}

	public boolean isAlerted(Mob guard) {
		return alerted.contains(guard.getUUID());
	}

	/** The guards seen at the last refresh (alive or not; filter yourself). */
	public List<Mob> guards() {
		return guards;
	}

	/** Number of wave mobs still tracked. */
	public int waveMobCount() {
		return waveMobs.size();
	}

	/** Ticks of quiet counted toward calming down. */
	public int quietTicks() {
		return quietTicks;
	}

	/** True while the panel at {@code pos} is sabotaged (block state when loaded). */
	public static boolean panelDead(ServerLevel level, long pos) {
		BlockPos p = BlockPos.of(pos);
		if (!level.isLoaded(p)) {
			return false;
		}
		BlockState s = level.getBlockState(p);
		return !s.is(AlarmBlocks.PANEL) || s.getValue(AlarmPanelBlock.SABOTAGED);
	}

	/** The panel a sensor at {@code pos} is wired to: the nearest one in the zone (or null when there is none). */
	public BlockPos wiredPanel(BlockPos pos) {
		BlockPos best = null;
		double bestSq = Double.MAX_VALUE;
		for (long l : panels) {
			BlockPos p = BlockPos.of(l);
			double d = p.distSqr(pos);
			if (d < bestSq) {
				bestSq = d;
				best = p;
			}
		}
		return best;
	}

	/** A sensor at {@code pos} works unless the panel it is wired to has been sabotaged. */
	public boolean sensorLive(ServerLevel level, BlockPos pos) {
		BlockPos panel = wiredPanel(pos);
		return panel == null || !panelDead(level, panel.asLong());
	}

	/** Live (not sabotaged) panels. */
	public List<BlockPos> livePanels(ServerLevel level) {
		List<BlockPos> out = new ArrayList<>();
		for (long l : panels) {
			if (!panelDead(level, l)) {
				out.add(BlockPos.of(l));
			}
		}
		return out;
	}

	// ---------------------------------------------------------------- save

	CompoundTag save() {
		CompoundTag t = new CompoundTag();
		t.putLong("Id", id);
		t.putIntArray("Box", new int[] { box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ() });
		t.putString("Profile", profileId);
		t.putInt("State", state.ordinal());
		t.putBoolean("EverAlert", everAlert);
		t.putInt("Wave", wave);
		t.putBoolean("DoorsShut", doorsShut);
		t.put("Panels", longs(panels));
		t.put("Lamps", longs(lamps));
		t.put("Doors", longs(doors));
		t.put("Spawns", longs(spawns));
		t.put("KeyOpened", longs(keyOpened));
		t.put("Scanned", longs(scanned));
		ListTag g = new ListTag();
		for (UUID u : ghosts) {
			g.add(StringTag.valueOf(u.toString()));
		}
		t.put("Ghosts", g);
		return t;
	}

	static AlarmZone load(CompoundTag t) {
		int[] b = t.getIntArray("Box");
		if (b.length != 6) {
			return null;
		}
		AlarmZone z = new AlarmZone(t.getLong("Id"), new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]), t.getString("Profile"));
		z.state = AlarmState.byId(t.getInt("State"));
		z.everAlert = t.getBoolean("EverAlert");
		z.wave = t.getInt("Wave");
		z.doorsShut = t.getBoolean("DoorsShut");
		read(t, "Panels", z.panels);
		read(t, "Lamps", z.lamps);
		read(t, "Doors", z.doors);
		read(t, "Spawns", z.spawns);
		read(t, "KeyOpened", z.keyOpened);
		read(t, "Scanned", z.scanned);
		ListTag g = t.getList("Ghosts", Tag.TAG_STRING);
		for (int i = 0; i < g.size(); i++) {
			try {
				z.ghosts.add(UUID.fromString(g.getString(i)));
			} catch (IllegalArgumentException ignored) {
				// a damaged entry -- skip it
			}
		}
		if (z.state == AlarmState.SUSPICIOUS) {
			z.state = AlarmState.CALM; // suspicion is transient
		}
		return z;
	}

	private static LongArrayTag longs(LongSet set) {
		return new LongArrayTag(new LongArrayList(set).toLongArray());
	}

	private static void read(CompoundTag t, String key, LongSet into) {
		for (long l : t.getLongArray(key)) {
			into.add(l);
		}
	}
}
