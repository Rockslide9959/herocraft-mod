package com.projecthero.mod.supersoldier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.supersoldier.data.SuperSoldierState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import org.joml.Vector3f;

/**
 * The single server-side API for the Super Soldier Hero-Tier power (v0.14.8). Nothing else pokes
 * {@link SuperSoldierState}; every mutator re-saves through {@link ServerPlayer#setAttached}. The moves live in
 * {@link SuperSoldierAbilities}, the incoming / outgoing damage rules in {@link SuperSoldierDamage}, the numbers in
 * {@link SuperSoldierConfig}.
 *
 * <h2>Passives (always on while he has the power)</h2>
 * +50% movement speed, +5 hearts, +7 unarmed melee, 30% less damage taken, a 2.25-block jump with a matching safe
 * fall, 20% knockback resistance, +15% attack speed, out-of-combat healing and immunity to Poison and Nausea. All of
 * it is fixed-id transient attribute modifiers set by {@link #reconcile} every tick, so nothing can stack and revoking
 * the power (or a relog) strips every one of them.
 */
public final class SuperSoldier {
	public static final String KEY = "super_soldier";

	static final ResourceLocation SPEED_ID = PowerToggles.id("super_soldier_speed");
	static final ResourceLocation HEALTH_ID = PowerToggles.id("super_soldier_health");
	static final ResourceLocation UNARMED_ID = PowerToggles.id("super_soldier_unarmed");
	static final ResourceLocation ONSLAUGHT_ID = PowerToggles.id("super_soldier_onslaught");
	static final ResourceLocation JUMP_ID = PowerToggles.id("super_soldier_jump");
	static final ResourceLocation SAFE_FALL_ID = PowerToggles.id("super_soldier_safe_fall");
	static final ResourceLocation KNOCKBACK_ID = PowerToggles.id("super_soldier_knockback");
	static final ResourceLocation ATTACK_SPEED_ID = PowerToggles.id("super_soldier_attack_speed");

	/** Per-player throttle for action-bar feedback. */
	private static final Map<UUID, Long> LAST_MESSAGE = new HashMap<>();

	private SuperSoldier() {
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
	}

	// ---------------------------------------------------------------- state access

	public static SuperSoldierState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.SUPER_SOLDIER_STATE);
	}

	static void save(ServerPlayer player, SuperSoldierState state) {
		player.setAttached(ModAttachments.SUPER_SOLDIER_STATE, state);
	}

	/** Safe on the client too (the attachment is synced); the server never trusts a client's copy. */
	public static boolean hasPower(Player player) {
		SuperSoldierState s = player.getAttachedOrElse(ModAttachments.SUPER_SOLDIER_STATE, null);
		return s != null && s.hasPower;
	}

	public static boolean onslaughtActive(Player player) {
		SuperSoldierState s = player.getAttachedOrElse(ModAttachments.SUPER_SOLDIER_STATE, null);
		return s != null && s.hasPower && s.onslaughtUntil > player.level().getGameTime();
	}

	public static boolean focusActive(Player player) {
		SuperSoldierState s = player.getAttachedOrElse(ModAttachments.SUPER_SOLDIER_STATE, null);
		return s != null && s.hasPower && s.focusUntil > player.level().getGameTime();
	}

	public static int cooldownRemaining(Player player, String abilityId) {
		SuperSoldierState s = player.getAttachedOrElse(ModAttachments.SUPER_SOLDIER_STATE, null);
		if (s == null) {
			return 0;
		}
		Long ready = s.abilityReadyAt.get(abilityId);
		return ready == null ? 0 : (int) Math.max(0L, ready - player.level().getGameTime());
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
		SuperSoldierState s = state(player);
		long now = player.level().getGameTime();
		if (now - s.lastCombatTick >= 5L) { // at most one attachment write per 5 ticks
			SuperSoldierState n = s.copy();
			n.lastCombatTick = now;
			save(player, n);
		}
	}

	// ---------------------------------------------------------------- grant / revoke

	/** The refined serum / a lucky unrefined one / the admin command. Returns false if the player already has the power. */
	public static boolean grant(ServerPlayer player) {
		if (state(player).hasPower) {
			return false;
		}
		HeroTiers.claimPrimary(player, KEY);
		SuperSoldierState s = new SuperSoldierState();
		s.hasPower = true;
		save(player, s);
		reconcile(player);
		player.setHealth(player.getMaxHealth());
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.2f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 0.8f);
		level.sendParticles(new DustParticleOptions(new Vector3f(0.25f, 0.45f, 1.0f), 1.3f),
				player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.5, 0.8, 0.5, 0.05);
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.8, 0.4, 0.05);
		player.displayClientMessage(Component.translatable("message.projecthero.super_soldier.acquired")
				.withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.super_soldier.acquired_hint")
				.withStyle(ChatFormatting.GRAY), false);
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
				Component.translatable("message.projecthero.super_soldier.title").withStyle(ChatFormatting.BLUE, ChatFormatting.BOLD)));
		return true;
	}

	public static void revoke(ServerPlayer player) {
		SuperSoldierAbilities.clear(player.getUUID());
		LAST_MESSAGE.remove(player.getUUID());
		save(player, new SuperSoldierState()); // hasPower = false
		reconcile(player);
	}

	// ---------------------------------------------------------------- stats

	/** The upward launch speed that reaches roughly {@code height} blocks (vanilla gravity 0.08, drag 0.98). */
	public static double verticalSpeedForHeight(double height) {
		double lo = 0.1;
		double hi = 5.0;
		for (int i = 0; i < 30; i++) {
			double mid = (lo + hi) * 0.5;
			double y = 0.0;
			double vy = mid;
			double max = 0.0;
			for (int t = 0; t < 200 && (vy > 0.0 || t == 0); t++) {
				y += vy;
				max = Math.max(max, y);
				vy = (vy - 0.08) * 0.98;
			}
			if (max < height) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		return (lo + hi) * 0.5;
	}

	/** The multiplier on vanilla's 0.42 jump velocity that carries a player {@code blocks} high. */
	public static double jumpModifier(double blocks) {
		return verticalSpeedForHeight(blocks) / 0.42 - 1.0;
	}

	/**
	 * Brings every attribute modifier in line with the state. Idempotent -- fixed ids, and {@link PowerToggles#modifier}
	 * only writes when the value changes -- so it runs every tick (the unarmed bonus follows the main hand).
	 */
	public static void reconcile(ServerPlayer player) {
		SuperSoldierState s = state(player);
		if (!s.hasPower) {
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
			PowerToggles.clearModifier(player, Attributes.MAX_HEALTH, HEALTH_ID);
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, UNARMED_ID);
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, ONSLAUGHT_ID);
			PowerToggles.clearModifier(player, Attributes.JUMP_STRENGTH, JUMP_ID);
			PowerToggles.clearModifier(player, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL_ID);
			PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID);
			PowerToggles.clearModifier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID);
			if (player.getHealth() > player.getMaxHealth()) {
				player.setHealth(player.getMaxHealth());
			}
			return;
		}
		PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, SuperSoldierConfig.SPEED_BONUS,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		PowerToggles.modifier(player, Attributes.MAX_HEALTH, HEALTH_ID, SuperSoldierConfig.HEALTH_BONUS, AttributeModifier.Operation.ADD_VALUE);
		// v0.14.11: +7 melee whatever is held (it used to need an empty hand) -- it stacks with a sword
		PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, UNARMED_ID, SuperSoldierConfig.UNARMED_BONUS,
				AttributeModifier.Operation.ADD_VALUE);
		if (s.onslaughtUntil > player.level().getGameTime()) {
			PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, ONSLAUGHT_ID, SuperSoldierConfig.ONSLAUGHT_ATTACK_BONUS,
					AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, ONSLAUGHT_ID);
		}
		PowerToggles.modifier(player, Attributes.JUMP_STRENGTH, JUMP_ID, jumpModifier(SuperSoldierConfig.JUMP_BLOCKS),
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		PowerToggles.modifier(player, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL_ID, SuperSoldierConfig.SAFE_FALL_BONUS,
				AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, SuperSoldierConfig.KNOCKBACK_RESISTANCE,
				AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID, SuperSoldierConfig.ATTACK_SPEED_BONUS,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	}

	/** The damage-taken factor: 0.7 while he has the power (30% less), 1.0 otherwise. */
	public static float damageTakenFactor(Player player) {
		return hasPower(player) ? SuperSoldierConfig.DAMAGE_TAKEN_FACTOR : 1.0f;
	}

	// ---------------------------------------------------------------- tick

	/** Runs for every Super Soldier every server tick (from {@link SuperSoldierAbilityManager#serverTick}). */
	public static void tick(ServerPlayer player) {
		reconcile(player);
		SuperSoldierState s = state(player);
		long now = player.level().getGameTime();
		if (s.onslaughtUntil != 0L && now >= s.onslaughtUntil) {
			SuperSoldierState n = s.copy();
			n.onslaughtUntil = 0L;
			save(player, n);
			reconcile(player);
			say(player, "message.projecthero.super_soldier.onslaught_end", ChatFormatting.GRAY);
		} else if (s.onslaughtUntil > now && now % 4L == 0L) {
			((ServerLevel) player.level()).sendParticles(new DustParticleOptions(new Vector3f(0.3f, 0.5f, 1.0f), 1.0f),
					player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.35, 0.6, 0.35, 0.0);
		}
		if (s.focusUntil != 0L && now >= s.focusUntil) {
			SuperSoldierState n = state(player).copy();
			n.focusUntil = 0L;
			save(player, n);
			SuperSoldierAbilities.clearMarks(player.getUUID());
		}
		// out-of-combat healing: 1 HP every 2 s after 5 s without a fight (not while starving)
		if (now % SuperSoldierConfig.REGEN_INTERVAL == 0L && now - s.lastCombatTick >= SuperSoldierConfig.REGEN_DELAY
				&& player.isAlive() && player.getHealth() < player.getMaxHealth() && player.getFoodData().getFoodLevel() > 6) {
			player.heal(1.0f);
		}
		// an enhanced metabolism: Poison and Nausea never take hold
		if (now % 10L == 0L) {
			shrug(player, MobEffects.POISON);
			shrug(player, MobEffects.CONFUSION);
		}
	}

	private static void shrug(ServerPlayer player, Holder<MobEffect> effect) {
		MobEffectInstance cur = player.getEffect(effect);
		if (cur != null) {
			player.removeEffect(effect);
		}
	}

	// ---------------------------------------------------------------- lifecycle

	public static void onPlayerJoin(ServerPlayer player) {
		SuperSoldierState s = player.getAttachedOrElse(ModAttachments.SUPER_SOLDIER_STATE, null);
		if (s == null) {
			return;
		}
		if (!s.hasPower) {
			reconcile(player); // strips anything a save made before a revoke might still carry
			return;
		}
		long now = player.level().getGameTime();
		SuperSoldierState n = s.copy();
		// game time is per-world: nothing carried over from another world may lock the player out
		n.busyUntil = 0L;
		n.iframeUntil = 0L;
		n.noFallUntil = 0L;
		n.onslaughtUntil = 0L;
		n.focusUntil = 0L;
		n.lastCombatTick = 0L;
		n.abilityReadyAt.entrySet().removeIf(e -> e.getValue() > now + 20L * 150L);
		save(player, n);
		reconcile(player);
	}

	public static void onPlayerRespawn(ServerPlayer player) {
		SuperSoldierState s = player.getAttachedOrElse(ModAttachments.SUPER_SOLDIER_STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		SuperSoldierState n = s.copy();
		n.busyUntil = 0L;
		n.iframeUntil = 0L;
		n.noFallUntil = 0L;
		n.onslaughtUntil = 0L;
		n.focusUntil = 0L;
		n.lastCombatTick = 0L;
		save(player, n);
		reconcile(player);
		player.setHealth(player.getMaxHealth()); // the +5 hearts are there from the first moment of a respawn
	}

	/** Death / logout / dimension change: drop everything transient (the power and the cooldowns stay). */
	public static void clearTransient(ServerPlayer player) {
		SuperSoldierAbilities.clear(player.getUUID());
		LAST_MESSAGE.remove(player.getUUID());
		SuperSoldierState s = player.getAttachedOrElse(ModAttachments.SUPER_SOLDIER_STATE, null);
		if (s != null && s.hasPower) {
			SuperSoldierState n = s.copy();
			n.busyUntil = 0L;
			n.iframeUntil = 0L;
			n.focusUntil = 0L;
			save(player, n);
		}
	}
}
