package com.projecthero.mod.hulk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * The Hulk's abilities (v0.13.14 kit). Only the Hulk himself, in control, can use them -- not Banner, not during a
 * rampage, not while calming down. Every number is in {@link HulkConfig#abilities()}.
 *
 * <pre>
 *   R  Power Punch   -- a 3-wide, 6-long punch, 30 damage                         5 s
 *   G  Ground Smash  -- a ring round him (30) and a crater                          5 s
 *   Z  Thunderclap   -- a cone shockwave in front (22), shatters glass and leaves   8 s
 *      Shift+Z HULK SMASH -- hold 5 s: 100 damage forward and all round, a huge crater  60 s
 *   X  Super Leap    -- hold to charge, release to launch; the landing is a shockwave  2 s
 *   C  Charge        -- runs forward on his own for 8 s, 20 to everything he runs through, breaks soft blocks  12 s after
 *   V  Grab (see {@link HulkGrab})
 * </pre>
 *
 * Hits land on the animation's impact frame through a tiny per-player task queue; the animation itself reaches every
 * client through {@link HulkState#animId} / {@link HulkState#animStart}. Sprint Smash (sprinting through soft blocks) is
 * a passive now, governed by the config.
 */
public final class HulkAbilities {
	public static final String POWER_PUNCH = "power_punch";
	public static final String GROUND_SMASH = "ground_smash";
	public static final String THUNDERCLAP = "thunderclap";
	public static final String HULK_SMASH = "hulk_smash";
	public static final String SUPER_LEAP = "super_leap";
	public static final String CHARGE = "charge";
	public static final String GRAB = "grab";

	/** Ticks from the key press to the hit (matches the animations). */
	public static final int PUNCH_IMPACT_TICKS = 5;
	public static final int CLAP_IMPACT_TICKS = 6;
	public static final int SMASH_IMPACT_TICKS = 10;
	public static final int HULK_SMASH_IMPACT_TICKS = 8;

	static final DustParticleOptions GAMMA = new DustParticleOptions(new Vector3f(0.35f, 1.0f, 0.25f), 1.6f);
	static final DustParticleOptions GAMMA_DEEP = new DustParticleOptions(new Vector3f(0.1f, 0.6f, 0.1f), 2.2f);

	private record Task(long at, Runnable run) {
	}

	private static final Map<UUID, List<Task>> TASKS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LEAP_TAKEOFF = new ConcurrentHashMap<>();
	private static final Map<UUID, Vec3> LAST_POS = new ConcurrentHashMap<>();
	private static final Map<UUID, HulkCombat.Hit> CHARGE_HITS = new ConcurrentHashMap<>();

	private HulkAbilities() {
	}

	public static void clearSessionState() {
		TASKS.clear();
		LEAP_TAKEOFF.clear();
		LAST_POS.clear();
		CHARGE_HITS.clear();
	}

	public static void clear(UUID id) {
		TASKS.remove(id);
		LEAP_TAKEOFF.remove(id);
		LAST_POS.remove(id);
		CHARGE_HITS.remove(id);
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

	static void cooldown(ServerPlayer player, String id, int ticks) {
		HulkState n = Hulk.state(player).copy();
		n.abilityReadyAt.put(id, player.level().getGameTime() + ticks);
		Hulk.save(player, n);
	}

	static void anim(ServerPlayer player, int animId) {
		HulkState n = Hulk.state(player).copy();
		n.animId = animId;
		n.animStart = player.level().getGameTime();
		Hulk.save(player, n);
	}

	static void schedule(ServerPlayer player, int delay, Runnable run) {
		TASKS.computeIfAbsent(player.getUUID(), k -> new ArrayList<>())
				.add(new Task(player.level().getGameTime() + delay, run));
	}

	/** Only the Hulk in control: Banner is told to get angry first; a rampaging or calming Hulk is ignored. */
	static boolean canAct(ServerPlayer player) {
		HulkState s = Hulk.state(player);
		if (!s.hulk) {
			Hulk.say(player, "message.projecthero.hulk.banner_cannot", ChatFormatting.GRAY);
			return false;
		}
		long now = player.level().getGameTime();
		if (s.rampaging(now) || s.combat.calming) {
			return false;
		}
		if (Hulk.changing(s, now)) {
			Hulk.say(player, "message.projecthero.hulk.changing", ChatFormatting.DARK_GREEN);
			return false; // v0.13.15: not until the unwilling change is over
		}
		return player.isAlive() && !player.isSpectator();
	}

	static Vec3 flatLook(ServerPlayer p) {
		Vec3 l = p.getLookAngle();
		Vec3 f = new Vec3(l.x, 0, l.z);
		return f.lengthSqr() < 1.0e-5 ? Vec3.directionFromRotation(0, p.getYRot()) : f.normalize();
	}

	// ---------------------------------------------------------------- R Power Punch

	public static void powerPunch(ServerPlayer player) {
		if (!canAct(player) || !ready(player, POWER_PUNCH)) {
			return;
		}
		cooldown(player, POWER_PUNCH, HulkConfig.abilities().powerPunchCooldownTicks);
		anim(player, HulkState.ANIM_PUNCH);
		AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.5f);
		schedule(player, PUNCH_IMPACT_TICKS, () -> powerPunchImpact(player));
	}

	private static void powerPunchImpact(ServerPlayer player) {
		if (!Hulk.isHulk(player) || !player.isAlive()) {
			return;
		}
		HulkConfig.Abilities cfg = HulkConfig.abilities();
		ServerLevel level = (ServerLevel) player.level();
		Vec3 dir = flatLook(player);
		Vec3 origin = player.position().add(0, player.getBbHeight() * 0.35, 0);
		HulkCombat.Hit hit = new HulkCombat.Hit(cfg.powerPunchDamage, cfg.powerPunchKnockback, 0.35);
		HulkCombat.sweep(player, player.position(), dir, cfg.powerPunchRange, cfg.powerPunchWidth, player.getBbHeight(), hit);
		// the air it throws: a tunnel of cloud and crits out to the end of its reach
		for (double d = 1.0; d <= cfg.powerPunchRange; d += 0.75) {
			Vec3 p = origin.add(dir.scale(d));
			level.sendParticles(ParticleTypes.CLOUD, p.x, p.y + 0.4, p.z, 3, 0.4, 0.4, 0.4, 0.02);
			if (((int) (d * 4)) % 3 == 0) {
				level.sendParticles(ParticleTypes.CRIT, p.x, p.y + 0.4, p.z, 3, 0.5, 0.5, 0.5, 0.2);
			}
		}
		Vec3 fist = origin.add(dir.scale(1.6)).add(0, 0.4, 0);
		level.sendParticles(ParticleTypes.EXPLOSION, fist.x, fist.y, fist.z, 1, 0, 0, 0, 0);
		level.playSound(null, fist.x, fist.y, fist.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.9f, 1.5f);
		level.playSound(null, fist.x, fist.y, fist.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2f, 0.6f);
		HulkCombat.shake(level, player.position(), 0.35f, 6, 16.0);
	}

	// ---------------------------------------------------------------- G Ground Smash

	public static void groundSmash(ServerPlayer player) {
		if (!canAct(player) || !ready(player, GROUND_SMASH)) {
			return;
		}
		cooldown(player, GROUND_SMASH, HulkConfig.abilities().groundSmashCooldownTicks);
		anim(player, HulkState.ANIM_SMASH);
		schedule(player, SMASH_IMPACT_TICKS, () -> groundSmashImpact(player));
	}

	static void groundSmashImpact(ServerPlayer player) {
		if (!Hulk.isHulk(player) || !player.isAlive()) {
			return;
		}
		HulkConfig.Abilities cfg = HulkConfig.abilities();
		Vec3 at = player.position();
		shockwave(player, at, cfg.groundSmashRadius, cfg.groundSmashDamage, cfg.groundSmashKnockback, cfg.groundSmashLift, 0.9f);
		ServerLevel level = (ServerLevel) player.level();
		HulkCombat.crater(player, at.add(flatLook(player).scale(1.5)).add(0, -0.5, 0), cfg.groundSmashCraterRadius, 80);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.3, at.z, 1, 0, 0, 0, 0);
	}

	/** A ring of damage, knockback, lift and flying earth round {@code at}; also the leap landing and rampage stomps. */
	static void shockwave(ServerPlayer player, Vec3 at, double radius, float damage, double knockback, double lift, float shake) {
		ServerLevel level = (ServerLevel) player.level();
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

	// ---------------------------------------------------------------- Z Thunderclap

	public static void thunderclap(ServerPlayer player) {
		if (!canAct(player) || !ready(player, THUNDERCLAP)) {
			return;
		}
		cooldown(player, THUNDERCLAP, HulkConfig.abilities().thunderclapCooldownTicks);
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
		HulkCombat.Hit hit = new HulkCombat.Hit(cfg.thunderclapDamage, cfg.thunderclapKnockback, 0.35);
		for (LivingEntity e : HulkCombat.targets(player, player.getBoundingBox().inflate(range))) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(origin);
			double dist = to.length();
			if (dist < 1.0e-3 || dist > range || to.normalize().dot(dir) < cosHalf) {
				continue;
			}
			float falloff = (float) (1.0 - 0.5 * Math.min(1.0, dist / range));
			if (hit.hit.add(e.getId()) && AbilityHelpers.hurtLands(player, e, cfg.thunderclapDamage * falloff)
					&& !com.projecthero.mod.titanshifter.TitanCombat.isBoss(e)) {
				AbilityHelpers.push(e, to.normalize().scale(cfg.thunderclapKnockback * falloff).add(0, 0.35 * falloff, 0));
			}
		}
		if (HulkCombat.canBreakBlocks(level)) {
			shatterFragile(level, player, origin, dir, range, cosHalf);
		}
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
		HulkCombat.shake(level, player.position(), 0.5f, 10, 24.0);
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
					if (isFragile(level, pos, level.getBlockState(pos))) {
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

	// ---------------------------------------------------------------- Shift+Z HULK SMASH

	/** Shift+Z pressed: start the 5-second wind-up. */
	public static void beginHulkSmash(ServerPlayer player) {
		if (!canAct(player)) {
			return;
		}
		HulkState s = Hulk.state(player);
		if (s.combat.smashChargeStart > 0L) {
			return;
		}
		if (!ready(player, HULK_SMASH)) {
			Hulk.say(player, "message.projecthero.hulk.hulk_smash_cooldown", ChatFormatting.GRAY,
					(int) Math.ceil(cooldownRemaining(player, HULK_SMASH) / 20.0));
			return;
		}
		HulkState n = s.copy();
		n.combat.smashChargeStart = player.level().getGameTime();
		Hulk.save(player, n);
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0f, 0.6f);
		Hulk.say(player, "message.projecthero.hulk.hulk_smash_charging", ChatFormatting.GREEN);
	}

	/** Z released: a wind-up that did not finish is called off (no cooldown spent). */
	public static void releaseHulkSmash(ServerPlayer player) {
		HulkState s = Hulk.state(player);
		if (s.combat.smashChargeStart <= 0L) {
			return;
		}
		HulkState n = s.copy();
		n.combat.smashChargeStart = 0L;
		Hulk.save(player, n);
		Hulk.say(player, "message.projecthero.hulk.hulk_smash_cancelled", ChatFormatting.GRAY);
	}

	/** Wind-up progress 0..1 (client-safe). */
	public static float hulkSmashCharge(net.minecraft.world.entity.player.Player player) {
		HulkState s = player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.HULK_STATE, null);
		if (s == null || s.combat.smashChargeStart <= 0L) {
			return 0.0f;
		}
		return Math.min(1.0f, (player.level().getGameTime() - s.combat.smashChargeStart)
				/ (float) Math.max(1, HulkConfig.abilities().hulkSmashChargeTicks));
	}

	private static void tickHulkSmashCharge(ServerPlayer player, HulkState s, long now) {
		long start = s.combat.smashChargeStart;
		if (start <= 0L) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		long held = now - start;
		float progress = Math.min(1.0f, held / (float) Math.max(1, HulkConfig.abilities().hulkSmashChargeTicks));
		double h = player.getBbHeight();
		// gamma boils off him, faster as the wind-up builds, and the ground trembles
		level.sendParticles(GAMMA, player.getX(), player.getY() + h * 0.5, player.getZ(), 2 + (int) (10 * progress), 0.6, h * 0.45, 0.6, 0.04);
		if (held % 3 == 0) {
			level.sendParticles(GAMMA_DEEP, player.getX(), player.getY() + 0.2, player.getZ(), 4, 1.5 * progress + 0.5, 0.05, 1.5 * progress + 0.5, 0.0);
		}
		if (held % 20 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS,
					1.5f + progress, 0.6f + progress * 0.4f);
			HulkCombat.shake(level, player.position(), 0.15f + 0.35f * progress, 18, 20.0);
		}
		if (progress >= 1.0f) {
			fireHulkSmash(player);
		}
	}

	private static void fireHulkSmash(ServerPlayer player) {
		HulkState n = Hulk.state(player).copy();
		n.combat.smashChargeStart = 0L;
		n.animId = HulkState.ANIM_HULK_SMASH;
		n.animStart = player.level().getGameTime();
		n.abilityReadyAt.put(HULK_SMASH, n.animStart + HulkConfig.abilities().hulkSmashCooldownTicks);
		Hulk.save(player, n);
		ServerLevel level = (ServerLevel) player.level();
		// the roar comes first, then the fists come down
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 3.0f, 0.5f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 2.0f, 0.7f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.2f, 0.6f);
		player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.hulk.hulk_smash")
				.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
		schedule(player, HULK_SMASH_IMPACT_TICKS, () -> hulkSmashImpact(player));
	}

	private static void hulkSmashImpact(ServerPlayer player) {
		if (!Hulk.isHulk(player) || !player.isAlive()) {
			return;
		}
		HulkConfig.Abilities cfg = HulkConfig.abilities();
		ServerLevel level = (ServerLevel) player.level();
		Vec3 at = player.position();
		Vec3 dir = flatLook(player);
		HulkCombat.Hit hit = new HulkCombat.Hit(cfg.hulkSmashDamage, 3.0, 1.2);
		// a blast rolling out in front, then the shockwave all round
		HulkCombat.sweep(player, at, dir, cfg.hulkSmashForwardRange, cfg.hulkSmashForwardWidth, 8.0, hit);
		HulkCombat.radial(player, at, cfg.hulkSmashRadius, hit, true);
		HulkCombat.crater(player, at.add(dir.scale(3.0)).add(0, -0.5, 0), cfg.hulkSmashCraterRadius, 450);

		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 2, 1.0, 0.2, 1.0, 0.0);
		for (double d = 2.0; d <= cfg.hulkSmashForwardRange; d += 2.0) {
			Vec3 p = at.add(dir.scale(d));
			level.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y + 1.0, p.z, 1, 0.8, 0.5, 0.8, 0.0);
			level.sendParticles(GAMMA, p.x, p.y + 1.0, p.z, 14, cfg.hulkSmashForwardWidth * 0.25, 1.2, cfg.hulkSmashForwardWidth * 0.25, 0.05);
			level.sendParticles(ParticleTypes.CLOUD, p.x, p.y + 0.5, p.z, 6, 1.5, 0.4, 1.5, 0.1);
		}
		for (double r = 3.0; r <= cfg.hulkSmashRadius; r += 3.0) {
			HulkCombat.ring(level, GAMMA_DEEP, at.add(0, 0.3, 0), r, (int) (r * 5));
			HulkCombat.ring(level, ParticleTypes.CLOUD, at.add(0, 0.2, 0), r, (int) (r * 3));
		}
		// a green column where he struck
		for (int y = 0; y < 14; y++) {
			level.sendParticles(GAMMA, at.x, at.y + y, at.z, 6, 0.8, 0.3, 0.8, 0.02);
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 4.0f, 0.5f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3.0f, 0.6f);
		HulkCombat.shake(level, at, 1.3f, 40, 64.0);
	}

	// ---------------------------------------------------------------- X Super Leap

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
		launch(player, n, cfg.leapMinBlocks + (cfg.leapMaxBlocks - cfg.leapMinBlocks) * charge, player.getLookAngle());
		n.abilityReadyAt.put(SUPER_LEAP, n.animStart + cfg.leapCooldownTicks);
		Hulk.save(player, n);
	}

	/** Throws the Hulk {@code blocks} along {@code dir} (also used by the rampage). Mutates {@code n}; the caller saves it. */
	static void launch(ServerPlayer player, HulkState n, double blocks, Vec3 dir) {
		Vec3 v = AbilityHelpers.ballisticLaunch(dir, blocks, player.onGround());
		n.leaping = true;
		n.animId = HulkState.ANIM_LEAP;
		n.animStart = player.level().getGameTime();
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

	// ---------------------------------------------------------------- C Charge

	public static void charge(ServerPlayer player) {
		if (!canAct(player) || !ready(player, CHARGE)) {
			return;
		}
		HulkState s = Hulk.state(player);
		if (s.combat.chargeUntil > player.level().getGameTime()) {
			return;
		}
		HulkState n = s.copy();
		n.combat.chargeUntil = player.level().getGameTime() + HulkConfig.abilities().chargeTicks;
		Hulk.save(player, n);
		CHARGE_HITS.put(player.getUUID(), new HulkCombat.Hit(HulkConfig.abilities().chargeDamage, 1.8, 0.45));
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.6f, 0.8f);
		Hulk.say(player, "message.projecthero.hulk.charge", ChatFormatting.GREEN);
	}

	public static boolean charging(net.minecraft.world.entity.player.Player player) {
		HulkState s = player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.HULK_STATE, null);
		return s != null && s.hulk && s.combat.chargeUntil > player.level().getGameTime();
	}

	private static void tickCharge(ServerPlayer player, HulkState s, long now) {
		if (s.combat.chargeUntil <= 0L) {
			return;
		}
		if (now >= s.combat.chargeUntil || !s.hulk || s.rampaging(now) || player.isPassenger() || player.isSpectator()) {
			endCharge(player);
			return;
		}
		HulkConfig.Abilities cfg = HulkConfig.abilities();
		ServerLevel level = (ServerLevel) player.level();
		Vec3 dir = flatLook(player);
		Vec3 vel = dir.scale(cfg.chargeSpeed);
		double vy = player.onGround() ? -0.05 : Math.max(player.getDeltaMovement().y - 0.08, -1.2);
		breakAhead(player, dir, true);
		AbilityHelpers.launchSelf(player, new Vec3(vel.x, vy, vel.z));
		player.setSprinting(true);
		HulkCombat.Hit hit = CHARGE_HITS.computeIfAbsent(player.getUUID(), k -> new HulkCombat.Hit(cfg.chargeDamage, 1.8, 0.45));
		Vec3 center = player.position().add(dir.scale(0.8)).add(0, player.getBbHeight() * 0.4, 0);
		HulkCombat.radial(player, center, cfg.chargeHitRadius, hit, false);
		if (now % 2 == 0) {
			level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(), 4, 0.6, 0.1, 0.6, 0.02);
			BlockState ground = level.getBlockState(player.blockPosition().below());
			if (!ground.isAir()) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), player.getX(), player.getY() + 0.1, player.getZ(),
						5, 0.7, 0.1, 0.7, 0.1);
			}
		}
		if (now % 6 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_STEP, SoundSource.PLAYERS, 1.2f, 0.7f);
			HulkCombat.shake(level, player.position(), 0.25f, 6, 14.0);
		}
	}

	static void endCharge(ServerPlayer player) {
		CHARGE_HITS.remove(player.getUUID());
		HulkState s = Hulk.state(player);
		if (s.combat.chargeUntil <= 0L) {
			return;
		}
		HulkState n = s.copy();
		n.combat.chargeUntil = 0L;
		n.abilityReadyAt.put(CHARGE, player.level().getGameTime() + HulkConfig.abilities().chargeCooldownTicks);
		Hulk.save(player, n);
		player.setSprinting(false);
	}

	/**
	 * Smashes the soft blocks right in front of him, from his feet to the top of his head. {@code forced}: the Charge
	 * (and the rampage) break through whatever is there; otherwise only when the Sprint Smash passive applies.
	 */
	static int breakAhead(ServerPlayer player, Vec3 fwd, boolean forced) {
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
				if (!HulkCombat.breakable(level, pos, state)) {
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
			CHARGE_HITS.remove(player.getUUID());
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
				s = Hulk.state(player);
			}
		}
		tickHulkSmashCharge(player, s, now);
		s = Hulk.state(player);
		tickCharge(player, s, now);
		s = Hulk.state(player);
		tickSprintSmash(player, s);
	}
}
