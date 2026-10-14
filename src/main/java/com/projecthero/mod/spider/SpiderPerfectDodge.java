package com.projecthero.mod.spider;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.mixin.MeleeAttackGoalAccessor;
import com.projecthero.mod.mixin.MobGoalsAccessor;
import com.projecthero.mod.network.SpiderReflexFxPayload;
import com.projecthero.mod.spider.data.SpiderMoveState;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.2 (user-approved): the <b>perfect dodge</b>, built with a generous window (the user worried nobody could react
 * in time).
 *
 * <ol>
 * <li><b>Early warning.</b> Spider-Sense predicts a hit about 0.6 s ({@link #WARN_LEAD} ticks) before it lands: a melee
 * mob hunting him whose swing timer ({@link MeleeAttackGoalAccessor}) and closing distance put the blow inside that
 * lead, a skeleton-style archer at full draw on him, any player winding up at him, and every projectile whose
 * simulated path hits him within the lead. His client gets {@link SpiderReflexFxPayload#DODGE_WARNING}: the comic
 * tingle lines, a screen-edge flash, a distinct tingle sound and a "Jump" cue by the crosshair.</li>
 * <li><b>Jump during the warning</b> (any time until the hit lands, plus {@link #LATE_GRACE} ticks of latency slack)
 * arms the dodge for {@link #ARMED_TICKS}. The next eligible hit in that time is <b>fully evaded</b>: a quick sidestep
 * (or a flip), the attacker and everything hostile within {@link #SLOW_RADIUS} blocks heavily slowed for 1.5 s (a melee
 * attacker's swing is pushed back as long), and his next melee or ability hit within 3 s is a <b>counter</b>: x2
 * damage and a knockback flourish. His client gets a desaturating vignette pulse and a sound.</li>
 * <li>Rules: 5 s cooldown; never while swinging or hanging; no world time slow (multiplayer); a Jump outside a warning
 * is just a jump; the automatic dodge chance ({@link SpiderSense#rollFor}) still runs for every other hit.</li>
 * </ol>
 */
public final class SpiderPerfectDodge {
	public static final String PERFECT_DODGE = "perfect_dodge";
	/** How far ahead (ticks) a hit is foreseen. */
	public static final int WARN_LEAD = 12;
	/** Jump this long after the predicted landing still counts (network latency / a slow mob). */
	public static final int LATE_GRACE = 4;
	/** A Jump arms the dodge for this long. */
	public static final int ARMED_TICKS = 16;
	public static final int COOLDOWN = 100;
	public static final int SLOW_TICKS = 30;
	public static final int SLOW_AMPLIFIER = 5;
	public static final double SLOW_RADIUS = 4.0;
	public static final int COUNTER_TICKS = 60;
	public static final float COUNTER_MULTIPLIER = 2.0f;

	/** player -> game time the open warning closes. */
	private static final Map<UUID, Long> WARN_UNTIL = new ConcurrentHashMap<>();
	/** player -> game time the warning cue was last sent (throttles the packet). */
	private static final Map<UUID, Long> WARN_SENT = new ConcurrentHashMap<>();
	/** player -> game time the armed dodge runs out. */
	private static final Map<UUID, Long> ARMED_UNTIL = new ConcurrentHashMap<>();
	/** player -> game time the counter window closes. */
	private static final Map<UUID, Long> COUNTER_UNTIL = new ConcurrentHashMap<>();
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private SpiderPerfectDodge() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (!REENTRANT.get() && source.getEntity() instanceof ServerPlayer p && p != entity && !blocked && taken > 0.0f
					&& entity.isAlive() && counterActive(p)) {
				counter(p, entity, taken);
			}
		});
	}

	public static void clearSessionState() {
		WARN_UNTIL.clear();
		WARN_SENT.clear();
		ARMED_UNTIL.clear();
		COUNTER_UNTIL.clear();
	}

	public static void clear(ServerPlayer player) {
		UUID id = player.getUUID();
		WARN_UNTIL.remove(id);
		WARN_SENT.remove(id);
		ARMED_UNTIL.remove(id);
		COUNTER_UNTIL.remove(id);
	}

	// ---------------- state ----------------

	private static long now(ServerPlayer player) {
		return player.level().getGameTime();
	}

	private static boolean before(Map<UUID, Long> map, ServerPlayer player) {
		Long until = map.get(player.getUUID());
		return until != null && now(player) <= until;
	}

	/** Is a Spider-Sense warning open (a hit is about to land)? */
	public static boolean warned(ServerPlayer player) {
		return before(WARN_UNTIL, player);
	}

	/** Has a Jump during the warning armed the dodge? */
	public static boolean armed(ServerPlayer player) {
		return before(ARMED_UNTIL, player);
	}

	/** Is his next hit a counter? */
	public static boolean counterActive(ServerPlayer player) {
		return before(COUNTER_UNTIL, player);
	}

	/** Not swinging, not hanging (on a line of any kind), alive, a Spider-Man. */
	public static boolean free(ServerPlayer player) {
		SpiderMoveState s = SpiderMoves.state(player);
		return SpiderMan.hasPower(player) && player.isAlive() && !player.isSpectator() && !SpiderSwing.isSwinging(player)
				&& !s.has(SpiderMoveState.FLAG_HANGING);
	}

	/** Off its 5 s cooldown? */
	public static boolean ready(ServerPlayer player) {
		return SpiderMan.abilityReady(player, PERFECT_DODGE);
	}

	/**
	 * Open (or keep open) the warning for a hit expected in {@code eta} ticks, coming from {@code from} (may be null).
	 * Tells his client on the onset and then every few ticks while it lasts. Public for the gametests.
	 */
	public static void warn(ServerPlayer player, int eta, Vec3 from) {
		long now = now(player);
		long until = now + Math.max(0, eta) + LATE_GRACE;
		Long cur = WARN_UNTIL.get(player.getUUID());
		boolean onset = cur == null || now > cur;
		if (cur == null || until > cur || onset) {
			WARN_UNTIL.put(player.getUUID(), until);
		}
		// already armed and the same threat is still coming (a slow walker): the armed dodge waits for it
		Long armed = ARMED_UNTIL.get(player.getUUID());
		if (armed != null && now <= armed && until + LATE_GRACE > armed) {
			ARMED_UNTIL.put(player.getUUID(), until + LATE_GRACE);
		}
		SpiderMoves.tingle(player, now); // the comic tingle lines round his head (everyone sees them)
		Long sent = WARN_SENT.get(player.getUUID());
		if (onset || sent == null || now - sent >= 4) {
			WARN_SENT.put(player.getUUID(), now);
			float yaw = player.getYRot();
			if (from != null) {
				Vec3 d = from.subtract(player.getEyePosition());
				if (d.x * d.x + d.z * d.z > 1.0E-4) {
					yaw = (float) (Mth.atan2(-d.x, d.z) * (180.0 / Math.PI));
				}
			}
			int left = (int) (WARN_UNTIL.get(player.getUUID()) - now);
			if (ServerPlayNetworking.canSend(player, SpiderReflexFxPayload.TYPE)) {
				ServerPlayNetworking.send(player, new SpiderReflexFxPayload(SpiderReflexFxPayload.DODGE_WARNING, left, yaw));
			}
		}
	}

	/**
	 * Jump pressed (from his client). Inside an open warning, free and off cooldown: the dodge is armed and true is
	 * returned. Anywhere else it was just a jump.
	 */
	public static boolean arm(ServerPlayer player) {
		if (!warned(player) || !free(player) || !ready(player)) {
			return false;
		}
		ARMED_UNTIL.put(player.getUUID(), now(player) + ARMED_TICKS);
		return true;
	}

	// ---------------- the dodge ----------------

	/**
	 * From {@code SpiderSense}'s damage veto (an eligible attack on him): when the dodge is armed, evade it completely
	 * and return true (the damage is cancelled).
	 */
	public static boolean tryPerfectDodge(ServerPlayer player, DamageSource source) {
		if (!armed(player) || !free(player) || !ready(player)) {
			return false;
		}
		ARMED_UNTIL.remove(player.getUUID());
		WARN_UNTIL.remove(player.getUUID());
		SpiderMan.triggerCooldown(player, PERFECT_DODGE, COOLDOWN);
		COUNTER_UNTIL.put(player.getUUID(), now(player) + COUNTER_TICKS);
		ServerLevel level = (ServerLevel) player.level();
		Entity attacker = source.getEntity();
		Entity direct = source.getDirectEntity();
		Vec3 from = direct != null ? direct.position() : attacker != null ? attacker.position() : null;

		// the sidestep: off to whichever side he was already drifting (else a coin flip), with a hop -- a flip if he
		// was already off the ground
		Vec3 toHim = from == null ? player.getLookAngle().reverse() : player.position().subtract(from);
		Vec3 flat = new Vec3(toHim.x, 0, toHim.z);
		flat = flat.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0f, player.getYRot()).reverse() : flat.normalize();
		Vec3 side = new Vec3(-flat.z, 0, flat.x);
		Vec3 v = player.getDeltaMovement();
		double drift = v.x * side.x + v.z * side.z;
		if (drift < -0.01 || (Math.abs(drift) <= 0.01 && player.getRandom().nextBoolean())) {
			side = side.reverse();
		}
		boolean flip = !player.onGround();
		Vec3 push = side.scale(flip ? 0.45 : 0.75).add(flat.scale(0.15)).add(0, flip ? 0.35 : 0.3, 0);
		AbilityHelpers.launchSelf(player, push);
		player.resetFallDistance();
		if (flip) {
			SpiderMoves.anim(player, SpiderAnim.COUNTER_FLIP);
		}

		// a projectile is dealt with (sent back / caught / swatted), never left to fly on through him
		if (direct instanceof Projectile p) {
			SpiderSense.deflect(player, p, player.getRandom());
		}
		// the attacker and everything hostile close by: heavily slowed for 1.5 s
		if (attacker instanceof LivingEntity le && le != player) {
			daze(player, le);
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SLOW_RADIUS),
				x -> x != player && x != attacker && x.isAlive() && x.distanceToSqr(player) <= SLOW_RADIUS * SLOW_RADIUS)) {
			if (HeroTargets.canHarm(player, e) && !com.projecthero.mod.squad.Squads.areAllies(player, e)
					&& (e instanceof net.minecraft.world.entity.monster.Enemy || (e instanceof Mob m && m.getTarget() == player)
							|| e instanceof net.minecraft.world.entity.player.Player)) {
				daze(player, e);
			}
		}

		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7f, 1.7f);
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.4, player.getZ(), 8, 0.3, 0.15, 0.3, 0.03);
		level.sendParticles(ParticleTypes.ENCHANTED_HIT, player.getX(), player.getY() + 1.1, player.getZ(), 10, 0.4, 0.5, 0.4, 0.1);
		player.displayClientMessage(Component.translatable("message.projecthero.spider_man.perfect_dodge")
				.withStyle(net.minecraft.ChatFormatting.AQUA), true);
		if (ServerPlayNetworking.canSend(player, SpiderReflexFxPayload.TYPE)) {
			ServerPlayNetworking.send(player, new SpiderReflexFxPayload(SpiderReflexFxPayload.DODGE_PERFECT, COUNTER_TICKS, 0f));
		}
		return true;
	}

	/** Heavily slowed for 1.5 s; a melee mob's next swing is pushed back as long. */
	private static void daze(ServerPlayer player, LivingEntity e) {
		AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, SLOW_AMPLIFIER);
		AbilityHelpers.applyControl(e, MobEffects.DIG_SLOWDOWN, SLOW_TICKS, 2);
		if (e instanceof Mob mob) {
			mob.getNavigation().stop();
			MeleeAttackGoal goal = meleeGoal(mob);
			if (goal != null) {
				MeleeAttackGoalAccessor acc = (MeleeAttackGoalAccessor) goal;
				acc.projecthero$setTicksUntilNextAttack(Math.max(acc.projecthero$ticksUntilNextAttack(), SLOW_TICKS));
			}
		}
		if (player.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.ITEM_COBWEB, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 4,
					e.getBbWidth() * 0.3, e.getBbHeight() * 0.25, e.getBbWidth() * 0.3, 0.0);
		}
	}

	/** The counter: the hit he just landed is doubled, with a knockback flourish. */
	private static void counter(ServerPlayer player, LivingEntity target, float taken) {
		COUNTER_UNTIL.remove(player.getUUID());
		REENTRANT.set(true);
		try {
			target.invulnerableTime = 0;
			AbilityHelpers.hurt(player, target, taken * (COUNTER_MULTIPLIER - 1.0f));
		} finally {
			REENTRANT.set(false);
		}
		Vec3 d = target.position().subtract(player.position());
		Vec3 flat = new Vec3(d.x, 0, d.z);
		flat = flat.lengthSqr() < 1.0E-4 ? player.getLookAngle() : flat.normalize();
		target.knockback(1.4, -flat.x, -flat.z);
		target.setDeltaMovement(target.getDeltaMovement().add(0, 0.35, 0));
		target.hurtMarked = true;
		if (player.level() instanceof ServerLevel level) {
			Vec3 c = target.position().add(0, target.getBbHeight() * 0.6, 0);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 14, 0.3, 0.3, 0.3, 0.3);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0f, 0.9f);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.8f, 1.2f);
		}
		player.displayClientMessage(Component.translatable("message.projecthero.spider_man.counter_hit")
				.withStyle(net.minecraft.ChatFormatting.GOLD), true);
		if (ServerPlayNetworking.canSend(player, SpiderReflexFxPayload.TYPE)) {
			ServerPlayNetworking.send(player, new SpiderReflexFxPayload(SpiderReflexFxPayload.COUNTER_LANDED, 0, 0f));
		}
	}

	// ---------------- the prediction ----------------

	static MeleeAttackGoal meleeGoal(Mob mob) {
		for (WrappedGoal w : ((MobGoalsAccessor) mob).projecthero$goalSelector().getAvailableGoals()) {
			if (w.getGoal() instanceof MeleeAttackGoal g) {
				return g;
			}
		}
		return null;
	}

	/** Per-player tick: look 0.6 s ahead for a hit and open the warning (only while the dodge is available). */
	public static void serverTick(ServerPlayer player) {
		if ((player.tickCount & 1) != 0 || !free(player) || !ready(player) || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		Threat t = predict(player, level);
		if (t != null) {
			warn(player, t.eta, t.from);
		}
	}

	/** The soonest hit expected within {@link #WARN_LEAD} ticks. */
	public record Threat(int eta, Vec3 from) {
	}

	/** The soonest hit on {@code player} expected inside the lead, or null. Public for the gametests. */
	public static Threat predict(ServerPlayer player, ServerLevel level) {
		Threat best = null;
		AABB melee = player.getBoundingBox().inflate(6.0, 3.0, 6.0);
		for (Mob mob : level.getEntitiesOfClass(Mob.class, melee, m -> m.isAlive() && m.getTarget() == player && !SpiderWrap.isWrapped(m))) {
			int eta = meleeEta(mob, player);
			if (eta >= 0 && eta <= WARN_LEAD && (best == null || eta < best.eta)) {
				best = new Threat(eta, mob.position().add(0, mob.getBbHeight() * 0.5, 0));
			}
			// a bow at full draw on him: the arrow leaves at 20 ticks of draw
			if (mob.isUsingItem() && SpiderSense.aimingAt(mob, player)) {
				int left = 20 - mob.getTicksUsingItem();
				if (left >= 0 && left <= WARN_LEAD - 2 && (best == null || left + 2 < best.eta)) {
					best = new Threat(left + 2, mob.getEyePosition());
				}
			}
		}
		// archers further out
		for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(20.0, 8.0, 20.0),
				m -> m.isAlive() && m.isUsingItem() && m.getTarget() == player && m.distanceToSqr(player) > 36.0)) {
			if (SpiderSense.aimingAt(mob, player)) {
				int flight = (int) Math.ceil(Math.sqrt(mob.distanceToSqr(player)) / 2.0);
				int left = 20 - mob.getTicksUsingItem() + flight;
				if (left >= 0 && left <= WARN_LEAD && (best == null || left < best.eta)) {
					best = new Threat(left, mob.getEyePosition());
				}
			}
		}
		// another player winding up at him, close and facing him (squadmates excepted)
		for (ServerPlayer other : level.getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(4.0),
				o -> o != player && o.isAlive() && !o.isSpectator() && !com.projecthero.mod.squad.Squads.areAllies(o, player))) {
			Vec3 to = player.position().subtract(other.position());
			if (to.lengthSqr() > 1.0E-4 && other.getLookAngle().dot(to.normalize()) > 0.8 && other.getAttackStrengthScale(0f) > 0.6f
					&& (other.swinging || other.getMainHandItem().getItem() instanceof net.minecraft.world.item.SwordItem
							|| other.getMainHandItem().getItem() instanceof net.minecraft.world.item.AxeItem)) {
				int eta = other.swinging ? 1 : 6;
				if (best == null || eta < best.eta) {
					best = new Threat(eta, other.position().add(0, 1.0, 0));
				}
			}
		}
		// projectiles: simulate each one's flight and see whether it meets him inside the lead
		AABB near = player.getBoundingBox().inflate(32.0, 16.0, 32.0);
		AABB body = player.getBoundingBox().inflate(0.45);
		for (Projectile proj : level.getEntitiesOfClass(Projectile.class, near, e -> e.isAlive() && e.getOwner() != player)) {
			if (proj.getOwner() != null && com.projecthero.mod.squad.Squads.areAllies(proj.getOwner(), player)) {
				continue;
			}
			int eta = projectileEta(proj, body);
			if (eta >= 0 && (best == null || eta < best.eta)) {
				best = new Threat(eta, proj.position());
			}
		}
		return best;
	}

	/**
	 * Ticks until {@code mob}'s melee blow lands on {@code player}, or -1 when none is coming soon: in reach (or about to
	 * be, at its closing speed) and its swing timer about to run out. A mob with no melee goal (brain AI, modded) counts
	 * as about to swing once it is in reach.
	 */
	static int meleeEta(Mob mob, ServerPlayer player) {
		AABB reach = mob.getBoundingBox().inflate(0.83, 0.0, 0.83);
		AABB him = player.getBoundingBox();
		double gx = Math.max(0, Math.max(reach.minX - him.maxX, him.minX - reach.maxX));
		double gz = Math.max(0, Math.max(reach.minZ - him.maxZ, him.minZ - reach.maxZ));
		double gy = Math.max(0, Math.max(reach.minY - him.maxY, him.minY - reach.maxY));
		double gap = Math.sqrt(gx * gx + gz * gz);
		if (gy > 0.5) {
			return -1;
		}
		int arrive = 0;
		if (gap > 0.0) {
			Vec3 to = player.position().subtract(mob.position());
			Vec3 flat = new Vec3(to.x, 0, to.z);
			double closing = flat.lengthSqr() < 1.0E-4 ? 0.0 : mob.getDeltaMovement().dot(flat.normalize());
			double speed = Math.max(closing, mob.getNavigation().isInProgress() ? 0.12 : 0.0);
			if (speed <= 1.0E-3) {
				return -1;
			}
			arrive = (int) Math.ceil(gap / speed);
		}
		MeleeAttackGoal goal = meleeGoal(mob);
		int timer = goal == null ? 0 : Math.max(0, ((MeleeAttackGoalAccessor) goal).projecthero$ticksUntilNextAttack());
		if (goal == null && gap > 0.6) {
			return -1;
		}
		return Math.max(arrive, timer);
	}

	/** Ticks until {@code proj} enters {@code body} along its simulated flight (within the lead), or -1. */
	static int projectileEta(Projectile proj, AABB body) {
		Vec3 v = proj.getDeltaMovement();
		if (v.lengthSqr() < 0.01 || (proj instanceof AbstractArrow a && a.isNoPhysics())) {
			return -1;
		}
		double gravity = proj.isNoGravity() ? 0.0 : proj instanceof AbstractArrow ? 0.05 : proj instanceof ThrowableProjectile ? 0.03 : 0.0;
		double drag = proj instanceof AbstractArrow || proj instanceof ThrowableProjectile ? 0.99 : 1.0;
		Vec3 p = proj.position();
		for (int i = 1; i <= WARN_LEAD; i++) {
			Vec3 next = p.add(v);
			if (body.contains(next) || body.clip(p, next).isPresent()) {
				return i;
			}
			p = next;
			v = v.scale(drag).subtract(0, gravity, 0);
		}
		return -1;
	}
}
