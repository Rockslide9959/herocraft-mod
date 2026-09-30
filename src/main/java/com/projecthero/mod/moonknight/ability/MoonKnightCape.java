package com.projecthero.mod.moonknight.ability;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.moonknight.MoonKnight;
import com.projecthero.mod.moonknight.MoonKnightAnim;
import com.projecthero.mod.moonknight.MoonKnightConfig;
import com.projecthero.mod.moonknight.data.MoonKnightAction;
import com.projecthero.mod.moonknight.item.MoonKnightTruncheonItem;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Cape (Moon Knight Phase 3). Since v0.13.21 it is on no ability key at all:
 * <ul>
 *   <li><b>Cape Glide</b> -- jump, then hold Sneak in the air: the cape stretches into a triangle of webbing between
 *       the arms and the legs ({@code FLAG_GLIDING}), he tips forward flat and glides like a weaker elytra -- no item,
 *       no rocket boost, no fall damage -- steering where he looks; a glide sinks {@link MoonKnightConfig#GLIDE_SINK}
 *       / lunar power per tick, so it carries much further at night. Letting go of Sneak, landing, water or a ladder
 *       ends it. Gliding into a mob kicks it: {@link MoonKnightConfig#GLIDE_KICK_DAMAGE} x power and knockback.</li>
 *   <li><b>Cape Block</b> -- hold right click with an empty main hand (or the Truncheon): the cape wraps round him
 *       ({@code FLAG_CAPE_BLOCK}) and every hit does {@link MoonKnightConfig#CAPE_BLOCK_FACTOR} (30% less) for as
 *       long as it is held -- no time limit, no cooldown -- at half speed. The client sends the press / release
 *       ({@code MoonKnightActionPayload.CAPE_BLOCK_START / STOP}); the server re-validates.</li>
 *   <li><b>Shadow Step</b> ({@link #shadowStep}, fired by SNEAK+G): a blink
 *       {@link MoonKnightConfig#SHADOW_STEP_DISTANCE} x power blocks straight back in a puff of shadow, and 3 s x power
 *       of invisibility.</li>
 * </ul>
 *
 * <p><b>How the glide moves.</b> The player's own client is authoritative for its movement (a server
 * {@code setDeltaMovement} every tick would fight it and rubber-band), so -- the same split as Spider-Man's swing --
 * the server only decides: it reads the synced Sneak state and sets / clears {@code FLAG_GLIDING}, keeps fall distance
 * at zero, and ends the glide on landing. The gliding player's client ({@code MoonKnightCombatClient}) reads its own
 * synced flag every tick and steers its velocity through {@link #glideVelocity}, the one pure function both sides
 * share. The sink rate never drops below {@link MoonKnightConfig#GLIDE_MIN_SINK}, so the server's "flying is not
 * enabled" check stays happy.
 */
public final class MoonKnightCape implements MoonKnightMove {
	public static final MoonKnightCape INSTANCE = new MoonKnightCape();

	private static final ResourceLocation BLOCK_SLOW = ProjectHeroMod.id("moon_knight_shroud_slow");

	/** Game time the last glide ended (for the landing grace). */
	private static final Map<UUID, Long> GLIDE_ENDED = new ConcurrentHashMap<>();
	/** Consecutive server ticks off the ground (a glide needs a jump first). */
	private static final Map<UUID, Integer> AIR_TICKS = new ConcurrentHashMap<>();
	/** Game time of the last glide kick (one kick per {@link MoonKnightConfig#GLIDE_KICK_GAP_TICKS}). */
	private static final Map<UUID, Long> LAST_KICK = new ConcurrentHashMap<>();

	private MoonKnightCape() {
	}

	// ---------------------------------------------------------------- Cape Glide (jump + hold Sneak)

	public static boolean isGliding(Player player) {
		return MoonKnightAnim.flag(player, MoonKnightAction.FLAG_GLIDING);
	}

	private static boolean canGlide(ServerPlayer player) {
		// v0.14.4: no cape, no glide -- Steven's Mr. Knight suit has none (switching to him mid-glide ends it)
		return MoonKnight.alter(player).hasCape() && !player.onGround() && !player.isInWater() && !player.isInLava() && !player.isFallFlying()
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

	/**
	 * Per tick: the glide follows Sneak. Airborne for {@link MoonKnightConfig#GLIDE_MIN_AIR_TICKS}+ with Sneak held
	 * starts it; letting go of Sneak (or landing, water, a ladder...) ends it. Public for the gametests.
	 */
	public static void tickGlide(ServerPlayer player) {
		UUID id = player.getUUID();
		int air = player.onGround() ? 0 : AIR_TICKS.getOrDefault(id, 0) + 1;
		AIR_TICKS.put(id, Math.min(air, 1000));
		if (!isGliding(player)) {
			if (player.isShiftKeyDown() && air >= MoonKnightConfig.GLIDE_MIN_AIR_TICKS && canGlide(player)
					&& !MoonKnightGrapple.isPulling(player) && !MoonKnightDash.isDashing(player)) {
				startGlide(player);
			}
			return;
		}
		if (!canGlide(player) || !player.isShiftKeyDown()) {
			stopGlide(player);
			return;
		}
		player.resetFallDistance();
		glideKick(player);
		long t = player.level().getGameTime();
		if (t % 3 == 0) {
			Vec3 back = player.getLookAngle().multiply(1, 0, 1).normalize().scale(-0.6);
			player.serverLevel().sendParticles(MoonKnightCombat.MOON, player.getX() + back.x, player.getY() + 0.6,
					player.getZ() + back.z, 2, 0.5, 0.1, 0.5, 0.0);
		}
		if (t % 40 == 0) {
			AbilityHelpers.sound(player, SoundEvents.PHANTOM_FLAP, 0.3f, 1.1f);
		}
	}

	/**
	 * Gliding into a mob: the first enemy the gliding body touches takes {@link MoonKnightConfig#GLIDE_KICK_DAMAGE} x
	 * power and is knocked along the flight line; then {@link MoonKnightConfig#GLIDE_KICK_GAP_TICKS} before the next.
	 * Returns how many were kicked (0 or 1). Public for the gametests.
	 */
	public static int glideKick(ServerPlayer player) {
		long now = player.level().getGameTime();
		Long last = LAST_KICK.get(player.getUUID());
		if (last != null && now - last < MoonKnightConfig.GLIDE_KICK_GAP_TICKS) {
			return 0;
		}
		Vec3 v = player.getDeltaMovement();
		Vec3 flat = new Vec3(v.x, 0.0, v.z);
		Vec3 ahead = flat.lengthSqr() < 1.0e-4 ? player.getLookAngle().multiply(1, 0, 1) : flat;
		ahead = ahead.lengthSqr() < 1.0e-4 ? Vec3.ZERO : ahead.normalize();
		// the prone body: a little forward of the feet box, and reaching down to the legs
		AABB reach = player.getBoundingBox().inflate(0.5, 0.4, 0.5).move(ahead.scale(0.6));
		LivingEntity hit = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity e : player.serverLevel().getEntitiesOfClass(LivingEntity.class, reach,
				e -> e != player && e.isAlive() && !MoonKnightCombat.friendly(player, e))) {
			double d = e.distanceToSqr(player);
			if (d < best) {
				best = d;
				hit = e;
			}
		}
		if (hit == null) {
			return 0;
		}
		float power = MoonKnightAbilities.power(player);
		LAST_KICK.put(player.getUUID(), now);
		MoonKnightCombat.hit(player, hit, MoonKnightConfig.GLIDE_KICK_DAMAGE * power);
		MoonKnightCombat.knock(hit, player.position().subtract(ahead), MoonKnightConfig.GLIDE_KICK_KNOCKBACK * power, 0.25);
		MoonKnightAnim.play(player, MoonKnightAnim.GLIDE_KICK);
		ServerLevel level = player.serverLevel();
		Vec3 at = hit.position().add(0, hit.getBbHeight() * 0.5, 0);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.3);
		level.sendParticles(MoonKnightCombat.MOON, at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.02);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.9f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_AIR, net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.3f);
		return 1;
	}

	// ---------------------------------------------------------------- Cape Block (hold right click)

	/** Is a Cape Block allowed right now: suited, the main hand empty or holding the Truncheon, not using an item. */
	public static boolean canBlock(Player player) {
		ItemStack main = player.getMainHandItem();
		// v0.14.4: no cape, no Cape Block (Steven)
		return MoonKnight.isTransformed(player) && MoonKnight.alter(player).hasCape() && !player.isSpectator() && !player.isUsingItem()
				&& (main.isEmpty() || main.getItem() instanceof MoonKnightTruncheonItem);
	}

	public static boolean isBlocking(Player player) {
		return MoonKnightAnim.flag(player, MoonKnightAction.FLAG_CAPE_BLOCK);
	}

	/** Right click went down (payload): raise the cape. */
	public static void startBlock(ServerPlayer player) {
		if (!canBlock(player) || isBlocking(player)) {
			return;
		}
		MoonKnightAnim.setFlag(player, MoonKnightAction.FLAG_CAPE_BLOCK, true);
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(BLOCK_SLOW, MoonKnightConfig.CAPE_BLOCK_SPEED_PENALTY,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		ServerLevel level = player.serverLevel();
		level.sendParticles(MoonKnightCombat.MOON, player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.5, 0.6, 0.5, 0.02);
		AbilityHelpers.sound(player, SoundEvents.ARMOR_EQUIP_LEATHER, 1.0f, 0.7f);
		AbilityHelpers.sound(player, SoundEvents.PHANTOM_FLAP, 0.5f, 0.6f);
	}

	/** Right click released (payload), or the block stopped being legal: lower the cape. */
	public static void stopBlock(ServerPlayer player) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(BLOCK_SLOW);
		}
		if (isBlocking(player)) {
			MoonKnightAnim.setFlag(player, MoonKnightAction.FLAG_CAPE_BLOCK, false);
			AbilityHelpers.sound(player, SoundEvents.ARMOR_EQUIP_LEATHER, 0.8f, 1.2f);
		}
	}

	/** A hit landed on the raised cape. */
	private static void onBlockedHit(ServerPlayer player) {
		player.serverLevel().sendParticles(MoonKnightCombat.PALE_BLUE, player.getX(), player.getY() + 1.0, player.getZ(),
				6, 0.4, 0.5, 0.4, 0.02);
		AbilityHelpers.sound(player, SoundEvents.WOOL_HIT, 1.0f, 0.8f);
	}

	// ---------------------------------------------------------------- Shadow Step (SNEAK+G)

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
		MoonKnightAbilities.cooldown(player, "kick_sneak", MoonKnightConfig.SHADOW_STEP_COOLDOWN);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, net.minecraft.sounds.SoundSource.PLAYERS, 0.9f, 0.8f);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.PHANTOM_FLAP, net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 0.6f);
		return moved;
	}

	// ---------------------------------------------------------------- upkeep + damage hooks

	@Override
	public void tick(ServerPlayer player) {
		tickGlide(player);
		if (isBlocking(player) && !canBlock(player)) {
			stopBlock(player); // picked something up, started eating, ...
		}
	}

	@Override
	public void onUntransform(ServerPlayer player) {
		stopGlide(player);
		stopBlock(player);
		AIR_TICKS.remove(player.getUUID());
	}

	/** Incoming-damage multiplier from the cape (Cape Block: every hit 30% less). */
	public static float incomingFactor(ServerPlayer player, DamageSource source) {
		if (!isBlocking(player)) {
			return 1.0f;
		}
		onBlockedHit(player);
		return MoonKnightConfig.CAPE_BLOCK_FACTOR;
	}

	/** True if a fall should do no damage right now: gliding, just landed from a glide, or mid grapple / dash / dive. */
	public static boolean negatesFall(ServerPlayer player) {
		if (isGliding(player)) {
			return true;
		}
		Long ended = GLIDE_ENDED.get(player.getUUID());
		if (ended != null && player.level().getGameTime() - ended <= MoonKnightConfig.GLIDE_FALL_GRACE_TICKS) {
			return true;
		}
		return MoonKnightGrapple.protectsFromFall(player) || MoonKnightTruncheon.protectsFromFall(player)
				|| MoonKnightDash.protectsFromFall(player);
	}

	public static void clearSessionState() {
		GLIDE_ENDED.clear();
		AIR_TICKS.clear();
		LAST_KICK.clear();
	}
}
