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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
 * shield; later in v0.17.7, user's list: X the Dash, Shift+X the Shoulder / Shield Bash, V the Spinning Roundhouse, Assemble buffs only). Every one is server-validated by {@link #begin}: power, alive, not locked by a running move, cooldown ready -- and only
 * then is the cooldown started. Multi-tick moves (the Combo Strike's punches, the Flying Kick's lunge, the Takedown's slam, the
 * Leaping Slam's landing, the Barrage, the Storm) run from small per-player tables ticked by {@link #tick}, and are dropped if the
 * player dies, logs out or loses the power ({@link #clear}; every table is emptied by {@code ServerStateReset}).
 *
 * <pre>
 *   R        Combo Strike         Shift+R  Uppercut Launcher
 *   G        Flying Kick          Shift+G  Judo Takedown
 *   Z        Leaping Slam         Shift+Z  ULTIMATE: Peak Human Barrage (no shield) / Shield Storm (a shield held or slotted)
 *   X        Dash                 Shift+X  Shoulder Bash (no shield) / Shield Bash (a shield held or slotted)
 *   V        Spinning Roundhouse  Shift+V  Assemble
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
	/** v0.17.7 (user's list): X, the Dash (was the Tactical Roll). */
	public static final String DASH = "dash";
	/** v0.17.7: Shift+X -- the Shoulder Bash bare-handed, the Shield Bash with a shield (was the High Leap); one cooldown, {@link #BASH}. */
	public static final String SHOULDER_BASH = "shoulder_bash";
	public static final String SHIELD_BASH = "shield_bash";
	/** The cooldown key both bashes write and read. */
	public static final String BASH = "bash";
	/** v0.17.7: V, the Spinning Roundhouse (replaced the Counter Stance). */
	public static final String ROUNDHOUSE = "spinning_roundhouse";
	/** v0.17.7: Shift+V (Battle Cry + Tactical Focus + the Onslaught buff). */
	public static final String ASSEMBLE = "assemble";

	/** The Guidebook / power-info rows: key label, ability id. */
	public static final String[][] GUIDE_ROWS = {
			{ "R", COMBO }, { "Shift+R", UPPERCUT },
			{ "G", FLYING_KICK }, { "Shift+G", TAKEDOWN },
			{ "Z", SLAM }, { "Shift+Z", BARRAGE }, { "Shift+Z (shield)", SHIELD_STORM },
			{ "X", DASH }, { "Shift+X", SHOULDER_BASH }, { "Shift+X (shield)", SHIELD_BASH },
			{ "V", ROUNDHOUSE }, { "Shift+V", ASSEMBLE },
			{ "C", SHIELD_THROW },
			{ "N", "shield_slot" } }; // v0.17.5: only in the Captain America Battle Suit top

	/** The cooldown key an ability id writes: both ultimates share {@link #ULTIMATE}, both bashes {@link #BASH}. */
	public static String cooldownKey(String id) {
		if (SHOULDER_BASH.equals(id) || SHIELD_BASH.equals(id)) {
			return BASH;
		}
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

	/** v0.17.7: a Dash or a Bash carrying him -- its line (fixed at the start), timing, and the entity ids it has already hit. */
	private static final class Charge {
		final Vec3 dir;
		final long start;
		final int ticks;
		final double speed;
		final boolean bash;
		final boolean shield;
		final Set<Integer> hit = new HashSet<>();

		Charge(Vec3 dir, long start, int ticks, double speed, boolean bash, boolean shield) {
			this.dir = dir;
			this.start = start;
			this.ticks = ticks;
			this.speed = speed;
			this.bash = bash;
			this.shield = shield;
		}
	}

	private static final Map<UUID, List<Task>> TASKS = new HashMap<>();
	private static final Map<UUID, Kick> KICKS = new HashMap<>();
	/** Leaping Slam in the air: game time of take-off. */
	private static final Map<UUID, Long> SLAMS = new HashMap<>();
	/** v0.17.7: the ultimates in progress (entity ids only -- no entities or worlds held). */
	private static final Map<UUID, Barrage> BARRAGES = new HashMap<>();
	private static final Map<UUID, Storm> STORMS = new HashMap<>();
	/** v0.17.7: the Dash / Bash in progress (entity ids only). */
	private static final Map<UUID, Charge> CHARGES = new HashMap<>();
	/** Set while a move is applying its damage, so the melee hooks do not treat it as a punch. */
	private static final ThreadLocal<Boolean> ABILITY_HIT = ThreadLocal.withInitial(() -> false);

	private SuperSoldierAbilities() {
	}

	public static void clearSessionState() {
		TASKS.clear();
		KICKS.clear();
		SLAMS.clear();
		BARRAGES.clear();
		STORMS.clear();
		CHARGES.clear();
	}

	public static void clear(UUID id) {
		TASKS.remove(id);
		KICKS.remove(id);
		SLAMS.remove(id);
		BARRAGES.remove(id);
		STORMS.remove(id);
		CHARGES.remove(id);
	}

	public static boolean isAbilityHit() {
		return ABILITY_HIT.get();
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
	 * One move hitting one target: damage (at most {@link SuperSoldierConfig#MAX_HIT}, boss-capped) and knockback away from {@code origin} plus {@code lift} (never on a boss). True if it landed.
	 */
	public static boolean strike(ServerPlayer p, LivingEntity target, float baseDamage, Vec3 origin, double knockback, double lift) {
		if (!canTarget(p, target)) {
			return false;
		}
		float dmg = Math.min(baseDamage, SuperSoldierConfig.MAX_HIT); // v0.17.7: Street tier ceiling
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
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0.3);
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
				strike(p, t, comboDamage(hit),
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

	/** v0.17.7: the three punches -- 5, 7, 10 (Black Panther's unsuited Warrior's Combo). */
	public static float comboDamage(int hit) {
		return hit == 0 ? SuperSoldierConfig.COMBO_HIT_1_DAMAGE : hit == 1 ? SuperSoldierConfig.COMBO_HIT_2_DAMAGE : SuperSoldierConfig.COMBO_FINISHER_DAMAGE;
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

	/** A lunging flying kick into the enemy in front (up to 8 blocks away): 12 damage (v0.17.7), a hard knockback. */
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
	 * 15 damage (v0.17.7) and a short stun (Slowness IV + Weakness). Bosses are too big to throw -- they just take the (capped) hit.
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
	 * Throws the shield in his hand where he aims (v0.17.7: light aim assist, bounces only to enemies within 6 blocks of the
	 * one hit). Adamantium Shield: 9 a hit, up to 5 bounces, 25 blocks; Vibranium: 10, 5 bounces, 30; any other shield: 8,
	 * 2 bounces, 25. The stack leaves his hand while it flies and the
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
		// v0.17.7: no lock-on -- it flies where he aims, with a light aim assist (SoldierShieldEntity.assistTarget)
		SoldierShieldEntity thrown = SoldierShieldEntity.launch(p, from, p.getLookAngle(), held, hand, adamantium);
		if (fromSlot) {
			thrown.returnToSlot();
		}
		if (vibranium) {
			thrown.upgrade(com.projecthero.mod.ultron.contagion.item.VibraniumShieldItem.THROW_DAMAGE,
					com.projecthero.mod.ultron.contagion.item.VibraniumShieldItem.THROW_BOUNCES,
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
	 * He leaps, hurls the shield where he aims (v0.17.7: the C rules -- light aim assist, up to 5 bounces to enemies within 6
	 * blocks of the one hit, 15 each, a bright red / white / blue trail), hangs in the air until it is back, catches it, flips forward and drives the shield into the ground: a star-shaped
	 * shockwave for 20 at the centre (6 blocks, 60% at the edge) that knocks enemies up. No fall damage. No enemy within 16 = no
	 * cooldown.
	 */
	public static boolean shieldStorm(ServerPlayer p) {
		// the gate only (an enemy in front or anywhere within 16); the throw itself flies where he aims (v0.17.7)
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
		Vec3 from = p.getEyePosition().add(0, -0.2, 0).add(p.getLookAngle().scale(0.6));
		// v0.17.7: the same aim / assist / bounce rules as C (it used to lock onto the nearest hostile and ricochet 8 times within 16)
		SoldierShieldEntity thrown = SoldierShieldEntity.launch(p, from, p.getLookAngle(), held, hand, adamantium);
		thrown.storm(SuperSoldierConfig.STORM_HIT_DAMAGE, SuperSoldierConfig.STORM_MAX_BOUNCES, SuperSoldierConfig.STORM_RANGE,
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

	// ---------------------------------------------------------------- X: Dash / Shift+X: Shoulder Bash, Shield Bash (v0.17.7)

	/**
	 * The way a Dash or a Bash goes: his full look, pitch included. Standing on the ground and looking down, it runs level instead (it
	 * would only drive him into the floor); looking straight down on the ground, his facing.
	 */
	public static Vec3 dashDirection(Player p) {
		Vec3 look = p.getLookAngle().normalize();
		if (p.onGround() && look.y < 0.0) {
			return flatLook(p);
		}
		return look;
	}

	/** X: about 8 blocks along his aim in a quarter of a second. Stops at a wall; no fall damage from it. */
	public static boolean dash(ServerPlayer p) {
		if (!begin(p, DASH, SuperSoldierConfig.DASH_COOLDOWN, SuperSoldierConfig.DASH_TICKS)) {
			return false;
		}
		long now = p.level().getGameTime();
		edit(p, n -> n.noFallUntil = Math.max(n.noFallUntil, now + SuperSoldierConfig.DASH_NO_FALL_TICKS));
		SuperSoldier.anim(p, SuperSoldierState.ANIM_DASH, false);
		AbilityHelpers.sound(p, SoundEvents.BREEZE_JUMP, 0.7f, 1.6f);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.5f, 1.7f);
		((ServerLevel) p.level()).sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 6, 0.25, 0.05, 0.25, 0.03);
		startCharge(p, new Charge(dashDirection(p), now, SuperSoldierConfig.DASH_TICKS, SuperSoldierConfig.DASH_SPEED, false, false));
		return true;
	}

	/**
	 * Shift+X: a charge along his aim (about 8 blocks). Bare-handed it is the Shoulder Bash (10), with a shield (either hand or slotted
	 * -- the {@link #hasShield} rule the whole kit uses) the Shield Bash (14). Everything along the path is hit once and knocked along the
	 * charge; nothing can hurt him for the charge plus 2 ticks. One cooldown for both.
	 */
	public static boolean bash(ServerPlayer p) {
		boolean shield = hasShield(p);
		if (!begin(p, shield ? SHIELD_BASH : SHOULDER_BASH, SuperSoldierConfig.BASH_COOLDOWN, SuperSoldierConfig.BASH_TICKS)) {
			return false;
		}
		long now = p.level().getGameTime();
		edit(p, n -> {
			n.iframeUntil = Math.max(n.iframeUntil, now + SuperSoldierConfig.BASH_TICKS + SuperSoldierConfig.BASH_IFRAME_EXTRA);
			n.noFallUntil = Math.max(n.noFallUntil, now + SuperSoldierConfig.DASH_NO_FALL_TICKS);
		});
		// the shield arm leads the Shield Bash (a slotted shield: the off arm, where the suit's guard raises it)
		boolean off = shield && shieldHand(p) != InteractionHand.MAIN_HAND;
		SuperSoldier.anim(p, shield ? SuperSoldierState.ANIM_SHIELD_BASH : SuperSoldierState.ANIM_SHOULDER_BASH, off);
		if (shield) {
			AbilityHelpers.sound(p, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.0f, 0.8f);
		}
		AbilityHelpers.sound(p, SoundEvents.BREEZE_JUMP, 0.9f, 0.9f);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 0.6f);
		QuirkCamera.fovPunch(p, 0.08f, 5);
		((ServerLevel) p.level()).sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 10, 0.3, 0.05, 0.3, 0.05);
		startCharge(p, new Charge(dashDirection(p), now, SuperSoldierConfig.BASH_TICKS, SuperSoldierConfig.BASH_SPEED, true, shield));
		SuperSoldier.markCombat(p);
		return true;
	}

	private static void startCharge(ServerPlayer p, Charge c) {
		CHARGES.put(p.getUUID(), c);
		if (!tickCharge(p, c)) {
			CHARGES.remove(p.getUUID());
		}
	}

	/** True while a Dash or a Bash is carrying him (tests). */
	public static boolean charging(ServerPlayer p) {
		return CHARGES.containsKey(p.getUUID());
	}

	/** True while he cannot be hurt (the Bash's frames). */
	public static boolean invulnerable(ServerPlayer p) {
		return SuperSoldier.state(p).iframeUntil > p.level().getGameTime();
	}

	/** One tick of a Dash / Bash: the step along its line (level along the floor, stopped by a wall). False once it is over. */
	private static boolean tickCharge(ServerPlayer p, Charge c) {
		if (!alive(p)) {
			return false;
		}
		ServerLevel level = (ServerLevel) p.level();
		long age = level.getGameTime() - c.start;
		if (age >= c.ticks) {
			endCharge(p);
			return false;
		}
		Vec3 step = c.dir.scale(c.speed);
		AABB box = p.getBoundingBox().deflate(1.0e-3);
		if (!level.noCollision(p, box.move(step))) {
			Vec3 flat = new Vec3(step.x, 0.0, step.z);
			if (flat.lengthSqr() > 1.0e-4 && level.noCollision(p, box.move(flat.normalize().scale(c.speed)))) {
				step = flat.normalize().scale(c.speed); // skimming a floor or a ceiling: carry on level
			} else {
				// a wall: stop here (the move itself never pushes him into a block -- vanilla collision does the rest)
				if (c.bash) {
					bashHits(p, c, Vec3.ZERO);
				}
				endCharge(p);
				return false;
			}
		}
		if (c.bash) {
			bashHits(p, c, step);
		}
		AbilityHelpers.launchSelf(p, step);
		// the trail: subtle for the Dash, red / white / blue for the Bash
		Vec3 at = p.position().add(0, p.getBbHeight() * 0.5, 0);
		if (c.bash) {
			level.sendParticles(age % 2 == 0 ? red(1.1f) : white(1.1f), at.x, at.y, at.z, 3, 0.2, 0.35, 0.2, 0.0);
			level.sendParticles(blue(1.2f), at.x, at.y, at.z, 3, 0.2, 0.35, 0.2, 0.0);
			level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.2, p.getZ(), 2, 0.15, 0.05, 0.15, 0.02);
		} else {
			level.sendParticles(blue(0.8f), at.x, at.y, at.z, 2, 0.15, 0.3, 0.15, 0.0);
			level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.15, p.getZ(), 1, 0.1, 0.05, 0.1, 0.01);
		}
		return true;
	}

	/** The charge is over: most of the speed comes off (no long slide, no shooting on upward). */
	private static void endCharge(ServerPlayer p) {
		Vec3 v = p.getDeltaMovement();
		AbilityHelpers.launchSelf(p, new Vec3(v.x * 0.3, Math.min(v.y, 0.15), v.z * 0.3));
	}

	/** Everything near his body and the step he is about to take (swept), hit once each and knocked along the charge. */
	private static void bashHits(ServerPlayer p, Charge c, Vec3 step) {
		double r = SuperSoldierConfig.BASH_HIT_RADIUS;
		AABB body = p.getBoundingBox();
		AABB sweep = body.minmax(body.move(step)).inflate(r - 0.3);
		Vec3 flatDir = new Vec3(c.dir.x, 0.0, c.dir.z);
		flatDir = flatDir.lengthSqr() < 1.0e-4 ? flatLook(p) : flatDir.normalize();
		float damage = c.shield ? SuperSoldierConfig.SHIELD_BASH_DAMAGE : SuperSoldierConfig.SHOULDER_BASH_DAMAGE;
		ServerLevel level = (ServerLevel) p.level();
		for (LivingEntity e : targets(p, sweep)) {
			if (!c.hit.add(e.getId())) {
				continue;
			}
			// knocked along the charge: the knockback's origin sits behind the target on the charge's line
			Vec3 origin = e.position().subtract(flatDir);
			if (!strike(p, e, damage, origin, SuperSoldierConfig.BASH_KNOCKBACK, SuperSoldierConfig.BASH_LIFT)) {
				continue;
			}
			Vec3 hit = e.position().add(0, e.getBbHeight() * 0.55, 0).subtract(flatDir.scale(e.getBbWidth() * 0.5));
			if (c.shield) {
				level.playSound(null, hit.x, hit.y, hit.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 0.75f);
				level.playSound(null, hit.x, hit.y, hit.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35f, 1.7f);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hit.x, hit.y, hit.z, 14, 0.15, 0.15, 0.15, 0.4);
			} else {
				level.playSound(null, hit.x, hit.y, hit.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0f, 0.7f);
				level.playSound(null, hit.x, hit.y, hit.z, SoundEvents.IRON_GOLEM_ATTACK, SoundSource.PLAYERS, 0.7f, 1.0f);
				level.sendParticles(ParticleTypes.CLOUD, hit.x, hit.y, hit.z, 6, 0.2, 0.2, 0.2, 0.05);
			}
			level.sendParticles(ParticleTypes.EXPLOSION, hit.x, hit.y, hit.z, 1, 0, 0, 0, 0);
			level.sendParticles(blue(1.3f), hit.x, hit.y, hit.z, 10, 0.3, 0.3, 0.3, 0.0);
			QuirkCamera.shake(p, c.shield ? 0.22f : 0.16f, 4);
		}
	}

	// ---------------------------------------------------------------- V: Spinning Roundhouse (v0.17.7; replaced the Counter Stance)

	/**
	 * V: he chambers the kick as he turns, spins a full circle and lands a roundhouse a quarter-second later: everything in a 150-degree
	 * arc in front of him within 3.5 blocks takes 11, is knocked away and dazed (Slowness II, 1 s). No target needed -- it is a sweep.
	 */
	public static boolean roundhouse(ServerPlayer p) {
		if (!begin(p, ROUNDHOUSE, SuperSoldierConfig.ROUNDHOUSE_COOLDOWN, SuperSoldierConfig.ROUNDHOUSE_WINDUP + 4)) {
			return false;
		}
		SuperSoldier.anim(p, SuperSoldierState.ANIM_ROUNDHOUSE, false);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 0.7f);
		AbilityHelpers.sound(p, SoundEvents.BREEZE_JUMP, 0.5f, 1.3f);
		schedule(p, SuperSoldierConfig.ROUNDHOUSE_WINDUP, () -> roundhouseKick(p));
		return true;
	}

	/** The kick lands (public for tests). Returns how many it hit. */
	public static int roundhouseKick(ServerPlayer p) {
		if (!alive(p)) {
			return 0;
		}
		ServerLevel level = (ServerLevel) p.level();
		Vec3 feet = p.position();
		Vec3 fwd = flatLook(p);
		double range = SuperSoldierConfig.ROUNDHOUSE_RANGE;
		double minDot = Math.cos(Math.toRadians(SuperSoldierConfig.ROUNDHOUSE_ARC_DEG * 0.5));
		int n = 0;
		for (LivingEntity e : targets(p, p.getBoundingBox().inflate(range, 1.5, range))) {
			if (AbilityHelpers.distanceSqToBox(e, feet) > range * range && AbilityHelpers.distanceSqToBox(e, p.getEyePosition()) > range * range) {
				continue;
			}
			Vec3 to = new Vec3(e.getX() - feet.x, 0.0, e.getZ() - feet.z);
			if (to.lengthSqr() > 0.36 && to.normalize().dot(fwd) < minDot) {
				continue; // behind or beside him (anything right against him is always caught)
			}
			if (strike(p, e, SuperSoldierConfig.ROUNDHOUSE_DAMAGE, feet, SuperSoldierConfig.ROUNDHOUSE_KNOCKBACK, SuperSoldierConfig.ROUNDHOUSE_LIFT)) {
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, SuperSoldierConfig.ROUNDHOUSE_DAZE_TICKS, 1);
				Vec3 c = e.position().add(0, e.getBbHeight() * 0.6, 0);
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
				level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 10, 0.25, 0.25, 0.25, 0.35);
				n++;
			}
		}
		// the kick's arc: red / white / blue at hip height
		for (int i = 0; i <= 12; i++) {
			double a = Math.toRadians(-SuperSoldierConfig.ROUNDHOUSE_ARC_DEG * 0.5 + SuperSoldierConfig.ROUNDHOUSE_ARC_DEG * i / 12.0);
			Vec3 d = fwd.yRot((float) a).scale(2.2);
			DustParticleOptions col = i % 3 == 0 ? red(1.2f) : i % 3 == 1 ? white(1.2f) : blue(1.2f);
			level.sendParticles(col, feet.x + d.x, feet.y + 1.0, feet.z + d.z, 1, 0.03, 0.03, 0.03, 0.0);
		}
		AbilityHelpers.sound(p, n > 0 ? SoundEvents.PLAYER_ATTACK_KNOCKBACK : SoundEvents.PLAYER_ATTACK_NODAMAGE, 1.0f, 0.8f);
		if (n > 0) {
			AbilityHelpers.sound(p, SoundEvents.IRON_GOLEM_ATTACK, 0.8f, 1.15f);
			QuirkCamera.shake(p, 0.15f, 4);
			SuperSoldier.markCombat(p);
		}
		return n;
	}

	// ---------------------------------------------------------------- fall damage: landing on the shield (v0.17.7)

	/**
	 * v0.17.7 (user: "if player is blocking with shield and looking at ground then they dont take fall damage"): blocking with a shield
	 * -- a raised shield in either hand (vanilla's use) or the Battle Suit slot's guard -- and looking at least 60 degrees down.
	 */
	public static boolean shieldLanding(ServerPlayer p) {
		boolean blocking = (p.isUsingItem() && p.getUseItem().getItem() instanceof ShieldItem) || CapShieldSlot.isGuarding(p);
		return blocking && p.getXRot() >= SuperSoldierConfig.SHIELD_LANDING_MIN_PITCH;
	}

	/** The shield takes the landing: a heavy thud, sparks and the ground's dust. */
	public static void shieldLandingFx(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = p.position();
		level.playSound(null, c.x, c.y, c.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 0.6f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.3f, 1.4f);
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y + 0.1, c.z, 12, 0.4, 0.05, 0.4, 0.25);
		level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.1, c.z, 8, 0.4, 0.05, 0.4, 0.04);
		groundDust(level, c, 16, 0.5);
	}

	// ---------------------------------------------------------------- Shift+V: Assemble (v0.17.7)

	/**
	 * Shift+V: he plants his feet, raises a fist (or the shield) and calls "Assemble!". For 50 s he and every squadmate within 24 blocks
	 * get Strength II, Resistance II, Regeneration II and Speed II. 100 s cooldown from the call. (The gold / blue rally ring is only a
	 * visual now: no knockback, debuffs or marks.)
	 */
	public static boolean assemble(ServerPlayer p) {
		if (!begin(p, ASSEMBLE, SuperSoldierConfig.ASSEMBLE_COOLDOWN, 10)) {
			return false;
		}
		long now = p.level().getGameTime();
		int dur = SuperSoldierConfig.ASSEMBLE_DURATION;
		int amp = SuperSoldierConfig.ASSEMBLE_AMPLIFIER;
		edit(p, n -> n.assembleUntil = now + dur);
		boolean off = shieldHand(p) == InteractionHand.OFF_HAND;
		SuperSoldier.anim(p, SuperSoldierState.ANIM_ASSEMBLE, off);
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = p.position();
		Component call = Component.translatable("message.projecthero.super_soldier.assemble").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
		double r = SuperSoldierConfig.ASSEMBLE_ALLY_RADIUS;
		for (Player other : level.getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(r),
				o -> o.isAlive() && !o.isSpectator() && (o == p || com.projecthero.mod.squad.Squads.areAllies(p, o)))) {
			if (other.distanceToSqr(p) > r * r) {
				continue;
			}
			other.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, dur, amp, false, true, true));
			other.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, dur, amp, false, true, true));
			other.addEffect(new MobEffectInstance(MobEffects.REGENERATION, dur, amp, false, true, true));
			other.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, dur, amp, false, true, true));
			other.displayClientMessage(call, true);
			level.sendParticles(gold(1.1f), other.getX(), other.getY() + 1.0, other.getZ(), 10, 0.4, 0.6, 0.4, 0.0);
		}
		SuperSoldier.markCombat(p);
		AbilityHelpers.sound(p, SoundEvents.RAID_HORN, 0.45f, 1.55f);
		AbilityHelpers.sound(p, SoundEvents.BELL_BLOCK, 1.0f, 0.75f);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_LEVELUP, 0.5f, 1.4f);
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1.2, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 2.4, c.z, 24, 0.15, 0.6, 0.15, 0.08);
		level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.1, c.z, 14, 0.5, 0.05, 0.5, 0.06);
		groundDust(level, c, 24, 0.8);
		// the rally ring rolls out over three ticks, gold and blue (a visual only)
		double ring = SuperSoldierConfig.ASSEMBLE_RING_RADIUS;
		for (int w = 0; w < 4; w++) {
			final double rr = ring * (w + 1) / 4.0;
			final int pts = 16 + w * 10;
			Runnable draw = () -> {
				ring(level, c, 0.25, rr, pts, gold(1.5f), blue(1.5f));
				ring(level, c, 0.9, rr * 0.92, pts / 2, gold(1.0f), gold(1.0f));
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
		// v0.17.7: the ultimates, the Dash and the Bash
		Barrage b = BARRAGES.get(id);
		if (b != null && now > b.start && !tickBarrage(p, b)) {
			BARRAGES.remove(id);
		}
		Storm st = STORMS.get(id);
		if (st != null && now > st.start && STORMS.get(id) == st && !tickStorm(p, st)) {
			STORMS.remove(id);
		}
		Charge ch = CHARGES.get(id);
		if (ch != null && now > ch.start && CHARGES.get(id) == ch && !tickCharge(p, ch)) {
			CHARGES.remove(id);
		}
	}
}
