package com.projecthero.mod.sentry;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.sentry.block.BurntShadowBlock;
import com.projecthero.mod.sentry.data.SentryAttachments;
import com.projecthero.mod.sentry.data.SentryState;
import com.projecthero.mod.squad.Squads;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

import org.joml.Vector3f;

/**
 * v0.17.5 Sentry &amp; the Void (Bob Reynolds, "the power of a million exploding suns"): the single server-side API of the power.
 * Nothing else changes {@link SentryState} except the takeover ({@link VoidTakeover}). The numbers are {@link SentryConfig}'s; the
 * moves are {@link SentryMoves}' and {@link VoidMoves}' (routed by {@link SentryAbilityManager}), flight is {@link SentryFlight}'s.
 *
 * <ul>
 *   <li><b>Sun</b> (gold Gauge): fuels the Sentry moves. Fills fast in daylight, slowly at night, not at all in the dark.</li>
 *   <li><b>Void</b> (black Hairline): the danger. Rises with the big moves, heavy hits, kills, fighting at night and standing in the
 *   dark; falls in daylight, while healing others and while V (Calm) is held. 50 whispers, 75 misfires, 90 the sky dims and hits
 *   leave burnt shadows, 100 the Void takes over ({@link VoidTakeover}).</li>
 *   <li><b>The body</b>: +30 health (Void Form +40), 70% less damage taken (75%), +12 melee (+16), +30% speed, Sun-fed healing.</li>
 * </ul>
 */
public final class Sentry {
	public static final String KEY = "sentry";

	/** His light (the Sentry moves) and the Void's darkness, as their own damage types (armour still counts). */
	public static final ResourceKey<DamageType> LIGHT = ResourceKey.create(Registries.DAMAGE_TYPE, ProjectHeroMod.id("sentry_light"));
	public static final ResourceKey<DamageType> VOID = ResourceKey.create(Registries.DAMAGE_TYPE, ProjectHeroMod.id("sentry_void"));

	private static final ResourceLocation HEALTH_ID = PowerToggles.id("sentry_health");
	private static final ResourceLocation SPEED_ID = PowerToggles.id("sentry_speed");
	private static final ResourceLocation ATTACK_ID = PowerToggles.id("sentry_attack");

	public static final int GOLD = 0xFFC83A;
	public static final int BLUE = 0x2F6BFF;
	public static final int VOID_BLACK = 0x0A0610;

	private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();
	/** Gametests: pins the Sun exposure per player (the test world's sky is not predictable). */
	private static final Map<UUID, Sun> PINNED_SUN = new ConcurrentHashMap<>();

	private Sentry() {
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
		LAST_TICK.clear();
		PINNED_SUN.clear();
		SentryMoves.clearSessionState();
		VoidMoves.clearSessionState();
		SentryFlight.clearSessionState();
		VoidTakeover.clearSessionState();
	}

	static void forget(UUID id) {
		LAST_MESSAGE.remove(id);
		LAST_TICK.remove(id);
		PINNED_SUN.remove(id);
		SentryMoves.forget(id);
		VoidMoves.forget(id);
		SentryFlight.forget(id);
	}

	// ---------------------------------------------------------------- state (client and server)

	public static SentryState state(ServerPlayer player) {
		return player.getAttachedOrCreate(SentryAttachments.STATE);
	}

	static void save(ServerPlayer player, SentryState state) {
		player.setAttached(SentryAttachments.STATE, state);
	}

	public static SentryState peek(Player player) {
		return player.getAttachedOrElse(SentryAttachments.STATE, null);
	}

	public static boolean hasPower(Player player) {
		SentryState s = peek(player);
		return s != null && s.hasPower;
	}

	/** In Void Form (by choice or the takeover). Client-safe. */
	public static boolean voidForm(Player player) {
		SentryState s = peek(player);
		return s != null && s.hasPower && (s.voidForm || s.inTakeover());
	}

	/** The Void has the body. Client-safe. */
	public static boolean inTakeover(Entity entity) {
		if (!(entity instanceof Player player)) {
			return false;
		}
		SentryState s = peek(player);
		return s != null && s.hasPower && s.inTakeover();
	}

	public static boolean isFlying(Player player) {
		SentryState s = peek(player);
		return s != null && s.hasPower && (s.flying || s.inTakeover());
	}

	/** Flying under his own steam (X), not carried by the Void. Client-safe: the shared directional flight uses it. */
	public static boolean selfFlying(Player player) {
		SentryState s = peek(player);
		return s != null && s.hasPower && s.flying && !s.inTakeover();
	}

	public static boolean calming(Player player) {
		SentryState s = peek(player);
		return s != null && s.hasPower && s.calming;
	}

	public static float sun(Player player) {
		SentryState s = peek(player);
		return s == null ? 0f : s.sun;
	}

	public static float voidLevel(Player player) {
		SentryState s = peek(player);
		return s == null ? 0f : s.voidLevel;
	}

	static long now(Player player) {
		return player.level().getGameTime();
	}

	public static int cooldownRemaining(Player player, String id) {
		SentryState s = peek(player);
		if (s == null) {
			return 0;
		}
		Long ready = s.abilityReadyAt.get(id);
		return ready == null ? 0 : (int) Math.max(0L, ready - now(player));
	}

	static void startCooldown(ServerPlayer player, String id, int ticks) {
		if (ticks <= 0) {
			return;
		}
		SentryState n = state(player).copy();
		n.abilityReadyAt.put(id, now(player) + ticks);
		save(player, n);
	}

	public static void clearCooldowns(ServerPlayer player) {
		SentryState n = state(player).copy();
		n.abilityReadyAt.clear();
		n.letOutLockedUntil = 0L;
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

	static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
	}

	static void sound(ServerPlayer player, net.minecraft.core.Holder<SoundEvent> sound, float volume, float pitch) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
	}

	public static DustParticleOptions dust(int rgb, float size) {
		return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f), size);
	}

	// ---------------------------------------------------------------- the two meters

	/** Creative players never pay, and the Void never climbs for them. */
	static boolean free(ServerPlayer player) {
		return player.getAbilities().instabuild;
	}

	public static boolean hasSun(ServerPlayer player, float amount) {
		return free(player) || state(player).sun + 1.0e-3f >= amount;
	}

	/** Spends Sun if there is enough; false ("Not enough sunlight") if not. */
	public static boolean spendSun(ServerPlayer player, float amount) {
		if (free(player) || amount <= 0f) {
			return true;
		}
		SentryState s = state(player);
		if (s.sun + 1.0e-3f < amount) {
			say(player, "message.projecthero.sentry.no_sun", ChatFormatting.GRAY);
			return false;
		}
		SentryState n = s.copy();
		n.sun = Math.max(0f, s.sun - amount);
		save(player, n);
		return true;
	}

	public static void setSun(ServerPlayer player, float value) {
		SentryState n = state(player).copy();
		n.sun = Math.max(0f, Math.min(SentryConfig.SUN_MAX, value));
		save(player, n);
	}

	public static void setVoid(ServerPlayer player, float value) {
		SentryState n = state(player).copy();
		n.voidLevel = Math.max(0f, Math.min(SentryConfig.VOID_MAX, value));
		save(player, n);
		if (n.voidLevel >= SentryConfig.VOID_MAX) {
			VoidTakeover.tryBegin(player);
		}
	}

	/**
	 * Adds (or, negative, takes) Void. Reaching 100 hands the body to the Void ({@link VoidTakeover#tryBegin}). The takeover holds the
	 * meter at 100; a creative player's never climbs.
	 */
	public static void addVoid(ServerPlayer player, float amount) {
		SentryState s = state(player);
		if (!s.hasPower || amount == 0f || s.inTakeover() || (amount > 0f && free(player))) {
			return;
		}
		setVoid(player, s.voidLevel + amount);
	}

	/** How much sun reaches him where he stands. */
	public enum Sun {
		DIRECT, SHADE, NIGHT, DARK
	}

	public static Sun sun(ServerPlayer player, boolean ignorePin) {
		Sun pinned = ignorePin ? null : PINNED_SUN.get(player.getUUID());
		if (pinned != null) {
			return pinned;
		}
		Level level = player.level();
		if (!level.dimensionType().hasSkyLight() || level.dimensionType().hasCeiling()) {
			return Sun.DARK;
		}
		BlockPos eye = BlockPos.containing(player.getEyePosition());
		boolean sky = level.canSeeSky(eye);
		boolean day = level.isDay();
		if (day && sky && !level.isRainingAt(eye)) {
			return Sun.DIRECT;
		}
		if (!sky && level.getBrightness(LightLayer.SKY, eye) < 8 && level.getBrightness(LightLayer.BLOCK, eye) < 8) {
			return Sun.DARK;
		}
		return day ? Sun.SHADE : Sun.NIGHT;
	}

	/** Gametests only: pins {@link #sun} for this player ({@code null} = the real sky again). */
	public static void pinSun(ServerPlayer player, Sun sun) {
		if (sun == null) {
			PINNED_SUN.remove(player.getUUID());
		} else {
			PINNED_SUN.put(player.getUUID(), sun);
		}
	}

	public static float sunPerSecond(Sun sun) {
		return switch (sun) {
			case DIRECT -> SentryConfig.SUN_DIRECT_PER_SECOND;
			case SHADE -> SentryConfig.SUN_SHADE_PER_SECOND;
			case NIGHT -> SentryConfig.SUN_NIGHT_PER_SECOND;
			case DARK -> SentryConfig.SUN_DARK_PER_SECOND;
		};
	}

	/** The Void's own drift a second where he stands (before Calm and Void Form). */
	public static float voidPerSecond(Sun sun) {
		return switch (sun) {
			case DIRECT -> SentryConfig.VOID_DAYLIGHT_PER_SECOND;
			case SHADE -> 0f;
			case NIGHT -> SentryConfig.VOID_NIGHT_PER_SECOND;
			case DARK -> SentryConfig.VOID_DARK_PER_SECOND;
		};
	}

	/** Whether the Void has passed {@code threshold} for this player. Client-safe. */
	public static boolean voidAt(Player player, float threshold) {
		return hasPower(player) && voidLevel(player) >= threshold;
	}

	// ---------------------------------------------------------------- hits

	public static DamageSource lightSource(ServerPlayer player) {
		return player.damageSources().source(LIGHT, player);
	}

	public static DamageSource voidSource(ServerPlayer player) {
		return player.damageSources().source(VOID, player);
	}

	/**
	 * One of his moves hits {@code target} for {@code amount} (light or the Void's darkness). In the takeover the Void's own rules
	 * apply ({@link VoidTakeover#canHit}: everything living); otherwise squadmates, their pets and players with PvP off are vetoed by
	 * {@link HeroTargets#canHarm}. Damage is always his, so a boss's threat table counts it. Above 90 Void a hit leaves a burnt shadow.
	 */
	public static boolean hit(ServerPlayer player, LivingEntity target, float amount, boolean voidDamage) {
		if (target == null || target == player || !target.isAlive()) {
			return false;
		}
		boolean takeover = inTakeover(player);
		if (takeover ? !VoidTakeover.canHit(player, target) : !HeroTargets.canHarm(player, target)) {
			return false;
		}
		DamageSource source = voidDamage ? voidSource(player) : lightSource(player);
		float before = target.getHealth();
		boolean landed;
		if (takeover && !(target instanceof Player)) {
			int saved = target.invulnerableTime;
			target.invulnerableTime = 0;
			landed = target.hurt(source, amount);
			if (!landed) {
				target.invulnerableTime = saved;
			}
		} else if (takeover) {
			landed = target.hurt(source, amount); // a player: vanilla's own PvP rule still stands
		} else {
			landed = com.projecthero.mod.hero.power.AbilityHelpers.hurtBurst(player, target, source, amount);
		}
		boolean hurt = landed || target.getHealth() < before;
		if (hurt && voidLevel(player) >= SentryConfig.VOID_DIMMING) {
			shadow(player.serverLevel(), target);
		}
		return hurt;
	}

	/** May an area of his touch {@code e}: never him, never a squadmate, never a protected target (the takeover: everything). */
	public static boolean areaVictim(ServerPlayer player, Entity e) {
		if (!(e instanceof LivingEntity le) || le == player || !le.isAlive() || le.isSpectator()) {
			return false;
		}
		if (inTakeover(player)) {
			return VoidTakeover.canHit(player, le);
		}
		return !Squads.areAllies(player, le) && HeroTargets.canHarm(player, le);
	}

	/** A burnt shadow under {@code target} (an erased creature, or anything hit above 90 Void). */
	public static void shadow(ServerLevel level, LivingEntity target) {
		BurntShadowBlock.lay(level, target.blockPosition(), target.getDirection());
	}

	// ---------------------------------------------------------------- the power

	/** The serum (and the command). False if he already has it or the unique-powers rule refuses. */
	public static boolean grant(ServerPlayer player) {
		SentryState old = state(player);
		if (old.hasPower) {
			return false;
		}
		if (com.projecthero.mod.hero.UniquePowers.refuse(player, KEY)) {
			return false;
		}
		HeroTiers.claimPrimary(player, KEY);
		SentryState s = new SentryState();
		s.hasPower = true;
		s.sun = SentryConfig.SUN_MAX;
		save(player, s);
		reconcile(player);
		player.connection.send(new ClientboundSetTitleTextPacket(
				Component.translatable("message.projecthero.sentry.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
		player.connection.send(new ClientboundSetSubtitleTextPacket(
				Component.translatable("message.projecthero.sentry.subtitle").withStyle(ChatFormatting.DARK_GRAY)));
		player.displayClientMessage(Component.translatable("message.projecthero.sentry.acquired").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.sentry.acquired_hint").withStyle(ChatFormatting.GRAY), false);
		return true;
	}

	public static void revoke(ServerPlayer player) {
		if (state(player).inTakeover()) {
			VoidTakeover.end(player, VoidTakeover.End.REVOKED);
		}
		SentryFlight.stop(player);
		save(player, new SentryState());
		reconcile(player);
		forget(player.getUUID());
	}

	/** The running moves end (death, a new world, leaving); the form stays. */
	public static void clearTransient(ServerPlayer player) {
		SentryState s = peek(player);
		if (s == null) {
			return;
		}
		SentryFlight.stop(player);
		SentryMoves.forget(player.getUUID());
		VoidMoves.forget(player.getUUID());
		if (s.beamStart != 0L || s.sunsStart != 0L || s.shieldUntil != 0L || s.nightfallUntil != 0L || s.gripTarget >= 0 || s.calming
				|| s.flying) {
			SentryState n = state(player).copy();
			n.beamStart = 0L;
			n.sunsStart = 0L;
			n.shieldUntil = 0L;
			n.nightfallUntil = 0L;
			n.gripTarget = -1;
			n.calming = false;
			n.flying = false;
			save(player, n);
		}
		reconcile(player);
	}

	/** Void Form on / off (Shift+V, V Return, the takeover). */
	static void setVoidForm(ServerPlayer player, boolean on) {
		SentryState n = state(player).copy();
		if (n.voidForm == on) {
			return;
		}
		n.voidForm = on;
		n.beamStart = 0L;
		n.sunsStart = 0L;
		n.gripTarget = -1;
		n.calming = false;
		save(player, n);
		SentryMoves.forget(player.getUUID());
		reconcile(player);
	}

	// ---------------------------------------------------------------- stats

	public static void reconcile(ServerPlayer player) {
		SentryState s = peek(player);
		if (s == null || !s.hasPower) {
			PowerToggles.clearModifier(player, Attributes.MAX_HEALTH, HEALTH_ID);
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID);
			if (player.getHealth() > player.getMaxHealth()) {
				player.setHealth(player.getMaxHealth());
			}
			return;
		}
		boolean dark = s.voidForm || s.inTakeover();
		PowerToggles.modifier(player, Attributes.MAX_HEALTH, HEALTH_ID,
				dark ? SentryConfig.VOID_HEALTH_BONUS : SentryConfig.SENTRY_HEALTH_BONUS, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, SentryConfig.SPEED_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID,
				dark ? SentryConfig.VOID_MELEE_BONUS : SentryConfig.SENTRY_MELEE_BONUS, AttributeModifier.Operation.ADD_VALUE);
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	// ---------------------------------------------------------------- tick

	/** Every player, every tick (from {@code AbilityRouter.serverTick}): cheap unless he is the Sentry. */
	public static void tick(ServerPlayer player) {
		SentryState s = player.getAttachedOrElse(SentryAttachments.STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		long now = now(player);
		Long prev = LAST_TICK.put(player.getUUID(), now);
		if (prev != null && prev == now) {
			return;
		}
		if (s.inTakeover()) {
			VoidTakeover.tick(player);
			return;
		}
		player.getFoodData().setExhaustion(0f);
		if (player.tickCount % 20 == 0) {
			tickMeters(player);
			reconcile(player);
		} else if (s.calming && player.tickCount % 5 == 0) {
			// Calm drains in quarter steps so the bar moves smoothly
			SentryState n = state(player).copy();
			n.voidLevel = Math.max(0f, n.voidLevel + SentryConfig.VOID_CALM_PER_SECOND / 4f);
			save(player, n);
		}
		if (player.tickCount % SentryConfig.REGEN_INTERVAL == 0 && player.getHealth() < player.getMaxHealth() && state(player).sun > 0f) {
			player.heal(1f);
			if (!free(player)) {
				SentryState n = state(player).copy();
				n.sun = Math.max(0f, n.sun - SentryConfig.REGEN_SUN);
				save(player, n);
			}
		}
		SentryMoves.tick(player);
		VoidMoves.tick(player);
		SentryFlight.tick(player);
	}

	/** Once a second: the sun fills Sun and moves the Void; Calm and Void Form move it too. */
	public static void tickMeters(ServerPlayer player) {
		SentryState s = state(player);
		Sun sun = sun(player, false);
		float voidDelta = voidPerSecond(sun);
		if (s.calming) {
			voidDelta += SentryConfig.VOID_CALM_PER_SECOND / 4f; // the other 3/4 come in the quarter steps
		}
		if (s.voidForm) {
			voidDelta += SentryConfig.VOID_FORM_PER_SECOND;
			if (sun == Sun.DIRECT) {
				voidDelta -= SentryConfig.VOID_DAYLIGHT_PER_SECOND; // daylight doesn't cool the Void Form
			}
		}
		SentryState n = s.copy();
		n.sun = Math.min(SentryConfig.SUN_MAX, n.sun + sunPerSecond(sun));
		if (!(voidDelta > 0f && free(player))) {
			n.voidLevel = Math.max(0f, Math.min(SentryConfig.VOID_MAX, n.voidLevel + voidDelta));
		}
		save(player, n);
		if (n.voidLevel >= SentryConfig.VOID_MAX) {
			VoidTakeover.tryBegin(player);
		}
	}

	public static void onPlayerJoin(ServerPlayer player) {
		SentryState s = peek(player);
		if (s == null) {
			return;
		}
		if (s.inTakeover()) {
			// left in the Void's grip (a crash, a server stop): the takeover ends cleanly and the world comes back
			VoidTakeover.end(player, VoidTakeover.End.LOGGED_OUT);
		}
		clearTransient(player);
	}

	/** After a death: the Void lets go (the takeover already ended when he died). */
	public static void onPlayerRespawn(ServerPlayer player) {
		SentryState s = peek(player);
		if (s == null || !s.hasPower) {
			return;
		}
		clearTransient(player);
		reconcile(player);
	}
}
