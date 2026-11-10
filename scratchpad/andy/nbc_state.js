// v0.17.3 NBC-style regeneration: AndyState gets ten segments and frame-based regrowth.
const fs = require('fs');
const f = 'src/main/java/com/projecthero/mod/andy/data/AndyState.java';
let t = fs.readFileSync(f, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
function rep(a, b) { if (!t.includes(a)) throw new Error('missing: ' + a.slice(0, 90)); t = t.replace(a, b); }

rep(` * <h2>The six parts</h2>
 * Index {@link #HEAD}..{@link #LEFT_LEG}. Each has HP ({@link #partHp}) and three times in {@link #partTimes}: when it broke
 * (0 = whole), when its regrowth started (0 = still waiting; it may be set in the future, as the head-only regrowth plans
 * every part at once) and when the regrowth ends. Clients draw the part hidden from the break until the regrowth starts,
 * then re-forming (bone, then muscle, then skin) until it ends.`,
` * <h2>The six parts and ten segments</h2>
 * Index {@link #HEAD}..{@link #LEFT_LEG} are the six parts with HP ({@link #partHp}). v0.17.3 (NBC Heroes' Regenerative
 * Segmentation): the regrowth runs on ten SEGMENTS -- the six parts (an arm or leg is the limb down to the wrist / ankle)
 * plus {@link #RIGHT_HAND}, {@link #LEFT_HAND}, {@link #RIGHT_FOOT}, {@link #LEFT_FOOT}. Each segment has three times in
 * {@link #partTimes}: when it broke (0 = whole), when its regrowth started (0 = not growing) and when it stops: it grows one
 * frame a tick from the start, so {@code end - start} is the frame it grows to ({@link AndyConfig#FRAMES} = whole, fewer =
 * held at a stage by the Regen Menu). Clients draw the segment hidden from the break until it starts, then re-forming (bone,
 * then flesh, then skin).`);
rep(`	public static final int PARTS = 6;
`, `	public static final int PARTS = 6;
	/** v0.17.3: the hands and feet, segments of their own (no HP: they go with their limb, or on their own in flight and shots). */
	public static final int RIGHT_HAND = 6;
	public static final int LEFT_HAND = 7;
	public static final int RIGHT_FOOT = 8;
	public static final int LEFT_FOOT = 9;
	public static final int SEGS = 10;
`);
rep(`	/** v0.17.3 (tester: "Add the option to choose not to regenerate"): Shift+V's fourth setting, Off -- nothing grows back or heals. */
	public static final int FLAG_REGEN_OFF = 128;`,
`	/**
	 * v0.17.3 Shift+V, Toggle Regen (NBC's Halt): auto-regrowth off -- nothing starts growing back on its own (a segment already
	 * growing finishes); the Regen Menu grows segments a stage at a time.
	 */
	public static final int FLAG_REGEN_OFF = 128;`);
rep(`	public static final int FLAG_NEXT_OFF = 256;
`, `	public static final int FLAG_NEXT_OFF = 256;
	/** v0.17.3: the Regen Menu is open (the ability keys grow / strip segments instead of the moves). */
	public static final int FLAG_REGEN_MENU = 512;
`);
rep(`		while (this.partTimes.size() < PARTS * 3) {`, `		while (this.partTimes.size() < SEGS * 3) {`);
rep(`		this.regenSpeed = Math.floorMod(regenSpeed, AndyConfig.SPEEDS);`, `		this.regenSpeed = regenSpeed; // v0.17.3: unused (one speed now); kept so old saves load`);
rep(`		for (int i = 0; i < PARTS * 3; i++) {
			l.add(0L);`, `		for (int i = 0; i < SEGS * 3; i++) {
			l.add(0L);`);
rep(`	/** 0 while the part is gone, rising to 1 as it re-forms; 1 when whole. */
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
		for (int i = 0; i < PARTS; i++) {`,
`	/**
	 * The frame a segment is at (0 = gone .. {@link AndyConfig#FRAMES} = whole): one a tick from its start, up to the frame it
	 * grows to ({@code end - start}). Fractional between ticks (the renderers pass the partial tick).
	 */
	public float frame(int seg, float now) {
		if (!broken(seg)) {
			return AndyConfig.FRAMES[seg];
		}
		long start = regrowStart(seg);
		if (start <= 0L || now < start) {
			return 0f;
		}
		return Math.min(now - start, (float) (regrowEnd(seg) - start));
	}

	/** The frame a growing segment stops at ({@code end - start}; 0 when it is not growing). */
	public int targetFrame(int seg) {
		long start = regrowStart(seg);
		return !broken(seg) ? AndyConfig.FRAMES[seg] : start <= 0L ? 0 : (int) (regrowEnd(seg) - start);
	}

	/** 0 while the segment is gone, rising to 1 as it re-forms (frame / its full frames); 1 when whole. */
	public float regrowth(int part, float now) {
		return broken(part) ? Math.min(1f, frame(part, now) / AndyConfig.FRAMES[part]) : 1f;
	}

	/** Bone, flesh or skin: the stage a segment is in at {@code now} ({@link #STAGE_GONE} at frame 0). */
	public int stage(int seg, float now) {
		float f = frame(seg, now);
		if (f >= AndyConfig.FRAMES[seg]) {
			return STAGE_SKIN;
		}
		return f <= 0f ? STAGE_GONE : f < AndyConfig.FLESH_FRAME[seg] ? STAGE_BONE : STAGE_FLESH;
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
		for (int i = 0; i < SEGS; i++) {`);
rep(`		return Math.min(1f, (now - start) / AndyConfig.cutRegrowTicks(regenSpeed, c0));`, `		return Math.min(1f, (now - start) / AndyConfig.cutRegrowTicks(c0));`);
fs.writeFileSync(f, crlf ? t.replace(/\n/g, '\r\n') : t);
console.log('ok');
