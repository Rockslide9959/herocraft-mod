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
 * Only a player with the Gamma power ({@link HulkState#hasPower}) builds rage. As Banner it climbs when he is
 * hurt or fights ({@link HulkDamage}) and bleeds off once he has been left alone for a while. At 75 he can let
 * the Hulk out with H; at 100 the Hulk comes out on his own. As the Hulk, rage burns down every second and
 * every hit he takes pours some back in. At 0 he shrinks back to Banner, exhausted (Weakness + Slowness, no
 * rage, no change) for a few seconds.
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

	private static final DustParticleOptions GAMMA_GREEN = new DustParticleOptions(new Vector3f(0.3f, 0.95f, 0.2f), 1.6f);
	private static final DustParticleOptions DEEP_GREEN = new DustParticleOptions(new Vector3f(0.12f, 0.55f, 0.1f), 1.2f);

	/** Per-player throttle for action-bar feedback. */
	private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();

	private Hulk() {
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
	}

	// ---------------------------------------------------------------- state

	public static HulkState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.HULK_STATE);
	}

	static void save(ServerPlayer player, HulkState state) {
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

	public static boolean exhausted(Player player) {
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		return s != null && s.exhaustedUntil > player.level().getGameTime();
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

	// ---------------------------------------------------------------- grant / revoke

	/** Gives the Gamma power (command now; the Gamma Serum in Phase 4). False if the player already has it. */
	public static boolean grant(ServerPlayer player) {
		if (state(player).hasPower) {
			return false;
		}
		HeroTiers.claimPrimary(player, KEY);
		HulkState s = new HulkState();
		s.hasPower = true;
		s.formChangedAt = player.level().getGameTime() - HulkConfig.TOGGLE_DEBOUNCE_TICKS;
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
		save(player, new HulkState());
		reconcile(player);
		PowerToggles.clearModifier(player, Attributes.SCALE, SCALE_ID);
		LAST_MESSAGE.remove(player.getUUID());
	}

	// ---------------------------------------------------------------- rage

	/** Sets rage outright (the {@code /hulk setrage} test command). 100 lets the Hulk out on the next tick. */
	public static void setRage(ServerPlayer player, float value) {
		HulkState n = state(player).copy();
		n.rage = clampRage(value);
		save(player, n);
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
		gain(player, s, amount * per);
	}

	/** This player with the Gamma power just dealt {@code amount} to something. Only builds rage as Banner. */
	public static void onDealt(ServerPlayer player, float amount) {
		HulkState s = state(player);
		if (!s.hasPower || player.isSpectator()) {
			return;
		}
		gain(player, s, s.hulk ? 0.0f : amount * HulkConfig.RAGE_PER_DAMAGE_DEALT);
	}

	private static void gain(ServerPlayer player, HulkState s, float amount) {
		long now = player.level().getGameTime();
		HulkState n = s.copy();
		n.lastCombatAt = now;
		// an exhausted Banner has nothing left to get angry with
		if (amount > 0.0f && (s.hulk || s.exhaustedUntil <= now)) {
			n.rage = clampRage(s.rage + amount);
		}
		save(player, n);
	}

	// ---------------------------------------------------------------- the change

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

	/** Banner becomes the Hulk. {@code forced}: rage hit the top on its own. */
	public static void transform(ServerPlayer player, boolean forced) {
		HulkState s = state(player);
		if (!s.hasPower || s.hulk) {
			return;
		}
		float ratio = healthRatio(player);
		HulkState n = s.copy();
		n.hulk = true;
		n.formChangedAt = player.level().getGameTime();
		save(player, n);
		reconcile(player);
		player.setHealth(Math.min(player.getMaxHealth(), ratio * player.getMaxHealth() + HulkConfig.TRANSFORM_HEAL));
		transformFx(player, forced);
	}

	/** The Hulk shrinks back to Banner. {@code exhaust}: rage ran out (the normal way) -- Weakness + Slowness follow. */
	public static void revert(ServerPlayer player, boolean exhaust) {
		HulkState s = state(player);
		if (!s.hasPower || !s.hulk) {
			return;
		}
		long now = player.level().getGameTime();
		float ratio = healthRatio(player);
		HulkState n = s.copy();
		n.hulk = false;
		n.rage = 0.0f;
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
			if (!s.hasPower) {
				PowerToggles.clearModifier(player, Attributes.SCALE, SCALE_ID);
			}
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
		double step = HulkConfig.SCALE_BONUS / Math.max(1, HulkConfig.GROWTH_TICKS);
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
		if (player.tickCount % 20 == 0) {
			reconcile(player); // safety net: a respawn or another mod may have cleared a transient modifier
		}
		if (!player.isAlive() || player.isSpectator()) {
			return;
		}

		if (s.hulk) {
			if (now % 20L == 0L) {
				HulkState n = s.copy();
				n.rage = clampRage(s.rage - HulkConfig.HULK_DRAIN_PER_SECOND);
				save(player, n);
				s = n;
			}
			if (s.rage <= 0.0f) {
				revert(player, true);
				return;
			}
			if (player.tickCount % HulkConfig.REGEN_INTERVAL_TICKS == 0 && player.getHealth() < player.getMaxHealth()) {
				player.heal(HulkConfig.REGEN_AMOUNT);
			}
			tickAura(level, player, s, now);
			return;
		}

		if (s.rage >= HulkConfig.RAGE_MAX && s.exhaustedUntil <= now) {
			transform(player, true);
			return;
		}
		if (s.rage > 0.0f && now % 20L == 0L && now - s.lastCombatAt >= HulkConfig.CALM_DELAY_TICKS) {
			HulkState n = s.copy();
			n.rage = clampRage(s.rage - HulkConfig.CALM_DECAY_PER_SECOND);
			save(player, n);
		}
		// Banner is close to losing it: a green flicker around him
		if (s.rage >= HulkConfig.MANUAL_TRANSFORM_RAGE && now % 10L == 0L) {
			level.sendParticles(DEEP_GREEN, player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.3, 0.5, 0.3, 0.0);
		}
	}

	private static void tickAura(ServerLevel level, ServerPlayer player, HulkState s, long now) {
		double h = player.getBbHeight();
		// green gamma pours off him while he grows
		if (now - s.formChangedAt < HulkConfig.GROWTH_TICKS) {
			level.sendParticles(GAMMA_GREEN, player.getX(), player.getY() + h * 0.5, player.getZ(), 6, 0.5, h * 0.4, 0.5, 0.02);
			return;
		}
		if (now % 8L == 0L) {
			level.sendParticles(DEEP_GREEN, player.getX(), player.getY() + h * 0.5, player.getZ(), 1, 0.45, h * 0.35, 0.45, 0.0);
		}
	}

	// ---------------------------------------------------------------- effects

	private static void transformFx(ServerPlayer player, boolean forced) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.6f, 0.7f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0f, 0.6f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.0f, 0.5f);
		level.sendParticles(GAMMA_GREEN, c.x, c.y, c.z, 80, 0.7, 1.0, 0.7, 0.05);
		level.sendParticles(DEEP_GREEN, c.x, c.y, c.z, 40, 0.9, 1.1, 0.9, 0.02);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y, c.z, 20, 0.8, 1.0, 0.8, 0.1);
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

	/** Join: re-apply the stats of whatever form was saved (transient modifiers do not survive a relog). */
	public static void onPlayerJoin(ServerPlayer player) {
		HulkState s = state(player);
		if (!s.hasPower) {
			return;
		}
		HulkState n = s.copy();
		// game time is per world: nothing carried over from another world may lock the player out
		n.exhaustedUntil = 0L;
		n.formChangedAt = 0L;
		n.lastCombatAt = 0L;
		save(player, n);
		reconcile(player);
	}

	/** Respawn: a fresh, calm Banner -- no rage, no Hulk, full size back to normal at once. */
	public static void onPlayerRespawn(ServerPlayer player) {
		HulkState s = state(player);
		if (!s.hasPower) {
			return;
		}
		HulkState n = s.copy();
		n.hulk = false;
		n.rage = 0.0f;
		n.exhaustedUntil = 0L;
		n.formChangedAt = 0L;
		save(player, n);
		reconcile(player);
		PowerToggles.clearModifier(player, Attributes.SCALE, SCALE_ID);
	}

	/** Death / logout / dimension change: nothing transient to drop beyond the message throttle. */
	public static void clearTransient(ServerPlayer player) {
		LAST_MESSAGE.remove(player.getUUID());
	}
}
