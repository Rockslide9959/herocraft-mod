package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.allmight.AllMight;
import com.projecthero.mod.allmight.AllMightAbilities;
import com.projecthero.mod.allmight.data.AllMightState;
import com.projecthero.mod.calling.SpawnCallings;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.hero.PowerSuppression;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkKit;
import com.projecthero.mod.quirk.QuirkKits;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.item.QuirkItems;
import com.projecthero.mod.quirk.item.QuirkSerumItem;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7: the Quirk core -- grant / revoke (direct, command, serum, calling), the one-Primary rule against a Miraculous,
 * revoke cleaning up, the costume toggle, the meter's sync flush, overload and Erasure ({@link PowerSuppression}) holding
 * the keys of a Quirk AND of another hero (All Might), Erasure's flight cut and status effect, the serum items and
 * recipes. Mock players are not ticked by the server, so tests call the ticks themselves.
 */
public class QuirkCoreGameTests implements FabricGameTest {
	@SuppressWarnings("removal")
	private static ServerPlayer mock(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.0, 3.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		return p;
	}

	private static int command(ServerPlayer p, String cmd) {
		var src = p.createCommandSourceStack().withPermission(4).withSuppressedOutput();
		p.getServer().getCommands().performPrefixedCommand(src, cmd);
		return 1;
	}

	/** Wraps {@code q}'s real kit, recording every key edge it receives; ALWAYS restore with {@link #restore}. */
	private static final class Recorder implements QuirkKit {
		final QuirkKit real;
		final List<String> calls = new ArrayList<>();

		Recorder(QuirkKit real) {
			this.real = real;
		}

		@Override
		public void handle(ServerPlayer player, AbilitySlot slot, boolean pressed, boolean shift) {
			calls.add(slot.name() + (pressed ? "+" : "-"));
		}

		@Override
		public boolean usableWhileOverloaded(AbilitySlot slot, boolean shift) {
			return slot == AbilitySlot.SLOT_6;
		}

		@Override
		public String[] idsOf(AbilitySlot slot) {
			return real.idsOf(slot);
		}

		@Override
		public int maxCooldown(String abilityId) {
			return real.maxCooldown(abilityId);
		}

		@Override
		public float modifyIncomingDamage(ServerPlayer player, DamageSource source, float amount) {
			return amount;
		}
	}

	// ---------------------------------------------------------------- grant / revoke

	@GameTest(template = EMPTY_STRUCTURE)
	public void grantSwapRevoke(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		helper.assertTrue(Quirks.grant(p, Quirk.HELLFLAME), "granted");
		helper.assertTrue(Quirks.holds(p, Quirk.HELLFLAME), "holds Hellflame");
		helper.assertTrue(!Quirks.grant(p, Quirk.HELLFLAME), "twice refused");
		helper.assertTrue(Quirks.grant(p, Quirk.ENGINE), "swap");
		helper.assertTrue(Quirks.holds(p, Quirk.ENGINE) && !Quirks.holds(p, Quirk.HELLFLAME), "one quirk at a time");
		Quirks.setMeter(p, 140f);
		helper.assertTrue(Quirks.meter(p) == 100f, "meter clamps");
		Quirks.revoke(p);
		helper.assertTrue(!Quirks.hasPower(p), "revoked");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyQuirkIsAHeroTierKey(GameTestHelper helper) {
		for (Quirk q : Quirk.values()) {
			ServerPlayer p = mock(helper);
			helper.assertTrue(HeroTiers.HERO_KEYS.contains(q.heroKey()) && PowerGrants.HERO_TIER_KEYS.contains(q.heroKey()), q.heroKey() + " is a Hero-Tier key");
			helper.assertTrue(PowerGrants.grantHero(p, q.heroKey()).ok(), q.heroKey() + " granted by key");
			helper.assertTrue(Quirks.holds(p, q) && HeroTiers.holdsHero(p, q.heroKey()) && HeroTiers.heroCount(p) == 1, q.id + " held, one hero");
			helper.assertTrue(HeroTiers.hasHeroTier(p), q.id + " counts as a Hero-Tier power");
			helper.assertTrue(PowerGrants.revokeHeroTier(p, q.heroKey()) && !Quirks.hasPower(p), q.id + " revoked by key");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void commandGrantRemoveAndCostume(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		command(p, "projecthero power grant quirk_explosion");
		helper.assertTrue(Quirks.holds(p, Quirk.EXPLOSION), "/projecthero power grant quirk_explosion");
		helper.assertTrue(Quirks.costumeOn(p), "the costume is on by default");
		command(p, "projecthero quirk costume");
		helper.assertFalse(Quirks.costumeOn(p), "/projecthero quirk costume turns it off");
		helper.assertTrue(Quirks.toggleCostume(p) && Quirks.costumeOn(p), "and back on");
		command(p, "projecthero quirk meter 42");
		helper.assertTrue(Quirks.meter(p) == 42f, "/projecthero quirk meter 42");
		command(p, "projecthero power remove quirk_explosion");
		helper.assertFalse(Quirks.hasPower(p), "/projecthero power remove quirk_explosion");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void onePrimaryAgainstTheMiraculous(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		helper.assertTrue(Miraculous.grant(p, Kwami.TIKKI), "Ladybug first");
		helper.assertTrue(Quirks.grant(p, Quirk.ZERO_GRAVITY), "then a Quirk");
		helper.assertTrue(!Miraculous.hasPower(p) && Quirks.holds(p, Quirk.ZERO_GRAVITY) && HeroTiers.heroCount(p) == 1, "the Quirk replaced the Miraculous");
		helper.assertTrue(Miraculous.grant(p, Kwami.PLAGG), "then a Miraculous again");
		helper.assertTrue(!Quirks.hasPower(p) && Miraculous.holds(p, Kwami.PLAGG) && HeroTiers.heroCount(p) == 1, "the Miraculous replaced the Quirk");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void revokeCleansUp(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		Quirks.grant(p, Quirk.OVERHAUL);
		String[] ids = QuirkKits.of(Quirk.OVERHAUL).idsOf(AbilitySlot.SLOT_1);
		Quirks.setCooldown(p, ids.length > 0 ? ids[0] : "quirk_overhaul_test", 200);
		Quirks.setVal(p, "test", 7L);
		Quirks.setVec(p, "test", new Vec3(1, 2, 3));
		Quirks.toggleCostume(p);
		Quirks.overload(p, 200);
		Quirks.setMeter(p, 80f);
		Quirks.revoke(p);
		var s = Quirks.state(p);
		helper.assertTrue(!s.hasPower && s.quirk.isEmpty() && s.vals.isEmpty() && s.vecs.isEmpty() && s.abilityReadyAt.isEmpty()
				&& s.overloadUntil == 0L && s.costume, "the state is fresh again");
		helper.assertTrue(Quirks.meter(p) == 0f, "the meter is forgotten");
		helper.assertTrue(!HeroTiers.hasHeroTier(p), "no Hero-Tier power left");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the meter

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void meterFlushesToTheSyncedState(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		Quirks.grant(p, Quirk.ENGINE);
		Quirks.setMeter(p, 50f);
		Quirks.tick(p);
		helper.assertTrue(Quirks.state(p).meter == 50f, "first change flushes at once, got " + Quirks.state(p).meter);
		Quirks.setMeter(p, 100f);
		helper.runAfterDelay(1, () -> {
			Quirks.tick(p);
			helper.assertTrue(Quirks.state(p).meter == 100f, "hitting 100 flushes at once (edge)");
			helper.assertTrue(Quirks.clientMeter(p) == 100f, "clientMeter reads the synced value");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- overload / Erasure on the keys

	@GameTest(template = EMPTY_STRUCTURE)
	public void overloadAndErasureHoldTheQuirkKeys(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		Quirks.grant(p, Quirk.EXPLOSION);
		QuirkKit real = QuirkKits.of(Quirk.EXPLOSION);
		Recorder rec = new Recorder(real);
		QuirkKits.register(Quirk.EXPLOSION, rec);
		try {
			AbilityRouter.handleInput(p, 1, true);
			helper.assertTrue(rec.calls.contains("SLOT_1+"), "a normal press reaches the kit");
			AbilityRouter.handleInput(p, 1, false);

			Quirks.overload(p, 100);
			rec.calls.clear();
			AbilityRouter.handleInput(p, 2, true);
			helper.assertFalse(rec.calls.contains("SLOT_2+"), "overloaded: the press is held");
			AbilityRouter.handleInput(p, 6, true);
			helper.assertTrue(rec.calls.contains("SLOT_6+"), "overloaded: a usableWhileOverloaded key still works");
			AbilityRouter.handleInput(p, 2, false);
			AbilityRouter.handleInput(p, 6, false);
			Quirks.edit(p, n -> n.overloadUntil = 0L);

			PowerSuppression.suppress(p, 100);
			rec.calls.clear();
			AbilityRouter.handleInput(p, 3, true);
			helper.assertFalse(rec.calls.contains("SLOT_3+"), "Erased: the press is held");
			AbilityRouter.handleInput(p, 3, false);
			helper.assertTrue(rec.calls.contains("SLOT_3-"), "Erased: a release still reaches the kit");
			helper.assertTrue(PowerSuppression.refuse(p), "every power receiver refuses");
			helper.assertFalse(PowerSuppression.refuse(p, StopAction.CAPE_BLOCK_STOP), "a *_STOP action passes");
			PowerSuppression.release(p);
			rec.calls.clear();
			AbilityRouter.handleInput(p, 4, true);
			helper.assertTrue(rec.calls.contains("SLOT_4+"), "released: the keys work again");
		} finally {
			QuirkKits.register(Quirk.EXPLOSION, real);
			PowerSuppression.release(p);
		}
		helper.succeed();
	}

	private enum StopAction {
		CAPE_BLOCK_STOP
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void erasureHoldsAnotherHerosKeys(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		AllMight.grant(p);
		AllMightState s = AllMight.state(p).copy();
		s.fullPower = true;
		p.setAttached(com.projecthero.mod.attachment.ModAttachments.ALL_MIGHT_STATE, s);
		AllMight.reconcile(p);
		try {
			PowerSuppression.suppress(p, 100);
			AbilityRouter.handleInput(p, 1, true); // R = Detroit Smash
			helper.assertTrue(AllMight.cooldownRemaining(p, AllMightAbilities.DETROIT) == 0, "Erased: All Might's Detroit Smash does not start");
			AbilityRouter.handleInput(p, 1, false);
			PowerSuppression.release(p);
			AbilityRouter.handleInput(p, 1, true);
			helper.assertTrue(AllMight.cooldownRemaining(p, AllMightAbilities.DETROIT) > 0, "released: it does");
		} finally {
			PowerSuppression.release(p);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void erasureCutsFlightAndShowsTheEffect(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		Quirks.grant(p, Quirk.DARK_SHADOW);
		p.getAbilities().mayfly = true;
		p.getAbilities().flying = true;
		try {
			PowerSuppression.suppress(p, 60);
			PowerSuppression.tickPlayer(p);
			helper.assertFalse(p.getAbilities().flying || p.getAbilities().mayfly, "power flight is cut");
			helper.assertTrue(p.hasEffect(PowerSuppression.ErasedEffect.HOLDER), "the Erased effect shows");
			helper.assertTrue(com.projecthero.mod.quirk.Quirks.activeAs(p, Quirk.DARK_SHADOW) == false, "activeAs is false while erased");
			PowerSuppression.release(p);
			PowerSuppression.tickPlayer(p);
			helper.assertFalse(p.hasEffect(PowerSuppression.ErasedEffect.HOLDER), "the effect goes when it ends");
		} finally {
			PowerSuppression.release(p);
			PowerSuppression.forget(p);
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- serums / calling

	@GameTest(template = EMPTY_STRUCTURE)
	public void serumsGrantAndRecipesLoad(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		ItemStack stack = new ItemStack(QuirkItems.serumOf(Quirk.ERASURE));
		p.setItemInHand(InteractionHand.MAIN_HAND, stack);
		ItemStack left = stack.getItem().finishUsingItem(stack, p.level(), p);
		helper.assertTrue(Quirks.holds(p, Quirk.ERASURE), "the Erasure serum gives Erasure");
		helper.assertTrue(left.is(Items.GLASS_BOTTLE), "and leaves a bottle");
		helper.assertTrue(QuirkSerumItem.drink(p, Quirk.ERASURE, p.getRandom()) == null, "a second dose does nothing");
		Quirk random = QuirkSerumItem.drink(p, null, p.getRandom());
		helper.assertTrue(random != null && random != Quirk.ERASURE && Quirks.holds(p, random), "the Quirk Trigger gives a different Quirk");
		var recipes = p.getServer().getRecipeManager();
		for (Quirk q : Quirk.values()) {
			helper.assertTrue(recipes.byKey(ProjectHeroMod.id("quirk_serum_" + q.id)).isPresent(), q.id + " serum recipe loads");
		}
		helper.assertTrue(recipes.byKey(ProjectHeroMod.id("quirk_serum_random")).isPresent(), "the Quirk Trigger recipe loads");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void spawnCallingOffersAQuirk(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		SpawnCallings.forceNext = new SpawnCallings.Offer(SpawnCallings.Kind.QUIRK, null, 0L, 0L, false);
		SpawnCallings.Offer o = SpawnCallings.roll(p);
		helper.assertTrue(o != null && o.kind() == SpawnCallings.Kind.QUIRK && o.quirk() != null, "a forced roll picks a Quirk");
		SpawnCallings.offerNow(p, Quirk.HELLFLAME);
		helper.assertTrue(SpawnCallings.pending(p) != null && SpawnCallings.pending(p).quirk() == Quirk.HELLFLAME, "the offer is up");
		helper.assertTrue(SpawnCallings.accept(p), "accepted");
		helper.assertTrue(Quirks.holds(p, Quirk.HELLFLAME) && SpawnCallings.pending(p) == null, "Accept grants the Quirk");
		helper.succeed();
	}
}
