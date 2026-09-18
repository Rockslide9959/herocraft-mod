package com.projecthero.mod.greenlantern.construct;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.GreenLanternEnergy;
import com.projecthero.mod.greenlantern.GreenLanternOath;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.PowerToggles;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * The reusable hard-light construct system: owner-tracked, TTL-restoring, capacity-enforced. Every
 * one of the twelve named constructs is a {@link Construct} instance dispatched by
 * {@link ConstructType.Kind} rather than its own class -- most are backed by real temporary blocks
 * (cloned from {@code ConjuredStructures}' TTL/restore shape), a few (turret, drill, energy blade,
 * atmosphere bubble, the Hard-Light Tool Kit) are "marker" constructs with no blocks, ticked purely in
 * Java, and two (Battering Ram, Rescue Tether) are instant one-shots that never enter {@link #BY_OWNER}
 * at all.
 *
 * <p>Global rules enforced here: no cap on the number of active constructs any more (v0.11.7 removed it
 * outright; {@link ConstructType#slotWeight()} is still tracked/reported but nothing compares it against
 * a ceiling -- Sentry Turret alone keeps its own explicit {@link GreenLanternConfig#TURRET_MAX_LIVE}),
 * 24-block placement range (several types override this with their own, longer range), never overwrites
 * bedrock/portals/containers/unbreakable blocks, never drops items, vanishes on owner
 * death/dimension-change/logout/hard depletion (see {@code clearFor}), and a turret/cage never
 * targets/affects the owner, a squadmate, or a tamed mob.
 */
public final class GreenLanternConstructs {
	private static final Map<UUID, List<Construct>> BY_OWNER = new ConcurrentHashMap<>();
	private static final net.minecraft.resources.ResourceLocation BLADE_DAMAGE =
			com.projecthero.mod.ProjectHeroMod.id("green_lantern_energy_blade");
	private static final net.minecraft.resources.ResourceLocation BLADE_REACH =
			com.projecthero.mod.ProjectHeroMod.id("green_lantern_energy_blade_reach");
	/** Lantern-Corps green, matching every other hard-light effect's dust colour in this power. */
	private static final ParticleOptions GREEN_DUST = new DustParticleOptions(new Vector3f(0.208f, 0.941f, 0.459f), 1.5f);

	private GreenLanternConstructs() {
	}

	public static void initialize() {
		// Punching one of a construct's own blocks either chips away at its HP (Wall/Cage; the whole
		// construct collapses at 0) or, for the HP-less kinds (Platform/Bridge/Stair-Ramp/Lantern
		// Light), dismisses it outright on the first punch -- either way the vanilla break path never
		// runs, so a construct block is never actually mined and never drops anything. Mirrors
		// ConjuredStructures' dismiss-by-punch pattern.
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (!(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			for (List<Construct> list : BY_OWNER.values()) {
				for (Construct c : list) {
					for (Construct.Cell cell : c.cells) {
						if (cell.pos().equals(pos)) {
							if (c.type.maxHp() > 0f) {
								damage(c, 20f, sp);
							} else {
								dismissOne(c, true);
							}
							return InteractionResult.SUCCESS;
						}
					}
				}
			}
			return InteractionResult.PASS;
		});

		// A block-based construct is never meant to be minable through the normal survival path either
		// (a player standing under a Platform and breaking it from below, a pickaxe on a Bridge, etc.) --
		// treat any break attempt on a tracked cell as a punch-dismiss instead of letting it break/drop.
		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
			if (!(player instanceof ServerPlayer sp)) {
				return true;
			}
			for (List<Construct> list : BY_OWNER.values()) {
				for (Construct c : list) {
					for (Construct.Cell cell : c.cells) {
						if (cell.pos().equals(pos)) {
							if (c.type.maxHp() > 0f) {
								damage(c, 20f, sp);
							} else {
								dismissOne(c, true);
							}
							return false;
						}
					}
				}
			}
			return true;
		});

		// v0.11.5: right-clicking a Carry Platform cell seats the clicking player on it (an invisible
		// armour-stand "seat" riding along whenever the platform moves), so the owner can ferry more than
		// just themselves. Anyone may sit, not just the owner or squadmates.
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			for (List<Construct> list : BY_OWNER.values()) {
				for (Construct c : list) {
					if (c.type != ConstructType.CARRY_PLATFORM) {
						continue;
					}
					for (int i = 0; i < c.cells.size(); i++) {
						if (c.cells.get(i).pos().equals(pos)) {
							trySeat(sp, c, i);
							return InteractionResult.SUCCESS;
						}
					}
				}
			}
			return InteractionResult.PASS;
		});
	}

	public static void clearSessionState() {
		BY_OWNER.clear();
		RESCUE_HELD.clear();
	}

	public static List<Construct> of(UUID owner) {
		return BY_OWNER.getOrDefault(owner, List.of());
	}

	public static int activeWeight(UUID owner) {
		int w = 0;
		for (Construct c : of(owner)) {
			w += c.type.slotWeight();
		}
		return w;
	}

	private static Construct firstOfType(UUID owner, ConstructType type) {
		for (Construct c : of(owner)) {
			if (c.type == type) {
				return c;
			}
		}
		return null;
	}

	private static int countOfType(UUID owner, ConstructType type) {
		int n = 0;
		for (Construct c : of(owner)) {
			if (c.type == type) {
				n++;
			}
		}
		return n;
	}

	/**
	 * A second C-press on an already-active {@link #TOGGLE_TYPES} instance flips it on/off, rather than
	 * deploying another one (v0.11.7, explicit user request for Mining Drill/Energy Blade/Carry Platform
	 * -- "if player presses c with this construct active make them toggle the drive on, if they press c
	 * while its on make them toggle it off"). Turning ON applies whatever effect that type actually has
	 * (Energy Blade's melee buff; Mining Drill/Carry Platform have none to apply here, their own tick
	 * functions simply gate on {@link Construct#toggledOn}); turning off reverses it. Upkeep for these
	 * three only drains while toggled on (see {@link #tickUpkeep}), except Carry Platform, whose upkeep
	 * is unconditional (it is still occupying world blocks either way).
	 */
	private static void toggleConstruct(ServerPlayer player, Construct c) {
		c.toggledOn = !c.toggledOn;
		if (c.type == ConstructType.ENERGY_BLADE) {
			if (c.toggledOn) {
				PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, BLADE_DAMAGE,
						GreenLanternConfig.ENERGY_BLADE_DAMAGE - 1f, AttributeModifier.Operation.ADD_VALUE);
				PowerToggles.modifier(player, Attributes.ENTITY_INTERACTION_RANGE, BLADE_REACH,
						GreenLanternConfig.ENERGY_BLADE_REACH - 3.0, AttributeModifier.Operation.ADD_VALUE);
			} else {
				removeMeleeBuff(c);
			}
		}
		player.serverLevel().sendParticles(GREEN_DUST, player.getX(), player.getY() + 1.0, player.getZ(),
				c.toggledOn ? 20 : 8, 0.3, 0.5, 0.3, 0.03);
		player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
				c.toggledOn ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS,
				0.5f, c.toggledOn ? 1.5f : 1.0f);
	}

	// ---------------- deploy ----------------

	/** MINING_DRILL, ENERGY_BLADE and CARRY_PLATFORM (v0.11.7): a second deploy of the same type toggles
	 *  the caster's own already-active instance instead of trying (and refusing) to place another one. */
	private static final java.util.Set<ConstructType> TOGGLE_TYPES =
			java.util.EnumSet.of(ConstructType.MINING_DRILL, ConstructType.ENERGY_BLADE, ConstructType.CARRY_PLATFORM);

	public static void deploy(ServerPlayer player, ConstructType type) {
		if (type == ConstructType.BATTERING_RAM) {
			batteringRam(player);
			return;
		}
		if (type == ConstructType.RESCUE_TETHER) {
			rescueGrab(player);
			return;
		}
		if (TOGGLE_TYPES.contains(type)) {
			Construct existing = firstOfType(player.getUUID(), type);
			if (existing != null) {
				toggleConstruct(player, existing);
				return;
			}
		}
		// Only one Tool Kit at a time -- countToolKitPieces()/tickKind's TOOL_KIT case count pieces
		// per-PLAYER, not per-construct-instance, so two live kits sharing one pool of tagged tools
		// would let a player drop pieces from one kit without either one ever ending.
		if (type == ConstructType.HARD_LIGHT_TOOLS && hasLiveToolKit(player.getUUID())) {
			GreenLanternEnergy.feedback(player, "message.projecthero.green_lantern.tool_kit_already_active");
			return;
		}
		// v0.11.7: no generic construct-slot cap any more (explicit user request, "remove construct
		// limit") -- Sentry Turret alone keeps its own separate, explicitly-requested hard cap.
		if (type == ConstructType.SENTRY_TURRET && countOfType(player.getUUID(), ConstructType.SENTRY_TURRET)
				>= GreenLanternConfig.TURRET_MAX_LIVE) {
			GreenLanternEnergy.feedback(player, "message.projecthero.green_lantern.turret_limit");
			return;
		}
		String cooldownId = cooldownIdFor(type);
		if (cooldownId != null && !GreenLantern.abilityReady(player, cooldownId)) {
			feedbackCooldown(player, cooldownId);
			return;
		}
		float cost = totalCost(type, player);
		if (!GreenLanternEnergy.spend(player, cost)) {
			GreenLanternEnergy.feedback(player, "message.projecthero.ability.low_charge");
			return;
		}
		com.projecthero.mod.greenlantern.GreenLanternBattery.onAbilityUsed(player);

		ServerLevel level = player.serverLevel();
		Vec3 anchor = type == ConstructType.CARRY_PLATFORM ? carryPlatformAnchor(player)
				: type == ConstructType.PLATFORM ? placementPoint(player, GreenLanternConfig.PLATFORM_RANGE)
				: placementPoint(player);
		Construct c = new Construct(player.getUUID(), type, level, anchor, level.getGameTime());
		switch (type.kind()) {
			case MELEE_BUFF -> spawnEnergyBlade(player, c);
			case WALL -> spawnWall(player, c);
			case PLATFORM_BLOCKS -> spawnPlatform(player, c, type == ConstructType.CARRY_PLATFORM);
			case BRIDGE_BLOCKS -> spawnBridge(player, c);
			case RAMP_BLOCKS -> spawnRamp(player, c);
			case LIGHT_BLOCKS -> spawnLight(player, c);
			case CAGE -> spawnCage(player, c);
			case TURRET -> {} // anchor + timer only, ticked below
			case BUBBLE -> {} // marker only
			case DRILL -> {} // marker only
			case TOOL_KIT -> spawnToolKit(player, c);
			default -> {}
		}
		boolean usesBlocks = type.kind() == ConstructType.Kind.WALL || type.kind() == ConstructType.Kind.PLATFORM_BLOCKS
				|| type.kind() == ConstructType.Kind.BRIDGE_BLOCKS || type.kind() == ConstructType.Kind.RAMP_BLOCKS
				|| type.kind() == ConstructType.Kind.LIGHT_BLOCKS;
		boolean placementFailed = (usesBlocks && c.cells.isEmpty())
				|| (type.kind() == ConstructType.Kind.CAGE && c.cagedEntityId < 0)
				|| (type.kind() == ConstructType.Kind.TOOL_KIT && !c.toolKitGranted);
		if (placementFailed) {
			// placement failed entirely (e.g. every target cell was protected/occupied, or no target found)
			GreenLanternEnergy.refund(player, cost);
			GreenLanternEnergy.feedback(player, type.kind() == ConstructType.Kind.TOOL_KIT
					? "message.projecthero.green_lantern.tool_kit_no_room" : "message.projecthero.ability.invalid_target");
			return;
		}
		BY_OWNER.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(c);
		emitDeployGlow(player);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_AMBIENT,
				SoundSource.PLAYERS, 0.5f, 1.4f);
	}

	/**
	 * A burst of green hard-light particles at the caster's hand -- v0.11.5, "at the very least ... make
	 * the players hand glow green particles so the player knows that something is happening", added for
	 * every construct kind, including the marker-only ones (turret/bubble/drill/energy blade/tool kit)
	 * that place no blocks of their own to look at.
	 */
	private static void emitDeployGlow(ServerPlayer player) {
		Vec3 hand = player.getEyePosition().add(player.getLookAngle().scale(0.6)).add(0, -0.3, 0);
		player.serverLevel().sendParticles(GREEN_DUST, hand.x, hand.y, hand.z, 12, 0.15, 0.15, 0.15, 0.02);
	}

	/** Carry Platform's spawn point: 5 blocks ahead of the owner, at their own foot height (walkable). */
	private static Vec3 carryPlatformAnchor(ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		Vec3 horizontal = new Vec3(look.x, 0, look.z);
		if (horizontal.lengthSqr() < 1.0e-4) {
			double yawRad = Math.toRadians(player.getYRot());
			horizontal = new Vec3(-Math.sin(yawRad), 0, Math.cos(yawRad));
		}
		return player.position().add(horizontal.normalize().scale(5.0));
	}

	/** The three constructs with an explicit post-collapse cooldown in the build brief; null for the rest. */
	private static String cooldownIdFor(ConstructType type) {
		return switch (type) {
			case CONTAINMENT_CAGE -> "construct_cage";
			case SENTRY_TURRET -> "construct_turret";
			case HARD_LIGHT_WALL -> "construct_wall";
			default -> null;
		};
	}

	/**
	 * The cooldown remaining for {@code type} right now, or 0 if it has none or isn't on cooldown --
	 * used by {@code GreenLanternHud}'s C-slot box, since C can deploy whichever construct is selected.
	 */
	public static int cooldownRemainingFor(net.minecraft.world.entity.player.Player player, ConstructType type) {
		String id = switch (type) {
			case BATTERING_RAM -> "battering_ram";
			case RESCUE_TETHER -> "rescue_tether";
			default -> cooldownIdFor(type);
		};
		return id == null ? 0 : GreenLantern.cooldownRemaining(player, id);
	}

	private static int cooldownTicksFor(ConstructType type) {
		return switch (type) {
			case CONTAINMENT_CAGE -> GreenLanternConfig.CAGE_COOLDOWN_TICKS;
			case SENTRY_TURRET -> GreenLanternConfig.TURRET_COOLDOWN_TICKS;
			case HARD_LIGHT_WALL -> GreenLanternConfig.WALL_COOLDOWN_TICKS;
			default -> 0;
		};
	}

	private static void applyEndCooldown(ServerPlayer owner, Construct c) {
		String id = cooldownIdFor(c.type);
		if (id != null) {
			GreenLantern.triggerCooldown(owner, id, cooldownTicksFor(c.type));
		}
	}

	/**
	 * v0.11.5: "it just says cooldown, show the cooldowns" -- names the actual seconds remaining instead
	 * of the bare generic {@code message.projecthero.ability.cooldown_simple} text.
	 */
	private static void feedbackCooldown(ServerPlayer player, String cooldownId) {
		int seconds = (int) Math.ceil(GreenLantern.cooldownRemaining(player, cooldownId) / 20.0);
		player.displayClientMessage(Component.translatable(
				"message.projecthero.green_lantern.construct_cooldown", seconds), true);
	}

	/** v0.11.7: Bridge and Stair/Ramp are now flat, fixed-dimension costs -- no more per-segment scaling. */
	private static float totalCost(ConstructType type, ServerPlayer player) {
		return type.initialCost();
	}

	private static Vec3 placementPoint(ServerPlayer player) {
		return placementPoint(player, GreenLanternConfig.CONSTRUCT_PLACE_RANGE);
	}

	private static Vec3 placementPoint(ServerPlayer player, double range) {
		BlockHitResult hit = AbilityHelpers.raycastBlock(player, range);
		if (hit.getType() != HitResult.Type.MISS) {
			return hit.getLocation();
		}
		return player.getEyePosition().add(player.getLookAngle().scale(range));
	}

	// ---------------- per-kind spawn ----------------

	/**
	 * v0.11.7: deploying just equips the stance, free and with no effect yet -- a second C press
	 * ({@link #toggleConstruct}) is what actually turns the blade (and its cost) on.
	 */
	private static void spawnEnergyBlade(ServerPlayer player, Construct c) {
	}

	private static void spawnWall(ServerPlayer player, Construct c) {
		Direction facing = player.getDirection();
		Direction side = facing.getClockWise();
		BlockPos center = BlockPos.containing(c.anchor);
		for (int w = -2; w <= 2; w++) {
			for (int h = 0; h < GreenLanternConfig.WALL_HEIGHT; h++) {
				add(c, center.relative(side, w).above(h), lightBlockState());
			}
		}
		place(c);
	}

	private static void spawnPlatform(ServerPlayer player, Construct c, boolean carry) {
		if (carry) {
			// v0.11.7: spawns level with the owner's own Y (was one block below -- explicit user request),
			// a fixed 3x3 square independent of facing so it can be recomputed around a moving center
			// every tick without re-deriving a facing/side pair.
			BlockPos center = BlockPos.containing(c.anchor);
			for (BlockPos pos : carryPlatformCells(center)) {
				add(c, pos, carryPlatformBlockState());
			}
			place(c);
			// platformCenter tracks the same frame as c.anchor/carryPlatformAnchor() -- tickCarryPlatform()
			// re-derives the block-grid center from it with the same (now none) offset, so the two never
			// drift apart.
			c.platformCenter = c.anchor;
			c.platformBlockCenter = center;
			spawnCarryPlatformSeats(c);
			return;
		}
		// v0.11.7: a 4x4 footprint (up from 3x3) placed wherever the 30-block-range placementPoint()
		// landed -- on the ground if the caster's aim hit solid terrain within range, otherwise floating
		// out in front of them, exactly the existing placementPoint() fallback behaviour.
		BlockPos center = BlockPos.containing(c.anchor).below();
		Direction facing = player.getDirection();
		Direction side = facing.getClockWise();
		int size = GreenLanternConfig.PLATFORM_SIZE;
		for (int f = -1; f < size - 1; f++) {
			for (int w = -1; w < size - 1; w++) {
				add(c, center.relative(facing, f).relative(side, w), lightBlockState());
			}
		}
		place(c);
	}

	/** The nine world positions of a 3x3 footprint centred on {@code center}, in a fixed, stable order. */
	private static List<BlockPos> carryPlatformCells(BlockPos center) {
		List<BlockPos> cells = new ArrayList<>(9);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				cells.add(center.offset(dx, 0, dz));
			}
		}
		return cells;
	}

	// ---------------- Carry Platform (v0.11.5: follows its owner and can carry passengers) ----------------

	/** One invisible, unkillable seat per cell -- a rider on one follows it automatically as it moves. */
	private static void spawnCarryPlatformSeats(Construct c) {
		for (Construct.Cell cell : c.cells) {
			ArmorStand stand = new ArmorStand(net.minecraft.world.entity.EntityType.ARMOR_STAND, c.level);
			stand.setPos(cell.pos().getX() + 0.5, cell.pos().getY() + 1.0, cell.pos().getZ() + 0.5);
			stand.setInvisible(true);
			stand.setNoGravity(true);
			stand.setInvulnerable(true);
			stand.setSilent(true);
			stand.setNoBasePlate(true);
			stand.setShowArms(false);
			c.level.addFreshEntity(stand);
			c.seatEntityIds.add(stand.getId());
		}
	}

	private static void removeCarrySeats(Construct c) {
		for (int id : c.seatEntityIds) {
			net.minecraft.world.entity.Entity seat = c.level.getEntity(id);
			if (seat != null) {
				for (net.minecraft.world.entity.Entity rider : List.copyOf(seat.getPassengers())) {
					rider.stopRiding();
				}
				seat.discard();
			}
		}
		c.seatEntityIds.clear();
	}

	/** Right-click on an unoccupied Carry Platform cell seats the clicking player on it. */
	private static void trySeat(ServerPlayer player, Construct c, int cellIndex) {
		if (cellIndex >= c.seatEntityIds.size() || player.isPassenger()) {
			return;
		}
		net.minecraft.world.entity.Entity seat = c.level.getEntity(c.seatEntityIds.get(cellIndex));
		if (!(seat instanceof ArmorStand stand) || !stand.isAlive()) {
			return;
		}
		if (!stand.getPassengers().isEmpty()) {
			GreenLanternEnergy.feedback(player, "message.projecthero.green_lantern.seat_occupied");
			return;
		}
		player.startRiding(stand, true);
	}

	/**
	 * Steers the platform toward a point {@link GreenLanternConfig#CARRY_PLATFORM_SPEED_CAP_BPS} blocks
	 * away 5 blocks in front of the owner's live look direction ("follows their mouse"), at the owner's
	 * own height. {@link Construct#platformCenter} tracks the continuous position; the actual blocks
	 * (and their riders, via the seat entities) only move when that crosses into a new block, so a slow
	 * drift doesn't churn the world every tick. v0.11.7: only actually steers while
	 * {@link Construct#toggledOn} ("drive") is on -- a second C press on the already-active platform
	 * toggles it (see {@link #toggleConstruct}); while off, the platform simply holds its last position
	 * (still fully usable as a stationary platform, just not chasing the owner).
	 */
	private static void tickCarryPlatform(ServerPlayer owner, Construct c) {
		if (!c.toggledOn) {
			return;
		}
		Vec3 target = carryPlatformAnchor(owner);
		Vec3 current = c.platformCenter;
		Vec3 toTarget = target.subtract(current);
		double dist = toTarget.length();
		double maxStep = GreenLanternConfig.CARRY_PLATFORM_SPEED_CAP_BPS / 20.0;
		c.platformCenter = dist <= maxStep || dist < 1.0e-4 ? target : current.add(toTarget.normalize().scale(maxStep));

		// v0.11.7: same Y-cell as the owner's own frame, no ".below()" offset any more.
		BlockPos newCenter = BlockPos.containing(c.platformCenter);
		if (newCenter.equals(c.platformBlockCenter)) {
			return;
		}
		BlockPos oldCenter = c.platformBlockCenter;
		restore(c);
		c.cells.clear();
		for (BlockPos pos : carryPlatformCells(newCenter)) {
			add(c, pos, carryPlatformBlockState());
		}
		place(c);
		c.platformBlockCenter = newCenter;

		int dx = newCenter.getX() - oldCenter.getX();
		int dy = newCenter.getY() - oldCenter.getY();
		int dz = newCenter.getZ() - oldCenter.getZ();
		for (int id : c.seatEntityIds) {
			net.minecraft.world.entity.Entity seat = c.level.getEntity(id);
			if (seat != null) {
				seat.teleportTo(seat.getX() + dx, seat.getY() + dy, seat.getZ() + dz);
			}
		}
	}

	/** v0.11.7: fixed 20-long, 3-wide -- explicit user request (replaces the old aim-to-a-target length). */
	private static void spawnBridge(ServerPlayer player, Construct c) {
		Direction facing = player.getDirection();
		Direction side = facing.getClockWise();
		BlockPos start = player.blockPosition().below().relative(facing);
		int halfWidth = GreenLanternConfig.BRIDGE_WIDTH / 2;
		for (int f = 0; f < GreenLanternConfig.BRIDGE_LENGTH; f++) {
			for (int w = -halfWidth; w <= halfWidth; w++) {
				add(c, start.relative(facing, f).relative(side, w), lightBlockState());
			}
		}
		place(c);
	}

	/** v0.11.7: 3 blocks wide -- explicit user request. */
	private static void spawnRamp(ServerPlayer player, Construct c) {
		Direction facing = player.getDirection();
		Direction side = facing.getClockWise();
		BlockPos start = player.blockPosition().below().relative(facing);
		int halfWidth = GreenLanternConfig.RAMP_WIDTH / 2;
		for (int f = 0; f < GreenLanternConfig.RAMP_MAX_SEGMENTS; f++) {
			for (int w = -halfWidth; w <= halfWidth; w++) {
				add(c, start.relative(facing, f).relative(side, w).above(f), lightBlockState());
			}
		}
		place(c);
	}

	private static void spawnLight(ServerPlayer player, Construct c) {
		add(c, BlockPos.containing(c.anchor).above(), Blocks.SEA_LANTERN.defaultBlockState());
		place(c);
	}

	/**
	 * v0.11.7: fits the cage to the target instead of always using the same 3x3 footprint -- explicit
	 * user request ("make it fit the form of any mob it encases, make it a 2x2 for spiders and cows and
	 * sheep and stuff"). Anything spider/cow/sheep-sized or smaller (bounding-box width <= 1.5, which
	 * covers essentially every overworld mob) gets a tight 2x2 footprint of solid walls at body height;
	 * anything bigger keeps the original roomier 3x3 perimeter-with-open-corners shape. Either way, a
	 * flying target additionally gets its top and bottom fully sealed rather than left with the small
	 * open gap a ground-bound mob doesn't need closed ("if player tries to use it on a flying mob make
	 * it cover them completely so that they are trapped in place").
	 */
	private static void spawnCage(ServerPlayer player, Construct c) {
		LivingEntity target = AbilityHelpers.raycastEntity(player, GreenLanternConfig.CAGE_RANGE);
		if (target == null || !isHostileTarget(player, target)) {
			return;
		}
		c.cagedEntityId = target.getId();
		boolean flying = isFlyingMob(target);
		BlockPos center = target.blockPosition();
		if (target.getBbWidth() <= 1.5) {
			// A 2-wide footprint has no true "corner to leave open" the way a 3-wide one does -- every
			// cell in it IS the perimeter -- so it's a full solid ring at body height, sealed top/bottom
			// only for a flying target.
			int xLo = target.getX() - Math.floor(target.getX()) < 0.5 ? -1 : 0;
			int zLo = target.getZ() - Math.floor(target.getZ()) < 0.5 ? -1 : 0;
			for (int x = xLo; x <= xLo + 1; x++) {
				for (int z = zLo; z <= zLo + 1; z++) {
					add(c, center.offset(x, 1, z), lightBlockState());
					if (flying) {
						add(c, center.offset(x, 0, z), lightBlockState());
						add(c, center.offset(x, 2, z), lightBlockState());
					}
				}
			}
		} else {
			for (int x = -1; x <= 1; x++) {
				for (int y = 0; y <= 2; y++) {
					for (int z = -1; z <= 1; z++) {
						boolean edge = Math.abs(x) == 1 || Math.abs(z) == 1 || y == 0 || y == 2;
						boolean corner = Math.abs(x) == 1 && Math.abs(z) == 1;
						boolean capCenter = (y == 0 || y == 2) && x == 0 && z == 0;
						if (edge && !corner && (!capCenter || flying)) {
							add(c, center.offset(x, y, z), lightBlockState());
						}
					}
				}
			}
		}
		place(c);
	}

	/** Vanilla mobs that fly or otherwise ignore gravity -- Containment Cage seals these in completely. */
	private static boolean isFlyingMob(LivingEntity e) {
		return e.isNoGravity()
				|| e instanceof net.minecraft.world.entity.monster.Vex
				|| e instanceof net.minecraft.world.entity.monster.Ghast
				|| e instanceof net.minecraft.world.entity.monster.Phantom
				|| e instanceof net.minecraft.world.entity.ambient.Bat
				|| e instanceof net.minecraft.world.entity.animal.Parrot
				|| e instanceof net.minecraft.world.entity.animal.Bee;
	}

	// ---------------- Rescue Tether (v0.11.6: a proper grab -- hold, throw, or set down) ----------------

	/** Player UUID -> the entity id they're currently carrying via Rescue Tether (absent = not holding). */
	private static final Map<UUID, Integer> RESCUE_HELD = new ConcurrentHashMap<>();

	/**
	 * Rescue Tether reworked (v0.11.6, explicit user request: "make it a grab move, players press c and
	 * pick up the target, they can press c again to throw the target or shift+C to let them down
	 * safely") from a single instant yank into an actual hold. The first C press grabs whatever's under
	 * the crosshair (ally or foe; a "rescue" doesn't discriminate, though {@link AbilityHelpers}'
	 * general-purpose grab-target filter still excludes armour stands and boss-tier health) and keeps it
	 * held in front of the caster every tick ({@link #tickRescueHeld}); a second C press throws it
	 * ({@link #throwRescueHeld}); Shift+C sets it down safely instead of the usual dismiss-all
	 * ({@code GreenLanternAbilityManager#handleAbilitySix}). Never enters {@link #BY_OWNER} -- the hold
	 * is tracked in {@link #RESCUE_HELD} instead, exactly as the one-shot {@link #batteringRam} never did.
	 */
	private static void rescueGrab(ServerPlayer player) {
		if (RESCUE_HELD.containsKey(player.getUUID())) {
			throwRescueHeld(player);
			return;
		}
		if (!GreenLantern.abilityReady(player, "rescue_tether")) {
			feedbackCooldown(player, "rescue_tether");
			return;
		}
		LivingEntity target = AbilityHelpers.raycastEntity(player, GreenLanternConfig.TETHER_RANGE);
		if (target == null || !AbilityHelpers.isValidGrabTarget(target, player)) {
			GreenLanternEnergy.feedback(player, "message.projecthero.ability.invalid_target");
			return;
		}
		if (!GreenLanternEnergy.spend(player, GreenLanternConfig.TETHER_COST)) {
			GreenLanternEnergy.feedback(player, "message.projecthero.ability.low_charge");
			return;
		}
		GreenLantern.triggerCooldown(player, "rescue_tether", GreenLanternConfig.TETHER_COOLDOWN_TICKS);
		com.projecthero.mod.greenlantern.GreenLanternBattery.onAbilityUsed(player);

		ServerLevel level = player.serverLevel();
		Vec3 hand = player.getEyePosition().add(player.getLookAngle().scale(0.6)).add(0, -0.3, 0);
		AbilityHelpers.line(level, hand, target.position().add(0, target.getBbHeight() * 0.5, 0), GREEN_DUST, 3.0);

		RESCUE_HELD.put(player.getUUID(), target.getId());
		target.setDeltaMovement(Vec3.ZERO);
		target.fallDistance = 0f;
		AbilityHelpers.sound(player, SoundEvents.TRIDENT_RETURN, 0.8f, 1.1f);
	}

	/**
	 * Per-tick while a Rescue Tether hold is active ({@code GreenLanternAbilityManager#serverTick}) --
	 * glues the held target {@link GreenLanternConfig#TETHER_HOLD_DISTANCE} blocks in front of the
	 * caster's eyes every tick, mirroring {@code GrabHelper#tick}'s reposition-every-tick approach (the
	 * generic hero-power grab helper), re-implemented here since Green Lantern keeps its own state
	 * rather than going through the experimental-power {@code AbilityContext} resource system. v0.11.7:
	 * also drains {@link GreenLanternConfig#TETHER_UPKEEP_PER_SEC} to maintain the hold -- an unpayable
	 * upkeep releases the target the same safe way Shift+C does, rather than dropping them outright.
	 */
	public static void tickRescueHeld(ServerPlayer player) {
		Integer id = RESCUE_HELD.get(player.getUUID());
		if (id == null) {
			return;
		}
		net.minecraft.world.entity.Entity e = player.level().getEntity(id);
		if (!(e instanceof LivingEntity target) || !target.isAlive()
				|| player.distanceToSqr(target) > GreenLanternConfig.TETHER_MAX_HOLD_RANGE_SQR) {
			RESCUE_HELD.remove(player.getUUID());
			return;
		}
		if (player.tickCount % 20 == 0
				&& !GreenLanternEnergy.drainTick(player, GreenLanternConfig.TETHER_UPKEEP_PER_SEC)) {
			releaseRescueHeldSafely(player);
			return;
		}
		Vec3 hold = player.getEyePosition().add(player.getLookAngle().scale(GreenLanternConfig.TETHER_HOLD_DISTANCE));
		target.setPos(hold.x, hold.y - target.getBbHeight() / 2, hold.z);
		target.setDeltaMovement(Vec3.ZERO);
		target.fallDistance = 0f;
		target.hurtMarked = true;
	}

	/** Second C press while holding: launches the held target in the caster's look direction. */
	private static void throwRescueHeld(ServerPlayer player) {
		Integer id = RESCUE_HELD.remove(player.getUUID());
		if (id == null) {
			return;
		}
		net.minecraft.world.entity.Entity e = player.level().getEntity(id);
		if (e instanceof LivingEntity target && target.isAlive()) {
			target.setDeltaMovement(player.getLookAngle().scale(GreenLanternConfig.TETHER_THROW_SPEED).add(0, 0.3, 0));
			target.hurtMarked = true;
			target.hasImpulse = true;
		}
		AbilityHelpers.sound(player, SoundEvents.TRIDENT_THROW, 0.8f, 1.2f);
	}

	/**
	 * Shift+C while holding: sets the target down where it is, no damage and no launch -- a brief Slow
	 * Falling if it's still airborne so "safely" actually holds even off a ledge. Checked by
	 * {@code GreenLanternAbilityManager#handleAbilitySix} before the ordinary dismiss-all, so Shift+C
	 * releases a held rescue target instead of trying (and having nothing) to dismiss.
	 *
	 * @return true if something was actually being held and released.
	 */
	public static boolean releaseRescueHeldSafely(ServerPlayer player) {
		Integer id = RESCUE_HELD.remove(player.getUUID());
		if (id == null) {
			return false;
		}
		net.minecraft.world.entity.Entity e = player.level().getEntity(id);
		if (e instanceof LivingEntity target && target.isAlive()) {
			target.setDeltaMovement(0, -0.05, 0);
			target.fallDistance = 0f;
			target.hurtMarked = true;
			if (!target.onGround()) {
				target.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false, false));
			}
		}
		return true;
	}

	// ---------------- Hard-Light Tool Kit ----------------

	/** NBT marker key identifying a hard-light tool piece -- survives an anvil rename, unlike a name match. */
	private static final String TOOL_KIT_TAG = "projecthero_gl_tool_kit";
	private static final String[] TOOL_KIT_NAME_KEYS = {
			"message.projecthero.green_lantern.tool_kit.pickaxe",
			"message.projecthero.green_lantern.tool_kit.axe",
			"message.projecthero.green_lantern.tool_kit.shovel",
			"message.projecthero.green_lantern.tool_kit.flint_and_steel",
	};
	// v0.11.7: a flint and steel added to the kit -- explicit user request.
	private static final net.minecraft.world.item.Item[] TOOL_KIT_BASES = {
			net.minecraft.world.item.Items.DIAMOND_PICKAXE,
			net.minecraft.world.item.Items.DIAMOND_AXE,
			net.minecraft.world.item.Items.DIAMOND_SHOVEL,
			net.minecraft.world.item.Items.FLINT_AND_STEEL,
	};

	private static net.minecraft.world.item.ItemStack toolKitPiece(net.minecraft.world.item.Item base, String nameKey) {
		net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(base);
		stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.translatable(nameKey)
				.withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GREEN).withItalic(false)));
		net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
		tag.putBoolean(TOOL_KIT_TAG, true);
		stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
				net.minecraft.world.item.component.CustomData.of(tag));
		return stack;
	}

	private static boolean isToolKitPiece(net.minecraft.world.item.ItemStack stack) {
		net.minecraft.world.item.component.CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBoolean(TOOL_KIT_TAG);
	}

	/** Whether {@code owner} already has a live Tool Kit -- at most one may be active at a time. */
	public static boolean hasLiveToolKit(UUID owner) {
		for (Construct c : of(owner)) {
			if (c.type.kind() == ConstructType.Kind.TOOL_KIT) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Deploy: one diamond pickaxe/axe/shovel each. Refuses (leaving {@code c.toolKitGranted} false, so
	 * {@code deploy()} treats it as a failed placement and refunds) rather than {@code drop()}ing
	 * whatever doesn't fit -- a dropped piece with no construct behind it would be a free permanent
	 * diamond tool the moment it's picked back up, since nothing would ever end the kit that "granted" it.
	 */
	private static void spawnToolKit(ServerPlayer player, Construct c) {
		int free = 0;
		for (net.minecraft.world.item.ItemStack stack : player.getInventory().items) {
			if (stack.isEmpty()) {
				free++;
			}
		}
		if (free < TOOL_KIT_BASES.length) {
			return;
		}
		for (int i = 0; i < TOOL_KIT_BASES.length; i++) {
			if (!player.getInventory().add(toolKitPiece(TOOL_KIT_BASES[i], TOOL_KIT_NAME_KEYS[i]))) {
				// The free-slot count above makes this practically unreachable, but if it ever does
				// happen, strip whatever was granted so far rather than leaving a half-granted kit --
				// c.toolKitGranted stays false, so deploy() takes the failed-placement refund path.
				deleteLooseToolKitPieces(player);
				return;
			}
		}
		c.toolKitGranted = true;
	}

	/** Counts every tagged piece the player is holding, including one on the container cursor. */
	private static int countToolKitPieces(ServerPlayer owner) {
		int count = 0;
		for (net.minecraft.world.item.ItemStack stack : owner.getInventory().items) {
			if (isToolKitPiece(stack)) {
				count++;
			}
		}
		for (net.minecraft.world.item.ItemStack stack : owner.getInventory().offhand) {
			if (isToolKitPiece(stack)) {
				count++;
			}
		}
		if (isToolKitPiece(owner.containerMenu.getCarried())) {
			count++;
		}
		return count;
	}

	/**
	 * Dropping any one of the three tools ends the whole kit (per the deploy-as-one-bundle design) --
	 * called whenever a Tool Kit construct ends for any reason, so the remaining pieces don't linger.
	 */
	private static void removeToolKit(Construct c) {
		ServerPlayer p = c.level.getServer() != null ? c.level.getServer().getPlayerList().getPlayer(c.owner) : null;
		if (p != null) {
			deleteLooseToolKitPieces(p);
		}
	}

	/**
	 * Deletes any hard-light tool {@code player} is holding (inventory, offhand, or the container
	 * cursor) while they have no live Tool Kit construct of their own. Called both to clean up an
	 * ending kit's own leftovers immediately, and periodically for every online player (not just
	 * Green Lanterns -- a traded, gifted or chest-stashed piece can end up on anyone) via
	 * {@link #sweepLooseToolKitPieces}. Mirrors {@code GreenLanternSuitArmor#deleteLoose}'s "this
	 * shouldn't exist any more" pattern for exactly the same reason: a piece with no construct behind
	 * it any more would otherwise be a free, permanent diamond tool the moment anyone picks it up.
	 */
	public static boolean deleteLooseToolKitPieces(ServerPlayer player) {
		var inv = player.getInventory();
		boolean removed = false;
		for (int i = 0; i < inv.items.size(); i++) {
			if (isToolKitPiece(inv.items.get(i))) {
				inv.items.set(i, net.minecraft.world.item.ItemStack.EMPTY);
				removed = true;
			}
		}
		for (int i = 0; i < inv.offhand.size(); i++) {
			if (isToolKitPiece(inv.offhand.get(i))) {
				inv.offhand.set(i, net.minecraft.world.item.ItemStack.EMPTY);
				removed = true;
			}
		}
		if (isToolKitPiece(player.containerMenu.getCarried())) {
			player.containerMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
			removed = true;
		}
		return removed;
	}

	private static void batteringRam(ServerPlayer player) {
		if (!GreenLantern.abilityReady(player, "battering_ram")) {
			feedbackCooldown(player, "battering_ram");
			return;
		}
		if (!GreenLanternEnergy.spend(player, GreenLanternConfig.RAM_COST)) {
			GreenLanternEnergy.feedback(player, "message.projecthero.ability.low_charge");
			return;
		}
		GreenLantern.triggerCooldown(player, "battering_ram", GreenLanternConfig.RAM_COOLDOWN_TICKS);
		com.projecthero.mod.greenlantern.GreenLanternBattery.onAbilityUsed(player);
		AbilityHelpers.launchSelf(player, player.getLookAngle().scale(1.4).add(0, 0.1, 0));
		LivingEntity target = AbilityHelpers.raycastEntity(player, GreenLanternConfig.RAM_DISTANCE);
		if (target != null) {
			AbilityHelpers.hurt(player, target, GreenLanternConfig.RAM_DAMAGE * GreenLanternOath.multiplier(player));
			AbilityHelpers.knockbackFrom(target, player.position(), 2.0);
		}
		BlockHitResult hit = AbilityHelpers.raycastBlock(player, 3.0);
		if (hit.getType() != HitResult.Type.MISS) {
			BlockState state = player.level().getBlockState(hit.getBlockPos());
			if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
				player.level().setBlock(hit.getBlockPos(),
						state.setValue(net.minecraft.world.level.block.DoorBlock.OPEN, true), Block.UPDATE_ALL);
			}
		}
		AbilityHelpers.sound(player, SoundEvents.IRON_GOLEM_ATTACK, 1.0f, 0.8f);
	}

	// ---------------- block helpers ----------------

	// v0.11.5: green, not light blue -- "all the constructs need green models" so they read as the
	// Green Lantern's own hard light rather than a generic glass block.
	private static BlockState lightBlockState() {
		return Blocks.GREEN_STAINED_GLASS.defaultBlockState();
	}

	private static BlockState carryPlatformBlockState() {
		return Blocks.LIME_STAINED_GLASS.defaultBlockState();
	}

	private static void add(Construct c, BlockPos pos, BlockState state) {
		BlockState current = c.level.getBlockState(pos);
		if (!current.canBeReplaced() && !current.isAir()) {
			return;
		}
		if (current.getDestroySpeed(c.level, pos) < 0) {
			return; // bedrock / barrier / command block / unbreakable
		}
		if (c.level.getBlockEntity(pos) != null) {
			return; // never overwrite a container/block-entity
		}
		if (c.level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class,
				new net.minecraft.world.phys.AABB(pos)).stream().anyMatch(p -> !p.getUUID().equals(c.owner))) {
			return; // never suffocate another player
		}
		c.cells.add(new Construct.Cell(pos.immutable(), current, state));
	}

	private static void place(Construct c) {
		for (Construct.Cell cell : c.cells) {
			c.level.setBlock(cell.pos(), cell.placed(), Block.UPDATE_ALL);
		}
	}

	private static void restore(Construct c) {
		for (Construct.Cell cell : c.cells) {
			if (c.level.hasChunkAt(cell.pos()) && c.level.getBlockState(cell.pos()) == cell.placed()) {
				c.level.setBlock(cell.pos(), cell.previous(), Block.UPDATE_ALL);
			}
		}
	}

	// ---------------- damage / dismissal ----------------

	private static void damage(Construct c, float amount, ServerPlayer attacker) {
		c.hp -= amount;
		if (c.hp <= 0f) {
			dismissOne(c, true);
		}
	}

	private static void dismissOne(Construct c, boolean broken) {
		List<Construct> list = BY_OWNER.get(c.owner);
		if (list == null || !list.remove(c)) {
			return;
		}
		restore(c);
		if (c.type.kind() == ConstructType.Kind.MELEE_BUFF) {
			removeMeleeBuff(c);
		}
		if (c.type.kind() == ConstructType.Kind.TOOL_KIT) {
			removeToolKit(c);
		}
		if (c.type == ConstructType.CARRY_PLATFORM) {
			removeCarrySeats(c);
		}
		if (broken) {
			c.level.sendParticles(ParticleTypes.END_ROD, c.anchor.x, c.anchor.y, c.anchor.z, 20, 0.6, 0.6, 0.6, 0.05);
			c.level.playSound(null, c.anchor.x, c.anchor.y, c.anchor.z, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.6f, 0.8f);
			ServerPlayer owner = c.level.getServer() != null ? c.level.getServer().getPlayerList().getPlayer(c.owner) : null;
			if (owner != null) {
				applyEndCooldown(owner, c);
			}
		}
	}

	private static void removeMeleeBuff(Construct c) {
		ServerPlayer p = c.level.getServer() != null ? c.level.getServer().getPlayerList().getPlayer(c.owner) : null;
		if (p != null) {
			PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, BLADE_DAMAGE);
			PowerToggles.clearModifier(p, Attributes.ENTITY_INTERACTION_RANGE, BLADE_REACH);
		}
	}

	public static void dismissAll(UUID owner) {
		List<Construct> list = BY_OWNER.remove(owner);
		if (list == null) {
			return;
		}
		for (Construct c : list) {
			restore(c);
			if (c.type.kind() == ConstructType.Kind.MELEE_BUFF) {
				removeMeleeBuff(c);
			}
			if (c.type.kind() == ConstructType.Kind.TOOL_KIT) {
				removeToolKit(c);
			}
			if (c.type == ConstructType.CARRY_PLATFORM) {
				removeCarrySeats(c);
			}
		}
	}

	/** Shift+C -- dismiss every owned construct except the suit (the suit is never tracked here anyway). */
	public static void dismissRequested(ServerPlayer player) {
		dismissAll(player.getUUID());
		player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.constructs_dismissed"), true);
	}

	/** Death/logout/dimension-change/hard-depletion cleanup -- same effect as {@link #dismissAll}. */
	public static void clearFor(UUID owner) {
		dismissAll(owner);
	}

	// ---------------- global tick ----------------

	public static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		for (Iterator<Map.Entry<UUID, List<Construct>>> ownerIt = BY_OWNER.entrySet().iterator(); ownerIt.hasNext();) {
			Map.Entry<UUID, List<Construct>> entry = ownerIt.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
			if (owner == null) {
				// Logged out -- restore blocks now rather than waiting; the entry itself is dropped.
				for (Construct c : entry.getValue()) {
					restore(c);
					if (c.type == ConstructType.CARRY_PLATFORM) {
						removeCarrySeats(c);
					}
				}
				ownerIt.remove();
				continue;
			}
			Iterator<Construct> it = entry.getValue().iterator();
			while (it.hasNext()) {
				Construct c = it.next();
				if (c.expired(now) || !tickUpkeep(owner, c) || !tickKind(owner, c, now)) {
					restore(c);
					if (c.type.kind() == ConstructType.Kind.MELEE_BUFF) {
						removeMeleeBuff(c);
					}
					if (c.type.kind() == ConstructType.Kind.TOOL_KIT) {
						removeToolKit(c);
					}
					if (c.type == ConstructType.CARRY_PLATFORM) {
						removeCarrySeats(c);
					}
					applyEndCooldown(owner, c);
					it.remove();
				}
			}
		}
	}

	/**
	 * Sweeps every online player (not just Green Lanterns -- a hard-light tool traded, gifted or left
	 * in a shared chest can end up on anyone) for loose Tool Kit pieces with no live construct behind
	 * them. Throttled by the caller to once/sec rather than every tick -- an untracked tool sitting
	 * around for up to a second before vanishing is imperceptible, and scanning every online player's
	 * full inventory every single tick is not worth paying for.
	 */
	public static void sweepLooseToolKitPieces(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (!hasLiveToolKit(p.getUUID())) {
				deleteLooseToolKitPieces(p);
			}
		}
	}

	/**
	 * Returns false if upkeep could not be paid (construct should end). v0.11.7: Mining Drill and Energy
	 * Blade are free while merely equipped -- only {@link Construct#toggledOn} actually drains anything.
	 */
	private static boolean tickUpkeep(ServerPlayer owner, Construct c) {
		if ((c.type == ConstructType.MINING_DRILL || c.type == ConstructType.ENERGY_BLADE) && !c.toggledOn) {
			return true;
		}
		float upkeepPerSec = c.type.upkeepPerSec();
		if (upkeepPerSec <= 0f) {
			return true;
		}
		return GreenLanternEnergy.drainTick(owner, upkeepPerSec / 20f);
	}

	/** Returns false if the construct's own logic says it should end now (target lost, drill idle, etc). */
	private static boolean tickKind(ServerPlayer owner, Construct c, long now) {
		switch (c.type.kind()) {
			case TURRET -> tickTurret(owner, c, now);
			case CAGE -> {
				return c.level.getEntity(c.cagedEntityId) != null;
			}
			case BUBBLE -> tickBubble(owner, c);
			case DRILL -> tickDrill(owner, c);
			case MELEE_BUFF -> tickEnergyBlade(owner, c);
			case PLATFORM_BLOCKS -> {
				if (c.type == ConstructType.CARRY_PLATFORM) {
					tickCarryPlatform(owner, c);
				}
			}
			case TOOL_KIT -> {
				// Dropping any one of the four tools ends the whole kit (removeToolKit strips the rest).
				return countToolKitPieces(owner) >= TOOL_KIT_BASES.length;
			}
			default -> {}
		}
		return true;
	}

	/** While toggled on, a green glow around the wielding hand/arm ("make the players arm glow green to
	 *  show that its active", explicit user request). */
	private static void tickEnergyBlade(ServerPlayer owner, Construct c) {
		if (!c.toggledOn || owner.tickCount % 3 != 0) {
			return;
		}
		net.minecraft.world.phys.Vec3 hand = owner.getEyePosition()
				.add(owner.getLookAngle().scale(0.7)).add(0, -0.4, 0);
		owner.serverLevel().sendParticles(GREEN_DUST, hand.x, hand.y, hand.z, 2, 0.08, 0.08, 0.08, 0.0);
	}

	private static void tickTurret(ServerPlayer owner, Construct c, long now) {
		// v0.11.7: a standing green hard-light rod marks the anchor at all times (explicit user request,
		// "the sentry itself should spawn a green rod to visually show them"), independent of whether it
		// fires this tick.
		if (now % 4 == 0) {
			AbilityHelpers.line(c.level, c.anchor, c.anchor.add(0, 1.6, 0), GREEN_DUST, 2.0);
		}
		if (now < c.nextFireAt) {
			return;
		}
		c.nextFireAt = now + GreenLanternConfig.TURRET_FIRE_INTERVAL_TICKS;
		LivingEntity target = null;
		double best = GreenLanternConfig.TURRET_TARGET_RADIUS * GreenLanternConfig.TURRET_TARGET_RADIUS;
		for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class,
				new net.minecraft.world.phys.AABB(c.anchor.x, c.anchor.y, c.anchor.z, c.anchor.x, c.anchor.y, c.anchor.z)
						.inflate(GreenLanternConfig.TURRET_TARGET_RADIUS))) {
			if (!isHostileTarget(owner, e)) {
				continue;
			}
			double d = e.position().distanceToSqr(c.anchor);
			if (d < best) {
				best = d;
				target = e;
			}
		}
		if (target == null) {
			return;
		}
		AbilityHelpers.line(c.level, c.anchor, target.position().add(0, target.getBbHeight() * 0.5, 0),
				ParticleTypes.END_ROD, 3.0);
		AbilityHelpers.hurt(owner, target, GreenLanternConfig.TURRET_DAMAGE * GreenLanternOath.multiplier(owner));
		c.level.playSound(null, c.anchor.x, c.anchor.y, c.anchor.z, SoundEvents.ARROW_SHOOT, SoundSource.NEUTRAL, 0.4f, 1.6f);
	}

	private static boolean isHostileTarget(ServerPlayer owner, LivingEntity e) {
		if (e == owner || !e.isAlive()) {
			return false;
		}
		if (e instanceof net.minecraft.world.entity.player.Player p) {
			if (isSquadmate(owner, p)) {
				return false;
			}
			return owner.getServer() != null && owner.getServer().isPvpAllowed();
		}
		if (e instanceof TamableAnimal tame && tame.isTame()) {
			return false; // never target friendly tamed mobs
		}
		return e instanceof net.minecraft.world.entity.monster.Enemy;
	}

	private static boolean isSquadmate(ServerPlayer owner, net.minecraft.world.entity.player.Player other) {
		return owner.getServer() != null
				&& com.projecthero.mod.squad.SquadManager.get(owner.getServer()).sameSquad(owner.getUUID(), other.getUUID());
	}

	private static void tickBubble(ServerPlayer owner, Construct c) {
		if (owner.position().distanceToSqr(c.anchor) <= GreenLanternConfig.BUBBLE_RADIUS * GreenLanternConfig.BUBBLE_RADIUS) {
			owner.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 30, 0, false, false, false));
			if (owner.getAirSupply() < owner.getMaxAirSupply()) {
				owner.setAirSupply(owner.getMaxAirSupply());
			}
		}
	}

	/**
	 * v0.11.7: only mines while {@link Construct#toggledOn} -- a second C press on the already-active
	 * drill toggles it (see {@link #toggleConstruct}). The per-block charge is gone; mining now just
	 * rides on the flat {@link GreenLanternConfig#DRILL_UPKEEP_PER_SEC} upkeep (see {@link #tickUpkeep})
	 * that already only drains while on. While on, green particles spiral around the mining hand
	 * ("the player should have green particles rotating around their hand to simulate the drill").
	 */
	private static void tickDrill(ServerPlayer owner, Construct c) {
		if (!c.toggledOn) {
			return;
		}
		net.minecraft.world.phys.Vec3 hand = owner.getEyePosition()
				.add(owner.getLookAngle().scale(0.7)).add(0, -0.4, 0);
		double angle = (owner.tickCount % 20) * (Math.PI * 2 / 20.0);
		owner.serverLevel().sendParticles(GREEN_DUST,
				hand.x + Math.cos(angle) * 0.25, hand.y + Math.sin(angle) * 0.25, hand.z, 1, 0, 0, 0, 0.0);
		if (owner.tickCount % 5 != 0) {
			return;
		}
		BlockHitResult hit = AbilityHelpers.raycastBlock(owner, GreenLanternConfig.DRILL_REACH);
		if (hit.getType() == HitResult.Type.MISS) {
			return;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = owner.level().getBlockState(pos);
		if (state.isAir() || state.getDestroySpeed(owner.level(), pos) < 0) {
			return;
		}
		owner.gameMode.destroyBlock(pos);
	}
}
