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
 * <h2>Output and Strain (v0.16.6)</h2>
 * User: "Lets try the new output limit". The 5-minute OFA timer is gone. One For All runs at an <b>Output</b> level -- 20%, 45%,
 * 100% -- that landed hits charge ({@link #onHitLanded}) and that drops a level after 8 s without a hit; higher Output means
 * harder ({@link #smashMultiplier}) and wider ({@link #sizeScale}) moves. The limit is <b>Strain</b>: time at 45% and especially
 * 100% builds it ({@link #tickOutputAndStrain}); at full Strain his arms give out ({@link #recoil}).
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
	/** v0.16.6: game time a plain punch last charged Output (punch spam charges once per {@code OUTPUT_PUNCH_SPACING_TICKS}). */
	private static final Map<UUID, Long> LAST_PUNCH_CHARGE = new HashMap<>();

	private AllMight() {
	}

	public static void clearSessionState() {
		PEAK_FALL.clear();
		LAST_MESSAGE.clear();
		LAST_PUNCH_CHARGE.clear();
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

	/** v0.16.6: the Output level (0 = 20%, 1 = 45%, 2 = 100%); 0 outside the Power Form. */
	public static int output(Player player) {
		AllMightState s = player.getAttachedOrElse(ModAttachments.ALL_MIGHT_STATE, null);
		return s == null || !s.hasPower || !s.fullPower ? AllMightState.OUTPUT_20 : s.output;
	}

	public static int outputPercent(Player player) {
		return AllMightConfig.OUTPUT_PERCENT[output(player)];
	}

	public static float strain(Player player) {
		AllMightState s = player.getAttachedOrElse(ModAttachments.ALL_MIGHT_STATE, null);
		return s == null ? 0f : s.strain;
	}

	/** v0.16.6: his arms have given out -- every Smash is locked. */
	public static boolean inRecoil(Player player) {
		AllMightState s = player.getAttachedOrElse(ModAttachments.ALL_MIGHT_STATE, null);
		return s != null && s.hasPower && s.recoilUntil > player.level().getGameTime();
	}

	/** v0.16.6: holds a borrowed One For All (capped at 45%, double Strain, cannot pass it on). */
	public static boolean isBorrower(Player player) {
		AllMightState s = player.getAttachedOrElse(ModAttachments.ALL_MIGHT_STATE, null);
		return s != null && s.hasPower && s.loanRole == AllMightState.LOAN_BORROWER;
	}

	/** v0.16.6: lent their One For All out (holds no power until it comes back). */
	public static boolean isLender(Player player) {
		AllMightState s = player.getAttachedOrElse(ModAttachments.ALL_MIGHT_STATE, null);
		return s != null && !s.hasPower && s.loanRole == AllMightState.LOAN_LENDER;
	}

	/** The highest Output this holder may reach: 100%, or 45% on a borrowed One For All. */
	public static int outputCap(AllMightState s) {
		return s.loanRole == AllMightState.LOAN_BORROWER ? AllMightConfig.BORROWED_OUTPUT_CAP : AllMightState.OUTPUT_100;
	}

	public static int cooldownRemaining(Player player, String abilityId) {
		AllMightState s = player.getAttachedOrElse(ModAttachments.ALL_MIGHT_STATE, null);
		if (s == null) {
			return 0;
		}
		Long ready = s.abilityReadyAt.get(abilityId);
		return ready == null ? 0 : (int) Math.max(0L, ready - player.level().getGameTime());
	}

	/** Ability damage multiplier (v0.16.6): the Output level -- x1.0 at 20%, x1.25 at 45%, x1.6 at 100%. */
	public static float smashMultiplier(ServerPlayer player) {
		return AllMightConfig.OUTPUT_DAMAGE[output(player)];
	}

	/** v0.16.6: shockwave width / radius multiplier of the Output level (x1.0 / x1.2 / x1.4). */
	public static double sizeScale(ServerPlayer player) {
		return AllMightConfig.OUTPUT_SIZE[output(player)];
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

	// ---------------------------------------------------------------- v0.16.6: Output

	/**
	 * A hit landed (one call per Smash however many it struck, or per plain punch): resets the decay clock and charges toward the
	 * next Output level -- {@code OUTPUT_HITS_TO_45} hits from 20%, {@code OUTPUT_HITS_TO_100} from 45%. Nothing charges in the
	 * recoil, during Plus Ultra (it is already at 100%) or past a borrower's 45% cap.
	 *
	 * @param punch a plain melee punch (throttled to one charge per {@code OUTPUT_PUNCH_SPACING_TICKS})
	 */
	public static void onHitLanded(ServerPlayer player, boolean punch) {
		AllMightState s = state(player);
		if (!s.hasPower || !s.fullPower) {
			return;
		}
		long now = player.level().getGameTime();
		if (punch) {
			Long last = LAST_PUNCH_CHARGE.get(player.getUUID());
			if (last != null && now - last < AllMightConfig.OUTPUT_PUNCH_SPACING_TICKS && now >= last) {
				return;
			}
			LAST_PUNCH_CHARGE.put(player.getUUID(), now);
		}
		AllMightState n = s.copy();
		n.lastHitAt = now;
		boolean levelled = false;
		if (!s.plusUltra && s.recoilUntil <= now && s.output < outputCap(s)) {
			int need = s.output == AllMightState.OUTPUT_20 ? AllMightConfig.OUTPUT_HITS_TO_45 : AllMightConfig.OUTPUT_HITS_TO_100;
			n.outputProgress = s.outputProgress + 1.0f / Math.max(1, need);
			if (n.outputProgress >= 0.999f) {
				n.output = s.output + 1;
				n.outputProgress = 0f;
				levelled = true;
			}
		} else if (s.output >= outputCap(s)) {
			n.outputProgress = 0f;
		}
		save(player, n);
		if (levelled) {
			outputFx(player, n.output, true);
		}
	}

	/** Sets the Output level directly (Plus Ultra, tests, the recoil). Clamped to the holder's cap. */
	public static void setOutput(ServerPlayer player, int level) {
		AllMightState s = state(player);
		AllMightState n = s.copy();
		n.output = Math.max(AllMightState.OUTPUT_20, Math.min(outputCap(s), level));
		n.outputProgress = 0f;
		n.lastHitAt = player.level().getGameTime();
		save(player, n);
	}

	/** Adds (or removes) Strain, scaled for a borrower; reaching the top triggers the recoil. */
	public static void addStrain(ServerPlayer player, float amount) {
		AllMightState s = state(player);
		if (!s.hasPower) {
			return;
		}
		float scaled = amount > 0f && s.loanRole == AllMightState.LOAN_BORROWER ? amount * AllMightConfig.BORROWED_STRAIN_MULTIPLIER : amount;
		AllMightState n = s.copy();
		n.strain = Math.max(0f, Math.min(AllMightConfig.STRAIN_MAX, s.strain + scaled));
		save(player, n);
		if (n.strain >= AllMightConfig.STRAIN_MAX - 1.0e-3f && s.recoilUntil <= player.level().getGameTime()) {
			recoil(player);
		}
	}

	/**
	 * Full Strain: his arms give out. Forced back to 20%, Plus Ultra ends, every Smash is locked for {@code RECOIL_TICKS}, Weakness
	 * II + Mining Fatigue for as long, and the client hangs his arms limp ({@code AllMightPose}). A borrower's loan ends early.
	 */
	public static void recoil(ServerPlayer player) {
		AllMightState s = state(player);
		if (!s.hasPower) {
			return;
		}
		long now = player.level().getGameTime();
		AllMightAbilities.cancelCharge(player, false);
		AllMightState n = s.copy();
		n.output = AllMightState.OUTPUT_20;
		n.outputProgress = 0f;
		n.strain = AllMightConfig.STRAIN_MAX;
		n.recoilUntil = now + AllMightConfig.RECOIL_TICKS;
		n.animId = AllMightState.ANIM_NONE;
		n.busyUntil = now;
		if (s.plusUltra) {
			n.plusUltra = false;
			n.plusUltraUntil = 0L;
			n.abilityReadyAt.put(AllMightAbilities.PLUS_ULTRA, now + AllMightConfig.PLUS_ULTRA_COOLDOWN_TICKS);
		}
		save(player, n);
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, AllMightConfig.RECOIL_TICKS, AllMightConfig.RECOIL_WEAKNESS_AMPLIFIER, false, true, true));
		player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, AllMightConfig.RECOIL_TICKS, AllMightConfig.RECOIL_FATIGUE_AMPLIFIER, false, true, true));
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 1.2f, 0.5f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.2f, 0.6f);
		steam(level, player, 12);
		player.displayClientMessage(Component.translatable("message.projecthero.all_might.recoil").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
		if (s.loanRole == AllMightState.LOAN_BORROWER) {
			AllMightPassOn.endLoanFromBorrower(player, AllMightPassOn.EndReason.STRAIN);
		}
	}

	private static void outputFx(ServerPlayer player, int level, boolean up) {
		ServerLevel lvl = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, player.getBbHeight() * 0.55, 0);
		if (up) {
			lvl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8f,
					level >= AllMightState.OUTPUT_100 ? 1.6f : 1.2f);
			AllMightShockwave.burst(lvl, ParticleTypes.ELECTRIC_SPARK, c, level >= AllMightState.OUTPUT_100 ? 40 : 20, 0.6, 0.3);
			AllMightShockwave.burst(lvl, new DustParticleOptions(new Vector3f(1.0f, 0.86f, 0.45f), 1.3f), c, 18, 0.6, 0.05);
		} else {
			lvl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.5f, 1.5f);
		}
		player.displayClientMessage(Component.translatable("message.projecthero.all_might.output", AllMightConfig.OUTPUT_PERCENT[level])
				.withStyle(level >= AllMightState.OUTPUT_100 ? ChatFormatting.GOLD : ChatFormatting.YELLOW, ChatFormatting.BOLD), true);
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

	/** A brand-new holder's state: Base Form, 20%, no Strain, H ready. */
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
		clearRecoilEffects(player, old);
		// v0.16.6: a borrower losing the power (time, death, logout, a new Primary power...) sends it home to the lender
		if (old.loanRole == AllMightState.LOAN_BORROWER) {
			AllMightPassOn.returnToLender(player, old);
		} else if (old.loanRole == AllMightState.LOAN_LENDER) {
			AllMightPassOn.clearLenderEffects(player);
		}
	}

	private static void clearRecoilEffects(ServerPlayer player, AllMightState old) {
		if (old.recoilUntil > player.level().getGameTime()) {
			player.removeEffect(MobEffects.WEAKNESS);
			player.removeEffect(MobEffects.DIG_SLOWDOWN);
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
		// v0.16.6: every change of form starts again at 20% Output
		n.output = AllMightState.OUTPUT_20;
		n.outputProgress = 0f;
		if (!toFull && s.plusUltra) {
			n.plusUltra = false;
			n.plusUltraUntil = 0L;
			n.strain = Math.max(s.strain, AllMightConfig.PLUS_ULTRA_AFTER_STRAIN);
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

	/** v0.16.6: steam venting off both arms (shoulder to fist) at 100% Output and under heavy Strain. */
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
		tickOutputAndStrain(player, now);
		s = state(player);
		if (!s.hasPower) {
			return; // a borrower's loan ended on full Strain
		}
		tickPowerFormRegen(player, s);
		AllMightAbilities.tick(player);
		tickLanding(player, s, now);
		tickAura(player, s, now);
	}

	/** v0.16.6: Plus Ultra is a fixed 22 s of 100%; when it runs out Strain is set to 75 and the cooldown starts. */
	private static void endPlusUltra(ServerPlayer player, AllMightState s, long now) {
		AllMightState n = s.copy();
		n.plusUltra = false;
		n.plusUltraUntil = 0L;
		n.strain = Math.max(s.strain, AllMightConfig.PLUS_ULTRA_AFTER_STRAIN);
		n.lastHitAt = now; // the 8 s decay clock starts now
		n.abilityReadyAt.put(AllMightAbilities.PLUS_ULTRA, now + AllMightConfig.PLUS_ULTRA_COOLDOWN_TICKS);
		save(player, n);
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8f, 1.6f);
		steam(level, player, 6);
		player.displayClientMessage(Component.translatable("message.projecthero.all_might.plus_ultra_off").withStyle(ChatFormatting.GRAY), true);
	}

	/**
	 * v0.16.6: the Output decay and the Strain meter. Output drops a level (progress reset) after {@code OUTPUT_DECAY_TICKS} without a
	 * hit (never during Plus Ultra). Every {@code STRAIN_STEP_TICKS}: Strain builds at 45% / 100% (not during Plus Ultra), and
	 * recovers at 20% or in the Base Form. Full Strain is the recoil.
	 */
	private static void tickOutputAndStrain(ServerPlayer player, long now) {
		AllMightState s = state(player);
		if (s.fullPower && !s.plusUltra && now - s.lastHitAt >= AllMightConfig.OUTPUT_DECAY_TICKS
				&& (s.output > AllMightState.OUTPUT_20 || s.outputProgress > 0f)) {
			AllMightState n = s.copy();
			boolean dropped = s.output > AllMightState.OUTPUT_20;
			n.output = Math.max(AllMightState.OUTPUT_20, s.output - 1);
			n.outputProgress = 0f;
			n.lastHitAt = now; // the next level down gets its own 8 s
			save(player, n);
			if (dropped) {
				outputFx(player, n.output, false);
			}
			s = n;
		}
		if (now % AllMightConfig.STRAIN_STEP_TICKS != 0L) {
			return;
		}
		float perSecond;
		if (s.fullPower && !s.plusUltra && s.output == AllMightState.OUTPUT_100) {
			perSecond = AllMightConfig.STRAIN_PER_SECOND_100;
		} else if (s.fullPower && !s.plusUltra && s.output == AllMightState.OUTPUT_45) {
			perSecond = AllMightConfig.STRAIN_PER_SECOND_45;
		} else if (s.plusUltra) {
			perSecond = 0f;
		} else {
			perSecond = -(s.fullPower ? AllMightConfig.STRAIN_RECOVER_POWER_FORM : AllMightConfig.STRAIN_RECOVER_BASE_FORM);
		}
		if (perSecond == 0f || (perSecond < 0f && s.strain <= 0f)) {
			return;
		}
		float step = perSecond * AllMightConfig.STRAIN_STEP_TICKS / 20.0f;
		float before = s.strain;
		addStrain(player, step);
		float after = state(player).strain;
		if (before < AllMightConfig.STRAIN_WARNING && after >= AllMightConfig.STRAIN_WARNING && after < AllMightConfig.STRAIN_MAX) {
			player.displayClientMessage(Component.translatable("message.projecthero.all_might.strain_warning").withStyle(ChatFormatting.RED), true);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 1.4f);
		}
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
		radius *= sizeScale(player);
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
		// the recoil: his spent arms smoke
		if (s.recoilUntil > now && now % 6L == 0L) {
			armSteam(level, player, 1);
		}
		if (!s.fullPower) {
			return;
		}
		// v0.16.6: 100% Output -- the arms vent steam and gold sparks crackle off him (the glow and the arcs are drawn on the model
		// client-side by AllMightGlowLayer; these are the particles that go with it)
		if (s.output == AllMightState.OUTPUT_100 && now % 3L == 0L) {
			armSteam(level, player, 1);
			Vec3 c = player.position().add(0, player.getBbHeight() * 0.6, 0);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, AllMightShockwave.particles(2), 0.45, player.getBbHeight() * 0.3, 0.45, 0.08);
			if (now % 6L == 0L) {
				level.sendParticles(new DustParticleOptions(new Vector3f(1.0f, 0.82f, 0.3f), 1.0f), c.x, c.y, c.z,
						AllMightShockwave.particles(3), 0.45, player.getBbHeight() * 0.35, 0.45, 0.0);
			}
		}
		// heavy Strain: he vents steam to show it
		if (s.strain >= AllMightConfig.STRAIN_WARNING && now % 4L == 0L) {
			steam(level, player, 1);
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
		// v0.16.6: a pre-0.16.6 save may still have the old OFA Plus Ultra running; Output starts again at 20%
		n.plusUltra = false;
		n.plusUltraUntil = 0L;
		n.output = AllMightState.OUTPUT_20;
		n.outputProgress = 0f;
		if (n.recoilUntil > now + AllMightConfig.RECOIL_TICKS || n.recoilUntil < now) {
			n.recoilUntil = 0L;
		}
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
		n.output = AllMightState.OUTPUT_20;
		n.outputProgress = 0f;
		n.strain = 0f;
		n.recoilUntil = 0L;
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
		LAST_PUNCH_CHARGE.remove(player.getUUID());
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
