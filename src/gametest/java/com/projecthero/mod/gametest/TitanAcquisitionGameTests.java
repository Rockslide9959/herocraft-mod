package com.projecthero.mod.gametest;

import java.util.EnumMap;
import java.util.Map;

import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.titanshifter.TitanShifter;
import com.projecthero.mod.titanshifter.TitanType;
import com.projecthero.mod.titanshifter.item.TitanShifterItems;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 (bug: "you cannot naturally get other titan shifters, only Attack Titan"): every Titan type can be obtained in
 * survival. The Titan Serum (crafted) lets the drinker pick any of them; the random-power serums (Heroic / Prismatic),
 * which used to always hand out the Attack Titan, now roll a weighted random Titan. The admin grant stays the Attack Titan.
 */
public class TitanAcquisitionGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	/** The crafted Titan Serum's picker reaches every type, and the serum has a survival crafting recipe. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void theCraftedTitanSerumGivesEveryTitan(GameTestHelper helper) {
		boolean craftable = helper.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
				.anyMatch(r -> r.value().getResultItem(helper.getLevel().registryAccess()).is(TitanShifterItems.TITAN_SERUM));
		helper.assertTrue(craftable, "the Titan Serum has a crafting recipe");
		for (TitanType type : TitanType.values()) {
			ServerPlayer p = mock(helper);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TitanShifterItems.TITAN_SERUM));
			p.getItemInHand(InteractionHand.MAIN_HAND).use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
			helper.assertTrue(TitanShifter.pickPending(p), "drinking opens the picker (" + type.id + ")");
			helper.assertTrue(TitanShifter.choose(p, type), "picked " + type.id);
			helper.assertTrue(TitanShifter.isShifter(p) && TitanShifter.selectedType(p) == type, "a " + type.id + " shifter");
			helper.assertTrue(p.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(), "the serum is used up (" + type.id + ")");
			TitanShifter.revoke(p);
		}
		helper.succeed();
	}

	/**
	 * The random serums' grant ({@code PowerGrants.grantHero(player, "titan_shifter", false)}, exactly what
	 * {@code RandomPowerSerumItem.grantRandom} calls) hands out every Titan, not only the Attack Titan.
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void theRandomSerumsRollEveryTitan(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		Map<TitanType, Integer> seen = new EnumMap<>(TitanType.class);
		for (int i = 0; i < 300 && seen.size() < TitanType.values().length; i++) {
			helper.assertTrue(PowerGrants.grantHero(p, "titan_shifter", false).ok(), "the serum grant succeeds");
			seen.merge(TitanShifter.selectedType(p), 1, Integer::sum);
			TitanShifter.revoke(p);
		}
		for (TitanType type : TitanType.values()) {
			helper.assertTrue(seen.containsKey(type), "a random serum can give the " + type.id + " (seen " + seen + ")");
		}
		// the admin command is unchanged: always the Attack Titan
		helper.assertTrue(PowerGrants.grantHero(p, "titan_shifter").ok(), "the command grant succeeds");
		helper.assertTrue(TitanShifter.selectedType(p) == TitanType.GENERIC_TITAN, "the command grants the Attack Titan");
		helper.succeed();
	}

	/** The weighting: Attack most common, Armoured rarest, every type possible (seeded, so deterministic). */
	@GameTest(template = EMPTY_STRUCTURE)
	public void theRandomTitanWeightsAreSensible(GameTestHelper helper) {
		RandomSource random = RandomSource.create(1234L);
		Map<TitanType, Integer> counts = new EnumMap<>(TitanType.class);
		int n = 20000;
		for (int i = 0; i < n; i++) {
			counts.merge(TitanType.rollNatural(random), 1, Integer::sum);
		}
		int total = 0;
		for (TitanType t : TitanType.values()) {
			helper.assertTrue(t.naturalWeight() > 0, t.id + " has a weight");
			total += t.naturalWeight();
		}
		for (TitanType t : TitanType.values()) {
			double expected = (double) t.naturalWeight() / total;
			double got = counts.getOrDefault(t, 0) / (double) n;
			helper.assertTrue(Math.abs(got - expected) < 0.02, t.id + " rolled " + got + ", expected about " + expected);
		}
		helper.assertTrue(counts.get(TitanType.GENERIC_TITAN) > counts.get(TitanType.ARMORED_TITAN),
				"the Attack Titan is more common than the Armoured Titan");
		helper.succeed();
	}
}
