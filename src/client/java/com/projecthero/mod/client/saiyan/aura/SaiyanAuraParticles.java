package com.projecthero.mod.client.saiyan.aura;

import org.joml.Vector3f;

import com.projecthero.mod.client.saiyan.SaiyanFx;
import com.projecthero.mod.saiyan.SaiyanForm;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.17.3: the Saiyan aura's client tick (every Saiyan within 64 blocks): eases the drawn aura's charge level
 * ({@link SaiyanAuraRenderer#tickLevel}), and the particles the drawing can't do --
 * <ul>
 * <li>charging on the ground: chips of the block underfoot lift off and FLOAT up round him (held up against gravity while he
 * charges, so they drop when he stops). At most {@value #MAX_DEBRIS} at once across all players.</li>
 * <li>the transformation: a ring of debris thrown out and up.</li>
 * <li>first person, for the local player only (who never sees the drawn aura): a few small sparks low at the screen's edges,
 * where the hands are -- never in the middle of the view.</li>
 * </ul>
 */
public final class SaiyanAuraParticles {
	static final int MAX_DEBRIS = 40;

	private static final Particle[] DEBRIS = new Particle[MAX_DEBRIS];
	private static final int[] DEBRIS_OWNER = new int[MAX_DEBRIS];
	private static final float[] DEBRIS_RISE = new float[MAX_DEBRIS];
	private static final float[] DEBRIS_SPIN = new float[MAX_DEBRIS];

	private SaiyanAuraParticles() {
	}

	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) {
			clear();
			return;
		}
		if (mc.isPaused()) {
			return;
		}
		Vec3 cam = mc.player.position();
		for (Player p : level.players()) {
			if (p.distanceToSqr(cam) > 64 * 64) {
				continue;
			}
			boolean active = SaiyanFx.active(p);
			if (!active && !SaiyanAuraRenderer.tracked(p.getId())) {
				continue;
			}
			float ground = groundDistance(level, p);
			BlockPos head = BlockPos.containing(p.getX(), p.getEyeY(), p.getZ());
			float daylight = Mth.clamp((level.getSkyDarken(1f) - 0.2f) / 0.8f, 0f, 1f) * level.getBrightness(LightLayer.SKY, head) / 15f;
			SaiyanAuraRenderer.tickLevel(p, ground, daylight);
			if (active && !p.isInvisible()) {
				tick(mc, level, p, ground, p == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON);
			}
		}
		SaiyanAuraRenderer.endTick();
		holdDebris(level);
	}

	public static void clear() {
		for (int i = 0; i < MAX_DEBRIS; i++) {
			DEBRIS[i] = null;
		}
		SaiyanAuraRenderer.clear();
	}

	/** Blocks from the feet down to the first solid surface (within 4), or -1. */
	private static float groundDistance(ClientLevel level, Player p) {
		if (p.onGround()) {
			return 0f;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int fy = Mth.floor(p.getY());
		for (int k = 0; k <= 4; k++) {
			pos.set(p.getBlockX(), fy - k, p.getBlockZ());
			VoxelShape s = level.getBlockState(pos).getCollisionShape(level, pos);
			if (!s.isEmpty()) {
				double d = p.getY() - (pos.getY() + s.max(net.minecraft.core.Direction.Axis.Y));
				return d >= 0 && d < 4 ? (float) d : -1f;
			}
		}
		return -1f;
	}

	private static Vector3f rgb(int c) {
		return new Vector3f(((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f);
	}

	private static void tick(Minecraft mc, ClientLevel level, Player p, float ground, boolean firstPerson) {
		RandomSource r = level.random;
		SaiyanForm f = SaiyanFx.form(p);
		boolean charging = SaiyanFx.charging(p);
		int tt = SaiyanFx.transformingTicks(p);
		BlockPos under = BlockPos.containing(p.getX(), p.getY() - 0.2, p.getZ());
		BlockState block = level.getBlockState(under);
		boolean onSolid = ground == 0f && block.getRenderShape() != RenderShape.INVISIBLE && !block.isAir();
		// charging: chips of the ground lift off round him and float up
		if (charging && onSolid && r.nextInt(3) == 0) {
			int slot = freeDebris();
			if (slot >= 0) {
				// in first person they start further out (and are let go lower, see holdDebris) so they never cross the view
				double a = r.nextDouble() * Mth.TWO_PI, rad = firstPerson ? 1.6 + r.nextDouble() * 1.2 : 0.7 + r.nextDouble() * 1.5;
				Particle d = mc.particleEngine.createParticle(new BlockParticleOption(ParticleTypes.BLOCK, block), p.getX() + Math.sin(a) * rad,
						p.getY() + 0.05, p.getZ() + Math.cos(a) * rad, 0, 0, 0);
				if (d != null) {
					d.scale(0.45f + r.nextFloat() * 0.55f);
					d.setLifetime(50 + r.nextInt(40));
					d.setParticleSpeed(0, 0.12 + r.nextDouble() * 0.06, 0);
					DEBRIS[slot] = d;
					DEBRIS_OWNER[slot] = p.getId();
					DEBRIS_RISE[slot] = 0.025f + r.nextFloat() * 0.035f;
					DEBRIS_SPIN[slot] = (r.nextBoolean() ? 1f : -1f) * (0.01f + r.nextFloat() * 0.02f);
				}
			}
		}
		// the transformation: a ring of debris thrown out and up
		if (tt <= 1 && f != SaiyanForm.BASE && onSolid) {
			for (int i = 0; i < 28; i++) {
				double a = i / 28.0 * Mth.TWO_PI + r.nextDouble() * 0.2;
				double sp = 0.25 + r.nextDouble() * 0.3;
				Particle d = mc.particleEngine.createParticle(new BlockParticleOption(ParticleTypes.BLOCK, block), p.getX() + Math.sin(a) * 0.7,
						p.getY() + 0.1, p.getZ() + Math.cos(a) * 0.7, 0, 0, 0);
				if (d != null) {
					d.scale(0.6f + r.nextFloat() * 0.6f);
					// first person: thrown low and outward only, never up across the view
					d.setParticleSpeed(Math.sin(a) * sp, firstPerson ? 0.12 + r.nextDouble() * 0.08 : 0.35 + r.nextDouble() * 0.35, Math.cos(a) * sp);
				}
			}
		}
		// first person: the drawn aura is never round the camera -- a few sparks low at the edges of the view, by the hands
		boolean kaioken = SaiyanFx.kaioken(p);
		if (firstPerson && (charging ? r.nextInt(2) == 0 : ((f != SaiyanForm.BASE || kaioken) && r.nextInt(7) == 0))) {
			int col = kaioken ? 0xFF3030 : f == SaiyanForm.BASE ? 0xEAF2FF : f.auraColor & 0xFFFFFF;
			Vec3 eye = p.getEyePosition();
			Vec3 look = p.getViewVector(1f);
			Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
			int side = r.nextBoolean() ? 1 : -1;
			Vec3 h = eye.add(look.scale(0.8)).add(right.scale(side * (0.5 + r.nextDouble() * 0.15))).add(0, -0.5 - r.nextDouble() * 0.15, 0);
			Particle s = mc.particleEngine.createParticle(new DustParticleOptions(rgb(col), 0.3f + r.nextFloat() * 0.2f), h.x, h.y, h.z, 0, 0, 0);
			if (s != null) {
				s.setParticleSpeed((r.nextDouble() - 0.5) * 0.01 + right.x * side * 0.01, 0.025 + r.nextDouble() * 0.02,
						(r.nextDouble() - 0.5) * 0.01 + right.z * side * 0.01);
				s.setLifetime(8 + r.nextInt(6));
			}
		}
	}

	private static int freeDebris() {
		for (int i = 0; i < MAX_DEBRIS; i++) {
			if (DEBRIS[i] == null || !DEBRIS[i].isAlive()) {
				DEBRIS[i] = null;
				return i;
			}
		}
		return -1;
	}

	/**
	 * Holds the floating debris up while its Saiyan still charges: each tick its speed is set so that after the particle's own
	 * gravity (0.04 / tick) it rises slowly, drifting round him; once he stops it is let go and falls.
	 */
	private static void holdDebris(ClientLevel level) {
		for (int i = 0; i < MAX_DEBRIS; i++) {
			Particle d = DEBRIS[i];
			if (d == null) {
				continue;
			}
			if (!d.isAlive()) {
				DEBRIS[i] = null;
				continue;
			}
			net.minecraft.world.entity.Entity e = level.getEntity(DEBRIS_OWNER[i]);
			if (!(e instanceof Player p) || !SaiyanFx.charging(p)) {
				DEBRIS[i] = null; // let go: it falls
				continue;
			}
			Minecraft mc = Minecraft.getInstance();
			if (p == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON && d.getBoundingBox().minY > p.getY() + 0.9) {
				DEBRIS[i] = null; // first person: let go below eye level, so nothing floats up across the view
				continue;
			}
			// a slow swirl round the Saiyan, and a bob
			double px = d.getBoundingBox().getCenter().x - p.getX(), pz = d.getBoundingBox().getCenter().z - p.getZ();
			float spin = DEBRIS_SPIN[i];
			double bob = Math.sin((level.getGameTime() + i * 7) * 0.25) * 0.012;
			d.setParticleSpeed(-pz * spin, DEBRIS_RISE[i] + 0.04 + bob, px * spin);
		}
	}
}
