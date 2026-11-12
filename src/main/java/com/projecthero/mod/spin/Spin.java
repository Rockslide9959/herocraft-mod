package com.projecthero.mod.spin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.spin.data.SpinState;
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
 * v0.17.3 the Spin (the Spin): the single server-side API of the power. Nothing else changes {@link SpinState}. The numbers
 * are in {@link SpinConfig}; the moves are {@link SpinMoves}' (routed by {@link SpinAbilityManager}).
 *
 * <ul>
 *   <li><b>Passives</b>: Regeneration I (20 health, no extra hearts), +15% speed, +4 melee, 15% damage resistance ({@link SpinDamage}).</li>
 *   <li><b>Spin</b> (0..100) pays for the moves, 4 a second back.</li>
 *   <li><b>Two returning Steel Balls</b>: a move that needs a ball is refused (and greyed on the HUD) while it is out.</li>
 *   <li><b>Riding</b> a horse: every Spin move hits 30% harder and the Golden Rectangle closes twice as fast.</li>
 * </ul>
 */
public final class Spin {
	public static final String KEY = "spin";

	/** Perfect Spin damage: the player's hit that ignores armour ({@code data/projecthero/damage_type/golden_spin.json}). */
	public static final ResourceKey<DamageType> GOLDEN_SPIN = ResourceKey.create(Registries.DAMAGE_TYPE, ProjectHeroMod.id("golden_spin"));

	private static final ResourceLocation HEALTH_ID = PowerToggles.id("spin_health");
	private static final ResourceLocation SPEED_ID = PowerToggles.id("spin_speed");
	private static final ResourceLocation ATTACK_ID = PowerToggles.id("spin_attack");

	static final int GOLD = 0xFFC83A;
	static final int GREEN = 0x4FB86A;

	private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();

	private Spin() {
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
		LAST_TICK.clear();
		SpinAbilityManager.clearSessionState();
		SpinMoves.clearSessionState();
		SpinMarks.clearSessionState();
		SpinValkyrie.clearSessionState();
		SpinDisguise.clearSessionState();
		SpinBalls.clearSessionState();
	}

	static void forget(UUID id) {
		LAST_MESSAGE.remove(id);
		LAST_TICK.remove(id);
		SpinAbilityManager.forget(id);
		SpinMoves.forget(id);
		SpinDisguise.forget(id);
		SpinBalls.forget(id);
	}

	// ---------------------------------------------------------------- state (client and server)

	public static SpinState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.SPIN_STATE);
	}

	static void save(ServerPlayer player, SpinState state) {
		player.setAttached(ModAttachments.SPIN_STATE, state);
	}

	public static SpinState peek(Player player) {
		return player.getAttachedOrElse(ModAttachments.SPIN_STATE, null);
	}

	public static boolean hasPower(Player player) {
		SpinState s = peek(player);
		return s != null && s.hasPower;
	}

	static long now(Player player) {
		return player.level().getGameTime();
	}

	public static float spin(Player player) {
		SpinState s = peek(player);
		return s == null ? 0f : s.spin;
	}

	/** On a horse (any tamed or wild horse, donkey, mule...): the riding bonus. Client-safe. */
	public static boolean riding(Player player) {
		return player.getVehicle() instanceof AbstractHorse;
	}

	/** Ball {@code i}'s item is in one of his hands right now. Client-safe. */
	public static boolean ballReady(Player player, int i) {
		return hasPower(player) && SpinBalls.ballInHand(player, i);
	}

	/** How many hands hold one of his balls (0..2). Client-safe. */
	public static int ballsReady(Player player) {
		return hasPower(player) ? SpinBalls.handsHolding(player) : 0;
	}

	/** The ball in his main hand (-1 = none): the one R, G, Shift+G, Z, Shift+Z, C, V and Shift+V use. */
	public static int readyBall(Player player) {
		return hasPower(player) ? SpinBalls.inHand(player, net.minecraft.world.InteractionHand.MAIN_HAND) : -1;
	}

	public static boolean disguised(Player player) {
		SpinState s = peek(player);
		return s != null && s.hasPower && s.disguiseUntil > now(player);
	}

	public static int disguiseTicksLeft(Player player) {
		SpinState s = peek(player);
		return s == null || !s.hasPower ? 0 : (int) Math.max(0L, s.disguiseUntil - now(player));
	}

	public static boolean shielding(Player player) {
		SpinState s = peek(player);
		return s != null && s.hasPower && s.shieldUntil > now(player);
	}

	public static int valkyrieCooldown(Player player) {
		SpinState s = peek(player);
		return s == null || !s.hasPower ? 0 : (int) Math.max(0L, s.valkyrieDeadUntil - now(player));
	}

	/** The hold that is charging (SpinState.CHARGE_*). Client-safe. */
	public static int charging(Player player) {
		SpinState s = peek(player);
		return s == null || !s.hasPower || s.chargeStart <= 0L ? SpinState.CHARGE_NONE : s.chargeMove;
	}

	/** Ticks since the hold began (0 when not charging). Client-safe. */
	public static int chargeElapsed(Player player) {
		SpinState s = peek(player);
		return s == null || s.chargeStart <= 0L ? 0 : (int) Math.max(0L, now(player) - s.chargeStart);
	}

	/** Ticks until the rectangle closes, for the charge running now ({@link #chargeTicksFor} when not charging). */
	public static int chargeTarget(Player player) {
		SpinState s = peek(player);
		return s == null || s.chargeStart <= 0L || s.chargeTicks <= 0 ? chargeTicksFor(player) : s.chargeTicks;
	}

	/** How long the Golden Rectangle takes to close right now: half on a horse. */
	public static int chargeTicksFor(Player player) {
		return riding(player) ? SpinConfig.GOLDEN_CHARGE_TICKS / SpinConfig.RIDING_CHARGE_DIVISOR : SpinConfig.GOLDEN_CHARGE_TICKS;
	}

	/** Is a release after {@code elapsed} ticks of a {@code target}-tick charge a Perfect Spin? */
	public static boolean isPerfect(int elapsed, int target) {
		return Math.abs(elapsed - target) <= SpinConfig.PERFECT_WINDOW;
	}

	public static int cooldownRemaining(Player player, String id) {
		SpinState s = peek(player);
		if (s == null) {
			return 0;
		}
		Long ready = s.abilityReadyAt.get(id);
		return ready == null ? 0 : (int) Math.max(0L, ready - now(player));
	}

	static void startCooldown(ServerPlayer player, String id, int ticks) {
		SpinState n = state(player).copy();
		n.abilityReadyAt.put(id, now(player) + ticks);
		save(player, n);
	}

	/** Clears every cooldown (the tune command and the tests). */
	public static void clearCooldowns(ServerPlayer player) {
		SpinState n = state(player).copy();
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
		SpinState s = state(player);
		if (!s.hasPower) {
			return false;
		}
		if (player.getAbilities().instabuild || amount <= 0f) {
			return true;
		}
		if (s.spin + 1.0e-3f < amount) {
			say(player, "message.projecthero.spin.no_spin", ChatFormatting.GRAY);
			return false;
		}
		SpinState n = s.copy();
		n.spin = Math.max(0f, s.spin - amount);
		save(player, n);
		return true;
	}

	/** Has at least {@code amount} Spin (creative always does). */
	public static boolean hasSpin(ServerPlayer player, float amount) {
		return player.getAbilities().instabuild || state(player).spin + 1.0e-3f >= amount;
	}

	public static void setSpin(ServerPlayer player, float value) {
		SpinState n = state(player).copy();
		n.spin = Math.max(0f, Math.min(SpinConfig.SPIN_MAX, value));
		save(player, n);
	}

	// ---------------------------------------------------------------- the balls

	/** Ball {@code i} leaves the hand as a thrown entity / comes back. */
	public static void setThrown(ServerPlayer player, int i, boolean out) {
		SpinState s = state(player);
		int bits = out ? s.thrown | (1 << i) : s.thrown & ~(1 << i);
		if (bits == s.thrown) {
			return;
		}
		SpinState n = s.copy();
		n.thrown = bits;
		save(player, n);
	}

	/** Ball {@code i} is busy (on the ground, orbiting) for {@code ticks}. */
	static void setBusy(ServerPlayer player, int i, int ticks) {
		SpinState n = state(player).copy();
		n.setBusy(i, now(player) + ticks);
		save(player, n);
	}

	/** Scan / Quake / Shield: ball {@code i} leaves {@code hand} for {@code ticks}, then {@link SpinBalls#tick} gives it back there. */
	static void takeBusy(ServerPlayer player, int i, int ticks, net.minecraft.world.InteractionHand hand) {
		SpinBalls.take(player, hand);
		SpinState n = state(player).copy();
		n.setBusy(i, now(player) + ticks);
		n.owed |= 1 << i;
		if (hand == net.minecraft.world.InteractionHand.OFF_HAND) {
			n.owed |= 1 << (i + 2);
		} else {
			n.owed &= ~(1 << (i + 2));
		}
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
		return riding(player) ? SpinConfig.RIDING_DAMAGE_MULT : 1f;
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
		SpinState old = state(player);
		if (old.hasPower) {
			return false;
		}
		if (com.projecthero.mod.hero.UniquePowers.refuse(player, KEY)) {
			return false;
		}
		HeroTiers.claimPrimary(player, KEY);
		SpinState s = new SpinState();
		s.hasPower = true;
		s.spin = SpinConfig.SPIN_MAX;
		s.valkyrie = old.valkyrie; // she remembers him
		s.gen0 = old.gen0 + 1; // any ball item left over from an earlier time is stale
		s.gen1 = old.gen1 + 1;
		save(player, s);
		SpinBalls.giveBoth(player); // his two Steel Balls: main hand and off hand when free
		reconcile(player);
		player.setHealth(player.getMaxHealth());
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		level.playSound(null, c.x, c.y, c.z, SpinSounds.BODY_SPIN, SoundSource.PLAYERS, 0.8f, 1.0f);
		level.sendParticles(dust(GOLD, 1.4f), c.x, c.y, c.z, 30, 0.5, 0.9, 0.5, 0.0);
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
				Component.translatable("message.projecthero.spin.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
				Component.translatable("message.projecthero.spin.subtitle").withStyle(ChatFormatting.GREEN)));
		player.displayClientMessage(Component.translatable("message.projecthero.spin.acquired").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.spin.acquired_hint").withStyle(ChatFormatting.GRAY), false);
		return true;
	}

	public static void revoke(ServerPlayer player) {
		SpinMoves.recallBalls(player);
		SpinBalls.removeAll(player);
		SpinState old = state(player);
		SpinState n = new SpinState();
		n.valkyrie = old.valkyrie;
		n.gen0 = old.gen0; // kept, so balls left in chests stay stale
		n.gen1 = old.gen1;
		save(player, n);
		reconcile(player);
		forget(player.getUUID());
	}

	/** Death, a new world, leaving: the hold, the shield and the thrown balls end. */
	public static void clearTransient(ServerPlayer player) {
		SpinState s = peek(player);
		if (s == null || !s.hasPower) {
			return;
		}
		SpinMoves.recallBalls(player); // (each thrown ball returns to his hand as it goes)
		// a ball still marked out with no entity (a restart lost it) or held by Scan / Quake / Shield comes back now
		SpinState cur = state(player);
		for (int i = 0; i < SpinConfig.BALLS; i++) {
			if ((cur.thrown & (1 << i)) != 0 || (cur.owed & (1 << i)) != 0) {
				boolean off = (cur.owed & (1 << i)) != 0 && (cur.owed & (1 << (i + 2))) != 0;
				SpinBalls.giveBack(player, i, off ? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND);
			}
		}
		SpinState n = state(player).copy();
		n.owed = 0;
		n.chargeStart = 0L;
		n.chargeMove = SpinState.CHARGE_NONE;
		n.thrown = 0;
		n.busy0 = 0L;
		n.busy1 = 0L;
		n.shieldUntil = 0L;
		n.leapUntil = 0L;
		save(player, n);
		SpinMoves.forget(player.getUUID());
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
		SpinState n = state(player).copy();
		n.spin = SpinConfig.SPIN_MAX;
		n.disguiseUntil = 0L;
		save(player, n);
		SpinBalls.ensure(player); // the balls are kept on death
		reconcile(player);
	}

	// ---------------------------------------------------------------- stats

	public static void reconcile(ServerPlayer player) {
		SpinState s = peek(player);
		if (s == null || !s.hasPower) {
			PowerToggles.clearModifier(player, Attributes.MAX_HEALTH, HEALTH_ID);
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID);
			if (player.getHealth() > player.getMaxHealth()) {
				player.setHealth(player.getMaxHealth());
			}
			return;
		}
		PowerToggles.modifier(player, Attributes.MAX_HEALTH, HEALTH_ID, SpinConfig.HEALTH_BONUS, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, SpinConfig.SPEED_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID, SpinConfig.MELEE_BONUS, AttributeModifier.Operation.ADD_VALUE);
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	// ---------------------------------------------------------------- tick

	public static void tick(ServerPlayer player) {
		SpinState s = player.getAttachedOrElse(ModAttachments.SPIN_STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		long now = now(player);
		Long prev = LAST_TICK.put(player.getUUID(), now);
		if (prev != null && prev == now) {
			return;
		}
		// the Spin bar (in place; re-sent every few ticks)
		s.spin = Math.min(SpinConfig.SPIN_MAX, s.spin + SpinConfig.SPIN_REGEN_PER_TICK);
		// passive Regeneration I
		if (now % SpinConfig.REGEN_EVERY_TICKS == 0L && player.isAlive() && player.getHealth() < player.getMaxHealth()) {
			player.heal(1f);
		}
		// Spin Leap: safe until he lands
		if (s.leapUntil > 0L && (now >= s.leapUntil || (player.onGround() && now - (s.leapUntil - SpinConfig.LEAP_SAFE_TICKS) > 3))) {
			SpinState n = s.copy();
			n.leapUntil = 0L;
			save(player, n);
			player.resetFallDistance();
		}
		if (player.tickCount % 20 == 0) {
			reconcile(player);
		}
		SpinAbilityManager.tick(player);
		SpinMoves.tick(player);
		SpinBalls.tick(player);
		SpinDisguise.tick(player);
		SpinValkyrie.tick(player);
		if (now % SpinConfig.SYNC_INTERVAL == 0) {
			save(player, state(player).copy());
		}
	}
}
