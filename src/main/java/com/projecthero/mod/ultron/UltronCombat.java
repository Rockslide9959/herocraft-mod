package com.projecthero.mod.ultron;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ultron.entity.UltronRobot;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.12: how Ultron's robots hurt things and how things hurt them.
 * <ul>
 *   <li>{@link #canTarget}: who they shoot at -- any living thing that isn't one of Ultron's own (players in creative or
 *       spectator, armour stands and the dead excepted).</li>
 *   <li>{@link #hit}: every Ultron hit goes through here, so a suited Iron Man's hull takes the
 *       {@link UltronConfig.Damage#ironManDrainBonus} extra ("he's in your systems").</li>
 *   <li>{@link #hitscan}: a red repulsor / sniper bolt -- a ray stopped by blocks, first valid target hit, beam drawn.</li>
 *   <li>{@link #multiplier}: lightning / electricity x1.5, Hulk or a heavy melee hit x1.25.</li>
 * </ul>
 */
public final class UltronCombat {
	private UltronCombat() {
	}

	/** May an Ultron robot attack {@code e}? */
	public static boolean canTarget(Entity e) {
		if (!(e instanceof LivingEntity le) || !le.isAlive() || le.isDeadOrDying() || e.isSpectator() || e instanceof ArmorStand
				|| e instanceof UltronRobot) {
			return false;
		}
		return !(e instanceof Player p) || !p.getAbilities().invulnerable;
	}

	/** A valid player fighter (survival / adventure, alive). Mock gametest players report creative, so abilities are checked. */
	public static boolean isFighter(Player p) {
		return p.isAlive() && !p.isSpectator() && !p.getAbilities().invulnerable;
	}

	/** Hurts {@code target} from {@code attacker}; true if it landed. */
	public static boolean hit(ServerLevel level, LivingEntity attacker, LivingEntity target, float amount) {
		DamageSource src = attacker == null ? level.damageSources().magic() : level.damageSources().mobAttack(attacker);
		return hit(level, src, target, amount);
	}

	public static boolean hit(ServerLevel level, DamageSource src, LivingEntity target, float amount) {
		if (!canTarget(target)) {
			return false;
		}
		target.invulnerableTime = 0;
		boolean hurt = target.hurt(src, amount);
		if (hurt && target instanceof ServerPlayer sp) {
			drainSuit(sp, amount);
		}
		return hurt;
	}

	/** The extra hull damage an Ultron hit does to a worn Iron Man suit. */
	public static void drainSuit(ServerPlayer player, float amount) {
		if (!TonyStark.hasPower(player)) {
			return;
		}
		String suitId = IronManArmor.wornSuitId(player);
		if (suitId == null) {
			return;
		}
		float extra = (float) (amount * IronManEnergy.INTEGRITY_PER_DAMAGE * UltronConfig.damage().ironManDrainBonus);
		if (extra > 0f) {
			IronManEnergy.damageIntegrity(player, suitId, extra);
		}
	}

	/**
	 * A hit-scan bolt from {@code from} along {@code dir}: stopped by blocks, the first valid target within
	 * {@code width} of the line takes {@code damage}. Draws the beam ({@code kind}) and returns what it hit (or null).
	 */
	public static LivingEntity hitscan(ServerLevel level, LivingEntity shooter, Vec3 from, Vec3 dir, double range, float damage, int kind,
			int life, float width) {
		Vec3 to = from.add(dir.normalize().scale(range));
		BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
		Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
		EntityHitResult hitResult = ProjectileUtil.getEntityHitResult(level, shooter, from, end, new AABB(from, end).inflate(1.0 + width),
				UltronCombat::canTarget, width);
		Vec3 stop = hitResult != null ? hitResult.getLocation() : end;
		UltronFx.beam(level, from, stop, kind, life);
		if (hitResult != null && hitResult.getEntity() instanceof LivingEntity target) {
			hit(level, shooter, target, damage);
			level.sendParticles(UltronFx.RED, stop.x, stop.y, stop.z, 6, 0.15, 0.15, 0.15, 0.05);
			return target;
		}
		if (block.getType() != HitResult.Type.MISS) {
			level.sendParticles(UltronFx.RED_SMALL, end.x, end.y, end.z, 5, 0.1, 0.1, 0.1, 0.02);
		}
		return null;
	}

	/** Every valid target within {@code radius} of {@code line} from {@code a} to {@code b} (a thick beam / sweep). */
	public static List<LivingEntity> alongLine(ServerLevel level, Vec3 a, Vec3 b, double radius) {
		List<LivingEntity> out = new ArrayList<>();
		AABB box = new AABB(a, b).inflate(radius + 1.0);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, UltronCombat::canTarget)) {
			AABB fat = e.getBoundingBox().inflate(radius);
			if (fat.contains(a) || fat.clip(a, b).isPresent()) {
				out.add(e);
			}
		}
		return out;
	}

	/** The nearest valid player fighter within {@code range} of {@code from}, or null. */
	public static Player nearestPlayer(ServerLevel level, Vec3 from, double range) {
		Player best = null;
		double bestD = range * range;
		for (Player p : level.players()) {
			if (!isFighter(p)) {
				continue;
			}
			double d = p.distanceToSqr(from);
			if (d <= bestD) {
				bestD = d;
				best = p;
			}
		}
		return best;
	}

	/** Every valid player fighter within {@code range} of {@code from}. */
	public static List<Player> playersNear(ServerLevel level, Vec3 from, double range) {
		List<Player> out = new ArrayList<>();
		for (Player p : level.players()) {
			if (isFighter(p) && p.distanceToSqr(from) <= range * range) {
				out.add(p);
			}
		}
		return out;
	}

	// ---------------------------------------------------------------- damage taken

	/**
	 * The multiplier an Ultron robot applies to {@code amount} from {@code source}: lightning or an Electrokinesis user
	 * {@link UltronConfig.Damage#electricMultiplier}; a Hulk, or any direct melee hit of the threshold or more,
	 * {@link UltronConfig.Damage#heavyMeleeMultiplier}. The biggest one applies (they don't stack).
	 */
	public static float multiplier(DamageSource source, float amount) {
		UltronConfig.Damage cfg = UltronConfig.damage();
		if (isElectric(source)) {
			return (float) cfg.electricMultiplier;
		}
		Entity attacker = source.getEntity();
		boolean direct = attacker != null && source.getDirectEntity() == attacker;
		if (attacker instanceof Player p && direct) {
			if (isHulk(p) || amount >= cfg.heavyMeleeThreshold && !source.is(DamageTypeTags.IS_PROJECTILE)) {
				return (float) cfg.heavyMeleeMultiplier;
			}
		}
		return 1.0f;
	}

	/** Lightning (Thor's storms, a struck bolt) or a hit from someone wielding Electrokinesis. */
	public static boolean isElectric(DamageSource source) {
		if (source.is(DamageTypeTags.IS_LIGHTNING)) {
			return true;
		}
		if (source.getEntity() instanceof Player p) {
			ExperimentalState st = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
			return st != null && "power_07_electrokinesis".equals(st.activePower);
		}
		return false;
	}

	private static boolean isHulk(Player p) {
		try {
			return com.projecthero.mod.hulk.Hulk.isHulk(p);
		} catch (RuntimeException e) {
			return false;
		}
	}
}
