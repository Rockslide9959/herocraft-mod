package com.projecthero.mod.ironman;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.network.TitanShakePayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user): "give the hulkbuster suit a landing an animation when landing after flying". When the Hulkbuster
 * ({@code mark_8}) touches down after flight -- or after a big fall -- the server picks the landing from how hard it came
 * in and tells every client (the {@code IronManPosePayload}, so everyone sees it):
 * <ul>
 *   <li><b>Heavy</b> ({@link IronManAbilityFx#HB_LAND_HEAVY}, {@value #HEAVY_TICKS} ticks): the three-point superhero
 *       landing -- slammed into a deep lunge, right knee and right fist in the ground, the look-up, then the rise.</li>
 *   <li><b>Light</b> ({@link IronManAbilityFx#HB_LAND_LIGHT}, {@value #LIGHT_TICKS} ticks): a gentle hover touchdown, the
 *       knees soak it up.</li>
 * </ul>
 * Both raise a dust ring and a ring of the ground's block particles, with a heavy thud and a short camera shake for
 * players nearby -- all scaled by the landing's {@linkplain #intensity intensity}. The pose holds the wearer only until
 * the impact frame ({@code HulkbusterLandingClient}: no input for {@value #IMPACT_TICKS} ticks); after it, moving cancels
 * the rest of the clip.
 */
public final class HulkbusterLanding {
	public static final String SUIT = "mark_8";
	public static final int HEAVY_TICKS = 23; // superhero_land is 1.15 s
	public static final int LIGHT_TICKS = 12; // touchdown_land is 0.6 s
	/** The heavy clip's impact frame (0.15 s); movement cancels the clip only after it. */
	public static final int IMPACT_TICKS = 3;
	/** A fall at least this high (blocks) lands with the animation. */
	public static final double FALL_MIN = 4.0;
	/** Intensity at or above which a landing is the heavy one. */
	public static final float HEAVY_AT = 0.4f;

	/** The last landing per player (gametests / debugging). */
	public record Landing(long time, float intensity, boolean heavy) {
	}

	private static final Map<UUID, Landing> LAST = new HashMap<>();
	private static final Map<UUID, Double> FALL_PEAK = new HashMap<>();

	private HulkbusterLanding() {
	}

	/** Is {@code p} wearing the whole Hulkbuster (the body that has the landing)? */
	public static boolean suited(ServerPlayer p) {
		return SUIT.equals(IronManArmor.wornSuitId(p)) && IronManArmor.wearingFullSuit(p, SUIT);
	}

	/**
	 * 0..1 from the flight's peak per-tick speed and descent (blocks / tick) over its last moments: a hover touchdown is
	 * ~0, a dive into the ground at full speed is 1.
	 */
	public static float intensity(double peakSpeed, double peakDescent) {
		double byDescent = (peakDescent - 0.08) / 0.75;
		double bySpeed = (peakSpeed - 0.35) / 1.6;
		return (float) Mth.clamp(Math.max(byDescent, bySpeed), 0.0, 1.0);
	}

	/** 0..1 for a fall of {@code height} blocks (4 = light-ish, 14+ = full). */
	public static float fallIntensity(double height) {
		return (float) Mth.clamp((height - 2.5) / 11.5, 0.0, 1.0);
	}

	/** Flight ended on the ground ({@code IronManLandingSlam.onLanded}): land with the motion it came in with. */
	public static void onFlightLanded(ServerPlayer p, double peakSpeed, double peakDescent) {
		if (!suited(p)) {
			return;
		}
		FALL_PEAK.remove(p.getUUID());
		float i = intensity(peakSpeed, peakDescent);
		land(p, i, i >= HEAVY_AT || IronManFlightLook.isHardLanding(peakSpeed, peakDescent, true));
	}

	/** Every server tick: track a non-flying fall and land it when it is big enough. */
	public static void tick(ServerPlayer p) {
		UUID id = p.getUUID();
		if (!suited(p) || IronManFlight.isFlying(p) || p.getAbilities().flying || p.isInWater() || p.isPassenger()
				|| p.onClimbable()) {
			FALL_PEAK.remove(id);
			return;
		}
		if (!p.onGround()) {
			FALL_PEAK.merge(id, p.getY(), Math::max);
			return;
		}
		Double peak = FALL_PEAK.remove(id);
		if (peak != null && peak - p.getY() >= FALL_MIN) {
			float i = fallIntensity(peak - p.getY());
			land(p, i, i >= HEAVY_AT);
		}
	}

	/** Play the landing: the clip for everyone, the dust / block ring, the thud and the shake. Public for the gametests. */
	public static void land(ServerPlayer p, float intensity, boolean heavy) {
		long now = p.level().getGameTime();
		Landing prev = LAST.get(p.getUUID());
		if (prev != null && now - prev.time() < 6) {
			return; // one landing per touchdown
		}
		LAST.put(p.getUUID(), new Landing(now, intensity, heavy));
		IronManAbilityFx.play(p, heavy ? IronManAbilityFx.HB_LAND_HEAVY : IronManAbilityFx.HB_LAND_LIGHT, heavy ? HEAVY_TICKS : LIGHT_TICKS);
		if (!(p.level() instanceof ServerLevel level)) {
			return;
		}
		float k = heavy ? Math.max(intensity, HEAVY_AT) : intensity * 0.6f;
		Vec3 feet = p.position();
		BlockState ground = groundUnder(level, feet);
		// the dust ring rolling out from the feet
		int dust = 10 + Math.round(26 * k);
		double reach = 0.9 + 2.2 * k;
		for (int n = 0; n < dust; n++) {
			double a = Math.PI * 2 * n / dust + p.getRandom().nextDouble() * 0.2;
			double cx = Math.cos(a), cz = Math.sin(a);
			level.sendParticles(ParticleTypes.POOF, feet.x + cx * 0.8, feet.y + 0.1, feet.z + cz * 0.8, 0, cx, 0.02, cz, 0.12 + 0.25 * k);
			if (heavy || n % 2 == 0) {
				// the low dust wave rolling out along the ground (short-lived -- no lingering smoke columns)
				level.sendParticles(ParticleTypes.CLOUD, feet.x + cx * 0.6, feet.y + 0.12, feet.z + cz * 0.6, 0, cx, 0.01, cz,
						0.18 + 0.22 * k);
			}
		}
		// the ground's own blocks kicked up in a ring
		if (ground != null) {
			BlockParticleOption block = new BlockParticleOption(ParticleTypes.BLOCK, ground);
			int ring = 12 + Math.round(30 * k);
			for (int n = 0; n < ring; n++) {
				double a = Math.PI * 2 * n / ring;
				level.sendParticles(block, feet.x + Math.cos(a) * reach, feet.y + 0.1, feet.z + Math.sin(a) * reach, 2, 0.15, 0.05, 0.15,
						0.1 + 0.15 * k);
			}
		}
		if (heavy) {
			// the fist hits the ground just ahead of the right shoulder: a crack of debris and a dust plume there
			float yaw = p.getYRot() * Mth.DEG_TO_RAD;
			Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
			Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
			Vec3 fist = feet.add(fwd.scale(0.55)).add(right.scale(0.5));
			level.sendParticles(ParticleTypes.EXPLOSION, fist.x, fist.y + 0.2, fist.z, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.DUST_PLUME, fist.x, fist.y + 0.1, fist.z, 2, 0.15, 0.05, 0.15, 0.03);
			if (ground != null) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), fist.x, fist.y + 0.15, fist.z, 18, 0.25, 0.1, 0.25,
						0.3);
			}
		}
		// the thud: a deep metal-and-earth impact, louder and lower the harder it came in
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.35f + 0.6f * k,
				0.55f - 0.15f * k);
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.25f + 0.45f * k, 0.5f);
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.PLAYERS, 0.5f + 0.5f * k, 0.6f);
		if (ground != null) {
			level.playSound(null, feet.x, feet.y, feet.z, ground.getSoundType().getBreakSound(), SoundSource.PLAYERS, 0.8f + 0.6f * k, 0.6f);
		}
		// the camera shake for everyone near (the wearer too)
		double radius = 8.0 + 16.0 * k;
		for (ServerPlayer other : level.players()) {
			double d = other.position().distanceTo(feet);
			if (d <= radius) {
				float s = (0.15f + 0.85f * k) * (float) (1.0 - 0.8 * d / radius);
				ServerPlayNetworking.send(other, new TitanShakePayload(s, 6 + Math.round(8 * k)));
			}
		}
	}

	private static BlockState groundUnder(ServerLevel level, Vec3 feet) {
		for (int dy = 0; dy <= 1; dy++) {
			BlockPos pos = BlockPos.containing(feet.x, feet.y - 0.2 - dy, feet.z);
			BlockState s = level.getBlockState(pos);
			if (!s.isAir() && s.getRenderShape() != RenderShape.INVISIBLE) {
				return s;
			}
		}
		return null;
	}

	/** The last landing {@code p} made, or null (gametests). */
	public static Landing last(ServerPlayer p) {
		return LAST.get(p.getUUID());
	}

	/** Server stopped: drop the per-player maps (ServerStateReset). */
	public static void clearSessionState() {
		LAST.clear();
		FALL_PEAK.clear();
	}
}
