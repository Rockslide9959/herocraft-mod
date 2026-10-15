package com.projecthero.mod.hulk;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hulk.data.HulkState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.4 (user): X -- Super Leap. Hold X up to {@code superLeapChargeTicks} (2 s; the leap bar above the hotbar
 * fills); a tap goes {@code superLeapTapBlocks} (25), half charge {@code superLeapHalfBlocks} (100), full
 * {@code superLeapFullBlocks} (500), along the look direction.
 *
 * <p>The flight has NO air drag: the launch impulse is solved for a drag-free arc ({@link #launchVelocity}) and, while
 * {@link HulkState#leaping}, the owner's client holds his horizontal speed at the launch value and takes vanilla's
 * vertical drag back off each tick ({@link #cancelDrag}) -- only gravity shapes the arc, so he never slows until he
 * lands. The launch angle follows his look but is kept between {@code superLeapMinAngle} and {@code superLeapMaxAngle};
 * the horizontal speed is capped ({@code superLeapMaxSpeed}, a steeper arc instead) so the chunks ahead keep up, and the
 * climb is capped ({@code superLeapMaxClimb}) to stay under the server's 80-tick floating check. Movement stays the
 * client's, so his own chunk loading works as normal: a chunk that is not there yet holds him in place until it arrives.
 * Take-off and landing crack the ground; the landing shockwave scales with the leap. No fall damage (the Hulk takes none).
 */
public final class HulkLeap {
	/** Vanilla's per-tick air drag on a player (vertical; horizontal is 0.91 in air). */
	public static final double VERTICAL_DRAG = 0.98;

	private static final Map<UUID, Long> TAKEOFF = new ConcurrentHashMap<>();
	private static final Map<UUID, Double> BLOCKS = new ConcurrentHashMap<>();

	private HulkLeap() {
	}

	static void clearSessionState() {
		TAKEOFF.clear();
		BLOCKS.clear();
	}

	static void clear(UUID id) {
		TAKEOFF.remove(id);
		BLOCKS.remove(id);
	}

	// ---------------------------------------------------------------- the numbers

	/** Charge 0..1 -> blocks: tap 25, half 100, full 500 (straight lines in between). */
	public static double blocksFor(float charge) {
		HulkConfig.Abilities c = HulkConfig.abilities();
		double t = Math.max(0.0, Math.min(1.0, charge));
		if (t <= 0.5) {
			return c.superLeapTapBlocks + (c.superLeapHalfBlocks - c.superLeapTapBlocks) * (t / 0.5);
		}
		return c.superLeapHalfBlocks + (c.superLeapFullBlocks - c.superLeapHalfBlocks) * ((t - 0.5) / 0.5);
	}

	/** Charge 0..1 of the current Super Leap (client-safe). */
	public static float charge(Player player) {
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		if (s == null || s.leapChargeStart <= 0L) {
			return 0.0f;
		}
		return Math.min(1.0f, (player.level().getGameTime() - s.leapChargeStart)
				/ (float) Math.max(1, HulkConfig.abilities().superLeapChargeTicks));
	}

	/**
	 * The launch velocity of a drag-free arc that comes down {@code blocks} away (on level ground) along {@code look}'s
	 * heading. Discrete ticks: position += v, then v.y -= g -- so the range is vh * (2 vy / g + 1).
	 */
	public static Vec3 launchVelocity(Vec3 look, double blocks, double gravity) {
		HulkConfig.Abilities c = HulkConfig.abilities();
		return dragFreeArc(look, blocks, gravity, c.superLeapMinAngle, c.superLeapMaxAngle, c.superLeapMaxSpeed, c.superLeapMaxClimb);
	}

	/**
	 * The general drag-free arc: heading from {@code look}, angle from its pitch kept in [{@code minAngle},
	 * {@code maxAngle}] degrees, horizontal speed at most {@code maxSpeed} (a steeper arc instead) and climb speed at most
	 * {@code maxClimb} (a flatter, faster one instead). Also the V far throw.
	 */
	public static Vec3 dragFreeArc(Vec3 look, double blocks, double gravity, double minAngle, double maxAngle, double maxSpeed, double maxClimb) {
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : flat.normalize();
		double pitchUp = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, look.normalize().y))));
		double angle = Math.toRadians(Math.max(minAngle, Math.min(maxAngle, pitchUp)));
		double g = Math.max(1.0e-3, gravity);
		double sin = Math.sin(angle);
		double cos = Math.cos(angle);
		// (2 sin cos / g) v^2 + cos v - R = 0
		double a = 2.0 * sin * cos / g;
		double v = (-cos + Math.sqrt(cos * cos + 4.0 * a * blocks)) / (2.0 * a);
		double vh = v * cos;
		double vy = v * sin;
		if (vh > maxSpeed) {
			// too fast for the chunks ahead: the same distance on a steeper, slower arc
			vh = maxSpeed;
			vy = g * (blocks / vh - 1.0) / 2.0;
		}
		if (vy > maxClimb) {
			vy = maxClimb;
			vh = blocks / (2.0 * vy / g + 1.0);
		}
		return new Vec3(flat.x * vh, vy, flat.z * vh);
	}

	/**
	 * After vanilla's air step: hold the launch's horizontal speed and take the vertical drag back off -- the client calls
	 * this every tick of the leap (and the tests simulate with it).
	 */
	public static Vec3 cancelDrag(Vec3 afterVanilla, HulkState.Moves moves) {
		return new Vec3(moves.leapVx, afterVanilla.y / VERTICAL_DRAG, moves.leapVz);
	}

	/** Where a drag-free leap with {@code v} comes down on level ground: the horizontal distance (tests, tuning). */
	public static double simulateRange(Vec3 v, double gravity, int maxTicks) {
		HulkState.Moves m = new HulkState.Moves();
		m.leapVx = v.x;
		m.leapVy = v.y;
		m.leapVz = v.z;
		Vec3 pos = Vec3.ZERO;
		Vec3 vel = v;
		for (int t = 0; t < maxTicks; t++) {
			pos = pos.add(vel);
			if (t > 0 && pos.y <= 0.0) {
				break;
			}
			// vanilla airborne step, then the leap takes the drag back off
			Vec3 vanilla = new Vec3(vel.x * 0.91, (vel.y - gravity) * VERTICAL_DRAG, vel.z * 0.91);
			vel = cancelDrag(vanilla, m);
		}
		return Math.sqrt(pos.x * pos.x + pos.z * pos.z);
	}

	// ---------------------------------------------------------------- X held / released

	/** X pressed: start charging (on the ground only). */
	public static void begin(ServerPlayer player) {
		if (!HulkAbilities.canAct(player)) {
			return;
		}
		HulkState s = Hulk.state(player);
		if (s.leapChargeStart > 0L || s.leaping || !HulkAbilities.ready(player, HulkAbilities.SUPER_LEAP)) {
			return;
		}
		if (!player.onGround()) {
			Hulk.say(player, "message.projecthero.hulk.leap_ground", ChatFormatting.GRAY);
			return;
		}
		HulkState n = s.copy();
		n.leapChargeStart = player.level().getGameTime();
		n.animId = HulkState.ANIM_LEAP_CHARGE;
		n.animStart = n.leapChargeStart;
		Hulk.save(player, n);
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_STEP, SoundSource.PLAYERS, 1.2f, 0.5f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0f, 1.2f);
		GroundCracks.crackAt(level, player.position(), 4, 40);
	}

	/** X released: launch along the look direction, farther the longer it was held. */
	public static void release(ServerPlayer player) {
		HulkState s = Hulk.state(player);
		if (s.leapChargeStart <= 0L) {
			return;
		}
		float charge = charge(player);
		HulkState n = s.copy();
		n.leapChargeStart = 0L;
		if (!Hulk.isHulk(player) || !player.isAlive()) {
			Hulk.save(player, n);
			return;
		}
		launch(player, n, blocksFor(charge), player.getLookAngle());
		n.abilityReadyAt.put(HulkAbilities.SUPER_LEAP, n.animStart + HulkConfig.abilities().leapCooldownTicks);
		Hulk.save(player, n);
	}

	/** Throws the Hulk {@code blocks} along {@code dir} (also the rampage). Mutates {@code n}; the caller saves it. */
	static void launch(ServerPlayer player, HulkState n, double blocks, Vec3 dir) {
		Vec3 v = launchVelocity(dir, blocks, player.getGravity());
		n.leaping = true;
		n.moves.leapVx = v.x;
		n.moves.leapVy = v.y;
		n.moves.leapVz = v.z;
		n.animId = HulkState.ANIM_LEAP_LAUNCH;
		n.animStart = player.level().getGameTime();
		TAKEOFF.put(player.getUUID(), n.animStart);
		BLOCKS.put(player.getUUID(), blocks);
		AbilityHelpers.launchSelf(player, v);
		ServerLevel level = (ServerLevel) player.level();
		double t = scale(blocks);
		Vec3 at = player.position();
		// the ground he pushed off cracks and bursts
		GroundCracks.star(level, at, 2.5 + 3.5 * t, 7, 100);
		BlockState ground = level.getBlockState(player.blockPosition().below());
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.1, at.z, 50 + (int) (60 * t), 1.2, 0.1, 1.2, 0.3);
			level.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, ground), at.x, at.y + 0.1, at.z, 20, 1.0, 0.1, 1.0, 0.3);
		}
		level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, at.x, at.y + 0.2, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.1, at.z, 30, 1.0, 0.1, 1.0, 0.08);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0f + (float) t, 1.3f - 0.4f * (float) t);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 1.4f, 0.6f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.7f, 1.2f);
		HulkCombat.shake(level, at, 0.3f + 0.4f * (float) t, 10, 20.0);
	}

	/** 0..1: how big a leap was, for the take-off and the landing (25 blocks = 0, 500 = 1). */
	static double scale(double blocks) {
		HulkConfig.Abilities c = HulkConfig.abilities();
		double span = Math.max(1.0, c.superLeapFullBlocks - c.superLeapTapBlocks);
		return Math.sqrt(Math.max(0.0, Math.min(1.0, (blocks - c.superLeapTapBlocks) / span)));
	}

	// ---------------------------------------------------------------- in the air / landing

	/** Every Hulk tick: the charge left running, the trail, and the landing. */
	static void tick(ServerPlayer player, long now) {
		HulkState s = Hulk.state(player);
		// a charge left running (key lost) launches itself after a few seconds
		if (s.leapChargeStart > 0L && now - s.leapChargeStart > HulkConfig.abilities().superLeapChargeTicks + 100) {
			release(player);
			s = Hulk.state(player);
		}
		if (s.leapChargeStart > 0L && now % 3 == 0) {
			// crouched and straining: the ground under him splits as the charge builds
			float c = charge(player);
			ServerLevel level = (ServerLevel) player.level();
			GroundCracks.star(level, player.position(), 1.0 + 2.0 * c, 4, 40);
			level.sendParticles(HulkAbilities.GAMMA, player.getX(), player.getY() + 0.3, player.getZ(), 2 + (int) (6 * c), 0.6, 0.1, 0.6, 0.02);
		}
		if (!s.leaping) {
			return;
		}
		player.resetFallDistance();
		Long off = TAKEOFF.get(player.getUUID());
		ServerLevel level = (ServerLevel) player.level();
		if (now % 2 == 0 && !player.onGround()) {
			level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + player.getBbHeight() * 0.4, player.getZ(), 1, 0.2, 0.2, 0.2, 0.0);
		}
		boolean settled = player.onGround() || player.isInWater() || player.isPassenger() || player.getAbilities().flying;
		if (settled && (off == null || now - off > 4)) {
			HulkState n = s.copy();
			n.leaping = false;
			n.moves.leapVx = 0.0;
			n.moves.leapVy = 0.0;
			n.moves.leapVz = 0.0;
			boolean landed = player.onGround();
			if (landed) {
				n.animId = HulkState.ANIM_LEAP_LAND;
				n.animStart = now;
			}
			Hulk.save(player, n);
			TAKEOFF.remove(player.getUUID());
			Double blocks = BLOCKS.remove(player.getUUID());
			if (landed) {
				land(player, blocks == null ? HulkConfig.abilities().superLeapTapBlocks : blocks);
			}
		}
	}

	/** The landing: a shockwave, a crater and cracks that grow with the leap. */
	static void land(ServerPlayer player, double blocks) {
		HulkConfig.Abilities c = HulkConfig.abilities();
		double t = scale(blocks);
		double radius = c.superLeapLandRadiusMin + (c.superLeapLandRadiusMax - c.superLeapLandRadiusMin) * t;
		float damage = (float) (c.superLeapLandDamageMin + (c.superLeapLandDamageMax - c.superLeapLandDamageMin) * t);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 at = player.position();
		player.resetFallDistance();
		HulkAbilities.shockwave(player, at, radius, damage, 1.0 + 1.2 * t, 0.45 + 0.5 * t, 0.6f + 0.7f * (float) t);
		GroundCracks.star(level, at, radius * 1.1 * WorldBreaker.aoe(player), 8 + (int) (6 * t), 120);
		if (t > 0.3) {
			HulkCombat.crater(player, at.add(0, -0.5, 0), 1.5 + 2.5 * t, (int) (20 + 120 * t));
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.3, at.z, 1, 0, 0, 0, 0);
		}
		level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, at.x, at.y + 0.3, at.z, 1, 0, 0, 0, 0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 2.0f + 2.0f * (float) t, 0.7f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 1.5f, 0.6f);
	}
}
