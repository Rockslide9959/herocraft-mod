package com.projecthero.mod.speedforce;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import com.projecthero.mod.hero.power.p05.GeoTerrain;
import com.projecthero.mod.speedforce.data.SpeedForceMemories;
import com.projecthero.mod.speedforce.data.SpeedForceMemories.Place;
import com.projecthero.mod.speedforce.data.SpeedForceState;
import com.projecthero.mod.speedforce.network.SpeedBuildPayload;
import com.projecthero.mod.speedforce.network.SpeedForceFxPayload;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * v0.17.1 (user): the Speed Force Speedster's speed work -- no new keys.
 * <ul>
 *   <li><b>Speed Build</b>: blocks in the off hand, hold right-click while running in Speed Mode -- a bridge is laid under
 *       the feet along the run at the height it started (sneaking: a 2-high wall beside the run instead), using up the
 *       blocks. The client reports the held key ({@link SpeedBuildPayload}) and holds the runner up on the bridge level.</li>
 *   <li><b>Speed Smelt</b>: an empty main hand on a furnace / smoker / blast furnace in Speed Mode -- the hands blur and the
 *       whole input stack is cooked in {@link SpeedForceConfig#SMELT_TICKS}, burning the fuel it would have burned.</li>
 *   <li><b>Auto-Courier</b>: the Shift+N menu's Send runs the main-hand stack to a marked place -- into the chest marked
 *       there, else dropped on the spot -- loading that chunk just to deliver.</li>
 * </ul>
 * Block placing goes through {@link GeoTerrain#canEdit} (the terrain-damage setting, protection, claims, the border).
 */
public final class SpeedForceWorks {
	public static final String COURIER = "courier";

	/** Who is holding Speed Build, how, and the bridge's level. */
	private static final class Build {
		int mode;
		int floorY;
		Vec3 last;
	}

	/** A furnace being worked: where, and when it is done. */
	private record Smelt(ResourceKey<Level> dimension, BlockPos pos, long until) {
	}

	private static final Map<UUID, Build> BUILDS = new ConcurrentHashMap<>();
	private static final Map<UUID, Smelt> SMELTS = new ConcurrentHashMap<>();

	/** Client hook: is the local player Speed Building (so their own client doesn't place the off-hand block too)? */
	public static Predicate<Player> clientBuilding = p -> false;

	private SpeedForceWorks() {
	}

	static void initialize() {
		PayloadTypeRegistry.playC2S().register(SpeedBuildPayload.TYPE, SpeedBuildPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SpeedBuildPayload.TYPE,
				(payload, context) -> context.server().execute(() -> setBuild(context.player(), payload.mode())));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			BUILDS.remove(handler.getPlayer().getUUID());
			SMELTS.remove(handler.getPlayer().getUUID());
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!SpeedForceSpeedster.speedActive(player) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			// Speed Build: the off hand's blocks are the build's, not placed one at a time
			if (hand == InteractionHand.OFF_HAND && player.getOffhandItem().getItem() instanceof BlockItem
					&& (level.isClientSide() ? clientBuilding.test(player)
							: BUILDS.containsKey(player.getUUID()) && player instanceof ServerPlayer sp
									&& SpeedForceSpeedster.groundSpeedBps(sp) >= SpeedForceConfig.BUILD_MIN_BPS)) {
				return InteractionResult.FAIL;
			}
			// Speed Smelt: an empty main hand on a furnace
			if (hand == InteractionHand.MAIN_HAND && player.getMainHandItem().isEmpty() && !player.isShiftKeyDown()
					&& level.getBlockEntity(hit.getBlockPos()) instanceof AbstractFurnaceBlockEntity furnace) {
				if (!level.isClientSide() && player instanceof ServerPlayer sp) {
					startSmelt(sp, (ServerLevel) level, hit.getBlockPos(), furnace);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
	}

	static void clearSessionState() {
		BUILDS.clear();
		SMELTS.clear();
	}

	static void tick(ServerPlayer p) {
		tickBuild(p);
		tickSmelt(p);
	}

	// ---------------------------------------------------------------- Speed Build

	/** The client's held right-click (a {@link SpeedBuildPayload} mode). Public for the gametests. */
	public static void setBuild(ServerPlayer p, int mode) {
		if (mode != SpeedBuildPayload.BRIDGE && mode != SpeedBuildPayload.WALL) {
			BUILDS.remove(p.getUUID());
			return;
		}
		Build b = BUILDS.computeIfAbsent(p.getUUID(), k -> new Build());
		if (b.mode != mode) {
			b.mode = mode;
			b.floorY = Mth.floor(p.getY() + 0.01);
			b.last = null;
		}
	}

	public static boolean building(ServerPlayer p) {
		return BUILDS.containsKey(p.getUUID());
	}

	private static void tickBuild(ServerPlayer p) {
		Build b = BUILDS.get(p.getUUID());
		if (b == null) {
			return;
		}
		Vec3 now = p.position();
		Vec3 from = b.last == null ? now : b.last;
		b.last = now;
		if (!SpeedForceSpeedster.speedActive(p) || !(p.level() instanceof ServerLevel level)) {
			return;
		}
		buildAlong(p, level, b.mode, b.floorY, from, now);
	}

	/**
	 * Lays the off hand's blocks along the run from {@code from} to {@code to}: the bridge one block under {@code floorY}
	 * (the width of the runner), or the wall to the right of the run. Returns how many went down. Public for the gametests.
	 */
	public static int buildAlong(ServerPlayer p, ServerLevel level, int mode, int floorY, Vec3 from, Vec3 to) {
		ItemStack hand = p.getOffhandItem();
		if (!(hand.getItem() instanceof BlockItem item) || hand.isEmpty()) {
			return 0;
		}
		Vec3 d = new Vec3(to.x - from.x, 0, to.z - from.z);
		double len = d.length();
		if (len < SpeedForceConfig.BUILD_MIN_BPS / 20.0) {
			return 0; // not running
		}
		Vec3 dir = d.scale(1.0 / len);
		Vec3 right = new Vec3(-dir.z, 0, dir.x);
		BlockState state = item.getBlock().defaultBlockState();
		Set<BlockPos> spots = new java.util.LinkedHashSet<>();
		int steps = (int) Math.max(1, Math.min(128, Math.ceil(len / 0.5)));
		for (int i = 0; i <= steps; i++) {
			Vec3 q = from.lerp(to, i / (double) steps);
			if (mode == SpeedBuildPayload.WALL) {
				Vec3 side = q.add(right.scale(SpeedForceConfig.BUILD_WALL_SIDE));
				int y = Mth.floor(p.getY() + 0.01);
				for (int h = 0; h < SpeedForceConfig.BUILD_WALL_HEIGHT; h++) {
					spots.add(BlockPos.containing(side.x, y + h, side.z));
				}
			} else {
				for (double ox : new double[] { -0.3, 0.3 }) {
					for (double oz : new double[] { -0.3, 0.3 }) {
						spots.add(BlockPos.containing(q.x + ox, floorY - 1, q.z + oz));
					}
				}
			}
		}
		int placed = 0;
		boolean creative = p.getAbilities().instabuild;
		for (BlockPos pos : spots) {
			if (placed >= SpeedForceConfig.BUILD_MAX_PER_TICK || (!creative && p.getOffhandItem().isEmpty())) {
				break;
			}
			BlockState there = level.getBlockState(pos);
			if (!there.canBeReplaced() || !GeoTerrain.canEdit(p, level, pos) || !state.canSurvive(level, pos)
					|| !level.isUnobstructed(state, pos, CollisionContext.empty())) {
				continue;
			}
			level.setBlock(pos, state, net.minecraft.world.level.block.Block.UPDATE_ALL);
			placed++;
			if (!creative) {
				p.getOffhandItem().shrink(1);
			}
		}
		if (placed > 0) {
			SoundType sound = state.getSoundType();
			level.playSound(null, to.x, to.y, to.z, sound.getPlaceSound(), SoundSource.BLOCKS, 0.6f, sound.getPitch() * 1.2f);
			if (!creative && p.getOffhandItem().isEmpty()) {
				SpeedForceSpeedster.say(p, "message.projecthero.speed_force_speedster.build_out", ChatFormatting.GRAY);
			}
		}
		return placed;
	}

	// ---------------------------------------------------------------- Speed Smelt

	private static void startSmelt(ServerPlayer p, ServerLevel level, BlockPos pos, AbstractFurnaceBlockEntity furnace) {
		if (SMELTS.containsKey(p.getUUID())) {
			return;
		}
		if (furnace.getItem(0).isEmpty() || recipeFor(level, furnace).isEmpty()) {
			SpeedForceSpeedster.say(p, "message.projecthero.speed_force_speedster.smelt_nothing", ChatFormatting.GRAY);
			return;
		}
		if (!SpeedForceSpeedster.spendMeter(p, SpeedForceConfig.SMELT_COST)) {
			SpeedForceSpeedster.say(p, "message.projecthero.speed_force_speedster.need_meter", ChatFormatting.RED,
					Math.round(SpeedForceConfig.SMELT_COST));
			return;
		}
		SMELTS.put(p.getUUID(), new Smelt(level.dimension(), pos.immutable(), level.getGameTime() + SpeedForceConfig.SMELT_TICKS));
		SpeedForceSpeedster.setAnim(p, SpeedForceState.ANIM_PUNCHES); // the hands blur
		SpeedForceSpeedster.sound(p, SoundEvents.FURNACE_FIRE_CRACKLE, 1.0f, 1.8f);
	}

	private static void tickSmelt(ServerPlayer p) {
		Smelt job = SMELTS.get(p.getUUID());
		if (job == null) {
			return;
		}
		ServerLevel level = p.serverLevel();
		if (level.dimension() != job.dimension() || p.position().distanceToSqr(Vec3.atCenterOf(job.pos()))
				> SpeedForceConfig.SMELT_REACH * SpeedForceConfig.SMELT_REACH || !level.isLoaded(job.pos())) {
			SMELTS.remove(p.getUUID());
			return;
		}
		long now = level.getGameTime();
		Vec3 c = Vec3.atCenterOf(job.pos());
		if (now % 3 == 0) {
			level.sendParticles(ParticleTypes.FLAME, c.x, c.y + 0.2, c.z, 4, 0.3, 0.2, 0.3, 0.02);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 0.6, c.z, 3, 0.4, 0.3, 0.4, 0.1);
			level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1.1, p.getZ(), 2, 0.3, 0.2, 0.3, 0.02);
		}
		if (now % 8 == 0 && SpeedForceSpeedster.state(p).animId != SpeedForceState.ANIM_PUNCHES) {
			SpeedForceSpeedster.setAnim(p, SpeedForceState.ANIM_PUNCHES);
		}
		if (now < job.until()) {
			return;
		}
		SMELTS.remove(p.getUUID());
		if (level.getBlockEntity(job.pos()) instanceof AbstractFurnaceBlockEntity furnace) {
			int made = smeltAll(level, furnace);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.6f, 1.6f);
			level.sendParticles(ParticleTypes.LAVA, c.x, c.y + 0.5, c.z, 6, 0.3, 0.2, 0.3, 0.0);
			p.displayClientMessage(Component.translatable("message.projecthero.speed_force_speedster.smelt_done", made)
					.withStyle(ChatFormatting.GOLD), true);
		}
	}

	private static RecipeType<? extends AbstractCookingRecipe> typeOf(AbstractFurnaceBlockEntity f) {
		return f instanceof BlastFurnaceBlockEntity ? RecipeType.BLASTING : f instanceof SmokerBlockEntity ? RecipeType.SMOKING : RecipeType.SMELTING;
	}

	private static Optional<? extends RecipeHolder<? extends AbstractCookingRecipe>> recipeFor(ServerLevel level, AbstractFurnaceBlockEntity f) {
		return find(level, typeOf(f), f.getItem(0));
	}

	private static <T extends AbstractCookingRecipe> Optional<RecipeHolder<T>> find(ServerLevel level, RecipeType<T> type, ItemStack in) {
		return level.getRecipeManager().getRecipeFor(type, new SingleRecipeInput(in), level);
	}

	/**
	 * Cooks the furnace's whole input stack at once, as far as its fuel and output slot allow -- each item burning the fuel
	 * the furnace itself would burn for it (smokers and blast furnaces: half the burn for half the cook time). Returns how
	 * many it made. Public for the gametests.
	 */
	public static int smeltAll(ServerLevel level, AbstractFurnaceBlockEntity furnace) {
		return smeltWith(level, furnace, typeOf(furnace));
	}

	private static <T extends AbstractCookingRecipe> int smeltWith(ServerLevel level, AbstractFurnaceBlockEntity furnace, RecipeType<T> type) {
		ItemStack in = furnace.getItem(0);
		if (in.isEmpty()) {
			return 0;
		}
		Optional<RecipeHolder<T>> found = find(level, type, in);
		if (found.isEmpty()) {
			return 0;
		}
		RecipeHolder<T> holder = found.get();
		ItemStack result = holder.value().assemble(new SingleRecipeInput(in), level.registryAccess());
		int cook = Math.max(1, holder.value().getCookingTime());
		double fuelFactor = type == RecipeType.SMELTING ? 1.0 : 0.5;
		Map<Item, Integer> fuels = AbstractFurnaceBlockEntity.getFuel();
		int bank = 0;
		int made = 0;
		while (!furnace.getItem(0).isEmpty() && !result.isEmpty()) {
			ItemStack out = furnace.getItem(2);
			if (!out.isEmpty() && (!ItemStack.isSameItemSameComponents(out, result) || out.getCount() + result.getCount() > out.getMaxStackSize())) {
				break;
			}
			while (bank < cook) {
				ItemStack fuel = furnace.getItem(1);
				Integer burn = fuel.isEmpty() ? null : fuels.get(fuel.getItem());
				if (burn == null || burn <= 0) {
					break;
				}
				bank += (int) Math.round(burn * fuelFactor);
				Item remainder = fuel.getItem().getCraftingRemainingItem();
				fuel.shrink(1);
				if (fuel.isEmpty() && remainder != null) {
					furnace.setItem(1, new ItemStack(remainder));
				}
			}
			if (bank < cook) {
				break; // out of fuel
			}
			bank -= cook;
			furnace.getItem(0).shrink(1);
			if (out.isEmpty()) {
				furnace.setItem(2, result.copy());
			} else {
				out.grow(result.getCount());
			}
			furnace.setRecipeUsed(holder);
			made++;
		}
		furnace.setChanged();
		return made;
	}

	// ---------------------------------------------------------------- the Auto-Courier

	/** The chest (barrel, any container) the player is looking at within reach, or null. */
	public static BlockPos lookedAtContainer(ServerPlayer p) {
		HitResult hit = p.pick(SpeedForceConfig.COURIER_CHEST_REACH, 1.0f, false);
		if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK
				&& p.level().getBlockEntity(bh.getBlockPos()) instanceof Container && !(p.level().getBlockEntity(bh.getBlockPos()) instanceof AbstractFurnaceBlockEntity)) {
			return bh.getBlockPos().immutable();
		}
		return null;
	}

	/**
	 * Send: the main-hand stack runs to marked place {@code index} -- into the chest marked there (what doesn't fit is left
	 * beside it), or dropped at the spot. The chunk there is loaded just to deliver.
	 */
	public static boolean courier(ServerPlayer p, int index) {
		if (!SpeedForceSpeedster.hasPower(p) || p.getServer() == null) {
			return false;
		}
		SpeedForceMemories mem = SpeedForceMemory.of(p);
		if (index < 0 || index >= mem.marked().size()) {
			return false;
		}
		ItemStack stack = p.getMainHandItem();
		if (stack.isEmpty()) {
			SpeedForceSpeedster.say(p, "message.projecthero.speed_force_speedster.courier_empty", ChatFormatting.GRAY);
			return false;
		}
		if (SpeedForceSpeedster.cooldownRemaining(p, COURIER) > 0) {
			return false;
		}
		Place place = mem.marked().get(index);
		ServerLevel to = SpeedForceMemory.levelOf(p.getServer(), place.dimension());
		if (to == null || to.dimension() == SpeedForceRealm.DIMENSION) {
			return false;
		}
		if (!SpeedForceSpeedster.spendMeter(p, SpeedForceConfig.COURIER_COST)) {
			SpeedForceSpeedster.say(p, "message.projecthero.speed_force_speedster.need_meter", ChatFormatting.RED,
					Math.round(SpeedForceConfig.COURIER_COST));
			return false;
		}
		ItemStack moving = stack.copy();
		int count = moving.getCount();
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		BlockPos spot = BlockPos.containing(place.x(), place.y(), place.z());
		BlockPos chest = place.chest().orElse(null);
		// load the chunk there (synchronously -- it unloads again once nothing keeps it)
		to.getChunk(SectionPos.blockToSectionCoord(spot.getX()), SectionPos.blockToSectionCoord(spot.getZ()));
		boolean intoChest = false;
		if (chest != null) {
			to.getChunk(SectionPos.blockToSectionCoord(chest.getX()), SectionPos.blockToSectionCoord(chest.getZ()));
			BlockEntity be = to.getBlockEntity(chest);
			if (be instanceof Container c) {
				moving = HopperBlockEntity.addItem(null, c, moving, null);
				be.setChanged();
				intoChest = moving.getCount() < count;
			}
		}
		if (!moving.isEmpty()) {
			ItemEntity drop = new ItemEntity(to, place.x(), place.y() + 0.5, place.z(), moving);
			drop.setDeltaMovement(Vec3.ZERO);
			drop.setDefaultPickUpDelay();
			to.addFreshEntity(drop);
		}
		SpeedForceAbilities.cooldown(p, COURIER, SpeedForceConfig.COURIER_COOLDOWN);
		Vec3 c = p.position().add(0, 1.0, 0);
		p.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 30, 0.5, 0.8, 0.5, 0.3);
		SpeedForceSpeedster.fx(p, SpeedForceFxPayload.STREAK, c, c.add(p.getLookAngle().scale(6)));
		SpeedForceSpeedster.sound(p, SoundEvents.TRIDENT_RIPTIDE_1.value(), 0.8f, 1.6f);
		Vec3 at = new Vec3(place.x(), place.y() + 1.0, place.z());
		to.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 20, 0.4, 0.6, 0.4, 0.2);
		p.displayClientMessage(Component.translatable(intoChest ? "message.projecthero.speed_force_speedster.courier_chest"
				: "message.projecthero.speed_force_speedster.courier_dropped", count, stack.getHoverName(), SpeedForceMemory.describe(place))
				.withStyle(ChatFormatting.GOLD), false);
		return true;
	}
}
