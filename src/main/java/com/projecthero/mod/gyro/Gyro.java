package com.projecthero.mod.gyro;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.gyro.data.GyroState;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.power.PowerToggles;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.17.3 Gyro Zeppeli (the Spin): the single server-side API of the power. Nothing else changes {@link GyroState}. The numbers
 * are in {@link GyroConfig}; the moves are {@link GyroMoves}' (routed by {@link GyroAbilityManager}).
 *
 * <ul>
 *   <li><b>Passives</b>: +8 hearts, +15% speed, +4 melee, 15% damage resistance ({@link GyroDamage}).</li>
 *   <li><b>Spin</b> (0..100) pays for the moves, 4 a second back.</li>
 *   <li><b>Two returning Steel Balls</b>: a move that needs a ball is refused (and greyed on the HUD) while it is out.</li>
 *   <li><b>Riding</b> a horse: every Spin move hits 30% harder and the Golden Rectangle closes twice as fast.</li>
 * </ul>
 */
public final class Gyro {
	public static final String KEY = "gyro";

	/** Perfect Spin damage: the player's hit that ignores armour ({@code data/projecthero/damage_type/golden_spin.json}). */
	public static final ResourceKey<DamageType> GOLDEN_SPIN = ResourceKey.create(Registries.DAMAGE_TYPE, ProjectHeroMod.id("golden_spin"));

	private static final ResourceLocation HEALTH_ID = PowerToggles.id("gyro_health");
	private static final ResourceLocation SPEED_ID = PowerToggles.id("gyro_speed");
	private static final ResourceLocation ATTACK_ID = PowerToggles.id("gyro_attack");

	static final int GOLD = 0xFFC83A;
	static final int GREEN = 0x4FB86A;

	private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();

	private Gyro() {
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
		LAST_TICK.clear();
		GyroAbilityManager.clearSessionState();
		GyroMoves.clearSessionState();
		GyroMarks.clearSessionState();
		GyroValkyrie.clearSessionState();
		GyroDisguise.clearSessionState();
	}

	static void forget(UUID id) {
		LAST_MESSAGE.remove(id);
		LAST_TICK.remove(id);
		GyroAbilityManager.forget(id);
		GyroMoves.forget(id);
		GyroDisguise.forget(id);
	}

	// ---------------------------------------------------------------- state (client and server)

	public static GyroState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.GYRO_STATE);
	}

	static void save(ServerPlayer player, GyroState state) {
		player.setAttached(ModAttachments.GYRO_STATE, state);
	}

	public static GyroState peek(Player player) {
		return player.getAttachedOrElse(ModAttachments.GYRO_STATE, null);
	}

	public static boolean hasPower(Player player) {
		GyroState s = peek(player);
		return s != null && s.hasPower;
	}

	static long now(Player player) {
		return player.level().getGameTime();
	}

	public static float spin(Player player) {
		GyroState s = peek(player);
		return s == null ? 0f : s.spin;
	}

	/** On a horse (any tamed or wild horse, donkey, mule...): the riding bonus. Client-safe. */
	public static boolean riding(Player player) {
		return player.getVehicle() instanceof AbstractHorse;
	}

	/** Ball {@code i} is in his hand right now. Client-safe. */
	public static boolean ballReady(Player player, int i) {
		GyroState s = peek(player);
		return s != null && s.hasPower && s.ballReady(i, now(player));
	}

	/** How many balls are in hand. Client-safe. */
	public static int ballsReady(Player player) {
		GyroState s = peek(player);
		return s == null || !s.hasPower ? 0 : s.ballsReady(now(player));
	}

	/** The first ball in hand (-1 = none). */
	public static int readyBall(Player player) {
		for (int i = 0; i < GyroConfig.BALLS; i++) {
			if (ballReady(player, i)) {
				return i;
			}
		}
		return -1;
	}

	public static boolean disguised(Player player) {
		GyroState s = peek(player);
		return s != null && s.hasPower && s.disguiseUntil > now(player);
	}

	public static int disguiseTicksLeft(Player player) {
		GyroState s = peek(player);
		return s == null || !s.hasPower ? 0 : (int) Math.max(0L, s.disguiseUntil - now(player));
	}

	public static boolean shielding(Player player) {
		GyroState s = peek(player);
		return s != null && s.hasPower && s.shieldUntil > now(player);
	}

	public static int valkyrieCooldown(Player player) {
		GyroState s = peek(player);
		return s == null || !s.hasPower ? 0 : (int) Math.max(0L, s.valkyrieDeadUntil - now(player));
	}

	/** The hold that is charging (GyroState.CHARGE_*). Client-safe. */
	public static int charging(Player player) {
		GyroState s = peek(player);
		return s == null || !s.hasPower || s.chargeStart <= 0L ? GyroState.CHARGE_NONE : s.chargeMove;
	}

	/** Ticks since the hold began (0 when not charging). Client-safe. */
	public static int chargeElapsed(Player player) {
		GyroState s = peek(player);
		return s == null || s.chargeStart <= 0L ? 0 : (int) Math.max(0L, now(player) - s.chargeStart);
	}

	/** Ticks until the rectangle closes, for the charge running now ({@link #chargeTicksFor} when not charging). */
	public static int chargeTarget(Player player) {
		GyroState s = peek(player);
		return s == null || s.chargeStart <= 0L || s.chargeTicks <= 0 ? chargeTicksFor(player) : s.chargeTicks;
	}

	/** How long the Golden Rectangle takes to close right now: half on a horse. */
	public static int chargeTicksFor(Player player) {
		return riding(player) ? GyroConfig.GOLDEN_CHARGE_TICKS / GyroConfig.RIDING_CHARGE_DIVISOR : GyroConfig.GOLDEN_CHARGE_TICKS;
	}

	/** Is a release after {@code elapsed} ticks of a {@code target}-tick charge a Perfect Spin? */
	public static boolean isPerfect(int elapsed, int target) {
		return Math.abs(elapsed - target) <= GyroConfig.PERFECT_WINDOW;
	}

	public static int cooldownRemaining(Player player, String id) {
		GyroState s = peek(player);
		if (s == null) {
			return 0;
		}
		Long ready = s.abilityReadyAt.get(id);
		return ready == null ? 0 : (int) Math.max(0L, ready - now(player));
	}

	static void startCooldown(ServerPlayer player, String id, int ticks) {
		GyroState n = state(player).copy();
		n.abilityReadyAt.put(id, now(player) + ticks);
		save(player, n);
	}

	/** Clears every cooldown (the tune command and the tests). */
	public static void clearCooldowns(ServerPlayer player) {
		GyroState n = state(player).copy();
		n.abilityReadyAt.clear();
		n.valkyrieDeadUntil = 0L;
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

	static DustParticleOptions dust(int rgb, float size) {
		return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f), size);
	}

	// ---------------------------------------------------------------- Spin (the moves' API)

	/** Spends Spin if there is enough. False (nothing spent, "Not enough Spin" shown) if not. Creative never pays. */
	public static boolean spendSpin(ServerPlayer player, float amount) {
		GyroState s = state(player);
		if (!s.hasPower) {
			return false;
		}
		if (player.getAbilities().instabuild || amount <= 0f) {
			return true;
		}
		if (s.spin + 1.0e-3f < amount) {
			say(player, "message.projecthero.gyro.no_spin", ChatFormatting.GRAY);
			return false;
		}
		GyroState n = s.copy();
		n.spin = Math.max(0f, s.spin - amount);
		save(player, n);
		return true;
	}

	/** Has at least {@code amount} Spin (creative always does). */
	public static boolean hasSpin(ServerPlayer player, float amount) {
		return player.getAbilities().instabuild || state(player).spin + 1.0e-3f >= amount;
	}

	public static void setSpin(ServerPlayer player, float value) {
		GyroState n = state(player).copy();
		n.spin = Math.max(0f, Math.min(GyroConfig.SPIN_MAX, value));
		save(player, n);
	}

	// ---------------------------------------------------------------- the balls

	/** Ball {@code i} leaves the hand as a thrown entity / comes back. */
	public static void setThrown(ServerPlayer player, int i, boolean out) {
		GyroState s = state(player);
		int bits = out ? s.thrown | (1 << i) : s.thrown & ~(1 << i);
		if (bits == s.thrown) {
			return;
		}
		GyroState n = s.copy();
		n.thrown = bits;
		save(player, n);
	}

	/** Ball {@code i} is busy (on the ground, orbiting) for {@code ticks}. */
	static void setBusy(ServerPlayer player, int i, int ticks) {
		GyroState n = state(player).copy();
		n.setBusy(i, now(player) + ticks);
		save(player, n);
	}

	// ---------------------------------------------------------------- hits

	/** The hand a ball flies back to: a little in front of his right shoulder. */
	public static Vec3 handPos(Player player) {
		Vec3 look = player.getViewVector(1f);
		float yaw = player.getYRot() * ((float) Math.PI / 180f);
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		return player.getEyePosition().add(0, -0.45, 0).add(look.scale(0.4)).add(right.scale(0.35));
	}

	/** Every Spin move's damage multiplier right now (the riding bonus). */
	public static float damageMult(Player player) {
		return riding(player) ? GyroConfig.RIDING_DAMAGE_MULT : 1f;
	}

	/**
	 * A Spin move hits {@code target} for {@code amount} (x the riding bonus). A Perfect Spin ({@code ignoreArmour}) is dealt as
	 * {@link #GOLDEN_SPIN}, which armour doesn't stop. It is always the player's own damage (the squad veto and boss threat see
	 * him). False when the target can't be harmed (a squadmate, their pet, his own horse, PvP off) or nothing landed.
	 */
	public static boolean hit(ServerPlayer player, LivingEntity target, float amount, boolean ignoreArmour) {
		if (target == null || target == player || !HeroTargets.canHarm(player, target)) {
			return false;
		}
		float dmg = amount * damageMult(player);
		DamageSource source = ignoreArmour ? player.damageSources().source(GOLDEN_SPIN, player) : player.damageSources().playerAttack(player);
		float before = target.getHealth();
		boolean landed = com.projecthero.mod.hero.power.AbilityHelpers.hurtBurst(player, target, source, dmg);
		return landed || target.getHealth() < before;
	}

	// ---------------------------------------------------------------- the power

	/** The Zeppeli Steel Balls, the command. False if he already has it (or someone else does, unique powers). */
	public static boolean grant(ServerPlayer player) {
		GyroState old = state(player);
		if (old.hasPower) {
			return false;
		}
		if (com.projecthero.mod.hero.UniquePowers.refuse(player, KEY)) {
			return false;
		}
		HeroTiers.claimPrimary(player, KEY);
		GyroState s = new GyroState();
		s.hasPower = true;
		s.spin = GyroConfig.SPIN_MAX;
		s.valkyrie = old.valkyrie; // she remembers him
		save(player, s);
		reconcile(player);
		player.setHealth(player.getMaxHealth());
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		level.playSound(null, c.x, c.y, c.z, GyroSounds.BODY_SPIN, SoundSource.PLAYERS, 0.8f, 1.0f);
		level.sendParticles(dust(GOLD, 1.4f), c.x, c.y, c.z, 30, 0.5, 0.9, 0.5, 0.0);
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
				Component.translatable("message.projecthero.gyro.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
				Component.translatable("message.projecthero.gyro.subtitle").withStyle(ChatFormatting.GREEN)));
		player.displayClientMessage(Component.translatable("message.projecthero.gyro.acquired").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.gyro.acquired_hint").withStyle(ChatFormatting.GRAY), false);
		return true;
	}

	public static void revoke(ServerPlayer player) {
		GyroMoves.recallBalls(player);
		GyroState old = state(player);
		GyroState n = new GyroState();
		n.valkyrie = old.valkyrie;
		save(player, n);
		reconcile(player);
		forget(player.getUUID());
	}

	/** Death, a new world, leaving: the hold, the shield and the thrown balls end. */
	public static void clearTransient(ServerPlayer player) {
		GyroState s = peek(player);
		if (s == null || !s.hasPower) {
			return;
		}
		GyroMoves.recallBalls(player);
		GyroState n = state(player).copy();
		n.chargeStart = 0L;
		n.chargeMove = GyroState.CHARGE_NONE;
		n.thrown = 0;
		n.busy0 = 0L;
		n.busy1 = 0L;
		n.shieldUntil = 0L;
		n.leapUntil = 0L;
		save(player, n);
		GyroMoves.forget(player.getUUID());
		reconcile(player);
	}

	public static void onPlayerJoin(ServerPlayer player) {
		if (hasPower(player)) {
			clearTransient(player);
		}
	}

	/** A death: the bar comes back full and the disguise drops. Cooldowns stay. */
	public static void onPlayerRespawn(ServerPlayer player) {
		if (!hasPower(player)) {
			return;
		}
		clearTransient(player);
		GyroState n = state(player).copy();
		n.spin = GyroConfig.SPIN_MAX;
		n.disguiseUntil = 0L;
		save(player, n);
		reconcile(player);
	}

	// ---------------------------------------------------------------- stats

	public static void reconcile(ServerPlayer player) {
		GyroState s = peek(player);
		if (s == null || !s.hasPower) {
			PowerToggles.clearModifier(player, Attributes.MAX_HEALTH, HEALTH_ID);
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID);
			if (player.getHealth() > player.getMaxHealth()) {
				player.setHealth(player.getMaxHealth());
			}
			return;
		}
		PowerToggles.modifier(player, Attributes.MAX_HEALTH, HEALTH_ID, GyroConfig.HEALTH_BONUS, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, GyroConfig.SPEED_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID, GyroConfig.MELEE_BONUS, AttributeModifier.Operation.ADD_VALUE);
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	// ---------------------------------------------------------------- tick

	public static void tick(ServerPlayer player) {
		GyroState s = player.getAttachedOrElse(ModAttachments.GYRO_STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		long now = now(player);
		Long prev = LAST_TICK.put(player.getUUID(), now);
		if (prev != null && prev == now) {
			return;
		}
		// the Spin bar (in place; re-sent every few ticks)
		s.spin = Math.min(GyroConfig.SPIN_MAX, s.spin + GyroConfig.SPIN_REGEN_PER_TICK);
		// Spin Leap: safe until he lands
		if (s.leapUntil > 0L && (now >= s.leapUntil || (player.onGround() && now - (s.leapUntil - GyroConfig.LEAP_SAFE_TICKS) > 3))) {
			GyroState n = s.copy();
			n.leapUntil = 0L;
			save(player, n);
			player.resetFallDistance();
		}
		if (player.tickCount % 20 == 0) {
			reconcile(player);
		}
		GyroAbilityManager.tick(player);
		GyroMoves.tick(player);
		GyroDisguise.tick(player);
		GyroValkyrie.tick(player);
		if (now % GyroConfig.SYNC_INTERVAL == 0) {
			save(player, state(player).copy());
		}
	}
}
