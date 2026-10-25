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
 * A physics wingsuit on the vanilla glide (the elytra flight, enabled by {@code EntityElytraEvents.CUSTOM} while
 * {@link FalconState#flying}): double-tap jump takes off, diving builds speed, pulling up trades it for height, and the
 * client adds the jet boost (Sprint, burns Thrust), the wing flap (Space), the ground skim and the camera bank. Touching
 * down ends it -- a fast landing is a hero landing.
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
	/** Game time of the last take-off (the first few ticks may still touch the ground). */
	private static final Map<UUID, Long> TAKE_OFF_AT = new ConcurrentHashMap<>();

	private Falcon() {
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
		LAST_TICK.clear();
		LAST_POS.clear();
		RECENT_SPEED.clear();
		TAKE_OFF_AT.clear();
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
		FalconAbilities.clear(player);
		FalconAbilities.removeRedwing(player);
		FalconState old = state(player);
		FalconState n = new FalconState();
		n.blocked = FalconItems.fullSet(player);
		n.abilityReadyAt.putAll(old.abilityReadyAt);
		save(player, n);
		if (player.isFallFlying() && old.flying) {
			player.stopFallFlying();
		}
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
		TAKE_OFF_AT.remove(id);
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

	// ---------------------------------------------------------------- flight

	/** Double-tap jump: the wings snap open with a jet burst (from the ground, a jet kick up first). */
	public static void takeOff(ServerPlayer player) {
		FalconState s = state(player);
		if (!s.hasPower || s.flying || !player.isAlive() || player.isPassenger() || player.isInWater() || player.getAbilities().flying) {
			return;
		}
		long now = player.level().getGameTime();
		FalconState n = s.copy();
		n.flying = true;
		n.animId = FalconState.ANIM_TAKEOFF;
		n.animStart = now;
		save(player, n);
		TAKE_OFF_AT.put(player.getUUID(), now);
		if (player.onGround()) {
			Vec3 look = player.getLookAngle();
			AbilityHelpers.launchSelf(player, new Vec3(look.x * 0.35, FalconConfig.TAKEOFF_LIFT, look.z * 0.35));
		} else {
			player.startFallFlying();
		}
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position();
		level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.2, c.z, 14, 0.5, 0.1, 0.5, 0.06);
		level.sendParticles(ParticleTypes.FLAME, c.x, c.y + 0.8, c.z, 8, 0.2, 0.2, 0.2, 0.02);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 1.2f, 0.7f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0f, 0.8f);
	}

	/** Space while gliding: a wing flap (the climb itself is applied by the client, which moves the player). */
	public static void flap(ServerPlayer player) {
		FalconState s = state(player);
		if (!s.hasPower || !s.flying) {
			return;
		}
		if (cooldownRemaining(player, FalconAbilities.FLAP) > 0 || !spendThrust(player, FalconConfig.FLAP_COST)) {
			return;
		}
		FalconState n = state(player).copy();
		n.abilityReadyAt.put(FalconAbilities.FLAP, player.level().getGameTime() + FalconConfig.FLAP_COOLDOWN);
		save(player, n);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 0.9, 0);
		level.sendParticles(WHITE, c.x, c.y, c.z, 10, 0.9, 0.15, 0.9, 0.0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 1.0f, 1.1f);
	}

	public static void setBoost(ServerPlayer player, boolean on) {
		FalconState s = state(player);
		boolean want = on && s.hasPower && s.flying && s.thrust > 0.5f;
		if (s.boosting == want) {
			return;
		}
		FalconState n = s.copy();
		n.boosting = want;
		save(player, n);
		if (want) {
			Vec3 c = player.position();
			player.level().playSound(null, c.x, c.y, c.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.8f, 0.6f);
		}
	}

	/** Folds the wings away (landed, in water, a command). */
	public static void stopFlying(ServerPlayer player) {
		FalconState s = state(player);
		if (!s.flying) {
			return;
		}
		FalconState n = s.copy();
		n.flying = false;
		n.boosting = false;
		save(player, n);
		if (player.isFallFlying()) {
			player.stopFallFlying();
		}
	}

	private static void land(ServerPlayer player, double speed) {
		stopFlying(player);
		player.resetFallDistance();
		if (speed < FalconConfig.HERO_LANDING_SPEED) {
			return;
		}
		setAnim(player, FalconState.ANIM_LAND);
		ServerLevel level = (ServerLevel) player.level();
		BlockPos below = player.blockPosition().below();
		BlockState ground = level.getBlockState(below);
		Vec3 c = player.position();
		if (!ground.isAir()) {
			BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, ground);
			for (int i = 0; i < 28; i++) {
				double a = i * Math.PI * 2 / 28;
				for (double r = 1.0; r <= 2.8; r += 0.9) {
					level.sendParticles(dust, c.x + Math.cos(a) * r, c.y + 0.1, c.z + Math.sin(a) * r, 1, 0.05, 0.02, 0.05, 0.12);
				}
			}
		}
		level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.1, c.z, 16, 1.2, 0.05, 1.2, 0.05);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35f, 0.6f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_SMALL_FALL, SoundSource.PLAYERS, 1.0f, 0.6f);
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
		tickFlight(player, now);
		if (now % 5 == 0) {
			tickThrust(player);
		}
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

	private static void tickFlight(ServerPlayer player, long now) {
		FalconState s = state(player);
		if (!s.flying) {
			return;
		}
		player.resetFallDistance();
		if (player.isInWater() || player.isPassenger() || player.getAbilities().flying || !player.isAlive()) {
			stopFlying(player);
			return;
		}
		Long at = TAKE_OFF_AT.get(player.getUUID());
		long age = at == null ? 100L : now - at;
		if (player.onGround() && age > 4) {
			land(player, recentSpeed(player));
			return;
		}
		if (!player.isFallFlying() && !player.onGround() && age >= 2) {
			player.startFallFlying();
		}
		// the jets: flame and smoke from the pack while boosting; contrails at speed
		ServerLevel level = (ServerLevel) player.level();
		if (s.boosting && now % 2 == 0) {
			Vec3 back = player.position().add(0, 0.9, 0).subtract(player.getLookAngle().scale(0.6));
			level.sendParticles(ParticleTypes.FLAME, back.x, back.y, back.z, 2, 0.08, 0.08, 0.08, 0.01);
			level.sendParticles(ParticleTypes.SMOKE, back.x, back.y, back.z, 1, 0.1, 0.1, 0.1, 0.01);
		}
		if (recentSpeed(player) > 1.2 && now % 3 == 0) {
			Vec3 c = player.position().add(0, 0.9, 0);
			Vec3 look = player.getLookAngle();
			Vec3 side = look.cross(new Vec3(0, 1, 0));
			side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
			for (int k = -1; k <= 1; k += 2) {
				Vec3 tip = c.add(side.scale(1.3 * k)).subtract(look.scale(0.4));
				level.sendParticles(ParticleTypes.CLOUD, tip.x, tip.y, tip.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
	}

	private static void tickThrust(ServerPlayer player) {
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
			next = cur + FalconConfig.THRUST_REGEN_GLIDE / 4f;
		} else if (player.onGround()) {
			next = cur + FalconConfig.THRUST_REGEN_GROUND / 4f;
		}
		next = Math.max(0f, Math.min(FalconConfig.THRUST_MAX, next));
		if (next != s.thrust || boosting != s.boosting) {
			FalconState n = s.copy();
			n.thrust = next;
			n.boosting = boosting;
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
		n.flying = false;
		n.boosting = false;
		n.shieldUntil = 0L;
		n.rollUntil = 0L;
		n.carryId = -1;
		n.firingUntil = 0L;
		n.redwingId = -1;
		n.animId = FalconState.ANIM_NONE;
		n.thrust = Float.isNaN(n.thrust) ? 0f : Math.max(0f, Math.min(FalconConfig.THRUST_MAX, n.thrust));
		n.abilityReadyAt.entrySet().removeIf(e -> e.getValue() > now + FalconConfig.BOMB_COOLDOWN + 20L);
		save(player, n);
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
