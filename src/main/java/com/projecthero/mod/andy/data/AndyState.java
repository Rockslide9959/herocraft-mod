package com.projecthero.mod.andy.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.projecthero.mod.andy.AndyConfig;

/**
 * v0.17.3: the whole Andy (Undead Unluck) power for one player in one attachment ({@code projecthero:andy_state}):
 * persistent, {@code copyOnDeath} and synced to every client (every viewer sees a part break and regrow from it). Only
 * {@code Andy} and its helpers change it, always through a {@link #copy()}.
 *
 * <h2>The six parts</h2>
 * Index {@link #HEAD}..{@link #LEFT_LEG}. Each has HP ({@link #partHp}) and three times in {@link #partTimes}: when it broke
 * (0 = whole), when its regrowth started (0 = still waiting; it may be set in the future, as the head-only regrowth plans
 * every part at once) and when the regrowth ends. Clients draw the part hidden from the break until the regrowth starts,
 * then re-forming (bone, then muscle, then skin) until it ends. How it went ({@link #kind}) decides the look:
 * {@link #KIND_BURST} bursts into the skin's pixels, {@link #KIND_LAUNCHED} left as a projectile, {@link #KIND_SHATTER}
 * went in the Undead shatter, {@link #KIND_SPENT} was used up by finger shots and {@link #KIND_BURNT} burnt away in a boost.
 *
 * <p>The stage of the head-only regrowth (rolling head, arm drag, ...) is not stored: both sides work it out from the part
 * times ({@code AndyStages}).
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
	/** Used up by Parts / Crimson Bullets: the last pixels went with the shot (no burst). */
	public static final int KIND_SPENT = 4;
	/** Burnt away while boosting in flight (it went pixel by pixel as embers: no burst). */
	public static final int KIND_BURNT = 5;

	public static final int ANIM_NONE = 0;
	public static final int ANIM_FINGER = 1;
	public static final int ANIM_CRIMSON = 2;
	public static final int ANIM_CRESCENT = 3;
	public static final int ANIM_GREAT_CRESCENT = 4;
	public static final int ANIM_VORTEX = 6;
	public static final int ANIM_HEAD = 7;
	public static final int ANIM_CARD = 8;
	public static final int ANIM_QUARDEAD = 9;
	public static final int ANIM_VULCAN = 10;
	public static final int ANIM_DEAD_BLADE = 11;
	public static final int ANIM_DIVISION = 12;
	public static final int ANIM_HEADBUTT = 13;
	public static final int ANIM_CARD_BACK = 14;
	public static final int ANIM_PUNCH = 15;
	/** X: the Head Launch wind-up while X is held, the Leg Launch and the Head Launch themselves (AndyLaunch). */
	public static final int ANIM_HEAD_CHARGE = 40;
	public static final int ANIM_LEG_LAUNCH = 41;
	public static final int ANIM_HEAD_LAUNCH = 42;
	/** v0.17.3 Shift+V: the arm torn off as the Bone Sword, and the sword bursting as it is put away. */
	public static final int ANIM_SWORD_RIP = 50;
	public static final int ANIM_SWORD_AWAY = 51;
	/** The Bone Sword kit (AndyBoneSword): the three Bone Slash cuts (52-54), then one id a move. */
	public static final int ANIM_SWORD_SLASH1 = 52;
	public static final int ANIM_SWORD_SLASH2 = 53;
	public static final int ANIM_SWORD_SLASH3 = 54;
	public static final int ANIM_SWORD_THRUST = 55;
	public static final int ANIM_SWORD_EDGE = 56;
	public static final int ANIM_SWORD_GUARD = 57;
	public static final int ANIM_SWORD_SPLINTER = 58;
	public static final int ANIM_SWORD_IMPALE = 59;
	public static final int ANIM_SWORD_DASH = 60;
	public static final int ANIM_SWORD_PARRY = 61;
	public static final int ANIM_SWORD_COUNTER = 62;
	/** v0.17.3 hold V: reaching over and struggling with the off arm before it tears off (animStart: when the struggle began). */
	public static final int ANIM_SWORD_STRUGGLE = 63;
	/** v0.17.3 tap V: Tear Off -- a limb wrenched off and flung aside. */
	public static final int ANIM_TEAR_OFF = 64;


	public static final int FLAG_PROPELLING = 1;
	public static final int FLAG_DEAD_LINE = 2;
	public static final int FLAG_DEAD_ROAD = 4;
	/** Right click held while flying: 40 b/s and the body burns away. */
	public static final int FLAG_BOOST = 16;
	/** R held: the arm drawn back for the stretch punch. */
	public static final int FLAG_PUNCH = 32;
	/** The last one-arm move (finger shot, punch) used the off arm (the main one was gone or used up). */
	public static final int FLAG_OFF_ARM = 64;
	/** v0.17.3 (tester: "Add the option to choose not to regenerate"): Shift+V's fourth setting, Off -- nothing grows back or heals. */
	public static final int FLAG_REGEN_OFF = 128;
	/** v0.17.3 (tester: "Alternation between left and right hands"): the next one-arm move comes from the off arm (the last used the main one). */
	public static final int FLAG_NEXT_OFF = 256;

	// ---------------------------------------------------------------- the extra slots ({@link #extra})
	/** When the hands / feet (and anything half burnt) started growing back after a flight (0: not). */
	public static final int X_CUT_REGROW = 0;
	/** The burn: which stage it is in (0 legs, 1 arms, 2 body) and how many ticks into it. */
	public static final int X_BURN_SEG = 1;
	public static final int X_BURN_TICKS = 2;
	/** Parts / Crimson Bullets: how much of each arm is used up (x1000) as of the last shot, and when that was. */
	public static final int X_EATEN_R = 3;
	public static final int X_EATEN_L = 4;
	public static final int X_LAST_SHOT = 5;
	/** When the stretch punch started winding up. */
	public static final int X_PUNCH_START = 6;
	/**
	 * v0.17.3 Bone Sword (Shift+V): 0 when it is not out, else (when it was drawn << 3) | the torn-off arm. Slot 8 (7 is left
	 * free); see {@link #boneSwordArm} / {@link #boneSwordSince}.
	 */
	public static final int X_BONE_SWORD = 8;
	/** v0.17.3 tap V: the limb the last Tear Off took (for its animation). */
	public static final int X_TORN = 7;
	public static final int EXTRA = 10;

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
	/** Undead: just shattered, untouchable and no moves until this game time. */
	public long reformUntil;
	/** Crimson Bullet slowed the regeneration until this game time. */
	public long slowRegenUntil;
	public int flags;
	/** When the held move (X / C) started. */
	public long holdStart;
	public long lastHitAt;
	/** Small extra numbers by index ({@link #X_CUT_REGROW} ...), one list for the codec's 16-field limit. */
	public List<Long> extra;

	public AndyState() {
		this(false, full(), times(), 0, 1, new HashMap<>(), 0L, 0, 0L, 0L, 0L, 0, 0L, 0L, List.of());
	}

	public AndyState(boolean hasPower, List<Float> partHp, List<Long> partTimes, int kinds, int regenSpeed, Map<String, Long> abilityReadyAt,
			long victorUntil, int animId, long animStart, long reformUntil, long slowRegenUntil, int flags, long holdStart, long lastHitAt, List<Long> extra) {
		this.hasPower = hasPower;
		this.partHp = new ArrayList<>(partHp);
		while (this.partHp.size() < PARTS) {
			this.partHp.add(AndyConfig.PART_HP[this.partHp.size()]);
		}
		this.partTimes = new ArrayList<>(partTimes);
		while (this.partTimes.size() < PARTS * 3) {
			this.partTimes.add(0L);
		}
		this.kinds = kinds;
		this.regenSpeed = Math.floorMod(regenSpeed, AndyConfig.SPEEDS);
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
		this.victorUntil = victorUntil;
		this.animId = animId;
		this.animStart = animStart;
		this.reformUntil = reformUntil;
		this.slowRegenUntil = slowRegenUntil;
		this.flags = flags;
		this.holdStart = holdStart;
		this.lastHitAt = lastHitAt;
		this.extra = new ArrayList<>(extra);
		while (this.extra.size() < EXTRA) {
			this.extra.add(0L);
		}
	}

	public static List<Float> full() {
		List<Float> l = new ArrayList<>();
		for (float hp : AndyConfig.PART_HP) {
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
				slowRegenUntil, flags, holdStart, lastHitAt, extra);
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

	/** The part is there and whole (not broken, or regrown right to the end). */
	public boolean has(int part, float now) {
		return !broken(part) || regrowth(part, now) >= 1f;
	}

	public boolean anyBroken() {
		for (int i = 0; i < PARTS; i++) {
			if (broken(i)) {
				return true;
			}
		}
		return false;
	}

	/** Only the head is left (everything else gone or still growing back). */
	public boolean headOnly(float now) {
		if (!has(HEAD, now)) {
			return false;
		}
		for (int i = BODY; i < PARTS; i++) {
			if (has(i, now)) {
				return false;
			}
		}
		return true;
	}

	public long ex(int i) {
		return extra.get(i);
	}

	public void setEx(int i, long v) {
		extra.set(i, v);
	}

	// ---------------------------------------------------------------- the Bone Sword (Shift+V)

	/** The Bone Sword is out. */
	public boolean boneSword() {
		return ex(X_BONE_SWORD) != 0L;
	}

	/** The arm torn off to make the Bone Sword (-1 when it is not out); the sword is held in the other hand. */
	public int boneSwordArm() {
		long v = ex(X_BONE_SWORD);
		return v == 0L ? -1 : (int) (v & 7L);
	}

	/** When the Bone Sword was drawn (0 when it is not out). */
	public long boneSwordSince() {
		return ex(X_BONE_SWORD) >> 3;
	}

	public void setBoneSword(int arm, long since) {
		setEx(X_BONE_SWORD, arm < 0 ? 0L : (Math.max(1L, since) << 3) | arm);
	}

	// ---------------------------------------------------------------- the finger shots' bite out of an arm

	/** What was gone of the arm at the last shot (0..1). */
	public float eaten0(int arm) {
		return ex(arm == RIGHT_ARM ? X_EATEN_R : X_EATEN_L) / 1000f;
	}

	/** 0..1 through the regrowth of the used-up bit of an arm (0 while still waiting for {@link AndyConfig#EATEN_REGROW_WAIT}). */
	public float eatenRegrow(int arm, float now) {
		float c0 = eaten0(arm);
		if (c0 <= 0f) {
			return 0f;
		}
		float start = ex(X_LAST_SHOT) + AndyConfig.EATEN_REGROW_WAIT;
		if (now < start) {
			return 0f;
		}
		return Math.min(1f, (now - start) / AndyConfig.cutRegrowTicks(regenSpeed, c0));
	}

	/**
	 * How much of an arm the finger shots have used up (0..1) at {@code now}: what was gone at the last shot, then -- once
	 * {@link AndyConfig#EATEN_REGROW_WAIT} has passed without a shot -- less and less as the skin closes back down to the
	 * fingertips (it follows the skin front of the bone / muscle / skin regrowth).
	 */
	public float eaten(int arm, float now) {
		float c0 = eaten0(arm);
		if (c0 <= 0f) {
			return 0f;
		}
		float q = eatenRegrow(arm, now);
		return q <= 0f ? c0 : c0 * (1f - AndyConfig.skinShare(q));
	}

	/** The arm can shoot or punch: there, whole, and not used up. */
	public boolean armReady(int arm, float now) {
		return has(arm, now) && !broken(arm) && eaten(arm, now) < 0.999f;
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
			Codec.LONG.optionalFieldOf("last_hit_at", 0L).forGetter(s -> s.lastHitAt),
			// v0.17.3: an old save's "regen_style" (three looks, now one) is simply skipped -- a record codec ignores keys it doesn't know
			Codec.LONG.listOf().optionalFieldOf("extra", List.of()).forGetter(s -> s.extra)
	).apply(i, AndyState::new));
}
