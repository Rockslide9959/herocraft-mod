package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.entity.MjolnirEntity;
import com.projecthero.mod.hammer.MjolnirRecall;
import com.projecthero.mod.hammer.ThorWeapon;
import com.projecthero.mod.hammer.ThorWeaponHands;
import com.projecthero.mod.item.ModDataComponents;
import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.power.StormEnergy;
import com.projecthero.mod.power.ThorPowers;
import com.projecthero.mod.power.ThorState;
import com.projecthero.mod.power.ThorThrown;
import com.projecthero.mod.power.ThorTwinKit;
import com.projecthero.mod.stormbreaker.StormbreakerEntity;
import com.projecthero.mod.worthiness.Worthiness;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Thor (user: "make the weapons actually leave the players hands and then return after" / "if player calls a weapon to
 * themself while having a weapon in their inventory then the second weapon goes into the offhand"):
 * <ul>
 *   <li>Twin Throw really takes both stacks out of the hands ({@link ThorThrown}), keeps Twin Gods while they fly, and lands each
 *   back in the hand it left -- never a second copy;</li>
 *   <li>a thrown Mjolnir / Stormbreaker entity comes back to the OFF hand it was thrown from;</li>
 *   <li>{@link ThorWeaponHands}: a called weapon takes the off hand next to the other one (the off-hand item goes to the pack, or
 *   drops when it is full -- never deleted); the other weapon in the pack is brought to the main hand; otherwise as before;</li>
 *   <li>safety: the out-stack survives a save (codec), an overdue one comes straight back, death hands it back first, and the
 *   call key never rebuilds a weapon that is out on a throw.</li>
 * </ul>
 * Each test has its own batch, high above the structure; mock players are on the player list, so the per-player tick
 * ({@code ThorPowers.serverTick} -> {@code ThorThrown.tick}) runs for them.
 */
public class ThorThrowHandsV0177GameTests implements FabricGameTest {
	private static final int ALT = 70;

	private static ServerPlayer thor(GameTestHelper helper, Item main, Item off) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 pos = helper.absoluteVec(new Vec3(2.5, 2.0 + ALT, 2.5));
		p.moveTo(pos.x, pos.y, pos.z, 0.0f, 0.0f);
		p.setNoGravity(true);
		Worthiness.setScore(p, Worthiness.TEST_WORTHY_SCORE);
		p.setAttached(ModAttachments.BOUND_HAMMER_ID, UUID.randomUUID());
		p.getInventory().selected = 0;
		p.setItemInHand(InteractionHand.MAIN_HAND, main == null ? ItemStack.EMPTY : new ItemStack(main));
		p.setItemInHand(InteractionHand.OFF_HAND, off == null ? ItemStack.EMPTY : new ItemStack(off));
		StormEnergy.set(p, StormEnergy.MAX);
		return p;
	}

	/** Every copy of {@code item} on the player (all slots), out on a throw, and in the world near the test. */
	private static int copies(GameTestHelper helper, ServerPlayer p, Item item) {
		int n = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			if (p.getInventory().getItem(i).is(item)) {
				n++;
			}
		}
		ThorThrown out = ThorThrown.of(p);
		if (out.main().is(item)) {
			n++;
		}
		if (out.off().is(item)) {
			n++;
		}
		return n + worldCopies(helper, p, item).size();
	}

	private static List<Entity> worldCopies(GameTestHelper helper, ServerPlayer p, Item item) {
		List<Entity> out = new ArrayList<>();
		helper.getLevel().getEntities(EntityTypeTest.forClass(Entity.class), new AABB(p.blockPosition()).inflate(24), e -> !e.isRemoved()
				&& ((e instanceof MjolnirEntity m && m.getItem().is(item)) || (e instanceof StormbreakerEntity s && s.getItem().is(item))
						|| (e instanceof ItemEntity i && i.getItem().is(item))), out);
		return out;
	}

	private static void leave(GameTestHelper helper, ServerPlayer p) {
		for (Item item : new Item[] { ModItems.MJOLNIR, ModItems.STORMBREAKER, Items.TORCH }) {
			worldCopies(helper, p, item).forEach(Entity::discard);
		}
		p.setAttached(ModAttachments.THOR_THROWN, ThorThrown.EMPTY);
		if (!p.isRemoved()) {
			helper.getLevel().getServer().getPlayerList().remove(p);
		}
	}

	// ------------------------------------------------------------------ request 1: the weapons leave the hands

	@GameTest(template = EMPTY_STRUCTURE, batch = "thor_hands_v0177_twin", timeoutTicks = 80)
	public void twinThrowTakesBothWeaponsOutOfTheHandsAndBackIntoTheSameHands(GameTestHelper helper) {
		ServerPlayer p = thor(helper, ModItems.STORMBREAKER, ModItems.MJOLNIR); // the "other way round" grip on purpose
		helper.assertTrue(ThorState.of(p) == ThorState.TWIN, "Twin Gods before the throw");
		ThorTwinKit.twinThrow(p);
		helper.runAfterDelay(ThorTwinKit.THROW_WINDUP + 3, () -> {
			helper.assertTrue(p.getMainHandItem().isEmpty() && p.getOffhandItem().isEmpty(), "both hands are empty while they fly");
			ThorThrown out = ThorThrown.of(p);
			helper.assertTrue(out.main().is(ModItems.STORMBREAKER) && out.off().is(ModItems.MJOLNIR),
					"each weapon is out from its own hand, got main=" + out.main() + " off=" + out.off());
			helper.assertTrue(ThorState.of(p) == ThorState.TWIN, "still Twin Gods while they fly (perks and flight carry on)");
			helper.assertTrue(copies(helper, p, ModItems.MJOLNIR) == 1 && copies(helper, p, ModItems.STORMBREAKER) == 1,
					"one of each, no copies");
		});
		helper.runAfterDelay(ThorTwinKit.THROW_HOME + 4, () -> {
			helper.assertTrue(p.getMainHandItem().is(ModItems.STORMBREAKER), "Stormbreaker is back in the main hand it left");
			helper.assertTrue(p.getOffhandItem().is(ModItems.MJOLNIR), "Mjolnir is back in the off hand it left");
			helper.assertTrue(ThorThrown.of(p).isEmpty(), "nothing left out");
			helper.assertTrue(copies(helper, p, ModItems.MJOLNIR) == 1 && copies(helper, p, ModItems.STORMBREAKER) == 1,
					"still one of each");
			leave(helper, p);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "thor_hands_v0177_entity", timeoutTicks = 120)
	public void aThrownWeaponComesBackToTheOffHandItLeft(GameTestHelper helper) {
		ServerPlayer p = thor(helper, ModItems.STORMBREAKER, ModItems.MJOLNIR);
		ItemStack hammer = p.getOffhandItem().copy();
		p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		MjolnirEntity e = new MjolnirEntity(helper.getLevel(), p);
		e.setItem(hammer);
		e.setThrownFrom(InteractionHand.OFF_HAND);
		Vec3 at = helper.absoluteVec(new Vec3(5.5, 3.0 + ALT, 5.5));
		e.setPos(at.x, at.y, at.z);
		helper.getLevel().addFreshEntity(e);
		e.beginReturn();
		helper.runAfterDelay(2, () -> helper.assertTrue(e.catchHand() == InteractionHand.OFF_HAND,
				"the returning hammer is headed for the off hand, got " + e.catchHand()));
		helper.succeedWhen(() -> {
			helper.assertTrue(e.isRemoved(), "the hammer has arrived");
			helper.assertTrue(p.getOffhandItem().is(ModItems.MJOLNIR), "Mjolnir is back in the off hand");
			helper.assertTrue(p.getMainHandItem().is(ModItems.STORMBREAKER), "Stormbreaker never moved -- still Twin Gods");
			helper.assertTrue(copies(helper, p, ModItems.MJOLNIR) == 1, "one hammer");
			leave(helper, p);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "thor_hands_v0177_axe", timeoutTicks = 120)
	public void aThrownStormbreakerComesBackToTheOffHandItLeft(GameTestHelper helper) {
		ServerPlayer p = thor(helper, ModItems.MJOLNIR, ModItems.STORMBREAKER);
		ItemStack axe = p.getOffhandItem().copy();
		p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		StormbreakerEntity e = new StormbreakerEntity(helper.getLevel(), p);
		e.setItem(axe);
		e.setThrownFrom(InteractionHand.OFF_HAND);
		Vec3 at = helper.absoluteVec(new Vec3(5.5, 3.0 + ALT, 5.5));
		e.setPos(at.x, at.y, at.z);
		helper.getLevel().addFreshEntity(e);
		e.beginReturn();
		helper.succeedWhen(() -> {
			helper.assertTrue(e.isRemoved(), "the axe has arrived");
			helper.assertTrue(p.getOffhandItem().is(ModItems.STORMBREAKER), "Stormbreaker is back in the off hand");
			helper.assertTrue(p.getMainHandItem().is(ModItems.MJOLNIR), "Mjolnir never moved");
			helper.assertTrue(copies(helper, p, ModItems.STORMBREAKER) == 1, "one axe");
			leave(helper, p);
		});
	}

	// ------------------------------------------------------------------ request 2: calling into the off hand

	@GameTest(template = EMPTY_STRUCTURE, batch = "thor_hands_v0177_call")
	public void aCalledWeaponTakesTheOffHandAndTheOffHandItemGoesToThePack(GameTestHelper helper) {
		ServerPlayer p = thor(helper, ModItems.STORMBREAKER, Items.TORCH);
		helper.assertTrue(ThorWeaponHands.plan(p, ThorWeapon.MJOLNIR, null, true) == InteractionHand.OFF_HAND, "planned: off hand");
		ThorWeaponHands.Landing l = ThorWeaponHands.place(p, new ItemStack(ModItems.MJOLNIR), ThorWeapon.MJOLNIR, null, true);
		helper.assertTrue(l == ThorWeaponHands.Landing.OFF, "landed in the off hand, got " + l);
		helper.assertTrue(p.getMainHandItem().is(ModItems.STORMBREAKER) && p.getOffhandItem().is(ModItems.MJOLNIR), "dual wielding");
		helper.assertTrue(p.getInventory().countItem(Items.TORCH) == 1, "the torch moved into the pack");
		helper.assertTrue(ThorState.of(p) == ThorState.TWIN, "Twin Gods");
		leave(helper, p);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "thor_hands_v0177_full")
	public void withAFullPackTheOffHandItemIsDroppedNeverDeleted(GameTestHelper helper) {
		ServerPlayer p = thor(helper, ModItems.MJOLNIR, Items.TORCH);
		Inventory inv = p.getInventory();
		for (int i = 0; i < inv.items.size(); i++) {
			if (i != inv.selected) {
				inv.items.set(i, new ItemStack(Items.DIRT, 64));
			}
		}
		ThorWeaponHands.place(p, new ItemStack(ModItems.STORMBREAKER), ThorWeapon.STORMBREAKER, null, true);
		helper.assertTrue(p.getOffhandItem().is(ModItems.STORMBREAKER), "the axe takes the off hand");
		helper.assertTrue(worldCopies(helper, p, Items.TORCH).size() == 1, "the torch dropped at his feet");
		leave(helper, p);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "thor_hands_v0177_hotbar")
	public void theOtherWeaponOnTheHotbarIsSelectedAndTheCalledOneTakesTheOffHand(GameTestHelper helper) {
		ServerPlayer p = thor(helper, Items.IRON_SWORD, null);
		p.getInventory().setItem(3, new ItemStack(ModItems.STORMBREAKER));
		helper.assertTrue(ThorWeaponHands.plan(p, ThorWeapon.MJOLNIR, null, true) == InteractionHand.OFF_HAND, "planned: off hand");
		ThorWeaponHands.place(p, new ItemStack(ModItems.MJOLNIR), ThorWeapon.MJOLNIR, null, true);
		helper.assertTrue(p.getInventory().selected == 3, "the Stormbreaker slot is selected");
		helper.assertTrue(p.getMainHandItem().is(ModItems.STORMBREAKER) && p.getOffhandItem().is(ModItems.MJOLNIR), "dual wielding");
		helper.assertTrue(p.getInventory().getItem(0).is(Items.IRON_SWORD), "the sword stays in its own slot");
		leave(helper, p);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "thor_hands_v0177_backpack")
	public void theOtherWeaponInTheBackpackIsSwappedIntoTheMainHand(GameTestHelper helper) {
		ServerPlayer p = thor(helper, Items.IRON_SWORD, null);
		p.getInventory().setItem(20, new ItemStack(ModItems.MJOLNIR));
		ThorWeaponHands.place(p, new ItemStack(ModItems.STORMBREAKER), ThorWeapon.STORMBREAKER, null, true);
		helper.assertTrue(p.getMainHandItem().is(ModItems.MJOLNIR), "Mjolnir comes out of the backpack into the main hand");
		helper.assertTrue(p.getOffhandItem().is(ModItems.STORMBREAKER), "the called axe takes the off hand");
		helper.assertTrue(p.getInventory().getItem(20).is(Items.IRON_SWORD), "the sword took Mjolnir's slot");
		leave(helper, p);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "thor_hands_v0177_legacy")
	public void withNoOtherWeaponItLandsAsBefore(GameTestHelper helper) {
		ServerPlayer p = thor(helper, null, Items.TORCH);
		helper.assertTrue(ThorWeaponHands.place(p, new ItemStack(ModItems.MJOLNIR), ThorWeapon.MJOLNIR, null, true)
				== ThorWeaponHands.Landing.MAIN, "an empty main hand takes it");
		helper.assertTrue(p.getOffhandItem().is(Items.TORCH), "the off hand is left alone");
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD)); // (replaces that hammer)
		helper.assertTrue(ThorWeaponHands.place(p, new ItemStack(ModItems.MJOLNIR), ThorWeapon.MJOLNIR, null, false)
				== ThorWeaponHands.Landing.PACK, "a busy main hand: the pack");
		helper.assertTrue(p.getMainHandItem().is(Items.IRON_SWORD) && p.getOffhandItem().is(Items.TORCH), "nothing displaced");
		leave(helper, p);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "thor_hands_v0177_both")
	public void rWithBothInThePackPutsThemInBothHands(GameTestHelper helper) {
		ServerPlayer p = thor(helper, null, null);
		p.getInventory().setItem(5, new ItemStack(ModItems.STORMBREAKER));
		p.getInventory().setItem(22, new ItemStack(ModItems.MJOLNIR));
		helper.assertTrue(ThorWeaponHands.arrangeDual(p), "something moved");
		helper.assertTrue(p.getMainHandItem().is(ModItems.STORMBREAKER), "the hotbar axe is selected for the main hand");
		helper.assertTrue(p.getOffhandItem().is(ModItems.MJOLNIR), "Mjolnir comes out of the backpack into the off hand");
		helper.assertFalse(ThorWeaponHands.arrangeDual(p), "already dual wielding: nothing more moves");
		helper.assertTrue(copies(helper, p, ModItems.MJOLNIR) == 1 && copies(helper, p, ModItems.STORMBREAKER) == 1, "one of each");
		leave(helper, p);
		helper.succeed();
	}

	// ------------------------------------------------------------------ safety

	@GameTest(template = EMPTY_STRUCTURE, batch = "thor_hands_v0177_save")
	public void aWeaponOutOnAThrowSurvivesASaveAndAnOverdueOneComesStraightBack(GameTestHelper helper) {
		ServerPlayer p = thor(helper, ModItems.MJOLNIR, null);
		ItemStack hammer = p.getMainHandItem();
		hammer.set(ModDataComponents.HAMMER_ID, UUID.randomUUID());
		helper.assertTrue(ThorThrown.launch(p, ThorWeapon.MJOLNIR, 40) == InteractionHand.MAIN_HAND, "launched from the main hand");
		helper.assertTrue(ThorThrown.launch(p, ThorWeapon.MJOLNIR, 40) == null, "the same weapon cannot be thrown twice");
		var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		Tag saved = ThorThrown.CODEC.encodeStart(ops, ThorThrown.of(p)).getOrThrow();
		ThorThrown loaded = ThorThrown.CODEC.parse(ops, saved).getOrThrow();
		helper.assertTrue(ItemStack.isSameItemSameComponents(loaded.main(), ThorThrown.of(p).main()), "the stack survives a save whole");
		helper.assertTrue(loaded.mainBack() == ThorThrown.of(p).mainBack(), "and its return time");
		// an overdue one (a relog after the return time) comes back on the next tick
		p.setAttached(ModAttachments.THOR_THROWN, new ThorThrown(loaded.main(), ItemStack.EMPTY, helper.getLevel().getGameTime() - 5, 0L));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(p.getMainHandItem().is(ModItems.MJOLNIR), "back in the hand");
			helper.assertTrue(ThorThrown.of(p).isEmpty(), "nothing left out");
			helper.assertTrue(copies(helper, p, ModItems.MJOLNIR) == 1, "one hammer");
			leave(helper, p);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "thor_hands_v0177_taken")
	public void aHandFilledMeanwhileSendsItToThePackNotOverTheItem(GameTestHelper helper) {
		ServerPlayer p = thor(helper, ModItems.STORMBREAKER, null);
		ThorThrown.launch(p, ThorWeapon.STORMBREAKER, 3);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE)); // he grabbed something mid-throw
		helper.runAfterDelay(6, () -> {
			helper.assertTrue(p.getMainHandItem().is(Items.IRON_PICKAXE), "the pickaxe is not replaced");
			helper.assertTrue(p.getInventory().countItem(ModItems.STORMBREAKER) == 1, "the axe went into the pack");
			helper.assertTrue(copies(helper, p, ModItems.STORMBREAKER) == 1, "one axe");
			leave(helper, p);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "thor_hands_v0177_death")
	public void dyingMidThrowHandsTheWeaponBackBeforeAnythingDrops(GameTestHelper helper) {
		ServerPlayer p = thor(helper, ModItems.STORMBREAKER, null);
		ThorThrown.launch(p, ThorWeapon.STORMBREAKER, 100);
		helper.assertTrue(p.getMainHandItem().isEmpty(), "out of the hand");
		p.die(helper.getLevel().damageSources().generic());
		helper.assertTrue(ThorThrown.of(p).isEmpty(), "nothing left out on the dead player");
		helper.assertTrue(copies(helper, p, ModItems.STORMBREAKER) == 1,
				"exactly one axe -- in the inventory or dropped -- got " + copies(helper, p, ModItems.STORMBREAKER));
		leave(helper, p);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "thor_hands_v0177_recall")
	public void theCallKeyNeverRebuildsAWeaponThatIsOutOnAThrow(GameTestHelper helper) {
		ServerPlayer p = thor(helper, ModItems.MJOLNIR, null);
		p.setAttached(ModAttachments.BOUND_HAMMER_ID, null);
		ThorPowers.toggleBinding(p, p.getMainHandItem()); // a real binding, with a registry record
		ThorThrown.launch(p, ThorWeapon.MJOLNIR, 40);
		helper.assertTrue(MjolnirRecall.call(p), "the call is acknowledged");
		helper.assertTrue(ThorThrown.of(p).main().is(ModItems.MJOLNIR), "the hammer is still out on its throw");
		helper.assertTrue(worldCopies(helper, p, ModItems.MJOLNIR).isEmpty(), "and no second one was sent flying in");
		helper.assertTrue(copies(helper, p, ModItems.MJOLNIR) == 1, "one hammer");
		leave(helper, p);
		helper.succeed();
	}
}
