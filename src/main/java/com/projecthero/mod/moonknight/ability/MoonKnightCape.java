package com.projecthero.mod.moonknight.ability;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.moonknight.MoonKnightAnim;
import com.projecthero.mod.moonknight.MoonKnightConfig;
import com.projecthero.mod.moonknight.data.MoonKnightAction;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * X -- the Cape (Moon Knight Phase 3).
 * <ul>
 *   <li><b>TAP</b>: Cape Glide on / off. While airborne the cape spreads ({@code FLAG_GLIDING}) and he glides like a
 *       weaker elytra -- no item, no rocket boost, no fall damage -- steering where he looks; a glide sinks
 *       {@link MoonKnightConfig#GLIDE_SINK} / lunar power per tick, so it carries much further at night. It ends on a
 *       second tap, on landing, in water, or on a ladder.</li>
 *   <li><b>HOLD</b>: Cape Shroud -- wrapped in the cape ({@code FLAG_SHROUD}): projectiles do 40%, melee 75%, and he
 *       moves at half speed. At most {@link MoonKnightConfig#SHROUD_MAX_TICKS} x power; cooldown
 *       {@link MoonKnightConfig#SHROUD_COOLDOWN} starts when it ends.</li>
 *   <li><b>SNEAK+X</b>: Shadow Step -- a blink {@link MoonKnightConfig#SHADOW_STEP_DISTANCE} x power blocks straight
 *       back (as far as there is room) in a puff of shadow, and 3 s x power of invisibility.</li>
 * </ul>
 *
 * <p><b>How the glide moves.</b> The player's own client is authoritative for its movement (a server
 * {@code setDeltaMovement} every tick would fight it and rubber-band), so -- the same split as Spider-Man's swing --
 * the server only decides: it sets / clears the synced {@code FLAG_GLIDING}, keeps fall distance at zero, and ends
 * the glide on landing. The gliding player's client ({@code MoonKnightGlideClient}) reads its own synced flag every
 * tick and steers its velocity through {@link #glideVelocity}, the one pure function both sides share. The sink rate
 * never drops below {@link MoonKnightConfig#GLIDE_MIN_SINK}, so the server's "flying is not enabled" check stays happy.
 */
public final class MoonKnightCape implements MoonKnightMove {
	public static final MoonKnightCape INSTANCE = new MoonKnightCape();

	private static final ResourceLocation SHROUD_SLOW = ProjectHeroMod.id("moon_knight_shroud_slow");

	/** Game time the last glide ended (for the landing grace). */
	private static final Map<UUID, Long> GLIDE_ENDED = new ConcurrentHashMap<>();
	/** Game time the current shroud started. */
	private static final Map<UUID, Long> SHROUD_START = new ConcurrentHashMap<>();

	private MoonKnightCape() {
	}

	// ---------------------------------------------------------------- TAP: Cape Glide

	@Override
	public void tap(ServerPlayer player) {
		if (isGliding(player)) {
			stopGlide(player);
			return;
		}
		if (!canGlide(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.moon_knight.glide_airborne")
					.withStyle(ChatFormatting.GRAY), true);
			return;
		}
		startGlide(player);
	}

	public static boolean isGliding(Player player) {
		return MoonKnightAnim.flag(player, MoonKnightAction.FLAG_GLIDING);
	}

	private static boolean canGlide(ServerPlayer player) {
		return !player.onGround() && !player.isInWater() && !player.isInLava() && !player.isFallFlying()
				&& !player.getAbilities().flying && !player.isPassenger() && !player.onClimbable();
	}

	public static void startGlide(ServerPlayer player) {
		MoonKnightAnim.setFlag(player, MoonKnightAction.FLAG_GLIDING, true);
		player.resetFallDistance();
		ServerLevel level = player.serverLevel();
		level.sendParticles(MoonKnightCombat.MOON, player.getX(), player.getY() + 1.2, player.getZ(), 14, 0.6, 0.2, 0.6, 0.02);
		AbilityHelpers.sound(player, SoundEvents.PHANTOM_FLAP, 0.8f, 0.8f);
		AbilityHelpers.sound(player, SoundEvents.ARMOR_EQUIP_ELYTRA, 0.7f, 1.2f);
	}

	public static void stopGlide(ServerPlayer player) {
		if (!isGliding(player)) {
			return;
		}
		MoonKnightAnim.setFlag(player, MoonKnightAction.FLAG_GLIDING, false);
		GLIDE_ENDED.put(player.getUUID(), player.level().getGameTime());
		player.resetFallDistance();
		AbilityHelpers.sound(player, SoundEvents.PHANTOM_FLAP, 0.5f, 1.3f);
	}

	/** Vanilla's airborne step: gravity, then these drags, applied after every move. */
	private static final double GRAVITY = 0.08;
	private static final double DRAG_Y = 0.98;
	private static final double DRAG_XZ = 0.91;

	/**
	 * The glide, as a pure function (shared by the client that moves the player and by the tests).
	 *
	 * @param v the velocity vanilla left after its own airborne step (gravity + drag already applied); it is undone
	 *          first, so the result is exactly the velocity of the next move
	 * @return the velocity for the next move: horizontally steered toward where the player looks; vertically eased to
	 *         a gentle sink (a jump still rises until gravity brings it down to the sink rate). Looking down dives
	 *         faster and steeper; looking up bleeds speed and flattens the glide a little -- it never climbs.
	 */
	public static Vec3 glideVelocity(Vec3 v, float yawDeg, float pitchDeg, float power) {
		double dive = Mth.clamp(pitchDeg / 60.0, -0.5, 1.0);
		double down = Math.max(0.0, dive);
		double up = Math.max(0.0, -dive);
		double speed = MoonKnightConfig.GLIDE_SPEED * (1.0 + MoonKnightConfig.GLIDE_DIVE_SPEED_BONUS * down) * (1.0 - 0.3 * up);
		double sink = MoonKnightConfig.GLIDE_BETTER_AT_NIGHT
				? MoonKnightConfig.GLIDE_SINK / Math.max(0.1, power) : MoonKnightConfig.GLIDE_SINK;
		sink = Math.max(MoonKnightConfig.GLIDE_MIN_SINK, sink * (1.0 + 2.5 * down) * (1.0 - 0.3 * up));
		// the velocity actually moved with last tick, before vanilla's gravity / drag
		double px = v.x / DRAG_XZ;
		double pz = v.z / DRAG_XZ;
		double py = v.y / DRAG_Y + GRAVITY;
		double yaw = Math.toRadians(yawDeg);
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double k = MoonKnightConfig.GLIDE_STEER;
		double hx = px + (fx * speed - px) * k;
		double hz = pz + (fz * speed - pz) * k;
		double target = -sink;
		double vy = py > target ? Math.max(target, py - GRAVITY) : py + (target - py) * 0.35;
		return new Vec3(hx, vy, hz);
	}

	/** Vanilla's airborne step after a move (what {@link #glideVelocity} undoes). */
	public static Vec3 vanillaAirStep(Vec3 moved) {
		return new Vec3(moved.x * DRAG_XZ, (moved.y - GRAVITY) * DRAG_Y, moved.z * DRAG_XZ);
	}

	/** The per-tick descent a level glide settles at for this lunar power (tests). */
	public static double settledSink(float power) {
		Vec3 moved = new Vec3(0.0, -0.3, 0.0);
		for (int i = 0; i < 80; i++) {
			moved = glideVelocity(vanillaAirStep(moved), 0.0f, 0.0f, power);
		}
		return -moved.y;
	}

	private static void tickGlide(ServerPlayer player) {
		if (!isGliding(player)) {
			return;
		}
		if (!canGlide(player)) {
			stopGlide(player);
			return;
		}
		player.resetFallDistance();
		long t = player.level().getGameTime();
		if (t % 3 == 0) {
			Vec3 back = player.getLookAngle().multiply(1, 0, 1).normalize().scale(-0.6);
			player.serverLevel().sendParticles(MoonKnightCombat.MOON, player.getX() + back.x, player.getY() + 1.1,
					player.getZ() + back.z, 2, 0.5, 0.1, 0.5, 0.0);
		}
		if (t % 40 == 0) {
			AbilityHelpers.sound(player, SoundEvents.PHANTOM_FLAP, 0.3f, 1.1f);
		}
	}

	// ---------------------------------------------------------------- HOLD: Cape Shroud

	@Override
	public void holdStart(ServerPlayer player) {
		if (!MoonKnightAbilities.ready(player, "cape_hold")) {
			return;
		}
		SHROUD_START.put(player.getUUID(), player.level().getGameTime());
		MoonKnightAnim.setFlag(player, MoonKnightAction.FLAG_SHROUD, true);
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(SHROUD_SLOW, MoonKnightConfig.SHROUD_SPEED_PENALTY,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		ServerLevel level = player.serverLevel();
		level.sendParticles(MoonKnightCombat.MOON, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.5, 0.6, 0.5, 0.02);
		AbilityHelpers.sound(player, SoundEvents.ARMOR_EQUIP_LEATHER, 1.0f, 0.7f);
		AbilityHelpers.sound(player, SoundEvents.PHANTOM_FLAP, 0.6f, 0.6f);
	}

	public static boolean isShrouded(Player player) {
		return MoonKnightAnim.flag(player, MoonKnightAction.FLAG_SHROUD);
	}

	@Override
	public void holdTick(ServerPlayer player, int ticksHeld) {
		Long start = SHROUD_START.get(player.getUUID());
		if (start == null) {
			return;
		}
		long now = player.level().getGameTime();
		if (now - start >= Math.round(MoonKnightConfig.SHROUD_MAX_TICKS * MoonKnightAbilities.power(player))) {
			endShroud(player);
			return;
		}
		if (now % 4 == 0) {
			player.serverLevel().sendParticles(MoonKnightCombat.PALE_BLUE, player.getX(), player.getY() + 1.0,
					player.getZ(), 3, 0.45, 0.6, 0.45, 0.0);
		}
	}

	@Override
	public void holdRelease(ServerPlayer player, int ticksHeld) {
		endShroud(player);
	}

	@Override
	public void cancelHold(ServerPlayer player) {
		endShroud(player);
	}

	/** End a shroud in progress (no-op if none) and start its cooldown. */
	public static void endShroud(ServerPlayer player) {
		Long start = SHROUD_START.remove(player.getUUID());
		removeShroudSlow(player);
		MoonKnightAnim.setFlag(player, MoonKnightAction.FLAG_SHROUD, false);
		if (start != null) {
			MoonKnightAbilities.cooldown(player, "cape_hold", MoonKnightConfig.SHROUD_COOLDOWN);
			AbilityHelpers.sound(player, SoundEvents.ARMOR_EQUIP_LEATHER, 0.8f, 1.2f);
		}
	}

	private static void removeShroudSlow(ServerPlayer player) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(SHROUD_SLOW);
		}
	}

	// ---------------------------------------------------------------- SNEAK: Shadow Step

	@Override
	public void sneak(ServerPlayer player) {
		if (!MoonKnightAbilities.ready(player, "cape_sneak")) {
			return;
		}
		shadowStep(player);
	}

	/** Blink straight back as far as there is room (up to 5 x power blocks); returns the distance moved. */
	public static double shadowStep(ServerPlayer player) {
		float power = MoonKnightAbilities.power(player);
		Vec3 look = player.getLookAngle();
		Vec3 back = new Vec3(-look.x, 0.0, -look.z);
		if (back.lengthSqr() < 1.0e-4) {
			float yaw = player.getYRot() * Mth.DEG_TO_RAD;
			back = new Vec3(Mth.sin(yaw), 0.0, -Mth.cos(yaw));
		}
		back = back.normalize();
		double max = MoonKnightConfig.SHADOW_STEP_DISTANCE * power;
		ServerLevel level = player.serverLevel();
		Vec3 from = player.position();
		AABB box = player.getBoundingBox();
		double moved = 0.0;
		for (double d = 0.25; d <= max + 1.0e-6; d += 0.25) {
			Vec3 off = back.scale(d);
			if (!level.noCollision(player, box.move(off))) {
				break;
			}
			moved = d;
		}
		Vec3 to = from.add(back.scale(moved));
		level.sendParticles(MoonKnightCombat.SHADOW, from.x, from.y + 1.0, from.z, 30, 0.35, 0.7, 0.35, 0.02);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, from.x, from.y + 1.0, from.z, 10, 0.3, 0.6, 0.3, 0.02);
		AbilityHelpers.line(level, from.add(0, 1.0, 0), to.add(0, 1.0, 0), ParticleTypes.SMOKE, 3.0);
		if (moved > 0.0) {
			player.teleportTo(to.x, to.y, to.z);
			player.resetFallDistance();
		}
		level.sendParticles(MoonKnightCombat.SHADOW, to.x, to.y + 1.0, to.z, 20, 0.35, 0.7, 0.35, 0.02);
		level.sendParticles(ParticleTypes.SQUID_INK, to.x, to.y + 1.0, to.z, 6, 0.3, 0.5, 0.3, 0.02);
		int invis = Math.round(MoonKnightConfig.SHADOW_STEP_INVIS_TICKS * power);
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, invis, 0, false, false, true));
		MoonKnightAnim.play(player, MoonKnightAnim.SHADOW_STEP);
		MoonKnightAbilities.cooldown(player, "cape_sneak", MoonKnightConfig.SHADOW_STEP_COOLDOWN);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, net.minecraft.sounds.SoundSource.PLAYERS, 0.9f, 0.8f);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.PHANTOM_FLAP, net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 0.6f);
		return moved;
	}

	// ---------------------------------------------------------------- upkeep + damage hooks

	@Override
	public void tick(ServerPlayer player) {
		tickGlide(player);
		// a shroud with no key behind it (a relog mid-hold) never outlives its flag
		if (isShrouded(player) && !SHROUD_START.containsKey(player.getUUID())) {
			endShroud(player);
		}
	}

	@Override
	public void onUntransform(ServerPlayer player) {
		stopGlide(player);
		endShroud(player);
	}

	/** Incoming-damage multiplier from this key (Cape Shroud: projectiles 40%, melee 75%). */
	public static float incomingFactor(ServerPlayer player, DamageSource source) {
		if (!isShrouded(player)) {
			return 1.0f;
		}
		if (source.is(DamageTypeTags.IS_PROJECTILE)) {
			return MoonKnightConfig.SHROUD_PROJECTILE_FACTOR;
		}
		Entity direct = source.getDirectEntity();
		if (direct instanceof LivingEntity && direct == source.getEntity() && !source.is(DamageTypeTags.IS_EXPLOSION)) {
			return MoonKnightConfig.SHROUD_MELEE_FACTOR;
		}
		return 1.0f;
	}

	/** True if a fall should do no damage right now: gliding, just landed from a glide, or mid grapple / dive. */
	public static boolean negatesFall(ServerPlayer player) {
		if (isGliding(player)) {
			return true;
		}
		Long ended = GLIDE_ENDED.get(player.getUUID());
		if (ended != null && player.level().getGameTime() - ended <= MoonKnightConfig.GLIDE_FALL_GRACE_TICKS) {
			return true;
		}
		return MoonKnightGrapple.protectsFromFall(player) || MoonKnightTruncheon.protectsFromFall(player);
	}

	public static void clearSessionState() {
		GLIDE_ENDED.clear();
		SHROUD_START.clear();
	}
}
