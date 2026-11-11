package com.projecthero.mod.undead.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.projecthero.mod.undead.UndeadConfig;

/**
 * v0.17.3: the whole Undead (Undead Unluck) power for one player in one attachment ({@code projecthero:undead_state}):
 * persistent, {@code copyOnDeath} and synced to every client (every viewer sees a part break and regrow from it). Only
 * {@code Undead} and its helpers change it, always through a {@link #copy()}.
 *
 * <h2>The six parts and ten segments</h2>
 * Index {@link #HEAD}..{@link #LEFT_LEG} are the six parts with HP ({@link #partHp}). v0.17.3 (NBC Heroes' Regenerative
 * Segmentation): the regrowth runs on ten SEGMENTS -- the six parts (an arm or leg is the limb down to the wrist / ankle)
 * plus {@link #RIGHT_HAND}, {@link #LEFT_HAND}, {@link #RIGHT_FOOT}, {@link #LEFT_FOOT}. Each segment has three times in
 * {@link #partTimes}: when it broke (0 = whole), when its regrowth started (0 = not growing) and when it is whole again: it
 * grows evenly from the start to the end through its {@link UndeadConfig#FRAMES} (one frame a tick at the normal speed;
 * {@code UndeadRegenMods.durationMult} stretches {@code end - start}). Clients draw the segment hidden from the break until it
 * starts, then re-forming (bone, then flesh, then skin). How it went ({@link #kind}) decides the look:
 * {@link #KIND_BURST} bursts into the skin's pixels, {@link #KIND_LAUNCHED} left as a projectile, {@link #KIND_SHATTER}
 * went in the Undead shatter, {@link #KIND_SPENT} was used up by finger shots and {@link #KIND_BURNT} burnt away in a boost.
 *
 * <p>The stage of the head-only regrowth (rolling head, arm drag, ...) is not stored: both sides work it out from the part
 * times ({@code UndeadStages}).
 */
public final class UndeadState {
	public static final int HEAD = 0;
	public static final int BODY = 1;
	public static final int RIGHT_ARM = 2;
	public static final int LEFT_ARM = 3;
	public static final int RIGHT_LEG = 4;
	public static final int LEFT_LEG = 5;
	public static final int PARTS = 6;
	/** v0.17.3: the hands and feet, segments of their own (no HP: they go with their limb, or on their own in flight and shots). */
	public static final int RIGHT_HAND = 6;
	public static final int LEFT_HAND = 7;
	public static final int RIGHT_FOOT = 8;
	public static final int LEFT_FOOT = 9;
	public static final int SEGS = 10;

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
	/** X: the Head Launch wind-up while X is held, the Leg Launch and the Head Launch themselves (UndeadLaunch). */
	public static final int ANIM_HEAD_CHARGE = 40;
	public static final int ANIM_LEG_LAUNCH = 41;
	public static final int ANIM_HEAD_LAUNCH = 42;
	/** v0.17.3 Shift+V: the arm torn off as the Bone Sword, and the sword bursting as it is put away. */
	public static final int ANIM_SWORD_RIP = 50;
	public static final int ANIM_SWORD_AWAY = 51;
	/** The Bone Sword kit (UndeadBoneSword): the three Bone Slash cuts (52-54), then one id a move. */
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
	/** v0.17.3 tap V: Reform -- the body snapped back together at once (a flash of blood and flesh closing on each part). */
	public static final int ANIM_REFORM = 64;
	/**
	 * v0.17.3 ejection moves (UndeadLaunch / UndeadBloodMoves; animStart: when it began): Shift+G Blood Whirlwind (the rise and
	 * the spin), tap X Eject Feet (the launch), Shift+X Combustion Punch (flying at the target, then the punch itself).
	 */
	public static final int ANIM_BLOOD_WHIRL = 80;
	public static final int ANIM_EJECT_FEET = 81;
	public static final int ANIM_COMBUSTION = 82;
	public static final int ANIM_COMBUSTION_PUNCH = 83;


	public static final int FLAG_PROPELLING = 1;
	public static final int FLAG_DEAD_LINE = 2;
	public static final int FLAG_DEAD_ROAD = 4;
	/** Right click held while flying: 40 b/s and the body burns away. */
	public static final int FLAG_BOOST = 16;
	/** R held: the arm drawn back for the stretch punch. */
	public static final int FLAG_PUNCH = 32;
	/** The last one-arm move (finger shot, punch) used the off arm (the main one was gone or used up). */
	public static final int FLAG_OFF_ARM = 64;
	// 128 was Toggle Regen (auto-regrowth off) and 512 the Regen Menu, both removed in v0.17.3 (user: "remove the regen menu from
	// this power please"): regrowth is always automatic. LEGACY_FLAGS clears them from an old save; don't reuse those bits.
	private static final int LEGACY_FLAGS = 128 | 512;
	/** v0.17.3 (tester: "Alternation between left and right hands"): the next one-arm move comes from the off arm (the last used the main one). */
	public static final int FLAG_NEXT_OFF = 256;
	/** v0.17.3 G held: Blood Spray -- both forearm stumps spraying a 2-block stream along the aim (UndeadBloodMoves). */
	public static final int FLAG_BLOOD_SPRAY = 8192;

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
	/** v0.17.3 tap V: the parts the last Reform closed (bit per part, for its flash). */
	public static final int X_REFORMED = 7;
	/**
	 * v0.17.3 ejection (UndeadEject): a bit per segment that came off with more than itself -- a hand / foot bit: it took half
	 * its forearm / calf with it (the limb is drawn cut at {@code UndeadEjectConfig.EJECT_ROWS} and grows back from there with
	 * the hand / foot); the {@link #BODY} bit: the torso is cut to its top third (Combustion Punch). A bit clears once its
	 * segment is whole again. Slot 13 (9-12 left free for the other v0.17.3 work).
	 */
	public static final int X_EJECTED = 13;
	public static final int EXTRA = 16;

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

	public UndeadState() {
		this(false, full(), times(), 0, 1, new HashMap<>(), 0L, 0, 0L, 0L, 0L, 0, 0L, 0L, List.of());
	}

	public UndeadState(boolean hasPower, List<Float> partHp, List<Long> partTimes, int kinds, int regenSpeed, Map<String, Long> abilityReadyAt,
			long victorUntil, int animId, long animStart, long reformUntil, long slowRegenUntil, int flags, long holdStart, long lastHitAt, List<Long> extra) {
		this.hasPower = hasPower;
		this.partHp = new ArrayList<>(partHp);
		while (this.partHp.size() < PARTS) {
			this.partHp.add(UndeadConfig.PART_HP[this.partHp.size()]);
		}
		this.partTimes = new ArrayList<>(partTimes);
		while (this.partTimes.size() < SEGS * 3) {
			this.partTimes.add(0L);
		}
		this.kinds = kinds;
		this.regenSpeed = regenSpeed; // v0.17.3: unused (one speed now); kept so old saves load
		this.abilityReadyAt = new HashMap<>(abilityReadyAt);
		this.victorUntil = victorUntil;
		this.animId = animId;
		this.animStart = animStart;
		this.reformUntil = reformUntil;
		this.slowRegenUntil = slowRegenUntil;
		this.flags = flags & ~LEGACY_FLAGS;
		this.holdStart = holdStart;
		this.lastHitAt = lastHitAt;
		this.extra = new ArrayList<>(extra);
		while (this.extra.size() < EXTRA) {
			this.extra.add(0L);
		}
	}

	public static List<Float> full() {
		List<Float> l = new ArrayList<>();
		for (float hp : UndeadConfig.PART_HP) {
			l.add(hp);
		}
		return l;
	}

	private static List<Long> times() {
		List<Long> l = new ArrayList<>();
		for (int i = 0; i < SEGS * 3; i++) {
			l.add(0L);
		}
		return l;
	}

	public UndeadState copy() {
		return new UndeadState(hasPower, partHp, partTimes, kinds, regenSpeed, abilityReadyAt, victorUntil, animId, animStart, reformUntil,
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

	/**
	 * The frame a segment is at (0 = gone .. {@link UndeadConfig#FRAMES} = whole): evenly from its start to its end, one a tick at
	 * the normal speed. Fractional between ticks (the renderers pass the partial tick).
	 */
	public float frame(int seg, float now) {
		if (!broken(seg)) {
			return UndeadConfig.FRAMES[seg];
		}
		long start = regrowStart(seg);
		if (start <= 0L || now < start) {
			return 0f;
		}
		long len = Math.max(1L, regrowEnd(seg) - start);
		return Math.min(1f, (now - start) / len) * UndeadConfig.FRAMES[seg];
	}

	/** 0 while the segment is gone, rising to 1 as it re-forms (frame / its full frames); 1 when whole. */
	public float regrowth(int part, float now) {
		return broken(part) ? Math.min(1f, frame(part, now) / UndeadConfig.FRAMES[part]) : 1f;
	}

	/** Bone, flesh or skin: the stage a segment is in at {@code now} ({@link #STAGE_GONE} at frame 0). */
	public int stage(int seg, float now) {
		float f = frame(seg, now);
		if (f >= UndeadConfig.FRAMES[seg]) {
			return STAGE_SKIN;
		}
		return f <= 0f ? STAGE_GONE : f < UndeadConfig.FLESH_FRAME[seg] ? STAGE_BONE : STAGE_FLESH;
	}

	public static final int STAGE_GONE = 0, STAGE_BONE = 1, STAGE_FLESH = 2, STAGE_SKIN = 3;

	/** The hand or foot at the end of an arm or leg (-1 for the head and body). */
	public static int extremity(int part) {
		return switch (part) {
			case RIGHT_ARM -> RIGHT_HAND;
			case LEFT_ARM -> LEFT_HAND;
			case RIGHT_LEG -> RIGHT_FOOT;
			case LEFT_LEG -> LEFT_FOOT;
			default -> -1;
		};
	}

	/**
	 * v0.17.3 (user: "the arm regenerates and then only after that the hand regenerates ... please fix that"): the arm / leg
	 * {@code limb} and its hand / foot are growing back as ONE sweep -- started together, ending together
	 * ({@code Undead#tick}) -- so the whole limb is drawn as one growth from the shoulder / hip out to the fingertips / toes, the
	 * hand simply the last stretch of the same front. False for the head and body, and for a hand / foot growing alone.
	 */
	public boolean sweeping(int limb) {
		int end = extremity(limb);
		return end >= 0 && broken(limb) && broken(end) && regrowStart(limb) > 0L && regrowStart(end) == regrowStart(limb)
				&& regrowEnd(end) == regrowEnd(limb);
	}

	/** The arm or leg a hand or foot grows from (the segment itself for the six parts). */
	public static int limbOf(int seg) {
		return switch (seg) {
			case RIGHT_HAND -> RIGHT_ARM;
			case LEFT_HAND -> LEFT_ARM;
			case RIGHT_FOOT -> RIGHT_LEG;
			case LEFT_FOOT -> LEFT_LEG;
			default -> seg;
		};
	}

	/** The segment it grows from in NBC's order (-1 for the head). */
	public static int parentOf(int seg) {
		return switch (seg) {
			case HEAD -> -1;
			case BODY -> HEAD;
			case RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG -> BODY;
			default -> limbOf(seg);
		};
	}

	/** The part is there and whole (not broken, or regrown right to the end). */
	public boolean has(int part, float now) {
		return !broken(part) || regrowth(part, now) >= 1f;
	}

	public boolean anyBroken() {
		for (int i = 0; i < SEGS; i++) {
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

	/** 0..1 through the regrowth of the used-up bit of an arm (0 while still waiting for {@link UndeadConfig#EATEN_REGROW_WAIT}). */
	public float eatenRegrow(int arm, float now) {
		float c0 = eaten0(arm);
		if (c0 <= 0f) {
			return 0f;
		}
		float start = ex(X_LAST_SHOT) + UndeadConfig.EATEN_REGROW_WAIT;
		if (now < start) {
			return 0f;
		}
		return Math.min(1f, (now - start) / UndeadConfig.cutRegrowTicks(c0));
	}

	/**
	 * How much of an arm the finger shots have used up (0..1) at {@code now}: what was gone at the last shot, then -- once
	 * {@link UndeadConfig#EATEN_REGROW_WAIT} has passed without a shot -- less and less as the skin closes back down to the
	 * fingertips (it follows the skin front of the bone / muscle / skin regrowth).
	 */
	public float eaten(int arm, float now) {
		float c0 = eaten0(arm);
		if (c0 <= 0f) {
			return 0f;
		}
		float q = eatenRegrow(arm, now);
		return q <= 0f ? c0 : c0 * (1f - UndeadConfig.skinShare(q));
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

	public static final Codec<UndeadState> CODEC = RecordCodecBuilder.create(i -> i.group(
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
	).apply(i, UndeadState::new));
}
