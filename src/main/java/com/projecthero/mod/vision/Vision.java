package com.projecthero.mod.vision;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.vision.data.VisionAttachments;
import com.projecthero.mod.vision.data.VisionState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.17.3 Vision (the synthezoid): the single server-side API of the power. Nothing else changes {@link VisionState}. The numbers are
 * in {@link VisionConfig}; the moves are {@link VisionMoves}' (routed by {@link VisionAbilityManager}), the flight {@link VisionFlight}'s,
 * the hits {@link VisionDamage}'s. See {@code docs/VISION_DESIGN.md}.
 *
 * <ul>
 *   <li><b>Passives</b>: 45 health, 30% damage resistance, +6 melee; no hunger (food held at 18, no saturation), no drowning, immune to
 *   poison, wither, falls and suffocation.</li>
 *   <li><b>Mind Stone energy</b> (0..100) pays for the moves; it refills 2.5 a second, 4 in direct sunlight, 1.5 in the dark.</li>
 *   <li><b>Toggles</b>: the Human Hologram (V, cosmetic: your own skin over the suit), Intangibility (Shift+V: nothing hits him, he
 *   can't attack, he walks through walls), Diamond Density (Shift+C: 65% off every hit, knockback immunity, slower, no flight).</li>
 * </ul>
 */
public final class Vision {
	public static final String KEY = "vision";

	private static final ResourceLocation HEALTH_ID = PowerToggles.id("vision_health");
	private static final ResourceLocation ATTACK_ID = PowerToggles.id("vision_attack");
	private static final ResourceLocation SPEED_ID = PowerToggles.id("vision_density_speed");
	private static final ResourceLocation KNOCKBACK_ID = PowerToggles.id("vision_density_knockback");

	/** The Mind Stone's gold-yellow (the beams, the HUD meter). */
	public static final int GOLD = 0xFFD23C;
	public static final int GOLD_DARK = 0xC89A1E;
	/** The suit's maroon and green (sparks, dust). */
	public static final int MAROON = 0x8A3A47;
	public static final int GREEN = 0x4F8A72;

	private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();

	private Vision() {
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
		LAST_TICK.clear();
		VisionAbilityManager.clearSessionState();
		VisionMoves.clearSessionState();
		VisionFlight.clearSessionState();
	}

	static void forget(UUID id) {
		LAST_MESSAGE.remove(id);
		LAST_TICK.remove(id);
		VisionAbilityManager.forget(id);
		VisionMoves.forget(id);
		VisionFlight.forget(id);
	}

	// ---------------------------------------------------------------- state (client and server)

	public static VisionState state(ServerPlayer player) {
		return player.getAttachedOrCreate(VisionAttachments.STATE);
	}

	static void save(ServerPlayer player, VisionState state) {
		player.setAttached(VisionAttachments.STATE, state);
	}

	public static VisionState peek(Player player) {
		return player.getAttachedOrElse(VisionAttachments.STATE, null);
	}

	public static boolean hasPower(Player player) {
		VisionState s = peek(player);
		return s != null && s.hasPower;
	}

	static long now(Player player) {
		return player.level().getGameTime();
	}

	/** Double-tap Space flight is on. Client-safe. */
	public static boolean isFlying(Player player) {
		VisionState s = peek(player);
		return s != null && s.hasPower && s.flying;
	}

	public static float energy(Player player) {
		VisionState s = peek(player);
		return s == null ? 0f : s.energy;
	}

	/** R held: the Mind Stone Beam. Client-safe. */
	public static boolean beaming(Player player) {
		VisionState s = peek(player);
		return s != null && s.hasPower && s.beam;
	}

	/** Shift+Z: Mind Stone Unleashed is channelling. Client-safe. */
	public static boolean unleashing(Player player) {
		VisionState s = peek(player);
		return s != null && s.hasPower && s.unleash;
	}

	/** Ticks since Mind Stone Unleashed began (-1 = not channelling). Client-safe. */
	public static int unleashAge(Player player) {
		VisionState s = peek(player);
		return s == null || !s.hasPower || !s.unleash ? -1 : (int) Math.max(0L, now(player) - s.unleashStart);
	}

	/** Shift+R: the Solar Lance is charging. Client-safe. */
	public static boolean chargingLance(Player player) {
		VisionState s = peek(player);
		return s != null && s.hasPower && s.lanceStart > 0L;
	}

	/** V: the Human Hologram is up (or building). Client-safe. */
	public static boolean hologram(Player player) {
		VisionState s = peek(player);
		return s != null && s.hasPower && s.hologram;
	}

	/** Shift+V: Intangibility is on. Client-safe. */
	public static boolean intangible(Player player) {
		VisionState s = peek(player);
		return s != null && s.hasPower && s.intangible;
	}

	/** Phase Dash's intangible quarter second. Client-safe. */
	public static boolean dashing(Player player) {
		VisionState s = peek(player);
		return s != null && s.hasPower && s.dashUntil > now(player);
	}

	/** Nothing touches him: Intangibility or a Phase Dash. Client-safe. */
	public static boolean phased(Player player) {
		return intangible(player) || dashing(player);
	}

	/** He walks through walls sideways (Intangibility only; the dash stops at walls). Client-safe. */
	public static boolean wallPhasing(Player player) {
		return intangible(player);
	}

	/** Shift+C: Diamond Density is on. Client-safe. */
	public static boolean dense(Player player) {
		VisionState s = peek(player);
		return s != null && s.hasPower && s.density;
	}

	public static boolean diving(Player player) {
		VisionState s = peek(player);
		return s != null && s.hasPower && s.diveUntil > now(player);
	}

	public static int cooldownRemaining(Player player, String id) {
		VisionState s = peek(player);
		if (s == null) {
			return 0;
		}
		Long ready = s.abilityReadyAt.get(id);
		return ready == null ? 0 : (int) Math.max(0L, ready - now(player));
	}

	public static void startCooldown(ServerPlayer player, String id, int ticks) {
		VisionState n = state(player).copy();
		n.abilityReadyAt.put(id, now(player) + ticks);
		save(player, n);
	}

	public static void clearCooldowns(ServerPlayer player) {
		VisionState n = state(player).copy();
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

	/** The Mind Stone on his brow, a little in front of the face. */
	public static Vec3 stonePos(Player player) {
		return player.getEyePosition().add(0, 0.18, 0).add(player.getViewVector(1f).scale(0.3));
	}

	// ---------------------------------------------------------------- energy

	/** Spends energy if there is enough ("Not enough Mind Stone energy" otherwise). Creative never pays. */
	public static boolean spend(ServerPlayer player, float amount) {
		VisionState s = state(player);
		if (!s.hasPower) {
			return false;
		}
		if (player.getAbilities().instabuild || amount <= 0f) {
			return true;
		}
		if (s.energy + 1.0e-3f < amount) {
			say(player, "message.projecthero.vision.no_energy", ChatFormatting.GRAY);
			return false;
		}
		VisionState n = s.copy();
		n.energy = Math.max(0f, s.energy - amount);
		save(player, n);
		return true;
	}

	public static boolean hasEnergy(ServerPlayer player, float amount) {
		return player.getAbilities().instabuild || state(player).energy + 1.0e-3f >= amount;
	}

	/** Drains {@code perSecond / 20} in place (re-sent with the bar). False when it ran dry. Creative never pays. */
	static boolean drain(ServerPlayer player, float perSecond) {
		if (player.getAbilities().instabuild) {
			return true;
		}
		VisionState s = state(player);
		s.energy = Math.max(0f, s.energy - perSecond / 20f);
		return s.energy > 0f;
	}

	public static void setEnergy(ServerPlayer player, float value) {
		VisionState n = state(player).copy();
		n.energy = Math.max(0f, Math.min(VisionConfig.ENERGY_MAX, value));
		save(player, n);
	}

	// ---------------------------------------------------------------- the sun

	public enum Sun { DIRECT, SHADE, DARK }

	/** How much sun reaches him where he stands. */
	public static Sun sun(Player player) {
		Level level = player.level();
		if (!level.dimensionType().hasSkyLight() || level.dimensionType().hasCeiling()) {
			return Sun.DARK;
		}
		BlockPos eye = BlockPos.containing(player.getEyePosition());
		boolean sky = level.canSeeSky(eye);
		if (level.isDay() && sky && !level.isRainingAt(eye)) {
			return Sun.DIRECT;
		}
		if (!sky && level.getBrightness(LightLayer.SKY, eye) < 8) {
			return Sun.DARK;
		}
		return Sun.SHADE;
	}

	public static float regenPerSecond(Sun sun) {
		return switch (sun) {
			case DIRECT -> VisionConfig.REGEN_SUN;
			case SHADE -> VisionConfig.REGEN_SHADE;
			case DARK -> VisionConfig.REGEN_DARK;
		};
	}

	/** Something is draining the bar right now (no regeneration while it is). */
	static boolean draining(VisionState s) {
		return s.beam || s.unleash || s.intangible || s.density;
	}

	// ---------------------------------------------------------------- hits

	/** One of his moves hits {@code target} for {@code amount} (his own damage: squad rules and boss threat see him). */
	public static boolean hit(ServerPlayer player, LivingEntity target, float amount) {
		return hit(player, target, amount, player.damageSources().playerAttack(player));
	}

	public static boolean hit(ServerPlayer player, LivingEntity target, float amount, DamageSource source) {
		if (target == null || target == player || !target.isAlive() || !HeroTargets.canHarm(player, target)) {
			return false;
		}
		float before = target.getHealth();
		boolean landed = com.projecthero.mod.hero.power.AbilityHelpers.hurtBurst(player, target, source, amount);
		return landed || target.getHealth() < before;
	}

	// ---------------------------------------------------------------- the power

	/** The Cradle with the Mind Stone, the command. False if he is already Vision (or someone else is, unique powers). */
	public static boolean grant(ServerPlayer player) {
		VisionState old = state(player);
		if (old.hasPower) {
			return false;
		}
		if (com.projecthero.mod.hero.UniquePowers.refuse(player, KEY)) {
			return false;
		}
		HeroTiers.claimPrimary(player, KEY);
		VisionState s = new VisionState();
		s.hasPower = true;
		s.energy = VisionConfig.ENERGY_MAX;
		save(player, s);
		reconcile(player);
		player.setHealth(player.getMaxHealth());
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		sound(level, c, SoundEvents.BEACON_ACTIVATE, 0.5f, 1.2f);
		sound(level, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 0.8f);
		level.sendParticles(dust(GOLD, 1.5f), c.x, c.y, c.z, 50, 0.5, 0.9, 0.5, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 0.6, c.z, 20, 0.3, 0.3, 0.3, 0.05);
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
				Component.translatable("message.projecthero.vision.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
				Component.translatable("message.projecthero.vision.subtitle").withStyle(ChatFormatting.YELLOW)));
		player.displayClientMessage(Component.translatable("message.projecthero.vision.acquired").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.vision.acquired_hint").withStyle(ChatFormatting.GRAY), false);
		return true;
	}

	public static void revoke(ServerPlayer player) {
		boolean wasInWall = intangible(player) && insideWall(player);
		clearTransient(player);
		save(player, new VisionState());
		reconcile(player);
		if (wasInWall) {
			pushOut(player);
		}
		forget(player.getUUID());
	}

	/** Death, a new world, leaving: the flight, the beams, the dash, the grip and Intangibility end (pushed out of a wall first). */
	public static void clearTransient(ServerPlayer player) {
		VisionState s = peek(player);
		if (s == null || !s.hasPower) {
			return;
		}
		boolean inWall = s.intangible && insideWall(player);
		VisionMoves.endAll(player);
		VisionFlight.stop(player);
		VisionState n = state(player).copy();
		n.flying = false;
		n.beam = false;
		n.unleash = false;
		n.lanceStart = 0L;
		n.intangible = false;
		n.dashUntil = 0L;
		n.diveUntil = 0L;
		save(player, n);
		if (inWall && player.isAlive()) {
			pushOut(player);
		}
		VisionMoves.forget(player.getUUID());
		reconcile(player);
	}

	public static void onPlayerJoin(ServerPlayer player) {
		if (hasPower(player)) {
			clearTransient(player);
		}
	}

	/** A death: the bar comes back full and Diamond Density is off. Cooldowns and the hologram stay as they were. */
	public static void onPlayerRespawn(ServerPlayer player) {
		if (!hasPower(player)) {
			return;
		}
		clearTransient(player);
		VisionState n = state(player).copy();
		n.energy = VisionConfig.ENERGY_MAX;
		n.density = false;
		save(player, n);
		reconcile(player);
	}

	// ---------------------------------------------------------------- the toggles

	/** V: the hologram of his own skin goes up / comes down (the shimmer runs on every client from {@code holoChangeAt}). */
	public static boolean toggleHologram(ServerPlayer player) {
		VisionState s = state(player);
		if (!s.hasPower) {
			return false;
		}
		VisionState n = s.copy();
		n.hologram = !s.hologram;
		n.holoChangeAt = now(player);
		save(player, n);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = stonePos(player);
		sound(level, c, n.hologram ? SoundEvents.BEACON_POWER_SELECT : SoundEvents.BEACON_DEACTIVATE, 0.5f, 1.8f);
		sound(level, c, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 1.5f);
		level.sendParticles(dust(GOLD, 0.9f), c.x, c.y, c.z, 10, 0.1, 0.1, 0.1, 0.0);
		return true;
	}

	/** Shift+V: Intangibility on / off. Off is refused inside a wall (he'd be stuck in it). */
	public static boolean toggleIntangible(ServerPlayer player) {
		VisionState s = state(player);
		if (!s.hasPower) {
			return false;
		}
		if (s.intangible) {
			if (insideWall(player)) {
				say(player, "message.projecthero.vision.in_wall", ChatFormatting.GRAY);
				return false;
			}
			setIntangible(player, false);
			return true;
		}
		if (!hasEnergy(player, VisionConfig.PHASE_START_COST)) {
			say(player, "message.projecthero.vision.no_energy", ChatFormatting.GRAY);
			return false;
		}
		if (s.density) {
			setDensity(player, false); // can't be both diamond-hard and a ghost
		}
		VisionMoves.endForPhase(player);
		setIntangible(player, true);
		return true;
	}

	static void setIntangible(ServerPlayer player, boolean on) {
		VisionState n = state(player).copy();
		n.intangible = on;
		save(player, n);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.0, 0);
		sound(level, c, on ? SoundEvents.ILLUSIONER_MIRROR_MOVE : SoundEvents.AMETHYST_BLOCK_PLACE, 0.6f, on ? 1.4f : 0.9f);
		level.sendParticles(dust(GOLD, 1.0f), c.x, c.y, c.z, 24, 0.35, 0.8, 0.35, 0.0);
	}

	/** Shift+C: Diamond Density on / off. On drops him out of the sky. */
	public static boolean toggleDensity(ServerPlayer player) {
		VisionState s = state(player);
		if (!s.hasPower) {
			return false;
		}
		if (!s.density) {
			if (s.intangible) {
				if (insideWall(player)) {
					say(player, "message.projecthero.vision.in_wall", ChatFormatting.GRAY);
					return false;
				}
				setIntangible(player, false);
			}
			if (!hasEnergy(player, 1f)) {
				say(player, "message.projecthero.vision.no_energy", ChatFormatting.GRAY);
				return false;
			}
		}
		setDensity(player, !s.density);
		return true;
	}

	static void setDensity(ServerPlayer player, boolean on) {
		if (on && isFlying(player)) {
			VisionFlight.stop(player);
		}
		VisionState n = state(player).copy();
		n.density = on;
		save(player, n);
		reconcile(player);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position();
		sound(level, c, on ? SoundEvents.ANVIL_LAND : SoundEvents.AMETHYST_BLOCK_BREAK, on ? 0.35f : 0.6f, on ? 0.7f : 1.2f);
		if (on) {
			level.sendParticles(ParticleTypes.CRIT, c.x, c.y + 1.0, c.z, 20, 0.4, 0.7, 0.4, 0.1);
		}
	}

	// ---------------------------------------------------------------- walls

	/** His body overlaps a solid block. */
	public static boolean insideWall(Player player) {
		return !player.level().noCollision(player, player.getBoundingBox().deflate(1.0e-3));
	}

	/** Moves him to the nearest spot where he fits (searching outward); true if he was moved. */
	public static boolean pushOut(ServerPlayer player) {
		if (!insideWall(player)) {
			return false;
		}
		Level level = player.level();
		BlockPos feet = player.blockPosition();
		AABB box = player.getBoundingBox();
		double w = box.getXsize() / 2.0;
		double h = box.getYsize();
		int r = VisionConfig.PHASE_PUSH_RADIUS;
		Vec3 best = null;
		double bestD = Double.MAX_VALUE;
		for (int dy = -1; dy <= r; dy++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					double d = dx * dx + dz * dz + dy * dy * 1.5 + (dy < 0 ? 4 : 0);
					if (d >= bestD) {
						continue;
					}
					Vec3 at = new Vec3(feet.getX() + dx + 0.5, feet.getY() + dy, feet.getZ() + dz + 0.5);
					AABB test = new AABB(at.x - w, at.y, at.z - w, at.x + w, at.y + h, at.z + w);
					if (level.noCollision(player, test) && level.getWorldBorder().isWithinBounds(BlockPos.containing(at))) {
						best = at;
						bestD = d;
					}
				}
			}
		}
		if (best == null) {
			best = new Vec3(player.getX(), level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, feet.getX(), feet.getZ()),
					player.getZ());
		}
		player.teleportTo(best.x, best.y, best.z);
		player.resetFallDistance();
		((ServerLevel) level).sendParticles(dust(GOLD, 1.0f), best.x, best.y + 1.0, best.z, 16, 0.3, 0.6, 0.3, 0.0);
		return true;
	}

	// ---------------------------------------------------------------- stats

	public static void reconcile(ServerPlayer player) {
		VisionState s = peek(player);
		if (s == null || !s.hasPower) {
			PowerToggles.clearModifier(player, Attributes.MAX_HEALTH, HEALTH_ID);
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID);
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
			PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID);
			if (player.getHealth() > player.getMaxHealth()) {
				player.setHealth(player.getMaxHealth());
			}
			return;
		}
		PowerToggles.modifier(player, Attributes.MAX_HEALTH, HEALTH_ID, VisionConfig.HEALTH_BONUS, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, ATTACK_ID,
				VisionConfig.MELEE_BONUS + (s.density ? VisionConfig.DENSITY_MELEE : 0.0), AttributeModifier.Operation.ADD_VALUE);
		if (s.density) {
			PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID, VisionConfig.DENSITY_SLOW, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			PowerToggles.modifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, 1.0, AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
			PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID);
		}
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	// ---------------------------------------------------------------- tick

	public static void tick(ServerPlayer player) {
		VisionState s = player.getAttachedOrElse(VisionAttachments.STATE, null);
		if (s == null || !s.hasPower) {
			return;
		}
		long now = now(player);
		Long prev = LAST_TICK.put(player.getUUID(), now);
		if (prev != null && prev == now) {
			return;
		}
		tickBar(player, s);
		tickBody(player);
		if (player.tickCount % 20 == 0) {
			reconcile(player);
		}
		tickToggles(player);
		VisionFlight.tick(player);
		VisionMoves.tick(player);
		VisionAbilityManager.tick(player);
		if (now % VisionConfig.SYNC_INTERVAL == 0) {
			save(player, state(player).copy());
		}
	}

	/** The bar: refilled by the Mind Stone (faster in the sun) unless something is draining it (in place; re-sent every few ticks). */
	public static void tickBar(ServerPlayer player, VisionState s) {
		if (draining(s)) {
			return;
		}
		s.energy = Math.min(VisionConfig.ENERGY_MAX, s.energy + regenPerSecond(sun(player)) / 20f);
	}

	/** The synthezoid body: no hunger, no drowning, no poison or wither. */
	public static void tickBody(ServerPlayer player) {
		var food = player.getFoodData();
		if (food.getFoodLevel() != VisionConfig.FOOD_LEVEL) {
			food.setFoodLevel(VisionConfig.FOOD_LEVEL);
		}
		if (food.getSaturationLevel() > 0f) {
			food.setSaturation(0f);
		}
		food.setExhaustion(0f);
		if (player.getAirSupply() < player.getMaxAirSupply()) {
			player.setAirSupply(player.getMaxAirSupply());
		}
		if (player.hasEffect(MobEffects.POISON)) {
			player.removeEffect(MobEffects.POISON);
		}
		if (player.hasEffect(MobEffects.WITHER)) {
			player.removeEffect(MobEffects.WITHER);
		}
		player.resetFallDistance();
	}

	/** Intangibility and Diamond Density drain the bar; an empty bar ends them (pushed out of a wall if he's in one). */
	public static void tickToggles(ServerPlayer player) {
		VisionState s = state(player);
		if (s.intangible) {
			if (!drain(player, VisionConfig.PHASE_DRAIN_PER_SECOND)) {
				boolean inWall = insideWall(player);
				setIntangible(player, false);
				if (inWall) {
					pushOut(player);
				}
				say(player, "message.projecthero.vision.phase_out", ChatFormatting.GRAY);
			} else if (player.tickCount % 3 == 0) {
				ServerLevel level = (ServerLevel) player.level();
				Vec3 c = player.position().add(0, 1.0, 0);
				level.sendParticles(dust(GOLD, 0.7f), c.x, c.y, c.z, 2, 0.3, 0.7, 0.3, 0.0);
			}
		}
		s = state(player);
		if (s.density) {
			if (!drain(player, VisionConfig.DENSITY_DRAIN_PER_SECOND)) {
				setDensity(player, false);
				say(player, "message.projecthero.vision.density_out", ChatFormatting.GRAY);
			} else if (player.onGround() && player.getDeltaMovement().horizontalDistanceSqr() > 0.003 && player.tickCount % 4 == 0) {
				ServerLevel level = (ServerLevel) player.level();
				level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 0.1, player.getZ(), 3, 0.25, 0.05, 0.25, 0.02);
			}
		}
	}
}
