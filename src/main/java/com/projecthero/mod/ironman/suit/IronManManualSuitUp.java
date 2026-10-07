package com.projecthero.mod.ironman.suit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManFlight;
import com.projecthero.mod.ironman.IronManSounds;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.11: the <b>hand-built suit-up</b> -- what plain C does when a suit is sitting in your pack. Instead of the pieces
 * building themselves on, Tony puts the armour on by hand, piece by piece, with a hammer (and, in the workshop version, a
 * wrench). The timeline is pure data and math (no client classes, no world state), so the server sequence (the bottom
 * half of this class, driven from {@code IronManSuitUpManager#tick}), the synced clock ({@link IronManSuitFx#STYLE_MK1_BUILD}
 * / {@link IronManSuitFx#STYLE_MANUAL}), the client pose / prop renderers and the gametests all read the same numbers.
 *
 * <p>While it runs the player is held still (no walking / jumping, the Suit Platform's transient movement lock), flight
 * and abilities are locked ({@code IronManSuitUpManager#assembling}) and -- like every suit-up since v0.15.3 -- nothing
 * hurts them, so damage never interrupts it. Death stops it where it is; logout / server stop / a dimension change
 * finish it at once (the rest of the pieces straight onto the body). The real stacks only ever move pack -> slot, one at a
 * time, so nothing is duplicated or lost on any of those paths.
 *
 * <h2>{@link #KIND_MK1} -- the Mark 1, built in the cave ({@value #MK1_TICKS} ticks = 30 s)</h2>
 * Sit down on the floor; pick up the boots, fit them round the feet, hammer them shut (five strikes), pull the straps
 * tight; the same for the greaves (four strikes at the knees, straps at the hips); stand up; lift the chestplate over the
 * head and pull it down, hammer the chest rivets, hold the left arm out and hammer the bracer, roll the shoulders; lift
 * the helmet on, press it down, two strikes at the side, and pull the faceplate down with the left hand. The hammer is in
 * the right hand the whole time.
 *
 * <h2>{@link #KIND_WORKSHOP} -- Marks 2 to 7 in the workshop ({@value #WORKSHOP_TICKS} ticks = 25 s)</h2>
 * Standing the whole time (no sitting on the floor): step into the boots and clamp them, ratchet the ankle bolts; slap the
 * greaves shut, two hammer taps at the knees, ratchet the hip bolts; pull the chestplate over the head, clamp both sides,
 * press the arc reactor home, two rivets, ratchet the left gauntlet, clamp the right one, flex; lift the helmet on, clamp
 * the jaw, ratchet the back of the neck -- and last of all pull the faceplate down.
 *
 * <p>Each piece leaves the pack and lands in its armour slot at its <em>fit</em> tick, then builds on with the ordinary
 * build-on look (base halves, then the outer shell), but timed to the work: the halves close while the plate is being
 * fitted, and the shell fills in a burst after each hammer strike / clamp / ratchet ({@link #progress}). A piece that is
 * not in the plan (not carried) skips its whole segment, so a partial set is simply shorter.
 */
public final class IronManManualSuitUp {
	public static final int KIND_MK1 = 0;
	public static final int KIND_WORKSHOP = 1;
	/** The whole Mark 1 hand build, all four pieces: 30 s. */
	public static final int MK1_TICKS = 600;
	/** The whole Marks 2-7 workshop build, all four pieces: 25 s. */
	public static final int WORKSHOP_TICKS = 500;

	/**
	 * Key layout: right arm x/y/z, left arm x/y/z, right leg x/y/z, left leg x/y/z, head x/y, torso lean, sit (0..1), and
	 * the "wrist": how far the tool in the right hand is turned from pointing straight out of the front of the fist toward
	 * the forearm (radians). Minecraft arms have no elbow, so it is the wrist that puts the hammer head on the target.
	 */
	public static final int RAX = 0, RAY = 1, RAZ = 2, LAX = 3, LAY = 4, LAZ = 5, RLX = 6, RLY = 7, RLZ = 8, LLX = 9,
			LLY = 10, LLZ = 11, HX = 12, HY = 13, LEAN = 14, SIT = 15, TILT = 16, SIZE = 17;
	/** The tool at rest in the fist: angled forward and down. */
	public static final float REST_TILT = 1.2f;
	/** How far a full sit lowers the body (model pixels, x the wearer's scale): hips 2 px off the floor. */
	public static final float SIT_DROP_PX = 10f;

	/** Props (bit flags): the hammer / the wrench in the right hand, the piece being fitted in the left hand. */
	public static final int PROP_HAMMER = 1, PROP_WRENCH = 2, PROP_PIECE = 4;

	public static final int EV_PICKUP = 1;
	public static final int EV_STRIKE = 2;
	public static final int EV_CLAMP = 3;
	public static final int EV_RATCHET = 4;
	public static final int EV_STRAP = 5;
	public static final int EV_SERVO = 6;
	public static final int EV_FACEPLATE = 7;
	public static final int EV_SIT = 8;
	public static final int EV_SETTLE = 9;
	public static final int EV_TOOL = 10;

	/**
	 * Something that happens at one tick of the sequence: a sound / spark burst (and, for {@link #EV_FACEPLATE}, the
	 * faceplate closing). {@code right / up / fwd} = where on the body (blocks from the feet, body-relative, at scale 1).
	 */
	public record Event(int tick, int type, int bit, float right, float up, float fwd) {
		Event shifted(int by) {
			return new Event(tick + by, type, bit, right, up, fwd);
		}
	}

	/** One pose keyframe: the key and the props held from this tick on. */
	public record Key(int tick, float[] v, int props) {
	}

	/** One stretch of the sequence: a piece (bit 0 HEAD .. 3 FEET) or a link (-1: sitting down / standing up). */
	static final class Segment {
		final int bit;
		final int length;
		final List<Key> keys = new ArrayList<>();
		final List<Event> events = new ArrayList<>();
		/** The tick the piece lands in its slot (segment-local), and when its base halves have closed. */
		int fit = -1;
		int fitEnd = -1;
		/** How long the piece's build-on runs from {@link #fit}. */
		int window;

		Segment(int bit, int length) {
			this.bit = bit;
			this.length = length;
		}

		Segment key(int t, float[] v, int props) {
			keys.add(new Key(t, v, props));
			return this;
		}

		Segment event(int t, int type, float right, float up, float fwd) {
			events.add(new Event(t, type, bit, right, up, fwd));
			return this;
		}

		Segment fit(int at, int end, int doneAt) {
			fit = at;
			fitEnd = end;
			window = doneAt - at;
			return this;
		}

		/**
		 * A run of hammer strikes: the arm comes up over {@code period - 3} ticks and down onto the target in 3, a little
		 * rebound after. {@code raised} / {@code hit} are the right-arm keys, everything else comes from {@code base}.
		 */
		Segment strikes(int firstHit, int count, int period, float[] base, float[] raised, float[][] hits, float[][] at,
				int props) {
			for (int i = 0; i < count; i++) {
				int hit = firstHit + i * period;
				float[] h = hits[i % hits.length];
				float[] loc = at[i % at.length];
				key(hit - period + 4, withRight(base, raised), props);
				key(hit - 2, withRight(base, lerpArm(raised, h, 0.35f)), props);
				key(hit, withRight(base, h), props);
				key(hit + 2, withRight(base, lerpArm(h, raised, 0.12f)), props);
				event(hit, EV_STRIKE, loc[0], loc[1], loc[2]);
			}
			return this;
		}

		/** A ratchet: the right forearm swings back and forth (arm yaw), a click on every return stroke. */
		Segment ratchet(int start, int strokes, int period, float[] base, float yawA, float yawB, float[] loc, int props) {
			for (int i = 0; i < strokes; i++) {
				int t = start + i * period;
				key(t, with(base, RAY, base[RAY] + yawA), props);
				key(t + period / 2, with(base, RAY, base[RAY] + yawB), props);
				event(t + period / 2, EV_RATCHET, loc[0], loc[1], loc[2]);
			}
			key(start + strokes * period, base, props);
			return this;
		}
	}

	/** The whole planned sequence for one kind and one set of pieces, in global ticks (0 = the C press). */
	public static final class Schedule {
		final int kind;
		final int plan;
		final int total;
		final int[] pieceAt = { -1, -1, -1, -1 };
		final int[] pieceDone = { -1, -1, -1, -1 };
		final List<Key> keys = new ArrayList<>();
		final List<Event> events = new ArrayList<>();
		int faceplateAt = -1;

		Schedule(int kind, int plan) {
			this.kind = kind;
			this.plan = plan;
			int off = 0;
			for (Segment seg : segments(kind, plan)) {
				for (Key k : seg.keys) {
					int t = k.tick() + off;
					if (!keys.isEmpty() && t <= keys.get(keys.size() - 1).tick()) {
						continue; // the previous segment already ends on this pose at this tick
					}
					keys.add(new Key(t, k.v(), k.props()));
				}
				for (Event e : seg.events) {
					Event g = e.shifted(off);
					events.add(g);
					if (g.type() == EV_FACEPLATE) {
						faceplateAt = g.tick();
					}
				}
				if (seg.bit >= 0) {
					pieceAt[seg.bit] = off + seg.fit;
					pieceDone[seg.bit] = off + seg.fit + seg.window;
				}
				off += seg.length;
			}
			this.total = off;
			events.sort((a, b) -> Integer.compare(a.tick(), b.tick()));
		}

		public int total() {
			return total;
		}

		public int plan() {
			return plan;
		}

		/** The tick piece {@code bit} lands in its slot (-1 if it is not in the plan). */
		public int pieceAt(int bit) {
			return bit >= 0 && bit < 4 ? pieceAt[bit] : -1;
		}

		/** The tick piece {@code bit} is fully built (-1 if it is not in the plan). */
		public int pieceDone(int bit) {
			return bit >= 0 && bit < 4 ? pieceDone[bit] : -1;
		}

		/** The tick the faceplate is pulled down (-1 without a helmet in the plan). */
		public int faceplateAt() {
			return faceplateAt;
		}

		public List<Event> events() {
			return Collections.unmodifiableList(events);
		}

		public List<Key> keys() {
			return Collections.unmodifiableList(keys);
		}

		/** Every event at exactly tick {@code t}. */
		public List<Event> eventsAt(int t) {
			List<Event> out = new ArrayList<>(2);
			for (Event e : events) {
				if (e.tick() == t) {
					out.add(e);
				} else if (e.tick() > t) {
					break;
				}
			}
			return out;
		}

		/** The body pose {@code age} ticks in, as {weight, key...}, or null outside the sequence. */
		public float[] pose(float age) {
			if (age < 0f || age > total || keys.isEmpty()) {
				return null;
			}
			float w = Math.min(smooth(age / 6f), smooth((total - age) / 8f));
			float[] key = keyAt(age);
			float[] out = new float[SIZE + 1];
			out[0] = w;
			System.arraycopy(key, 0, out, 1, SIZE);
			return out;
		}

		/** The interpolated key at {@code age} (each keyframe pair eased in and out). */
		public float[] keyAt(float age) {
			Key prev = keys.get(0);
			if (age <= prev.tick()) {
				return prev.v();
			}
			for (int i = 1; i < keys.size(); i++) {
				Key next = keys.get(i);
				if (age < next.tick()) {
					float u = (age - prev.tick()) / (float) (next.tick() - prev.tick());
					return lerp(prev.v(), next.v(), smooth(u));
				}
				prev = next;
			}
			return prev.v();
		}

		/** The props held {@code age} ticks in ({@link #PROP_HAMMER} ...). */
		public int props(float age) {
			if (age < 0f || age > total) {
				return 0;
			}
			int p = keys.isEmpty() ? 0 : keys.get(0).props();
			for (Key k : keys) {
				if (k.tick() > age) {
					break;
				}
				p = k.props();
			}
			return p;
		}

		/** Which piece is being worked on (or carried) {@code age} ticks in: the segment's bit, -1 between pieces. */
		public int pieceInHand(float age) {
			int best = -1;
			int bestAt = Integer.MIN_VALUE;
			for (int bit = 0; bit < 4; bit++) {
				// a piece is picked up a little before it lands (its segment starts at most ~40 ticks before the fit)
				if (pieceAt[bit] >= 0 && age <= pieceAt[bit] + 1 && pieceAt[bit] - 45 <= age && pieceAt[bit] > bestAt) {
					best = bit;
					bestAt = pieceAt[bit];
				}
			}
			return best;
		}
	}

	/** Built on first use (immutable once built; a race just builds the same thing twice). Pure data, no world state. */
	private static final Schedule[][] CACHE = new Schedule[2][16];

	private IronManManualSuitUp() {
	}

	/** The sequence for {@code kind} and the planned pieces {@code plan} (bit 0 HEAD .. 3 FEET). */
	public static Schedule schedule(int kind, int plan) {
		int k = kind == KIND_MK1 ? 0 : 1;
		Schedule s = CACHE[k][plan & 15];
		if (s == null) {
			s = new Schedule(k, plan & 15);
			CACHE[k][plan & 15] = s;
		}
		return s;
	}

	/**
	 * Which hand build {@code suitId} uses, or -1 for none: the Mark 1 builds in the cave, Marks 2 to 7 in the workshop. The
	 * Mark 5 only ever deploys from its suitcase, and the Mark 8 (and anything newer) keeps the self-building suit-up.
	 */
	public static int kindFor(String suitId) {
		if (suitId == null) {
			return -1;
		}
		return switch (suitId) {
			case "mark_1" -> KIND_MK1;
			case "mark_2", "mark_iii", "mark_4", "mark_6", "mark_vii" -> KIND_WORKSHOP;
			default -> -1;
		};
	}

	/** The synced style of a hand build. */
	public static int style(int kind) {
		return kind == KIND_MK1 ? IronManSuitFx.STYLE_MK1_BUILD : IronManSuitFx.STYLE_MANUAL;
	}

	/** The synced pose of a hand build. */
	public static int poseKind(int kind) {
		return kind == KIND_MK1 ? IronManSuitFx.POSE_MK1_BUILD : IronManSuitFx.POSE_MANUAL_UP;
	}

	/** The hand-build kind a synced style stands for, or -1. */
	public static int kindOfStyle(int style) {
		return style == IronManSuitFx.STYLE_MK1_BUILD ? KIND_MK1 : style == IronManSuitFx.STYLE_MANUAL ? KIND_WORKSHOP : -1;
	}

	/** The hand-build kind a synced pose stands for, or -1. */
	public static int kindOfPose(int pose) {
		return pose == IronManSuitFx.POSE_MK1_BUILD ? KIND_MK1 : pose == IronManSuitFx.POSE_MANUAL_UP ? KIND_WORKSHOP : -1;
	}

	/** The synced pose variant: which suit (for the piece drawn in the hand) and the plan. */
	public static int variant(String suitId, int plan) {
		int idx = 0;
		int i = 0;
		for (IronManSuit s : IronManSuits.all()) {
			if (s.id().equals(suitId)) {
				idx = i;
				break;
			}
			i++;
		}
		return idx * 16 + (plan & 15);
	}

	public static int planOf(int variant) {
		return variant & 15;
	}

	/** The suit id a pose variant carries (null if unknown). */
	public static String suitOf(int variant) {
		int idx = variant >> 4;
		int i = 0;
		for (IronManSuit s : IronManSuits.all()) {
			if (i++ == idx) {
				return s.id();
			}
		}
		return null;
	}

	/** How long piece {@code bit} builds on in this kind (the same whatever else is planned). */
	public static int window(int kind, int bit) {
		Schedule full = schedule(kind, 15);
		int at = full.pieceAt(bit);
		return at < 0 ? IronManSuitFx.BUILD_TICKS : Math.max(1, full.pieceDone(bit) - at);
	}

	/**
	 * How far piece {@code bit} is built on {@code age} ticks after it landed: the base halves close while it is being
	 * fitted (to {@code 0.3}), then the outer shell fills in a quick burst after each strike / clamp / ratchet, all of it
	 * done ({@code 1}) at the end of its window. Monotonic.
	 */
	public static float progress(int kind, int bit, float age) {
		Segment seg = pieceSegment(kind, bit);
		if (seg == null) {
			return clamp(age / IronManSuitFx.BUILD_TICKS);
		}
		if (age <= 0f) {
			return 0f;
		}
		if (age >= seg.window) {
			return 1f;
		}
		float fitLen = Math.max(1, seg.fitEnd - seg.fit);
		if (age < fitLen) {
			return 0.3f * smooth(age / fitLen);
		}
		List<Event> work = new ArrayList<>();
		for (Event e : seg.events) {
			if (e.tick() > seg.fitEnd && workEvent(e.type())) {
				work.add(e);
			}
		}
		float p = 0.3f;
		float step = work.isEmpty() ? 0f : 0.66f / work.size();
		for (Event e : work) {
			float since = age - (e.tick() - seg.fit);
			if (since <= 0f) {
				break;
			}
			p += step * smooth(since / 3f);
		}
		if (work.isEmpty()) {
			return Math.min(1f, 0.3f + 0.7f * clamp((age - fitLen) / (seg.window - fitLen)));
		}
		// after the last burst the last few edge pixels settle in by the end of the window
		Event last = work.get(work.size() - 1);
		float lastAt = last.tick() - seg.fit + 3f;
		if (age > lastAt) {
			p += 0.04f * clamp((age - lastAt) / Math.max(1f, seg.window - lastAt));
		}
		return Math.min(1f, p);
	}

	private static boolean workEvent(int type) {
		return type == EV_STRIKE || type == EV_CLAMP || type == EV_RATCHET || type == EV_STRAP || type == EV_SERVO
				|| type == EV_SETTLE;
	}

	private static final Segment[][] PIECE_SEGMENTS = new Segment[2][4];

	static Segment pieceSegment(int kind, int bit) {
		if (bit < 0 || bit > 3) {
			return null;
		}
		Segment[] row = PIECE_SEGMENTS[kind == KIND_MK1 ? 0 : 1];
		if (row[bit] == null) {
			for (Segment s : segments(kind, 15)) {
				if (s.bit >= 0) {
					row[s.bit] = s;
				}
			}
		}
		return row[bit];
	}

	// ======================================================================== the choreography

	private static float[] k(float rax, float ray, float raz, float lax, float lay, float laz, float rlx, float rly,
			float rlz, float llx, float lly, float llz, float hx, float hy, float lean, float sit) {
		return new float[] { rax, ray, raz, lax, lay, laz, rlx, rly, rlz, llx, lly, llz, hx, hy, lean, sit, REST_TILT };
	}

	/** {@code base} with the arms replaced. */
	private static float[] arms(float[] base, float rax, float ray, float raz, float lax, float lay, float laz) {
		float[] o = base.clone();
		o[RAX] = rax;
		o[RAY] = ray;
		o[RAZ] = raz;
		o[LAX] = lax;
		o[LAY] = lay;
		o[LAZ] = laz;
		return o;
	}

	private static float[] with(float[] base, int i, float v) {
		float[] o = base.clone();
		o[i] = v;
		return o;
	}

	private static float[] with(float[] base, int i, float v, int j, float w) {
		return with(with(base, i, v), j, w);
	}

	/** {@code base} with the right arm set to {@code arm} (x, y, z, wrist). */
	private static float[] withRight(float[] base, float[] arm) {
		float[] o = base.clone();
		o[RAX] = arm[0];
		o[RAY] = arm[1];
		o[RAZ] = arm[2];
		o[TILT] = arm[3];
		return o;
	}

	private static float[] lerpArm(float[] a, float[] b, float t) {
		float[] o = new float[4];
		for (int i = 0; i < 4; i++) {
			o[i] = a[i] + (b[i] - a[i]) * t;
		}
		return o;
	}

	/** A right-arm strike key: arm x, y, z and the wrist. */
	private static float[] a(float x, float y, float z, float wrist) {
		return new float[] { x, y, z, wrist };
	}

	// legs
	private static final float[] STAND = k(0f, 0f, 0.08f, 0f, 0f, -0.08f, 0f, 0f, 0.03f, 0f, 0f, -0.03f, 0.1f, 0f, 0f, 0f);
	private static final float[] READY = k(-0.1f, 0f, 0.14f, -0.1f, 0f, -0.14f, 0f, 0f, 0.07f, 0f, 0f, -0.07f, 0.15f, 0f, 0f, 0f);

	/** Sitting on the floor, legs straight out in front, a little apart. */
	private static float[] sit(float[] base) {
		float[] o = base.clone();
		o[RLX] = -1.48f;
		o[RLY] = 0.10f;
		o[RLZ] = 0.04f;
		o[LLX] = -1.48f;
		o[LLY] = -0.10f;
		o[LLZ] = -0.04f;
		o[SIT] = 1f;
		return o;
	}

	private static float[] lean(float[] base, float lean, float head) {
		float[] o = base.clone();
		o[LEAN] = lean;
		o[HX] = head;
		return o;
	}

	private static final float[] SIT_REST = sit(k(-0.5f, -0.1f, 0.12f, -0.5f, 0.1f, -0.12f, 0, 0, 0, 0, 0, 0, 0.35f, 0f, 0.15f, 1f));

	static List<Segment> segments(int kind, int plan) {
		List<Segment> out = new ArrayList<>();
		boolean feet = (plan & 8) != 0;
		boolean legs = (plan & 4) != 0;
		boolean chest = (plan & 2) != 0;
		boolean head = (plan & 1) != 0;
		if (kind == KIND_MK1) {
			if (feet || legs) {
				out.add(mk1SitDown());
			}
			if (feet) {
				out.add(mk1Boots());
			}
			if (legs) {
				out.add(mk1Greaves());
			}
			if (feet || legs) {
				out.add(mk1StandUp());
			}
			if (chest) {
				out.add(mk1Chest());
			}
			if (head) {
				out.add(mk1Helmet());
			}
		} else {
			out.add(new Segment(-1, 10).key(0, STAND, 0).key(8, READY, 0));
			if (feet) {
				out.add(wsBoots());
			}
			if (legs) {
				out.add(wsGreaves());
			}
			if (chest) {
				out.add(wsChest());
			}
			if (head) {
				out.add(wsHelmet());
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ Mark 1 (600 ticks)

	private static final int H = PROP_HAMMER;
	private static final int HP = PROP_HAMMER | PROP_PIECE;

	private static Segment mk1SitDown() {
		Segment s = new Segment(-1, 25);
		s.key(0, STAND, H);
		s.key(11, k(-0.9f, 0f, 0.2f, -0.9f, 0f, -0.2f, -0.85f, 0.08f, 0.04f, -0.85f, -0.08f, -0.04f, 0.3f, 0f, 0.45f, 0.45f), H);
		s.key(23, SIT_REST, H);
		s.event(22, EV_SIT, 0f, 0.1f, 0f);
		return s;
	}

	/** Sitting, reaching out to the left for the next piece on the floor beside you. */
	private static final float[] SIT_REACH = sit(k(-0.5f, -0.1f, 0.12f, -0.85f, 0f, -1.1f, 0, 0, 0, 0, 0, 0, 0.35f, 0f, 0.1f, 1f));

	private static Segment mk1Boots() {
		Segment s = new Segment(3, 135);
		s.key(0, SIT_REST, H);
		s.key(12, SIT_REACH, H);
		s.key(15, SIT_REACH, HP);
		s.event(15, EV_PICKUP, -0.35f, 0.1f, 0.1f);
		float[] bring = sit(k(-0.6f, -0.1f, 0.1f, -1.0f, 0.3f, -0.05f, 0, 0, 0, 0, 0, 0, 0.6f, 0f, 0.5f, 1f));
		s.key(26, bring, HP);
		s.key(27, bring, H);
		float[] press = sit(k(-0.95f, -0.25f, -0.05f, -0.95f, 0.25f, 0.05f, 0, 0, 0, 0, 0, 0, 0.65f, 0f, 0.6f, 1f));
		s.key(36, press, H);
		s.key(41, with(press, RAX, -0.85f, LAX, -0.85f), H);
		s.key(46, press, H);
		s.event(46, EV_CLAMP, 0f, 0.12f, 0.72f);
		s.fit(26, 46, 134);
		// five strikes, right boot / left boot alternately; the left hand steadies the boot
		// sitting upright: the fist comes down to just above the shins and the wrist drops the hammer head onto the boot
		float[] steady = sit(k(-0.6f, -0.1f, 0.1f, -0.9f, 0.2f, 0f, 0, 0, 0, 0, 0, 0, 0.65f, 0f, 0f, 1f));
		s.strikes(60, 5, 12, steady, a(-2.5f, -0.05f, 0.15f, 2.0f),
				new float[][] { a(-1.45f, 0f, 0f, 2.93f), a(-1.45f, -0.5f, -0.05f, 2.93f) },
				new float[][] { { 0.12f, 0.3f, 0.62f }, { -0.12f, 0.3f, 0.62f } }, H);
		// pull the straps tight
		float[] grab = sit(k(-0.85f, -0.2f, 0f, -0.85f, 0.2f, 0f, 0, 0, 0, 0, 0, 0, 0.6f, 0f, 0.5f, 1f));
		float[] pull = sit(k(-0.55f, -0.2f, 0.05f, -0.55f, 0.2f, -0.05f, 0, 0, 0, 0, 0, 0, 0.5f, 0f, 0.3f, 1f));
		s.key(116, grab, H);
		s.key(122, pull, H);
		s.event(122, EV_STRAP, 0f, 0.12f, 0.6f);
		s.key(127, grab, H);
		s.key(132, pull, H);
		s.event(132, EV_STRAP, 0f, 0.12f, 0.6f);
		s.key(134, SIT_REST, H);
		return s;
	}

	private static Segment mk1Greaves() {
		Segment s = new Segment(2, 140);
		s.key(0, SIT_REST, H);
		s.key(10, SIT_REACH, H);
		s.key(13, SIT_REACH, HP);
		s.event(13, EV_PICKUP, -0.35f, 0.1f, 0.1f);
		float[] bring = sit(k(-0.55f, -0.1f, 0.1f, -0.8f, 0.3f, 0f, 0, 0, 0, 0, 0, 0, 0.5f, 0f, 0.3f, 1f));
		s.key(24, bring, HP);
		s.key(25, bring, H);
		float[] press = sit(k(-0.75f, -0.2f, -0.05f, -0.75f, 0.2f, 0.05f, 0, 0, 0, 0, 0, 0, 0.55f, 0f, 0.35f, 1f));
		s.key(34, press, H);
		s.key(39, with(press, RAX, -0.65f, LAX, -0.65f), H);
		s.key(44, press, H);
		s.event(44, EV_CLAMP, 0f, 0.22f, 0.35f);
		s.fit(24, 44, 136);
		// leaning back a little so the knees are in front of the fist; the hammer is driven straight down onto them
		float[] steady = sit(k(-0.55f, -0.1f, 0.1f, -0.75f, 0.2f, 0f, 0, 0, 0, 0, 0, 0, 0.55f, 0f, -0.35f, 1f));
		s.strikes(58, 4, 12, steady, a(-2.6f, -0.05f, 0.2f, 2.2f),
				new float[][] { a(-1.6f, 0f, 0f, 3.18f), a(-1.6f, -0.5f, -0.05f, 3.18f) },
				new float[][] { { 0.12f, 0.3f, 0.38f }, { -0.12f, 0.3f, 0.38f } }, H);
		// tighten the straps at the hips
		float[] hips = sit(k(-0.25f, 0f, 0.35f, -0.25f, 0f, -0.35f, 0, 0, 0, 0, 0, 0, 0.5f, 0f, 0.15f, 1f));
		float[] tug = sit(k(-0.05f, 0f, 0.22f, -0.05f, 0f, -0.22f, 0, 0, 0, 0, 0, 0, 0.45f, 0f, 0.1f, 1f));
		s.key(104, hips, H);
		s.key(110, tug, H);
		s.event(110, EV_STRAP, 0f, 0.15f, 0.05f);
		s.key(116, hips, H);
		s.key(122, tug, H);
		s.event(122, EV_STRAP, 0f, 0.15f, 0.05f);
		s.key(136, SIT_REST, H);
		return s;
	}

	private static Segment mk1StandUp() {
		Segment s = new Segment(-1, 35);
		s.key(0, SIT_REST, H);
		s.key(12, k(-1.0f, 0f, 0.25f, -1.0f, 0f, -0.25f, -0.9f, 0.08f, 0.04f, -0.9f, -0.08f, -0.04f, 0.3f, 0f, 0.55f, 0.5f), H);
		s.key(24, k(-0.2f, 0f, 0.15f, -0.2f, 0f, -0.15f, -0.2f, 0f, 0.03f, -0.2f, 0f, -0.03f, 0.15f, 0f, 0.15f, 0.05f), H);
		s.key(32, STAND, H);
		s.event(28, EV_SETTLE, 0f, 0.6f, 0f);
		return s;
	}

	/** Standing, bent over, the left hand reaching for the piece. */
	private static float[] bendPick(float lean) {
		return k(-0.3f, 0f, 0.1f, -0.35f, 0.1f, -0.3f, 0f, 0f, 0.03f, 0f, 0f, -0.03f, 0.75f, 0f, lean, 0f);
	}

	private static final float[] OVERHEAD = k(-2.95f, 0f, 0.3f, -2.95f, 0f, -0.3f, 0f, 0f, 0.05f, 0f, 0f, -0.05f, -0.35f, 0f, 0f, 0f);
	private static final float[] DOWN_ONTO = k(-1.6f, -0.5f, 0.5f, -1.6f, 0.5f, -0.5f, 0f, 0f, 0.05f, 0f, 0f, -0.05f, 0.2f, 0f, 0f, 0f);
	private static final float[] HUG = k(-1.25f, -0.75f, 0f, -1.25f, 0.75f, 0f, 0f, 0f, 0.05f, 0f, 0f, -0.05f, 0.45f, 0f, 0.05f, 0f);
	private static final float[] HELMET_UP = k(-2.75f, -0.3f, 0.05f, -2.75f, 0.3f, -0.05f, 0f, 0f, 0.05f, 0f, 0f, -0.05f, -0.4f, 0f, 0f, 0f);
	private static final float[] ONTO_HEAD = k(-2.45f, -0.75f, 0.1f, -2.45f, 0.75f, -0.1f, 0f, 0f, 0.05f, 0f, 0f, -0.05f, 0.05f, 0f, 0f, 0f);
	/** Chest rivets: the fist in front of the chest, the wrist turning the hammer back onto the plate. */
	private static final float[] CHEST_RAISED = a(-2.3f, -0.45f, 0.3f, 3.4f);
	private static final float[] CHEST_HIT = a(-1.45f, -0.75f, 0f, 4.08f);
	private static final float[] PRESS_HEAD =k(-2.55f, -0.65f, 0.15f, -2.55f, 0.65f, -0.15f, 0f, 0f, 0.05f, 0f, 0f, -0.05f, 0.12f, 0f, 0f, 0f);

	private static Segment mk1Chest() {
		Segment s = new Segment(1, 160);
		s.key(0, STAND, H);
		s.key(12, bendPick(0.8f), H);
		s.key(15, bendPick(0.8f), HP);
		s.event(15, EV_PICKUP, -0.3f, 0.5f, 0.3f);
		s.key(28, OVERHEAD, HP);
		s.key(40, DOWN_ONTO, HP);
		s.key(41, DOWN_ONTO, H);
		s.key(50, HUG, H);
		s.key(56, with(HUG, RAX, -1.15f, LAX, -1.15f), H);
		s.key(62, HUG, H);
		s.event(62, EV_CLAMP, 0f, 1.15f, 0.25f);
		s.fit(40, 62, 156);
		// the chest rivets: the left hand holds the plate, the right hammers
		float[] holdPlate = with(k(-1.0f, -0.4f, 0.2f, -1.2f, 0.6f, 0f, 0f, 0f, 0.05f, 0f, 0f, -0.05f, 0.55f, 0f, 0.05f, 0f), TILT, 3.4f);
		s.strikes(74, 3, 12, holdPlate, CHEST_RAISED, new float[][] { CHEST_HIT, a(-1.4f, -0.65f, 0.05f, 4.05f) },
				new float[][] { { 0.05f, 1.2f, 0.2f }, { -0.05f, 1.15f, 0.2f } }, H);
		// the left arm held out, the right hammering the bracer from above
		float[] armOut = with(k(-1.0f, -0.4f, 0.2f, -1.45f, 0.35f, 0f, 0f, 0f, 0.05f, 0f, 0f, -0.05f, 0.45f, 0f, 0f, 0f), TILT, 2.6f);
		s.key(104, armOut, H);
		s.strikes(114, 3, 11, armOut, a(-3.0f, -0.35f, 0.2f, 2.6f), new float[][] { a(-2.6f, -0.5f, 0.05f, 3.69f) },
				new float[][] { { -0.15f, 1.35f, 0.5f } }, H);
		// roll the shoulders: the plates settle
		s.key(146, k(0.1f, 0f, 0.45f, 0.1f, 0f, -0.45f, 0f, 0f, 0.05f, 0f, 0f, -0.05f, -0.1f, 0f, 0f, 0f), H);
		s.event(146, EV_SETTLE, 0f, 1.3f, 0f);
		s.key(156, STAND, H);
		return s;
	}

	private static Segment mk1Helmet() {
		Segment s = new Segment(0, 105);
		s.key(0, STAND, H);
		s.key(10, bendPick(0.6f), H);
		s.key(13, bendPick(0.6f), HP);
		s.event(13, EV_PICKUP, -0.3f, 0.6f, 0.3f);
		s.key(24, HELMET_UP, HP);
		s.key(34, ONTO_HEAD, HP);
		s.key(35, ONTO_HEAD, H);
		s.key(44, PRESS_HEAD, H);
		s.key(49, with(PRESS_HEAD, RAX, -2.45f, LAX, -2.45f), H);
		s.key(54, PRESS_HEAD, H);
		s.event(54, EV_CLAMP, 0f, 1.62f, 0f);
		s.fit(34, 54, 86);
		// two strikes at the side of the helmet, the left hand steadying it on top
		float[] steady = with(k(-2.6f, 0.2f, 0.9f, -2.7f, 0.55f, 0f, 0f, 0f, 0.05f, 0f, 0f, -0.05f, 0.1f, 0f, 0f, 0f), TILT, 2.0f);
		s.strikes(66, 2, 12, steady, a(-3.0f, -0.2f, 0.5f, 2.0f), new float[][] { a(-2.75f, -0.5f, 0.3f, 2.4f) },
				new float[][] { { 0.2f, 1.85f, 0.1f } }, H);
		// the left hand finds the faceplate and pulls it down
		s.key(88, k(-0.4f, 0f, 0.15f, -2.8f, 0.45f, 0.1f, 0f, 0f, 0.05f, 0f, 0f, -0.05f, 0.1f, 0f, 0f, 0f), H);
		s.key(96, k(-0.35f, 0f, 0.15f, -1.7f, 0.45f, 0f, 0f, 0f, 0.05f, 0f, 0f, -0.05f, 0.05f, 0f, 0f, 0f), H);
		s.event(93, EV_FACEPLATE, 0f, 1.6f, 0.2f);
		s.key(104, with(STAND, HX, -0.2f), H);
		return s;
	}

	// ------------------------------------------------------------------ Marks 2-7 workshop (500 ticks)

	private static final int W = PROP_WRENCH;
	private static final int P = PROP_PIECE;

	private static Segment wsBoots() {
		Segment s = new Segment(3, 90);
		s.key(0, READY, 0);
		s.key(8, bendPick(0.95f), 0);
		s.key(10, bendPick(0.95f), P);
		s.event(10, EV_PICKUP, -0.3f, 0.3f, 0.3f);
		float[] place = k(-0.25f, 0f, 0.1f, -0.15f, 0.15f, -0.1f, 0f, 0f, 0.05f, 0f, 0f, -0.05f, 0.8f, 0f, 0.85f, 0f);
		s.key(20, place, P);
		s.key(22, place, 0);
		// step into them: arms out for balance, right foot then left, each clamping shut
		float[] balance = k(-0.1f, 0f, 0.45f, -0.1f, 0f, -0.45f, 0f, 0f, 0.05f, 0f, 0f, -0.05f, 0.6f, 0f, 0.1f, 0f);
		s.key(28, with(balance, RLX, -0.55f), 0);
		s.key(34, balance, 0);
		s.event(34, EV_CLAMP, 0.12f, 0.1f, 0.05f);
		s.key(40, with(balance, LLX, -0.55f), 0);
		s.key(46, balance, 0);
		s.event(46, EV_CLAMP, -0.12f, 0.1f, 0.05f);
		s.fit(22, 46, 80);
		// bend down with the wrench and ratchet the ankle bolts
		// bent double, the arm swung back toward the feet and the wrench turned down onto the ankle bolt
		float[] ankle = with(k(0.45f, -0.1f, 0.05f, 0.35f, 0.1f, -0.05f, 0f, 0f, 0.05f, 0f, 0f, -0.05f, 0.8f, 0f, 0.95f, 0f),
				TILT, 1.6f);
		s.key(50, with(balance, HX, 0.7f), W);
		s.event(50, EV_TOOL, 0.2f, 0.9f, 0f);
		s.key(54, ankle, W);
		s.ratchet(54, 3, 8, ankle, -0.35f, 0.15f, new float[] { 0.1f, 0.15f, 0.2f }, W);
		s.key(84, READY, 0);
		return s;
	}

	private static Segment wsGreaves() {
		Segment s = new Segment(2, 100);
		s.key(0, READY, 0);
		s.key(8, bendPick(0.95f), 0);
		s.key(10, bendPick(0.95f), P);
		s.event(10, EV_PICKUP, -0.3f, 0.3f, 0.3f);
		float[] hold = k(-0.45f, -0.2f, 0.05f, -0.45f, 0.2f, -0.05f, 0f, 0f, 0.05f, 0f, 0f, -0.05f, 0.55f, 0f, 0.3f, 0f);
		s.key(22, hold, P);
		s.key(23, hold, 0);
		// slap each greave shut
		s.key(30, k(-0.35f, 0f, 0.05f, -0.3f, 0.2f, -0.1f, 0f, 0f, 0.05f, 0f, 0f, -0.05f, 0.5f, 0f, 0.35f, 0f), 0);
		s.event(34, EV_CLAMP, 0.12f, 0.6f, 0.15f);
		s.key(38, k(-0.3f, -0.2f, 0.1f, -0.35f, 0f, -0.05f, 0f, 0f, 0.05f, 0f, 0f, -0.05f, 0.5f, 0f, 0.35f, 0f), 0);
		s.event(42, EV_CLAMP, -0.12f, 0.6f, 0.15f);
		s.fit(22, 42, 94);
		// two hammer taps at the knees
		float[] knees = k(-0.6f, -0.1f, 0.1f, -0.6f, 0.2f, 0f, 0f, 0f, 0.05f, 0f, 0f, -0.05f, 0.7f, 0f, 0.3f, 0f);
		s.key(46, knees, H);
		s.event(46, EV_TOOL, 0.2f, 0.9f, 0f);
		s.strikes(56, 2, 12, knees, a(-1.6f, -0.1f, 0.2f, 1.8f),
				new float[][] { a(-0.25f, -0.05f, 0f, 2.39f), a(-0.25f, -0.3f, -0.05f, 2.39f) },
				new float[][] { { 0.12f, 0.55f, 0.18f }, { -0.12f, 0.55f, 0.18f } }, H);
		// ratchet the hip bolts
		// the fist just in front of the hip, the wrench turned back onto the bolt at its side
		float[] hip = with(k(-0.35f, -0.1f, 0.2f, -0.3f, 0f, -0.3f, 0f, 0f, 0.05f, 0f, 0f, -0.05f, 0.45f, 0f, 0.15f, 0f),
				TILT, 3.0f);
		s.key(74, hip, W);
		s.event(74, EV_TOOL, 0.2f, 0.9f, 0f);
		s.ratchet(78, 2, 8, hip, -0.4f, 0.1f, new float[] { 0.25f, 0.95f, 0.1f }, W);
		s.key(96, READY, 0);
		return s;
	}

	private static Segment wsChest() {
		Segment s = new Segment(1, 175);
		s.key(0, READY, 0);
		s.key(10, bendPick(0.75f), 0);
		s.key(12, bendPick(0.75f), P);
		s.event(12, EV_PICKUP, -0.3f, 0.5f, 0.3f);
		s.key(24, OVERHEAD, P);
		s.key(36, DOWN_ONTO, P);
		s.key(37, DOWN_ONTO, 0);
		s.key(44, HUG, 0);
		s.event(50, EV_CLAMP, 0.22f, 1.1f, 0.1f);
		s.key(52, with(HUG, RAX, -1.1f, LAX, -1.1f), 0);
		s.event(58, EV_CLAMP, -0.22f, 1.1f, 0.1f);
		s.fit(36, 58, 160);
		// press the arc reactor home
		float[] reactor = k(-1.1f, -0.8f, 0f,-0.2f, 0f, -0.15f, 0f, 0f, 0.05f, 0f, 0f, -0.05f, 0.5f, 0f, 0f, 0f);
		s.key(64, reactor, 0);
		s.event(70, EV_SERVO, 0f, 1.2f, 0.25f);
		// two rivets
		float[] holdPlate = with(k(-1.0f, -0.4f, 0.2f, -1.2f, 0.6f, 0f, 0f, 0f, 0.05f, 0f, 0f, -0.05f, 0.55f, 0f, 0.05f, 0f), TILT, 3.4f);
		s.key(74, holdPlate, H);
		s.event(74, EV_TOOL, 0.2f, 0.9f, 0f);
		s.strikes(84, 2, 12, holdPlate, CHEST_RAISED, new float[][] { CHEST_HIT },
				new float[][] { { 0.05f, 1.2f, 0.2f } }, H);
		// ratchet the left gauntlet
		// the left forearm held out low, the right fist over it, the wrench turned down onto the gauntlet
		float[] gauntletL = with(k(-1.15f, -0.5f, 0f, -1.2f, 0.3f, 0f, 0f, 0f, 0.05f, 0f, 0f, -0.05f, 0.55f, 0f, 0f, 0f),
				TILT, 2.0f);
		s.key(102, gauntletL, W);
		s.event(102, EV_TOOL, 0.2f, 0.9f, 0f);
		s.ratchet(106, 3, 8, gauntletL, -0.2f, 0.15f, new float[] { -0.12f, 1.15f, 0.55f }, W);
		// clamp the right gauntlet with the left hand
		float[] gauntletR = k(-1.45f, -0.3f, 0f, -1.3f, 0.65f, 0f, 0f, 0f, 0.05f, 0f, 0f, -0.05f, 0.45f, 0f, 0f, 0f);
		s.key(134, gauntletR, 0);
		s.event(142, EV_CLAMP, 0.12f, 1.15f, 0.55f);
		s.key(144, with(gauntletR, LAX, -1.4f), 0);
		// flex: arms out, the servos check
		s.key(152, k(-0.1f, 0f, 1.2f, -0.1f, 0f, -1.2f, 0f, 0f, 0.07f, 0f, 0f, -0.07f, -0.05f, 0f, 0f, 0f), 0);
		s.event(154, EV_SERVO, 0f, 1.3f, 0f);
		s.key(162, k(-0.25f, 0f, 0.5f, -0.25f, 0f, -0.5f, 0f, 0f, 0.07f, 0f, 0f, -0.07f, 0f, 0f, 0f, 0f), 0);
		s.key(170, READY, 0);
		return s;
	}

	private static Segment wsHelmet() {
		Segment s = new Segment(0, 125);
		s.key(0, READY, 0);
		s.key(10, bendPick(0.6f), 0);
		s.key(12, bendPick(0.6f), P);
		s.event(12, EV_PICKUP, -0.3f, 0.6f, 0.3f);
		s.key(24, HELMET_UP, P);
		s.key(34, ONTO_HEAD, P);
		s.key(35, ONTO_HEAD, 0);
		s.key(42, PRESS_HEAD, 0);
		s.event(46, EV_CLAMP, 0.15f, 1.5f, 0.05f);
		s.key(48, with(PRESS_HEAD, RAX, -2.45f, LAX, -2.45f), 0);
		s.event(52, EV_CLAMP, -0.15f, 1.5f, 0.05f);
		s.fit(34, 52, 86);
		// ratchet the bolt at the back of the neck, the left hand holding the helmet
		// the right fist up behind the head, the wrench turned down onto the bolt at the back of the neck; the left hand
		// holds the helmet steady at the brow
		float[] neck = with(k(-3.5f, 0.1f, 0.2f, -2.8f, 0.4f, 0f, 0f, 0f, 0.05f, 0f, 0f, -0.05f, 0.3f, 0f, 0f, 0f),
				TILT, -1.25f);
		s.key(58, neck, W);
		s.event(58, EV_TOOL, 0.2f, 0.9f, 0f);
		s.ratchet(62, 2, 8, neck, -0.25f, 0.15f, new float[] { 0.05f, 1.55f, -0.15f }, W);
		s.key(84, READY, 0);
		// last of all: the faceplate, pulled down by hand
		s.key(94, k(-2.85f, -0.45f, 0.05f, -0.1f, 0f, -0.12f, 0f, 0f, 0.07f, 0f, 0f, -0.07f, 0.05f, 0f, 0f, 0f), 0);
		s.key(102, k(-1.75f, -0.45f, 0f, -0.1f, 0f, -0.12f, 0f, 0f, 0.07f, 0f, 0f, -0.07f, 0.05f, 0f, 0f, 0f), 0);
		s.event(99, EV_FACEPLATE, 0f, 1.6f, 0.2f);
		s.key(110, with(READY, HX, -0.2f), 0);
		return s;
	}

	// ======================================================================== the server sequence

	/** Register the death / respawn / logout / dimension-change safety hooks. */
	public static void initialize() {
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p) {
				onDeath(p);
			}
		});
		net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register(
				(oldPlayer, newPlayer, alive) -> onDeath(newPlayer));
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
				(handler, server) -> onDisconnect(handler.getPlayer()));
		net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
				(player, origin, destination) -> onChangeWorld(player));
	}

	/** Is {@code player} putting a suit on by hand right now? */
	public static boolean running(ServerPlayer player) {
		TonyStarkState s = TonyStark.state(player);
		return s.transitionManual != 0 && !s.transitionSuit.isEmpty();
	}

	/** Which hand build is running ({@link #KIND_MK1} / {@link #KIND_WORKSHOP}), or -1. */
	public static int runningKind(ServerPlayer player) {
		return running(player) ? TonyStark.state(player).transitionManual - 1 : -1;
	}

	/**
	 * Start putting {@code suit} on by hand: the pieces in {@code mask} are only <em>reserved</em> (each leaves the pack in
	 * the tick it is fitted, so it is always in exactly one place), the player is held still, flight is cut and the synced
	 * pose / style start. Called by {@code IronManSuitUpManager#beginSuitUp} once all its checks have passed.
	 */
	static void start(ServerPlayer player, IronManSuit suit, int kind, int mask) {
		TonyStarkState s = TonyStark.state(player);
		Schedule sch = schedule(kind, mask);
		s.transitionSuit = suit.id();
		s.transitionUp = true;
		s.transitionToCase = false;
		s.transitionFromCase = false;
		s.transitionBracelet = false;
		s.transitionManual = kind + 1;
		s.transitionPlan = mask;
		s.transitionMask = mask;
		s.transitionReleaseMask = 0;
		s.transitionTotal = sch.total();
		s.transitionTicks = sch.total();
		if (IronManFlight.isFlying(player)) {
			IronManFlight.setFlying(player, false);
		}
		if (com.projecthero.mod.ironman.RepulsorBoots.isFlying(player)) {
			com.projecthero.mod.ironman.RepulsorBoots.setFlying(player, false);
		}
		IronManSuitPlatformBlockEntity.setFrozen(player, true);
		IronManSuitFx fx = IronManSuitFx.of(player);
		// fresh piece clocks (a stale one must not be read with this style's windows), the pose clock + who / what
		player.setAttached(ModAttachments.IRON_MAN_SUIT_FX, new IronManSuitFx(0L, 0L, 0L, 0L, 0, style(kind),
				player.level().getGameTime(), sch.total(), poseKind(kind), fx.faceplateAt(), variant(suit.id(), mask)));
		player.displayClientMessage(Component.translatable(kind == KIND_MK1
				? "message.projecthero.ironman.hand_build_mk1" : "message.projecthero.ironman.hand_build_workshop",
				Component.translatable(suit.nameKey()), sch.total() / 20).withStyle(ChatFormatting.GRAY), true);
		playAt(player, SoundEvents.ARMOR_EQUIP_GENERIC.value(), 0f, 0.5f, 0f, 0.7f, 0.9f);
	}

	/** One server tick of a hand build ({@code IronManSuitUpManager#tick} hands it over while one runs). */
	static void tick(ServerPlayer player, TonyStarkState s) {
		if (!player.isAlive()) {
			abort(player);
			return;
		}
		int kind = s.transitionManual - 1;
		Schedule sch = schedule(kind, s.transitionPlan);
		s.transitionTicks--;
		int elapsed = s.transitionTotal - s.transitionTicks;
		// held still for the whole build: no walking, no jumping, no flying
		IronManSuitPlatformBlockEntity.setFrozen(player, true);
		if (IronManFlight.isFlying(player)) {
			IronManFlight.setFlying(player, false);
		}
		for (int bit = 0; bit < 4; bit++) {
			if ((s.transitionMask & (1 << bit)) != 0 && sch.pieceAt(bit) >= 0 && elapsed >= sch.pieceAt(bit)) {
				s.transitionMask &= ~(1 << bit);
				fitPiece(player, s, bit, kind);
			}
		}
		for (Event e : sch.eventsAt(elapsed)) {
			fire(player, s, kind, e);
		}
		if (s.transitionTicks <= 0) {
			finish(player, s);
		}
	}

	/** The piece leaves the pack and lands in its slot (the real stack), starting its hand build-on. */
	private static void fitPiece(ServerPlayer player, TonyStarkState s, int bit, int kind) {
		EquipmentSlot slot = SLOT_BY_BIT[bit];
		int idx = IronManSuitUpManager.findInInventory(player, s.transitionSuit, IronManSuitUpManager.typeOf(slot));
		if (idx < 0) {
			return; // gone (dropped, traded) -- skip it rather than conjure one
		}
		ItemStack piece = player.getInventory().removeItem(idx, 1);
		if (piece.isEmpty()) {
			return;
		}
		IronManSuitUpManager.evictSlot(player, slot);
		player.setItemSlot(slot, piece);
		IronManSuitFx.markPiece(player, slot, true);
		if (bit == 0) {
			// the helmet goes on with its faceplate up; it is pulled down last (EV_FACEPLATE)
			player.setAttached(ModAttachments.IRON_MAN_FACEPLATE_OPEN, true);
			IronManSuitFx.faceplateMoved(player);
		}
		float y = (float) IronManSuitUpManager.slotHeight(slot);
		playAt(player, (kind == KIND_MK1 ? SoundEvents.ARMOR_EQUIP_IRON : SoundEvents.ARMOR_EQUIP_NETHERITE).value(),
				0f, y, 0.1f, 0.8f, 0.9f + bit * 0.05f);
	}

	private static void fire(ServerPlayer player, TonyStarkState s, int kind, Event e) {
		ServerLevel level = player.serverLevel();
		boolean pieceOn = e.bit() < 0 || IronManArmor.isPieceWorn(player, SLOT_BY_BIT[e.bit()], s.transitionSuit);
		Vec3 at = where(player, e);
		switch (e.type()) {
			case EV_PICKUP -> playAt(player, SoundEvents.ARMOR_EQUIP_CHAIN.value(), e, 0.6f, 1.1f);
			case EV_SIT -> playAt(player, SoundEvents.ARMOR_EQUIP_GENERIC.value(), e, 0.6f, 0.7f);
			case EV_TOOL -> playAt(player, SoundEvents.CHAIN_PLACE, e, 0.5f, 1.4f);
			case EV_SETTLE -> {
				if (pieceOn) {
					playAt(player, SoundEvents.ARMOR_EQUIP_IRON.value(), e, 0.7f, 0.8f);
				}
			}
			case EV_STRIKE -> {
				if (!pieceOn) {
					return;
				}
				// a metal-on-metal clink, the anvil ring under it on the Mark 1, and a spray of sparks
				float pitch = 1.05f + level.random.nextFloat() * 0.25f;
				playAt(player, SoundEvents.ANVIL_USE, e, kind == KIND_MK1 ? 0.45f : 0.3f, kind == KIND_MK1 ? pitch : pitch + 0.2f);
				playAt(player, SoundEvents.SMITHING_TABLE_USE, e, 0.35f, 1.3f);
				level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, kind == KIND_MK1 ? 8 : 5, 0.06, 0.06, 0.06, 0.25);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 4, 0.05, 0.05, 0.05, 0.15);
				if (kind == KIND_MK1) {
					level.sendParticles(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, 1, 0.03, 0.03, 0.03, 0.01);
				}
			}
			case EV_CLAMP -> {
				if (!pieceOn) {
					return;
				}
				IronManSounds.playAt(level, at.x, at.y, at.z, IronManSounds.CLAMP, 0.75f, kind == KIND_MK1 ? 0.8f : 1.05f);
				playAt(player, SoundEvents.IRON_TRAPDOOR_CLOSE, e, 0.4f, kind == KIND_MK1 ? 0.9f : 1.4f);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 3, 0.08, 0.05, 0.08, 0.05);
			}
			case EV_RATCHET -> {
				if (pieceOn) {
					playAt(player, SoundEvents.LEVER_CLICK, e, 0.35f, 1.9f);
					playAt(player, SoundEvents.TRIPWIRE_CLICK_ON, e, 0.3f, 1.6f);
				}
			}
			case EV_STRAP -> {
				if (pieceOn) {
					playAt(player, SoundEvents.ARMOR_EQUIP_LEATHER.value(), e, 0.7f, 1.0f);
				}
			}
			case EV_SERVO -> {
				if (pieceOn) {
					IronManSounds.playAt(level, at.x, at.y, at.z, IronManSounds.SERVO, 0.7f, 1.0f);
				}
			}
			case EV_FACEPLATE -> closeFaceplate(player, s.transitionSuit);
			default -> {
			}
		}
	}

	/** The faceplate pulled down by hand: the full suit seals and comes online; a partial one just shuts it. */
	private static void closeFaceplate(ServerPlayer player, String suitId) {
		if (!IronManArmor.isPieceWorn(player, EquipmentSlot.HEAD, suitId)) {
			return;
		}
		if (IronManArmor.wearingFullSuit(player, suitId)) {
			IronManSuitUpManager.faceplateClose(player, suitId);
			return;
		}
		player.setAttached(ModAttachments.IRON_MAN_FACEPLATE_OPEN, false);
		IronManSuitFx.faceplateMoved(player);
		IronManSounds.play(player, IronManSounds.FACEPLATE_SEAL, 0.8f, 1.0f);
	}

	/** The last tick: the suit is on and online, the player can move again. */
	static void finish(ServerPlayer player, TonyStarkState s) {
		String suitId = s.transitionSuit;
		if (player.getAttachedOrElse(ModAttachments.IRON_MAN_FACEPLATE_OPEN, false)
				&& IronManArmor.isPieceWorn(player, EquipmentSlot.HEAD, suitId)) {
			closeFaceplate(player, suitId); // the faceplate beat was skipped somehow -- never leave it hanging open
		}
		clear(player, s);
		IronManSuitFx.endPose(player);
	}

	/**
	 * Death (or a respawn that carried the state over): stop where it is. Every piece is already in exactly one place --
	 * on the body (it drops with the armour) or still in the pack -- so nothing is lost or duplicated.
	 */
	public static void abort(ServerPlayer player) {
		TonyStarkState s = TonyStark.state(player);
		if (s.transitionManual == 0) {
			return;
		}
		clear(player, s);
		IronManSuitFx fx = IronManSuitFx.of(player);
		player.setAttached(ModAttachments.IRON_MAN_SUIT_FX, IronManSuitFx.EMPTY.withFaceplate(fx.faceplateAt()));
	}

	/**
	 * Logout / server stop / changing dimension mid-build: finish it at once -- every still-planned piece goes from the pack
	 * straight onto the body (no animation) and the faceplate shuts, so the player is saved (or arrives) fully suited and no
	 * piece is ever in two places or none.
	 */
	public static void completeNow(ServerPlayer player) {
		TonyStarkState s = TonyStark.state(player);
		if (s.transitionManual == 0 || s.transitionSuit.isEmpty()) {
			return;
		}
		String suitId = s.transitionSuit;
		for (int bit = 0; bit < 4; bit++) {
			if ((s.transitionMask & (1 << bit)) == 0) {
				continue;
			}
			s.transitionMask &= ~(1 << bit);
			EquipmentSlot slot = SLOT_BY_BIT[bit];
			int idx = IronManSuitUpManager.findInInventory(player, suitId, IronManSuitUpManager.typeOf(slot));
			if (idx < 0) {
				continue;
			}
			ItemStack piece = player.getInventory().removeItem(idx, 1);
			if (!piece.isEmpty()) {
				IronManSuitUpManager.evictSlot(player, slot);
				player.setItemSlot(slot, piece);
			}
		}
		player.setAttached(ModAttachments.IRON_MAN_FACEPLATE_OPEN, false);
		clear(player, s);
		IronManSuitFx fx = IronManSuitFx.of(player);
		player.setAttached(ModAttachments.IRON_MAN_SUIT_FX, IronManSuitFx.EMPTY.withFaceplate(fx.faceplateAt()));
	}

	public static void onDeath(ServerPlayer player) {
		abort(player);
	}

	public static void onDisconnect(ServerPlayer player) {
		completeNow(player);
	}

	public static void onChangeWorld(ServerPlayer player) {
		completeNow(player);
	}

	private static void clear(ServerPlayer player, TonyStarkState s) {
		s.transitionManual = 0;
		s.transitionMask = 0;
		s.transitionPlan = 0;
		s.transitionReleaseMask = 0;
		s.transitionTicks = 0;
		s.transitionSuit = "";
		IronManSuitPlatformBlockEntity.setFrozen(player, false);
	}

	/** Where on the body an event happens, in the world (body-relative, scaled with the wearer, lowered while sitting). */
	static Vec3 where(ServerPlayer player, Event e) {
		double yaw = Math.toRadians(player.yBodyRot);
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double rx = -Math.cos(yaw);
		double rz = -Math.sin(yaw);
		float sc = player.getScale();
		return new Vec3(player.getX() + (rx * e.right() + fx * e.fwd()) * sc, player.getY() + e.up() * sc,
				player.getZ() + (rz * e.right() + fz * e.fwd()) * sc);
	}

	private static void playAt(ServerPlayer player, SoundEvent sound, Event e, float volume, float pitch) {
		Vec3 at = where(player, e);
		player.serverLevel().playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
	}

	private static void playAt(ServerPlayer player, SoundEvent sound, float right, float up, float fwd, float volume,
			float pitch) {
		playAt(player, sound, new Event(0, 0, -1, right, up, fwd), volume, pitch);
	}

	private static final EquipmentSlot[] SLOT_BY_BIT = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

	// ------------------------------------------------------------------ math

	static float[] lerp(float[] a, float[] b, float t) {
		float[] out = new float[a.length];
		for (int i = 0; i < a.length; i++) {
			out[i] = a[i] + (b[i] - a[i]) * t;
		}
		return out;
	}

	static float smooth(float x) {
		x = clamp(x);
		return x * x * (3f - 2f * x);
	}

	static float clamp(float v) {
		return v < 0f ? 0f : (v > 1f ? 1f : v);
	}
}
