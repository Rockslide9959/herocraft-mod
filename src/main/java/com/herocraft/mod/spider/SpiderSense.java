package com.herocraft.mod.spider;

import com.herocraft.mod.network.SpiderSenseGlowPayload;
import com.herocraft.mod.network.SpiderSenseWarningPayload;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Spider Sense (v0.6.17 overhaul). Still event-driven for the reactive half -- it hangs off the same
 * {@code ALLOW_DAMAGE} veto the rest of the mod's damage rules use -- but now backed by a throttled
 * per-player scan that looks for trouble <em>before</em> it lands and points the player at it.
 *
 * <h2>What it does</h2>
 * <ul>
 *   <li><b>Threat / directional warning.</b> A mob or player that has locked onto you within ~24
 *       blocks, an attack winding up in melee range, a hostile projectile closing on you, a primed
 *       explosion, or a hazard right beside you (lava, a falling block) all fire a cue: a quiet chime
 *       plus a crosshair pulse and a marker pointing left / right / behind / above / below. Several
 *       can be lit at once, and each stays up as long as the sense keeps feeling that danger
 *       (v0.6.19).</li>
 *   <li><b>Attack prediction.</b> The melee cue fires while the mob's swing is winding up, not when
 *       the hit connects, and opens a short <em>perfect-dodge window</em>.</li>
 *   <li><b>Automatic dodge.</b> 50% of eligible melee hits are negated outright, and (v0.7.5) 60% of
 *       eligible ranged / projectile hits. v0.6.21: the dodge no
 *       longer shoves the player -- the forced velocity fought with web-swing and wall-crawl. The hit
 *       simply does not land; the player keeps their own momentum.</li>
 *   <li><b>Manual perfect dodge.</b> Move, jump or crouch during the warning window and the next
 *       eligible hit is guaranteed to be negated.</li>
 *   <li><b>Projectile catching / deflection.</b> A dodged arrow is caught and kept; a dodged
 *       trident / fireball / snowball / other projectile is sometimes flung straight back instead.</li>
 * </ul>
 *
 * <p><b>Simplifications, noted honestly:</b> "attack prediction" is a proximity + swing-animation
 * heuristic, not true per-frame animation timing; "environmental danger" covers lava and falling
 * blocks (the hazards with a clean server-side signal), not every conceivable one; explosion
 * detection covers primed TNT, swelling creepers and end crystals.
 */
public final class SpiderSense {
	/** Spec section 22: half of all eligible melee attacks are avoided. */
	public static final float DODGE_CHANCE = 0.5f;
	/** v0.7.5: ranged / projectile attacks are easier for the sense to read -- 60% are avoided. */
	public static final float RANGED_DODGE_CHANCE = 0.6f;
	/** Minimum ticks between two dodges, so a crowd cannot chain-teleport the player around. */
	private static final int DODGE_INTERVAL = 6;

	/** How far off a threat lock is still felt. */
	private static final double THREAT_RANGE = 24.0;
	/** Ticks the perfect-dodge window stays open after a prediction cue. */
	private static final int DODGE_WINDOW_TICKS = 12;

	// warning kinds, matching SpiderSenseWarningPayload
	private static final int KIND_MELEE = 0;
	private static final int KIND_PROJECTILE = 1;
	private static final int KIND_EXPLOSION = 2;
	// kind 3 (fall) retired in v0.6.19 -- Spider-Man takes no fall damage now, so there is nothing to warn about
	private static final int KIND_ENVIRONMENT = 4;
	private static final int KIND_TARGETED = 5;

	// cooldown-map keys (they ride on the player's existing spider cooldown map -- transient in spirit)
	private static final String LAST_DODGE = "spider_sense_dodge";
	private static final String DODGE_WINDOW = "spider_sense_window";
	private static final String WARN_PREFIX = "spider_sense_warn_";

	/**
	 * v0.9.3: the entity ids the current threat scan turned up for the player being ticked. Populated by
	 * {@link #markThreatGlow} during a scan pass and sent to <em>that player only</em> as a
	 * {@link SpiderSenseGlowPayload}; their client outlines exactly these red. Nothing is written to the
	 * mob itself, so no other player's client is told anything (spec: "nobody else should be able to
	 * benefit from this power"). Server is single-threaded, so this one reused set is safe.
	 */
	private static final it.unimi.dsi.fastutil.ints.IntOpenHashSet SCAN_GLOW = new it.unimi.dsi.fastutil.ints.IntOpenHashSet();

	private SpiderSense() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(SpiderSense::onAllowDamage);
	}

	/** Record a threatening mob for the red danger glow -- per-viewer only, sent below in {@link #serverTick}. */
	private static void markThreatGlow(net.minecraft.world.entity.Entity threat) {
		if (threat instanceof net.minecraft.world.entity.LivingEntity living && !(living instanceof ServerPlayer)) {
			SCAN_GLOW.add(living.getId());
		}
	}

	// ---------------- eligibility ----------------

	/**
	 * Whether this damage is the sort a danger sense could plausibly get you out of the way of: an
	 * attack, delivered by something, that has not already landed by fiat.
	 */
	public static boolean eligible(DamageSource source) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.BYPASSES_EFFECTS)) {
			return false;
		}
		if (source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.GENERIC_KILL)
				|| source.is(DamageTypes.OUTSIDE_BORDER) || source.is(DamageTypes.STARVE)
				|| source.is(DamageTypes.DROWN) || source.is(DamageTypes.DRY_OUT)
				|| source.is(DamageTypes.WITHER) || source.is(DamageTypes.MAGIC)
				|| source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_FIRE)
				|| source.is(DamageTypeTags.IS_FREEZING) || source.is(DamageTypeTags.IS_DROWNING)) {
			return false;
		}
		return source.getEntity() != null || source.getDirectEntity() instanceof Projectile;
	}

	private static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || !SpiderMan.hasPower(player)) {
			return true;
		}
		if (!eligible(source)) {
			return true;
		}
		warnOfHit(player, source);

		long now = player.level().getGameTime();
		if (!dodgeOffCooldown(player, now)) {
			return true;
		}

		boolean perfect = inDodgeWindow(player, now) && isEvading(player);
		if (!perfect && !rollFor(player, source)) {
			return true;
		}
		dodge(player, source);
		return false;
	}

	/** True when the incoming damage is a ranged / projectile hit rather than a melee blow. */
	private static boolean isRanged(DamageSource source) {
		return source.getDirectEntity() instanceof Projectile;
	}

	/** The melee dodge roll -- explicit generator so tests can drive it deterministically. */
	public static boolean roll(RandomSource random) {
		return random.nextFloat() < DODGE_CHANCE;
	}

	/** The dodge roll for a specific hit: {@link #RANGED_DODGE_CHANCE} for ranged / projectile
	 * attacks, {@link #DODGE_CHANCE} otherwise. */
	public static boolean roll(RandomSource random, DamageSource source) {
		return random.nextFloat() < (isRanged(source) ? RANGED_DODGE_CHANCE : DODGE_CHANCE);
	}

	/**
	 * Black Suit Spider-Man: Spider Sense dodge chance 50% -> ~60% (spec) -- a flat +0.1 on top of
	 * whichever base chance {@link #roll} would have used, deliberately not multiplicative so it stays
	 * a clear, bounded bump rather than compounding with the ranged rate.
	 */
	public static boolean rollFor(ServerPlayer player, DamageSource source) {
		float bonus = com.herocraft.mod.symbiote.Symbiote.isActive(player)
				&& com.herocraft.mod.symbiote.SymbioteHostType.of(player) == com.herocraft.mod.symbiote.SymbioteHostType.SPIDER_MAN
				? 0.1f : 0.0f;
		return player.getRandom().nextFloat() < (isRanged(source) ? RANGED_DODGE_CHANCE : DODGE_CHANCE) + bonus;
	}

	// ---------------- the reactive dodge ----------------

	/**
	 * Whether the reactive dodge is off its short interval cooldown.
	 *
	 * <p>v0.6.19 bug fix: this used to be {@code now - lastDodge < DODGE_INTERVAL} where
	 * {@code lastDodge} returned {@link Long#MIN_VALUE} for "never dodged". {@code now - Long.MIN_VALUE}
	 * overflows to a large <em>negative</em> number, which is always {@code < DODGE_INTERVAL} -- so a
	 * player who had not dodged yet could never dodge, and since a failed dodge never stamps the key,
	 * that was every player, forever. Now it is a plain ready-at check with no arithmetic on the
	 * sentinel.
	 */
	private static boolean dodgeOffCooldown(ServerPlayer player, long now) {
		Long readyAt = SpiderMan.state(player).abilityReadyAt.get(LAST_DODGE);
		return readyAt == null || now >= readyAt;
	}

	private static boolean inDodgeWindow(ServerPlayer player, long now) {
		Long until = SpiderMan.state(player).abilityReadyAt.get(DODGE_WINDOW);
		return until != null && now < until;
	}

	/** Move, jump or crouch: the deliberate evasion that turns the warning into a guaranteed dodge. */
	private static boolean isEvading(ServerPlayer player) {
		Vec3 v = player.getDeltaMovement();
		double horizontal = Math.sqrt(v.x * v.x + v.z * v.z);
		return horizontal > 0.09 || v.y > 0.20 || player.isShiftKeyDown();
	}

	private static void dodge(ServerPlayer player, DamageSource source) {
		SpiderMan.triggerCooldown(player, LAST_DODGE, DODGE_INTERVAL);

		// v0.6.21: the dodge no longer shoves the player. The forced velocity + per-hit motion packet
		// fought with web-swing and wall-crawl movement (the player got yanked off their arc). The hit
		// is still slipped -- {@code onAllowDamage} returns false -- and a thrown projectile is still
		// caught or deflected; the player just stays where their own momentum was taking them.
		if (!catchProjectile(player, source)) {
			deflectProjectile(player, source);
		}

		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5f, 1.8f);
		// v0.6.21: only the Spider-Man player sees their own sense react.
		level.sendParticles(player, ParticleTypes.CRIT, false, player.getX(), player.getY() + 1.0, player.getZ(),
				8, 0.3, 0.4, 0.3, 0.05);
	}

	/**
	 * A dodged arrow is plucked out of the air: the projectile is discarded and exactly one matching
	 * item is handed over (a full inventory drops it). Returns true if a projectile was consumed.
	 */
	private static boolean catchProjectile(ServerPlayer player, DamageSource source) {
		if (!(source.getDirectEntity() instanceof AbstractArrow arrow) || !arrow.isAlive()) {
			return false;
		}
		ItemStack caught = arrow.getPickupItemStackOrigin().copy();
		if (caught.isEmpty()) {
			caught = new ItemStack(Items.ARROW);
		}
		caught.setCount(1);
		arrow.discard();

		if (!player.getInventory().add(caught)) {
			player.drop(caught, false);
		}
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6f, 1.4f);
		player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		level.sendParticles(ParticleTypes.ITEM_COBWEB, player.getX(), player.getEyeY(), player.getZ(),
				5, 0.2, 0.2, 0.2, 0.0);
		return true;
	}

	/**
	 * A dodged non-arrow projectile -- a trident, a fireball, a snowball -- is sometimes redirected
	 * back the way it came instead of merely missing.
	 */
	private static void deflectProjectile(ServerPlayer player, DamageSource source) {
		if (!(source.getDirectEntity() instanceof Projectile projectile) || !projectile.isAlive()) {
			return;
		}
		if (player.getRandom().nextFloat() > 0.6f) {
			return;
		}
		Vec3 back = player.getLookAngle();
		Entity shooter = projectile.getOwner();
		if (shooter != null) {
			Vec3 toShooter = shooter.position().add(0, shooter.getBbHeight() * 0.5, 0)
					.subtract(projectile.position());
			if (toShooter.lengthSqr() > 1.0E-4) {
				back = toShooter.normalize();
			}
		}
		double speed = Math.max(0.8, projectile.getDeltaMovement().length());
		projectile.setDeltaMovement(back.scale(speed));
		projectile.setOwner(player);
		if (projectile instanceof ThrownTrident) {
			projectile.setDeltaMovement(back.scale(Math.max(1.6, speed)));
		}
		projectile.hurtMarked = true;
		((ServerLevel) player.level()).playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.7f, 1.5f);
	}

	// ---------------- the warning cue ----------------

	private static void warnOfHit(ServerPlayer player, DamageSource source) {
		Entity attacker = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
		int kind = source.getDirectEntity() instanceof Projectile ? KIND_PROJECTILE : KIND_MELEE;
		Vec3 at = attacker != null ? attacker.position().add(0, attacker.getBbHeight() * 0.5, 0) : null;
		fireWarning(player, kind, at);
	}

	/**
	 * Warn of a threat of {@code kind}, pointing toward {@code threatPos} (may be null for "no
	 * direction").
	 *
	 * <p>v0.6.19: the client marker is refreshed <em>every</em> call, so as long as the scan keeps
	 * seeing this threat the HUD keeps showing it -- it only fades once the danger is genuinely gone.
	 * Several kinds can be lit at once (a mob behind you, an arrow ahead, lava below). Only the audible
	 * / particle cue is throttled per kind, so a persistent threat is not a persistent chime.
	 */
	private static void fireWarning(ServerPlayer player, int kind, Vec3 threatPos) {
		long now = player.level().getGameTime();
		if (kind == KIND_MELEE || kind == KIND_PROJECTILE) {
			openDodgeWindow(player, now);
		}

		float yaw = player.getYRot();
		int vertical = 0;
		if (threatPos != null) {
			Vec3 d = threatPos.subtract(player.getEyePosition());
			if (d.x * d.x + d.z * d.z > 1.0E-4) {
				yaw = (float) (Mth.atan2(-d.x, d.z) * (180.0 / Math.PI));
			}
			if (d.y > 2.0) {
				vertical = 1;
			} else if (d.y < -2.0) {
				vertical = 2;
			}
		}

		// Throttle only the chime + particle cue; the HUD marker below always refreshes.
		String cueKey = WARN_PREFIX + kind;
		Long until = SpiderMan.state(player).abilityReadyAt.get(cueKey);
		if (until == null || now >= until) {
			SpiderMan.triggerCooldown(player, cueKey, 24);
			player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.35f, 2.0f);
			spawnDirectionalCue(player, threatPos);
		}
		ServerPlayNetworking.send(player, new SpiderSenseWarningPayload(kind, yaw, vertical));
	}

	/**
	 * The Spider-Sense visual cue (v0.6.21). Sent <em>only</em> to the Spider-Man player -- no other
	 * player sees another's danger sense go off -- and drawn as a short arrow of particles springing
	 * from just in front of the face and pointing <em>straight at</em> the threat, not a vague cloud.
	 * With no known direction it falls back to a small sparkle right at eye level.
	 */
	private static void spawnDirectionalCue(ServerPlayer player, Vec3 threatPos) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 dir = threatPos != null ? threatPos.subtract(eye) : Vec3.ZERO;
		if (dir.lengthSqr() < 1.0E-4) {
			level.sendParticles(player, ParticleTypes.ENCHANT, false, eye.x, eye.y, eye.z, 6, 0.2, 0.2, 0.2, 0.02);
			return;
		}
		Vec3 unit = dir.normalize();
		for (int i = 1; i <= 7; i++) {
			Vec3 p = eye.add(unit.scale(0.5 + i * 0.35));
			level.sendParticles(player, ParticleTypes.ENCHANTED_HIT, false, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		Vec3 tip = eye.add(unit.scale(0.5 + 7 * 0.35));
		level.sendParticles(player, ParticleTypes.CRIT, false, tip.x, tip.y, tip.z, 5, 0.04, 0.04, 0.04, 0.0);
	}

	private static void openDodgeWindow(ServerPlayer player, long now) {
		if (inDodgeWindow(player, now)) {
			return;
		}
		SpiderMan.triggerCooldown(player, DODGE_WINDOW, DODGE_WINDOW_TICKS);
	}

	// ---------------- the pre-emptive scan ----------------

	/**
	 * Throttled per-player look-ahead. Nothing here loops over a chunk's worth of hostiles -- every
	 * query is a small box around the player, and each sub-scan runs only every few ticks.
	 */
	public static void serverTick(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		int t = player.tickCount;

		if (t % 5 == 0) {
			SCAN_GLOW.clear();
			scanIncomingAttacks(player, level);
		}
		if (t % 10 == 0) {
			scanTargeting(player, level);
			scanExplosions(player, level);
			scanEnvironment(player, level);
			// v0.9.3: hand this player (and only this player) the current red-glow threat set.
			ServerPlayNetworking.send(player, new SpiderSenseGlowPayload(SCAN_GLOW.toIntArray()));
		}
	}

	/** A wind-up in melee range, or a hostile projectile closing on the player: predict the hit. */
	private static void scanIncomingAttacks(ServerPlayer player, ServerLevel level) {
		AABB melee = player.getBoundingBox().inflate(3.6, 2.0, 3.6);
		for (Mob mob : level.getEntitiesOfClass(Mob.class, melee,
				m -> m.isAlive() && m.getTarget() == player)) {
			if (mob.swinging || mob.attackAnim > 0.0f) {
				fireWarning(player, KIND_MELEE, mob.position().add(0, mob.getBbHeight() * 0.5, 0));
				markThreatGlow(mob);
				break;
			}
		}

		AABB near = player.getBoundingBox().inflate(18.0, 12.0, 18.0);
		for (Projectile proj : level.getEntitiesOfClass(Projectile.class, near, Entity::isAlive)) {
			if (proj.getOwner() == player) {
				continue;
			}
			Vec3 toPlayer = player.getEyePosition().subtract(proj.position());
			Vec3 vel = proj.getDeltaMovement();
			if (vel.lengthSqr() < 1.0E-4 || toPlayer.lengthSqr() < 1.0E-4) {
				continue;
			}
			// closing, and roughly aimed at the player
			if (vel.normalize().dot(toPlayer.normalize()) > 0.9) {
				fireWarning(player, KIND_PROJECTILE, proj.position());
				return;
			}
		}
	}

	/** Something within ~24 blocks has locked onto the player but has not swung yet. */
	private static void scanTargeting(ServerPlayer player, ServerLevel level) {
		AABB box = player.getBoundingBox().inflate(THREAT_RANGE, 10.0, THREAT_RANGE);
		boolean warned = false;
		for (Mob mob : level.getEntitiesOfClass(Mob.class, box,
				m -> m.isAlive() && m.getTarget() == player)) {
			if (mob.distanceToSqr(player) > THREAT_RANGE * THREAT_RANGE) {
				continue;
			}
			// v0.6.22: glow EVERY nearby mob hunting the player red, but only raise one directional cue.
			markThreatGlow(mob);
			if (!warned) {
				fireWarning(player, KIND_TARGETED, mob.position().add(0, mob.getBbHeight() * 0.5, 0));
				warned = true;
			}
		}
	}

	private static void scanExplosions(ServerPlayer player, ServerLevel level) {
		AABB box = player.getBoundingBox().inflate(8.0);
		for (Entity e : level.getEntitiesOfClass(Entity.class, box, Entity::isAlive)) {
			boolean danger = e instanceof net.minecraft.world.entity.item.PrimedTnt
					|| e instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal
					|| (e instanceof Creeper creeper && (creeper.isIgnited() || creeper.getSwellDir() > 0));
			if (danger) {
				fireWarning(player, KIND_EXPLOSION, e.position().add(0, e.getBbHeight() * 0.5, 0));
				return;
			}
		}
	}

	private static void scanEnvironment(ServerPlayer player, ServerLevel level) {
		// a falling block (anvil, dripstone, gravel) directly overhead
		AABB above = new AABB(player.getX() - 0.6, player.getY() + 0.5, player.getZ() - 0.6,
				player.getX() + 0.6, player.getY() + 7.0, player.getZ() + 0.6);
		for (FallingBlockEntity fb : level.getEntitiesOfClass(FallingBlockEntity.class, above, Entity::isAlive)) {
			fireWarning(player, KIND_ENVIRONMENT, fb.position());
			return;
		}
		// lava within a block or two of the feet
		BlockPos base = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, -1, -2), base.offset(2, 1, 2))) {
			FluidState fluid = level.getFluidState(pos);
			if (fluid.is(net.minecraft.tags.FluidTags.LAVA)) {
				fireWarning(player, KIND_ENVIRONMENT,
						new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
				return;
			}
		}
	}

}
