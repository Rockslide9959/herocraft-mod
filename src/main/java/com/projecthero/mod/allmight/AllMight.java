package com.projecthero.mod.allmight;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.allmight.data.AllMightState;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.power.AbilityHelpers;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * The single server-side API for the All Might / One For All Hero-Tier power (v0.12.33). Nothing else pokes
 * {@link AllMightState}; every mutator re-saves through {@link ServerPlayer#setAttached}. The abilities live in
 * {@link AllMightAbilities}, the shared air-pressure code in {@link AllMightShockwave}, the numbers in
 * {@link AllMightConfig}, passing on / lending in {@link AllMightPassOn}.
 *
 * <h2>Two forms (v0.12.34)</h2>
 * <b>Base Form</b> is a plain player -- no bonuses, no passives, no abilities. <b>H</b> switches to the <b>Power Form</b>: he grows to
 * 2.7 blocks over a second (steaming), gets 13 melee, 40 max HP (the health percentage carries over), 50% less damage, Speed III,
 * a slow self-heal and a 3-block jump, and the abilities unlock. The stats are recomputed from {@code (fullPower)} by
 * {@link #reconcile}, which only ever sets fixed-id transient attribute modifiers -- so pressing H any number of times cannot
 * stack anything.
 *
 * <h2>The One For All timer (v0.12.39, back in v0.16.7 at 20 minutes -- and the only limit)</h2>
 * Being in the Power Form drains a 20:00 timer 1 s a second, the Base Form refills it at 0.5 s a second, Plus Ultra and the United
 * States of Smash spend a one-off slice, and at 0:00 he is forced back to the Base Form ({@link #exhaust}); H waits until 4 s have
 * come back. Numbers: {@link AllMightConfig#timer()}. v0.16.7 (user: "remove strain and output, it should just be the 20 minute
 * timer like how it was before"): every Smash lands its full designed damage in the Power Form (x{@code PLUS_ULTRA_MULTIPLIER}
 * during Plus Ultra, {@link #smashMultiplier}); the Output percentage, the safe limit and the per-limb strain are gone. See
 * {@code docs/OFA_TIMER.md}.
 */
public final class AllMight {
	public static final String KEY = "all_might";

	private static final ResourceLocation HEALTH_ID = PowerToggles.id("all_might_health");
	private static final ResourceLocation ATTACK_ID = PowerToggles.id("all_might_attack");
	private static final ResourceLocation JUMP_ID = PowerToggles.id("all_might_jump");
	private static final ResourceLocation SCALE_ID = PowerToggles.id("all_might_scale");
	private static final ResourceLocation REACH_ID = PowerToggles.id("all_might_reach");

	/** Highest fall distance seen since the last time the player stood on the ground (for the landing impacts). */
	private static final Map<UUID, Float> PEAK_FALL = new HashMap<>();
	/** Per-player throttle for action-bar feedback. */
	private static final Map<UUID, Long> LAST_MESSAGE = new HashMap<>();
	/** v0.16.7: the game tick the One For All timer last stepped (so a second call in the same tick cannot drain it twice). */
	private static final Map<UUID, Long> LAST_TIMER_STEP = new HashMap<>();

	private AllMight() {
	}

	public static void clearSessionState() {
		PEAK_FALL.clear();
		LAST_TIMER_STEP.clear();
		LAST_MESSAGE.clear();
		AllMightAbilities.clearSessionState();
		AllMightPassOn.clearSessionState();
	}

	// ---------------------------------------------------------------- state access

	public static AllMightState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.ALL_MIGHT_STATE);
	}

	static void save(ServerPlayer player, AllMightState state) {
		player.setAttached(ModAttachments.ALL_MIGHT_STATE, state);
	}

	/** Safe on the client too (the attachment is synced); the server never trusts a client's copy. */
	public static boolean hasPower(Player player) {
		AllMightState s = player.getAttachedOrElse(ModAttachments.ALL_MIGHT_STATE, null);
		return s != null && s.hasPower;
	}

	public static boolean isFullPower(Player player) {
		AllMightState s = player.getAttachedOrElse(ModAttachments.ALL_MIGHT_STATE, null);
		return s != null && s.hasPower && s.fullPower;
	}

	public static boolean plusUltraActive(Player player) {
		AllMightState s = player.getAttachedOrElse(ModAttachments.ALL_MIGHT_STATE, null);
		return s != null && s.hasPower && s.fullPower && s.plusUltra;
	}

	/** Inside the damage-proof transformation window. */
	public static boolean transforming(Player player) {
		AllMightState s = player.getAttachedOrElse(ModAttachments.ALL_MIGHT_STATE, null);
		return s != null && s.hasPower && s.transformUntil > player.level().getGameTime();
	}

	/** v0.16.6: holds a borrowed One For All (on a loan timer, cannot pass it on). */
	public static boolean isBorrower(Player player) {
		AllMightState s = player.getAttachedOrElse(ModAttachments.ALL_MIGHT_STATE, null);
		return s != null && s.hasPower && s.loanRole == AllMightState.LOAN_BORROWER;
	}

	/** v0.16.6: lent their One For All out (holds no power until it comes back). */
	public static boolean isLender(Player player) {
		AllMightState s = player.getAttachedOrElse(ModAttachments.ALL_MIGHT_STATE, null);
		return s != null && !s.hasPower && s.loanRole == AllMightState.LOAN_LENDER;
	}

	public static int cooldownRemaining(Player player, String abilityId) {
		AllMightState s = player.getAttachedOrElse(ModAttachments.ALL_MIGHT_STATE, null);
		if (s == null) {
			return 0;
		}
		Long ready = s.abilityReadyAt.get(abilityId);
		return ready == null ? 0 : (int) Math.max(0L, ready - player.level().getGameTime());
	}

	/**
	 * The damage multiplier on every Smash (v0.12.39 rules, back in v0.16.7): x1 -- the full designed damage -- in the Power Form,
	 * x{@link AllMightConfig#PLUS_ULTRA_MULTIPLIER} while Plus Ultra runs.
	 */
	public static float smashMultiplier(Player player) {
		return plusUltraActive(player) ? AllMightConfig.PLUS_ULTRA_MULTIPLIER : 1.0f;
	}

	static void say(ServerPlayer player, String key, ChatFormatting colour, Object... args) {
		long now = player.level().getGameTime();
		Long last = LAST_MESSAGE.get(player.getUUID());
		if (last != null && now - last < 10) {
			return;
		}
		LAST_MESSAGE.put(player.getUUID(), now);
		player.displayClientMessage(Component.translatable(key, args).withStyle(colour), true);
	}

	public static void markCombat(ServerPlayer player) {
		AllMightState s = state(player);
		long now = player.level().getGameTime();
		if (now - s.lastCombatTick >= 5L) { // cheap: at most one attachment write per 5 ticks
			AllMightState n = s.copy();
			n.lastCombatTick = now;
			save(player, n);
		}
	}

	// ---------------------------------------------------------------- v0.16.7: the One For All timer (back, at 20 minutes)

	/** The full timer, in seconds (config: 1200 = 20:00 of Power Form). */
	public static float timerMax() {
		return Math.max(1f, AllMightConfig.timer().powerFormSeconds);
	}

	/** Seconds of Power Form left (an old save / a new holder has a full timer), never above the full timer. */
	public static float timeLeft(AllMightState s) {
		float max = timerMax();
		return s.ofaTimer < 0f ? max : Math.min(max, s.ofaTimer);
	}

	public static float timeLeft(ServerPlayer player) {
		return timeLeft(state(player));
	}

	/** Sets the seconds left on {@code n} (clamped) and records the full timer for the HUD. */
	static void setTimeLeft(AllMightState n, float seconds) {
		n.ofaTimer = Math.max(0f, Math.min(timerMax(), seconds));
		n.ofaTimerMax = timerMax();
	}

	/** Sets the timer directly (tests, the harness). */
	public static void setTimer(ServerPlayer player, float seconds) {
		AllMightState n = state(player).copy();
		setTimeLeft(n, seconds);
		save(player, n);
	}

	/** Spends {@code cost} seconds of the timer if there is that much left (+ {@code margin}); false (nothing spent) otherwise. */
	static boolean spendTimer(ServerPlayer player, float cost, float margin) {
		AllMightState s = state(player);
		float left = timeLeft(s);
		if (left + 1.0e-3f < cost + margin) {
			say(player, "message.projecthero.all_might.low_ofa", ChatFormatting.RED, clock(cost), clock(left));
			return false;
		}
		AllMightState n = s.copy();
		setTimeLeft(n, left - cost);
		save(player, n);
		return true;
	}

	/** m:ss, rounded down (as the HUD shows it). */
	public static String clock(float seconds) {
		int secs = (int) Math.floor(Math.max(0f, seconds) + 1.0e-3f);
		return String.format(java.util.Locale.ROOT, "%d:%02d", secs / 60, secs % 60);
	}

	/**
	 * v0.12.39, back in v0.16.7 at 20 minutes: once a second the Power Form drains the timer and the Base Form refills it. At 0:00 he
	 * is forced back to the Base Form ({@link #exhaust}). Returns true if that happened this tick.
	 */
	private static boolean tickTimer(ServerPlayer player, long now) {
		AllMightState s = state(player);
		float left = timeLeft(s);
		AllMightConfig.Timer t = AllMightConfig.timer();
		Long last = LAST_TIMER_STEP.get(player.getUUID());
		if (now % 20L == 0L && (last == null || last != now)) {
			LAST_TIMER_STEP.put(player.getUUID(), now); // once per game tick, however often tick() is called in it
			float next = s.fullPower ? Math.max(0f, left - t.drainPerSecond) : Math.min(timerMax(), left + t.rechargePerSecond);
			if (Math.abs(next - left) > 1.0e-4f || s.ofaTimer < 0f) {
				AllMightState n = s.copy();
				setTimeLeft(n, next);
				save(player, n);
			}
			left = next;
		}
		if (s.fullPower && left <= 0f) {
			exhaust(player);
			return true;
		}
		return false;
	}

	/** The One For All timer ran out: back to the Base Form (the old v0.12.39 message and hiss). */
	static void exhaust(ServerPlayer player) {
		AllMightState s = state(player);
		if (!s.hasPower || !s.fullPower) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		changeForm(player, false);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.2f, 0.7f);
		player.displayClientMessage(Component.translatable("message.projecthero.all_might.exhausted").withStyle(ChatFormatting.RED), true);
	}

	// ---------------------------------------------------------------- grant / revoke

	/** Use the Vestige / command. Returns false if the player already has the power (or is lending it out). */
	public static boolean grant(ServerPlayer player) {
		AllMightState cur = state(player);
		if (cur.hasPower || cur.loanRole == AllMightState.LOAN_LENDER) {
			return false;
		}
		// v0.16.1: projectheroUniquePowers -- refused (nothing granted) while someone else holds it
		if (com.projecthero.mod.hero.UniquePowers.refuse(player, KEY)) {
			return false;
		}
		HeroTiers.claimPrimary(player, KEY);
		AllMightState s = fresh(player);
		save(player, s);
		reconcile(player);
		acquiredFx(player);
		player.displayClientMessage(Component.translatable("message.projecthero.all_might.acquired")
				.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.all_might.acquired_hint")
				.withStyle(ChatFormatting.YELLOW), false);
		return true;
	}

	/** A new holder's state: Base Form, a full timer, H ready. */
	static AllMightState fresh(ServerPlayer player) {
		long now = player.level().getGameTime();
		AllMightState s = new AllMightState();
		s.hasPower = true;
		s.fullPower = false;
		s.formChangedAt = now - AllMightConfig.FORM_TOGGLE_DEBOUNCE_TICKS; // H works straight away
		return s;
	}

	static void acquiredFx(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.4f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8f, 1.6f);
		AllMightShockwave.burst(level, ParticleTypes.ELECTRIC_SPARK, c, 50, 0.6, 0.35);
		AllMightShockwave.burst(level, new DustParticleOptions(new Vector3f(1.0f, 0.78f, 0.2f), 1.4f), c, 25, 0.7, 0.05);
	}

	public static void revoke(ServerPlayer player) {
		revoke(player, true);
	}

	/** @param returnCostume give the worn costume and the locker's contents back to the player (not for a dead player: it would vanish) */
	public static void revoke(ServerPlayer player, boolean returnCostume) {
		AllMightState old = state(player);
		AllMightAbilities.cancelCharge(player, false);
		AllMightAbilities.clear(player.getUUID());
		PEAK_FALL.remove(player.getUUID());
		AllMightState s = new AllMightState(); // hasPower=false, no loan
		s.lendReadyAt = old.lendReadyAt;
		save(player, s);
		reconcile(player);
		if (returnCostume) {
			AllMightSuit.returnAll(player);
		} else {
			AllMightSuit.strip(player);
		}
		// v0.16.6: a borrower losing the power (time, death, logout, a new Primary power...) sends it home to the lender
		if (old.loanRole == AllMightState.LOAN_BORROWER) {
			AllMightPassOn.returnToLender(player, old);
		} else if (old.loanRole == AllMightState.LOAN_LENDER) {
			AllMightPassOn.clearLenderEffects(player);
		}
	}

	// ---------------------------------------------------------------- stats

	/** The multiplier on the vanilla jump velocity (0.42) that carries a player {@code blocks} high. */
	static double jumpVelocityModifier(double blocks) {
		return AllMightAbilities.verticalSpeedForHeight(blocks) / 0.42 - 1.0;
	}

	/**
	 * Brings every attribute modifier and effect in line with {@code (hasPower, fullPower)}. Idempotent -- fixed ids, each branch
	 * checks before it writes -- so it is safe to call as often as you like (H spam, the once-a-second audit, join, respawn).
	 * The body size is not set here: it eases in and out over a second ({@link #tickScale}).
	 */
	public static void reconcile(ServerPlayer player) {
		AllMightState s = state(player);
		if (!s.hasPower || !s.fullPower) {
			PowerToggles.clearModifier(player, Attributes.MAX_HEALTH, HEALTH_ID);
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID);
			PowerToggles.clearModifier(player, Attributes.JUMP_STRENGTH, JUMP_ID);
			PowerToggles.clearModifier(player, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID);
			clearOurEffect(player, MobEffects.MOVEMENT_SPEED, AllMightConfig.FULL_SPEED_AMPLIFIER);
			clearOurEffect(player, MobEffects.REGENERATION, 0);
			if (!s.hasPower) {
				PowerToggles.clearModifier(player, Attributes.SCALE, SCALE_ID);
			}
			if (player.getHealth() > player.getMaxHealth()) {
				player.setHealth(player.getMaxHealth());
			}
			return;
		}
		PowerToggles.modifier(player, Attributes.MAX_HEALTH, HEALTH_ID, AllMightConfig.FULL_HEALTH_BONUS, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID, AllMightConfig.FULL_ATTACK_BONUS, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.JUMP_STRENGTH, JUMP_ID, jumpVelocityModifier(AllMightConfig.FULL_JUMP_BLOCKS),
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		PowerToggles.modifier(player, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID, AllMightConfig.FULL_REACH_BONUS, AttributeModifier.Operation.ADD_VALUE);
		keepEffect(player, MobEffects.MOVEMENT_SPEED, AllMightConfig.FULL_SPEED_AMPLIFIER);
	}

	/** A short hidden effect that is topped up while it is running low, so it never clobbers another power's infinite one and lapses on its own. */
	static void keepEffect(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int amplifier) {
		MobEffectInstance cur = player.getEffect(effect);
		if (cur != null && (cur.isInfiniteDuration() || cur.getAmplifier() > amplifier)) {
			return; // somebody else's stronger / permanent one
		}
		if (cur == null || cur.getAmplifier() < amplifier || cur.getDuration() <= 40) {
			player.addEffect(new MobEffectInstance(effect, 100, amplifier, false, false, false));
		}
	}

	static void clearOurEffect(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int amplifier) {
		MobEffectInstance cur = player.getEffect(effect);
		if (cur != null && !cur.isInfiniteDuration() && cur.getAmplifier() == amplifier && cur.getDuration() <= 100) {
			player.removeEffect(effect);
		}
	}

	/** The fall-damage reduction of the current form: v0.12.35 -- the Power Form takes no fall damage at all (Base Form: none). */
	public static float fallReduction(ServerPlayer player) {
		return isFullPower(player) ? 1.0f : 0.0f;
	}

	/** The damage-taken factor (1 - reduction) of the current form: 1.0 in the Base Form, 0.5 in the Power Form. */
	public static float damageTakenFactor(ServerPlayer player) {
		return 1.0f - (isFullPower(player) ? AllMightConfig.FULL_DAMAGE_REDUCTION : 0.0f);
	}

	// ---------------------------------------------------------------- H: the form

	/** The H key: toggle between the Base Form and the Power Form. Server-validated; safe to spam. */
	public static void toggleForm(ServerPlayer player) {
		AllMightState s = state(player);
		if (!s.hasPower || !player.isAlive() || player.isSpectator() || com.projecthero.mod.hulk.Hulk.isHulk(player)) {
			return; // v0.13.12: not while he is the Hulk (the two growths would stack)
		}
		long now = player.level().getGameTime();
		if (now - s.formChangedAt < AllMightConfig.FORM_TOGGLE_DEBOUNCE_TICKS || now < s.transformUntil) {
			return;
		}
		float need = AllMightConfig.timer().minimumToTransformSeconds;
		if (!s.fullPower && timeLeft(s) + 1.0e-3f < need) {
			// v0.16.7: the timer ran out -- the Power Form waits until the Base Form has refilled a little
			say(player, "message.projecthero.all_might.low_ofa", ChatFormatting.RED, clock(need), clock(timeLeft(s)));
			return;
		}
		changeForm(player, !s.fullPower);
	}

	/** Switches form: the health percentage carries over, the body grows / shrinks over a second and steam pours off. */
	static void changeForm(ServerPlayer player, boolean toFull) {
		changeForm(player, toFull, true);
	}

	static void changeForm(ServerPlayer player, boolean toFull, boolean fx) {
		AllMightState s = state(player);
		long now = player.level().getGameTime();
		float ratio = player.getMaxHealth() <= 0f ? 1f : player.getHealth() / player.getMaxHealth();
		AllMightAbilities.cancelCharge(player, true);
		AllMightState n = s.copy();
		n.fullPower = toFull;
		n.formChangedAt = now;
		int lock = toFull ? AllMightConfig.TRANSFORM_TICKS : AllMightConfig.DETRANSFORM_TICKS;
		n.busyUntil = now + lock;
		n.transformUntil = now + lock; // damage-proof while the body changes size
		n.animId = toFull ? AllMightState.ANIM_TRANSFORM_UP : AllMightState.ANIM_TRANSFORM_DOWN;
		n.animStart = now;
		if (!toFull && s.plusUltra) {
			n.plusUltra = false;
			n.plusUltraUntil = 0L;
			n.abilityReadyAt.put(AllMightAbilities.PLUS_ULTRA, now + AllMightConfig.PLUS_ULTRA_COOLDOWN_TICKS);
		}
		save(player, n);
		reconcile(player);
		if (toFull) {
			AllMightSuit.tearOffRegularArmour(player); // v0.12.43: regular armour tears off (-50 durability) and drops
			AllMightSuit.equip(player); // whatever is in the costume locker (N) goes on
		} else {
			AllMightSuit.strip(player);
		}
		// 20 HP at 100% is 40 HP at 100%; 10 of 20 becomes 20 of 40 (and back)
		player.setHealth(Math.max(0.5f, Math.min(player.getMaxHealth(), ratio * player.getMaxHealth())));
		if (fx) {
			transformFx(player, toFull);
		}
	}

	/** Steam venting off the body: a puff of cloud and smoke at a few heights, scaled to how tall he currently is. */
	static void steam(ServerLevel level, ServerPlayer player, int count) {
		double h = Math.max(1.8, player.getBbHeight());
		for (int i = 0; i < 3; i++) {
			double y = player.getY() + h * (0.25 + 0.3 * i);
			level.sendParticles(ParticleTypes.CLOUD, player.getX(), y, player.getZ(), AllMightShockwave.particles(count), 0.35, 0.15, 0.35, 0.03);
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, player.getX(), y, player.getZ(), AllMightShockwave.particles(Math.max(1, count / 2)),
					0.3, 0.15, 0.3, 0.02);
		}
	}

	/** v0.16.6: steam venting off both arms (shoulder to fist) during Plus Ultra. */
	static void armSteam(ServerLevel level, ServerPlayer player, int count) {
		double h = Math.max(1.8, player.getBbHeight());
		Vec3 fwd = Vec3.directionFromRotation(0, player.yBodyRot);
		Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
		double half = 0.37 * h / 1.8;
		for (int sgn = -1; sgn <= 1; sgn += 2) {
			Vec3 shoulder = player.position().add(side.scale(sgn * half)).add(0, h * 0.72, 0);
			Vec3 hand = shoulder.add(0, -h * 0.32, 0).add(fwd.scale(0.1));
			Vec3 at = shoulder.lerp(hand, level.random.nextDouble());
			level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, AllMightShockwave.particles(count), 0.08, 0.1, 0.08, 0.025);
			level.sendParticles(ParticleTypes.WHITE_SMOKE, at.x, at.y, at.z, AllMightShockwave.particles(count), 0.06, 0.1, 0.06, 0.02);
		}
	}

	private static void transformFx(ServerPlayer player, boolean toFull) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		if (toFull) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.4f, 0.6f);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.1f, 1.3f);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.7f, 1.4f);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0f, 0.8f);
			AllMightShockwave.burst(level, ParticleTypes.EXPLOSION, c, 3, 0.5, 0.0);
			AllMightShockwave.burst(level, ParticleTypes.ELECTRIC_SPARK, c, 70, 0.7, 0.4);
			AllMightShockwave.burst(level, new DustParticleOptions(new Vector3f(1.0f, 0.78f, 0.2f), 1.5f), c, 40, 0.8, 0.05);
			steam(level, player, 10);
			AllMightShockwave.ring(level, ParticleTypes.CLOUD, player.position().add(0, 0.2, 0), 2.5, 24);
			AllMightShockwave.ring(level, ParticleTypes.CLOUD, player.position().add(0, 0.2, 0), 4.5, 24);
			AllMightShockwave.shake(level, player.position(), 0.5f, 20);
			// a brief gust that shoves bystanders clear (no damage)
			for (var e : AbilityHelpers.living(level, player.position(), 6.0, x -> x != player)) {
				if (!AllMightShockwave.isBoss(e)) {
					AbilityHelpers.knockbackFrom(e, player.position(), 1.0);
				}
			}
			player.displayClientMessage(Component.translatable("message.projecthero.all_might.full_power")
					.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), true);
		} else {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.9f, 1.2f);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.2f, 0.9f);
			steam(level, player, 10);
			AllMightShockwave.burst(level, ParticleTypes.CLOUD, c, 20, 0.6, 0.05);
			AllMightShockwave.burst(level, ParticleTypes.ELECTRIC_SPARK, c, 15, 0.5, 0.15);
		}
	}

	// ---------------------------------------------------------------- tick

	/** Runs for every All Might every server tick. */
	public static void tick(ServerPlayer player) {
		AllMightState s = state(player);
		if (!s.hasPower) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();

		tickScale(player, s);
		if (s.fullPower && player.tickCount % 5 == 0) {
			AllMightSuit.bounceRegularArmour(player); // v0.12.43: the Power Form only wears the All Might armour
		}
		// the once-a-second safety net (a respawn or another mod may have cleared the transient modifiers)
		if (player.tickCount % 20 == 0) {
			reconcile(player);
		}
		if (s.fullPower && s.plusUltra && now >= s.plusUltraUntil) {
			endPlusUltra(player, s, now);
		}
		if (tickTimer(player, now)) {
			return; // v0.16.7: the One For All timer ran out -- back to the Base Form
		}
		s = state(player);
		tickPowerFormRegen(player, s);
		AllMightAbilities.tick(player);
		tickLanding(player, s, now);
		tickAura(player, s, now);
	}

	/** Plus Ultra is a fixed 22 s; when it runs out the Smashes are back to their normal damage and the cooldown starts. */
	private static void endPlusUltra(ServerPlayer player, AllMightState s, long now) {
		AllMightState n = s.copy();
		n.plusUltra = false;
		n.plusUltraUntil = 0L;
		n.abilityReadyAt.put(AllMightAbilities.PLUS_ULTRA, now + AllMightConfig.PLUS_ULTRA_COOLDOWN_TICKS);
		save(player, n);
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8f, 1.6f);
		steam(level, player, 6);
		player.displayClientMessage(Component.translatable("message.projecthero.all_might.plus_ultra_off").withStyle(ChatFormatting.GRAY), true);
	}

	/** v0.12.39: the Power Form's slow self-healing: 1 HP every 4 s (below Regeneration I). */
	private static void tickPowerFormRegen(ServerPlayer player, AllMightState s) {
		if (s.fullPower && player.tickCount % AllMightConfig.FULL_REGEN_INTERVAL_TICKS == 0 && player.getHealth() < player.getMaxHealth()) {
			player.heal(AllMightConfig.FULL_REGEN_AMOUNT);
		}
	}

	/** Eases the body toward its target size (2.7 blocks in the Power Form, 1.8 in the Base Form) one twentieth of the difference a tick. */
	private static void tickScale(ServerPlayer player, AllMightState s) {
		net.minecraft.world.entity.ai.attributes.AttributeInstance inst = player.getAttribute(Attributes.SCALE);
		if (inst == null) {
			return;
		}
		AttributeModifier m = inst.getModifier(SCALE_ID);
		double cur = m == null ? 0.0 : m.amount();
		double target = s.fullPower ? AllMightConfig.FULL_SCALE_BONUS : 0.0;
		if (Math.abs(cur - target) < 1.0e-4) {
			if (!s.fullPower && m != null) {
				PowerToggles.clearModifier(player, Attributes.SCALE, SCALE_ID);
			}
			return;
		}
		double step = AllMightConfig.FULL_SCALE_BONUS / Math.max(1, AllMightConfig.GROWTH_TICKS);
		double next = cur < target ? Math.min(target, cur + step) : Math.max(target, cur - step);
		next = Math.round(next * 1000.0) / 1000.0;
		if (next > cur) {
			// growth waits (and retries) if the bigger body would not fit where he stands
			double w = 0.6 * (1.0 + next);
			double h = 1.8 * (1.0 + next);
			var box = new net.minecraft.world.phys.AABB(player.getX() - w / 2, player.getY(), player.getZ() - w / 2,
					player.getX() + w / 2, player.getY() + h, player.getZ() + w / 2);
			if (!player.level().noCollision(player, box)) {
				return;
			}
		}
		if (next <= 1.0e-4) {
			PowerToggles.clearModifier(player, Attributes.SCALE, SCALE_ID);
		} else {
			PowerToggles.modifier(player, Attributes.SCALE, SCALE_ID, next, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		}
	}

	/** Hard landings from a real fall release a shockwave sized by how far he fell. */
	private static void tickLanding(ServerPlayer player, AllMightState s, long now) {
		if (!s.fullPower) {
			PEAK_FALL.remove(player.getUUID());
			return;
		}
		if (player.onGround() || player.isInWater() || player.isPassenger() || player.getAbilities().flying) {
			Float peak = PEAK_FALL.remove(player.getUUID());
			if (peak != null && peak >= AllMightConfig.LANDING_SMALL_MIN_FALL && !AllMightAbilities.suppressLanding(player)) {
				landingImpact(player, peak);
			}
			if (player.onGround() && s.noFallUntil > now && now > s.noFallUntil - AllMightConfig.LAUNCH_NO_FALL_TICKS + 10) {
				AllMightState n = state(player).copy();
				n.noFallUntil = 0L; // the launch is over
				save(player, n);
			}
			return;
		}
		if (player.fallDistance > 1.0f) {
			PEAK_FALL.merge(player.getUUID(), player.fallDistance, Math::max);
		}
	}

	private static void landingImpact(ServerPlayer player, float fall) {
		float dmg;
		double radius;
		double kb;
		if (fall >= AllMightConfig.LANDING_HEAVY_MIN_FALL) {
			dmg = AllMightConfig.LANDING_HEAVY_DAMAGE;
			radius = AllMightConfig.LANDING_HEAVY_RADIUS;
			kb = 1.6;
		} else if (fall >= AllMightConfig.LANDING_MEDIUM_MIN_FALL) {
			dmg = AllMightConfig.LANDING_MEDIUM_DAMAGE;
			radius = AllMightConfig.LANDING_MEDIUM_RADIUS;
			kb = 1.1;
		} else {
			dmg = AllMightConfig.LANDING_SMALL_DAMAGE;
			radius = AllMightConfig.LANDING_SMALL_RADIUS;
			kb = 0.7;
		}
		ServerLevel level = (ServerLevel) player.level();
		Vec3 at = player.position();
		AllMightShockwave.Wave wave = new AllMightShockwave.Wave(dmg, kb, 0.35);
		AllMightShockwave.radial(player, at, radius, wave, true);
		AllMightShockwave.ring(level, ParticleTypes.CLOUD, at.add(0, 0.15, 0), radius * 0.6, 20);
		AllMightShockwave.burst(level, new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
				level.getBlockState(net.minecraft.core.BlockPos.containing(at).below())), at.add(0, 0.2, 0), (int) (10 + radius * 3), radius * 0.4, 0.1);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, (float) Math.min(1.4, 0.4 + radius * 0.12), 1.3f);
		if (fall >= AllMightConfig.LANDING_MEDIUM_MIN_FALL) {
			AllMightShockwave.shake(level, at, (float) Math.min(0.8, radius * 0.1), 12);
		}
	}

	private static void tickAura(ServerPlayer player, AllMightState s, long now) {
		ServerLevel level = (ServerLevel) player.level();
		// steam pours off him while the body grows or shrinks (the transformation second)
		if (now - s.formChangedAt < AllMightConfig.GROWTH_TICKS && now % 2L == 0L) {
			steam(level, player, 3);
		}
		if (!s.fullPower) {
			return;
		}
		// Plus Ultra -- the arms vent steam and gold sparks crackle off his body (server particles; no arm glow)
		if (s.plusUltra && now % 3L == 0L) {
			armSteam(level, player, 1);
			Vec3 c = player.position().add(0, player.getBbHeight() * 0.6, 0);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, AllMightShockwave.particles(2), 0.45, player.getBbHeight() * 0.3, 0.45, 0.08);
			if (now % 6L == 0L) {
				level.sendParticles(new DustParticleOptions(new Vector3f(1.0f, 0.82f, 0.3f), 1.0f), c.x, c.y, c.z,
						AllMightShockwave.particles(3), 0.45, player.getBbHeight() * 0.35, 0.45, 0.0);
			}
		}
		// v0.16.7 (as in v0.12.39): running out of One For All -- he vents steam to show it
		if (timeLeft(s) < AllMightConfig.timer().lowSteamSeconds && now % 3L == 0L) {
			steam(level, player, 2);
		}
		if (now % 6L == 0L && player.getDeltaMovement().horizontalDistanceSqr() > 0.06) {
			Vec3 f = player.position().add(0, 0.6, 0);
			level.sendParticles(ParticleTypes.CLOUD, f.x, f.y, f.z, AllMightShockwave.particles(1), 0.2, 0.3, 0.2, 0.01);
		}
	}

	// ---------------------------------------------------------------- lifecycle

	public static void onPlayerJoin(ServerPlayer player) {
		AllMightState s = state(player);
		if (!s.hasPower) {
			return;
		}
		long now = player.level().getGameTime();
		AllMightState n = s.copy();
		// game time is per-world: nothing carried over from another world may lock the player out
		n.busyUntil = 0L;
		n.transformUntil = 0L;
		n.noFallUntil = 0L;
		n.chargeStart = 0L;
		n.animId = AllMightState.ANIM_NONE;
		n.abilityReadyAt.entrySet().removeIf(e -> e.getValue() > now + 20L * 120L);
		// a save may still have Plus Ultra running (the old Output / limit / limb keys are ignored by the codec)
		n.plusUltra = false;
		n.plusUltraUntil = 0L;
		save(player, n);
		reconcile(player);
	}

	public static void onPlayerRespawn(ServerPlayer player) {
		AllMightState s = state(player);
		if (!s.hasPower) {
			return;
		}
		AllMightState n = s.copy();
		n.plusUltra = false;
		n.plusUltraUntil = 0L;
		n.busyUntil = 0L;
		n.transformUntil = 0L;
		n.noFallUntil = 0L;
		n.animId = AllMightState.ANIM_NONE;
		setTimeLeft(n, timerMax()); // v0.16.7 (as in v0.12.39): a respawn refills the One For All timer
		n.abilityReadyAt.clear();
		save(player, n);
		reconcile(player);
		if (n.fullPower) {
			player.setHealth(player.getMaxHealth()); // a respawn starts at full health, whichever form
			AllMightSuit.equip(player);
		}
	}

	/** Death / logout / dimension change: drop everything transient (the power and the chosen form stay). */
	public static void clearTransient(ServerPlayer player) {
		AllMightAbilities.clear(player.getUUID());
		PEAK_FALL.remove(player.getUUID());
		LAST_MESSAGE.remove(player.getUUID());
		LAST_TIMER_STEP.remove(player.getUUID());
		AllMightState s = player.getAttachedOrElse(ModAttachments.ALL_MIGHT_STATE, null);
		if (s != null && s.hasPower) {
			AllMightState n = s.copy();
			n.busyUntil = 0L;
			n.chargeStart = 0L;
			n.animId = AllMightState.ANIM_NONE;
			save(player, n);
		}
	}

	/** Called when the player dies: drop everything transient (the power and the chosen form stay). The costume is real gear and drops like any armour. */
	public static void onDeath(ServerPlayer player) {
		clearTransient(player);
	}
}
