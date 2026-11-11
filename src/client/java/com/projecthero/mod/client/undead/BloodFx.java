package com.projecthero.mod.client.undead;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * v0.17.3 (user: "improve all the blood vfx"): Undead's blood, client-side and pooled (fixed arrays, nothing allocated per
 * frame): droplets in several dark-red shades that arc under gravity and leave a splat on the floor where they land (the
 * splats fade after a few seconds), soft mist puffs on bursts, and the jets' spray. Capped at {@link #MAX_DROPS} droplets
 * and {@link #MAX_SPLATS} splats (the oldest go first). Fed by {@link UndeadClient} and the renderers; drawn from
 * {@link UndeadEffectsRenderer} after the entities.
 */
public final class BloodFx {
	public static final int MAX_DROPS = 2400;
	public static final int MAX_SPLATS = 320;
	/** Dark reds, darkest first; the last two are the bright arterial core. */
	public static final int[] SHADES = { 0x3A0204, 0x55060A, 0x6E0A0E, 0x8A1014, 0xA8161A, 0xC8201E, 0xE0342A };

	// droplets: position, previous position, velocity, size, colour, age, life, kind (0 drop, 1 mist, 2 ember)
	private static final float[] X = new float[MAX_DROPS], Y = new float[MAX_DROPS], Z = new float[MAX_DROPS];
	private static final float[] PX = new float[MAX_DROPS], PY = new float[MAX_DROPS], PZ = new float[MAX_DROPS];
	private static final float[] VX = new float[MAX_DROPS], VY = new float[MAX_DROPS], VZ = new float[MAX_DROPS];
	private static final float[] SIZE = new float[MAX_DROPS], GRAV = new float[MAX_DROPS];
	private static final int[] RGB = new int[MAX_DROPS], AGE = new int[MAX_DROPS], LIFE = new int[MAX_DROPS], KIND = new int[MAX_DROPS];
	private static int drops;
	private static int dropCursor;

	// splats: a flat blot on a block face (only floors), fading out
	private static final float[] SX = new float[MAX_SPLATS], SY = new float[MAX_SPLATS], SZ = new float[MAX_SPLATS], SS = new float[MAX_SPLATS],
			SR = new float[MAX_SPLATS];
	private static final int[] SRGB = new int[MAX_SPLATS], SAGE = new int[MAX_SPLATS], SLIFE = new int[MAX_SPLATS], SLIGHT = new int[MAX_SPLATS];
	private static int splats;
	private static int splatCursor;
	private static long lastSplatSound;

	private static final RandomSource RND = RandomSource.create();
	private static final Vector3f[] CORNERS = new Vector3f[8];
	private static final Quaternionf Q = new Quaternionf();

	static {
		for (int i = 0; i < 8; i++) {
			CORNERS[i] = new Vector3f();
		}
	}

	private BloodFx() {
	}

	public static void reset() {
		drops = 0;
		splats = 0;
	}

	public static int count() {
		return drops;
	}

	// ---------------------------------------------------------------- spawning

	private static int slot() {
		if (drops < MAX_DROPS) {
			return drops++;
		}
		// full: the oldest-ish slot goes (round robin over the pool)
		dropCursor = (dropCursor + 1) % MAX_DROPS;
		return dropCursor;
	}

	/** One droplet. {@code kind}: 0 a drop (falls, splats), 1 mist (floats, swells, fades), 2 an ember (rises, glows). */
	public static void drop(double x, double y, double z, double vx, double vy, double vz, float size, int rgb, int life, int kind) {
		int i = slot();
		X[i] = PX[i] = (float) x;
		Y[i] = PY[i] = (float) y;
		Z[i] = PZ[i] = (float) z;
		VX[i] = (float) vx;
		VY[i] = (float) vy;
		VZ[i] = (float) vz;
		SIZE[i] = size;
		RGB[i] = rgb;
		AGE[i] = 0;
		LIFE[i] = life;
		KIND[i] = kind;
		GRAV[i] = kind == 0 || kind == 3 || kind == 4 ? 0.045f : kind == 2 ? -0.004f : 0.0f;
	}

	/**
	 * v0.17.3 (user: "make the blood on the blade actually drip off onto the ground"): a drop falling off the Bone Sword at
	 * {@code at}. Unlike the other drops it shows right in front of the camera (kind 4: no near fade), so the drips off a sword
	 * held in first person are seen leaving it; it falls and splats like any drop.
	 */
	public static void bladeDrip(Vec3 at, Vec3 carry, float size) {
		drop(at.x, at.y, at.z, carry.x * 0.6, Math.min(0.0, carry.y * 0.4) - 0.02, carry.z * 0.6, size, SHADES[1 + RND.nextInt(3)], 50, 4);
	}

	public static int shade(float darkness) {
		int k = Mth.clamp((int) ((1f - darkness) * (SHADES.length - 2) + RND.nextFloat() * 1.6f), 0, SHADES.length - 1);
		return SHADES[k];
	}

	/** A spray of drops from {@code at} along {@code dir} (any length; normalised), {@code spread} 0 (a line) .. 1 (all round). */
	public static void spurt(Vec3 at, Vec3 dir, int count, double speed, double spread, Vec3 carry) {
		Vec3 d = dir.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : dir.normalize();
		for (int k = 0; k < count; k++) {
			double sp = speed * (0.45 + RND.nextDouble() * 0.8);
			double ox = (RND.nextDouble() - 0.5) * 2 * spread, oy = (RND.nextDouble() - 0.5) * 2 * spread, oz = (RND.nextDouble() - 0.5) * 2 * spread;
			Vec3 v = d.add(ox, oy, oz).normalize().scale(sp).add(carry);
			float size = 0.022f + RND.nextFloat() * 0.03f;
			drop(at.x, at.y, at.z, v.x, v.y, v.z, size, shade(RND.nextFloat()), 22 + RND.nextInt(26), 0);
		}
	}

	/** A soft puff of red mist. */
	public static void mist(Vec3 at, int count, float size, Vec3 carry) {
		for (int k = 0; k < count; k++) {
			drop(at.x + (RND.nextDouble() - 0.5) * 0.3, at.y + (RND.nextDouble() - 0.5) * 0.3, at.z + (RND.nextDouble() - 0.5) * 0.3,
					carry.x + (RND.nextDouble() - 0.5) * 0.06, carry.y + RND.nextDouble() * 0.03, carry.z + (RND.nextDouble() - 0.5) * 0.06,
					size * (0.6f + RND.nextFloat() * 0.6f), SHADES[2 + RND.nextInt(3)], 14 + RND.nextInt(10), 1);
		}
	}

	/** A slow drip off a fresh stump. */
	public static void drip(Vec3 at) {
		drop(at.x + (RND.nextDouble() - 0.5) * 0.08, at.y, at.z + (RND.nextDouble() - 0.5) * 0.08, 0, -0.02, 0, 0.035f + RND.nextFloat() * 0.025f,
				SHADES[1 + RND.nextInt(3)], 40, 0);
	}

	/**
	 * One tick of a blood jet out of a stump at {@code at}, blowing along {@code dir} (a unit vector; the jet's thrust is the
	 * other way): a fast bright core, darker spray round it, a few heavy drops that arc away. {@code power} 1 normal, ~1.8 boost.
	 */
	public static void jet(Vec3 at, Vec3 dir, Vec3 carry, float power) {
		// (the drawn stream is the jet's body; the drops only texture it: few, and leaving a little behind the stump so they do
		// not crowd the silhouette seen from behind)
		int core = power > 1.5f ? 2 : 1, spray = (int) (2 * power);
		at = at.add(dir.scale(0.2));
		for (int k = 0; k < core; k++) {
			double sp = (0.45 + RND.nextDouble() * 0.3) * power;
			Vec3 v = dir.add((RND.nextDouble() - 0.5) * 0.12, (RND.nextDouble() - 0.5) * 0.12, (RND.nextDouble() - 0.5) * 0.12).normalize().scale(sp).add(carry);
			drop(at.x, at.y, at.z, v.x, v.y, v.z, 0.03f + RND.nextFloat() * 0.02f, SHADES[4 + RND.nextInt(3)], 4 + RND.nextInt(3), 3);
		}
		for (int k = 0; k < spray; k++) {
			double sp = (0.15 + RND.nextDouble() * 0.25) * power;
			// a narrow spray round the stream (a wide one fanned out beside him like wings)
			Vec3 v = dir.add((RND.nextDouble() - 0.5) * 0.45, (RND.nextDouble() - 0.5) * 0.45, (RND.nextDouble() - 0.5) * 0.45).normalize().scale(sp).add(carry.scale(0.85));
			drop(at.x, at.y, at.z, v.x, v.y, v.z, 0.02f + RND.nextFloat() * 0.02f, shade(0.4f + RND.nextFloat() * 0.6f), 8 + RND.nextInt(10), 0);
		}
		if (RND.nextFloat() < 0.15f * power) {
			mist(at.add(dir.scale(0.7)), 1, 0.12f, carry.scale(0.9));
		}
	}

	/** Embers and soot off a burning edge. */
	public static void embers(Vec3 at, Vec3 carry, int count) {
		for (int k = 0; k < count; k++) {
			int rgb = UndeadRegrow.EMBER[2 + RND.nextInt(3)];
			drop(at.x + (RND.nextDouble() - 0.5) * 0.25, at.y + (RND.nextDouble() - 0.5) * 0.25, at.z + (RND.nextDouble() - 0.5) * 0.25,
					carry.x * 0.7 + (RND.nextDouble() - 0.5) * 0.08, carry.y * 0.7 + RND.nextDouble() * 0.06, carry.z * 0.7 + (RND.nextDouble() - 0.5) * 0.08,
					0.025f + RND.nextFloat() * 0.03f, rgb, 10 + RND.nextInt(14), 2);
		}
	}

	/** Charred flesh shades: soot black to scorched brown, for the flecks off the boost's burning edge. */
	private static final int[] CHAR = { 0x1A1412, 0x2A1E1A, 0x3A2620, 0x4E2E22 };

	/** v0.17.3 flight boost: one fleck of char off a burning edge -- trails back in the wake and falls (no splat). */
	public static void charFleck(Vec3 at, Vec3 carry) {
		drop(at.x + (RND.nextDouble() - 0.5) * 0.2, at.y + (RND.nextDouble() - 0.5) * 0.2, at.z + (RND.nextDouble() - 0.5) * 0.2,
				carry.x * 0.6 + (RND.nextDouble() - 0.5) * 0.06, carry.y * 0.6 + RND.nextDouble() * 0.03, carry.z * 0.6 + (RND.nextDouble() - 0.5) * 0.06,
				0.02f + RND.nextFloat() * 0.02f, CHAR[RND.nextInt(CHAR.length)], 14 + RND.nextInt(12), 3);
	}

	// ---------------------------------------------------------------- arterial spray (v0.17.3 blood flight, UndeadJets)

	/** Flesh shades: torn muscle and fat, for the bits the boost's burn and the take-off throw off. */
	public static final int[] FLESH = { 0x5E1416, 0x7E2224, 0x9C3434, 0xB8504A, 0xD08070 };

	/**
	 * One piece breaking off an arterial jet at {@code at}: out along unit {@code dir} at {@code speed} (relative to the body,
	 * which carries it with {@code carry}), off the line by up to {@code spread}. A drop is small and quick (it streaks); a
	 * {@code glob} is heavy, dark and lives long enough to land and splat.
	 */
	public static void arterialDrop(Vec3 at, Vec3 dir, Vec3 carry, double speed, double spread, boolean glob) {
		double ox = (RND.nextDouble() - 0.5) * 2 * spread, oy = (RND.nextDouble() - 0.5) * 2 * spread, oz = (RND.nextDouble() - 0.5) * 2 * spread;
		double dx = dir.x + ox, dy = dir.y + oy, dz = dir.z + oz;
		double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (l < 1.0e-6) {
			return;
		}
		double k = speed / l;
		double jx = (RND.nextDouble() - 0.5) * 0.06, jy = (RND.nextDouble() - 0.5) * 0.06, jz = (RND.nextDouble() - 0.5) * 0.06;
		float size = glob ? 0.07f + RND.nextFloat() * 0.03f : 0.018f + RND.nextFloat() * 0.03f;
		int rgb = glob ? SHADES[1 + RND.nextInt(3)] : shade(RND.nextFloat());
		drop(at.x + jx, at.y + jy, at.z + jz, carry.x + dx * k, carry.y + dy * k, carry.z + dz * k, size, rgb, glob ? 50 + RND.nextInt(20) : 14 + RND.nextInt(16), 0);
	}

	/**
	 * A stump erupting (a hand or foot blown off): a crown of blood thrown out round the cut, a fast core out of it, a few
	 * heavy globs, torn flesh and a puff of mist. {@code axis}: out of the stump.
	 */
	public static void eruption(Vec3 at, Vec3 axis, Vec3 carry, float power) {
		Vec3 d = axis.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : axis.normalize();
		Vec3 a = d.cross(Math.abs(d.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
		Vec3 b = d.cross(a);
		int ring = (int) (22 * power);
		for (int k = 0; k < ring; k++) {
			// round the cut face, tipped outward: a crown
			double ang = RND.nextDouble() * Math.PI * 2;
			Vec3 radial = a.scale(Math.cos(ang)).add(b.scale(Math.sin(ang)));
			Vec3 v = radial.scale(0.6 + RND.nextDouble() * 0.4).add(d.scale(0.4 + RND.nextDouble() * 0.6)).normalize().scale((0.12 + RND.nextDouble() * 0.28) * power);
			drop(at.x, at.y, at.z, carry.x + v.x, carry.y + v.y + 0.05, carry.z + v.z, 0.02f + RND.nextFloat() * 0.035f, shade(RND.nextFloat()),
					24 + RND.nextInt(24), 0);
		}
		for (int k = 0; k < (int) (8 * power); k++) {
			arterialDrop(at, d, carry, (0.35 + RND.nextDouble() * 0.3) * power, 0.15, false);
		}
		for (int k = 0; k < 3; k++) {
			arterialDrop(at, d.add(0, 0.4, 0), carry, (0.12 + RND.nextDouble() * 0.15) * power, 0.5, true);
		}
		flesh(at, carry.add(d.scale(0.12 * power)), (int) (4 * power));
		mist(at.add(d.scale(0.2)), (int) (3 * power), 0.3f, carry.scale(0.6));
	}

	/** Torn flesh: small chunks in muscle and fat shades, flung with {@code vel} (plus a scatter), that fall and splat. */
	public static void flesh(Vec3 at, Vec3 vel, int count) {
		for (int k = 0; k < count; k++) {
			drop(at.x, at.y, at.z, vel.x + (RND.nextDouble() - 0.5) * 0.18, vel.y + RND.nextDouble() * 0.12, vel.z + (RND.nextDouble() - 0.5) * 0.18,
					0.035f + RND.nextFloat() * 0.03f, FLESH[RND.nextInt(FLESH.length)], 30 + RND.nextInt(20), 0);
		}
	}

	// ---------------------------------------------------------------- tick

	public static void tick() {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			reset();
			return;
		}
		for (int i = 0; i < drops; i++) {
			if (++AGE[i] > LIFE[i]) {
				remove(i);
				i--;
				continue;
			}
			PX[i] = X[i];
			PY[i] = Y[i];
			PZ[i] = Z[i];
			VY[i] -= GRAV[i];
			float drag = KIND[i] == 1 ? 0.86f : 0.97f;
			VX[i] *= drag;
			VY[i] *= KIND[i] == 1 ? 0.86f : 0.985f;
			VZ[i] *= drag;
			float nx = X[i] + VX[i], ny = Y[i] + VY[i], nz = Z[i] + VZ[i];
			if (KIND[i] == 0 || KIND[i] == 3 || KIND[i] == 4) {
				BlockPos below = BlockPos.containing(nx, ny, nz);
				BlockState st = level.getBlockState(below);
				if (!st.isAir()) {
					VoxelShape shape = st.getCollisionShape(level, below);
					if (!shape.isEmpty()) {
						double top = below.getY() + shape.max(Direction.Axis.Y);
						if ((KIND[i] == 0 && RND.nextFloat() < 0.45f || KIND[i] == 4) && ny <= top + 0.001 && Y[i] >= top - 0.05) {
							splat(nx, (float) top, nz, SIZE[i] * (2.2f + RND.nextFloat() * 1.6f), RGB[i], level);
							if (level.getGameTime() - lastSplatSound >= 4) {
								lastSplatSound = level.getGameTime();
								UndeadSounds.splat(new Vec3(nx, top, nz));
							}
						}
						remove(i);
						i--;
						continue;
					}
				}
			}
			X[i] = nx;
			Y[i] = ny;
			Z[i] = nz;
		}
		for (int i = 0; i < splats; i++) {
			if (++SAGE[i] > SLIFE[i]) {
				int last = --splats;
				copySplat(last, i);
				i--;
			}
		}
	}

	private static void remove(int i) {
		int last = --drops;
		if (i != last) {
			X[i] = X[last];
			Y[i] = Y[last];
			Z[i] = Z[last];
			PX[i] = PX[last];
			PY[i] = PY[last];
			PZ[i] = PZ[last];
			VX[i] = VX[last];
			VY[i] = VY[last];
			VZ[i] = VZ[last];
			SIZE[i] = SIZE[last];
			GRAV[i] = GRAV[last];
			RGB[i] = RGB[last];
			AGE[i] = AGE[last];
			LIFE[i] = LIFE[last];
			KIND[i] = KIND[last];
		}
	}

	private static void splat(float x, float y, float z, float size, int rgb, ClientLevel level) {
		int i;
		if (splats < MAX_SPLATS) {
			i = splats++;
		} else {
			splatCursor = (splatCursor + 1) % MAX_SPLATS;
			i = splatCursor;
		}
		SX[i] = x;
		SY[i] = y + 0.004f + RND.nextFloat() * 0.003f;
		SZ[i] = z;
		SS[i] = Math.min(0.13f, size * 0.8f);
		SR[i] = RND.nextFloat() * Mth.TWO_PI;
		SRGB[i] = rgb;
		SAGE[i] = 0;
		SLIFE[i] = 80 + RND.nextInt(60);
		SLIGHT[i] = LevelRenderer.getLightColor(level, BlockPos.containing(x, y + 0.1, z));
	}

	private static void copySplat(int from, int to) {
		if (from == to) {
			return;
		}
		SX[to] = SX[from];
		SY[to] = SY[from];
		SZ[to] = SZ[from];
		SS[to] = SS[from];
		SR[to] = SR[from];
		SRGB[to] = SRGB[from];
		SAGE[to] = SAGE[from];
		SLIFE[to] = SLIFE[from];
		SLIGHT[to] = SLIGHT[from];
	}

	// ---------------------------------------------------------------- render

	private static final float WU0 = 5.3f / 16f, WV0 = 0.3f / 16f, WU1 = 5.7f / 16f, WV1 = 0.7f / 16f;

	public static void render(WorldRenderContext context) {
		if ((drops == 0 && splats == 0) || context.consumers() == null || context.matrixStack() == null) {
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		Camera camera = context.camera();
		Vec3 cam = camera.getPosition();
		PoseStack.Pose last = context.matrixStack().last();
		Matrix4f m = last.pose();
		// the solid drops and embers: little cubes
		VertexConsumer solid = context.consumers().getBuffer(RenderType.entityCutoutNoCull(UndeadRender.PALETTE));
		for (int i = 0; i < drops; i++) {
			if (KIND[i] == 1) {
				continue;
			}
			float x = Mth.lerp(partial, PX[i], X[i]) - (float) cam.x;
			float y = Mth.lerp(partial, PY[i], Y[i]) - (float) cam.y;
			float z = Mth.lerp(partial, PZ[i], Z[i]) - (float) cam.z;
			float age = AGE[i] + partial;
			float near = KIND[i] == 4 ? 1f : Mth.clamp((Mth.sqrt(x * x + y * y + z * z) - 1.3f) / 1.7f, 0f, 1f);
			float h = SIZE[i] * 0.5f * Mth.clamp((LIFE[i] - age) / 4f, 0f, 1f) * near;
			if (h < 0.002f) {
				continue;
			}
			// stretched along its velocity: a falling streak, not a cube
			float vx = VX[i], vy = VY[i], vz = VZ[i];
			float sp = Mth.sqrt(vx * vx + vy * vy + vz * vz);
			float stretch = 1f + Math.min(1.6f, sp * 3f);
			if (sp > 1.0e-4f) {
				Q.rotationTo(0f, 1f, 0f, vx / sp, vy / sp, vz / sp);
			} else {
				Q.identity();
			}
			for (int c = 0; c < 8; c++) {
				CORNERS[c].set((c & 1) == 0 ? -h : h, ((c & 2) == 0 ? -h : h) * stretch, (c & 4) == 0 ? -h : h).rotate(Q).add(x, y, z);
			}
			int light = KIND[i] == 2 ? 0xF000F0 : LevelRenderer.getLightColor(level, BlockPos.containing(X[i], Y[i], Z[i]));
			cube(solid, last, RGB[i], 255, light);
		}
		// the splats and the mist: translucent
		VertexConsumer soft = context.consumers().getBuffer(RenderType.entityTranslucent(UndeadRender.PALETTE));
		for (int i = 0; i < splats; i++) {
			float fade = Mth.clamp((SLIFE[i] - SAGE[i] - partial) / 30f, 0f, 1f);
			float grow = Mth.clamp((SAGE[i] + partial) / 3f, 0.4f, 1f);
			int a = (int) (230 * fade);
			if (a < 4) {
				continue;
			}
			float s = SS[i] * grow, c = Mth.cos(SR[i]) * s, sn = Mth.sin(SR[i]) * s;
			float x = SX[i] - (float) cam.x, y = SY[i] - (float) cam.y, z = SZ[i] - (float) cam.z;
			int rgb = SRGB[i];
			int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
			// a blot: an irregular octagon-ish pair of rotated squares
			quadFlat(soft, last, m, x, y, z, c, sn, r, g, b, a, SLIGHT[i]);
			quadFlat(soft, last, m, x, y + 0.0005f, z, (c - sn) * 0.72f, (sn + c) * 0.72f, Math.max(0, r - 18), g, b, a, SLIGHT[i]);
		}
		Quaternionf face = camera.rotation();
		for (int i = 0; i < drops; i++) {
			if (KIND[i] != 1) {
				continue;
			}
			float x = Mth.lerp(partial, PX[i], X[i]) - (float) cam.x;
			float y = Mth.lerp(partial, PY[i], Y[i]) - (float) cam.y;
			float z = Mth.lerp(partial, PZ[i], Z[i]) - (float) cam.z;
			float t = (AGE[i] + partial) / LIFE[i];
			float s = SIZE[i] * (0.5f + t * 1.1f);
			float near = Mth.clamp((Mth.sqrt(x * x + y * y + z * z) - 2f) / 2f, 0f, 1f);
			int a = (int) (80 * (1f - t) * Math.min(1f, t * 6f) * near);
			if (a < 3) {
				continue;
			}
			int rgb = RGB[i];
			int light = LevelRenderer.getLightColor(level, BlockPos.containing(X[i], Y[i], Z[i]));
			billboard(soft, last, m, face, x, y, z, s, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, a, light);
		}
	}

	private static final int[][] FACES = { { 0, 4, 6, 2 }, { 1, 3, 7, 5 }, { 0, 1, 5, 4 }, { 2, 6, 7, 3 }, { 0, 2, 3, 1 }, { 4, 5, 7, 6 } };

	private static void cube(VertexConsumer vc, PoseStack.Pose pose, int rgb, int a, int light) {
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		Matrix4f m = pose.pose();
		for (int[] f : FACES) {
			for (int k = 0; k < 4; k++) {
				Vector3f v = CORNERS[f[k]];
				vc.addVertex(m, v.x, v.y, v.z).setColor(r, g, b, a).setUv(k == 1 || k == 2 ? WU1 : WU0, k >= 2 ? WV1 : WV0).setOverlay(OverlayTexture.NO_OVERLAY)
						.setLight(light).setNormal(pose, 0f, 1f, 0f);
			}
		}
	}

	private static void quadFlat(VertexConsumer vc, PoseStack.Pose pose, Matrix4f m, float x, float y, float z, float c, float s, int r, int g, int b, int a,
			int light) {
		vc.addVertex(m, x - c + s, y, z - s - c).setColor(r, g, b, a).setUv(WU0, WV0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
		vc.addVertex(m, x - c - s, y, z - s + c).setColor(r, g, b, a).setUv(WU0, WV1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
		vc.addVertex(m, x + c - s, y, z + s + c).setColor(r, g, b, a).setUv(WU1, WV1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
		vc.addVertex(m, x + c + s, y, z + s - c).setColor(r, g, b, a).setUv(WU1, WV0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
	}

	private static final Vector3f BA = new Vector3f(), BB = new Vector3f();

	private static void billboard(VertexConsumer vc, PoseStack.Pose pose, Matrix4f m, Quaternionf face, float x, float y, float z, float s, int r, int g, int b,
			int a, int light) {
		BA.set(s, 0, 0).rotate(face);
		BB.set(0, s, 0).rotate(face);
		vc.addVertex(m, x - BA.x - BB.x, y - BA.y - BB.y, z - BA.z - BB.z).setColor(r, g, b, a).setUv(WU0, WV0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
				.setNormal(pose, 0, 1, 0);
		vc.addVertex(m, x - BA.x + BB.x, y - BA.y + BB.y, z - BA.z + BB.z).setColor(r, g, b, a).setUv(WU0, WV1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
				.setNormal(pose, 0, 1, 0);
		vc.addVertex(m, x + BA.x + BB.x, y + BA.y + BB.y, z + BA.z + BB.z).setColor(r, g, b, a).setUv(WU1, WV1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
				.setNormal(pose, 0, 1, 0);
		vc.addVertex(m, x + BA.x - BB.x, y + BA.y - BB.y, z + BA.z - BB.z).setColor(r, g, b, a).setUv(WU1, WV0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
				.setNormal(pose, 0, 1, 0);
	}
}
