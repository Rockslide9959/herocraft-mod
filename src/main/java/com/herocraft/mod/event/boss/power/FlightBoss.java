package com.herocraft.mod.event.boss.power;

import com.herocraft.mod.event.boss.BossPowerController;
import com.herocraft.mod.event.entity.EmpoweredZombie;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Flight: the answer to Thor, Iron Man and every experimental flier -- and a boss that genuinely
 * fights in three dimensions.
 *
 * <h2>How it fights</h2>
 * <ul>
 *   <li><b>Takes off on its own</b> once its wings are ready, climbing to hold station well above the
 *       target where ground attacks cannot reach it.</li>
 *   <li><b>Weaves while airborne</b> -- a constant lateral juke that makes it hard to hit with
 *       projectiles, the "dodges attacks" behaviour.</li>
 *   <li><b>Dive-bombs</b> straight down onto the target for a heavy area hit, then climbs back up.</li>
 *   <li><b>Is driven back to the ground when it is hit</b> in the air, and kept there for a few
 *       seconds (and slowed while grounded) -- so shooting it down is the counterplay, and it is
 *       faster in the air than on the ground by design.</li>
 * </ul>
 *
 * <p>Flight is disabling gravity and steering velocity directly. The moment flight ends -- landing,
 * or being shot down -- gravity is restored, so there is no state that can strand it hovering.
 */
public class FlightBoss extends BossPowerController {
	public static final String POWER_KEY = "power_03_flight";

	private static final int SLOT_TAKEOFF = 0;
	private static final int SLOT_DIVE = 1;
	private static final int SLOT_BUFFET = 2;
	private static final int SLOT_GUST = 3;

	private static final double AIR_SPEED = 0.55;
	/** How high above the target it tries to hold station. */
	private static final double HOVER_HEIGHT = 8.0;

	private boolean flying;
	/** While {@code boss.tickCount} is below this, it has been shot down and cannot take off. */
	private int groundedUntil;
	/** Alternating juke direction. */
	private int weavePhase;

	public FlightBoss(EmpoweredZombie boss) {
		super(boss);
	}

	@Override
	public String powerKey() {
		return POWER_KEY;
	}

	@Override
	public double preferredRange() {
		return flying ? 4.0 : 2.5;
	}

	@Override
	public ParticleOptions auraParticle() {
		return ParticleTypes.CLOUD;
	}

	@Override
	public BossEvent.BossBarColor barColor() {
		return BossEvent.BossBarColor.WHITE;
	}

	@Override
	public void tick(ServerLevel level, LivingEntity target) {
		boolean forcedDown = boss.tickCount < groundedUntil;

		if (flying) {
			flyAndFight(level, target);
			return;
		}

		if (forcedDown) {
			// Shot down: it is stuck on the ground and slowed. Still not harmless -- it uses its
			// grounded wind kit while it recovers.
			boss.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 24, 0, false, false));
			groundedKit(level, target);
			return;
		}

		// Take off proactively the moment it can -- it wants to be in the air, not just when kited.
		if (ready(SLOT_TAKEOFF) && boss.distanceTo(target) < 40.0) {
			startFlying(level);
			startCooldown(SLOT_TAKEOFF, 50);
			return;
		}
		groundedKit(level, target);
	}

	// ---------------- airborne ----------------

	private void flyAndFight(ServerLevel level, LivingEntity target) {
		boss.setNoGravity(true);
		boss.getNavigation().stop();
		boss.resetFallDistance();

		// Hold station above the target, plus a constant weave so it is a hard shot.
		weavePhase++;
		Vec3 want = target.position().add(0, HOVER_HEIGHT, 0);
		Vec3 to = want.subtract(boss.position());
		Vec3 lateral = new Vec3(-to.z, 0, to.x);
		if (lateral.lengthSqr() > 1.0e-4) {
			lateral = lateral.normalize().scale((weavePhase % 2 == 0 ? 1 : -1) * 0.35);
		}
		if (to.lengthSqr() > 1.0e-4) {
			Vec3 v = to.normalize().scale(AIR_SPEED).add(lateral);
			boss.setDeltaMovement(boss.getDeltaMovement().scale(0.5).add(v));
			boss.hasImpulse = true;
		}

		if (ready(SLOT_DIVE) && boss.getY() > target.getY() + 3.5 && horizontalDistance(target) < 14.0) {
			dive(level, target);
			startCooldown(SLOT_DIVE, 110);
			return;
		}
		// A downdraft on anyone who has climbed up to meet it in the air.
		if (ready(SLOT_BUFFET) && !playersNear(level, 5.5).isEmpty() && freshChoice(SLOT_BUFFET)) {
			buffet(level);
			startCooldown(SLOT_BUFFET, 140);
		}
	}

	/** Drop onto the target: fast, straight down, a heavy area hit, then it climbs again. */
	private void dive(ServerLevel level, LivingEntity target) {
		Vec3 to = target.position().subtract(boss.position());
		boss.setDeltaMovement(to.x * 0.35, -1.4, to.z * 0.35);
		boss.hasImpulse = true;
		sound(level, SoundEvents.PHANTOM_SWOOP, 1.3f, 0.6f);
		particleLine(level, ParticleTypes.CLOUD, boss.position(), target.position(), 1.5);
		for (Player player : playersNear(level, 4.5)) {
			hurt(player, 9.0f);
			knockAway(player, target.position(), 1.4, 0.45);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY(), target.getZ(),
				2, 0.3, 0.1, 0.3, 0.0);
	}

	// ---------------- grounded ----------------

	private void groundedKit(ServerLevel level, LivingEntity target) {
		boss.setNoGravity(false);
		double distance = boss.distanceTo(target);
		if (ready(SLOT_BUFFET) && !playersNear(level, 4.5).isEmpty() && freshChoice(SLOT_BUFFET)) {
			buffet(level);
			startCooldown(SLOT_BUFFET, 150);
			return;
		}
		if (distance > 6.0 && distance < 24.0 && ready(SLOT_GUST) && boss.hasLineOfSight(target)) {
			gust(level, target);
			startCooldown(SLOT_GUST, 130);
		}
	}

	/** Downdraft: shoves everyone close back and knocks them down. Repositioning, not damage. */
	private void buffet(ServerLevel level) {
		sound(level, SoundEvents.ENDER_DRAGON_FLAP, 1.2f, 0.8f);
		level.sendParticles(ParticleTypes.CLOUD, boss.getX(), boss.getY() + 0.4, boss.getZ(),
				40, 3.0, 0.3, 3.0, 0.05);
		for (Player player : playersNear(level, 5.0)) {
			hurt(player, 4.0f);
			knockAway(player, boss.position(), 1.6, 0.5);
		}
	}

	/** A focused gust at a distant target: a line of cloud, a shove and light damage. */
	private void gust(ServerLevel level, LivingEntity target) {
		particleLine(level, ParticleTypes.CLOUD, boss.getEyePosition(),
				target.position().add(0, target.getBbHeight() * 0.5, 0), 2.0);
		sound(level, SoundEvents.BREEZE_SHOOT, 1.0f, 0.9f);
		if (boss.distanceTo(target) < 26.0) {
			hurt(target, 4.0f);
			knockAway(target, boss.position(), 1.2, 0.25);
		}
	}

	// ---------------- lifecycle ----------------

	private void startFlying(ServerLevel level) {
		flying = true;
		boss.setNoGravity(true);
		boss.setDeltaMovement(boss.getDeltaMovement().x, 0.9, boss.getDeltaMovement().z);
		boss.hasImpulse = true;
		sound(level, SoundEvents.ENDER_DRAGON_FLAP, 1.0f, 1.2f);
		particles(level, ParticleTypes.CLOUD, boss.position(), 16, 0.6);
	}

	private void land(ServerLevel level, boolean shotDown) {
		flying = false;
		boss.setNoGravity(false);
		if (shotDown) {
			groundedUntil = boss.tickCount + 120;
			boss.setDeltaMovement(boss.getDeltaMovement().x * 0.4, -0.6, boss.getDeltaMovement().z * 0.4);
			sound(level, SoundEvents.PHANTOM_HURT, 1.2f, 0.7f);
		}
		particles(level, ParticleTypes.CLOUD, boss.position(), 8, 0.5);
	}

	/**
	 * Hit in the air -> it is driven back to the ground and pinned there for a few seconds. A near miss
	 * (small hit) just makes it juke sideways.
	 */
	@Override
	public void onDamaged(ServerLevel level, DamageSource source, float amount) {
		if (!flying) {
			return;
		}
		if (amount >= 3.0f || !source.isDirect()) {
			land(level, true);
		} else {
			Vec3 side = new Vec3(random().nextDouble() - 0.5, 0.1, random().nextDouble() - 0.5).normalize().scale(0.8);
			boss.setDeltaMovement(boss.getDeltaMovement().add(side));
			boss.hasImpulse = true;
		}
	}

	@Override
	public void onSpawn(ServerLevel level) {
		boss.setNoGravity(false);
	}

	private double horizontalDistance(LivingEntity target) {
		double dx = boss.getX() - target.getX();
		double dz = boss.getZ() - target.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	/**
	 * Flight pairs badly with Teleportation -- both are "close the gap" powers, and a boss that can do
	 * either at will never has to commit to a position, which removes the player's ability to read it.
	 */
	@Override
	public boolean compatibleWith(String otherPowerKey) {
		return super.compatibleWith(otherPowerKey) && !otherPowerKey.equals(TeleportationBoss.POWER_KEY);
	}
}
