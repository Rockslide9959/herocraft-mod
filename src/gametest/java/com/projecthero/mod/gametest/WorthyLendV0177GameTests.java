package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.projecthero.mod.entity.MjolnirEntity;
import com.projecthero.mod.hammer.MjolnirRecall;
import com.projecthero.mod.hammer.ThorWeapon;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.item.ModDataComponents;
import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.power.ThorPowers;
import com.projecthero.mod.power.ThorThrown;
import com.projecthero.mod.squad.Squad;
import com.projecthero.mod.squad.SquadManager;
import com.projecthero.mod.stormbreaker.StormbreakerEntity;
import com.projecthero.mod.supersoldier.SuperSoldier;
import com.projecthero.mod.supersoldier.SuperSoldierSerum;
import com.projecthero.mod.worthiness.Worthiness;
import com.projecthero.mod.worthy.WorthyConfig;
import com.projecthero.mod.worthy.WorthyFlight;
import com.projecthero.mod.worthy.WorthyKit;
import com.projecthero.mod.worthy.WorthyLending;
import com.projecthero.mod.worthy.WorthyLoans;
import com.projecthero.mod.worthy.WorthyMoves;
import com.projecthero.mod.worthy.WorthyState;
import com.projecthero.mod.worthy.WorthyAttachments;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Thor x Super Soldier (user: "Thor squad mate can shift+Right click a super soldier squad mate to assign either Mjolnir or
 * stormbreaker to that player, in the weapon menu thor player can retrieve the weapons back ... super soldier should be able to fly
 * with Mjolnir or stormbreaker"):
 * <ul>
 *   <li>a weapon is lent only to a Super Soldier who is a squadmate; it lands in his main hand, his old item goes to his pack, the loan
 *   names its owner, and a lent Mjolnir is not ejected from him (he is not worthy);</li>
 *   <li>the Call back brings it home -- exactly one copy; leaving the squad, losing the power and dying bring it home too;</li>
 *   <li>the loan survives a save (SavedData round trip, the view rebuilt after a "relog");</li>
 *   <li>the kit follows his hands (weapon only / shield + weapon / Stormbreaker / an unlent axe = nothing), a key fires the kit's move
 *   (C throws the weapon out of his hand and it comes back);</li>
 *   <li>flight only with a lent weapon, and it ends when the weapon goes; R never tears a lent weapon from him; a stranger can't lift
 *   a lent weapon off the ground, its Super Soldier can.</li>
 * </ul>
 * Each test has its own batch. Mock players are on the player list, so the mod's per-player server tick runs for them.
 */
public class WorthyLendV0177GameTests implements FabricGameTest {
	private static ServerPlayer at(GameTestHelper helper, double x, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 pos = helper.absoluteVec(new Vec3(x, 2.0, z));
		p.moveTo(pos.x, pos.y, pos.z, 0.0f, 0.0f);
		return p;
	}

	/** A worthy Thor with Mjolnir bound in the main hand (and a bound Stormbreaker in slot 8 if {@code axe}). */
	private static ServerPlayer thor(GameTestHelper helper, double x, double z, boolean axe) {
		ServerPlayer p = at(helper, x, z);
		Worthiness.setScore(p, Worthiness.TEST_WORTHY_SCORE);
		ItemStack mjolnir = new ItemStack(ModItems.MJOLNIR);
		p.getInventory().selected = 0;
		p.setItemInHand(InteractionHand.MAIN_HAND, mjolnir);
		ThorPowers.toggleBinding(p, mjolnir);
		if (axe) {
			p.getInventory().setItem(8, new ItemStack(ModItems.STORMBREAKER));
			p.getInventory().tick(); // the real binding path: StormbreakerItem#inventoryTick
		}
		return p;
	}

	private static ServerPlayer soldier(GameTestHelper helper, double x, double z) {
		ServerPlayer p = at(helper, x, z);
		helper.assertTrue(SuperSoldierSerum.drink(p, true, 0.99f) == SuperSoldierSerum.Outcome.GRANTED, "the refined serum always takes");
		p.getInventory().selected = 0;
		return p;
	}

	private static Squad squad(GameTestHelper helper, ServerPlayer leader, ServerPlayer... members) {
		SquadManager squads = SquadManager.get(helper.getLevel().getServer());
		Squad s = squads.create("wl" + leader.getUUID().toString().substring(0, 8), leader.getUUID());
		for (ServerPlayer m : members) {
			squads.addMember(s, m.getUUID());
		}
		return s;
	}

	private static UUID id(ItemStack stack) {
		return stack.get(ModDataComponents.HAMMER_ID);
	}

	/** Every copy of weapon {@code id}: in these players (all slots, out on a throw, the cursor) and in the world nearby. */
	private static int copies(GameTestHelper helper, UUID id, ServerPlayer... players) {
		int n = 0;
		for (ServerPlayer p : players) {
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				if (id.equals(id(p.getInventory().getItem(i)))) {
					n++;
				}
			}
			for (InteractionHand hand : InteractionHand.values()) {
				if (id.equals(id(ThorThrown.of(p).in(hand)))) {
					n++;
				}
			}
		}
		List<Entity> world = new ArrayList<>();
		helper.getLevel().getEntities(EntityTypeTest.forClass(Entity.class), new AABB(helper.absolutePos(net.minecraft.core.BlockPos.ZERO)).inflate(48),
				e -> !e.isRemoved() && ((e instanceof MjolnirEntity m && id.equals(id(m.getItem())))
						|| (e instanceof StormbreakerEntity s && id.equals(id(s.getItem())))
						|| (e instanceof ItemEntity i && id.equals(id(i.getItem())))), world);
		return n + world.size();
	}

	private static boolean holds(ServerPlayer p, UUID id) {
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			if (id.equals(id(p.getInventory().getItem(i)))) {
				return true;
			}
		}
		return false;
	}

	private static void cleanUp(GameTestHelper helper, Squad squad, UUID weapon, ServerPlayer... players) {
		WorthyLoans loans = WorthyLoans.get(helper.getLevel().getServer());
		for (ServerPlayer p : players) {
			for (WorthyLoans.Loan loan : loans.involving(p.getUUID())) {
				loans.remove(loan.hammerId());
			}
			p.setAttached(ThorThrownAccess.TYPE, ThorThrown.EMPTY);
		}
		if (weapon != null) {
			helper.getLevel().getEntities(EntityTypeTest.forClass(Entity.class), new AABB(helper.absolutePos(net.minecraft.core.BlockPos.ZERO)).inflate(48),
					e -> (e instanceof MjolnirEntity m && weapon.equals(id(m.getItem()))) || (e instanceof StormbreakerEntity s && weapon.equals(id(s.getItem())))
							|| (e instanceof ItemEntity i && weapon.equals(id(i.getItem())))).forEach(Entity::discard);
		}
		if (squad != null) {
			SquadManager.get(helper.getLevel().getServer()).disband(squad);
		}
		for (ServerPlayer p : players) {
			if (!p.isRemoved()) {
				helper.getLevel().getServer().getPlayerList().remove(p);
			}
		}
	}

	/** ThorThrown's attachment type, for the clean-up. */
	private static final class ThorThrownAccess {
		static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<ThorThrown> TYPE =
				com.projecthero.mod.attachment.ModAttachments.THOR_THROWN;
	}

	// ------------------------------------------------------------------ lending

	@GameTest(template = EMPTY_STRUCTURE, batch = "worthy_v0177_lend", timeoutTicks = 60)
	public void aWeaponIsLentOnlyToASuperSoldierSquadmateIntoHisMainHand(GameTestHelper helper) {
		ServerPlayer thor = thor(helper, 2.5, 2.5, false);
		ServerPlayer stranger = soldier(helper, 4.5, 2.5); // a Super Soldier, not in the squad
		ServerPlayer mortal = at(helper, 2.5, 4.5); // in the squad, no power
		ServerPlayer cap = soldier(helper, 4.5, 4.5);
		Squad squad = squad(helper, thor, mortal, cap);
		UUID hammer = id(thor.getMainHandItem());
		helper.assertTrue(hammer != null && thor.getUUID().equals(thor.getMainHandItem().get(ModDataComponents.BOUND_OWNER)), "Mjolnir is bound to Thor");

		helper.assertFalse(WorthyLending.lend(thor, stranger), "a Super Soldier outside the squad is refused");
		helper.assertFalse(WorthyLending.lend(thor, mortal), "a squadmate who is not a Super Soldier is refused");
		helper.assertTrue(thor.getMainHandItem().is(ModItems.MJOLNIR), "a refusal moves nothing");

		cap.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TORCH, 5));
		helper.assertTrue(WorthyLending.lend(thor, cap), "a Super Soldier squadmate takes it");
		helper.assertTrue(thor.getMainHandItem().isEmpty(), "it left Thor's hand");
		helper.assertTrue(cap.getMainHandItem().is(ModItems.MJOLNIR) && hammer.equals(id(cap.getMainHandItem())), "the same Mjolnir is in his main hand");
		helper.assertTrue(cap.getInventory().countItem(Items.TORCH) == 5, "his torches moved to the pack, none lost");
		WorthyLoans.Loan loan = WorthyLoans.get(helper.getLevel().getServer()).byHammer(hammer);
		helper.assertTrue(loan != null && loan.owner().equals(thor.getUUID()) && loan.borrower().equals(cap.getUUID()), "the loan names its owner");
		helper.assertTrue(thor.getUUID().equals(cap.getMainHandItem().get(ModDataComponents.BOUND_OWNER)), "still bound to Thor");
		helper.assertTrue(WorthyLending.lentOut(thor, ThorWeapon.MJOLNIR) && WorthyState.of(thor).lentTo(ThorWeapon.MJOLNIR.ordinal()) != null,
				"Thor's view shows it lent");
		helper.assertTrue(WorthyKit.kit(cap) == WorthyKit.Kit.MJOLNIR, "Mjolnir alone = the Mjolnir kit");
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(cap.getMainHandItem().is(ModItems.MJOLNIR), "a lent Mjolnir is not ejected from its unworthy Super Soldier");
			helper.assertTrue(copies(helper, hammer, thor, cap) == 1, "one Mjolnir");
			cleanUp(helper, squad, hammer, thor, stranger, mortal, cap);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "worthy_v0177_callback", timeoutTicks = 200)
	public void theCallBackBringsItHomeOneCopyOnly(GameTestHelper helper) {
		ServerPlayer thor = thor(helper, 2.5, 2.5, false);
		ServerPlayer cap = soldier(helper, 5.5, 5.5);
		Squad squad = squad(helper, thor, cap);
		UUID hammer = id(thor.getMainHandItem());
		helper.assertTrue(WorthyLending.lend(thor, cap), "lent");
		// R leaves it alone
		MjolnirRecall.call(thor);
		helper.assertTrue(cap.getMainHandItem().is(ModItems.MJOLNIR), "R does not tear a lent weapon out of his hand");
		helper.assertTrue(WorthyLending.callBack(thor, ThorWeapon.MJOLNIR), "the Call back");
		helper.assertTrue(WorthyLoans.get(helper.getLevel().getServer()).byHammer(hammer) == null, "the loan is over");
		helper.assertFalse(holds(cap, hammer), "it left the Super Soldier at once");
		helper.assertTrue(copies(helper, hammer, thor, cap) == 1, "one copy, on its way");
		helper.succeedWhen(() -> {
			helper.assertTrue(holds(thor, hammer), "back with Thor");
			helper.assertTrue(copies(helper, hammer, thor, cap) == 1, "still exactly one");
			helper.assertTrue(WorthyState.of(thor).lent.isEmpty() && WorthyState.of(cap).borrowed.isEmpty(), "both views cleared");
			cleanUp(helper, squad, hammer, thor, cap);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "worthy_v0177_squad", timeoutTicks = 200)
	public void leavingTheSquadSendsItHome(GameTestHelper helper) {
		ServerPlayer thor = thor(helper, 2.5, 2.5, false);
		ServerPlayer cap = soldier(helper, 5.5, 5.5);
		Squad squad = squad(helper, thor, cap);
		UUID hammer = id(thor.getMainHandItem());
		helper.assertTrue(WorthyLending.lend(thor, cap), "lent");
		SquadManager.get(helper.getLevel().getServer()).removeMember(squad, cap.getUUID());
		helper.succeedWhen(() -> {
			helper.assertTrue(WorthyLoans.get(helper.getLevel().getServer()).byHammer(hammer) == null, "the loan ended with the squad");
			helper.assertTrue(holds(thor, hammer) && !holds(cap, hammer), "back with Thor");
			helper.assertTrue(copies(helper, hammer, thor, cap) == 1, "one copy");
			cleanUp(helper, squad, hammer, thor, cap);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "worthy_v0177_power", timeoutTicks = 200)
	public void losingThePowerSendsItHome(GameTestHelper helper) {
		ServerPlayer thor = thor(helper, 2.5, 2.5, false);
		ServerPlayer cap = soldier(helper, 5.5, 5.5);
		Squad squad = squad(helper, thor, cap);
		UUID hammer = id(thor.getMainHandItem());
		helper.assertTrue(WorthyLending.lend(thor, cap), "lent");
		SuperSoldier.revoke(cap);
		helper.succeedWhen(() -> {
			helper.assertTrue(WorthyLoans.get(helper.getLevel().getServer()).byHammer(hammer) == null, "the loan ended with the power");
			helper.assertTrue(holds(thor, hammer) && !holds(cap, hammer), "back with Thor");
			helper.assertTrue(copies(helper, hammer, thor, cap) == 1, "one copy");
			cleanUp(helper, squad, hammer, thor, cap);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "worthy_v0177_death", timeoutTicks = 200)
	public void dyingSendsItHomeNeverTwo(GameTestHelper helper) {
		ServerPlayer thor = thor(helper, 2.5, 2.5, false);
		ServerPlayer cap = soldier(helper, 5.5, 5.5);
		Squad squad = squad(helper, thor, cap);
		UUID hammer = id(thor.getMainHandItem());
		helper.assertTrue(WorthyLending.lend(thor, cap), "lent");
		cap.hurt(helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
		helper.succeedWhen(() -> {
			helper.assertTrue(WorthyLoans.get(helper.getLevel().getServer()).byHammer(hammer) == null, "the loan ended at the death");
			helper.assertTrue(holds(thor, hammer), "back with Thor");
			helper.assertTrue(copies(helper, hammer, thor, cap) == 1, "one copy (nothing dropped with the body)");
			cleanUp(helper, squad, hammer, thor, cap);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "worthy_v0177_persist", timeoutTicks = 40)
	public void theLoanSurvivesASaveAndTheViewIsRebuilt(GameTestHelper helper) {
		ServerPlayer thor = thor(helper, 2.5, 2.5, false);
		ServerPlayer cap = soldier(helper, 5.5, 5.5);
		Squad squad = squad(helper, thor, cap);
		UUID hammer = id(thor.getMainHandItem());
		helper.assertTrue(WorthyLending.lend(thor, cap), "lent");
		WorthyLoans loans = WorthyLoans.get(helper.getLevel().getServer());
		CompoundTag saved = loans.save(new CompoundTag(), helper.getLevel().registryAccess());
		WorthyLoans reloaded = WorthyLoans.load(saved, helper.getLevel().registryAccess());
		WorthyLoans.Loan back = reloaded.byHammer(hammer);
		helper.assertTrue(back != null && back.equals(loans.byHammer(hammer)), "the SavedData round trip keeps the loan exactly");
		WorthyState s = WorthyState.fromTag(WorthyState.of(cap).toTag());
		helper.assertTrue(s.borrows(hammer), "the attachment round trip keeps the view");
		// a "relog" with a blank view: the login rebuilds it from the SavedData
		cap.setAttached(WorthyAttachments.STATE, new WorthyState());
		helper.assertFalse(WorthyLending.isLent(cap, cap.getMainHandItem()), "blank view");
		WorthyLending.validate(cap);
		helper.assertTrue(WorthyLending.isLent(cap, cap.getMainHandItem()), "the view is rebuilt from the loans");
		cleanUp(helper, squad, hammer, thor, cap);
		helper.succeed();
	}

	// ------------------------------------------------------------------ the kits

	@GameTest(template = EMPTY_STRUCTURE, batch = "worthy_v0177_kits", timeoutTicks = 80)
	public void theKitFollowsHisHandsAndAKeyFiresIt(GameTestHelper helper) {
		ServerPlayer thor = thor(helper, 2.5, 2.5, true);
		ServerPlayer cap = soldier(helper, 5.5, 5.5);
		ServerPlayer other = soldier(helper, 5.5, 2.5);
		Squad squad = squad(helper, thor, cap);
		UUID hammer = id(thor.getMainHandItem());
		helper.assertTrue(WorthyKit.kit(other) == WorthyKit.Kit.NONE, "no weapon, no kit");
		other.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.STORMBREAKER));
		helper.assertTrue(WorthyKit.kit(other) == WorthyKit.Kit.NONE, "a Stormbreaker nobody lent him gives no kit");
		helper.assertTrue(WorthyLending.lend(thor, cap), "Mjolnir lent");
		helper.assertTrue(WorthyKit.kit(cap) == WorthyKit.Kit.MJOLNIR, "weapon only");
		cap.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
		helper.assertTrue(WorthyKit.kit(cap) == WorthyKit.Kit.SHIELD_MJOLNIR, "shield in the off hand = the shield + weapon kit");
		cap.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		// the second weapon: Thor takes the axe into his hand and lends it too -- it is in the main hand now, Mjolnir in the pack
		thor.getInventory().selected = 8;
		UUID axe = id(thor.getMainHandItem());
		helper.assertTrue(thor.getMainHandItem().is(ModItems.STORMBREAKER) && axe != null, "Thor holds his bound axe");
		helper.assertTrue(WorthyLending.lend(thor, cap), "Stormbreaker lent too");
		helper.assertTrue(WorthyKit.kit(cap) == WorthyKit.Kit.STORMBREAKER, "the axe in the main hand = the Stormbreaker kit");
		helper.assertTrue(holds(cap, hammer), "Mjolnir stepped into his pack, kept");
		// C throws the axe out of his hand; it comes back
		helper.assertTrue(WorthyKit.handle(cap, AbilitySlot.SLOT_6, true), "the kit takes the key");
		helper.assertTrue(ThorThrown.of(cap).has(ThorWeapon.STORMBREAKER) && cap.getMainHandItem().isEmpty(), "the axe left his hand");
		helper.assertTrue(WorthyMoves.cooldownRemaining(cap, WorthyKit.AXE_THROW) > 0, "the throw's cooldown started");
		helper.assertTrue(WorthyKit.kit(cap) == WorthyKit.Kit.STORMBREAKER, "still the axe kit while it flies");
		helper.runAfterDelay(WorthyConfig.THROW_OUT_TICKS + WorthyConfig.THROW_BACK_TICKS + 4, () -> {
			helper.assertTrue(cap.getMainHandItem().is(ModItems.STORMBREAKER) && axe.equals(id(cap.getMainHandItem())), "the axe is back in his hand");
			helper.assertTrue(copies(helper, axe, thor, cap) == 1 && copies(helper, hammer, thor, cap) == 1, "one of each");
			cleanUp(helper, squad, null, thor, cap, other);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "worthy_v0177_flight", timeoutTicks = 120)
	public void flightOnlyWithALentWeaponAndItEndsWhenTheWeaponGoes(GameTestHelper helper) {
		ServerPlayer thor = thor(helper, 2.5, 2.5, false);
		ServerPlayer cap = soldier(helper, 5.5, 5.5);
		Squad squad = squad(helper, thor, cap);
		UUID hammer = id(thor.getMainHandItem());
		cap.setNoGravity(true);
		helper.assertFalse(WorthyFlight.toggle(cap), "no lent weapon, no flight");
		helper.assertFalse(WorthyFlight.isFlying(cap), "not flying");
		helper.assertTrue(WorthyLending.lend(thor, cap), "lent");
		helper.assertTrue(WorthyFlight.toggle(cap), "with the lent weapon he takes off");
		helper.assertTrue(WorthyFlight.isFlying(cap) && cap.getAbilities().mayfly, "flying");
		helper.runAfterDelay(4, () -> {
			WorthyLending.callBack(thor, ThorWeapon.MJOLNIR);
			helper.runAfterDelay(3, () -> {
				helper.assertFalse(WorthyFlight.isFlying(cap), "the weapon is gone, so is the flight");
				helper.assertFalse(cap.getAbilities().mayfly, "vanilla flight taken back");
				helper.succeedWhen(() -> {
					helper.assertTrue(holds(thor, hammer), "home");
					cleanUp(helper, squad, hammer, thor, cap);
				});
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "worthy_v0177_lift", timeoutTicks = 120)
	public void aStrangerCantLiftALentWeaponItsSuperSoldierCan(GameTestHelper helper) {
		ServerPlayer thor = thor(helper, 2.5, 2.5, false);
		ServerPlayer cap = soldier(helper, 4.5, 4.5);
		ServerPlayer stranger = at(helper, 5.5, 4.5);
		Squad squad = squad(helper, thor, cap);
		UUID hammer = id(thor.getMainHandItem());
		helper.assertTrue(WorthyLending.lend(thor, cap), "lent");
		// he puts it down: a resting hammer at his feet
		ItemStack stack = cap.getMainHandItem();
		cap.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		MjolnirEntity resting = MjolnirEntity.createResting(helper.getLevel(), cap, stack, cap.position(), Vec3.ZERO, true);
		helper.getLevel().addFreshEntity(resting);
		helper.runAfterDelay(45, () -> { // past the pickup grace
			resting.interact(stranger, InteractionHand.MAIN_HAND);
			helper.assertTrue(!resting.isRemoved() && !holds(stranger, hammer), "a stranger can't lift it");
			helper.assertFalse(WorthyLending.mayLift(stranger, stack), "nor may he");
			resting.interact(cap, InteractionHand.MAIN_HAND);
			helper.assertTrue(holds(cap, hammer), "its Super Soldier picks it back up");
			helper.assertTrue(copies(helper, hammer, thor, cap, stranger) == 1, "one copy");
			cleanUp(helper, squad, hammer, thor, cap, stranger);
			helper.succeed();
		});
	}
}
