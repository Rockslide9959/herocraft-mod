package com.herocraft.mod.event;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Who is taking part in an event, and whether they are currently <em>present</em> for it.
 *
 * <p>Deliberately cheap: {@link #refresh} does one pass over the level's already-in-memory player
 * list (never an entity scan, never a chunk query) and is called on the event's own slow tick, not
 * every game tick. Everything else is map lookups.
 *
 * <p>A participant who walks out of the area is <em>not</em> forgotten -- they keep their reward
 * eligibility and simply stop counting as present, which is what drives multiplayer scaling
 * (spec section 22: "someone far away should not increase boss health") and the abandon timer.
 */
public final class EventParticipants {
	/** Everyone who has ever been present, in join order. */
	private final Map<UUID, Record> byId = new LinkedHashMap<>();
	/** Recomputed by {@link #refresh}; never persisted. */
	private final List<ServerPlayer> presentNow = new ArrayList<>();

	public static final class Record {
		public final UUID id;
		public String name;
		/** Game time this player was last inside the event area. */
		public long lastPresentTick;
		/** True once they have been present while the event was actually running. */
		public boolean eligible;
		/** Deaths during this event -- used by the "Deathless" advancement. */
		public int deaths;

		Record(UUID id, String name) {
			this.id = id;
			this.name = name;
		}
	}

	public void refresh(ServerLevel level, BlockPos center, double presentRadius, boolean running) {
		presentNow.clear();
		double sq = presentRadius * presentRadius;
		long now = level.getGameTime();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}
			if (player.distanceToSqr(center.getX() + 0.5, player.getY(), center.getZ() + 0.5) > sq) {
				continue;
			}
			presentNow.add(player);
			Record r = byId.computeIfAbsent(player.getUUID(),
					id -> new Record(id, player.getGameProfile().getName()));
			r.name = player.getGameProfile().getName();
			r.lastPresentTick = now;
			if (running) {
				r.eligible = true;
			}
		}
	}

	/** Players inside the area as of the last {@link #refresh}. Never null, may be empty. */
	public List<ServerPlayer> present() {
		return presentNow;
	}

	public int presentCount() {
		return presentNow.size();
	}

	/** Everyone who has earned reward eligibility, whether or not they are online right now. */
	public List<UUID> eligible() {
		List<UUID> out = new ArrayList<>();
		for (Record r : byId.values()) {
			if (r.eligible) {
				out.add(r.id);
			}
		}
		return out;
	}

	public boolean isEligible(UUID id) {
		Record r = byId.get(id);
		return r != null && r.eligible;
	}

	public Record record(UUID id) {
		return byId.get(id);
	}

	public void recordDeath(UUID id) {
		Record r = byId.get(id);
		if (r != null) {
			r.deaths++;
		}
	}

	public int deaths(UUID id) {
		Record r = byId.get(id);
		return r == null ? 0 : r.deaths;
	}

	/** How many distinct players have been eligible -- the number the "Last Stand" advancement counts. */
	public int eligibleCount() {
		int n = 0;
		for (Record r : byId.values()) {
			if (r.eligible) {
				n++;
			}
		}
		return n;
	}

	/** The most recent tick at which anybody at all was present. */
	public long lastAnyonePresentTick() {
		long best = Long.MIN_VALUE;
		for (Record r : byId.values()) {
			best = Math.max(best, r.lastPresentTick);
		}
		return best;
	}

	/** Online, eligible participants -- the audience for messages and boss bars. */
	public List<ServerPlayer> onlineEligible(ServerLevel level) {
		List<ServerPlayer> out = new ArrayList<>();
		for (Record r : byId.values()) {
			if (!r.eligible) {
				continue;
			}
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(r.id);
			if (p != null) {
				out.add(p);
			}
		}
		return out;
	}

	// ---------------- persistence ----------------

	public void save(CompoundTag tag) {
		ListTag list = new ListTag();
		for (Record r : byId.values()) {
			CompoundTag t = new CompoundTag();
			t.putUUID("Id", r.id);
			t.putString("Name", r.name == null ? "" : r.name);
			t.putLong("LastPresent", r.lastPresentTick);
			t.putBoolean("Eligible", r.eligible);
			t.putInt("Deaths", r.deaths);
			list.add(t);
		}
		tag.put("Participants", list);
	}

	public void load(CompoundTag tag) {
		byId.clear();
		ListTag list = tag.getList("Participants", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = list.getCompound(i);
			if (!t.hasUUID("Id")) {
				continue;
			}
			Record r = new Record(t.getUUID("Id"), t.getString("Name"));
			r.lastPresentTick = t.getLong("LastPresent");
			r.eligible = t.getBoolean("Eligible");
			r.deaths = t.getInt("Deaths");
			byId.put(r.id, r);
		}
	}

}
