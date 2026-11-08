package com.projecthero.mod.andy;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.andy.data.AndyState;
import com.projecthero.mod.andy.entity.AndyCloneEntity;
import com.projecthero.mod.andy.entity.AndyPartEntity;
import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Andy's moves (v0.17.3); see {@link AndyAbilityManager} for the keys. Timed moves are small per-player runs stepped from
 * {@link #tick}; every map here is dropped by {@link #clear}, {@link #forget} and {@link #clearSessionState}.
 */
public final class AndyAbilities {
	public static final String PARTS_BULLET = "parts_bullet";
	public static final String CRIMSON_BULLET = "crimson_bullet";
	public static final String CRESCENT = "crimson_crescent_moon";
	public static final String GREAT_CRESCENT = "great_crescent_moon";
	public static final String PROPULSION = "blood_propulsion";
	public static final String VORTEX = "vortex_bullet";
	public static final String HEAD_BULLET = "parts_bullet_head";
	public static final String REGEN = "regen_speed";
	public static final String CARD = "pull_the_card";
	public static final String DEAD_LINE = "dead_line";
	public static final String QUARDEAD = "dead_quardead";
	public static final String VULCAN = "dead_vulcan";
	public static final String DIVISION = "division_bullet";
	public static final String DEAD_BLADE = "dead_blade";
	public static final String DEAD_ROAD = "dead_road";

	/** Great Deep-Crimson Crescent Moon: where the waves run from, which way, since when, and who they hit. */
	private record Waves(Vec3 from, Vec3 dir, long start, Set<Integer> hit) {
	}

	private static final Map<UUID, Waves> WAVES = new ConcurrentHashMap<>();
	/** Dead Vulcan: shots still to fire. */
	private static final Map<UUID, Integer> VULCANS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_HEADBUTT = new ConcurrentHashMap<>();

	private AndyAbilities() {
	}

	static void clearSessionState() {
		WAVES.clear();
		VULCANS.clear();
		LAST_HEADBUTT.clear();
	}

	static void forget(UUID id) {
		WAVES.remove(id);
		VULCANS.remove(id);
		LAST_HEADBUTT.remove(id);
	}

	/** Ends every running move. */
	public static void clear(ServerPlayer p) {
		UUID id = p.getUUID();
		WAVES.remove(id);
		VULCANS.remove(id);
		AndyState s = Andy.peek(p);
		if (s != null && s.flags != 0) {
			AndyState n = s.copy();
			n.flags = 0;
			Andy.save(p, n);
		}
	}

	// ---------------------------------------------------------------- shared

	private static boolean begin(ServerPlayer p, String id) {
		if (!Andy.canAct(p)) {
			return false;
		}
		int cd = Andy.cooldownRemaining(p, id);
		if (cd > 0) {
			Andy.say(p, "message.projecthero.andy.cooldown", ChatFormatting.GRAY, Component.translatable("projecthero.andy.ability." + id),
					String.format(java.util.Locale.ROOT, "%.1f", cd / 20.0f));
			return false;
		}
		return true;
	}

	/** The move needs the main hand. */
	private static boolean hand(ServerPlayer p) {
		if (Andy.mainArmMissing(p)) {
			Andy.say(p, "message.projecthero.andy.no_arm", ChatFormatting.GRAY);
			return false;
		}
		return true;
	}

	private static void cooldown(ServerPlayer p, String id, int ticks) {
		AndyState n = Andy.state(p).copy();
		n.abilityReadyAt.put(id, p.level().getGameTime() + ticks);
		Andy.save(p, n);
	}

	private static void anim(ServerPlayer p, int id) {
		AndyState n = Andy.state(p).copy();
		n.animId = id;
		n.animStart = p.level().getGameTime();
		Andy.save(p, n);
	}

	private static void flag(ServerPlayer p, int flag, boolean on) {
		AndyState n = Andy.state(p).copy();
		n.setFlag(flag, on);
		if (on) {
			n.holdStart = p.level().getGameTime();
		}
		Andy.save(p, n);
	}

	private static ServerLevel level(ServerPlayer p) {
		return (ServerLevel) p.level();
	}

	private static void sound(ServerPlayer p, SoundEvent s, float vol, float pitch) {
		p.level().playSound(null, p.getX(), p.getY() + 1.0, p.getZ(), s, SoundSource.PLAYERS, vol, pitch);
	}

	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		return flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
	}

	/** The main hand's fingertip, about where the arm points along the look. */
	public static Vec3 handOrigin(ServerPlayer p) {
		Vec3 r = AndyParts.right(p);
		double side = Andy.mainArm(p) == AndyState.RIGHT_ARM ? 1 : -1;
		return p.getEyePosition().add(r.scale(0.32 * side)).add(0, -0.32, 0).add(p.getLookAngle().scale(0.55));
	}

	static boolean isBoss(LivingEntity target) {
		return target.getMaxHealth() >= AndyConfig.BOSS_HEALTH_THRESHOLD || TitanCombat.isBoss(target);
	}

	/** One hit from a move (Victor's multiplier and the boss cap applied). */
	public static boolean strike(ServerPlayer owner, LivingEntity target, Vec3 origin, float damage, double knockback, double lift) {
		if (!HeroTargets.canHarm(owner, target)) {
			return false;
		}
		float d = damage * (Andy.victor(owner) ? AndyConfig.VICTOR_DAMAGE : 1f);
		boolean boss = isBoss(target);
		if (boss) {
			d = Math.min(d, target.getMaxHealth() * AndyConfig.BOSS_MAX_FRACTION_PER_HIT);
		}
		if (!AbilityHelpers.hurtBurst(owner, target, owner.level().damageSources().playerAttack(owner), Math.max(1f, d))) {
			return false;
		}
		if (!boss) {
			if (knockback > 0) {
				AbilityHelpers.knockbackFrom(target, origin, knockback);
			}
			if (lift > 0) {
				AbilityHelpers.push(target, new Vec3(0, lift, 0));
			}
		}
		Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
		level(owner).sendParticles(Andy.BLOOD, c.x, c.y, c.z, 6, 0.25, 0.3, 0.25, 0.0);
		return true;
	}

	private static java.util.List<LivingEntity> around(ServerPlayer p, Vec3 c, double r) {
		return level(p).getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r),
				e -> e != p && !(e instanceof AndyCloneEntity) && HeroTargets.canHarm(p, e)
						&& e.position().add(0, e.getBbHeight() * 0.5, 0).distanceToSqr(c) <= r * r);
	}

	/** The things in a frontal arc of {@code reach} blocks and {@code cos} half-angle. */
	private static java.util.List<LivingEntity> arc(ServerPlayer p, double reach, double cos) {
		Vec3 c = p.position().add(0, 0.9, 0);
		Vec3 f = flatLook(p);
		return around(p, c, reach).stream().filter(e -> {
			Vec3 to = new Vec3(e.getX() - p.getX(), 0, e.getZ() - p.getZ());
			return to.lengthSqr() < 0.8 || to.normalize().dot(f) >= cos;
		}).toList();
	}

	// ---------------------------------------------------------------- R: fingers

	public static void partsBullet(ServerPlayer p) {
		if (!begin(p, PARTS_BULLET) || !hand(p)) {
			return;
		}
		cooldown(p, PARTS_BULLET, AndyConfig.PARTS_BULLET_COOLDOWN);
		anim(p, AndyState.ANIM_FINGER);
		AndyPartEntity.shoot(p, AndyPartEntity.STYLE_FINGER, -1, handOrigin(p), p.getLookAngle().scale(AndyConfig.FINGER_SPEED), AndyConfig.PARTS_BULLET_DAMAGE);
		Vec3 h = handOrigin(p);
		AndySfx.partsBullet(level(p), h); // v0.17.3 Andy sounds
		level(p).sendParticles(Andy.BLOOD, h.x, h.y, h.z, 3, 0.03, 0.03, 0.03, 0.0);
	}

	public static void crimsonBullet(ServerPlayer p) {
		if (!begin(p, CRIMSON_BULLET) || !hand(p)) {
			return;
		}
		cooldown(p, CRIMSON_BULLET, AndyConfig.CRIMSON_COOLDOWN);
		anim(p, AndyState.ANIM_CRIMSON);
		AndyPartEntity.shoot(p, AndyPartEntity.STYLE_CRIMSON, -1, handOrigin(p), p.getLookAngle().scale(AndyConfig.FINGER_SPEED * 1.2), AndyConfig.CRIMSON_DAMAGE);
		AndyState n = Andy.state(p).copy();
		n.slowRegenUntil = p.level().getGameTime() + AndyConfig.CRIMSON_SLOW_TICKS;
		Andy.save(p, n);
		Vec3 h = handOrigin(p);
		AndySfx.crimsonBullet(level(p), h); // v0.17.3 Andy sounds
		level(p).sendParticles(Andy.BLOOD, h.x, h.y, h.z, 6, 0.1, 0.1, 0.1, 0.0);
	}

	// ---------------------------------------------------------------- G: Kurikara

	public static void crescent(ServerPlayer p) {
		if (!begin(p, CRESCENT) || !hand(p)) {
			return;
		}
		cooldown(p, CRESCENT, AndyConfig.CRESCENT_COOLDOWN);
		anim(p, AndyState.ANIM_CRESCENT);
		Vec3 c = p.position().add(0, 1.0, 0);
		for (LivingEntity e : arc(p, AndyConfig.CRESCENT_REACH, 0.2)) {
			strike(p, e, c, AndyConfig.CRESCENT_DAMAGE, 0.6, 0.1);
		}
		Vec3 f = flatLook(p);
		Vec3 r = AndyParts.right(p);
		for (int i = -6; i <= 6; i++) {
			double a = i / 6.0 * 1.2;
			Vec3 at = c.add(f.scale(Math.cos(a) * 2.4)).add(r.scale(Math.sin(a) * 2.4)).add(0, -i * 0.04, 0);
			level(p).sendParticles(Andy.BLOOD, at.x, at.y, at.z, 2, 0.04, 0.04, 0.04, 0.0);
		}
		level(p).sendParticles(ParticleTypes.SWEEP_ATTACK, c.x + f.x * 1.6, c.y, c.z + f.z * 1.6, 1, 0, 0, 0, 0);
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.8f);
		sound(p, SoundEvents.TRIDENT_RETURN, 0.7f, 1.4f);
	}

	public static void greatCrescent(ServerPlayer p) {
		if (!begin(p, GREAT_CRESCENT) || !hand(p)) {
			return;
		}
		cooldown(p, GREAT_CRESCENT, AndyConfig.GREAT_CRESCENT_COOLDOWN);
		anim(p, AndyState.ANIM_GREAT_CRESCENT);
		WAVES.put(p.getUUID(), new Waves(p.position(), flatLook(p), p.level().getGameTime(), new HashSet<>()));
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.5f);
		sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.6f, 0.6f);
	}

	private static void tickWaves(ServerPlayer p, Waves w, long now) {
		long age = now - w.start() - 5; // the sword comes down first
		if (age < 0) {
			return;
		}
		double d = 1.2 + age * 1.1;
		if (d > AndyConfig.GREAT_CRESCENT_RANGE) {
			WAVES.remove(p.getUUID());
			return;
		}
		Vec3 front = w.from().add(w.dir().scale(d));
		Vec3 side = new Vec3(-w.dir().z, 0, w.dir().x);
		ServerLevel level = level(p);
		double width = 1.4 + d * 0.06;
		double height = 2.6 + d * 0.08;
		// the waves themselves are drawn by every client (AndyEffectsRenderer); a spray of blood along the crest here
		for (int i = -4; i <= 4 && age % 2 == 0; i++) {
			double s = i / 4.0;
			double back = s * s * 0.9;
			for (int k = 2; k < 3; k++) {
				double y = k / 2.0 * height * (1.0 - 0.45 * s * s);
				Vec3 at = front.add(side.scale(s * width)).add(w.dir().scale(-back)).add(0, y, 0);
				level.sendParticles(Andy.BLOOD, at.x, at.y, at.z, 1, 0.1, 0.05, 0.1, 0.0);
			}
		}
		if (age % 3 == 0) {
			level.playSound(null, front.x, front.y, front.z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.8f, 0.6f);
		}
		for (LivingEntity e : around(p, front.add(0, 1, 0), width + 0.8)) {
			if (w.hit().add(e.getId())) {
				strike(p, e, front.subtract(w.dir()), AndyConfig.GREAT_CRESCENT_DAMAGE, 1.2, 0.45);
			}
		}
	}

	// ---------------------------------------------------------------- X: movement

	public static void startPropulsion(ServerPlayer p) {
		if (!begin(p, PROPULSION)) {
			return;
		}
		if (Andy.missing(p, AndyState.RIGHT_ARM) && Andy.missing(p, AndyState.LEFT_ARM)) {
			Andy.say(p, "message.projecthero.andy.no_arm", ChatFormatting.GRAY);
			return;
		}
		flag(p, AndyState.FLAG_PROPELLING, true);
		sound(p, SoundEvents.GENERIC_SPLASH, 1.0f, 1.4f);
	}

	public static void stopPropulsion(ServerPlayer p) {
		AndyState s = Andy.state(p);
		if (!s.flag(AndyState.FLAG_PROPELLING)) {
			return;
		}
		flag(p, AndyState.FLAG_PROPELLING, false);
		cooldown(p, PROPULSION, AndyConfig.PROPULSION_COOLDOWN);
		p.resetFallDistance();
	}

	private static void tickPropulsion(ServerPlayer p, AndyState s, long now) {
		if (now - s.holdStart > AndyConfig.PROPULSION_MAX || (Andy.missing(p, AndyState.RIGHT_ARM) && Andy.missing(p, AndyState.LEFT_ARM))) {
			stopPropulsion(p);
			return;
		}
		boolean oneArm = Andy.missing(p, AndyState.RIGHT_ARM) || Andy.missing(p, AndyState.LEFT_ARM);
		double speed = AndyConfig.PROPULSION_SPEED * (oneArm ? 0.6 : 1.0);
		Vec3 v = p.getLookAngle().scale(speed);
		AbilityHelpers.launchSelf(p, v);
		if (now % 4 == 0) {
			p.causeFoodExhaustion(0.4f);
		}
		Vec3 r = AndyParts.right(p);
		Vec3 back = p.getLookAngle().scale(-0.4);
		ServerLevel level = level(p);
		for (int side = -1; side <= 1; side += 2) {
			int arm = side > 0 ? AndyState.RIGHT_ARM : AndyState.LEFT_ARM;
			if (Andy.missing(p, arm)) {
				continue;
			}
			Vec3 wrist = p.position().add(r.scale(0.42 * side)).add(0, 0.75, 0).add(back);
			level.sendParticles(Andy.BLOOD_BIG, wrist.x, wrist.y, wrist.z, 3, 0.05, 0.05, 0.05, 0.0);
			level.sendParticles(Andy.BLOOD, wrist.x - v.x, wrist.y - v.y, wrist.z - v.z, 3, 0.12, 0.12, 0.12, 0.0);
		}
		if (now % 6 == 0) {
			sound(p, SoundEvents.GENERIC_SPLASH, 0.5f, 1.6f);
		}
	}

	// ---------------------------------------------------------------- Z: launched parts

	public static void vortexBullet(ServerPlayer p) {
		if (!begin(p, VORTEX) || !hand(p)) {
			return;
		}
		int arm = Andy.mainArm(p);
		cooldown(p, VORTEX, AndyConfig.VORTEX_COOLDOWN);
		anim(p, AndyState.ANIM_VORTEX);
		Andy.breakPart(p, arm, AndyState.KIND_LAUNCHED);
		Vec3 r = AndyParts.right(p);
		double side = arm == AndyState.RIGHT_ARM ? 1 : -1;
		Vec3 from = p.getEyePosition().add(r.scale(0.36 * side)).add(0, -0.3, 0).add(p.getLookAngle().scale(0.4));
		AndyPartEntity.shoot(p, AndyPartEntity.STYLE_DRILL, arm, from, p.getLookAngle().scale(AndyConfig.VORTEX_SPEED), AndyConfig.VORTEX_DAMAGE);
		sound(p, SoundEvents.TRIDENT_RIPTIDE_3.value(), 1.0f, 1.2f);
		sound(p, SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, 0.8f, 0.5f);
		level(p).sendParticles(Andy.BLOOD, from.x, from.y, from.z, 5, 0.1, 0.1, 0.1, 0.0);
	}

	public static void headBullet(ServerPlayer p) {
		if (!begin(p, HEAD_BULLET)) {
			return;
		}
		if (Andy.missing(p, AndyState.HEAD)) {
			Andy.say(p, "message.projecthero.andy.no_head", ChatFormatting.GRAY);
			return;
		}
		cooldown(p, HEAD_BULLET, AndyConfig.HEAD_BULLET_COOLDOWN);
		anim(p, AndyState.ANIM_HEAD);
		Vec3 from = p.getEyePosition().add(p.getLookAngle().scale(0.3));
		Andy.breakPart(p, AndyState.HEAD, AndyState.KIND_LAUNCHED);
		AndyPartEntity.shoot(p, AndyPartEntity.STYLE_HEAD, AndyState.HEAD, from, p.getLookAngle().scale(AndyConfig.HEAD_SPEED), AndyConfig.HEAD_BULLET_DAMAGE);
		sound(p, SoundEvents.CROSSBOW_SHOOT, 1.0f, 0.5f);
		sound(p, SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, 0.8f, 0.6f);
		level(p).sendParticles(Andy.BLOOD, from.x, from.y - 0.25, from.z, 5, 0.12, 0.05, 0.12, 0.0);
	}

	// ---------------------------------------------------------------- V

	public static void pullCard(ServerPlayer p) {
		if (!Andy.canAct(p)) {
			return;
		}
		AndyState s = Andy.state(p);
		long now = p.level().getGameTime();
		if (s.victorUntil > now) {
			// the card goes back in early
			AndyState n = s.copy();
			n.victorUntil = now;
			Andy.save(p, n);
			return;
		}
		if (!begin(p, CARD)) {
			return;
		}
		AndyState n = Andy.state(p).copy();
		n.victorUntil = now + AndyConfig.VICTOR_TICKS;
		n.abilityReadyAt.put(CARD, now + AndyConfig.VICTOR_COOLDOWN);
		n.animId = AndyState.ANIM_CARD;
		n.animStart = now;
		n.flags &= ~(AndyState.FLAG_DEAD_LINE);
		Andy.save(p, n);
		Andy.reconcile(p);
		VULCANS.remove(p.getUUID());
		sound(p, SoundEvents.WITHER_AMBIENT, 0.6f, 1.4f);
		sound(p, SoundEvents.ELYTRA_FLYING, 0.4f, 2.0f);
		Vec3 c = p.getEyePosition();
		level(p).sendParticles(Andy.BLOOD, c.x, c.y + 0.3, c.z, 10, 0.3, 0.2, 0.3, 0.0);
		Andy.say(p, "message.projecthero.andy.victor", ChatFormatting.DARK_RED);
	}

	// ---------------------------------------------------------------- C: Dead Line (beam), Dead Quardead

	public static void startDeadLine(ServerPlayer p) {
		if (!begin(p, DEAD_LINE) || !hand(p)) {
			return;
		}
		flag(p, AndyState.FLAG_DEAD_LINE, true);
		sound(p, SoundEvents.BUCKET_EMPTY, 1.0f, 1.8f);
	}

	public static void stopDeadLine(ServerPlayer p) {
		if (!Andy.state(p).flag(AndyState.FLAG_DEAD_LINE)) {
			return;
		}
		flag(p, AndyState.FLAG_DEAD_LINE, false);
		cooldown(p, DEAD_LINE, AndyConfig.DEAD_LINE_COOLDOWN);
	}

	/** The beam's end this tick: the first block or creature along the look from the fingertip. */
	public static Vec3[] beam(ServerPlayer p, double range, LivingEntity[] hitOut) {
		Vec3 from = handOrigin(p);
		Vec3 dir = p.getLookAngle();
		Vec3 far = p.getEyePosition().add(dir.scale(range));
		BlockHitResult bhr = p.level().clip(new ClipContext(from, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? far : bhr.getLocation();
		EntityHitResult ehr = ProjectileUtil.getEntityHitResult(p, from, end, new AABB(from, end).inflate(1.0),
				e -> e instanceof LivingEntity le && !(e instanceof AndyCloneEntity) && HeroTargets.canHarm(p, le) && e.isPickable(), from.distanceToSqr(end));
		if (ehr != null && ehr.getEntity() instanceof LivingEntity le) {
			end = ehr.getLocation();
			hitOut[0] = le;
		}
		return new Vec3[] { from, end };
	}

	private static void tickDeadLine(ServerPlayer p, AndyState s, long now) {
		long age = now - s.holdStart;
		if (age > AndyConfig.DEAD_LINE_MAX || Andy.mainArmMissing(p)) {
			stopDeadLine(p);
			return;
		}
		Vec3 from = handOrigin(p);
		com.projecthero.mod.beamclash.BeamClash.report(p, from, p.getLookAngle(), AndyConfig.DEAD_LINE_RANGE, 1.0f, 0xC0101A); // v0.17.3
		LivingEntity[] hit = new LivingEntity[1];
		Vec3[] seg = beam(p, AndyConfig.DEAD_LINE_RANGE, hit);
		if (hit[0] != null && age % AndyConfig.DEAD_LINE_HIT_EVERY == 0 && !com.projecthero.mod.beamclash.BeamClash.inClash(p)) {
			strike(p, hit[0], from, AndyConfig.DEAD_LINE_DAMAGE, 0.15, 0.0);
		}
		if (age % 3 == 0) {
			level(p).sendParticles(Andy.BLOOD, seg[1].x, seg[1].y, seg[1].z, 3, 0.1, 0.1, 0.1, 0.0);
		}
		if (age % 8 == 0) {
			sound(p, SoundEvents.BUCKET_EMPTY, 0.4f, 2.0f);
		}
		if (age % 10 == 0) {
			p.causeFoodExhaustion(0.6f);
		}
	}

	public static void quardead(ServerPlayer p) {
		if (!begin(p, QUARDEAD)) {
			return;
		}
		cooldown(p, QUARDEAD, AndyConfig.QUARDEAD_COOLDOWN);
		anim(p, AndyState.ANIM_QUARDEAD);
		Vec3 f = flatLook(p);
		Vec3 r = AndyParts.right(p);
		Vec3[] at = { p.position().add(r.scale(1.3)), p.position().add(r.scale(-1.3)), p.position().add(f.scale(-1.4)) };
		float dmg = AndyConfig.CLONE_DAMAGE * (Andy.victor(p) ? AndyConfig.VICTOR_DAMAGE : 1f);
		int[] tints = { AndyCloneEntity.TINT_SKIN, AndyCloneEntity.TINT_BLOOD, AndyCloneEntity.TINT_BONE };
		for (int i = 0; i < 3; i++) {
			AndyCloneEntity.spawn(p, at[i].x, p.getY(), at[i].z, tints[i], AndyConfig.QUARDEAD_TICKS, dmg);
			level(p).sendParticles(Andy.BLOOD, at[i].x, p.getY() + 1, at[i].z, 5, 0.3, 0.6, 0.3, 0.0);
		}
		sound(p, SoundEvents.SLIME_SQUISH, 1.2f, 0.6f);
		sound(p, SoundEvents.SKELETON_AMBIENT, 0.8f, 0.8f);
	}

	// ---------------------------------------------------------------- Victor's kit

	public static void vulcan(ServerPlayer p) {
		if (!begin(p, VULCAN) || !hand(p)) {
			return;
		}
		cooldown(p, VULCAN, AndyConfig.VULCAN_COOLDOWN);
		anim(p, AndyState.ANIM_VULCAN);
		VULCANS.put(p.getUUID(), AndyConfig.VULCAN_SHOTS);
	}

	/** Dead Vulcan shots still to fire (tests). */
	public static int vulcanLeft(ServerPlayer p) {
		Integer v = VULCANS.get(p.getUUID());
		return v == null ? 0 : v;
	}

	private static void tickVulcan(ServerPlayer p, int left, long now) {
		if (now % 2 != 0) {
			return;
		}
		if (left <= 0 || Andy.mainArmMissing(p)) {
			VULCANS.remove(p.getUUID());
			return;
		}
		VULCANS.put(p.getUUID(), left - 1);
		Vec3 spread = new Vec3((p.getRandom().nextDouble() - 0.5) * 0.06, (p.getRandom().nextDouble() - 0.5) * 0.06, (p.getRandom().nextDouble() - 0.5) * 0.06);
		AndyPartEntity.shoot(p, AndyPartEntity.STYLE_VULCAN, -1, handOrigin(p), p.getLookAngle().add(spread).normalize().scale(AndyConfig.FINGER_SPEED * 1.1),
				AndyConfig.VULCAN_DAMAGE);
		sound(p, SoundEvents.CROSSBOW_SHOOT, 0.6f, 1.8f + p.getRandom().nextFloat() * 0.2f);
	}

	public static void division(ServerPlayer p) {
		if (!begin(p, DIVISION) || !hand(p)) {
			return;
		}
		cooldown(p, DIVISION, AndyConfig.DIVISION_COOLDOWN);
		anim(p, AndyState.ANIM_DIVISION);
		Vec3 look = p.getLookAngle();
		Vec3 r = AndyParts.right(p);
		for (int i = -1; i <= 1; i++) {
			Vec3 v = look.add(r.scale(i * 0.12)).normalize().scale(AndyConfig.FINGER_SPEED * 0.8);
			AndyPartEntity.shoot(p, AndyPartEntity.STYLE_DIVISION, -1, handOrigin(p), v, AndyConfig.PARTS_BULLET_DAMAGE);
		}
		sound(p, SoundEvents.CROSSBOW_SHOOT, 1.0f, 1.0f);
	}

	/** A Division Bullet finger grows into a short-lived self where it lands. */
	public static void divisionClone(ServerPlayer p, Vec3 at) {
		AndyCloneEntity.spawn(p, at.x, at.y, at.z, AndyCloneEntity.TINT_BLOOD, AndyConfig.DIVISION_CLONE_TICKS,
				AndyConfig.CLONE_DAMAGE * AndyConfig.VICTOR_DAMAGE);
		level(p).sendParticles(Andy.BLOOD, at.x, at.y + 1, at.z, 5, 0.3, 0.6, 0.3, 0.0);
		p.level().playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0f, 0.7f);
	}

	public static void deadBlade(ServerPlayer p) {
		if (!begin(p, DEAD_BLADE) || !hand(p)) {
			return;
		}
		cooldown(p, DEAD_BLADE, AndyConfig.DEAD_BLADE_COOLDOWN);
		anim(p, AndyState.ANIM_DEAD_BLADE);
		Vec3 c = p.position().add(0, 1.0, 0);
		for (LivingEntity e : arc(p, AndyConfig.DEAD_BLADE_REACH, -0.05)) {
			strike(p, e, c, AndyConfig.DEAD_BLADE_DAMAGE, 1.3, 0.3);
		}
		Vec3 f = flatLook(p);
		Vec3 r = AndyParts.right(p);
		for (int i = -6; i <= 6; i++) {
			double a = i / 6.0 * 1.6;
			for (double rad = 4.0; rad <= 5.0; rad += 1.0) {
				Vec3 at = c.add(f.scale(Math.cos(a) * rad)).add(r.scale(Math.sin(a) * rad));
				level(p).sendParticles(Andy.BLOOD, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.0);
			}
		}
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.3f, 0.5f);
		sound(p, SoundEvents.ANVIL_LAND, 0.4f, 0.6f);
	}

	public static void deadRoad(ServerPlayer p) {
		if (!begin(p, DEAD_ROAD) || !hand(p)) {
			return;
		}
		cooldown(p, DEAD_ROAD, AndyConfig.DEAD_ROAD_COOLDOWN);
		flag(p, AndyState.FLAG_DEAD_ROAD, true);
		sound(p, SoundEvents.WARDEN_SONIC_CHARGE, 0.8f, 1.4f);
	}

	private static void tickDeadRoad(ServerPlayer p, AndyState s, long now) {
		long age = now - s.holdStart;
		if (age > AndyConfig.DEAD_ROAD_TICKS || Andy.mainArmMissing(p) || !Andy.victor(p)) {
			flag(p, AndyState.FLAG_DEAD_ROAD, false);
			return;
		}
		Vec3 from = handOrigin(p);
		com.projecthero.mod.beamclash.BeamClash.report(p, from, p.getLookAngle(), AndyConfig.DEAD_ROAD_RANGE, 2.0f, 0xE01020); // v0.17.3
		Vec3 dir = p.getLookAngle();
		Vec3 far = p.getEyePosition().add(dir.scale(AndyConfig.DEAD_ROAD_RANGE));
		BlockHitResult bhr = p.level().clip(new ClipContext(from, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? far : bhr.getLocation();
		if (age % 4 == 0 && !com.projecthero.mod.beamclash.BeamClash.inClash(p)) {
			// a thick spiral: everything within 1.3 blocks of the line
			Vec3 seg = end.subtract(from);
			double len = seg.length();
			for (LivingEntity e : level(p).getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(1.5),
					e -> e != p && !(e instanceof AndyCloneEntity) && HeroTargets.canHarm(p, e))) {
				Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
				double t = Math.max(0, Math.min(len, c.subtract(from).dot(seg) / len));
				if (from.add(seg.scale(t / len)).distanceTo(c) <= 1.3 + e.getBbWidth() * 0.5) {
					strike(p, e, from, AndyConfig.DEAD_ROAD_DAMAGE, 0.5, 0.05);
				}
			}
		}
		if (age % 6 == 0) {
			sound(p, SoundEvents.GENERIC_SPLASH, 0.8f, 0.5f);
		}
	}

	// ---------------------------------------------------------------- Repair Headbutt (passive)

	/** A heavy head hit (or the head breaking) snaps back at the nearest enemy within reach. */
	public static void repairHeadbutt(ServerPlayer p, boolean headBroke) {
		long now = p.level().getGameTime();
		Long last = LAST_HEADBUTT.get(p.getUUID());
		if ((last != null && now - last < AndyConfig.HEADBUTT_COOLDOWN && now >= last) || Andy.reforming(p)) {
			return;
		}
		LivingEntity best = null;
		double bestSq = AndyConfig.HEADBUTT_REACH * AndyConfig.HEADBUTT_REACH;
		for (LivingEntity e : around(p, p.getEyePosition(), AndyConfig.HEADBUTT_REACH)) {
			if (!HeroTargets.isHostile(p, e) && p.getLastHurtByMob() != e) {
				continue;
			}
			double d = e.distanceToSqr(p);
			if (d < bestSq) {
				bestSq = d;
				best = e;
			}
		}
		if (best == null) {
			return;
		}
		LAST_HEADBUTT.put(p.getUUID(), now);
		if (!headBroke) {
			anim(p, AndyState.ANIM_HEADBUTT);
		}
		strike(p, best, p.position(), AndyConfig.HEADBUTT_DAMAGE, 1.1, 0.25);
		p.level().playSound(null, best.getX(), best.getEyeY(), best.getZ(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.PLAYERS, 0.7f, 1.3f);
	}

	// ---------------------------------------------------------------- tick

	static void tick(ServerPlayer p) {
		AndyState s = Andy.state(p);
		long now = p.level().getGameTime();
		if (s.reformUntil > now) {
			return;
		}
		if (s.flag(AndyState.FLAG_PROPELLING)) {
			tickPropulsion(p, s, now);
		}
		if (s.flag(AndyState.FLAG_DEAD_LINE)) {
			tickDeadLine(p, Andy.state(p), now);
		}
		if (s.flag(AndyState.FLAG_DEAD_ROAD)) {
			tickDeadRoad(p, Andy.state(p), now);
		}
		Waves w = WAVES.get(p.getUUID());
		if (w != null) {
			tickWaves(p, w, now);
		}
		Integer v = VULCANS.get(p.getUUID());
		if (v != null) {
			tickVulcan(p, v, now);
		}
	}
}
