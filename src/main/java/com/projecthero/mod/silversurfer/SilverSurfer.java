package com.projecthero.mod.silversurfer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.silversurfer.data.SilverSurferState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * The single server-side API for the Silver Surfer (Norrin Radd, the Power Cosmic), the v0.17.1 Hero-Tier power. Nothing
 * else touches {@link SilverSurferState}; every change re-saves through {@link ServerPlayer#setAttached}. The moves live
 * in {@link SilverSurferAbilities}, the board flight in {@link SilverSurferFlight}, the damage rules in
 * {@link SilverSurferDamage}, Transmutation's statues in {@link SilverSurferStatues}, the numbers in
 * {@link SilverSurferConfig}.
 *
 * <h2>How it works</h2>
 * Right-clicking the Silver Surfer's Board (Galactus's reward, or the late-game recipe) floods the Power Cosmic into the
 * holder: a flash, the board bonds to him and he becomes the Surfer (one Primary power, as always). He looks like the
 * Herald from the Galactus event (the same skin, chrome sheen and glow). Double-tap jump and the board flies up under his
 * feet: the fastest flight in the mod.
 *
 * <h2>The Power Cosmic</h2>
 * 0..100, refilling 6 a second under open sky and 1.5 underground or indoors.
 *
 * <h2>Passives</h2>
 * No fall, fire, drowning or suffocation damage; no hunger drain; Regeneration I; he breathes anywhere.
 */
public final class SilverSurfer {
	public static final String KEY = "silver_surfer";

	public static final DustParticleOptions SILVER = new DustParticleOptions(new Vector3f(0.88f, 0.92f, 1.0f), 1.3f);
	public static final DustParticleOptions SILVER_BIG = new DustParticleOptions(new Vector3f(0.92f, 0.95f, 1.0f), 2.4f);
	public static final DustParticleOptions COSMIC = new DustParticleOptions(new Vector3f(0.55f, 0.75f, 1.0f), 1.6f);

	private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();
	/** Whether he stands under open sky (re-checked once a second). */
	private static final Map<UUID, Boolean> UNDER_SKY = new ConcurrentHashMap<>();

	private SilverSurfer() {
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
		LAST_TICK.clear();
		UNDER_SKY.clear();
		SilverSurferAbilities.clearSessionState();
		SilverSurferAbilityManager.clearSessionState();
		SilverSurferFlight.clearSessionState();
		SilverSurferStatues.clearSessionState();
	}

	// ---------------------------------------------------------------- state

	public static SilverSurferState state(ServerPlayer player) {
		return player.getAttachedOrCreate(SilverSurferAttachments.STATE);
	}

	static void save(ServerPlayer player, SilverSurferState state) {
		player.setAttached(SilverSurferAttachments.STATE, state);
	}

	public static SilverSurferState peek(Player player) {
		return player.getAttachedOrElse(SilverSurferAttachments.STATE, null);
	}

	public static boolean hasPower(Player player) {
		SilverSurferState s = peek(player);
		return s != null && s.hasPower;
	}

	/** On the board (client-safe). */
	public static boolean surfing(Player player) {
		SilverSurferState s = peek(player);
		return s != null && s.hasPower && s.surfing;
	}

	/** Shift+X Light-Speed is running (client-safe). */
	public static boolean lightSpeed(Player player) {
		SilverSurferState s = peek(player);
		return s != null && s.hasPower && s.surfing && s.lightSpeedUntil > player.level().getGameTime();
	}

	/** Shift+Z Molecular Phase is running (client-safe: the collision mixin reads it on both sides). */
	public static boolean phasing(Player player) {
		SilverSurferState s = peek(player);
		return s != null && s.hasPower && s.phaseUntil > player.level().getGameTime();
	}

	public static boolean barrierUp(Player player) {
		SilverSurferState s = peek(player);
		return s != null && s.hasPower && s.barrierUntil > player.level().getGameTime() && s.barrierHp > 0f;
	}

	public static boolean awareness(Player player) {
		SilverSurferState s = peek(player);
		return s != null && s.hasPower && s.awareness;
	}

	public static float cosmic(Player player) {
		SilverSurferState s = peek(player);
		return s == null ? 0f : s.cosmic;
	}

	public static int cooldownRemaining(Player player, String abilityId) {
		SilverSurferState s = peek(player);
		if (s == null) {
			return 0;
		}
		Long ready = s.abilityReadyAt.get(abilityId);
		return ready == null ? 0 : (int) Math.max(0L, ready - player.level().getGameTime());
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

	// ---------------------------------------------------------------- the Power Cosmic

	public static boolean spendCosmic(ServerPlayer player, float cost) {
		if (cost <= 0f) {
			return true;
		}
		SilverSurferState s = state(player);
		if (s.cosmic + 1.0e-3f < cost) {
			return false;
		}
		SilverSurferState n = s.copy();
		n.cosmic = Math.max(0f, Math.min(SilverSurferConfig.COSMIC_MAX, s.cosmic) - cost);
		save(player, n);
		return true;
	}

	public static void setCosmic(ServerPlayer player, float value) {
		SilverSurferState n = state(player).copy();
		n.cosmic = Math.max(0f, Math.min(SilverSurferConfig.COSMIC_MAX, value));
		save(player, n);
	}

	/** Under open sky (cached a second; the gametests and the first tick ask directly). */
	public static boolean underSky(ServerPlayer player) {
		Level level = player.level();
		if (!level.dimensionType().hasSkyLight() || level.dimensionType().hasCeiling()) {
			return false;
		}
		return level.canSeeSky(BlockPos.containing(player.getEyePosition()));
	}

	/** The refill a second where he is now. */
	public static float regenPerSecond(ServerPlayer player) {
		Boolean sky = UNDER_SKY.get(player.getUUID());
		if (sky == null) {
			sky = underSky(player);
			UNDER_SKY.put(player.getUUID(), sky);
		}
		return sky ? SilverSurferConfig.COSMIC_REGEN_SKY : SilverSurferConfig.COSMIC_REGEN_COVERED;
	}

	// ---------------------------------------------------------------- grant / revoke

	/** The board / the command. False if the player is already the Surfer or the power is taken (unique powers). */
	public static boolean grant(ServerPlayer player) {
		if (state(player).hasPower) {
			return false;
		}
		if (com.projecthero.mod.hero.UniquePowers.refuse(player, KEY)) {
			return false;
		}
		HeroTiers.claimPrimary(player, KEY);
		long now = player.level().getGameTime();
		SilverSurferState s = new SilverSurferState();
		s.hasPower = true;
		s.cosmic = SilverSurferConfig.COSMIC_MAX;
		s.animId = SilverSurferState.ANIM_BOND;
		s.animStart = now;
		save(player, s);
		bondFx(player);
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
				Component.translatable("message.projecthero.silver_surfer.title").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)));
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
				Component.translatable("message.projecthero.silver_surfer.subtitle").withStyle(ChatFormatting.AQUA)));
		player.displayClientMessage(Component.translatable("message.projecthero.silver_surfer.acquired")
				.withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.silver_surfer.acquired_hint")
				.withStyle(ChatFormatting.GRAY), false);
		return true;
	}

	/** The Power Cosmic floods in: a silver flash, a column of light, a chime. */
	private static void bondFx(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 2, 0, 0, 0, 0);
		level.sendParticles(SILVER_BIG, c.x, c.y, c.z, 60, 0.6, 1.2, 0.6, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 50, 0.3, 1.5, 0.3, 0.12);
		for (int i = 0; i < 24; i++) {
			level.sendParticles(COSMIC, c.x, c.y + i * 0.5, c.z, 3, 0.15, 0.1, 0.15, 0.0);
		}
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 1.4f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 0.7f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6f, 1.6f);
		player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false, false));
	}

	public static void revoke(ServerPlayer player) {
		SilverSurferAbilities.clear(player);
		SilverSurferFlight.stop(player, false);
		save(player, new SilverSurferState());
		player.removeEffect(MobEffects.REGENERATION);
		forget(player.getUUID());
	}

	private static void forget(UUID id) {
		LAST_MESSAGE.remove(id);
		LAST_TICK.remove(id);
		UNDER_SKY.remove(id);
		SilverSurferAbilityManager.forget(id);
	}

	static boolean canAct(ServerPlayer player) {
		SilverSurferState s = state(player);
		return s.hasPower && player.isAlive() && !player.isSpectator();
	}

	// ---------------------------------------------------------------- tick

	public static void tick(ServerPlayer player) {
		SilverSurferState s = peek(player);
		if (s == null || !s.hasPower) {
			return;
		}
		long now = player.level().getGameTime();
		Long prev = LAST_TICK.put(player.getUUID(), now);
		if (prev != null && prev == now) {
			return;
		}
		if (player.tickCount % 20 == 0) {
			UNDER_SKY.put(player.getUUID(), underSky(player));
		}
		if (now % 5 == 0) {
			tickCosmic(player, peek(player));
		}
		tickPassives(player);
		SilverSurferFlight.tick(player, now);
		SilverSurferAbilities.tick(player, now);
	}

	private static void tickCosmic(ServerPlayer player, SilverSurferState s) {
		float cur = Float.isNaN(s.cosmic) ? 0f : s.cosmic;
		float rate = regenPerSecond(player);
		if (s.surfing && player.isSprinting()) {
			rate -= SilverSurferConfig.SPRINT_SURF_COST_PER_SECOND;
		}
		if (s.awareness) {
			rate -= SilverSurferConfig.AWARENESS_COST_PER_SECOND;
		}
		if (s.beamStart != 0L || s.barrierUntil > player.level().getGameTime()) {
			rate = 0f; // channelling: no refill (their own upkeep is spent by the moves)
		}
		float next = Math.max(0f, Math.min(SilverSurferConfig.COSMIC_MAX, cur + rate / 4f));
		if (next != s.cosmic) {
			SilverSurferState n = s.copy();
			n.cosmic = next;
			save(player, n);
		}
		if (s.awareness && next <= 0f) {
			SilverSurferAbilities.setAwareness(player, false);
		}
	}

	/** Breathes anywhere, never burns, never hungers, Regeneration I. */
	private static void tickPassives(ServerPlayer player) {
		if (player.getAirSupply() < player.getMaxAirSupply()) {
			player.setAirSupply(player.getMaxAirSupply());
		}
		if (player.isOnFire()) {
			player.clearFire();
		}
		if (player.getFoodData().getExhaustionLevel() > 0f) {
			player.getFoodData().setExhaustion(0f);
		}
		MobEffectInstance regen = player.getEffect(MobEffects.REGENERATION);
		if (regen == null || (regen.getAmplifier() == 0 && regen.getDuration() < SilverSurferConfig.REGEN_REFRESH)) {
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, SilverSurferConfig.REGEN_REFRESH * 2, 0, true, false, true));
		}
	}

	// ---------------------------------------------------------------- lifecycle

	public static void onPlayerJoin(ServerPlayer player) {
		SilverSurferState s = state(player);
		if (!s.hasPower) {
			return;
		}
		long now = player.level().getGameTime();
		SilverSurferState n = s.copy();
		n.surfing = false;
		n.lightSpeedUntil = 0L;
		n.barrierUntil = 0L;
		n.phaseUntil = 0L;
		n.beamStart = 0L;
		n.beamEnd = java.util.List.of();
		n.ramUntil = 0L;
		n.animId = SilverSurferState.ANIM_NONE;
		n.cosmic = Float.isNaN(n.cosmic) ? 0f : Math.max(0f, Math.min(SilverSurferConfig.COSMIC_MAX, n.cosmic));
		n.abilityReadyAt.entrySet().removeIf(e -> e.getValue() > now + SilverSurferConfig.METEOR_COOLDOWN + 20L);
		save(player, n);
	}

	public static void onPlayerRespawn(ServerPlayer player) {
		SilverSurferState s = state(player);
		if (!s.hasPower) {
			return;
		}
		SilverSurferState n = s.copy();
		n.surfing = false;
		n.lightSpeedUntil = 0L;
		n.barrierUntil = 0L;
		n.phaseUntil = 0L;
		n.beamStart = 0L;
		n.beamEnd = java.util.List.of();
		n.ramUntil = 0L;
		n.animId = SilverSurferState.ANIM_NONE;
		n.cosmic = Math.max(n.cosmic, SilverSurferConfig.COSMIC_MAX * 0.5f);
		n.abilityReadyAt.clear();
		save(player, n);
	}

	/** Death / logout / dimension change: drop everything transient (the power and the Power Cosmic stay). */
	public static void clearTransient(ServerPlayer player) {
		SilverSurferAbilities.clear(player);
		SilverSurferFlight.stop(player, false);
		forget(player.getUUID());
		SilverSurferState s = peek(player);
		if (s != null && s.hasPower && (s.beamStart != 0L || s.barrierUntil != 0L || s.phaseUntil != 0L || s.lightSpeedUntil != 0L)) {
			SilverSurferState n = s.copy();
			n.beamStart = 0L;
			n.beamEnd = java.util.List.of();
			n.barrierUntil = 0L;
			n.phaseUntil = 0L;
			n.lightSpeedUntil = 0L;
			n.ramUntil = 0L;
			save(player, n);
		}
	}

	static void setAnim(ServerPlayer player, int animId) {
		SilverSurferState n = state(player).copy();
		n.animId = animId;
		n.animStart = player.level().getGameTime();
		save(player, n);
	}
}
