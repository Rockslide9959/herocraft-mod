package com.projecthero.mod.hulk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hulk.data.HulkState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * The Hulk's abilities -- v0.16.4 (user) rebuilt kit. Only the Hulk himself, in control, can use them -- not Banner, not
 * during a rampage. Every number is in {@link HulkConfig#abilities()}; all damage goes through the normal damage path
 * (so the rage damage bonus applies).
 *
 * <pre>
 *   R        Super Punch         -- an 8-block cone in front: damage, knockback, smashes the blocks in it
 *   Shift+R  Ground Smash        -- both fists down: a ring round him, a crater, the ground cracked open
 *   G        Thunderclap         -- a sonic cone (~12 blocks): knockback + stagger, shatters glass
 *   Shift+G  Fault-line Stomp    -- a fissure races ~16 blocks ahead, launching what stands on it
 *   Z        Bulldozer Charge    -- barrels ~12 blocks forward, carrying mobs along and flinging them aside
 *   Shift+Z  Launch Uppercut     -- the target (or nearest in front, 4 blocks) goes high into the air
 *   X        Super Leap          -- hold up to 0.5 s: 25 / 100 / 500 blocks, no drag ({@link HulkLeap})
 *   C        Gamma Burst         -- spends rage: a radial blast of gamma energy
 *   Shift+C  Earth Wall          -- a wall of earth torn up as cover; crumbles after 10 s ({@link HulkEarthWall})
 *   V        Grab / far throw, Shift+V Debris Throw / Loki Slam ({@link HulkGrab})
 * </pre>
 *
 * Hits land on the animation's impact frame through a tiny per-player task queue; the animation itself reaches every
 * client through {@link HulkState#animId} / {@link HulkState#animStart}. Sprint Smash (sprinting through soft blocks) is
 * a passive, governed by the config.
 */
public final class HulkAbilities {
	// v0.16.4 kit ids (cooldown keys + lang names projecthero.hulk.ability.<id>)
	public static final String SUPER_PUNCH = "super_punch";
	public static final String GROUND_SMASH = "ground_smash";
	public static final String THUNDERCLAP = "thunderclap";
	public static final String FAULT_STOMP = "fault_stomp";
	public static final String BULLDOZE = "bulldoze";
	public static final String UPPERCUT = "uppercut";
	public static final String SUPER_LEAP = "super_leap";
	public static final String GAMMA_BURST = "gamma_burst";
	public static final String EARTH_WALL = "earth_wall";
	public static final String GRAB = "grab";
	public static final String LOKI_SLAM = "loki_slam";
	/** The Shift+V debris rip shares {@link #GRAB}'s cooldown (it starts at the throw); this id is its lang name. */
	public static final String DEBRIS_THROW = "debris_throw";
	/** @deprecated v0.16.4: HULK SMASH left the kit; kept so older HUD code still compiles (never on cooldown). */
	@Deprecated
	public static final String HULK_SMASH = "hulk_smash";

	/** Ticks from the key press to the hit -- v0.16.4: the impact frames of ANIMS' clips (seconds x 20). */
	public static final int SUPER_PUNCH_IMPACT_TICKS = 6; // super_punch 0.30 s
	public static final int CLAP_IMPACT_TICKS = 6; // thunderclap 0.28 s
	public static final int SMASH_IMPACT_TICKS = 8; // ground_smash 0.42 s (also the rampage stomp)
	public static final int FAULT_IMPACT_TICKS = 8; // fault_stomp 0.40 s
	public static final int UPPERCUT_IMPACT_TICKS = 6; // uppercut 0.32 s
	public static final int GAMMA_BURST_IMPACT_TICKS = 9; // gamma_burst 0.45 s
	public static final int EARTH_WALL_IMPACT_TICKS = 10; // earth_wall 0.48 s
	/** V: the far throw lets go at throw_far 0.30 s, the debris at debris_throw 0.34 s. */
	public static final int THROW_FAR_RELEASE_TICKS = 6;
	public static final int DEBRIS_THROW_RELEASE_TICKS = 7;

	static final DustParticleOptions GAMMA = new DustParticleOptions(new Vector3f(0.35f, 1.0f, 0.25f), 1.6f);
	static final DustParticleOptions GAMMA_DEEP = new DustParticleOptions(new Vector3f(0.1f, 0.6f, 0.1f), 2.2f);
	static final DustParticleOptions GAMMA_BRIGHT = new DustParticleOptions(new Vector3f(0.7f, 1.0f, 0.5f), 2.6f);

	private record Task(long at, Runnable run) {
	}

	/** {@code rampage}: the rampage AI started it (it keeps running while he rampages). */
	private record Dozer(Vec3 start, Vec3 dir, Set<Integer> carried, HulkCombat.Hit hit, boolean rampage) {
	}

	/**
	 * v0.16.4 (user): "make him use moves more" -- the player the rampage AI is driving a move for right now (server thread
	 * only). {@link #canAct} lets a rampaging Hulk act while it is set, and the moves skip the player's own cooldowns (the AI
	 * keeps its own).
	 */
	private static UUID driving;

	/** Runs {@code move} as the rampage AI's (see {@link #driving}). */
	static void driven(ServerPlayer player, Runnable move) {
		UUID before = driving;
		driving = player.getUUID();
		try {
			move.run();
		} finally {
			driving = before;
		}
	}

	/** The rampage AI is driving this player's move right now. */
	static boolean drivenNow(ServerPlayer player) {
		return driving != null && driving.equals(player.getUUID());
	}

	private static final Map<UUID, List<Task>> TASKS = new ConcurrentHashMap<>();
	private static final Map<UUID, Vec3> LAST_POS = new ConcurrentHashMap<>();
	private static final Map<UUID, Dozer> DOZERS = new ConcurrentHashMap<>();

	private HulkAbilities() {
	}

	public static void clearSessionState() {
		TASKS.clear();
		LAST_POS.clear();
		DOZERS.clear();
		HulkLeap.clearSessionState();
		GroundCracks.clearSessionState();
		HulkEarthWall.clearSessionState();
	}

	public static void clear(UUID id) {
		TASKS.remove(id);
		LAST_POS.remove(id);
		DOZERS.remove(id);
		HulkLeap.clear(id);
	}

	// ---------------------------------------------------------------- cooldowns / helpers

	public static boolean ready(ServerPlayer player, String id) {
		Long at = Hulk.state(player).abilityReadyAt.get(id);
		return at == null || player.level().getGameTime() >= at;
	}

	public static int cooldownRemaining(net.minecraft.world.entity.player.Player player, String id) {
		HulkState s = player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.HULK_STATE, null);
		if (s == null) {
			return 0;
		}
		Long at = s.abilityReadyAt.get(id);
		return at == null ? 0 : (int) Math.max(0L, at - player.level().getGameTime());
	}

	public static void cooldown(ServerPlayer player, String id, int ticks) {
		HulkState n = Hulk.state(player).copy();
		n.abilityReadyAt.put(id, player.level().getGameTime() + ticks);
		Hulk.save(player, n);
	}

	public static void anim(ServerPlayer player, int animId) {
		HulkState n = Hulk.state(player).copy();
		n.animId = animId;
		n.animStart = player.level().getGameTime();
		Hulk.save(player, n);
	}

	public static void schedule(ServerPlayer player, int delay, Runnable run) {
		TASKS.computeIfAbsent(player.getUUID(), k -> new ArrayList<>())
				.add(new Task(player.level().getGameTime() + delay, run));
	}

	/** Only the Hulk in control: Banner is told to get angry first; a rampaging or calming Hulk is ignored. */
	public static boolean canAct(ServerPlayer player) {
		HulkState s = Hulk.state(player);
		if (!s.hulk) {
			Hulk.say(player, "message.projecthero.hulk.banner_cannot", ChatFormatting.GRAY);
			return false;
		}
		long now = player.level().getGameTime();
		if ((s.rampaging(now) && !drivenNow(player)) || s.combat.calming) {
			return false;
		}
		if (Hulk.changing(s, now)) {
			Hulk.say(player, "message.projecthero.hulk.changing", ChatFormatting.DARK_GREEN);
			return false; // v0.13.15: not until the unwilling change is over
		}
		return player.isAlive() && !player.isSpectator();
	}

	/** {@link #canAct} + off cooldown; starts the cooldown and the animation. */
	private static boolean start(ServerPlayer player, String id, int cooldownTicks, int animId) {
		if (!canAct(player) || (!drivenNow(player) && !ready(player, id)) || busy(player)) {
			return false;
		}
		HulkState n = Hulk.state(player).copy();
		long now = player.level().getGameTime();
		n.abilityReadyAt.put(id, now + cooldownTicks);
		n.animId = animId;
		n.animStart = now;
		Hulk.save(player, n);
		return true;
	}

	/** Mid-leap, mid-charge, mid-slam: the other moves wait. */
	public static boolean busy(net.minecraft.world.entity.player.Player player) {
		HulkState s = player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.HULK_STATE, null);
		if (s == null) {
			return false;
		}
		long now = player.level().getGameTime();
		return s.leapChargeStart > 0L || s.combat.chargeUntil > now
				|| (player instanceof ServerPlayer sp && HulkGrab.slamming(sp));
	}

	public static Vec3 flatLook(ServerPlayer p) {
		Vec3 l = p.getLookAngle();
		Vec3 f = new Vec3(l.x, 0, l.z);
		return f.lengthSqr() < 1.0e-5 ? Vec3.directionFromRotation(0, p.getYRot()) : f.normalize();
	}

	/** Chest height, where his blows start from. */
	static Vec3 chest(ServerPlayer p) {
		return p.position().add(0, p.getBbHeight() * 0.55, 0);
	}

	/** Everything he may hurt within {@code range} of {@code origin} inside a cone ({@code cosHalf}) round {@code dir}. */
	static List<LivingEntity> cone(ServerPlayer player, Vec3 origin, Vec3 dir, double range, double cosHalf) {
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : HulkCombat.targets(player, player.getBoundingBox().inflate(range + 1.0))) {
			Vec3 mid = e.position().add(0, e.getBbHeight() * 0.5, 0);
			Vec3 to = mid.subtract(origin);
			double dist = Math.sqrt(AbilityHelpers.distanceSqToBox(e, origin));
			if (dist > range) {
				continue;
			}
			// close in, anything overlapping his front counts; further out it must be inside the cone
			double len = to.length();
			if (len > 1.0e-3 && to.scale(1.0 / len).dot(dir) < cosHalf && !(dist < 1.5 && to.dot(dir) > 0)) {
				continue;
			}
			out.add(e);
		}
		return out;
	}

	private static BlockState groundUnder(ServerLevel level, Vec3 at) {
		BlockPos s = GroundCracks.surface(level, at.x, at.y, at.z);
		BlockState st = s == null ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : level.getBlockState(s);
		return st.isAir() ? net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState() : st;
	}

	// ---------------------------------------------------------------- R Super Punch

	public static void superPunch(ServerPlayer player) {
		if (!start(player, SUPER_PUNCH, HulkConfig.abilities().superPunchCooldownTicks, HulkState.ANIM_SUPER_PUNCH)) {
			return;
		}
		AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.4f);
		AbilityHelpers.sound(player, SoundEvents.RAVAGER_ATTACK, 1.0f, 0.6f);
		schedule(player, SUPER_PUNCH_IMPACT_TICKS, () -> superPunchImpact(player));
	}

	private static void superPunchImpact(ServerPlayer player) {
		if (!Hulk.isHulk(player) || !player.isAlive()) {
			return;
		}
		HulkConfig.Abilities cfg = HulkConfig.abilities();
		ServerLevel level = (ServerLevel) player.level();
		Vec3 dir = player.getLookAngle().normalize();
		Vec3 origin = chest(player);
		double range = cfg.superPunchRange * WorldBreaker.aoe(player); // v0.16.4 (user): World Breaker doubles every area
		double halfRad = Math.toRadians(cfg.superPunchConeDegrees * 0.5);
		double cosHalf = Math.cos(halfRad);
		HulkCombat.Hit hit = new HulkCombat.Hit(cfg.superPunchDamage, cfg.superPunchKnockback, 0.4);
		for (LivingEntity e : cone(player, origin, dir, range, cosHalf)) {
			double dist = Math.sqrt(AbilityHelpers.distanceSqToBox(e, origin));
			float falloff = (float) (1.0 - 0.35 * Math.min(1.0, dist / range));
			HulkCombat.strike(player, e, origin, hit, falloff);
		}
		if (HulkCombat.canBreakBlocks(level)) {
			punchBlocks(player, level, origin, dir, range, cosHalf, (int) Math.round(cfg.superPunchMaxBlocks * WorldBreaker.destruction(player)));
		}
		// the shock it throws: rings of air widening along the cone
		Vec3 fist = origin.add(dir.scale(1.6));
		level.sendParticles(ParticleTypes.EXPLOSION, fist.x, fist.y, fist.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, fist.x, fist.y, fist.z, 1, 0, 0, 0, 0);
		Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 a = dir.cross(up).normalize();
		Vec3 b = dir.cross(a).normalize();
		for (double d = 1.5; d <= range; d += 1.0) {
			double r = d * Math.tan(halfRad) * 0.8;
			Vec3 c = origin.add(dir.scale(d));
			int pts = 6 + (int) (r * 4);
			for (int i = 0; i < pts; i++) {
				double ang = Math.PI * 2 * i / pts;
				Vec3 p = c.add(a.scale(Math.cos(ang) * r)).add(b.scale(Math.sin(ang) * r));
				level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.01);
			}
			level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 4, r * 0.4, r * 0.4, r * 0.4, 0.3);
		}
		level.sendParticles(ParticleTypes.GUST, origin.x + dir.x * range * 0.6, origin.y + dir.y * range * 0.6, origin.z + dir.z * range * 0.6,
				3, 1.0, 0.6, 1.0, 0.0);
		level.playSound(null, fist.x, fist.y, fist.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2f, 1.3f);
		level.playSound(null, fist.x, fist.y, fist.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.5f, 0.6f);
		level.playSound(null, fist.x, fist.y, fist.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.5f, 0.5f);
		level.playSound(null, fist.x, fist.y, fist.z, SoundEvents.IRON_GOLEM_ATTACK, SoundSource.PLAYERS, 1.2f, 0.5f);
		HulkCombat.shake(level, player.position(), 0.55f, 8, 20.0);
	}

	/** Smashes what the punch's cone reaches (nearest first). The ground under his feet only if he punches downward. */
	private static void punchBlocks(ServerPlayer player, ServerLevel level, Vec3 origin, Vec3 dir, double range, double cosHalf, int max) {
		boolean down = dir.y < -0.4;
		double floorY = player.getY();
		int r = (int) Math.ceil(range);
		BlockPos o = BlockPos.containing(origin);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		List<BlockPos> hits = new ArrayList<>();
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					pos.set(o.getX() + dx, o.getY() + dy, o.getZ() + dz);
					if (!down && pos.getY() < floorY - 0.01) {
						continue;
					}
					Vec3 to = Vec3.atCenterOf(pos).subtract(origin);
					double dist = to.length();
					if (dist > range || dist < 0.8 || to.scale(1.0 / dist).dot(dir) < cosHalf) {
						continue;
					}
					if (!level.isLoaded(pos) || !level.mayInteract(player, pos)) {
						continue;
					}
					if (HulkCombat.breakable(level, pos, level.getBlockState(pos))) {
						hits.add(pos.immutable());
					}
				}
			}
		}
		hits.sort(java.util.Comparator.comparingDouble(p -> Vec3.atCenterOf(p).distanceToSqr(origin)));
		int n = 0;
		for (BlockPos p : hits) {
			if (n >= max) {
				break;
			}
			BlockState st = level.getBlockState(p);
			if (n % 4 == 0) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5,
						6, 0.4, 0.4, 0.4, 0.25);
			}
			level.destroyBlock(p, HulkConfig.world().dropBrokenBlocks && n % 3 == 0, player);
			n++;
		}
	}

	// ---------------------------------------------------------------- Shift+R Ground Smash

	public static void groundSmash(ServerPlayer player) {
		if (!start(player, GROUND_SMASH, HulkConfig.abilities().groundSmashCooldownTicks, HulkState.ANIM_GROUND_SMASH)) {
			return;
		}
		AbilityHelpers.sound(player, SoundEvents.RAVAGER_ROAR, 0.8f, 0.9f);
		schedule(player, SMASH_IMPACT_TICKS, () -> groundSmashImpact(player));
	}

	/** Also the rampage's stomp ({@code HulkControl}). */
	static void groundSmashImpact(ServerPlayer player) {
		if (!Hulk.isHulk(player) || !player.isAlive()) {
			return;
		}
		HulkConfig.Abilities cfg = HulkConfig.abilities();
		Vec3 at = player.position();
		ServerLevel level = (ServerLevel) player.level();
		shockwave(player, at, cfg.groundSmashRadius, cfg.groundSmashDamage, cfg.groundSmashKnockback, cfg.groundSmashLift, 1.1f);
		HulkCombat.crater(player, at.add(flatLook(player).scale(1.5)).add(0, -0.5, 0), cfg.groundSmashCraterRadius, 80);
		GroundCracks.star(level, at.add(flatLook(player).scale(1.5)), cfg.groundSmashCrackRadius * WorldBreaker.aoe(player), 10, 140);
		BlockState ground = groundUnder(level, at);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.3, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, at.x, at.y + 0.3, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, ground), at.x, at.y + 0.2, at.z, 60,
				cfg.groundSmashRadius * 0.45, 0.1, cfg.groundSmashRadius * 0.45, 0.4);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 2.5f, 0.6f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 2.0f, 0.5f);
	}

	/** A ring of damage, knockback, lift and flying earth round {@code at}; also the leap landing and rampage stomps. */
	public static void shockwave(ServerPlayer player, Vec3 at, double radius, float damage, double knockback, double lift, float shake) {
		ServerLevel level = (ServerLevel) player.level();
		radius *= WorldBreaker.aoe(player); // v0.16.4 (user): World Breaker doubles the ring (smash, leap landing, rampage stomp)
		HulkCombat.radial(player, at, radius, new HulkCombat.Hit(damage, knockback, lift), true);
		BlockState ground = level.getBlockState(BlockPos.containing(at).below());
		if (ground.isAir()) {
			ground = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
		}
		BlockParticleOption dirt = new BlockParticleOption(ParticleTypes.BLOCK, ground);
		for (double r = 1.5; r <= radius; r += 1.5) {
			HulkCombat.ring(level, dirt, at.add(0, 0.2, 0), r, (int) (r * 8));
			level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, (int) (r * 4), r * 0.7, 0.1, r * 0.7, 0.02);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 3, 0.8, 0.1, 0.8, 0.0);
		level.sendParticles(GAMMA, at.x, at.y + 0.5, at.z, 25, radius * 0.4, 0.2, radius * 0.4, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.6f, 0.7f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0f, 0.5f);
		HulkCombat.shake(level, at, shake, 16, 24.0);
	}

	// ---------------------------------------------------------------- G Thunderclap

	public static void thunderclap(ServerPlayer player) {
		if (!start(player, THUNDERCLAP, HulkConfig.abilities().clapCooldownTicks, HulkState.ANIM_THUNDERCLAP)) {
			return;
		}
		if (airborne(player)) {
			// v0.16.4 (user): "make it so that the hulk can thunderclap mid-air so he can change his trajectory mid-air" -- in the
			// air the clap goes off at once (no wind-up), stops him dead and -- v0.16.4 (user: "shoot the hulk flying in the
			// direction opposite to where he aimed") -- the recoil hurls him the opposite way to his aim
			airRedirect(player);
			thunderclapImpact(player, true);
			return;
		}
		schedule(player, CLAP_IMPACT_TICKS, () -> thunderclapImpact(player, false));
	}

	/** v0.16.4: off the ground (not standing, swimming, flying or riding) -- where G becomes the air clap. */
	public static boolean airborne(ServerPlayer player) {
		if (player.onGround() || player.isInWater() || player.isInLava() || player.getAbilities().flying || player.isPassenger()) {
			return false;
		}
		// a mock / just-moved player may not have its onGround flag yet: nothing solid right under his feet either
		return player.level().noCollision(player, player.getBoundingBox().move(0.0, -0.3, 0.0));
	}

	/**
	 * v0.16.4 (user, "stop + redirect"): the air clap kills his momentum and the recoil shoots him ({@code airClapPush} blocks
	 * a tick) the OPPOSITE way to his look -- clap at the ground and he goes up, clap ahead and he flies back. Any direction.
	 * Mid Super Leap the synced launch velocity becomes the recoil, so the client holds that horizontal speed (no air drag)
	 * until he lands, like a leap; the leap itself stays open so its landing still counts as one and the fall is never felt. The state is saved BEFORE the motion packet goes out, so the
	 * client never re-applies the old leap speed over the new push. Returns the push.
	 */
	public static Vec3 airRedirect(ServerPlayer player) {
		HulkConfig.Abilities cfg = HulkConfig.abilities();
		HulkState s = Hulk.state(player);
		Vec3 push = player.getLookAngle().normalize().scale(-cfg.airClapPush);
		if (s.leaping) {
			HulkState n = s.copy();
			n.moves.leapVx = push.x;
			n.moves.leapVy = push.y;
			n.moves.leapVz = push.z;
			Hulk.save(player, n);
		}
		AbilityHelpers.launchSelf(player, push); // replaces his velocity outright (momentum gone), hurtMarked + motion packet
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, player.getBbHeight() * 0.5, 0);
		// the clap's burst on the side he aimed at (the recoil carries him away from it)
		Vec3 back = c.subtract(push.normalize().scale(1.5));
		level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, back.x, back.y, back.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.CLOUD, back.x, back.y, back.z, 24, 0.8, 0.8, 0.8, 0.08);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.6f, 0.6f);
		return push;
	}

	private static void thunderclapImpact(ServerPlayer player, boolean air) {
		if (!Hulk.isHulk(player) || !player.isAlive()) {
			return;
		}
		HulkConfig.Abilities cfg = HulkConfig.abilities();
		ServerLevel level = (ServerLevel) player.level();
		Vec3 origin = player.getEyePosition().subtract(0, player.getBbHeight() * 0.2, 0);
		Vec3 dir = player.getLookAngle().normalize();
		// v0.16.4: the air clap's cone is a bit smaller (range and damage x airClapScale)
		double airScale = air ? Math.max(0.0, cfg.airClapScale) : 1.0;
		double range = cfg.clapRange * WorldBreaker.aoe(player) * airScale;
		double cosHalf = Math.cos(Math.toRadians(cfg.clapConeDegrees * 0.5));
		float clapDamage = (float) (cfg.clapDamage * airScale);
		HulkCombat.Hit hit = new HulkCombat.Hit(clapDamage, 0.0, 0.0);
		for (LivingEntity e : cone(player, origin, dir, range, cosHalf)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(origin);
			double dist = to.length();
			float falloff = (float) (1.0 - 0.5 * Math.min(1.0, dist / range));
			if (hit.hit.add(e.getId()) && AbilityHelpers.hurtLands(player, e, clapDamage * falloff)) {
				if (!com.projecthero.mod.titanshifter.TitanCombat.isBoss(e)) {
					Vec3 push = (dist < 1.0e-3 ? dir : to.normalize()).scale(cfg.clapKnockback * falloff);
					AbilityHelpers.push(e, new Vec3(push.x, 0.35 * falloff, push.z));
				}
				// the stagger: deafened and reeling
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, cfg.clapStaggerTicks, 3);
				AbilityHelpers.applyControl(e, MobEffects.CONFUSION, cfg.clapStaggerTicks + 20, 0);
				if (e instanceof Mob mob) {
					mob.getNavigation().stop();
				}
			}
		}
		if (HulkCombat.canBreakBlocks(level)) {
			shatterFragile(level, player, origin, dir, range, cosHalf, (int) Math.round(250 * WorldBreaker.destruction(player)));
		}
		Vec3 hands = player.getEyePosition().add(dir.scale(1.2)).subtract(0, player.getBbHeight() * 0.25, 0);
		level.sendParticles(ParticleTypes.EXPLOSION, hands.x, hands.y, hands.z, 2, 0.2, 0.2, 0.2, 0.0);
		level.sendParticles(ParticleTypes.FLASH, hands.x, hands.y, hands.z, 1, 0, 0, 0, 0);
		// the sound wave itself: sonic rings rolling out, widening with the cone
		for (double d = 1.5; d <= range; d += 1.5) {
			Vec3 p = hands.add(dir.scale(d));
			double spread = d * Math.tan(Math.toRadians(cfg.clapConeDegrees * 0.5)) * 0.6;
			level.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 8, spread, spread * 0.5, spread, 0.03);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 2, spread * 0.5, spread * 0.3, spread * 0.5, 0.0);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2.0f, 1.3f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.4f, 1.7f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8f, 1.8f);
		HulkCombat.shake(level, player.position(), 0.5f, 10, 24.0);
	}

	/** Glass, panes, ice, leaves, flowers and other soft plants inside the cone shatter. */
	private static void shatterFragile(ServerLevel level, ServerPlayer player, Vec3 origin, Vec3 dir, double range, double cosHalf, int cap) {
		int broken = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int r = (int) Math.ceil(range);
		BlockPos o = BlockPos.containing(origin);
		for (int dx = -r; dx <= r && broken < cap; dx++) {
			for (int dy = -r; dy <= r && broken < cap; dy++) {
				for (int dz = -r; dz <= r && broken < cap; dz++) {
					pos.set(o.getX() + dx, o.getY() + dy, o.getZ() + dz);
					Vec3 to = Vec3.atCenterOf(pos).subtract(origin);
					double dist = to.length();
					if (dist > range || dist < 0.5 || to.normalize().dot(dir) < cosHalf) {
						continue;
					}
					if (isFragile(level, pos, level.getBlockState(pos)) && level.mayInteract(player, pos)) {
						level.destroyBlock(pos, HulkConfig.world().dropBrokenBlocks, player);
						broken++;
					}
				}
			}
		}
	}

	public static boolean isFragile(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.isAir() || state.hasBlockEntity()) {
			return false;
		}
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness < 0.0f) {
			return false;
		}
		if (state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SNOW)) {
			return true;
		}
		if (state.getSoundType() == SoundType.GLASS && hardness <= 0.5f) {
			return true; // glass, panes, ice, glowstone
		}
		return state.canBeReplaced() && state.getFluidState().isEmpty(); // grass, ferns, vines, dead bushes...
	}

	// ---------------------------------------------------------------- Shift+G Fault-line Stomp

	public static void faultStomp(ServerPlayer player) {
		if (!start(player, FAULT_STOMP, HulkConfig.abilities().faultCooldownTicks, HulkState.ANIM_FAULT_STOMP)) {
			return;
		}
		AbilityHelpers.sound(player, SoundEvents.RAVAGER_STEP, 1.5f, 0.5f);
		schedule(player, FAULT_IMPACT_TICKS, () -> faultStompImpact(player));
	}

	private static void faultStompImpact(ServerPlayer player) {
		if (!Hulk.isHulk(player) || !player.isAlive()) {
			return;
		}
		HulkConfig.Abilities cfg = HulkConfig.abilities();
		ServerLevel level = (ServerLevel) player.level();
		Vec3 dir = flatLook(player);
		Vec3 origin = player.position().add(dir.scale(player.getBbWidth() * 0.5 + 0.5));
		HulkCombat.Hit hit = new HulkCombat.Hit(cfg.faultDamage, 0.3, cfg.faultLaunch);
		GroundCracks.star(level, player.position(), 2.5, 6, 100);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 2.0f, 0.5f);
		HulkCombat.shake(level, player.position(), 0.6f, 14, 24.0);
		// the fissure races off along the ground: one block every faultTicksPerBlock ticks
		int steps = (int) Math.ceil(cfg.faultLength * WorldBreaker.aoe(player));
		for (int i = 0; i <= steps; i++) {
			double d = i;
			int delay = (int) Math.round(i * cfg.faultTicksPerBlock);
			Runnable step = () -> faultStep(player, origin, dir, d, hit);
			if (delay <= 0) {
				step.run();
			} else {
				schedule(player, delay, step);
			}
		}
	}

	private static void faultStep(ServerPlayer player, Vec3 origin, Vec3 dir, double d, HulkCombat.Hit hit) {
		if (!player.isAlive()) {
			return;
		}
		HulkConfig.Abilities cfg = HulkConfig.abilities();
		ServerLevel level = (ServerLevel) player.level();
		double width = cfg.faultWidth * WorldBreaker.aoe(player);
		Vec3 p = origin.add(dir.scale(d));
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		// a jagged crack, wide enough to see from above, wandering a little side to side
		double wobble = Math.sin(d * 1.7) * 0.6;
		Vec3 c = p.add(side.scale(wobble));
		BlockPos s = null;
		for (double w = -width * 0.5; w <= width * 0.5 + 1.0e-3; w += 0.9) {
			BlockPos at = GroundCracks.crackAt(level, c.add(side.scale(w)), Math.abs(w) < 0.6 ? 9 : 6, 140);
			if (at != null && Math.abs(w) < 0.6) {
				s = at;
			}
		}
		double groundY = s == null ? p.y : s.getY() + 1.0;
		BlockState ground = s == null ? net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState() : level.getBlockState(s);
		Vec3 burst = new Vec3(c.x, groundY, c.z);
		level.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, ground), burst.x, burst.y, burst.z, 12, 0.5, 0.0, 0.5, 0.4);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), burst.x, burst.y + 0.3, burst.z, 14, 0.6, 0.3, 0.6, 0.3);
		level.sendParticles(ParticleTypes.DUST_PLUME, burst.x, burst.y + 0.2, burst.z, 4, 0.5, 0.1, 0.5, 0.05);
		if (((int) d) % 3 == 0) {
			level.playSound(null, burst.x, burst.y, burst.z, SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 1.6f, 0.5f);
			level.playSound(null, burst.x, burst.y, burst.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6f, 1.4f);
		}
		// everything standing on it is thrown up
		double half = width * 0.5 + 0.5;
		net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(c.x - half - 1, groundY - 2.0, c.z - half - 1,
				c.x + half + 1, groundY + 2.5, c.z + half + 1);
		for (LivingEntity e : HulkCombat.targets(player, box)) {
			double rx = e.getX() - c.x;
			double rz = e.getZ() - c.z;
			if (Math.abs(rx * dir.z - rz * dir.x) > half + e.getBbWidth() * 0.5 || Math.abs(rx * dir.x + rz * dir.z) > 1.2) {
				continue;
			}
			HulkCombat.strike(player, e, burst.subtract(dir), hit, 1.0f);
		}
	}

	// ---------------------------------------------------------------- Z Bulldozer Charge

	public static void bulldoze(ServerPlayer player) {
		HulkConfig.Abilities cfg = HulkConfig.abilities();
		int maxTicks = (int) Math.ceil(cfg.bulldozeBlocks / Math.max(0.1, cfg.bulldozeSpeed)) + 12;
		if (!start(player, BULLDOZE, cfg.bulldozeCooldownTicks + maxTicks, HulkState.ANIM_BULLDOZE)) {
			return;
		}
		HulkState n = Hulk.state(player).copy();
		n.combat.chargeUntil = player.level().getGameTime() + maxTicks;
		Hulk.save(player, n);
		DOZERS.put(player.getUUID(), new Dozer(player.position(), flatLook(player), new java.util.HashSet<>(),
				new HulkCombat.Hit(cfg.bulldozeDamage, 0.0, 0.0), drivenNow(player)));
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.8f, 0.8f);
		GroundCracks.star(level, player.position(), 2.0, 5, 60);
	}

	/** True while Z's charge runs (client-safe). */
	public static boolean charging(net.minecraft.world.entity.player.Player player) {
		HulkState s = player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.HULK_STATE, null);
		return s != null && s.hulk && s.combat.chargeUntil > player.level().getGameTime();
	}

	private static void tickBulldoze(ServerPlayer player, HulkState s, long now) {
		if (s.combat.chargeUntil <= 0L) {
			return;
		}
		Dozer dz = DOZERS.get(player.getUUID());
		if (dz == null || now >= s.combat.chargeUntil || !s.hulk || (s.rampaging(now) && !dz.rampage()) || player.isPassenger()
				|| player.isSpectator()
				|| horizontalDistance(player.position(), dz.start()) >= HulkConfig.abilities().bulldozeBlocks) {
			endCharge(player);
			return;
		}
		HulkConfig.Abilities cfg = HulkConfig.abilities();
		ServerLevel level = (ServerLevel) player.level();
		Vec3 dir = dz.dir();
		Vec3 vel = dir.scale(cfg.bulldozeSpeed);
		double vy = player.onGround() ? -0.05 : Math.max(player.getDeltaMovement().y - 0.08, -1.2);
		breakAhead(player, dir, true, cfg.bulldozeMaxHardness);
		AbilityHelpers.launchSelf(player, new Vec3(vel.x, vy, vel.z));
		player.setSprinting(true);
		// everything in front: hit once, then carried along on his shoulders until he stops
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		Vec3 front = player.position().add(dir.scale(player.getBbWidth() * 0.5 + 0.6)).add(0, player.getBbHeight() * 0.4, 0);
		double hitRadius = cfg.bulldozeHitRadius * WorldBreaker.aoe(player);
		for (LivingEntity e : HulkCombat.targets(player, player.getBoundingBox().inflate(hitRadius))) {
			Vec3 rel = e.position().subtract(player.position());
			double along = rel.x * dir.x + rel.z * dir.z;
			if (along < -0.3 || along > player.getBbWidth() * 0.5 + hitRadius) {
				continue;
			}
			if (dz.hit().hit.add(e.getId())) {
				AbilityHelpers.hurtLands(player, e, cfg.bulldozeDamage);
				level.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 12, 0.3, 0.3, 0.3, 0.3);
				level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2f, 0.6f);
			}
			if (!com.projecthero.mod.titanshifter.TitanCombat.isBoss(e)) {
				dz.carried().add(e.getId());
				double lat = rel.x * side.x + rel.z * side.z;
				Vec3 carry = front.add(side.scale(Math.max(-1.0, Math.min(1.0, lat)))).add(dir.scale(0.4));
				e.setDeltaMovement(vel.x + (carry.x - e.getX()) * 0.5, 0.12, vel.z + (carry.z - e.getZ()) * 0.5);
				e.hurtMarked = true;
				e.fallDistance = 0.0f;
			}
		}
		if (now % 2 == 0) {
			level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(), 5, 0.7, 0.1, 0.7, 0.02);
			BlockState ground = level.getBlockState(player.blockPosition().below());
			if (!ground.isAir()) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), player.getX(), player.getY() + 0.1, player.getZ(),
						8, 0.8, 0.1, 0.8, 0.15);
				GroundCracks.crackAt(level, player.position(), 5, 80);
			}
			level.sendParticles(ParticleTypes.GUST, front.x, front.y, front.z, 1, 0.3, 0.3, 0.3, 0.0);
		}
		if (now % 4 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_STEP, SoundSource.PLAYERS, 1.5f, 0.6f);
			HulkCombat.shake(level, player.position(), 0.3f, 6, 14.0);
		}
	}

	private static double horizontalDistance(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Ends Z's charge: whatever he carried is flung aside. Also from {@code Hulk} / {@code HulkControl}. */
	static void endCharge(ServerPlayer player) {
		Dozer dz = DOZERS.remove(player.getUUID());
		HulkState s = Hulk.state(player);
		if (s.combat.chargeUntil <= 0L) {
			return;
		}
		HulkState n = s.copy();
		n.combat.chargeUntil = 0L;
		n.abilityReadyAt.put(BULLDOZE, player.level().getGameTime() + HulkConfig.abilities().bulldozeCooldownTicks);
		Hulk.save(player, n);
		player.setSprinting(false);
		if (dz == null) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		Vec3 side = new Vec3(-dz.dir().z, 0, dz.dir().x);
		for (int id : dz.carried()) {
			if (level.getEntity(id) instanceof LivingEntity e && e.isAlive()) {
				Vec3 rel = e.position().subtract(player.position());
				double sign = rel.x * side.x + rel.z * side.z >= 0 ? 1.0 : -1.0;
				AbilityHelpers.push(e, side.scale(sign * 1.6).add(dz.dir().scale(0.8)).add(0, 0.5, 0));
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0, 0, 0, 0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_BIG_FALL, SoundSource.PLAYERS, 1.5f, 0.5f);
	}

	/**
	 * Smashes the soft blocks right in front of him, from his feet to the top of his head. {@code forced}: the charge
	 * (and the rampage) break through whatever is there; otherwise only when the Sprint Smash passive applies.
	 */
	static int breakAhead(ServerPlayer player, Vec3 fwd, boolean forced) {
		return breakAhead(player, fwd, forced, Float.MAX_VALUE);
	}

	static int breakAhead(ServerPlayer player, Vec3 fwd, boolean forced, float maxHardness) {
		ServerLevel level = (ServerLevel) player.level();
		HulkConfig.World w = HulkConfig.world();
		if (!HulkCombat.canBreakBlocks(level) || (!forced && !w.sprintSmashEnabled)) {
			return 0;
		}
		Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
		double half = player.getBbWidth() * 0.5;
		double reach = half + 0.8;
		int height = (int) Math.ceil(player.getBbHeight());
		int broken = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (double s2 = -half; s2 <= half + 1.0e-3; s2 += 0.5) {
			for (int y = 0; y < height; y++) {
				Vec3 p = player.position().add(fwd.scale(reach)).add(side.scale(s2)).add(0, y + 0.5, 0);
				pos.set(p.x, p.y, p.z);
				BlockState state = level.getBlockState(pos);
				if (!HulkCombat.breakable(level, pos, state) || state.getDestroySpeed(level, pos) > maxHardness) {
					continue;
				}
				if (state.getCollisionShape(level, pos).isEmpty() && !isFragile(level, pos, state)
						&& !state.is(net.minecraft.world.level.block.Blocks.COBWEB)) { // v0.13.17: he tears through webs
					continue;
				}
				level.destroyBlock(pos, w.dropBrokenBlocks, player);
				broken++;
			}
		}
		if (broken > 0 && player.tickCount % 4 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.PLAYERS, 0.5f, 0.7f);
			HulkCombat.shake(level, player.position(), 0.2f, 4, 12.0);
		}
		return broken;
	}

	/** Sprint Smash: sprinting as the Hulk into soft blocks breaks them (config-switched). */
	private static void tickSprintSmash(ServerPlayer player, HulkState s) {
		if (!player.isSprinting() || player.isPassenger() || s.combat.chargeUntil > 0L) {
			LAST_POS.remove(player.getUUID());
			return;
		}
		// movement is client-driven: measure the speed from how far he actually moved since the last tick
		Vec3 last = LAST_POS.put(player.getUUID(), player.position());
		double speed = last == null ? 0.0 : Math.sqrt(Math.pow(player.getX() - last.x, 2) + Math.pow(player.getZ() - last.z, 2));
		if (speed < HulkConfig.abilities().sprintSmashMinSpeed) {
			return;
		}
		breakAhead(player, flatLook(player), false);
	}

	// ---------------------------------------------------------------- Shift+Z Launch Uppercut

	public static void uppercut(ServerPlayer player) {
		if (!canAct(player) || (!drivenNow(player) && !ready(player, UPPERCUT)) || busy(player)) {
			return;
		}
		LivingEntity target = uppercutTarget(player);
		if (target == null) {
			Hulk.say(player, "message.projecthero.hulk.no_target", ChatFormatting.GRAY);
			return;
		}
		start(player, UPPERCUT, HulkConfig.abilities().uppercutCooldownTicks, HulkState.ANIM_UPPERCUT);
		AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.5f);
		schedule(player, UPPERCUT_IMPACT_TICKS, () -> uppercutImpact(player, target));
	}

	/** The mob he is looking at, or the nearest one in front within reach. */
	static LivingEntity uppercutTarget(ServerPlayer player) {
		double range = HulkConfig.abilities().uppercutRange + player.getBbWidth() * 0.5;
		LivingEntity aimed = AbilityHelpers.raycastEntity(player, range + 1.0);
		if (aimed != null && HulkCombat.targets(player, aimed.getBoundingBox().inflate(0.1)).contains(aimed)
				&& Math.sqrt(AbilityHelpers.distanceSqToBox(aimed, chest(player))) <= range) {
			return aimed;
		}
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : cone(player, chest(player), flatLook(player), range, Math.cos(Math.toRadians(60)))) {
			double d = e.distanceToSqr(player);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	private static void uppercutImpact(ServerPlayer player, LivingEntity target) {
		if (!Hulk.isHulk(player) || !player.isAlive() || !target.isAlive()
				|| target.distanceToSqr(player) > Math.pow(HulkConfig.abilities().uppercutRange + player.getBbWidth() + 2.0, 2)) {
			return;
		}
		HulkConfig.Abilities cfg = HulkConfig.abilities();
		ServerLevel level = (ServerLevel) player.level();
		target.invulnerableTime = 0;
		boolean landed = AbilityHelpers.hurtLands(player, target, cfg.uppercutDamage);
		if (landed && !com.projecthero.mod.titanshifter.TitanCombat.isBoss(target)) {
			target.setDeltaMovement(target.getDeltaMovement().x * 0.2, cfg.uppercutLaunch, target.getDeltaMovement().z * 0.2);
			target.hurtMarked = true;
			target.fallDistance = 0.0f;
			// the trail it leaves going up
			for (int i = 1; i <= 12; i += 2) {
				schedule(player, i, () -> {
					if (target.isAlive()) {
						level.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY(), target.getZ(), 3, 0.2, 0.2, 0.2, 0.01);
					}
				});
			}
		}
		Vec3 c = target.position().add(0, target.getBbHeight() * 0.4, 0);
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 25, 0.4, 0.4, 0.4, 0.5);
		level.sendParticles(ParticleTypes.GUST, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		GroundCracks.star(level, target.position(), 2.0, 5, 80);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.5f, 0.5f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.IRON_GOLEM_ATTACK, SoundSource.PLAYERS, 1.5f, 0.6f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.2f, 0.7f);
		HulkCombat.shake(level, player.position(), 0.4f, 8, 16.0);
	}

	// ---------------------------------------------------------------- X Super Leap (HulkLeap)

	public static void beginLeap(ServerPlayer player) {
		if (busy(player) && Hulk.state(player).leapChargeStart <= 0L) {
			return;
		}
		HulkLeap.begin(player);
	}

	public static void releaseLeap(ServerPlayer player) {
		HulkLeap.release(player);
	}

	/** Charge 0..1 of the current Super Leap (client-safe). */
	public static float leapCharge(net.minecraft.world.entity.player.Player player) {
		return HulkLeap.charge(player);
	}

	/** Throws the Hulk {@code blocks} along {@code dir} (also used by the rampage). Mutates {@code n}; the caller saves it. */
	static void launch(ServerPlayer player, HulkState n, double blocks, Vec3 dir) {
		HulkLeap.launch(player, n, blocks, dir);
	}

	/** @deprecated v0.16.4: HULK SMASH left the kit -- always 0. */
	@Deprecated
	public static float hulkSmashCharge(net.minecraft.world.entity.player.Player player) {
		return 0.0f;
	}

	// ---------------------------------------------------------------- C Gamma Burst

	public static void gammaBurst(ServerPlayer player) {
		HulkConfig.Abilities cfg = HulkConfig.abilities();
		if (!canAct(player) || !ready(player, GAMMA_BURST) || busy(player)) {
			return;
		}
		float rage = Hulk.state(player).rage;
		if (rage < cfg.gammaBurstRageCost) {
			Hulk.say(player, "message.projecthero.hulk.gamma_burst_rage", ChatFormatting.GRAY, Math.round(cfg.gammaBurstRageCost));
			return;
		}
		start(player, GAMMA_BURST, cfg.gammaBurstCooldownTicks, HulkState.ANIM_GAMMA_BURST);
		Hulk.setRage(player, Math.max(0.0f, rage - cfg.gammaBurstRageCost));
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 2.0f, 0.7f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5f, 0.6f);
		// the gamma gathers in him for the wind-up
		for (int i = 0; i < GAMMA_BURST_IMPACT_TICKS; i += 2) {
			schedule(player, i, () -> {
				double h = player.getBbHeight();
				level.sendParticles(GAMMA, player.getX(), player.getY() + h * 0.5, player.getZ(), 14, 1.5, h * 0.5, 1.5, -0.08);
				level.sendParticles(ParticleTypes.GLOW, player.getX(), player.getY() + h * 0.5, player.getZ(), 4, 0.6, h * 0.4, 0.6, 0.02);
			});
		}
		schedule(player, GAMMA_BURST_IMPACT_TICKS, () -> gammaBurstImpact(player));
	}

	private static void gammaBurstImpact(ServerPlayer player) {
		if (!Hulk.isHulk(player) || !player.isAlive()) {
			return;
		}
		HulkConfig.Abilities cfg = HulkConfig.abilities();
		ServerLevel level = (ServerLevel) player.level();
		Vec3 at = player.position();
		double h = player.getBbHeight();
		Vec3 core = at.add(0, h * 0.5, 0);
		double radius = cfg.gammaBurstRadius * WorldBreaker.aoe(player);
		HulkCombat.radial(player, core, radius, new HulkCombat.Hit(cfg.gammaBurstDamage, cfg.gammaBurstKnockback, 0.5), true);
		level.sendParticles(ParticleTypes.FLASH, core.x, core.y, core.z, 2, 0, 0, 0, 0);
		level.sendParticles(GAMMA_BRIGHT, core.x, core.y, core.z, 60, 0.8, h * 0.4, 0.8, 0.3);
		// the blast rolls out as a glowing green shell over a few ticks
		int waves = 5;
		for (int k = 0; k < waves; k++) {
			double r = radius * (k + 1) / waves;
			Runnable wave = () -> {
				HulkCombat.ring(level, GAMMA, at.add(0, 0.3, 0), r, (int) (r * 7));
				HulkCombat.ring(level, GAMMA_DEEP, at.add(0, h * 0.5, 0), r * 0.9, (int) (r * 5));
				for (int i = 0; i < (int) (r * 6); i++) {
					double th = Math.random() * Math.PI * 2;
					double ph = Math.random() * Math.PI * 0.5;
					level.sendParticles(GAMMA_BRIGHT, at.x + Math.cos(th) * Math.cos(ph) * r, at.y + 0.3 + Math.sin(ph) * r * 0.7,
							at.z + Math.sin(th) * Math.cos(ph) * r, 1, 0.1, 0.1, 0.1, 0.0);
				}
			};
			if (k == 0) {
				wave.run();
			} else {
				schedule(player, k, wave);
			}
		}
		for (int y = 0; y < 10; y++) {
			level.sendParticles(GAMMA, at.x, at.y + y, at.z, 4, 0.5, 0.3, 0.5, 0.02);
		}
		GroundCracks.star(level, at, radius * 0.6, 8, 100);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2.5f, 0.6f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.5f, 0.6f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 2.0f, 0.5f);
		HulkCombat.shake(level, at, 0.9f, 18, 32.0);
	}

	// ---------------------------------------------------------------- Shift+C Earth Wall

	public static void earthWall(ServerPlayer player) {
		if (!start(player, EARTH_WALL, HulkConfig.abilities().earthWallCooldownTicks, HulkState.ANIM_EARTH_WALL)) {
			return;
		}
		AbilityHelpers.sound(player, SoundEvents.RAVAGER_ROAR, 0.7f, 1.0f);
		schedule(player, EARTH_WALL_IMPACT_TICKS, () -> {
			if (Hulk.isHulk(player) && player.isAlive() && !HulkEarthWall.raise(player)) {
				Hulk.say(player, "message.projecthero.hulk.no_earth", ChatFormatting.GRAY);
				cooldown(player, EARTH_WALL, 20); // nothing to tear up: most of the cooldown back
			}
		});
	}

	// ---------------------------------------------------------------- tick

	/** Every Gamma player, every tick, from {@link Hulk#tick}. */
	static void tick(ServerPlayer player) {
		long now = player.level().getGameTime();
		List<Task> tasks = TASKS.get(player.getUUID());
		if (tasks != null && !tasks.isEmpty()) {
			List<Task> due = new ArrayList<>();
			tasks.removeIf(t -> {
				if (t.at() <= now) {
					due.add(t);
					return true;
				}
				return false;
			});
			due.forEach(t -> t.run().run());
		}
		HulkState s = Hulk.state(player);
		if (!s.hulk) {
			if (s.leapChargeStart > 0L || s.leaping || s.combat.smashChargeStart > 0L || s.combat.chargeUntil > 0L) {
				HulkState n = s.copy();
				n.leapChargeStart = 0L;
				n.leaping = false;
				n.combat.smashChargeStart = 0L;
				n.combat.chargeUntil = 0L;
				Hulk.save(player, n);
			}
			DOZERS.remove(player.getUUID());
			return;
		}
		HulkLeap.tick(player, now);
		s = Hulk.state(player);
		tickBulldoze(player, s, now);
		s = Hulk.state(player);
		tickSprintSmash(player, s);
	}
}
