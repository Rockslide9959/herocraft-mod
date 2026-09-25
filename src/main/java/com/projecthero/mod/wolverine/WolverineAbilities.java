package com.projecthero.mod.wolverine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.wolverine.data.ClawTier;
import com.projecthero.mod.wolverine.data.WolverineState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The six Wolverine abilities. Everything is decided here on the server -- the client only ever sends
 * "slot N pressed" -- and every number comes from {@link WolverineConfig}. Claw moves deploy the claws
 * if they are retracted (a Wolverine mid-fight should never lose a press to a toggle); Rage does not
 * need them. Multi-hit moves queue their later hits on {@link WolverineScheduler}.
 */
public final class WolverineAbilities {
	public static final String SLASH = "claw_slash";
	public static final String CROSS = "cross_slash";
	public static final String DASH = "claw_dash";
	public static final String RAGE = "berserker_rage";
	public static final String FRENZY = "frenzy";
	public static final String EXECUTION = "adamantium_execution";

	/** Entity ids already struck by the current Claw Dash, per player. Static cache: reset with the session. */
	private static final Map<UUID, Set<Integer>> DASH_HITS = new ConcurrentHashMap<>();
	/** The entity each player is currently dragging along on a Claw Dash (v0.12.12): player -> entity id. */
	private static final Map<UUID, Integer> DASH_GRAB = new ConcurrentHashMap<>();
	/** Players whose next claw swing uses the off (left) hand -- claw moves alternate hands (v0.12.13). */
	private static final Set<UUID> LEFT_NEXT = ConcurrentHashMap.newKeySet();

	private WolverineAbilities() {
	}

	public static void clearSessionState() {
		DASH_HITS.clear();
		DASH_GRAB.clear();
		LEFT_NEXT.clear();
	}

	// ---------------- shared helpers ----------------

	/** Common gate: has the power, off cooldown; auto-deploys the claws for a claw move. */
	private static boolean prepare(ServerPlayer player, String id, String nameKey, boolean needsClaws) {
		if (!Wolverine.hasPower(player) || Wolverine.transforming(player)) {
			return false;
		}
		if (id.equals(EXECUTION) && Wolverine.clawTier(player) != ClawTier.ADAMANTIUM) {
			Wolverine.say(player, "message.projecthero.wolverine.adamantium_required", ChatFormatting.RED);
			return false;
		}
		if (!Wolverine.abilityReady(player, id)) {
			player.displayClientMessage(Component.translatable("message.projecthero.wolverine.cooldown",
					Component.translatable(nameKey),
					String.format(Locale.ROOT, "%.1f", Wolverine.cooldownRemaining(player, id) / 20.0f))
					.withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		if (needsClaws && !Wolverine.clawTier(player).hasClaws()) {
			Wolverine.say(player, "message.projecthero.wolverine.bone_claws_required", ChatFormatting.RED);
			return false;
		}
		if (needsClaws && !Wolverine.clawsOut(player)) {
			Wolverine.setClaws(player, true);
		}
		return true;
	}

	/** Berserker Rage adds 50% to every claw hit. */
	private static float scaled(ServerPlayer player, float base) {
		return Wolverine.raging(player) ? base * (1.0f + (float) WolverineConfig.RAGE_DAMAGE_BONUS) : base;
	}

	/** Living things in front of the player within {@code range} (measured to their hitbox), nearest first. */
	private static List<LivingEntity> inCone(ServerPlayer player, double range, double minDot) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : AbilityHelpers.enemiesAround(player, eye, range)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			double len = to.length();
			if (len < 1.3 || to.scale(1.0 / len).dot(look) >= minDot) {
				out.add(e);
			}
		}
		out.sort((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
		return out;
	}

	/** Living things in a box in front of the player: {@code range} deep, {@code width} blocks wide, nearest first. */
	private static List<LivingEntity> inBox(ServerPlayer player, double range, double width) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
		double half = width / 2.0;
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : AbilityHelpers.enemiesAround(player, eye, range + half + 2.0)) {
			Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			double fwd = c.x * flat.x + c.z * flat.z;
			double side = Math.abs(c.x * -flat.z + c.z * flat.x);
			double halfW = e.getBbWidth() / 2.0;
			if (fwd >= -0.5 - halfW && fwd <= range + halfW && side <= half + halfW
					&& Math.abs(c.y) <= 2.0 + e.getBbHeight() / 2.0) {
				out.add(e);
			}
		}
		out.sort((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
		return out;
	}

	private static boolean squadmates(ServerPlayer a, ServerPlayer b) {
		return com.projecthero.mod.squad.SquadManager.get(a.server).sameSquad(a.getUUID(), b.getUUID());
	}

	private static boolean strike(ServerPlayer player, LivingEntity target, float damage, double knockback) {
		if (!AbilityHelpers.hurtBurst(player, target, scaled(player, damage))) {
			return false;
		}
		if (knockback > 0.0) {
			AbilityHelpers.knockbackFrom(target, player.position(), knockback);
		}
		if (player.level() instanceof ServerLevel level) {
			Vec3 at = target.position().add(0, target.getBbHeight() * 0.55, 0);
			level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.25, 0.3, 0.25, 0.15);
			level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 6, 0.25, 0.3, 0.25, 0.1);
		}
		return true;
	}

	private static void slashFx(ServerPlayer player, double reach, double sideways, float pitch) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		Vec3 look = player.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
		Vec3 p = player.getEyePosition().add(look.scale(reach)).add(right.scale(sideways)).add(0, -0.25, 0);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, pitch);
		AbilityHelpers.sound(player, SoundEvents.CHAIN_HIT, 0.5f, pitch + 0.4f);
	}

	private static void swing(ServerPlayer player, int slot) {
		boolean left = !LEFT_NEXT.add(player.getUUID());
		if (left) {
			LEFT_NEXT.remove(player.getUUID());
		}
		// a held off-hand item would be waved about with the claw swing, so only use it when empty
		player.swing(left && player.getOffhandItem().isEmpty() ? net.minecraft.world.InteractionHand.OFF_HAND
				: net.minecraft.world.InteractionHand.MAIN_HAND, true);
		Wolverine.markAction(player, slot);
	}

	// ---------------- 1: Claw Slash (R) ----------------

	public static void clawSlash(ServerPlayer player) {
		if (!prepare(player, SLASH, "projecthero.wolverine.ability.claw_slash", true)) {
			return;
		}
		Wolverine.triggerCooldown(player, SLASH, WolverineConfig.SLASH_COOLDOWN);
		swing(player, 1);
		slashFx(player, 1.6, 0.0, 1.0f);
		for (LivingEntity target : inBox(player, WolverineConfig.SLASH_RANGE, WolverineConfig.STRIKE_WIDTH)) {
			strike(player, target, WolverineConfig.slashDamage(Wolverine.clawTier(player)), 0.6);
		}
	}

	// ---------------- 2: Cross Slash (G) ----------------

	public static void crossSlash(ServerPlayer player) {
		if (!prepare(player, CROSS, "projecthero.wolverine.ability.cross_slash", true)) {
			return;
		}
		Wolverine.triggerCooldown(player, CROSS, WolverineConfig.CROSS_COOLDOWN);
		swing(player, 2);
		crossHit(player, 0.6, 0.9f);
		WolverineScheduler.schedule(player, WolverineConfig.CROSS_GAP_TICKS, () -> {
			swing(player, 2);
			crossHit(player, -0.6, 1.25f);
		});
	}

	private static void crossHit(ServerPlayer player, double side, float pitch) {
		slashFx(player, 1.5, side, pitch);
		for (LivingEntity target : inBox(player, WolverineConfig.CROSS_RANGE, WolverineConfig.STRIKE_WIDTH)) {
			strike(player, target, WolverineConfig.crossDamageEach(Wolverine.clawTier(player)), 0.35);
		}
	}

	// ---------------- 3: Claw Dash (X) ----------------

	public static void clawDash(ServerPlayer player) {
		if (!prepare(player, DASH, "projecthero.wolverine.ability.claw_dash", true)) {
			return;
		}
		Vec3 look = player.getLookAngle();
		// never launch through geometry: shorten the dash to stop just short of any block in the way
		double dist = WolverineConfig.DASH_BLOCKS;
		BlockHitResult block = AbilityHelpers.raycastBlock(player, dist + 1.0);
		if (block.getType() != HitResult.Type.MISS) {
			dist = Math.max(1.5, Math.min(dist, player.getEyePosition().distanceTo(block.getLocation()) - 1.0));
		}
		long now = player.level().getGameTime();
		WolverineState c = Wolverine.state(player).copy();
		c.dashUntil = now + WolverineConfig.DASH_MAX_TICKS;
		Wolverine.save(player, c);
		WolverinePassives.reconcile(player);
		DASH_HITS.put(player.getUUID(), new HashSet<>());
		DASH_GRAB.remove(player.getUUID());
		Wolverine.triggerCooldown(player, DASH, WolverineConfig.DASH_COOLDOWN);
		AbilityHelpers.launchSelf(player, AbilityHelpers.ballisticLaunch(look, dist, player.onGround()));
		swing(player, 3);
		if (player.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(), 10, 0.3, 0.1, 0.3, 0.05);
		}
		AbilityHelpers.sound(player, SoundEvents.PHANTOM_SWOOP, 0.8f, 1.5f);
	}

	/** Per-tick dash upkeep: sweep for enemies to slash through, end the dash on landing. */
	private static void tickDash(ServerPlayer player, WolverineState s, long now) {
		Set<Integer> hits = DASH_HITS.get(player.getUUID());
		if (s.dashUntil <= now) {
			if (hits != null) {
				DASH_HITS.remove(player.getUUID());
			}
			DASH_GRAB.remove(player.getUUID());
			return;
		}
		if (hits == null) {
			return;
		}
		if (player.level() instanceof ServerLevel level && player.tickCount % 2 == 0) {
			level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.9, player.getZ(), 2, 0.2, 0.3, 0.2, 0.02);
		}
		// a box 3 blocks wide along his line of travel, long enough to cover the ground covered this tick
		Vec3 vel = player.getDeltaMovement();
		double speed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
		Vec3 dir = speed > 0.05 ? new Vec3(vel.x / speed, 0, vel.z / speed) : player.getLookAngle();
		dir = new Vec3(dir.x, 0, dir.z);
		dir = dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : dir.normalize();
		final Vec3 axis = dir;
		final double reachBack = speed + 0.5;
		Vec3 centre = player.position().add(0, player.getBbHeight() * 0.5, 0);
		for (LivingEntity target : AbilityHelpers.enemiesAround(player, centre, speed + 4.0)) {
			Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(centre);
			double along = c.x * axis.x + c.z * axis.z;
			double side = Math.abs(c.x * -axis.z + c.z * axis.x);
			double halfW = target.getBbWidth() / 2.0;
			if (along < -reachBack - halfW || along > 1.8 + halfW || side > WolverineConfig.STRIKE_WIDTH / 2.0 + halfW
					|| Math.abs(c.y) > 2.0 + target.getBbHeight() / 2.0) {
				continue;
			}
			if (!hits.add(target.getId())) {
				continue;
			}
			boolean hurt = strike(player, target, WolverineConfig.dashDamage(Wolverine.clawTier(player)), 0.9);
			if (hurt) {
				slashFx(player, 0.9, 0.0, 0.8f);
			}
			// the first thing the claws sink into is seized and carried along. v0.12.21: a player is grabbed even
			// when the hit itself is swallowed by their damage cooldown (enemiesAround already applies the PvP
			// rules), but never a squadmate.
			boolean grabbable = hurt || (target instanceof ServerPlayer tp && !squadmates(player, tp));
			if (grabbable && target.isAlive() && !DASH_GRAB.containsKey(player.getUUID())) {
				DASH_GRAB.put(player.getUUID(), target.getId());
			}
		}
		dragGrabbed(player);
		// landing ends the dash early (after it has actually left the ground)
		if (s.dashUntil - now < WolverineConfig.DASH_MAX_TICKS - 3 && player.onGround()) {
			WolverineState c = s.copy();
			c.dashUntil = 0L;
			Wolverine.save(player, c);
			WolverinePassives.reconcile(player);
			DASH_HITS.remove(player.getUUID());
			DASH_GRAB.remove(player.getUUID());
		}
	}

	/** Keep the seized entity pinned on the claws in front of the player, moving with them, until the dash ends. */
	private static void dragGrabbed(ServerPlayer player) {
		Integer id = DASH_GRAB.get(player.getUUID());
		if (id == null) {
			return;
		}
		net.minecraft.world.entity.Entity e = player.serverLevel().getEntity(id);
		if (!(e instanceof LivingEntity target) || !target.isAlive()) {
			DASH_GRAB.remove(player.getUUID());
			return;
		}
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
		// the player moves after this tick runs, so aim ahead by their velocity or the grabbed entity trails behind
		Vec3 to = player.position().add(player.getDeltaMovement().scale(WolverineConfig.DASH_GRAB_LEAD_TICKS))
				.add(flat.scale(WolverineConfig.DASH_GRAB_DISTANCE));
		if (target instanceof ServerPlayer tp) {
			// v0.12.21: a player's own client owns their position, so setPos is simply overwritten by their next
			// movement packet. Drive them by velocity instead (the same packet path as every other pull), aimed at
			// the spot in front of the dasher, and teleport when they have fallen well behind.
			Vec3 gap = to.subtract(target.position());
			if (gap.lengthSqr() > 16.0 && target.level().noCollision(target, target.getBoundingBox().move(gap))) {
				tp.connection.teleport(to.x, to.y, to.z, tp.getYRot(), tp.getXRot());
			}
			Vec3 v = gap.length() > 3.0 ? gap.normalize().scale(3.0) : gap;
			target.setDeltaMovement(player.getDeltaMovement().add(v.scale(0.5)));
			target.fallDistance = 0.0f;
			target.hurtMarked = true;
			return;
		}
		// never drag them through a wall: skip the move when the spot in front is blocked
		if (target.level().noCollision(target, target.getBoundingBox().move(to.subtract(target.position())))) {
			target.setPos(to.x, to.y, to.z);
		}
		target.setDeltaMovement(player.getDeltaMovement());
		target.fallDistance = 0.0f;
		target.hurtMarked = true;
	}

	// ---------------- 6: Berserker Rage (C) ----------------

	public static void berserkerRage(ServerPlayer player) {
		if (!Wolverine.hasPower(player)) {
			return;
		}
		if (Wolverine.raging(player)) {
			// cannot stack or be re-triggered while it burns
			player.displayClientMessage(Component.translatable("message.projecthero.wolverine.rage_active")
					.withStyle(ChatFormatting.GRAY), true);
			return;
		}
		// no cooldown: the rage bar (filled by taking and dealing damage) is the gate
		float meter = Wolverine.state(player).rageMeter;
		if (meter < WolverineConfig.RAGE_BAR_MAX) {
			player.displayClientMessage(Component.translatable("message.projecthero.wolverine.rage_not_ready",
					(int) (100.0f * meter / WolverineConfig.RAGE_BAR_MAX)).withStyle(ChatFormatting.GRAY), true);
			return;
		}
		long now = player.level().getGameTime();
		WolverineState c = Wolverine.state(player).copy();
		c.rageUntil = now + WolverineConfig.RAGE_TICKS;
		c.rageMeter = 0.0f;
		Wolverine.save(player, c);
		WolverinePassives.reconcile(player);
		Wolverine.markAction(player, 6);
		if (player.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.ANGRY_VILLAGER, player.getX(), player.getY() + 1.6, player.getZ(), 6, 0.4, 0.3, 0.4, 0.0);
			level.sendParticles(ParticleTypes.CRIMSON_SPORE, player.getX(), player.getY() + 1.0, player.getZ(), 25, 0.5, 0.8, 0.5, 0.02);
		}
		AbilityHelpers.sound(player, SoundEvents.WOLF_GROWL, 1.0f, 0.6f);
		AbilityHelpers.sound(player, SoundEvents.RAVAGER_ROAR, 0.6f, 1.4f);
		player.displayClientMessage(Component.translatable("message.projecthero.wolverine.rage_started")
				.withStyle(ChatFormatting.RED), true);
	}

	// ---------------- 5: Frenzy (C) ----------------

	public static void frenzy(ServerPlayer player) {
		if (!Wolverine.hasPower(player)) {
			return;
		}
		if (!Wolverine.abilityReady(player, FRENZY)) {
			prepare(player, FRENZY, "projecthero.wolverine.ability.frenzy", true); // prints the cooldown note
			return;
		}
		if (AbilityHelpers.enemiesAround(player, player.position(), WolverineConfig.FRENZY_RANGE).isEmpty()) {
			player.displayClientMessage(Component.translatable("message.projecthero.wolverine.no_target")
					.withStyle(ChatFormatting.GRAY), true);
			return; // nothing to shred: no cooldown spent
		}
		if (!prepare(player, FRENZY, "projecthero.wolverine.ability.frenzy", true)) {
			return;
		}
		Wolverine.triggerCooldown(player, FRENZY, WolverineConfig.FRENZY_COOLDOWN);
		// the controlled target-hit table: one strike per tick-step, each going to the least-hit target
		// still in range, so the five strikes spread over a crowd and never loop past their count
		Map<Integer, Integer> hitCount = new HashMap<>();
		for (int i = 0; i < WolverineConfig.FRENZY_STRIKES; i++) {
			WolverineScheduler.schedule(player, i * WolverineConfig.FRENZY_INTERVAL_TICKS, () -> frenzyStrike(player, hitCount));
		}
	}

	private static void frenzyStrike(ServerPlayer player, Map<Integer, Integer> hitCount) {
		LivingEntity best = null;
		int bestHits = Integer.MAX_VALUE;
		double bestDist = Double.MAX_VALUE;
		for (LivingEntity e : AbilityHelpers.enemiesAround(player, player.position().add(0, 0.9, 0), WolverineConfig.FRENZY_RANGE)) {
			int h = hitCount.getOrDefault(e.getId(), 0);
			double d = e.distanceToSqr(player);
			if (h < bestHits || (h == bestHits && d < bestDist)) {
				best = e;
				bestHits = h;
				bestDist = d;
			}
		}
		swing(player, 5);
		if (best == null) {
			return;
		}
		hitCount.merge(best.getId(), 1, Integer::sum);
		if (strike(player, best, WolverineConfig.frenzyDamage(Wolverine.clawTier(player)), 0.15)) {
			AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1.3f + 0.1f * hitCount.size());
			if (player.level() instanceof ServerLevel level) {
				Vec3 p = best.position().add(0, best.getBbHeight() * 0.6, 0);
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0.2, 0.2, 0.2, 0.0);
			}
		}
	}

	// ---------------- 4: Adamantium Execution (Z) ----------------

	/** Z pressed: start charging. Nothing happens until the key is released after a full charge. */
	public static void beginExecutionCharge(ServerPlayer player) {
		if (!Wolverine.hasPower(player)) {
			return;
		}
		WolverineState s = Wolverine.state(player);
		if (s.chargeStartedAt != 0L || WolverineScheduler.hasPending(player)) {
			return;
		}
		if (!prepare(player, EXECUTION, "projecthero.wolverine.ability.adamantium_execution", true)) {
			return; // on cooldown: says so, and no charge starts
		}
		WolverineState c = s.copy();
		c.chargeStartedAt = player.level().getGameTime();
		Wolverine.save(player, c);
		AbilityHelpers.sound(player, SoundEvents.CHAIN_PLACE, 0.7f, 0.6f);
	}

	/** Z released: fire the execution if the charge was complete, otherwise just drop it. */
	public static void releaseExecutionCharge(ServerPlayer player) {
		WolverineState s = Wolverine.state(player);
		if (!s.hasPower || s.chargeStartedAt == 0L) {
			return;
		}
		long held = player.level().getGameTime() - s.chargeStartedAt;
		endCharge(player);
		if (held < WolverineConfig.EXECUTION_CHARGE_TICKS) {
			player.displayClientMessage(Component.translatable("message.projecthero.wolverine.charge_cancelled")
					.withStyle(ChatFormatting.GRAY), true);
			return;
		}
		execution(player);
	}

	private static void endCharge(ServerPlayer player) {
		WolverineState c = Wolverine.state(player).copy();
		c.chargeStartedAt = 0L;
		Wolverine.save(player, c);
		player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
	}

	/** While charging: slow the Wolverine, gather sparks, tick a rising note each second, ping at full. */
	private static void tickCharge(ServerPlayer player, WolverineState s, long now) {
		long held = now - s.chargeStartedAt;
		if (held > WolverineConfig.EXECUTION_CHARGE_TIMEOUT) {
			endCharge(player);
			return;
		}
		if (held % 4 == 0) {
			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 8, 1, false, false, false));
		}
		boolean full = held >= WolverineConfig.EXECUTION_CHARGE_TICKS;
		if (player.level() instanceof ServerLevel level && held % 4 == 0) {
			Vec3 p = player.getEyePosition().add(player.getLookAngle().scale(0.8));
			level.sendParticles(full ? ParticleTypes.CRIT : ParticleTypes.ENCHANTED_HIT, p.x, p.y - 0.3, p.z,
					full ? 4 : 2, 0.3, 0.2, 0.3, 0.05);
		}
		if (held > 0 && held % 20 == 0 && !full) {
			AbilityHelpers.sound(player, SoundEvents.NOTE_BLOCK_HAT.value(), 0.8f, 0.8f + 0.15f * (held / 20));
		}
		if (held == WolverineConfig.EXECUTION_CHARGE_TICKS) {
			AbilityHelpers.sound(player, SoundEvents.ANVIL_PLACE, 0.6f, 1.6f);
			AbilityHelpers.sound(player, SoundEvents.WOLF_GROWL, 0.8f, 0.6f);
			player.displayClientMessage(Component.translatable("message.projecthero.wolverine.charge_ready")
					.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
		}
	}

	public static void execution(ServerPlayer player) {
		if (!Wolverine.hasPower(player)) {
			return;
		}
		if (!Wolverine.abilityReady(player, EXECUTION) || WolverineScheduler.hasPending(player)) {
			prepare(player, EXECUTION, "projecthero.wolverine.ability.adamantium_execution", true);
			return;
		}
		// not a global attack: it needs a target near enough to lunge onto
		double reach = WolverineConfig.EXECUTION_RANGE + WolverineConfig.EXECUTION_LUNGE_BLOCKS;
		List<LivingEntity> targets = inCone(player, reach, 0.5);
		if (targets.isEmpty()) {
			player.displayClientMessage(Component.translatable("message.projecthero.wolverine.no_target")
					.withStyle(ChatFormatting.GRAY), true);
			return;
		}
		if (!prepare(player, EXECUTION, "projecthero.wolverine.ability.adamantium_execution", true)) {
			return;
		}
		LivingEntity mark = targets.get(0);
		Wolverine.triggerCooldown(player, EXECUTION, WolverineConfig.EXECUTION_COOLDOWN);
		swing(player, 4);
		// wind-up: root in place, claws glinting
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, WolverineConfig.EXECUTION_WINDUP_TICKS, 6, false, false, false));
		AbilityHelpers.sound(player, SoundEvents.WOLF_GROWL, 0.9f, 0.5f);
		AbilityHelpers.sound(player, SoundEvents.CHAIN_PLACE, 0.8f, 0.6f);
		for (int t = 0; t < WolverineConfig.EXECUTION_WINDUP_TICKS; t += 2) {
			WolverineScheduler.schedule(player, t, () -> {
				if (player.level() instanceof ServerLevel level) {
					Vec3 p = player.getEyePosition().add(player.getLookAngle().scale(0.8));
					level.sendParticles(ParticleTypes.CRIT, p.x, p.y - 0.3, p.z, 4, 0.3, 0.2, 0.3, 0.05);
				}
			});
		}
		WolverineScheduler.schedule(player, WolverineConfig.EXECUTION_WINDUP_TICKS, () -> executionLunge(player, mark));
	}

	private static void executionLunge(ServerPlayer player, LivingEntity mark) {
		player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		Vec3 aim = mark.isAlive() ? mark.position().add(0, mark.getBbHeight() * 0.5, 0).subtract(player.getEyePosition())
				: player.getLookAngle();
		double lunge = Math.min(WolverineConfig.EXECUTION_LUNGE_BLOCKS, Math.max(1.0, aim.length() - 1.5));
		AbilityHelpers.launchSelf(player, AbilityHelpers.ballisticLaunch(aim, lunge, player.onGround()));
		AbilityHelpers.sound(player, SoundEvents.PHANTOM_SWOOP, 0.9f, 1.1f);
		WolverineScheduler.schedule(player, 3, () -> executionStrike(player, mark));
	}

	private static void executionStrike(ServerPlayer player, LivingEntity mark) {
		swing(player, 4);
		LivingEntity target = null;
		double reach = WolverineConfig.EXECUTION_RANGE;
		if (mark.isAlive() && AbilityHelpers.distanceSqToBox(mark, player.getEyePosition()) <= reach * reach) {
			target = mark;
		} else {
			List<LivingEntity> near = inCone(player, reach, 0.3);
			if (!near.isEmpty()) {
				target = near.get(0);
			}
		}
		if (target == null) {
			Wolverine.triggerCooldown(player, EXECUTION, WolverineConfig.EXECUTION_MISS_COOLDOWN);
			player.displayClientMessage(Component.translatable("message.projecthero.wolverine.execution_missed")
					.withStyle(ChatFormatting.GRAY), true);
			return;
		}
		if (strike(player, target, WolverineConfig.EXECUTION_DAMAGE, 1.6) && player.level() instanceof ServerLevel level) {
			Vec3 p = target.position().add(0, target.getBbHeight() * 0.55, 0);
			level.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y, p.z, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 3, 0.5, 0.4, 0.5, 0.0);
			level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 30, 0.5, 0.5, 0.5, 0.3);
			AbilityHelpers.sound(player, SoundEvents.ANVIL_LAND, 0.8f, 1.4f);
			AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.6f);
			AbilityHelpers.sound(player, SoundEvents.IRON_GOLEM_DAMAGE, 0.9f, 0.7f);
		}
	}

	// ---------------- tick ----------------

	public static void tick(ServerPlayer player) {
		WolverineState s = Wolverine.state(player);
		if (s.hasPower) {
			long now = player.level().getGameTime();
			tickDash(player, s, now);
			if (s.chargeStartedAt != 0L) {
				tickCharge(player, s, now);
			}
		}
	}
}
