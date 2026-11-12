package com.projecthero.mod.raven;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.raven.data.RavenState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.17.3 Raven (Teen Titans, daughter of Trigon): the single server-side API of the power. Nothing else changes {@link RavenState}.
 * The numbers are in {@link RavenConfig}; the moves are {@link RavenMoves}' (routed by {@link RavenAbilityManager}); the flight is
 * {@link RavenFlight}'s; the emotional leak is {@link RavenLeak}'s.
 *
 * <ul>
 *   <li><b>Passives</b>: 20 health, 10% damage resistance ({@link RavenDamage}), slow fall whenever she isn't flying.</li>
 *   <li><b>Dark Energy</b> (the Slab, 0..100) pays for the moves, 5 a second back.</li>
 *   <li><b>Composure</b> (the Hairline under it): 100 is calm. Being hurt, big moves and hostile mobs around for a long time push it
 *   toward Rage; it comes back slowly when things are quiet and fast while meditating. Under 30 her emotions leak, under 10 is Rage
 *   (four red eyes, Trigon's Wrath).</li>
 * </ul>
 */
public final class Raven {
	public static final String KEY = "raven";

	private static final ResourceLocation HEALTH_ID = PowerToggles.id("raven_health");
	private static final ResourceLocation SCALE_ID = PowerToggles.id("raven_wrath_scale");

	/** Dark energy colours (dust). */
	public static final int VOID = 0x1A0A26;
	public static final int VIOLET = 0x6A2BB0;
	public static final int RED = 0xC0182A;

	private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();
	/** Last time she was hurt (Composure comes back only after a quiet spell). */
	private static final Map<UUID, Long> LAST_HURT = new ConcurrentHashMap<>();
	/** How many ticks in a row a hostile mob has been near. */
	private static final Map<UUID, Integer> HOSTILE_TICKS = new ConcurrentHashMap<>();

	private Raven() {
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
		LAST_TICK.clear();
		LAST_HURT.clear();
		HOSTILE_TICKS.clear();
		RavenAbilityManager.clearSessionState();
		RavenMoves.clearSessionState();
		RavenFlight.clearSessionState();
		RavenRestore.clearSessionState();
	}

	static void forget(UUID id) {
		LAST_MESSAGE.remove(id);
		LAST_TICK.remove(id);
		LAST_HURT.remove(id);
		HOSTILE_TICKS.remove(id);
		RavenAbilityManager.forget(id);
		RavenMoves.forget(id);
		RavenFlight.forget(id);
	}

	// ---------------------------------------------------------------- state (client and server)

	public static RavenState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.RAVEN_STATE);
	}

	static void save(ServerPlayer player, RavenState state) {
		player.setAttached(ModAttachments.RAVEN_STATE, state);
	}

	public static RavenState peek(Player player) {
		return player.getAttachedOrElse(ModAttachments.RAVEN_STATE, null);
	}

	public static boolean hasPower(Player player) {
		RavenState s = peek(player);
		return s != null && s.hasPower;
	}

	static long now(Player player) {
		return player.level().getGameTime();
	}

	public static float energy(Player player) {
		RavenState s = peek(player);
		return s == null ? 0f : s.energy;
	}

	public static float composure(Player player) {
		RavenState s = peek(player);
		return s == null ? RavenConfig.COMPOSURE_MAX : s.composure;
	}

	public static boolean isFlying(Player player) {
		RavenState s = peek(player);
		return s != null && s.hasPower && s.flying;
	}

	public static boolean meditating(Player player) {
		RavenState s = peek(player);
		return s != null && s.hasPower && s.meditating;
	}

	public static boolean shielding(Player player) {
		RavenState s = peek(player);
		return s != null && s.hasPower && s.shield;
	}

	/** Trigon's Wrath is running. Client-safe. */
	public static boolean inWrath(Player player) {
		RavenState s = peek(player);
		return s != null && s.hasPower && s.wrathUntil > now(player);
	}

	public static int wrathTicksLeft(Player player) {
		RavenState s = peek(player);
		return s == null || !s.hasPower ? 0 : (int) Math.max(0L, s.wrathUntil - now(player));
	}

	/** Under 30 Composure: her emotions leak. Client-safe. */
	public static boolean leaking(Player player) {
		return hasPower(player) && composure(player) < RavenConfig.LEAK_BELOW;
	}

	/** Under 10 Composure (or in the Wrath): Rage -- four red eyes, Shift+Z unlocked. Client-safe. */
	public static boolean rage(Player player) {
		return hasPower(player) && (composure(player) < RavenConfig.RAGE_BELOW || inWrath(player));
	}

	/** Alias of {@link #rage} (Doctor Fate's DoctorFateChaos looks this name up by reflection: Raven at Rage counts as Chaos). */
	public static boolean inRage(Player player) {
		return rage(player);
	}

	/** The white casting eyes. Client-safe. */
	public static boolean casting(Player player) {
		RavenState s = peek(player);
		return s != null && s.hasPower && (s.castUntil > now(player) || s.meditating || s.tkTarget >= 0 || s.shield);
	}

	/** A streak of shadow (Shadow Dash, sunk for Shadow Step): no damage taken, the body hidden. Client-safe. */
	public static boolean inShadow(Player player) {
		RavenState s = peek(player);
		return s != null && s.hasPower && s.shadowUntil > now(player);
	}

	public static int cooldownRemaining(Player player, String id) {
		RavenState s = peek(player);
		if (s == null) {
			return 0;
		}
		Long ready = s.abilityReadyAt.get(id);
		return ready == null ? 0 : (int) Math.max(0L, ready - now(player));
	}

	static void startCooldown(ServerPlayer player, String id, int ticks) {
		RavenState n = state(player).copy();
		n.abilityReadyAt.put(id, now(player) + ticks);
		save(player, n);
	}

	public static void clearCooldowns(ServerPlayer player) {
		RavenState n = state(player).copy();
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

	static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
	}

	public static DustParticleOptions dust(int rgb, float size) {
		return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f), size);
	}

	/** A puff of dark energy (black and violet) at {@code c}. */
	static void darkPuff(ServerLevel level, Vec3 c, int count, double spread) {
		level.sendParticles(dust(VOID, 1.6f), c.x, c.y, c.z, count, spread, spread, spread, 0.0);
		level.sendParticles(dust(VIOLET, 1.1f), c.x, c.y, c.z, Math.max(1, count / 2), spread, spread, spread, 0.0);
	}

	// ---------------------------------------------------------------- the bars

	/** Spends Dark Energy if there is enough. False (nothing spent, "Not enough Dark Energy") if not. Creative never pays. */
	public static boolean spendEnergy(ServerPlayer player, float amount) {
		RavenState s = state(player);
		if (!s.hasPower) {
			return false;
		}
		if (player.getAbilities().instabuild || amount <= 0f) {
			return true;
		}
		if (s.energy + 1.0e-3f < amount) {
			say(player, "message.projecthero.raven.no_energy", ChatFormatting.GRAY);
			return false;
		}
		RavenState n = s.copy();
		n.energy = Math.max(0f, s.energy - amount);
		save(player, n);
		return true;
	}

	public static boolean hasEnergy(ServerPlayer player, float amount) {
		return player.getAbilities().instabuild || state(player).energy + 1.0e-3f >= amount;
	}

	public static void setEnergy(ServerPlayer player, float value) {
		RavenState n = state(player).copy();
		n.energy = Math.max(0f, Math.min(RavenConfig.ENERGY_MAX, value));
		save(player, n);
	}

	public static void setComposure(ServerPlayer player, float value) {
		RavenState n = state(player).copy();
		n.composure = Math.max(0f, Math.min(RavenConfig.COMPOSURE_MAX, value));
		save(player, n);
	}

	/** Pushes Composure toward Rage by {@code amount} (not during the Wrath, which holds it at Rage). */
	public static void unsettle(ServerPlayer player, float amount) {
		RavenState s = state(player);
		if (!s.hasPower || amount <= 0f || s.wrathUntil > now(player)) {
			return;
		}
		setComposure(player, s.composure - amount);
	}

	/** She was hurt for {@code amount}: Composure drops, and the quiet spell starts over. */
	static void onHurt(ServerPlayer player, float amount) {
		LAST_HURT.put(player.getUUID(), now(player));
		unsettle(player, amount * RavenConfig.COMPOSURE_PER_DAMAGE);
	}

	/** Marks a cast: the white eyes for a moment. */
	static void cast(ServerPlayer player) {
		RavenState n = state(player).copy();
		n.castUntil = now(player) + RavenConfig.CAST_EYES_TICKS;
		save(player, n);
	}

	// ---------------------------------------------------------------- hits

	/** Every Raven bolt's multiplier right now: +25% while her emotions leak. */
	public static float boltMult(Player player) {
		return leaking(player) ? RavenConfig.LEAK_BOLT_BONUS : 1f;
	}

	/**
	 * A Raven move hits {@code target} for {@code amount}. Always the player's own damage (the squad veto and boss threat see her).
	 * False when the target can't be harmed (a squadmate, their pet, PvP off) or nothing landed.
	 */
	public static boolean hit(ServerPlayer player, LivingEntity target, float amount) {
		if (target == null || target == player || !HeroTargets.canHarm(player, target)) {
			return false;
		}
		float before = target.getHealth();
		boolean landed = AbilityHelpers.hurtBurst(player, target, player.damageSources().playerAttack(player), amount);
		return landed || target.getHealth() < before;
	}

	/** A boss can't be lifted, grasped, launched or thrown (it still takes the damage). */
	public static boolean isBoss(LivingEntity target) {
		return target.getMaxHealth() >= RavenConfig.BOSS_HEALTH_THRESHOLD || com.projecthero.mod.titanshifter.TitanCombat.isBoss(target);
	}

	// ---------------------------------------------------------------- the power

	/** The Book of Azarath, the command. False if she already has it (or someone else does, unique powers). */
	public static boolean grant(ServerPlayer player) {
		RavenState old = state(player);
		if (old.hasPower) {
			return false;
		}
		if (com.projecthero.mod.hero.UniquePowers.refuse(player, KEY)) {
			return false;
		}
		HeroTiers.claimPrimary(player, KEY);
		RavenState s = new RavenState();
		s.hasPower = true;
		s.energy = RavenConfig.ENERGY_MAX;
		s.composure = RavenConfig.COMPOSURE_MAX;
		save(player, s);
		reconcile(player);
		player.setHealth(player.getMaxHealth());
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		level.playSound(null, c.x, c.y, c.z, RavenSounds.LEARN, SoundSource.PLAYERS, 0.6f, 0.8f);
		level.playSound(null, c.x, c.y, c.z, RavenSounds.CHANT, SoundSource.PLAYERS, 0.5f, 0.6f);
		darkPuff(level, c, 40, 0.8);
		level.sendParticles(ParticleTypes.ENCHANT, c.x, c.y + 0.6, c.z, 60, 0.8, 0.8, 0.8, 0.6);
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 60, 20));
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
				Component.translatable("message.projecthero.raven.title").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)));
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
				Component.translatable("message.projecthero.raven.subtitle").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
		player.displayClientMessage(Component.translatable("message.projecthero.raven.acquired").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.raven.acquired_hint").withStyle(ChatFormatting.GRAY), false);
		return true;
	}

	public static void revoke(ServerPlayer player) {
		clearTransient(player);
		save(player, new RavenState());
		reconcile(player);
		forget(player.getUUID());
	}

	/** Death, a new world, leaving: the holds, the shield, the flight, the debris and the Wrath end. */
	public static void clearTransient(ServerPlayer player) {
		RavenState s = peek(player);
		if (s == null || !s.hasPower) {
			return;
		}
		RavenMoves.endAll(player);
		RavenFlight.stop(player);
		RavenFlight.stopMeditation(player);
		RavenState n = state(player).copy();
		n.flying = false;
		n.meditating = false;
		n.shield = false;
		n.shieldHp = 0f;
		n.wrathUntil = 0L;
		n.shadowUntil = 0L;
		n.tkTarget = -1;
		n.debris = 0;
		save(player, n);
		reconcile(player);
	}

	public static void onPlayerJoin(ServerPlayer player) {
		if (hasPower(player)) {
			clearTransient(player);
		}
	}

	/** A death: both bars come back full and calm. Cooldowns stay. */
	public static void onPlayerRespawn(ServerPlayer player) {
		if (!hasPower(player)) {
			return;
		}
		clearTransient(player);
		RavenState n = state(player).copy();
		n.energy = RavenConfig.ENERGY_MAX;
		n.composure = RavenConfig.COMPOSURE_MAX;
		save(player, n);
		reconcile(player);
	}

	// ---------------------------------------------------------------- stats

	public static void reconcile(ServerPlayer player) {
		RavenState s = peek(player);
		if (s == null || !s.hasPower) {
			PowerToggles.clearModifier(player, Attributes.MAX_HEALTH, HEALTH_ID);
			PowerToggles.clearModifier(player, Attributes.SCALE, SCALE_ID);
			if (player.getHealth() > player.getMaxHealth()) {
				player.setHealth(player.getMaxHealth());
			}
			return;
		}
		PowerToggles.modifier(player, Attributes.MAX_HEALTH, HEALTH_ID, RavenConfig.HEALTH_BONUS, AttributeModifier.Operation.ADD_VALUE);
		if (s.wrathUntil > now(player)) {
			PowerToggles.modifier(player, Attributes.SCALE, SCALE_ID, RavenConfig.WRATH_SCALE - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		} else {
			PowerToggles.clearModifier(player, Attributes.SCALE, SCALE_ID);
		}
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	// ---------------------------------------------------------------- tick

	public static void tick(ServerPlayer player) {
		RavenState s = player.getAttachedOrElse(ModAttachments.RAVEN_STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		long now = now(player);
		Long prev = LAST_TICK.put(player.getUUID(), now);
		if (prev != null && prev == now) {
			return;
		}
		boolean wrath = s.wrathUntil > now;
		// Dark Energy (in place; re-sent every few ticks)
		if (!s.shield && s.tkTarget < 0) {
			s.energy = Math.min(RavenConfig.ENERGY_MAX, s.energy + RavenConfig.ENERGY_REGEN_PER_TICK
					+ (s.meditating ? RavenConfig.MEDITATE_ENERGY_PER_TICK : 0f));
		}
		// Composure
		if (wrath) {
			s.composure = 0f;
		} else {
			boolean hostile = !player.level().getEntitiesOfClass(LivingEntity.class,
					player.getBoundingBox().inflate(RavenConfig.HOSTILE_RADIUS),
					e -> e instanceof Enemy && e.isAlive() && !(e instanceof Player)).isEmpty();
			int ht = hostile ? HOSTILE_TICKS.merge(player.getUUID(), 1, Integer::sum) : 0;
			if (!hostile) {
				HOSTILE_TICKS.remove(player.getUUID());
			}
			if (s.meditating) {
				s.composure = Math.min(RavenConfig.COMPOSURE_MAX, s.composure + RavenConfig.MEDITATE_COMPOSURE_PER_TICK);
			} else if (ht > RavenConfig.HOSTILE_GRACE_TICKS) {
				s.composure = Math.max(0f, s.composure - RavenConfig.HOSTILE_DRAIN_PER_TICK);
			} else if (!hostile) {
				Long hurt = LAST_HURT.get(player.getUUID());
				if (hurt == null || now - hurt > RavenConfig.CALM_AFTER_TICKS || now < hurt) {
					s.composure = Math.min(RavenConfig.COMPOSURE_MAX, s.composure + RavenConfig.CALM_REGEN_PER_TICK);
				}
			}
		}
		// slow fall, always on unless she is flying, meditating or in the Wrath (she is a giant then)
		if (!s.flying && !s.meditating && !wrath && player.isAlive() && !player.isSpectator()) {
			MobEffectInstance cur = player.getEffect(MobEffects.SLOW_FALLING);
			if (cur == null || cur.getDuration() < 10) {
				player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, true, false, false));
			}
		}
		// the Wrath ends: drained (calm again, no Dark Energy)
		if (s.wrathUntil > 0L && !wrath) {
			RavenMoves.endWrath(player);
		}
		if (player.tickCount % 20 == 0) {
			reconcile(player);
		}
		RavenFlight.tick(player);
		RavenMoves.tick(player);
		if (state(player).composure < RavenConfig.LEAK_BELOW) {
			RavenLeak.tick(player);
		}
		if (now % RavenConfig.SYNC_INTERVAL == 0) {
			save(player, state(player).copy());
		}
	}
}
