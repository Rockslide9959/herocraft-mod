package com.projecthero.mod.hulk.gladiator;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hulk.Hulk;
import com.projecthero.mod.hulk.HulkAbilities;
import com.projecthero.mod.hulk.HulkCombat;
import com.projecthero.mod.hulk.HulkConfig;
import com.projecthero.mod.hulk.HulkGrab;
import com.projecthero.mod.hulk.data.HulkState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.15.3: the Gladiator Hulk -- the Hulk wearing the full gladiator gear ({@link GladiatorGear#isGladiator}) swaps the
 * bare-handed kit for twelve weapon moves (axe in the left hand, hammer in the right). Tap the key or hold Shift:
 *
 * <pre>
 *   R  Axe Cleave        -- wide sweep in front, heavy knockback, bleeds   | Shift+R Hammer Uppercut -- one target launched high
 *   G  Hammer Quake      -- overhead slam, a forward cone fissure          | Shift+G Earthsplitter   -- a line of erupting earth
 *   Z  Champion's Roar   -- buff (damage + knockback resistance), mobs flee, rage | Shift+Z Weapon Clash -- ringing stun
 *   X  Arena Leap        -- big leap, weapons-first crater slam            | Shift+X Meteor Dive     -- straight up, guided dive
 *   C  Axe Throw         -- out and back                                   | Shift+C Hammer Hurl     -- arc, shockwave, stuck; C recalls
 *   V  Whirlwind         -- 3 s spin, pulls in, ends in a slam             | Shift+V Arena Grapple   -- pin and pound; Shift+V throws
 * </pre>
 *
 * Rage, control / rampage, the death save, Calm, riding and the passives are the Hulk's own and unchanged. Everything
 * hits through {@link HulkCombat} (the Hulk's targeting: squads, PvP, {@code HeroTargets}, bosses never shoved), lands on
 * the animation's impact frame through {@link HulkAbilities#schedule}, and keeps its cooldown in the synced
 * {@link HulkState#abilityReadyAt} map. Numbers: {@link HulkConfig#gladiator()}.
 */
public final class GladiatorAbilities {
	public static final String AXE_CLEAVE = "g_axe_cleave";
	public static final String HAMMER_UPPERCUT = "g_hammer_uppercut";
	public static final String HAMMER_QUAKE = "g_hammer_quake";
	public static final String EARTHSPLITTER = "g_earthsplitter";
	public static final String CHAMPIONS_ROAR = "g_champions_roar";
	public static final String WEAPON_CLASH = "g_weapon_clash";
	public static final String ARENA_LEAP = "g_arena_leap";
	public static final String METEOR_DIVE = "g_meteor_dive";
	public static final String AXE_THROW = "g_axe_throw";
	public static final String HAMMER_HURL = "g_hammer_hurl";
	public static final String WHIRLWIND = "g_whirlwind";
	public static final String ARENA_GRAPPLE = "g_arena_grapple";

	/** Ticks from the key press to the hit (match the animations). */
	public static final int CLEAVE_IMPACT_TICKS = 6;
	public static final int UPPERCUT_IMPACT_TICKS = 6;
	public static final int QUAKE_IMPACT_TICKS = 10;
	public static final int SPLITTER_IMPACT_TICKS = 10;
	public static final int ROAR_IMPACT_TICKS = 6;
	public static final int CLASH_IMPACT_TICKS = 8;
	public static final int AXE_RELEASE_TICKS = 5;
	public static final int HAMMER_RELEASE_TICKS = 7;
	/** A thrown weapon still away after this long is simply back (its entity was lost to an unload, say). */
	public static final int AXE_AWAY_LIMIT_TICKS = 200;

	public static final DustParticleOptions BRONZE = new DustParticleOptions(new Vector3f(0.85f, 0.6f, 0.25f), 1.4f);
	private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.6f, 0.05f, 0.05f), 1.0f);

	private static final ResourceLocation ROAR_ATTACK_ID = PowerToggles.id("gladiator_roar_attack");
	private static final ResourceLocation ROAR_KNOCKBACK_ID = PowerToggles.id("gladiator_roar_knockback");

	private record Away(int entityId, long since) {
	}

	private record Whirl(long until, HulkCombat.Hit slam) {
	}

	private static final int LEAP_ARENA = 0;
	private static final int LEAP_RISE = 1;
	private static final int LEAP_DIVE = 2;

	private static final class Leap {
		int mode;
		final long takeoff;
		long phaseAt;

		Leap(int mode, long takeoff) {
			this.mode = mode;
			this.takeoff = takeoff;
			this.phaseAt = takeoff;
		}
	}

	private static final class Grapple {
		final int entityId;
		int blows;
		long nextBlow;

		Grapple(int entityId, long nextBlow) {
			this.entityId = entityId;
			this.nextBlow = nextBlow;
		}
	}

	private static final Map<UUID, Away> AXE = new ConcurrentHashMap<>();
	private static final Map<UUID, Away> HAMMER = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> ROAR_UNTIL = new ConcurrentHashMap<>();
	private static final Map<UUID, Whirl> WHIRL = new ConcurrentHashMap<>();
	private static final Map<UUID, Leap> LEAP = new ConcurrentHashMap<>();
	private static final Map<UUID, Grapple> GRAPPLE = new ConcurrentHashMap<>();
	/** GameTests only: players treated as wearing the full kit, whatever they wear. */
	private static final Set<UUID> FORCED_KIT = ConcurrentHashMap.newKeySet();

	private GladiatorAbilities() {
	}

	/** ServerStateReset (through {@link Hulk#clearSessionState}). */
	public static void clearSessionState() {
		AXE.clear();
		HAMMER.clear();
		ROAR_UNTIL.clear();
		WHIRL.clear();
		LEAP.clear();
		GRAPPLE.clear();
		FORCED_KIT.clear();
	}

	/** GameTests: treat {@code player} as wearing (or not) the full gladiator kit, independent of the gear. */
	public static void forceKitForTests(Player player, boolean on) {
		if (on) {
			FORCED_KIT.add(player.getUUID());
		} else {
			FORCED_KIT.remove(player.getUUID());
		}
	}

	/** The Gladiator kit replaces the Hulk's keys: the Hulk with the full gear on (client-safe). */
	public static boolean active(Player player) {
		return Hulk.isHulk(player) && (FORCED_KIT.contains(player.getUUID()) || GladiatorGear.isGladiator(player));
	}

	// ---------------------------------------------------------------- keys

	/** The tap move on each key (R G X Z V C = slots 1-6). */
	public static String tapId(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> AXE_CLEAVE;
			case SLOT_2 -> HAMMER_QUAKE;
			case SLOT_3 -> ARENA_LEAP;
			case SLOT_4 -> CHAMPIONS_ROAR;
			case SLOT_5 -> WHIRLWIND;
			default -> AXE_THROW;
		};
	}

	/** The Shift+key move on each key. */
	public static String shiftId(AbilitySlot slot) {
		return switch (slot) {
			case SLOT_1 -> HAMMER_UPPERCUT;
			case SLOT_2 -> EARTHSPLITTER;
			case SLOT_3 -> METEOR_DIVE;
			case SLOT_4 -> WEAPON_CLASH;
			case SLOT_5 -> ARENA_GRAPPLE;
			default -> HAMMER_HURL;
		};
	}

	/** Lang name of a move id: {@code projecthero.hulk.gladiator.ability.<name>}. */
	public static String langName(String id) {
		return "projecthero.hulk.gladiator.ability." + id.substring(2);
	}

	public static int maxCooldown(String id) {
		HulkConfig.Gladiator c = HulkConfig.gladiator();
		return switch (id) {
			case AXE_CLEAVE -> c.axeCleaveCooldownTicks;
			case HAMMER_UPPERCUT -> c.uppercutCooldownTicks;
			case HAMMER_QUAKE -> c.quakeCooldownTicks;
			case EARTHSPLITTER -> c.earthsplitterCooldownTicks;
			case CHAMPIONS_ROAR -> c.roarCooldownTicks;
			case WEAPON_CLASH -> c.clashCooldownTicks;
			case ARENA_LEAP -> c.arenaLeapCooldownTicks;
			case METEOR_DIVE -> c.meteorCooldownTicks;
			case AXE_THROW -> c.axeThrowCooldownTicks;
			case HAMMER_HURL -> c.hammerHurlCooldownTicks;
			case WHIRLWIND -> c.whirlwindCooldownTicks;
			case ARENA_GRAPPLE -> c.grappleCooldownTicks;
			default -> 0;
		};
	}

	/** From {@code HulkAbilityManager}: the six keys while {@link #active}. Only presses do anything. */
	public static void handle(ServerPlayer player, AbilitySlot slot, boolean pressed) {
		if (!pressed) {
			return;
		}
		boolean shift = player.isShiftKeyDown();
		switch (slot) {
			case SLOT_1 -> {
				if (shift) {
					hammerUppercut(player);
				} else {
					axeCleave(player);
				}
			}
			case SLOT_2 -> {
				if (shift) {
					earthsplitter(player);
				} else {
					hammerQuake(player);
				}
			}
			case SLOT_3 -> {
				if (shift) {
					meteorDive(player);
				} else {
					arenaLeap(player);
				}
			}
			case SLOT_4 -> {
				if (shift) {
					weaponClash(player);
				} else {
					championsRoar(player);
				}
			}
			case SLOT_5 -> {
				if (shift) {
					arenaGrapple(player);
				} else {
					whirlwind(player);
				}
			}
			case SLOT_6 -> {
				// any C while the hammer is out calls it home
				if (HAMMER.containsKey(player.getUUID())) {
					recallHammer(player);
				} else if (shift) {
					hammerHurl(player);
				} else {
					axeThrow(player);
				}
			}
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- shared

	/** Spinning, pinning or in a gladiator leap: the other moves wait. */
	public static boolean busy(Player player) {
		UUID id = player.getUUID();
		return WHIRL.containsKey(id) || GRAPPLE.containsKey(id) || LEAP.containsKey(id);
	}

	public static boolean axeAway(Player player) {
		return AXE.containsKey(player.getUUID()) || GladiatorGear.weaponAway(player, true);
	}

	public static boolean hammerAway(Player player) {
		return HAMMER.containsKey(player.getUUID()) || GladiatorGear.weaponAway(player, false);
	}

	public static boolean whirling(Player player) {
		return WHIRL.containsKey(player.getUUID());
	}

	public static boolean grappling(Player player) {
		return GRAPPLE.containsKey(player.getUUID());
	}

	public static boolean roaring(Player player) {
		Long until = ROAR_UNTIL.get(player.getUUID());
		return until != null && until > player.level().getGameTime();
	}

	/** True if {@code e} is pinned by some Gladiator's Arena Grapple. */
	public static boolean isPinned(Entity e) {
		for (Grapple g : GRAPPLE.values()) {
			if (g.entityId == e.getId()) {
				return true;
			}
		}
		return false;
	}

	/** Can act, isn't busy, the move is off cooldown and the weapons it swings are in his hands. */
	private static boolean start(ServerPlayer player, String id, boolean axe, boolean hammer) {
		if (!active(player) || !HulkAbilities.canAct(player) || busy(player) || !HulkAbilities.ready(player, id)) {
			return false;
		}
		if (axe && axeAway(player)) {
			Hulk.say(player, "message.projecthero.hulk.gladiator.axe_away", ChatFormatting.GRAY);
			return false;
		}
		if (hammer && hammerAway(player)) {
			Hulk.say(player, "message.projecthero.hulk.gladiator.hammer_away", ChatFormatting.GRAY);
			return false;
		}
		return true;
	}

	private static boolean stillActive(ServerPlayer player) {
		return active(player) && player.isAlive();
	}

	private static ServerLevel level(ServerPlayer player) {
		return (ServerLevel) player.level();
	}

	private static Vec3 right(Vec3 fwd) {
		return new Vec3(-fwd.z, 0, fwd.x);
	}

	/** The ground block under a point (searching a few blocks up and down), or dirt. */
	private static BlockState groundAt(ServerLevel level, Vec3 p) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dy = 1; dy >= -3; dy--) {
			pos.set(p.x, p.y + dy - 1, p.z);
			BlockState s = level.getBlockState(pos);
			if (!s.isAir() && s.getFluidState().isEmpty()) {
				return s;
			}
		}
		return Blocks.DIRT.defaultBlockState();
	}

	// ---------------------------------------------------------------- R Axe Cleave

	public static void axeCleave(ServerPlayer player) {
		if (!start(player, AXE_CLEAVE, true, false)) {
			return;
		}
		HulkAbilities.cooldown(player, AXE_CLEAVE, HulkConfig.gladiator().axeCleaveCooldownTicks);
		HulkAbilities.anim(player, GladiatorAnims.AXE_CLEAVE);
		AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.6f);
		HulkAbilities.schedule(player, CLEAVE_IMPACT_TICKS, () -> axeCleaveImpact(player));
	}

	private static void axeCleaveImpact(ServerPlayer player) {
		if (!stillActive(player)) {
			return;
		}
		HulkConfig.Gladiator c = HulkConfig.gladiator();
		ServerLevel level = level(player);
		Vec3 fwd = HulkAbilities.flatLook(player);
		Vec3 at = player.position();
		double cosHalf = Math.cos(Math.toRadians(c.axeCleaveArcDegrees * 0.5));
		HulkCombat.Hit hit = new HulkCombat.Hit(c.axeCleaveDamage, c.axeCleaveKnockback, 0.3);
		AABB box = player.getBoundingBox().inflate(c.axeCleaveRange + 1.0, 1.0, c.axeCleaveRange + 1.0);
		for (LivingEntity e : HulkCombat.targets(player, box)) {
			Vec3 to = new Vec3(e.getX() - at.x, 0, e.getZ() - at.z);
			double dist = to.length() - e.getBbWidth() * 0.5;
			if (dist > c.axeCleaveRange || (to.lengthSqr() > 0.25 && to.normalize().dot(fwd) < cosHalf)) {
				continue;
			}
			if (HulkCombat.strike(player, e, at, hit, 1.0f)) {
				bleed(player, e, c.bleedDamage, c.bleedSeconds);
				Vec3 m = e.position().add(0, e.getBbHeight() * 0.6, 0);
				level.sendParticles(BLOOD, m.x, m.y, m.z, 8, 0.25, 0.3, 0.25, 0.0);
			}
		}
		// the arc of the blade: sweeps from his right round to his left
		Vec3 side = right(fwd);
		double half = Math.toRadians(c.axeCleaveArcDegrees * 0.5);
		double y = at.y + player.getBbHeight() * 0.45;
		for (int i = 0; i <= 12; i++) {
			double a = -half + 2.0 * half * i / 12.0;
			Vec3 dir = fwd.scale(Math.cos(a)).add(side.scale(Math.sin(a)));
			for (double r = 2.0; r <= c.axeCleaveRange; r += 1.75) {
				Vec3 p = at.add(dir.scale(r));
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, y, p.z, 1, 0, 0, 0, 0);
			}
			Vec3 tip = at.add(dir.scale(c.axeCleaveRange));
			level.sendParticles(ParticleTypes.CRIT, tip.x, y, tip.z, 2, 0.1, 0.2, 0.1, 0.1);
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.3f, 0.5f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.5f, 1.4f);
		HulkCombat.shake(level, at, 0.3f, 6, 14.0);
	}

	/** Bleed: {@code damage} a second for {@code seconds} seconds, through the Hulk's own task clock. */
	private static void bleed(ServerPlayer player, LivingEntity e, float damage, int seconds) {
		for (int i = 1; i <= seconds; i++) {
			HulkAbilities.schedule(player, 20 * i, () -> {
				if (!e.isAlive() || e.level() != player.level()) {
					return;
				}
				AbilityHelpers.hurtLands(player, e, damage);
				Vec3 m = e.position().add(0, e.getBbHeight() * 0.5, 0);
				level(player).sendParticles(BLOOD, m.x, m.y, m.z, 5, 0.2, 0.3, 0.2, 0.0);
			});
		}
	}

	// ---------------------------------------------------------------- Shift+R Hammer Uppercut

	public static void hammerUppercut(ServerPlayer player) {
		if (!start(player, HAMMER_UPPERCUT, false, true)) {
			return;
		}
		HulkAbilities.cooldown(player, HAMMER_UPPERCUT, HulkConfig.gladiator().uppercutCooldownTicks);
		HulkAbilities.anim(player, GladiatorAnims.HAMMER_UPPERCUT);
		AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.4f);
		HulkAbilities.schedule(player, UPPERCUT_IMPACT_TICKS, () -> hammerUppercutImpact(player));
	}

	/** The one target in front: what he looks at, else the nearest thing in a narrow cone. */
	private static LivingEntity frontTarget(ServerPlayer player, double range) {
		LivingEntity looked = AbilityHelpers.raycastEntity(player, range);
		if (looked != null && HulkCombat.targets(player, looked.getBoundingBox().inflate(0.1)).contains(looked)) {
			return looked;
		}
		Vec3 fwd = HulkAbilities.flatLook(player);
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : HulkCombat.targets(player, player.getBoundingBox().inflate(range, 2.0, range))) {
			Vec3 to = new Vec3(e.getX() - player.getX(), 0, e.getZ() - player.getZ());
			double d = to.length() - e.getBbWidth() * 0.5;
			if (d > range || (to.lengthSqr() > 0.25 && to.normalize().dot(fwd) < 0.6)) {
				continue;
			}
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	private static void hammerUppercutImpact(ServerPlayer player) {
		if (!stillActive(player)) {
			return;
		}
		HulkConfig.Gladiator c = HulkConfig.gladiator();
		ServerLevel level = level(player);
		LivingEntity target = frontTarget(player, c.uppercutRange);
		Vec3 fwd = HulkAbilities.flatLook(player);
		Vec3 swing = player.position().add(fwd.scale(1.8)).add(0, player.getBbHeight() * 0.5, 0);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, swing.x, swing.y, swing.z, 1, 0, 0, 0, 0);
		if (target == null) {
			return;
		}
		HulkCombat.Hit hit = new HulkCombat.Hit(c.uppercutDamage, 0.0, 0.0);
		if (HulkCombat.strike(player, target, player.position(), hit, 1.0f)
				&& !com.projecthero.mod.titanshifter.TitanCombat.isBoss(target)) {
			target.setDeltaMovement(fwd.x * 0.25, c.uppercutLaunch, fwd.z * 0.25);
			target.hurtMarked = true;
			target.hasImpulse = true;
		}
		Vec3 m = target.position().add(0, target.getBbHeight() * 0.5, 0);
		level.sendParticles(ParticleTypes.EXPLOSION, m.x, m.y, m.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.CRIT, m.x, m.y, m.z, 20, 0.3, 0.6, 0.3, 0.4);
		level.sendParticles(ParticleTypes.CLOUD, m.x, target.getY(), m.z, 10, 0.4, 0.1, 0.4, 0.05);
		level.playSound(null, m.x, m.y, m.z, SoundEvents.MACE_SMASH_AIR, SoundSource.PLAYERS, 1.4f, 0.7f);
		level.playSound(null, m.x, m.y, m.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2f, 0.5f);
		HulkCombat.shake(level, player.position(), 0.35f, 6, 14.0);
	}

	// ---------------------------------------------------------------- G Hammer Quake

	public static void hammerQuake(ServerPlayer player) {
		if (!start(player, HAMMER_QUAKE, false, true)) {
			return;
		}
		HulkAbilities.cooldown(player, HAMMER_QUAKE, HulkConfig.gladiator().quakeCooldownTicks);
		HulkAbilities.anim(player, GladiatorAnims.HAMMER_QUAKE);
		AbilityHelpers.sound(player, SoundEvents.RAVAGER_ROAR, 0.6f, 1.1f);
		HulkAbilities.schedule(player, QUAKE_IMPACT_TICKS, () -> hammerQuakeImpact(player));
	}

	private static void hammerQuakeImpact(ServerPlayer player) {
		if (!stillActive(player)) {
			return;
		}
		HulkConfig.Gladiator c = HulkConfig.gladiator();
		ServerLevel level = level(player);
		Vec3 fwd = HulkAbilities.flatLook(player);
		Vec3 at = player.position();
		double halfRad = Math.toRadians(c.quakeConeDegrees * 0.5);
		double cosHalf = Math.cos(halfRad);
		HulkCombat.Hit hit = new HulkCombat.Hit(c.quakeDamage, c.quakeKnockback, c.quakeLift);
		AABB box = new AABB(at.x - c.quakeRange, at.y - 3.0, at.z - c.quakeRange, at.x + c.quakeRange, at.y + 4.0, at.z + c.quakeRange);
		for (LivingEntity e : HulkCombat.targets(player, box)) {
			Vec3 to = new Vec3(e.getX() - at.x, 0, e.getZ() - at.z);
			double dist = to.length();
			if (dist > c.quakeRange + e.getBbWidth() * 0.5 || (dist > 0.5 && to.normalize().dot(fwd) < cosHalf)) {
				continue;
			}
			float scale = (float) (1.0 - 0.4 * Math.min(1.0, dist / c.quakeRange));
			HulkCombat.strike(player, e, at, hit, scale);
		}
		// the fissure: a crack running out along the cone's spine, the ground bursting up either side of it
		Vec3 side = right(fwd);
		int cracked = 0;
		boolean breaks = HulkCombat.canBreakBlocks(level);
		for (double d = 1.5; d <= c.quakeRange; d += 1.0) {
			Vec3 p = at.add(fwd.scale(d));
			BlockState ground = groundAt(level, p);
			BlockParticleOption dirt = new BlockParticleOption(ParticleTypes.BLOCK, ground);
			double spread = d * Math.tan(halfRad);
			level.sendParticles(dirt, p.x, p.y + 0.3, p.z, 8, spread * 0.4, 0.3, spread * 0.4, 0.15);
			level.sendParticles(ParticleTypes.CLOUD, p.x, p.y + 0.2, p.z, 3, spread * 0.4, 0.1, spread * 0.4, 0.02);
			if (((int) d) % 3 == 0) {
				level.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y + 0.4, p.z, 1, 0, 0, 0, 0);
			}
			if (breaks && cracked < 40) {
				double wobble = Math.sin(d * 1.7) * 0.6;
				Vec3 q = p.add(side.scale(wobble));
				BlockPos pos = BlockPos.containing(q.x, at.y - 0.5, q.z);
				BlockState st = level.getBlockState(pos);
				if (HulkCombat.breakable(level, pos, st) && st.getDestroySpeed(level, pos) <= c.quakeCrackHardness
						&& level.mayInteract(player, pos)) {
					level.destroyBlock(pos, HulkConfig.world().dropBrokenBlocks && cracked % 3 == 0, player);
					cracked++;
				}
			}
		}
		Vec3 head = at.add(fwd.scale(2.0));
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, head.x, head.y + 0.3, head.z, 1, 0, 0, 0, 0);
		level.playSound(null, head.x, head.y, head.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.6f, 0.6f);
		level.playSound(null, head.x, head.y, head.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.8f, 0.6f);
		HulkCombat.shake(level, at, 0.8f, 16, 28.0);
	}

	// ---------------------------------------------------------------- Shift+G Earthsplitter

	public static void earthsplitter(ServerPlayer player) {
		if (!start(player, EARTHSPLITTER, true, false)) {
			return;
		}
		HulkAbilities.cooldown(player, EARTHSPLITTER, HulkConfig.gladiator().earthsplitterCooldownTicks);
		HulkAbilities.anim(player, GladiatorAnims.EARTHSPLITTER);
		AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.5f);
		HulkAbilities.schedule(player, SPLITTER_IMPACT_TICKS, () -> earthsplitterImpact(player));
	}

	private static void earthsplitterImpact(ServerPlayer player) {
		if (!stillActive(player)) {
			return;
		}
		HulkConfig.Gladiator c = HulkConfig.gladiator();
		ServerLevel level = level(player);
		Vec3 fwd = HulkAbilities.flatLook(player);
		Vec3 origin = player.position();
		HulkCombat.Hit hit = new HulkCombat.Hit(c.earthsplitterDamage, 0.4, c.earthsplitterLift);
		Vec3 blade = origin.add(fwd.scale(1.6));
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, groundAt(level, blade)), blade.x, blade.y + 0.2, blade.z, 30, 0.4, 0.2, 0.4, 0.2);
		level.playSound(null, blade.x, blade.y, blade.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.2f, 0.5f);
		HulkCombat.shake(level, origin, 0.5f, 10, 20.0);
		// the eruption runs out along the line, two blocks a tick
		for (int step = 0; step * 2.0 < c.earthsplitterLength; step++) {
			final double d = 2.0 + step * 2.0;
			HulkAbilities.schedule(player, step, () -> erupt(player, origin, fwd, d, hit));
		}
	}

	private static void erupt(ServerPlayer player, Vec3 origin, Vec3 fwd, double d, HulkCombat.Hit hit) {
		if (!player.isAlive() || player.level() == null) {
			return;
		}
		HulkConfig.Gladiator c = HulkConfig.gladiator();
		ServerLevel level = level(player);
		for (double dd = d - 1.0; dd <= d; dd += 1.0) {
			Vec3 p = origin.add(fwd.scale(dd));
			if (!level.isLoaded(BlockPos.containing(p))) {
				return;
			}
			BlockState ground = groundAt(level, p);
			BlockParticleOption block = new BlockParticleOption(ParticleTypes.BLOCK, ground);
			level.sendParticles(block, p.x, p.y + 0.5, p.z, 18, 0.5, 0.6, 0.5, 0.35);
			level.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, ground), p.x, p.y + 0.2, p.z, 14, 0.5, 0.0, 0.5, 0.6);
			level.sendParticles(ParticleTypes.CLOUD, p.x, p.y + 0.4, p.z, 3, 0.4, 0.4, 0.4, 0.05);
			// a line-shaped hit: only what stands inside the strip
			AABB strip = new AABB(p.x - c.earthsplitterWidth * 0.5, p.y - 1.5, p.z - c.earthsplitterWidth * 0.5,
					p.x + c.earthsplitterWidth * 0.5, p.y + 3.0, p.z + c.earthsplitterWidth * 0.5);
			for (LivingEntity e : HulkCombat.targets(player, strip)) {
				HulkCombat.strike(player, e, p.subtract(fwd), hit, 1.0f);
			}
		}
		if (((int) d) % 4 == 0) {
			Vec3 p = origin.add(fwd.scale(d));
			level.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y + 0.5, p.z, 1, 0, 0, 0, 0);
			level.playSound(null, p.x, p.y, p.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 0.9f);
			level.playSound(null, p.x, p.y, p.z, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.2f, 0.5f);
		}
	}

	// ---------------------------------------------------------------- Z Champion's Roar

	public static void championsRoar(ServerPlayer player) {
		if (!start(player, CHAMPIONS_ROAR, false, false)) {
			return;
		}
		HulkAbilities.cooldown(player, CHAMPIONS_ROAR, HulkConfig.gladiator().roarCooldownTicks);
		HulkAbilities.anim(player, GladiatorAnims.CHAMPIONS_ROAR);
		HulkAbilities.schedule(player, ROAR_IMPACT_TICKS, () -> championsRoarImpact(player));
	}

	private static void championsRoarImpact(ServerPlayer player) {
		if (!stillActive(player)) {
			return;
		}
		HulkConfig.Gladiator c = HulkConfig.gladiator();
		ServerLevel level = level(player);
		Vec3 at = player.position();
		ROAR_UNTIL.put(player.getUUID(), level.getGameTime() + c.roarBuffTicks);
		PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, ROAR_ATTACK_ID, c.roarAttackBonus, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.KNOCKBACK_RESISTANCE, ROAR_KNOCKBACK_ID, c.roarKnockbackResistance,
				AttributeModifier.Operation.ADD_VALUE);
		Hulk.setRage(player, Hulk.rage(player) + c.roarRage);
		for (LivingEntity e : HulkCombat.targets(player, player.getBoundingBox().inflate(c.roarRadius, 4.0, c.roarRadius))) {
			if (e.distanceToSqr(player) > c.roarRadius * c.roarRadius) {
				continue;
			}
			// staggered: weak and slow for a moment...
			AbilityHelpers.applyControl(e, MobEffects.WEAKNESS, 100, 1);
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 20, 3);
			if (com.projecthero.mod.titanshifter.TitanCombat.isBoss(e)) {
				continue;
			}
			AbilityHelpers.knockbackFrom(e, at, 0.6);
			// ...then they run
			if (e instanceof PathfinderMob mob) {
				Vec3 away = DefaultRandomPos.getPosAway(mob, 16, 7, at);
				if (away != null) {
					mob.setTarget(null);
					mob.getNavigation().moveTo(away.x, away.y, away.z, 1.5);
				}
			} else if (e instanceof Mob mob) {
				mob.setTarget(null);
			}
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 3.0f, 0.6f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 1.6f, 0.9f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 1.2f, 1.2f);
		Vec3 mouth = player.getEyePosition().add(HulkAbilities.flatLook(player).scale(0.8));
		level.sendParticles(ParticleTypes.SONIC_BOOM, mouth.x, mouth.y, mouth.z, 1, 0, 0, 0, 0);
		for (double r = 2.0; r <= c.roarRadius; r += 2.5) {
			HulkCombat.ring(level, BRONZE, at.add(0, 0.6, 0), r, (int) (r * 6));
			HulkCombat.ring(level, ParticleTypes.CLOUD, at.add(0, 0.2, 0), r, (int) (r * 2));
		}
		HulkCombat.shake(level, at, 0.6f, 16, 24.0);
	}

	private static void endRoar(ServerPlayer player) {
		ROAR_UNTIL.remove(player.getUUID());
		PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, ROAR_ATTACK_ID);
		PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, ROAR_KNOCKBACK_ID);
	}

	// ---------------------------------------------------------------- Shift+Z Weapon Clash

	public static void weaponClash(ServerPlayer player) {
		if (!start(player, WEAPON_CLASH, true, true)) {
			return;
		}
		HulkAbilities.cooldown(player, WEAPON_CLASH, HulkConfig.gladiator().clashCooldownTicks);
		HulkAbilities.anim(player, GladiatorAnims.WEAPON_CLASH);
		HulkAbilities.schedule(player, CLASH_IMPACT_TICKS, () -> weaponClashImpact(player));
	}

	private static void weaponClashImpact(ServerPlayer player) {
		if (!stillActive(player)) {
			return;
		}
		HulkConfig.Gladiator c = HulkConfig.gladiator();
		ServerLevel level = level(player);
		Vec3 at = player.position();
		HulkCombat.Hit hit = new HulkCombat.Hit(c.clashDamage, 0.6, 0.1);
		for (LivingEntity e : HulkCombat.targets(player, player.getBoundingBox().inflate(c.clashRadius, 3.0, c.clashRadius))) {
			if (Math.sqrt(AbilityHelpers.distanceSqToBox(e, at)) > c.clashRadius) {
				continue;
			}
			HulkCombat.strike(player, e, at, hit, 1.0f);
			stun(e, c.clashStunTicks);
		}
		Vec3 hands = player.getEyePosition().add(HulkAbilities.flatLook(player).scale(1.0)).subtract(0, 0.4, 0);
		level.sendParticles(ParticleTypes.FLASH, hands.x, hands.y, hands.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hands.x, hands.y, hands.z, 30, 0.3, 0.3, 0.3, 0.6);
		for (double r = 1.5; r <= c.clashRadius; r += 1.5) {
			HulkCombat.ring(level, ParticleTypes.CRIT, at.add(0, player.getBbHeight() * 0.5, 0), r, (int) (r * 7));
			HulkCombat.ring(level, BRONZE, at.add(0, 0.4, 0), r, (int) (r * 4));
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 2.0f, 1.5f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 3.0f, 0.6f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.5f, 1.0f);
		HulkCombat.shake(level, at, 0.5f, 12, 18.0);
	}

	/** Stunned: can't move, can't swing (softened against players by {@link AbilityHelpers#applyControl}). */
	private static void stun(LivingEntity e, int ticks) {
		AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, ticks, 9);
		AbilityHelpers.applyControl(e, MobEffects.WEAKNESS, ticks, 2);
		AbilityHelpers.applyControl(e, MobEffects.DIG_SLOWDOWN, ticks, 2);
		if (e instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		if (e.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(), 6, 0.3, 0.1, 0.3, 0.05);
		}
	}

	// ---------------------------------------------------------------- X Arena Leap / Shift+X Meteor Dive

	public static void arenaLeap(ServerPlayer player) {
		if (!start(player, ARENA_LEAP, true, true)) {
			return;
		}
		if (!player.onGround()) {
			Hulk.say(player, "message.projecthero.hulk.leap_ground", ChatFormatting.GRAY);
			return;
		}
		HulkConfig.Gladiator c = HulkConfig.gladiator();
		HulkAbilities.cooldown(player, ARENA_LEAP, c.arenaLeapCooldownTicks);
		HulkAbilities.anim(player, GladiatorAnims.ARENA_LEAP);
		LEAP.put(player.getUUID(), new Leap(LEAP_ARENA, player.level().getGameTime()));
		AbilityHelpers.launchSelf(player, AbilityHelpers.ballisticLaunch(player.getLookAngle(), c.arenaLeapBlocks, true));
		takeoffFx(player);
	}

	public static void meteorDive(ServerPlayer player) {
		if (!start(player, METEOR_DIVE, true, true)) {
			return;
		}
		HulkConfig.Gladiator c = HulkConfig.gladiator();
		HulkAbilities.cooldown(player, METEOR_DIVE, c.meteorCooldownTicks);
		HulkAbilities.anim(player, GladiatorAnims.METEOR_DIVE);
		LEAP.put(player.getUUID(), new Leap(LEAP_RISE, player.level().getGameTime()));
		AbilityHelpers.launchSelf(player, new Vec3(0, c.meteorRiseSpeed, 0));
		takeoffFx(player);
	}

	private static void takeoffFx(ServerPlayer player) {
		ServerLevel level = level(player);
		BlockState ground = level.getBlockState(player.blockPosition().below());
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), player.getX(), player.getY() + 0.1, player.getZ(),
					40, 1.0, 0.1, 1.0, 0.2);
		}
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(), 20, 0.8, 0.1, 0.8, 0.05);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 1.4f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.7f, 1.0f);
	}

	private static void tickLeap(ServerPlayer player, Leap leap, long now) {
		HulkConfig.Gladiator c = HulkConfig.gladiator();
		ServerLevel level = level(player);
		if (now - leap.takeoff > 240) {
			LEAP.remove(player.getUUID()); // lost (stuck on something, a flight mod): give up quietly
			return;
		}
		boolean settled = player.onGround() || player.isInWater() || player.isPassenger() || player.getAbilities().flying;
		switch (leap.mode) {
			case LEAP_ARENA -> {
				if (settled && now - leap.takeoff > 4) {
					LEAP.remove(player.getUUID());
					if (player.onGround()) {
						slam(player, player.position(), c.arenaLeapRadius, c.arenaLeapDamage, 1.8, 0.7, c.arenaLeapCraterRadius, false);
					}
				} else if (now % 2 == 0) {
					level.sendParticles(BRONZE, player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.4, 0.6, 0.4, 0.0);
				}
			}
			case LEAP_RISE -> {
				if (player.getDeltaMovement().y <= 0.15 || now - leap.phaseAt >= 14) {
					leap.mode = LEAP_DIVE;
					leap.phaseAt = now;
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.5f, 0.7f);
				}
				level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 2, 0.3, 0.1, 0.3, 0.02);
			}
			default -> {
				// guided: every tick he steers toward where he is looking now
				boolean landed = (settled || player.horizontalCollision) && now - leap.phaseAt > 1;
				if (landed || now - leap.phaseAt > 60) {
					LEAP.remove(player.getUUID());
					AbilityHelpers.launchSelf(player, Vec3.ZERO);
					slam(player, player.position(), c.meteorRadius, c.meteorDamage, 2.4, 0.9, c.meteorCraterRadius, true);
					return;
				}
				Vec3 aim = diveAim(player, c.meteorDiveRange);
				Vec3 to = aim.subtract(player.position());
				Vec3 dir = to.lengthSqr() < 1.0e-4 ? new Vec3(0, -1, 0) : to.normalize();
				if (dir.y > -0.25) {
					dir = new Vec3(dir.x, -0.25, dir.z).normalize(); // always a dive, never a climb
				}
				AbilityHelpers.launchSelf(player, dir.scale(c.meteorDiveSpeed));
				level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.4, 0.6, 0.4, 0.02);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.4, player.getZ(), 3, 0.3, 0.3, 0.3, 0.01);
				if (now % 4 == 0) {
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6f, 0.5f);
				}
			}
		}
	}

	/** Where Meteor Dive is heading: the block he looks at, else straight below the end of his look. */
	private static Vec3 diveAim(ServerPlayer player, double range) {
		BlockHitResult hit = AbilityHelpers.raycastBlock(player, range);
		if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
			return hit.getLocation();
		}
		Vec3 end = player.getEyePosition().add(player.getLookAngle().scale(range));
		return new Vec3(end.x, Math.min(end.y, player.getY() - 4.0), end.z);
	}

	/** The weapons-first landing (Arena Leap, Meteor Dive) and the Whirlwind's closing slam. */
	private static void slam(ServerPlayer player, Vec3 at, double radius, float damage, double knockback, double lift, double crater,
			boolean meteor) {
		ServerLevel level = level(player);
		HulkAbilities.anim(player, GladiatorAnims.SLAM);
		HulkCombat.radial(player, at, radius, new HulkCombat.Hit(damage, knockback, lift), !meteor);
		if (crater > 0.0) {
			HulkCombat.crater(player, at.add(HulkAbilities.flatLook(player).scale(1.2)).add(0, -0.5, 0), crater, meteor ? 60 : 80);
		}
		BlockState ground = groundAt(level, at);
		BlockParticleOption dirt = new BlockParticleOption(ParticleTypes.BLOCK, ground);
		for (double r = 1.0; r <= radius; r += 1.0) {
			HulkCombat.ring(level, dirt, at.add(0, 0.2, 0), r, (int) (r * 8));
		}
		level.sendParticles(meteor ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, at.x, at.y + 0.4, at.z, meteor ? 1 : 3,
				0.6, 0.1, 0.6, 0.0);
		level.sendParticles(BRONZE, at.x, at.y + 0.5, at.z, 30, radius * 0.4, 0.3, radius * 0.4, 0.0);
		if (meteor) {
			level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.3, at.z, 20, 1.0, 0.2, 1.0, 0.0);
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, meteor ? 2.5f : 1.6f, 0.6f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 2.0f, 0.6f);
		HulkCombat.shake(level, at, meteor ? 1.0f : 0.7f, 18, 28.0);
	}

	// ---------------------------------------------------------------- C Axe Throw / Shift+C Hammer Hurl

	public static void axeThrow(ServerPlayer player) {
		if (!start(player, AXE_THROW, true, false)) {
			return;
		}
		HulkAbilities.cooldown(player, AXE_THROW, HulkConfig.gladiator().axeThrowCooldownTicks);
		HulkAbilities.anim(player, GladiatorAnims.AXE_THROW);
		// the axe is out of his hand from the press: no second throw, and the gear shows an empty left hand
		AXE.put(player.getUUID(), new Away(-1, player.level().getGameTime()));
		GladiatorGear.setWeaponAway(player, true, true);
		HulkAbilities.schedule(player, AXE_RELEASE_TICKS, () -> releaseAxe(player));
	}

	private static void releaseAxe(ServerPlayer player) {
		if (!stillActive(player) || !AXE.containsKey(player.getUUID())) {
			weaponHome(player, true, null);
			return;
		}
		HulkConfig.Gladiator c = HulkConfig.gladiator();
		ServerLevel level = level(player);
		Vec3 look = player.getLookAngle();
		Vec3 fwd = HulkAbilities.flatLook(player);
		Vec3 hand = player.position().add(0, player.getBbHeight() * 0.7, 0).add(right(fwd).scale(-0.6)).add(fwd.scale(0.6));
		GladiatorAxeEntity axe = GladiatorEntities.THROWN_AXE.create(level);
		if (axe == null || !level.isPositionEntityTicking(BlockPos.containing(hand))) {
			weaponHome(player, true, null);
			return;
		}
		axe.moveTo(hand.x, hand.y, hand.z, player.getYRot(), 0.0f);
		axe.setup(player, look.scale(c.axeThrowSpeed), new HulkCombat.Hit(c.axeThrowDamage, 0.8, 0.2),
				new HulkCombat.Hit(c.axeThrowDamage, 0.6, 0.15));
		level.addFreshEntity(axe);
		AXE.put(player.getUUID(), new Away(axe.getId(), level.getGameTime()));
		level.playSound(null, hand.x, hand.y, hand.z, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.2f, 0.6f);
		level.playSound(null, hand.x, hand.y, hand.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.8f);
	}

	public static void hammerHurl(ServerPlayer player) {
		if (!start(player, HAMMER_HURL, false, true)) {
			return;
		}
		HulkAbilities.cooldown(player, HAMMER_HURL, HulkConfig.gladiator().hammerHurlCooldownTicks);
		HulkAbilities.anim(player, GladiatorAnims.HAMMER_HURL);
		HAMMER.put(player.getUUID(), new Away(-1, player.level().getGameTime()));
		GladiatorGear.setWeaponAway(player, false, true);
		HulkAbilities.schedule(player, HAMMER_RELEASE_TICKS, () -> releaseHammer(player));
	}

	private static void releaseHammer(ServerPlayer player) {
		if (!stillActive(player) || !HAMMER.containsKey(player.getUUID())) {
			weaponHome(player, false, null);
			return;
		}
		HulkConfig.Gladiator c = HulkConfig.gladiator();
		ServerLevel level = level(player);
		Vec3 fwd = HulkAbilities.flatLook(player);
		Vec3 hand = player.position().add(0, player.getBbHeight() * 0.85, 0).add(right(fwd).scale(0.6)).add(fwd.scale(0.6));
		GladiatorHammerEntity hammer = GladiatorEntities.THROWN_HAMMER.create(level);
		if (hammer == null || !level.isPositionEntityTicking(BlockPos.containing(hand))) {
			weaponHome(player, false, null);
			return;
		}
		Vec3 v = player.getLookAngle().scale(c.hammerHurlSpeed).add(0, 0.3, 0);
		hammer.moveTo(hand.x, hand.y, hand.z, player.getYRot(), 0.0f);
		hammer.setup(player, v, new HulkCombat.Hit(c.hammerHurlDamage, 1.4, 0.5), new HulkCombat.Hit(c.hammerRecallDamage, 1.0, 0.3));
		level.addFreshEntity(hammer);
		HAMMER.put(player.getUUID(), new Away(hammer.getId(), level.getGameTime()));
		level.playSound(null, hand.x, hand.y, hand.z, SoundEvents.MACE_SMASH_AIR, SoundSource.PLAYERS, 1.4f, 0.6f);
		level.playSound(null, hand.x, hand.y, hand.z, SoundEvents.RAVAGER_ATTACK, SoundSource.PLAYERS, 0.8f, 0.8f);
	}

	/** C while the hammer is away: it rips out of the ground and flies home. */
	public static void recallHammer(ServerPlayer player) {
		Away away = HAMMER.get(player.getUUID());
		if (away == null) {
			return;
		}
		Entity e = level(player).getEntity(away.entityId());
		if (e instanceof GladiatorHammerEntity hammer && hammer.isAlive()) {
			if (!hammer.returning()) {
				hammer.recall();
				HulkAbilities.anim(player, GladiatorAnims.HAMMER_RECALL);
				AbilityHelpers.sound(player, SoundEvents.RAVAGER_ROAR, 0.5f, 1.4f);
			}
		} else if (away.entityId() >= 0) {
			weaponHome(player, false, null); // the hammer is gone (unloaded): it is simply back
		}
	}

	/** The thrown weapon is back in his hand (or lost): clear the away flag. {@code from}: the entity that came home, if any. */
	public static void weaponHome(ServerPlayer player, boolean axe, Entity from) {
		Map<UUID, Away> map = axe ? AXE : HAMMER;
		Away away = map.get(player.getUUID());
		if (from != null && away != null && away.entityId() != from.getId()) {
			return; // an older throw's entity: the current one is still out
		}
		map.remove(player.getUUID());
		GladiatorGear.setWeaponAway(player, axe, false);
		if (from != null) {
			level(player).playSound(null, player.getX(), player.getY(), player.getZ(),
					axe ? SoundEvents.ARMOR_EQUIP_NETHERITE.value() : SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 1.2f, 0.7f);
		}
	}

	/** Takes back a weapon that is away at once (its entity is removed). */
	private static void recallNow(ServerPlayer player, boolean axe) {
		Map<UUID, Away> map = axe ? AXE : HAMMER;
		Away away = map.remove(player.getUUID());
		if (away != null && away.entityId() >= 0 && player.level() instanceof ServerLevel level) {
			Entity e = level.getEntity(away.entityId());
			if (e != null) {
				e.discard();
			}
		}
		GladiatorGear.setWeaponAway(player, axe, false);
	}

	private static void tickAway(ServerPlayer player, boolean axe, long now) {
		Map<UUID, Away> map = axe ? AXE : HAMMER;
		Away away = map.get(player.getUUID());
		if (away == null || away.entityId() < 0) {
			if (away != null && now - away.since() > 40) {
				recallNow(player, axe); // the release task never ran
			}
			return;
		}
		Entity e = level(player).getEntity(away.entityId());
		int limit = axe ? AXE_AWAY_LIMIT_TICKS : HulkConfig.gladiator().hammerStuckTicks + 400;
		if (e == null || e.isRemoved() || now - away.since() > limit) {
			recallNow(player, axe);
		}
	}

	// ---------------------------------------------------------------- V Whirlwind

	public static void whirlwind(ServerPlayer player) {
		if (!start(player, WHIRLWIND, true, true)) {
			return;
		}
		HulkConfig.Gladiator c = HulkConfig.gladiator();
		long now = player.level().getGameTime();
		// the cooldown is set when the spin ends; mark it now so a second press can't start another
		HulkAbilities.cooldown(player, WHIRLWIND, c.whirlwindTicks + c.whirlwindCooldownTicks);
		HulkAbilities.anim(player, GladiatorAnims.WHIRLWIND);
		WHIRL.put(player.getUUID(), new Whirl(now + c.whirlwindTicks, new HulkCombat.Hit(c.whirlwindSlamDamage, 1.6, 0.6)));
		AbilityHelpers.sound(player, SoundEvents.RAVAGER_ROAR, 1.0f, 1.2f);
	}

	private static void tickWhirl(ServerPlayer player, Whirl whirl, long now) {
		HulkConfig.Gladiator c = HulkConfig.gladiator();
		ServerLevel level = level(player);
		Vec3 at = player.position();
		if (now >= whirl.until()) {
			WHIRL.remove(player.getUUID());
			HulkAbilities.cooldown(player, WHIRLWIND, c.whirlwindCooldownTicks);
			slam(player, at, c.whirlwindSlamRadius, c.whirlwindSlamDamage, 1.6, 0.6, 0.0, false);
			return;
		}
		long t = whirl.until() - now;
		if (t % Math.max(1, c.whirlwindPulseTicks) == 0) {
			HulkCombat.Hit hit = new HulkCombat.Hit(c.whirlwindDamage, 0.5, 0.15);
			AABB box = player.getBoundingBox().inflate(c.whirlwindPullRadius, 2.0, c.whirlwindPullRadius);
			for (LivingEntity e : HulkCombat.targets(player, box)) {
				double d = Math.sqrt(AbilityHelpers.distanceSqToBox(e, at.add(0, 1.0, 0)));
				if (d <= c.whirlwindRadius) {
					e.invulnerableTime = 0; // a hit every pulse, faster than the vanilla hurt window
					HulkCombat.strike(player, e, at, hit, 1.0f);
				} else if (d <= c.whirlwindPullRadius && !com.projecthero.mod.titanshifter.TitanCombat.isBoss(e)) {
					// dragged into the blades
					Vec3 in = at.subtract(e.position());
					Vec3 flat = new Vec3(in.x, 0, in.z).normalize().scale(0.35);
					AbilityHelpers.push(e, flat);
				}
			}
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.6f + 0.02f * (t % 10));
		}
		double y = at.y + player.getBbHeight() * 0.45;
		double a = now * 1.1;
		for (int i = 0; i < 2; i++) {
			double ai = a + Math.PI * i;
			double r = c.whirlwindRadius * 0.85;
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x + Math.cos(ai) * r, y, at.z + Math.sin(ai) * r, 1, 0, 0, 0, 0);
			level.sendParticles(BRONZE, at.x + Math.cos(ai + 0.4) * r, y + 0.2, at.z + Math.sin(ai + 0.4) * r, 2, 0.1, 0.1, 0.1, 0.0);
		}
		if (now % 2 == 0) {
			level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, 4, 1.2, 0.05, 1.2, 0.04);
		}
	}

	// ---------------------------------------------------------------- Shift+V Arena Grapple

	public static void arenaGrapple(ServerPlayer player) {
		Grapple g = GRAPPLE.get(player.getUUID());
		if (g != null) {
			throwGrappled(player, g);
			return;
		}
		if (!start(player, ARENA_GRAPPLE, false, true)) {
			return;
		}
		HulkConfig.Gladiator c = HulkConfig.gladiator();
		LivingEntity target = frontTarget(player, c.grappleRange);
		if (target == null || !HulkGrab.grabbable(player, target) || isPinned(target)) {
			Hulk.say(player, "message.projecthero.hulk.grab_refused", ChatFormatting.GRAY);
			return;
		}
		target.stopRiding();
		target.ejectPassengers();
		long now = player.level().getGameTime();
		// the cooldown runs from the release; marked now so it can't be started twice
		HulkAbilities.cooldown(player, ARENA_GRAPPLE, c.grappleBlows * c.grappleBlowTicks + c.grappleCooldownTicks + 40);
		GRAPPLE.put(player.getUUID(), new Grapple(target.getId(), now + c.grappleBlowTicks));
		HulkAbilities.anim(player, GladiatorAnims.GRAPPLE);
		ServerLevel level = level(player);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2f, 0.5f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ATTACK, SoundSource.PLAYERS, 1.0f, 0.7f);
	}

	private static LivingEntity grappled(ServerPlayer player, Grapple g) {
		Entity e = level(player).getEntity(g.entityId);
		return e instanceof LivingEntity l && l.isAlive() && !l.isRemoved() ? l : null;
	}

	private static Vec3 pinPoint(ServerPlayer player) {
		return player.position().add(HulkAbilities.flatLook(player).scale(1.9));
	}

	private static void tickGrapple(ServerPlayer player, Grapple g, long now) {
		HulkConfig.Gladiator c = HulkConfig.gladiator();
		LivingEntity target = grappled(player, g);
		if (target == null || target.distanceToSqr(player) > 64.0) {
			endGrapple(player);
			return;
		}
		// pinned to the ground in front of him
		Vec3 pin = pinPoint(player);
		target.moveTo(pin.x, player.getY(), pin.z, target.getYRot(), target.getXRot());
		target.setDeltaMovement(Vec3.ZERO);
		target.hurtMarked = true;
		target.fallDistance = 0.0f;
		if (target instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		if (now < g.nextBlow) {
			return;
		}
		g.blows++;
		g.nextBlow = now + c.grappleBlowTicks;
		ServerLevel level = level(player);
		target.invulnerableTime = 0;
		AbilityHelpers.hurtLands(player, target, c.grappleBlowDamage);
		Vec3 m = target.position().add(0, target.getBbHeight() * 0.5, 0);
		level.sendParticles(ParticleTypes.CRIT, m.x, m.y, m.z, 12, 0.3, 0.3, 0.3, 0.3);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, groundAt(level, target.position())), m.x, target.getY() + 0.1, m.z,
				12, 0.5, 0.1, 0.5, 0.15);
		level.playSound(null, m.x, m.y, m.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8f, 0.6f + 0.1f * g.blows);
		level.playSound(null, m.x, m.y, m.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0f, 0.6f);
		HulkCombat.shake(level, m, 0.3f, 4, 12.0);
		if (g.blows >= c.grappleBlows) {
			endGrapple(player);
		}
	}

	private static void throwGrappled(ServerPlayer player, Grapple g) {
		HulkConfig.Gladiator c = HulkConfig.gladiator();
		LivingEntity target = grappled(player, g);
		GRAPPLE.remove(player.getUUID());
		HulkAbilities.cooldown(player, ARENA_GRAPPLE, c.grappleCooldownTicks);
		HulkAbilities.anim(player, HulkState.ANIM_THROW);
		if (target == null) {
			return;
		}
		target.invulnerableTime = 0;
		AbilityHelpers.hurtLands(player, target, c.grappleThrowDamage);
		if (!com.projecthero.mod.titanshifter.TitanCombat.isBoss(target)) {
			target.setDeltaMovement(player.getLookAngle().scale(c.grappleThrowSpeed).add(0, 0.45, 0));
			target.hurtMarked = true;
			target.hasImpulse = true;
		}
		ServerLevel level = level(player);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2f, 0.5f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.8f, 1.2f);
	}

	private static void endGrapple(ServerPlayer player) {
		if (GRAPPLE.remove(player.getUUID()) == null) {
			return;
		}
		HulkAbilities.cooldown(player, ARENA_GRAPPLE, HulkConfig.gladiator().grappleCooldownTicks);
		HulkState s = Hulk.state(player);
		if (s.animId == GladiatorAnims.GRAPPLE) {
			HulkAbilities.anim(player, HulkState.ANIM_NONE);
		}
	}

	// ---------------------------------------------------------------- tick / lifecycle

	/** Every Gamma player, every tick, from {@link Hulk#tick}. */
	public static void tick(ServerPlayer player) {
		UUID id = player.getUUID();
		boolean anything = AXE.containsKey(id) || HAMMER.containsKey(id) || ROAR_UNTIL.containsKey(id) || WHIRL.containsKey(id)
				|| LEAP.containsKey(id) || GRAPPLE.containsKey(id);
		if (!anything) {
			return;
		}
		if (!active(player) || HulkAbilityLock.locked(player)) {
			clear(player); // left Hulk form or took the gear off: the weapons come home, everything stops
			return;
		}
		long now = player.level().getGameTime();
		tickAway(player, true, now);
		tickAway(player, false, now);
		Long roar = ROAR_UNTIL.get(id);
		if (roar != null) {
			if (now >= roar) {
				endRoar(player);
			} else if (now % 5 == 0) {
				level(player).sendParticles(BRONZE, player.getX(), player.getY() + player.getBbHeight() * 0.6, player.getZ(), 2, 0.5, 0.6, 0.5, 0.0);
			}
		}
		Whirl whirl = WHIRL.get(id);
		if (whirl != null) {
			tickWhirl(player, whirl, now);
		}
		Leap leap = LEAP.get(id);
		if (leap != null) {
			tickLeap(player, leap, now);
		}
		Grapple g = GRAPPLE.get(id);
		if (g != null) {
			tickGrapple(player, g, now);
		}
	}

	/** Revert / revoke / death / logout / dimension change / kit removed: stop everything, weapons back in hand. */
	public static void clear(ServerPlayer player) {
		UUID id = player.getUUID();
		recallNow(player, true);
		recallNow(player, false);
		endRoar(player);
		WHIRL.remove(id);
		LEAP.remove(id);
		GRAPPLE.remove(id);
	}

	/** Join: nothing can be away after a relog. */
	public static void onJoin(ServerPlayer player) {
		GladiatorGear.setWeaponAway(player, true, false);
		GladiatorGear.setWeaponAway(player, false, false);
	}

	/** Small helper so a rampage / calm locks the gladiator's running moves too. */
	private static final class HulkAbilityLock {
		static boolean locked(ServerPlayer player) {
			HulkState s = Hulk.state(player);
			return s.rampaging(player.level().getGameTime()) || s.combat.calming;
		}
	}
}
