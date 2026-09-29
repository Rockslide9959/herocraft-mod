package com.projecthero.mod.darkseid.raid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * The official participant list of one Darkseid Raid -- separate from the event framework's own
 * {@code EventParticipants} (which only tracks who is nearby, for pausing/abandoning) because this raid has rules
 * that one doesn't: a hard cap ({@code maxParticipants}), a roster that seals once Darkseid is down to half health,
 * re-entry lockouts after a death, and reward eligibility decided by all of that.
 *
 * <p>Plain data: the raid does all the deciding and persists it with {@link #save}/{@link #load}.
 */
public final class DarkseidRoster {
	public static final class Member {
		public final UUID id;
		public String name;
		/** Registered when the beacon was used (vs. joining later, before the seal). */
		public boolean original;
		/** Gets the raid rewards on victory. */
		public boolean rewardEligible = true;
		/** Within the raid area (arena + grace margin) as of the last refresh. Transient. */
		public boolean inside;
		/** Alive as of the last refresh / death. */
		public boolean alive = true;
		/** Game time before which this member may not re-enter the arena (after a death). */
		public long lockoutUntil;
		public int deaths;
		/** Ticks spent outside the arena boundary in a row. Transient. */
		public int outsideTicks;

		Member(UUID id, String name) {
			this.id = id;
			this.name = name;
		}

		public boolean lockedOut(long now) {
			return now < lockoutUntil;
		}
	}

	private final Map<UUID, Member> members = new LinkedHashMap<>();
	/** Non-members already told the roster is sealed/full, so they are told once. Transient. */
	private final Set<UUID> toldOutsider = new HashSet<>();

	public Member get(UUID id) {
		return members.get(id);
	}

	public boolean contains(UUID id) {
		return members.containsKey(id);
	}

	public Member add(UUID id, String name, boolean original) {
		Member m = members.computeIfAbsent(id, k -> new Member(k, name));
		m.original = m.original || original;
		m.name = name;
		return m;
	}

	public int size() {
		return members.size();
	}

	public boolean isEmpty() {
		return members.isEmpty();
	}

	public Collection<Member> all() {
		return members.values();
	}

	public List<UUID> ids() {
		return new ArrayList<>(members.keySet());
	}

	/** True the first time an outsider is seen (so they get one message, not one per tick). */
	public boolean markToldOutsider(UUID id) {
		return toldOutsider.add(id);
	}

	public void save(CompoundTag tag) {
		ListTag list = new ListTag();
		for (Member m : members.values()) {
			CompoundTag t = new CompoundTag();
			t.putUUID("Id", m.id);
			t.putString("Name", m.name == null ? "" : m.name);
			t.putBoolean("Original", m.original);
			t.putBoolean("Reward", m.rewardEligible);
			t.putLong("Lockout", m.lockoutUntil);
			t.putInt("Deaths", m.deaths);
			list.add(t);
		}
		tag.put("Roster", list);
	}

	public void load(CompoundTag tag) {
		members.clear();
		ListTag list = tag.getList("Roster", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = list.getCompound(i);
			if (!t.hasUUID("Id")) {
				continue;
			}
			Member m = new Member(t.getUUID("Id"), t.getString("Name"));
			m.original = t.getBoolean("Original");
			m.rewardEligible = !t.contains("Reward") || t.getBoolean("Reward");
			m.lockoutUntil = t.getLong("Lockout");
			m.deaths = t.getInt("Deaths");
			members.put(m.id, m);
		}
	}
}
