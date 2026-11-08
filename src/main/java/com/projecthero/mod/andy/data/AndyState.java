package com.projecthero.mod.andy.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * v0.17.3: the whole Andy (Undead Unluck) power for one player in one attachment ({@code projecthero:andy_state}):
 * persistent, {@code copyOnDeath} and synced to every client (every viewer sees a part break and regrow from it). Only
 * {@code Andy} and its helpers change it, always through a {@link #copy()}.
 *
 * <h2>The six parts</h2>
 * Index {@link #HEAD}..{@link #LEFT_LEG}. Each has HP ({@link #partHp}) and three times in {@link #partTimes}: when it broke
 * (0 = whole), when its regrowth started (0 = still waiting) and when the regrowth ends. Clients draw the part hidden from
 * the break until the regrowth starts, then re-forming pixel by pixel until it ends. How it went ({@link #kind}) decides the
 * look: {@link #KIND_BURST} bursts into the skin's pixels, {@link #KIND_LAUNCHED} left as a projectile (no burst: the
 * projectile is the part), {@link #KIND_SHATTER} went in the Undead shatter.
 */
public final class AndyState {
	public static final int HEAD = 0;
	public static final int BODY = 1;
	public static final int RIGHT_ARM = 2;
	public static final int LEFT_ARM = 3;
	public static final int RIGHT_LEG = 4;
	public static final int LEFT_LEG = 5;
	public static final int PARTS = 6;

	public static final int KIND_NONE = 0;
	public static final int KIND_BURST = 1;
	public static final int KIND_LAUNCHED = 2;
	public static final int KIND_SHATTER = 3;

	public static final int ANIM_NONE = 0;
	public static final int ANIM_FINGER = 1;
	public static final int ANIM_CRIMSON = 2;
	public static final int ANIM_CRESCENT = 3;
	public static final int ANIM_GREAT_CRESCENT = 4;
	public static final int ANIM_GLIDE = 5;
	public static final int ANIM_VORTEX = 6;
	public static final int ANIM_HEAD = 7;
	public static final int ANIM_CARD = 8;
	public static final int ANIM_QUARDEAD = 9;
	public static final int ANIM_VULCAN = 10;
	public static final int ANIM_DEAD_BLADE = 11;
	public static final int ANIM_DIVISION = 12;
	public static final int ANIM_HEADBUTT = 13;
	public static final int ANIM_CARD_BACK = 14;

	public static final int FLAG_PROPELLING = 1;
	public static final int FLAG_DEAD_LINE = 2;
	public static final int FLAG_DEAD_ROAD = 4;
	public static final int FLAG_GLIDING = 8;

	public boolean hasPower;
	public List<Float> partHp;
	public List<Long> partTimes;
	/** 3 bits per part: how it last broke. */
	public int kinds;
	public int regenSpeed;
	public Map<String, Long> abilityReadyAt;
	/** Victor runs until this game time (0 = the card is in). */
	public long victorUntil;
	public int animId;
	public long animStart;
	/** Undead: the body re-forms from the head until this game time (invulnerable, no moves). */
	public long reformUntil;
	/** Crimson Bullet slowed the regeneration until this game time. */
	public long slowRegenUntil;
	public int flags;
	/** When the held move (X / C) started. */
	public long holdStart;
	public long lastHitAt;

	public AndyState() {
		this(false, full(), times(), 0, 1, new HashMap<>(), 0L, 0, 0L, 0L, 0L, 0, 0L, 0L);
	}

	public AndyState(boolean hasPower, List<Float> partHp, List<Long> partTimes, int kinds, int regenSpeed, Map<String, Long> abilityReadyAt,
			long victorUntil, int animId, long animStart, long reformUntil, long slowRegenUntil, int flags, long holdStart, long lastHitAt) {
		this.hasPower = hasPower;
		this.partHp = new ArrayList<>(partHp);
		while (this.partHp.size() < PARTS) {
			this.partHp.add(com.projecthero.mod.andy.AndyConfig.PART_HP[this.partHp.size()]);
		}
		this.partTimes = new ArrayList<>(partTimes);
		while (this.partTimes.size() < PARTS * 3) {
			this.partTimes.add(0L);
		}
		this.kinds = kinds;
		this.regenSpeed = Math.floorMod(regenSpeed, 3);
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
		this.victorUntil = victorUntil;
		this.animId = animId;
		this.animStart = animStart;
		this.reformUntil = reformUntil;
		this.slowRegenUntil = slowRegenUntil;
		this.flags = flags;
		this.holdStart = holdStart;
		this.lastHitAt = lastHitAt;
	}

	public static List<Float> full() {
		List<Float> l = new ArrayList<>();
		for (float hp : com.projecthero.mod.andy.AndyConfig.PART_HP) {
			l.add(hp);
		}
		return l;
	}

	private static List<Long> times() {
		List<Long> l = new ArrayList<>();
		for (int i = 0; i < PARTS * 3; i++) {
			l.add(0L);
		}
		return l;
	}

	public AndyState copy() {
		return new AndyState(hasPower, partHp, partTimes, kinds, regenSpeed, abilityReadyAt, victorUntil, animId, animStart, reformUntil,
				slowRegenUntil, flags, holdStart, lastHitAt);
	}

	// ---------------------------------------------------------------- the parts

	public float hp(int part) {
		return partHp.get(part);
	}

	public long brokenAt(int part) {
		return partTimes.get(part * 3);
	}

	public long regrowStart(int part) {
		return partTimes.get(part * 3 + 1);
	}

	public long regrowEnd(int part) {
		return partTimes.get(part * 3 + 2);
	}

	public void setTimes(int part, long brokenAt, long regrowStart, long regrowEnd) {
		partTimes.set(part * 3, brokenAt);
		partTimes.set(part * 3 + 1, regrowStart);
		partTimes.set(part * 3 + 2, regrowEnd);
	}

	/** The part is gone (or still re-forming). */
	public boolean broken(int part) {
		return brokenAt(part) > 0L;
	}

	public int kind(int part) {
		return (kinds >>> (part * 3)) & 7;
	}

	public void setKind(int part, int kind) {
		kinds = (kinds & ~(7 << (part * 3))) | ((kind & 7) << (part * 3));
	}

	/** 0 while the part is gone, rising to 1 as it re-forms; 1 when whole. */
	public float regrowth(int part, float now) {
		if (!broken(part)) {
			return 1f;
		}
		long start = regrowStart(part);
		long end = regrowEnd(part);
		if (start <= 0L || now < start) {
			return 0f;
		}
		if (end <= start) {
			return 1f;
		}
		return Math.min(1f, (now - start) / (float) (end - start));
	}

	public boolean anyBroken() {
		for (int i = 0; i < PARTS; i++) {
			if (broken(i)) {
				return true;
			}
		}
		return false;
	}

	public boolean flag(int f) {
		return (flags & f) != 0;
	}

	public void setFlag(int f, boolean on) {
		flags = on ? flags | f : flags & ~f;
	}

	public static final Codec<AndyState> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("has_power", false).forGetter(s -> s.hasPower),
			Codec.FLOAT.listOf().optionalFieldOf("part_hp", List.of()).forGetter(s -> s.partHp),
			Codec.LONG.listOf().optionalFieldOf("part_times", List.of()).forGetter(s -> s.partTimes),
			Codec.INT.optionalFieldOf("kinds", 0).forGetter(s -> s.kinds),
			Codec.INT.optionalFieldOf("regen_speed", 1).forGetter(s -> s.regenSpeed),
			Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ability_ready_at", Map.of())
					.forGetter(s -> new HashMap<>(s.abilityReadyAt)),
			Codec.LONG.optionalFieldOf("victor_until", 0L).forGetter(s -> s.victorUntil),
			Codec.INT.optionalFieldOf("anim_id", 0).forGetter(s -> s.animId),
			Codec.LONG.optionalFieldOf("anim_start", 0L).forGetter(s -> s.animStart),
			Codec.LONG.optionalFieldOf("reform_until", 0L).forGetter(s -> s.reformUntil),
			Codec.LONG.optionalFieldOf("slow_regen_until", 0L).forGetter(s -> s.slowRegenUntil),
			Codec.INT.optionalFieldOf("flags", 0).forGetter(s -> s.flags),
			Codec.LONG.optionalFieldOf("hold_start", 0L).forGetter(s -> s.holdStart),
			Codec.LONG.optionalFieldOf("last_hit_at", 0L).forGetter(s -> s.lastHitAt)
	).apply(i, AndyState::new));
}
