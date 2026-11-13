package com.projecthero.mod.saiyan;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.saiyan.data.SaiyanState;
import com.projecthero.mod.saiyan.kit.SaiyanKit;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.17.3 Saiyan revamp: the single server-side API of the Saiyan core. Nothing else changes {@link SaiyanState}. The numbers
 * are all in {@link SaiyanConfig}; the moves are {@link SaiyanKit}'s (routed by {@link SaiyanAbilityManager}).
 *
 * <ul>
 *   <li><b>Passives</b>: 40 max health, faster, +7 melee, faster swings, knockback resistance, a 2-block jump, Regeneration I,
 *   saturation that lasts 30% longer ({@code mixin.SaiyanHungerMixin}).</li>
 *   <li><b>Ki</b> (0..100) pays for every move: {@link #ki}, {@link #spendKi}, {@link #addKi}. +1 a second; V charges it.</li>
 *   <li><b>Rage</b> (0..100) fills in a fight; at 100, H is <b>Super Saiyan</b> for exactly 3 minutes, then a 60 s lockout.</li>
 *   <li><b>Kaioken</b>: Shift + hold V for 3 s. A big boost that eats health, never below 2 HP.</li>
 *   <li><b>Zenkai Boost</b>: 10 s below 3 hearts heals and makes him 50% stronger for a minute.</li>
 *   <li><b>A stronger enemy</b> (1.5x his max health, or a boss) toughens him while they trade blows.</li>
 * </ul>
 * {@link #damageMult} is applied to every hit he deals by {@link SaiyanDamage} -- the kit's moves need not multiply themselves.
 */
public final class Saiyan {
	public static final String KEY = "saiyan";

	private static final ResourceLocation HEALTH_ID = PowerToggles.id("saiyan_health");
	private static final ResourceLocation SPEED_ID = PowerToggles.id("saiyan_speed");
	private static final ResourceLocation ATTACK_ID = PowerToggles.id("saiyan_attack");
	private static final ResourceLocation ATTACK_SPEED_ID = PowerToggles.id("saiyan_attack_speed");
	private static final ResourceLocation KNOCKBACK_ID = PowerToggles.id("saiyan_knockback");
	private static final ResourceLocation JUMP_ID = PowerToggles.id("saiyan_jump");
	private static final ResourceLocation SAFE_FALL_ID = PowerToggles.id("saiyan_safe_fall");
	private static final ResourceLocation SSJ_SPEED_ID = PowerToggles.id("saiyan_ssj_speed");
	private static final ResourceLocation SSJ_ARMOR_ID = PowerToggles.id("saiyan_ssj_armor");
	private static final ResourceLocation KAIOKEN_SPEED_ID = PowerToggles.id("saiyan_kaioken_speed");
	private static final ResourceLocation KAIOKEN_ATTACK_SPEED_ID = PowerToggles.id("saiyan_kaioken_attack_speed");
	private static final ResourceLocation CHARGE_ID = PowerToggles.id("saiyan_charge");

	private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();
	/** Ticks spent below 3 hearts in a row (Zenkai). */
	private static final Map<UUID, Integer> LOW_HEALTH_TICKS = new ConcurrentHashMap<>();
	/** Last time he hit or was hit (the Rage decay waits). */
	private static final Map<UUID, Long> LAST_COMBAT = new ConcurrentHashMap<>();
	/** Last landed hit that gave Rage (one every {@link SaiyanConfig#RAGE_HIT_GAP} ticks). */
	private static final Map<UUID, Long> LAST_RAGE_HIT = new ConcurrentHashMap<>();
	/** The melee streak: {count, time of the last hit}. */
	private static final Map<UUID, long[]> STREAK = new ConcurrentHashMap<>();

	private Saiyan() {
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
		LAST_TICK.clear();
		LOW_HEALTH_TICKS.clear();
		LAST_COMBAT.clear();
		LAST_RAGE_HIT.clear();
		STREAK.clear();
		SaiyanFlight.clearSessionState();
		SaiyanUltraInstinct.clearSessionState();
		SaiyanAbilityManager.clearSessionState();
		SaiyanKit.clearSessionState();
	}

	static void forget(UUID id) {
		LAST_MESSAGE.remove(id);
		LAST_TICK.remove(id);
		LOW_HEALTH_TICKS.remove(id);
		LAST_COMBAT.remove(id);
		LAST_RAGE_HIT.remove(id);
		STREAK.remove(id);
		SaiyanFlight.forget(id);
		SaiyanUltraInstinct.forget(id);
		SaiyanAbilityManager.forget(id);
	}

	// ---------------------------------------------------------------- state (client and server)

	public static SaiyanState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.SAIYAN_STATE);
	}

	static void save(ServerPlayer player, SaiyanState state) {
		player.setAttached(ModAttachments.SAIYAN_STATE, state);
	}

	public static SaiyanState peek(Player player) {
		return player.getAttachedOrElse(ModAttachments.SAIYAN_STATE, null);
	}

	public static boolean hasPower(Player player) {
		SaiyanState s = peek(player);
		return s != null && s.hasPower;
	}

	private static long now(Player player) {
		return player.level().getGameTime();
	}

	/** Super Saiyan is on. Client-safe. */
	public static boolean ssj(Player player) {
		SaiyanState s = peek(player);
		return s != null && s.hasPower && s.ssj;
	}

	/** Base, Super Saiyan or (v0.17.3, secret) Ultra Instinct (the aura and tail). Client-safe. */
	public static SaiyanForm form(Player player) {
		return !ssj(player) ? SaiyanForm.BASE : SaiyanUltraInstinct.active(player) ? SaiyanForm.ULTRA : SaiyanForm.SSJ;
	}

	/** Ticks of Super Saiyan left (0 when not transformed). Client-safe. */
	public static int ssjTicksLeft(Player player) {
		SaiyanState s = peek(player);
		return s == null || !s.hasPower || !s.ssj ? 0 : (int) Math.max(0L, s.ssjUntil - now(player));
	}

	/** Ticks of the post-Super-Saiyan lockout left. Client-safe. */
	public static int lockoutTicksLeft(Player player) {
		SaiyanState s = peek(player);
		return s == null || !s.hasPower ? 0 : (int) Math.max(0L, s.ssjLockUntil - now(player));
	}

	public static float ki(Player player) {
		SaiyanState s = peek(player);
		return s == null ? 0f : s.ki;
	}

	public static float kiMax(Player player) {
		return SaiyanConfig.KI_MAX;
	}

	public static float rage(Player player) {
		SaiyanState s = peek(player);
		return s == null ? 0f : s.rage;
	}

	/** V is charging Ki (the charge aura). Client-safe. */
	public static boolean charging(Player player) {
		SaiyanState s = peek(player);
		return s != null && s.hasPower && s.charging;
	}

	public static boolean isFlying(Player player) {
		SaiyanState s = peek(player);
		return s != null && s.hasPower && s.flying;
	}

	public static boolean kaioken(Player player) {
		SaiyanState s = peek(player);
		return s != null && s.hasPower && s.kaioken();
	}

	/** 0..1 of the Shift+hold V wind-up (0 when not held). Client-safe. */
	public static float kaiokenWindup(Player player) {
		SaiyanState s = peek(player);
		if (s == null || !s.hasPower || s.kaiokenHoldAt <= 0L) {
			return 0f;
		}
		return Math.min(1f, Math.max(0f, (now(player) - s.kaiokenHoldAt) / (float) SaiyanConfig.KAIOKEN_WINDUP));
	}

	public static int kaiokenCooldown(Player player) {
		SaiyanState s = peek(player);
		return s == null || !s.hasPower ? 0 : (int) Math.max(0L, s.kaiokenReadyAt - now(player));
	}

	public static boolean zenkai(Player player) {
		SaiyanState s = peek(player);
		return s != null && s.hasPower && s.zenkaiUntil > now(player);
	}

	public static int zenkaiTicksLeft(Player player) {
		SaiyanState s = peek(player);
		return s == null || !s.hasPower ? 0 : (int) Math.max(0L, s.zenkaiUntil - now(player));
	}

	public static int zenkaiCooldown(Player player) {
		SaiyanState s = peek(player);
		return s == null || !s.hasPower ? 0 : (int) Math.max(0L, s.zenkaiReadyAt - now(player));
	}

	/** Fighting a significantly stronger enemy right now. Client-safe. */
	public static boolean strongFoe(Player player) {
		SaiyanState s = peek(player);
		return s != null && s.hasPower && s.strongFoeUntil > now(player);
	}

	/**
	 * Ticks since the player last changed form (the transformation burst plays over the first
	 * {@link SaiyanConfig#TRANSFORM_BURST_TICKS}); {@link Integer#MAX_VALUE} without the power or before any change.
	 */
	public static int transformingTicks(Player player) {
		SaiyanState s = peek(player);
		if (s == null || !s.hasPower || s.formSince <= 0L) {
			return Integer.MAX_VALUE;
		}
		long d = now(player) - s.formSince;
		return d < 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, d);
	}

	/**
	 * Everything that multiplies the damage he deals right now, added up: Super Saiyan +35%, Kaioken +40%, Zenkai +50%, and
	 * (melee only) a stronger enemy +10%. 1.0 with none. Client-safe.
	 */
	public static float damageMult(Player player, boolean melee) {
		SaiyanState s = peek(player);
		if (s == null || !s.hasPower) {
			return 1f;
		}
		long now = now(player);
		float m = 1f;
		if (s.ssj) {
			m += SaiyanConfig.SSJ_DAMAGE;
		}
		if (s.kaioken()) {
			m += SaiyanConfig.KAIOKEN_DAMAGE;
		}
		if (s.zenkaiUntil > now) {
			m += SaiyanConfig.ZENKAI_DAMAGE;
		}
		if (melee && s.strongFoeUntil > now) {
			m += SaiyanConfig.STRONG_MELEE;
		}
		m += SaiyanUltraInstinct.damageBonus(player); // v0.17.3 Ultra Instinct +25%, a counter +50% more
		return m;
	}

	/** Share of every incoming hit taken away (a stronger enemy: 10%). */
	public static float damageResist(Player player) {
		return strongFoe(player) ? SaiyanConfig.STRONG_RESIST : 0f;
	}

	/** Ki regained per tick right now (not charging): 1/s, x2 in Super Saiyan, +10% against a stronger enemy, none in flight. */
	public static float kiRegenPerTick(Player player) {
		SaiyanState s = peek(player);
		if (s == null || !s.hasPower || s.flying || SaiyanUltraInstinct.active(player)) {
			return 0f;
		}
		float r = SaiyanConfig.KI_REGEN_PER_TICK;
		if (s.ssj) {
			r *= SaiyanConfig.SSJ_KI_REGEN_MULT;
		}
		if (s.strongFoeUntil > now(player)) {
			r *= 1f + SaiyanConfig.STRONG_KI_REGEN;
		}
		return r;
	}

	/** Flight speed multiplier (Super Saiyan flies 20% faster). Client-safe. */
	public static double flightSpeedMult(Player player) {
		return !ssj(player) ? 1.0 : 1.0 + SaiyanConfig.SSJ_SPEED + (SaiyanUltraInstinct.active(player) ? SaiyanUltraInstinct.SPEED : 0.0);
	}

	public static int cooldownRemaining(Player player, String id) {
		SaiyanState s = peek(player);
		if (s == null) {
			return 0;
		}
		Long ready = s.abilityReadyAt.get(id);
		return ready == null ? 0 : (int) Math.max(0L, ready - now(player));
	}

	static void startCooldown(ServerPlayer player, String id, int ticks) {
		SaiyanState n = state(player).copy();
		n.abilityReadyAt.put(id, now(player) + ticks);
		save(player, n);
	}

	static void say(ServerPlayer player, String key, ChatFormatting colour, Object... args) {
		long now = now(player);
		Long last = LAST_MESSAGE.get(player.getUUID());
		if (last != null && now - last < 10 && now >= last) {
			return;
		}
		LAST_MESSAGE.put(player.getUUID(), now);
		player.displayClientMessage(Component.translatable(key, args).withStyle(colour), true);
	}

	// ---------------------------------------------------------------- Ki (the kit's API)

	/** Spends Ki if there is enough. False (and nothing spent, "Not enough Ki" shown) if not. Creative never pays. */
	public static boolean spendKi(ServerPlayer player, float amount) {
		SaiyanState s = state(player);
		if (!s.hasPower) {
			return false;
		}
		if (player.getAbilities().instabuild || amount <= 0f) {
			return true;
		}
		if (s.ki + 1.0e-3f < amount) {
			say(player, "message.projecthero.saiyan.no_ki", ChatFormatting.GRAY);
			return false;
		}
		SaiyanState n = s.copy();
		n.ki = Math.max(0f, s.ki - amount);
		save(player, n);
		return true;
	}

	/** Adds (or with a negative amount, takes) Ki, clamped to 0..100. */
	public static void addKi(ServerPlayer player, float amount) {
		SaiyanState s = state(player);
		if (!s.hasPower) {
			return;
		}
		setKi(player, s.ki + amount);
	}

	public static void setKi(ServerPlayer player, float value) {
		SaiyanState n = state(player).copy();
		n.ki = Math.max(0f, Math.min(SaiyanConfig.KI_MAX, value));
		save(player, n);
	}

	public static void setRage(ServerPlayer player, float value) {
		SaiyanState n = state(player).copy();
		n.rage = Math.max(0f, Math.min(SaiyanConfig.RAGE_MAX, value));
		save(player, n);
	}

	/** Rage from a fight (x1.5 below half health; none while in Super Saiyan). */
	static void addRage(ServerPlayer player, float amount) {
		SaiyanState s = state(player);
		if (!s.hasPower || s.ssj || amount <= 0f) {
			return;
		}
		if (player.getHealth() < player.getMaxHealth() * 0.5f) {
			amount *= SaiyanConfig.RAGE_LOW_HEALTH_MULT;
		}
		boolean wasFull = s.rage >= SaiyanConfig.RAGE_MAX;
		setRage(player, s.rage + amount);
		if (!wasFull && state(player).rage >= SaiyanConfig.RAGE_MAX) {
			player.displayClientMessage(Component.translatable("message.projecthero.saiyan.rage_full").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
		}
	}

	/** V: charge Ki on / off ({@link SaiyanAbilityManager}: a tap or a hold). */
	public static void setCharging(ServerPlayer player, boolean on) {
		SaiyanState s = state(player);
		if (!s.hasPower || s.charging == on) {
			return;
		}
		if (on && (!player.isAlive() || player.isSpectator())) {
			return;
		}
		SaiyanState n = s.copy();
		n.charging = on;
		save(player, n);
		reconcile(player);
		if (on) {
			// the power-up swell; the loop that follows is the client's (SaiyanLoopSounds)
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SaiyanSounds.KI_CHARGE_START, SoundSource.PLAYERS, 0.7f, 1.0f);
		}
	}

	// ---------------------------------------------------------------- the fight hooks (SaiyanDamage)

	/** He lost {@code amount} health to {@code attacker} (null for none). */
	static void onHurt(ServerPlayer player, LivingEntity attacker, float amount) {
		if (!hasPower(player) || amount <= 0f) {
			return;
		}
		LAST_COMBAT.put(player.getUUID(), now(player));
		if (attacker != null && attacker != player) {
			noteFoe(player, attacker);
		}
		addRage(player, amount * SaiyanConfig.RAGE_PER_HP_LOST);
		SaiyanUltraInstinct.onHurt(player);
	}

	/** He landed a hit on {@code target} ({@code melee}: a punch, not a Ki move). */
	static void onHitLanded(ServerPlayer player, LivingEntity target, boolean melee) {
		if (!hasPower(player) || target == player) {
			return;
		}
		long now = now(player);
		UUID id = player.getUUID();
		LAST_COMBAT.put(id, now);
		noteFoe(player, target);
		Long last = LAST_RAGE_HIT.get(id);
		if (last == null || now - last >= SaiyanConfig.RAGE_HIT_GAP || now < last) {
			LAST_RAGE_HIT.put(id, now);
			addRage(player, SaiyanConfig.RAGE_PER_HIT);
		}
		if (melee) {
			long[] st = STREAK.computeIfAbsent(id, k -> new long[] { 0L, 0L });
			if (st[0] > 0 && (now - st[1] > SaiyanConfig.STREAK_GAP || now < st[1])) {
				st[0] = 0;
			}
			st[0]++;
			st[1] = now;
			if (st[0] >= SaiyanConfig.STREAK_HITS) {
				st[0] = 0;
				addKi(player, SaiyanConfig.STREAK_KI);
			}
		}
	}

	/** Melee hits in the current streak (0..2). */
	public static int streak(ServerPlayer player) {
		long[] st = STREAK.get(player.getUUID());
		return st == null ? 0 : (int) st[0];
	}

	/** A significantly stronger enemy: a boss, or at least 1.5x his max health. */
	public static boolean isStronger(Player player, LivingEntity foe) {
		return foe != null && foe != player && foe.isAlive()
				&& (TitanCombat.isBoss(foe) || foe.getMaxHealth() >= player.getMaxHealth() * SaiyanConfig.STRONG_HEALTH_RATIO);
	}

	private static void noteFoe(ServerPlayer player, LivingEntity foe) {
		if (!isStronger(player, foe)) {
			return;
		}
		SaiyanState s = state(player);
		boolean was = s.strongFoeUntil > now(player);
		SaiyanState n = s.copy();
		n.strongFoeUntil = now(player) + SaiyanConfig.STRONG_WINDOW;
		save(player, n);
		if (!was) {
			player.displayClientMessage(Component.translatable("message.projecthero.saiyan.strong_foe").withStyle(ChatFormatting.LIGHT_PURPLE), true);
		}
	}

	// ---------------------------------------------------------------- the power

	/** The serum, a random roll or the command. False if the player already holds it (or someone else does). */
	public static boolean grant(ServerPlayer player) {
		SaiyanState old = state(player);
		if (old.hasPower) {
			return false;
		}
		if (com.projecthero.mod.hero.UniquePowers.refuse(player, KEY)) {
			return false;
		}
		HeroTiers.claimPrimary(player, KEY);
		SaiyanState s = new SaiyanState();
		s.hasPower = true;
		s.ki = SaiyanConfig.KI_MAX;
		s.formSince = now(player);
		save(player, s);
		reconcile(player);
		player.setHealth(player.getMaxHealth());
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		level.playSound(null, c.x, c.y, c.z, SaiyanSounds.TRANSFORM_BURST, SoundSource.PLAYERS, 0.8f, 1.1f);
		level.sendParticles(dust(SaiyanForm.SSJ.auraColor, 2.0f), c.x, c.y, c.z, 30, 0.5, 0.9, 0.5, 0.0);
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
				Component.translatable("message.projecthero.saiyan.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
				Component.translatable("message.projecthero.saiyan.subtitle").withStyle(ChatFormatting.YELLOW)));
		player.displayClientMessage(Component.translatable("message.projecthero.saiyan.acquired").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.saiyan.acquired_hint").withStyle(ChatFormatting.GRAY), false);
		return true;
	}

	public static void revoke(ServerPlayer player) {
		SaiyanFlight.stop(player);
		save(player, new SaiyanState());
		reconcile(player);
		forget(player.getUUID());
	}

	/** Death, a new world, leaving: the charge, the wind-up and the flight end. */
	public static void clearTransient(ServerPlayer player) {
		SaiyanState s = peek(player);
		if (s == null || !s.hasPower) {
			return;
		}
		if (s.charging || s.kaiokenHoldAt > 0L) {
			SaiyanState n = s.copy();
			n.charging = false;
			n.kaiokenHoldAt = 0L;
			save(player, n);
		}
		SaiyanFlight.stop(player);
		reconcile(player);
	}

	public static void onPlayerJoin(ServerPlayer player) {
		if (hasPower(player)) {
			clearTransient(player);
		}
	}

	/** A death ends Super Saiyan (no lockout) and Kaioken, and empties Rage; the bar comes back full. Cooldowns stay. */
	public static void onPlayerRespawn(ServerPlayer player) {
		if (!hasPower(player)) {
			return;
		}
		SaiyanUltraInstinct.onRespawn(player);
		SaiyanState n = state(player).copy();
		n.ssj = false;
		n.ssjUntil = 0L;
		n.rage = 0f;
		n.charging = false;
		n.flying = false;
		n.kaiokenSince = 0L;
		n.kaiokenHoldAt = 0L;
		n.zenkaiUntil = 0L;
		n.strongFoeUntil = 0L;
		n.ki = SaiyanConfig.KI_MAX;
		save(player, n);
		LOW_HEALTH_TICKS.remove(player.getUUID());
		reconcile(player);
	}

	// ---------------------------------------------------------------- Super Saiyan

	/** Why H can't transform right now (null = it may). */
	public static String transformRefusal(ServerPlayer player) {
		SaiyanState s = state(player);
		if (!s.hasPower || !player.isAlive() || player.isSpectator()) {
			return "message.projecthero.saiyan.cant";
		}
		if (s.ssj) {
			return "message.projecthero.saiyan.already_ssj";
		}
		if (s.ssjLockUntil > now(player)) {
			return "message.projecthero.saiyan.locked_out";
		}
		if (s.rage < SaiyanConfig.RAGE_MAX - 1.0e-3f) {
			return "message.projecthero.saiyan.rage_not_full";
		}
		return null;
	}

	/** H: Super Saiyan, if the Rage bar is full. True if he transformed. */
	public static boolean transform(ServerPlayer player) {
		// v0.17.3: H again as a Super Saiyan is the secret Ultra Instinct, once unlocked (else the usual "already" note)
		if (hasPower(player) && state(player).ssj && player.isAlive()) {
			return SaiyanUltraInstinct.enter(player);
		}
		String why = transformRefusal(player);
		if (why != null) {
			say(player, why, ChatFormatting.GRAY, (lockoutTicksLeft(player) + 19) / 20, Math.round(state(player).rage));
			return false;
		}
		if (kaioken(player)) {
			stopKaioken(player, false);
		}
		long now = now(player);
		SaiyanState n = state(player).copy();
		n.ssj = true;
		n.formSince = now;
		n.ssjUntil = now + SaiyanConfig.SSJ_TICKS;
		n.rage = 0f;
		save(player, n);
		reconcile(player);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position();
		// the flash itself (rays, pillar, shockwave, debris) is drawn by every client from transformingTicks
		level.playSound(null, c.x, c.y + 1.0, c.z, SaiyanSounds.TRANSFORM_BURST, SoundSource.PLAYERS, 1.0f, 0.95f);
		level.sendParticles(dust(SaiyanForm.SSJ.auraColor, 2.0f), c.x, c.y + 1.0, c.z, 40, 0.6, 1.0, 0.6, 0.0);
		player.displayClientMessage(Component.translatable(SaiyanForm.SSJ.nameKey()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
		return true;
	}

	/** The 3 minutes are up: back to Base, Rage at 0 and a 60 s lockout. */
	static void endSsj(ServerPlayer player) {
		SaiyanState s = state(player);
		if (!s.ssj) {
			return;
		}
		long now = now(player);
		SaiyanState n = s.copy();
		n.ssj = false;
		n.ssjUntil = 0L;
		n.formSince = now;
		n.rage = 0f;
		n.ssjLockUntil = now + SaiyanConfig.SSJ_LOCKOUT;
		save(player, n);
		reconcile(player);
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SaiyanSounds.POWER_DOWN, SoundSource.PLAYERS, 0.8f, 1.0f);
		level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SaiyanSounds.EXHAUSTED, SoundSource.PLAYERS, 0.7f, 1.0f);
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.4, 0.6, 0.4, 0.02);
		player.displayClientMessage(Component.translatable("message.projecthero.saiyan.ssj_over").withStyle(ChatFormatting.RED), true);
	}

	/** Command hook: lifts the post-Super-Saiyan lockout. */
	public static void clearLockout(ServerPlayer player) {
		SaiyanState n = state(player).copy();
		n.ssjLockUntil = 0L;
		save(player, n);
	}

	/** Test / command hook: ends Super Saiyan now, as if the timer had run out. */
	public static void forceEndSsj(ServerPlayer player) {
		endSsj(player);
	}

	// ---------------------------------------------------------------- Kaioken

	/** Why Kaioken can't start (null = it may). */
	public static String kaiokenRefusal(ServerPlayer player) {
		SaiyanState s = state(player);
		if (!s.hasPower || !player.isAlive() || player.isSpectator()) {
			return "message.projecthero.saiyan.cant";
		}
		if (s.ssj) {
			return "message.projecthero.saiyan.kaioken_ssj";
		}
		if (s.kaiokenReadyAt > now(player)) {
			return "message.projecthero.saiyan.kaioken_cooldown";
		}
		if (player.getHealth() <= SaiyanConfig.KAIOKEN_FLOOR + SaiyanConfig.KAIOKEN_DRAIN) {
			return "message.projecthero.saiyan.kaioken_weak";
		}
		return null;
	}

	public static boolean startKaioken(ServerPlayer player) {
		String why = kaiokenRefusal(player);
		if (why != null) {
			say(player, why, ChatFormatting.GRAY, (kaiokenCooldown(player) + 19) / 20);
			return false;
		}
		SaiyanState n = state(player).copy();
		n.kaiokenSince = now(player);
		save(player, n);
		reconcile(player);
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SaiyanSounds.TRANSFORM_BURST, SoundSource.PLAYERS, 0.8f, 1.25f);
		level.sendParticles(dust(0xFFFF2A2A, 2.0f), player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.5, 0.9, 0.5, 0.0);
		player.displayClientMessage(Component.translatable("message.projecthero.saiyan.kaioken_on").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
		return true;
	}

	/** Kaioken ends (toggled off, the 2 HP floor, transforming): the 30 s cooldown starts. */
	public static void stopKaioken(ServerPlayer player, boolean announce) {
		SaiyanState s = state(player);
		if (!s.kaioken()) {
			return;
		}
		SaiyanState n = s.copy();
		n.kaiokenSince = 0L;
		n.kaiokenReadyAt = now(player) + SaiyanConfig.KAIOKEN_COOLDOWN;
		save(player, n);
		reconcile(player);
		player.level().playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SaiyanSounds.POWER_DOWN, SoundSource.PLAYERS, 0.6f, 1.2f);
		if (announce) {
			player.displayClientMessage(Component.translatable("message.projecthero.saiyan.kaioken_off").withStyle(ChatFormatting.GRAY), true);
		}
	}

	// ---------------------------------------------------------------- Zenkai

	private static void zenkai(ServerPlayer player) {
		long now = now(player);
		SaiyanState n = state(player).copy();
		n.zenkaiUntil = now + SaiyanConfig.ZENKAI_TICKS;
		n.zenkaiReadyAt = now + SaiyanConfig.ZENKAI_COOLDOWN;
		save(player, n);
		player.heal(SaiyanConfig.ZENKAI_HEAL);
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SaiyanSounds.KI_CHARGE_START, SoundSource.PLAYERS, 0.9f, 1.3f);
		level.sendParticles(dust(0xFFFFB020, 1.6f), player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.4, 0.8, 0.4, 0.0);
		player.displayClientMessage(Component.translatable("message.projecthero.saiyan.zenkai").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
	}

	static DustParticleOptions dust(int argb, float size) {
		return new DustParticleOptions(new Vector3f(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f), size);
	}

	// ---------------------------------------------------------------- stats

	public static void reconcile(ServerPlayer player) {
		SaiyanState s = peek(player);
		if (s == null || !s.hasPower) {
			PowerToggles.clearModifier(player, Attributes.MAX_HEALTH, HEALTH_ID);
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID);
			PowerToggles.clearModifier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID);
			PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID);
			PowerToggles.clearModifier(player, Attributes.JUMP_STRENGTH, JUMP_ID);
			PowerToggles.clearModifier(player, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL_ID);
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, SSJ_SPEED_ID);
			PowerToggles.clearModifier(player, Attributes.ARMOR, SSJ_ARMOR_ID);
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, KAIOKEN_SPEED_ID);
			PowerToggles.clearModifier(player, Attributes.ATTACK_SPEED, KAIOKEN_ATTACK_SPEED_ID);
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, CHARGE_ID);
			SaiyanUltraInstinct.reconcile(player, false);
			if (player.getHealth() > player.getMaxHealth()) {
				player.setHealth(player.getMaxHealth());
			}
			return;
		}
		PowerToggles.modifier(player, Attributes.MAX_HEALTH, HEALTH_ID, SaiyanConfig.HEALTH_BONUS, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, SaiyanConfig.SPEED_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID, SaiyanConfig.MELEE_BONUS, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID, SaiyanConfig.ATTACK_SPEED_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		PowerToggles.modifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, SaiyanConfig.KNOCKBACK_RESIST, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.JUMP_STRENGTH, JUMP_ID, SaiyanConfig.JUMP_BONUS, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL_ID, SaiyanConfig.SAFE_FALL_BONUS, AttributeModifier.Operation.ADD_VALUE);
		toggle(player, s.ssj, Attributes.MOVEMENT_SPEED, SSJ_SPEED_ID, SaiyanConfig.SSJ_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		toggle(player, s.ssj, Attributes.ARMOR, SSJ_ARMOR_ID, SaiyanConfig.SSJ_ARMOR, AttributeModifier.Operation.ADD_VALUE);
		toggle(player, s.kaioken(), Attributes.MOVEMENT_SPEED, KAIOKEN_SPEED_ID, SaiyanConfig.KAIOKEN_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		toggle(player, s.kaioken(), Attributes.ATTACK_SPEED, KAIOKEN_ATTACK_SPEED_ID, SaiyanConfig.KAIOKEN_ATTACK_SPEED,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		toggle(player, s.charging, Attributes.MOVEMENT_SPEED, CHARGE_ID, SaiyanConfig.CHARGE_SLOW, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		SaiyanUltraInstinct.reconcile(player, true);
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	private static void toggle(ServerPlayer player, boolean on, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
			ResourceLocation id, double amount, AttributeModifier.Operation op) {
		if (on) {
			PowerToggles.modifier(player, attr, id, amount, op);
		} else {
			PowerToggles.clearModifier(player, attr, id);
		}
	}

	// ---------------------------------------------------------------- tick

	public static void tick(ServerPlayer player) {
		SaiyanState s = player.getAttachedOrElse(ModAttachments.SAIYAN_STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		long now = now(player);
		UUID id = player.getUUID();
		Long prev = LAST_TICK.put(id, now);
		if (prev != null && prev == now) {
			return;
		}
		// Super Saiyan's 3 minutes
		if (s.ssj && now >= s.ssjUntil && !SaiyanUltraInstinct.active(player)) {
			endSsj(player); // (Ultra Instinct holds the form until it ends, then takes it along)
		}
		// Kaioken eats health: 1 HP every 1.5 s, never below 2 (it ends there)
		s = state(player);
		if (s.kaioken() && player.isAlive()) {
			long on = now - s.kaiokenSince;
			if (on > 0 && on % SaiyanConfig.KAIOKEN_DRAIN_EVERY == 0) {
				float h = player.getHealth() - SaiyanConfig.KAIOKEN_DRAIN;
				if (h <= SaiyanConfig.KAIOKEN_FLOOR) {
					player.setHealth(Math.min(player.getHealth(), SaiyanConfig.KAIOKEN_FLOOR));
					stopKaioken(player, false);
					player.displayClientMessage(Component.translatable("message.projecthero.saiyan.kaioken_spent").withStyle(ChatFormatting.RED), true);
				} else {
					player.setHealth(h);
				}
			}
		}
		// Zenkai: 10 s below 3 hearts
		s = state(player);
		if (player.isAlive() && now >= s.zenkaiReadyAt && player.getHealth() < SaiyanConfig.ZENKAI_HEALTH) {
			int t = LOW_HEALTH_TICKS.merge(id, 1, Integer::sum);
			if (t >= SaiyanConfig.ZENKAI_LOW_TICKS) {
				LOW_HEALTH_TICKS.remove(id);
				zenkai(player);
			}
		} else {
			LOW_HEALTH_TICKS.remove(id);
		}
		// Ki
		s = state(player);
		boolean creative = player.getAbilities().instabuild;
		float ki = s.ki;
		if (s.charging) {
			ki += SaiyanConfig.KI_CHARGE_PER_TICK;
		} else {
			ki += kiRegenPerTick(player);
		}
		if (s.flying && !creative) {
			ki -= SaiyanConfig.KI_FLIGHT_PER_TICK;
		}
		s.ki = Math.max(0f, Math.min(SaiyanConfig.KI_MAX, ki));
		// Rage cools off out of combat
		Long fought = LAST_COMBAT.get(id);
		if (s.rage > 0f && !s.ssj && (fought == null || now - fought >= SaiyanConfig.RAGE_IDLE_TICKS || now < fought)) {
			s.rage = Math.max(0f, s.rage - SaiyanConfig.RAGE_DECAY_PER_TICK);
		}
		if (s.ki <= 0f && s.flying) {
			SaiyanFlight.stop(player);
		}
		// v0.17.3 the secret Ultra Instinct: its unlock watch, or its Ki drain and end
		SaiyanUltraInstinct.tick(player);
		// Regeneration I (paused while Kaioken burns)
		s = state(player);
		if (!s.kaioken() && player.isAlive() && now % SaiyanConfig.REGEN_INTERVAL == 0 && player.getHealth() < player.getMaxHealth()) {
			player.heal(SaiyanConfig.REGEN_AMOUNT);
		}
		if (player.tickCount % 20 == 0) {
			reconcile(player);
		}
		SaiyanFlight.tick(player);
		SaiyanAbilityManager.tick(player);
		SaiyanKit.tick(player);
		if (now % SaiyanConfig.SYNC_INTERVAL == 0) {
			save(player, state(player).copy());
		}
	}
}
