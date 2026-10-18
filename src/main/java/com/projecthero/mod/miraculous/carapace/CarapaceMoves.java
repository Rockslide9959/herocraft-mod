package com.projecthero.mod.miraculous.carapace;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.carapace.entity.CarapaceEntityTypes;
import com.projecthero.mod.miraculous.carapace.entity.ShieldThrowEntity;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.punisher.PunisherControl;
import com.projecthero.mod.squad.Squads;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6: Carapace's twelve moves (see {@code docs/MIRACULOUS_CARAPACE.md}) and the per-player sessions of the ones
 * that run over time (the Bash / Roll drive, the Taunt's mobs, the Protect bubble). Every public move is called by
 * {@link CarapaceKit#handle} once its cooldown is ready, and by the gametests directly. The dome, the wall and the
 * timers live in the synced {@code vals} / {@code vecs} (every client draws them from there).
 *
 * <p>The sessions are static and keyed by player; they hold entity ids, never entities, and are dropped by
 * {@link CarapaceKit#forget} / {@link CarapaceKit#clearSessionState}.
 */
public final class CarapaceMoves {
	static final ResourceLocation STEP_ID = ProjectHeroMod.id("carapace_surf_step");
	static final ResourceLocation KNOCKBACK_ID = ProjectHeroMod.id("carapace_steadfast_knockback");
	static final ResourceLocation SWIM_ID = ProjectHeroMod.id("carapace_tidal_swim");

	/** A move running over time for one player. */
	static final class Session {
		// Shield Bash / Shell Roll: a held push along one direction
		Vec3 driveDir;
		double driveSpeed;
		int driveTicks;
		boolean bashing;
		// Taunt
		final Set<Integer> taunted = new HashSet<>();
		// Protect
		int protectId = -1;
	}

	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

	private CarapaceMoves() {
	}

	static void clearSessionState() {
		SESSIONS.clear();
	}

	static void forget(UUID id) {
		SESSIONS.remove(id);
	}

	private static Session session(ServerPlayer p) {
		return SESSIONS.computeIfAbsent(p.getUUID(), k -> new Session());
	}

	private static long now(ServerPlayer p) {
		return p.level().getGameTime();
	}

	private static MiraculousState st(ServerPlayer p) {
		return Miraculous.state(p);
	}

	static Vec3 flatLook(Player p) {
		Vec3 l = p.getLookAngle();
		Vec3 f = new Vec3(l.x, 0, l.z);
		return f.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : f.normalize();
	}

	private static Vec3 chest(Player p) {
		return p.position().add(0, p.getBbHeight() * 0.62, 0);
	}

	private static void sound(ServerPlayer p, SoundEvent s, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	/** Within {@link CarapaceConfig#FRONT_DOT} of his flattened look. */
	static boolean inFront(Player p, Vec3 at) {
		Vec3 d = at.subtract(chest(p));
		Vec3 flatD = new Vec3(d.x, 0, d.z);
		if (flatD.lengthSqr() < 1.0e-4) {
			return true;
		}
		return flatD.normalize().dot(flatLook(p)) > CarapaceConfig.FRONT_DOT;
	}

	/** Moves that need the Shield in his hand take it off the ground first. */
	private static void dismount(ServerPlayer p) {
		if (st(p).val(Carapace.V_SURF) != 0L) {
			surfOff(p);
		}
	}

	// ================================================================ R: Shield Bash / Shift+R: Shield Throw

	/**
	 * Charges {@link CarapaceConfig#BASH_DISTANCE} along his look behind the shield; the first creature in front takes
	 * {@link CarapaceConfig#BASH_DAMAGE} and is thrown back. Returns what was hit on the first tick (tests), else null.
	 */
	public static @Nullable LivingEntity bash(ServerPlayer p) {
		dismount(p);
		Session s = session(p);
		s.driveDir = flatLook(p);
		s.driveSpeed = CarapaceConfig.BASH_SPEED;
		s.driveTicks = (int) Math.ceil(CarapaceConfig.BASH_DISTANCE / CarapaceConfig.BASH_SPEED);
		s.bashing = true;
		Miraculous.setVal(p, Carapace.V_BASH, now(p) + s.driveTicks);
		Miraculous.setAnim(p, Carapace.ANIM_BASH);
		Miraculous.setCooldown(p, CarapaceConfig.BASH, CarapaceConfig.BASH_COOLDOWN);
		sound(p, SoundEvents.RAVAGER_ROAR, 0.35f, 1.6f);
		sound(p, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.0f, 0.8f);
		LivingEntity hit = bashCheck(p, s);
		if (hit == null) {
			drive(p, s);
		}
		return hit;
	}

	/** The thing the shield's face meets now, struck; ends the charge. */
	private static @Nullable LivingEntity bashCheck(ServerPlayer p, Session s) {
		Vec3 face = p.position().add(0, 0.9, 0).add(s.driveDir.scale(0.8));
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, face, CarapaceConfig.BASH_REACH + 1.0)) {
			double d = Math.sqrt(AbilityHelpers.distanceSqToBox(e, face));
			Vec3 to = e.position().subtract(p.position());
			if (d > CarapaceConfig.BASH_REACH || new Vec3(to.x, 0, to.z).dot(s.driveDir) < -0.2) {
				continue;
			}
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		if (best == null) {
			return null;
		}
		CarapaceCombat.strike(p, best, p.position(), CarapaceConfig.BASH_DAMAGE, CarapaceConfig.BASH_KNOCKBACK, CarapaceConfig.BASH_LIFT);
		ServerLevel level = p.serverLevel();
		level.sendParticles(ParticleTypes.EXPLOSION, face.x, face.y, face.z, 1, 0, 0, 0, 0);
		level.sendParticles(CarapaceCombat.GREEN_BIG, face.x, face.y, face.z, 14, 0.3, 0.4, 0.3, 0.0);
		sound(p, SoundEvents.SHIELD_BLOCK, 1.2f, 0.6f);
		sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.8f);
		s.driveTicks = 0;
		s.bashing = false;
		Miraculous.setVal(p, Carapace.V_BASH, 0L);
		AbilityHelpers.launchSelf(p, s.driveDir.scale(0.1));
		return best;
	}

	private static void drive(ServerPlayer p, Session s) {
		Vec3 v = p.getDeltaMovement();
		AbilityHelpers.launchSelf(p, new Vec3(s.driveDir.x * s.driveSpeed, Math.min(v.y, 0.0) - 0.04, s.driveDir.z * s.driveSpeed));
	}

	/** Throws the Shield (refused while it is still out). */
	public static @Nullable ShieldThrowEntity throwShield(ServerPlayer p) {
		if (st(p).val(Carapace.V_SHIELD_OUT) != 0L) {
			Miraculous.say(p, "message.projecthero.carapace.shield_out", ChatFormatting.GREEN);
			return null;
		}
		dismount(p);
		ShieldThrowEntity e = CarapaceEntityTypes.SHIELD.create(p.serverLevel());
		if (e == null) {
			return null;
		}
		Vec3 from = p.getEyePosition().add(0, -0.3, 0).add(p.getLookAngle().scale(0.7));
		LivingEntity first = throwTarget(p);
		Vec3 aim = first != null ? first.position().add(0, first.getBbHeight() * 0.5, 0) : AbilityHelpers.aimPoint(p, CarapaceConfig.THROW_RANGE);
		e.launch(p, from, aim, first);
		p.serverLevel().addFreshEntity(e);
		Miraculous.setVal(p, Carapace.V_SHIELD_OUT, 1L);
		Miraculous.setAnim(p, Carapace.ANIM_THROW);
		sound(p, SoundEvents.TRIDENT_THROW.value(), 1.0f, 0.8f);
		return e;
	}

	/** The creature under the crosshair, else the valid one nearest it within {@link CarapaceConfig#THROW_ASSIST_DEGREES}. */
	static @Nullable LivingEntity throwTarget(ServerPlayer p) {
		LivingEntity direct = AbilityHelpers.raycastEntity(p, CarapaceConfig.THROW_RANGE);
		if (direct != null && CarapaceCombat.isTarget(p, direct)) {
			return direct;
		}
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		double bestDot = Math.cos(Math.toRadians(CarapaceConfig.THROW_ASSIST_DEGREES));
		LivingEntity best = null;
		for (LivingEntity le : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(CarapaceConfig.THROW_RANGE),
				e -> e != p && e.isAlive() && CarapaceCombat.isTarget(p, e))) {
			Vec3 to = le.position().add(0, le.getBbHeight() * 0.5, 0).subtract(eye);
			double d = to.length();
			if (d < 0.5 || d > CarapaceConfig.THROW_RANGE) {
				continue;
			}
			double dot = to.scale(1.0 / d).dot(look);
			if (dot > bestDot && p.hasLineOfSight(le)) {
				bestDot = dot;
				best = le;
			}
		}
		return best;
	}

	/** The ricochet: the nearest creature he may hit within {@link CarapaceConfig#THROW_BOUNCE_RANGE} of {@code from}, not hit yet, in sight. */
	public static @Nullable LivingEntity nextBounce(ServerPlayer p, Vec3 from, Set<Integer> hit) {
		double r = CarapaceConfig.THROW_BOUNCE_RANGE;
		LivingEntity best = null;
		double bestD = r * r;
		for (LivingEntity le : p.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(from, from).inflate(r),
				e -> e != p && e.isAlive() && !hit.contains(e.getId()) && CarapaceCombat.isTarget(p, e))) {
			Vec3 c = le.position().add(0, le.getBbHeight() * 0.5, 0);
			double d = c.distanceToSqr(from);
			if (d >= bestD) {
				continue;
			}
			if (p.level().clip(new net.minecraft.world.level.ClipContext(from, c, net.minecraft.world.level.ClipContext.Block.COLLIDER,
					net.minecraft.world.level.ClipContext.Fluid.NONE, p)).getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
				continue;
			}
			bestD = d;
			best = le;
		}
		return best;
	}

	/** The Shield is back on his arm; the throw's cooldown starts now. */
	public static void shieldCaught(ServerPlayer p) {
		if (!Miraculous.holds(p, Kwami.WAYZZ)) {
			return;
		}
		Miraculous.setVal(p, Carapace.V_SHIELD_OUT, 0L);
		if (Miraculous.transformed(p)) {
			Miraculous.setAnim(p, Carapace.ANIM_CATCH);
			Miraculous.setCooldown(p, CarapaceConfig.THROW, CarapaceConfig.THROW_COOLDOWN);
		}
		sound(p, SoundEvents.ARMOR_EQUIP_IRON.value(), 0.9f, 1.1f);
	}

	// ================================================================ G: Shield Slam / Shift+G: Taunt

	/** The shield driven into the ground: a {@link CarapaceConfig#SLAM_RADIUS}-block shockwave, damage and a stun. Returns how many were hit. */
	public static int slam(ServerPlayer p) {
		dismount(p);
		Miraculous.setAnim(p, Carapace.ANIM_SLAM);
		Miraculous.setCooldown(p, CarapaceConfig.SLAM, CarapaceConfig.SLAM_COOLDOWN);
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position();
		BlockPos under = BlockPos.containing(c.x, c.y - 0.5, c.z);
		BlockState ground = level.getBlockState(under);
		for (int ring = 1; ring <= 4; ring++) {
			double rr = CarapaceConfig.SLAM_RADIUS * ring / 4.0;
			int n = 8 + ring * 6;
			for (int i = 0; i < n; i++) {
				double a = Math.PI * 2 * i / n;
				double x = c.x + Math.cos(a) * rr, z = c.z + Math.sin(a) * rr;
				if (!ground.isAir()) {
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, c.y + 0.1, z, 2, 0.1, 0.05, 0.1, 0.15);
				}
				level.sendParticles(CarapaceCombat.GREEN, x, c.y + 0.15, z, 1, 0, 0, 0, 0);
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.2, c.z, 2, 0.4, 0.0, 0.4, 0);
		sound(p, SoundEvents.ANVIL_LAND, 0.8f, 0.6f);
		sound(p, SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.3f);
		int n = 0;
		double r = CarapaceConfig.SLAM_RADIUS;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, c.add(0, 0.5, 0), r)) {
			if (Math.abs(e.getY() - c.y) > 2.5) {
				continue;
			}
			if (CarapaceCombat.strike(p, e, c, CarapaceConfig.SLAM_DAMAGE, 0.4, CarapaceConfig.SLAM_LIFT)) {
				CarapaceCombat.stun(e, CarapaceConfig.SLAM_STUN_TICKS);
				level.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() + 0.2, e.getZ(), 8, 0.3, 0.1, 0.3, 0.1);
				n++;
			}
		}
		return n;
	}

	/** Every monster within {@link CarapaceConfig#TAUNT_RADIUS} comes for him for {@link CarapaceConfig#TAUNT_TICKS}; he takes 30% less. Returns how many. */
	public static int taunt(ServerPlayer p) {
		Session s = session(p);
		s.taunted.clear();
		double r = CarapaceConfig.TAUNT_RADIUS;
		for (Mob m : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(r),
				m -> m.isAlive() && m.distanceToSqr(p) <= r * r && HeroTargets.canHarm(p, m) && !CarapaceCombat.isBoss(m))) {
			s.taunted.add(m.getId());
			retarget(m, p);
			p.serverLevel().sendParticles(ParticleTypes.ANGRY_VILLAGER, m.getX(), m.getY() + m.getBbHeight() + 0.4, m.getZ(), 1, 0.1, 0.1, 0.1, 0);
		}
		Miraculous.setVal(p, Carapace.V_TAUNT, now(p) + CarapaceConfig.TAUNT_TICKS);
		Miraculous.setAnim(p, Carapace.ANIM_TAUNT);
		Miraculous.setCooldown(p, CarapaceConfig.TAUNT, CarapaceConfig.TAUNT_COOLDOWN);
		CarapaceCombat.glow(p.serverLevel(), chest(p), 16, 0.5);
		sound(p, SoundEvents.SHIELD_BLOCK, 1.0f, 0.7f);
		sound(p, SoundEvents.RAVAGER_ROAR, 0.6f, 1.4f);
		return s.taunted.size();
	}

	private static void retarget(Mob m, LivingEntity target) {
		m.setTarget(target);
		if (m.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)) {
			m.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
		}
	}

	// ================================================================ X: Shield Surf / Shift+X: Shell Roll

	/** Toggles riding the shield (the speed itself is simulated by his own client, see {@code CarapaceSurfClient}). True: now on. */
	public static boolean surf(ServerPlayer p) {
		if (st(p).val(Carapace.V_SURF) != 0L) {
			surfOff(p);
			return false;
		}
		if (st(p).val(Carapace.V_SHIELD_OUT) != 0L) {
			Miraculous.say(p, "message.projecthero.carapace.shield_out", ChatFormatting.GREEN);
			return false;
		}
		guardDown(p);
		Miraculous.setVal(p, Carapace.V_SURF, 1L);
		PowerToggles.modifier(p, Attributes.STEP_HEIGHT, STEP_ID, CarapaceConfig.SURF_STEP, AttributeModifier.Operation.ADD_VALUE);
		Miraculous.setAnim(p, Carapace.ANIM_SURF);
		sound(p, SoundEvents.ARMOR_EQUIP_TURTLE.value(), 1.0f, 1.0f);
		sound(p, SoundEvents.BOAT_PADDLE_WATER, 0.6f, 1.3f);
		return true;
	}

	static void surfOff(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, STEP_ID);
		if (st(p).val(Carapace.V_SURF) == 0L) {
			return;
		}
		Miraculous.setVal(p, Carapace.V_SURF, 0L);
		if (Miraculous.transformed(p)) {
			Miraculous.setCooldown(p, CarapaceConfig.SURF, CarapaceConfig.SURF_COOLDOWN);
		}
		sound(p, SoundEvents.ARMOR_EQUIP_TURTLE.value(), 0.8f, 1.3f);
	}

	/** Tucks into the shell and rolls {@link CarapaceConfig#ROLL_DISTANCE} along his look, taking no damage meanwhile. */
	public static void roll(ServerPlayer p) {
		dismount(p);
		guardDown(p);
		Session s = session(p);
		s.driveDir = flatLook(p);
		s.driveSpeed = CarapaceConfig.ROLL_SPEED;
		s.driveTicks = (int) Math.ceil(CarapaceConfig.ROLL_DISTANCE / CarapaceConfig.ROLL_SPEED);
		s.bashing = false;
		Miraculous.setVal(p, Carapace.V_ROLL, now(p) + s.driveTicks + CarapaceConfig.ROLL_GRACE);
		Miraculous.setAnim(p, Carapace.ANIM_ROLL);
		Miraculous.setCooldown(p, CarapaceConfig.ROLL, CarapaceConfig.ROLL_COOLDOWN);
		drive(p, s);
		sound(p, SoundEvents.ARMOR_EQUIP_TURTLE.value(), 1.0f, 0.7f);
		sound(p, SoundEvents.TURTLE_SHAMBLE, 1.0f, 0.8f);
	}

	// ================================================================ Z: Shell Guard (held) / Shift+Z: Shelter

	public static boolean guardUp(ServerPlayer p) {
		if (st(p).val(Carapace.V_GUARD) != 0L || !Miraculous.ready(p, CarapaceConfig.GUARD)) {
			return false;
		}
		if (st(p).val(Carapace.V_SHIELD_OUT) != 0L) {
			Miraculous.say(p, "message.projecthero.carapace.shield_out", ChatFormatting.GREEN);
			return false;
		}
		dismount(p);
		Miraculous.setVal(p, Carapace.V_GUARD, now(p));
		sound(p, SoundEvents.ARMOR_EQUIP_IRON.value(), 1.0f, 0.8f);
		return true;
	}

	public static void guardDown(ServerPlayer p) {
		if (st(p).val(Carapace.V_GUARD) == 0L) {
			return;
		}
		Miraculous.setVal(p, Carapace.V_GUARD, 0L);
		if (Miraculous.transformed(p)) {
			Miraculous.setCooldown(p, CarapaceConfig.GUARD, CarapaceConfig.GUARD_COOLDOWN);
		}
	}

	private static void tickGuard(ServerPlayer p, MiraculousState s) {
		long since = s.val(Carapace.V_GUARD);
		if (since == 0L) {
			return;
		}
		if (now(p) - since > CarapaceConfig.GUARD_MAX_TICKS) {
			guardDown(p);
			return;
		}
		ServerLevel level = p.serverLevel();
		Vec3 c = chest(p);
		double r = CarapaceConfig.GUARD_REACH;
		for (Projectile proj : level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(r + 2.0), Entity::isAlive)) {
			if (proj.getOwner() == p || !inFront(p, proj.position())) {
				continue;
			}
			Vec3 v = proj.getDeltaMovement();
			// this tick or the next brings it within reach
			if (proj.position().distanceTo(c) > r + 0.6 && proj.position().add(v).distanceTo(c) > r + 0.6) {
				continue;
			}
			if (v.lengthSqr() > 1.0e-4 && v.dot(c.subtract(proj.position())) <= 0.0) {
				continue; // moving away
			}
			reflect(p, proj);
		}
	}

	/** A projectile sent back the way it came (arrows too: they fly back at whoever shot them). */
	static void reflect(ServerPlayer p, Projectile proj) {
		ServerLevel level = p.serverLevel();
		Vec3 at = proj.position();
		Entity shooter = proj.getOwner();
		proj.deflect(ProjectileDeflection.REVERSE, p, p, true);
		proj.setOwner(p);
		if (shooter != null && shooter.isAlive() && !(proj instanceof ThrownTrident)) {
			// aim it back at the shooter
			Vec3 to = shooter.position().add(0, shooter.getBbHeight() * 0.6, 0).subtract(at);
			double speed = Math.max(0.8, proj.getDeltaMovement().length());
			if (to.lengthSqr() > 1.0e-4) {
				proj.setDeltaMovement(to.normalize().scale(speed));
				proj.hurtMarked = true;
			}
		}
		if (proj instanceof AbstractArrow arrow) {
			arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		}
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.15, 0.15, 0.15, 0.25);
		level.sendParticles(CarapaceCombat.GREEN, at.x, at.y, at.z, 5, 0.12, 0.12, 0.12, 0.0);
		sound(p, SoundEvents.SHIELD_BLOCK, 0.9f, 1.3f);
	}

	/**
	 * Shift+Z: the energy dome round where he stands -- radius {@link CarapaceConfig#SHELTER_RADIUS} for
	 * {@link CarapaceConfig#SHELTER_TICKS}. Starts the Miraculous countdown. False: a Shelter already stands.
	 */
	public static boolean shelter(ServerPlayer p) {
		long now = now(p);
		if (st(p).val(Carapace.V_SHELTER) > now) {
			Miraculous.say(p, "message.projecthero.carapace.shelter_running", ChatFormatting.GREEN);
			return false;
		}
		dismount(p);
		guardDown(p);
		Vec3 c = p.position();
		Miraculous.setVec(p, Carapace.X_SHELTER, c);
		Miraculous.setVal(p, Carapace.V_SHELTER, now + CarapaceConfig.SHELTER_TICKS);
		Miraculous.setAnim(p, Carapace.ANIM_SHELTER);
		Miraculous.setCooldown(p, CarapaceConfig.SHELTER, CarapaceConfig.SHELTER_COOLDOWN);
		Miraculous.startCountdown(p);
		ServerLevel level = p.serverLevel();
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1.0, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(CarapaceCombat.GREEN_BIG, c.x, c.y + 1.0, c.z, 50, 1.5, 1.0, 1.5, 0.0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5f, 1.2f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 1.5f, 1.0f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 0.8f);
		return true;
	}

	/** Him, or a squadmate of his. */
	private static boolean friend(ServerPlayer p, Entity e) {
		return e == p || (e instanceof Player o && Squads.areAllies(p, o));
	}

	/** Something the dome / the wall keeps out: a creature he may harm, never him or a squadmate. */
	private static boolean keptOut(ServerPlayer p, LivingEntity e) {
		return !friend(p, e) && HeroTargets.canHarm(p, e);
	}

	private static void tickShelter(ServerPlayer p, MiraculousState s, long now) {
		long until = s.val(Carapace.V_SHELTER);
		if (until == 0L) {
			return;
		}
		Vec3 c = Miraculous.vec(s, Carapace.X_SHELTER);
		if (now >= until || c == null) {
			Miraculous.setVal(p, Carapace.V_SHELTER, 0L);
			Miraculous.setVec(p, Carapace.X_SHELTER, null);
			if (c != null) {
				p.serverLevel().sendParticles(CarapaceCombat.GREEN_BIG, c.x, c.y + 2.0, c.z, 40, 3.0, 2.0, 3.0, 0.0);
				p.serverLevel().playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.2f, 1.2f);
			}
			return;
		}
		ServerLevel level = p.serverLevel();
		double r = CarapaceConfig.SHELTER_RADIUS;
		AABB box = new AABB(c, c).inflate(r + 4.0);
		// creatures inside (or touching the shell) are pushed out
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && keptOut(p, e))) {
			Vec3 at = e.position().add(0, e.getBbHeight() * 0.5, 0);
			Vec3 off = at.subtract(c);
			double edge = r + e.getBbWidth() * 0.5 + 0.3;
			if (off.length() >= edge) {
				continue;
			}
			Vec3 flat = new Vec3(off.x, 0, off.z);
			Vec3 out = flat.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : flat.normalize();
			pushOut(e, c.add(out.scale(edge + 0.2)), out, CarapaceCombat.isBoss(e));
			if (now % 4 == 0) {
				level.sendParticles(CarapaceCombat.GREEN, at.x - out.x * 0.4, at.y, at.z - out.z * 0.4, 4, 0.2, 0.3, 0.2, 0.0);
			}
		}
		// projectiles from outside stop at the shell
		for (Projectile proj : level.getEntitiesOfClass(Projectile.class, box, Entity::isAlive)) {
			if (proj.getOwner() != null && friend(p, proj.getOwner())) {
				continue;
			}
			Vec3 at = proj.position();
			Vec3 next = at.add(proj.getDeltaMovement());
			if (at.distanceTo(c) < r + 0.3 || next.distanceTo(c) < r + 0.3) {
				stopProjectile(level, proj);
			}
		}
		// him and his squad inside heal
		if (now % 20 == 0) {
			for (Player o : level.getEntitiesOfClass(Player.class, new AABB(c, c).inflate(r), o -> o.isAlive() && friend(p, o))) {
				if (o.position().add(0, 0.9, 0).distanceTo(c) <= r) {
					o.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 50, CarapaceConfig.SHELTER_REGEN_AMP, true, false, true));
				}
			}
			level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.5f, 1.4f);
		}
	}

	/** {@code e} moved to {@code to} (players and bosses: shoved there instead), with a push along {@code out}. */
	private static void pushOut(LivingEntity e, Vec3 to, Vec3 out, boolean boss) {
		if (e instanceof ServerPlayer sp) {
			AbilityHelpers.launchSelf(sp, out.scale(0.8).add(0, 0.2, 0));
			return;
		}
		if (!boss && e.level().noCollision(e, e.getBoundingBox().move(to.x - e.getX(), 0, to.z - e.getZ()))) {
			e.teleportTo(to.x, e.getY(), to.z);
		}
		Vec3 v = e.getDeltaMovement();
		double into = v.dot(out);
		e.setDeltaMovement(v.subtract(out.scale(Math.min(0.0, into))).add(out.scale(boss ? 0.3 : 0.5)));
		e.hurtMarked = true;
		if (e instanceof Mob m) {
			m.getNavigation().stop();
		}
	}

	private static void stopProjectile(ServerLevel level, Projectile proj) {
		Vec3 at = proj.position();
		level.sendParticles(CarapaceCombat.GREEN_BIG, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.0);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.2);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.7f, 1.6f);
		proj.discard();
	}

	// ================================================================ V: Shell Barrier / Shift+V: Protect

	/** A {@link CarapaceConfig#BARRIER_WIDTH}x{@link CarapaceConfig#BARRIER_HEIGHT} energy wall {@link CarapaceConfig#BARRIER_DISTANCE} ahead, across his look. */
	public static void barrier(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		Vec3 dir = flatLook(p);
		Vec3 c = p.position().add(dir.scale(CarapaceConfig.BARRIER_DISTANCE));
		c = ground(level, c.add(0, 1.0, 0));
		Miraculous.setVec(p, Carapace.X_BARRIER, c);
		Miraculous.setVec(p, Carapace.X_BARRIER_DIR, dir);
		Miraculous.setVal(p, Carapace.V_BARRIER, now(p) + CarapaceConfig.BARRIER_TICKS);
		Miraculous.setAnim(p, Carapace.ANIM_BARRIER);
		Miraculous.setCooldown(p, CarapaceConfig.BARRIER, CarapaceConfig.BARRIER_COOLDOWN);
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		for (int i = 0; i < 24; i++) {
			double u = (level.random.nextDouble() - 0.5) * CarapaceConfig.BARRIER_WIDTH;
			double h = level.random.nextDouble() * CarapaceConfig.BARRIER_HEIGHT;
			Vec3 q = c.add(side.scale(u)).add(0, h, 0);
			level.sendParticles(CarapaceCombat.GREEN_BIG, q.x, q.y, q.z, 1, 0, 0, 0, 0);
		}
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2f, 1.4f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 0.9f);
	}

	/** The ground at or under {@code v} (up to 6 blocks down). */
	private static Vec3 ground(ServerLevel level, Vec3 v) {
		BlockPos pos = BlockPos.containing(v.x, v.y + 0.5, v.z);
		for (int i = 0; i < 8; i++) {
			BlockPos below = pos.below();
			if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty() && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
				return new Vec3(v.x, pos.getY(), v.z);
			}
			pos = below;
		}
		return v;
	}

	/** Test / client-shared geometry: is {@code q} within the wall's slab (half thickness {@code half})? Returns the signed depth along its facing, or NaN when outside its extents. */
	public static double barrierSide(Vec3 c, Vec3 dir, Vec3 q, double half, double grow) {
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		Vec3 off = q.subtract(c);
		double lateral = off.dot(side);
		if (Math.abs(lateral) > CarapaceConfig.BARRIER_WIDTH * 0.5 + grow || off.y < -1.0 - grow || off.y > CarapaceConfig.BARRIER_HEIGHT + grow) {
			return Double.NaN;
		}
		return off.dot(dir);
	}

	private static void tickBarrier(ServerPlayer p, MiraculousState s, long now) {
		long until = s.val(Carapace.V_BARRIER);
		if (until == 0L) {
			return;
		}
		Vec3 c = Miraculous.vec(s, Carapace.X_BARRIER);
		Vec3 dir = Miraculous.vec(s, Carapace.X_BARRIER_DIR);
		if (now >= until || c == null || dir == null) {
			Miraculous.setVal(p, Carapace.V_BARRIER, 0L);
			Miraculous.setVec(p, Carapace.X_BARRIER, null);
			Miraculous.setVec(p, Carapace.X_BARRIER_DIR, null);
			if (c != null) {
				p.serverLevel().sendParticles(CarapaceCombat.GREEN, c.x, c.y + 1.5, c.z, 24, 1.5, 0.8, 0.3, 0.0);
			}
			return;
		}
		ServerLevel level = p.serverLevel();
		AABB box = new AABB(c, c).inflate(CarapaceConfig.BARRIER_WIDTH, CarapaceConfig.BARRIER_HEIGHT + 2.0, CarapaceConfig.BARRIER_WIDTH).move(0, 1.0, 0);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && keptOut(p, e))) {
			double half = e.getBbWidth() * 0.5 + 0.25;
			double depth = barrierSide(c, dir, e.position().add(0, 0.1, 0), half, half);
			if (Double.isNaN(depth) || Math.abs(depth) >= half) {
				continue;
			}
			// which side it belongs on: where it came from (its last position), else his far side
			double was = barrierSide(c, dir, new Vec3(e.xo, e.yo + 0.1, e.zo), half, half + 1.0);
			double sign = !Double.isNaN(was) && Math.abs(was) > 1.0e-3 ? Math.signum(was) : 1.0;
			Vec3 out = dir.scale(sign);
			Vec3 to = e.position().add(out.scale(half - depth * sign + 0.05));
			pushOut(e, to, out, CarapaceCombat.isBoss(e));
		}
		for (Projectile proj : level.getEntitiesOfClass(Projectile.class, box.inflate(3.0), Entity::isAlive)) {
			if (proj.getOwner() != null && friend(p, proj.getOwner())) {
				continue;
			}
			Vec3 a = proj.position();
			Vec3 b = a.add(proj.getDeltaMovement());
			double da = barrierSide(c, dir, a, 0.3, 0.3);
			double db = barrierSide(c, dir, b, 0.3, 0.3);
			boolean inside = !Double.isNaN(da) && Math.abs(da) < 0.4;
			boolean crosses = !Double.isNaN(da) && !Double.isNaN(db) && Math.signum(da) != Math.signum(db);
			if (inside || crosses) {
				stopProjectile(level, proj);
			}
		}
		if (now % 10 == 0) {
			Vec3 side = new Vec3(-dir.z, 0, dir.x);
			Vec3 q = c.add(side.scale((level.random.nextDouble() - 0.5) * CarapaceConfig.BARRIER_WIDTH)).add(0, level.random.nextDouble() * 3, 0);
			level.sendParticles(CarapaceCombat.GREEN, q.x, q.y, q.z, 1, 0, 0, 0, 0);
		}
	}

	/**
	 * Shift+V: a shield bubble that soaks {@link CarapaceConfig#PROTECT_ABSORB} damage (Absorption) for
	 * {@link CarapaceConfig#PROTECT_TICKS} on the player he looks at, else on himself. Returns who got it.
	 */
	public static LivingEntity protect(ServerPlayer p) {
		LivingEntity target = p;
		LivingEntity looked = AbilityHelpers.raycastEntity(p, CarapaceConfig.PROTECT_RANGE);
		if (looked instanceof Player o && o.isAlive() && !o.isSpectator() && (Squads.areAllies(p, o) || !HeroTargets.isHostile(p, o))) {
			target = o;
		}
		Session s = session(p);
		// a bubble already on someone else bursts
		if (s.protectId >= 0 && s.protectId != target.getId() && p.serverLevel().getEntity(s.protectId) instanceof LivingEntity old) {
			old.removeEffect(MobEffects.ABSORPTION);
		}
		int amp = Math.max(0, Math.round(CarapaceConfig.PROTECT_ABSORB / 4f) - 1);
		target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, CarapaceConfig.PROTECT_TICKS, amp, false, true, true));
		target.setAbsorptionAmount(Math.max(target.getAbsorptionAmount(), CarapaceConfig.PROTECT_ABSORB));
		s.protectId = target.getId();
		Miraculous.setVal(p, Carapace.V_PROTECT, now(p) + CarapaceConfig.PROTECT_TICKS);
		Miraculous.setVal(p, Carapace.V_PROTECT_ID, target.getId() + 1L);
		Miraculous.setAnim(p, Carapace.ANIM_PROTECT);
		Miraculous.setCooldown(p, CarapaceConfig.PROTECT, CarapaceConfig.PROTECT_COOLDOWN);
		ServerLevel level = p.serverLevel();
		Vec3 tc = target.position().add(0, target.getBbHeight() * 0.5, 0);
		if (target != p) {
			AbilityHelpers.line(level, chest(p), tc, CarapaceCombat.GREEN, 3.0);
		}
		CarapaceCombat.glow(level, tc, 20, 0.6);
		level.playSound(null, tc.x, tc.y, tc.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.6f);
		level.playSound(null, tc.x, tc.y, tc.z, SoundEvents.ARMOR_EQUIP_TURTLE.value(), SoundSource.PLAYERS, 1.0f, 1.2f);
		return target;
	}

	private static void tickProtect(ServerPlayer p, MiraculousState s, long now) {
		long until = s.val(Carapace.V_PROTECT);
		if (until == 0L) {
			return;
		}
		Session ss = SESSIONS.get(p.getUUID());
		Entity e = p.serverLevel().getEntity((int) (s.val(Carapace.V_PROTECT_ID) - 1L));
		boolean gone = !(e instanceof LivingEntity le) || !le.isAlive() || (now > until - CarapaceConfig.PROTECT_TICKS + 2 && le.getAbsorptionAmount() <= 0.01f);
		if (now >= until || gone) {
			Miraculous.setVal(p, Carapace.V_PROTECT, 0L);
			Miraculous.setVal(p, Carapace.V_PROTECT_ID, 0L);
			if (ss != null) {
				ss.protectId = -1;
			}
			if (e != null) {
				Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
				p.serverLevel().sendParticles(CarapaceCombat.GREEN_BIG, c.x, c.y, c.z, 20, 0.6, 0.8, 0.6, 0.0);
				p.serverLevel().playSound(null, c.x, c.y, c.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.7f, 1.5f);
			}
		}
	}

	// ================================================================ C: Steadfast / Shift+C: Tidal Swim

	/** {@link CarapaceConfig#STEADFAST_TICKS} of knockback immunity and Resistance II. */
	public static void steadfast(ServerPlayer p) {
		p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, CarapaceConfig.STEADFAST_TICKS, CarapaceConfig.STEADFAST_RESISTANCE_AMP, false, true,
				true));
		PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, 1.0, AttributeModifier.Operation.ADD_VALUE);
		Miraculous.setVal(p, Carapace.V_STEADFAST, now(p) + CarapaceConfig.STEADFAST_TICKS);
		Miraculous.setAnim(p, Carapace.ANIM_STEADFAST);
		Miraculous.setCooldown(p, CarapaceConfig.STEADFAST, CarapaceConfig.STEADFAST_COOLDOWN);
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position();
		for (int i = 0; i < 16; i++) {
			double a = Math.PI * 2 * i / 16;
			level.sendParticles(CarapaceCombat.SHELL, c.x + Math.cos(a) * 0.9, c.y + 0.1, c.z + Math.sin(a) * 0.9, 1, 0, 0.05, 0, 0);
		}
		CarapaceCombat.glow(level, c.add(0, 1.0, 0), 10, 0.4);
		sound(p, SoundEvents.ANVIL_PLACE, 0.6f, 0.7f);
		sound(p, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.0f, 0.7f);
	}

	/** {@link CarapaceConfig#TIDAL_TICKS} of water breathing, Dolphin's Grace and turtle-fast swimming. */
	public static void tidal(ServerPlayer p) {
		int t = CarapaceConfig.TIDAL_TICKS;
		p.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, t, 0, false, true, true));
		p.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, t, 0, false, true, true));
		PowerToggles.modifier(p, Attributes.WATER_MOVEMENT_EFFICIENCY, SWIM_ID, CarapaceConfig.TIDAL_SWIM_BONUS, AttributeModifier.Operation.ADD_VALUE);
		Miraculous.setVal(p, Carapace.V_TIDAL, now(p) + t);
		Miraculous.setAnim(p, Carapace.ANIM_TIDAL);
		Miraculous.setCooldown(p, CarapaceConfig.TIDAL, CarapaceConfig.TIDAL_COOLDOWN);
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position().add(0, 1.0, 0);
		level.sendParticles(ParticleTypes.BUBBLE_POP, c.x, c.y, c.z, 20, 0.5, 0.7, 0.5, 0.05);
		level.sendParticles(ParticleTypes.SPLASH, c.x, c.y, c.z, 20, 0.5, 0.5, 0.5, 0.1);
		sound(p, SoundEvents.TURTLE_SWIM, 1.0f, 1.0f);
		sound(p, SoundEvents.CONDUIT_AMBIENT_SHORT, 1.0f, 1.3f);
	}

	// ================================================================ incoming damage

	/** The kit's {@code modifyIncomingDamage}: the Roll, the Guard, the Taunt's 30%. */
	public static float incoming(ServerPlayer p, DamageSource source, float amount) {
		MiraculousState s = st(p);
		long now = now(p);
		if (s.val(Carapace.V_ROLL) > now) {
			return 0f;
		}
		if (s.val(Carapace.V_GUARD) != 0L) {
			Vec3 from = source.getDirectEntity() != null ? source.getDirectEntity().position() : source.getSourcePosition();
			if (from != null && source.getDirectEntity() != p && inFront(p, from)) {
				if (source.getDirectEntity() instanceof LivingEntity attacker && attacker.distanceTo(p) < 5.0 && !CarapaceCombat.isBoss(attacker)) {
					AbilityHelpers.knockbackFrom(attacker, p.position(), CarapaceConfig.GUARD_KNOCKBACK);
				}
				sound(p, SoundEvents.SHIELD_BLOCK, 0.9f, 1.0f);
				Vec3 c = chest(p).add(flatLook(p).scale(0.6));
				p.serverLevel().sendParticles(CarapaceCombat.GREEN, c.x, c.y, c.z, 6, 0.25, 0.25, 0.25, 0.0);
				return 0f;
			}
		}
		if (s.val(Carapace.V_TAUNT) > now) {
			return amount * CarapaceConfig.TAUNT_DAMAGE_TAKEN;
		}
		return amount;
	}

	// ================================================================ the tick

	static void tick(ServerPlayer p) {
		long now = now(p);
		MiraculousState s = st(p);
		Session ss = SESSIONS.get(p.getUUID());
		// the Bash / Roll drive
		if (ss != null && ss.driveTicks > 0) {
			ss.driveTicks--;
			if (ss.bashing && bashCheck(p, ss) != null) {
				ss.driveTicks = 0;
			} else if (ss.driveTicks > 0) {
				drive(p, ss);
				if (!ss.bashing && now % 2 == 0) {
					p.serverLevel().sendParticles(ParticleTypes.POOF, p.getX(), p.getY() + 0.3, p.getZ(), 2, 0.3, 0.1, 0.3, 0.02);
				}
			} else {
				ss.bashing = false;
				AbilityHelpers.launchSelf(p, ss.driveDir.scale(0.15).add(0, Math.min(0, p.getDeltaMovement().y), 0));
			}
		}
		if (s.val(Carapace.V_BASH) != 0L && now >= s.val(Carapace.V_BASH)) {
			Miraculous.setVal(p, Carapace.V_BASH, 0L);
		}
		if (s.val(Carapace.V_ROLL) != 0L && now >= s.val(Carapace.V_ROLL)) {
			Miraculous.setVal(p, Carapace.V_ROLL, 0L);
		}
		// the Taunt
		long taunt = s.val(Carapace.V_TAUNT);
		if (taunt != 0L) {
			if (now >= taunt) {
				Miraculous.setVal(p, Carapace.V_TAUNT, 0L);
				if (ss != null) {
					ss.taunted.clear();
				}
			} else if (ss != null && now % 10 == 0) {
				for (int id : ss.taunted) {
					if (p.serverLevel().getEntity(id) instanceof Mob m && m.isAlive() && m.getTarget() != p && !PunisherControl.isStunned(m)) {
						retarget(m, p);
					}
				}
			}
		}
		tickGuard(p, s);
		tickShelter(p, s, now);
		tickBarrier(p, s, now);
		tickProtect(p, s, now);
		// the timed buffs
		long sf = s.val(Carapace.V_STEADFAST);
		if (sf != 0L && now >= sf) {
			Miraculous.setVal(p, Carapace.V_STEADFAST, 0L);
			PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID);
		}
		long td = s.val(Carapace.V_TIDAL);
		if (td != 0L) {
			if (now >= td) {
				Miraculous.setVal(p, Carapace.V_TIDAL, 0L);
				PowerToggles.clearModifier(p, Attributes.WATER_MOVEMENT_EFFICIENCY, SWIM_ID);
			} else if (p.isInWater() && now % 6 == 0) {
				p.serverLevel().sendParticles(ParticleTypes.BUBBLE, p.getX(), p.getY() + 0.5, p.getZ(), 3, 0.3, 0.3, 0.3, 0.02);
			}
		}
		// surfing: the spray
		if (s.val(Carapace.V_SURF) != 0L && now % 3 == 0 && p.getDeltaMovement().horizontalDistanceSqr() > 0.04) {
			BlockPos under = BlockPos.containing(p.getX(), p.getY() - 0.2, p.getZ());
			ServerLevel level = p.serverLevel();
			if (level.getFluidState(under).isEmpty() && level.getFluidState(p.blockPosition()).isEmpty()) {
				BlockState b = level.getBlockState(under);
				if (!b.isAir()) {
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, b), p.getX(), p.getY() + 0.05, p.getZ(), 3, 0.25, 0.02, 0.25, 0.1);
				}
			} else {
				level.sendParticles(ParticleTypes.SPLASH, p.getX(), p.getY() + 0.1, p.getZ(), 6, 0.3, 0.05, 0.3, 0.1);
			}
		}
	}

	/** Detransformation / logout: every running move of his ends, the dome and the wall drop, the Shield comes home. */
	static void endAll(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, STEP_ID);
		PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID);
		PowerToggles.clearModifier(p, Attributes.WATER_MOVEMENT_EFFICIENCY, SWIM_ID);
		Session ss = SESSIONS.get(p.getUUID());
		if (ss != null && ss.protectId >= 0 && p.serverLevel().getEntity(ss.protectId) instanceof LivingEntity le && le != p) {
			le.removeEffect(MobEffects.ABSORPTION);
		}
		ServerLevel level = p.serverLevel();
		for (ShieldThrowEntity e : level.getEntitiesOfClass(ShieldThrowEntity.class, p.getBoundingBox().inflate(256.0), e -> e.ownerEntityId() == p.getId())) {
			e.discard();
		}
		Miraculous.edit(p, n -> n.vals.keySet().removeIf(k -> k.startsWith("cp_")));
		Miraculous.edit(p, n -> n.vecs.keySet().removeIf(k -> k.startsWith("cp_")));
		SESSIONS.remove(p.getUUID());
	}
}
