package com.projecthero.mod.hero.mutation;

import java.util.Locale;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.hero.MutationTrigger;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.hero.data.ResearchStage;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The mutation / freak-accident engine (spec section 2 &amp; 7).
 *
 * <p>Flow: drink a serum → {@link ModMobEffects#UNSTABLE_MUTATION} marks a pending power → the
 * player performs that power's exposure event (natural ambient conditions detected here, or a
 * laboratory device calling {@link #triggerExposure}) before the effect expires → the power is
 * permanently unlocked with the {@code UNUSUAL MUTATION DETECTED} title.
 *
 * <p>All checks are server-side. Drinking the serum alone never grants a power. Re-drinking a serum
 * for an already-owned power never stacks anything ({@link ExperimentalPowers#grant} is idempotent).
 */
public final class MutationManager {
	// ambient exposure dwell times (ticks)
	private static final int DWELL_FIRE = 60;
	private static final int DWELL_POWDER_SNOW = 60;
	private static final int DWELL_SUNLIGHT = 100;
	private static final int DWELL_DARKNESS = 60;
	private static final int DWELL_PLANTS = 40;
	private static final int DWELL_SUBMERSION = 1200;
	private static final int DWELL_STONE = 100;
	private static final int DWELL_GEODE = 60;
	private static final int DWELL_PSIONIC = 60;
	private static final int DWELL_MAGNETIC = 80;
	private static final int DWELL_STORM_ALT = 100;

	private MutationManager() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(MutationManager::onAfterDamage);
		UseItemCallback.EVENT.register(MutationManager::onUseItem);
	}

	// ---------------- per-tick ----------------

	public static void serverTick(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		ExperimentalState s = ExperimentalPowers.state(player);
		var effect = player.getEffect(ModMobEffects.UNSTABLE_MUTATION);

		if (effect == null) {
			if (!s.pendingMutationPower.isEmpty()) {
				s.pendingMutationPower = "";
				s.pendingExposureProgress.clear();
				s.pendingExposureTicks = 0;
				MutationFeedback.serumFaded(player);
			}
			return;
		}

		Power target = ModSerums.powerForAmplifier(effect.getAmplifier());
		if (target == null) {
			return;
		}

		if (!s.pendingMutationPower.equals(target.key())) {
			s.pendingMutationPower = target.key();
			s.pendingMutationExpiryTick = level.getGameTime() + effect.getDuration();
			s.pendingExposureProgress.clear();
			s.pendingExposureTicks = 0;
			if (!ExperimentalPowers.owns(player, target)) {
				ExperimentalPowers.advanceResearch(player, target, ResearchStage.SERUM_STABILIZED);
				award(player, "serum_stabilized");
				MutationFeedback.serumTookHold(player, target);
			}
		}

		if (ExperimentalPowers.owns(player, target)) {
			return;
		}
		ambientDetectors(player, level, target);
	}

	private static void ambientDetectors(ServerPlayer player, ServerLevel level, Power power) {
		MutationTrigger.Kind kind = power.trigger().kind();
		BlockPos pos = player.blockPosition();
		ExperimentalState s = ExperimentalPowers.state(player);

		switch (kind) {
			case FIRE_EXPOSURE -> dwell(player, s, player.getRemainingFireTicks() > 0
					|| level.getBlockState(pos).is(BlockTags.FIRE), DWELL_FIRE, power, kind);
			case POWDER_SNOW -> dwell(player, s, player.isInPowderSnow, DWELL_POWDER_SNOW, power, kind);
			case DIRECT_SUNLIGHT -> dwell(player, s, level.isDay() && !level.isRaining()
					&& level.canSeeSky(pos) && level.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos) >= 15,
					DWELL_SUNLIGHT, power, kind);
			case TRUE_DARKNESS -> dwell(player, s, !level.isDay()
					&& level.getMaxLocalRawBrightness(pos) == 0, DWELL_DARKNESS, power, kind);
			case PLANT_SURROUNDINGS -> dwell(player, s, level.canSeeSky(pos) && countPlants(level, pos) >= 6,
					DWELL_PLANTS, power, kind);
			case SUBMERSION -> dwell(player, s, player.isUnderWater(), DWELL_SUBMERSION, power, kind);
			case GEOLOGICAL_RESONANCE -> dwell(player, s, isNaturalStone(level.getBlockState(pos.below())),
					DWELL_STONE, power, kind);
			case AMETHYST_GEODE -> dwell(player, s, countAmethyst(level, pos) >= 8, DWELL_GEODE, power, kind);
			case PSIONIC_RESONANCE -> dwell(player, s, nearEnchantingSetup(level, pos), DWELL_PSIONIC, power, kind);
			case MAGNETIC_FIELD -> dwell(player, s, countMetal(level, pos) >= 16, DWELL_MAGNETIC, power, kind);
			case GRAVITY_DISTORTION -> {
				// natural alt: survive a long fall while the serum is active
				if (player.fallDistance >= 14.0f) {
					s.pendingExposureProgress.add("longfall");
				}
				if (player.onGround() && s.pendingExposureProgress.contains("longfall") && player.isAlive()) {
					attemptExposure(player, power, kind);
				}
			}
			case PRESSURE_CHAMBER -> dwell(player, s, level.isThundering() && level.canSeeSky(pos) && pos.getY() > 150,
					DWELL_STORM_ALT, power, kind);
			case HIGH_INTENSITY_LIGHT -> dwell(player, s, level.isDay() && !level.isRaining() && level.canSeeSky(pos)
					&& player.getXRot() < -55.0f, DWELL_SUNLIGHT, power, kind);
			default -> {
				// ELECTRICAL_DISCHARGE / LIGHTNING / EXPLOSION / NEAR_DEATH / SPIDER_VENOM / ENDER_PEARL /
				// RESONANT_HORN / SLIME_IMPACT / ENERGY_OVERLOAD -> event-driven (see onAfterDamage / onUseItem)
				// MOLECULAR_COMPRESSION / MASS_COMPRESSION -> laboratory device only (devices batch).
			}
		}
	}

	private static void dwell(ServerPlayer player, ExperimentalState s, boolean condition, int required,
			Power power, MutationTrigger.Kind kind) {
		if (condition) {
			s.pendingExposureTicks++;
			if (s.pendingExposureTicks >= required) {
				attemptExposure(player, power, kind);
			}
		} else if (s.pendingExposureTicks > 0) {
			s.pendingExposureTicks = 0;
		}
	}

	// ---------------- event-driven exposures ----------------

	private static void onAfterDamage(LivingEntity entity, DamageSource source, float base, float taken, boolean blocked) {
		if (!(entity instanceof ServerPlayer player) || !player.isAlive()) {
			return;
		}
		ExperimentalState s = ExperimentalPowers.state(player);
		if (s.pendingMutationPower.isEmpty()) {
			return;
		}
		Power power = com.projecthero.mod.hero.Powers.byKey(s.pendingMutationPower);
		if (power == null || ExperimentalPowers.owns(player, power)) {
			return;
		}
		MutationTrigger.Kind want = power.trigger().kind();

		boolean fire = source.is(DamageTypeTags.IS_FIRE);
		boolean explosion = source.is(DamageTypeTags.IS_EXPLOSION);
		boolean lightning = source.is(DamageTypes.LIGHTNING_BOLT);
		boolean spider = source.getEntity() instanceof Spider;

		// Energy Absorption: needs two DIFFERENT energy-like sources survived.
		if (want == MutationTrigger.Kind.ENERGY_OVERLOAD) {
			if (fire) {
				s.pendingExposureProgress.add("fire");
			}
			if (explosion) {
				s.pendingExposureProgress.add("explosion");
			}
			if (lightning) {
				s.pendingExposureProgress.add("lightning");
			}
			if (s.pendingExposureProgress.size() >= 2) {
				attemptExposure(player, power, want);
			}
			return;
		}

		if ((want == MutationTrigger.Kind.EXPLOSION && explosion)
				|| (want == MutationTrigger.Kind.FIRE_EXPOSURE && fire)
				|| ((want == MutationTrigger.Kind.LIGHTNING || want == MutationTrigger.Kind.ELECTRICAL_DISCHARGE) && lightning)
				|| (want == MutationTrigger.Kind.SPIDER_VENOM && spider)
				|| (want == MutationTrigger.Kind.NEAR_DEATH && player.getHealth() <= 6.0f)) {
			attemptExposure(player, power, want);
		}
	}

	private static InteractionResultHolder<net.minecraft.world.item.ItemStack> onUseItem(
			net.minecraft.world.entity.player.Player player, net.minecraft.world.level.Level level, InteractionHand hand) {
		net.minecraft.world.item.ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResultHolder.pass(stack);
		}
		ExperimentalState s = ExperimentalPowers.state(serverPlayer);
		if (s.pendingMutationPower.isEmpty()) {
			return InteractionResultHolder.pass(stack);
		}
		Power power = com.projecthero.mod.hero.Powers.byKey(s.pendingMutationPower);
		if (power == null) {
			return InteractionResultHolder.pass(stack);
		}
		MutationTrigger.Kind want = power.trigger().kind();
		if (want == MutationTrigger.Kind.RESONANT_HORN && stack.is(Items.GOAT_HORN)) {
			attemptExposure(serverPlayer, power, want);
		} else if (want == MutationTrigger.Kind.ENDER_PEARL && stack.is(Items.ENDER_PEARL)) {
			attemptExposure(serverPlayer, power, want);
		}
		return InteractionResultHolder.pass(stack);
	}

	/** Entry point for laboratory devices (devices batch) and other explicit exposure sources. */
	public static void triggerExposure(ServerPlayer player, MutationTrigger.Kind kind) {
		ExperimentalState s = ExperimentalPowers.state(player);
		if (s.pendingMutationPower.isEmpty()) {
			return;
		}
		Power power = com.projecthero.mod.hero.Powers.byKey(s.pendingMutationPower);
		if (power == null || ExperimentalPowers.owns(player, power)) {
			return;
		}
		if (kindMatches(power.trigger().kind(), kind)) {
			attemptExposure(player, power, kind);
		}
	}

	private static boolean kindMatches(MutationTrigger.Kind want, MutationTrigger.Kind got) {
		if (want == got) {
			return true;
		}
		return switch (want) {
			case ELECTRICAL_DISCHARGE -> got == MutationTrigger.Kind.LIGHTNING;
			case LIGHTNING -> got == MutationTrigger.Kind.ELECTRICAL_DISCHARGE;
			case GRAVITY_DISTORTION -> got == MutationTrigger.Kind.PRESSURE_CHAMBER;
			default -> false;
		};
	}

	// ---------------- grant ----------------

	private static void attemptExposure(ServerPlayer player, Power power, MutationTrigger.Kind kind) {
		ExperimentalState s = ExperimentalPowers.state(player);
		if (!power.key().equals(s.pendingMutationPower) || ExperimentalPowers.owns(player, power)) {
			return;
		}
		// v0.11.15: the mutation group takes one of the two Primary slots. If the player already holds two
		// Hero-Tier powers the oldest is replaced (and the Symbiote is removed); with one or none nothing is
		// lost. Mutations still stack with each other up to the mutation capacity.
		int heroesBefore = com.projecthero.mod.hero.HeroTiers.heroCount(player);
		if (com.projecthero.mod.hero.HeroTiers.claimExperimental(player)
				&& com.projecthero.mod.hero.HeroTiers.heroCount(player) < heroesBefore) {
			player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
					"message.projecthero.mutation.replaced_hero_tier").withStyle(net.minecraft.ChatFormatting.YELLOW), false);
		}
		if (ExperimentalPowers.atCapacity(player)) {
			MutationFeedback.capacityFull(player);
			return;
		}
		ExperimentalPowers.advanceResearch(player, power, ResearchStage.EXPOSURE_SURVIVED);
		award(player, "exposure_survived");
		MutationFeedback.exposureSurvived(player, power);
		grantMutation(player, power);
	}

	private static void grantMutation(ServerPlayer player, Power power) {
		boolean granted = ExperimentalPowers.grant(player, power);
		player.removeEffect(ModMobEffects.UNSTABLE_MUTATION);

		ExperimentalState s = ExperimentalPowers.state(player);
		s.pendingMutationPower = "";
		s.pendingExposureProgress.clear();
		s.pendingExposureTicks = 0;

		if (granted) {
			ExperimentalPowers.advanceResearch(player, power, ResearchStage.MUTATION_CONFIRMED);
			award(player, "mutation_confirmed");
			MutationFeedback.mutationConfirmed(player, power);
		}
	}

	// ---------------- research notes ----------------

	public static boolean studyResearchNote(ServerPlayer player, Power power) {
		boolean advanced = ExperimentalPowers.advanceResearch(player, power, ResearchStage.RESEARCH_FOUND);
		if (advanced) {
			award(player, "research_found");
			MutationFeedback.researchFound(player, power);
		} else {
			MutationFeedback.researchAlreadyKnown(player, power);
		}
		return advanced;
	}

	/** A "blank" research note: reveals a random power the player has not yet begun researching. */
	public static boolean studyRandomResearch(ServerPlayer player) {
		java.util.List<Power> candidates = new java.util.ArrayList<>();
		for (Power p : com.projecthero.mod.hero.Powers.all()) {
			if (ExperimentalPowers.researchStage(player, p) == ResearchStage.UNKNOWN) {
				candidates.add(p);
			}
		}
		if (candidates.isEmpty()) {
			player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
					"message.projecthero.research.all_known"), true);
			return false;
		}
		Power pick = candidates.get(player.getRandom().nextInt(candidates.size()));
		return studyResearchNote(player, pick);
	}

	// ---------------- helpers ----------------

	private static void award(ServerPlayer player, String stage) {
		if (player.getServer() == null) {
			return;
		}
		AdvancementHolder holder = player.getServer().getAdvancements()
				.get(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, "mutation/" + stage));
		if (holder != null) {
			player.getAdvancements().award(holder, "code_trigger");
		}
	}

	private static boolean isNaturalStone(BlockState state) {
		return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.DEEPSLATE) || state.is(BlockTags.STONE_ORE_REPLACEABLES);
	}

	private static int countPlants(ServerLevel level, BlockPos center) {
		int count = 0;
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
			BlockState st = level.getBlockState(p);
			if (st.is(BlockTags.FLOWERS) || st.is(BlockTags.SAPLINGS) || st.is(BlockTags.LEAVES)
					|| st.is(Blocks.SHORT_GRASS) || st.is(Blocks.TALL_GRASS) || st.is(Blocks.FERN)
					|| st.is(Blocks.MOSS_BLOCK) || st.is(Blocks.VINE) || st.is(Blocks.MOSS_CARPET)) {
				count++;
			}
		}
		return count;
	}

	private static int countAmethyst(ServerLevel level, BlockPos center) {
		int count = 0;
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-3, -3, -3), center.offset(3, 3, 3))) {
			BlockState st = level.getBlockState(p);
			if (st.is(Blocks.AMETHYST_BLOCK) || st.is(Blocks.BUDDING_AMETHYST) || st.is(Blocks.AMETHYST_CLUSTER)
					|| st.is(Blocks.LARGE_AMETHYST_BUD) || st.is(Blocks.MEDIUM_AMETHYST_BUD)) {
				count++;
			}
		}
		return count;
	}

	private static int countMetal(ServerLevel level, BlockPos center) {
		int count = 0;
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-3, -3, -3), center.offset(3, 3, 3))) {
			BlockState st = level.getBlockState(p);
			if (st.is(Blocks.IRON_BLOCK) || st.is(Blocks.COPPER_BLOCK) || st.is(Blocks.RAW_IRON_BLOCK)
					|| st.is(Blocks.RAW_COPPER_BLOCK) || st.is(Blocks.IRON_BARS) || st.is(Blocks.ANVIL)) {
				count++;
			}
		}
		return count;
	}

	private static boolean nearEnchantingSetup(ServerLevel level, BlockPos center) {
		boolean table = false;
		int bookshelves = 0;
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-3, -2, -3), center.offset(3, 2, 3))) {
			BlockState st = level.getBlockState(p);
			if (st.is(Blocks.ENCHANTING_TABLE)) {
				table = true;
			} else if (st.is(Blocks.BOOKSHELF)) {
				bookshelves++;
			}
		}
		return table && bookshelves >= 8;
	}

	public static String describe(ServerPlayer player) {
		ExperimentalState s = ExperimentalPowers.state(player);
		return String.format(Locale.ROOT, "pending=%s ticks=%d progress=%s",
				s.pendingMutationPower.isEmpty() ? "<none>" : s.pendingMutationPower,
				s.pendingExposureTicks, s.pendingExposureProgress);
	}
}
