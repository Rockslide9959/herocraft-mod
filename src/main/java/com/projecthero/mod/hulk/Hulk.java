package com.projecthero.mod.hulk;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hulk.data.HulkState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * The single server-side API for the Hulk Hero-Tier power. Nothing else pokes {@link HulkState}; every mutator re-saves
 * through {@link ServerPlayer#setAttached}. All numbers live in {@link HulkConfig}.
 *
 * <h2>v0.16.4 rules (user, docs/HULK_V0164_REVAMP_CONTRACT.md)</h2>
 * <ul>
 *   <li><b>The change</b> happens two ways only: Banner dies (every time, no cooldown -- the death is cancelled and the
 *       Hulk comes out), or the player HOLDS H for {@link HulkConfig#HOLD_TO_CHANGE_TICKS}. Nothing else -- no rage
 *       trigger, no tap at 50. Every change picks one of the six transformation animations at random
 *       ({@link HulkState.Combat#transformStyle}); the quick one keeps its shorter clock, the other five share the slow one.</li>
 *   <li><b>Rage</b> exists only as the Hulk (Banner has none): +1 per point of damage taken, +{@link HulkConfig#HULK_RAGE_PER_HIT}
 *       per hit landed, burning off slowly out of combat. 0 rage never changes him back. It sets his size (3.5 blocks at 0 to 6
 *       at 100, through {@link Attributes#SCALE}) and his damage (+0% to +50%, {@link HulkDamage}).</li>
 *   <li><b>Changing back</b> (tap H) is locked for {@link HulkConfig#DETRANSFORM_LOCK_TICKS} after he comes out; after a
 *       willing (hold-H) Hulk changes back, hold H is on a {@link HulkConfig#WILLING_COOLDOWN_TICKS} cooldown -- dying still
 *       forces the change.</li>
 *   <li><b>Dying as the Hulk</b> is cancelled too: he falls flat on his back, slowly changes back into the regular player, and
 *       dies {@link HulkConfig#DEATH_FINAL_DELAY_TICKS} later -- a real death, which the base-form change never catches.</li>
 *   <li><b>The rampage</b> (every minute, any Hulk) lives in {@link HulkControl}; the N roar in {@link HulkRoar}.</li>
 * </ul>
 * Calming down is gone.
 *
 * <h2>The body</h2>
 * {@link #reconcile} sets every stat as a fixed-id transient attribute modifier from {@code (hasPower, hulk)} -- idempotent,
 * so it runs every second as a safety net. The size is eased by {@link #tickScale}, which waits if the bigger body would not
 * fit; reach and step height follow the size.
 */
public final class Hulk {
	public static final String KEY = "hulk";

	private static final ResourceLocation SCALE_ID = PowerToggles.id("hulk_scale");
	private static final ResourceLocation ATTACK_ID = PowerToggles.id("hulk_attack");
	private static final ResourceLocation HEALTH_ID = PowerToggles.id("hulk_health");
	private static final ResourceLocation KNOCKBACK_ID = PowerToggles.id("hulk_knockback");
	private static final ResourceLocation TOUGHNESS_ID = PowerToggles.id("hulk_toughness");
	private static final ResourceLocation STEP_ID = PowerToggles.id("hulk_step");
	private static final ResourceLocation REACH_ID = PowerToggles.id("hulk_reach");
	private static final ResourceLocation BLOCK_REACH_ID = PowerToggles.id("hulk_block_reach");
	private static final ResourceLocation SPEED_ID = PowerToggles.id("hulk_speed");
	private static final ResourceLocation ATTACK_KNOCKBACK_ID = PowerToggles.id("hulk_attack_knockback");
	private static final ResourceLocation ARMOR_ID = PowerToggles.id("hulk_armor");
	/** v0.13.15: the change pins him to the spot (movement and jump multiplied to nothing). v0.16.4: so does a rampage and dying. */
	private static final ResourceLocation CHANGE_LOCK_ID = PowerToggles.id("hulk_change_lock");
	private static final ResourceLocation CHANGE_JUMP_LOCK_ID = PowerToggles.id("hulk_change_jump_lock");
	/** v0.15.18: the rage-tier bonuses. v0.16.4: only the speed one is left (damage scales with rage in HulkDamage). */
	private static final ResourceLocation TIER_ATTACK_ID = PowerToggles.id("hulk_tier_attack");
	private static final ResourceLocation TIER_SPEED_ID = PowerToggles.id("hulk_tier_speed");

	private static final DustParticleOptions GAMMA_GREEN = new DustParticleOptions(new Vector3f(0.3f, 0.95f, 0.2f), 1.6f);
	private static final DustParticleOptions DEEP_GREEN = new DustParticleOptions(new Vector3f(0.12f, 0.55f, 0.1f), 1.2f);

	/** Per-player throttle for action-bar feedback. */
	private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
	/** v0.16.4: the scale bonus he had when the current change back began (the shrink eases from it). */
	private static final Map<UUID, Double> SHRINK_FROM = new ConcurrentHashMap<>();
	/** v0.16.4: what dealt the Hulk's killing blow -- the final death is reported with it. */
	private static final Map<UUID, DamageSource> DEATH_SOURCE = new ConcurrentHashMap<>();
	/** v0.16.4: players whose real death is in progress (the base-form change must let it through). */
	private static final Set<UUID> FINAL_DEATH = ConcurrentHashMap.newKeySet();

	private Hulk() {
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
		SHRINK_FROM.clear();
		DEATH_SOURCE.clear();
		FINAL_DEATH.clear();
		HulkAbilities.clearSessionState();
		HulkGrab.clearSessionState();
		com.projecthero.mod.hulk.gladiator.GladiatorAbilities.clearSessionState(); // v0.15.3
		HulkControl.clearSessionState();
		HulkRoar.clearSessionState(); // v0.16.4: the scared mobs
		GammaOverload.clearSessionState();
		HulkMind.clearSessionState(); // v0.16.4: live mind sessions
	}

	// ---------------------------------------------------------------- state

	public static HulkState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.HULK_STATE);
	}

	public static void save(ServerPlayer player, HulkState state) {
		player.setAttached(ModAttachments.HULK_STATE, state);
	}

	/** Has the Gamma power. Safe on the client (the attachment is synced). */
	public static boolean hasPower(Player player) {
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		return s != null && s.hasPower;
	}

	/** Is the Hulk right now. Safe on the client. */
	public static boolean isHulk(Player player) {
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		return s != null && s.hasPower && s.hulk;
	}

	public static float rage(Player player) {
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		return s == null ? 0.0f : s.rage;
	}

	/**
	 * Mid-change (growing into the Hulk) or in the Hulk-form death sequence -- he cannot move, act or be hurt until it is over.
	 * Client-safe.
	 */
	public static boolean changing(Player player) {
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		return s != null && changing(s, player.level().getGameTime());
	}

	public static boolean changing(HulkState s, long now) {
		return s.hasPower && (s.dying() || (s.hulk && now - s.formChangedAt < changeTicks(s)));
	}

	/** v0.16.4: in the Hulk-form death sequence. Client-safe. */
	public static boolean dying(Player player) {
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		return s != null && s.hasPower && s.dying();
	}

	/**
	 * Ticks the change into the Hulk takes for this state. v0.16.4: every change is the slow one
	 * ({@link HulkConfig#FORCED_CHANGE_TICKS}) except the quick style ({@link HulkState#TRANSFORM_QUICK}).
	 */
	public static int changeTicks(HulkState s) {
		return s.combat.transformStyle == HulkState.TRANSFORM_QUICK ? HulkConfig.GROWTH_TICKS : HulkConfig.FORCED_CHANGE_TICKS;
	}

	/** v0.16.4: 0..1 of the growth part of the change (what the size and the cross-fade follow). */
	private static float growth(HulkState s, float t) {
		if (s.combat.transformStyle == HulkState.TRANSFORM_QUICK) {
			return t / HulkConfig.GROWTH_TICKS;
		}
		return (t - HulkConfig.FORCED_KNEEL_TICKS) / HulkConfig.FORCED_GROWTH_TICKS;
	}

	/**
	 * v0.13.15: how much of the Hulk shows, 0 (Banner) .. 1 (the Hulk), with {@code partialTick} for a smooth fade. The
	 * renderer cross-fades the two bodies with it. It runs on the same clock as the growth in {@link #tickScale}. Client-safe.
	 */
	public static float visibility(Player player, float partialTick) {
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		if (s == null || !s.hasPower) {
			return 0.0f;
		}
		float t = (player.level().getGameTime() - s.formChangedAt) + partialTick;
		if (s.hulk) {
			return s.dying() ? 1.0f : smooth(growth(s, t));
		}
		// v0.16.4: the death sequence's change back is the slow one
		return 1.0f - smooth(t / (s.dying() ? HulkConfig.DEATH_REVERT_TICKS : HulkConfig.GROWTH_TICKS));
	}

	private static float smooth(float f) {
		f = Math.max(0.0f, Math.min(1.0f, f));
		return f * f * (3.0f - 2.0f * f);
	}

	/** v0.16.4: exhaustion is gone with the new rules -- kept for old callers (always false unless a save still has it). */
	public static boolean exhausted(Player player) {
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		return s != null && s.exhaustedUntil > player.level().getGameTime();
	}

	/** v0.15.18: the Hulk's rage tier ({@link HulkConfig#TIER_CALM} ...); always Calm for Banner. Client-safe. */
	public static int rageTier(Player player) {
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		return s == null || !s.hasPower || !s.hulk ? HulkConfig.TIER_CALM : HulkConfig.rageTier(s.rage);
	}

	/** v0.16.4: 0..1 while Banner is holding H toward the change (0 when not). Client-safe. */
	public static float holdProgress(Player player) {
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		if (s == null || !s.hasPower || s.hulk || s.combat.strainStart == 0L) {
			return 0.0f;
		}
		long t = player.level().getGameTime() - s.combat.strainStart;
		return Math.max(0.0f, Math.min(1.0f, t / (float) HulkConfig.HOLD_TO_CHANGE_TICKS));
	}

	/** v0.16.4: ticks left on the hold-H cooldown after a willing Hulk changed back (0 = ready). Client-safe. */
	public static int holdCooldown(Player player) {
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		return s == null ? 0 : (int) Math.max(0L, s.rules.holdCooldownUntil - player.level().getGameTime());
	}

	/** v0.16.4: ticks before the Hulk may change back (the 3-minute lock; 0 = he may). Client-safe. */
	public static int revertLock(Player player) {
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		return s == null || !s.hulk ? 0 : (int) Math.max(0L, s.formChangedAt + HulkConfig.DETRANSFORM_LOCK_TICKS - player.level().getGameTime());
	}

	/** v0.16.4: what this player's hits are multiplied by -- the Hulk's rage bonus (+0..+50%), 1 for anyone else. Client-safe. */
	public static float damageMultiplier(Player player) {
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		// v0.16.4 (user): World Breaker doubles it on top ("his damage and destruction and aoe should be doubled")
		return s == null || !s.hasPower || !s.hulk ? 1.0f : HulkConfig.damageMultiplier(s.rage) * WorldBreaker.damage(player);
	}

	public static void say(ServerPlayer player, String key, ChatFormatting colour, Object... args) {
		long now = player.level().getGameTime();
		Long last = LAST_MESSAGE.get(player.getUUID());
		if (last != null && now - last < 10) {
			return;
		}
		LAST_MESSAGE.put(player.getUUID(), now);
		player.displayClientMessage(Component.translatable(key, args).withStyle(colour), true);
	}

	private static String clock(int ticks) {
		int secs = (ticks + 19) / 20;
		return secs >= 60 ? String.format(java.util.Locale.ROOT, "%d:%02d", secs / 60, secs % 60) : secs + "s";
	}

	// ---------------------------------------------------------------- grant / revoke

	/** Gives the Gamma power (command now; the Gamma origin). False if the player already has it. */
	public static boolean grant(ServerPlayer player) {
		if (state(player).hasPower) {
			return false;
		}
		// v0.16.1: projectheroUniquePowers -- refused (nothing granted) while someone else holds it
		if (com.projecthero.mod.hero.UniquePowers.refuse(player, KEY)) {
			return false;
		}
		HeroTiers.claimPrimary(player, KEY);
		HulkState s = new HulkState();
		s.hasPower = true;
		// far enough back that H works at once and nothing is still fading
		s.formChangedAt = player.level().getGameTime() - Math.max(HulkConfig.TOGGLE_DEBOUNCE_TICKS, HulkConfig.GROWTH_TICKS);
		save(player, s);
		reconcile(player);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		level.sendParticles(GAMMA_GREEN, c.x, c.y, c.z, 40, 0.5, 0.9, 0.5, 0.02);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 0.6f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.5f, 0.7f);
		player.displayClientMessage(Component.translatable("message.projecthero.hulk.acquired")
				.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.hulk.acquired_hint_v0164")
				.withStyle(ChatFormatting.DARK_GREEN), false);
		return true;
	}

	/** Takes the Gamma power away; a Hulk shrinks straight back. */
	public static void revoke(ServerPlayer player) {
		HulkAbilities.clear(player.getUUID());
		com.projecthero.mod.hulk.gladiator.GladiatorAbilities.clear(player); // v0.15.3: the thrown weapons come home
		HulkGrab.release(player);
		HulkControl.clear(player.getUUID());
		HulkRoar.clear(player.getUUID());
		ejectRider(player);
		save(player, new HulkState());
		reconcile(player);
		PowerToggles.clearModifier(player, Attributes.SCALE, SCALE_ID);
		clearChangeLock(player);
		forget(player.getUUID());
	}

	private static void forget(UUID id) {
		LAST_MESSAGE.remove(id);
		SHRINK_FROM.remove(id);
		DEATH_SOURCE.remove(id);
	}

	// ---------------------------------------------------------------- rage

	/** Sets rage outright (the {@code /hulk setrage} test command). v0.16.4: Banner has no rage -- it only sticks as the Hulk. */
	public static void setRage(ServerPlayer player, float value) {
		HulkState n = state(player).copy();
		n.rage = n.hulk ? clampRage(value) : 0.0f;
		save(player, n);
		applyRageTier(player, n); // v0.15.18: a Hulk's tier follows at once
		if (n.hasPower && n.hulk) {
			applyRageHealth(player, n); // v0.16.4 (user): and his health bars
		}
	}

	private static float clampRage(float v) {
		return Math.max(0.0f, Math.min(HulkConfig.RAGE_MAX, v));
	}

	/** This player with the Gamma power was just hurt for {@code amount}. v0.16.4: only the Hulk gets angry. */
	public static void onHurt(ServerPlayer player, float amount) {
		HulkState s = state(player);
		if (!s.hasPower || player.isSpectator()) {
			return;
		}
		gain(player, s, s.hulk ? amount * HulkConfig.HULK_RAGE_PER_DAMAGE_TAKEN : 0.0f, true);
	}

	/**
	 * This player with the Gamma power just hit something for {@code amount}. The Hulk gains {@link HulkConfig#HULK_RAGE_PER_HIT}
	 * per hit (a punch, or each thing an ability hits); Banner gains nothing.
	 */
	public static void onDealt(ServerPlayer player, float amount) {
		HulkState s = state(player);
		if (!s.hasPower || player.isSpectator()) {
			return;
		}
		gain(player, s, s.hulk ? HulkConfig.HULK_RAGE_PER_HIT : 0.0f, false);
	}

	/** {@code hurt}: he took the hit. */
	private static void gain(ServerPlayer player, HulkState s, float amount, boolean hurt) {
		long now = player.level().getGameTime();
		HulkState n = s.copy();
		n.lastCombatAt = now;
		if (hurt) {
			n.combat.lastHurtAt = now;
		}
		if (amount > 0.0f && s.hulk && !s.burnout(now)) { // v0.16.4 (user): World Breaker's Burnout freezes rage at 0
			n.rage = clampRage(s.rage + amount);
		}
		save(player, n);
	}

	// ---------------------------------------------------------------- the change

	/**
	 * H went down (the packet). As the Hulk it asks to change back ({@link #tryRevert}); as Banner it starts the hold -- keep
	 * holding H for {@link HulkConfig#HOLD_TO_CHANGE_TICKS} ({@link #releaseH} calls it off). Server-validated; safe to spam.
	 */
	public static void pressH(ServerPlayer player) {
		HulkState s = state(player);
		if (!s.hasPower || !player.isAlive() || player.isSpectator() || s.dying()) {
			return;
		}
		if (s.hulk) {
			// v0.16.4 (user): at 100 rage H unleashes World Breaker instead (and does nothing while it runs)
			if (WorldBreaker.pressH(player)) {
				return;
			}
			tryRevert(player);
			return;
		}
		startHold(player);
	}

	/** H let go -- an unfinished hold is called off. */
	public static void releaseH(ServerPlayer player) {
		HulkState s = state(player);
		if (s.combat.strainStart == 0L) {
			return;
		}
		HulkState n = s.copy();
		n.combat.strainStart = 0L;
		save(player, n);
		if (!s.hulk) {
			say(player, "message.projecthero.hulk.hold_cancelled", ChatFormatting.GRAY);
		}
	}

	/** v0.16.4: why hold-H can't start right now (a lang key), or null if it can. */
	private static String holdRefusal(ServerPlayer player, HulkState s, long now) {
		if (com.projecthero.mod.titanshifter.TitanShifter.phase(player).insideForm()
				|| com.projecthero.mod.allmight.AllMight.isFullPower(player)) {
			return "message.projecthero.hulk.cannot_now";
		}
		if (s.rules.holdCooldownUntil > now) {
			return "message.projecthero.hulk.hold_refused"; // v0.16.4 (user): "the hulk doesnt want to answer your call" -- no timer
		}
		return null;
	}

	/** v0.16.4: Banner starts holding H toward the willing change. */
	private static void startHold(ServerPlayer player) {
		HulkState s = state(player);
		long now = player.level().getGameTime();
		if (s.hulk || s.combat.strainStart != 0L || now - s.formChangedAt < HulkConfig.TOGGLE_DEBOUNCE_TICKS) {
			return;
		}
		String refusal = holdRefusal(player, s, now);
		if (refusal != null) {
			say(player, refusal, ChatFormatting.GRAY, clock((int) (s.rules.holdCooldownUntil - now)));
			return;
		}
		HulkState n = s.copy();
		n.combat.strainStart = now;
		save(player, n);
		say(player, "message.projecthero.hulk.hold_start", ChatFormatting.DARK_GREEN);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 1.0f, 0.5f);
	}

	/** v0.16.4: every tick while Banner holds H -- heartbeat and gamma, and the change once the hold is long enough. */
	private static void tickHold(ServerLevel level, ServerPlayer player, HulkState s, long now) {
		long t = now - s.combat.strainStart;
		if (t < 0L || t > HulkConfig.HOLD_TO_CHANGE_TICKS + 40L) {
			releaseH(player); // a stale flag (another world's clock, a lost release)
			return;
		}
		float f = Math.min(1.0f, t / (float) HulkConfig.HOLD_TO_CHANGE_TICKS);
		double h = player.getBbHeight();
		if (now % 3L == 0L) {
			level.sendParticles(DEEP_GREEN, player.getX(), player.getY() + h * 0.5, player.getZ(), 1 + Math.round(f * 4.0f), 0.3, h * 0.25,
					0.3, 0.0);
		}
		long beat = Math.max(6L, 16L - Math.round(f * 10.0f));
		if (t % beat == 0L) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS,
					0.6f + f * 0.8f, 0.7f + f * 0.2f);
		}
		if (t >= HulkConfig.HOLD_TO_CHANGE_TICKS) {
			HulkState n = s.copy();
			n.combat.strainStart = 0L;
			save(player, n);
			transform(player, false);
		}
	}

	/**
	 * H as the Hulk -- change back. Not mid-change, not while rampaging, not in the first
	 * {@link HulkConfig#DETRANSFORM_LOCK_TICKS} (the player is told how long is left). A Hulk who came out by choice (hold H)
	 * puts hold-H on a {@link HulkConfig#WILLING_COOLDOWN_TICKS} cooldown as he changes back.
	 */
	public static void tryRevert(ServerPlayer player) {
		HulkState s = state(player);
		if (!s.hasPower || !s.hulk || !player.isAlive() || s.dying()) {
			return;
		}
		long now = player.level().getGameTime();
		if (now - s.formChangedAt < changeTicks(s)) {
			say(player, "message.projecthero.hulk.changing", ChatFormatting.GRAY);
			return;
		}
		if (s.rampaging(now)) {
			say(player, "message.projecthero.hulk.revert_rampage", ChatFormatting.RED);
			return;
		}
		long lock = s.formChangedAt + HulkConfig.DETRANSFORM_LOCK_TICKS - now;
		if (lock > 0L) {
			// v0.16.4 (user): "it shouldnt show the timer it should just say that the hulk wont let you go yet and it should have
			// a low growl"
			long last = LAST_MESSAGE.getOrDefault(player.getUUID(), Long.MIN_VALUE / 2);
			say(player, "message.projecthero.hulk.revert_held", ChatFormatting.RED);
			if (now - last >= 10L) {
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.POLAR_BEAR_WARNING, SoundSource.PLAYERS,
						0.45f, 0.5f);
			}
			return;
		}
		boolean willing = !s.combat.unwilling;
		revert(player, false);
		if (willing) {
			HulkState n = state(player).copy();
			n.rules.holdCooldownUntil = now + HulkConfig.WILLING_COOLDOWN_TICKS;
			save(player, n);
			// v0.16.4 (user): no timer for the 1-minute cooldown
			player.displayClientMessage(Component.translatable("message.projecthero.hulk.reverted_willing").withStyle(ChatFormatting.GREEN), true);
		} else {
			player.displayClientMessage(Component.translatable("message.projecthero.hulk.reverted_willing").withStyle(ChatFormatting.GREEN), true);
		}
	}

	/**
	 * The willing change with the change itself skipped -- he is the Hulk, ready to act, at once (tests and helpers; the game's
	 * own hold-H runs the full change through {@link #transform}). Refused on the hold-H cooldown, inside a Titan or in All
	 * Might's Power Form.
	 */
	public static void tryTransform(ServerPlayer player) {
		HulkState s = state(player);
		if (!s.hasPower || !player.isAlive() || player.isSpectator() || s.dying()) {
			return;
		}
		long now = player.level().getGameTime();
		if (s.hulk) {
			say(player, "message.projecthero.hulk.already", ChatFormatting.GREEN);
			return;
		}
		String refusal = holdRefusal(player, s, now);
		if (refusal != null) {
			say(player, refusal, ChatFormatting.GRAY, clock((int) (s.rules.holdCooldownUntil - now)));
			return;
		}
		transform(player, false);
		if (isHulk(player)) {
			HulkState n = state(player).copy();
			long done = changeTicks(n) + 1L;
			n.formChangedAt = now - done;
			n.rules.nextRampageAt = now + HulkConfig.control().rampageEveryTicks;
			save(player, n);
			clearChangeLock(player);
		}
	}

	/** Banner becomes the Hulk. {@code forced}: Banner died (the death change); otherwise the willing hold-H change. */
	public static void transform(ServerPlayer player, boolean forced) {
		transform(player, forced, true);
	}

	/**
	 * {@code burst}: the {@link HulkConfig#TRANSFORM_HEAL} on top. v0.16.4: every change picks one of the six transformation
	 * animations at random; the rampage clock starts the moment he is out.
	 */
	public static void transform(ServerPlayer player, boolean forced, boolean burst) {
		HulkState s = state(player);
		if (!s.hasPower || s.hulk) {
			return;
		}
		// v0.13.12 (Phase 5): not from inside a Titan, and not in All Might's Power Form (the growths would stack)
		if (com.projecthero.mod.titanshifter.TitanShifter.phase(player).insideForm()
				|| com.projecthero.mod.allmight.AllMight.isFullPower(player)) {
			say(player, "message.projecthero.hulk.cannot_now", ChatFormatting.GRAY);
			return;
		}
		float ratio = healthRatio(player);
		long now = player.level().getGameTime();
		HulkState n = s.copy();
		n.hulk = true;
		n.formChangedAt = now;
		n.rage = forced ? HulkConfig.DEATH_CHANGE_RAGE : 0.0f;
		n.lastCombatAt = now;
		n.exhaustedUntil = 0L;
		n.combat.control = 100.0f;
		n.combat.lastDealtAt = now;
		n.combat.promptKey = 0;
		n.combat.rampageUntil = 0L;
		n.combat.unwilling = forced;
		n.combat.strainStart = 0L;
		n.combat.calming = false;
		// v0.16.4 (user): "4 MORE transformation animations (6 total, random each change)"
		n.combat.transformStyle = player.getRandom().nextInt(HulkState.TRANSFORM_STAGGER + 1);
		n.rules.warnLen = 0;
		n.rules.warnPos = 0;
		n.rules.ringStreak = 0;
		n.rules.deathFallAt = 0L;
		n.rules.nextRampageAt = now + changeTicks(n) + HulkConfig.control().rampageEveryTicks;
		// v0.16.4 (user): changing resets Banner's 10-minute clock; nothing of an old rampage carries over
		HulkControl.clearRampage(n);
		n.wild.pending = 0;
		n.wild.warnBanner = false;
		n.wild.bannerTicks = 0;
		n.wild.bannerNextAt = 0;
		save(player, n);
		SHRINK_FROM.remove(player.getUUID());
		tearOffArmour(player); // v0.13.14: he bursts out of it
		reconcile(player);
		player.setHealth(Math.min(player.getMaxHealth(), ratio * player.getMaxHealth() + (burst ? HulkConfig.TRANSFORM_HEAL : 0.0f)));
		transformFx(player, n.combat.transformStyle == HulkState.TRANSFORM_QUICK, forced);
	}

	/**
	 * The Hulk shrinks back to Banner. {@code exhaust}: Weakness + Slowness follow (v0.16.4: nothing in the rules asks for it
	 * any more; kept for old callers).
	 */
	public static void revert(ServerPlayer player, boolean exhaust) {
		HulkState s = state(player);
		if (!s.hasPower || !s.hulk) {
			return;
		}
		long now = player.level().getGameTime();
		WorldBreaker.stop(player); // v0.16.4: a change back ends World Breaker at once (Burnout + its cooldown still count)
		HulkGrab.release(player);
		HulkAbilities.endCharge(player);
		com.projecthero.mod.hulk.gladiator.GladiatorAbilities.clear(player); // v0.15.3
		HulkControl.clear(player.getUUID());
		ejectRider(player);
		SHRINK_FROM.put(player.getUUID(), scaleBonus(player));
		float ratio = healthRatio(player);
		HulkState n = state(player).copy();
		n.hulk = false;
		n.combat.rampageUntil = 0L;
		n.combat.promptKey = 0;
		n.combat.control = 100.0f;
		n.combat.unwilling = false;
		n.rage = 0.0f;
		n.combat.lastHurtAt = 0L;
		n.formChangedAt = now;
		n.exhaustedUntil = exhaust ? now + HulkConfig.EXHAUSTED_TICKS : 0L;
		n.rules.nextRampageAt = 0L;
		n.rules.warnLen = 0;
		n.rules.warnPos = 0;
		n.rules.ringStreak = 0;
		// v0.16.4 (user): Banner's clock starts again from the change back
		HulkControl.clearRampage(n);
		n.wild.pending = 0;
		n.wild.warnBanner = false;
		n.wild.bannerTicks = 0;
		n.wild.bannerNextAt = 0;
		save(player, n);
		reconcile(player);
		player.setHealth(Math.max(1.0f, Math.min(player.getMaxHealth(), ratio * player.getMaxHealth())));
		if (exhaust) {
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, HulkConfig.EXHAUSTED_TICKS, 0, false, true, true));
			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, HulkConfig.EXHAUSTED_TICKS, 0, false, true, true));
		}
		revertFx(player);
	}

	private static float healthRatio(ServerPlayer player) {
		return player.getMaxHealth() <= 0.0f ? 1.0f : player.getHealth() / player.getMaxHealth();
	}

	// ---------------------------------------------------------------- stats

	/**
	 * Every stat in line with {@code (hasPower, hulk)}. Idempotent (fixed ids, write only on change). The size is not set
	 * here -- it eases in and out ({@link #tickScale}); a player without the power has it cleared outright.
	 */
	public static void reconcile(ServerPlayer player) {
		HulkState s = state(player);
		if (!s.hasPower || !s.hulk) {
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID);
			PowerToggles.clearModifier(player, Attributes.MAX_HEALTH, HEALTH_ID);
			PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID);
			PowerToggles.clearModifier(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID);
			PowerToggles.clearModifier(player, Attributes.STEP_HEIGHT, STEP_ID);
			PowerToggles.clearModifier(player, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID);
			PowerToggles.clearModifier(player, Attributes.BLOCK_INTERACTION_RANGE, BLOCK_REACH_ID);
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
			PowerToggles.clearModifier(player, Attributes.ATTACK_KNOCKBACK, ATTACK_KNOCKBACK_ID);
			PowerToggles.clearModifier(player, Attributes.ARMOR, ARMOR_ID);
			if (!s.dying()) {
				clearChangeLock(player);
			}
			if (!s.hasPower) {
				PowerToggles.clearModifier(player, Attributes.SCALE, SCALE_ID);
			}
			applyRageTier(player, s);
			if (player.getHealth() > player.getMaxHealth()) {
				player.setHealth(player.getMaxHealth());
			}
			return;
		}
		PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID, HulkConfig.ATTACK_BONUS, AttributeModifier.Operation.ADD_VALUE);
		applyRageHealth(player, s);
		PowerToggles.modifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, HulkConfig.KNOCKBACK_RESISTANCE,
				AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID, HulkConfig.ARMOR_TOUGHNESS_BONUS,
				AttributeModifier.Operation.ADD_VALUE);
		// v0.13.14: faster, harder-hitting, and diamond-level armour of his own
		PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, HulkConfig.SPEED_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		PowerToggles.modifier(player, Attributes.ATTACK_KNOCKBACK, ATTACK_KNOCKBACK_ID, HulkConfig.ATTACK_KNOCKBACK_BONUS,
				AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.ARMOR, ARMOR_ID, HulkConfig.ARMOR_BONUS, AttributeModifier.Operation.ADD_VALUE);
		applySizeStats(player);
		applyRageTier(player, s);
	}

	/**
	 * v0.16.4 (user): "at 0% rage he has 2 bars of hp and at 100% he gets 3 bars" -- 40 HP at 0 rage up to 60 at 100, in whole
	 * HP ({@link HulkConfig#healthBonusForRage}). When the max moves his current health keeps its share of it (never below 1,
	 * so a falling max never kills him). Written only on change.
	 */
	static void applyRageHealth(ServerPlayer player, HulkState s) {
		double bonus = HulkConfig.healthBonusForRage(s.rage);
		AttributeInstance inst = player.getAttribute(Attributes.MAX_HEALTH);
		AttributeModifier m = inst == null ? null : inst.getModifier(HEALTH_ID);
		if (m != null && m.amount() == bonus) {
			return;
		}
		float oldMax = player.getMaxHealth();
		float health = player.getHealth();
		PowerToggles.modifier(player, Attributes.MAX_HEALTH, HEALTH_ID, bonus, AttributeModifier.Operation.ADD_VALUE);
		if (m != null && oldMax > 0.0f && player.isAlive() && health > 0.0f) {
			float newMax = player.getMaxHealth();
			player.setHealth(Math.max(1.0f, Math.min(newMax, health / oldMax * newMax)));
		}
	}

	/**
	 * v0.16.4: reach and step height follow his size -- a 6-block Hulk's eyes are 5 blocks up, so a flat +1.5 reach could not
	 * touch a pig at his feet. Fixed ids, written only on change.
	 */
	private static void applySizeStats(ServerPlayer player) {
		double scale = 1.0 + scaleBonus(player);
		double reach = HulkConfig.REACH_BONUS + 1.8 * Math.max(0.0, scale - 1.0);
		double step = Math.max(HulkConfig.STEP_HEIGHT_BONUS, Math.min(1.4, 0.6 * (scale - 1.0)));
		reach = Math.round(reach * 20.0) / 20.0;
		step = Math.round(step * 20.0) / 20.0;
		PowerToggles.modifier(player, Attributes.STEP_HEIGHT, STEP_ID, step, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID, reach, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.BLOCK_INTERACTION_RANGE, BLOCK_REACH_ID, reach, AttributeModifier.Operation.ADD_VALUE);
	}

	/**
	 * v0.15.18: the rage-tier bonus in line with {@code s}. v0.16.4: only the speed part is left -- his damage scales
	 * smoothly with rage instead ({@link HulkDamage}, +0..+50%), so the old tier damage modifier is always cleared.
	 */
	static void applyRageTier(ServerPlayer player, HulkState s) {
		PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, TIER_ATTACK_ID);
		int tier = s.hasPower && s.hulk ? HulkConfig.rageTier(s.rage) : HulkConfig.TIER_CALM;
		if (tier == HulkConfig.TIER_CALM) {
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, TIER_SPEED_ID);
			return;
		}
		boolean enraged = tier == HulkConfig.TIER_ENRAGED;
		PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, TIER_SPEED_ID,
				enraged ? HulkConfig.ENRAGED_SPEED_BONUS : HulkConfig.ANGRY_SPEED_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	}

	// ---------------------------------------------------------------- armour, riders

	private static final net.minecraft.world.entity.EquipmentSlot[] ARMOUR_SLOTS = {
			net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
			net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET };

	/** Worn armour bursts off as he grows: each piece takes {@link HulkConfig#ARMOUR_TEAR_DAMAGE} durability and falls to the ground. */
	static void tearOffArmour(ServerPlayer player) {
		boolean any = false;
		for (net.minecraft.world.entity.EquipmentSlot slot : ARMOUR_SLOTS) {
			net.minecraft.world.item.ItemStack worn = player.getItemBySlot(slot);
			if (worn.isEmpty() || boundToSlot(worn)) {
				continue;
			}
			any = true;
			player.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
			if (worn.isDamageableItem()) {
				int damage = worn.getDamageValue() + HulkConfig.ARMOUR_TEAR_DAMAGE;
				if (damage >= worn.getMaxDamage()) {
					player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.9f, 1.0f);
					continue; // torn apart completely
				}
				worn.setDamageValue(damage);
			}
			net.minecraft.world.entity.item.ItemEntity dropped = player.drop(worn, false);
			if (dropped != null) {
				dropped.setPickUpDelay(60);
			}
		}
		if (any) {
			player.displayClientMessage(Component.translatable("message.projecthero.hulk.armour_torn").withStyle(ChatFormatting.RED), true);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 1.0f, 0.5f);
		}
	}

	/** While he is out, armour that gets put on is pushed straight back off (to the inventory, or dropped when it is full). */
	private static void bounceArmour(ServerPlayer player) {
		for (net.minecraft.world.entity.EquipmentSlot slot : ARMOUR_SLOTS) {
			net.minecraft.world.item.ItemStack worn = player.getItemBySlot(slot);
			if (worn.isEmpty() || boundToSlot(worn)) {
				continue;
			}
			net.minecraft.world.item.ItemStack piece = worn.copy();
			player.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
			if (!player.getInventory().add(piece)) {
				player.drop(piece, false);
			}
			say(player, "message.projecthero.hulk.armour_refused", ChatFormatting.RED);
		}
	}

	/** Curse of Binding (and power suits bound that way) stays put -- those belong to other systems. */
	private static boolean boundToSlot(net.minecraft.world.item.ItemStack stack) {
		return net.minecraft.world.item.enchantment.EnchantmentHelper.has(stack,
				net.minecraft.world.item.enchantment.EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE);
	}

	/** Whoever is riding his back gets off (he changed back, died, rampaged, logged out). */
	static void ejectRider(ServerPlayer player) {
		if (!player.getPassengers().isEmpty()) {
			player.ejectPassengers();
		}
	}

	/** Current eased size bonus (0 = Banner size; the Hulk is {@code scaleForRage(rage) - 1}). */
	public static double scaleBonus(ServerPlayer player) {
		AttributeInstance inst = player.getAttribute(Attributes.SCALE);
		AttributeModifier m = inst == null ? null : inst.getModifier(SCALE_ID);
		return m == null ? 0.0 : m.amount();
	}

	/** v0.16.4: the size bonus he should have right now (before the per-tick easing and the room check). */
	static double targetScaleBonus(ServerPlayer player, HulkState s, long now) {
		if (s.hulk) {
			double full = HulkConfig.scaleForRage(s.rage) - 1.0;
			if (s.worldBreaker(now)) {
				full = WorldBreaker.scaleBonus(); // v0.16.4 (user): "in this rage mode hulk grows to 10 blocks tall"
			}
			if (s.dying()) {
				return Math.max(0.0, scaleBonus(player)); // he lies there as he fell
			}
			float t = now - s.formChangedAt;
			if (t < changeTicks(s)) {
				return full * smooth(growth(s, t)); // grown over the change itself
			}
			return full;
		}
		Double from = SHRINK_FROM.get(player.getUUID());
		if (from == null || from <= 0.0) {
			return 0.0;
		}
		int dur = s.dying() ? HulkConfig.DEATH_REVERT_TICKS : HulkConfig.GROWTH_TICKS;
		return from * (1.0 - smooth((now - s.formChangedAt) / (float) dur));
	}

	/**
	 * Eases the body toward its target size; growth waits while there is no room. v0.16.4: as the Hulk the target is his
	 * rage's height (3.5 to 6 blocks), so he swells as he gets angrier and shrinks back as he cools.
	 */
	private static void tickScale(ServerPlayer player, HulkState s, long now) {
		double cur = scaleBonus(player);
		double target = targetScaleBonus(player, s, now);
		if (Math.abs(cur - target) < 1.0e-4) {
			if (target <= 1.0e-4 && cur != 0.0) {
				PowerToggles.clearModifier(player, Attributes.SCALE, SCALE_ID);
			}
			if (!s.hulk && target <= 1.0e-4) {
				SHRINK_FROM.remove(player.getUUID());
			}
			return;
		}
		double next;
		boolean changeClock = (s.hulk && now - s.formChangedAt < changeTicks(s)) || (!s.hulk && SHRINK_FROM.containsKey(player.getUUID()));
		if (changeClock) {
			next = target; // the change (either way) runs on its own clock
		} else {
			// v0.16.4: World Breaker grows him (and Burnout shrinks him back) much faster than rage does
			double ease = s.worldBreaker(now) || (s.burnout(now) && cur > target)
					? Math.max(HulkConfig.SIZE_EASE_PER_TICK, HulkConfig.worldBreaker().sizeEasePerTick) : HulkConfig.SIZE_EASE_PER_TICK;
			double step = Math.max(ease * 0.25, Math.min(ease, Math.abs(target - cur) * 0.15));
			next = cur < target ? Math.min(target, cur + step) : Math.max(target, cur - step);
		}
		next = Math.round(next * 1000.0) / 1000.0;
		if (next > cur) {
			double w = 0.6 * (1.0 + next);
			double h = HulkConfig.PLAYER_HEIGHT * (1.0 + next);
			AABB box = new AABB(player.getX() - w / 2, player.getY(), player.getZ() - w / 2,
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
		if (s.hulk) {
			applySizeStats(player);
		}
	}

	/**
	 * Pinned in place through the change, the death sequence and a rampage (the attributes sync, so the client stops moving
	 * too; a rampage moves him by velocity from the server).
	 */
	private static void tickChangeLock(ServerPlayer player, HulkState s, long now) {
		boolean rampage = s.rampaging(now);
		if (!player.isAlive() || (!changing(s, now) && !rampage)) {
			clearChangeLock(player);
			return;
		}
		PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, CHANGE_LOCK_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		PowerToggles.modifier(player, Attributes.JUMP_STRENGTH, CHANGE_JUMP_LOCK_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		if (rampage) {
			return; // HulkControl drives him
		}
		player.setSprinting(false);
		if (player.onGround()) {
			Vec3 v = player.getDeltaMovement();
			if (v.x * v.x + v.z * v.z > 1.0e-4) {
				player.setDeltaMovement(0.0, v.y, 0.0);
				player.hurtMarked = true;
			}
		}
	}

	private static void clearChangeLock(ServerPlayer player) {
		PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, CHANGE_LOCK_ID);
		PowerToggles.clearModifier(player, Attributes.JUMP_STRENGTH, CHANGE_JUMP_LOCK_ID);
	}

	// ---------------------------------------------------------------- tick

	/** Every player, every server tick (from {@code AbilityRouter.serverTick}). A no-op without the power. */
	public static void tick(ServerPlayer player) {
		HulkState s = state(player);
		if (!s.hasPower) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();
		// v0.16.4 (user): "i dont want the player to be forever stuck as the hulk" -- first, whatever else happens this tick
		HulkControl.watchdog(player);
		s = state(player);
		tickScale(player, s, now);
		tickChangeLock(player, s, now);
		if (player.tickCount % 20 == 0) {
			reconcile(player); // safety net: a respawn or another mod may have cleared a transient modifier
		}
		if (!player.isAlive() || player.isSpectator()) {
			return;
		}
		if (s.dying()) {
			tickDeath(level, player, s, now);
			return;
		}
		HulkControl.preTick(player); // v0.16.4: aimed before the moves' impacts land
		HulkAbilities.tick(player);
		HulkGrab.tick(player);
		com.projecthero.mod.hulk.gladiator.GladiatorAbilities.tick(player); // v0.15.3: the Gladiator's running moves
		HulkControl.tick(player);
		HulkRoar.tick(player);
		s = state(player);
		if (!s.hulk && !player.getPassengers().isEmpty()) {
			ejectRider(player);
		}
		HulkRiding.tick(player); // v0.15.21: riders never take fall damage

		if (s.hulk) {
			// v0.16.4 (user): World Breaker (rage pinned at 100, timed out into Burnout) / Burnout (rage frozen at 0)
			WorldBreaker.tick(player, now);
			s = state(player);
			// rage only burns off once he has been out of combat (no hit taken or dealt) for 5 s -- all the way to 0, which never
			// changes him back (v0.16.4)
			long quiet = now - Math.max(s.lastCombatAt, s.formChangedAt + changeTicks(s));
			if (now % 20L == 0L && quiet >= HulkConfig.HULK_OUT_OF_COMBAT_TICKS && s.rage > 0.0f && !s.worldBreaker(now)) {
				HulkState n = s.copy();
				n.rage = clampRage(s.rage - HulkConfig.HULK_DRAIN_PER_SECOND);
				save(player, n);
				s = n;
			}
			applyRageTier(player, s);
			applyRageHealth(player, s); // v0.16.4 (user): 2 bars of health at 0 rage, 3 at 100
			if (player.tickCount % HulkConfig.REGEN_INTERVAL_TICKS == 0 && player.getHealth() < player.getMaxHealth()) {
				player.heal(HulkConfig.REGEN_AMOUNT);
			}
			if (player.isOnFire()) {
				player.clearFire(); // fire does not touch him
			}
			if (player.tickCount % 5 == 0) {
				bounceArmour(player);
			}
			if (changing(s, now)) {
				tickChange(level, player, s, now);
			} else {
				tickAura(level, player, s, now);
			}
			return;
		}

		// v0.16.4: Banner has no rage -- a leftover from an older save is cleared
		if (s.rage > 0.0f) {
			HulkState n = s.copy();
			n.rage = 0.0f;
			save(player, n);
			s = n;
		}
		// holding H -- the willing change
		if (s.combat.strainStart != 0L) {
			tickHold(level, player, s, now);
		}
	}

	private static void tickAura(ServerLevel level, ServerPlayer player, HulkState s, long now) {
		double h = player.getBbHeight();
		if (now % 8L == 0L) {
			level.sendParticles(DEEP_GREEN, player.getX(), player.getY() + h * 0.5, player.getZ(), 1, 0.45, h * 0.35, 0.45, 0.0);
		}
		// v0.15.18: Enraged -- the gamma glow pours off him, hotter toward 100
		if (HulkConfig.rageTier(s.rage) == HulkConfig.TIER_ENRAGED && now % 3L == 0L) {
			rageGlow(level, player, 0.4f + 0.6f * (s.rage - HulkConfig.RAGE_TIER_ENRAGED) / (HulkConfig.RAGE_MAX - HulkConfig.RAGE_TIER_ENRAGED), now);
		}
	}

	// ---------------------------------------------------------------- effects

	/** v0.15.18: an Enraged Hulk -- green dust off his body, a pulse of it with a quickening heartbeat. {@code heat} 0..1. */
	private static void rageGlow(ServerLevel level, ServerPlayer player, float heat, long now) {
		heat = Math.max(0.0f, Math.min(1.0f, heat));
		double h = player.getBbHeight();
		int count = 2 + Math.round(heat * 6.0f);
		level.sendParticles(GAMMA_GREEN, player.getX(), player.getY() + h * 0.55, player.getZ(), count, 0.32, h * 0.3, 0.32, 0.01);
		level.sendParticles(DEEP_GREEN, player.getX(), player.getY() + h * 0.3, player.getZ(), 1 + count / 3, 0.35, h * 0.25, 0.35, 0.0);
		long beat = Math.max(12L, 30L - Math.round(heat * 18.0f)); // the heart speeds up
		if (now % beat < 3L) {
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + h * 0.6, player.getZ(), 2 + Math.round(heat * 4.0f),
					0.4, h * 0.3, 0.4, 0.0);
			if (now % beat == 0L && heat > 0.4f) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS,
						0.4f + heat * 0.6f, 0.8f);
			}
		}
	}

	/**
	 * The change, tick by tick. The quick style: green gamma pours off him while he grows (the roar came at the start). The
	 * five slow styles: fighting it (heartbeats, gamma leaking out), growing as the Hulk takes over, then the roar.
	 */
	private static void tickChange(ServerLevel level, ServerPlayer player, HulkState s, long now) {
		long t = now - s.formChangedAt;
		double h = player.getBbHeight();
		if (s.combat.transformStyle == HulkState.TRANSFORM_QUICK) {
			level.sendParticles(GAMMA_GREEN, player.getX(), player.getY() + h * 0.5, player.getZ(), 6, 0.5, h * 0.4, 0.5, 0.02);
			return;
		}
		if (t < HulkConfig.FORCED_KNEEL_TICKS) {
			if (t % 10L == 0L) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.6f, 0.6f);
			}
			level.sendParticles(DEEP_GREEN, player.getX(), player.getY() + h * 0.4, player.getZ(), 2, 0.3, h * 0.2, 0.3, 0.0);
			return;
		}
		long grow = t - HulkConfig.FORCED_KNEEL_TICKS;
		if (grow < HulkConfig.FORCED_GROWTH_TICKS) {
			float f = grow / (float) HulkConfig.FORCED_GROWTH_TICKS;
			level.sendParticles(GAMMA_GREEN, player.getX(), player.getY() + h * 0.45, player.getZ(), 3 + Math.round(f * 5.0f), 0.45, h * 0.3,
					0.45, 0.02);
			if (grow % 8L == 0L) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.8f,
						0.55f + f * 0.2f);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS,
						0.8f, 0.5f + f * 0.3f); // stretching, tearing
			}
			if (grow % 20L == 10L) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_STUNNED, SoundSource.PLAYERS, 0.8f + f,
						0.5f);
			}
			return;
		}
		if (t == HulkConfig.FORCED_KNEEL_TICKS + HulkConfig.FORCED_GROWTH_TICKS + 6L) {
			roar(level, player); // he stands tall and lets it out
		}
		if (now % 4L == 0L) {
			level.sendParticles(DEEP_GREEN, player.getX(), player.getY() + h * 0.5, player.getZ(), 2, 0.5, h * 0.35, 0.5, 0.0);
		}
	}

	/** The roar and the shock as the Hulk arrives. */
	private static void roar(ServerLevel level, ServerPlayer player) {
		Vec3 c = player.position().add(0, player.getBbHeight() * 0.5, 0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.6f, 0.7f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.POLAR_BEAR_WARNING, SoundSource.PLAYERS, 1.8f, 0.45f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.6f, 1.6f);
		HulkCombat.shake(level, player.position(), 0.6f, HulkConfig.GROWTH_TICKS, 24.0);
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(), 30, 1.2, 0.1, 1.2, 0.08);
		level.sendParticles(GAMMA_GREEN, c.x, c.y, c.z, 80, 0.7, 1.0, 0.7, 0.05);
		level.sendParticles(DEEP_GREEN, c.x, c.y, c.z, 40, 0.9, 1.1, 0.9, 0.02);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y, c.z, 20, 0.8, 1.0, 0.8, 0.1);
	}

	private static void transformFx(ServerPlayer player, boolean quick, boolean forced) {
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0f, 0.6f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.0f, 0.5f);
		if (quick) {
			roar(level, player);
		} else {
			// the slow change -- the roar comes when he stands tall (tickChange)
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 1.2f, 0.5f);
			level.sendParticles(DEEP_GREEN, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.4, 0.7, 0.4, 0.02);
		}
		player.displayClientMessage(Component.translatable(forced ? "message.projecthero.hulk.transform_forced"
				: "message.projecthero.hulk.transform").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
	}

	private static void revertFx(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 1.0f, 0.6f);
		level.sendParticles(DEEP_GREEN, c.x, c.y, c.z, 30, 0.5, 0.8, 0.5, 0.02);
	}

	// ---------------------------------------------------------------- lifecycle

	/**
	 * Join: logging out ends the Hulk -- he comes back as Banner. v0.16.4: a player who logged out in the middle of the Hulk
	 * death sequence comes back for its last two seconds (and dies); the hold-H cooldown is kept (clamped, since game time is
	 * per world).
	 */
	public static void onPlayerJoin(ServerPlayer player) {
		HulkState s = state(player);
		if (!s.hasPower) {
			return;
		}
		long now = player.level().getGameTime();
		com.projecthero.mod.hulk.gladiator.GladiatorAbilities.onJoin(player); // v0.15.3: no weapon is away after a relog
		// v0.16.4 (user): a rampage / forced rampage / takeover still running resumes with the SAME end time (he comes back as
		// the Hulk, mid-rampage); one that ended while he was away -- or from another world's clock -- is cleaned up
		long left = s.combat.rampageUntil - now;
		if (s.hulk && !s.dying() && s.combat.rampageUntil != 0L && left > 0L && left <= HulkControl.durationTicks(s.wild.kind) + 20L) {
			HulkState r = s.copy();
			r.formChangedAt = now - changeTicks(r) - 1L; // out and ready, not mid-change
			r.leapChargeStart = 0L;
			r.leaping = false;
			r.combat.strainStart = 0L;
			r.combat.chargeUntil = 0L;
			r.combat.holding = false;
			r.wild.target = -1;
			r.abilityReadyAt.entrySet().removeIf(e -> e.getValue() > now + 20L * 60L);
			if (r.wild.startedAt > now) {
				r.wild.startedAt = now - 100L;
			}
			WorldBreaker.sanitize(r, now, false); // v0.16.4: World Breaker never survives a relog
			r.moves.throwChargeStart = 0L;
			save(player, r);
			reconcile(player);
			return;
		}
		boolean endedTakeover = s.hulk && !s.dying() && s.combat.rampageUntil != 0L && s.wild.kind == HulkState.RAMPAGE_TAKEOVER;
		HulkState n = s.copy();
		HulkControl.clearRampage(n);
		n.wild.pending = 0;
		n.wild.warnBanner = false;
		if (endedTakeover) {
			n.wild.failStreak = 0; // the takeover ran its course while he was away: he wakes as Banner
		}
		n.hulk = false;
		n.rage = 0.0f;
		n.combat.unwilling = false;
		n.combat.strainStart = 0L;
		n.combat.rampageUntil = 0L;
		n.combat.calming = false;
		// game time is per world: nothing carried over from another world may lock the player out
		n.exhaustedUntil = 0L;
		n.formChangedAt = 0L;
		n.lastCombatAt = 0L;
		n.combat.lastHurtAt = 0L;
		n.leapChargeStart = 0L;
		n.leaping = false;
		n.animId = HulkState.ANIM_NONE;
		n.abilityReadyAt.entrySet().removeIf(e -> e.getValue() > now + 20L * 60L);
		n.rules.nextRampageAt = 0L;
		n.rules.warnLen = 0;
		n.rules.warnPos = 0;
		n.rules.ringStreak = 0;
		if (n.rules.holdCooldownUntil > now + HulkConfig.WILLING_COOLDOWN_TICKS) {
			n.rules.holdCooldownUntil = 0L;
		}
		// v0.16.4: he comes back as Banner -- a World Breaker from before is over (its cooldown runs), no charge left running
		WorldBreaker.sanitize(n, now, false);
		n.moves.throwChargeStart = 0L;
		if (s.dying()) {
			// he was dying when he left: the last two seconds, then the death
			n.rules.deathFallAt = now - HulkConfig.DEATH_LIE_TICKS - HulkConfig.DEATH_REVERT_TICKS;
			n.formChangedAt = now - HulkConfig.DEATH_REVERT_TICKS;
			n.animId = HulkState.ANIM_DEATH_FALL;
			n.animStart = n.rules.deathFallAt;
		}
		save(player, n);
		reconcile(player);
		PowerToggles.clearModifier(player, Attributes.SCALE, SCALE_ID);
		SHRINK_FROM.remove(player.getUUID());
	}

	/** Respawn: a fresh Banner -- no rage, no Hulk, normal size at once. The hold-H cooldown carries on. */
	public static void onPlayerRespawn(ServerPlayer player) {
		HulkState s = state(player);
		if (!s.hasPower) {
			return;
		}
		HulkState n = s.copy();
		n.hulk = false;
		n.combat.unwilling = false;
		n.combat.strainStart = 0L;
		n.combat.rampageUntil = 0L;
		n.rage = 0.0f;
		n.exhaustedUntil = 0L;
		n.formChangedAt = 0L;
		n.leapChargeStart = 0L;
		n.leaping = false;
		n.animId = HulkState.ANIM_NONE;
		n.abilityReadyAt.clear();
		long cooldown = n.rules.holdCooldownUntil;
		n.rules = new HulkState.Rules();
		n.rules.holdCooldownUntil = cooldown;
		// v0.16.4: nothing of a rampage survives death; the losing streak and Banner's (paused) clock do
		HulkControl.clearRampage(n);
		n.wild.pending = 0;
		n.wild.warnBanner = false;
		WorldBreaker.sanitize(n, player.level().getGameTime(), false); // v0.16.4
		n.moves.throwChargeStart = 0L;
		n.moves.wbBurnoutUntil = 0L;
		save(player, n);
		reconcile(player);
		PowerToggles.clearModifier(player, Attributes.SCALE, SCALE_ID);
		forget(player.getUUID());
		clearChangeLock(player);
	}

	/** Death / logout / dimension change: the running moves, the rampage and the riders end. The death sequence is kept. */
	public static void clearTransient(ServerPlayer player) {
		LAST_MESSAGE.remove(player.getUUID());
		HulkAbilities.clear(player.getUUID());
		HulkGrab.release(player);
		com.projecthero.mod.hulk.gladiator.GladiatorAbilities.clear(player); // v0.15.3
		HulkControl.clear(player.getUUID());
		HulkRoar.clear(player.getUUID());
		ejectRider(player);
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		if (s != null && (s.leapChargeStart != 0L || s.leaping || (s.animId != HulkState.ANIM_NONE && !s.dying()) || s.combat.chargeUntil != 0L
				|| s.combat.smashChargeStart != 0L || s.combat.calming || s.combat.holding
				|| s.combat.strainStart != 0L || s.rules.warnLen != 0)) {
			// v0.16.4 (user): a running rampage / takeover is KEPT (its end time and kind): a relog resumes it with the same
			// end, and onPlayerJoin / the watchdog clean up one that has expired
			boolean rampage = s.combat.rampageUntil != 0L && s.hulk && !s.dying();
			HulkState n = s.copy();
			n.leapChargeStart = 0L;
			n.leaping = false;
			if (!s.dying()) {
				n.animId = HulkState.ANIM_NONE;
			}
			n.combat.chargeUntil = 0L;
			n.combat.smashChargeStart = 0L;
			n.combat.calming = false;
			n.combat.holding = false;
			n.combat.strainStart = 0L;
			n.combat.promptKey = 0;
			n.rules.warnLen = 0;
			n.rules.warnPos = 0;
			n.wild.warnBanner = false;
			if (!rampage) {
				HulkControl.clearRampage(n);
				n.combat.control = 100.0f;
				if (n.hulk) {
					n.rules.nextRampageAt = player.level().getGameTime() + HulkConfig.control().rampageEveryTicks;
				}
			}
			save(player, n);
		}
	}

	// ---------------------------------------------------------------- death

	/** Client-safe: would Banner's death be turned into the change right now? v0.16.4: always, as Banner. */
	public static boolean deathSaveReady(Player player) {
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		return s != null && s.hasPower && !s.hulk && !s.dying();
	}

	/**
	 * v0.16.4 (user): every death of a Gamma player is turned into something else (the {@code ALLOW_DEATH} hook). Returns true
	 * if the death was prevented (the caller cancels it).
	 * <ul>
	 *   <li>As Banner: the Hulk comes out -- every time, no cooldown, even on the hold-H cooldown -- at full health
	 *       ({@link #transform} forced, a random style).</li>
	 *   <li>As the Hulk: he does not die yet -- he falls flat on his back ({@link HulkState#ANIM_DEATH_FALL}), slowly changes
	 *       back into the regular player and dies {@link HulkConfig#DEATH_FINAL_DELAY_TICKS} after that ({@link #tickDeath}).</li>
	 * </ul>
	 * /kill and the void still kill at once, and so does anything that stops the Hulk coming out (inside a Titan, All Might's
	 * Power Form) -- and the final death of the sequence is never caught.
	 */
	public static boolean tryDeathSave(ServerPlayer player, DamageSource source) {
		HulkState s = state(player);
		if (!s.hasPower || player.isSpectator() || player.getAbilities().invulnerable || FINAL_DEATH.contains(player.getUUID())
				|| source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)
				|| source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)) {
			return false;
		}
		if (s.dying()) {
			player.setHealth(1.0f);
			return true; // already on his way out -- the sequence decides when
		}
		if (s.hulk) {
			startDeathFall(player, source);
			return true;
		}
		if (com.projecthero.mod.titanshifter.TitanShifter.phase(player).insideForm()
				|| com.projecthero.mod.allmight.AllMight.isFullPower(player)) {
			return false;
		}
		HulkState n = s.copy();
		n.exhaustedUntil = 0L;
		n.combat.strainStart = 0L;
		save(player, n);
		player.setHealth(1.0f);
		transform(player, true);
		if (isHulk(player) && HulkConfig.control().enabled) {
			// v0.16.4 (user): "When the player dies and the hulk takes over the hulk should immediately be in a rampage mode" --
			// a normal (ring) rampage the moment the change is over
			HulkState r = state(player).copy();
			// v0.16.4 (user): "whenever the player dies they get transported to the same place ... they need to right click the
			// screen to regain control of the hulk ... make this happen instead of the regular rampage minigame" -- the mind rampage
			r.wild.pending = (HulkConfig.control().mindScreen ? HulkState.RAMPAGE_MIND : HulkState.RAMPAGE_NORMAL) + 1;
			save(player, r);
		}
		player.setHealth(player.getMaxHealth());
		player.clearFire();
		player.invulnerableTime = 40;
		player.removeEffect(MobEffects.WITHER);
		player.removeEffect(MobEffects.POISON);
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 3.0f, 0.5f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8f, 0.7f);
		level.sendParticles(GAMMA_GREEN, player.getX(), player.getY() + 1.0, player.getZ(), 80, 0.8, 1.2, 0.8, 0.1);
		HulkAbilities.shockwave(player, player.position(), 5.0, 6.0f, 1.4, 0.5, 0.7f); // v0.16.4 (user nerf): 8 -> 6 (x0.75)
		player.displayClientMessage(Component.translatable("message.projecthero.hulk.death_save")
				.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
		return true;
	}

	/** v0.16.4: the Hulk took his killing blow -- down he goes, flat on his back. */
	static void startDeathFall(ServerPlayer player, DamageSource source) {
		long now = player.level().getGameTime();
		WorldBreaker.stop(player); // v0.16.4: dying ends World Breaker (no 10-block body lying there for good)
		HulkGrab.release(player);
		HulkAbilities.endCharge(player);
		com.projecthero.mod.hulk.gladiator.GladiatorAbilities.clear(player);
		HulkControl.clear(player.getUUID());
		ejectRider(player);
		DEATH_SOURCE.put(player.getUUID(), source);
		HulkState n = state(player).copy();
		n.rules.deathFallAt = now;
		n.rules.warnLen = 0;
		n.rules.warnPos = 0;
		n.rules.ringStreak = 0;
		HulkControl.clearRampage(n); // v0.16.4: a takeover / forced rampage ends with the fall too
		n.wild.pending = 0;
		n.combat.strainStart = 0L;
		n.combat.holding = false;
		n.combat.chargeUntil = 0L;
		n.combat.smashChargeStart = 0L;
		n.leapChargeStart = 0L;
		n.animId = HulkState.ANIM_DEATH_FALL;
		n.animStart = now;
		save(player, n);
		player.setHealth(1.0f);
		player.clearFire();
		player.removeEffect(MobEffects.WITHER);
		player.removeEffect(MobEffects.POISON);
		player.setSprinting(false);
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_STUNNED, SoundSource.PLAYERS, 2.0f, 0.5f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_DEATH, SoundSource.PLAYERS, 1.2f, 0.6f);
		player.displayClientMessage(Component.translatable("message.projecthero.hulk.falling").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
				true);
	}

	/** v0.16.4: the death sequence, tick by tick -- the fall, the slow change back, the real death. */
	private static void tickDeath(ServerLevel level, ServerPlayer player, HulkState s, long now) {
		long t = now - s.rules.deathFallAt;
		if (player.getHealth() < 1.0f) {
			player.setHealth(1.0f);
		}
		if (t == 12L) { // he hits the ground
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_BIG_FALL, SoundSource.PLAYERS, 1.6f, 0.5f);
			level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(), 30, player.getBbWidth(), 0.05,
					player.getBbWidth(), 0.03);
			HulkCombat.shake(level, player.position(), 0.5f, 12, 16.0);
		}
		if (s.hulk) {
			if (t >= HulkConfig.DEATH_LIE_TICKS) {
				// the slow change back, still on his back
				SHRINK_FROM.put(player.getUUID(), scaleBonus(player));
				HulkState n = s.copy();
				n.hulk = false;
				n.rage = 0.0f;
				n.formChangedAt = now;
				n.combat.unwilling = false;
				n.rules.nextRampageAt = 0L;
				save(player, n);
				reconcile(player);
				player.setHealth(1.0f);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 1.2f, 0.5f);
			} else if (t % 10L == 0L) {
				level.sendParticles(DEEP_GREEN, player.getX(), player.getY() + 0.5, player.getZ(), 6, player.getBbWidth() * 0.6, 0.3,
						player.getBbWidth() * 0.6, 0.0);
			}
			return;
		}
		long since = t - HulkConfig.DEATH_LIE_TICKS;
		if (since < HulkConfig.DEATH_REVERT_TICKS && since % 6L == 0L) {
			level.sendParticles(DEEP_GREEN, player.getX(), player.getY() + 0.4, player.getZ(), 3, 0.5, 0.2, 0.5, 0.0);
		}
		if (since == HulkConfig.DEATH_REVERT_TICKS) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2f, 0.4f);
		}
		if (since >= HulkConfig.DEATH_REVERT_TICKS + HulkConfig.DEATH_FINAL_DELAY_TICKS) {
			finalDeath(player);
		}
	}

	/** v0.16.4: the real death at the end of the sequence (drops, the death screen) -- never caught by the base-form change. */
	static void finalDeath(ServerPlayer player) {
		DamageSource source = DEATH_SOURCE.remove(player.getUUID());
		if (source == null) {
			source = player.damageSources().generic();
		}
		HulkState n = state(player).copy();
		n.rules.deathFallAt = 0L;
		n.animId = HulkState.ANIM_NONE;
		save(player, n);
		clearChangeLock(player);
		SHRINK_FROM.remove(player.getUUID());
		FINAL_DEATH.add(player.getUUID());
		try {
			player.setHealth(0.0f);
			player.die(source);
		} finally {
			FINAL_DEATH.remove(player.getUUID());
		}
	}

	/** v0.16.4: is this player's real death going through right now? (The base-form change lets it.) */
	public static boolean finalDeathInProgress(Player player) {
		return FINAL_DEATH.contains(player.getUUID());
	}
}
