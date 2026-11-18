package com.projecthero.mod.client.powerarmour;

/**
 * v0.17.5 Power Armour: the moving plates of the voxel suit. Every texel of every layer belongs to the first panel whose region
 * holds its centre ({@link SuitMesh}); the rest is {@link #STATIC} (shoulders, sides, hands, boots). A panel opens about a hinge
 * (pivot + axis + angle) or slides ({@code tx, ty, tz}), scaled by how open its {@link Group} is; {@link #parent} panels ride on
 * their parent. Coordinates are model pixels in the part's own space (vanilla's y-down limb space, the player model's boxes).
 *
 * <p>Round 2: the suit opens on <b>one side</b> -- the side you clicked ({@link #side()}): from the front the chest doors hinge
 * out, the abdomen flap drops, the helmet's front half swings up like a visor and the arm and leg fronts clam open (the leg
 * fronts split down the middle); from the back the back doors split, the helmet's back half swings up and back and the arm and
 * leg backs open. The outer side plates slide out a little either way. The plates are a thin shell inside the player's box
 * (the whole suit is drawn scaled up), so every hinge sits just outside the box edge.
 */
public enum SuitPanel {
	STATIC(Part.BODY, 0, null, 0, 0, 0, 0, 0f, 0f, 0, 0, 0, Group.NONE),
	// ---- body: x -4..4, y 0..12, z -2..2
	CHEST_R(Part.BODY, F.N, new float[] { -9, 0, -9, 0, 6, 9 }, -4.1f, 0, -2.1f, 1, 0f, 105f, 0, 0, 0, Group.CHEST),
	CHEST_L(Part.BODY, F.N, new float[] { 0, 0, -9, 9, 6, 9 }, 4.1f, 0, -2.1f, 1, 0f, -105f, 0, 0, 0, Group.CHEST),
	ABDOMEN(Part.BODY, F.N, new float[] { -9, 6, -9, 9, 13, 9 }, 0, 12.1f, -2.1f, 0, 0f, 70f, 0, 0, 0, Group.CHEST),
	BACK_R(Part.BODY, F.S, new float[] { -9, -1, -9, 0, 13, 9 }, -4.1f, 0, 2.1f, 1, 0f, -110f, 0, 0, 0, Group.BACK),
	BACK_L(Part.BODY, F.S, new float[] { 0, -1, -9, 9, 13, 9 }, 4.1f, 0, 2.1f, 1, 0f, 110f, 0, 0, 0, Group.BACK),
	// ---- head: x -4..4, y -8..0, z -4..4. Back half (HELMET) swings up and back, front half (VISOR) up and forward, both about
	// the crown line (y -8, z 0)
	HELMET(Part.HEAD, F.S | F.W | F.E | F.D | F.U, new float[] { -9, -10, 0, 9, 2, 9 }, 0, -8.1f, 0f, 0, 0f, 100f, 0, 0, 0, Group.HELMET),
	VISOR(Part.HEAD, F.N | F.W | F.E | F.D | F.U, new float[] { -9, -10, -9, 9, 2, 0 }, 0, -8.1f, 0f, 0, 0f, -100f, 0, 0, 0, Group.VISOR),
	// ---- right arm: x -3..1, y -2..10, z -2..2 (outer side = west); the shoulder plate and the hand stay put
	RARM_FRONT(Part.RIGHT_ARM, F.N, new float[] { -9, 0.75f, -9, 9, 7.75f, 9 }, -3.1f, 0, -2.1f, 1, 0f, 82f, 0, 0, 0, Group.ARMS),
	RARM_BACK(Part.RIGHT_ARM, F.S, new float[] { -9, 0.75f, -9, 9, 7.75f, 9 }, -3.1f, 0, 2.1f, 1, 0f, -82f, 0, 0, 0, Group.ARMS),
	RARM_OUTER(Part.RIGHT_ARM, F.W, new float[] { -9, 0.75f, -9, 9, 7.75f, 9 }, 0, 0, 0, 0, 0f, 0f, -0.8f, 0, 0, Group.ARMS),
	// ---- left arm: x -1..3, y -2..10, z -2..2 (outer side = east)
	LARM_FRONT(Part.LEFT_ARM, F.N, new float[] { -9, 0.75f, -9, 9, 7.75f, 9 }, 3.1f, 0, -2.1f, 1, 0f, -82f, 0, 0, 0, Group.ARMS),
	LARM_BACK(Part.LEFT_ARM, F.S, new float[] { -9, 0.75f, -9, 9, 7.75f, 9 }, 3.1f, 0, 2.1f, 1, 0f, 82f, 0, 0, 0, Group.ARMS),
	LARM_OUTER(Part.LEFT_ARM, F.E, new float[] { -9, 0.75f, -9, 9, 7.75f, 9 }, 0, 0, 0, 0, 0f, 0f, 0.8f, 0, 0, Group.ARMS),
	// ---- right leg: x -2..2, y 0..12, z -2..2 (outer side = west); the boot (y > 9.5) stays put
	RLEG_FRONT_O(Part.RIGHT_LEG, F.N, new float[] { -9, -1, -9, 0, 9.5f, 9 }, -2.1f, 0, -2.1f, 1, 0f, 86f, 0, 0, 0, Group.LEGS),
	RLEG_FRONT_I(Part.RIGHT_LEG, F.N, new float[] { 0, -1, -9, 9, 9.5f, 9 }, 2.1f, 0, -2.1f, 1, 0f, -86f, 0, 0, 0, Group.LEGS),
	RLEG_BACK(Part.RIGHT_LEG, F.S, new float[] { -9, -1, -9, 9, 9.5f, 9 }, -2.1f, 0, 2.1f, 1, 0f, -96f, 0, 0, 0, Group.LEGS),
	RLEG_OUTER(Part.RIGHT_LEG, F.W, new float[] { -9, -1, -9, 9, 9.5f, 9 }, 0, 0, 0, 0, 0f, 0f, -0.7f, 0, 0, Group.LEGS),
	// ---- left leg (outer side = east)
	LLEG_FRONT_O(Part.LEFT_LEG, F.N, new float[] { 0, -1, -9, 9, 9.5f, 9 }, 2.1f, 0, -2.1f, 1, 0f, -86f, 0, 0, 0, Group.LEGS),
	LLEG_FRONT_I(Part.LEFT_LEG, F.N, new float[] { -9, -1, -9, 0, 9.5f, 9 }, -2.1f, 0, -2.1f, 1, 0f, 86f, 0, 0, 0, Group.LEGS),
	LLEG_BACK(Part.LEFT_LEG, F.S, new float[] { -9, -1, -9, 9, 9.5f, 9 }, 2.1f, 0, 2.1f, 1, 0f, 96f, 0, 0, 0, Group.LEGS),
	LLEG_OUTER(Part.LEFT_LEG, F.E, new float[] { -9, -1, -9, 9, 9.5f, 9 }, 0, 0, 0, 0, 0f, 0f, 0.7f, 0, 0, Group.LEGS);

	/** Face bits: north (front), south (back), west (-x), east (+x), down (top, min y), up (bottom, max y). */
	public static final class F {
		public static final int N = 1, S = 2, W = 4, E = 8, D = 16, U = 32;

		private F() {
		}
	}

	/** Which side a panel opens on: {@link com.projecthero.mod.powerarmour.PowerArmourState#SIDE_FRONT}, SIDE_BACK, or both. */
	public static final int FRONT = 0, BACK = 1, BOTH = 2;

	/** The six posed parts the suit rides on (the player model's). */
	public enum Part {
		HEAD(-4, -8, -4, 8, 8, 8, 0, 0),
		BODY(-4, 0, -2, 8, 12, 4, 16, 16),
		RIGHT_ARM(-3, -2, -2, 4, 12, 4, 40, 16),
		LEFT_ARM(-1, -2, -2, 4, 12, 4, 32, 48),
		RIGHT_LEG(-2, 0, -2, 4, 12, 4, 0, 16),
		LEFT_LEG(-2, 0, -2, 4, 12, 4, 16, 48);

		/** Box origin and size (px) and its UV origin on a 64x64 skin layout (the suit textures are the same layout at 128). */
		public final float x0, y0, z0, w, h, d;
		public final int u, v;

		Part(float x0, float y0, float z0, float w, float h, float d, int u, int v) {
			this.x0 = x0;
			this.y0 = y0;
			this.z0 = z0;
			this.w = w;
			this.h = h;
			this.d = d;
			this.u = u;
			this.v = v;
		}

		public float cx() {
			return x0 + w / 2f;
		}

		public float cy() {
			return y0 + h / 2f;
		}

		public float cz() {
			return z0 + d / 2f;
		}
	}

	/** Which step of the sequence moves a panel. */
	public enum Group {
		NONE, LEGS, ARMS, BACK, CHEST, HELMET, VISOR
	}

	public final Part part;
	public final int faces;
	/** x0, y0, z0, x1, y1, z1 the texel centre must fall in (null: anything, for STATIC). */
	public final float[] region;
	public final float px, py, pz;
	/** 0 = x axis, 1 = y axis. */
	public final int axis;
	public final float angle;
	public final float tx, ty, tz;
	public final Group group;

	SuitPanel(Part part, int faces, float[] region, float px, float py, float pz, int axis, float unused, float angle, float tx,
			float ty, float tz, Group group) {
		this.part = part;
		this.faces = faces;
		this.region = region;
		this.px = px;
		this.py = py;
		this.pz = pz;
		this.axis = axis;
		this.angle = angle;
		this.tx = tx;
		this.ty = ty;
		this.tz = tz;
		this.group = group;
	}

	public SuitPanel parent() {
		return null;
	}

	/** The side this panel opens on ({@link #FRONT}, {@link #BACK} or {@link #BOTH} for the sliding side plates). */
	public int side() {
		return switch (this) {
			case CHEST_R, CHEST_L, ABDOMEN, VISOR, RARM_FRONT, LARM_FRONT, RLEG_FRONT_O, RLEG_FRONT_I, LLEG_FRONT_O, LLEG_FRONT_I -> FRONT;
			case BACK_R, BACK_L, HELMET, RARM_BACK, LARM_BACK, RLEG_BACK, LLEG_BACK -> BACK;
			default -> BOTH;
		};
	}

	/** The panel a texel of {@code part} on face {@code faceBit} centred at (x, y, z) belongs to. */
	public static SuitPanel of(Part part, int faceBit, float x, float y, float z) {
		SuitPanel[] all = values();
		for (int i = all.length - 1; i > 0; i--) {
			SuitPanel p = all[i];
			if (p == STATIC || p.part != part || (p.faces & faceBit) == 0) {
				continue;
			}
			float[] r = p.region;
			if (x >= r[0] && x < r[3] && y >= r[1] && y < r[4] && z >= r[2] && z < r[5]) {
				return p;
			}
		}
		return STATIC;
	}

	/**
	 * Closing windows of each group over the seal (0..1): legs, arms, back, chest, helmet, faceplate. Opening runs the other way
	 * round ({@link #openWindow}).
	 */
	public static float[] closeWindow(Group g) {
		return switch (g) {
			case LEGS -> new float[] { 0.00f, 0.30f };
			case ARMS -> new float[] { 0.18f, 0.48f };
			case BACK -> new float[] { 0.34f, 0.60f };
			case CHEST -> new float[] { 0.46f, 0.72f };
			case HELMET -> new float[] { 0.64f, 0.86f };
			case VISOR -> new float[] { 0.76f, 0.95f };
			default -> new float[] { 0f, 1f };
		};
	}

	public static float[] openWindow(Group g) {
		return switch (g) {
			case VISOR -> new float[] { 0.00f, 0.28f };
			case HELMET -> new float[] { 0.10f, 0.42f };
			case CHEST -> new float[] { 0.18f, 0.55f };
			case BACK -> new float[] { 0.26f, 0.62f };
			case ARMS -> new float[] { 0.40f, 0.82f };
			case LEGS -> new float[] { 0.52f, 1.00f };
			default -> new float[] { 0f, 1f };
		};
	}
}
