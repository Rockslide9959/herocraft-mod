package com.projecthero.mod.ghostrider;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ghostrider.data.GhostRiderState;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.power.PowerToggles;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * The single server-side API for Ghost Rider, the v0.16.5 Hero-Tier power. Nothing else touches
 * {@link GhostRiderState}; every change re-saves through {@link ServerPlayer#setAttached}. The moves live in
 * {@link GhostRiderAbilities}, the damage rules and guilt in {@link GhostRiderDamage}, the numbers in
 * {@link GhostRiderConfig}.
 *
 * <h2>How it works</h2>
 * Mephisto's Contract (rare Nether fortress / bastion loot) is signed in blood: the Spirit of Vengeance bonds with the
 * signer, who pays 5 hearts of max health until the first transformation. <b>H</b> sets the head ablaze any time: the
 * flames climb the body and the head burns down to a flaming skull. Transformed: immune to fire and lava, +6 max
 * health, never drowns, undead fear him, and the six keys work. Human: fire can't hurt him, nothing else.
 *
 * <h2>Hellfire and Vengeance</h2>
 * Hellfire (0..100) pays for the moves and refills at Superman's sun rate, always. Every creature that hurts him, a
 * villager or a player is <i>guilty</i>; hitting the guilty fills Vengeance (0..100), which the Penance Stare burns.
 *
 * <h2>v0.16.5 rework (the user's list)</h2>
 * Transformed: +11 melee, Regeneration III that costs 2 Hellfire a second (it stops when the Hellfire is gone), food
 * goes twice as fast. H only turns him back out of combat (nothing dealt or taken for
 * {@link GhostRiderConfig#OUT_OF_COMBAT_TICKS}) with no hostile monster within 20 blocks. And he can't die: a lethal hit
 * leaves him on 6 health -- forced into the Spirit if he was human -- and the Spirit then holds on until the next
 * sunrise ({@link GhostRiderState#dayLockUntil}). {@code /kill} and the void still kill.
 */
public final class GhostRider {
	public static final String KEY = "ghost_rider";

	private static final ResourceLocation BLOOD_PRICE_ID = PowerToggles.id("ghost_rider_blood_price");
	private static final ResourceLocation HEALTH_ID = PowerToggles.id("ghost_rider_health");
	private static final ResourceLocation ATTACK_ID = PowerToggles.id("ghost_rider_attack");

	/** Hellfire orange and an ember red. */
	public static final DustParticleOptions HELLFIRE = new DustParticleOptions(new Vector3f(1.0f, 0.45f, 0.05f), 1.4f);
	public static final DustParticleOptions HELLFIRE_BIG = new DustParticleOptions(new Vector3f(1.0f, 0.55f, 0.1f), 2.4f);
	public static final DustParticleOptions EMBER = new DustParticleOptions(new Vector3f(0.75f, 0.08f, 0.02f), 1.1f);

	private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TOGGLE = new ConcurrentHashMap<>();
	/** Game time a guilty creature was last hit by each Ghost Rider (Vengeance only decays after 10 s without). */
	private static final Map<UUID, Long> LAST_GUILTY_HIT = new ConcurrentHashMap<>();
	/** Game time each Ghost Rider last dealt or took combat damage (H won't turn him back for a while after). */
	private static final Map<UUID, Long> LAST_COMBAT = new ConcurrentHashMap<>();
	/** Food exhaustion at the last tick (transformed, every rise is doubled). */
	private static final Map<UUID, Float> LAST_EXHAUSTION = new ConcurrentHashMap<>();

	private GhostRider() {
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
		LAST_TICK.clear();
		LAST_TOGGLE.clear();
		LAST_GUILTY_HIT.clear();
		LAST_COMBAT.clear();
		LAST_EXHAUSTION.clear();
		GhostRiderAbilities.clearSessionState();
		GhostRiderAbilityManager.clearSessionState();
		GhostRiderDamage.clearSessionState();
	}

	// ---------------------------------------------------------------- state

	public static GhostRiderState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.GHOST_RIDER_STATE);
	}

	static void save(ServerPlayer player, GhostRiderState state) {
		player.setAttached(ModAttachments.GHOST_RIDER_STATE, state);
	}

	private static GhostRiderState peek(Player player) {
		return player.getAttachedOrElse(ModAttachments.GHOST_RIDER_STATE, null);
	}

	/** Client-safe (the attachment is synced). */
	public static boolean hasPower(Player player) {
		GhostRiderState s = peek(player);
		return s != null && s.hasPower;
	}

	/** Has the power and the Spirit is out. Client-safe. */
	public static boolean transformed(Player player) {
		GhostRiderState s = peek(player);
		return s != null && s.hasPower && s.transformed;
	}

	public static float hellfire(Player player) {
		GhostRiderState s = peek(player);
		return s == null ? 0f : s.hellfire;
	}

	public static float vengeance(Player player) {
		GhostRiderState s = peek(player);
		return s == null ? 0f : s.vengeance;
	}

	public static boolean shieldUp(Player player) {
		GhostRiderState s = peek(player);
		return s != null && s.transformed && s.shieldUntil > player.level().getGameTime();
	}

	public static int cooldownRemaining(Player player, String abilityId) {
		GhostRiderState s = peek(player);
		if (s == null) {
			return 0;
		}
		Long ready = s.abilityReadyAt.get(abilityId);
		return ready == null ? 0 : (int) Math.max(0L, ready - player.level().getGameTime());
	}

	/** Hellfire per second before the Regeneration's cost: Superman's sun rate, always (v0.16.5 rework). Client-safe. */
	public static float regenPerSecond(Player player) {
		GhostRiderState s = peek(player);
		return s == null || !s.hasPower ? 0f : GhostRiderConfig.HELLFIRE_REGEN;
	}

	/** Hellfire per second after the Regeneration III's cost (what the HUD shows). Client-safe. */
	public static float netRegenPerSecond(Player player) {
		GhostRiderState s = peek(player);
		if (s == null || !s.hasPower) {
			return 0f;
		}
		return GhostRiderConfig.HELLFIRE_REGEN - (s.transformed ? GhostRiderConfig.REGEN_HELLFIRE_PER_SECOND : 0f);
	}

	/** The Spirit holds on until sunrise (a cheated death): H can't turn him back. Client-safe. */
	public static boolean dayLocked(Player player) {
		GhostRiderState s = peek(player);
		return s != null && s.hasPower && s.dayLockUntil > 0L;
	}

	static void say(ServerPlayer player, String key, ChatFormatting colour, Object... args) {
		long now = player.level().getGameTime();
		Long last = LAST_MESSAGE.get(player.getUUID());
		if (last != null && now - last < 10 && now >= last) {
			return;
		}
		LAST_MESSAGE.put(player.getUUID(), now);
		player.displayClientMessage(Component.translatable(key, args).withStyle(colour), true);
	}

	// ---------------------------------------------------------------- resources

	public static boolean spendHellfire(ServerPlayer player, float cost) {
		if (cost <= 0f) {
			return true;
		}
		GhostRiderState s = state(player);
		if (s.hellfire + 1.0e-3f < cost) {
			return false;
		}
		GhostRiderState n = s.copy();
		n.hellfire = Math.max(0f, Math.min(GhostRiderConfig.HELLFIRE_MAX, s.hellfire) - cost);
		save(player, n);
		return true;
	}

	public static void setHellfire(ServerPlayer player, float value) {
		GhostRiderState n = state(player).copy();
		n.hellfire = Math.max(0f, Math.min(GhostRiderConfig.HELLFIRE_MAX, value));
		save(player, n);
	}

	public static void setVengeance(ServerPlayer player, float value) {
		GhostRiderState n = state(player).copy();
		n.vengeance = Math.max(0f, Math.min(GhostRiderConfig.VENGEANCE_MAX, value));
		save(player, n);
	}

	/** A hit on a guilty creature: Vengeance grows with the damage. */
	static void feedVengeance(ServerPlayer player, float damage) {
		LAST_GUILTY_HIT.put(player.getUUID(), player.level().getGameTime());
		GhostRiderState s = state(player);
		float next = Math.min(GhostRiderConfig.VENGEANCE_MAX, s.vengeance + damage * GhostRiderConfig.VENGEANCE_PER_DAMAGE);
		if (next != s.vengeance) {
			GhostRiderState n = s.copy();
			n.vengeance = next;
			save(player, n);
		}
	}

	// ---------------------------------------------------------------- grant / revoke

	/** Mephisto's Contract / the command. False if the player already carries the Spirit. */
	public static boolean grant(ServerPlayer player) {
		if (state(player).hasPower) {
			return false;
		}
		if (com.projecthero.mod.hero.UniquePowers.refuse(player, KEY)) {
			return false;
		}
		HeroTiers.claimPrimary(player, KEY);
		GhostRiderState s = new GhostRiderState();
		s.hasPower = true;
		s.hellfire = GhostRiderConfig.HELLFIRE_MAX;
		save(player, s);
		reconcile(player);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 80, 0.5, 1.0, 0.5, 0.05);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y, c.z, 30, 0.4, 0.8, 0.4, 0.03);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 30, 0.5, 1.0, 0.5, 0.02);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.6f, 1.4f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.2f, 0.6f);
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
				Component.translatable("message.projecthero.ghost_rider.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
				Component.translatable("message.projecthero.ghost_rider.subtitle").withStyle(ChatFormatting.RED)));
		player.displayClientMessage(Component.translatable("message.projecthero.ghost_rider.acquired")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.ghost_rider.acquired_hint")
				.withStyle(ChatFormatting.YELLOW), false);
		return true;
	}

	public static void revoke(ServerPlayer player) {
		GhostRiderAbilities.clear(player);
		GhostRiderAbilities.dismissCycle(player);
		save(player, new GhostRiderState());
		reconcile(player);
		forget(player.getUUID());
	}

	private static void forget(UUID id) {
		LAST_MESSAGE.remove(id);
		LAST_TICK.remove(id);
		LAST_TOGGLE.remove(id);
		LAST_GUILTY_HIT.remove(id);
		LAST_COMBAT.remove(id);
		LAST_EXHAUSTION.remove(id);
		GhostRiderAbilityManager.forget(id);
	}

	// ---------------------------------------------------------------- the change (H)

	/** H: the Spirit of Vengeance out or back (debounced). */
	public static void toggleForm(ServerPlayer player) {
		GhostRiderState s = state(player);
		if (!s.hasPower || !player.isAlive() || player.isSpectator()) {
			return;
		}
		long now = player.level().getGameTime();
		Long last = LAST_TOGGLE.get(player.getUUID());
		if (last != null && now - last < GhostRiderConfig.TOGGLE_COOLDOWN && now >= last) {
			return;
		}
		LAST_TOGGLE.put(player.getUUID(), now);
		if (s.transformed) {
			String refusal = revertRefusal(player);
			if (refusal != null) {
				long left = GhostRiderConfig.OUT_OF_COMBAT_TICKS - (now - LAST_COMBAT.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2));
				say(player, refusal, ChatFormatting.RED, Math.max(1, (int) Math.ceil(left / 20.0)));
				return;
			}
			revert(player);
		} else {
			transform(player);
		}
	}

	// ---------------------------------------------------------------- the rules for turning back (v0.16.5 rework)

	/** He just dealt or took combat damage. */
	static void markCombat(ServerPlayer player) {
		LAST_COMBAT.put(player.getUUID(), player.level().getGameTime());
	}

	/** Has he dealt or taken combat damage in the last {@link GhostRiderConfig#OUT_OF_COMBAT_TICKS}? */
	public static boolean inCombat(ServerPlayer player) {
		Long last = LAST_COMBAT.get(player.getUUID());
		long now = player.level().getGameTime();
		return last != null && now >= last && now - last < GhostRiderConfig.OUT_OF_COMBAT_TICKS;
	}

	/** A hostile monster ({@link Enemy}) within {@link GhostRiderConfig#REVERT_HOSTILE_RANGE} blocks. */
	public static boolean hostileNear(ServerPlayer player) {
		double r = GhostRiderConfig.REVERT_HOSTILE_RANGE;
		return !player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(r),
				m -> m instanceof Enemy && m.isAlive() && m.distanceToSqr(player) <= r * r).isEmpty();
	}

	/** Why H may not turn him back right now (a lang key), or null if it may. */
	public static String revertRefusal(ServerPlayer player) {
		if (dayLocked(player)) {
			return "message.projecthero.ghost_rider.revert_locked";
		}
		if (inCombat(player)) {
			return "message.projecthero.ghost_rider.revert_combat";
		}
		if (hostileNear(player)) {
			return "message.projecthero.ghost_rider.revert_hostile";
		}
		return null;
	}

	/** Overworld day time (the day/night every dimension shares). */
	private static long dayTime(ServerPlayer player) {
		return player.server.overworld().getDayTime();
	}

	/** The next sunrise after {@code dayTime} (day time 0 of the next day). */
	public static long nextSunrise(long dayTime) {
		return (Math.floorDiv(dayTime, 24000L) + 1L) * 24000L;
	}

	/** Is {@code dayTime} in the day window (sunrise to sunset)? */
	public static boolean isDaytime(long dayTime) {
		return Math.floorMod(dayTime, 24000L) < 12000L;
	}

	/**
	 * The lock lifts at the first daytime at or after its sunrise (or at once if the clock was set back past the death,
	 * e.g. {@code /time set day}). Public for the tests, which hand in the time instead of moving the world's clock.
	 */
	public static void tickDayLock(ServerPlayer player, long dayTime) {
		GhostRiderState s = state(player);
		if (s.dayLockUntil <= 0L || !isDaytime(dayTime)) {
			return;
		}
		if (dayTime >= s.dayLockUntil || dayTime < s.dayLockUntil - 24000L) {
			GhostRiderState n = s.copy();
			n.dayLockUntil = 0L;
			save(player, n);
			player.displayClientMessage(Component.translatable("message.projecthero.ghost_rider.lock_lifted").withStyle(ChatFormatting.GOLD), true);
		}
	}

	/**
	 * A lethal hit (Fabric's {@code ALLOW_DEATH}): he doesn't die. Left on {@link GhostRiderConfig#DEATH_SAVE_HEALTH}, a
	 * human is forced into the Spirit, and either way the Spirit holds on until the next sunrise. True if the death was
	 * cheated. {@code /kill}, the void and anything that bypasses invulnerability still kill.
	 */
	public static boolean cheatDeath(ServerPlayer player, DamageSource source) {
		GhostRiderState s = state(player);
		if (!s.hasPower || player.isSpectator() || source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)
				|| source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		boolean wasHuman = !s.transformed;
		GhostRiderState n = s.copy();
		n.dayLockUntil = nextSunrise(dayTime(player));
		save(player, n);
		if (wasHuman) {
			transform(player); // the Spirit takes over
		}
		player.setHealth(Math.min(player.getMaxHealth(), GhostRiderConfig.DEATH_SAVE_HEALTH));
		player.clearFire();
		markCombat(player);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 60, 0.4, 0.9, 0.4, 0.06);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y, c.z, 30, 0.4, 0.9, 0.4, 0.04);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.5f, 1.6f);
		player.displayClientMessage(Component.translatable(wasHuman ? "message.projecthero.ghost_rider.death_taken"
				: "message.projecthero.ghost_rider.death_cheated").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
		return true;
	}

	public static void transform(ServerPlayer player) {
		GhostRiderState s = state(player);
		if (!s.hasPower || s.transformed) {
			return;
		}
		long now = player.level().getGameTime();
		GhostRiderState n = s.copy();
		n.transformed = true;
		n.formChangeAt = now;
		n.bonded = true; // the blood price is paid with the first change
		n.animId = GhostRiderState.ANIM_TRANSFORM;
		n.animStart = now;
		save(player, n);
		reconcile(player);
		player.clearFire();
		GhostRiderAbilities.beginTransformFx(player);
	}

	public static void revert(ServerPlayer player) {
		GhostRiderState s = state(player);
		if (!s.transformed) {
			return;
		}
		GhostRiderAbilities.clear(player);
		GhostRiderAbilities.dismissCycle(player);
		long now = player.level().getGameTime();
		GhostRiderState n = state(player).copy();
		n.transformed = false;
		n.formChangeAt = now;
		n.blasting = false;
		n.chainUntil = 0L;
		n.chainTarget = java.util.List.of();
		n.spinUntil = 0L;
		n.shieldUntil = 0L;
		n.stareTarget = -1;
		n.rushing = false;
		n.animId = GhostRiderState.ANIM_REVERT;
		n.animStart = now;
		save(player, n);
		reconcile(player);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.6, 0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 30, 0.3, 0.5, 0.3, 0.02);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0f, 0.7f);
	}

	// ---------------------------------------------------------------- stats

	/** Max-health modifiers: the blood price until bonded, +6 while transformed; +11 melee while transformed. */
	public static void reconcile(ServerPlayer player) {
		GhostRiderState s = peek(player);
		boolean owes = s != null && s.hasPower && !s.bonded;
		boolean out = s != null && s.hasPower && s.transformed;
		if (owes) {
			PowerToggles.modifier(player, Attributes.MAX_HEALTH, BLOOD_PRICE_ID, -GhostRiderConfig.BLOOD_PRICE, AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(player, Attributes.MAX_HEALTH, BLOOD_PRICE_ID);
		}
		if (out) {
			PowerToggles.modifier(player, Attributes.MAX_HEALTH, HEALTH_ID, GhostRiderConfig.MAX_HEALTH_BONUS, AttributeModifier.Operation.ADD_VALUE);
			PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID, GhostRiderConfig.MELEE_BONUS, AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(player, Attributes.MAX_HEALTH, HEALTH_ID);
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID);
			clearRegen(player);
		}
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	/** Whether he may use a move right now; says why not. */
	static boolean canAct(ServerPlayer player) {
		GhostRiderState s = state(player);
		if (!s.hasPower || !player.isAlive() || player.isSpectator()) {
			return false;
		}
		if (!s.transformed) {
			say(player, "message.projecthero.ghost_rider.not_transformed", ChatFormatting.GRAY);
			return false;
		}
		if (player.level().getGameTime() - s.formChangeAt < GhostRiderConfig.TRANSFORM_TICKS - 10) {
			return false; // still burning in
		}
		return true;
	}

	// ---------------------------------------------------------------- tick

	public static void tick(ServerPlayer player) {
		GhostRiderState s = player.getAttachedOrElse(ModAttachments.GHOST_RIDER_STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		long now = player.level().getGameTime();
		Long prev = LAST_TICK.put(player.getUUID(), now);
		if (prev != null && prev == now) {
			return;
		}
		if (player.tickCount % 20 == 0) {
			reconcile(player);
		}
		if (now % 5 == 0) {
			tickResources(player, s, now);
		}
		if (player.isOnFire()) {
			player.clearFire(); // hellfire can't burn its own host
		}
		tickHunger(player, s.transformed);
		if (s.dayLockUntil > 0L && now % 20 == 0) {
			tickDayLock(player, dayTime(player));
		}
		if (s.transformed) {
			if (player.getAirSupply() < player.getMaxAirSupply()) {
				player.setAirSupply(player.getMaxAirSupply());
			}
			if (now % 20 == 0) {
				tickUndeadFear(player);
			}
			if (now % 4 == 0 && player.isAlive()) {
				// embers rising off the skull for everyone else (the owner sees the client-side flames)
				ServerLevel level = (ServerLevel) player.level();
				level.sendParticles(ParticleTypes.SMALL_FLAME, player.getX(), player.getEyeY() + 0.45, player.getZ(), 1, 0.12, 0.1, 0.12, 0.01);
			}
		}
		GhostRiderAbilities.tick(player);
	}

	private static void tickResources(ServerPlayer player, GhostRiderState s, long now) {
		float hf = Float.isNaN(s.hellfire) ? 0f : s.hellfire;
		float nextHf = Math.min(GhostRiderConfig.HELLFIRE_MAX, hf + regenPerSecond(player) / 4f);
		// transformed: Regeneration III, paid for in Hellfire; it stops when there's none left to pay
		float regenCost = GhostRiderConfig.REGEN_HELLFIRE_PER_SECOND / 4f;
		if (s.transformed && player.isAlive() && nextHf + 1.0e-4f >= regenCost) {
			nextHf -= regenCost;
			applyRegen(player);
		} else {
			clearRegen(player);
		}
		float v = Float.isNaN(s.vengeance) ? 0f : s.vengeance;
		Long lastHit = LAST_GUILTY_HIT.get(player.getUUID());
		float nextV = v;
		if (v > 0f && (lastHit == null || now - lastHit > 200L || now < lastHit)) {
			nextV = Math.max(0f, v - GhostRiderConfig.VENGEANCE_DECAY / 4f);
		}
		if (nextHf != s.hellfire || nextV != s.vengeance) {
			GhostRiderState n = s.copy();
			n.hellfire = nextHf;
			n.vengeance = nextV;
			save(player, n);
		}
	}

	/** Our Regeneration III: hidden, topped up while it runs (never weakens a stronger one). */
	private static void applyRegen(ServerPlayer player) {
		MobEffectInstance cur = player.getEffect(MobEffects.REGENERATION);
		if (cur == null || cur.getAmplifier() < GhostRiderConfig.REGEN_AMPLIFIER
				|| (cur.getAmplifier() == GhostRiderConfig.REGEN_AMPLIFIER && !cur.isInfiniteDuration() && cur.getDuration() < 40)) {
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, GhostRiderConfig.REGEN_AMPLIFIER, true, false, true));
		}
	}

	/** Removes our Regeneration III (out of Hellfire, turned back). */
	private static void clearRegen(ServerPlayer player) {
		MobEffectInstance cur = player.getEffect(MobEffects.REGENERATION);
		if (cur != null && cur.getAmplifier() == GhostRiderConfig.REGEN_AMPLIFIER && !cur.isInfiniteDuration() && cur.getDuration() <= 100
				&& cur.isAmbient()) {
			player.removeEffect(MobEffects.REGENERATION);
		}
	}

	/** Transformed, every rise in food exhaustion is doubled (the Cyborg's halving, the other way). */
	private static void tickHunger(ServerPlayer player, boolean transformed) {
		float e = player.getFoodData().getExhaustionLevel();
		Float last = LAST_EXHAUSTION.put(player.getUUID(), e);
		if (transformed && last != null && e > last) {
			float doubled = Math.min(40f, last + (e - last) * GhostRiderConfig.HUNGER_FACTOR);
			player.getFoodData().setExhaustion(doubled);
			LAST_EXHAUSTION.put(player.getUUID(), doubled);
		}
	}

	/** Undead that came for him lose their nerve: their target drops and they back away (wither skeletons flee). */
	private static void tickUndeadFear(ServerPlayer player) {
		double r = GhostRiderConfig.UNDEAD_FEAR_RANGE;
		AABB box = player.getBoundingBox().inflate(r);
		for (Mob mob : player.level().getEntitiesOfClass(Mob.class, box,
				m -> m.getType().is(EntityTypeTags.UNDEAD) && m.isAlive() && !GhostRiderCombat.isBoss(m))) {
			boolean flee = mob instanceof WitherSkeleton || mob.getTarget() == player;
			if (mob.getTarget() == player) {
				mob.setTarget(null);
			}
			if (flee) {
				Vec3 away = mob.position().subtract(player.position());
				away = away.horizontalDistanceSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : new Vec3(away.x, 0, away.z).normalize();
				Vec3 to = mob.position().add(away.scale(10.0));
				mob.getNavigation().moveTo(to.x, to.y, to.z, 1.4);
			}
		}
	}

	// ---------------------------------------------------------------- lifecycle

	public static void onPlayerJoin(ServerPlayer player) {
		GhostRiderState s = state(player);
		if (!s.hasPower) {
			return;
		}
		long now = player.level().getGameTime();
		GhostRiderState n = s.copy();
		n.blasting = false;
		n.rushing = false;
		n.chainUntil = 0L;
		n.chainTarget = java.util.List.of();
		n.spinUntil = 0L;
		n.shieldUntil = 0L;
		n.stareTarget = -1;
		n.animId = GhostRiderState.ANIM_NONE;
		n.formChangeAt = 0L;
		n.hellfire = Float.isNaN(n.hellfire) ? 0f : Math.max(0f, Math.min(GhostRiderConfig.HELLFIRE_MAX, n.hellfire));
		n.vengeance = Float.isNaN(n.vengeance) ? 0f : Math.max(0f, Math.min(GhostRiderConfig.VENGEANCE_MAX, n.vengeance));
		n.abilityReadyAt.entrySet().removeIf(e -> e.getValue() > now + GhostRiderConfig.WRATH_COOLDOWN + 20L);
		save(player, n);
		reconcile(player);
	}

	public static void onPlayerRespawn(ServerPlayer player) {
		GhostRiderState s = state(player);
		if (!s.hasPower) {
			return;
		}
		GhostRiderState n = s.copy();
		n.transformed = false;
		n.formChangeAt = 0L;
		n.blasting = false;
		n.rushing = false;
		n.chainUntil = 0L;
		n.chainTarget = java.util.List.of();
		n.spinUntil = 0L;
		n.shieldUntil = 0L;
		n.stareTarget = -1;
		n.animId = GhostRiderState.ANIM_NONE;
		n.hellfire = Math.max(n.hellfire, GhostRiderConfig.HELLFIRE_MAX * 0.5f);
		n.vengeance = 0f;
		n.dayLockUntil = 0L; // (only /kill or the void get this far)
		n.abilityReadyAt.clear();
		save(player, n);
		reconcile(player);
	}

	/** Death / logout / dimension change: drop everything transient (the power and the form stay). */
	public static void clearTransient(ServerPlayer player) {
		GhostRiderAbilities.clear(player);
		GhostRiderAbilities.dismissCycle(player);
		forget(player.getUUID());
		GhostRiderState s = player.getAttachedOrElse(ModAttachments.GHOST_RIDER_STATE, null);
		if (s != null && s.hasPower && (s.blasting || s.rushing || s.chainUntil != 0L || s.spinUntil != 0L || s.shieldUntil != 0L
				|| s.stareTarget != -1)) {
			GhostRiderState n = s.copy();
			n.blasting = false;
			n.rushing = false;
			n.chainUntil = 0L;
			n.chainTarget = java.util.List.of();
			n.spinUntil = 0L;
			n.shieldUntil = 0L;
			n.stareTarget = -1;
			save(player, n);
		}
	}

	static void setAnim(ServerPlayer player, int animId) {
		GhostRiderState n = state(player).copy();
		n.animId = animId;
		n.animStart = player.level().getGameTime();
		save(player, n);
	}
}
