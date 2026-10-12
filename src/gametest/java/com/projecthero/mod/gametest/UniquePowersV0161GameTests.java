package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.hero.UniquePowers;
import com.projecthero.mod.hero.UniquePowersData;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.supersoldier.SuperSoldier;
import com.projecthero.mod.supersoldier.SuperSoldierSerum;
import com.projecthero.mod.supersoldier.item.SuperSoldierItems;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: the {@code projectheroUniquePowers} gamerule. Off: two players may hold the same hero power. On: a second
 * player is refused and told who holds it, the slot frees the moment the holder loses the power (revoke / operator
 * release), duplicates made before the rule was turned on are kept, an operator force grant still works, and a refused
 * serum / Arc Reactor is not used up.
 *
 * <p>Every test turns the rule on for the whole server, so they share their own batch (nothing else runs alongside) and
 * always turn it back off in a {@code finally}. Each starts from an empty holder table: mock players from earlier tests
 * can still be on the player list and holding powers, and the once-a-second reconcile would otherwise have recorded
 * them. Everything happens within the test's first tick, before that reconcile can run again.
 */
public class UniquePowersV0161GameTests implements FabricGameTest {
	private static final String BATCH = "unique_powers_v0161";

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		return p;
	}

	private static MinecraftServer begin(GameTestHelper helper, boolean ruleOn) {
		MinecraftServer server = helper.getLevel().getServer();
		UniquePowersData.get(server).clearAll();
		UniquePowers.setEnabled(server, ruleOn);
		return server;
	}

	private static void end(MinecraftServer server) {
		UniquePowers.setEnabled(server, false);
		UniquePowersData.get(server).clearAll();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH)
	public void ruleOffAllowsDuplicates(GameTestHelper helper) {
		MinecraftServer server = begin(helper, false);
		try {
			ServerPlayer a = player(helper);
			ServerPlayer b = player(helper);
			helper.assertTrue(PowerGrants.grantHero(a, "iron_man").ok(), "first Tony Stark");
			helper.assertTrue(PowerGrants.grantHero(b, "iron_man").ok(), "rule off: a second Tony Stark is fine");
			helper.assertTrue(TonyStark.hasPower(a) && TonyStark.hasPower(b), "both hold it");
			helper.assertTrue(UniquePowersData.get(server).holders("iron_man").size() == 2, "both are recorded even with the rule off");
		} finally {
			end(server);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH)
	public void ruleOnBlocksSecondPlayerAndNamesHolder(GameTestHelper helper) {
		MinecraftServer server = begin(helper, true);
		try {
			ServerPlayer a = player(helper);
			ServerPlayer b = player(helper);
			helper.assertTrue(PowerGrants.grantHero(a, "hulk").ok(), "first Hulk");
			String name = a.getGameProfile().getName();
			helper.assertTrue(name.equals(UniquePowers.blockingHolder(b, "hulk")), "the holder is named");
			PowerGrants.Result r = PowerGrants.grantHero(b, "hulk");
			helper.assertFalse(r.ok(), "rule on: the second Hulk is refused");
			helper.assertFalse(com.projecthero.mod.hulk.Hulk.hasPower(b), "and gets nothing");
			helper.assertTrue(r.message().getContents() instanceof TranslatableContents t
					&& "message.projecthero.unique_powers.taken".equals(t.getKey())
					&& t.getArgs().length == 2 && name.equals(String.valueOf(t.getArgs()[1])), "the refusal names the holder");
			helper.assertTrue(UniquePowers.blockingHolder(a, "hulk") == null, "the holder is never blocked by themself");
			helper.assertTrue(UniquePowers.blockingHolder(b, "thor") == null, "other powers stay free");
			// an operator override still works
			helper.assertTrue(UniquePowers.forced(() -> PowerGrants.grantHero(b, "hulk")).ok(), "force grant");
			helper.assertTrue(com.projecthero.mod.hulk.Hulk.hasPower(b), "force grant gave the power");
		} finally {
			end(server);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH)
	public void slotFreesAfterRevokeAndRelease(GameTestHelper helper) {
		MinecraftServer server = begin(helper, true);
		try {
			ServerPlayer a = player(helper);
			ServerPlayer b = player(helper);
			helper.assertTrue(PowerGrants.grantHero(a, "titan_shifter").ok(), "first Titan Shifter");
			helper.assertFalse(PowerGrants.grantHero(b, "titan_shifter").ok(), "blocked while held");
			PowerGrants.revokeHeroTier(a, "titan_shifter");
			helper.assertTrue(UniquePowers.blockingHolder(b, "titan_shifter") == null, "revoking frees the slot");
			helper.assertTrue(PowerGrants.grantHero(b, "titan_shifter").ok(), "now the second player can have it");
			helper.assertFalse(PowerGrants.grantHero(a, "titan_shifter").ok(), "and the first is the one blocked");

			// an operator release takes it from the (online) holder and opens the slot
			helper.assertTrue(UniquePowers.release(server, "titan_shifter") == 1, "one holder released");
			helper.assertFalse(com.projecthero.mod.titanshifter.TitanShifter.isShifter(b), "the released holder lost it");
			helper.assertTrue(PowerGrants.grantHero(a, "titan_shifter").ok(), "free again after release");
		} finally {
			end(server);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH)
	public void duplicatesFromBeforeTheRuleAreKept(GameTestHelper helper) {
		MinecraftServer server = begin(helper, false);
		try {
			ServerPlayer a = player(helper);
			ServerPlayer b = player(helper);
			ServerPlayer c = player(helper);
			helper.assertTrue(PowerGrants.grantHero(a, "nova").ok() && PowerGrants.grantHero(b, "nova").ok(), "two Novas, rule off");
			UniquePowers.setEnabled(server, true);
			UniquePowers.reconcile(a, null);
			UniquePowers.reconcile(b, null);
			helper.assertTrue(com.projecthero.mod.nova.Nova.hasPower(a) && com.projecthero.mod.nova.Nova.hasPower(b),
					"turning the rule on strips nobody");
			helper.assertFalse(PowerGrants.grantHero(c, "nova").ok(), "but a third is refused");
			PowerGrants.revokeHeroTier(a, "nova");
			helper.assertFalse(PowerGrants.grantHero(c, "nova").ok(), "still refused while any holder remains");
		} finally {
			end(server);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = BATCH)
	public void refusedSerumAndReactorAreNotConsumed(GameTestHelper helper) {
		MinecraftServer server = begin(helper, true);
		try {
			ServerPlayer a = player(helper);
			ServerPlayer b = player(helper);
			helper.assertTrue(SuperSoldierSerum.drink(a, true, 0.0f) == SuperSoldierSerum.Outcome.GRANTED, "first Super Soldier");

			// the refined serum, drunk to the end, is refused and kept (no bottle swap, no roll)
			ItemStack serum = new ItemStack(SuperSoldierItems.REFINED_SERUM);
			ItemStack after = serum.getItem().finishUsingItem(serum, helper.getLevel(), b);
			helper.assertTrue(after == serum && serum.getCount() == 1 && serum.is(SuperSoldierItems.REFINED_SERUM),
					"the refused serum is not used up");
			helper.assertFalse(SuperSoldier.hasPower(b), "and grants nothing");
			// an unrefined one would have rolled for death -- a refusal never rolls
			helper.assertTrue(SuperSoldierSerum.drink(b, false, 0.99f) == SuperSoldierSerum.Outcome.TAKEN, "taken, not rejected");
			helper.assertTrue(b.isAlive(), "no rejection death");

			// the Arc Reactor too
			helper.assertTrue(PowerGrants.grantHero(a, "iron_man").ok(), "the first player becomes Tony Stark instead");
			ServerPlayer c = player(helper);
			c.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(IronManItems.ARC_REACTOR));
			IronManItems.ARC_REACTOR.use(helper.getLevel(), c, InteractionHand.MAIN_HAND);
			helper.assertTrue(c.getMainHandItem().is(IronManItems.ARC_REACTOR) && c.getMainHandItem().getCount() == 1,
					"the refused Arc Reactor is kept");
			helper.assertFalse(TonyStark.hasPower(c), "no Tony Stark");
		} finally {
			end(server);
		}
		helper.succeed();
	}
}
