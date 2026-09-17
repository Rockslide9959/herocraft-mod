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
import com.projecthero.mod.greenlantern.data.GreenLanternState;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.PowerToggles;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The reusable hard-light construct system: owner-tracked, TTL-restoring, capacity-enforced. Every
 * one of the thirteen named constructs is a {@link Construct} instance dispatched by
 * {@link ConstructType.Kind} rather than its own class -- most are backed by real temporary blocks
 * (cloned from {@code ConjuredStructures}' TTL/restore shape), a few (turret, tether, drill, energy
 * blade, atmosphere bubble, the Hard-Light Tool Kit) are "marker" constructs with no blocks, ticked
 * purely in Java.
 *
 * <p>Global rules enforced here: max {@link GreenLanternConfig#CONSTRUCT_MAX_SLOTS} weighted slots
 * (see {@link ConstructType#slotWeight()}), 24-block placement range, never overwrites
 * bedrock/portals/containers/unbreakable blocks, never drops items, vanishes on owner
 * death/dimension-change/logout/hard depletion (see {@code clearFor}), and a turret/cage/tether never
 * targets/affects the owner, a squadmate, or a tamed mob.
 */
public final class GreenLanternConstructs {
	private static final Map<UUID, List<Construct>> BY_OWNER = new ConcurrentHashMap<>();
	private static final net.minecraft.resources.ResourceLocation BLADE_DAMAGE =
			com.projecthero.mod.ProjectHeroMod.id("green_lantern_energy_blade");
	private static final net.minecraft.resources.ResourceLocation BLADE_REACH =
			com.projecthero.mod.ProjectHeroMod.id("green_lantern_energy_blade_reach");

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
	}

	public static void clearSessionState() {
		BY_OWNER.clear();
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

	// ---------------- deploy ----------------

	public static void deploy(ServerPlayer player, ConstructType type) {
		GreenLanternState s = GreenLantern.state(player);
		if (!type.unlockedFor(s)) {
			GreenLanternEnergy.feedback(player, "message.projecthero.ability.mastery_locked");
			return;
		}
		if (type == ConstructType.BATTERING_RAM) {
			batteringRam(player);
			return;
		}
		// Only one Tool Kit at a time -- countToolKitPieces()/tickKind's TOOL_KIT case count pieces
		// per-PLAYER, not per-construct-instance, so two live kits sharing one pool of tagged tools
		// would let a player drop pieces from one kit without either one ever ending.
		if (type == ConstructType.HARD_LIGHT_TOOLS && hasLiveToolKit(player.getUUID())) {
			GreenLanternEnergy.feedback(player, "message.projecthero.green_lantern.tool_kit_already_active");
			return;
		}
		String cooldownId = cooldownIdFor(type);
		if (cooldownId != null && !GreenLantern.abilityReady(player, cooldownId)) {
			GreenLanternEnergy.feedback(player, "message.projecthero.ability.cooldown_simple");
			return;
		}
		if (activeWeight(player.getUUID()) + type.slotWeight() > GreenLanternConfig.CONSTRUCT_MAX_SLOTS) {
			GreenLanternEnergy.feedback(player, "message.projecthero.green_lantern.construct_slots_full");
			return;
		}
		float cost = totalCost(type, player);
		if (!GreenLanternEnergy.spend(player, cost)) {
			GreenLanternEnergy.feedback(player, "message.projecthero.ability.low_charge");
			return;
		}
		com.projecthero.mod.greenlantern.GreenLanternBattery.onAbilityUsed(player);

		ServerLevel level = player.serverLevel();
		Vec3 anchor = placementPoint(player);
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
			case TETHER -> spawnTether(player, c);
			case DRILL -> {} // marker only
			case TOOL_KIT -> spawnToolKit(player, c);
			default -> {}
		}
		boolean usesBlocks = type.kind() == ConstructType.Kind.WALL || type.kind() == ConstructType.Kind.PLATFORM_BLOCKS
				|| type.kind() == ConstructType.Kind.BRIDGE_BLOCKS || type.kind() == ConstructType.Kind.RAMP_BLOCKS
				|| type.kind() == ConstructType.Kind.LIGHT_BLOCKS;
		boolean placementFailed = (usesBlocks && c.cells.isEmpty())
				|| (type.kind() == ConstructType.Kind.CAGE && c.cagedEntityId < 0)
				|| (type.kind() == ConstructType.Kind.TETHER && c.tetherTargetId < 0)
				|| (type.kind() == ConstructType.Kind.TOOL_KIT && !c.toolKitGranted);
		if (placementFailed) {
			// placement failed entirely (e.g. every target cell was protected/occupied, or no target found)
			GreenLanternEnergy.refund(player, cost);
			GreenLanternEnergy.feedback(player, type.kind() == ConstructType.Kind.TOOL_KIT
					? "message.projecthero.green_lantern.tool_kit_no_room" : "message.projecthero.ability.invalid_target");
			return;
		}
		BY_OWNER.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(c);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_AMBIENT,
				SoundSource.PLAYERS, 0.5f, 1.4f);
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

	private static float totalCost(ConstructType type, ServerPlayer player) {
		float base = type.initialCost();
		if (type == ConstructType.BRIDGE) {
			base += GreenLanternConfig.BRIDGE_COST_PER_SEGMENT * bridgeLength(player);
		} else if (type == ConstructType.STAIR_RAMP) {
			base += GreenLanternConfig.RAMP_COST_PER_SEGMENT * GreenLanternConfig.RAMP_MAX_SEGMENTS;
		}
		return base;
	}

	private static Vec3 placementPoint(ServerPlayer player) {
		BlockHitResult hit = AbilityHelpers.raycastBlock(player, GreenLanternConfig.CONSTRUCT_PLACE_RANGE);
		if (hit.getType() != HitResult.Type.MISS) {
			return hit.getLocation();
		}
		return player.getEyePosition().add(player.getLookAngle().scale(GreenLanternConfig.CONSTRUCT_PLACE_RANGE));
	}

	// ---------------- per-kind spawn ----------------

	private static void spawnEnergyBlade(ServerPlayer player, Construct c) {
		PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, BLADE_DAMAGE,
				GreenLanternConfig.ENERGY_BLADE_DAMAGE - 1f, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(player, Attributes.ENTITY_INTERACTION_RANGE, BLADE_REACH,
				GreenLanternConfig.ENERGY_BLADE_REACH - 3.0, AttributeModifier.Operation.ADD_VALUE);
	}

	private static void spawnWall(ServerPlayer player, Construct c) {
		Direction facing = player.getDirection();
		Direction side = facing.getClockWise();
		BlockPos center = BlockPos.containing(c.anchor);
		for (int w = -2; w <= 2; w++) {
			for (int h = 0; h < 3; h++) {
				add(c, center.relative(side, w).above(h), lightBlockState());
			}
		}
		place(c);
	}

	private static void spawnPlatform(ServerPlayer player, Construct c, boolean carry) {
		BlockPos center = BlockPos.containing(c.anchor).below();
		int halfW = carry ? 1 : 1;
		int halfL = carry ? 2 : 1;
		Direction facing = player.getDirection();
		Direction side = facing.getClockWise();
		for (int f = -halfL; f <= halfL; f++) {
			for (int w = -halfW; w <= halfW; w++) {
				add(c, center.relative(facing, f).relative(side, w), lightBlockState());
			}
		}
		place(c);
	}

	private static int bridgeLength(ServerPlayer player) {
		BlockHitResult hit = AbilityHelpers.raycastBlock(player, GreenLanternConfig.BRIDGE_MAX_LENGTH);
		if (hit.getType() == HitResult.Type.MISS) {
			return GreenLanternConfig.BRIDGE_MAX_LENGTH;
		}
		return Math.max(3, Math.min(GreenLanternConfig.BRIDGE_MAX_LENGTH, (int) player.position().distanceTo(hit.getLocation())));
	}

	private static void spawnBridge(ServerPlayer player, Construct c) {
		Direction facing = player.getDirection();
		BlockPos start = player.blockPosition().below().relative(facing);
		int length = bridgeLength(player);
		for (int f = 0; f < length; f++) {
			add(c, start.relative(facing, f), lightBlockState());
		}
		place(c);
	}

	private static void spawnRamp(ServerPlayer player, Construct c) {
		Direction facing = player.getDirection();
		BlockPos start = player.blockPosition().below().relative(facing);
		for (int f = 0; f < GreenLanternConfig.RAMP_MAX_SEGMENTS; f++) {
			add(c, start.relative(facing, f).above(f), lightBlockState());
		}
		place(c);
	}

	private static void spawnLight(ServerPlayer player, Construct c) {
		add(c, BlockPos.containing(c.anchor).above(), Blocks.SEA_LANTERN.defaultBlockState());
		place(c);
	}

	private static void spawnCage(ServerPlayer player, Construct c) {
		LivingEntity target = AbilityHelpers.raycastEntity(player, GreenLanternConfig.CAGE_RANGE);
		if (target == null || !isHostileTarget(player, target)) {
			return;
		}
		c.cagedEntityId = target.getId();
		BlockPos center = target.blockPosition();
		for (int x = -1; x <= 1; x++) {
			for (int y = 0; y <= 2; y++) {
				for (int z = -1; z <= 1; z++) {
					boolean edge = Math.abs(x) == 1 || Math.abs(z) == 1 || y == 0 || y == 2;
					boolean corner = Math.abs(x) == 1 && Math.abs(z) == 1;
					if (edge && !corner && !(x == 0 && z == 0)) {
						add(c, center.offset(x, y, z), lightBlockState());
					}
				}
			}
		}
		place(c);
	}

	private static void spawnTether(ServerPlayer player, Construct c) {
		LivingEntity target = AbilityHelpers.raycastEntity(player, GreenLanternConfig.TETHER_RANGE);
		if (target != null && isHostileTarget(player, target)) {
			c.tetherTargetId = target.getId();
		}
	}

	// ---------------- Hard-Light Tool Kit ----------------

	/** NBT marker key identifying a hard-light tool piece -- survives an anvil rename, unlike a name match. */
	private static final String TOOL_KIT_TAG = "projecthero_gl_tool_kit";
	private static final String[] TOOL_KIT_NAME_KEYS = {
			"message.projecthero.green_lantern.tool_kit.pickaxe",
			"message.projecthero.green_lantern.tool_kit.axe",
			"message.projecthero.green_lantern.tool_kit.shovel",
	};
	private static final net.minecraft.world.item.Item[] TOOL_KIT_BASES = {
			net.minecraft.world.item.Items.DIAMOND_PICKAXE,
			net.minecraft.world.item.Items.DIAMOND_AXE,
			net.minecraft.world.item.Items.DIAMOND_SHOVEL,
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
			GreenLanternEnergy.feedback(player, "message.projecthero.ability.cooldown_simple");
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
			AbilityHelpers.hurt(player, target, GreenLanternConfig.RAM_DAMAGE);
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

	private static BlockState lightBlockState() {
		return Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
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

	/** Returns false if upkeep could not be paid (construct should end). */
	private static boolean tickUpkeep(ServerPlayer owner, Construct c) {
		float upkeepPerSec = c.type.upkeepPerSec();
		if (GreenLantern.state(owner).hasMastery(GreenLanternState.MASTERY_IV)) {
			upkeepPerSec *= (1f - GreenLanternConfig.EFFICIENT_FOCUS_UPKEEP_DISCOUNT);
		}
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
			case TETHER -> {
				return tickTether(owner, c);
			}
			case DRILL -> tickDrill(owner, c);
			case TOOL_KIT -> {
				// Dropping any one of the three tools ends the whole kit (removeToolKit strips the rest).
				return countToolKitPieces(owner) >= 3;
			}
			default -> {}
		}
		return true;
	}

	private static void tickTurret(ServerPlayer owner, Construct c, long now) {
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
		AbilityHelpers.hurt(owner, target, GreenLanternConfig.TURRET_DAMAGE);
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

	private static boolean tickTether(ServerPlayer owner, Construct c) {
		if (c.tetherTargetId < 0) {
			return false;
		}
		net.minecraft.world.entity.Entity target = c.level.getEntity(c.tetherTargetId);
		if (target == null || !target.isAlive()) {
			return false;
		}
		if (target instanceof net.minecraft.world.entity.player.Player p
				&& (isSquadmate(owner, p) || owner.getServer() == null || !owner.getServer().isPvpAllowed())) {
			return false;
		}
		Vec3 toOwner = owner.position().subtract(target.position());
		double dist = toOwner.length();
		if (dist < 1.5) {
			return false;
		}
		double speedFactor = target instanceof net.minecraft.world.entity.vehicle.Boat
				|| target instanceof net.minecraft.world.entity.vehicle.AbstractMinecart ? 0.5 : 1.0;
		Vec3 pull = toOwner.normalize().scale(GreenLanternConfig.TETHER_PULL_SPEED_BPS / 20.0 * speedFactor);
		target.setDeltaMovement(target.getDeltaMovement().add(pull));
		target.hurtMarked = true;
		return true;
	}

	private static void tickDrill(ServerPlayer owner, Construct c) {
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
		if (!GreenLanternEnergy.canSpend(owner, GreenLanternConfig.DRILL_COST_PER_BLOCK)) {
			return;
		}
		if (owner.gameMode.destroyBlock(pos)) {
			GreenLanternEnergy.spend(owner, GreenLanternConfig.DRILL_COST_PER_BLOCK);
		}
	}
}
