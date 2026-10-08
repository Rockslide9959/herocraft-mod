package com.projecthero.mod.ironman.suit;

import static com.projecthero.mod.ironman.suit.IronManManualSuitUp.CHEST_HIT;
import static com.projecthero.mod.ironman.suit.IronManManualSuitUp.CHEST_RAISED;
import static com.projecthero.mod.ironman.suit.IronManManualSuitUp.HELMET_UP;
import static com.projecthero.mod.ironman.suit.IronManManualSuitUp.HUG;
import static com.projecthero.mod.ironman.suit.IronManManualSuitUp.LAX;
import static com.projecthero.mod.ironman.suit.IronManManualSuitUp.LAZ;
import static com.projecthero.mod.ironman.suit.IronManManualSuitUp.ONTO_HEAD;
import static com.projecthero.mod.ironman.suit.IronManManualSuitUp.OVERHEAD;
import static com.projecthero.mod.ironman.suit.IronManManualSuitUp.RAX;
import static com.projecthero.mod.ironman.suit.IronManManualSuitUp.RAZ;
import static com.projecthero.mod.ironman.suit.IronManManualSuitUp.HX;
import static com.projecthero.mod.ironman.suit.IronManManualSuitUp.RLX;
import static com.projecthero.mod.ironman.suit.IronManManualSuitUp.LLX;
import static com.projecthero.mod.ironman.suit.IronManManualSuitUp.STAND;
import static com.projecthero.mod.ironman.suit.IronManManualSuitUp.TILT;
import static com.projecthero.mod.ironman.suit.IronManManualSuitUp.a;
import static com.projecthero.mod.ironman.suit.IronManManualSuitUp.bendPick;
import static com.projecthero.mod.ironman.suit.IronManManualSuitUp.k;
import static com.projecthero.mod.ironman.suit.IronManManualSuitUp.with;

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
import com.projecthero.mod.ironman.suit.IronManManualSuitUp.Event;
import com.projecthero.mod.ironman.suit.IronManManualSuitUp.Key;
import com.projecthero.mod.ironman.suit.IronManManualSuitUp.Segment;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.15, explicit user request ("give new animations for removing the armour by pressing C for the mark 1-7; the mark
 * 2-7 should be different than the mark 1's"): what plain C does to take a Mark 1-7 suit off (the Mark 5 keeps its
 * suitcase fold, the Mark 8 and later their reverse build). Like the hand-built suit-up ({@link IronManManualSuitUp}) the
 * timeline is pure data (keyframed body pose + timed events), read alike by the server sequence, the synced pose clock and
 * the client pose / prop / texture code; the wearer is held still meanwhile (the Suit Platform's movement lock, with the
 * view's FOV held -- {@code GantryFovMixin}).
 *
 * <h2>{@link #KIND_MK1} -- the Mark 1, pulled off by hand</h2>
 * The faceplate is pushed up, then piece by piece, helmet -> chestplate -> greaves -> boots: the hammer knocks the bolts
 * loose (a clank and a spray of sparks at each), the plate is pulled off (it leaves its slot that tick -- the real stack
 * goes into the main inventory -- and a display copy is held in the hands), held out to the left, let go, falls and
 * vanishes with a thud on the floor. Then the shoulders roll.
 *
 * <h2>{@link #KIND_SLEEK} -- Marks 2-7: the plates retract</h2>
 * Arms relaxed a little out from the body, chin up; the faceplate lifts, the suit powers down and every plate retracts
 * panel by panel toward the arc reactor ({@link #vanishAt}: boots first, then the legs, the helmet, the arms, and the
 * torso last of all into the reactor). Each piece leaves its slot the tick its last panel is gone ({@link Schedule#offAt}).
 *
 * <p>Pose / style ids 70-79 (the Iron Man suits range): {@link IronManSuitFx#POSE_MK1_OFF}, {@link IronManSuitFx#POSE_SLEEK_OFF}.
 * The running kind rides on {@code TonyStarkState#transitionManual} as {@link #CODE_MK1} / {@link #CODE_SLEEK} (the hand
 * builds use 1 / 2), so the hand build's death / logout / dimension-change hooks cover it too.
 */
public final class IronManSuitRemoval {
	public static final int KIND_MK1 = 0;
	public static final int KIND_SLEEK = 1;
	/** {@code TonyStarkState#transitionManual} while a removal runs (the hand-built suit-ups use 1 and 2). */
	public static final int CODE_MK1 = 3;
	public static final int CODE_SLEEK = 4;

	// events of their own (the hand build's EV_STRIKE / EV_STRAP / EV_SERVO / EV_SETTLE are reused as they are)
	public static final int EV_FACEPLATE_UP = 30;
	public static final int EV_PULL = 31;
	public static final int EV_DROP = 32;
	public static final int EV_LAND = 33;
	public static final int EV_GONE = 34;
	public static final int EV_POWER_DOWN = 35;
	public static final int EV_RETRACT = 36;

	/** A dropped Mark 1 plate falls for this long (ticks) before it lands; it shrinks away over {@link #VANISH_TICKS}. */
	public static final int FALL_TICKS = 9;
	public static final int VANISH_TICKS = 4;
	/** The Mark 1 plate's fall: blocks per tick squared (from the hand to the floor in {@link #FALL_TICKS}). */
	public static final float FALL_GRAVITY = 0.012f;

	/** Marks 2-7: the retract wave runs over these ticks of the sequence. */
	public static final int RETRACT_FROM = 16;
	public static final int RETRACT_TICKS = 70;

	/** Retract regions: helmet, torso, arms (the chestplate's), legs, boots. */
	public static final int R_HELMET = 0, R_TORSO = 1, R_ARMS = 2, R_LEGS = 3, R_BOOTS = 4;
	/** Each region's share of the wave {from, to} (0 = the first to go): far from the reactor first, the torso last. */
	private static final float[] V_LO = { 0.22f, 0.66f, 0.42f, 0.14f, 0.0f };
	private static final float[] V_HI = { 0.50f, 1.00f, 0.72f, 0.42f, 0.20f };
	/** Distance (model pixels) from the reactor at which a region's texels are nearest / furthest. */
	private static final float[] D_MIN = { 4f, 0f, 3f, 8f, 14f };
	private static final float[] D_MAX = { 14f, 9.5f, 12f, 20.5f, 21f };
	/** The arc reactor, in the skin rig's rest coordinates (model pixels, feet at y 0). */
	public static final float REACTOR_X = 0f, REACTOR_Y = 20f, REACTOR_Z = 0f;

	private static final Schedule[][] CACHE = new Schedule[2][16];

	private IronManSuitRemoval() {
	}

	// ======================================================================== which marks

	/** Which removal plain C plays for {@code suitId}: {@link #KIND_MK1}, {@link #KIND_SLEEK} or -1 (keeps its own). */
	public static int kindFor(String suitId) {
		if (suitId == null) {
			return -1;
		}
		return switch (suitId) {
			case "mark_1" -> KIND_MK1;
			case "mark_2", "mark_iii", "mark_4", "mark_6", "mark_vii" -> KIND_SLEEK;
			default -> -1;
		};
	}

	public static int style(int kind) {
		return kind == KIND_MK1 ? IronManSuitFx.STYLE_MK1_OFF : IronManSuitFx.STYLE_SLEEK_OFF;
	}

	public static int poseKind(int kind) {
		return kind == KIND_MK1 ? IronManSuitFx.POSE_MK1_OFF : IronManSuitFx.POSE_SLEEK_OFF;
	}

	/** The removal kind a synced pose stands for, or -1. */
	public static int kindOfPose(int pose) {
		return pose == IronManSuitFx.POSE_MK1_OFF ? KIND_MK1 : pose == IronManSuitFx.POSE_SLEEK_OFF ? KIND_SLEEK : -1;
	}

	// ======================================================================== the retract wave (Marks 2-7)

	/** The retract region of a texel of piece {@code bit} on bone {@code bone}. */
	public static int region(int bit, String bone) {
		return switch (bit) {
			case 0 -> R_HELMET;
			case 1 -> bone != null && (bone.contains("gauntlet") || bone.contains("upper_arm") || bone.contains("shoulder")
					|| bone.contains("blade")) ? R_ARMS : R_TORSO;
			case 2 -> R_LEGS;
			default -> R_BOOTS;
		};
	}

	/**
	 * Where in the wave (0..1 of {@link #RETRACT_TICKS}) a texel of {@code region} at rest position {@code x, y, z} (model
	 * pixels) is gone: inside its region's share, the panels furthest from the reactor first; {@code jitter} (0..1, one
	 * value per 2x2 panel) staggers neighbouring panels so they fold away one after another.
	 */
	public static float vanishAt(int region, float x, float y, float z, float jitter) {
		int r = Math.max(0, Math.min(V_LO.length - 1, region));
		float dx = x - REACTOR_X;
		float dy = y - REACTOR_Y;
		float dz = z - REACTOR_Z;
		float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		float far = IronManManualSuitUp.clamp((d - D_MIN[r]) / (D_MAX[r] - D_MIN[r]));
		float order = IronManManualSuitUp.clamp(0.82f * far + 0.18f * IronManManualSuitUp.clamp(jitter));
		return V_LO[r] + (V_HI[r] - V_LO[r]) * (1f - order);
	}

	/** How far through the retract wave the sequence is {@code age} ticks in (0..1). */
	public static float wave(float age) {
		return IronManManualSuitUp.clamp((age - RETRACT_FROM) / RETRACT_TICKS);
	}

	/** Has every texel of {@code region} retracted {@code age} ticks into a Marks 2-7 removal? */
	public static boolean regionGone(int region, float age) {
		return age >= RETRACT_FROM + V_HI[region] * RETRACT_TICKS;
	}

	/** The tick the last panel of piece {@code bit} is gone (it then leaves its slot). */
	static int sleekOffTick(int bit) {
		float hi = switch (bit) {
			case 0 -> V_HI[R_HELMET];
			case 1 -> Math.max(V_HI[R_TORSO], V_HI[R_ARMS]);
			case 2 -> V_HI[R_LEGS];
			default -> V_HI[R_BOOTS];
		};
		return RETRACT_FROM + (int) Math.ceil(hi * RETRACT_TICKS) + 1;
	}

	// ======================================================================== the schedule

	/** The whole planned removal for one kind and one set of worn pieces, in ticks (0 = the C press). */
	public static final class Schedule {
		final int kind;
		final int plan;
		final int total;
		final int[] offAt = { -1, -1, -1, -1 };
		final int[] dropAt = { -1, -1, -1, -1 };
		final List<Key> keys = new ArrayList<>();
		final List<Event> events = new ArrayList<>();

		Schedule(int kind, int plan) {
			this.kind = kind;
			this.plan = plan;
			int off = 0;
			for (Segment seg : segments(kind, plan)) {
				for (Key k : seg.keys) {
					int t = k.tick() + off;
					if (!keys.isEmpty() && t <= keys.get(keys.size() - 1).tick()) {
						continue;
					}
					keys.add(new Key(t, k.v(), k.props()));
				}
				for (Event e : seg.events) {
					events.add(e.shifted(off));
				}
				off += seg.length;
			}
			total = off;
			if (kind == KIND_SLEEK) {
				for (int bit = 0; bit < 4; bit++) {
					if ((plan & (1 << bit)) != 0) {
						offAt[bit] = sleekOffTick(bit);
						events.add(new Event(offAt[bit], EV_GONE, bit, 0f, (float) IronManSuitUpManager.slotHeight(SLOTS[bit]), 0.1f));
					}
				}
			}
			for (Event e : events) {
				if (e.bit() >= 0 && e.bit() < 4) {
					if (e.type() == EV_PULL) {
						offAt[e.bit()] = e.tick();
					} else if (e.type() == EV_DROP) {
						dropAt[e.bit()] = e.tick();
					}
				}
			}
			events.sort((a, b) -> Integer.compare(a.tick(), b.tick()));
		}

		public int kind() {
			return kind;
		}

		public int total() {
			return total;
		}

		public int plan() {
			return plan;
		}

		/** The tick piece {@code bit} leaves its slot (-1 if it is not in the plan). */
		public int offAt(int bit) {
			return bit >= 0 && bit < 4 ? offAt[bit] : -1;
		}

		/** Mark 1: the tick the pulled-off piece {@code bit} is let go of (-1 for none). */
		public int dropAt(int bit) {
			return bit >= 0 && bit < 4 ? dropAt[bit] : -1;
		}

		public List<Event> events() {
			return Collections.unmodifiableList(events);
		}

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

		/** The body pose {@code age} ticks in, as {weight, key...} (the hand build's layout), or null outside it. */
		public float[] pose(float age) {
			if (age < 0f || age > total || keys.isEmpty()) {
				return null;
			}
			float w = Math.min(IronManManualSuitUp.smooth(age / 6f), IronManManualSuitUp.smooth((total - age) / 8f));
			float[] key = keyAt(age);
			float[] out = new float[IronManManualSuitUp.SIZE + 1];
			out[0] = w;
			System.arraycopy(key, 0, out, 1, IronManManualSuitUp.SIZE);
			return out;
		}

		public float[] keyAt(float age) {
			Key prev = keys.get(0);
			if (age <= prev.tick()) {
				return prev.v();
			}
			for (int i = 1; i < keys.size(); i++) {
				Key next = keys.get(i);
				if (age < next.tick()) {
					float u = (age - prev.tick()) / (float) (next.tick() - prev.tick());
					return IronManManualSuitUp.lerp(prev.v(), next.v(), IronManManualSuitUp.smooth(u));
				}
				prev = next;
			}
			return prev.v();
		}

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

		/** Mark 1: the piece held in the hands {@code age} ticks in (pulled off, not yet let go), or -1. */
		public int pieceInHand(float age) {
			for (int bit = 0; bit < 4; bit++) {
				if (offAt[bit] >= 0 && dropAt[bit] > offAt[bit] && age >= offAt[bit] && age < dropAt[bit]) {
					return bit;
				}
			}
			return -1;
		}
	}

	/** The removal for {@code kind} and the worn pieces {@code plan} (bit 0 HEAD .. 3 FEET). */
	public static Schedule schedule(int kind, int plan) {
		int k = kind == KIND_MK1 ? 0 : 1;
		Schedule s = CACHE[k][plan & 15];
		if (s == null) {
			s = new Schedule(k, plan & 15);
			CACHE[k][plan & 15] = s;
		}
		return s;
	}

	// ======================================================================== the choreography

	private static final int H = IronManManualSuitUp.PROP_HAMMER;
	private static final int HP = IronManManualSuitUp.PROP_HAMMER | IronManManualSuitUp.PROP_PIECE;
	private static final int EV_STRIKE = IronManManualSuitUp.EV_STRIKE;

	/** Holding a pulled-off plate out to the left, ready to let it go; the head turned to watch. */
	private static final float[] DROP_LEFT = k(-0.3f, 0f, 0.15f, -0.95f, -0.25f, -0.95f, 0f, 0f, 0.05f, 0f, 0f, -0.05f,
			0.25f, 0.35f, 0f, 0f);
	/** The same, bent over a little (the greaves and boots come off low). */
	private static final float[] DROP_LEFT_LOW = k(-0.3f, 0f, 0.15f, -0.55f, -0.2f, -0.9f, 0f, 0f, 0.05f, 0f, 0f, -0.05f,
			0.4f, 0.35f, 0.2f, 0f);
	/** Marks 2-7: relaxed, arms a little out from the body, chin up. */
	private static final float[] RELAX = k(-0.12f, 0f, 0.42f, -0.12f, 0f, -0.42f, 0f, 0f, 0.09f, 0f, 0f, -0.09f, -0.12f, 0f,
			-0.03f, 0f);
	private static final float[] SHOULDERS = k(0.1f, 0f, 0.45f, 0.1f, 0f, -0.45f, 0f, 0f, 0.05f, 0f, 0f, -0.05f, -0.1f, 0f, 0f, 0f);

	static List<Segment> segments(int kind, int plan) {
		List<Segment> out = new ArrayList<>();
		if (kind == KIND_SLEEK) {
			out.add(sleek());
			return out;
		}
		out.add(new Segment(-1, 6).key(0, STAND, H));
		if ((plan & 1) != 0) {
			out.add(mk1Helmet());
		}
		if ((plan & 2) != 0) {
			out.add(mk1Chest());
		}
		if ((plan & 4) != 0) {
			out.add(mk1Greaves());
		}
		if ((plan & 8) != 0) {
			out.add(mk1Boots());
		}
		Segment outro = new Segment(-1, 22);
		outro.key(0, STAND, H);
		outro.key(10, SHOULDERS, H);
		outro.event(10, IronManManualSuitUp.EV_SETTLE, 0f, 1.3f, 0f);
		outro.key(20, STAND, H);
		out.add(outro);
		return out;
	}

	/** Let go of the plate held out to the left at {@code t}: it falls and lands {@link #FALL_TICKS} later. */
	private static void drop(Segment s, int t, float[] pose, float up) {
		s.key(t, pose, H);
		s.event(t, EV_DROP, -0.45f, up, 0.25f);
		s.event(t + FALL_TICKS, EV_LAND, -0.5f, 0.05f, 0.3f);
	}

	private static Segment mk1Helmet() {
		Segment s = new Segment(0, 72);
		s.key(0, STAND, H);
		// the left hand pushes the faceplate up
		s.key(8, k(-0.4f, 0f, 0.15f, -2.8f, 0.45f, 0.1f, 0f, 0f, 0.05f, 0f, 0f, -0.05f, 0.1f, 0f, 0f, 0f), H);
		s.event(9, EV_FACEPLATE_UP, 0f, 1.6f, 0.2f);
		// two knocks at the side of the helmet, the left hand steadying it on top
		float[] steady = with(k(-2.6f, 0.2f, 0.9f, -2.7f, 0.55f, 0f, 0f, 0f, 0.05f, 0f, 0f, -0.05f, 0.1f, 0f, 0f, 0f), TILT, 2.0f);
		s.key(16, steady, H);
		s.strikes(26, 2, 10, steady, a(-3.0f, -0.2f, 0.5f, 2.0f), new float[][] { a(-2.75f, -0.5f, 0.3f, 2.4f) },
				new float[][] { { 0.2f, 1.85f, 0.1f } }, H);
		// both hands on it, wrench it off and lift it clear
		s.key(42, ONTO_HEAD, H);
		s.key(46, with(ONTO_HEAD, RAX, -2.55f, LAX, -2.55f), H);
		s.event(46, EV_PULL, 0f, 1.75f, 0f);
		s.key(47, with(ONTO_HEAD, RAX, -2.55f, LAX, -2.55f), HP);
		s.key(54, HELMET_UP, HP);
		s.key(60, DROP_LEFT, HP);
		drop(s, 61, DROP_LEFT, 1.25f);
		s.key(71, STAND, H);
		return s;
	}

	private static Segment mk1Chest() {
		Segment s = new Segment(1, 102);
		s.key(0, STAND, H);
		// the left arm held out, the right knocking the bracer bolts loose
		float[] armOut = with(k(-1.0f, -0.4f, 0.2f, -1.45f, 0.35f, 0f, 0f, 0f, 0.05f, 0f, 0f, -0.05f, 0.45f, 0f, 0f, 0f), TILT, 2.6f);
		s.key(8, armOut, H);
		s.strikes(18, 2, 11, armOut, a(-3.0f, -0.35f, 0.2f, 2.6f), new float[][] { a(-2.6f, -0.5f, 0.05f, 3.69f) },
				new float[][] { { -0.15f, 1.35f, 0.5f } }, H);
		// the chest rivets
		float[] holdPlate = with(k(-1.0f, -0.4f, 0.2f, -1.2f, 0.6f, 0f, 0f, 0f, 0.05f, 0f, 0f, -0.05f, 0.55f, 0f, 0.05f, 0f), TILT, 3.4f);
		s.key(36, holdPlate, H);
		s.strikes(46, 2, 11, holdPlate, CHEST_RAISED, new float[][] { CHEST_HIT, a(-1.4f, -0.65f, 0.05f, 4.05f) },
				new float[][] { { 0.05f, 1.2f, 0.2f }, { -0.05f, 1.15f, 0.2f } }, H);
		// grab both sides and haul it up over the head
		s.key(64, HUG, H);
		s.key(68, with(HUG, RAX, -1.15f, LAX, -1.15f), H);
		s.event(68, EV_PULL, 0f, 1.2f, 0.2f);
		s.key(69, with(HUG, RAX, -1.15f, LAX, -1.15f), HP);
		s.key(78, OVERHEAD, HP);
		s.key(86, DROP_LEFT, HP);
		drop(s, 88, DROP_LEFT, 1.2f);
		s.key(100, STAND, H);
		return s;
	}

	private static Segment mk1Greaves() {
		Segment s = new Segment(2, 82);
		s.key(0, STAND, H);
		// two knocks at the knee bolts
		float[] knees = k(-0.6f, -0.1f, 0.1f, -0.6f, 0.2f, 0f, 0f, 0f, 0.05f, 0f, 0f, -0.05f, 0.7f, 0f, 0.3f, 0f);
		s.key(8, knees, H);
		s.strikes(18, 2, 11, knees, a(-1.6f, -0.1f, 0.2f, 1.8f),
				new float[][] { a(-0.25f, -0.05f, 0f, 2.39f), a(-0.25f, -0.3f, -0.05f, 2.39f) },
				new float[][] { { 0.12f, 0.55f, 0.18f }, { -0.12f, 0.55f, 0.18f } }, H);
		// loosen the straps at the hips
		float[] hips = k(-0.25f, 0f, 0.35f, -0.25f, 0f, -0.35f, 0f, 0f, 0.05f, 0f, 0f, -0.05f, 0.5f, 0f, 0.15f, 0f);
		float[] tug = k(-0.05f, 0f, 0.22f, -0.05f, 0f, -0.22f, 0f, 0f, 0.05f, 0f, 0f, -0.05f, 0.45f, 0f, 0.1f, 0f);
		s.key(36, hips, H);
		s.key(41, tug, H);
		s.event(41, IronManManualSuitUp.EV_STRAP, 0f, 0.95f, 0.05f);
		// bend down and pull them off
		s.key(49, bendPick(0.95f), H);
		s.event(53, EV_PULL, 0f, 0.5f, 0.2f);
		s.key(53, bendPick(0.95f), HP);
		s.key(61, bendPick(0.6f), HP);
		s.key(67, DROP_LEFT_LOW, HP);
		drop(s, 69, DROP_LEFT_LOW, 0.85f);
		s.key(80, STAND, H);
		return s;
	}

	private static Segment mk1Boots() {
		Segment s = new Segment(3, 84);
		s.key(0, STAND, H);
		// bent double, the hammer knocking the ankle clamps open
		float[] ankle = with(k(0.45f, -0.1f, 0.05f, 0.35f, 0.1f, -0.05f, 0f, 0f, 0.05f, 0f, 0f, -0.05f, 0.8f, 0f, 0.95f, 0f),
				TILT, 1.6f);
		s.key(10, ankle, H);
		s.strikes(20, 2, 11, ankle, a(0.0f, -0.1f, 0.05f, 1.2f), new float[][] { a(0.5f, -0.1f, 0.05f, 1.7f) },
				new float[][] { { 0.12f, 0.15f, 0.15f }, { -0.12f, 0.15f, 0.15f } }, H);
		// step out of them: arms out for balance, right foot then left
		float[] balance = k(-0.1f, 0f, 0.45f, -0.1f, 0f, -0.45f, 0f, 0f, 0.05f, 0f, 0f, -0.05f, 0.6f, 0f, 0.1f, 0f);
		s.key(38, balance, H);
		s.key(43, with(balance, RLX, -0.55f), H);
		s.event(43, IronManManualSuitUp.EV_CLAMP, 0.12f, 0.1f, 0.05f);
		s.key(48, balance, H);
		s.key(53, with(balance, LLX, -0.55f), H);
		s.event(53, IronManManualSuitUp.EV_CLAMP, -0.12f, 0.1f, 0.05f);
		s.key(58, balance, H);
		// pick them up and toss them aside
		s.key(64, bendPick(0.95f), H);
		s.event(66, EV_PULL, 0f, 0.15f, 0.2f);
		s.key(66, bendPick(0.95f), HP);
		s.key(72, DROP_LEFT_LOW, HP);
		drop(s, 73, DROP_LEFT_LOW, 0.85f);
		s.key(82, STAND, H);
		return s;
	}

	private static Segment sleek() {
		int end = RETRACT_FROM + RETRACT_TICKS;
		Segment s = new Segment(-1, end + 28);
		s.key(0, STAND, 0);
		s.event(5, EV_FACEPLATE_UP, 0f, 1.6f, 0.2f);
		s.key(10, RELAX, 0);
		s.event(12, EV_POWER_DOWN, 0f, 1.2f, 0.25f);
		// slow breaths while the plates fold away
		float[] in = with(RELAX, RAZ, 0.48f, LAZ, -0.48f);
		in = with(in, HX, -0.17f);
		for (int t = 28; t < end; t += 24) {
			s.key(t, in, 0);
			s.key(t + 12, RELAX, 0);
		}
		for (int t = RETRACT_FROM + 2; t < end; t += 9) {
			s.event(t, EV_RETRACT, 0f, 1.0f, 0.1f);
		}
		// arms down, a look at the bare hands, the shoulders roll
		s.key(end + 6, k(0f, 0f, 0.1f, 0f, 0f, -0.1f, 0f, 0f, 0.03f, 0f, 0f, -0.03f, 0.3f, 0f, 0f, 0f), 0);
		s.key(end + 16, SHOULDERS, 0);
		s.event(end + 16, IronManManualSuitUp.EV_SETTLE, 0f, 1.3f, 0f);
		s.key(end + 26, STAND, 0);
		return s;
	}

	// ======================================================================== the server sequence

	private static final EquipmentSlot[] SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

	/** Is {@code player} taking a suit off this way right now? */
	public static boolean running(ServerPlayer player) {
		TonyStarkState s = TonyStark.state(player);
		return !s.transitionSuit.isEmpty() && (s.transitionManual == CODE_MK1 || s.transitionManual == CODE_SLEEK);
	}

	/**
	 * Start taking {@code suit} off: the worn pieces in {@code mask} stay on the body until each one's tick, the player is
	 * held still, flight is cut, the synced pose / style start. Called by {@code IronManSuitUpManager#beginSuitDown} once
	 * its checks (room in the pack) have passed.
	 */
	static void start(ServerPlayer player, IronManSuit suit, int kind, int mask) {
		TonyStarkState s = TonyStark.state(player);
		Schedule sch = schedule(kind, mask);
		s.transitionSuit = suit.id();
		s.transitionUp = false;
		s.transitionToCase = false;
		s.transitionFromCase = false;
		s.transitionBracelet = false;
		s.transitionManual = kind == KIND_MK1 ? CODE_MK1 : CODE_SLEEK;
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
		player.setAttached(ModAttachments.IRON_MAN_SUIT_FX, new IronManSuitFx(0L, 0L, 0L, 0L, 0, style(kind),
				player.level().getGameTime(), sch.total(), poseKind(kind), fx.faceplateAt(),
				IronManManualSuitUp.variant(suit.id(), mask)));
		IronManSounds.play(player, IronManSounds.SERVO, 0.8f, kind == KIND_MK1 ? 0.7f : 0.85f);
	}

	/** One server tick ({@code IronManManualSuitUp#tick} hands it over while a removal runs). */
	static void tick(ServerPlayer player, TonyStarkState s) {
		if (!player.isAlive()) {
			abort(player);
			return;
		}
		int kind = s.transitionManual == CODE_MK1 ? KIND_MK1 : KIND_SLEEK;
		Schedule sch = schedule(kind, s.transitionPlan);
		s.transitionTicks--;
		int elapsed = s.transitionTotal - s.transitionTicks;
		IronManSuitPlatformBlockEntity.setFrozen(player, true);
		if (IronManFlight.isFlying(player)) {
			IronManFlight.setFlying(player, false);
		}
		for (int bit = 0; bit < 4; bit++) {
			if ((s.transitionMask & (1 << bit)) != 0 && sch.offAt(bit) >= 0 && elapsed >= sch.offAt(bit)) {
				s.transitionMask &= ~(1 << bit);
				takeOff(player, s, bit);
			}
		}
		for (Event e : sch.eventsAt(elapsed)) {
			fire(player, s, kind, e);
		}
		if (s.transitionTicks <= 0) {
			finish(player, s);
		}
	}

	/** Piece {@code bit} off the body and into the main inventory (charge stamped on) -- the real stack, in one tick. */
	private static void takeOff(ServerPlayer player, TonyStarkState s, int bit) {
		EquipmentSlot slot = SLOTS[bit];
		if (!IronManArmor.isPieceWorn(player, slot, s.transitionSuit)) {
			return;
		}
		if (bit == 0) {
			player.setAttached(ModAttachments.IRON_MAN_FACEPLATE_OPEN, false);
		}
		IronManSuitUpManager.removeForSuitDown(player, s, slot);
	}

	private static void fire(ServerPlayer player, TonyStarkState s, int kind, Event e) {
		ServerLevel level = player.serverLevel();
		Vec3 at = IronManManualSuitUp.where(player, e);
		boolean mk1 = kind == KIND_MK1;
		boolean pieceOn = e.bit() < 0 || IronManArmor.isPieceWorn(player, SLOTS[e.bit()], s.transitionSuit);
		switch (e.type()) {
			case EV_FACEPLATE_UP -> {
				if (IronManArmor.isPieceWorn(player, EquipmentSlot.HEAD, s.transitionSuit)
						&& !player.getAttachedOrElse(ModAttachments.IRON_MAN_FACEPLATE_OPEN, false)) {
					player.setAttached(ModAttachments.IRON_MAN_FACEPLATE_OPEN, true);
					IronManSuitFx.faceplateMoved(player);
					IronManSounds.play(player, IronManSounds.FACEPLATE_OPEN, 0.8f, mk1 ? 0.8f : 1.0f);
				}
			}
			case EV_POWER_DOWN -> {
				IronManSounds.play(player, IronManSounds.HUD_OFF, 0.7f, 0.9f);
				IronManSounds.playAt(level, at.x, at.y, at.z, IronManSounds.SERVO, 0.6f, 0.6f);
			}
			case EV_RETRACT -> {
				// a soft ripple of panel clicks while the plates fold away
				IronManManualSuitUp.playAt(player, SoundEvents.LEVER_CLICK, e, 0.18f, 1.7f + level.random.nextFloat() * 0.3f);
				IronManSounds.playAt(level, at.x, at.y, at.z, IronManSounds.CLAMP, 0.18f, 1.6f + level.random.nextFloat() * 0.2f);
			}
			case EV_GONE -> {
				IronManSounds.playAt(level, at.x, at.y, at.z, IronManSounds.RELEASE, 0.6f, 1.25f + e.bit() * 0.05f);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 6, 0.2, 0.12, 0.2, 0.05);
			}
			case EV_PULL -> {
				IronManManualSuitUp.playAt(player, SoundEvents.IRON_TRAPDOOR_OPEN, e, 0.6f, 0.75f);
				IronManManualSuitUp.playAt(player, SoundEvents.ARMOR_EQUIP_IRON.value(), e, 0.8f, 0.7f);
				level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 4, 0.15, 0.1, 0.15, 0.01);
			}
			case EV_DROP -> IronManManualSuitUp.playAt(player, SoundEvents.ARMOR_EQUIP_GENERIC.value(), e, 0.5f, 0.6f);
			case EV_LAND -> {
				// the plate hits the floor with a clank and is gone
				IronManManualSuitUp.playAt(player, SoundEvents.ANVIL_LAND, e, 0.22f, 1.35f);
				IronManSounds.playAt(level, at.x, at.y, at.z, IronManSounds.MK1_CLUNK, 0.6f, 0.9f);
				level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.1, at.z, 5, 0.12, 0.04, 0.12, 0.02);
			}
			case IronManManualSuitUp.EV_STRIKE -> {
				if (!pieceOn) {
					return;
				}
				// a bolt knocked loose: metal-on-metal, the anvil ring, sparks and a puff of grit
				float pitch = 0.95f + level.random.nextFloat() * 0.25f;
				IronManManualSuitUp.playAt(player, SoundEvents.ANVIL_USE, e, 0.45f, pitch);
				IronManManualSuitUp.playAt(player, SoundEvents.CHAIN_BREAK, e, 0.45f, 0.8f);
				level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 7, 0.06, 0.06, 0.06, 0.25);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 5, 0.05, 0.05, 0.05, 0.15);
				level.sendParticles(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, 1, 0.03, 0.03, 0.03, 0.01);
			}
			case IronManManualSuitUp.EV_STRAP -> IronManManualSuitUp.playAt(player, SoundEvents.ARMOR_EQUIP_LEATHER.value(), e, 0.7f, 0.8f);
			case IronManManualSuitUp.EV_CLAMP -> {
				IronManSounds.playAt(level, at.x, at.y, at.z, IronManSounds.RELEASE, 0.6f, 0.8f);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 3, 0.08, 0.05, 0.08, 0.05);
			}
			case IronManManualSuitUp.EV_SETTLE -> IronManManualSuitUp.playAt(player, SoundEvents.ARMOR_EQUIP_GENERIC.value(), e, 0.5f, 1.1f);
			default -> {
			}
		}
	}

	/** The last tick: anything still worn of the plan comes off, the player can move again. */
	static void finish(ServerPlayer player, TonyStarkState s) {
		for (int bit = 0; bit < 4; bit++) {
			if ((s.transitionMask & (1 << bit)) != 0) {
				takeOff(player, s, bit);
			}
		}
		String suitId = s.transitionSuit;
		clear(player, s);
		IronManSuitFx.endPose(player);
		IronManSounds.play(player, IronManSounds.SUIT_STORED, 0.8f, IronManSounds.markPitch(suitId));
		if (!IronManArmor.wearingAnyIronMan(player)) {
			TonyStark.setActiveSuit(player, "");
		}
	}

	/** Death: stop where it is -- every piece is on the body (drops with the armour) or already in the pack. */
	static void abort(ServerPlayer player) {
		TonyStarkState s = TonyStark.state(player);
		clear(player, s);
		IronManSuitFx fx = IronManSuitFx.of(player);
		player.setAttached(ModAttachments.IRON_MAN_SUIT_FX, IronManSuitFx.EMPTY.withFaceplate(fx.faceplateAt()));
	}

	/** Logout / server stop / dimension change: finish at once -- every still-worn planned piece into the pack. */
	static void completeNow(ServerPlayer player) {
		TonyStarkState s = TonyStark.state(player);
		for (int bit = 0; bit < 4; bit++) {
			if ((s.transitionMask & (1 << bit)) != 0) {
				takeOff(player, s, bit);
			}
		}
		clear(player, s);
		IronManSuitFx fx = IronManSuitFx.of(player);
		player.setAttached(ModAttachments.IRON_MAN_SUIT_FX, IronManSuitFx.EMPTY.withFaceplate(fx.faceplateAt()));
		if (!IronManArmor.wearingAnyIronMan(player)) {
			TonyStark.setActiveSuit(player, "");
		}
	}

	private static void clear(ServerPlayer player, TonyStarkState s) {
		s.transitionManual = 0;
		s.transitionMask = 0;
		s.transitionPlan = 0;
		s.transitionReleaseMask = 0;
		s.transitionTicks = 0;
		s.transitionSuit = "";
		s.transitionUp = true;
		IronManSuitPlatformBlockEntity.setFrozen(player, false);
	}
}
