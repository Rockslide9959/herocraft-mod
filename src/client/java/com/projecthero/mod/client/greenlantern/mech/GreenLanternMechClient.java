package com.projecthero.mod.client.greenlantern.mech;

import com.projecthero.mod.greenlantern.mech.GreenLanternMech;
import com.projecthero.mod.greenlantern.mech.HardLightMechEntity;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the Hard-Light Mech's client side -- the renderer, the HUD, and the weight of it: every client plays its
 * stomping footfalls (sound, ground dust, a camera shake that fades with distance) in step with how far it really
 * walked, a heavy landing after a jump, and the Ground Slam's quake. The camera pull-back lives in
 * {@code GreenLanternMechCameraMixin}, which also applies {@link #shakeYaw} / {@link #shakePitch}.
 */
public final class GreenLanternMechClient {
	/** One footfall per this many blocks walked (the walk clip: two steps per 0.6 s at 10 blocks / s). */
	private static final double STRIDE = 3.0;
	private static final DustParticleOptions GREEN = new DustParticleOptions(new org.joml.Vector3f(0.208f, 0.941f, 0.459f), 1.5f);

	private static float shake;
	private static float shakeYaw;
	private static float shakePitch;
	private static long lastSlamSeen = Long.MIN_VALUE;

	private GreenLanternMechClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(GreenLanternMech.TYPE, HardLightMechRenderer::new);
		HudRenderCallback.EVENT.register(GreenLanternMechHud::render);
		ClientTickEvents.END_CLIENT_TICK.register(GreenLanternMechClient::tick);
	}

	/** Whether the local player is sitting in their own Hard-Light Mech. */
	public static boolean piloting() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && HardLightMechEntity.isPilot(mc.player);
	}

	public static float shakeYaw() {
		return shakeYaw;
	}

	public static float shakePitch() {
		return shakePitch;
	}

	private static void addShake(Entity viewer, Vec3 at, float amount, double range) {
		double d = viewer.position().distanceTo(at);
		if (d < range) {
			shake = Math.min(3.5f, shake + amount * (float) (1.0 - d / range));
		}
	}

	private static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null || mc.isPaused()) {
			shake = 0f;
			shakeYaw = shakePitch = 0f;
			return;
		}
		RandomSource rnd = mc.level.getRandom();
		for (HardLightMechEntity m : mc.level.getEntitiesOfClass(HardLightMechEntity.class, mc.player.getBoundingBox().inflate(64.0))) {
			tickMech(mc, m, rnd);
		}
		shake *= 0.78f;
		if (shake < 0.02f) {
			shake = 0f;
		}
		shakeYaw = (rnd.nextFloat() - 0.5f) * shake;
		shakePitch = (rnd.nextFloat() - 0.5f) * shake;
	}

	private static void tickMech(Minecraft mc, HardLightMechEntity m, RandomSource rnd) {
		double dx = m.getX() - m.xo, dz = m.getZ() - m.zo;
		double dy = m.getY() - m.yo;
		boolean onGround = m.onGround();
		if (m.isActive() && onGround) {
			m.clientStepDist += Math.sqrt(dx * dx + dz * dz);
			if (m.clientStepDist >= STRIDE) {
				m.clientStepDist -= STRIDE;
				m.clientFoot ^= 1;
				footfall(mc, m, m.clientFoot == 0 ? 0.75 : -0.75, 1.0f, rnd);
			}
		}
		// landing after a jump / a fall: both feet at once, harder the faster it came down
		if (onGround && !m.clientWasOnGround && m.clientFallVel < -0.3) {
			float k = (float) Math.min(2.0, -m.clientFallVel * 2.0);
			footfall(mc, m, 0.75, k, rnd);
			footfall(mc, m, -0.75, k, rnd);
			m.clientStepDist = 0;
		}
		m.clientWasOnGround = onGround;
		m.clientFallVel = dy;
		// the Ground Slam quake (once per slam)
		long slam = m.slamAt();
		if (slam != lastSlamSeen && m.level().getGameTime() - slam < 4) {
			lastSlamSeen = slam;
			addShake(mc.player, m.position(), 3.0f, 32.0);
		}
		// thruster flames while it is rising (the jump) -- drawn by every client
		if (m.isActive() && dy > 0.2) {
			for (int s = -1; s <= 1; s += 2) {
				Vec3 n = m.local(s * 0.5, 3.35, -1.35);
				mc.level.addParticle(ParticleTypes.FLAME, n.x, n.y, n.z, 0, -0.25, 0);
				mc.level.addParticle(GREEN, n.x, n.y - 0.2, n.z, 0, -0.1, 0);
			}
		}
	}

	private static void footfall(Minecraft mc, HardLightMechEntity m, double side, float power, RandomSource rnd) {
		Vec3 f = m.local(side, 0.05, 0.2);
		BlockPos below = BlockPos.containing(f.x, f.y - 0.3, f.z);
		BlockState ground = mc.level.getBlockState(below);
		mc.level.playLocalSound(f.x, f.y, f.z, SoundEvents.IRON_GOLEM_STEP, SoundSource.NEUTRAL, 1.6f * power, 0.5f + rnd.nextFloat() * 0.1f, false);
		mc.level.playLocalSound(f.x, f.y, f.z, SoundEvents.AMETHYST_BLOCK_STEP, SoundSource.NEUTRAL, 0.9f * power, 0.5f, false);
		if (!ground.isAir()) {
			for (int i = 0; i < 10 * power; i++) {
				mc.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), f.x + (rnd.nextDouble() - 0.5) * 1.4, f.y + 0.1,
						f.z + (rnd.nextDouble() - 0.5) * 1.4, (rnd.nextDouble() - 0.5) * 0.3, 0.15, (rnd.nextDouble() - 0.5) * 0.3);
			}
		}
		for (int i = 0; i < 4; i++) {
			mc.level.addParticle(GREEN, f.x + (rnd.nextDouble() - 0.5) * 1.2, f.y + 0.1, f.z + (rnd.nextDouble() - 0.5) * 1.2, 0, 0.02, 0);
		}
		addShake(mc.player, f, 0.9f * power, 24.0);
	}
}
