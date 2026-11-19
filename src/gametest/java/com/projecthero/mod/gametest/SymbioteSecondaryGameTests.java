package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.kryptonian.Kryptonian;
import com.projecthero.mod.kryptonian.KryptonianAbilityManager;
import com.projecthero.mod.symbiote.Symbiote;
import com.projecthero.mod.symbiote.SymbioteAbilityManager;
import com.projecthero.mod.symbiote.SymbioteBareHands;
import com.projecthero.mod.symbiote.SymbioteKeys;
import com.projecthero.mod.symbiote.SymbioteKeys.HAction;
import com.projecthero.mod.symbiote.SymbioteState;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 (user: "since symbiote is a secondary power for now can we make it able to be used with any other power, you only get
 * symbiote moveset if player transforms into symbiote armour while having a primary power, if H does something for the primary power
 * already while having symbiote then make it shift+H to transform into symbiote"): the Symbiote stacks on any Primary without replacing
 * it; suit off, the Primary keeps the keys and the Symbiote is dormant; suit on, the Symbiote kit owns them (the Primary is still held)
 * and gives them back when the suit comes off; the H / Shift+H table; another power's suit blocks the suit-up. Mock players are not
 * ticked, so the tests drive the suit clock themselves.
 */
public class SymbioteSecondaryGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		return p;
	}

	/** H, with the suit clock run to the end (on or off). */
	private static void toggleSuit(ServerPlayer p) {
		Symbiote.state(p).toggleReadyAt = 0L;
		Symbiote.toggle(p);
		Symbiote.state(p).transformStartTick -= 10_000L;
		Symbiote.tick(p);
	}

	private static long spikeCooldown(ServerPlayer p) {
		SymbioteState s = Symbiote.state(p);
		return Math.max(0L, s.abilityCooldowns.get(1) - p.level().getGameTime());
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void untransformedThePrimaryKeepsItsKeys(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		Kryptonian.grant(p);
		helper.assertTrue(Symbiote.grant(p), "bonded on top of the Kryptonian");
		helper.assertTrue(Kryptonian.hasPower(p) && Symbiote.hasSymbiote(p), "both held");
		helper.assertTrue(Symbiote.stackedNow(p) && Symbiote.withPrimary(p), "a stacked Normal host (synced flag set on grant)");
		helper.assertTrue(Symbiote.dormant(p), "suit off: the Symbiote is dormant");
		helper.assertTrue(KryptonianAbilityManager.hasContext(p) && !SymbioteAbilityManager.hasContext(p), "the Kryptonian has the keys");
		helper.assertFalse(SymbioteBareHands.applies(p), "no Symbiote tool hands while dormant");
		AbilityRouter.handleInput(p, 2, true);
		AbilityRouter.handleInput(p, 2, false);
		helper.assertTrue(spikeCooldown(p) == 0L, "G did not reach the Symbiote kit (no Spike Shot cooldown)");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void suitOnTheSymbioteOwnsTheKeysAndGivesThemBack(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		Kryptonian.grant(p);
		Symbiote.grant(p);
		toggleSuit(p);
		helper.assertTrue(Symbiote.isActive(p), "suited");
		helper.assertTrue(Symbiote.ownsKeysOverPrimary(p) && !Symbiote.dormant(p), "the suit owns the keys over the Primary");
		helper.assertTrue(SymbioteAbilityManager.hasContext(p), "the Symbiote kit is live");
		helper.assertTrue(Kryptonian.hasPower(p), "the Kryptonian is still held underneath (passives keep running)");
		AbilityRouter.handleInput(p, 2, true);
		AbilityRouter.handleInput(p, 2, false);
		helper.assertTrue(spikeCooldown(p) > 0L, "G went to the Symbiote kit (Spike Shot on cooldown)");
		// H / N are swallowed by the router while suited (no throw)
		AbilityRouter.handleInput(p, 7, true);
		AbilityRouter.handleInput(p, 7, false);
		toggleSuit(p);
		helper.assertFalse(Symbiote.isActive(p), "suit off");
		helper.assertTrue(!SymbioteAbilityManager.hasContext(p) && KryptonianAbilityManager.hasContext(p), "the Kryptonian has the keys again");
		helper.assertTrue(Symbiote.hasSymbiote(p), "and the Symbiote is still bonded");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void noPrimaryIsReplacedEitherWay(GameTestHelper helper) {
		helper.assertTrue(PowerGrants.SECONDARY_KEYS.contains("symbiote"), "a Secondary grant key (an admin grant adds it on top)");
		// Primary first, then the Symbiote
		ServerPlayer a = mock(helper);
		Kryptonian.grant(a);
		Symbiote.grant(a);
		helper.assertTrue(Kryptonian.hasPower(a), "bonding keeps the Primary");
		// the Symbiote first, then a Primary
		ServerPlayer b = mock(helper);
		Symbiote.grant(b);
		Kryptonian.grant(b);
		helper.assertTrue(Symbiote.hasSymbiote(b) && Kryptonian.hasPower(b), "a new Primary keeps the Symbiote");
		// the Symbiote first, then a mutation (serum path)
		ServerPlayer c = mock(helper);
		Symbiote.grant(c);
		helper.assertTrue(PowerGrants.grantExperimental(c, Powers.byKey("power_01_super_strength")), "the mutation is granted");
		helper.assertTrue(Symbiote.hasSymbiote(c) && !ExperimentalPowers.state(c).ownedPowers.isEmpty(), "a mutation keeps the Symbiote too");
		helper.assertTrue(Symbiote.stackedNow(c), "stacked on the mutation");
		// no Primary: the old behaviour -- the kit works with the suit off
		ServerPlayer d = mock(helper);
		Symbiote.grant(d);
		helper.assertTrue(!Symbiote.stackedNow(d) && !Symbiote.dormant(d) && SymbioteAbilityManager.hasContext(d),
				"alone, the Symbiote kit is live with the suit off, as before");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theHKeyTable(GameTestHelper helper) {
		// not stacked: the ordinary chain (H toggles a lone host's suit there, as it always did)
		helper.assertTrue(SymbioteKeys.route(false, false, true, true, true, true) == HAction.PRIMARY, "not stacked: untouched");
		// suit on over a Primary: H and Shift+H take it off
		helper.assertTrue(SymbioteKeys.route(true, true, true, false, false, false) == HAction.SYMBIOTE, "suited: H = suit off");
		helper.assertTrue(SymbioteKeys.route(true, true, true, true, true, true) == HAction.SYMBIOTE, "suited: Shift+H = suit off");
		// the Primary uses plain H (Thor's armour, Sentry, Max Steel, a mutation's Utility 1, ...)
		helper.assertTrue(SymbioteKeys.route(true, false, true, false, false, false) == HAction.PRIMARY, "Primary owns H: H is the Primary's");
		helper.assertTrue(SymbioteKeys.route(true, false, true, true, false, false) == HAction.SYMBIOTE, "Primary owns H: Shift+H = suit");
		helper.assertTrue(SymbioteKeys.route(true, false, true, false, true, true) == HAction.TAP_OR_HOLD,
				"a mutation with an H utility and a wheel: tap = Utility 1, hold = wheel");
		helper.assertTrue(SymbioteKeys.route(true, false, true, true, true, true) == HAction.SYMBIOTE, "and Shift+H is the suit");
		// the Primary does nothing on H
		helper.assertTrue(SymbioteKeys.route(true, false, false, false, false, false) == HAction.SYMBIOTE, "H = suit");
		helper.assertTrue(SymbioteKeys.route(true, false, false, true, false, false) == HAction.SYMBIOTE, "Shift+H = suit (no wheel)");
		helper.assertTrue(SymbioteKeys.route(true, false, false, true, true, false) == HAction.WHEEL, "Shift+H = the wheel when there is one");
		helper.assertTrue(SymbioteKeys.WHEEL_HOLD_TICKS == 10, "the wheel opens after holding H half a second");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void anotherPowersSuitBlocksTheSuitUp(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		Kryptonian.grant(p);
		Symbiote.grant(p);
		// another power's bound suit piece in a slot (as every power suit is synthesised: PowerEquipmentLock.bind)
		net.minecraft.world.item.ItemStack piece = new net.minecraft.world.item.ItemStack(com.projecthero.mod.falcon.item.FalconItems.GOGGLES);
		com.projecthero.mod.armor.PowerEquipmentLock.bind(p, piece);
		p.setItemSlot(EquipmentSlot.HEAD, piece);
		helper.assertTrue(Symbiote.otherFormActive(p), "another power's suit is on");
		toggleSuit(p);
		helper.assertFalse(Symbiote.isActive(p), "the Symbiote waits until it is off");
		p.setItemSlot(EquipmentSlot.HEAD, net.minecraft.world.item.ItemStack.EMPTY);
		helper.assertFalse(Symbiote.otherFormActive(p), "nothing else on");
		toggleSuit(p);
		helper.assertTrue(Symbiote.isActive(p), "now it suits up");
		helper.assertFalse(Symbiote.otherFormActive(p), "its own pieces never count as another suit");
		helper.assertTrue(p.getAttachedOrElse(ModAttachments.SYMBIOTE_WITH_PRIMARY, false), "the synced stacked flag is set");
		helper.succeed();
	}
}
