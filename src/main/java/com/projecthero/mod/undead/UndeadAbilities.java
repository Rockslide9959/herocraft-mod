package com.projecthero.mod.undead;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.undead.data.UndeadState;
import com.projecthero.mod.undead.entity.UndeadCloneEntity;
import com.projecthero.mod.undead.entity.UndeadPartEntity;
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
 * Undead's moves (v0.17.3); see {@link UndeadAbilityManager} for the keys. Timed moves are small per-player runs stepped from
 * {@link #tick}; every map here is dropped by {@link #clear}, {@link #forget} and {@link #clearSessionState}.
 */
public final class UndeadAbilities {
	public static final String PARTS_BULLET = "parts_bullet";
	public static final String CRIMSON_BULLET = "crimson_bullet";
	public static final String CRESCENT = "crimson_crescent_moon";
	public static final String GREAT_CRESCENT = "great_crescent_moon";
	public static final String PROPULSION = "blood_propulsion";
	public static final String VORTEX = "vortex_bullet";
	public static final String HEAD_BULLET = "parts_bullet_head";
	public static final String REGEN = "regen_speed";
	/** v0.17.3 tap V: the body snapped back together ({@link Undead#reform}). */
	public static final String REFORM = "reform";
	public static final String CARD = "pull_the_card";
	public static final String DEAD_LINE = "dead_line";
	public static final String QUARDEAD = "dead_quardead";
	public static final String VULCAN = "dead_vulcan";
	public static final String DIVISION = "division_bullet";
	public static final String DEAD_BLADE = "dead_blade";
	public static final String DEAD_ROAD = "dead_road";
	/** v0.17.3: hold R -- the arm drawn back and thrown off the body. */
	public static final String PUNCH = "stretch_punch";

	/** Great Deep-Crimson Crescent Moon: where the waves run from, which way, since when, and who they hit. */
	private record Waves(Vec3 from, Vec3 dir, long start, Set<Integer> hit) {
	}

	private static final Map<UUID, Waves> WAVES = new ConcurrentHashMap<>();
	/** Dead Vulcan: shots still to fire. */
	private static final Map<UUID, Integer> VULCANS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_HEADBUTT = new ConcurrentHashMap<>();

	private UndeadAbilities() {
	}

	static void clearSessionState() {
		WAVES.clear();
		VULCANS.clear();
		LAST_HEADBUTT.clear();
		UndeadBoneSword.clearSessionState();
	}

	static void forget(UUID id) {
		WAVES.remove(id);
		VULCANS.remove(id);
		LAST_HEADBUTT.remove(id);
		UndeadBoneSword.forget(id);
	}

	/** Ends every running move (and the Bone Sword bursts: the arm grows back). */
	public static void clear(ServerPlayer p) {
		UUID id = p.getUUID();
		UndeadBoneSword.clear(p);
		WAVES.remove(id);
		VULCANS.remove(id);
		UndeadState s = Undead.peek(p);
		if (s != null && (s.flags & ~UndeadState.FLAG_REGEN_OFF) != 0) {
			UndeadState n = s.copy();
			// (v0.17.3: the hands and feet are segments of their own now: they grow back by the regrowth once he has landed)
			n.flags = s.flags & UndeadState.FLAG_REGEN_OFF; // auto-regrowth off stays off
			Undead.save(p, n);
		}
	}

	// ---------------------------------------------------------------- shared

	private static boolean begin(ServerPlayer p, String id) {
		if (!Undead.canAct(p)) {
			return false;
		}
		int cd = Undead.cooldownRemaining(p, id);
		if (cd > 0) {
			Undead.say(p, "message.projecthero.undead.cooldown", ChatFormatting.GRAY, Component.translatable("projecthero.undead.ability." + id),
					String.format(java.util.Locale.ROOT, "%.1f", cd / 20.0f));
			return false;
		}
		return true;
	}

	/** The move needs the main hand. */
	private static boolean hand(ServerPlayer p) {
		if (Undead.mainArmMissing(p)) {
			Undead.say(p, "message.projecthero.undead.no_arm", ChatFormatting.GRAY);
			return false;
		}
		return true;
	}

	private static void cooldown(ServerPlayer p, String id, int ticks) {
		UndeadState n = Undead.state(p).copy();
		n.abilityReadyAt.put(id, p.level().getGameTime() + ticks);
		Undead.save(p, n);
	}

	private static void anim(ServerPlayer p, int id) {
		UndeadState n = Undead.state(p).copy();
		n.animId = id;
		n.animStart = p.level().getGameTime();
		Undead.save(p, n);
	}

	private static void flag(ServerPlayer p, int flag, boolean on) {
		UndeadState n = Undead.state(p).copy();
		n.setFlag(flag, on);
		if (on) {
			n.holdStart = p.level().getGameTime();
		}
		Undead.save(p, n);
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
		return handOrigin(p, Undead.mainArm(p));
	}

	/** That arm's fingertip, about where the arm points along the look. */
	public static Vec3 handOrigin(ServerPlayer p, int arm) {
		Vec3 r = UndeadParts.right(p);
		double side = arm == UndeadState.RIGHT_ARM ? 1 : -1;
		return p.getEyePosition().add(r.scale(0.32 * side)).add(0, -0.32, 0).add(p.getLookAngle().scale(0.55));
	}

	/**
	 * v0.17.3 (user: "when the player launches their arm the arm shoots slightly above where they were aiming"): what the
	 * crosshair is on -- the first block or creature along the look from the eye, within {@link UndeadConfig#AIM_RANGE} (the far
	 * end of the look when nothing is).
	 */
	public static Vec3 aimPoint(ServerPlayer p) {
		Vec3 eye = p.getEyePosition();
		Vec3 far = eye.add(p.getLookAngle().scale(UndeadConfig.AIM_RANGE));
		BlockHitResult bhr = p.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? far : bhr.getLocation();
		EntityHitResult ehr = ProjectileUtil.getEntityHitResult(p, eye, end, new AABB(eye, end).inflate(1.0),
				e -> e instanceof LivingEntity && e != p && !e.isSpectator() && e.isPickable() && !(e instanceof UndeadCloneEntity), eye.distanceToSqr(end));
		return ehr != null ? ehr.getLocation() : end;
	}

	/**
	 * The direction from {@code from} (a hand, a shoulder: off the eye line) to {@link #aimPoint}, so what leaves the hand lands
	 * on the crosshair rather than flying parallel to the look (and off by the hand's offset). Straight along the look when the
	 * aim point is too close or behind the hand.
	 */
	public static Vec3 aimDir(ServerPlayer p, Vec3 from) {
		Vec3 look = p.getLookAngle();
		Vec3 d = aimPoint(p).subtract(from);
		if (d.lengthSqr() < 1.0) {
			return look;
		}
		d = d.normalize();
		return d.dot(look) < 0.7 ? look : d;
	}

	static boolean isBoss(LivingEntity target) {
		return target.getMaxHealth() >= UndeadConfig.BOSS_HEALTH_THRESHOLD || TitanCombat.isBoss(target);
	}

	/** One hit from a move (Victor's multiplier and the boss cap applied). */
	public static boolean strike(ServerPlayer owner, LivingEntity target, Vec3 origin, float damage, double knockback, double lift) {
		if (!HeroTargets.canHarm(owner, target)) {
			return false;
		}
		float d = damage * (Undead.victor(owner) ? UndeadConfig.VICTOR_DAMAGE : 1f);
		boolean boss = isBoss(target);
		if (boss) {
			d = Math.min(d, target.getMaxHealth() * UndeadConfig.BOSS_MAX_FRACTION_PER_HIT);
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
		level(owner).sendParticles(Undead.BLOOD, c.x, c.y, c.z, 6, 0.25, 0.3, 0.25, 0.0);
		return true;
	}

	private static java.util.List<LivingEntity> around(ServerPlayer p, Vec3 c, double r) {
		return level(p).getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r),
				e -> e != p && !(e instanceof UndeadCloneEntity) && HeroTargets.canHarm(p, e)
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

	// ---------------------------------------------------------------- R: fingers and the stretch punch

	/**
	 * The arm a one-arm move uses (the main one, else the other: {@link Undead#shootingArm}); remembered in
	 * {@link UndeadState#FLAG_OFF_ARM} so every viewer poses the right one. -1 (and a dull click) with neither.
	 */
	private static int pickArm(ServerPlayer p) {
		int arm = Undead.shootingArm(p);
		if (arm < 0) {
			Undead.say(p, "message.projecthero.undead.no_arm", ChatFormatting.GRAY);
			sound(p, SoundEvents.DISPENSER_FAIL, 0.6f, 1.4f);
			return -1;
		}
		UndeadState n = Undead.state(p).copy();
		n.setFlag(UndeadState.FLAG_OFF_ARM, arm != Undead.mainArm(p));
		n.setFlag(UndeadState.FLAG_NEXT_OFF, arm == Undead.mainArm(p)); // the next one comes from the other hand
		Undead.save(p, n);
		return arm;
	}

	/**
	 * v0.17.3 (user): every finger shot takes a bite out of the arm, from the fingertips up ({@code amount} of it, see
	 * {@link UndeadConfig#PARTS_EAT}); it adds up shot after shot, and a used-up arm is gone ({@link UndeadState#KIND_SPENT}). It
	 * only starts to grow back {@link UndeadConfig#EATEN_REGROW_WAIT} after the last shot.
	 */
	public static void eat(ServerPlayer p, int arm, float amount) {
		long now = p.level().getGameTime();
		UndeadState n = Undead.state(p).copy();
		float c = Math.min(1f, n.eaten(arm, now) + amount);
		n.setEx(UndeadState.X_LAST_SHOT, now);
		boolean spent = c >= 0.999f;
		n.setEx(arm == UndeadState.RIGHT_ARM ? UndeadState.X_EATEN_R : UndeadState.X_EATEN_L, spent ? 0L : Math.round(c * 1000f));
		// the other arm's bite waits as well: "after not shooting for 2 seconds"
		int other = Undead.otherArm(arm);
		float co = n.eaten(other, now);
		n.setEx(other == UndeadState.RIGHT_ARM ? UndeadState.X_EATEN_R : UndeadState.X_EATEN_L, Math.round(co * 1000f));
		Undead.save(p, n);
		if (spent) {
			Undead.breakPart(p, arm, UndeadState.KIND_SPENT);
		}
	}

	public static void partsBullet(ServerPlayer p) {
		if (!begin(p, PARTS_BULLET)) {
			return;
		}
		int arm = pickArm(p);
		if (arm < 0) {
			return;
		}
		if (UndeadConfig.PARTS_BULLET_COOLDOWN > 0) {
			cooldown(p, PARTS_BULLET, UndeadConfig.PARTS_BULLET_COOLDOWN);
		}
		anim(p, UndeadState.ANIM_FINGER);
		Vec3 h = handOrigin(p, arm);
		UndeadPartEntity.shoot(p, UndeadPartEntity.STYLE_FINGER, -1, h, aimDir(p, h).scale(UndeadConfig.FINGER_SPEED), UndeadConfig.PARTS_BULLET_DAMAGE);
		eat(p, arm, UndeadConfig.PARTS_EAT);
		UndeadSfx.partsBullet(level(p), h); // v0.17.3 Undead sounds
		level(p).sendParticles(Undead.BLOOD, h.x, h.y, h.z, 3, 0.03, 0.03, 0.03, 0.0);
	}

	public static void crimsonBullet(ServerPlayer p) {
		if (!begin(p, CRIMSON_BULLET)) {
			return;
		}
		int arm = pickArm(p);
		if (arm < 0) {
			return;
		}
		cooldown(p, CRIMSON_BULLET, UndeadConfig.CRIMSON_COOLDOWN);
		anim(p, UndeadState.ANIM_CRIMSON);
		Vec3 h = handOrigin(p, arm);
		UndeadPartEntity.shoot(p, UndeadPartEntity.STYLE_CRIMSON, -1, h, aimDir(p, h).scale(UndeadConfig.FINGER_SPEED * 1.2), UndeadConfig.CRIMSON_DAMAGE);
		eat(p, arm, UndeadConfig.CRIMSON_EAT);
		UndeadSfx.crimsonBullet(level(p), h); // v0.17.3 Undead sounds
		level(p).sendParticles(Undead.BLOOD, h.x, h.y, h.z, 6, 0.1, 0.1, 0.1, 0.0);
	}

	/** Hold R past {@link UndeadConfig#PUNCH_HOLD_TICKS}: the arm draws back. False if it can't (cooldown, no arm). */
	public static boolean startPunch(ServerPlayer p) {
		if (Undead.cooldownRemaining(p, PUNCH) > 0 || !Undead.canAct(p) || Undead.shootingArm(p) < 0) {
			return false;
		}
		int arm = pickArm(p);
		UndeadState n = Undead.state(p).copy();
		n.setFlag(UndeadState.FLAG_PUNCH, true);
		n.setEx(UndeadState.X_PUNCH_START, p.level().getGameTime());
		Undead.save(p, n);
		UndeadSfx.punchStretch(p.level(), p.getEyePosition()); // the stretch loop while it is held is client-side (UndeadSounds.punchCharge)
		return arm >= 0;
	}

	/** The wind-up dropped (let go too early, the arm lost meanwhile). */
	public static void cancelPunch(ServerPlayer p) {
		UndeadState s = Undead.state(p);
		if (s.flag(UndeadState.FLAG_PUNCH)) {
			UndeadState n = s.copy();
			n.setFlag(UndeadState.FLAG_PUNCH, false);
			Undead.save(p, n);
		}
	}

	/**
	 * Let go of a charged R: the whole arm flies off his body fist first ({@link UndeadPartEntity#STYLE_PUNCH}), 20 damage. That
	 * arm is gone (launched) and grows back as usual.
	 */
	public static void throwPunch(ServerPlayer p) {
		UndeadState s = Undead.state(p);
		if (!s.flag(UndeadState.FLAG_PUNCH)) {
			return;
		}
		cancelPunch(p);
		int arm = s.flag(UndeadState.FLAG_OFF_ARM) ? Undead.otherArm(Undead.mainArm(p)) : Undead.mainArm(p);
		if (!s.armReady(arm, p.level().getGameTime())) {
			arm = Undead.shootingArm(p);
			if (arm < 0) {
				return;
			}
		}
		cooldown(p, PUNCH, UndeadConfig.PUNCH_COOLDOWN);
		anim(p, UndeadState.ANIM_PUNCH);
		Undead.breakPart(p, arm, UndeadState.KIND_LAUNCHED);
		Vec3 r = UndeadParts.right(p);
		double side = arm == UndeadState.RIGHT_ARM ? 1 : -1;
		Vec3 from = p.getEyePosition().add(r.scale(0.36 * side)).add(0, -0.3, 0).add(p.getLookAngle().scale(0.5));
		// v0.17.3: at what the crosshair is on (from the shoulder, not parallel to the look), fist first along the flight
		UndeadPartEntity.shoot(p, UndeadPartEntity.STYLE_PUNCH, arm, from, aimDir(p, from).scale(UndeadConfig.PUNCH_SPEED), UndeadConfig.PUNCH_DAMAGE);
		UndeadSfx.punchFire(level(p), from);
		UndeadSfx.launch(level(p), from);
		level(p).sendParticles(Undead.BLOOD_BIG, from.x, from.y, from.z, 6, 0.1, 0.1, 0.1, 0.0);
	}

	// ---------------------------------------------------------------- G: Kurikara

	public static void crescent(ServerPlayer p) {
		if (!begin(p, CRESCENT) || !hand(p)) {
			return;
		}
		cooldown(p, CRESCENT, UndeadConfig.CRESCENT_COOLDOWN);
		anim(p, UndeadState.ANIM_CRESCENT);
		Vec3 c = p.position().add(0, 1.0, 0);
		for (LivingEntity e : arc(p, UndeadConfig.CRESCENT_REACH, 0.2)) {
			strike(p, e, c, UndeadConfig.CRESCENT_DAMAGE, 0.6, 0.1);
		}
		Vec3 f = flatLook(p);
		Vec3 r = UndeadParts.right(p);
		for (int i = -6; i <= 6; i++) {
			double a = i / 6.0 * 1.2;
			Vec3 at = c.add(f.scale(Math.cos(a) * 2.4)).add(r.scale(Math.sin(a) * 2.4)).add(0, -i * 0.04, 0);
			level(p).sendParticles(Undead.BLOOD, at.x, at.y, at.z, 2, 0.04, 0.04, 0.04, 0.0);
		}
		level(p).sendParticles(ParticleTypes.SWEEP_ATTACK, c.x + f.x * 1.6, c.y, c.z + f.z * 1.6, 1, 0, 0, 0, 0);
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.8f);
		sound(p, SoundEvents.TRIDENT_RETURN, 0.7f, 1.4f);
	}

	public static void greatCrescent(ServerPlayer p) {
		if (!begin(p, GREAT_CRESCENT) || !hand(p)) {
			return;
		}
		cooldown(p, GREAT_CRESCENT, UndeadConfig.GREAT_CRESCENT_COOLDOWN);
		anim(p, UndeadState.ANIM_GREAT_CRESCENT);
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
		if (d > UndeadConfig.GREAT_CRESCENT_RANGE) {
			WAVES.remove(p.getUUID());
			return;
		}
		Vec3 front = w.from().add(w.dir().scale(d));
		Vec3 side = new Vec3(-w.dir().z, 0, w.dir().x);
		ServerLevel level = level(p);
		double width = 1.4 + d * 0.06;
		double height = 2.6 + d * 0.08;
		// the waves themselves are drawn by every client (UndeadEffectsRenderer); a spray of blood along the crest here
		for (int i = -4; i <= 4 && age % 2 == 0; i++) {
			double s = i / 4.0;
			double back = s * s * 0.9;
			for (int k = 2; k < 3; k++) {
				double y = k / 2.0 * height * (1.0 - 0.45 * s * s);
				Vec3 at = front.add(side.scale(s * width)).add(w.dir().scale(-back)).add(0, y, 0);
				level.sendParticles(Undead.BLOOD, at.x, at.y, at.z, 1, 0.1, 0.05, 0.1, 0.0);
			}
		}
		if (age % 3 == 0) {
			level.playSound(null, front.x, front.y, front.z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.8f, 0.6f);
		}
		for (LivingEntity e : around(p, front.add(0, 1, 0), width + 0.8)) {
			if (w.hit().add(e.getId())) {
				strike(p, e, front.subtract(w.dir()), UndeadConfig.GREAT_CRESCENT_DAMAGE, 1.2, 0.45);
			}
		}
	}

	// ---------------------------------------------------------------- Z: launched parts

	public static void vortexBullet(ServerPlayer p) {
		if (!begin(p, VORTEX)) {
			return;
		}
		int arm = pickArm(p); // v0.17.3: left and right in turn, like the other thrown parts
		if (arm < 0) {
			return;
		}
		cooldown(p, VORTEX, UndeadConfig.VORTEX_COOLDOWN);
		anim(p, UndeadState.ANIM_VORTEX);
		Undead.breakPart(p, arm, UndeadState.KIND_LAUNCHED);
		Vec3 r = UndeadParts.right(p);
		double side = arm == UndeadState.RIGHT_ARM ? 1 : -1;
		Vec3 from = p.getEyePosition().add(r.scale(0.36 * side)).add(0, -0.3, 0).add(p.getLookAngle().scale(0.4));
		UndeadPartEntity.shoot(p, UndeadPartEntity.STYLE_DRILL, arm, from, aimDir(p, from).scale(UndeadConfig.VORTEX_SPEED), UndeadConfig.VORTEX_DAMAGE);
		sound(p, SoundEvents.TRIDENT_RIPTIDE_3.value(), 1.0f, 1.2f);
		sound(p, SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, 0.8f, 0.5f);
		level(p).sendParticles(Undead.BLOOD, from.x, from.y, from.z, 5, 0.1, 0.1, 0.1, 0.0);
	}

	public static void headBullet(ServerPlayer p) {
		if (!begin(p, HEAD_BULLET)) {
			return;
		}
		if (Undead.missing(p, UndeadState.HEAD)) {
			Undead.say(p, "message.projecthero.undead.no_head", ChatFormatting.GRAY);
			return;
		}
		if (Undead.missing(p, UndeadState.BODY)) {
			Undead.say(p, "message.projecthero.undead.no_body", ChatFormatting.GRAY);
			return;
		}
		cooldown(p, HEAD_BULLET, UndeadConfig.HEAD_BULLET_COOLDOWN);
		anim(p, UndeadState.ANIM_HEAD);
		Vec3 from = p.getEyePosition().add(p.getLookAngle().scale(0.3));
		Undead.breakPart(p, UndeadState.HEAD, UndeadState.KIND_LAUNCHED);
		UndeadPartEntity.shoot(p, UndeadPartEntity.STYLE_HEAD, UndeadState.HEAD, from, p.getLookAngle().scale(UndeadConfig.HEAD_SPEED), UndeadConfig.HEAD_BULLET_DAMAGE);
		sound(p, SoundEvents.CROSSBOW_SHOOT, 1.0f, 0.5f);
		sound(p, SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, 0.8f, 0.6f);
		level(p).sendParticles(Undead.BLOOD, from.x, from.y - 0.25, from.z, 5, 0.12, 0.05, 0.12, 0.0);
	}

	// ---------------------------------------------------------------- V

	public static void pullCard(ServerPlayer p) {
		if (!UndeadConfig.VICTOR_ENABLED || !Undead.canAct(p)) {
			return;
		}
		UndeadState s = Undead.state(p);
		long now = p.level().getGameTime();
		if (s.victorUntil > now) {
			// the card goes back in early
			UndeadState n = s.copy();
			n.victorUntil = now;
			Undead.save(p, n);
			return;
		}
		if (!begin(p, CARD)) {
			return;
		}
		UndeadState n = Undead.state(p).copy();
		n.victorUntil = now + UndeadConfig.VICTOR_TICKS;
		n.abilityReadyAt.put(CARD, now + UndeadConfig.VICTOR_COOLDOWN);
		n.animId = UndeadState.ANIM_CARD;
		n.animStart = now;
		n.flags &= ~(UndeadState.FLAG_DEAD_LINE);
		Undead.save(p, n);
		Undead.reconcile(p);
		VULCANS.remove(p.getUUID());
		sound(p, SoundEvents.WITHER_AMBIENT, 0.6f, 1.4f);
		sound(p, SoundEvents.ELYTRA_FLYING, 0.4f, 2.0f);
		Vec3 c = p.getEyePosition();
		level(p).sendParticles(Undead.BLOOD, c.x, c.y + 0.3, c.z, 10, 0.3, 0.2, 0.3, 0.0);
		Undead.say(p, "message.projecthero.undead.victor", ChatFormatting.DARK_RED);
	}

	// ---------------------------------------------------------------- C: Dead Quardead (Deadline: UndeadBloodMoves)

	/** The beam's end this tick: the first block or creature along the look from the fingertip. */
	public static Vec3[] beam(ServerPlayer p, double range, LivingEntity[] hitOut) {
		Vec3 from = handOrigin(p);
		Vec3 dir = p.getLookAngle();
		Vec3 far = p.getEyePosition().add(dir.scale(range));
		BlockHitResult bhr = p.level().clip(new ClipContext(from, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? far : bhr.getLocation();
		EntityHitResult ehr = ProjectileUtil.getEntityHitResult(p, from, end, new AABB(from, end).inflate(1.0),
				e -> e instanceof LivingEntity le && !(e instanceof UndeadCloneEntity) && HeroTargets.canHarm(p, le) && e.isPickable(), from.distanceToSqr(end));
		if (ehr != null && ehr.getEntity() instanceof LivingEntity le) {
			end = ehr.getLocation();
			hitOut[0] = le;
		}
		return new Vec3[] { from, end };
	}

	// (v0.17.3: Deadline, the reworked Dead Line, is UndeadBloodMoves')

	public static void quardead(ServerPlayer p) {
		if (!begin(p, QUARDEAD)) {
			return;
		}
		cooldown(p, QUARDEAD, UndeadConfig.QUARDEAD_COOLDOWN);
		anim(p, UndeadState.ANIM_QUARDEAD);
		Vec3 f = flatLook(p);
		Vec3 r = UndeadParts.right(p);
		Vec3[] at = { p.position().add(r.scale(1.3)), p.position().add(r.scale(-1.3)), p.position().add(f.scale(-1.4)) };
		float dmg = UndeadConfig.CLONE_DAMAGE * (Undead.victor(p) ? UndeadConfig.VICTOR_DAMAGE : 1f);
		int[] tints = { UndeadCloneEntity.TINT_SKIN, UndeadCloneEntity.TINT_BLOOD, UndeadCloneEntity.TINT_BONE };
		for (int i = 0; i < 3; i++) {
			UndeadCloneEntity.spawn(p, at[i].x, p.getY(), at[i].z, tints[i], UndeadConfig.QUARDEAD_TICKS, dmg);
			level(p).sendParticles(Undead.BLOOD, at[i].x, p.getY() + 1, at[i].z, 5, 0.3, 0.6, 0.3, 0.0);
		}
		sound(p, SoundEvents.SLIME_SQUISH, 1.2f, 0.6f);
		sound(p, SoundEvents.SKELETON_AMBIENT, 0.8f, 0.8f);
	}

	// ---------------------------------------------------------------- Victor's kit

	public static void vulcan(ServerPlayer p) {
		if (!begin(p, VULCAN) || !hand(p)) {
			return;
		}
		cooldown(p, VULCAN, UndeadConfig.VULCAN_COOLDOWN);
		anim(p, UndeadState.ANIM_VULCAN);
		VULCANS.put(p.getUUID(), UndeadConfig.VULCAN_SHOTS);
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
		if (left <= 0 || Undead.mainArmMissing(p)) {
			VULCANS.remove(p.getUUID());
			return;
		}
		VULCANS.put(p.getUUID(), left - 1);
		Vec3 spread = new Vec3((p.getRandom().nextDouble() - 0.5) * 0.06, (p.getRandom().nextDouble() - 0.5) * 0.06, (p.getRandom().nextDouble() - 0.5) * 0.06);
		UndeadPartEntity.shoot(p, UndeadPartEntity.STYLE_VULCAN, -1, handOrigin(p), p.getLookAngle().add(spread).normalize().scale(UndeadConfig.FINGER_SPEED * 1.1),
				UndeadConfig.VULCAN_DAMAGE);
		sound(p, SoundEvents.CROSSBOW_SHOOT, 0.6f, 1.8f + p.getRandom().nextFloat() * 0.2f);
	}

	public static void division(ServerPlayer p) {
		if (!begin(p, DIVISION) || !hand(p)) {
			return;
		}
		cooldown(p, DIVISION, UndeadConfig.DIVISION_COOLDOWN);
		anim(p, UndeadState.ANIM_DIVISION);
		Vec3 look = p.getLookAngle();
		Vec3 r = UndeadParts.right(p);
		for (int i = -1; i <= 1; i++) {
			Vec3 v = look.add(r.scale(i * 0.12)).normalize().scale(UndeadConfig.FINGER_SPEED * 0.8);
			UndeadPartEntity.shoot(p, UndeadPartEntity.STYLE_DIVISION, -1, handOrigin(p), v, UndeadConfig.PARTS_BULLET_DAMAGE);
		}
		sound(p, SoundEvents.CROSSBOW_SHOOT, 1.0f, 1.0f);
	}

	/** A Division Bullet finger grows into a short-lived self where it lands. */
	public static void divisionClone(ServerPlayer p, Vec3 at) {
		UndeadCloneEntity.spawn(p, at.x, at.y, at.z, UndeadCloneEntity.TINT_BLOOD, UndeadConfig.DIVISION_CLONE_TICKS,
				UndeadConfig.CLONE_DAMAGE * UndeadConfig.VICTOR_DAMAGE);
		level(p).sendParticles(Undead.BLOOD, at.x, at.y + 1, at.z, 5, 0.3, 0.6, 0.3, 0.0);
		p.level().playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0f, 0.7f);
	}

	public static void deadBlade(ServerPlayer p) {
		if (!begin(p, DEAD_BLADE) || !hand(p)) {
			return;
		}
		cooldown(p, DEAD_BLADE, UndeadConfig.DEAD_BLADE_COOLDOWN);
		anim(p, UndeadState.ANIM_DEAD_BLADE);
		Vec3 c = p.position().add(0, 1.0, 0);
		for (LivingEntity e : arc(p, UndeadConfig.DEAD_BLADE_REACH, -0.05)) {
			strike(p, e, c, UndeadConfig.DEAD_BLADE_DAMAGE, 1.3, 0.3);
		}
		Vec3 f = flatLook(p);
		Vec3 r = UndeadParts.right(p);
		for (int i = -6; i <= 6; i++) {
			double a = i / 6.0 * 1.6;
			for (double rad = 4.0; rad <= 5.0; rad += 1.0) {
				Vec3 at = c.add(f.scale(Math.cos(a) * rad)).add(r.scale(Math.sin(a) * rad));
				level(p).sendParticles(Undead.BLOOD, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.0);
			}
		}
		sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.3f, 0.5f);
		sound(p, SoundEvents.ANVIL_LAND, 0.4f, 0.6f);
	}

	public static void deadRoad(ServerPlayer p) {
		if (!begin(p, DEAD_ROAD) || !hand(p)) {
			return;
		}
		cooldown(p, DEAD_ROAD, UndeadConfig.DEAD_ROAD_COOLDOWN);
		flag(p, UndeadState.FLAG_DEAD_ROAD, true);
		sound(p, SoundEvents.WARDEN_SONIC_CHARGE, 0.8f, 1.4f);
	}

	private static void tickDeadRoad(ServerPlayer p, UndeadState s, long now) {
		long age = now - s.holdStart;
		if (age > UndeadConfig.DEAD_ROAD_TICKS || Undead.mainArmMissing(p) || !Undead.victor(p)) {
			flag(p, UndeadState.FLAG_DEAD_ROAD, false);
			return;
		}
		Vec3 from = handOrigin(p);
		com.projecthero.mod.beamclash.BeamClash.report(p, from, p.getLookAngle(), UndeadConfig.DEAD_ROAD_RANGE, 2.0f, 0xE01020); // v0.17.3
		Vec3 dir = p.getLookAngle();
		Vec3 far = p.getEyePosition().add(dir.scale(UndeadConfig.DEAD_ROAD_RANGE));
		BlockHitResult bhr = p.level().clip(new ClipContext(from, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? far : bhr.getLocation();
		if (age % 4 == 0 && !com.projecthero.mod.beamclash.BeamClash.inClash(p)) {
			// a thick spiral: everything within 1.3 blocks of the line
			Vec3 seg = end.subtract(from);
			double len = seg.length();
			for (LivingEntity e : level(p).getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(1.5),
					e -> e != p && !(e instanceof UndeadCloneEntity) && HeroTargets.canHarm(p, e))) {
				Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
				double t = Math.max(0, Math.min(len, c.subtract(from).dot(seg) / len));
				if (from.add(seg.scale(t / len)).distanceTo(c) <= 1.3 + e.getBbWidth() * 0.5) {
					strike(p, e, from, UndeadConfig.DEAD_ROAD_DAMAGE, 0.5, 0.05);
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
		if ((last != null && now - last < UndeadConfig.HEADBUTT_COOLDOWN && now >= last) || Undead.reforming(p)) {
			return;
		}
		LivingEntity best = null;
		double bestSq = UndeadConfig.HEADBUTT_REACH * UndeadConfig.HEADBUTT_REACH;
		for (LivingEntity e : around(p, p.getEyePosition(), UndeadConfig.HEADBUTT_REACH)) {
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
			anim(p, UndeadState.ANIM_HEADBUTT);
		}
		strike(p, best, p.position(), UndeadConfig.HEADBUTT_DAMAGE, 1.1, 0.25);
		p.level().playSound(null, best.getX(), best.getEyeY(), best.getZ(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.PLAYERS, 0.7f, 1.3f);
	}

	// ---------------------------------------------------------------- tick

	static void tick(ServerPlayer p) {
		UndeadBoneSword.tick(p); // v0.17.3 Shift+V
		UndeadState s = Undead.state(p);
		long now = p.level().getGameTime();
		if (s.reformUntil > now) {
			return;
		}
		if (s.flag(UndeadState.FLAG_DEAD_ROAD)) {
			tickDeadRoad(p, Undead.state(p), now);
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
