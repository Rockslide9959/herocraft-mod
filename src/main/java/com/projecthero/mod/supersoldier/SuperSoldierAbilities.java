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
import com.projecthero.mod.quirk.fx.QuirkCamera;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * The Super Soldier moves (v0.14.8; v0.14.9 G / Shift+G became bare-handed attacks and the shield throw moved to C, thrown from
 * his hand; v0.17.7 rework: Battle Cry, Tactical Focus and the Onslaught buff merged into Assemble on Shift+V, V became the
 * Counter Stance attack, and Shift+Z is an ultimate in two versions -- the Peak Human Barrage bare-handed, the Shield Storm with a
 * shield). Every one is server-validated by {@link #begin}: power, alive, not locked by a running move, cooldown ready -- and only
 * then is the cooldown started. Multi-tick moves (the Combo Strike's punches, the Flying Kick's lunge, the Takedown's slam, the
 * Leaping Slam's landing, the Barrage, the Storm) run from small per-player tables ticked by {@link #tick}, and are dropped if the
 * player dies, logs out or loses the power ({@link #clear}; every table is emptied by {@code ServerStateReset}).
 *
 * <pre>
 *   R        Combo Strike         Shift+R  Uppercut Launcher
 *   G        Flying Kick          Shift+G  Judo Takedown
 *   Z        Leaping Slam         Shift+Z  ULTIMATE: Peak Human Barrage (no shield) / Shield Storm (a shield held or slotted)
 *   X        Tactical Roll        Shift+X  High Leap
 *   V        Counter Stance       Shift+V  Assemble
 *   C        Shield Throw (needs a shield in either hand: the Adamantium Shield at full power, any other shield weaker)
 * </pre>
 */
public final class SuperSoldierAbilities {
	public static final String COMBO = "combo_strike";
	public static final String UPPERCUT = "uppercut_launcher";
	public static final String FLYING_KICK = "flying_kick";
	public static final String TAKEDOWN = "judo_takedown";
	public static final String SHIELD_THROW = "shield_throw";
	public static final String SLAM = "leaping_slam";
	/** v0.17.7: the two ultimates (one shared cooldown, {@link #ULTIMATE}). */
	public static final String BARRAGE = "peak_human_barrage";
	public static final String SHIELD_STORM = "shield_storm";
	/** The cooldown key both ultimates write and read. */
	public static final String ULTIMATE = "ultimate";
	public static final String ROLL = "tactical_roll";
	public static final String HIGH_LEAP = "high_leap";
	/** v0.17.7: V. */
	public static final String COUNTER = "counter_stance";
	/** v0.17.7: Shift+V (Battle Cry + Tactical Focus + the Onslaught buff). */
	public static final String ASSEMBLE = "assemble";

	/** The Guidebook / power-info rows: key label, ability id. */
	public static final String[][] GUIDE_ROWS = {
			{ "R", COMBO }, { "Shift+R", UPPERCUT },
			{ "G", FLYING_KICK }, { "Shift+G", TAKEDOWN },
			{ "Z", SLAM }, { "Shift+Z", BARRAGE }, { "Shift+Z (shield)", SHIELD_STORM },
			{ "X", ROLL }, { "Shift+X", HIGH_LEAP },
			{ "V", COUNTER }, { "Shift+V", ASSEMBLE },
			{ "C", SHIELD_THROW },
			{ "N", "shield_slot" } }; // v0.17.5: only in the Captain America Battle Suit top

	/** The cooldown key an ability id writes: both ultimates share {@link #ULTIMATE}. */
	public static String cooldownKey(String id) {
		return BARRAGE.equals(id) || SHIELD_STORM.equals(id) ? ULTIMATE : id;
	}

	private record Task(long due, Runnable run) {
	}

	/** A Flying Kick in the air: who it is aimed at and when it started. */
	private record Kick(int targetId, long start) {
	}

	/** v0.17.7: a Peak Human Barrage under way -- the dash, then (once he arrives) the flurry and the haymaker. */
	private static final class Barrage {
		int targetId;
		final long start;
		long arrived = -1L;
		int hits;
		boolean windup;

		Barrage(int targetId, long start) {
			this.targetId = targetId;
			this.start = start;
		}
	}

	/** v0.17.7: a Shield Storm under way. */
	private static final class Storm {
		static final int RISE = 0, HOVER = 1, DIVE = 2;
		int phase = RISE;
		final long start;
		long phaseStart;
		int shieldId = -1;
		boolean fromSlot;
		boolean off;

		Storm(long start, boolean fromSlot, boolean off) {
			this.start = start;
			this.phaseStart = start;
			this.fromSlot = fromSlot;
			this.off = off;
		}
	}

	private static final Map<UUID, List<Task>> TASKS = new HashMap<>();
	private static final Map<UUID, Kick> KICKS = new HashMap<>();
	/** Leaping Slam in the air: game time of take-off. */
	private static final Map<UUID, Long> SLAMS = new HashMap<>();
	/** Assemble: the entity ids each player has marked. */
	private static final Map<UUID, Set<Integer>> MARKS = new HashMap<>();
	/** v0.17.7: the ultimates in progress (entity ids only -- no entities or worlds held). */
	private static final Map<UUID, Barrage> BARRAGES = new HashMap<>();
	private static final Map<UUID, Storm> STORMS = new HashMap<>();
	/** Set while a move is applying its damage, so the melee hooks do not treat it as a punch. */
	private static final ThreadLocal<Boolean> ABILITY_HIT = ThreadLocal.withInitial(() -> false);

	private SuperSoldierAbilities() {
	}

	public static void clearSessionState() {
		TASKS.clear();
		KICKS.clear();
		SLAMS.clear();
		MARKS.clear();
		BARRAGES.clear();
		STORMS.clear();
	}

	public static void clear(UUID id) {
		TASKS.remove(id);
		KICKS.remove(id);
		SLAMS.remove(id);
		MARKS.remove(id);
		BARRAGES.remove(id);
		STORMS.remove(id);
	}

	static void clearMarks(UUID id) {
		MARKS.remove(id);
	}

	public static boolean isAbilityHit() {
		return ABILITY_HIT.get();
	}

	/** True while Assemble has {@code target} marked for {@code player}. */
	public static boolean isMarked(ServerPlayer player, LivingEntity target) {
		Set<Integer> m = MARKS.get(player.getUUID());
		return m != null && m.contains(target.getId()) && SuperSoldier.assembleActive(player);
	}

	/** True while the Leaping Slam is in the air (tests + the fall-damage rule). */
	public static boolean slamming(ServerPlayer player) {
		return SLAMS.containsKey(player.getUUID());
	}

	/** True while a Flying Kick is still lunging (tests). */
	public static boolean kicking(ServerPlayer player) {
		return KICKS.containsKey(player.getUUID());
	}

	/** v0.17.7: true while the Barrage has him rooted at his target (the flurry and the haymaker; not the dash). Server only. */
	public static boolean barraging(Player player) {
		Barrage b = BARRAGES.get(player.getUUID());
		return b != null && b.arrived >= 0L && player instanceof ServerPlayer;
	}

	/** v0.17.7: a Barrage under way, dash included (tests). */
	public static boolean barrageRunning(ServerPlayer player) {
		return BARRAGES.containsKey(player.getUUID());
	}

	/** v0.17.7: the Shield Storm's phase (0 rise, 1 the shield out, 2 the dive), -1 when none runs (tests). */
	public static int stormPhase(ServerPlayer player) {
		Storm s = STORMS.get(player.getUUID());
		return s == null ? -1 : s.phase;
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
		String key = cooldownKey(id);
		Long ready = s.abilityReadyAt.get(key);
		if (ready != null && now < ready) {
			SuperSoldier.say(p, "message.projecthero.super_soldier.cooldown", ChatFormatting.RED,
					Component.translatable("projecthero.super_soldier.ability." + id),
					String.format(java.util.Locale.ROOT, "%.1f", (ready - now) / 20.0));
			return false;
		}
		SuperSoldierState n = s.copy();
		n.abilityReadyAt.put(key, now + cooldown);
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
		// v0.14.20: the shared rule 1 (com.projecthero.mod.combat.HeroTargets#canHarm)
		return com.projecthero.mod.combat.HeroTargets.canHarm(p, e);
	}

	/** v0.14.20: rule 2 (com.projecthero.mod.combat.HeroTargets#isHostile) -- ricochet picks, Assemble's debuffs and marks. */
	public static boolean isHostile(ServerPlayer p, LivingEntity e) {
		return com.projecthero.mod.combat.HeroTargets.isHostile(p, e);
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

	/** v0.17.7: the nearest hostile within {@code range} of him (the Shield Storm's opening target when none is in front). */
	static LivingEntity nearestHostile(ServerPlayer p, double range) {
		LivingEntity best = null;
		double bestD = range * range;
		for (LivingEntity e : targets(p, p.getBoundingBox().inflate(range))) {
			if (!isHostile(p, e)) {
				continue;
			}
			double d = e.distanceToSqr(p);
			if (d <= bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	/** v0.17.7: a shield he can use -- in either hand, or slotted in the Battle Suit (which always counts as held). */
	public static boolean hasShield(Player p) {
		return shieldHand(p) != null || CapShieldSlot.hasThrowable(p);
	}

	/**
	 * One move hitting one target: damage (x1.25 on an enemy Assemble has marked, at most {@link SuperSoldierConfig#MAX_HIT},
	 * boss-capped) and knockback away from {@code origin} plus {@code lift} (never on a boss). True if it landed.
	 */
	public static boolean strike(ServerPlayer p, LivingEntity target, float baseDamage, Vec3 origin, double knockback, double lift) {
		if (!canTarget(p, target)) {
			return false;
		}
		float dmg = baseDamage;
		boolean marked = isMarked(p, target);
		if (marked) {
			dmg *= SuperSoldierConfig.ASSEMBLE_MARK_MULTIPLIER;
		}
		dmg = Math.min(dmg, SuperSoldierConfig.MAX_HIT); // v0.17.7: Street tier ceiling
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
		level.sendParticles(marked ? ParticleTypes.ENCHANTED_HIT : ParticleTypes.CRIT, c.x, c.y, c.z, marked ? 12 : 6, 0.3, 0.3, 0.3, 0.3);
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

	private static DustParticleOptions white(float size) {
		return new DustParticleOptions(new Vector3f(0.95f, 0.95f, 0.95f), size);
	}

	private static DustParticleOptions gold(float size) {
		return new DustParticleOptions(new Vector3f(1.0f, 0.78f, 0.2f), size);
	}

	/** A flat ring of dust at height {@code y}. */
	private static void ring(ServerLevel level, Vec3 c, double y, double r, int points, DustParticleOptions a, DustParticleOptions b) {
		for (int i = 0; i < points; i++) {
			double ang = i * (Math.PI * 2.0 / points);
			level.sendParticles(i % 2 == 0 ? a : b, c.x + Math.cos(ang) * r, c.y + y, c.z + Math.sin(ang) * r, 1, 0, 0.02, 0, 0);
		}
	}

	private static void groundDust(ServerLevel level, Vec3 c, int count, double spread) {
		BlockState ground = level.getBlockState(BlockPos.containing(c.x, c.y - 0.5, c.z));
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), c.x, c.y + 0.1, c.z, count, spread, 0.1, spread, 0.2);
		}
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

	// ---------------------------------------------------------------- G: Flying Kick

	/** A lunging flying kick into the enemy in front (up to 8 blocks away): 12 damage, a hard knockback. */
	public static boolean flyingKick(ServerPlayer p) {
		LivingEntity target = facing(p, SuperSoldierConfig.KICK_RANGE);
		if (target == null) {
			SuperSoldier.say(p, "message.projecthero.super_soldier.no_target", ChatFormatting.GRAY);
			return false;
		}
		if (!begin(p, FLYING_KICK, SuperSoldierConfig.KICK_COOLDOWN, SuperSoldierConfig.KICK_TICKS + 2)) {
			return false;
		}
		long now = p.level().getGameTime();
		edit(p, n -> n.noFallUntil = Math.max(n.noFallUntil, now + 40L));
		AbilityHelpers.sound(p, SoundEvents.BREEZE_JUMP, 0.8f, 1.4f);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 0.8f);
		((ServerLevel) p.level()).sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 8, 0.3, 0.05, 0.3, 0.04);
		Kick kick = new Kick(target.getId(), now);
		if (tickKick(p, kick)) {
			KICKS.put(p.getUUID(), kick);
		}
		return true;
	}

	/** One tick of the lunge. True while it is still flying; false once it has landed or whiffed. */
	private static boolean tickKick(ServerPlayer p, Kick k) {
		long age = p.level().getGameTime() - k.start;
		Vec3 body = p.position().add(0, p.getBbHeight() * 0.5, 0);
		if (!alive(p) || !(p.level().getEntity(k.targetId) instanceof LivingEntity t) || !t.isAlive() || !canTarget(p, t)
				|| age > SuperSoldierConfig.KICK_TICKS) {
			if (age > 0) {
				Vec3 v = p.getDeltaMovement();
				AbilityHelpers.launchSelf(p, new Vec3(v.x * 0.3, Math.min(v.y, 0.1), v.z * 0.3));
			}
			return false;
		}
		Vec3 to = t.getBoundingBox().getCenter().subtract(body);
		if (AbilityHelpers.distanceSqToBox(t, p.position()) <= SuperSoldierConfig.KICK_REACH * SuperSoldierConfig.KICK_REACH
				|| AbilityHelpers.distanceSqToBox(t, body) <= SuperSoldierConfig.KICK_REACH * SuperSoldierConfig.KICK_REACH) {
			landKick(p, t, to);
			return false;
		}
		Vec3 dir = to.normalize();
		AbilityHelpers.launchSelf(p, new Vec3(dir.x * SuperSoldierConfig.KICK_SPEED, Math.max(-0.4, dir.y * SuperSoldierConfig.KICK_SPEED) + 0.08,
				dir.z * SuperSoldierConfig.KICK_SPEED));
		ServerLevel level = (ServerLevel) p.level();
		level.sendParticles(blue(1.0f), p.getX(), p.getY() + 0.4, p.getZ(), 3, 0.2, 0.2, 0.2, 0.0);
		level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.3, p.getZ(), 1, 0.1, 0.1, 0.1, 0.01);
		return true;
	}

	private static void landKick(ServerPlayer p, LivingEntity t, Vec3 to) {
		p.swing(InteractionHand.MAIN_HAND, true);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.7f);
		AbilityHelpers.sound(p, SoundEvents.IRON_GOLEM_ATTACK, 0.8f, 1.1f);
		strike(p, t, SuperSoldierConfig.KICK_DAMAGE, p.position(), SuperSoldierConfig.KICK_KNOCKBACK, SuperSoldierConfig.KICK_LIFT);
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(blue(1.3f), c.x, c.y, c.z, 14, 0.4, 0.4, 0.4, 0.0);
		level.sendParticles(red(1.1f), c.x, c.y, c.z, 8, 0.4, 0.4, 0.4, 0.0);
		// he rebounds off the kick: a little hop back, not a slide into the enemy
		Vec3 back = new Vec3(to.x, 0.0, to.z);
		back = back.lengthSqr() < 1.0e-4 ? flatLook(p).scale(-0.3) : back.normalize().scale(-0.3);
		AbilityHelpers.launchSelf(p, new Vec3(back.x, 0.3, back.z));
	}

	// ---------------------------------------------------------------- Shift+G: Judo Takedown

	/**
	 * Grab the enemy in front, hoist it over his shoulder and slam it into the ground behind him a quarter-second later:
	 * 14 damage and a short stun (Slowness IV + Weakness). Bosses are too big to throw -- they just take the (capped) hit.
	 */
	public static boolean takedown(ServerPlayer p) {
		LivingEntity target = facing(p, SuperSoldierConfig.TAKEDOWN_RANGE);
		if (target == null) {
			SuperSoldier.say(p, "message.projecthero.super_soldier.no_target", ChatFormatting.GRAY);
			return false;
		}
		if (!begin(p, TAKEDOWN, SuperSoldierConfig.TAKEDOWN_COOLDOWN, SuperSoldierConfig.TAKEDOWN_SLAM_DELAY + 4)) {
			return false;
		}
		boolean boss = isBoss(target);
		p.swing(InteractionHand.MAIN_HAND, true);
		AbilityHelpers.sound(p, SoundEvents.ARMOR_EQUIP_LEATHER, 1.0f, 0.7f);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 0.6f);
		ServerLevel level = (ServerLevel) p.level();
		if (!boss) {
			// hoisted over his shoulder
			Vec3 up = p.position().add(flatLook(p).scale(0.3)).add(0, p.getBbHeight() + 0.2, 0);
			if (level.noCollision(target, target.getBoundingBox().move(up.subtract(target.position())))) {
				target.teleportTo(up.x, up.y, up.z);
			}
			target.setDeltaMovement(0.0, 0.12, 0.0);
			target.hurtMarked = true;
			level.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY(), target.getZ(), 6, 0.3, 0.2, 0.3, 0.02);
		}
		schedule(p, SuperSoldierConfig.TAKEDOWN_SLAM_DELAY, () -> takedownSlam(p, target, boss));
		return true;
	}

	private static void takedownSlam(ServerPlayer p, LivingEntity target, boolean boss) {
		if (!alive(p) || !target.isAlive() || target.level() != p.level() || target.distanceToSqr(p) > 64.0) {
			return;
		}
		ServerLevel level = (ServerLevel) p.level();
		if (!boss) {
			// over the shoulder, onto the ground behind him (in front if there is a wall behind)
			Vec3 behind = p.position().subtract(flatLook(p).scale(1.6));
			Vec3 front = p.position().add(flatLook(p).scale(1.4));
			Vec3 land = level.noCollision(target, target.getBoundingBox().move(behind.subtract(target.position()))) ? behind
					: level.noCollision(target, target.getBoundingBox().move(front.subtract(target.position()))) ? front : null;
			if (land != null) {
				target.teleportTo(land.x, land.y, land.z);
			}
			target.setDeltaMovement(0.0, -0.8, 0.0);
			target.hurtMarked = true;
		}
		p.swing(InteractionHand.OFF_HAND, true);
		if (strike(p, target, SuperSoldierConfig.TAKEDOWN_DAMAGE, p.position(), 0.0, 0.0)) {
			AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN, SuperSoldierConfig.TAKEDOWN_STUN_TICKS, 3);
			AbilityHelpers.applyControl(target, MobEffects.WEAKNESS, SuperSoldierConfig.TAKEDOWN_STUN_TICKS, 0);
		}
		Vec3 c = target.position();
		level.playSound(null, c.x, c.y, c.z, SoundEvents.MACE_SMASH_GROUND, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_CRIT, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.6f);
		BlockState ground = level.getBlockState(BlockPos.containing(c.x, c.y - 0.5, c.z));
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), c.x, c.y + 0.1, c.z, 24, 0.5, 0.1, 0.5, 0.15);
		}
		level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.2, c.z, 10, 0.5, 0.1, 0.5, 0.05);
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y + 0.5, c.z, 12, 0.4, 0.3, 0.4, 0.3);
		level.sendParticles(blue(1.2f), c.x, c.y + 0.3, c.z, 12, 0.5, 0.2, 0.5, 0.0);
	}

	// ---------------------------------------------------------------- C: Shield Throw

	/** The hand holding a throwable shield (main hand first), or null. Any {@link ShieldItem} counts. */
	public static InteractionHand shieldHand(Player p) {
		if (p.getMainHandItem().getItem() instanceof ShieldItem) {
			return InteractionHand.MAIN_HAND;
		}
		if (p.getOffhandItem().getItem() instanceof ShieldItem) {
			return InteractionHand.OFF_HAND;
		}
		return null;
	}

	/**
	 * Throws the shield in his hand. The Adamantium Shield hits for 9 and bounces between up to 4 enemies over 24 blocks;
	 * any other shield hits for 6 and bounces between up to 3 over 16. The stack leaves his hand while it flies and the
	 * same stack comes back ({@link SoldierShieldEntity}). No shield: a hint, no cooldown.
	 */
	public static boolean shieldThrow(ServerPlayer p) {
		InteractionHand hand = shieldHand(p);
		// v0.17.5: a shield in the Battle Suit's slot always counts as held -- thrown from the slot, it flies home into the slot
		boolean fromSlot = hand == null && CapShieldSlot.hasThrowable(p);
		if (hand == null && !fromSlot) {
			SuperSoldier.say(p, "message.projecthero.super_soldier.no_shield", ChatFormatting.GRAY);
			return false;
		}
		ItemStack held = fromSlot ? CapShieldSlot.usable(p) : p.getItemInHand(hand);
		// v0.16.1: the Vibranium Shield is a better Adamantium Shield (it extends it) -- full-strength numbers and then some
		boolean adamantium = held.getItem() instanceof com.projecthero.mod.supersoldier.item.AdamantiumShieldItem;
		boolean vibranium = held.getItem() instanceof com.projecthero.mod.ultron.contagion.item.VibraniumShieldItem;
		if (!begin(p, SHIELD_THROW, adamantium ? SuperSoldierConfig.SHIELD_THROW_COOLDOWN : SuperSoldierConfig.NORMAL_SHIELD_THROW_COOLDOWN, 6)) {
			return false;
		}
		if (fromSlot) {
			held = CapShieldSlot.takeForThrow(p);
			hand = InteractionHand.MAIN_HAND; // the main arm throws it (and it would come back to that hand if the slot is gone)
		} else {
			if (p.isUsingItem() && p.getUsedItemHand() == hand) {
				p.stopUsingItem();
			}
			p.setItemInHand(hand, ItemStack.EMPTY);
		}
		p.swing(hand, true);
		AbilityHelpers.sound(p, SoundEvents.TRIDENT_THROW, 1.0f, adamantium ? 1.3f : 1.5f);
		Vec3 from = p.getEyePosition().add(0, -0.2, 0).add(p.getLookAngle().scale(0.6));
		LivingEntity first = facing(p, vibranium ? com.projecthero.mod.ultron.contagion.item.VibraniumShieldItem.THROW_RANGE
				: adamantium ? SuperSoldierConfig.SHIELD_RANGE : SuperSoldierConfig.NORMAL_SHIELD_RANGE);
		SoldierShieldEntity thrown = SoldierShieldEntity.launch(p, from, p.getLookAngle(), held, hand, adamantium, first);
		if (fromSlot) {
			thrown.returnToSlot();
		}
		if (vibranium) {
			thrown.upgrade(com.projecthero.mod.ultron.contagion.item.VibraniumShieldItem.THROW_DAMAGE,
					com.projecthero.mod.ultron.contagion.item.VibraniumShieldItem.THROW_HITS,
					com.projecthero.mod.ultron.contagion.item.VibraniumShieldItem.THROW_RANGE);
		}
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


	// ---------------------------------------------------------------- Shift+Z: the ULTIMATE (v0.17.7, two versions)

	/** Shift+Z: the Shield Storm with a shield in either hand (or slotted), the Peak Human Barrage bare-handed. One cooldown. */
	public static boolean ultimate(ServerPlayer p) {
		return hasShield(p) ? shieldStorm(p) : barrage(p);
	}

	// ---- unarmed: Peak Human Barrage

	/**
	 * He dashes onto the enemy in front (up to 6 blocks), roots it and himself, lands a six-hit flurry (alternating punches, a
	 * body shot, a knee, an elbow, an uppercut, 7.5 each), winds up and throws a haymaker for 20 that launches it with a 4-block
	 * burst (10 to everyone else). Bosses take the capped hits and are never moved. No target = no cooldown.
	 */
	public static boolean barrage(ServerPlayer p) {
		LivingEntity target = facing(p, SuperSoldierConfig.BARRAGE_RANGE);
		if (target == null) {
			SuperSoldier.say(p, "message.projecthero.super_soldier.no_target", ChatFormatting.GRAY);
			return false;
		}
		if (!begin(p, BARRAGE, SuperSoldierConfig.ULTIMATE_COOLDOWN,
				SuperSoldierConfig.BARRAGE_DASH_TICKS + SuperSoldierConfig.BARRAGE_HAYMAKER_TICK + 2)) {
			return false;
		}
		long now = p.level().getGameTime();
		edit(p, n -> n.noFallUntil = Math.max(n.noFallUntil, now + 60L));
		SuperSoldier.anim(p, SuperSoldierState.ANIM_BARRAGE_DASH, false);
		AbilityHelpers.sound(p, SoundEvents.BREEZE_JUMP, 0.8f, 1.5f);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 0.7f);
		QuirkCamera.fovPunch(p, 0.10f, 6);
		((ServerLevel) p.level()).sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 10, 0.3, 0.05, 0.3, 0.05);
		Barrage b = new Barrage(target.getId(), now);
		BARRAGES.put(p.getUUID(), b);
		if (!tickBarrage(p, b)) {
			BARRAGES.remove(p.getUUID());
		}
		SuperSoldier.markCombat(p);
		return true;
	}

	private static LivingEntity barrageTarget(ServerPlayer p, Barrage b) {
		if (p.level().getEntity(b.targetId) instanceof LivingEntity t && t.isAlive() && canTarget(p, t)
				&& t.distanceToSqr(p) < 100.0) {
			return t;
		}
		return null;
	}

	/** Stops him dead (keeps any fall). */
	private static void halt(ServerPlayer p) {
		Vec3 v = p.getDeltaMovement();
		AbilityHelpers.launchSelf(p, new Vec3(0.0, Math.min(0.0, v.y), 0.0));
	}

	/** One tick of the Barrage. False once it is over. */
	private static boolean tickBarrage(ServerPlayer p, Barrage b) {
		if (!alive(p)) {
			return false;
		}
		ServerLevel level = (ServerLevel) p.level();
		long now = level.getGameTime();
		LivingEntity t = barrageTarget(p, b);
		if (b.arrived < 0L) {
			if (t == null || now - b.start > SuperSoldierConfig.BARRAGE_DASH_TICKS) {
				halt(p); // it got away (or died): the dash whiffs
				SuperSoldier.anim(p, SuperSoldierState.ANIM_NONE, false);
				return false;
			}
			Vec3 body = p.position().add(0, p.getBbHeight() * 0.5, 0);
			double reach = SuperSoldierConfig.BARRAGE_REACH;
			if (AbilityHelpers.distanceSqToBox(t, p.position()) <= reach * reach || AbilityHelpers.distanceSqToBox(t, body) <= reach * reach) {
				// arrived: both rooted, the flurry starts next tick
				b.arrived = now;
				halt(p);
				SuperSoldier.anim(p, SuperSoldierState.ANIM_BARRAGE, false);
				if (!isBoss(t)) {
					AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, SuperSoldierConfig.BARRAGE_HAYMAKER_TICK + 6, 5);
					t.setDeltaMovement(0.0, Math.min(0.0, t.getDeltaMovement().y), 0.0);
					t.hurtMarked = true;
				}
				AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 0.6f);
				return true;
			}
			Vec3 dir = t.getBoundingBox().getCenter().subtract(body).normalize();
			double sp = SuperSoldierConfig.BARRAGE_DASH_SPEED;
			AbilityHelpers.launchSelf(p, new Vec3(dir.x * sp, Math.max(-0.4, dir.y * sp) + 0.05, dir.z * sp));
			level.sendParticles(blue(1.1f), p.getX(), p.getY() + 0.9, p.getZ(), 4, 0.25, 0.4, 0.25, 0.0);
			level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.2, p.getZ(), 2, 0.15, 0.05, 0.15, 0.02);
			return true;
		}
		long age = now - b.arrived;
		if (t == null) {
			// the target dropped mid-flurry: the next enemy right in front takes the rest
			t = facing(p, 3.5);
			if (t != null) {
				b.targetId = t.getId();
			}
		}
		halt(p); // rooted while he swings
		if (t != null && !isBoss(t)) {
			// locked in place in front of him
			t.setDeltaMovement(0.0, Math.min(0.0, t.getDeltaMovement().y), 0.0);
			t.hurtMarked = true;
		}
		while (b.hits < SuperSoldierConfig.BARRAGE_HITS && age >= 1 + (long) b.hits * SuperSoldierConfig.BARRAGE_HIT_INTERVAL) {
			flurryHit(p, t, b.hits);
			b.hits++;
		}
		if (!b.windup && age >= 1 + (long) SuperSoldierConfig.BARRAGE_HITS * SuperSoldierConfig.BARRAGE_HIT_INTERVAL) {
			// the wind-up: he draws the right hand back -- a rising whoosh
			b.windup = true;
			AbilityHelpers.sound(p, SoundEvents.TRIDENT_RIPTIDE_1, 0.8f, 1.5f);
			Vec3 c = p.position().add(0, 1.1, 0).subtract(flatLook(p).scale(0.4));
			level.sendParticles(gold(1.0f), c.x, c.y, c.z, 10, 0.25, 0.25, 0.25, 0.0);
		}
		if (age >= SuperSoldierConfig.BARRAGE_HAYMAKER_TICK) {
			haymaker(p, t);
			return false;
		}
		return true;
	}

	/** The flurry's hits: 0 right straight, 1 left hook, 2 right body shot, 3 knee, 4 left elbow, 5 right uppercut. */
	private static void flurryHit(ServerPlayer p, LivingEntity t, int i) {
		ServerLevel level = (ServerLevel) p.level();
		boolean heavy = i == 3 || i == 4;
		AbilityHelpers.sound(p, heavy ? SoundEvents.PLAYER_ATTACK_CRIT : SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 0.9f + i * 0.07f);
		if (i % 2 == 1) {
			AbilityHelpers.sound(p, SoundEvents.IRON_GOLEM_ATTACK, 0.45f, 1.4f);
		}
		QuirkCamera.shake(p, heavy ? 0.16f : 0.11f, 3);
		if (t == null) {
			return;
		}
		strike(p, t, SuperSoldierConfig.BARRAGE_HIT_DAMAGE, p.position(), 0.0, i == 5 ? 0.15 : 0.0);
		// hit sparks where the blow lands (the knee low, the uppercut under the chin)
		double h = i == 2 ? 0.45 : i == 3 ? 0.35 : i == 5 ? 0.8 : 0.65;
		Vec3 c = t.position().add(0, t.getBbHeight() * h, 0).add(p.position().subtract(t.position()).multiply(1, 0, 1).normalize().scale(0.3));
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 8, 0.15, 0.15, 0.15, 0.35);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 5, 0.05, 0.05, 0.05, 0.3);
		level.sendParticles(i % 2 == 0 ? blue(1.0f) : red(1.0f), c.x, c.y, c.z, 5, 0.15, 0.15, 0.15, 0.0);
		if (i != 3) {
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		}
	}

	/** The haymaker: 20, a launch (never a boss) and the 4-block burst for 10 on everyone else. */
	private static void haymaker(ServerPlayer p, LivingEntity t) {
		ServerLevel level = (ServerLevel) p.level();
		Vec3 at = t != null ? t.getBoundingBox().getCenter() : p.position().add(flatLook(p).scale(1.5)).add(0, 1.0, 0);
		if (t != null) {
			strike(p, t, SuperSoldierConfig.BARRAGE_HAYMAKER_DAMAGE, p.position(), SuperSoldierConfig.BARRAGE_HAYMAKER_KNOCKBACK,
					SuperSoldierConfig.BARRAGE_HAYMAKER_LIFT);
		}
		double r = SuperSoldierConfig.BARRAGE_BURST_RADIUS;
		for (LivingEntity e : targets(p, new AABB(at, at).inflate(r))) {
			if (e == t || AbilityHelpers.distanceSqToBox(e, at) > r * r) {
				continue;
			}
			strike(p, e, SuperSoldierConfig.BARRAGE_BURST_DAMAGE, at, 1.0, 0.35);
		}
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.55f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.7f, 1.45f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.0f, 1.1f);
		level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 3, 0.4, 0.4, 0.4, 0.0);
		level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 30, 0.4, 0.4, 0.4, 0.6);
		Vec3 feet = new Vec3(at.x, p.getY(), at.z);
		ring(level, feet, 0.2, r * 0.5, 20, blue(1.4f), red(1.4f));
		ring(level, feet, 0.2, r, 36, white(1.4f), blue(1.4f));
		groundDust(level, feet, 30, r * 0.35);
		QuirkCamera.shake(p, 0.4f, 9);
		QuirkCamera.fovPunch(p, 0.08f, 6);
	}

	// ---- with a shield: Shield Storm

	/**
	 * He leaps, hurls the shield (it ricochets between up to 8 enemies within 16 blocks for 15 each, a bright red / white / blue
	 * trail), hangs in the air until it is back, catches it, flips forward and drives the shield into the ground: a star-shaped
	 * shockwave for 20 at the centre (6 blocks, 60% at the edge) that knocks enemies up. No fall damage. No enemy within 16 = no
	 * cooldown.
	 */
	public static boolean shieldStorm(ServerPlayer p) {
		LivingEntity first = facing(p, SuperSoldierConfig.STORM_RANGE);
		if (first == null) {
			first = nearestHostile(p, SuperSoldierConfig.STORM_RANGE);
		}
		if (first == null) {
			SuperSoldier.say(p, "message.projecthero.super_soldier.no_target", ChatFormatting.GRAY);
			return false;
		}
		InteractionHand hand = shieldHand(p);
		boolean fromSlot = hand == null && CapShieldSlot.hasThrowable(p);
		if (hand == null && !fromSlot) {
			return false;
		}
		if (!begin(p, SHIELD_STORM, SuperSoldierConfig.ULTIMATE_COOLDOWN, SuperSoldierConfig.STORM_THROW_DELAY + 4)) {
			return false;
		}
		long now = p.level().getGameTime();
		edit(p, n -> n.noFallUntil = Math.max(n.noFallUntil,
				now + SuperSoldierConfig.STORM_MAX_HOVER_TICKS + SuperSoldierConfig.STORM_DIVE_MAX_TICKS + 60L));
		boolean off = hand == InteractionHand.OFF_HAND;
		SuperSoldier.anim(p, SuperSoldierState.ANIM_STORM_THROW, off);
		Vec3 f = flatLook(p).scale(0.15);
		AbilityHelpers.launchSelf(p, new Vec3(f.x, SuperSoldier.verticalSpeedForHeight(SuperSoldierConfig.STORM_LEAP_HEIGHT), f.z));
		AbilityHelpers.sound(p, SoundEvents.BREEZE_JUMP, 1.0f, 0.9f);
		AbilityHelpers.sound(p, SoundEvents.TRIDENT_RIPTIDE_3, 0.6f, 1.3f);
		ServerLevel level = (ServerLevel) p.level();
		level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 16, 0.5, 0.05, 0.5, 0.06);
		ring(level, p.position(), 0.15, 1.6, 16, blue(1.2f), red(1.2f));
		STORMS.put(p.getUUID(), new Storm(now, fromSlot, off));
		SuperSoldier.markCombat(p);
		return true;
	}

	/** The throw at the top of the leap. False if the shield is gone (put away mid-leap): the Storm ends there. */
	private static boolean stormThrow(ServerPlayer p, Storm st) {
		InteractionHand hand = shieldHand(p);
		boolean fromSlot = hand == null && CapShieldSlot.hasThrowable(p);
		if (hand == null && !fromSlot) {
			return false;
		}
		ItemStack held;
		if (fromSlot) {
			held = CapShieldSlot.takeForThrow(p);
			hand = InteractionHand.MAIN_HAND;
		} else {
			held = p.getItemInHand(hand);
			if (p.isUsingItem() && p.getUsedItemHand() == hand) {
				p.stopUsingItem();
			}
			p.setItemInHand(hand, ItemStack.EMPTY);
		}
		if (held.isEmpty()) {
			return false;
		}
		st.fromSlot = fromSlot;
		boolean adamantium = held.getItem() instanceof com.projecthero.mod.supersoldier.item.AdamantiumShieldItem;
		LivingEntity first = facing(p, SuperSoldierConfig.STORM_RANGE);
		if (first == null) {
			first = nearestHostile(p, SuperSoldierConfig.STORM_RANGE);
		}
		Vec3 from = p.getEyePosition().add(0, -0.2, 0).add(p.getLookAngle().scale(0.6));
		SoldierShieldEntity thrown = SoldierShieldEntity.launch(p, from, p.getLookAngle(), held, hand, adamantium, first);
		thrown.storm(SuperSoldierConfig.STORM_HIT_DAMAGE, SuperSoldierConfig.STORM_MAX_HITS, SuperSoldierConfig.STORM_RANGE,
				SuperSoldierConfig.STORM_SHIELD_SPEED);
		if (fromSlot) {
			thrown.returnToSlot();
		}
		st.shieldId = thrown.getId();
		st.phase = Storm.HOVER;
		st.phaseStart = p.level().getGameTime();
		AbilityHelpers.sound(p, SoundEvents.TRIDENT_THROW, 1.0f, 0.9f);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.6f);
		AbilityHelpers.sound(p, SoundEvents.RAID_HORN, 0.35f, 1.7f);
		ServerLevel level = (ServerLevel) p.level();
		level.sendParticles(ParticleTypes.FLASH, from.x, from.y, from.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, from.x, from.y, from.z, 14, 0.2, 0.2, 0.2, 0.4);
		QuirkCamera.shake(p, 0.15f, 4);
		return true;
	}

	/**
	 * v0.17.7: called by the thrown shield when it is handed back to him (caught, or delivered because it timed out). During a
	 * Shield Storm's hover: the catch -- a hop, a front flip, then the dive.
	 */
	public static void stormCaught(ServerPlayer p) {
		Storm st = STORMS.get(p.getUUID());
		if (st == null || st.phase != Storm.HOVER || !alive(p)) {
			return;
		}
		st.phase = Storm.DIVE;
		st.phaseStart = p.level().getGameTime();
		boolean off = shieldHand(p) == InteractionHand.OFF_HAND;
		SuperSoldier.anim(p, SuperSoldierState.ANIM_STORM_CATCH, off);
		AbilityHelpers.launchSelf(p, new Vec3(0.0, 0.35, 0.0));
		AbilityHelpers.sound(p, SoundEvents.ANVIL_LAND, 0.4f, 1.9f);
		AbilityHelpers.sound(p, SoundEvents.TRIDENT_RETURN, 1.0f, 0.9f);
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = p.position().add(0, 1.6, 0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 16, 0.3, 0.3, 0.3, 0.3);
		level.sendParticles(white(1.3f), c.x, c.y, c.z, 10, 0.3, 0.3, 0.3, 0.0);
	}

	private static boolean tickStorm(ServerPlayer p, Storm st) {
		if (!alive(p)) {
			return false;
		}
		long now = p.level().getGameTime();
		long age = now - st.phaseStart;
		switch (st.phase) {
			case Storm.RISE -> {
				if (age >= SuperSoldierConfig.STORM_THROW_DELAY && !stormThrow(p, st)) {
					SuperSoldier.anim(p, SuperSoldierState.ANIM_NONE, false);
					return false;
				}
			}
			case Storm.HOVER -> {
				// he hangs at the top of the leap while the shield does its work -- sinking slowly (faster than the 1/32 block a tick a
				// dedicated server's anti-fly check calls "floating", so a long ricochet chain never kicks him for flying)
				Vec3 v = p.getDeltaMovement();
				AbilityHelpers.launchSelf(p, new Vec3(v.x * 0.4, SuperSoldierConfig.STORM_HOVER_SINK, v.z * 0.4));
				Entity e = p.level().getEntity(st.shieldId);
				if (!(e instanceof SoldierShieldEntity shield) || shield.isRemoved()) {
					stormCaught(p); // gone without a catch call (should not happen): dive anyway
				} else if (age >= SuperSoldierConfig.STORM_MAX_HOVER_TICKS) {
					shield.recall(); // too long: handed straight back (calls stormCaught)
					if (st.phase == Storm.HOVER) {
						stormCaught(p);
					}
				}
			}
			default -> {
				if (age >= 2 && (p.onGround() || p.isInWater()) || age >= SuperSoldierConfig.STORM_DIVE_MAX_TICKS) {
					stormSlam(p);
					return false;
				}
				if (age >= 6) {
					// driving down with the shield
					AbilityHelpers.launchSelf(p, new Vec3(0.0, -SuperSoldierConfig.STORM_DIVE_SPEED, 0.0));
					ServerLevel level = (ServerLevel) p.level();
					level.sendParticles(white(1.0f), p.getX(), p.getY() + 1.0, p.getZ(), 3, 0.2, 0.4, 0.2, 0.0);
					level.sendParticles(blue(1.0f), p.getX(), p.getY() + 1.4, p.getZ(), 3, 0.2, 0.4, 0.2, 0.0);
				}
			}
		}
		return true;
	}

	/** The shield driven into the ground: the star shockwave. Public for tests (returns how many were hit). */
	public static int stormSlam(ServerPlayer p) {
		STORMS.remove(p.getUUID());
		boolean off = shieldHand(p) == InteractionHand.OFF_HAND;
		SuperSoldier.anim(p, SuperSoldierState.ANIM_STORM_SLAM, off);
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = p.position();
		int n = radial(p, c.add(0, 0.5, 0), SuperSoldierConfig.STORM_SLAM_RADIUS, SuperSoldierConfig.STORM_SLAM_DAMAGE,
				SuperSoldierConfig.STORM_SLAM_KNOCKBACK, SuperSoldierConfig.STORM_SLAM_LIFT);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.4f, 0.8f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6f, 1.2f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6f, 0.5f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 0.7f);
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.3, c.z, 3, 0.8, 0.1, 0.8, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 0.5, c.z, 30, 0.3, 0.2, 0.3, 0.35);
		groundDust(level, c, 50, SuperSoldierConfig.STORM_SLAM_RADIUS * 0.4);
		// the star: it grows out in three waves, the shield's colours
		float yaw = p.getYRot();
		for (int w = 0; w < 3; w++) {
			final double r = SuperSoldierConfig.STORM_SLAM_RADIUS * (w + 1) / 3.0;
			final int wave = w;
			Runnable draw = () -> star(level, c, yaw, r, wave);
			if (w == 0) {
				draw.run();
			} else {
				schedule(p, w * 2, draw);
			}
		}
		ring(level, c, 0.15, 1.2, 16, white(1.5f), white(1.5f));
		edit(p, s -> s.noFallUntil = Math.max(s.noFallUntil, p.level().getGameTime() + 20L));
		p.resetFallDistance();
		QuirkCamera.shake(p, 0.45f, 10);
		return n;
	}

	/** A flat five-point star outline of outer radius {@code r} around {@code c}, pointing where he faces. */
	private static void star(ServerLevel level, Vec3 c, float yaw, double r, int wave) {
		double inner = r * 0.42;
		double base = Math.toRadians(-yaw); // the first point straight ahead (look = (-sin yaw, cos yaw))
		Vec3[] pts = new Vec3[10];
		for (int i = 0; i < 10; i++) {
			double a = base + i * Math.PI / 5.0;
			double rr = i % 2 == 0 ? r : inner;
			pts[i] = new Vec3(c.x + Math.sin(a) * rr, c.y + 0.25, c.z + Math.cos(a) * rr);
		}
		DustParticleOptions col = wave == 0 ? white(1.6f) : wave == 1 ? red(1.6f) : blue(1.6f);
		for (int i = 0; i < 10; i++) {
			Vec3 a = pts[i];
			Vec3 b = pts[(i + 1) % 10];
			int steps = Math.max(2, (int) Math.ceil(a.distanceTo(b) * 3.0));
			for (int k = 0; k < steps; k++) {
				Vec3 q = a.lerp(b, k / (double) steps);
				level.sendParticles(col, q.x, q.y, q.z, 1, 0, 0.03, 0, 0);
			}
			if (i % 2 == 0) {
				level.sendParticles(ParticleTypes.CLOUD, a.x, a.y, a.z, 2, 0.1, 0.05, 0.1, 0.03);
			}
		}
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


	// ---------------------------------------------------------------- V: Counter Stance (v0.17.7)

	/**
	 * V: he braces for a second. The first melee hit in the window is negated and answered at once -- 10 damage, a knockback and a
	 * stagger (Slowness III + Weakness I, 1.5 s). Holding a shield (or with one slotted), projectiles that reach him in the window are
	 * sent straight back at the shooter.
	 */
	public static boolean counterStance(ServerPlayer p) {
		if (!begin(p, COUNTER, SuperSoldierConfig.COUNTER_COOLDOWN, 4)) {
			return false;
		}
		long now = p.level().getGameTime();
		edit(p, n -> n.counterUntil = now + SuperSoldierConfig.COUNTER_WINDOW);
		SuperSoldier.anim(p, SuperSoldierState.ANIM_COUNTER, shieldHand(p) == InteractionHand.OFF_HAND);
		AbilityHelpers.sound(p, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 0.9f, 1.3f);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_NODAMAGE, 0.8f, 0.7f);
		ServerLevel level = (ServerLevel) p.level();
		ring(level, p.position(), 0.1, 0.9, 12, blue(0.9f), white(0.9f));
		return true;
	}

	/**
	 * From {@link SuperSoldierDamage}'s ALLOW_DAMAGE: an incoming hit during the Counter Stance. True when it is negated (a melee hit
	 * from something in reach -- answered with the counter -- or, with a shield, a projectile -- sent back).
	 */
	public static boolean tryCounter(ServerPlayer p, DamageSource source) {
		if (!SuperSoldier.counterActive(p)) {
			return false;
		}
		if (source.getDirectEntity() instanceof Projectile proj) {
			if (!hasShield(p) || !source.is(DamageTypeTags.IS_PROJECTILE)) {
				return false;
			}
			if (proj.isAlive()) {
				reflect(p, proj);
			}
			return true;
		}
		if (!(source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO) || source.is(DamageTypes.PLAYER_ATTACK))) {
			return false;
		}
		if (!(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker || attacker == p
				|| AbilityHelpers.distanceSqToBox(attacker, p.position()) > SuperSoldierConfig.COUNTER_REACH * SuperSoldierConfig.COUNTER_REACH) {
			return false;
		}
		counterHit(p, attacker);
		return true;
	}

	/** The answer: the stance ends, a few frames of safety, the counter strike and the stagger. */
	private static void counterHit(ServerPlayer p, LivingEntity attacker) {
		long now = p.level().getGameTime();
		edit(p, n -> {
			n.counterUntil = 0L; // first, so a counter between two braced Super Soldiers cannot ping-pong
			n.iframeUntil = Math.max(n.iframeUntil, now + SuperSoldierConfig.COUNTER_AFTER_IFRAMES);
		});
		boolean shield = hasShield(p);
		SuperSoldier.anim(p, SuperSoldierState.ANIM_COUNTER_HIT, shieldHand(p) == InteractionHand.OFF_HAND);
		if (strike(p, attacker, SuperSoldierConfig.COUNTER_DAMAGE, p.position(), SuperSoldierConfig.COUNTER_KNOCKBACK,
				SuperSoldierConfig.COUNTER_LIFT)) {
			AbilityHelpers.applyControl(attacker, MobEffects.MOVEMENT_SLOWDOWN, SuperSoldierConfig.COUNTER_STAGGER_TICKS, 2);
			AbilityHelpers.applyControl(attacker, MobEffects.WEAKNESS, SuperSoldierConfig.COUNTER_STAGGER_TICKS, 0);
		}
		AbilityHelpers.sound(p, shield ? SoundEvents.SHIELD_BLOCK : SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, shield ? 0.9f : 0.8f);
		AbilityHelpers.sound(p, SoundEvents.ANVIL_LAND, 0.35f, 2.0f);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.9f);
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = p.position().add(0, 1.2, 0).add(attacker.getBoundingBox().getCenter().subtract(p.position().add(0, 1.2, 0)).scale(0.5));
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 16, 0.1, 0.1, 0.1, 0.45);
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 12, 0.2, 0.2, 0.2, 0.4);
		level.sendParticles(blue(1.2f), c.x, c.y, c.z, 8, 0.25, 0.25, 0.25, 0.0);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		QuirkCamera.shake(p, 0.2f, 4);
		SuperSoldier.markCombat(p);
	}

	/** A projectile sent back at whoever shot it (arrows too, never picked up again). */
	static void reflect(ServerPlayer p, Projectile proj) {
		Vec3 at = proj.position();
		Entity shooter = proj.getOwner();
		proj.deflect(ProjectileDeflection.REVERSE, p, p, true);
		proj.setOwner(p);
		if (shooter != null && shooter != p && shooter.isAlive() && !(proj instanceof ThrownTrident)) {
			Vec3 to = shooter.position().add(0, shooter.getBbHeight() * 0.6, 0).subtract(at);
			double speed = Math.max(0.9, proj.getDeltaMovement().length());
			if (to.lengthSqr() > 1.0e-4) {
				proj.setDeltaMovement(to.normalize().scale(speed));
				proj.hurtMarked = true;
			}
		}
		if (proj instanceof AbstractArrow arrow) {
			arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		}
		ServerLevel level = (ServerLevel) p.level();
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 1.3f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.3f, 2.0f);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 10, 0.05, 0.05, 0.05, 0.4);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.3);
	}

	/** Every tick of the stance with a shield: projectiles about to reach him are sent back before they land. */
	private static void counterReflectTick(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = p.position().add(0, p.getBbHeight() * 0.55, 0);
		double r = SuperSoldierConfig.COUNTER_REFLECT_REACH;
		for (Projectile proj : level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(r + 4.0), Entity::isAlive)) {
			if (proj.getOwner() == p) {
				continue;
			}
			Vec3 v = proj.getDeltaMovement();
			if (proj.position().distanceTo(c) > r + 0.6 && proj.position().add(v).distanceTo(c) > r + 0.6) {
				continue; // not within reach this tick or the next
			}
			if (v.lengthSqr() > 1.0e-4 && v.dot(c.subtract(proj.position())) <= 0.0) {
				continue; // moving away
			}
			reflect(p, proj);
		}
	}

	// ---------------------------------------------------------------- Shift+V: Assemble (v0.17.7)

	/**
	 * Shift+V: he plants his feet, raises a fist (or the shield) and calls "Assemble!". A gold / blue rally ring knocks enemies within
	 * 6 blocks back (no damage). For 10 s he and squadmates within 16 get Strength I, Speed I and Resistance I; hostiles within 16 get
	 * Weakness (10 s) and Slowness (6 s); every enemy within 30 glows (marked) for 10 s. While it runs his hits on the marked deal
	 * x1.25 and every punch shocks enemies within 3 blocks of the target for 6. Nothing over 20.
	 */
	public static boolean assemble(ServerPlayer p) {
		if (!begin(p, ASSEMBLE, SuperSoldierConfig.ASSEMBLE_COOLDOWN, 10)) {
			return false;
		}
		long now = p.level().getGameTime();
		int dur = SuperSoldierConfig.ASSEMBLE_DURATION;
		edit(p, n -> n.assembleUntil = now + dur);
		boolean off = shieldHand(p) == InteractionHand.OFF_HAND;
		SuperSoldier.anim(p, SuperSoldierState.ANIM_ASSEMBLE, off);
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = p.position();
		Component call = Component.translatable("message.projecthero.super_soldier.assemble").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
		// himself and every squad-mate in range
		for (Player other : level.getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(SuperSoldierConfig.ASSEMBLE_ALLY_RADIUS),
				o -> o.isAlive() && !o.isSpectator() && (o == p || com.projecthero.mod.squad.Squads.areAllies(p, o)))) {
			if (other.distanceToSqr(p) > SuperSoldierConfig.ASSEMBLE_ALLY_RADIUS * SuperSoldierConfig.ASSEMBLE_ALLY_RADIUS) {
				continue;
			}
			other.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, dur, 0, false, true, true));
			other.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, dur, 0, false, true, true));
			other.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, dur, 0, false, true, true));
			other.displayClientMessage(call, true);
			level.sendParticles(gold(1.1f), other.getX(), other.getY() + 1.0, other.getZ(), 10, 0.4, 0.6, 0.4, 0.0);
		}
		// enemies: knocked back by the ring, weakened and slowed, marked
		Set<Integer> marked = new HashSet<>();
		double ring = SuperSoldierConfig.ASSEMBLE_RING_RADIUS;
		double debuff = SuperSoldierConfig.ASSEMBLE_DEBUFF_RADIUS;
		double mark = SuperSoldierConfig.ASSEMBLE_MARK_RADIUS;
		for (LivingEntity e : targets(p, p.getBoundingBox().inflate(mark))) {
			if (!isHostile(p, e)) { // v0.14.20: rule 2
				continue;
			}
			double d2 = e.distanceToSqr(p);
			if (d2 > mark * mark) {
				continue;
			}
			e.addEffect(new MobEffectInstance(MobEffects.GLOWING, dur, 0, false, false, false));
			marked.add(e.getId());
			level.sendParticles(ParticleTypes.ENCHANTED_HIT, e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(), 4, 0.1, 0.1, 0.1, 0.0);
			if (d2 <= debuff * debuff) {
				AbilityHelpers.applyControl(e, MobEffects.WEAKNESS, dur, 0);
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, SuperSoldierConfig.ASSEMBLE_SLOW_TICKS, 0);
			}
			if (d2 <= ring * ring && !isBoss(e)) {
				AbilityHelpers.knockbackFrom(e, c, SuperSoldierConfig.ASSEMBLE_RING_KNOCKBACK);
				AbilityHelpers.push(e, new Vec3(0.0, SuperSoldierConfig.ASSEMBLE_RING_LIFT, 0.0));
			}
		}
		MARKS.put(p.getUUID(), marked);
		SuperSoldier.markCombat(p);
		AbilityHelpers.sound(p, SoundEvents.RAID_HORN, 0.45f, 1.55f);
		AbilityHelpers.sound(p, SoundEvents.BELL_BLOCK, 1.0f, 0.75f);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_LEVELUP, 0.5f, 1.4f);
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1.2, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 2.4, c.z, 24, 0.15, 0.6, 0.15, 0.08);
		level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.1, c.z, 14, 0.5, 0.05, 0.5, 0.06);
		groundDust(level, c, 24, 0.8);
		// the rally ring rolls out over three ticks, gold and blue
		for (int w = 0; w < 4; w++) {
			final double r = ring * (w + 1) / 4.0;
			final int pts = 16 + w * 10;
			Runnable draw = () -> {
				ring(level, c, 0.25, r, pts, gold(1.5f), blue(1.5f));
				ring(level, c, 0.9, r * 0.92, pts / 2, gold(1.0f), gold(1.0f));
			};
			if (w == 0) {
				draw.run();
			} else {
				schedule(p, w, draw);
			}
		}
		QuirkCamera.shake(p, 0.12f, 5);
		return true;
	}

	/** How many enemies the current Assemble has marked (tests / HUD). */
	public static int markCount(ServerPlayer p) {
		Set<Integer> m = MARKS.get(p.getUUID());
		return m == null ? 0 : m.size();
	}

	/** Assemble's melee shock: every enemy close to the one he just punched takes a jolt. */
	static void assembleShock(ServerPlayer p, LivingEntity punched) {
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = punched.position().add(0, punched.getBbHeight() * 0.5, 0);
		double r = SuperSoldierConfig.ASSEMBLE_SHOCK_RADIUS;
		AABB box = new AABB(c, c).inflate(r);
		for (LivingEntity e : targets(p, box)) {
			if (e == punched || AbilityHelpers.distanceSqToBox(e, c) > r * r) {
				continue;
			}
			if (strike(p, e, SuperSoldierConfig.ASSEMBLE_SHOCK_DAMAGE, c, 0.6, 0.1)) {
				AbilityHelpers.line(level, c, e.position().add(0, e.getBbHeight() * 0.5, 0), ParticleTypes.ELECTRIC_SPARK, 3.0);
			}
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 10, 0.4, 0.4, 0.4, 0.2);
		level.sendParticles(gold(1.1f), c.x, c.y, c.z, 6, 0.5, 0.5, 0.5, 0.0);
		level.sendParticles(blue(1.2f), c.x, c.y, c.z, 6, 0.5, 0.5, 0.5, 0.0);
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
		Kick kick = KICKS.get(id);
		if (kick != null && now > kick.start && !tickKick(p, kick)) {
			KICKS.remove(id);
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
		// v0.17.7: the ultimates and the Counter Stance's shield
		Barrage b = BARRAGES.get(id);
		if (b != null && now > b.start && !tickBarrage(p, b)) {
			BARRAGES.remove(id);
		}
		Storm st = STORMS.get(id);
		if (st != null && now > st.start && STORMS.get(id) == st && !tickStorm(p, st)) {
			STORMS.remove(id);
		}
		if (SuperSoldier.counterActive(p) && hasShield(p)) {
			counterReflectTick(p);
		}
	}
}
