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
 * {@link AllMightConfig}.
 *
 * <h2>Two forms (v0.12.34)</h2>
 * <b>Base Form</b> is a plain player -- no bonuses, no passives, no abilities. <b>H</b> switches to the <b>Power Form</b>: he grows to
 * 2.7 blocks over a second (steaming), gets 13 melee, 40 max HP (the health percentage carries over), 50% less damage, Speed III,
 * Regeneration I and a 3-block jump, and the six abilities unlock. <b>C</b> (Plus Ultra) is a drain-while-on toggle inside the Power
 * Form. The stats are recomputed from {@code (fullPower)} by {@link #reconcile}, which only ever sets fixed-id transient attribute
 * modifiers -- so pressing H any number of times cannot stack anything.
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

	private AllMight() {
	}

	public static void clearSessionState() {
		PEAK_FALL.clear();
		LAST_MESSAGE.clear();
		AllMightAbilities.clearSessionState();
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

	public static float ofa(Player player) {
		AllMightState s = player.getAttachedOrElse(ModAttachments.ALL_MIGHT_STATE, null);
		return s == null ? 0f : s.ofa;
	}

	public static int cooldownRemaining(Player player, String abilityId) {
		AllMightState s = player.getAttachedOrElse(ModAttachments.ALL_MIGHT_STATE, null);
		if (s == null) {
			return 0;
		}
		Long ready = s.abilityReadyAt.get(abilityId);
		return ready == null ? 0 : (int) Math.max(0L, ready - player.level().getGameTime());
	}

	/** Ability damage multiplier: Plus Ultra makes every move 30% stronger. */
	public static float smashMultiplier(ServerPlayer player) {
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

	// ---------------------------------------------------------------- OFA

	/** Spends {@code cost} OFA if the player has it. Never lets the resource go negative. */
	public static boolean spendOfa(ServerPlayer player, float cost) {
		AllMightState s = state(player);
		if (s.ofa + 1.0e-3f < cost) {
			return false;
		}
		AllMightState n = s.copy();
		n.ofa = Math.max(0f, s.ofa - cost);
		save(player, n);
		return true;
	}

	public static void addOfa(ServerPlayer player, float amount) {
		AllMightState s = state(player);
		AllMightState n = s.copy();
		n.ofa = Math.max(0f, Math.min(AllMightConfig.OFA_MAX, s.ofa + amount));
		save(player, n);
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

	// ---------------------------------------------------------------- grant / revoke

	/** Use the Vestige / command. Returns false if the player already has the power. */
	public static boolean grant(ServerPlayer player) {
		if (state(player).hasPower) {
			return false;
		}
		HeroTiers.claimPrimary(player, KEY);
		long now = player.level().getGameTime();
		AllMightState s = new AllMightState();
		s.hasPower = true;
		s.fullPower = false;
		s.ofa = AllMightConfig.OFA_MAX;
		s.formChangedAt = now - AllMightConfig.FORM_TOGGLE_DEBOUNCE_TICKS; // H works straight away
		save(player, s);
		reconcile(player);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.4f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8f, 1.6f);
		AllMightShockwave.burst(level, ParticleTypes.ELECTRIC_SPARK, c, 50, 0.6, 0.35);
		AllMightShockwave.burst(level, new DustParticleOptions(new Vector3f(0.3f, 1.0f, 0.45f), 1.4f), c, 25, 0.7, 0.05);
		player.displayClientMessage(Component.translatable("message.projecthero.all_might.acquired")
				.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.all_might.acquired_hint")
				.withStyle(ChatFormatting.YELLOW), false);
		return true;
	}

	public static void revoke(ServerPlayer player) {
		AllMightAbilities.clear(player.getUUID());
		PEAK_FALL.remove(player.getUUID());
		AllMightState s = new AllMightState(); // hasPower=false
		save(player, s);
		reconcile(player);
		AllMightSuit.returnAll(player);
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
			clearOurEffect(player, MobEffects.REGENERATION, AllMightConfig.FULL_REGEN_AMPLIFIER);
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
		keepEffect(player, MobEffects.REGENERATION, AllMightConfig.FULL_REGEN_AMPLIFIER);
	}

	/** A short hidden effect that is topped up while it is running low, so it never clobbers another power's infinite one and lapses on its own. */
	private static void keepEffect(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int amplifier) {
		MobEffectInstance cur = player.getEffect(effect);
		if (cur != null && (cur.isInfiniteDuration() || cur.getAmplifier() > amplifier)) {
			return; // somebody else's stronger / permanent one
		}
		if (cur == null || cur.getAmplifier() < amplifier || cur.getDuration() <= 40) {
			player.addEffect(new MobEffectInstance(effect, 100, amplifier, false, false, false));
		}
	}

	private static void clearOurEffect(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int amplifier) {
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
		if (!s.hasPower || !player.isAlive() || player.isSpectator()) {
			return;
		}
		long now = player.level().getGameTime();
		if (now - s.formChangedAt < AllMightConfig.FORM_TOGGLE_DEBOUNCE_TICKS || now < s.transformUntil) {
			return;
		}
		changeForm(player, !s.fullPower);
	}

	/** Switches form: the health percentage carries over, the body grows / shrinks over a second and steam pours off. */
	private static void changeForm(ServerPlayer player, boolean toFull) {
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
			n.abilityReadyAt.put(AllMightAbilities.PLUS_ULTRA, now + AllMightConfig.PLUS_ULTRA_COOLDOWN_TICKS);
		}
		save(player, n);
		reconcile(player);
		if (toFull) {
			AllMightSuit.equip(player); // whatever is in the costume locker (N) goes on
		} else {
			AllMightSuit.strip(player);
		}
		// 20 HP at 100% is 40 HP at 100%; 10 of 20 becomes 20 of 40 (and back)
		player.setHealth(Math.max(0.5f, Math.min(player.getMaxHealth(), ratio * player.getMaxHealth())));
		transformFx(player, toFull);
	}

	/** Runs out of One For All while Plus Ultra is on: the power gives out and he drops back to the Base Form. */
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
			AllMightShockwave.burst(level, new DustParticleOptions(new Vector3f(0.3f, 1.0f, 0.45f), 1.5f), c, 40, 0.8, 0.05);
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
		// the once-a-second safety net (a respawn or another mod may have cleared the transient modifiers)
		if (player.tickCount % 20 == 0) {
			reconcile(player);
		}
		if (s.fullPower && s.plusUltra) {
			tickPlusUltra(player, s, now);
			s = state(player);
			if (!s.fullPower) {
				return; // ran dry: he is back in the Base Form
			}
		}

		tickOfa(player, s, now);
		AllMightAbilities.tick(player);
		tickLanding(player, s, now);
		tickAura(player, s, now);
	}

	/** Plus Ultra costs OFA continuously; at zero it gives out and he drops back to the Base Form. */
	private static void tickPlusUltra(ServerPlayer player, AllMightState s, long now) {
		if (now % AllMightConfig.PLUS_ULTRA_DRAIN_INTERVAL_TICKS != 0L) {
			return;
		}
		AllMightState n = s.copy();
		n.ofa = Math.max(0f, s.ofa - AllMightConfig.PLUS_ULTRA_DRAIN_AMOUNT);
		save(player, n);
		if (n.ofa <= 0f) {
			exhaust(player);
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

	private static void tickOfa(ServerPlayer player, AllMightState s, long now) {
		if (s.ofa >= AllMightConfig.OFA_MAX || s.plusUltra) {
			return;
		}
		boolean calm = now - s.lastCombatTick >= AllMightConfig.OFA_COMBAT_LOCKOUT_TICKS;
		int interval = calm ? AllMightConfig.OFA_REGEN_INTERVAL_OUT_OF_COMBAT_TICKS : AllMightConfig.OFA_REGEN_INTERVAL_TICKS;
		if (now % interval == 0L) {
			addOfa(player, AllMightConfig.OFA_REGEN_AMOUNT);
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
				AllMightState n = s.copy();
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
		if (s.plusUltra && now % 2L == 0L) {
			Vec3 c = player.position().add(0, player.getBbHeight() * 0.5, 0);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, AllMightShockwave.particles(3), 0.4, player.getBbHeight() * 0.4, 0.4, 0.1);
			if (now % 4L == 0L) {
				level.sendParticles(new DustParticleOptions(new Vector3f(0.3f, 1.0f, 0.45f), 1.1f), c.x, c.y, c.z,
						AllMightShockwave.particles(3), 0.45, player.getBbHeight() * 0.42, 0.45, 0.0);
			}
		}
		// running out of One For All: he vents steam to show it
		if (s.ofa < AllMightConfig.LOW_OFA_STEAM && now % 3L == 0L) {
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
		n.busyUntil = 0L;
		n.transformUntil = 0L;
		n.noFallUntil = 0L;
		n.animId = AllMightState.ANIM_NONE;
		n.ofa = AllMightConfig.OFA_MAX;
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
