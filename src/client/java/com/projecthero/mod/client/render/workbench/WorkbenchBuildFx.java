package com.projecthero.mod.client.render.workbench;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;

/**
 * v0.17.2 (user): the Stark Workbench fabricating the EXACT armour piece. The real GeckoLib piece (the item's own worn
 * model, e.g. the Mk III helmet) is drawn on an invisible client armour stand ({@link #drawPiece}) while this class tells
 * {@code SuperheroArmorRenderer} how far each of its faces has got ({@link #quad}): every face (a plate) has its own
 * moment in the build, flies in along its own normal from a little way out, grows to size, overshoots and locks, cyan
 * hologram-tinted until it is home. The order is per piece:
 * <ul>
 *   <li><b>Helmet</b>: the back shell, then the sides, the crown and the chin, then the faceplate flies in from the front
 *       and locks; the eye slits light last (with a flicker).</li>
 *   <li><b>Chestplate</b>: a hologram frame forms first ({@link #cage}); plates layer on from the back to the front (back
 *       panels, then the sides and shoulders, the arms a beat behind, then the front), and the arc reactor glows on
 *       last.</li>
 *   <li><b>Leggings</b>: both legs build from the hips down in a plate cascade (each plate drops in from above), the
 *       left leg a beat behind the right.</li>
 *   <li><b>Boots</b>: the soles form (rising from under), the shells wrap round from the heel to the toe, the tops close,
 *       then a repulsor flare ({@link #flare}).</li>
 * </ul>
 * Pure functions of (piece, progress), so the in-world desk ({@code StarkWorkbenchRenderer}) and the screen preview show
 * the same frame. State is per draw call and render-thread only ({@link #begin} / {@link #end}).
 */
public final class WorkbenchBuildFx {
	private WorkbenchBuildFx() {
	}

	/** The piece being drawn (1 helmet .. 4 boots), 0 = not drawing a Workbench piece. */
	private static int piece;
	/** Build progress 0..1 of the piece being drawn. */
	private static float p = 1f;
	/** Draw as a cyan blueprint (not built, not fabricating). */
	private static boolean ghost;
	/** The bone {@code SuperheroArmorRenderer} is drawing (set per bone; the parent's cubes go before its children). */
	public static String bone = "";
	/** The tint of the last {@link #quad} accepted. */
	public static int colour = 0xFFFFFFFF;
	private static final Matrix4f SCRATCH = new Matrix4f();

	public static boolean active() {
		return piece != 0;
	}

	public static void begin(int pieceIndex, float progress, boolean asGhost) {
		piece = pieceIndex;
		p = Math.max(0f, Math.min(1f, progress));
		ghost = asGhost;
		bone = "";
	}

	public static void end() {
		piece = 0;
		p = 1f;
		ghost = false;
		bone = "";
	}

	// ------------------------------------------------------------------ the per-face choreography

	/** When the eyes / arc reactor / repulsors light, per piece (helmet, chest, legs, boots). */
	public static final float[] LIGHTS_AT = { 0.86f, 0.88f, 2f, 0.84f };

	/**
	 * The pose matrix to draw {@code quad} with right now (its fly-in offset and scale folded in), or null to leave it out
	 * (its moment has not come). Sets {@link #colour} to its tint. {@code glow} = the emissive pass (eyes / reactor /
	 * repulsors), which stays dark until the piece's lights come on.
	 */
	public static Matrix4f quad(GeoQuad quad, Matrix4f pose, int baseColour, boolean glow) {
		if (ghost) {
			colour = glow ? 0 : tint(baseColour, 0f);
			return glow ? null : pose;
		}
		if (glow && p < 1f) {
			float at = LIGHTS_AT[piece - 1];
			if (p < at || (p > at + 0.012f && p < at + 0.03f)) {
				return null; // dark until the lights come on, with one flicker
			}
			float k = Math.min(1f, (p - at) / 0.06f);
			colour = scale(baseColour, 0.35f + 0.65f * k);
			return pose;
		}
		if (p >= 1f) {
			colour = baseColour;
			return pose;
		}
		GeoVertex[] v = quad.vertices();
		float cx = 0f, cy = 0f, cz = 0f;
		for (GeoVertex gv : v) {
			cx += gv.position().x();
			cy += gv.position().y();
			cz += gv.position().z();
		}
		cx /= v.length;
		cy /= v.length;
		cz /= v.length;
		Vector3f n = quad.normal();
		Direction d = quad.direction();
		float jitter = hash(cx, cy, cz);
		float t0;
		float dur = 0.16f;
		float dist;
		float dx = n.x(), dy = n.y(), dz = n.z();
		switch (piece) {
			case 1 -> { // helmet
				if ("faceplate".equals(bone)) {
					t0 = 0.6f + 0.06f * jitter;
					dur = 0.2f;
					dist = 0.75f;
					dx = 0f;
					dy = 0.18f;
					dz = -1f; // out of the front (model -Z), locking onto the face
				} else {
					t0 = switch (d) {
						case SOUTH -> 0.04f;
						case EAST, WEST -> 0.18f;
						case UP -> 0.32f;
						case DOWN -> 0.42f;
						default -> 0.48f;
					} + 0.1f * jitter;
					dist = 0.45f;
					dy += 0.35f;
				}
			}
			case 2 -> { // chestplate: back to front, the arms a beat behind
				boolean arm = bone.contains("arm");
				float layer = switch (d) {
					case SOUTH -> 0f;
					case UP, DOWN -> 0.3f;
					case EAST, WEST -> arm ? 0.45f : 0.35f;
					default -> 0.75f;
				};
				t0 = 0.2f + 0.42f * layer + 0.08f * jitter + (arm ? 0.06f : 0f);
				dist = arm ? 0.55f : 0.45f;
				if (arm) {
					dx += Math.signum(cx) * 0.6f; // the arm plates swing in from the side
				}
			}
			case 3 -> { // leggings: hips down, a plate cascade dropping in from above
				float hips = 0.75f;
				float fromHips = Math.max(0f, Math.min(1f, (hips - cy) / 0.6f));
				boolean left = bone.startsWith("left");
				t0 = 0.04f + 0.6f * fromHips + 0.06f * jitter + (left ? 0.05f : 0f);
				dur = 0.14f;
				dist = 0.42f;
				dx *= 0.35f;
				dz *= 0.35f;
				dy = dy * 0.35f + 1f;
			}
			default -> { // boots: soles, then the shell wrapping heel to toe, then the tops
				if (d == Direction.DOWN) {
					t0 = 0.04f + 0.06f * jitter;
					dist = 0.32f;
					dx = 0f;
					dy = -1f;
					dz = 0f;
				} else if (d == Direction.UP) {
					t0 = 0.62f + 0.06f * jitter;
					dist = 0.3f;
					dy += 0.4f;
				} else {
					// heel (model +Z) first, wrapping round both sides to the toe (model -Z)
					float wrap = d == Direction.SOUTH ? 0f : d == Direction.NORTH ? 1f : 0.5f + 0.5f * Math.max(-1f, Math.min(1f, -cz / 0.16f));
					t0 = 0.18f + 0.36f * wrap + 0.05f * jitter;
					dist = 0.36f;
				}
			}
		}
		float q = (p - t0) / dur;
		if (q <= 0f) {
			return null;
		}
		if (q >= 1f) {
			colour = baseColour;
			return pose;
		}
		float off = 1f - easeOutBack(q); // 1 -> slightly below 0 (the overshoot) -> 0: it lands and locks
		float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len > 1e-4f) {
			dx /= len;
			dy /= len;
			dz /= len;
		}
		float s = 0.55f + 0.45f * Math.min(1f, q * 1.6f);
		SCRATCH.set(pose).translate(dx * dist * off, dy * dist * off, dz * dist * off)
				.translate(cx, cy, cz).scale(s).translate(-cx, -cy, -cz);
		colour = tint(baseColour, q);
		return SCRATCH;
	}

	/** The overall front of the build for the scan line (0 = start .. 1 = done), per piece, in the piece's own bounds. */
	public static float frontHeight(int pieceIndex, float progress) {
		float[] b = bounds(pieceIndex);
		float f = Math.max(0f, Math.min(1f, progress / 0.82f));
		return pieceIndex == 3 ? b[4] - (b[4] - b[1]) * f : b[1] + (b[4] - b[1]) * f; // the leggings build from the top down
	}

	private static float easeOutBack(float x) {
		float c1 = 1.9f;
		float c3 = c1 + 1f;
		float m = x - 1f;
		return 1f + c3 * m * m * m + c1 * m * m;
	}

	private static float hash(float x, float y, float z) {
		double h = Math.sin(x * 127.1 + y * 311.7 + z * 74.7) * 43758.5453;
		return (float) (h - Math.floor(h));
	}

	/** {@code base} tinted hologram cyan by {@code 1 - q} (q = 1: its own colours). */
	private static int tint(int base, float q) {
		float k = (1f - q) * (1f - q);
		int a = base >>> 24;
		int r = Math.round(((base >> 16) & 0xFF) * (1f - k) + 96 * k);
		int g = Math.round(((base >> 8) & 0xFF) * (1f - k) + 220 * k);
		int b = Math.round((base & 0xFF) * (1f - k) + 255 * k);
		return a << 24 | r << 16 | g << 8 | b;
	}

	private static int scale(int c, float k) {
		int r = Math.round(((c >> 16) & 0xFF) * k);
		int g = Math.round(((c >> 8) & 0xFF) * k);
		int b = Math.round((c & 0xFF) * k);
		return (c & 0xFF000000) | r << 16 | g << 8 | b;
	}

	// ------------------------------------------------------------------ the piece on its stand

	/** Each piece's box on the armour stand (block units, feet at 0, front +Z): x0, y0, z0, x1, y1, z1. */
	private static final float[][] BOUNDS = {
			{ -0.29f, 1.48f, -0.29f, 0.29f, 2.05f, 0.29f },
			{ -0.56f, 0.70f, -0.2f, 0.56f, 1.56f, 0.2f },
			{ -0.29f, 0.18f, -0.18f, 0.29f, 0.8f, 0.18f },
			{ -0.3f, -0.02f, -0.2f, 0.3f, 0.32f, 0.2f } };

	public static float[] bounds(int pieceIndex) {
		return BOUNDS[Math.max(1, Math.min(4, pieceIndex)) - 1];
	}

	/** The middle of the piece's box (the point the callers turn it round and centre it on). */
	public static float centreY(int pieceIndex) {
		float[] b = bounds(pieceIndex);
		return (b[1] + b[4]) / 2f;
	}

	/** How big the piece is drawn so all four read about the same size (the chestplate with its arms is the widest). */
	public static float displayScale(int pieceIndex) {
		return switch (pieceIndex) {
			case 1 -> 1.15f;
			case 2 -> 0.78f;
			case 3 -> 1.0f;
			default -> 1.25f;
		};
	}

	private static ArmorStand stand;

	private static ArmorStand stand(Level level) {
		if (stand == null || stand.level() != level) {
			stand = new ArmorStand(level, 0, 0, 0);
			stand.setInvisible(true); // only the armour shows
			stand.setNoBasePlate(true);
			stand.setShowArms(true);
			stand.setNoGravity(true);
		}
		stand.setYRot(0f);
		stand.yRotO = 0f;
		stand.yBodyRot = 0f;
		stand.yBodyRotO = 0f;
		stand.yHeadRot = 0f;
		stand.yHeadRotO = 0f;
		return stand;
	}

	private static EquipmentSlot slot(int pieceIndex) {
		return switch (pieceIndex) {
			case 1 -> EquipmentSlot.HEAD;
			case 2 -> EquipmentSlot.CHEST;
			case 3 -> EquipmentSlot.LEGS;
			default -> EquipmentSlot.FEET;
		};
	}

	/**
	 * Draw the real {@code armour} piece (1..4) at {@code progress} (1 = whole; {@code asGhost} = a cyan blueprint) with
	 * the stand's feet at the pose's origin (callers translate by {@link #centreY} to centre it).
	 */
	public static void drawPiece(PoseStack pose, MultiBufferSource buffers, int light, Item armour, int pieceIndex,
			float progress, boolean asGhost, float partialTick) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || armour == null) {
			return;
		}
		ArmorStand s = stand(mc.level);
		EquipmentSlot worn = slot(pieceIndex);
		for (EquipmentSlot es : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			ItemStack want = es == worn ? new ItemStack(armour) : ItemStack.EMPTY;
			if (!s.getItemBySlot(es).is(want.getItem())) {
				s.setItemSlot(es, want);
			}
		}
		var dispatcher = mc.getEntityRenderDispatcher();
		dispatcher.setRenderShadow(false);
		begin(pieceIndex, progress, asGhost);
		try {
			dispatcher.render(s, 0.0, 0.0, 0.0, 0f, partialTick, pose, buffers, light);
		} finally {
			end();
			dispatcher.setRenderShadow(true);
		}
	}

	// ------------------------------------------------------------------ hologram overlays (WorkbenchHoloRenderType.HOLO)

	private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, int a) {
		vc.addVertex(m, x, y, z).setColor(WorkbenchHolo.R, WorkbenchHolo.G, WorkbenchHolo.B, a);
	}

	private static void line(VertexConsumer vc, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz, float w, int a) {
		float h = w / 2f;
		if (ax != bx) {
			v(vc, m, ax, ay - h, az, a); v(vc, m, bx, by - h, bz, a); v(vc, m, bx, by + h, bz, a); v(vc, m, ax, ay + h, az, a);
			v(vc, m, ax, ay, az - h, a); v(vc, m, bx, by, bz - h, a); v(vc, m, bx, by, bz + h, a); v(vc, m, ax, ay, az + h, a);
		} else if (ay != by) {
			v(vc, m, ax - h, ay, az, a); v(vc, m, ax + h, ay, az, a); v(vc, m, bx + h, by, bz, a); v(vc, m, bx - h, by, bz, a);
			v(vc, m, ax, ay, az - h, a); v(vc, m, ax, ay, az + h, a); v(vc, m, bx, by, bz + h, a); v(vc, m, bx, by, bz - h, a);
		} else {
			v(vc, m, ax - h, ay, az, a); v(vc, m, ax + h, ay, az, a); v(vc, m, bx + h, by, bz, a); v(vc, m, bx - h, by, bz, a);
			v(vc, m, ax, ay - h, az, a); v(vc, m, ax, ay + h, az, a); v(vc, m, bx, by + h, bz, a); v(vc, m, bx, by - h, bz, a);
		}
	}

	private static int alpha(float a) {
		return Math.max(0, Math.min(255, Math.round(a)));
	}

	/**
	 * The hologram wireframe round the piece (stand coordinates) that the plates fill: drawn bright as the frame forms
	 * (the first ~20%; for the chestplate the frame is the first stage of the build), fading out as the piece goes solid,
	 * plus the scan line sweeping the build front and a soft plane under it. {@code time} in ticks.
	 */
	public static void cage(VertexConsumer vc, Matrix4f m, int pieceIndex, float progress, float time, float k) {
		float[] b = bounds(pieceIndex);
		float grow = Math.min(1f, progress / 0.18f);
		float fade = progress < 0.75f ? 1f : Math.max(0f, 1f - (progress - 0.75f) / 0.25f);
		int a = alpha(200 * k * fade * (0.4f + 0.6f * grow));
		float w = 0.012f;
		float yTop = b[1] + (b[4] - b[1]) * grow; // the frame's uprights grow up out of the base
		float x0 = b[0], x1 = b[3], z0 = b[2], z1 = b[5], y0 = b[1];
		line(vc, m, x0, y0, z0, x1, y0, z0, w, a);
		line(vc, m, x0, y0, z1, x1, y0, z1, w, a);
		line(vc, m, x0, y0, z0, x0, y0, z1, w, a);
		line(vc, m, x1, y0, z0, x1, y0, z1, w, a);
		for (float x : new float[] { x0, x1 }) {
			for (float z : new float[] { z0, z1 }) {
				line(vc, m, x, y0, z, x, yTop, z, w, a);
			}
		}
		if (grow >= 1f) {
			line(vc, m, x0, yTop, z0, x1, yTop, z0, w, a);
			line(vc, m, x0, yTop, z1, x1, yTop, z1, w, a);
			line(vc, m, x0, yTop, z0, x0, yTop, z1, w, a);
			line(vc, m, x1, yTop, z0, x1, yTop, z1, w, a);
		}
		if (pieceIndex == 2 && grow < 1f) {
			// the chest frame: ribs forming across the front and back as it rises
			for (int i = 1; i < 4; i++) {
				float ry = y0 + (b[4] - b[1]) * i / 4f;
				if (ry < yTop) {
					line(vc, m, x0 * 0.55f, ry, z0, x1 * 0.55f, ry, z0, w, a);
					line(vc, m, x0 * 0.55f, ry, z1, x1 * 0.55f, ry, z1, w, a);
				}
			}
		}
		if (progress >= 1f) {
			return;
		}
		// the scan line on the build front + a second, faster sweep over the whole piece
		float fy = frontHeight(pieceIndex, progress);
		scanRing(vc, m, b, fy, alpha(220 * k * fade), 0.016f);
		float sweep = b[1] + (b[4] - b[1]) * (0.5f + 0.5f * (float) Math.sin(time * 0.18f));
		scanRing(vc, m, b, sweep, alpha(90 * k * fade), 0.008f);
		int pa = alpha(26 * k * fade);
		float e = 0.03f;
		v(vc, m, x0 - e, fy, z0 - e, pa);
		v(vc, m, x1 + e, fy, z0 - e, pa);
		v(vc, m, x1 + e, fy, z1 + e, pa);
		v(vc, m, x0 - e, fy, z1 + e, pa);
	}

	private static void scanRing(VertexConsumer vc, Matrix4f m, float[] b, float y, int a, float w) {
		float e = 0.03f;
		float x0 = b[0] - e, x1 = b[3] + e, z0 = b[2] - e, z1 = b[5] + e;
		line(vc, m, x0, y, z0, x1, y, z0, w, a);
		line(vc, m, x0, y, z1, x1, y, z1, w, a);
		line(vc, m, x0, y, z0, x0, y, z1, w, a);
		line(vc, m, x1, y, z0, x1, y, z1, w, a);
	}

	/**
	 * The piece's light coming on (stand coordinates): the helmet's eye glints, the chest's arc reactor bloom, the boots'
	 * repulsor flare under the soles. Nothing before {@link #LIGHTS_AT}; strongest just after, settling to a glow.
	 */
	public static void flare(VertexConsumer vc, Matrix4f m, int pieceIndex, float progress, float time, float k) {
		float at = LIGHTS_AT[pieceIndex - 1];
		if (progress < at || at > 1f) {
			return;
		}
		float t = Math.min(1f, (progress - at) / 0.1f);
		float burst = (float) Math.sin(Math.PI * t); // 0 -> 1 -> 0
		float pulse = 0.8f + 0.2f * (float) Math.sin(time * 0.6f);
		switch (pieceIndex) {
			case 1 -> {
				float r = 0.02f + 0.07f * burst;
				int a = alpha((90 + 160 * burst) * k * pulse);
				for (float ex : new float[] { -0.09f, 0.09f }) {
					glint(vc, m, ex, 1.79f, 0.27f, r, a);
				}
			}
			case 2 -> glint(vc, m, 0f, 1.21f, 0.15f, 0.04f + 0.16f * burst, alpha((110 + 145 * burst) * k * pulse));
			case 4 -> {
				float len = 0.06f + 0.34f * burst;
				int a = alpha((120 + 135 * burst) * k * pulse);
				for (float bx : new float[] { -0.12f, 0.12f }) {
					// a cone of thrust straight down out of each sole
					float r0 = 0.07f;
					float r1 = 0.02f;
					float y0 = -0.02f;
					float y1 = y0 - len;
					v(vc, m, bx - r0, y0, -r0, a); v(vc, m, bx + r0, y0, -r0, a); v(vc, m, bx + r1, y1, -r1, 0); v(vc, m, bx - r1, y1, -r1, 0);
					v(vc, m, bx - r0, y0, r0, a); v(vc, m, bx + r0, y0, r0, a); v(vc, m, bx + r1, y1, r1, 0); v(vc, m, bx - r1, y1, r1, 0);
					v(vc, m, bx - r0, y0, -r0, a); v(vc, m, bx - r0, y0, r0, a); v(vc, m, bx - r1, y1, r1, 0); v(vc, m, bx - r1, y1, -r1, 0);
					v(vc, m, bx + r0, y0, -r0, a); v(vc, m, bx + r0, y0, r0, a); v(vc, m, bx + r1, y1, r1, 0); v(vc, m, bx + r1, y1, -r1, 0);
					glint(vc, m, bx, y0 - 0.005f, 0f, r0 * 1.4f, a);
				}
			}
			default -> {
			}
		}
	}

	/** A small star of light (three crossed squares) at (x, y, z). */
	private static void glint(VertexConsumer vc, Matrix4f m, float x, float y, float z, float r, int a) {
		v(vc, m, x - r, y - r, z, a); v(vc, m, x + r, y - r, z, a); v(vc, m, x + r, y + r, z, a); v(vc, m, x - r, y + r, z, a);
		v(vc, m, x, y - r, z - r, a); v(vc, m, x, y - r, z + r, a); v(vc, m, x, y + r, z + r, a); v(vc, m, x, y + r, z - r, a);
		v(vc, m, x - r, y, z - r, a); v(vc, m, x + r, y, z - r, a); v(vc, m, x + r, y, z + r, a); v(vc, m, x - r, y, z + r, a);
	}

	/**
	 * The finish flourish (t 0..1 over ~1 s, stand coordinates round the piece's middle): a bright ring that expands and
	 * fades, and four sparks of light flung out from the piece.
	 */
	public static void flourish(VertexConsumer vc, Matrix4f m, int pieceIndex, float t, float k) {
		if (t < 0f || t >= 1f) {
			return;
		}
		float cy = centreY(pieceIndex);
		float r = 0.2f + 0.9f * t;
		int a = alpha(230 * k * (1f - t));
		int seg = 24;
		for (int i = 0; i < seg; i++) {
			double a0 = i * Math.PI * 2 / seg;
			double a1 = (i + 1) * Math.PI * 2 / seg;
			float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
			float ri = r - 0.03f, ro = r + 0.03f;
			v(vc, m, c0 * ri, cy, s0 * ri, a); v(vc, m, c1 * ri, cy, s1 * ri, a); v(vc, m, c1 * ro, cy, s1 * ro, a); v(vc, m, c0 * ro, cy, s0 * ro, a);
		}
		for (int i = 0; i < 4; i++) {
			double ang = i * Math.PI / 2 + Math.PI / 4;
			float d = 0.15f + 0.7f * t;
			glint(vc, m, (float) Math.cos(ang) * d, cy + 0.2f * t, (float) Math.sin(ang) * d, 0.05f * (1f - t), a);
		}
		glint(vc, m, 0f, cy, 0f, 0.35f * (1f - t), alpha(160 * k * (1f - t)));
	}
}
