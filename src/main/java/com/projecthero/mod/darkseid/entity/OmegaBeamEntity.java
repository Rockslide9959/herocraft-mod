package com.projecthero.mod.darkseid.entity;

import java.util.UUID;

import com.projecthero.mod.darkseid.DarkseidConfig;
import com.projecthero.mod.darkseid.DarkseidDamage;
import com.projecthero.mod.darkseid.DarkseidSounds;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * One of Darkseid's two Omega Beams (also fired, weaker, by the Omega Relic).
 *
 * <p><b>v0.13.19 -- the zig-zag.</b> Like the films and cartoons, Darkseid's beams no longer simply curve: for the first
 * {@code zigzagTurns} legs they snake toward their target in sharp angular kinks -- each leg breaks away from the
 * straight line by a random 35-70 degrees, alternating sides and planes (up, down, left, right), and snaps to a new
 * heading every few ticks. The legs still make net progress toward the target (and run a little faster) so the beams
 * arrive in reasonable time; a leg that would run straight into a wall picks another heading, and within a few blocks
 * of the target the zig-zag stops. Then they home in as before.
 *
 * <p>Homing: the beam keeps steering for {@link #trackTicks} after the zig-zag, bending around corners and climbing after
 * flying players -- but only by a limited number of degrees per tick. That turn limit is the counterplay: a sharp change
 * of direction, a dive behind a wall or simply out-flying the curve makes it overshoot, and blocks stop it dead. Once
 * tracking runs out it flies straight until it hits something or fizzles.
 *
 * <p>The whole path -- every kink -- is drawn by {@code EnergyTrailRenderer} (the Omega Beam's glowing red ribbon, kept
 * {@link #TRAIL} ticks long so it reads as one continuous bent beam), not by particles: the only particles are a small
 * flash at each kink.
 */
public class OmegaBeamEntity extends EnergyProjectile {
	private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1.0f, 0.08f, 0.04f), 2.0f);
	/** Ribbon length in ticks: long enough that a zig-zagging beam reads as one continuous bent beam of light. */
	public static final int TRAIL = 30;

	private UUID targetId;
	private LivingEntity cachedTarget;
	private int trackTicks;
	private double turnDegrees = 6.0;
	private double speed = 0.9;
	private float damage = 12.0f;
	private int maxLife = 120;

	// ---- zig-zag state (server only)
	private int zigLegsLeft;
	private int zigLegTicks;
	private int zigLegIndex;
	private double zigPhi;
	private Vec3 zigDir = Vec3.ZERO;
	/** {@code life} at which homing began (0 without a zig-zag). */
	private int homingFrom;

	public OmegaBeamEntity(EntityType<? extends OmegaBeamEntity> type, Level level) {
		super(type, level, TRAIL);
	}

	/** Spawn a beam at {@code from} heading along {@code initialDir}, homing on {@code target} (no zig-zag). */
	public static OmegaBeamEntity fire(ServerLevel level, Entity owner, Vec3 from, Vec3 initialDir, LivingEntity target,
			float damage, double speed, double turnDegrees, int trackTicks) {
		return fire(level, owner, from, initialDir, target, damage, speed, turnDegrees, trackTicks, 0);
	}

	/**
	 * Spawn a beam that first zig-zags through {@code zigzagTurns} sharp legs (see the class doc; leg lengths/angles
	 * from {@link DarkseidConfig.Abilities}), then homes on {@code target} for {@code trackTicks}.
	 */
	public static OmegaBeamEntity fire(ServerLevel level, Entity owner, Vec3 from, Vec3 initialDir, LivingEntity target,
			float damage, double speed, double turnDegrees, int trackTicks, int zigzagTurns) {
		OmegaBeamEntity beam = new OmegaBeamEntity(DarkseidEntityTypes.OMEGA_BEAM, level);
		beam.setOwner(owner);
		beam.setPos(from.x, from.y, from.z);
		beam.cachedTarget = target;
		beam.targetId = target == null ? null : target.getUUID();
		beam.damage = damage;
		beam.speed = speed;
		beam.turnDegrees = turnDegrees;
		beam.trackTicks = trackTicks;
		int legs = target == null ? 0 : Math.max(0, zigzagTurns);
		beam.zigLegsLeft = legs;
		beam.maxLife = trackTicks + 50 + legs * Math.max(1, DarkseidConfig.abilities().omegaBeamZigZagLegTicksMax);
		Vec3 dir = initialDir.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : initialDir.normalize();
		beam.setDeltaMovement(dir.scale(speed));
		beam.faceAlong(beam.getDeltaMovement());
		if (legs > 0) {
			// the first kink breaks out on the side the beam was launched toward (the two eye beams splay apart)
			Vec3 to = beam.aimPoint(target).subtract(from);
			Vec3[] basis = basis(to);
			Vec3 lateral = dir.subtract(to.normalize().scale(dir.dot(to.normalize())));
			beam.zigPhi = lateral.lengthSqr() < 1.0e-4 ? level.random.nextDouble() * Math.PI * 2.0
					: Math.atan2(lateral.dot(basis[1]), lateral.dot(basis[0]));
		}
		level.addFreshEntity(beam);
		return beam;
	}

	private LivingEntity target(ServerLevel level) {
		if (cachedTarget != null && cachedTarget.isAlive() && !cachedTarget.isRemoved()) {
			return cachedTarget;
		}
		if (targetId != null && level.getEntity(targetId) instanceof LivingEntity living && living.isAlive()) {
			cachedTarget = living;
			return living;
		}
		return null;
	}

	private Vec3 aimPoint(LivingEntity target) {
		return target.position().add(0.0, target.getBbHeight() * 0.55, 0.0);
	}

	/** True while the beam is still in its zig-zag (tests, debugging). */
	public boolean isZigZagging() {
		return zigLegsLeft > 0 || zigLegTicks > 0;
	}

	@Override
	protected int maxLife() {
		return maxLife;
	}

	@Override
	protected void steer(ServerLevel level) {
		LivingEntity target = target(level);
		if ((zigLegsLeft > 0 || zigLegTicks > 0) && target != null) {
			Vec3 to = aimPoint(target).subtract(position());
			if (to.length() < DarkseidConfig.abilities().omegaBeamZigZagStopDistance) {
				endZigZag();
			} else {
				if (zigLegTicks <= 0) {
					if (zigLegsLeft <= 0) {
						endZigZag();
					} else {
						startLeg(level, target, to);
					}
				}
				if (zigLegTicks > 0) {
					zigLegTicks--;
					double zigSpeed = speed * Math.max(1.0, DarkseidConfig.abilities().omegaBeamZigZagSpeedMultiplier);
					setDeltaMovement(zigDir.scale(zigSpeed));
					if (zigLegTicks == 0 && zigLegsLeft <= 0) {
						endZigZag(); // that was the last leg: home from the next tick
					}
					return;
				}
			}
		} else if (zigLegsLeft > 0 || zigLegTicks > 0) {
			endZigZag(); // target gone: fly on
		}
		Vec3 v = getDeltaMovement();
		// accelerate gently to 125% over the first second of homing -- the launch is readable, the chase is not slow
		int homing = life - homingFrom;
		double wantSpeed = speed * Math.min(1.25, (zigLegIndex > 0 ? 1.15 : 1.0) + homing / 80.0);
		if (homing <= trackTicks && target != null) {
			v = turnToward(v, aimPoint(target).subtract(position()), turnDegrees);
		}
		setDeltaMovement(v.normalize().scale(wantSpeed));
		if (tickCount % 4 == 0) {
			level.sendParticles(RED, getX(), getY(), getZ(), 1, 0.05, 0.05, 0.05, 0.0);
		}
	}

	private void endZigZag() {
		zigLegsLeft = 0;
		zigLegTicks = 0;
		homingFrom = life;
	}

	/** Orthonormal pair perpendicular to {@code dir}: [0] horizontal "side", [1] the "up" of that plane. */
	private static Vec3[] basis(Vec3 dir) {
		Vec3 d = dir.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : dir.normalize();
		Vec3 side = d.cross(new Vec3(0, 1, 0));
		if (side.lengthSqr() < 1.0e-4) {
			side = new Vec3(1, 0, 0);
		}
		side = side.normalize();
		Vec3 up = side.cross(d).normalize();
		return new Vec3[] { side, up };
	}

	/** Snap to the next leg's heading: a sharp kink away from the line to the target, alternating sides. */
	private void startLeg(ServerLevel level, LivingEntity target, Vec3 to) {
		DarkseidConfig.Abilities cfg = DarkseidConfig.abilities();
		Vec3 dir = to.normalize();
		Vec3[] b = basis(dir);
		if (zigLegIndex > 0) {
			// the other side of the line, give or take ~50 degrees -- zig, then zag, in any plane
			zigPhi += Math.PI + (random.nextDouble() - 0.5) * Math.toRadians(100.0);
		}
		double min = Math.min(cfg.omegaBeamZigZagAngleMin, cfg.omegaBeamZigZagAngleMax);
		double max = Math.max(cfg.omegaBeamZigZagAngleMin, cfg.omegaBeamZigZagAngleMax);
		double angle = Math.toRadians(Math.max(0.0, Math.min(85.0, min + random.nextDouble() * (max - min))));
		boolean lowOverTarget = getY() - target.getY() < 2.0;
		Vec3 chosen = null;
		for (int attempt = 0; attempt < 4 && chosen == null; attempt++) {
			double phi = zigPhi + attempt * (Math.PI / 2.0);
			Vec3 perp = b[0].scale(Math.cos(phi)).add(b[1].scale(Math.sin(phi)));
			if (lowOverTarget && perp.y < -0.2) {
				// never dive into the floor next to a grounded target: mirror the kink upward
				phi = -phi;
				perp = b[0].scale(Math.cos(phi)).add(b[1].scale(Math.sin(phi)));
			}
			Vec3 legDir = dir.scale(Math.cos(angle)).add(perp.scale(Math.sin(angle))).normalize();
			// a leg straight into a wall would just waste the attack: look ~4 blocks down it first
			Vec3 probe = position().add(legDir.scale(4.0));
			if (level.clip(new ClipContext(position(), probe, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
					.getType() == HitResult.Type.MISS) {
				chosen = legDir;
				zigPhi = phi;
			}
		}
		if (chosen == null) {
			// boxed in: skip the rest of the zig-zag and home
			endZigZag();
			return;
		}
		zigDir = chosen;
		int lo = Math.max(1, Math.min(cfg.omegaBeamZigZagLegTicksMin, cfg.omegaBeamZigZagLegTicksMax));
		int hi = Math.max(lo, Math.max(cfg.omegaBeamZigZagLegTicksMin, cfg.omegaBeamZigZagLegTicksMax));
		zigLegTicks = lo + random.nextInt(hi - lo + 1);
		zigLegsLeft--;
		zigLegIndex++;
		faceAlong(chosen);
		if (zigLegIndex > 1) {
			// the kink itself: a tiny flash where the beam snaps to its new heading
			level.sendParticles(RED, getX(), getY(), getZ(), 4, 0.1, 0.1, 0.1, 0.0);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 2, 0.05, 0.05, 0.05, 0.05);
		}
	}

	@Override
	protected boolean canHit(Entity target) {
		Entity owner = getOwner();
		if (target == owner || target instanceof EnergyProjectile) {
			return false;
		}
		if (owner instanceof Player player) {
			// the Omega Relic: never the wielder's squad
			if (target instanceof Player other && level() instanceof ServerLevel server
					&& com.projecthero.mod.squad.Squads.areAllies(player, other)) {
				return false;
			}
			return target instanceof LivingEntity && !(target instanceof Player p && p.isCreative());
		}
		return DarkseidDamage.isValidVictim(target);
	}

	@Override
	protected void onStrikeEntity(ServerLevel level, Entity target) {
		if (target instanceof LivingEntity living) {
			Entity owner = getOwner();
			living.hurt(DarkseidDamage.omega(level, this, owner), damage);
			DarkseidDamage.knockAway(living, position().subtract(getDeltaMovement()), 0.6, 0.25);
		}
		impact(level);
	}

	@Override
	protected void onStrikeBlock(ServerLevel level, Vec3 at) {
		setPos(at.x, at.y, at.z);
		impact(level);
	}

	private void impact(ServerLevel level) {
		burst(level, position(), ParticleTypes.FLASH, 1);
		level.sendParticles(RED, getX(), getY(), getZ(), 18, 0.4, 0.4, 0.4, 0.0);
		level.sendParticles(ParticleTypes.LAVA, getX(), getY(), getZ(), 4, 0.2, 0.2, 0.2, 0.0);
		level.playSound(null, getX(), getY(), getZ(), DarkseidSounds.OMEGA_IMPACT, SoundSource.HOSTILE, 1.4f,
				0.9f + random.nextFloat() * 0.2f);
	}

	@Override
	public int trailColor() {
		return 0xE01008;
	}

	@Override
	public int coreColor() {
		return 0xFF5030;
	}

	@Override
	public float trailWidth() {
		return 0.22f;
	}
}
