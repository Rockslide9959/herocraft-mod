package com.projecthero.mod.squad;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The server-wide squad roster, persisted on the overworld's {@code DimensionDataStorage} exactly like
 * {@link com.projecthero.mod.hammer.MjolnirRegistry} (the conventional place for server-global
 * {@link SavedData}), so a squad survives a restart and a member's relog.
 *
 * <p>Pending invitations are deliberately <em>not</em> saved: an invite is a live social offer with a
 * short life, and one left dangling across a server restart is noise rather than useful state.
 */
public final class SquadManager extends SavedData {
	private static final String FILE_ID = ProjectHeroMod.MOD_ID + "_squads";
	private static final String TAG_SQUADS = "Squads";

	/** How long an invitation stands before it lapses. */
	public static final long INVITE_TICKS = 60L * 20L;
	/** Hard cap, so one squad cannot swallow a whole server and make friendly fire meaningless. */
	public static final int MAX_MEMBERS = 12;
	public static final int MAX_NAME_LENGTH = 24;

	/**
	 * See {@link com.projecthero.mod.hammer.MjolnirRegistry}'s own factory for why the
	 * {@link DataFixTypes} must be non-null even though nothing here needs fixing.
	 */
	private static final SavedData.Factory<SquadManager> FACTORY = new SavedData.Factory<>(
			SquadManager::new, SquadManager::load, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);

	private final Map<UUID, Squad> squads = new LinkedHashMap<>();
	/** invitee -> (squad id -> game time the offer lapses). Transient by design. */
	private final Map<UUID, Map<UUID, Long>> invites = new HashMap<>();

	public static SquadManager get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
	}

	public static SquadManager get(ServerLevel level) {
		return get(level.getServer());
	}

	private static SquadManager load(CompoundTag tag, HolderLookup.Provider registries) {
		SquadManager manager = new SquadManager();
		ListTag list = tag.getList(TAG_SQUADS, Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			Squad.CODEC.parse(NbtOps.INSTANCE, list.getCompound(i))
					.resultOrPartial(error -> ProjectHeroMod.LOGGER.warn("Dropping unreadable squad: {}", error))
					.ifPresent(squad -> manager.squads.put(squad.id(), squad));
		}
		return manager;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (Squad squad : squads.values()) {
			Squad.CODEC.encodeStart(NbtOps.INSTANCE, squad)
					.resultOrPartial(error -> ProjectHeroMod.LOGGER.warn("Failed to save squad: {}", error))
					.ifPresent(list::add);
		}
		tag.put(TAG_SQUADS, list);
		return tag;
	}

	// ---------------- lookups ----------------

	public Squad squadOf(UUID player) {
		for (Squad squad : squads.values()) {
			if (squad.has(player)) {
				return squad;
			}
		}
		return null;
	}

	public Squad byId(UUID id) {
		return squads.get(id);
	}

	public List<Squad> all() {
		return new ArrayList<>(squads.values());
	}

	public boolean sameSquad(UUID a, UUID b) {
		if (a.equals(b)) {
			return false; // you can always hurt yourself
		}
		Squad squad = squadOf(a);
		return squad != null && squad.has(b);
	}

	// ---------------- mutation ----------------

	public Squad create(String name, UUID leader) {
		Squad squad = Squad.create(name, leader);
		squads.put(squad.id(), squad);
		setDirty();
		return squad;
	}

	public void addMember(Squad squad, UUID player) {
		squad.add(player);
		clearInvites(player);
		setDirty();
	}

	/**
	 * Take a player out of their squad. The leader leaving hands the squad to whoever is left; the last
	 * member leaving disbands it, so an empty squad can never linger and block the name.
	 */
	public void removeMember(Squad squad, UUID player) {
		squad.remove(player);
		if (squad.size() == 0) {
			squads.remove(squad.id());
		} else if (squad.leader().equals(player)) {
			squad.setLeader(squad.members().iterator().next());
		}
		setDirty();
	}

	public void disband(Squad squad) {
		squads.remove(squad.id());
		setDirty();
	}

	public void rename(Squad squad, String name) {
		squad.setName(name);
		setDirty();
	}

	// ---------------- invitations ----------------

	public void invite(Squad squad, UUID invitee, long now) {
		invites.computeIfAbsent(invitee, k -> new HashMap<>()).put(squad.id(), now + INVITE_TICKS);
	}

	/** The squad this player has a live invitation to, or {@code null}. {@code squadId} may be null for "any". */
	public Squad pendingInvite(UUID invitee, UUID squadId, long now) {
		Map<UUID, Long> pending = invites.get(invitee);
		if (pending == null) {
			return null;
		}
		pending.values().removeIf(expiry -> expiry <= now);
		for (Map.Entry<UUID, Long> e : pending.entrySet()) {
			if (squadId != null && !squadId.equals(e.getKey())) {
				continue;
			}
			Squad squad = squads.get(e.getKey());
			if (squad != null) {
				return squad;
			}
		}
		return null;
	}

	public List<Squad> pendingInvites(UUID invitee, long now) {
		List<Squad> out = new ArrayList<>();
		Map<UUID, Long> pending = invites.get(invitee);
		if (pending == null) {
			return out;
		}
		pending.values().removeIf(expiry -> expiry <= now);
		for (UUID id : pending.keySet()) {
			Squad squad = squads.get(id);
			if (squad != null) {
				out.add(squad);
			}
		}
		return out;
	}

	public void clearInvites(UUID invitee) {
		invites.remove(invitee);
	}
}
