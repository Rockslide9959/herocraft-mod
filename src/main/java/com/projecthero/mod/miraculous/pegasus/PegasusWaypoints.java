package com.projecthero.mod.miraculous.pegasus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.miraculous.pegasus.network.PegasusWaypointsPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6, C (Mark Waypoint): every player's Pegasus waypoints -- up to {@link PegasusConfig#WAYPOINT_SLOTS} each, the
 * oldest replaced -- saved with the world (the overworld's data storage, {@code projecthero_pegasus_waypoints}), so they
 * outlast detransforming, dying, logging out and restarting (they are NOT in the Miraculous state, which forgets its
 * kit values on every detransformation). Keyed by player UUID: a player who gives the Horse Miraculous up and gets it
 * back finds them again. The Voyage menu lists them; the owner's client gets them ({@link #sync}) for the markers.
 */
public final class PegasusWaypoints extends SavedData {
	private static final String FILE_ID = ProjectHeroMod.MOD_ID + "_pegasus_waypoints";
	private static final SavedData.Factory<PegasusWaypoints> FACTORY = new SavedData.Factory<>(PegasusWaypoints::new, PegasusWaypoints::load,
			DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	/** One saved spot: the dimension's id, the feet position, the yaw, and when it was saved (oldest goes first). */
	public record Waypoint(String dim, double x, double y, double z, float yaw, long savedAt) {
		public Vec3 pos() {
			return new Vec3(x, y, z);
		}

		CompoundTag save() {
			CompoundTag t = new CompoundTag();
			t.putString("dim", dim);
			t.putDouble("x", x);
			t.putDouble("y", y);
			t.putDouble("z", z);
			t.putFloat("yaw", yaw);
			t.putLong("at", savedAt);
			return t;
		}

		static Waypoint load(CompoundTag t) {
			return new Waypoint(t.getString("dim"), t.getDouble("x"), t.getDouble("y"), t.getDouble("z"), t.getFloat("yaw"), t.getLong("at"));
		}
	}

	private final Map<UUID, List<Waypoint>> byPlayer = new HashMap<>();

	public static PegasusWaypoints get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
	}

	public static PegasusWaypoints load(CompoundTag tag, HolderLookup.Provider registries) {
		PegasusWaypoints w = new PegasusWaypoints();
		CompoundTag players = tag.getCompound("players");
		for (String key : players.getAllKeys()) {
			UUID id;
			try {
				id = UUID.fromString(key);
			} catch (IllegalArgumentException e) {
				continue;
			}
			ListTag list = players.getList(key, Tag.TAG_COMPOUND);
			List<Waypoint> out = new ArrayList<>();
			for (int i = 0; i < list.size() && out.size() < PegasusConfig.WAYPOINT_SLOTS; i++) {
				out.add(Waypoint.load(list.getCompound(i)));
			}
			w.byPlayer.put(id, out);
		}
		return w;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		CompoundTag players = new CompoundTag();
		byPlayer.forEach((id, list) -> {
			ListTag l = new ListTag();
			list.forEach(w -> l.add(w.save()));
			players.put(id.toString(), l);
		});
		tag.put("players", players);
		return tag;
	}

	/** {@code player}'s waypoints, oldest first (a copy). */
	public List<Waypoint> of(UUID player) {
		return List.copyOf(byPlayer.getOrDefault(player, List.of()));
	}

	/** Saves a new one, replacing the oldest when all slots are taken. Returns the slot it went to (1-based). */
	public int add(UUID player, Waypoint w) {
		List<Waypoint> list = byPlayer.computeIfAbsent(player, k -> new ArrayList<>());
		int slot;
		if (list.size() >= PegasusConfig.WAYPOINT_SLOTS) {
			int oldest = 0;
			for (int i = 1; i < list.size(); i++) {
				if (list.get(i).savedAt() < list.get(oldest).savedAt()) {
					oldest = i;
				}
			}
			list.set(oldest, w);
			slot = oldest + 1;
		} else {
			list.add(w);
			slot = list.size();
		}
		setDirty();
		return slot;
	}

	public void clear(UUID player) {
		if (byPlayer.remove(player) != null) {
			setDirty();
		}
	}

	/** Sends {@code player} their waypoints (the markers only they see). */
	public static void sync(ServerPlayer player) {
		List<PegasusWaypointsPayload.Entry> out = new ArrayList<>();
		List<Waypoint> list = get(player.server).of(player.getUUID());
		for (int i = 0; i < list.size(); i++) {
			Waypoint w = list.get(i);
			out.add(new PegasusWaypointsPayload.Entry(i + 1, w.dim(), w.x(), w.y(), w.z()));
		}
		if (ServerPlayNetworking.canSend(player, PegasusWaypointsPayload.TYPE)) {
			ServerPlayNetworking.send(player, new PegasusWaypointsPayload(out));
		}
	}
}
