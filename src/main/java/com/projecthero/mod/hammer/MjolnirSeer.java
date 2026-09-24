package com.projecthero.mod.hammer;

import java.util.Optional;

import com.projecthero.mod.worldgen.CraterSpawnState;
import com.projecthero.mod.worldgen.MjolnirCraterStructure;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/**
 * v0.11.15: how a player finds the (now very rare) Mjolnir. Shift + right-click any villager while holding
 * a Lightning Rod and it opens a one-line trade menu: {@value #COST} emeralds buys a bearing -- the villager
 * tells you which of the eight compass directions the nearest free Mjolnir lies in, measured from where you
 * are standing right now. Walk that way and it leads you there; come back and pay again if you lose the trail.
 *
 * <p>"Nearest free Mjolnir" is the closer of (a) any unbound hammer already lying in the overworld (the
 * {@link MjolnirRegistry} knows where every hammer that has ever loaded is) and (b) the nearest Mjolnir Crater
 * whose hammer has not yet been placed -- found by walking the crater structure set's own placement grid, so
 * it works on chunks nobody has visited.
 */
public final class MjolnirSeer {
	public static final int COST = 25;
	/** How many placement-grid regions in each direction to search for an unvisited crater. */
	private static final int SEARCH_REGIONS = 7;

	private static final String[] DIRECTIONS = {
			"north", "north_east", "east", "south_east", "south", "south_west", "west", "north_west"
	};

	private MjolnirSeer() {
	}

	public static void initialize() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !(entity instanceof AbstractVillager)
					|| !(player.getMainHandItem().is(Items.LIGHTNING_ROD) || player.getOffhandItem().is(Items.LIGHTNING_ROD))) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer sp) {
				open(sp);
			}
			return InteractionResult.sidedSuccess(world.isClientSide());
		});
	}

	private static void open(ServerPlayer player) {
		if (player.level().dimension() != Level.OVERWORLD) {
			player.displayClientMessage(Component.translatable("message.projecthero.hammer_seer.wrong_dimension")
					.withStyle(ChatFormatting.GRAY), true);
			return;
		}
		new SeerMerchant(player).openTradingScreen(player, Component.translatable("message.projecthero.hammer_seer.title"), 1);
	}

	// ---------------- the merchant ----------------

	private static final class SeerMerchant implements Merchant {
		private final MerchantOffers offers = new MerchantOffers();
		private Player trading;

		SeerMerchant(ServerPlayer player) {
			ItemStack directions = new ItemStack(Items.PAPER);
			directions.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
					Component.translatable("item.projecthero.hammer_directions").withStyle(style -> style.withItalic(false)));
			offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, COST), directions, Integer.MAX_VALUE, 0, 0.0f));
			this.trading = player;
		}

		@Override
		public void setTradingPlayer(Player player) {
			this.trading = player;
		}

		@Override
		public Player getTradingPlayer() {
			return trading;
		}

		@Override
		public MerchantOffers getOffers() {
			return offers;
		}

		@Override
		public void overrideOffers(MerchantOffers overrides) {
		}

		@Override
		public void notifyTrade(MerchantOffer offer) {
			if (trading instanceof ServerPlayer sp) {
				tell(sp);
			}
		}

		@Override
		public void notifyTradeUpdated(ItemStack stack) {
		}

		@Override
		public int getVillagerXp() {
			return 0;
		}

		@Override
		public void overrideXp(int xp) {
		}

		@Override
		public boolean showProgressBar() {
			return false;
		}

		@Override
		public SoundEvent getNotifyTradeSound() {
			return SoundEvents.VILLAGER_YES;
		}

		@Override
		public boolean isClientSide() {
			return false;
		}
	}

	private static void tell(ServerPlayer player) {
		BlockPos target = findNearestHammer(player);
		if (target == null) {
			player.displayClientMessage(Component.translatable("message.projecthero.hammer_seer.none")
					.withStyle(ChatFormatting.GRAY), false);
			return;
		}
		double dx = target.getX() + 0.5 - player.getX();
		double dz = target.getZ() + 0.5 - player.getZ();
		Component dir = Component.translatable("message.projecthero.hammer_seer.dir." + direction(dx, dz))
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
		player.displayClientMessage(Component.translatable("message.projecthero.hammer_seer.bearing", dir)
				.withStyle(ChatFormatting.AQUA), false);
	}

	/** One of the eight compass points ({@link #DIRECTIONS}). Minecraft: -Z is north, +X is east. */
	static String direction(double dx, double dz) {
		double angle = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = north, 90 = east
		int index = Math.floorMod((int) Math.round(angle / 45.0), 8);
		return DIRECTIONS[index];
	}

	// ---------------- finding the hammer ----------------

	/** The closest free hammer or not-yet-visited crater in the overworld, or {@code null} if there is none. */
	public static BlockPos findNearestHammer(ServerPlayer player) {
		ServerLevel overworld = player.server.overworld();
		BlockPos here = player.blockPosition();
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;

		for (HammerRecord record : MjolnirRegistry.get(overworld).freeHammers()) {
			if (record.dimension() != Level.OVERWORLD) {
				continue;
			}
			double d = record.lastPos().distSqr(here);
			if (d < bestDist) {
				bestDist = d;
				best = record.lastPos();
			}
		}

		BlockPos crater = findNearestUnvisitedCrater(overworld, here);
		if (crater != null && crater.distSqr(here) < bestDist) {
			best = crater;
		}
		return best;
	}

	private static BlockPos findNearestUnvisitedCrater(ServerLevel level, BlockPos origin) {
		Optional<Holder.Reference<Structure>> holder = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
				.getHolder(MjolnirCraterStructure.KEY);
		if (holder.isEmpty()) {
			return null;
		}
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
		CraterSpawnState spawned = CraterSpawnState.get(level);

		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (StructurePlacement placement : state.getPlacementsForStructure(holder.get())) {
			if (!(placement instanceof RandomSpreadStructurePlacement spread)) {
				continue;
			}
			int spacing = spread.spacing();
			int regionX = Math.floorDiv(origin.getX() >> 4, spacing);
			int regionZ = Math.floorDiv(origin.getZ() >> 4, spacing);
			for (int dx = -SEARCH_REGIONS; dx <= SEARCH_REGIONS; dx++) {
				for (int dz = -SEARCH_REGIONS; dz <= SEARCH_REGIONS; dz++) {
					ChunkPos chunk = spread.getPotentialStructureChunk(state.getLevelSeed(),
							(regionX + dx) * spacing, (regionZ + dz) * spacing);
					if (spawned.hasSpawned(chunk.toLong()) || !spread.isStructureChunk(state, chunk.x, chunk.z)) {
						continue;
					}
					int x = chunk.getMiddleBlockX();
					int z = chunk.getMiddleBlockZ();
					Holder<net.minecraft.world.level.biome.Biome> biome = generator.getBiomeSource().getNoiseBiome(
							QuartPos.fromBlock(x), QuartPos.fromBlock(64), QuartPos.fromBlock(z),
							level.getChunkSource().randomState().sampler());
					if (!holder.get().value().biomes().contains(biome)) {
						continue;
					}
					double d = origin.distSqr(new BlockPos(x, origin.getY(), z));
					if (d < bestDist) {
						bestDist = d;
						best = new BlockPos(x, origin.getY(), z);
					}
				}
			}
		}
		return best;
	}
}
