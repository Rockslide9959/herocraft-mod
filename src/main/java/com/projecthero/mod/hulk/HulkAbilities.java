package com.projecthero.mod.hulk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hulk.data.HulkState;
import com.projecthero.mod.network.TitanShakePayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * The Hulk's four abilities (v0.13.12, Phase 2). Only the Hulk himself can use them -- Banner gets a nudge to
 * get angry first. Every number is in {@link HulkConfig#abilities()} / {@link HulkConfig#world()}.
 *
 * <pre>
 *   R  Thunderclap   -- a cone shockwave in front of him: damage + knockback, shatters fragile blocks
 *   G  Ground Smash  -- both fists into the ground: a ring of damage, knockback and flying earth
 *   X  Super Leap    -- hold to charge, release to launch; the landing is a small shockwave
 *   C  Sprint Smash  -- toggles charging through soft blocks while sprinting (server switch in the config)
 * </pre>
 *
 * The hits land on the animation's impact frame through a tiny per-player task queue (the same idea as
 * {@code AllMightAbilities}); the animation itself is announced to every client through
 * {@link HulkState#animId} / {@link HulkState#animStart}.
 */
public final class HulkAbilities {
	public static final String THUNDERCLAP = "thunderclap";
	public static final String GROUND_SMASH = "ground_smash";
	public static final String SUPER_LEAP = "super_leap";

	/** Ticks from the key press to the clap / the fists hitting the ground (matches the animations). */
	public static final int CLAP_IMPACT_TICKS = 6;
	public static final int SMASH_IMPACT_TICKS = 10;

	private static final DustParticleOptions GAMMA = new DustParticleOptions(new Vector3f(0.35f, 1.0f, 0.25f), 1.4f);

	private record Task(long at, Runnable run) {
	}

	private static final Map<UUID, List<Task>> TASKS = new ConcurrentHashMap<>();
	/** Game time the current Super Leap took off (landing is only checked a few ticks after). */
	private static final Map<UUID, Long> LEAP_TAKEOFF = new ConcurrentHashMap<>();
	/** Last tick's position, for Sprint Smash's speed check. */
	private static final Map<UUID, Vec3> LAST_POS = new ConcurrentHashMap<>();

	private HulkAbilities() {
	}

	public static void clearSessionState() {
		TASKS.clear();
		LEAP_TAKEOFF.clear();
		LAST_POS.clear();
	}

	public static void clear(UUID id) {
		TASKS.remove(id);
		LEAP_TAKEOFF.remove(id);
		LAST_POS.remove(id);
	}

	// ---------------------------------------------------------------- cooldowns

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

	private static void cooldown(ServerPlayer player, String id, int ticks) {
		HulkState n = Hulk.state(player).copy();
		n.abilityReadyAt.put(id, player.level().getGameTime() + ticks);
		Hulk.save(player, n);
	}

	private static void anim(ServerPlayer player, int animId) {
		HulkState n = Hulk.state(player).copy();
		n.animId = animId;
		n.animStart = player.level().getGameTime();
		Hulk.save(player, n);
	}

	private static void schedule(ServerPlayer player, int delay, Runnable run) {
		TASKS.computeIfAbsent(player.getUUID(), k -> new ArrayList<>())
				.add(new Task(player.level().getGameTime() + delay, run));
	}

	/** Only the Hulk -- Banner is told to get angry first. */
	private static boolean canAct(ServerPlayer player) {
		if (!Hulk.isHulk(player)) {
			Hulk.say(player, "message.projecthero.hulk.banner_cannot", ChatFormatting.GRAY);
			return false;
		}
		return player.isAlive() && !player.isSpectator();
	}

	// ---------------------------------------------------------------- Thunderclap

	public static void thunderclap(ServerPlayer player) {
		if (!canAct(player) || !ready(player, THUNDERCLAP)) {
			return;
		}
		HulkConfig.Abilities cfg = HulkConfig.abilities();
		cooldown(player, THUNDERCLAP, cfg.thunderclapCooldownTicks);
		anim(player, HulkState.ANIM_CLAP);
		schedule(player, CLAP_IMPACT_TICKS, () -> thunderclapImpact(player));
	}

	private static void thunderclapImpact(ServerPlayer player) {
		if (!Hulk.isHulk(player) || !player.isAlive()) {
			return;
		}
		HulkConfig.Abilities cfg = HulkConfig.abilities();
		ServerLevel level = (ServerLevel) player.level();
		Vec3 origin = player.getEyePosition().subtract(0, player.getBbHeight() * 0.2, 0);
		Vec3 dir = player.getLookAngle().normalize();
		double range = cfg.thunderclapRange;
		double cosHalf = Math.cos(Math.toRadians(cfg.thunderclapConeDegrees * 0.5));

		for (LivingEntity e : AbilityHelpers.enemiesAround(player, origin, range)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(origin);
			double dist = to.length();
			if (dist < 1.0e-3 || to.normalize().dot(dir) < cosHalf) {
				continue;
			}
			float falloff = (float) (1.0 - 0.5 * Math.min(1.0, dist / range));
			if (AbilityHelpers.hurtLands(player, e, cfg.thunderclapDamage * falloff)) {
				Vec3 push = to.normalize().scale(cfg.thunderclapKnockback * falloff).add(0, 0.35 * falloff, 0);
				if (!com.projecthero.mod.titanshifter.TitanCombat.isBoss(e)) {
					AbilityHelpers.push(e, push);
				}
			}
		}
		if (canBreakBlocks(level)) {
			shatterFragile(level, player, origin, dir, range, cosHalf);
		}

		// the shockwave rolling out of his hands
		Vec3 hands = player.getEyePosition().add(dir.scale(1.2)).subtract(0, player.getBbHeight() * 0.25, 0);
		level.sendParticles(ParticleTypes.EXPLOSION, hands.x, hands.y, hands.z, 2, 0.2, 0.2, 0.2, 0.0);
		for (double d = 2.0; d <= range; d += 2.0) {
			Vec3 p = hands.add(dir.scale(d));
			double spread = d * Math.tan(Math.toRadians(cfg.thunderclapConeDegrees * 0.5)) * 0.6;
			level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 10, spread, spread * 0.5, spread, 0.02);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 2, spread * 0.5, spread * 0.3, spread * 0.5, 0.0);
		}
		level.sendParticles(ParticleTypes.SONIC_BOOM, hands.x + dir.x * 3, hands.y + dir.y * 3, hands.z + dir.z * 3, 1, 0, 0, 0, 0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.4f, 1.6f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8f, 1.8f);
		shake(level, player.position(), 0.5f, 10);
	}

	/** Glass, panes, ice, leaves, flowers and other soft plants inside the cone shatter. */
	private static void shatterFragile(ServerLevel level, ServerPlayer player, Vec3 origin, Vec3 dir, double range, double cosHalf) {
		int broken = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int r = (int) Math.ceil(range);
		BlockPos o = BlockPos.containing(origin);
		for (int dx = -r; dx <= r && broken < 250; dx++) {
			for (int dy = -r; dy <= r && broken < 250; dy++) {
				for (int dz = -r; dz <= r && broken < 250; dz++) {
					pos.set(o.getX() + dx, o.getY() + dy, o.getZ() + dz);
					Vec3 to = Vec3.atCenterOf(pos).subtract(origin);
					double dist = to.length();
					if (dist > range || dist < 0.5 || to.normalize().dot(dir) < cosHalf) {
						continue;
					}
					BlockState state = level.getBlockState(pos);
					if (isFragile(level, pos, state)) {
						level.destroyBlock(pos, HulkConfig.world().dropBrokenBlocks, player);
						broken++;
					}
				}
			}
		}
	}

	static boolean isFragile(ServerLevel level, BlockPos pos, BlockState state) {
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

	// ---------------------------------------------------------------- Ground Smash

	public static void groundSmash(ServerPlayer player) {
		if (!canAct(player) || !ready(player, GROUND_SMASH)) {
			return;
		}
		cooldown(player, GROUND_SMASH, HulkConfig.abilities().groundSmashCooldownTicks);
		anim(player, HulkState.ANIM_SMASH);
		schedule(player, SMASH_IMPACT_TICKS, () -> groundSmashImpact(player));
	}

	private static void groundSmashImpact(ServerPlayer player) {
		if (!Hulk.isHulk(player) || !player.isAlive()) {
			return;
		}
		HulkConfig.Abilities cfg = HulkConfig.abilities();
		shockwave(player, player.position(), cfg.groundSmashRadius, cfg.groundSmashDamage, cfg.groundSmashKnockback,
				cfg.groundSmashLift, 0.9f);
	}

	/** A ring of damage, knockback, lift and flying earth around {@code at}; also the leap landing. */
	private static void shockwave(ServerPlayer player, Vec3 at, double radius, float damage, double knockback, double lift, float shake) {
		ServerLevel level = (ServerLevel) player.level();
		for (LivingEntity e : AbilityHelpers.enemiesAround(player, at, radius)) {
			double dist = Math.sqrt(AbilityHelpers.distanceSqToBox(e, at));
			float falloff = (float) (1.0 - 0.5 * Math.min(1.0, dist / radius));
			if (AbilityHelpers.hurtLands(player, e, damage * falloff)) {
				if (!com.projecthero.mod.titanshifter.TitanCombat.isBoss(e)) {
					AbilityHelpers.knockbackFrom(e, at, knockback * falloff);
					AbilityHelpers.push(e, new Vec3(0, lift * falloff, 0));
				}
			}
		}
		BlockState ground = level.getBlockState(BlockPos.containing(at).below());
		if (ground.isAir()) {
			ground = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
		}
		BlockParticleOption dirt = new BlockParticleOption(ParticleTypes.BLOCK, ground);
		for (double r = 1.5; r <= radius; r += 1.5) {
			int points = (int) (r * 8);
			for (int i = 0; i < points; i++) {
				double a = Math.PI * 2 * i / points;
				double x = at.x + Math.cos(a) * r;
				double z = at.z + Math.sin(a) * r;
				level.sendParticles(dirt, x, at.y + 0.2, z, 3, 0.15, 0.1, 0.15, 0.15);
			}
			level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, (int) (r * 4), r * 0.7, 0.1, r * 0.7, 0.02);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 3, 0.8, 0.1, 0.8, 0.0);
		level.sendParticles(GAMMA, at.x, at.y + 0.5, at.z, 25, radius * 0.4, 0.2, radius * 0.4, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.6f, 0.7f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0f, 0.5f);
		shake(level, at, shake, 16);
	}

	// ---------------------------------------------------------------- Super Leap

	/** X pressed: start charging (on the ground only). */
	public static void beginLeap(ServerPlayer player) {
		if (!canAct(player)) {
			return;
		}
		HulkState s = Hulk.state(player);
		if (s.leapChargeStart > 0L || !ready(player, SUPER_LEAP)) {
			return;
		}
		if (!player.onGround()) {
			Hulk.say(player, "message.projecthero.hulk.leap_ground", ChatFormatting.GRAY);
			return;
		}
		HulkState n = s.copy();
		n.leapChargeStart = player.level().getGameTime();
		n.animId = HulkState.ANIM_LEAP_CHARGE;
		n.animStart = n.leapChargeStart;
		Hulk.save(player, n);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_STEP, SoundSource.PLAYERS, 1.0f, 0.6f);
	}

	/** Charge 0..1 of the current Super Leap (client-safe). */
	public static float leapCharge(net.minecraft.world.entity.player.Player player) {
		HulkState s = player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.HULK_STATE, null);
		if (s == null || s.leapChargeStart <= 0L) {
			return 0.0f;
		}
		return Math.min(1.0f, (player.level().getGameTime() - s.leapChargeStart) / (float) Math.max(1, HulkConfig.abilities().leapMaxChargeTicks));
	}

	/** X released: launch along the look direction, farther the longer it was held. */
	public static void releaseLeap(ServerPlayer player) {
		HulkState s = Hulk.state(player);
		if (s.leapChargeStart <= 0L) {
			return;
		}
		float charge = leapCharge(player);
		HulkState n = s.copy();
		n.leapChargeStart = 0L;
		if (!Hulk.isHulk(player) || !player.isAlive()) {
			Hulk.save(player, n);
			return;
		}
		HulkConfig.Abilities cfg = HulkConfig.abilities();
		double blocks = cfg.leapMinBlocks + (cfg.leapMaxBlocks - cfg.leapMinBlocks) * charge;
		// the launch maths works on a normal-sized body: the Hulk's own scale does not change gravity or drag
		Vec3 v = AbilityHelpers.ballisticLaunch(player.getLookAngle(), blocks, player.onGround());
		n.leaping = true;
		n.animId = HulkState.ANIM_LEAP;
		n.animStart = player.level().getGameTime();
		n.abilityReadyAt.put(SUPER_LEAP, n.animStart + cfg.leapCooldownTicks);
		Hulk.save(player, n);
		LEAP_TAKEOFF.put(player.getUUID(), n.animStart);
		AbilityHelpers.launchSelf(player, v);

		ServerLevel level = (ServerLevel) player.level();
		BlockState ground = level.getBlockState(player.blockPosition().below());
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), player.getX(), player.getY() + 0.1, player.getZ(),
					40, 1.0, 0.1, 1.0, 0.2);
		}
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(), 20, 0.8, 0.1, 0.8, 0.05);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 1.4f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.6f, 1.2f);
	}

	// ---------------------------------------------------------------- Sprint Smash

	/** C: the player's own Sprint Smash switch. */
	public static void toggleSprintSmash(ServerPlayer player) {
		if (!Hulk.hasPower(player)) {
			return;
		}
		if (!HulkConfig.world().sprintSmashEnabled || !HulkConfig.world().blockBreaking) {
			Hulk.say(player, "message.projecthero.hulk.sprint_smash_disabled", ChatFormatting.GRAY);
			return;
		}
		HulkState n = Hulk.state(player).copy();
		n.sprintSmash = !n.sprintSmash;
		Hulk.save(player, n);
		Hulk.say(player, n.sprintSmash ? "message.projecthero.hulk.sprint_smash_on" : "message.projecthero.hulk.sprint_smash_off",
				n.sprintSmash ? ChatFormatting.GREEN : ChatFormatting.GRAY);
	}

	private static void tickSprintSmash(ServerPlayer player, HulkState s) {
		HulkConfig.World w = HulkConfig.world();
		if (!s.sprintSmash || !w.sprintSmashEnabled || !player.isSprinting() || player.isPassenger()) {
			LAST_POS.remove(player.getUUID());
			return;
		}
		// movement is client-driven, so measure the speed from how far he actually moved since the last tick
		Vec3 last = LAST_POS.put(player.getUUID(), player.position());
		double speed = last == null ? 0.0 : Math.sqrt(Math.pow(player.getX() - last.x, 2) + Math.pow(player.getZ() - last.z, 2));
		ServerLevel level = (ServerLevel) player.level();
		if (speed < HulkConfig.abilities().sprintSmashMinSpeed || !canBreakBlocks(level)) {
			return;
		}
		// the look direction flattened: where he is charging
		Vec3 look = player.getLookAngle();
		Vec3 fwd = new Vec3(look.x, 0, look.z);
		if (fwd.lengthSqr() < 1.0e-4) {
			return;
		}
		fwd = fwd.normalize();
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
				if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()) {
					continue;
				}
				float hardness = state.getDestroySpeed(level, pos);
				if (hardness < 0.0f || hardness > w.maxBreakableHardness) {
					continue;
				}
				if (state.getCollisionShape(level, pos).isEmpty() && !isFragile(level, pos, state)) {
					continue;
				}
				level.destroyBlock(pos, w.dropBrokenBlocks, player);
				broken++;
			}
		}
		if (broken > 0 && player.tickCount % 4 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.PLAYERS, 0.5f, 0.7f);
			shake(level, player.position(), 0.2f, 4);
		}
	}

	static boolean canBreakBlocks(ServerLevel level) {
		HulkConfig.World w = HulkConfig.world();
		return w.blockBreaking && (!w.respectMobGriefing || level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING));
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
			if (s.leapChargeStart > 0L || s.leaping) {
				HulkState n = s.copy();
				n.leapChargeStart = 0L;
				n.leaping = false;
				Hulk.save(player, n);
			}
			return;
		}
		// a charge left running (key lost) launches itself at full charge after a few seconds
		if (s.leapChargeStart > 0L && now - s.leapChargeStart > HulkConfig.abilities().leapMaxChargeTicks + 100) {
			releaseLeap(player);
			s = Hulk.state(player);
		}
		if (s.leaping) {
			Long off = LEAP_TAKEOFF.get(player.getUUID());
			boolean settled = player.onGround() || player.isInWater() || player.isPassenger() || player.getAbilities().flying;
			if (settled && (off == null || now - off > 4)) {
				HulkState n = s.copy();
				n.leaping = false;
				Hulk.save(player, n);
				LEAP_TAKEOFF.remove(player.getUUID());
				if (player.onGround()) {
					HulkConfig.Abilities cfg = HulkConfig.abilities();
					shockwave(player, player.position(), cfg.leapLandingRadius, cfg.leapLandingDamage, 1.0, 0.45, 0.6f);
				}
			}
		}
		tickSprintSmash(player, s);
	}

	static void shake(ServerLevel level, Vec3 at, float intensity, int ticks) {
		if (!HulkConfig.world().screenShake) {
			return;
		}
		double r2 = 24.0 * 24.0;
		for (ServerPlayer p : level.players()) {
			double d2 = p.distanceToSqr(at);
			if (d2 <= r2) {
				ServerPlayNetworking.send(p, new TitanShakePayload(intensity * (float) (1.0 - Math.sqrt(d2 / r2) * 0.8), ticks));
			}
		}
	}
}
