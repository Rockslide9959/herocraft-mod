package com.projecthero.mod.kryptonian;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.kryptonian.data.KryptonianState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * The single server-side API for the Kryptonian Hero-Tier power (v0.14.8). Nothing else pokes {@link KryptonianState};
 * every mutator re-saves through {@link ServerPlayer#setAttached}. The moves live in {@link KryptonianAbilities},
 * flight in {@link KryptonianFlight}, the incoming-damage rules in {@link KryptonianDamage}, the weakness in
 * {@link Kryptonite}, the numbers in {@link KryptonianConfig}.
 *
 * <h2>The body</h2>
 * No form to switch into: a Kryptonian is always on. {@link #reconcile} sets every stat as a fixed-id transient
 * attribute modifier from {@link #empowered} (has the power, not near kryptonite, not burnt out by a Solar Flare), so
 * it is idempotent and runs once a second as a safety net.
 *
 * <h2>Solar Energy</h2>
 * 0..100, fed by the sun (4/s in direct sunlight, 1/s in daytime shade, 0.5/s at night, 0.25/s underground). It pays
 * for the moves; the body's passives never need it. Direct sunlight also heals him (2 HP/s) and feeds him.
 *
 * <p>v0.14.17: it refills all the time (v0.14.16's 5 s delay after a drain is gone); flight (0.1 a second) and the
 * Regeneration III he gets while hurt ({@link #tickRegeneration}, 1 a second) just net against the sun's refill.
 */
public final class Kryptonian {
	public static final String KEY = "kryptonian";

	private static final ResourceLocation HEALTH_ID = PowerToggles.id("kryptonian_health");
	private static final ResourceLocation ATTACK_ID = PowerToggles.id("kryptonian_attack");
	private static final ResourceLocation KNOCKBACK_ID = PowerToggles.id("kryptonian_knockback");
	private static final ResourceLocation SPEED_ID = PowerToggles.id("kryptonian_speed");
	private static final ResourceLocation STEP_ID = PowerToggles.id("kryptonian_step");
	private static final ResourceLocation REACH_ID = PowerToggles.id("kryptonian_reach");
	private static final ResourceLocation JUMP_ID = PowerToggles.id("kryptonian_jump");
	private static final ResourceLocation SAFE_FALL_ID = PowerToggles.id("kryptonian_safe_fall");

	static final DustParticleOptions SUN_GOLD = new DustParticleOptions(new Vector3f(1.0f, 0.82f, 0.25f), 1.4f);
	static final DustParticleOptions KRYPTONITE_GREEN = new DustParticleOptions(new Vector3f(0.35f, 1.0f, 0.3f), 1.2f);

	/** Per-player throttle for action-bar feedback. */
	private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
	/** v0.14.16: game time of the last {@link #tick} (one run per game tick) and of the last once-a-second pass. */
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_SECOND = new ConcurrentHashMap<>();
	/** v0.14.17: gametests pin the sun's refill rate per player (the test world's sun is not predictable). */
	private static final Map<UUID, Float> GAIN_FOR_TESTS = new ConcurrentHashMap<>();

	/** How much sun he is getting right now. */
	public enum Sun {
		DIRECT, SHADE, NIGHT, DARK
	}

	private Kryptonian() {
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
		LAST_TICK.clear();
		LAST_SECOND.clear();
		GAIN_FOR_TESTS.clear();
		Kryptonite.clearSessionState();
		KryptonianFlight.clearSessionState();
		KryptonianAbilities.clearSessionState();
		KryptonianAbilityManager.clearSessionState();
	}

	// ---------------------------------------------------------------- state

	public static KryptonianState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.KRYPTONIAN_STATE);
	}

	static void save(ServerPlayer player, KryptonianState state) {
		player.setAttached(ModAttachments.KRYPTONIAN_STATE, state);
	}

	/** Client-safe (the attachment is synced). */
	public static boolean hasPower(Player player) {
		KryptonianState s = player.getAttachedOrElse(ModAttachments.KRYPTONIAN_STATE, null);
		return s != null && s.hasPower;
	}

	/** Has the power and is at full strength: not near kryptonite and not burnt out by a Solar Flare. Client-safe. */
	public static boolean empowered(Player player) {
		KryptonianState s = player.getAttachedOrElse(ModAttachments.KRYPTONIAN_STATE, null);
		return s != null && s.hasPower && !s.weakened && s.depoweredUntil <= player.level().getGameTime();
	}

	public static boolean isFlying(Player player) {
		KryptonianState s = player.getAttachedOrElse(ModAttachments.KRYPTONIAN_STATE, null);
		return s != null && s.hasPower && s.flying;
	}

	public static boolean weakened(Player player) {
		KryptonianState s = player.getAttachedOrElse(ModAttachments.KRYPTONIAN_STATE, null);
		return s != null && s.hasPower && s.weakened;
	}

	public static boolean depowered(Player player) {
		KryptonianState s = player.getAttachedOrElse(ModAttachments.KRYPTONIAN_STATE, null);
		return s != null && s.hasPower && s.depoweredUntil > player.level().getGameTime();
	}

	public static boolean heatVisionActive(Player player) {
		KryptonianState s = player.getAttachedOrElse(ModAttachments.KRYPTONIAN_STATE, null);
		return s != null && s.hasPower && s.heatVision;
	}

	public static boolean xrayActive(Player player) {
		KryptonianState s = player.getAttachedOrElse(ModAttachments.KRYPTONIAN_STATE, null);
		return s != null && s.hasPower && s.xray; // v0.14.16: a toggle
	}

	/**
	 * v0.14.17: Solar Energy the sun gives him a second where he stands (before any running drain). v0.15.18: each worn
	 * Superman Suit piece soaks up {@link SupermanSuit#SOLAR_BONUS_PER_PIECE} more (the full three-piece suit: +50%).
	 */
	public static float solarGainPerSecond(ServerPlayer player) {
		Float pinned = GAIN_FOR_TESTS.get(player.getUUID());
		float base = pinned != null ? pinned : solarPerSecond(sun(player));
		return base * (1.0f + SupermanSuit.SOLAR_BONUS_PER_PIECE * SupermanSuit.piecesWorn(player));
	}

	/** Gametests only: pins {@link #solarGainPerSecond} for this player ({@code null} = the real sun again). */
	public static void setSolarGainForTests(ServerPlayer player, Float perSecond) {
		if (perSecond == null) {
			GAIN_FOR_TESTS.remove(player.getUUID());
		} else {
			GAIN_FOR_TESTS.put(player.getUUID(), perSecond);
		}
	}

	public static float solar(Player player) {
		KryptonianState s = player.getAttachedOrElse(ModAttachments.KRYPTONIAN_STATE, null);
		return s == null ? 0f : s.solar;
	}

	public static int cooldownRemaining(Player player, String abilityId) {
		KryptonianState s = player.getAttachedOrElse(ModAttachments.KRYPTONIAN_STATE, null);
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

	// ---------------------------------------------------------------- solar energy

	/**
	 * Spends {@code cost} Solar Energy if he has it.
	 */
	public static boolean spendSolar(ServerPlayer player, float cost) {
		KryptonianState s = state(player);
		if (s.solar + 1.0e-3f < cost) {
			return false;
		}
		if (cost <= 0f) {
			return true; // a free move is not a drain
		}
		KryptonianState n = s.copy();
		n.solar = Math.max(0f, Math.min(KryptonianConfig.SOLAR_MAX, s.solar) - cost);
		n.lastDrain = player.level().getGameTime();
		save(player, n);
		return true;
	}

	/** Sets Solar Energy outright (tests / the admin command). Not a drain. */
	public static void setSolar(ServerPlayer player, float value) {
		KryptonianState n = state(player).copy();
		n.solar = Math.max(0f, Math.min(KryptonianConfig.SOLAR_MAX, value));
		save(player, n);
	}

	/** How much sun reaches him where he stands. */
	public static Sun sun(ServerPlayer player) {
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
		if (!sky && level.getBrightness(net.minecraft.world.level.LightLayer.SKY, eye) < 8) {
			return Sun.DARK;
		}
		return day ? Sun.SHADE : Sun.NIGHT;
	}

	private static float solarPerSecond(Sun sun) {
		return switch (sun) {
			case DIRECT -> KryptonianConfig.SOLAR_SUN_PER_SECOND;
			case SHADE -> KryptonianConfig.SOLAR_SHADE_PER_SECOND;
			case NIGHT -> KryptonianConfig.SOLAR_NIGHT_PER_SECOND;
			case DARK -> KryptonianConfig.SOLAR_DARK_PER_SECOND;
		};
	}

	// ---------------------------------------------------------------- grant / revoke

	/** The Kryptonian Crystal / the command. False if the player already has the power. */
	public static boolean grant(ServerPlayer player) {
		if (state(player).hasPower) {
			return false;
		}
		HeroTiers.claimPrimary(player, KEY);
		KryptonianState s = new KryptonianState();
		s.hasPower = true;
		s.solar = KryptonianConfig.SOLAR_MAX;
		save(player, s);
		reconcile(player);
		player.setHealth(player.getMaxHealth());
		tickRegeneration(player); // whole now: no Regeneration
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		level.sendParticles(SUN_GOLD, c.x, c.y, c.z, 60, 0.6, 1.0, 0.6, 0.02);
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 40, 0.4, 1.2, 0.4, 0.08);
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 1.2f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.7f, 1.4f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5f, 0.6f);
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
				Component.translatable("message.projecthero.kryptonian.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
				Component.translatable("message.projecthero.kryptonian.subtitle").withStyle(ChatFormatting.YELLOW)));
		player.displayClientMessage(Component.translatable("message.projecthero.kryptonian.acquired")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.kryptonian.acquired_hint")
				.withStyle(ChatFormatting.YELLOW), false);
		return true;
	}

	public static void revoke(ServerPlayer player) {
		KryptonianFlight.stop(player, false);
		KryptonianAbilities.clear(player);
		KryptonianAbilities.setXray(player, false);
		Kryptonite.clear(player.getUUID());
		save(player, new KryptonianState());
		reconcile(player);
		LAST_MESSAGE.remove(player.getUUID());
		LAST_TICK.remove(player.getUUID());
		LAST_SECOND.remove(player.getUUID());
	}

	// ---------------------------------------------------------------- stats

	/** Brings every attribute modifier in line with {@link #empowered}. Idempotent (fixed ids); safe to call any time. */
	public static void reconcile(ServerPlayer player) {
		if (!empowered(player)) {
			PowerToggles.clearModifier(player, Attributes.MAX_HEALTH, HEALTH_ID);
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID);
			PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID);
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
			PowerToggles.clearModifier(player, Attributes.STEP_HEIGHT, STEP_ID);
			PowerToggles.clearModifier(player, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID);
			PowerToggles.clearModifier(player, Attributes.JUMP_STRENGTH, JUMP_ID);
			PowerToggles.clearModifier(player, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL_ID);
			reconcileRegeneration(player, false);
			if (player.getHealth() > player.getMaxHealth()) {
				player.setHealth(player.getMaxHealth());
			}
			return;
		}
		PowerToggles.modifier(player, Attributes.MAX_HEALTH, HEALTH_ID, KryptonianConfig.HEALTH_BONUS, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID, KryptonianConfig.ATTACK_BONUS, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, KryptonianConfig.KNOCKBACK_RESISTANCE,
				AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, KryptonianConfig.SPEED_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		PowerToggles.modifier(player, Attributes.STEP_HEIGHT, STEP_ID, KryptonianConfig.STEP_HEIGHT_BONUS, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID, KryptonianConfig.REACH_BONUS,
				AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.JUMP_STRENGTH, JUMP_ID, jumpVelocityModifier(KryptonianConfig.JUMP_BLOCKS),
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		// the client predicts fall damage off this; the server cancels it outright either way
		PowerToggles.modifier(player, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL_ID, 1000.0, AttributeModifier.Operation.ADD_VALUE);
		reconcileRegeneration(player, wantsRegeneration(player));
	}

	/**
	 * v0.14.16: Regeneration III only while he is hurt (below max health), empowered and has Solar Energy to pay its 1 a
	 * second ({@link #tickSolar}). Replaces v0.14.15's permanent Regeneration I.
	 */
	private static boolean wantsRegeneration(ServerPlayer player) {
		return empowered(player) && player.isAlive() && player.getHealth() < player.getMaxHealth() && state(player).solar > 0f;
	}

	/** Is the active Regeneration the Kryptonian's own (an infinite ambient instance at our amplifier)? */
	static boolean ownRegeneration(ServerPlayer player) {
		// v0.14.26: no effect any more -- "regenerating" means wanting to (hurt, empowered, Solar Energy left), which is what drains Solar
		return wantsRegeneration(player);
	}

	/**
	 * One infinite, ambient instance (no particles), only touched when wanted and actual disagree: a potion's
	 * Regeneration is left alone. v0.14.15's permanent Regeneration I (infinite, ambient, amplifier 0) is cleared out.
	 */
	private static void reconcileRegeneration(ServerPlayer player, boolean wanted) {
		MobEffectInstance active = player.getEffect(MobEffects.REGENERATION);
		if (active != null && active.isInfiniteDuration() && active.isAmbient() && active.getAmplifier() == 0) {
			player.removeEffect(MobEffects.REGENERATION); // the v0.14.15 one, from an older save
			active = null;
		}
		boolean ours = active != null && active.isInfiniteDuration() && active.isAmbient();
		if (wanted && active == null) {
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, MobEffectInstance.INFINITE_DURATION,
					KryptonianConfig.REGEN_AMPLIFIER, true, false, true));
		} else if (!wanted && ours) {
			player.removeEffect(MobEffects.REGENERATION);
		}
	}

	/** v0.14.16: every tick -- on the moment he is hurt, off the moment he is whole (or out of Solar Energy). */
	private static void tickRegeneration(ServerPlayer player) {
		// v0.14.26: a flat 1 HP every 2.5 s (KryptonianConfig.REGEN_INTERVAL) instead of Regeneration III + sun healing.
		// Any infinite ambient Regeneration from an older version is cleared; a potion's is left alone.
		reconcileRegeneration(player, false);
		if (wantsRegeneration(player) && player.tickCount % KryptonianConfig.REGEN_INTERVAL == 0) {
			player.heal(KryptonianConfig.REGEN_AMOUNT);
		}
	}

	/** The multiplier on the vanilla jump velocity (0.42) that carries a player {@code blocks} high. */
	static double jumpVelocityModifier(double blocks) {
		return verticalSpeedForHeight(blocks) / 0.42 - 1.0;
	}

	/** The launch speed (blocks/tick) that peaks {@code height} blocks up under vanilla gravity and drag. */
	public static double verticalSpeedForHeight(double height) {
		double lo = 0.3;
		double hi = 8.0;
		for (int i = 0; i < 30; i++) {
			double mid = (lo + hi) * 0.5;
			double y = 0.0;
			double vy = mid;
			double max = 0.0;
			for (int t = 0; t < 300 && (vy > 0.0 || t == 0); t++) {
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

	/** Damage-taken factor for everything that is not simply ignored: 0.25 at full strength, 1.0 near kryptonite. */
	public static float damageTakenFactor(ServerPlayer player) {
		return empowered(player) ? 1.0f - KryptonianConfig.DAMAGE_REDUCTION : 1.0f;
	}

	/** Whether he may use a move / fly right now; says why not. */
	static boolean canAct(ServerPlayer player) {
		KryptonianState s = state(player);
		if (!s.hasPower || !player.isAlive() || player.isSpectator()) {
			return false;
		}
		if (s.weakened) {
			say(player, "message.projecthero.kryptonian.weakened", ChatFormatting.GREEN);
			return false;
		}
		long now = player.level().getGameTime();
		if (s.depoweredUntil > now) {
			say(player, "message.projecthero.kryptonian.depowered", ChatFormatting.GRAY, (int) ((s.depoweredUntil - now + 19) / 20));
			return false;
		}
		return true;
	}

	// ---------------------------------------------------------------- tick

	/** Runs for every player every server tick (cheap when he has no power). */
	public static void tick(ServerPlayer player) {
		SupermanSuit.tick(player); // v0.14.9: a non-Kryptonian's Superman Suit pops off
		KryptonianState s = player.getAttachedOrElse(ModAttachments.KRYPTONIAN_STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();
		// v0.14.16: at most once per game tick (a second caller in the same tick -- e.g. a test driving a mock player
		// alongside the server -- would double every per-tick drain)
		Long prevTick = LAST_TICK.put(player.getUUID(), now);
		if (prevTick != null && prevTick == now) {
			return;
		}

		Kryptonite.tick(player);
		s = state(player);

		// v0.14.16: the once-a-second work runs off game time, not tickCount, so it never skips or doubles a second
		Long lastSecond = LAST_SECOND.get(player.getUUID());
		if (lastSecond == null || now - lastSecond >= 20L || now < lastSecond) {
			LAST_SECOND.put(player.getUUID(), now);
			reconcile(player);
			tickSolar(player, s, now);
			s = state(player);
		}
		if (s.depoweredUntil > 0L && s.depoweredUntil <= now) {
			KryptonianState n = s.copy();
			n.depoweredUntil = 0L;
			save(player, n);
			reconcile(player);
			player.displayClientMessage(Component.translatable("message.projecthero.kryptonian.repowered").withStyle(ChatFormatting.GOLD), true);
			s = n;
		}
		boolean strong = !s.weakened && s.depoweredUntil <= now;
		if (strong) {
			tickBody(player, level);
		}
		tickRegeneration(player);
		KryptonianFlight.tick(player);
		KryptonianAbilities.tick(player);
	}

	/**
	 * Once a second: the sun's refill minus the running drains -- flight (0.1/s) and the Regeneration III (1/s). v0.14.17:
	 * it refills all the time (no delay after a drain any more). Saved values above the 100 cap are clamped.
	 */
	private static void tickSolar(ServerPlayer player, KryptonianState s, long now) {
		if (s.weakened || s.depoweredUntil > now) {
			return; // kryptonite drains it instead (Kryptonite.tick); a spent Solar Flare leaves him empty
		}
		KryptonianState n = s.copy();
		boolean changed = false;
		if (n.solar > KryptonianConfig.SOLAR_MAX || Float.isNaN(n.solar)) {
			n.solar = Float.isNaN(n.solar) ? 0f : KryptonianConfig.SOLAR_MAX; // migration: the bar tops out at 100
			changed = true;
		}
		float drain = 0f;
		if (n.flying) {
			drain += KryptonianConfig.FLIGHT_SOLAR_PER_SECOND;
		}
		if (ownRegeneration(player)) {
			drain += KryptonianConfig.REGEN_SOLAR_PER_SECOND;
		}
		float next = Math.max(0f, Math.min(KryptonianConfig.SOLAR_MAX, n.solar + solarGainPerSecond(player) - drain));
		if (drain > 0f) {
			n.lastDrain = now;
		}
		if (next != n.solar || drain > 0f) {
			n.solar = next;
			changed = true;
		}
		if (changed) {
			save(player, n);
		}
		if (n.solar <= 0f) {
			if (drain > 0f) {
				tickRegeneration(player); // out of energy: the Regeneration stops
			}
			if (n.flying) {
				KryptonianFlight.stop(player, false);
				player.displayClientMessage(Component.translatable("message.projecthero.kryptonian.flight_no_solar")
						.withStyle(ChatFormatting.RED), true);
			}
		}
	}

	/** The always-on body: healing in direct sunlight, fed by the sun, fireproof, needs no air. */
	private static void tickBody(ServerPlayer player, ServerLevel level) {
		Sun sun = player.tickCount % 10 == 0 ? sun(player) : null;
		if (sun != null) {
			// v0.14.16: only direct sunlight heals on its own now; anywhere else the Solar-paid Regeneration III does it.
			// The check itself runs every 10 ticks; heal on the ones that line up with the interval.
			// v0.14.26: direct sunlight no longer heals on top -- all healing is the flat 1 HP / 2.5 s (tickRegeneration)
			if (sun == Sun.DIRECT && player.tickCount % KryptonianConfig.SUN_FEED_INTERVAL == 0
					&& player.getFoodData().getFoodLevel() < 20) {
				player.getFoodData().eat(1, 0.5f);
			}
			if (sun == Sun.DIRECT && player.tickCount % 40 == 0 && player.getHealth() < player.getMaxHealth()) {
				// a faint golden shimmer while the sun is healing him
				level.sendParticles(SUN_GOLD, player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.3, 0.6, 0.3, 0.0);
			}
		}
		if (player.isOnFire()) {
			player.clearFire();
		}
		if (player.getAirSupply() < player.getMaxAirSupply()) {
			player.setAirSupply(player.getMaxAirSupply());
		}
	}

	// ---------------------------------------------------------------- effects helpers

	/** A short hidden effect topped up while it runs low (never clobbers a stronger / permanent one). */
	static void keepEffect(ServerPlayer player, Holder<MobEffect> effect, int amplifier, int ticks) {
		MobEffectInstance cur = player.getEffect(effect);
		if (cur != null && (cur.isInfiniteDuration() || cur.getAmplifier() > amplifier)) {
			return;
		}
		if (cur == null || cur.getAmplifier() < amplifier || cur.getDuration() <= ticks / 2) {
			player.addEffect(new MobEffectInstance(effect, ticks, amplifier, false, true, true));
		}
	}

	static void setWeakened(ServerPlayer player, boolean weakened) {
		KryptonianState s = state(player);
		if (s.weakened == weakened) {
			return;
		}
		KryptonianState n = s.copy();
		n.weakened = weakened;
		if (weakened) {
			n.heatVision = false;
			n.breathing = false;
			n.flareChargeStart = 0L;
		}
		save(player, n);
		if (weakened) {
			KryptonianFlight.stop(player, true);
			KryptonianAbilities.clear(player);
			KryptonianAbilities.setXray(player, false); // v0.14.16: no powers means no X-Ray either
			player.displayClientMessage(Component.translatable("message.projecthero.kryptonian.kryptonite").withStyle(ChatFormatting.GREEN), true);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8f, 0.6f);
		} else {
			player.displayClientMessage(Component.translatable("message.projecthero.kryptonian.kryptonite_clear").withStyle(ChatFormatting.GOLD), true);
			player.removeEffect(MobEffects.WEAKNESS);
			player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		}
		reconcile(player);
	}

	/**
	 * After the Solar Flare: POWERLESS for {@code ticks} (30 s) -- {@link #empowered} is false, so no moves, no flight, no
	 * Regeneration, no damage reduction, no passives and no Solar Energy. v0.14.16: the first 6 s also bring Slowness IV,
	 * Blindness and Weakness IV, and any running move (X-Ray included) is switched off.
	 */
	static void depower(ServerPlayer player, int ticks) {
		KryptonianState n = state(player).copy();
		long now = player.level().getGameTime();
		n.depoweredUntil = now + ticks;
		n.solar = 0f;
		n.lastDrain = now;
		n.heatVision = false;
		n.breathing = false;
		save(player, n);
		KryptonianFlight.stop(player, true);
		KryptonianAbilities.clear(player);
		KryptonianAbilities.setXray(player, false);
		int t = KryptonianConfig.FLARE_DEBUFF_TICKS;
		int amp = KryptonianConfig.FLARE_DEBUFF_AMPLIFIER;
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, t, amp, false, true, true));
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, t, amp, false, true, true));
		player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, t, 0, false, true, true));
		reconcile(player);
	}

	// ---------------------------------------------------------------- lifecycle

	public static void onPlayerJoin(ServerPlayer player) {
		KryptonianState s = state(player);
		if (!s.hasPower) {
			return;
		}
		long now = player.level().getGameTime();
		KryptonianState n = s.copy();
		// game time is per-world: nothing carried over from another world may lock the player out
		n.heatVision = false;
		n.flareChargeStart = 0L;
		n.animId = KryptonianState.ANIM_NONE;
		if (n.depoweredUntil > now + KryptonianConfig.FLARE_DEPOWER_TICKS) {
			n.depoweredUntil = 0L;
		}
		n.breathing = false;
		if (n.lastDrain > now) {
			n.lastDrain = 0L;
		}
		// v0.14.16 migration: Solar Energy tops out at 100 -- anything saved above that is clamped down
		n.solar = Float.isNaN(n.solar) ? 0f : Math.max(0f, Math.min(KryptonianConfig.SOLAR_MAX, n.solar));
		n.abilityReadyAt.entrySet().removeIf(e -> e.getValue() > now + 20L * 120L);
		boolean wasFlying = n.flying;
		save(player, n);
		reconcile(player);
		if (wasFlying) {
			KryptonianFlight.resume(player);
		}
	}

	public static void onPlayerRespawn(ServerPlayer player) {
		KryptonianState s = state(player);
		if (!s.hasPower) {
			return;
		}
		KryptonianState n = s.copy();
		n.flying = false;
		n.weakened = false;
		n.heatVision = false;
		n.flareChargeStart = 0L;
		n.depoweredUntil = 0L;
		n.xray = false;
		n.breathing = false;
		n.lastDrain = 0L;
		n.animId = KryptonianState.ANIM_NONE;
		n.solar = Math.min(KryptonianConfig.SOLAR_MAX, Math.max(n.solar, KryptonianConfig.SOLAR_MAX * 0.5f));
		n.abilityReadyAt.clear();
		save(player, n);
		reconcile(player);
		player.setHealth(player.getMaxHealth());
		tickRegeneration(player);
	}

	/** Death / logout / dimension change: drop everything transient (the power and Solar Energy stay). */
	public static void clearTransient(ServerPlayer player) {
		KryptonianAbilities.clear(player);
		Kryptonite.clear(player.getUUID());
		LAST_MESSAGE.remove(player.getUUID());
		LAST_TICK.remove(player.getUUID());
		LAST_SECOND.remove(player.getUUID());
		KryptonianAbilityManager.forget(player.getUUID());
		KryptonianState s = player.getAttachedOrElse(ModAttachments.KRYPTONIAN_STATE, null);
		if (s != null && s.hasPower && (s.heatVision || s.breathing || s.flareChargeStart != 0L || s.animId != KryptonianState.ANIM_NONE)) {
			KryptonianState n = s.copy();
			n.heatVision = false;
			n.breathing = false;
			n.flareChargeStart = 0L;
			n.animId = KryptonianState.ANIM_NONE;
			save(player, n);
		}
	}

	/** Dimension change: flight carries over (re-grants mayfly in the new world). */
	public static void onWorldChange(ServerPlayer player) {
		clearTransient(player);
		if (isFlying(player)) {
			KryptonianFlight.resume(player);
		}
	}

	static void setAnim(ServerPlayer player, int animId) {
		KryptonianState n = state(player).copy();
		n.animId = animId;
		n.animStart = player.level().getGameTime();
		save(player, n);
	}
}
