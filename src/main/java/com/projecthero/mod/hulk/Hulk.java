package com.projecthero.mod.hulk;

import java.util.Map;
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
 * The single server-side API for the Hulk Hero-Tier power (v0.13.11 -- Phase 1: the core rage loop). Nothing
 * else pokes {@link HulkState}; every mutator re-saves through {@link ServerPlayer#setAttached}. All numbers
 * live in {@link HulkConfig}.
 *
 * <h2>The rage loop</h2>
 * Only a player with the Gamma power ({@link HulkState#hasPower}) builds rage. v0.13.17 rules: every point of damage
 * he TAKES is 1 rage, in either form. As Banner, the damage he deals builds nothing, and 5 s after the last hit he
 * took his rage bleeds off at 2 a second. As the Hulk, every hit he lands adds 2, and only once he has been out of
 * combat for 5 s does rage burn down (0.75 a second). v0.15.18 rules: at 50 Banner can let the Hulk out with H (below
 * that, holding H strains him out anyway, for some health and food); at 100 the Hulk comes out on his own (the unwilling
 * change). The Hulk's rage only burns down to {@link HulkConfig#HULK_RAGE_FLOOR} -- it never changes him back. Instead it
 * sets his rage tier (Calm / Angry / Enraged), which scales his damage and speed. The player taps H to change back
 * (an unwilling Hulk only once his rage is under 50); that is only exhausting (Weakness + Slowness, no rage, no change for
 * a few seconds) after 10 minutes as the Hulk or from 80+ rage.
 *
 * <h2>The body</h2>
 * {@link #reconcile} sets every stat as a fixed-id transient attribute modifier from {@code (hasPower, hulk)}
 * -- idempotent, so it runs every second as a safety net. The size (1.8x) is eased in and out over
 * {@link HulkConfig#GROWTH_TICKS} by {@link #tickScale}, which waits if the bigger body would not fit.
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
	/** v0.13.15: the unwilling change pins him to the spot (movement and jump multiplied to nothing). */
	private static final ResourceLocation CHANGE_LOCK_ID = PowerToggles.id("hulk_change_lock");
	private static final ResourceLocation CHANGE_JUMP_LOCK_ID = PowerToggles.id("hulk_change_jump_lock");
	/** v0.15.18: the rage-tier bonuses (Angry / Enraged), on top of the Hulk's own stats. */
	private static final ResourceLocation TIER_ATTACK_ID = PowerToggles.id("hulk_tier_attack");
	private static final ResourceLocation TIER_SPEED_ID = PowerToggles.id("hulk_tier_speed");

	private static final DustParticleOptions GAMMA_GREEN = new DustParticleOptions(new Vector3f(0.3f, 0.95f, 0.2f), 1.6f);
	private static final DustParticleOptions DEEP_GREEN = new DustParticleOptions(new Vector3f(0.12f, 0.55f, 0.1f), 1.2f);

	/** Per-player throttle for action-bar feedback. */
	private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();

	private Hulk() {
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
		HulkAbilities.clearSessionState();
		HulkGrab.clearSessionState();
		com.projecthero.mod.hulk.gladiator.GladiatorAbilities.clearSessionState(); // v0.15.3
		HulkControl.clearSessionState();
		HulkCalm.clearSessionState();
		GammaOverload.clearSessionState();
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
	 * v0.13.15: in the middle of the unwilling change (on his knees, growing, rising) -- he cannot move, act or be hurt until
	 * it is over. Client-safe.
	 */
	public static boolean changing(Player player) {
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		return s != null && changing(s, player.level().getGameTime());
	}

	public static boolean changing(HulkState s, long now) {
		return s.hasPower && s.hulk && s.combat.unwilling && now - s.formChangedAt < HulkConfig.FORCED_CHANGE_TICKS;
	}

	/** Ticks the change into the Hulk takes for this state: the slow unwilling change, or the quick one H makes. */
	public static int changeTicks(HulkState s) {
		return s.combat.unwilling ? HulkConfig.FORCED_CHANGE_TICKS : HulkConfig.GROWTH_TICKS;
	}

	/**
	 * v0.13.15: how much of the Hulk shows, 0 (Banner) .. 1 (the Hulk), with {@code partialTick} for a smooth fade. The
	 * renderer cross-fades the two bodies with it, so the Hulk phases onto Banner as he grows and off him as he shrinks. It
	 * runs on the same clock as the growth in {@link #tickScale}. Client-safe.
	 */
	public static float visibility(Player player, float partialTick) {
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		if (s == null || !s.hasPower) {
			return 0.0f;
		}
		float t = (player.level().getGameTime() - s.formChangedAt) + partialTick;
		if (s.hulk) {
			float f = s.combat.unwilling ? (t - HulkConfig.FORCED_KNEEL_TICKS) / HulkConfig.FORCED_GROWTH_TICKS : t / HulkConfig.GROWTH_TICKS;
			return smooth(f);
		}
		return 1.0f - smooth(t / HulkConfig.GROWTH_TICKS);
	}

	private static float smooth(float f) {
		f = Math.max(0.0f, Math.min(1.0f, f));
		return f * f * (3.0f - 2.0f * f);
	}

	public static boolean exhausted(Player player) {
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		return s != null && s.exhaustedUntil > player.level().getGameTime();
	}

	/** v0.15.18: the Hulk's rage tier ({@link HulkConfig#TIER_CALM} ...); always Calm for Banner. Client-safe. */
	public static int rageTier(Player player) {
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		return s == null || !s.hasPower || !s.hulk ? HulkConfig.TIER_CALM : HulkConfig.rageTier(s.rage);
	}

	/** v0.15.18: 0..1 while Banner is holding H to strain the Hulk out below 50 rage (0 when not). Client-safe. */
	public static float strainProgress(Player player) {
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		if (s == null || !s.hasPower || s.hulk || s.combat.strainStart == 0L) {
			return 0.0f;
		}
		long t = player.level().getGameTime() - s.combat.strainStart;
		return Math.max(0.0f, Math.min(1.0f, t / (float) HulkConfig.STRAIN_HOLD_TICKS));
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

	// ---------------------------------------------------------------- grant / revoke

	/** Gives the Gamma power (command now; the Gamma Serum in Phase 4). False if the player already has it. */
	public static boolean grant(ServerPlayer player) {
		if (state(player).hasPower) {
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
		player.displayClientMessage(Component.translatable("message.projecthero.hulk.acquired_hint")
				.withStyle(ChatFormatting.DARK_GREEN), false);
		return true;
	}

	/** Takes the Gamma power away; a Hulk shrinks straight back (no exhaustion). */
	public static void revoke(ServerPlayer player) {
		HulkAbilities.clear(player.getUUID());
		com.projecthero.mod.hulk.gladiator.GladiatorAbilities.clear(player); // v0.15.3: the thrown weapons come home
		HulkGrab.release(player);
		HulkCalm.clear(player.getUUID());
		HulkControl.clear(player.getUUID());
		ejectRider(player);
		save(player, new HulkState());
		reconcile(player);
		PowerToggles.clearModifier(player, Attributes.SCALE, SCALE_ID);
		clearChangeLock(player);
		LAST_MESSAGE.remove(player.getUUID());
	}

	// ---------------------------------------------------------------- rage

	/** Sets rage outright (the {@code /hulk setrage} test command). 100 lets the Hulk out on the next tick. */
	public static void setRage(ServerPlayer player, float value) {
		HulkState n = state(player).copy();
		n.rage = clampRage(value);
		save(player, n);
		applyRageTier(player, n); // v0.15.18: a Hulk's tier follows at once
	}

	private static float clampRage(float v) {
		return Math.max(0.0f, Math.min(HulkConfig.RAGE_MAX, v));
	}

	/** This player with the Gamma power was just hurt for {@code amount}. */
	public static void onHurt(ServerPlayer player, float amount) {
		HulkState s = state(player);
		if (!s.hasPower || player.isSpectator()) {
			return;
		}
		float per = s.hulk ? HulkConfig.HULK_RAGE_PER_DAMAGE_TAKEN : HulkConfig.RAGE_PER_DAMAGE_TAKEN;
		gain(player, s, amount * per, true);
		HulkCalm.interrupt(player); // pain breaks the focus
	}

	/**
	 * This player with the Gamma power just hit something for {@code amount}. v0.13.17: Banner gains nothing from the damage
	 * he deals; the Hulk gains {@link HulkConfig#HULK_RAGE_PER_HIT} per hit (a punch, or each thing an ability hits).
	 */
	public static void onDealt(ServerPlayer player, float amount) {
		HulkState s = state(player);
		if (!s.hasPower || player.isSpectator()) {
			return;
		}
		gain(player, s, s.hulk ? HulkConfig.HULK_RAGE_PER_HIT : 0.0f, false);
		if (s.hulk) {
			HulkControl.onDealtDamage(player); // hitting things is how he stays in charge
		}
	}

	/** {@code hurt}: he took the hit (Banner's rage only bleeds off 5 s after the last one). */
	private static void gain(ServerPlayer player, HulkState s, float amount, boolean hurt) {
		long now = player.level().getGameTime();
		HulkState n = s.copy();
		n.lastCombatAt = now;
		if (hurt) {
			n.combat.lastHurtAt = now;
		}
		// an exhausted Banner has nothing left to get angry with
		if (amount > 0.0f && (s.hulk || s.exhaustedUntil <= now)) {
			n.rage = clampRage(s.rage + amount);
		}
		save(player, n);
	}

	// ---------------------------------------------------------------- the change

	/**
	 * v0.15.18: H pressed (the packet). As the Hulk it changes back ({@link #tryRevert}); as Banner it lets the Hulk out at
	 * {@link HulkConfig#MANUAL_TRANSFORM_RAGE}+ rage, and below that starts the strain -- keep holding H for
	 * {@link HulkConfig#STRAIN_HOLD_TICKS} ({@link #releaseH} calls it off). Server-validated; safe to spam.
	 */
	public static void pressH(ServerPlayer player) {
		HulkState s = state(player);
		if (!s.hasPower || !player.isAlive() || player.isSpectator()) {
			return;
		}
		if (s.hulk) {
			tryRevert(player);
			return;
		}
		if (s.rage + 1.0e-3f >= HulkConfig.MANUAL_TRANSFORM_RAGE) {
			tryTransform(player);
			return;
		}
		startStrain(player);
	}

	/** v0.15.18: H let go -- an unfinished strain is called off. */
	public static void releaseH(ServerPlayer player) {
		HulkState s = state(player);
		if (s.combat.strainStart == 0L) {
			return;
		}
		HulkState n = s.copy();
		n.combat.strainStart = 0L;
		save(player, n);
		if (!s.hulk) {
			say(player, "message.projecthero.hulk.strain_cancelled", ChatFormatting.GRAY);
		}
	}

	/** v0.15.18: Banner below 50 rage starts straining the Hulk out (H held). */
	private static void startStrain(ServerPlayer player) {
		HulkState s = state(player);
		long now = player.level().getGameTime();
		if (s.hulk || s.combat.strainStart != 0L || s.combat.calming || now - s.formChangedAt < HulkConfig.TOGGLE_DEBOUNCE_TICKS) {
			return;
		}
		if (s.exhaustedUntil > now) {
			say(player, "message.projecthero.hulk.exhausted_wait", ChatFormatting.GRAY,
					(int) Math.ceil((s.exhaustedUntil - now) / 20.0));
			return;
		}
		if (com.projecthero.mod.titanshifter.TitanShifter.phase(player).insideForm()
				|| com.projecthero.mod.allmight.AllMight.isFullPower(player)) {
			say(player, "message.projecthero.hulk.cannot_now", ChatFormatting.GRAY);
			return;
		}
		HulkState n = s.copy();
		n.combat.strainStart = now;
		save(player, n);
		say(player, "message.projecthero.hulk.strain_start", ChatFormatting.DARK_GREEN);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 1.0f, 0.5f);
	}

	/** v0.15.18: every tick while Banner strains -- heartbeat and gamma, and the change once the hold is long enough. */
	private static void tickStrain(ServerLevel level, ServerPlayer player, HulkState s, long now) {
		if (s.exhaustedUntil > now || s.combat.calming) {
			releaseH(player);
			return;
		}
		long t = now - s.combat.strainStart;
		if (t < 0L || t > HulkConfig.STRAIN_HOLD_TICKS + 40L) {
			releaseH(player); // a stale flag (another world's clock, a lost release)
			return;
		}
		float f = Math.min(1.0f, t / (float) HulkConfig.STRAIN_HOLD_TICKS);
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
		if (t >= HulkConfig.STRAIN_HOLD_TICKS) {
			finishStrain(player);
		}
	}

	/** v0.15.18: the strain worked -- it costs health (never the last point) and food, then the willing change. */
	private static void finishStrain(ServerPlayer player) {
		HulkState n = state(player).copy();
		n.combat.strainStart = 0L;
		save(player, n);
		float health = player.getHealth();
		player.setHealth(Math.max(Math.min(health, 1.0f), health - HulkConfig.STRAIN_HEALTH_COST));
		net.minecraft.world.food.FoodData food = player.getFoodData();
		food.setFoodLevel(Math.max(0, food.getFoodLevel() - HulkConfig.STRAIN_FOOD_COST));
		food.setSaturation(Math.min(food.getSaturationLevel(), food.getFoodLevel()));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.0f, 0.6f);
		transform(player, false, false);
		if (isHulk(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.hulk.strain_done")
					.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
		}
	}

	/**
	 * v0.15.18: H as the Hulk -- change back whenever you like (the quick shrink). Not mid-change, not while rampaging, and an
	 * unwilling Hulk lets go only below {@link HulkConfig#MANUAL_TRANSFORM_RAGE} rage. Exhausting only after
	 * {@link HulkConfig#LONG_FORM_TICKS} as the Hulk or from {@link HulkConfig#EXHAUST_RAGE} rage.
	 */
	public static void tryRevert(ServerPlayer player) {
		HulkState s = state(player);
		if (!s.hasPower || !s.hulk || !player.isAlive() || s.combat.calming) {
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
		if (s.combat.unwilling && s.rage + 1.0e-3f >= HulkConfig.MANUAL_TRANSFORM_RAGE) {
			say(player, "message.projecthero.hulk.revert_unwilling", ChatFormatting.RED,
					(int) HulkConfig.MANUAL_TRANSFORM_RAGE, (int) Math.ceil(s.rage));
			return;
		}
		boolean exhaust = exhaustingRevert(s, now);
		revert(player, exhaust);
		if (!exhaust) {
			player.displayClientMessage(Component.translatable("message.projecthero.hulk.reverted_willing")
					.withStyle(ChatFormatting.GREEN), true);
		}
	}

	/** v0.15.18: would changing back now be exhausting -- 10 minutes as the Hulk in one go, or 80+ rage. */
	public static boolean exhaustingRevert(HulkState s, long now) {
		return now - s.formChangedAt >= HulkConfig.LONG_FORM_TICKS || s.rage + 1.0e-3f >= HulkConfig.EXHAUST_RAGE;
	}

	/** H: let the Hulk out by choice -- needs {@link HulkConfig#MANUAL_TRANSFORM_RAGE} rage. Server-validated; safe to spam. */
	public static void tryTransform(ServerPlayer player) {
		HulkState s = state(player);
		if (!s.hasPower || !player.isAlive() || player.isSpectator()) {
			return;
		}
		long now = player.level().getGameTime();
		if (now - s.formChangedAt < HulkConfig.TOGGLE_DEBOUNCE_TICKS) {
			return;
		}
		if (s.hulk) {
			say(player, "message.projecthero.hulk.already", ChatFormatting.GREEN);
			return;
		}
		if (s.exhaustedUntil > now) {
			say(player, "message.projecthero.hulk.exhausted_wait", ChatFormatting.GRAY,
					(int) Math.ceil((s.exhaustedUntil - now) / 20.0));
			return;
		}
		if (s.rage + 1.0e-3f < HulkConfig.MANUAL_TRANSFORM_RAGE) {
			say(player, "message.projecthero.hulk.not_angry", ChatFormatting.GRAY,
					(int) Math.floor(s.rage), (int) HulkConfig.MANUAL_TRANSFORM_RAGE);
			return;
		}
		transform(player, false);
	}

	/**
	 * Banner becomes the Hulk. {@code forced}: rage hit the top on its own (or the death save). v0.13.15: that is the
	 * unwilling change -- he drops to his knees and changes slowly, then has to fight the Hulk for control. H is the willing
	 * one: quick, and the player stays in charge the whole time.
	 */
	public static void transform(ServerPlayer player, boolean forced) {
		transform(player, forced, true);
	}

	/** {@code burst}: the {@link HulkConfig#TRANSFORM_HEAL} on top (v0.15.18: the strained change gets none). */
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
		HulkState n = s.copy();
		n.hulk = true;
		n.formChangedAt = player.level().getGameTime();
		n.combat.control = 100.0f;
		n.combat.lastDealtAt = n.formChangedAt;
		n.combat.promptKey = 0;
		n.combat.rampageUntil = 0L;
		n.combat.unwilling = forced;
		n.combat.strainStart = 0L;
		save(player, n);
		tearOffArmour(player); // v0.13.14: he bursts out of it
		reconcile(player);
		player.setHealth(Math.min(player.getMaxHealth(), ratio * player.getMaxHealth() + (burst ? HulkConfig.TRANSFORM_HEAL : 0.0f)));
		transformFx(player, forced);
	}

	/**
	 * The Hulk shrinks back to Banner. {@code exhaust}: Weakness + Slowness follow (v0.15.18: {@link #tryRevert} decides --
	 * a long stint or 80+ rage; Calm Down never exhausts).
	 */
	public static void revert(ServerPlayer player, boolean exhaust) {
		HulkState s = state(player);
		if (!s.hasPower || !s.hulk) {
			return;
		}
		long now = player.level().getGameTime();
		HulkGrab.release(player);
		HulkAbilities.endCharge(player);
		com.projecthero.mod.hulk.gladiator.GladiatorAbilities.clear(player); // v0.15.3
		HulkControl.clear(player.getUUID());
		ejectRider(player);
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
		save(player, n);
		reconcile(player);
		player.setHealth(Math.max(1.0f, Math.min(player.getMaxHealth(), ratio * player.getMaxHealth())));
		if (exhaust) {
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, HulkConfig.EXHAUSTED_TICKS, 0, false, true, true));
			player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, HulkConfig.EXHAUSTED_TICKS, 0, false, true, true));
		}
		revertFx(player, exhaust);
	}

	private static float healthRatio(ServerPlayer player) {
		return player.getMaxHealth() <= 0.0f ? 1.0f : player.getHealth() / player.getMaxHealth();
	}

	// ---------------------------------------------------------------- stats

	/**
	 * Every stat in line with {@code (hasPower, hulk)}. Idempotent (fixed ids, write only on change). The size is
	 * not set here -- it eases in and out ({@link #tickScale}); a player without the power has it cleared outright.
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
			clearChangeLock(player);
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
		PowerToggles.modifier(player, Attributes.MAX_HEALTH, HEALTH_ID, HulkConfig.HEALTH_BONUS, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, HulkConfig.KNOCKBACK_RESISTANCE,
				AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID, HulkConfig.ARMOR_TOUGHNESS_BONUS,
				AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.STEP_HEIGHT, STEP_ID, HulkConfig.STEP_HEIGHT_BONUS, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID, HulkConfig.REACH_BONUS,
				AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.BLOCK_INTERACTION_RANGE, BLOCK_REACH_ID, HulkConfig.REACH_BONUS,
				AttributeModifier.Operation.ADD_VALUE);
		// v0.13.14: faster, harder-hitting, and diamond-level armour of his own
		PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, HulkConfig.SPEED_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		PowerToggles.modifier(player, Attributes.ATTACK_KNOCKBACK, ATTACK_KNOCKBACK_ID, HulkConfig.ATTACK_KNOCKBACK_BONUS,
				AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.ARMOR, ARMOR_ID, HulkConfig.ARMOR_BONUS, AttributeModifier.Operation.ADD_VALUE);
		applyRageTier(player, s);
	}

	/**
	 * v0.15.18: the rage-tier bonuses in line with {@code s} -- Angry / Enraged multiply his total attack damage and add to
	 * his speed; Calm (and Banner) have none. Fixed ids, written only on change, so it is cheap to run every tick.
	 */
	static void applyRageTier(ServerPlayer player, HulkState s) {
		int tier = s.hasPower && s.hulk ? HulkConfig.rageTier(s.rage) : HulkConfig.TIER_CALM;
		if (tier == HulkConfig.TIER_CALM) {
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, TIER_ATTACK_ID);
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, TIER_SPEED_ID);
			return;
		}
		boolean enraged = tier == HulkConfig.TIER_ENRAGED;
		PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, TIER_ATTACK_ID,
				enraged ? HulkConfig.ENRAGED_DAMAGE_BONUS : HulkConfig.ANGRY_DAMAGE_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
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

	/** Current eased size bonus (0 = Banner size, {@link HulkConfig#SCALE_BONUS} = full Hulk). */
	public static double scaleBonus(ServerPlayer player) {
		AttributeInstance inst = player.getAttribute(Attributes.SCALE);
		AttributeModifier m = inst == null ? null : inst.getModifier(SCALE_ID);
		return m == null ? 0.0 : m.amount();
	}

	/** Eases the body toward its target size over {@link HulkConfig#GROWTH_TICKS}; growth waits while there is no room. */
	private static void tickScale(ServerPlayer player, HulkState s) {
		double cur = scaleBonus(player);
		double target = s.hulk ? HulkConfig.SCALE_BONUS : 0.0;
		if (Math.abs(cur - target) < 1.0e-4) {
			if (target == 0.0 && cur != 0.0) {
				PowerToggles.clearModifier(player, Attributes.SCALE, SCALE_ID);
			}
			return;
		}
		int growTicks = HulkConfig.GROWTH_TICKS;
		if (s.hulk && s.combat.unwilling) {
			// v0.13.15: the unwilling change -- nothing grows while he drops to his knees, then it comes slowly
			if (player.level().getGameTime() - s.formChangedAt < HulkConfig.FORCED_KNEEL_TICKS) {
				return;
			}
			growTicks = HulkConfig.FORCED_GROWTH_TICKS;
		}
		double step = HulkConfig.SCALE_BONUS / Math.max(1, growTicks);
		double next = cur < target ? Math.min(target, cur + step) : Math.max(target, cur - step);
		next = Math.round(next * 1000.0) / 1000.0;
		if (next > cur) {
			double w = 0.6 * (1.0 + next);
			double h = 1.8 * (1.0 + next);
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
	}

	/** v0.13.15: pinned in place for the unwilling change (the attributes sync, so the client stops moving too). */
	private static void tickChangeLock(ServerPlayer player, HulkState s, long now) {
		if (!changing(s, now) || !player.isAlive()) {
			clearChangeLock(player);
			return;
		}
		PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, CHANGE_LOCK_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		PowerToggles.modifier(player, Attributes.JUMP_STRENGTH, CHANGE_JUMP_LOCK_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
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
		tickScale(player, s);
		tickChangeLock(player, s, now);
		if (player.tickCount % 20 == 0) {
			reconcile(player); // safety net: a respawn or another mod may have cleared a transient modifier
		}
		if (!player.isAlive() || player.isSpectator()) {
			return;
		}
		HulkAbilities.tick(player);
		HulkGrab.tick(player);
		com.projecthero.mod.hulk.gladiator.GladiatorAbilities.tick(player); // v0.15.3: the Gladiator's running moves
		HulkControl.tick(player);
		HulkCalm.tick(player);
		s = state(player);
		if (!s.hulk && !player.getPassengers().isEmpty()) {
			ejectRider(player);
		}

		if (s.hulk) {
			// v0.13.17: the Hulk only burns rage once he has been out of combat (no hit taken or dealt) for 5 s
			long quiet = now - Math.max(s.lastCombatAt, s.formChangedAt + changeTicks(s));
			// v0.15.18: ...and only down to the floor -- running low never changes him back (H or Calm Down does)
			if (now % 20L == 0L && quiet >= HulkConfig.HULK_OUT_OF_COMBAT_TICKS && s.rage > HulkConfig.HULK_RAGE_FLOOR) {
				HulkState n = s.copy();
				n.rage = Math.max(HulkConfig.HULK_RAGE_FLOOR, clampRage(s.rage - HulkConfig.HULK_DRAIN_PER_SECOND));
				save(player, n);
				s = n;
			}
			// v0.15.18: an unwilling Hulk is tamed once his rage is under 50 -- from then on he is the player's like any other
			if (s.combat.unwilling && !changing(s, now) && !s.rampaging(now) && s.rage < HulkConfig.MANUAL_TRANSFORM_RAGE) {
				HulkState n = s.copy();
				n.combat.unwilling = false;
				save(player, n);
				s = n;
				player.displayClientMessage(Component.translatable("message.projecthero.hulk.tamed").withStyle(ChatFormatting.GREEN), true);
			}
			applyRageTier(player, s);
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
				tickForcedChange(level, player, s, now);
			} else {
				tickAura(level, player, s, now);
			}
			return;
		}

		if (s.rage >= HulkConfig.RAGE_MAX && s.exhaustedUntil <= now) {
			transform(player, true);
			return;
		}
		// v0.15.18: holding H below 50 -- straining the Hulk out
		if (s.combat.strainStart != 0L) {
			tickStrain(level, player, s, now);
			s = state(player);
			if (s.hulk) {
				return;
			}
		}
		// v0.13.12: standing near a Gamma Reactor feeds the rage (once a second)
		if (now % 20L == 0L && s.exhaustedUntil <= now && nearReactor(player)) {
			gain(player, s, HulkConfig.REACTOR_RAGE_PER_SECOND, false);
			s = state(player);
			level.sendParticles(GAMMA_GREEN, player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.4, 0.6, 0.4, 0.02);
			return;
		}
		// v0.13.17: Banner cools off 2 a second once he has gone 5 s without being hurt (hitting things doesn't count)
		if (s.rage > 0.0f && now % 20L == 0L && now - s.combat.lastHurtAt >= HulkConfig.CALM_DELAY_TICKS) {
			HulkState n = s.copy();
			n.rage = clampRage(s.rage - HulkConfig.CALM_DECAY_PER_SECOND);
			save(player, n);
		}
		// v0.13.15: Banner is close to losing it -- green gamma pours off him, thicker the nearer he gets to 100
		// (v0.15.18: from its own 75 threshold -- H already works from 50)
		if (s.rage > HulkConfig.BANNER_GLOW_RAGE && now % 3L == 0L) {
			rageGlow(level, player, (s.rage - HulkConfig.BANNER_GLOW_RAGE) / (HulkConfig.RAGE_MAX - HulkConfig.BANNER_GLOW_RAGE), now);
		}
	}

	/** A Gamma Reactor within {@link HulkConfig#REACTOR_RADIUS} blocks. */
	private static boolean nearReactor(ServerPlayer player) {
		int r = HulkConfig.REACTOR_RADIUS;
		net.minecraft.core.BlockPos c = player.blockPosition();
		for (net.minecraft.core.BlockPos p : net.minecraft.core.BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
			if (player.level().getBlockState(p).is(com.projecthero.mod.hulk.item.HulkItems.GAMMA_REACTOR)) {
				return true;
			}
		}
		return false;
	}

	private static void tickAura(ServerLevel level, ServerPlayer player, HulkState s, long now) {
		double h = player.getBbHeight();
		// green gamma pours off him while he grows
		if (now - s.formChangedAt < changeTicks(s)) {
			level.sendParticles(GAMMA_GREEN, player.getX(), player.getY() + h * 0.5, player.getZ(), 6, 0.5, h * 0.4, 0.5, 0.02);
			return;
		}
		if (now % 8L == 0L) {
			level.sendParticles(DEEP_GREEN, player.getX(), player.getY() + h * 0.5, player.getZ(), 1, 0.45, h * 0.35, 0.45, 0.0);
		}
		// v0.15.18: Enraged -- the gamma glow pours off him, hotter toward 100
		if (HulkConfig.rageTier(s.rage) == HulkConfig.TIER_ENRAGED && now % 3L == 0L) {
			rageGlow(level, player, 0.4f + 0.6f * (s.rage - HulkConfig.RAGE_TIER_ENRAGED) / (HulkConfig.RAGE_MAX - HulkConfig.RAGE_TIER_ENRAGED), now);
		}
	}

	// ---------------------------------------------------------------- effects

	/**
	 * v0.13.15: Banner past 75 rage (v0.15.18: and an Enraged Hulk) -- green dust off his body, a pulse of it with a
	 * quickening heartbeat. {@code heat} 0..1.
	 */
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
	 * v0.13.15: the unwilling change, tick by tick -- on his knees fighting it (heartbeats, gamma leaking out), growing as the
	 * Hulk takes over, then the roar as he stands.
	 */
	private static void tickForcedChange(ServerLevel level, ServerPlayer player, HulkState s, long now) {
		long t = now - s.formChangedAt;
		double h = player.getBbHeight();
		if (t < HulkConfig.FORCED_KNEEL_TICKS) {
			if (t % 10L == 0L) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.6f, 0.6f);
			}
			if (t == 5L) { // he hits the ground
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.9f, 0.6f);
				level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(), 10, 0.5, 0.05, 0.5, 0.02);
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
			roar(level, player); // he stands and lets it out
		}
		if (now % 4L == 0L) {
			level.sendParticles(DEEP_GREEN, player.getX(), player.getY() + h * 0.5, player.getZ(), 2, 0.5, h * 0.35, 0.5, 0.0);
		}
	}

	/** The roar and the shock as the Hulk arrives (at once for the willing change, as he stands for the unwilling one). */
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

	private static void transformFx(ServerPlayer player, boolean forced) {
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0f, 0.6f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.0f, 0.5f);
		if (forced) {
			// v0.13.15: the unwilling change -- he drops to his knees fighting it; the roar comes when he stands (tickForcedChange)
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 1.2f, 0.5f);
			level.sendParticles(DEEP_GREEN, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.4, 0.7, 0.4, 0.02);
		} else {
			roar(level, player);
		}
		player.displayClientMessage(Component.translatable(forced ? "message.projecthero.hulk.transform_forced"
				: "message.projecthero.hulk.transform").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
	}

	private static void revertFx(ServerPlayer player, boolean exhaust) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 1.0f, 0.6f);
		level.sendParticles(DEEP_GREEN, c.x, c.y, c.z, 30, 0.5, 0.8, 0.5, 0.02);
		if (exhaust) {
			player.displayClientMessage(Component.translatable("message.projecthero.hulk.reverted")
					.withStyle(ChatFormatting.GRAY), true);
		}
	}

	// ---------------------------------------------------------------- lifecycle

	/**
	 * Join (v0.13.12, Phase 5): logging out ends the Hulk -- he comes back as Banner with his rage kept (so a player at
	 * 50+ can press H straight away). A relog cannot restore a Hulk's health above Banner's 20 anyway: the transient
	 * max-health bonus is gone by the time the saved health is read.
	 */
	public static void onPlayerJoin(ServerPlayer player) {
		HulkState s = state(player);
		if (!s.hasPower) {
			return;
		}
		com.projecthero.mod.hulk.gladiator.GladiatorAbilities.onJoin(player); // v0.15.3: no weapon is away after a relog
		HulkState n = s.copy();
		n.hulk = false;
		n.combat.unwilling = false;
		n.combat.strainStart = 0L;
		// game time is per world: nothing carried over from another world may lock the player out
		n.exhaustedUntil = 0L;
		n.formChangedAt = 0L;
		n.lastCombatAt = 0L;
		n.combat.lastHurtAt = 0L;
		n.leapChargeStart = 0L;
		n.leaping = false;
		n.animId = HulkState.ANIM_NONE;
		n.abilityReadyAt.entrySet().removeIf(e -> e.getValue() > player.level().getGameTime() + 20L * 60L);
		save(player, n);
		reconcile(player);
		PowerToggles.clearModifier(player, Attributes.SCALE, SCALE_ID);
	}

	/** Respawn: a fresh, calm Banner -- no rage, no Hulk, full size back to normal at once. */
	public static void onPlayerRespawn(ServerPlayer player) {
		HulkState s = state(player);
		if (!s.hasPower) {
			return;
		}
		HulkState n = s.copy();
		n.hulk = false;
		n.combat.unwilling = false;
		n.combat.strainStart = 0L;
		n.rage = 0.0f;
		n.exhaustedUntil = 0L;
		n.formChangedAt = 0L;
		n.leapChargeStart = 0L;
		n.leaping = false;
		n.animId = HulkState.ANIM_NONE;
		n.abilityReadyAt.clear();
		save(player, n);
		reconcile(player);
		PowerToggles.clearModifier(player, Attributes.SCALE, SCALE_ID);
	}

	/** Death / logout / dimension change: nothing transient to drop beyond the message throttle. */
	public static void clearTransient(ServerPlayer player) {
		LAST_MESSAGE.remove(player.getUUID());
		HulkAbilities.clear(player.getUUID());
		HulkGrab.release(player);
		com.projecthero.mod.hulk.gladiator.GladiatorAbilities.clear(player); // v0.15.3
		HulkCalm.clear(player.getUUID());
		HulkControl.clear(player.getUUID());
		ejectRider(player);
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		if (s != null && (s.leapChargeStart != 0L || s.leaping || s.animId != HulkState.ANIM_NONE || s.combat.chargeUntil != 0L
				|| s.combat.smashChargeStart != 0L || s.combat.calming || s.combat.rampageUntil != 0L || s.combat.holding
				|| s.combat.strainStart != 0L)) {
			HulkState n = s.copy();
			n.leapChargeStart = 0L;
			n.leaping = false;
			n.animId = HulkState.ANIM_NONE;
			n.combat.chargeUntil = 0L;
			n.combat.smashChargeStart = 0L;
			n.combat.calming = false;
			n.combat.rampageUntil = 0L;
			n.combat.holding = false;
			n.combat.strainStart = 0L;
			n.combat.promptKey = 0;
			n.combat.control = 100.0f;
			save(player, n);
		}
	}

	// ---------------------------------------------------------------- the death save

	/** Client-safe: would "the Hulk refuses to die" save him right now? v0.13.17: always, as Banner -- never as the Hulk. */
	public static boolean deathSaveReady(Player player) {
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		return s != null && s.hasPower && !s.hulk;
	}

	/**
	 * v0.13.14: a Gamma player who would die is not allowed to -- the Hulk comes out at full health with a full rage bar
	 * (the unwilling change). v0.13.17: no cooldown -- Banner can never be killed; to kill a Gamma player you have to beat
	 * the Hulk, so a Hulk who would die dies. /kill and the void still kill, and so does anything that stops the Hulk
	 * coming out (inside a Titan, All Might's Power Form). Returns true if the death was prevented (the caller cancels it).
	 */
	public static boolean tryDeathSave(ServerPlayer player, net.minecraft.world.damagesource.DamageSource source) {
		HulkState s = state(player);
		if (!s.hasPower || s.hulk || player.isSpectator() || player.getAbilities().invulnerable
				|| com.projecthero.mod.titanshifter.TitanShifter.phase(player).insideForm()
				|| com.projecthero.mod.allmight.AllMight.isFullPower(player)
				|| source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)
				|| source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)) {
			return false;
		}
		HulkState n = s.copy();
		n.rage = HulkConfig.RAGE_MAX;
		n.exhaustedUntil = 0L;
		n.combat.calming = false;
		save(player, n);
		player.setHealth(1.0f);
		transform(player, true);
		player.setHealth(player.getMaxHealth());
		player.clearFire();
		player.invulnerableTime = 40;
		player.removeEffect(MobEffects.WITHER);
		player.removeEffect(MobEffects.POISON);
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 3.0f, 0.5f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8f, 0.7f);
		level.sendParticles(GAMMA_GREEN, player.getX(), player.getY() + 1.0, player.getZ(), 80, 0.8, 1.2, 0.8, 0.1);
		HulkAbilities.shockwave(player, player.position(), 5.0, 8.0f, 1.4, 0.5, 0.7f);
		player.displayClientMessage(Component.translatable("message.projecthero.hulk.death_save")
				.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
		return true;
	}
}
