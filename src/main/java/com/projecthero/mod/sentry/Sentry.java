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
 *   <li><b>Void</b> (the red bar): the danger. Round 3 (user): it rises only from using the powers (each move's own gain, and being
 *   merged with the Void); it drains slowly on its own, faster in sunlight, a lot in bed (a night slept through: -40) and while doing calm
 *   everyday things ({@link SentryCalmTasks}: fishing, harvesting, trading, breeding, music). 50 whispers, 75 misfires, 90 the sky dims and
 *   hits leave burnt shadows, 100 the Void takes over ({@link VoidTakeover}).</li>
 *   <li><b>The body</b>: 40 max health in both forms, 70% less damage taken (75% merged), +12 melee (+16), +30% speed, Sun-fed healing.</li>
 *   <li><b>v0.17.5 rework</b>: the suit is a craftable item now ({@link com.projecthero.mod.sentry.item.SentrySuitItem}); worn it slows the
 *   Void (x0.6 gains) and speeds the Sun (x1.5). Let It Out (round 3: H) is a toggle won through a trial ({@link LetItOut}); merged,
 *   every hit does +50% (round 3; was +25%), the Void moves recharge 25% faster, every Void gain is x1.5, and the red-and-black Void suit
 *   appears on him whether he wears the Sentry Suit or not ({@link #look}).</li>
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
		SentryCalmTasks.clearSessionState();
	}

	static void forget(UUID id) {
		LAST_MESSAGE.remove(id);
		LAST_TICK.remove(id);
		PINNED_SUN.remove(id);
		SentryMoves.forget(id);
		VoidMoves.forget(id);
		SentryFlight.forget(id);
		SentryCalmTasks.forget(id);
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

	/** Merged with the Void by choice (Let It Out won), not the takeover. Client-safe. */
	public static boolean merged(Player player) {
		SentryState s = peek(player);
		return s != null && s.hasPower && s.voidForm && !s.inTakeover();
	}

	/** The Sentry Suit (top + trousers) is worn. Client-safe (armour is synced). */
	public static boolean suitWorn(Player player) {
		return com.projecthero.mod.sentry.item.SentrySuitItem.suitWorn(player);
	}

	/** What every Void gain is multiplied by right now: x0.6 in the suit, x1.5 merged with the Void (both: x0.9). */
	public static float voidGainScale(Player player) {
		float k = 1f;
		if (suitWorn(player)) {
			k *= SentryConfig.SUIT_VOID_GAIN;
		}
		if (merged(player)) {
			k *= SentryConfig.MERGED_VOID_GAIN;
		}
		return k;
	}

	/** What the Sun gained a second is multiplied by: x1.5 in the suit. */
	public static float sunGainScale(Player player) {
		return suitWorn(player) ? SentryConfig.SUIT_SUN_GAIN : 1f;
	}

	/** Flying under his own steam (double-tap Space), not carried by the Void. Client-safe: the shared directional flight uses it. */
	public static boolean selfFlying(Player player) {
		SentryState s = peek(player);
		return s != null && s.hasPower && s.flying && !s.inTakeover();
	}

	/**
	 * Round 3 (user: "If player enters the controlled void mode then the red and black sentry suit should appear on the player even if the
	 * player isnt wearing the suit, the suit then disappears after player returns to normal unless they were wearing the suit before"): what
	 * the body shows. Client-safe (the state and the armour are synced); the suit layer and the cape draw from it, the tests read it.
	 */
	public enum Look {
		/** Nothing of his own: the wearer's skin (and armour). */
		NONE,
		/** The Sentry Suit (worn), the comics look. */
		SUIT,
		/** The Void suit over the worn Sentry Suit (merged, or fading back to the comics suit). */
		VOID_OVER_SUIT,
		/** The Void suit over the bare wearer (merged without the suit, or fading back to the normal skin): a visual only, no item. */
		VOID_OVER_SKIN,
		/** The takeover: all black. */
		BLACK
	}

	/** The look now ({@code fade}: how far into the Void the fade is, 0..1 -- see the suit layer's {@code voidFade}). */
	public static Look look(Player player, float fade) {
		if (inTakeover(player)) {
			return Look.BLACK;
		}
		boolean suit = com.projecthero.mod.sentry.item.SentrySuitItem.topWorn(player)
				|| com.projecthero.mod.sentry.item.SentrySuitItem.trousersWorn(player);
		if (!hasPower(player) || (fade <= 0f && !peek(player).voidForm)) { // (merged counts from its first tick, fade 0)
			return suit ? Look.SUIT : Look.NONE;
		}
		return suit ? Look.VOID_OVER_SUIT : Look.VOID_OVER_SKIN;
	}

	/** The look with the fade read off the state (server and client alike: game time, no partial tick). */
	public static Look look(Player player) {
		SentryState s = peek(player);
		float fade = 0f;
		if (s != null && s.hasPower && (s.voidForm || s.formChangedAt != 0L || s.inTakeover())) { // (never merged yet: no fade)
			float k = Math.max(0f, Math.min(1f, (player.level().getGameTime() - s.formChangedAt) / (float) SentryConfig.FORM_FADE_TICKS));
			fade = s.inTakeover() ? 1f : s.voidForm ? k : 1f - k;
		}
		return look(player, fade);
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

	/** Moveset 2: plays a short body pose on every viewer ({@link SentryState#pose}). */
	static void pose(ServerPlayer player, int pose, int ticks) {
		SentryState n = state(player).copy();
		n.pose = pose;
		n.poseAt = now(player);
		n.poseTicks = ticks;
		save(player, n);
	}

	/** The pose playing right now (0 none). Client-safe. */
	public static int poseNow(Player player) {
		SentryState s = peek(player);
		if (s == null || s.pose == 0) {
			return 0;
		}
		long age = now(player) - s.poseAt;
		return age >= 0 && age < s.poseTicks ? s.pose : 0;
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
	 * Adds (or, negative, takes) Void; a gain is scaled by {@link #voidGainScale} (the suit, merging). Reaching 100 hands the body to the Void ({@link VoidTakeover#tryBegin}). The takeover holds the
	 * meter at 100; a creative player's never climbs.
	 */
	public static void addVoid(ServerPlayer player, float amount) {
		SentryState s = state(player);
		if (!s.hasPower || amount == 0f || s.inTakeover() || (amount > 0f && free(player))) {
			return;
		}
		setVoid(player, s.voidLevel + (amount > 0f ? amount * voidGainScale(player) : amount));
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

	/**
	 * The Void's own drift a second where he stands, out of the merged form (round 3: it never rises on its own any more -- a slow drain
	 * everywhere, a little faster in direct sunlight; night and the dark feed nothing).
	 */
	public static float voidPerSecond(Sun sun) {
		return sun == Sun.DIRECT ? SentryConfig.VOID_DRAIN_PER_SECOND + SentryConfig.VOID_DAYLIGHT_PER_SECOND : SentryConfig.VOID_DRAIN_PER_SECOND;
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
		if (!takeover && merged(player)) {
			amount *= SentryConfig.MERGED_DAMAGE; // merged with the Void: +50% (round 3)
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
				|| s.flying || s.letOutTrialStart != 0L || s.pose != 0 || s.boostUntil != 0L || s.senseUntil != 0L || s.shroudUntil != 0L
				|| s.meldUntil != 0L) {
			SentryState n = state(player).copy();
			n.boostUntil = 0L;
			n.senseUntil = 0L;
			n.shroudUntil = 0L;
			n.meldUntil = 0L;
			n.beamStart = 0L;
			n.sunsStart = 0L;
			n.shieldUntil = 0L;
			n.nightfallUntil = 0L;
			n.gripTarget = -1;
			n.calming = false;
			n.flying = false;
			n.letOutTrialStart = 0L;
			n.pose = 0;
			save(player, n);
		}
		reconcile(player);
	}

	/** Void Form on / off (a won Let It Out trial, Shift+V / V back, the takeover). */
	static void setVoidForm(ServerPlayer player, boolean on) {
		SentryState n = state(player).copy();
		if (n.voidForm == on) {
			return;
		}
		n.voidForm = on;
		n.formChangedAt = now(player);
		n.letOutTrialStart = 0L;
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
			SentryCalmTasks.check(player);
			reconcile(player);
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
		LetItOut.tick(player);
	}

	/**
	 * Once a second: the sun fills Sun and moves the Void. Round 3 (user: "only make the players void bar increase when they use their
	 * powers ... sleeping lowers void bar a lot and doing tasks like fishing and stuff should also make it drain faster, void bar should
	 * drain slowly by default"): merged with the Void it climbs on its own (the form is the power: +0.5, x1.5); otherwise it drains (-0.05,
	 * -0.3 in direct sunlight), -1 a second asleep, -0.15 more with a bobber out in the water. The chunks for finished tasks come from
	 * {@link SentryCalmTasks}; a night slept through from {@link #woke}.
	 */
	public static void tickMeters(ServerPlayer player) {
		SentryState s = state(player);
		Sun sun = sun(player, false);
		float voidDelta = s.voidForm ? SentryConfig.VOID_FORM_PER_SECOND : voidPerSecond(sun);
		if (player.isSleeping()) {
			voidDelta += SentryConfig.VOID_SLEEP_PER_SECOND;
		}
		if (SentryCalmTasks.fishingInWater(player)) {
			voidDelta += SentryConfig.VOID_FISHING_PER_SECOND;
		}
		if (voidDelta > 0f) {
			voidDelta *= voidGainScale(player);
		}
		SentryState n = s.copy();
		n.sun = Math.min(SentryConfig.SUN_MAX, n.sun + sunPerSecond(sun) * sunGainScale(player));
		if (!(voidDelta > 0f && free(player))) {
			n.voidLevel = Math.max(0f, Math.min(SentryConfig.VOID_MAX, n.voidLevel + voidDelta));
		}
		save(player, n);
		if (n.voidLevel >= SentryConfig.VOID_MAX) {
			VoidTakeover.tryBegin(player);
		}
	}

	/**
	 * Round 3: he got out of bed ({@code EntitySleepEvents.STOP_SLEEPING}). A night slept through (woken by the morning) takes
	 * {@link SentryConfig#VOID_NIGHT_SLEPT} off at once; the drain while asleep came a second at a time ({@link #tickMeters}).
	 */
	public static void woke(ServerPlayer player, boolean nightSlept) {
		if (!hasPower(player) || inTakeover(player) || !nightSlept) {
			return;
		}
		addVoid(player, SentryConfig.VOID_NIGHT_SLEPT);
		say(player, "message.projecthero.sentry.slept", ChatFormatting.GOLD);
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
