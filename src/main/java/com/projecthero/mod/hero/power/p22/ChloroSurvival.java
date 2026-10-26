package com.projecthero.mod.hero.power.p22;

import static com.projecthero.mod.hero.power.p22.PlantManipulationHandlers.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.TempBlocks;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Chlorokinesis -- the survival side: Green Thumb (empty-hand right-clicks), Tree Feller, the fruit battery,
 * Root Sense, Overgrowth / Bloom growth and the Living Architecture builds (vine ladder, root bridge, grow shelter,
 * bramble wall).
 */
public final class ChloroSurvival {
	/** A live Bramble Wall: whose, where, until when. */
	private record Bramble(ResourceKey<Level> level, AABB box, long until) {
	}

	private static final Map<UUID, Bramble> BRAMBLES = new ConcurrentHashMap<>();
	/** Root Sense: the spots the sap trails run to, per player, while the pulse lasts. */
	private static final Map<UUID, List<BlockPos>> SENSE_TARGETS = new ConcurrentHashMap<>();
	/** The game time of the last fruit bonus per player (one per bite). */
	private static final Map<UUID, Long> LAST_FRUIT = new ConcurrentHashMap<>();

	private ChloroSurvival() {
	}

	public static void clearSessionState() {
		BRAMBLES.clear();
		SENSE_TARGETS.clear();
		LAST_FRUIT.clear();
	}

	private static ServerLevel level(ServerPlayer p) {
		return AbilityHelpers.level(p);
	}

	static void register() {
		UseBlockCallback.EVENT.register(ChloroSurvival::useBlock);
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !owns(sp)
					|| !sp.getMainHandItem().isEmpty() || !sp.isShiftKeyDown() || !(entity instanceof Animal animal)) {
				return InteractionResult.PASS;
			}
			return feedAnimal(sp, animal) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
			if (world instanceof ServerLevel sl && player instanceof ServerPlayer sp && sp.isShiftKeyDown() && owns(sp)
					&& state.is(BlockTags.LOGS)) {
				fellTree(sp, sl, pos, state);
			}
		});
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUUID();
			BRAMBLES.remove(id);
			SENSE_TARGETS.remove(id);
			LAST_FRUIT.remove(id);
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0) {
				ChloroMoves.onDamaged(entity, source);
			}
		});
	}

	// ================================================================ Green Thumb

	private static InteractionResult useBlock(Player player, Level world, InteractionHand hand, BlockHitResult hit) {
		if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !owns(sp)
				|| !sp.getMainHandItem().isEmpty() || !(world instanceof ServerLevel sl)) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = sl.getBlockState(pos);
		if (sp.isShiftKeyDown()) {
			return purify(sp, sl, pos, state, hit.getDirection()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		if (harvest(sp, sl, pos, state)) {
			return InteractionResult.SUCCESS;
		}
		if (state.is(BlockTags.SMALL_FLOWERS) || state.is(BlockTags.TALL_FLOWERS)) {
			if (!afford(sp, COST_FLOWER)) {
				return InteractionResult.FAIL;
			}
			spend(sp, COST_FLOWER);
			ChloroMoves.spawnItem(sl, Vec3.atCenterOf(pos), new ItemStack(state.getBlock().asItem()));
			sl.sendParticles(ChloroParticles.PETAL, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 6, 0.25, 0.2, 0.25, 0.01);
			sl.playSound(null, pos, SoundEvents.FLOWERING_AZALEA_PLACE, SoundSource.PLAYERS, 0.8f, 1.4f);
			return InteractionResult.SUCCESS;
		}
		if (state.getBlock() instanceof BonemealableBlock bm && bm.isValidBonemealTarget(sl, pos, state)) {
			if (!afford(sp, COST_BONEMEAL)) {
				return InteractionResult.FAIL;
			}
			spend(sp, COST_BONEMEAL);
			for (int i = 0; i < 2; i++) {
				// re-resolve each time: the first may turn a sapling into a tree (the v0.10.20 crash)
				BlockState cur = sl.getBlockState(pos);
				if (cur.getBlock() instanceof BonemealableBlock b2 && b2.isValidBonemealTarget(sl, pos, cur)) {
					b2.performBonemeal(sl, sl.random, pos, cur);
				}
			}
			sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0.0);
			sl.sendParticles(ChloroParticles.SAP, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, 4, 0.3, 0.2, 0.3, 0.0);
			sl.playSound(null, pos, SoundEvents.BONE_MEAL_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	/** Whether {@code state} is a fully grown crop Green Thumb harvests. */
	public static boolean ripe(BlockState state) {
		if (state.getBlock() instanceof CropBlock crop) {
			return crop.isMaxAge(state);
		}
		if (state.getBlock() instanceof NetherWartBlock) {
			return state.getValue(NetherWartBlock.AGE) >= 3;
		}
		if (state.getBlock() instanceof CocoaBlock) {
			return state.getValue(CocoaBlock.AGE) >= 2;
		}
		return false;
	}

	/** A ripe crop is picked and replanted (the seed it needs is taken out of its drops). Public for the gametests. */
	public static boolean harvest(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState state) {
		if (!ripe(state)) {
			return false;
		}
		List<ItemStack> drops = Block.getDrops(state, level, pos, null, p, ItemStack.EMPTY);
		ItemStack seed = state.getBlock().getCloneItemStack(level, pos, state);
		for (ItemStack d : drops) {
			if (!seed.isEmpty() && ItemStack.isSameItem(d, seed)) {
				d.shrink(1);
				break;
			}
		}
		BlockState replant;
		if (state.getBlock() instanceof CropBlock crop) {
			replant = crop.getStateForAge(0);
		} else if (state.getBlock() instanceof NetherWartBlock) {
			replant = state.setValue(NetherWartBlock.AGE, 0);
		} else {
			replant = state.setValue(CocoaBlock.AGE, 0);
		}
		level.setBlock(pos, replant, 3);
		for (ItemStack d : drops) {
			if (!d.isEmpty()) {
				Block.popResource(level, pos, d);
			}
		}
		level.sendParticles(ChloroParticles.LEAF, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 4, 0.3, 0.2, 0.3, 0.01);
		level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.PLAYERS, 1.0f, 1.1f);
		level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.PLAYERS, 0.8f, 1.2f);
		return true;
	}

	/** Sneak + empty hand: a 3x3 of dirt greens to grass, a 3x3 of stone grows moss. */
	public static boolean purify(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState state, Direction face) {
		boolean dirt = isBareDirt(state);
		boolean stone = isMossable(state);
		if (!dirt && !stone) {
			return false;
		}
		if (!afford(p, COST_PURIFY)) {
			return true;
		}
		spend(p, COST_PURIFY);
		Direction.Axis axis = face.getAxis();
		int changed = 0;
		for (int a = -1; a <= 1; a++) {
			for (int b = -1; b <= 1; b++) {
				BlockPos bp = switch (axis) {
					case Y -> pos.offset(a, 0, b);
					case X -> pos.offset(0, a, b);
					default -> pos.offset(a, b, 0);
				};
				BlockState st = level.getBlockState(bp);
				if (dirt && isBareDirt(st) && level.getBlockState(bp.above()).canBeReplaced() && level.getFluidState(bp.above()).isEmpty()) {
					level.setBlock(bp, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
					changed++;
				} else if (stone && isMossable(st)) {
					level.setBlock(bp, Blocks.MOSS_BLOCK.defaultBlockState(), 3);
					changed++;
				}
			}
		}
		Vec3 c = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.55));
		level.sendParticles(ChloroParticles.SAP, c.x, c.y, c.z, 12, 0.7, 0.3, 0.7, 0.01);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y, c.z, 10, 0.7, 0.3, 0.7, 0.0);
		level.playSound(null, pos, stone ? SoundEvents.MOSS_PLACE : SoundEvents.GRASS_PLACE, SoundSource.PLAYERS, 1.0f, 0.9f);
		return changed > 0;
	}

	private static boolean isBareDirt(BlockState st) {
		return st.is(Blocks.DIRT) || st.is(Blocks.COARSE_DIRT) || st.is(Blocks.ROOTED_DIRT);
	}

	private static boolean isMossable(BlockState st) {
		return st.is(Blocks.STONE) || st.is(Blocks.COBBLESTONE) || st.is(Blocks.DEEPSLATE) || st.is(Blocks.COBBLED_DEEPSLATE)
				|| st.is(Blocks.TUFF) || st.is(Blocks.ANDESITE) || st.is(Blocks.DIORITE) || st.is(Blocks.GRANITE);
	}

	/** Sneak + empty hand on an animal: fed into love (a baby grows), an untamed pet or horse may take to you. */
	public static boolean feedAnimal(ServerPlayer p, Animal animal) {
		ServerLevel level = level(p);
		boolean did = false;
		if (!afford(p, COST_FEED)) {
			return true;
		}
		if (animal instanceof TamableAnimal pet && !pet.isTame()) {
			if (level.random.nextFloat() < 0.34f) {
				pet.tame(p);
				pet.setOrderedToSit(true);
				level.broadcastEntityEvent(pet, (byte) 7);
			} else {
				level.broadcastEntityEvent(pet, (byte) 6);
			}
			did = true;
		} else if (animal instanceof AbstractHorse horse && !horse.isTamed()) {
			if (level.random.nextFloat() < 0.25f) {
				horse.tameWithName(p);
				level.broadcastEntityEvent(horse, (byte) 7);
			} else {
				level.broadcastEntityEvent(horse, (byte) 6);
			}
			did = true;
		} else if (animal.isBaby()) {
			animal.ageUp(Animal.getSpeedUpSecondsWhenFeeding(-animal.getAge()), true);
			did = true;
		} else if (animal.canFallInLove()) {
			animal.setInLove(p);
			did = true;
		}
		if (!did) {
			return false;
		}
		spend(p, COST_FEED);
		level.sendParticles(ChloroParticles.LEAF, animal.getX(), animal.getY() + animal.getBbHeight(), animal.getZ(), 5, 0.3, 0.2, 0.3, 0.01);
		level.playSound(null, animal.getX(), animal.getY(), animal.getZ(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.8f, 1.2f);
		p.swing(InteractionHand.MAIN_HAND, true);
		return true;
	}

	// ================================================================ Tree Feller

	/**
	 * Sneak while breaking a log: the rest of the tree comes down (up to 96 connected logs, only a natural tree -- real
	 * leaves round it), its wood dropped at the stump and a matching sapling planted. Returns how many logs fell.
	 */
	public static int fellTree(ServerPlayer p, ServerLevel level, BlockPos origin, BlockState broken) {
		Set<BlockPos> logs = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(origin);
		Set<BlockPos> seen = new HashSet<>();
		seen.add(origin);
		int leaves = 0;
		while (!queue.isEmpty() && logs.size() < FELL_MAX_LOGS) {
			BlockPos cur = queue.poll();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = 0; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						BlockPos n = cur.offset(dx, dy, dz);
						if (!seen.add(n) || Math.abs(n.getX() - origin.getX()) > 8 || Math.abs(n.getZ() - origin.getZ()) > 8
								|| n.getY() - origin.getY() > 32) {
							continue;
						}
						BlockState st = level.getBlockState(n);
						if (st.is(BlockTags.LOGS)) {
							logs.add(n);
							queue.add(n);
						} else if (st.getBlock() instanceof LeavesBlock && !st.getValue(LeavesBlock.PERSISTENT)) {
							leaves++;
						}
					}
				}
			}
		}
		if (logs.isEmpty() || leaves < 3 || !afford(p, COST_FELL)) {
			return 0;
		}
		spend(p, COST_FELL);
		ItemStack tool = p.getMainHandItem();
		int n = 0;
		for (BlockPos bp : logs) {
			BlockState st = level.getBlockState(bp);
			List<ItemStack> drops = p.isCreative() ? List.of() : Block.getDrops(st, level, bp, null, p, tool);
			if (n % 3 == 0) {
				level.levelEvent(2001, bp, Block.getId(st));
			}
			level.setBlock(bp, Blocks.AIR.defaultBlockState(), 3);
			for (ItemStack d : drops) {
				Block.popResource(level, origin, d);
			}
			n++;
		}
		if (!tool.isEmpty() && tool.isDamageableItem() && !p.isCreative()) {
			tool.hurtAndBreak(Math.max(1, n / 4), p, EquipmentSlot.MAINHAND);
		}
		// replant at the lowest point of the trunk
		BlockPos low = origin;
		for (BlockPos bp : logs) {
			if (bp.getY() < low.getY()) {
				low = bp;
			}
		}
		Block sapling = saplingFor(broken.getBlock());
		if (level.getBlockState(low).isAir() && sapling.defaultBlockState().canSurvive(level, low)) {
			level.setBlock(low, sapling.defaultBlockState(), 3);
			level.sendParticles(ChloroParticles.LEAF, low.getX() + 0.5, low.getY() + 0.5, low.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.02);
		}
		level.playSound(null, origin, SoundEvents.WOOD_BREAK, SoundSource.PLAYERS, 1.4f, 0.6f);
		level.playSound(null, origin, SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.PLAYERS, 1.0f, 0.7f);
		return n;
	}

	/** The sapling a log grows from: oak_log -> oak_sapling, mangrove -> propagule, else oak. */
	public static Block saplingFor(Block log) {
		ResourceLocation id = BuiltInRegistries.BLOCK.getKey(log);
		String path = id.getPath().replace("stripped_", "").replace("_log", "").replace("_wood", "");
		if (path.equals("mangrove")) {
			return Blocks.MANGROVE_PROPAGULE;
		}
		ResourceLocation sap = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), path + "_sapling");
		return BuiltInRegistries.BLOCK.getOptional(sap).orElse(Blocks.OAK_SAPLING);
	}

	// ================================================================ fruit

	private static boolean fruit(ItemStack s) {
		return s.is(Items.SWEET_BERRIES) || s.is(Items.GLOW_BERRIES) || s.is(Items.APPLE) || s.is(Items.MELON_SLICE)
				|| s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE);
	}

	/** The last bite of a fruit: +12 Chlorophyll, +2 hunger, +3 saturation. */
	static void fruitTick(ServerPlayer p) {
		if (!p.isUsingItem() || p.getUseItemRemainingTicks() != 1 || !fruit(p.getUseItem())) {
			return;
		}
		long now = p.level().getGameTime();
		Long last = LAST_FRUIT.get(p.getUUID());
		if (last != null && now - last < 4) {
			return;
		}
		LAST_FRUIT.put(p.getUUID(), now);
		eatFruit(p);
	}

	public static void eatFruit(ServerPlayer p) {
		gain(p, FRUIT_CHLOROPHYLL);
		p.getFoodData().eat(2, 0.75f);
		level(p).sendParticles(ChloroParticles.SAP, p.getX(), p.getY() + 1.2, p.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
	}

	// ================================================================ growth (Overgrowth / Bloom Burst)

	/** Bone-meals every crop / stem / berry bush within {@code r} (a disc, y -2..+2) {@code times} times. */
	public static int growCrops(ServerLevel level, BlockPos c, int r, int times) {
		int n = 0;
		for (BlockPos bp : BlockPos.betweenClosed(c.offset(-r, -2, -r), c.offset(r, 2, r))) {
			if ((bp.getX() - c.getX()) * (bp.getX() - c.getX()) + (bp.getZ() - c.getZ()) * (bp.getZ() - c.getZ()) > r * r) {
				continue;
			}
			BlockState st = level.getBlockState(bp);
			if (!(st.getBlock() instanceof CropBlock || st.getBlock() instanceof StemBlock || st.getBlock() instanceof SweetBerryBushBlock)) {
				continue;
			}
			BlockPos at = bp.immutable();
			for (int i = 0; i < times; i++) {
				BlockState cur = level.getBlockState(at);
				if (cur.getBlock() instanceof BonemealableBlock bm && bm.isValidBonemealTarget(level, at, cur)) {
					bm.performBonemeal(level, level.random, at, cur);
				}
			}
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.0);
			n++;
		}
		return n;
	}

	/**
	 * Overgrowth's harvest growth: every crop in {@code r} matures, stems and berry bushes ripen, up to 8 saplings grow into
	 * trees; with griefing on, bare dirt greens and a few flowers sprout. Returns how many plants grew.
	 */
	public static int overgrowLand(ServerPlayer p, ServerLevel level, BlockPos c, int r) {
		int grown = 0;
		int saplings = 0;
		int flowers = 0;
		boolean grief = AbilityHelpers.canGrief();
		List<BlockPos> saplingSpots = new ArrayList<>();
		Block[] blooms = { Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.AZURE_BLUET, Blocks.ALLIUM };
		for (BlockPos bp : BlockPos.betweenClosed(c.offset(-r, -3, -r), c.offset(r, 3, r))) {
			int dx = bp.getX() - c.getX();
			int dz = bp.getZ() - c.getZ();
			if (dx * dx + dz * dz > r * r) {
				continue;
			}
			BlockState st = level.getBlockState(bp);
			Block b = st.getBlock();
			if (b instanceof CropBlock crop) {
				if (!crop.isMaxAge(st)) {
					level.setBlock(bp, crop.getStateForAge(crop.getMaxAge()), 2);
					grown++;
				}
			} else if (b instanceof SweetBerryBushBlock) {
				if (st.getValue(SweetBerryBushBlock.AGE) < 3) {
					level.setBlock(bp, st.setValue(SweetBerryBushBlock.AGE, 3), 2);
					grown++;
				}
			} else if (b instanceof CocoaBlock) {
				if (st.getValue(CocoaBlock.AGE) < 2) {
					level.setBlock(bp, st.setValue(CocoaBlock.AGE, 2), 2);
					grown++;
				}
			} else if (b instanceof StemBlock) {
				BlockPos at = bp.immutable();
				for (int i = 0; i < 4; i++) {
					BlockState cur = level.getBlockState(at);
					if (cur.getBlock() instanceof BonemealableBlock bm && bm.isValidBonemealTarget(level, at, cur)) {
						bm.performBonemeal(level, level.random, at, cur);
					}
				}
				grown++;
			} else if (st.is(BlockTags.SAPLINGS) && saplings < 8) {
				saplingSpots.add(bp.immutable());
				saplings++;
			} else if (grief && st.is(Blocks.DIRT) && level.getBlockState(bp.above()).isAir()) {
				level.setBlock(bp, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
			} else if (grief && st.is(Blocks.GRASS_BLOCK) && flowers < 30 && level.getBlockState(bp.above()).isAir()
					&& level.random.nextFloat() < 0.05f) {
				level.setBlock(bp.above(), blooms[level.random.nextInt(blooms.length)].defaultBlockState(), 3);
				flowers++;
			}
		}
		for (BlockPos sp : saplingSpots) {
			for (int i = 0; i < 12; i++) {
				BlockState cur = level.getBlockState(sp);
				if (!(cur.getBlock() instanceof BonemealableBlock bm) || !bm.isValidBonemealTarget(level, sp, cur)
						|| !cur.is(BlockTags.SAPLINGS)) {
					break;
				}
				bm.performBonemeal(level, level.random, sp, cur);
			}
			grown++;
		}
		return grown;
	}

	// ================================================================ Shift+V: Living Architecture

	/** Which build a Living Architecture press makes. */
	public enum Build { LADDER, BRIDGE, SHELTER, BRAMBLE }

	/** The build for where {@code p} is looking. */
	public static Build buildFor(ServerPlayer p) {
		float pitch = p.getXRot();
		if (pitch > 50.0f) {
			return Build.BRIDGE;
		}
		if (pitch < -50.0f) {
			return Build.SHELTER;
		}
		BlockHitResult hit = AbilityHelpers.raycastBlock(p, 4.5);
		if (hit.getType() == HitResult.Type.BLOCK && hit.getDirection().getAxis().isHorizontal()) {
			return Build.LADDER;
		}
		return Build.BRAMBLE;
	}

	/** Shift+V: grows whatever the look asks for. Returns the build made, or null (nothing grew, nothing spent). */
	public static Build livingArchitecture(ServerPlayer p) {
		if (!AbilityHelpers.canGrief()) {
			p.displayClientMessage(Component.translatable("message.projecthero.chloro.no_grief"), true);
			return null;
		}
		Build b = buildFor(p);
		float cost = switch (b) {
			case LADDER -> COST_LADDER;
			case BRIDGE -> COST_BRIDGE;
			case SHELTER -> COST_SHELTER;
			case BRAMBLE -> COST_BRAMBLE;
		};
		if (!afford(p, cost)) {
			return null;
		}
		boolean ok = switch (b) {
			case LADDER -> vineLadder(p) > 0;
			case BRIDGE -> rootBridge(p) > 0;
			case SHELTER -> growShelter(p) > 0;
			case BRAMBLE -> brambleWall(p) > 0;
		};
		if (!ok) {
			p.displayClientMessage(Component.translatable("message.projecthero.chloro.no_room"), true);
			return null;
		}
		spend(p, cost);
		startCountdown(p, ARCH_CD_RES, b == Build.SHELTER ? SHELTER_CD : ARCH_CD);
		MutationVisuals.play(p, b == Build.BRIDGE ? "p22.snare" : b == Build.SHELTER ? "p22.bloom" : "p22.shape");
		AbilityHelpers.sound(p, SoundEvents.ROOTED_DIRT_PLACE, 1.2f, 0.7f);
		AbilityHelpers.sound(p, SoundEvents.AZALEA_LEAVES_PLACE, 1.2f, 0.8f);
		return b;
	}

	/** Real vines up the face of the wall looked at, from your feet up to 16 high. Returns how many were placed. */
	public static int vineLadder(ServerPlayer p) {
		ServerLevel level = level(p);
		BlockHitResult hit = AbilityHelpers.raycastBlock(p, 4.5);
		if (hit.getType() != HitResult.Type.BLOCK || !hit.getDirection().getAxis().isHorizontal()) {
			return 0;
		}
		Direction out = hit.getDirection();
		BlockPos wallAt = hit.getBlockPos();
		int placed = 0;
		int startY = Math.min(p.blockPosition().getY(), wallAt.getY());
		for (int i = 0; i < LADDER_MAX; i++) {
			BlockPos wall = new BlockPos(wallAt.getX(), startY + i, wallAt.getZ());
			BlockPos at = wall.relative(out);
			BlockState here = level.getBlockState(at);
			if (!level.getBlockState(wall).isFaceSturdy(level, wall, out)) {
				if (i > 2) {
					break;
				}
				continue;
			}
			if (!(here.isAir() || here.is(Blocks.VINE))) {
				if (i > 2) {
					break;
				}
				continue;
			}
			BlockState vine = (here.is(Blocks.VINE) ? here : Blocks.VINE.defaultBlockState())
					.setValue(VineBlock.getPropertyForFace(out.getOpposite()), true);
			level.setBlock(at, vine, 3);
			placed++;
			if (i % 2 == 0) {
				level.sendParticles(ChloroParticles.LEAF, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.01);
			}
		}
		return placed;
	}

	/** Ten blocks of mangrove roots straight ahead at your feet's level. Returns how many were placed. */
	public static int rootBridge(ServerPlayer p) {
		return rootBridge(p, BRIDGE_LENGTH);
	}

	/** The bridge, {@code length} blocks long (the gametests keep it inside their cage). */
	public static int rootBridge(ServerPlayer p, int length) {
		ServerLevel level = level(p);
		Direction dir = p.getDirection();
		BlockPos base = p.blockPosition().below();
		int placed = 0;
		for (int i = 1; i <= length; i++) {
			BlockPos at = base.relative(dir, i);
			BlockState st = level.getBlockState(at);
			if (st.canBeReplaced()) {
				level.setBlock(at, Blocks.MANGROVE_ROOTS.defaultBlockState(), 3);
				placed++;
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MANGROVE_ROOTS.defaultBlockState()), at.getX() + 0.5,
						at.getY() + 0.6, at.getZ() + 0.5, 4, 0.3, 0.1, 0.3, 0.05);
			}
		}
		return placed;
	}

	/**
	 * A permanent 5x5 hut of leaves round you: oak log posts, persistent leaf walls with a doorway facing your look, a leaf
	 * roof with a shroomlight in the middle, moss carpet underfoot. Only ever fills empty space. Returns blocks placed.
	 */
	public static int growShelter(ServerPlayer p) {
		ServerLevel level = level(p);
		BlockPos c = p.blockPosition();
		Direction door = p.getDirection();
		BlockState leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		BlockState flower = Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		BlockState log = Blocks.OAK_LOG.defaultBlockState();
		int placed = 0;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
				boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
				for (int dy = 0; dy <= 3; dy++) {
					BlockPos at = c.offset(dx, dy, dz);
					BlockState want;
					if (dy == 3) {
						want = dx == 0 && dz == 0 ? Blocks.SHROOMLIGHT.defaultBlockState() : ((dx + dz) % 3 == 0 ? flower : leaf);
					} else if (corner) {
						want = log;
					} else if (edge) {
						boolean doorway = dy <= 1 && door.getStepX() * dx + door.getStepZ() * dz == 2
								&& (door.getAxis() == Direction.Axis.X ? dz == 0 : dx == 0);
						if (doorway) {
							continue;
						}
						want = (dx * 7 + dz * 3 + dy) % 4 == 0 ? flower : leaf;
					} else if (dy == 0) {
						want = Blocks.MOSS_CARPET.defaultBlockState();
						if (!want.canSurvive(level, at)) {
							continue;
						}
					} else {
						continue;
					}
					if (level.getBlockState(at).canBeReplaced() && level.getFluidState(at).isEmpty()) {
						level.setBlock(at, want, 3);
						placed++;
					}
				}
			}
		}
		level.sendParticles(ChloroParticles.LEAF, c.getX() + 0.5, c.getY() + 2.5, c.getZ() + 0.5, 40, 1.8, 1.2, 1.8, 0.02);
		return placed;
	}

	/** A 7x3 wall of thorned leaves 3 blocks ahead for 12 s; enemies touching it take 3 a second and are slowed. */
	public static int brambleWall(ServerPlayer p) {
		ServerLevel level = level(p);
		Direction dir = p.getDirection();
		Direction side = dir.getClockWise();
		BlockPos centre = p.blockPosition().relative(dir, 3);
		BlockState a = Blocks.DARK_OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		BlockState b = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		int placed = 0;
		BlockPos min = null;
		BlockPos max = null;
		for (int s = -3; s <= 3; s++) {
			for (int y = 0; y < 3; y++) {
				BlockPos at = centre.relative(side, s).above(y);
				if (level.getBlockState(at).canBeReplaced() && level.getFluidState(at).isEmpty()
						&& TempBlocks.place(level, at, (s + y) % 2 == 0 ? a : b, BRAMBLE_TICKS)) {
					placed++;
					min = min == null ? at : new BlockPos(Math.min(min.getX(), at.getX()), Math.min(min.getY(), at.getY()), Math.min(min.getZ(), at.getZ()));
					max = max == null ? at : new BlockPos(Math.max(max.getX(), at.getX()), Math.max(max.getY(), at.getY()), Math.max(max.getZ(), at.getZ()));
					if (y == 1) {
						level.sendParticles(ChloroParticles.LEAF, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.01);
					}
				}
			}
		}
		if (placed > 0) {
			AABB box = new AABB(Vec3.atLowerCornerOf(min), Vec3.atLowerCornerOf(max).add(1, 1, 1));
			BRAMBLES.put(p.getUUID(), new Bramble(level.dimension(), box, level.getGameTime() + BRAMBLE_TICKS));
		}
		return placed;
	}

	static void brambleTick(ServerPlayer p) {
		Bramble b = BRAMBLES.get(p.getUUID());
		if (b == null) {
			return;
		}
		ServerLevel level = level(p);
		if (level.getGameTime() > b.until() || !level.dimension().equals(b.level())) {
			BRAMBLES.remove(p.getUUID());
			return;
		}
		if (level.getGameTime() % 10 != 0) {
			return;
		}
		AABB touch = b.box().inflate(0.7);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, touch, e -> com.projecthero.mod.combat.HeroTargets.isHostile(p, e))) {
			AbilityHelpers.hurt(p, e, BRAMBLE_DPS / 2.0f);
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 30, 1);
			level.sendParticles(ChloroParticles.SAP, e.getX(), e.getY() + 0.6, e.getZ(), 2, 0.2, 0.2, 0.2, 0.02);
		}
	}

	// ================================================================ Shift+C: Root Sense

	/** What Root Sense found (gametests read it back). */
	public record SenseReport(int crops, int ripe, int saplings, Map<String, Integer> ores, List<BlockPos> trails) {
	}

	private static final String[] ORE_ORDER = { "ancient_debris", "diamond", "emerald", "gold", "lapis", "redstone", "iron", "copper", "coal" };

	private static String oreKind(BlockState st) {
		if (st.is(Blocks.ANCIENT_DEBRIS)) {
			return "ancient_debris";
		}
		if (st.is(BlockTags.DIAMOND_ORES)) {
			return "diamond";
		}
		if (st.is(BlockTags.EMERALD_ORES)) {
			return "emerald";
		}
		if (st.is(BlockTags.GOLD_ORES)) {
			return "gold";
		}
		if (st.is(BlockTags.LAPIS_ORES)) {
			return "lapis";
		}
		if (st.is(BlockTags.REDSTONE_ORES)) {
			return "redstone";
		}
		if (st.is(BlockTags.IRON_ORES)) {
			return "iron";
		}
		if (st.is(BlockTags.COPPER_ORES)) {
			return "copper";
		}
		if (st.is(BlockTags.COAL_ORES)) {
			return "coal";
		}
		return null;
	}

	private static int oreRank(String kind) {
		for (int i = 0; i < ORE_ORDER.length; i++) {
			if (ORE_ORDER[i].equals(kind)) {
				return i;
			}
		}
		return ORE_ORDER.length;
	}

	/** Shift+C: roots spread 24 blocks through the ground -- crops, saplings and ores reported, sap trails to the best 8. */
	public static SenseReport rootSense(ServerPlayer p) {
		spend(p, COST_SENSE);
		ServerLevel level = level(p);
		BlockPos c = p.blockPosition();
		int r = SENSE_RADIUS;
		int crops = 0, ripe = 0, saplings = 0;
		Map<String, Integer> ores = new java.util.LinkedHashMap<>();
		record Found(BlockPos pos, int rank, double dist) {
		}
		List<Found> found = new ArrayList<>();
		BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (dx * dx + dz * dz > r * r) {
					continue;
				}
				for (int dy = -r / 2; dy <= r / 2; dy++) {
					bp.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
					if (!level.isLoaded(bp)) {
						continue;
					}
					BlockState st = level.getBlockState(bp);
					if (st.isAir()) {
						continue;
					}
					double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
					if (st.getBlock() instanceof CropBlock || st.getBlock() instanceof NetherWartBlock || st.getBlock() instanceof CocoaBlock) {
						crops++;
						if (ripe(st)) {
							ripe++;
							found.add(new Found(bp.immutable(), ORE_ORDER.length + 1, d));
						}
					} else if (st.is(BlockTags.SAPLINGS)) {
						saplings++;
					} else {
						String kind = oreKind(st);
						if (kind != null) {
							ores.merge(kind, 1, Integer::sum);
							found.add(new Found(bp.immutable(), oreRank(kind), d));
						}
					}
				}
			}
		}
		found.sort(Comparator.comparingInt(Found::rank).thenComparingDouble(Found::dist));
		List<BlockPos> trails = new ArrayList<>();
		for (Found f : found) {
			if (trails.size() >= 8) {
				break;
			}
			boolean near = false;
			for (BlockPos t : trails) {
				if (t.distSqr(f.pos()) < 9) {
					near = true; // one trail per vein
					break;
				}
			}
			if (!near) {
				trails.add(f.pos());
			}
		}
		SENSE_TARGETS.put(p.getUUID(), trails);
		set(p, "sense_ticks", SENSE_TICKS);
		ChloroFxEntity.spawn(p, ChloroFxEntity.SENSE, 30, p.position()).radius(SENSE_RADIUS);
		MutationVisuals.play(p, "p22.sense");
		AbilityHelpers.sound(p, SoundEvents.ROOTED_DIRT_PLACE, 1.0f, 0.5f);
		AbilityHelpers.sound(p, SoundEvents.SCULK_CLICKING, 0.8f, 0.6f);
		report(p, crops, ripe, saplings, ores, found.isEmpty() ? null : found.stream()
				.filter(f -> f.rank() < ORE_ORDER.length).findFirst().map(Found::pos).orElse(null), c);
		return new SenseReport(crops, ripe, saplings, ores, trails);
	}

	private static void report(ServerPlayer p, int crops, int ripe, int saplings, Map<String, Integer> ores, BlockPos best, BlockPos from) {
		p.displayClientMessage(Component.translatable("message.projecthero.chloro.sense.head", SENSE_RADIUS)
				.withStyle(net.minecraft.ChatFormatting.GREEN), false);
		p.displayClientMessage(Component.translatable("message.projecthero.chloro.sense.plants", crops, ripe, saplings)
				.withStyle(net.minecraft.ChatFormatting.GRAY), false);
		if (ores.isEmpty()) {
			p.displayClientMessage(Component.translatable("message.projecthero.chloro.sense.no_ores").withStyle(net.minecraft.ChatFormatting.GRAY),
					false);
		} else {
			MutableComponent list = Component.empty();
			boolean first = true;
			for (String kind : ORE_ORDER) {
				Integer n = ores.get(kind);
				if (n == null) {
					continue;
				}
				if (!first) {
					list.append(", ");
				}
				first = false;
				list.append(Component.literal(n + " ")).append(Component.translatable("message.projecthero.chloro.ore." + kind));
			}
			p.displayClientMessage(Component.translatable("message.projecthero.chloro.sense.ores", list).withStyle(net.minecraft.ChatFormatting.GRAY),
					false);
		}
		if (best != null) {
			String kind = oreKind(level(p).getBlockState(best));
			int dx = best.getX() - from.getX();
			int dz = best.getZ() - from.getZ();
			int dist = (int) Math.round(Math.sqrt(best.distSqr(from)));
			String[] dirs = { "S", "SW", "W", "NW", "N", "NE", "E", "SE" };
			double ang = Math.toDegrees(Math.atan2(-dx, dz));
			int idx = (int) Math.floorMod(Math.round(ang / 45.0), 8);
			int dy = best.getY() - from.getY();
			p.displayClientMessage(Component.translatable("message.projecthero.chloro.sense.nearest",
					Component.translatable("message.projecthero.chloro.ore." + (kind == null ? "coal" : kind)), dist, dirs[idx],
					dy < 0 ? Component.translatable("message.projecthero.chloro.sense.below", -dy)
							: Component.translatable("message.projecthero.chloro.sense.above", dy))
					.withStyle(net.minecraft.ChatFormatting.YELLOW), false);
		}
		p.displayClientMessage(Component.translatable("message.projecthero.chloro.sense.bar", crops, ores.values().stream().mapToInt(i -> i).sum()),
				true);
	}

	/** While a Root Sense lasts: glowing sap flows along the ground to each spot (only you see it). */
	static void senseTick(ServerPlayer p) {
		int left = Math.round(res(p, "sense_ticks"));
		List<BlockPos> targets = SENSE_TARGETS.get(p.getUUID());
		if (left <= 0 || targets == null) {
			if (targets != null && left <= 0) {
				SENSE_TARGETS.remove(p.getUUID());
			}
			return;
		}
		if (left % 5 != 0) {
			return;
		}
		ServerLevel level = level(p);
		Vec3 from = p.position().add(0, 0.15, 0);
		double phase = (left / 5) % 4 * 0.25;
		for (BlockPos t : targets) {
			Vec3 to = Vec3.atCenterOf(t);
			Vec3 d = to.subtract(from);
			double len = d.length();
			if (len < 0.5) {
				continue;
			}
			Vec3 step = d.normalize();
			for (double s = phase; s < Math.min(len, 26.0); s += 1.0) {
				Vec3 at = from.add(step.scale(s));
				level.sendParticles(p, ChloroParticles.SAP, true, at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.0);
			}
			level.sendParticles(p, ChloroParticles.SAP, true, to.x, to.y, to.z, 3, 0.3, 0.3, 0.3, 0.0);
		}
	}
}
