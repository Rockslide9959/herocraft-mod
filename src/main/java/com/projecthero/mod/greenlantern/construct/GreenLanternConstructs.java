package com.projecthero.mod.greenlantern.construct;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.entity.HardLightConstructEntity;
import com.projecthero.mod.greenlantern.GreenLanternEnergy;
import com.projecthero.mod.greenlantern.GreenLanternOath;
import com.projecthero.mod.greenlantern.block.GreenLanternBlocks;
import com.projecthero.mod.greenlantern.block.HardLightBlock;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.PowerToggles;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The reusable hard-light construct system: owner-tracked, TTL-restoring, capacity-enforced. Every
 * one of the named constructs is a {@link Construct} instance dispatched by
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
 *
 * <p><b>v0.13.21 look-and-feel pass</b> (explicit user request, "all the constructs look and work better"):
 * <ul>
 *   <li>Every block construct is built from the new {@link HardLightBlock} family (translucent full-bright green
 *   with a bright rim, self-removing if orphaned) instead of stained glass / a Sea Lantern; the Stair/Ramp uses real
 *   stair shapes you can walk up, Lantern Light is a floating orb with no collision.</li>
 *   <li>Block constructs build out over a few ticks ({@link Construct#buildPerTick}) with a spark per cell, a beam of
 *   green light traces from the hand to the spot being shaped, and every construct dissolves in a scatter of light
 *   when it ends -- not only when it is punched down. Timed constructs flicker and chime over their last 3 seconds.</li>
 *   <li>Placement aims at the air cell in front of whatever face you point at ({@link #targetCell}) rather than a raw
 *   hit point that could land inside the block -- which used to make a Platform aimed at the ground fail outright --
 *   and Wall / Turret drop onto the ground under the aimed spot. No cell is ever placed inside any player, the
 *   caster included.</li>
 *   <li>Per construct: see the v0.13.21 notes on each spawn/tick method below and in {@code GREENLANTERN_REFERENCE.md}.</li>
 * </ul>
 * {@link #CELL_INDEX} maps every placed cell back to its construct (O(1) punch/break/seat lookups and the orphan
 * check), and {@link #LIVE_DISPLAYS} does the same job for the turret's display entities.
 */
public final class GreenLanternConstructs {
	private static final Map<UUID, List<Construct>> BY_OWNER = new ConcurrentHashMap<>();
	/** v0.13.21: dimension -> cell -> owning construct, for every hard-light block currently in the world. */
	private static final Map<ResourceKey<Level>, Map<BlockPos, Construct>> CELL_INDEX = new ConcurrentHashMap<>();
	/** v0.13.21: every live hard-light display entity. One carrying {@link #DISPLAY_TAG} that is not in here is an orphan. */
	private static final Set<UUID> LIVE_DISPLAYS = ConcurrentHashMap.newKeySet();
	/** Orphaned display entities found while loading, discarded on the next construct tick (not mid-load). */
	private static final List<Entity> PENDING_DISCARD = Collections.synchronizedList(new ArrayList<>());
	private static final String DISPLAY_TAG = "projecthero_hard_light";
	/** v0.13.21: owner -> their Battering Ram head while it is still travelling. */
	private static final Map<UUID, RamStrike> RAM_ACTIVE = new ConcurrentHashMap<>();

	private static final net.minecraft.resources.ResourceLocation BLADE_DAMAGE =
			com.projecthero.mod.ProjectHeroMod.id("green_lantern_energy_blade");
	private static final net.minecraft.resources.ResourceLocation BLADE_REACH =
			com.projecthero.mod.ProjectHeroMod.id("green_lantern_energy_blade_reach");
	/** Lantern-Corps green, matching every other hard-light effect's dust colour in this power. */
	private static final ParticleOptions GREEN_DUST = new DustParticleOptions(new Vector3f(0.208f, 0.941f, 0.459f), 1.5f);
	/** v0.13.21: the small, paler spark every construct cell is built / dissolved with. */
	private static final ParticleOptions SPARK = new DustParticleOptions(new Vector3f(0.55f, 1.0f, 0.62f), 0.9f);
	/** v0.13.21: thinner dust for tether lines and the bubble's shell. */
	private static final ParticleOptions FINE_DUST = new DustParticleOptions(new Vector3f(0.30f, 0.98f, 0.52f), 0.8f);

	/** Hand-construct bitmask bits for {@link ModAttachments#GREEN_LANTERN_HAND_CONSTRUCTS}. */
	public static final int HAND_BLADE = 1;
	public static final int HAND_DRILL = 2;

	private GreenLanternConstructs() {
	}

	public static void initialize() {
		GreenLanternRingLight.initialize(); // v0.15.9: no ring light survives a server stop
		// Punching one of a construct's own blocks either chips away at its HP (Wall/Cage; the whole
		// construct collapses at 0) or, for the HP-less kinds (Platform/Bridge/Stair-Ramp/Lantern
		// Light), dismisses it outright on the first punch -- either way the vanilla break path never
		// runs, so a construct block is never actually mined and never drops anything. Mirrors
		// ConjuredStructures' dismiss-by-punch pattern. v0.13.21: an O(1) CELL_INDEX lookup instead of a scan.
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (!(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			Construct c = cellOwner(level, pos);
			if (c == null) {
				return InteractionResult.PASS;
			}
			punch(c, pos, sp);
			return InteractionResult.SUCCESS;
		});

		// A block-based construct is never meant to be minable through the normal survival path either
		// (a player standing under a Platform and breaking it from below, a pickaxe on a Bridge, etc.) --
		// treat any break attempt on a tracked cell as a punch-dismiss instead of letting it break/drop.
		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
			if (!(player instanceof ServerPlayer sp)) {
				return true;
			}
			Construct c = cellOwner(level, pos);
			if (c == null) {
				return true;
			}
			punch(c, pos, sp);
			return false;
		});

		// v0.11.5: right-clicking a Carry Platform cell seats the clicking player on it (an invisible
		// armour-stand "seat" riding along whenever the platform moves), so the owner can ferry more than
		// just themselves. Anyone may sit, not just the owner or squadmates.
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			Construct c = cellOwner(level, pos);
			if (c == null || c.type != ConstructType.CARRY_PLATFORM) {
				return InteractionResult.PASS;
			}
			for (int i = 0; i < c.cells.size(); i++) {
				if (c.cells.get(i).pos().equals(pos)) {
					trySeat(sp, c, i);
					return InteractionResult.SUCCESS;
				}
			}
			return InteractionResult.PASS;
		});

		// v0.13.21: a turret display entity saved into a chunk by a crash/restart (or left in a chunk that was
		// unloaded when its turret ended) is recognised by its tag and removed the moment it loads back in.
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.getTags().contains(DISPLAY_TAG) && !LIVE_DISPLAYS.contains(entity.getUUID())) {
				PENDING_DISCARD.add(entity);
			}
		});
	}

	public static void clearSessionState() {
		GreenLanternRingLight.clearSessionState(); // v0.15.9
		BY_OWNER.clear();
		RESCUE_HELD.clear();
		RESCUE_BUBBLE.clear();
		CELL_INDEX.clear();
		LIVE_DISPLAYS.clear();
		PENDING_DISCARD.clear();
		RAM_ACTIVE.clear();
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

	/** v0.13.21: whether a live construct owns the hard-light block at {@code pos} -- the blocks' own orphan check. */
	public static boolean isTrackedCell(ServerLevel level, BlockPos pos) {
		Map<BlockPos, Construct> cells = CELL_INDEX.get(level.dimension());
		return cells != null && cells.containsKey(pos);
	}

	private static Construct cellOwner(Level level, BlockPos pos) {
		Map<BlockPos, Construct> cells = CELL_INDEX.get(level.dimension());
		return cells == null ? null : cells.get(pos);
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
	 * is unconditional (it is still occupying world blocks either way). v0.13.21: also re-syncs
	 * {@link ModAttachments#GREEN_LANTERN_HAND_CONSTRUCTS}, so every client draws the blade / drill on the hand.
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
		syncHandConstructs(player);
		// v0.11.8: at the hand, not the head -- see AbilityHelpers#handPosition's javadoc.
		Vec3 toggleGlow = AbilityHelpers.handPosition(player);
		player.serverLevel().sendParticles(c.toggledOn ? SPARK : GREEN_DUST, toggleGlow.x, toggleGlow.y, toggleGlow.z,
				c.toggledOn ? 10 : 6, 0.2, 0.25, 0.2, 0.03);
		player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
				c.toggledOn ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS,
				0.5f, c.toggledOn ? 1.5f : 1.0f);
	}

	/** v0.13.21: publishes which hand constructs are switched on, for the client-side blade / drill models. */
	private static void syncHandConstructs(ServerPlayer player) {
		int mask = 0;
		for (Construct c : of(player.getUUID())) {
			if (c.toggledOn && c.type == ConstructType.ENERGY_BLADE) {
				mask |= HAND_BLADE;
			}
			if (c.toggledOn && c.type == ConstructType.MINING_DRILL) {
				mask |= HAND_DRILL;
			}
		}
		if (player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_HAND_CONSTRUCTS, 0) != mask) {
			player.setAttached(ModAttachments.GREEN_LANTERN_HAND_CONSTRUCTS, mask);
		}
	}

	private static void syncHandConstructs(MinecraftServer server, UUID owner) {
		ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(owner);
		if (p != null) {
			syncHandConstructs(p);
		}
	}

	// ---------------- deploy ----------------

	/** MINING_DRILL, ENERGY_BLADE and CARRY_PLATFORM (v0.11.7): a second deploy of the same type toggles
	 *  the caster's own already-active instance instead of trying (and refusing) to place another one. */
	private static final java.util.Set<ConstructType> TOGGLE_TYPES =
			java.util.EnumSet.of(ConstructType.MINING_DRILL, ConstructType.ENERGY_BLADE, ConstructType.CARRY_PLATFORM);

	public static void deploy(ServerPlayer player, ConstructType type) {
		// v0.14.3: Buzzsaw / Anvil Drop / Chain Snare / Launch Pad / Emerald Warrior are hard-light entities
		if (com.projecthero.mod.greenlantern.GreenLanternConstructAttacks.deploy(player, type)) {
			return;
		}
		if (type == ConstructType.BATTERING_RAM) {
			batteringRam(player);
			return;
		}
		if (type == ConstructType.RESCUE_TETHER) {
			rescueGrab(player);
			return;
		}
		// v0.15.9: the Lantern Light is the ring's own light now -- a second press puts it out
		if (type == ConstructType.LANTERN_LIGHT) {
			Construct lit = firstOfType(player.getUUID(), type);
			if (lit != null) {
				dismissOne(lit, false);
				player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE,
						SoundSource.PLAYERS, 0.5f, 1.2f);
				return;
			}
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
		// v0.13.21: find the cage's target BEFORE charging for it -- no spend-then-refund round trip for a miss.
		LivingEntity cageTarget = null;
		if (type.kind() == ConstructType.Kind.CAGE) {
			cageTarget = AbilityHelpers.raycastEntity(player, GreenLanternConfig.CAGE_RANGE);
			if (cageTarget == null || !AbilityHelpers.isValidGrabTarget(cageTarget, player)) {
				GreenLanternEnergy.feedback(player, "message.projecthero.ability.invalid_target");
				return;
			}
		}
		float cost = totalCost(type, player);
		if (!GreenLanternEnergy.spend(player, cost)) {
			GreenLanternEnergy.feedback(player, "message.projecthero.ability.low_charge");
			return;
		}
		com.projecthero.mod.greenlantern.GreenLanternBattery.onAbilityUsed(player);

		ServerLevel level = player.serverLevel();
		Vec3 anchor = anchorFor(player, type, cageTarget);
		Construct c = new Construct(player.getUUID(), type, level, anchor, level.getGameTime());
		switch (type.kind()) {
			case MELEE_BUFF -> spawnEnergyBlade(player, c);
			case WALL -> spawnWall(player, c);
			case PLATFORM_BLOCKS -> spawnPlatform(player, c, type == ConstructType.CARRY_PLATFORM);
			case BRIDGE_BLOCKS -> spawnBridge(player, c);
			case RAMP_BLOCKS -> spawnRamp(player, c);
			case RING_LIGHT -> {
				c.toggledOn = true;
				GreenLanternRingLight.start(player);
			}
			case CAGE -> spawnCage(player, c, cageTarget);
			case TURRET -> {} // anchor + timer only, ticked below (its display entities are added once it is tracked)
			case BUBBLE -> c.radius = GreenLanternConfig.BUBBLE_RADIUS;
			case DRILL -> {} // marker only
			case TOOL_KIT -> spawnToolKit(player, c);
			default -> {}
		}
		boolean usesBlocks = type.kind() == ConstructType.Kind.WALL || type.kind() == ConstructType.Kind.PLATFORM_BLOCKS
				|| type.kind() == ConstructType.Kind.BRIDGE_BLOCKS || type.kind() == ConstructType.Kind.RAMP_BLOCKS
				|| type.kind() == ConstructType.Kind.CAGE;
		boolean placementFailed = (usesBlocks && c.cells.isEmpty())
				|| (type.kind() == ConstructType.Kind.CAGE && c.cagedEntityId < 0)
				|| (type.kind() == ConstructType.Kind.TOOL_KIT && !c.toolKitGranted);
		if (placementFailed) {
			// placement failed entirely (e.g. every target cell was protected/occupied, or no target found)
			removeCarrySeats(c);
			GreenLanternEnergy.refund(player, cost);
			GreenLanternEnergy.feedback(player, type.kind() == ConstructType.Kind.TOOL_KIT
					? "message.projecthero.green_lantern.tool_kit_no_room" : "message.projecthero.ability.invalid_target");
			return;
		}
		BY_OWNER.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(c);
		com.projecthero.mod.greenlantern.GreenLanternVisuals.anim(player,
				com.projecthero.mod.greenlantern.data.GreenLanternFx.ANIM_CONSTRUCT); // v0.14.3: the shaping gesture
		startBuild(c);
		if (type.kind() == ConstructType.Kind.TURRET) {
			spawnTurretDisplays(c);
		}
		emitDeployGlow(player);
		if (isAimed(type)) {
			// v0.13.21: a beam of will traces from the ring hand to whatever is being shaped
			AbilityHelpers.line(level, AbilityHelpers.handPosition(player), c.anchor.add(0, 0.5, 0), SPARK, 1.5);
		}
		if (type.kind() == ConstructType.Kind.BUBBLE) {
			emitBubbleShell(c, 64);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_AMBIENT,
				SoundSource.PLAYERS, 0.5f, 1.4f);
		level.playSound(null, c.anchor.x, c.anchor.y, c.anchor.z, SoundEvents.AMETHYST_BLOCK_CHIME,
				SoundSource.PLAYERS, 0.9f, 1.2f);
	}

	private static boolean isAimed(ConstructType type) {
		return type == ConstructType.HARD_LIGHT_WALL || type == ConstructType.PLATFORM
				|| type == ConstructType.SENTRY_TURRET || type == ConstructType.CONTAINMENT_CAGE;
	}

	/**
	 * A burst of green hard-light particles at the caster's hand -- v0.11.5, "at the very least ... make
	 * the players hand glow green particles so the player knows that something is happening", added for
	 * every construct kind, including the marker-only ones (turret/bubble/drill/energy blade/tool kit)
	 * that place no blocks of their own to look at. v0.11.8: actually off to the side of the view instead
	 * of straight down the look vector -- the old offset put it dead-centre of the first-person screen,
	 * which read as "particles in the player's face" rather than "at the hand" (see
	 * {@link AbilityHelpers#handPosition}).
	 */
	private static void emitDeployGlow(ServerPlayer player) {
		Vec3 hand = AbilityHelpers.handPosition(player);
		player.serverLevel().sendParticles(GREEN_DUST, hand.x, hand.y, hand.z, 12, 0.15, 0.15, 0.15, 0.02);
	}

	/**
	 * v0.13.21: where each construct takes shape. Aimed ones resolve the air cell in front of the face you point at
	 * ({@link #targetCell}); Wall and Turret then drop onto the ground under it; the Atmosphere Bubble now forms around
	 * the caster (it used to be centred up to 24 blocks away on the aim point, where it never covered the caster at all).
	 */
	private static Vec3 anchorFor(ServerPlayer player, ConstructType type, LivingEntity cageTarget) {
		ServerLevel level = player.serverLevel();
		return switch (type) {
			case CARRY_PLATFORM -> carryPlatformAnchor(player);
			case PLATFORM -> Vec3.atBottomCenterOf(platformCell(player));
			case HARD_LIGHT_WALL, SENTRY_TURRET -> Vec3.atBottomCenterOf(groundSnap(level,
					targetCell(player, GreenLanternConfig.CONSTRUCT_PLACE_RANGE)));
			case CONTAINMENT_CAGE -> cageTarget.position();
			default -> player.position();
		};
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

	/**
	 * v0.13.21: the air cell a construct aimed at a block should occupy -- the one in front of the face the aim ray hit,
	 * or the cell at full range if it hit nothing. The old code used the raw hit point, which sits exactly on the face
	 * and so often resolved to the solid block itself.
	 */
	private static BlockPos targetCell(ServerPlayer player, double range) {
		BlockHitResult hit = AbilityHelpers.raycastBlock(player, range);
		if (hit.getType() != HitResult.Type.MISS) {
			return hit.getBlockPos().relative(hit.getDirection());
		}
		return BlockPos.containing(player.getEyePosition().add(player.getLookAngle().scale(range)));
	}

	/** Drops {@code pos} down through open cells (at most {@link GreenLanternConfig#CONSTRUCT_GROUND_SNAP_BLOCKS}) onto the ground. */
	private static BlockPos groundSnap(ServerLevel level, BlockPos pos) {
		BlockPos p = pos;
		for (int i = 0; i < GreenLanternConfig.CONSTRUCT_GROUND_SNAP_BLOCKS; i++) {
			BlockState below = level.getBlockState(p.below());
			if (!below.isAir() && !below.canBeReplaced()) {
				break;
			}
			p = p.below();
		}
		return p;
	}

	/**
	 * v0.13.21: the Platform's layer. Falling and looking down = right under your feet (a catch); otherwise the aimed
	 * air cell -- so a Platform aimed at the ground forms a raised floor on it instead of failing ("invalid target")
	 * because every one of its cells was inside the ground.
	 */
	private static BlockPos platformCell(ServerPlayer player) {
		if (!player.onGround() && player.getXRot() >= GreenLanternConfig.PLATFORM_CATCH_PITCH) {
			return player.blockPosition().below();
		}
		return targetCell(player, GreenLanternConfig.PLATFORM_RANGE);
	}

	/** Constructs with an explicit post-collapse cooldown; null for the rest. v0.11.9: Containment Cage no
	 *  longer has one at all -- explicit user request ("remove cooldowns and just make it disappear after
	 *  15 seconds"), it can simply be recast (at its normal cost) the instant it expires or is punched down. */
	private static String cooldownIdFor(ConstructType type) {
		return switch (type) {
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
			case BUZZSAW, ANVIL_DROP, CHAIN_SNARE, EMERALD_WARRIOR ->
					com.projecthero.mod.greenlantern.GreenLanternConstructAttacks.cooldownIdFor(type);
			default -> cooldownIdFor(type);
		};
		return id == null ? 0 : GreenLantern.cooldownRemaining(player, id);
	}

	private static int cooldownTicksFor(ConstructType type) {
		return switch (type) {
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

	// ---------------- per-kind spawn ----------------

	/**
	 * v0.11.7: deploying just equips the stance, free and with no effect yet -- a second C press
	 * ({@link #toggleConstruct}) is what actually turns the blade (and its cost) on.
	 */
	private static void spawnEnergyBlade(ServerPlayer player, Construct c) {
	}

	/** v0.13.21: stands on the ground under the aimed spot and rises out of it one row per tick. */
	private static void spawnWall(ServerPlayer player, Construct c) {
		Direction facing = player.getDirection();
		Direction side = facing.getClockWise();
		BlockPos center = BlockPos.containing(c.anchor);
		for (int h = 0; h < GreenLanternConfig.WALL_HEIGHT; h++) {
			for (int w = -2; w <= 2; w++) {
				add(c, center.relative(side, w).above(h), lightBlockState());
			}
		}
		c.buildPerTick = 5;
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
			// platformCenter tracks the same frame as c.anchor/carryPlatformAnchor() -- tickCarryPlatform()
			// re-derives the block-grid center from it with the same (now none) offset, so the two never
			// drift apart.
			c.platformCenter = c.anchor;
			c.platformBlockCenter = center;
			spawnCarryPlatformSeats(c);
			return;
		}
		// v0.11.7: a 4x4 footprint (up from 3x3). v0.13.21: on the aimed air cell's layer (see platformCell) and
		// spreading out from its middle over a few ticks.
		BlockPos center = BlockPos.containing(c.anchor);
		Direction facing = player.getDirection();
		Direction side = facing.getClockWise();
		int size = GreenLanternConfig.PLATFORM_SIZE;
		for (int f = -1; f < size - 1; f++) {
			for (int w = -1; w < size - 1; w++) {
				add(c, center.relative(facing, f).relative(side, w), lightBlockState());
			}
		}
		Vec3 mid = Vec3.atCenterOf(center).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.5))
				.add(Vec3.atLowerCornerOf(side.getNormal()).scale(0.5));
		c.cells.sort(Comparator.comparingDouble(cell -> Vec3.atCenterOf(cell.pos()).distanceToSqr(mid)));
		c.buildPerTick = 4;
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
			ArmorStand stand = newCarrySeat(c.level, cell.pos());
			c.level.addFreshEntity(stand);
			c.seatEntityIds.add(stand.getId());
		}
	}

	/**
	 * The invisible seat over one Carry Platform cell (not yet added to the level). v0.14.4: a zero-size MARKER stand
	 * standing on the cell's top surface -- a stand's seat point is its own height, so a normal 2-block stand sat riders
	 * two blocks above the platform; a marker's is its feet, so the rider sits ON the platform.
	 */
	public static ArmorStand newCarrySeat(net.minecraft.world.level.Level level, BlockPos cell) {
		ArmorStand stand = new ArmorStand(net.minecraft.world.entity.EntityType.ARMOR_STAND, level);
		stand.setPos(cell.getX() + 0.5, cell.getY() + 1.0, cell.getZ() + 0.5);
		stand.setInvisible(true);
		stand.setNoGravity(true);
		stand.setInvulnerable(true);
		stand.setSilent(true);
		stand.setNoBasePlate(true);
		stand.setShowArms(false);
		stand.getEntityData().set(ArmorStand.DATA_CLIENT_FLAGS,
				(byte) (stand.getEntityData().get(ArmorStand.DATA_CLIENT_FLAGS) | ArmorStand.CLIENT_FLAG_MARKER));
		stand.refreshDimensions();
		return stand;
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
		c.built = 0;
		placeCells(c, c.cells.size(), false);
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

	/**
	 * v0.11.7: fixed 20-long, 3-wide -- explicit user request (replaces the old aim-to-a-target length).
	 * v0.13.21: runs out from the caster two rows per tick, and starts right under your feet if you cast it in the air.
	 */
	private static void spawnBridge(ServerPlayer player, Construct c) {
		Direction facing = player.getDirection();
		Direction side = facing.getClockWise();
		BlockPos start = player.onGround() ? player.blockPosition().below().relative(facing) : player.blockPosition().below();
		int halfWidth = GreenLanternConfig.BRIDGE_WIDTH / 2;
		for (int f = 0; f < GreenLanternConfig.BRIDGE_LENGTH; f++) {
			for (int w = -halfWidth; w <= halfWidth; w++) {
				add(c, start.relative(facing, f).relative(side, w), lightBlockState());
			}
		}
		c.buildPerTick = GreenLanternConfig.BRIDGE_WIDTH * 2;
	}

	/**
	 * v0.11.7: 3 blocks wide -- explicit user request. v0.13.21: real stair steps (walkable without jumping every
	 * block, which the old full-block staircase needed), starting at your feet one block ahead rather than inside the
	 * ground, and climbing into place one step per tick.
	 */
	private static void spawnRamp(ServerPlayer player, Construct c) {
		Direction facing = player.getDirection();
		Direction side = facing.getClockWise();
		BlockPos start = player.blockPosition().relative(facing);
		int halfWidth = GreenLanternConfig.RAMP_WIDTH / 2;
		BlockState step = GreenLanternBlocks.HARD_LIGHT_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing);
		for (int f = 0; f < GreenLanternConfig.RAMP_MAX_SEGMENTS; f++) {
			for (int w = -halfWidth; w <= halfWidth; w++) {
				add(c, start.relative(facing, f).relative(side, w).above(f), step);
			}
		}
		c.facing = facing;
		c.buildPerTick = GreenLanternConfig.RAMP_WIDTH;
	}

	/**
	 * v0.13.21 rework: a closed hard-light box sized to the target -- a hollow 1-3 blocks wide and 1-4 tall (from its
	 * bounding box) with the target snapped to its middle, instead of the fixed 1-wide, 1-tall hollow that big mobs
	 * were shoved straight out of (they overlapped its walls). Floor and roof cells are simply tried along with the
	 * walls -- solid ground is skipped by {@link #add}, so the box seals itself exactly where there is open space to
	 * escape through, no separate sealBottom/sealTop guesswork. {@link #tickCage} keeps the target inside.
	 *
	 * <p>Earlier history: v0.11.8 moved off a solid 2x2 that ejected the target from its own cell; v0.11.10 made it
	 * work on any living target via {@link AbilityHelpers#isValidGrabTarget} (the Rescue Tether's filter).
	 */
	private static void spawnCage(ServerPlayer player, Construct c, LivingEntity target) {
		int[] box = cageInterior(target);
		int x0 = box[0], floor = box[1], z0 = box[2], w = box[3], h = box[4];
		// snap the target into the middle of the hollow first, so it is never standing in (and blocking) a wall cell
		Vec3 centre = new Vec3(x0 + w / 2.0, floor, z0 + w / 2.0);
		target.teleportTo(centre.x, Math.max(target.getY(), floor), centre.z);
		target.setDeltaMovement(Vec3.ZERO);
		target.hurtMarked = true;
		for (int x = x0 - 1; x <= x0 + w; x++) {
			for (int y = floor - 1; y <= floor + h; y++) {
				for (int z = z0 - 1; z <= z0 + w; z++) {
					boolean shell = x == x0 - 1 || x == x0 + w || y == floor - 1 || y == floor + h || z == z0 - 1 || z == z0 + w;
					if (shell) {
						add(c, new BlockPos(x, y, z), lightBlockState());
					}
				}
			}
		}
		if (c.cells.isEmpty()) {
			return;
		}
		c.cagedEntityId = target.getId();
		c.cageCenter = centre;
		c.cageHalfWidth = w / 2.0;
		c.buildPerTick = 0;
	}

	/** {x0, floorY, z0, width, height} of the hollow a Containment Cage builds around {@code target}. */
	private static int[] cageInterior(LivingEntity target) {
		int w = Mth.clamp(Mth.ceil(target.getBbWidth() - 1.0e-3), 1, GreenLanternConfig.CAGE_MAX_INTERIOR_WIDTH);
		int h = Mth.clamp(Mth.ceil(target.getBbHeight() - 1.0e-3), 1, GreenLanternConfig.CAGE_MAX_INTERIOR_HEIGHT);
		int x0 = (int) Math.round(target.getX() - w / 2.0);
		int z0 = (int) Math.round(target.getZ() - w / 2.0);
		return new int[] {x0, target.blockPosition().getY(), z0, w, h};
	}

	// ---------------- Rescue Tether (v0.11.6: a proper grab -- hold, throw, or set down) ----------------

	/** Player UUID -> the entity id they're currently carrying via Rescue Tether (absent = not holding). */
	private static final Map<UUID, Integer> RESCUE_HELD = new ConcurrentHashMap<>();
	/** v0.14.22: player UUID -> the hard-light bubble construct drawn round what they are carrying. */
	private static final Map<UUID, Integer> RESCUE_BUBBLE = new ConcurrentHashMap<>();

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
		Vec3 hand = AbilityHelpers.handPosition(player);
		Vec3 mid = target.position().add(0, target.getBbHeight() * 0.5, 0);
		AbilityHelpers.line(level, hand, mid, GREEN_DUST, 3.0);
		level.sendParticles(SPARK, mid.x, mid.y, mid.z, 16, target.getBbWidth() * 0.5, target.getBbHeight() * 0.4,
				target.getBbWidth() * 0.5, 0.02);

		RESCUE_HELD.put(player.getUUID(), target.getId());
		spawnRescueBubble(player, target);
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
	 * v0.13.21: the hold point is pulled in short of any wall between you and it (the target used to be pushed into
	 * blocks and suffocate when you looked at a wall), and a green tether visibly connects your hand to the target.
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
			popRescueBubble(player);
			return;
		}
		// v0.14.22, explicit user request: no time limit on the carry -- the per-second upkeep that used to wear the
		// charge down and drop the target is gone. Only C (throw) / Shift+C (set down) end it.
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 hold = eye.add(look.scale(GreenLanternConfig.TETHER_HOLD_DISTANCE));
		BlockHitResult wall = player.level().clip(new ClipContext(eye, hold, ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, player));
		if (wall.getType() != HitResult.Type.MISS) {
			double back = Math.max(0.4, target.getBbWidth() * 0.5 + 0.1);
			hold = wall.getLocation().subtract(look.scale(back));
		}
		target.setPos(hold.x, hold.y - target.getBbHeight() / 2, hold.z);
		pacifyCarried(target);
		target.setDeltaMovement(Vec3.ZERO);
		target.fallDistance = 0f;
		target.hurtMarked = true;
		Integer bubbleId = RESCUE_BUBBLE.get(player.getUUID());
		net.minecraft.world.entity.Entity bubble = bubbleId == null ? null : player.level().getEntity(bubbleId);
		if (bubble == null || bubble.isRemoved()) {
			spawnRescueBubble(player, target);
		} else {
			bubble.setPos(hold.x, hold.y, hold.z);
		}
		if (player.tickCount % 2 == 0) {
			ServerLevel level = player.serverLevel();
			AbilityHelpers.line(level, AbilityHelpers.handPosition(player), hold, FINE_DUST, 2.5);
			level.sendParticles(SPARK, hold.x, hold.y, hold.z, 2, target.getBbWidth() * 0.4, target.getBbHeight() * 0.35,
					target.getBbWidth() * 0.4, 0.0);
		}
	}

	/**
	 * v0.14.22, explicit user request: a bubble of hard light round whatever the tether is carrying. It is a
	 * {@link HardLightConstructEntity} (shape BUBBLE, radius in its scale) that {@link #tickRescueHeld} keeps on the
	 * target; the renderer draws it on the target's own interpolated position so it never lags behind.
	 */
	private static void spawnRescueBubble(ServerPlayer player, LivingEntity target) {
		popRescueBubble(player);
		double hw = target.getBbWidth() * 0.5;
		double hh = target.getBbHeight() * 0.5;
		float radius = (float) (Math.sqrt(hw * hw + hh * hh) + 0.3);
		Vec3 mid = target.position().add(0, hh, 0);
		HardLightConstructEntity bubble = HardLightConstructEntity.create(player.serverLevel(),
				HardLightConstructEntity.Shape.BUBBLE, player.getUUID(), mid, radius, 0);
		bubble.setTargetId(target.getId());
		player.serverLevel().addFreshEntity(bubble);
		RESCUE_BUBBLE.put(player.getUUID(), bubble.getId());
	}

	/**
	 * v0.14.28, explicit user request: nothing fights back from inside the bubble -- a carried creeper's fuse winds back
	 * down instead of exploding and a carried skeleton (any mob) drops its target and lowers its bow. Re-applied every
	 * tick (the mob's own goals would re-acquire a target), and nothing is saved on the mob, so releasing it -- or the
	 * server stopping mid-carry -- leaves it exactly as it was.
	 */
	public static void pacifyCarried(LivingEntity target) {
		if (target instanceof net.minecraft.world.entity.monster.Creeper creeper) {
			creeper.setSwellDir(-1);
		}
		if (target instanceof net.minecraft.world.entity.Mob mob) {
			mob.setTarget(null);
			mob.setAggressive(false);
			mob.getNavigation().stop();
			if (mob.isUsingItem()) {
				mob.stopUsingItem();
			}
		}
	}

	/** v0.14.22 (gametests): the entity id the player is carrying with Rescue Tether, or -1. */
	public static int rescueHeldId(ServerPlayer player) {
		return RESCUE_HELD.getOrDefault(player.getUUID(), -1);
	}

	/** v0.14.22 (gametests): the entity id of the bubble round the carried target, or -1. */
	public static int rescueBubbleId(ServerPlayer player) {
		return RESCUE_BUBBLE.getOrDefault(player.getUUID(), -1);
	}

	/** Ends the bubble with the renderer's 5-tick fade (its life runs out a few ticks from now). */
	private static void popRescueBubble(ServerPlayer player) {
		Integer id = RESCUE_BUBBLE.remove(player.getUUID());
		if (id == null) {
			return;
		}
		if (player.level().getEntity(id) instanceof HardLightConstructEntity bubble && !bubble.isRemoved()) {
			bubble.setTargetId(-1);
			bubble.setLife(bubble.tickCount + 5);
		}
	}

	/** Second C press while holding: launches the held target in the caster's look direction. */
	private static void throwRescueHeld(ServerPlayer player) {
		popRescueBubble(player);
		Integer id = RESCUE_HELD.remove(player.getUUID());
		if (id == null) {
			return;
		}
		net.minecraft.world.entity.Entity e = player.level().getEntity(id);
		if (e instanceof LivingEntity target && target.isAlive()) {
			target.setDeltaMovement(player.getLookAngle().scale(GreenLanternConfig.TETHER_THROW_SPEED).add(0, 0.3, 0));
			target.hurtMarked = true;
			target.hasImpulse = true;
			Vec3 mid = target.position().add(0, target.getBbHeight() * 0.5, 0);
			player.serverLevel().sendParticles(SPARK, mid.x, mid.y, mid.z, 14, 0.3, 0.3, 0.3, 0.08);
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
		popRescueBubble(player);
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

	/**
	 * v0.13.21: the pieces shimmer (enchantment glint) so they read as hard light, and are unbreakable -- a piece that
	 * wore out used to count as "dropped" and end the whole kit mid-dig.
	 */
	private static net.minecraft.world.item.ItemStack toolKitPiece(net.minecraft.world.item.Item base, String nameKey) {
		net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(base);
		stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.translatable(nameKey)
				.withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GREEN).withItalic(false)));
		net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
		tag.putBoolean(TOOL_KIT_TAG, true);
		stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
				net.minecraft.world.item.component.CustomData.of(tag));
		stack.set(net.minecraft.core.component.DataComponents.UNBREAKABLE,
				new net.minecraft.world.item.component.Unbreakable(false));
		stack.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	/** Public so {@code ItemEntityMixin} can recognise (and discard) one on the ground -- see its own
	 *  javadoc for why a tool kit piece must never actually land in the world. */
	public static boolean isToolKitPiece(net.minecraft.world.item.ItemStack stack) {
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

	// ---------------- Battering Ram (v0.13.21: a travelling ram head) ----------------

	/** One Battering Ram head in flight. */
	private static final class RamStrike {
		final ServerLevel level;
		final Vec3 dir;
		Vec3 pos;
		double travelled;

		RamStrike(ServerLevel level, Vec3 pos, Vec3 dir) {
			this.level = level;
			this.pos = pos;
			this.dir = dir;
		}
	}

	/**
	 * v0.13.21: the ram is now a visible hard-light ram head that shoots out from the ring hand along your aim at
	 * {@link GreenLanternConfig#RAM_SPEED_PER_TICK} blocks/tick, out to the same {@link GreenLanternConfig#RAM_DISTANCE},
	 * smashing the first creature it meets (same damage / knockback as before) or bursting against the first solid
	 * block -- flinging a door there open. It used to be an invisible instant hit-scan that struck a target 16 blocks
	 * away the moment you pressed the key, while the lunge itself carried you only a few blocks. The small lunge stays.
	 */
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
		RamStrike ram = new RamStrike(player.serverLevel(), player.getEyePosition().add(0, -0.35, 0), player.getLookAngle());
		RAM_ACTIVE.put(player.getUUID(), ram);
		AbilityHelpers.sound(player, SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.6f);
		if (stepRam(player, ram)) {
			RAM_ACTIVE.remove(player.getUUID());
		}
	}

	/** Advances one ram head a tick. Returns true once it has hit something or run out of distance. */
	private static boolean stepRam(ServerPlayer owner, RamStrike ram) {
		ServerLevel level = ram.level;
		double step = Math.min(GreenLanternConfig.RAM_SPEED_PER_TICK, GreenLanternConfig.RAM_DISTANCE - ram.travelled);
		Vec3 next = ram.pos.add(ram.dir.scale(step));
		BlockHitResult blockHit = level.clip(new ClipContext(ram.pos, next, ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, owner));
		Vec3 end = blockHit.getType() == HitResult.Type.MISS ? next : blockHit.getLocation();

		LivingEntity hit = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
				new AABB(ram.pos, end).inflate(GreenLanternConfig.RAM_HIT_RADIUS))) {
			if (e == owner || !e.isAlive() || e instanceof ArmorStand || !rammable(owner, e)) {
				continue;
			}
			var clip = e.getBoundingBox().inflate(GreenLanternConfig.RAM_HIT_RADIUS * 0.5).clip(ram.pos, end);
			boolean inside = e.getBoundingBox().inflate(GreenLanternConfig.RAM_HIT_RADIUS * 0.5).contains(ram.pos);
			if (clip.isEmpty() && !inside) {
				continue;
			}
			double d = inside ? 0.0 : clip.get().distanceToSqr(ram.pos);
			if (d < best) {
				best = d;
				hit = e;
			}
		}
		if (hit != null) {
			Vec3 impact = hit.position().add(0, hit.getBbHeight() * 0.5, 0);
			wireCube(level, impact.subtract(ram.dir.scale(0.6)), 0.45, GREEN_DUST);
			AbilityHelpers.hurt(owner, hit, GreenLanternConfig.RAM_DAMAGE * GreenLanternOath.multiplier(owner));
			AbilityHelpers.knockbackFrom(hit, impact.subtract(ram.dir.scale(2.0)), 2.0);
			level.sendParticles(SPARK, impact.x, impact.y, impact.z, 30, 0.4, 0.4, 0.4, 0.15);
			level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.IRON_GOLEM_ATTACK, SoundSource.PLAYERS, 1.0f, 0.8f);
			return true;
		}
		wireCube(level, end, 0.45, GREEN_DUST);
		AbilityHelpers.line(level, ram.pos, end, SPARK, 2.0);
		if (blockHit.getType() != HitResult.Type.MISS) {
			BlockPos pos = blockHit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof DoorBlock door) {
				door.setOpen(owner, level, state, pos, true);
			}
			level.sendParticles(SPARK, end.x, end.y, end.z, 24, 0.3, 0.3, 0.3, 0.12);
			level.playSound(null, end.x, end.y, end.z, SoundEvents.IRON_GOLEM_ATTACK, SoundSource.PLAYERS, 0.8f, 1.0f);
			return true;
		}
		ram.pos = end;
		ram.travelled += step;
		if (ram.travelled >= GreenLanternConfig.RAM_DISTANCE - 1.0e-3) {
			level.sendParticles(SPARK, end.x, end.y, end.z, 16, 0.3, 0.3, 0.3, 0.05);
			return true;
		}
		return false;
	}

	/** The ram passes through players it could never hurt (PvP off, squadmates, creative/spectator) instead of stopping on them. */
	private static boolean rammable(ServerPlayer owner, LivingEntity e) {
		// v0.14.20: the shared rule 1 (aimed) -- also passes through the owner's pets
		return com.projecthero.mod.combat.HeroTargets.canHarm(owner, e);
	}

	private static void tickRams(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, RamStrike>> it = RAM_ACTIVE.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, RamStrike> entry = it.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
			if (owner == null || owner.level() != entry.getValue().level || stepRam(owner, entry.getValue())) {
				it.remove();
			}
		}
	}

	/** The twelve edges of an axis-aligned cube of half-size {@code half} around {@code c}, in particles. */
	private static void wireCube(ServerLevel level, Vec3 c, double half, ParticleOptions particle) {
		for (int i = 0; i < 4; i++) {
			double sx = (i & 1) == 0 ? -half : half;
			double sz = (i & 2) == 0 ? -half : half;
			AbilityHelpers.line(level, c.add(sx, -half, sz), c.add(sx, half, sz), particle, 3.0);
			AbilityHelpers.line(level, c.add(-half, sx, sz), c.add(half, sx, sz), particle, 3.0);
			AbilityHelpers.line(level, c.add(sx, sz, -half), c.add(sx, sz, half), particle, 3.0);
		}
	}

	// ---------------- block helpers ----------------

	// v0.11.5: green, not light blue -- "all the constructs need green models" so they read as the
	// Green Lantern's own hard light rather than a generic glass block. v0.13.21: the dedicated hard-light block.
	private static BlockState lightBlockState() {
		return GreenLanternBlocks.HARD_LIGHT.defaultBlockState();
	}

	private static BlockState carryPlatformBlockState() {
		return GreenLanternBlocks.HARD_LIGHT.defaultBlockState().setValue(HardLightBlock.BRIGHT, true);
	}

	/**
	 * Queues {@code pos} as one of the construct's cells if it may be built there. v0.13.21: never inside ANY player,
	 * the caster included (the caster used to be exempt, so a Wall aimed at your own feet could entomb you).
	 */
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
		if (playerIn(c.level, pos)) {
			return; // never suffocate a player
		}
		c.cells.add(new Construct.Cell(pos.immutable(), current, state));
	}

	private static boolean playerIn(ServerLevel level, BlockPos pos) {
		return !level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, new AABB(pos),
				p -> !p.isSpectator()).isEmpty();
	}

	/** v0.13.21: puts the first batch of cells in the world now; {@link #tickBuild} places the rest. */
	private static void startBuild(Construct c) {
		placeCells(c, c.buildPerTick <= 0 ? c.cells.size() : c.buildPerTick, true);
	}

	private static void tickBuild(Construct c) {
		if (c.built < c.cells.size()) {
			placeCells(c, c.buildPerTick <= 0 ? c.cells.size() : c.buildPerTick, true);
		}
	}

	/**
	 * Places the next {@code count} cells. A cell whose spot changed since the construct was shaped (something moved in,
	 * a player stepped into it mid-build) is skipped rather than overwritten. Every placed cell is indexed in
	 * {@link #CELL_INDEX} before it goes into the world, so its orphan check always finds it.
	 */
	private static void placeCells(Construct c, int count, boolean sparkle) {
		int end = Math.min(c.cells.size(), c.built + count);
		Map<BlockPos, Construct> index = CELL_INDEX.computeIfAbsent(c.level.dimension(), k -> new ConcurrentHashMap<>());
		for (int i = c.built; i < end; i++) {
			Construct.Cell cell = c.cells.get(i);
			BlockPos pos = cell.pos();
			if (!c.level.hasChunkAt(pos)) {
				continue;
			}
			BlockState now = c.level.getBlockState(pos);
			if (now != cell.previous() || playerIn(c.level, pos)) {
				continue;
			}
			index.put(pos, c);
			c.level.setBlock(pos, cell.placed(), Block.UPDATE_ALL);
			if (sparkle) {
				c.level.sendParticles(SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.01);
			}
		}
		c.built = end;
	}

	private static void restore(Construct c) {
		Map<BlockPos, Construct> index = CELL_INDEX.get(c.level.dimension());
		for (Construct.Cell cell : c.cells) {
			if (index != null) {
				index.remove(cell.pos(), c);
			}
			if (c.level.hasChunkAt(cell.pos()) && c.level.getBlockState(cell.pos()) == cell.placed()) {
				c.level.setBlock(cell.pos(), cell.previous(), Block.UPDATE_ALL);
			}
		}
	}

	// ---------------- Sentry Turret display entities (v0.13.21) ----------------

	/**
	 * v0.13.21: the turret is finally something you can see -- a spinning hard-light core balanced on one corner,
	 * floating on a thin stalk of light (two vanilla block-display entities showing {@link HardLightBlock}, drawn
	 * full-bright) instead of an intermittent particle rod. Built from NBT because the display setters are private;
	 * tagged so an orphan left behind by a crash is removed on load (see {@link #initialize}).
	 */
	private static void spawnTurretDisplays(Construct c) {
		double h = GreenLanternConfig.TURRET_HOVER_HEIGHT;
		Quaternionf corner = new Quaternionf().rotateX((float) Math.toRadians(45.0)).rotateZ((float) Math.toRadians(35.264));
		spawnDisplay(c, c.anchor.add(0, h, 0), carryPlatformBlockState(), new Vector3f(0.42f, 0.42f, 0.42f), corner);
		spawnDisplay(c, c.anchor.add(0, (h - 0.2) / 2.0, 0), lightBlockState(),
				new Vector3f(0.07f, (float) (h - 0.2), 0.07f), new Quaternionf());
	}

	private static void spawnDisplay(Construct c, Vec3 pos, BlockState state, Vector3f scale, Quaternionf rotation) {
		CompoundTag tag = new CompoundTag();
		tag.putString("id", "minecraft:block_display");
		tag.put("block_state", NbtUtils.writeBlockState(state));
		Vector3f offset = rotation.transform(new Vector3f(-scale.x / 2f, -scale.y / 2f, -scale.z / 2f), new Vector3f());
		CompoundTag transform = new CompoundTag();
		transform.put("translation", floats(offset.x, offset.y, offset.z));
		transform.put("left_rotation", floats(rotation.x, rotation.y, rotation.z, rotation.w));
		transform.put("scale", floats(scale.x, scale.y, scale.z));
		transform.put("right_rotation", floats(0f, 0f, 0f, 1f));
		tag.put("transformation", transform);
		CompoundTag brightness = new CompoundTag();
		brightness.putInt("sky", 15);
		brightness.putInt("block", 15);
		tag.put("brightness", brightness);
		tag.putInt("teleport_duration", 2);
		ListTag tags = new ListTag();
		tags.add(StringTag.valueOf(DISPLAY_TAG));
		tag.put("Tags", tags);
		Entity display = EntityType.loadEntityRecursive(tag, c.level, e -> {
			e.moveTo(pos.x, pos.y, pos.z, 0f, 0f);
			return e;
		});
		if (display == null) {
			return;
		}
		LIVE_DISPLAYS.add(display.getUUID());
		if (c.level.addFreshEntity(display)) {
			c.displayEntities.add(display.getUUID());
		} else {
			LIVE_DISPLAYS.remove(display.getUUID());
		}
	}

	private static ListTag floats(float... values) {
		ListTag list = new ListTag();
		for (float v : values) {
			list.add(FloatTag.valueOf(v));
		}
		return list;
	}

	private static void removeDisplays(Construct c) {
		for (UUID id : c.displayEntities) {
			LIVE_DISPLAYS.remove(id);
			Entity e = c.level.getEntity(id);
			if (e != null) {
				e.discard();
			}
		}
		c.displayEntities.clear();
	}

	// ---------------- damage / dismissal ----------------

	/** A punch on one of the construct's cells: chip its HP (Wall/Cage) or dismiss it outright. */
	private static void punch(Construct c, BlockPos pos, ServerPlayer attacker) {
		if (c.type.maxHp() > 0f) {
			c.level.sendParticles(SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.35, 0.35, 0.35, 0.05);
			c.level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 1.0f, 0.8f + c.hp / c.type.maxHp() * 0.6f);
			damage(c, 20f, attacker);
		} else {
			dismissOne(c, true);
		}
	}

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
		end(c);
		if (broken) {
			c.level.sendParticles(ParticleTypes.END_ROD, c.anchor.x, c.anchor.y + 0.5, c.anchor.z, 20, 0.6, 0.6, 0.6, 0.05);
			c.level.playSound(null, c.anchor.x, c.anchor.y, c.anchor.z, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.6f, 0.8f);
			ServerPlayer owner = c.level.getServer() != null ? c.level.getServer().getPlayerList().getPlayer(c.owner) : null;
			if (owner != null) {
				applyEndCooldown(owner, c);
			}
		}
		syncHandConstructs(c.level.getServer(), c.owner);
	}

	/** Everything a construct owns goes away: its blocks, displays, seats, granted tools and buffs -- in a scatter of light. */
	private static void end(Construct c) {
		emitDissolve(c);
		restore(c);
		removeDisplays(c);
		if (c.type.kind() == ConstructType.Kind.MELEE_BUFF) {
			removeMeleeBuff(c);
		}
		if (c.type.kind() == ConstructType.Kind.TOOL_KIT) {
			removeToolKit(c);
		}
		if (c.type == ConstructType.CARRY_PLATFORM) {
			removeCarrySeats(c);
		}
		if (c.type.kind() == ConstructType.Kind.RING_LIGHT) {
			MinecraftServer server = c.level.getServer();
			GreenLanternRingLight.stop(c.owner, server, server == null ? null : server.getPlayerList().getPlayer(c.owner));
		}
	}

	/** v0.13.21: every construct fades out in green sparks as it ends (it used to vanish silently unless punched down). */
	private static void emitDissolve(Construct c) {
		int placed = Math.min(c.built, c.cells.size());
		if (placed > 0) {
			int step = Math.max(1, placed / 24);
			for (int i = 0; i < placed; i += step) {
				BlockPos p = c.cells.get(i).pos();
				c.level.sendParticles(SPARK, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.02);
			}
		} else if (c.type.kind() == ConstructType.Kind.TURRET) {
			double h = GreenLanternConfig.TURRET_HOVER_HEIGHT;
			c.level.sendParticles(SPARK, c.anchor.x, c.anchor.y + h, c.anchor.z, 16, 0.25, 0.25, 0.25, 0.04);
		} else if (c.type.kind() == ConstructType.Kind.BUBBLE) {
			emitBubbleShell(c, 48);
		}
		c.level.playSound(null, c.anchor.x, c.anchor.y, c.anchor.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS,
				0.6f, 1.4f);
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
		RAM_ACTIVE.remove(owner);
		if (list == null) {
			return;
		}
		MinecraftServer server = null;
		for (Construct c : list) {
			end(c);
			server = c.level.getServer();
		}
		syncHandConstructs(server, owner);
	}

	/** N (v0.14.3; was Shift+C) -- dismiss every owned construct except the suit (the suit is never tracked here anyway). */
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
		if (!PENDING_DISCARD.isEmpty()) {
			synchronized (PENDING_DISCARD) {
				for (Entity e : PENDING_DISCARD) {
					if (!e.isRemoved() && !LIVE_DISPLAYS.contains(e.getUUID())) {
						e.discard();
					}
				}
				PENDING_DISCARD.clear();
			}
		}
		tickRams(server);
		long now = server.overworld().getGameTime();
		if (now % 20 == 0) {
			com.projecthero.mod.greenlantern.GreenLanternConstructAttacks.pruneLive(server);
		}
		for (Iterator<Map.Entry<UUID, List<Construct>>> ownerIt = BY_OWNER.entrySet().iterator(); ownerIt.hasNext();) {
			Map.Entry<UUID, List<Construct>> entry = ownerIt.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
			if (owner == null) {
				// Logged out -- restore blocks now rather than waiting; the entry itself is dropped.
				for (Construct c : entry.getValue()) {
					restore(c);
					removeDisplays(c);
					if (c.type == ConstructType.CARRY_PLATFORM) {
						removeCarrySeats(c);
					}
					if (c.type.kind() == ConstructType.Kind.RING_LIGHT) {
						GreenLanternRingLight.stop(c.owner, server, null);
					}
				}
				ownerIt.remove();
				continue;
			}
			boolean removedAny = false;
			Iterator<Construct> it = entry.getValue().iterator();
			while (it.hasNext()) {
				Construct c = it.next();
				if (c.expired(now) || !tickUpkeep(owner, c) || !tickKind(owner, c, now)) {
					end(c);
					applyEndCooldown(owner, c);
					it.remove();
					removedAny = true;
					continue;
				}
				tickBuild(c);
				tickExpiryWarning(c, now);
			}
			if (removedAny) {
				syncHandConstructs(owner);
			}
		}
	}

	/** v0.13.21: over a timed construct's last 3 seconds it flickers, with one soft chime as the warning starts. */
	private static void tickExpiryWarning(Construct c, long now) {
		if (c.expiresAt == 0L) {
			return;
		}
		long left = c.expiresAt - now;
		if (left > GreenLanternConfig.CONSTRUCT_EXPIRY_WARN_TICKS || left <= 0) {
			return;
		}
		if (!c.expiryWarned) {
			c.expiryWarned = true;
			c.level.playSound(null, c.anchor.x, c.anchor.y, c.anchor.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS,
					0.35f, 1.8f);
		}
		if (now % 5 != 0) {
			return;
		}
		int placed = Math.min(c.built, c.cells.size());
		if (placed > 0) {
			for (int i = 0; i < Math.min(6, placed); i++) {
				BlockPos p = c.cells.get(c.level.random.nextInt(placed)).pos();
				c.level.sendParticles(SPARK, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 1, 0.35, 0.35, 0.35, 0.0);
			}
		} else {
			c.level.sendParticles(SPARK, c.anchor.x, c.anchor.y + 1.0, c.anchor.z, 3, 0.4, 0.5, 0.4, 0.0);
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
				return tickCage(c);
			}
			case BUBBLE -> tickBubble(owner, c, now);
			case DRILL -> tickDrill(owner, c);
			case MELEE_BUFF -> tickEnergyBlade(owner, c);
			case RING_LIGHT -> {
				GreenLanternRingLight.tick(owner);
			}
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

	/**
	 * v0.13.21: the cage ends the moment its target dies (not 20 ticks later when the corpse is removed), and holds the
	 * target in its hollow -- anything that ends up outside it (an enderman's teleport, a knockback that clipped a
	 * corner, an ender pearl) is pulled straight back in, unless it got clean away (12+ blocks), which ends the cage.
	 */
	private static boolean tickCage(Construct c) {
		Entity e = c.level.getEntity(c.cagedEntityId);
		if (!(e instanceof LivingEntity target) || !target.isAlive() || c.cageCenter == null) {
			return false;
		}
		double half = c.cageHalfWidth + 0.05;
		boolean outside = Math.abs(target.getX() - c.cageCenter.x) > half || Math.abs(target.getZ() - c.cageCenter.z) > half
				|| target.getY() < c.cageCenter.y - 1.0;
		if (outside) {
			if (target.position().distanceTo(c.cageCenter) > 12.0) {
				return false;
			}
			target.teleportTo(c.cageCenter.x, c.cageCenter.y, c.cageCenter.z);
			target.setDeltaMovement(Vec3.ZERO);
			target.hurtMarked = true;
		}
		return true;
	}

	/**
	 * While toggled on, a green glow around the wielding hand/arm ("make the players arm glow green to
	 * show that its active", explicit user request, reiterated v0.11.9). v0.13.21: every client now draws the blade
	 * itself on the hand (see {@link ModAttachments#GREEN_LANTERN_HAND_CONSTRUCTS}), so the particle swirl is only an
	 * occasional glint off it rather than the whole effect.
	 */
	private static void tickEnergyBlade(ServerPlayer owner, Construct c) {
		if (!c.toggledOn || owner.tickCount % 4 != 0) {
			return;
		}
		Vec3 hand = AbilityHelpers.handPosition(owner);
		owner.serverLevel().sendParticles(SPARK, hand.x, hand.y, hand.z, 1, 0.15, 0.15, 0.15, 0.01);
	}

	/**
	 * v0.13.21: the turret's core spins, it only fires at targets it can actually see (it used to shoot through
	 * walls), and each shot is a proper green bolt of light with a flash at both ends instead of a white end-rod line.
	 */
	private static void tickTurret(ServerPlayer owner, Construct c, long now) {
		Vec3 muzzle = c.anchor.add(0, GreenLanternConfig.TURRET_HOVER_HEIGHT, 0);
		if (!c.displayEntities.isEmpty()) {
			Entity core = c.level.getEntity(c.displayEntities.get(0));
			if (core != null) {
				core.setYRot(Mth.wrapDegrees(core.getYRot() + 9f));
			}
		}
		if (now < c.nextFireAt) {
			return;
		}
		LivingEntity target = null;
		double best = GreenLanternConfig.TURRET_TARGET_RADIUS * GreenLanternConfig.TURRET_TARGET_RADIUS;
		for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class,
				new AABB(muzzle, muzzle).inflate(GreenLanternConfig.TURRET_TARGET_RADIUS))) {
			if (!isHostileTarget(owner, e)) {
				continue;
			}
			double d = e.position().distanceToSqr(muzzle);
			if (d < best && canSee(c.level, muzzle, e, owner)) {
				best = d;
				target = e;
			}
		}
		if (target == null) {
			return;
		}
		c.nextFireAt = now + GreenLanternConfig.TURRET_FIRE_INTERVAL_TICKS;
		Vec3 aim = target.position().add(0, target.getBbHeight() * 0.5, 0);
		AbilityHelpers.line(c.level, muzzle, aim, GREEN_DUST, 3.0);
		c.level.sendParticles(SPARK, muzzle.x, muzzle.y, muzzle.z, 4, 0.12, 0.12, 0.12, 0.03);
		c.level.sendParticles(SPARK, aim.x, aim.y, aim.z, 6, 0.2, 0.2, 0.2, 0.05);
		AbilityHelpers.hurt(owner, target, GreenLanternConfig.TURRET_DAMAGE * GreenLanternOath.multiplier(owner));
		c.level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.35f, 2.0f);
	}

	public static boolean canSee(ServerLevel level, Vec3 from, LivingEntity e, ServerPlayer owner) {
		Vec3 to = e.position().add(0, e.getBbHeight() * 0.6, 0);
		return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner))
				.getType() == HitResult.Type.MISS;
	}

	public static boolean isHostileTarget(ServerPlayer owner, LivingEntity e) {
		// v0.14.20: the shared rule 2 (com.projecthero.mod.combat.HeroTargets#isHostile) -- turrets, homing, chains
		return com.projecthero.mod.combat.HeroTargets.isHostile(owner, e);
	}

	private static boolean isSquadmate(ServerPlayer owner, net.minecraft.world.entity.player.Player other) {
		return com.projecthero.mod.squad.Squads.areAllies(owner, other); // v0.14.4: a rampaging Hulk is nobody's squadmate
	}

	/**
	 * v0.13.21: the bubble is centred on where the caster stood (see {@link #anchorFor}), shares its air with squadmates
	 * inside it too, and shows its actual boundary -- a shimmering shell of light every half-second.
	 */
	private static void tickBubble(ServerPlayer owner, Construct c, long now) {
		double r2 = GreenLanternConfig.BUBBLE_RADIUS * GreenLanternConfig.BUBBLE_RADIUS;
		for (ServerPlayer p : c.level.getPlayers(p -> p.position().distanceToSqr(c.anchor) <= r2)) {
			if (p != owner && !isSquadmate(owner, p)) {
				continue;
			}
			p.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 30, 0, false, false, false));
			if (p.getAirSupply() < p.getMaxAirSupply()) {
				p.setAirSupply(p.getMaxAirSupply());
			}
		}
		if (now % 10 == 0) {
			emitBubbleShell(c, 40);
		}
	}

	/** Evenly spread points on the bubble's sphere (a Fibonacci lattice), rotated a little each call so it shimmers. */
	private static void emitBubbleShell(Construct c, int points) {
		double r = GreenLanternConfig.BUBBLE_RADIUS;
		double golden = Math.PI * (3.0 - Math.sqrt(5.0));
		double spin = (c.level.getGameTime() % 360) * 0.05;
		Vec3 centre = c.anchor.add(0, 1.0, 0);
		for (int i = 0; i < points; i++) {
			double y = 1.0 - (i + 0.5) * 2.0 / points;
			double ring = Math.sqrt(1.0 - y * y);
			double a = i * golden + spin;
			c.level.sendParticles(FINE_DUST, centre.x + Math.cos(a) * ring * r, centre.y + y * r, centre.z + Math.sin(a) * ring * r,
					1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	/**
	 * v0.11.7: only mines while {@link Construct#toggledOn} -- a second C press on the already-active
	 * drill toggles it (see {@link #toggleConstruct}). The per-block charge is gone; mining now just
	 * rides on the flat {@link GreenLanternConfig#DRILL_UPKEEP_PER_SEC} upkeep (see {@link #tickUpkeep})
	 * that already only drains while on. While on, green particles spiral around the mining hand
	 * ("the player should have green particles rotating around their hand to simulate the drill").
	 * v0.13.21: the spinning drill bit itself is now drawn on the hand by every client; the swirl is lighter, and the
	 * drill whirs as it bites.
	 */
	private static void tickDrill(ServerPlayer owner, Construct c) {
		if (!c.toggledOn) {
			return;
		}
		if (owner.tickCount % 2 == 0) {
			Vec3 hand = AbilityHelpers.handPosition(owner);
			double angle = (owner.tickCount % 20) * (Math.PI * 2 / 20.0);
			owner.serverLevel().sendParticles(SPARK,
					hand.x + Math.cos(angle) * 0.25, hand.y + Math.sin(angle) * 0.25, hand.z, 1, 0, 0, 0, 0.0);
		}
		if (owner.tickCount % GreenLanternConfig.DRILL_INTERVAL_TICKS != 0) {
			return;
		}
		BlockHitResult hit = AbilityHelpers.raycastBlock(owner, GreenLanternConfig.DRILL_REACH);
		if (hit.getType() == HitResult.Type.MISS) {
			return;
		}
		// v0.12.25: a 2x2 path -- the aimed block plus its nearest neighbour along each in-plane axis
		// (the side of the block the crosshair is on), across the face that was hit.
		BlockPos pos = hit.getBlockPos();
		Direction.Axis face = hit.getDirection().getAxis();
		Vec3 rel = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
		int dx = face == Direction.Axis.X ? 0 : (rel.x >= 0.5 ? 1 : -1);
		int dy = face == Direction.Axis.Y ? 0 : (rel.y >= 0.5 ? 1 : -1);
		int dz = face == Direction.Axis.Z ? 0 : (rel.z >= 0.5 ? 1 : -1);
		java.util.List<BlockPos> path = new java.util.ArrayList<>(4);
		path.add(pos);
		if (dx != 0) {
			path.add(pos.offset(dx, 0, 0));
		}
		if (dy != 0) {
			path.add(pos.offset(0, dy, 0));
		}
		if (dz != 0) {
			path.add(pos.offset(0, 0, dz));
		}
		if (dx != 0 && dy != 0) {
			path.add(pos.offset(dx, dy, 0));
		}
		if (dx != 0 && dz != 0) {
			path.add(pos.offset(dx, 0, dz));
		}
		if (dy != 0 && dz != 0) {
			path.add(pos.offset(0, dy, dz));
		}
		boolean mined = false;
		for (BlockPos p : path) {
			BlockState state = owner.level().getBlockState(p);
			if (state.isAir() || state.getDestroySpeed(owner.level(), p) < 0) {
				continue;
			}
			mined |= owner.gameMode.destroyBlock(p);
		}
		if (mined) {
			Vec3 at = hit.getLocation();
			owner.serverLevel().sendParticles(SPARK, at.x, at.y, at.z, 6, 0.3, 0.3, 0.3, 0.06);
			owner.serverLevel().playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.4f, 2.0f);
		}
	}
}
