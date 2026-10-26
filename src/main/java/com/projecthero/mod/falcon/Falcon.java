package com.projecthero.mod.falcon;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.falcon.data.FalconState;
import com.projecthero.mod.falcon.item.FalconItems;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.PowerToggles;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * The single server-side API for the Falcon (Sam Wilson), the v0.17.1 Hero-Tier power. Nothing else touches
 * {@link FalconState}; every change re-saves through {@link ServerPlayer#setAttached}. The moves live in
 * {@link FalconAbilities}, the damage rules in {@link FalconDamage}, the numbers in {@link FalconConfig}.
 *
 * <h2>How it works</h2>
 * The power is the gear: wearing all four craftable pieces (the EXO-7 Wing Pack, the Falcon Goggles, the Flight Suit
 * leggings and boots) makes the wearer the Falcon -- it claims the one Primary slot like any other hero. Taking a piece off
 * takes the power away. If another power replaces it while the set is still on, it stays off until a piece comes off and
 * goes back on ({@link FalconState#blocked}), so it never fights the new power for the slot.
 *
 * <h2>Flight</h2>
 * The mod-wide directional flight ({@link FalconFlight}, the Kryptonian's handling): double-tap jump takes off or drops out,
 * 19 b/s cruising, 35 sprinting, and holding X burns the jets for 40 b/s (14 Thrust a second). The client banks the camera
 * into turns; touching down ends it -- a fast landing is a hero landing.
 */
public final class Falcon {
	public static final String KEY = "falcon";

	private static final ResourceLocation SPRINT_ID = PowerToggles.id("falcon_sprint");

	public static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.95f, 0.12f, 0.18f), 1.0f);
	public static final DustParticleOptions WHITE = new DustParticleOptions(new Vector3f(0.92f, 0.94f, 0.98f), 1.1f);
	public static final DustParticleOptions FLAME = new DustParticleOptions(new Vector3f(1.0f, 0.62f, 0.2f), 1.2f);

	private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();
	/** Where the player was last tick (players move client-side; the server's own velocity is stale). */
	private static final Map<UUID, Vec3> LAST_POS = new ConcurrentHashMap<>();
	/** The fastest the player moved over the last two ticks, blocks a tick (for the hero landing / Dive Bomb). */
	private static final Map<UUID, Double> RECENT_SPEED = new ConcurrentHashMap<>();

	private Falcon() {
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
		LAST_TICK.clear();
		LAST_POS.clear();
		RECENT_SPEED.clear();
		FalconFlight.clearSessionState();
		FalconAbilities.clearSessionState();
		FalconAbilityManager.clearSessionState();
	}

	// ---------------------------------------------------------------- state

	public static FalconState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.FALCON_STATE);
	}

	static void save(ServerPlayer player, FalconState state) {
		player.setAttached(ModAttachments.FALCON_STATE, state);
	}

	public static FalconState peek(Player player) {
		return player.getAttachedOrElse(ModAttachments.FALCON_STATE, null);
	}

	public static boolean hasPower(Player player) {
		FalconState s = peek(player);
		return s != null && s.hasPower;
	}

	/** The wings are out (gliding). Client-safe. */
	public static boolean flying(Player player) {
		FalconState s = peek(player);
		return s != null && s.hasPower && s.flying;
	}

	/** The jets are burning. Client-safe. */
	public static boolean boosting(Player player) {
		FalconState s = peek(player);
		return s != null && s.hasPower && s.flying && s.boosting;
	}

	/** Tactical Goggles on. Client-safe. */
	public static boolean goggles(Player player) {
		FalconState s = peek(player);
		return s != null && s.hasPower && s.goggles;
	}

	public static float thrust(Player player) {
		FalconState s = peek(player);
		return s == null ? 0f : s.thrust;
	}

	public static boolean shieldUp(Player player) {
		FalconState s = peek(player);
		return s != null && s.hasPower && s.shieldUntil > player.level().getGameTime() && s.shieldHp > 0f;
	}

	public static boolean rolling(Player player) {
		FalconState s = peek(player);
		return s != null && s.hasPower && s.rollUntil > player.level().getGameTime();
	}

	public static int cooldownRemaining(Player player, String abilityId) {
		FalconState s = peek(player);
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

	/** The fastest the player moved over the last two ticks, blocks a tick. */
	static double recentSpeed(ServerPlayer player) {
		Double d = RECENT_SPEED.get(player.getUUID());
		return d == null ? 0.0 : d;
	}

	// ---------------------------------------------------------------- Thrust

	public static boolean spendThrust(ServerPlayer player, float cost) {
		if (cost <= 0f) {
			return true;
		}
		FalconState s = state(player);
		if (s.thrust + 1.0e-3f < cost) {
			return false;
		}
		FalconState n = s.copy();
		n.thrust = Math.max(0f, Math.min(FalconConfig.THRUST_MAX, s.thrust) - cost);
		save(player, n);
		return true;
	}

	public static void setThrust(ServerPlayer player, float value) {
		FalconState n = state(player).copy();
		n.thrust = Math.max(0f, Math.min(FalconConfig.THRUST_MAX, value));
		save(player, n);
	}

	// ---------------------------------------------------------------- grant / revoke

	/**
	 * The full set went on: the wearer becomes the Falcon (claims the one Primary slot). False if they already are, or the
	 * unique-powers rule refuses it (then it stays off until a piece comes off).
	 */
	public static boolean grant(ServerPlayer player) {
		FalconState s = state(player);
		if (s.hasPower) {
			return false;
		}
		if (com.projecthero.mod.hero.UniquePowers.refuse(player, KEY)) {
			FalconState n = s.copy();
			n.blocked = true;
			save(player, n);
			return false;
		}
		HeroTiers.claimPrimary(player, KEY);
		FalconState n = new FalconState();
		n.hasPower = true;
		n.thrust = FalconConfig.THRUST_MAX;
		n.abilityReadyAt.putAll(s.abilityReadyAt);
		save(player, n);
		reconcile(player);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.2, 0);
		level.sendParticles(WHITE, c.x, c.y, c.z, 30, 0.6, 0.5, 0.6, 0.0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.ARMOR_EQUIP_ELYTRA.value(), SoundSource.PLAYERS, 1.0f, 0.8f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6f, 1.7f);
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
				Component.translatable("message.projecthero.falcon.title").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
				Component.translatable("message.projecthero.falcon.subtitle").withStyle(ChatFormatting.GRAY)));
		player.displayClientMessage(Component.translatable("message.projecthero.falcon.acquired")
				.withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.falcon.acquired_hint")
				.withStyle(ChatFormatting.GRAY), false);
		return true;
	}

	/**
	 * Takes the power away (a piece came off, a command, or another power replaced it). With the set still on it stays off
	 * until a piece comes off and goes back on.
	 */
	public static void revoke(ServerPlayer player) {
		FalconFlight.stop(player);
		FalconAbilities.clear(player);
		FalconAbilities.removeRedwing(player);
		FalconState old = state(player);
		FalconState n = new FalconState();
		n.blocked = FalconItems.fullSet(player);
		n.abilityReadyAt.putAll(old.abilityReadyAt);
		save(player, n);
		reconcile(player);
		forget(player.getUUID());
	}

	/** Lets the worn set grant the power again (a command / grant asked for it). */
	public static void clearBlock(ServerPlayer player) {
		FalconState s = state(player);
		if (s.blocked) {
			FalconState n = s.copy();
			n.blocked = false;
			save(player, n);
		}
	}

	private static void forget(UUID id) {
		LAST_MESSAGE.remove(id);
		LAST_TICK.remove(id);
		LAST_POS.remove(id);
		RECENT_SPEED.remove(id);
		FalconFlight.forget(id);
		FalconAbilityManager.forget(id);
	}

	/** The sprint bonus: +20% while sprinting in the gear. */
	public static void reconcile(ServerPlayer player) {
		FalconState s = peek(player);
		if (s != null && s.hasPower && player.isSprinting()) {
			PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, SPRINT_ID, FalconConfig.SPRINT_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		} else {
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, SPRINT_ID);
		}
	}

	static boolean canAct(ServerPlayer player) {
		FalconState s = state(player);
		return s.hasPower && player.isAlive() && !player.isSpectator();
	}

	static void setAnim(ServerPlayer player, int animId) {
		FalconState n = state(player).copy();
		n.animId = animId;
		n.animStart = player.level().getGameTime();
		save(player, n);
	}

	// ---------------------------------------------------------------- flight (FalconFlight)

	/** Wings out, jets lit (double-tap jump, the Afterburner leap, the top of a Talon Drop). */
	public static void takeOff(ServerPlayer player) {
		FalconFlight.start(player);
	}

	/** The double-tap jump: take off, or drop out of the flight. */
	public static void toggleFlight(ServerPlayer player) {
		FalconFlight.toggle(player);
	}

	/** X held / let go: the jet burn. */
	public static void setBoost(ServerPlayer player, boolean on) {
		FalconFlight.setBoost(player, on);
	}

	/** Folds the wings away (landed, in water, a command). */
	public static void stopFlying(ServerPlayer player) {
		FalconFlight.stop(player);
	}

	public static float ammo(Player player) {
		FalconState s = peek(player);
		return s == null ? 0f : s.ammo;
	}

	/** Spends Wrist Gun rounds; false (nothing spent) if there are not enough. */
	public static boolean spendAmmo(ServerPlayer player, float rounds) {
		FalconState s = state(player);
		if (s.ammo + 1.0e-3f < rounds) {
			return false;
		}
		FalconState n = s.copy();
		n.ammo = Math.max(0f, Math.min(FalconConfig.AMMO_MAX, s.ammo) - rounds);
		save(player, n);
		return true;
	}

	public static void setAmmo(ServerPlayer player, float value) {
		FalconState n = state(player).copy();
		n.ammo = Math.max(0f, Math.min(FalconConfig.AMMO_MAX, value));
		save(player, n);
	}

	// ---------------------------------------------------------------- tick

	/** Every tick, for every player: the gear check (cheap), then the power itself. */
	/** Test hook: let the next {@link #tick} in this same game tick run (it runs once a tick otherwise). */
	public static void forgetTick(ServerPlayer player) {
		LAST_TICK.remove(player.getUUID());
	}

	public static void tick(ServerPlayer player) {
		FalconState s = player.getAttachedOrElse(ModAttachments.FALCON_STATE, null);
		boolean full = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(FalconItems.WING_PACK) && FalconItems.fullSet(player);
		if (s == null) {
			if (!full || player.isSpectator()) {
				return;
			}
			s = state(player);
		}
		long now = player.level().getGameTime();
		Long prev = LAST_TICK.put(player.getUUID(), now);
		if (prev != null && prev == now) {
			return;
		}
		if (!full) {
			if (s.hasPower) {
				revoke(player);
				say(player, "message.projecthero.falcon.gear_off", ChatFormatting.GRAY);
			} else if (s.blocked) {
				FalconState n = s.copy();
				n.blocked = false;
				save(player, n);
			}
			return;
		}
		if (!s.hasPower) {
			if (!s.blocked && player.isAlive() && !player.isSpectator()) {
				grant(player);
			}
			return;
		}
		tickMotion(player);
		if (player.tickCount % 10 == 0 || player.isSprinting() != hasSprintModifier(player)) {
			reconcile(player);
		}
		FalconFlight.tick(player, now);
		if (now % 5 == 0) {
			tickThrust(player, now);
		}
		FalconAbilityManager.tick(player);
		FalconAbilities.tick(player);
	}

	private static boolean hasSprintModifier(ServerPlayer player) {
		var inst = player.getAttribute(Attributes.MOVEMENT_SPEED);
		return inst != null && inst.getModifier(SPRINT_ID) != null;
	}

	private static void tickMotion(ServerPlayer player) {
		Vec3 pos = player.position();
		Vec3 last = LAST_POS.put(player.getUUID(), pos);
		double speed = last == null ? 0.0 : pos.distanceTo(last);
		if (speed > 12.0) {
			speed = 0.0; // a teleport
		}
		Double before = RECENT_SPEED.get(player.getUUID());
		RECENT_SPEED.put(player.getUUID(), Math.max(speed, before == null ? 0.0 : before * 0.5));
	}

	/** Every 5 ticks: Thrust burns while the jets do (and the burn stops when it runs out), else refills; rounds refill when not firing. */
	private static void tickThrust(ServerPlayer player, long now) {
		FalconState s = state(player);
		float cur = Float.isNaN(s.thrust) ? 0f : s.thrust;
		float next = cur;
		boolean boosting = s.boosting;
		if (s.boosting) {
			next = cur - FalconConfig.BOOST_COST_PER_SECOND / 4f;
			if (next <= 0f || !s.flying) {
				next = Math.max(0f, next);
				boosting = false;
			}
		} else if (s.flying) {
			next = cur + FalconConfig.THRUST_REGEN_FLYING / 4f;
		} else if (player.onGround()) {
			next = cur + FalconConfig.THRUST_REGEN_GROUND / 4f;
		}
		next = Math.max(0f, Math.min(FalconConfig.THRUST_MAX, next));
		float ammo = Float.isNaN(s.ammo) ? 0f : s.ammo;
		float nextAmmo = s.firingUntil > now ? ammo : Math.min(FalconConfig.AMMO_MAX, ammo + FalconConfig.AMMO_REGEN_PER_SECOND / 4f);
		if (next != s.thrust || boosting != s.boosting || nextAmmo != s.ammo) {
			FalconState n = s.copy();
			n.thrust = next;
			n.boosting = boosting;
			n.ammo = nextAmmo;
			save(player, n);
		}
	}

	// ---------------------------------------------------------------- lifecycle

	public static void onPlayerJoin(ServerPlayer player) {
		FalconState s = player.getAttachedOrElse(ModAttachments.FALCON_STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		long now = player.level().getGameTime();
		FalconState n = s.copy();
		n.boosting = false;
		n.shieldUntil = 0L;
		n.rollUntil = 0L;
		n.carryId = -1;
		n.firingUntil = 0L;
		n.redwingId = -1;
		n.animId = FalconState.ANIM_NONE;
		n.thrust = Float.isNaN(n.thrust) ? 0f : Math.max(0f, Math.min(FalconConfig.THRUST_MAX, n.thrust));
		n.abilityReadyAt.entrySet().removeIf(e -> e.getValue() > now + FalconConfig.BOMB_COOLDOWN + 20L);
		n.ammo = Float.isNaN(n.ammo) ? 0f : Math.max(0f, Math.min(FalconConfig.AMMO_MAX, n.ammo));
		save(player, n);
		if (n.flying) {
			FalconFlight.resume(player);
		}
		reconcile(player);
	}

	public static void onPlayerRespawn(ServerPlayer player) {
		FalconState s = player.getAttachedOrElse(ModAttachments.FALCON_STATE, null);
		if (s == null) {
			return;
		}
		// the gear stayed on the corpse (or in the inventory with keepInventory): the next tick re-checks the set
		FalconState n = new FalconState();
		save(player, n);
		reconcile(player);
	}

	/** Death / logout / dimension change: drop everything transient (the power follows the gear). */
	public static void clearTransient(ServerPlayer player) {
		FalconFlight.stop(player);
		FalconAbilities.clear(player);
		FalconAbilities.removeRedwing(player);
		forget(player.getUUID());
		FalconState s = player.getAttachedOrElse(ModAttachments.FALCON_STATE, null);
		if (s != null && s.hasPower && (s.flying || s.boosting || s.shieldUntil != 0L || s.carryId != -1 || s.firingUntil != 0L || s.redwingId != -1)) {
			FalconState n = s.copy();
			n.flying = false;
			n.boosting = false;
			n.shieldUntil = 0L;
			n.carryId = -1;
			n.firingUntil = 0L;
			n.redwingId = -1;
			save(player, n);
		}
	}
}
