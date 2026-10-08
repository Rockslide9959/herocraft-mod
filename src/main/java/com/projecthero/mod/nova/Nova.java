package com.projecthero.mod.nova;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.nova.data.NovaState;

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
 * The single server-side API for Nova (Richard Rider), the v0.15.13 Hero-Tier power. Nothing else pokes
 * {@link NovaState}; every mutator re-saves through {@link ServerPlayer#setAttached}. The moves live in
 * {@link NovaAbilities}, flight in {@link NovaFlight}, the incoming-damage rules in {@link NovaDamage}, the numbers in
 * {@link NovaConfig}.
 *
 * <h2>How it works</h2>
 * The Nova Corps Helmet (handed over by the dying Centurion at a Crashed Nova Corps Pod) grants the power, replacing
 * whatever Primary the player held. <b>H</b> puts the Nova Corps uniform on or takes it off; the uniform is not an item
 * (nothing can be dropped, traded or lost) -- it is drawn over the player from the synced state. Only while suited do the
 * passives (60% damage reduction, no fall damage, the Worldmind's 32-block mob outline), the flight and the six keys work.
 *
 * <h2>The Nova Force</h2>
 * 0..100, refilling 3 a second (half that while flying); every move spends some of it, fast flight drains 2 a second,
 * and the Overload needs all of it. v0.15.15: the bar is infinite while the Overload runs; when it ends the bar is empty
 * and refills at half speed for 60 s (stacking with the flying slowdown).
 */
public final class Nova {
	public static final String KEY = "nova";

	private static final ResourceLocation SAFE_FALL_ID = PowerToggles.id("nova_safe_fall");
	private static final ResourceLocation MELEE_ID = PowerToggles.id("nova_melee");

	/** Nova gold (255, 205, 60) and the Worldmind cyan (139, 248, 255). */
	public static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0f, 0.80f, 0.24f), 1.3f);
	public static final DustParticleOptions GOLD_BIG = new DustParticleOptions(new Vector3f(1.0f, 0.84f, 0.30f), 2.2f);
	public static final DustParticleOptions CYAN = new DustParticleOptions(new Vector3f(139 / 255f, 248 / 255f, 1.0f), 1.0f);

	/** Per-player throttle for action-bar feedback. */
	private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
	/** Game time of the last {@link #tick} (at most one run per game tick). */
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();
	/** Game time of the last H toggle (debounce). */
	private static final Map<UUID, Long> LAST_TOGGLE = new ConcurrentHashMap<>();

	private Nova() {
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
		LAST_TICK.clear();
		LAST_TOGGLE.clear();
		NovaFlight.clearSessionState();
		NovaAbilities.clearSessionState();
		NovaAbilityManager.clearSessionState();
	}

	// ---------------------------------------------------------------- state

	public static NovaState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.NOVA_STATE);
	}

	static void save(ServerPlayer player, NovaState state) {
		player.setAttached(ModAttachments.NOVA_STATE, state);
	}

	private static NovaState peek(Player player) {
		return player.getAttachedOrElse(ModAttachments.NOVA_STATE, null);
	}

	/** Client-safe (the attachment is synced). */
	public static boolean hasPower(Player player) {
		NovaState s = peek(player);
		return s != null && s.hasPower;
	}

	/** Has the power and the uniform is on. Client-safe. */
	public static boolean suited(Player player) {
		NovaState s = peek(player);
		return s != null && s.hasPower && s.suited;
	}

	public static boolean isFlying(Player player) {
		NovaState s = peek(player);
		return s != null && s.hasPower && s.suited && s.flying;
	}

	public static boolean blasting(Player player) {
		NovaState s = peek(player);
		return s != null && s.hasPower && s.suited && s.blasting;
	}

	public static boolean shieldUp(Player player) {
		NovaState s = peek(player);
		return s != null && s.hasPower && s.suited && s.shieldUntil > player.level().getGameTime();
	}

	public static boolean overloaded(Player player) {
		NovaState s = peek(player);
		return s != null && s.hasPower && s.suited && s.overloadUntil > player.level().getGameTime();
	}

	public static float force(Player player) {
		NovaState s = peek(player);
		return s == null ? 0f : s.force;
	}

	/** v0.15.15: ticks left of the post-Overload half-speed refill (0 when none). Client-safe. */
	public static int slowRegenRemaining(Player player) {
		NovaState s = peek(player);
		return s == null ? 0 : (int) Math.max(0L, Math.min(NovaConfig.OVERLOAD_SLOW_REGEN_TICKS, s.slowRegenUntil - player.level().getGameTime()));
	}

	/** v0.15.15: Nova Force refilled a second right now (3, halved while flying, halved again after an Overload). */
	public static float regenPerSecond(Player player) {
		NovaState s = peek(player);
		if (s == null || !s.hasPower) {
			return 0f;
		}
		float r = NovaConfig.FORCE_REGEN_PER_SECOND;
		if (s.flying && s.suited) {
			r *= NovaConfig.FLYING_REGEN_MULTIPLIER;
		}
		if (slowRegenRemaining(player) > 0) {
			r *= NovaConfig.OVERLOAD_AFTER_REGEN_MULTIPLIER;
		}
		return r;
	}

	public static int cooldownRemaining(Player player, String abilityId) {
		NovaState s = peek(player);
		if (s == null) {
			return 0;
		}
		Long ready = s.abilityReadyAt.get(abilityId);
		return ready == null ? 0 : (int) Math.max(0L, ready - player.level().getGameTime());
	}

	/** v0.15.15: double damage while the Overload runs. */
	public static float damageMultiplier(Player player) {
		return overloaded(player) ? NovaConfig.OVERLOAD_DAMAGE_MULTIPLIER : 1.0f;
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

	// ---------------------------------------------------------------- the Nova Force

	/** Spends {@code cost} Nova Force if he has it (free while the Overload runs: the bar is infinite then). */
	public static boolean spendForce(ServerPlayer player, float cost) {
		if (cost <= 0f || overloaded(player)) {
			return true;
		}
		NovaState s = state(player);
		if (s.force + 1.0e-3f < cost) {
			return false;
		}
		NovaState n = s.copy();
		n.force = Math.max(0f, Math.min(NovaConfig.FORCE_MAX, s.force) - cost);
		save(player, n);
		return true;
	}

	/** Sets the Nova Force outright (tests / the admin command). */
	public static void setForce(ServerPlayer player, float value) {
		NovaState n = state(player).copy();
		n.force = Math.max(0f, Math.min(NovaConfig.FORCE_MAX, value));
		save(player, n);
	}

	// ---------------------------------------------------------------- grant / revoke

	/** The Nova Corps Helmet / the command. False if the player already carries the Nova Force. */
	public static boolean grant(ServerPlayer player) {
		if (state(player).hasPower) {
			return false;
		}
		HeroTiers.claimPrimary(player, KEY);
		NovaState s = new NovaState();
		s.hasPower = true;
		s.force = NovaConfig.FORCE_MAX;
		save(player, s);
		reconcile(player);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		level.sendParticles(GOLD_BIG, c.x, c.y, c.z, 70, 0.6, 1.0, 0.6, 0.02);
		level.sendParticles(CYAN, c.x, c.y + 0.6, c.z, 20, 0.3, 0.3, 0.3, 0.02);
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 40, 0.4, 1.2, 0.4, 0.08);
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 1.3f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5f, 0.7f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6f, 1.5f);
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
				Component.translatable("message.projecthero.nova.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
				Component.translatable("message.projecthero.nova.subtitle").withStyle(ChatFormatting.AQUA)));
		player.displayClientMessage(Component.translatable("message.projecthero.nova.acquired")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.nova.acquired_hint")
				.withStyle(ChatFormatting.YELLOW), false);
		return true;
	}

	public static void revoke(ServerPlayer player) {
		NovaFlight.stop(player, false);
		NovaAbilities.clear(player);
		save(player, new NovaState());
		reconcile(player);
		forget(player.getUUID());
	}

	private static void forget(UUID id) {
		LAST_MESSAGE.remove(id);
		LAST_TICK.remove(id);
		LAST_TOGGLE.remove(id);
		NovaAbilityManager.forget(id);
	}

	// ---------------------------------------------------------------- the suit (H)

	/** H: the uniform on or off (debounced). */
	public static void toggleSuit(ServerPlayer player) {
		NovaState s = state(player);
		if (!s.hasPower || !player.isAlive() || player.isSpectator()) {
			return;
		}
		long now = player.level().getGameTime();
		Long last = LAST_TOGGLE.get(player.getUUID());
		if (last != null && now - last < NovaConfig.SUIT_TOGGLE_COOLDOWN && now >= last) {
			return;
		}
		LAST_TOGGLE.put(player.getUUID(), now);
		if (s.suited) {
			suitDown(player);
		} else {
			suitUp(player);
		}
	}

	public static void suitUp(ServerPlayer player) {
		NovaState s = state(player);
		if (!s.hasPower || s.suited) {
			return;
		}
		NovaState n = s.copy();
		n.suited = true;
		n.suitChangeAt = player.level().getGameTime();
		save(player, n);
		reconcile(player);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position();
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1.0, c.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.5f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5f, 0.8f);
		NovaAbilities.beginSuitUpFx(player);
	}

	public static void suitDown(ServerPlayer player) {
		NovaState s = state(player);
		if (!s.suited) {
			return;
		}
		boolean wasOverloaded = overloaded(player);
		NovaFlight.stop(player, false);
		NovaAbilities.clear(player);
		NovaState n = state(player).copy();
		if (wasOverloaded) {
			// taken off mid-Overload: no burst, but the bar still empties and refills slowly
			n.force = 0f;
			n.slowRegenUntil = player.level().getGameTime() + NovaConfig.OVERLOAD_SLOW_REGEN_TICKS;
		}
		n.suited = false;
		n.suitChangeAt = player.level().getGameTime();
		n.blasting = false;
		n.shieldUntil = 0L;
		n.overloadUntil = 0L;
		n.wellUntil = 0L;
		n.wellPos = java.util.List.of();
		n.slamming = false;
		n.animId = NovaState.ANIM_NONE;
		save(player, n);
		reconcile(player);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		level.sendParticles(GOLD, c.x, c.y, c.z, 40, 0.35, 0.9, 0.35, 0.02);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8f, 1.4f);
	}

	// ---------------------------------------------------------------- stats

	/** The safe-fall attribute lets the client predict "no fall damage" while suited (the server cancels it anyway). */
	public static void reconcile(ServerPlayer player) {
		if (suited(player)) {
			PowerToggles.modifier(player, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL_ID, 1000.0, AttributeModifier.Operation.ADD_VALUE);
			// v0.15.15: +8 melee while suited
			PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, MELEE_ID, NovaConfig.MELEE_BONUS, AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(player, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL_ID);
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, MELEE_ID);
		}
	}

	/** Damage-taken factor while suited: 0.4. */
	public static float damageTakenFactor(ServerPlayer player) {
		return suited(player) ? 1.0f - NovaConfig.DAMAGE_REDUCTION : 1.0f;
	}

	/** Whether he may use a move / fly right now; says why not. */
	static boolean canAct(ServerPlayer player) {
		NovaState s = state(player);
		if (!s.hasPower || !player.isAlive() || player.isSpectator()) {
			return false;
		}
		if (!s.suited) {
			say(player, "message.projecthero.nova.not_suited", ChatFormatting.GRAY);
			return false;
		}
		return true;
	}

	// ---------------------------------------------------------------- tick

	/** Runs for every player every server tick (cheap when he has no power). */
	public static void tick(ServerPlayer player) {
		NovaState s = player.getAttachedOrElse(ModAttachments.NOVA_STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		long now = player.level().getGameTime();
		Long prev = LAST_TICK.put(player.getUUID(), now);
		if (prev != null && prev == now) {
			return; // at most once per game tick
		}
		if (player.tickCount % 20 == 0) {
			reconcile(player);
		}
		if (now % 5 == 0) {
			tickForce(player, s);
		}
		NovaFlight.tick(player);
		NovaAbilities.tick(player);
	}

	/**
	 * Every 5 ticks: a quarter-second of refill (halved while flying, halved again for 60 s after an Overload) minus a
	 * quarter-second of fast-flight drain. Nothing moves while the Overload runs (the bar is infinite then).
	 */
	private static void tickForce(ServerPlayer player, NovaState s) {
		if (s.suited && s.overloadUntil > player.level().getGameTime()) {
			return;
		}
		float gain = regenPerSecond(player) / 4f;
		if (s.flying && NovaFlight.flyingFast(player)) {
			gain -= NovaConfig.FAST_FLIGHT_DRAIN_PER_SECOND / 4f;
		}
		float cur = Float.isNaN(s.force) ? 0f : s.force;
		float next = Math.max(0f, Math.min(NovaConfig.FORCE_MAX, cur + gain));
		if (next != s.force) {
			NovaState n = s.copy();
			n.force = next;
			save(player, n);
		}
	}

	// ---------------------------------------------------------------- lifecycle

	public static void onPlayerJoin(ServerPlayer player) {
		NovaState s = state(player);
		if (!s.hasPower) {
			return;
		}
		long now = player.level().getGameTime();
		NovaState n = s.copy();
		// game time is per world: nothing carried over may lock the player out
		n.blasting = false;
		n.slamming = false;
		n.animId = NovaState.ANIM_NONE;
		n.shieldUntil = 0L;
		n.overloadUntil = 0L;
		n.wellUntil = 0L;
		n.wellPos = java.util.List.of();
		n.suitChangeAt = 0L;
		if (n.slowRegenUntil > now + NovaConfig.OVERLOAD_SLOW_REGEN_TICKS) {
			n.slowRegenUntil = 0L; // a different world clock
		}
		n.force = Float.isNaN(n.force) ? 0f : Math.max(0f, Math.min(NovaConfig.FORCE_MAX, n.force));
		n.abilityReadyAt.entrySet().removeIf(e -> e.getValue() > now + NovaConfig.OVERLOAD_COOLDOWN + 20L);
		boolean wasFlying = n.flying && n.suited;
		n.flying = wasFlying;
		save(player, n);
		reconcile(player);
		if (wasFlying) {
			NovaFlight.resume(player);
		}
	}

	public static void onPlayerRespawn(ServerPlayer player) {
		NovaState s = state(player);
		if (!s.hasPower) {
			return;
		}
		NovaState n = s.copy();
		n.suited = false;
		n.flying = false;
		n.blasting = false;
		n.slamming = false;
		n.shieldUntil = 0L;
		n.overloadUntil = 0L;
		n.wellUntil = 0L;
		n.wellPos = java.util.List.of();
		n.animId = NovaState.ANIM_NONE;
		n.force = Math.max(n.force, NovaConfig.FORCE_MAX * 0.5f);
		n.abilityReadyAt.clear();
		save(player, n);
		reconcile(player);
	}

	/** Death / logout / dimension change: drop everything transient (the power, the suit and the Nova Force stay). */
	public static void clearTransient(ServerPlayer player) {
		NovaAbilities.clear(player);
		forget(player.getUUID());
		NovaState s = player.getAttachedOrElse(ModAttachments.NOVA_STATE, null);
		if (s != null && s.hasPower && (s.blasting || s.slamming || s.animId != NovaState.ANIM_NONE || s.wellUntil != 0L
				|| s.shieldUntil != 0L)) {
			NovaState n = s.copy();
			n.blasting = false;
			n.shieldUntil = 0L;
			n.slamming = false;
			n.animId = NovaState.ANIM_NONE;
			n.wellUntil = 0L;
			n.wellPos = java.util.List.of();
			save(player, n);
		}
	}

	public static void onWorldChange(ServerPlayer player) {
		clearTransient(player);
		if (isFlying(player)) {
			NovaFlight.resume(player);
		}
	}

	static void setAnim(ServerPlayer player, int animId) {
		NovaState n = state(player).copy();
		n.animId = animId;
		n.animStart = player.level().getGameTime();
		save(player, n);
	}
}
