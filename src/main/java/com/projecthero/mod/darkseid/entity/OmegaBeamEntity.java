package com.projecthero.mod.darkseid.entity;

import java.util.UUID;

import com.projecthero.mod.darkseid.DarkseidDamage;
import com.projecthero.mod.darkseid.DarkseidSounds;
import com.projecthero.mod.squad.SquadManager;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * One of Darkseid's two Omega Beams (also fired, weaker, by the Omega Relic). It leaves the eye already turning
 * toward its target and keeps steering for {@link #trackTicks}, bending around corners and climbing after flying
 * players -- but only by a limited number of degrees per tick. That turn limit is the whole counterplay: a sharp
 * change of direction, a dive behind a wall or simply out-flying the curve makes it overshoot, and blocks stop it
 * dead. Once tracking runs out it flies straight until it hits something or fizzles.
 */
public class OmegaBeamEntity extends EnergyProjectile {
	private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1.0f, 0.08f, 0.04f), 2.0f);

	private UUID targetId;
	private LivingEntity cachedTarget;
	private int trackTicks;
	private double turnDegrees = 6.0;
	private double speed = 0.9;
	private float damage = 12.0f;
	private int maxLife = 120;

	public OmegaBeamEntity(EntityType<? extends OmegaBeamEntity> type, Level level) {
		super(type, level);
	}

	/** Spawn a beam at {@code from} heading along {@code initialDir}, homing on {@code target}. */
	public static OmegaBeamEntity fire(ServerLevel level, Entity owner, Vec3 from, Vec3 initialDir, LivingEntity target,
			float damage, double speed, double turnDegrees, int trackTicks) {
		OmegaBeamEntity beam = new OmegaBeamEntity(DarkseidEntityTypes.OMEGA_BEAM, level);
		beam.setOwner(owner);
		beam.setPos(from.x, from.y, from.z);
		beam.cachedTarget = target;
		beam.targetId = target == null ? null : target.getUUID();
		beam.damage = damage;
		beam.speed = speed;
		beam.turnDegrees = turnDegrees;
		beam.trackTicks = trackTicks;
		beam.maxLife = trackTicks + 50;
		beam.setDeltaMovement(initialDir.normalize().scale(speed));
		beam.faceAlong(beam.getDeltaMovement());
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

	@Override
	protected int maxLife() {
		return maxLife;
	}

	@Override
	protected void steer(ServerLevel level) {
		Vec3 v = getDeltaMovement();
		// accelerate gently to 125% over the first second -- the launch is readable, the chase is not slow
		double wantSpeed = speed * Math.min(1.25, 1.0 + life / 80.0);
		if (life <= trackTicks) {
			LivingEntity target = target(level);
			if (target != null) {
				Vec3 aim = target.position().add(0.0, target.getBbHeight() * 0.55, 0.0).subtract(position());
				v = turnToward(v, aim, turnDegrees);
			}
		}
		setDeltaMovement(v.normalize().scale(wantSpeed));
		if (tickCount % 2 == 0) {
			level.sendParticles(RED, getX(), getY(), getZ(), 1, 0.05, 0.05, 0.05, 0.0);
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
					&& SquadManager.get(server).sameSquad(player.getUUID(), other.getUUID())) {
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
