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
 * <h2>Two separate systems</h2>
 * <b>H</b> toggles the persistent {@code fullPower} form. <b>C</b> (Full Cowl) is a temporary buff that expires on
 * its own. They never share state: the stats are recomputed from {@code (fullPower, cowlActive)} by {@link #reconcile},
 * which only ever sets fixed-id transient attribute modifiers -- so pressing either key any number of times cannot
 * stack anything.
 */
public final class AllMight {
	public static final String KEY = "all_might";

	private static final ResourceLocation HEALTH_ID = PowerToggles.id("all_might_health");
	private static final ResourceLocation ATTACK_ID = PowerToggles.id("all_might_attack");
	private static final ResourceLocation ATTACK_COWL_ID = PowerToggles.id("all_might_attack_cowl");
	private static final ResourceLocation SPEED_ID = PowerToggles.id("all_might_speed");
	private static final ResourceLocation KNOCKBACK_ID = PowerToggles.id("all_might_knockback");
	private static final ResourceLocation JUMP_ID = PowerToggles.id("all_might_jump");

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

	public static boolean cowlActive(Player player) {
		AllMightState s = player.getAttachedOrElse(ModAttachments.ALL_MIGHT_STATE, null);
		return s != null && s.hasPower && s.cowlUntil > player.level().getGameTime();
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

	/** Smash damage multiplier: the full-power form and an active Full Cowl each add a little. */
	public static float smashMultiplier(ServerPlayer player) {
		float m = 1.0f;
		if (isFullPower(player)) {
			m *= AllMightConfig.FULL_SMASH_MULTIPLIER;
		}
		if (cowlActive(player)) {
			m *= AllMightConfig.COWL_SMASH_MULTIPLIER;
		}
		return m;
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
		AllMightSuit.equip(player);
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
		AllMightSuit.strip(player);
		AllMightSuit.deleteLoose(player);
	}

	// ---------------------------------------------------------------- stats

	/** Jump HEIGHT multiplier to the jump-VELOCITY modifier (height ~ velocity squared). */
	static double jumpVelocityModifier(double heightMultiplier) {
		return Math.sqrt(Math.max(1.0, heightMultiplier)) - 1.0;
	}

	/**
	 * Brings every attribute modifier in line with {@code (hasPower, fullPower, cowl)}. Idempotent -- fixed ids, each branch
	 * checks before it writes -- so it is safe to call as often as you like (H spam, the once-a-second audit, join, respawn).
	 */
	public static void reconcile(ServerPlayer player) {
		AllMightState s = state(player);
		if (!s.hasPower) {
			PowerToggles.clearModifier(player, Attributes.MAX_HEALTH, HEALTH_ID);
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID);
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_COWL_ID);
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
			PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID);
			PowerToggles.clearModifier(player, Attributes.JUMP_STRENGTH, JUMP_ID);
			if (player.getHealth() > player.getMaxHealth()) {
				player.setHealth(player.getMaxHealth());
			}
			return;
		}
		boolean full = s.fullPower;
		boolean cowl = s.cowlUntil > player.level().getGameTime();
		PowerToggles.modifier(player, Attributes.MAX_HEALTH, HEALTH_ID,
				full ? AllMightConfig.FULL_HEALTH_BONUS : AllMightConfig.BASE_HEALTH_BONUS, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID,
				full ? AllMightConfig.FULL_ATTACK_BONUS : AllMightConfig.BASE_ATTACK_BONUS, AttributeModifier.Operation.ADD_VALUE);
		if (cowl) {
			PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, ATTACK_COWL_ID, AllMightConfig.COWL_ATTACK_MULTIPLIER,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		} else {
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_COWL_ID);
		}
		double formSpeed = full ? AllMightConfig.FULL_SPEED_BONUS : AllMightConfig.BASE_SPEED_BONUS;
		PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID,
				cowl ? Math.max(formSpeed, AllMightConfig.COWL_SPEED_BONUS) : formSpeed, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		double formKb = full ? AllMightConfig.FULL_KNOCKBACK_RESISTANCE : AllMightConfig.BASE_KNOCKBACK_RESISTANCE;
		PowerToggles.modifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID,
				cowl ? Math.max(formKb, AllMightConfig.COWL_KNOCKBACK_RESISTANCE) : formKb, AttributeModifier.Operation.ADD_VALUE);
		double height = (full ? AllMightConfig.FULL_JUMP_HEIGHT : AllMightConfig.BASE_JUMP_HEIGHT)
				+ (cowl ? AllMightConfig.COWL_JUMP_HEIGHT_BONUS : 0.0);
		PowerToggles.modifier(player, Attributes.JUMP_STRENGTH, JUMP_ID, jumpVelocityModifier(height),
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	/** The fall-damage reduction of the player's current form. */
	public static float fallReduction(ServerPlayer player) {
		return isFullPower(player) ? AllMightConfig.FULL_FALL_REDUCTION : AllMightConfig.BASE_FALL_REDUCTION;
	}

	/** The damage-taken factor (1 - reduction) of the current form and Full Cowl, multiplied together. */
	public static float damageTakenFactor(ServerPlayer player) {
		float f = 1.0f - (isFullPower(player) ? AllMightConfig.FULL_DAMAGE_REDUCTION : AllMightConfig.BASE_DAMAGE_REDUCTION);
		if (cowlActive(player)) {
			f *= 1.0f - AllMightConfig.COWL_DAMAGE_REDUCTION;
		}
		return f;
	}

	// ---------------------------------------------------------------- H: the form

	/** The H key: toggle between the contained form and full-power All Might. Server-validated; safe to spam. */
	public static void toggleForm(ServerPlayer player) {
		AllMightState s = state(player);
		if (!s.hasPower || !player.isAlive() || player.isSpectator()) {
			return;
		}
		long now = player.level().getGameTime();
		if (now - s.formChangedAt < AllMightConfig.FORM_TOGGLE_DEBOUNCE_TICKS || now < s.transformUntil) {
			return;
		}
		boolean toFull = !s.fullPower;
		AllMightState n = s.copy();
		n.fullPower = toFull;
		n.formChangedAt = now;
		int lock = toFull ? AllMightConfig.TRANSFORM_TICKS : AllMightConfig.DETRANSFORM_TICKS;
		n.busyUntil = now + lock;
		if (toFull) {
			n.transformUntil = now + AllMightConfig.TRANSFORM_TICKS; // damage-proof while the form swells
		}
		n.animId = toFull ? AllMightState.ANIM_TRANSFORM_UP : AllMightState.ANIM_TRANSFORM_DOWN;
		n.animStart = now;
		save(player, n);
		reconcile(player);
		transformFx(player, toFull);
	}

	private static void transformFx(ServerPlayer player, boolean toFull) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		if (toFull) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.4f, 0.6f);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.1f, 1.3f);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.7f, 1.4f);
			AllMightShockwave.burst(level, ParticleTypes.EXPLOSION, c, 3, 0.5, 0.0);
			AllMightShockwave.burst(level, ParticleTypes.ELECTRIC_SPARK, c, 70, 0.7, 0.4);
			AllMightShockwave.burst(level, new DustParticleOptions(new Vector3f(0.3f, 1.0f, 0.45f), 1.5f), c, 40, 0.8, 0.05);
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
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 1.0f);
			AllMightShockwave.burst(level, ParticleTypes.CLOUD, c, 20, 0.6, 0.05);
			AllMightShockwave.burst(level, ParticleTypes.ELECTRIC_SPARK, c, 15, 0.5, 0.15);
			player.displayClientMessage(Component.translatable("message.projecthero.all_might.contained")
					.withStyle(ChatFormatting.GRAY), true);
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

		// Full Cowl runs out on its own
		if (s.cowlUntil != 0L && now >= s.cowlUntil) {
			AllMightState n = s.copy();
			n.cowlUntil = 0L;
			save(player, n);
			reconcile(player);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6f, 1.6f);
			AllMightShockwave.burst(level, ParticleTypes.ELECTRIC_SPARK, player.position().add(0, 1.0, 0), 12, 0.4, 0.1);
			s = n;
		}
		// the once-a-second safety net (a respawn or another mod may have cleared the transient modifiers)
		if (player.tickCount % 20 == 0) {
			reconcile(player);
			AllMightSuit.reequipMissing(player);
			AllMightSuit.deleteLoose(player);
		}

		tickOfa(player, s, now);
		AllMightAbilities.tick(player);
		tickLanding(player, s, now);
		tickAura(player, s, now);
	}

	private static void tickOfa(ServerPlayer player, AllMightState s, long now) {
		if (s.ofa >= AllMightConfig.OFA_MAX) {
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
		if (s.cowlUntil > now && now % 2L == 0L) {
			Vec3 c = player.position().add(0, 1.0, 0);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, AllMightShockwave.particles(3), 0.4, 0.8, 0.4, 0.1);
			if (now % 4L == 0L) {
				level.sendParticles(new DustParticleOptions(new Vector3f(0.3f, 1.0f, 0.45f), 1.1f), c.x, c.y, c.z,
						AllMightShockwave.particles(3), 0.45, 0.85, 0.45, 0.0);
			}
		}
		if (s.fullPower && now % 6L == 0L && player.getDeltaMovement().horizontalDistanceSqr() > 0.06) {
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
		n.animId = AllMightState.ANIM_NONE;
		if (n.cowlUntil > now + AllMightConfig.COWL_DURATION_TICKS) {
			n.cowlUntil = 0L;
		}
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
		n.cowlUntil = 0L;
		n.busyUntil = 0L;
		n.transformUntil = 0L;
		n.noFallUntil = 0L;
		n.animId = AllMightState.ANIM_NONE;
		n.ofa = AllMightConfig.OFA_MAX;
		n.abilityReadyAt.clear();
		save(player, n);
		reconcile(player);
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
			n.animId = AllMightState.ANIM_NONE;
			save(player, n);
		}
	}

	/** Called before vanilla drops a dying player's equipment: the conjured costume never drops. */
	public static void onDeath(ServerPlayer player) {
		clearTransient(player);
		if (state(player).hasPower) {
			AllMightSuit.strip(player);
		}
	}
}
