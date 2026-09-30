package com.projecthero.mod.supersoldier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.supersoldier.data.SuperSoldierState;
import com.projecthero.mod.supersoldier.entity.SoldierShieldEntity;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * The ten Super Soldier moves (v0.14.8). Every one is server-validated by {@link #begin}: power, alive, not locked by a
 * running move, cooldown ready -- and only then is the cooldown started. Multi-tick moves (the Combo Strike's punches,
 * the Shield Bash charge, the Leaping Slam's landing) run from small per-player tables ticked by {@link #tick}, and are
 * dropped if the player dies, logs out or loses the power ({@link #clear}; every table is emptied by
 * {@code ServerStateReset}).
 *
 * <pre>
 *   R        Combo Strike         Shift+R  Uppercut Launcher
 *   G        Shield Throw         Shift+G  Shield Bash Charge
 *   Z        Leaping Slam         Shift+Z  Super Soldier Onslaught (ULTIMATE)
 *   X        Tactical Roll        Shift+X  High Leap
 *   V        Battle Cry           Shift+V  Tactical Focus
 * </pre>
 */
public final class SuperSoldierAbilities {
	public static final String COMBO = "combo_strike";
	public static final String UPPERCUT = "uppercut_launcher";
	public static final String SHIELD_THROW = "shield_throw";
	public static final String SHIELD_BASH = "shield_bash";
	public static final String SLAM = "leaping_slam";
	public static final String ONSLAUGHT = "onslaught";
	public static final String ROLL = "tactical_roll";
	public static final String HIGH_LEAP = "high_leap";
	public static final String BATTLE_CRY = "battle_cry";
	public static final String FOCUS = "tactical_focus";

	/** The Guidebook / power-info rows: key label, ability id. */
	public static final String[][] GUIDE_ROWS = {
			{ "R", COMBO }, { "Shift+R", UPPERCUT },
			{ "G", SHIELD_THROW }, { "Shift+G", SHIELD_BASH },
			{ "Z", SLAM }, { "Shift+Z", ONSLAUGHT },
			{ "X", ROLL }, { "Shift+X", HIGH_LEAP },
			{ "V", BATTLE_CRY }, { "Shift+V", FOCUS } };

	private record Task(long due, Runnable run) {
	}

	private static final class Bash {
		final Vec3 dir;
		final long start;
		final Set<Integer> hit = new HashSet<>();

		Bash(Vec3 dir, long start) {
			this.dir = dir;
			this.start = start;
		}
	}

	private static final Map<UUID, List<Task>> TASKS = new HashMap<>();
	private static final Map<UUID, Bash> BASHES = new HashMap<>();
	/** Leaping Slam in the air: game time of take-off. */
	private static final Map<UUID, Long> SLAMS = new HashMap<>();
	/** Tactical Focus: the entity ids each player has marked. */
	private static final Map<UUID, Set<Integer>> MARKS = new HashMap<>();
	/** Set while a move is applying its damage, so the melee hooks do not treat it as a punch. */
	private static final ThreadLocal<Boolean> ABILITY_HIT = ThreadLocal.withInitial(() -> false);

	private SuperSoldierAbilities() {
	}

	public static void clearSessionState() {
		TASKS.clear();
		BASHES.clear();
		SLAMS.clear();
		MARKS.clear();
	}

	public static void clear(UUID id) {
		TASKS.remove(id);
		BASHES.remove(id);
		SLAMS.remove(id);
		MARKS.remove(id);
	}

	static void clearMarks(UUID id) {
		MARKS.remove(id);
	}

	public static boolean isAbilityHit() {
		return ABILITY_HIT.get();
	}

	/** True while Tactical Focus has {@code target} marked for {@code player}. */
	public static boolean isMarked(ServerPlayer player, LivingEntity target) {
		Set<Integer> m = MARKS.get(player.getUUID());
		return m != null && m.contains(target.getId()) && SuperSoldier.focusActive(player);
	}

	/** True while the Leaping Slam is in the air (tests + the fall-damage rule). */
	public static boolean slamming(ServerPlayer player) {
		return SLAMS.containsKey(player.getUUID());
	}

	public static boolean bashing(ServerPlayer player) {
		return BASHES.containsKey(player.getUUID());
	}

	// ---------------------------------------------------------------- plumbing

	private static void schedule(ServerPlayer p, int delayTicks, Runnable run) {
		TASKS.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(new Task(p.level().getGameTime() + Math.max(0, delayTicks), run));
	}

	private static boolean alive(ServerPlayer p) {
		return p.isAlive() && !p.isRemoved() && SuperSoldier.hasPower(p);
	}

	/** The common gate: true (cooldown started, player locked for {@code lockTicks}) only if every check passed. */
	static boolean begin(ServerPlayer p, String id, int cooldown, int lockTicks) {
		SuperSoldierState s = SuperSoldier.state(p);
		if (!s.hasPower || !p.isAlive() || p.isSpectator()) {
			return false;
		}
		long now = p.level().getGameTime();
		if (now < s.busyUntil) {
			return false; // a running move: silently ignored so a held key cannot stack moves
		}
		Long ready = s.abilityReadyAt.get(id);
		if (ready != null && now < ready) {
			SuperSoldier.say(p, "message.projecthero.super_soldier.cooldown", ChatFormatting.RED,
					Component.translatable("projecthero.super_soldier.ability." + id),
					String.format(java.util.Locale.ROOT, "%.1f", (ready - now) / 20.0));
			return false;
		}
		SuperSoldierState n = s.copy();
		n.abilityReadyAt.put(id, now + cooldown);
		n.busyUntil = now + Math.max(lockTicks, SuperSoldierConfig.GLOBAL_LOCK_TICKS);
		SuperSoldier.save(p, n);
		return true;
	}

	private static void edit(ServerPlayer p, java.util.function.Consumer<SuperSoldierState> change) {
		SuperSoldierState n = SuperSoldier.state(p).copy();
		change.accept(n);
		SuperSoldier.save(p, n);
	}

	static Vec3 flatLook(Player p) {
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		return flat.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0, p.getYRot()) : flat.normalize();
	}

	// ---------------------------------------------------------------- targets / damage

	public static boolean isBoss(LivingEntity e) {
		return e.getMaxHealth() >= SuperSoldierConfig.BOSS_HEALTH_THRESHOLD || TitanCombat.isBoss(e);
	}

	/** Whether a move of {@code p}'s may touch {@code e}: never himself, squad-mates, his own pets or armour stands. */
	public static boolean canTarget(ServerPlayer p, LivingEntity e) {
		if (e == p || !e.isAlive() || e instanceof ArmorStand || e.isSpectator()) {
			return false;
		}
		if (e instanceof OwnableEntity own && p.getUUID().equals(own.getOwnerUUID())) {
			return false;
		}
		if (e instanceof Player other) {
			if (other.isCreative() || com.projecthero.mod.squad.Squads.areAllies(p, other)) {
				return false;
			}
			return p.getServer() != null && p.getServer().isPvpAllowed();
		}
		return true;
	}

	static List<LivingEntity> targets(ServerPlayer p, AABB box) {
		return p.level().getEntitiesOfClass(LivingEntity.class, box, e -> canTarget(p, e));
	}

	/** The enemy {@code p} is facing within {@code range} (the crosshair first, then the nearest one in a narrow cone). */
	static LivingEntity facing(ServerPlayer p, double range) {
		LivingEntity ray = AbilityHelpers.raycastEntity(p, range);
		if (ray != null && canTarget(p, ray)) {
			return ray;
		}
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : targets(p, p.getBoundingBox().inflate(range))) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double d = Math.sqrt(AbilityHelpers.distanceSqToBox(e, eye));
			if (d > range || to.lengthSqr() < 1.0e-6 || to.normalize().dot(look) < 0.55) {
				continue;
			}
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	/** Move damage multiplier: Onslaught makes every move 25% stronger. */
	static float moveMultiplier(ServerPlayer p) {
		return SuperSoldier.onslaughtActive(p) ? 1.25f : 1.0f;
	}

	/**
	 * One move hitting one target: damage (Onslaught-scaled, Focus-crit on a marked target, boss-capped) and knockback
	 * away from {@code origin} plus {@code lift} (never on a boss). True if it landed.
	 */
	public static boolean strike(ServerPlayer p, LivingEntity target, float baseDamage, Vec3 origin, double knockback, double lift) {
		if (!canTarget(p, target)) {
			return false;
		}
		float dmg = baseDamage * moveMultiplier(p);
		boolean crit = isMarked(p, target);
		if (crit) {
			dmg *= SuperSoldierConfig.FOCUS_CRIT_MULTIPLIER;
		}
		boolean boss = isBoss(target);
		if (boss) {
			dmg = Math.min(dmg, target.getMaxHealth() * SuperSoldierConfig.BOSS_MAX_FRACTION_PER_HIT);
		}
		boolean landed;
		ABILITY_HIT.set(true);
		try {
			landed = AbilityHelpers.hurtBurst(p, target, Math.max(1.0f, dmg)) || AbilityHelpers.hurtLands(p, target, Math.max(1.0f, dmg));
		} finally {
			ABILITY_HIT.set(false);
		}
		if (!landed) {
			return false;
		}
		if (!boss) {
			if (knockback > 0.0) {
				AbilityHelpers.knockbackFrom(target, origin, knockback);
			}
			if (lift > 0.0) {
				AbilityHelpers.push(target, new Vec3(0.0, lift, 0.0));
			}
		}
		SuperSoldier.markCombat(p);
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = target.position().add(0, target.getBbHeight() * 0.6, 0);
		level.sendParticles(crit ? ParticleTypes.ENCHANTED_HIT : ParticleTypes.CRIT, c.x, c.y, c.z, crit ? 12 : 6, 0.3, 0.3, 0.3, 0.3);
		return true;
	}

	/** Every valid target within {@code radius} of {@code center}; damage falls to 60% at the edge. Returns how many were hit. */
	static int radial(ServerPlayer p, Vec3 center, double radius, float damage, double knockback, double lift) {
		AABB box = new AABB(center, center).inflate(radius, Math.min(radius, 4.0), radius);
		int n = 0;
		for (LivingEntity e : targets(p, box)) {
			double d = Math.sqrt(AbilityHelpers.distanceSqToBox(e, center));
			if (d > radius) {
				continue;
			}
			float scale = (float) (1.0 - 0.4 * (d / radius));
			if (strike(p, e, damage * scale, center, knockback * scale, lift * scale)) {
				n++;
			}
		}
		return n;
	}

	private static DustParticleOptions blue(float size) {
		return new DustParticleOptions(new Vector3f(0.25f, 0.45f, 1.0f), size);
	}

	private static DustParticleOptions red(float size) {
		return new DustParticleOptions(new Vector3f(0.85f, 0.12f, 0.12f), size);
	}

	// ---------------------------------------------------------------- R: Combo Strike

	public static boolean comboStrike(ServerPlayer p) {
		LivingEntity target = facing(p, SuperSoldierConfig.COMBO_RANGE);
		if (target == null) {
			SuperSoldier.say(p, "message.projecthero.super_soldier.no_target", ChatFormatting.GRAY);
			return false;
		}
		if (!begin(p, COMBO, SuperSoldierConfig.COMBO_COOLDOWN, SuperSoldierConfig.COMBO_INTERVAL * 2 + 2)) {
			return false;
		}
		for (int i = 0; i < 3; i++) {
			final int hit = i;
			Runnable punch = () -> {
				if (!alive(p)) {
					return;
				}
				LivingEntity t = target.isAlive() && t0Reach(p, target) ? target : facing(p, SuperSoldierConfig.COMBO_RANGE);
				p.swing(hit == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, true);
				boolean finisher = hit == 2;
				AbilityHelpers.sound(p, finisher ? SoundEvents.PLAYER_ATTACK_KNOCKBACK : SoundEvents.PLAYER_ATTACK_STRONG, 1.0f,
						finisher ? 0.8f : 1.1f + hit * 0.1f);
				if (t == null) {
					return;
				}
				strike(p, t, finisher ? SuperSoldierConfig.COMBO_FINISHER_DAMAGE : SuperSoldierConfig.COMBO_HIT_DAMAGE,
						p.position(), finisher ? SuperSoldierConfig.COMBO_FINISHER_KNOCKBACK : 0.15, finisher ? 0.2 : 0.0);
				Vec3 c = t.position().add(0, t.getBbHeight() * 0.6, 0);
				((ServerLevel) p.level()).sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
			};
			if (i == 0) {
				punch.run();
			} else {
				schedule(p, SuperSoldierConfig.COMBO_INTERVAL * i, punch);
			}
		}
		return true;
	}

	private static boolean t0Reach(ServerPlayer p, LivingEntity t) {
		return AbilityHelpers.distanceSqToBox(t, p.getEyePosition()) <= (SuperSoldierConfig.COMBO_RANGE + 1.0) * (SuperSoldierConfig.COMBO_RANGE + 1.0);
	}

	// ---------------------------------------------------------------- Shift+R: Uppercut Launcher

	public static boolean uppercut(ServerPlayer p) {
		LivingEntity target = facing(p, SuperSoldierConfig.UPPERCUT_RANGE);
		if (target == null) {
			SuperSoldier.say(p, "message.projecthero.super_soldier.no_target", ChatFormatting.GRAY);
			return false;
		}
		if (!begin(p, UPPERCUT, SuperSoldierConfig.UPPERCUT_COOLDOWN, 6)) {
			return false;
		}
		p.swing(InteractionHand.MAIN_HAND, true);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.7f);
		AbilityHelpers.sound(p, SoundEvents.IRON_GOLEM_ATTACK, 0.9f, 1.2f);
		if (strike(p, target, SuperSoldierConfig.UPPERCUT_DAMAGE, p.position(), 0.0, 0.0) && !isBoss(target)) {
			Vec3 v = target.getDeltaMovement();
			target.setDeltaMovement(v.x * 0.3, SuperSoldierConfig.UPPERCUT_LIFT, v.z * 0.3);
			target.hurtMarked = true;
		}
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = target.position();
		level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.2, c.z, 10, 0.3, 0.1, 0.3, 0.05);
		level.sendParticles(blue(1.2f), c.x, c.y + 0.8, c.z, 16, 0.2, 0.8, 0.2, 0.0);
		return true;
	}

	// ---------------------------------------------------------------- G: Shield Throw

	public static boolean shieldThrow(ServerPlayer p) {
		if (!begin(p, SHIELD_THROW, SuperSoldierConfig.SHIELD_THROW_COOLDOWN, 6)) {
			return false;
		}
		p.swing(InteractionHand.MAIN_HAND, true);
		AbilityHelpers.sound(p, SoundEvents.TRIDENT_THROW, 1.0f, 1.3f);
		Vec3 from = p.getEyePosition().add(0, -0.2, 0).add(p.getLookAngle().scale(0.6));
		SoldierShieldEntity.launch(p, from, p.getLookAngle());
		return true;
	}

	// ---------------------------------------------------------------- Shift+G: Shield Bash Charge

	public static boolean shieldBash(ServerPlayer p) {
		if (!begin(p, SHIELD_BASH, SuperSoldierConfig.BASH_COOLDOWN, SuperSoldierConfig.BASH_TICKS + 2)) {
			return false;
		}
		Vec3 dir = flatLook(p);
		BASHES.put(p.getUUID(), new Bash(dir, p.level().getGameTime()));
		AbilityHelpers.sound(p, SoundEvents.SHIELD_BLOCK, 1.0f, 0.7f);
		AbilityHelpers.sound(p, SoundEvents.RAVAGER_ROAR, 0.35f, 1.8f);
		tickBash(p, BASHES.get(p.getUUID()));
		return true;
	}

	private static boolean tickBash(ServerPlayer p, Bash b) {
		long age = p.level().getGameTime() - b.start;
		if (age >= SuperSoldierConfig.BASH_TICKS || !alive(p) || (age > 1 && p.horizontalCollision)) {
			return false;
		}
		AbilityHelpers.launchSelf(p, new Vec3(b.dir.x * SuperSoldierConfig.BASH_SPEED, Math.min(p.getDeltaMovement().y, 0.0),
				b.dir.z * SuperSoldierConfig.BASH_SPEED));
		ServerLevel level = (ServerLevel) p.level();
		Vec3 front = p.position().add(b.dir.scale(0.8));
		AABB box = new AABB(front, front).inflate(SuperSoldierConfig.BASH_WIDTH * 0.5 + 0.4, 1.0, SuperSoldierConfig.BASH_WIDTH * 0.5 + 0.4)
				.move(0, 1.0, 0).expandTowards(b.dir.scale(SuperSoldierConfig.BASH_SPEED));
		for (LivingEntity e : targets(p, box)) {
			if (!b.hit.add(e.getId())) {
				continue;
			}
			if (strike(p, e, SuperSoldierConfig.BASH_DAMAGE, p.position().subtract(b.dir), SuperSoldierConfig.BASH_KNOCKBACK, 0.3)) {
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 0);
				level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.SHIELD_BLOCK, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.9f);
			}
		}
		level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.3, p.getZ(), 3, 0.2, 0.1, 0.2, 0.02);
		level.sendParticles(blue(1.0f), front.x, p.getY() + 1.0, front.z, 4, 0.3, 0.4, 0.3, 0.0);
		return true;
	}

	// ---------------------------------------------------------------- Z: Leaping Slam

	public static boolean leapingSlam(ServerPlayer p) {
		if (!begin(p, SLAM, SuperSoldierConfig.SLAM_COOLDOWN, 8)) {
			return false;
		}
		long now = p.level().getGameTime();
		edit(p, n -> n.noFallUntil = now + SuperSoldierConfig.LEAP_NO_FALL_TICKS);
		Vec3 f = flatLook(p).scale(SuperSoldierConfig.SLAM_FORWARD);
		AbilityHelpers.launchSelf(p, new Vec3(f.x, SuperSoldier.verticalSpeedForHeight(SuperSoldierConfig.SLAM_HEIGHT), f.z));
		SLAMS.put(p.getUUID(), now);
		AbilityHelpers.sound(p, SoundEvents.BREEZE_JUMP, 1.0f, 0.8f);
		((ServerLevel) p.level()).sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 12, 0.4, 0.05, 0.4, 0.05);
		return true;
	}

	/** Ends the slam now: the landing shockwave. Public for tests. */
	public static int slamImpact(ServerPlayer p) {
		SLAMS.remove(p.getUUID());
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = p.position();
		int n = radial(p, c.add(0, 0.5, 0), SuperSoldierConfig.SLAM_RADIUS, SuperSoldierConfig.SLAM_DAMAGE,
				SuperSoldierConfig.SLAM_KNOCKBACK, SuperSoldierConfig.SLAM_LIFT);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, net.minecraft.sounds.SoundSource.PLAYERS, 1.2f, 0.9f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 0.6f);
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.2, c.z, 2, 0.6, 0.1, 0.6, 0.0);
		BlockState ground = level.getBlockState(BlockPos.containing(c.x, c.y - 0.5, c.z));
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), c.x, c.y + 0.1, c.z, 40,
					SuperSoldierConfig.SLAM_RADIUS * 0.4, 0.1, SuperSoldierConfig.SLAM_RADIUS * 0.4, 0.2);
		}
		for (int i = 0; i < 24; i++) {
			double a = i * (Math.PI * 2.0 / 24.0);
			level.sendParticles(blue(1.3f), c.x + Math.cos(a) * SuperSoldierConfig.SLAM_RADIUS * 0.7, c.y + 0.2,
					c.z + Math.sin(a) * SuperSoldierConfig.SLAM_RADIUS * 0.7, 1, 0, 0, 0, 0);
		}
		p.resetFallDistance();
		return n;
	}

	// ---------------------------------------------------------------- Shift+Z: ULTIMATE Super Soldier Onslaught

	public static boolean onslaught(ServerPlayer p) {
		if (!begin(p, ONSLAUGHT, SuperSoldierConfig.ONSLAUGHT_COOLDOWN, 10)) {
			return false;
		}
		long now = p.level().getGameTime();
		edit(p, n -> n.onslaughtUntil = now + SuperSoldierConfig.ONSLAUGHT_DURATION);
		SuperSoldier.reconcile(p);
		p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, SuperSoldierConfig.ONSLAUGHT_DURATION, 0, false, false, true));
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, SuperSoldierConfig.ONSLAUGHT_DURATION, 0, false, false, true));
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = p.position();
		radial(p, c.add(0, 0.8, 0), SuperSoldierConfig.ONSLAUGHT_BURST_RADIUS, SuperSoldierConfig.ONSLAUGHT_BURST_DAMAGE, 1.3, 0.35);
		AbilityHelpers.sound(p, SoundEvents.RAID_HORN, 0.6f, 1.3f);
		AbilityHelpers.sound(p, SoundEvents.TOTEM_USE, 0.6f, 1.4f);
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1.0, c.z, 1, 0, 0, 0, 0);
		for (int ring = 1; ring <= 3; ring++) {
			double r = SuperSoldierConfig.ONSLAUGHT_BURST_RADIUS * ring / 3.0;
			for (int i = 0; i < 20 * ring; i++) {
				double a = i * (Math.PI * 2.0 / (20 * ring));
				level.sendParticles(ring == 2 ? red(1.4f) : blue(1.4f), c.x + Math.cos(a) * r, c.y + 0.3, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
			}
		}
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 1.0, c.z, 30, 0.4, 0.9, 0.4, 0.1);
		SuperSoldier.say(p, "message.projecthero.super_soldier.onslaught", ChatFormatting.BLUE);
		return true;
	}

	/** Onslaught's melee shock: every enemy close to the one he just punched takes a jolt. */
	static void onslaughtShock(ServerPlayer p, LivingEntity punched) {
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = punched.position().add(0, punched.getBbHeight() * 0.5, 0);
		AABB box = new AABB(c, c).inflate(SuperSoldierConfig.ONSLAUGHT_SHOCK_RADIUS);
		for (LivingEntity e : targets(p, box)) {
			if (e == punched || AbilityHelpers.distanceSqToBox(e, c) > SuperSoldierConfig.ONSLAUGHT_SHOCK_RADIUS * SuperSoldierConfig.ONSLAUGHT_SHOCK_RADIUS) {
				continue;
			}
			if (strike(p, e, SuperSoldierConfig.ONSLAUGHT_SHOCK_DAMAGE, c, 0.6, 0.1)) {
				AbilityHelpers.line(level, c, e.position().add(0, e.getBbHeight() * 0.5, 0), ParticleTypes.ELECTRIC_SPARK, 3.0);
			}
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 10, 0.4, 0.4, 0.4, 0.2);
		level.sendParticles(blue(1.2f), c.x, c.y, c.z, 8, 0.5, 0.5, 0.5, 0.0);
	}

	// ---------------------------------------------------------------- X: Tactical Roll

	public static boolean tacticalRoll(ServerPlayer p) {
		if (!begin(p, ROLL, SuperSoldierConfig.ROLL_COOLDOWN, 6)) {
			return false;
		}
		Vec3 motion = p.getDeltaMovement();
		Vec3 flat = new Vec3(motion.x, 0.0, motion.z);
		Vec3 dir = flat.lengthSqr() > 0.0025 ? flat.normalize() : flatLook(p);
		long now = p.level().getGameTime();
		edit(p, n -> {
			n.iframeUntil = now + SuperSoldierConfig.ROLL_IFRAMES;
			n.noFallUntil = Math.max(n.noFallUntil, now + 20L);
		});
		AbilityHelpers.launchSelf(p, new Vec3(dir.x * SuperSoldierConfig.ROLL_SPEED, p.onGround() ? 0.15 : Math.max(motion.y, 0.0),
				dir.z * SuperSoldierConfig.ROLL_SPEED));
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 1.5f);
		ServerLevel level = (ServerLevel) p.level();
		level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.2, p.getZ(), 10, 0.3, 0.1, 0.3, 0.03);
		return true;
	}

	/** True while the Tactical Roll's invulnerability frames run. */
	public static boolean rolling(ServerPlayer p) {
		return SuperSoldier.state(p).iframeUntil > p.level().getGameTime();
	}

	// ---------------------------------------------------------------- Shift+X: High Leap

	public static boolean highLeap(ServerPlayer p) {
		if (!begin(p, HIGH_LEAP, SuperSoldierConfig.HIGH_LEAP_COOLDOWN, 6)) {
			return false;
		}
		long now = p.level().getGameTime();
		edit(p, n -> n.noFallUntil = now + SuperSoldierConfig.LEAP_NO_FALL_TICKS);
		Vec3 f = flatLook(p).scale(SuperSoldierConfig.HIGH_LEAP_FORWARD);
		AbilityHelpers.launchSelf(p, new Vec3(f.x, SuperSoldier.verticalSpeedForHeight(SuperSoldierConfig.HIGH_LEAP_HEIGHT), f.z));
		AbilityHelpers.sound(p, SoundEvents.BREEZE_JUMP, 1.0f, 1.1f);
		ServerLevel level = (ServerLevel) p.level();
		level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 16, 0.5, 0.05, 0.5, 0.06);
		level.sendParticles(blue(1.0f), p.getX(), p.getY() + 0.5, p.getZ(), 10, 0.3, 0.4, 0.3, 0.0);
		return true;
	}

	// ---------------------------------------------------------------- V: Battle Cry

	public static boolean battleCry(ServerPlayer p) {
		if (!begin(p, BATTLE_CRY, SuperSoldierConfig.BATTLE_CRY_COOLDOWN, 6)) {
			return false;
		}
		ServerLevel level = (ServerLevel) p.level();
		int buff = SuperSoldierConfig.BATTLE_CRY_BUFF_TICKS;
		// himself and every squad-mate in range
		for (Player other : level.getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(SuperSoldierConfig.BATTLE_CRY_ALLY_RADIUS),
				o -> o.isAlive() && !o.isSpectator() && (o == p || com.projecthero.mod.squad.Squads.areAllies(p, o)))) {
			other.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, buff, 0, false, true, true));
			other.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, buff, 0, false, true, true));
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, other.getX(), other.getY() + 1.0, other.getZ(), 8, 0.4, 0.6, 0.4, 0.0);
		}
		// hostile mobs (and enemy players) nearby lose heart
		for (LivingEntity e : targets(p, p.getBoundingBox().inflate(SuperSoldierConfig.BATTLE_CRY_ENEMY_RADIUS))) {
			if (!(e instanceof Enemy) && !(e instanceof Player)) {
				continue;
			}
			AbilityHelpers.applyControl(e, MobEffects.WEAKNESS, SuperSoldierConfig.BATTLE_CRY_DEBUFF_TICKS, 0);
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, SuperSoldierConfig.BATTLE_CRY_DEBUFF_TICKS / 2, 0);
			level.sendParticles(ParticleTypes.ANGRY_VILLAGER, e.getX(), e.getY() + e.getBbHeight(), e.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
		}
		SuperSoldier.markCombat(p);
		p.swing(InteractionHand.MAIN_HAND, true);
		AbilityHelpers.sound(p, SoundEvents.PILLAGER_CELEBRATE, 1.0f, 0.7f);
		AbilityHelpers.sound(p, SoundEvents.RAID_HORN, 0.35f, 1.6f);
		Vec3 c = p.position();
		for (int i = 0; i < 32; i++) {
			double a = i * (Math.PI * 2.0 / 32.0);
			level.sendParticles(i % 2 == 0 ? blue(1.2f) : red(1.2f), c.x + Math.cos(a) * 2.5, c.y + 1.2, c.z + Math.sin(a) * 2.5, 1, 0, 0.2, 0, 0);
		}
		return true;
	}

	// ---------------------------------------------------------------- Shift+V: Tactical Focus

	public static boolean tacticalFocus(ServerPlayer p) {
		if (!begin(p, FOCUS, SuperSoldierConfig.FOCUS_COOLDOWN, 6)) {
			return false;
		}
		long now = p.level().getGameTime();
		edit(p, n -> n.focusUntil = now + SuperSoldierConfig.FOCUS_DURATION);
		Set<Integer> marked = new HashSet<>();
		ServerLevel level = (ServerLevel) p.level();
		for (LivingEntity e : targets(p, p.getBoundingBox().inflate(SuperSoldierConfig.FOCUS_RADIUS))) {
			if (!(e instanceof Enemy) && !(e instanceof Player)) {
				continue;
			}
			if (e.distanceToSqr(p) > SuperSoldierConfig.FOCUS_RADIUS * SuperSoldierConfig.FOCUS_RADIUS) {
				continue;
			}
			e.addEffect(new MobEffectInstance(MobEffects.GLOWING, SuperSoldierConfig.FOCUS_GLOW_TICKS, 0, false, false, false));
			marked.add(e.getId());
			level.sendParticles(ParticleTypes.ENCHANTED_HIT, e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(), 4, 0.1, 0.1, 0.1, 0.0);
		}
		MARKS.put(p.getUUID(), marked);
		AbilityHelpers.sound(p, SoundEvents.EVOKER_PREPARE_ATTACK, 0.6f, 1.8f);
		SuperSoldier.say(p, "message.projecthero.super_soldier.focus", ChatFormatting.GOLD, marked.size());
		return true;
	}

	/** How many enemies the current Tactical Focus has marked (tests / HUD). */
	public static int markCount(ServerPlayer p) {
		Set<Integer> m = MARKS.get(p.getUUID());
		return m == null ? 0 : m.size();
	}

	// ---------------------------------------------------------------- tick

	public static void tick(ServerPlayer p) {
		UUID id = p.getUUID();
		long now = p.level().getGameTime();
		List<Task> tasks = TASKS.get(id);
		if (tasks != null) {
			List<Task> due = new ArrayList<>();
			for (Iterator<Task> it = tasks.iterator(); it.hasNext();) {
				Task t = it.next();
				if (t.due <= now) {
					due.add(t);
					it.remove();
				}
			}
			if (tasks.isEmpty()) {
				TASKS.remove(id);
			}
			for (Task t : due) {
				t.run.run();
			}
		}
		Bash bash = BASHES.get(id);
		if (bash != null && now > bash.start && !tickBash(p, bash)) {
			BASHES.remove(id);
			Vec3 v = p.getDeltaMovement();
			AbilityHelpers.launchSelf(p, new Vec3(v.x * 0.3, v.y, v.z * 0.3));
		}
		Long slamStart = SLAMS.get(id);
		if (slamStart != null) {
			long air = now - slamStart;
			if (!alive(p)) {
				SLAMS.remove(id);
			} else if ((air >= 4 && (p.onGround() || p.isInWater())) || air >= SuperSoldierConfig.SLAM_MAX_AIR_TICKS) {
				slamImpact(p);
			} else if (air >= 6 && p.getDeltaMovement().y < 0.0) {
				// driving down hard once past the apex
				Vec3 v = p.getDeltaMovement();
				p.setDeltaMovement(v.x, Math.max(-2.0, v.y - 0.12), v.z);
				p.hurtMarked = true;
			}
		}
	}
}
