package com.projecthero.mod.starfire;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.starfire.data.StarfireState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.17.3 Starfire (Koriand'r of Tamaran): the single server-side API of the power. Nothing else changes {@link StarfireState}.
 * The numbers are in {@link StarfireConfig}; the moves are {@link StarfireMoves}' (routed by {@link StarfireAbilityManager}), the
 * flight {@link StarfireFlight}'s, the hits taken {@link StarfireDamage}'s.
 *
 * <ul>
 *   <li><b>Passives</b>: 30 health, +6 melee (punches knock back), 20% damage resistance, no fall damage, survives space.</li>
 *   <li><b>Starbolt</b> (0..100) pays for the moves and the flight; sunlight refills it 3x faster, night and the dark slowly.</li>
 *   <li><b>Joy and Fury</b>: one needle. Joy (flying, sun, eating, squadmates) makes flight faster and cheaper; Fury (being hurt, a
 *   squadmate hurt) makes starbolts hit up to 60% harder and flight heavier. Full Fury unlocks Supernova.</li>
 * </ul>
 */
public final class Starfire {
	public static final String KEY = "starfire";

	private static final ResourceLocation HEALTH_ID = PowerToggles.id("starfire_health");
	private static final ResourceLocation ATTACK_ID = PowerToggles.id("starfire_attack");

	/** Starbolt green, her eyes, the beams. */
	public static final int GREEN = 0x5CFF5A;
	public static final int GREEN_DARK = 0x1FA82E;
	/** Supernova's sun colour. */
	public static final int SUN = 0xFFD24A;

	private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();
	/** The food level last tick (eating builds Joy). */
	private static final Map<UUID, Integer> LAST_FOOD = new ConcurrentHashMap<>();
	/** A squadmate within 16 blocks (checked once a second). */
	private static final Map<UUID, Boolean> SQUAD_NEAR = new ConcurrentHashMap<>();

	private Starfire() {
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
		LAST_TICK.clear();
		LAST_FOOD.clear();
		SQUAD_NEAR.clear();
		StarfireAbilityManager.clearSessionState();
		StarfireMoves.clearSessionState();
		StarfireFlight.clearSessionState();
		StarfireRadiance.clearSessionState();
	}

	static void forget(UUID id) {
		LAST_MESSAGE.remove(id);
		LAST_TICK.remove(id);
		LAST_FOOD.remove(id);
		SQUAD_NEAR.remove(id);
		StarfireAbilityManager.forget(id);
		StarfireMoves.forget(id);
		StarfireFlight.forget(id);
	}

	// ---------------------------------------------------------------- state (client and server)

	public static StarfireState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.STARFIRE_STATE);
	}

	static void save(ServerPlayer player, StarfireState state) {
		player.setAttached(ModAttachments.STARFIRE_STATE, state);
	}

	public static StarfireState peek(Player player) {
		return player.getAttachedOrElse(ModAttachments.STARFIRE_STATE, null);
	}

	public static boolean hasPower(Player player) {
		StarfireState s = peek(player);
		return s != null && s.hasPower;
	}

	static long now(Player player) {
		return player.level().getGameTime();
	}

	/** Double-tap Space flight is on. Client-safe. */
	public static boolean isFlying(Player player) {
		StarfireState s = peek(player);
		return s != null && s.hasPower && s.flying;
	}

	public static float starbolt(Player player) {
		StarfireState s = peek(player);
		return s == null ? 0f : s.starbolt;
	}

	public static float mood(Player player) {
		StarfireState s = peek(player);
		return s == null ? 0f : s.mood;
	}

	public static float joy(Player player) {
		StarfireState s = peek(player);
		return s == null ? 0f : s.joy();
	}

	public static float fury(Player player) {
		StarfireState s = peek(player);
		return s == null ? 0f : s.fury();
	}

	/** Full Fury: the eyes blaze and Supernova unlocks. Client-safe. */
	public static boolean fullFury(Player player) {
		StarfireState s = peek(player);
		return s != null && s.hasPower && s.mood >= StarfireConfig.FURY_FULL;
	}

	public static boolean beaming(Player player) {
		StarfireState s = peek(player);
		return s != null && s.hasPower && s.beams;
	}

	public static boolean radiant(Player player) {
		StarfireState s = peek(player);
		return s != null && s.hasPower && s.radiance;
	}

	public static int charging(Player player) {
		StarfireState s = peek(player);
		return s == null || !s.hasPower || s.chargeStart <= 0L ? StarfireState.CHARGE_NONE : s.chargeMove;
	}

	public static int chargeElapsed(Player player) {
		StarfireState s = peek(player);
		return s == null || s.chargeStart <= 0L ? 0 : (int) Math.max(0L, now(player) - s.chargeStart);
	}

	public static boolean diving(Player player) {
		StarfireState s = peek(player);
		return s != null && s.hasPower && s.diveUntil > now(player);
	}

	public static boolean dashing(Player player) {
		StarfireState s = peek(player);
		return s != null && s.hasPower && s.dashUntil > now(player);
	}

	/** The player whose name tag she sees through walls right now (null = nobody). Client-safe. */
	public static UUID bondedWith(Player player) {
		StarfireState s = peek(player);
		if (s == null || !s.hasPower || s.bondWith.isEmpty() || s.bondUntil <= now(player)) {
			return null;
		}
		try {
			return UUID.fromString(s.bondWith);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** Starbolt damage multiplier from Fury: x1 .. x1.6. */
	public static float boltMult(Player player) {
		return 1f + StarfireConfig.FURY_DAMAGE_BONUS * fury(player);
	}

	/** Flight speed multiplier: Joy lifts it, Fury weighs it down. Client-safe. */
	public static double flightSpeedMult(Player player) {
		return 1.0 + StarfireConfig.FLIGHT_JOY_SPEED * joy(player) - StarfireConfig.FLIGHT_FURY_SLOW * fury(player);
	}

	/** Starbolt a second the flight costs right now. */
	public static float flightCostPerSecond(Player player) {
		return StarfireConfig.FLIGHT_COST_PER_SECOND * (1f - StarfireConfig.FLIGHT_JOY_DISCOUNT * joy(player))
				* (1f + StarfireConfig.FLIGHT_FURY_SURCHARGE * fury(player));
	}

	public static int cooldownRemaining(Player player, String id) {
		StarfireState s = peek(player);
		if (s == null) {
			return 0;
		}
		Long ready = s.abilityReadyAt.get(id);
		return ready == null ? 0 : (int) Math.max(0L, ready - now(player));
	}

	public static void startCooldown(ServerPlayer player, String id, int ticks) {
		StarfireState n = state(player).copy();
		n.abilityReadyAt.put(id, now(player) + ticks);
		save(player, n);
	}

	/** v0.17.3 animation round: sets or clears one of the synced animation bits ({@link StarfireState#anim}); never gameplay. */
	static void setAnimBit(ServerPlayer player, int bit, boolean on) {
		StarfireState s = state(player);
		int want = on ? (s.anim | bit) : (s.anim & ~bit);
		if (want != s.anim) {
			StarfireState n = s.copy();
			n.anim = want;
			save(player, n);
		}
	}

	public static void clearCooldowns(ServerPlayer player) {
		StarfireState n = state(player).copy();
		n.abilityReadyAt.clear();
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

	/** Every sound of the power: vanilla, quiet. */
	static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
	}

	static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
	}

	public static DustParticleOptions dust(int rgb, float size) {
		return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f), size);
	}

	// ---------------------------------------------------------------- Starbolt

	/** Spends Starbolt if there is enough ("Not enough Starbolt" otherwise). Creative never pays. */
	public static boolean spend(ServerPlayer player, float amount) {
		StarfireState s = state(player);
		if (!s.hasPower) {
			return false;
		}
		if (player.getAbilities().instabuild || amount <= 0f) {
			return true;
		}
		if (s.starbolt + 1.0e-3f < amount) {
			say(player, "message.projecthero.starfire.no_starbolt", ChatFormatting.GRAY);
			return false;
		}
		StarfireState n = s.copy();
		n.starbolt = Math.max(0f, s.starbolt - amount);
		save(player, n);
		return true;
	}

	public static boolean hasEnergy(ServerPlayer player, float amount) {
		return player.getAbilities().instabuild || state(player).starbolt + 1.0e-3f >= amount;
	}

	/** A bolt's cost with Glorious Radiance's surcharge. */
	public static float boltCost(Player player, float base) {
		return radiant(player) ? base * (1f + StarfireConfig.RADIANCE_BOLT_SURCHARGE) : base;
	}

	public static void setStarbolt(ServerPlayer player, float value) {
		StarfireState n = state(player).copy();
		n.starbolt = Math.max(0f, Math.min(StarfireConfig.STARBOLT_MAX, value));
		save(player, n);
	}

	// ---------------------------------------------------------------- the mood

	public static void setMood(ServerPlayer player, float value) {
		StarfireState n = state(player).copy();
		n.mood = Math.max(-StarfireConfig.MOOD_MAX, Math.min(StarfireConfig.MOOD_MAX, value));
		save(player, n);
	}

	/** Joy builds: the needle moves left (Glorious Radiance doubles it). */
	public static void addJoy(ServerPlayer player, float amount) {
		StarfireState s = state(player);
		if (!s.hasPower || amount <= 0f) {
			return;
		}
		float k = s.radiance ? StarfireConfig.RADIANCE_JOY_MULT : 1f;
		s.mood = Math.max(-StarfireConfig.MOOD_MAX, s.mood - amount * k); // (in place: re-sent with the bar)
	}

	/** Fury builds: the needle moves right. Saved at once (the eyes blaze at full). */
	public static void addFury(ServerPlayer player, float amount) {
		StarfireState s = state(player);
		if (!s.hasPower || amount <= 0f) {
			return;
		}
		StarfireState n = s.copy();
		n.mood = Math.min(StarfireConfig.MOOD_MAX, s.mood + amount);
		save(player, n);
	}

	// ---------------------------------------------------------------- the sun

	public enum Sun { DIRECT, SHADE, NIGHT, DARK }

	/** How much sun reaches her where she stands. */
	public static Sun sun(Player player) {
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
		if (!sky && level.getBrightness(LightLayer.SKY, eye) < 8) {
			return Sun.DARK;
		}
		return day ? Sun.SHADE : Sun.NIGHT;
	}

	public static float regenPerSecond(Sun sun) {
		return switch (sun) {
			case DIRECT -> StarfireConfig.REGEN_SUN;
			case SHADE -> StarfireConfig.REGEN_SHADE;
			case NIGHT -> StarfireConfig.REGEN_NIGHT;
			case DARK -> StarfireConfig.REGEN_DARK;
		};
	}

	// ---------------------------------------------------------------- hits

	/**
	 * One of her moves hits {@code target} for {@code amount}. Always her own damage (the squad veto and boss threat see her).
	 * False when the target can't be harmed or nothing landed.
	 */
	public static boolean hit(ServerPlayer player, LivingEntity target, float amount) {
		return hit(player, target, amount, player.damageSources().playerAttack(player));
	}

	public static boolean hit(ServerPlayer player, LivingEntity target, float amount, DamageSource source) {
		if (target == null || target == player || !target.isAlive() || !HeroTargets.canHarm(player, target)) {
			return false;
		}
		float before = target.getHealth();
		boolean landed;
		MOVE_HIT.set(true);
		try {
			landed = com.projecthero.mod.hero.power.AbilityHelpers.hurtBurst(player, target, source, amount);
		} finally {
			MOVE_HIT.set(false);
		}
		return landed || target.getHealth() < before;
	}

	private static final ThreadLocal<Boolean> MOVE_HIT = ThreadLocal.withInitial(() -> false);

	/** A move's hit is being dealt right now (her punch knockback stays out of it). */
	static boolean inMoveHit() {
		return MOVE_HIT.get();
	}

	/** Her hands: a little in front of each shoulder ({@code side} -1 left, +1 right). */
	public static Vec3 handPos(Player player, int side) {
		Vec3 look = player.getViewVector(1f);
		float yaw = player.getYRot() * ((float) Math.PI / 180f);
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		return player.getEyePosition().add(0, -0.45, 0).add(look.scale(0.5)).add(right.scale(0.35 * side));
	}

	// ---------------------------------------------------------------- the power

	/** The Tamaranean Starstone, the command. False if she already has it (or someone else does, unique powers). */
	public static boolean grant(ServerPlayer player) {
		StarfireState old = state(player);
		if (old.hasPower) {
			return false;
		}
		if (com.projecthero.mod.hero.UniquePowers.refuse(player, KEY)) {
			return false;
		}
		HeroTiers.claimPrimary(player, KEY);
		StarfireState s = new StarfireState();
		s.hasPower = true;
		s.starbolt = StarfireConfig.STARBOLT_MAX;
		s.mood = -30f; // she arrives happy
		s.villagers.putAll(old.villagers);
		save(player, s);
		reconcile(player);
		player.setHealth(player.getMaxHealth());
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		sound(level, c, SoundEvents.BEACON_ACTIVATE, 0.5f, 1.4f);
		level.sendParticles(dust(GREEN, 1.4f), c.x, c.y, c.z, 40, 0.5, 0.9, 0.5, 0.0);
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
				Component.translatable("message.projecthero.starfire.title").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)));
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
				Component.translatable("message.projecthero.starfire.subtitle").withStyle(ChatFormatting.LIGHT_PURPLE)));
		player.displayClientMessage(Component.translatable("message.projecthero.starfire.acquired").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.starfire.acquired_hint").withStyle(ChatFormatting.GRAY), false);
		return true;
	}

	public static void revoke(ServerPlayer player) {
		clearTransient(player);
		StarfireState old = state(player);
		StarfireState n = new StarfireState();
		n.villagers.putAll(old.villagers);
		save(player, n);
		reconcile(player);
		forget(player.getUUID());
	}

	/** Death, a new world, leaving: the flight, the holds, the beams, a carried creature and the light end. */
	public static void clearTransient(ServerPlayer player) {
		StarfireState s = peek(player);
		if (s == null || !s.hasPower) {
			return;
		}
		StarfireMoves.endAll(player);
		StarfireFlight.stop(player);
		StarfireRadiance.stop(player.getUUID(), player.getServer());
		StarfireState n = state(player).copy();
		n.flying = false;
		n.beams = false;
		n.chargeMove = StarfireState.CHARGE_NONE;
		n.chargeStart = 0L;
		n.diveUntil = 0L;
		n.dashUntil = 0L;
		n.anim &= ~StarfireState.ANIM_CARRYING;
		save(player, n);
		StarfireMoves.forget(player.getUUID());
		reconcile(player);
	}

	public static void onPlayerJoin(ServerPlayer player) {
		if (hasPower(player)) {
			clearTransient(player);
		}
	}

	/** A death: the bar comes back full and the needle centres. Cooldowns stay; Glorious Radiance is off. */
	public static void onPlayerRespawn(ServerPlayer player) {
		if (!hasPower(player)) {
			return;
		}
		clearTransient(player);
		StarfireState n = state(player).copy();
		n.starbolt = StarfireConfig.STARBOLT_MAX;
		n.mood = 0f;
		n.radiance = false;
		save(player, n);
		reconcile(player);
	}

	// ---------------------------------------------------------------- stats

	public static void reconcile(ServerPlayer player) {
		StarfireState s = peek(player);
		if (s == null || !s.hasPower) {
			PowerToggles.clearModifier(player, Attributes.MAX_HEALTH, HEALTH_ID);
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID);
			if (player.getHealth() > player.getMaxHealth()) {
				player.setHealth(player.getMaxHealth());
			}
			return;
		}
		PowerToggles.modifier(player, Attributes.MAX_HEALTH, HEALTH_ID, StarfireConfig.HEALTH_BONUS, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID, StarfireConfig.MELEE_BONUS, AttributeModifier.Operation.ADD_VALUE);
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	// ---------------------------------------------------------------- tick

	public static void tick(ServerPlayer player) {
		StarfireState s = player.getAttachedOrElse(ModAttachments.STARFIRE_STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		long now = now(player);
		Long prev = LAST_TICK.put(player.getUUID(), now);
		if (prev != null && prev == now) {
			return;
		}
		tickBar(player, s, now);
		tickMood(player, s, now);
		if (player.tickCount % 20 == 0) {
			reconcile(player);
		}
		// space: she never runs out of air
		if (player.getAirSupply() < player.getMaxAirSupply()) {
			player.setAirSupply(player.getMaxAirSupply());
		}
		if (player.getTicksFrozen() > 0) {
			player.setTicksFrozen(0);
		}
		StarfireFlight.tick(player);
		StarfireMoves.tick(player);
		StarfireAbilityManager.tick(player);
		if (radiant(player)) {
			StarfireRadiance.tick(player);
		}
		StarfireState cur = state(player);
		if (!cur.bondWith.isEmpty() && cur.bondUntil <= now) {
			StarfireState n = cur.copy();
			n.bondWith = "";
			n.bondUntil = 0L;
			save(player, n);
		}
		if (now % StarfireConfig.SYNC_INTERVAL == 0) {
			save(player, state(player).copy());
		}
	}

	/** The Starbolt bar: refilled by the sun (in place; re-sent every few ticks). */
	static void tickBar(ServerPlayer player, StarfireState s, long now) {
		float per = regenPerSecond(sun(player)) * com.projecthero.mod.titans.TitansTogether.regenMult(player); // v0.17.3: Titans Together +20%
		s.starbolt = Math.min(StarfireConfig.STARBOLT_MAX, s.starbolt + per / 20f);
	}

	/** The needle: Joy from flying, the sun, squadmates and food; then it settles toward the middle. */
	static void tickMood(ServerPlayer player, StarfireState s, long now) {
		float joy = 0f;
		if (s.flying) {
			joy += StarfireConfig.JOY_FLYING;
		}
		if (sun(player) == Sun.DIRECT) {
			joy += StarfireConfig.JOY_SUN;
		}
		if (now % 20 == 0 || !SQUAD_NEAR.containsKey(player.getUUID())) {
			SQUAD_NEAR.put(player.getUUID(), squadmateNear(player));
		}
		if (SQUAD_NEAR.getOrDefault(player.getUUID(), false)) {
			joy += StarfireConfig.JOY_SQUAD;
		}
		int food = player.getFoodData().getFoodLevel();
		Integer last = LAST_FOOD.put(player.getUUID(), food);
		float eaten = last != null && food > last ? (food - last) * StarfireConfig.JOY_PER_FOOD : 0f;
		float k = s.radiance ? StarfireConfig.RADIANCE_JOY_MULT : 1f;
		s.mood -= (joy / 20f + eaten) * k;
		// settle toward the middle
		float settle = StarfireConfig.MOOD_SETTLE / 20f;
		if (joy <= 0f) {
			if (s.mood > settle) {
				s.mood -= settle;
			} else if (s.mood < -settle) {
				s.mood += settle;
			}
		} else if (s.mood > settle) {
			s.mood -= settle; // being happy also calms her
		}
		s.mood = Math.max(-StarfireConfig.MOOD_MAX, Math.min(StarfireConfig.MOOD_MAX, s.mood));
	}

	static boolean squadmateNear(ServerPlayer player) {
		for (ServerPlayer o : player.serverLevel().players()) {
			if (o != player && o.isAlive() && !o.isSpectator() && o.distanceToSqr(player) < StarfireConfig.SQUAD_RANGE * StarfireConfig.SQUAD_RANGE
					&& com.projecthero.mod.squad.Squads.areAllies(player, o)) {
				return true;
			}
		}
		return false;
	}
}
