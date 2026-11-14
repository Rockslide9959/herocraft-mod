package com.projecthero.mod.gametest;

import java.util.UUID;

import com.projecthero.mod.doctorfate.DoctorFate;
import com.projecthero.mod.doctorfate.DoctorFateAbilityManager;
import com.projecthero.mod.doctorfate.DoctorFateConfig;
import com.projecthero.mod.doctorfate.DoctorFateHelmet;
import com.projecthero.mod.doctorfate.DoctorFateSpells;
import com.projecthero.mod.doctorfate.helmet.FateHelmet;
import com.projecthero.mod.doctorfate.helmet.FateHelmetEntity;
import com.projecthero.mod.doctorfate.helmet.FateHelmetGuard;
import com.projecthero.mod.doctorfate.helmet.FateHelmetRegistry;
import com.projecthero.mod.doctorfate.helmet.FateHelmetTag;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.4 Doctor Fate's Helmet of Fate as a real object, and the Dominion retune.
 * <ul>
 *   <li>The no-duplicate rules: H summons the one helmet out of a placed helmet, a dropped item, a chest and another player's
 *   inventory (and nothing is left behind); a double press makes no second one; a copy left in a chunk that wasn't loaded is a ghost
 *   that deletes itself as it loads (entity, item entity, chest, inventory).</li>
 *   <li>Shift+right-click wears only your own helmet, and not during the lockout.</li>
 *   <li>Dominion: each spell's step, and the sum of heavy use (every spell as soon as its cooldown allows) reaching Taking hold in
 *   about 2 minutes.</li>
 * </ul>
 * Mock players aren't ticked by the server, so tests over time drive {@link DoctorFate#tick} themselves.
 */
public class DoctorFateHelmetGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper, double x, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, 2.0, z));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		return p;
	}

	/** Doctor Fate with the helmet off and no copy anywhere yet. */
	private static ServerPlayer fate(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 1.5, 1.5);
		helper.assertTrue(DoctorFate.grant(p), "the grant works");
		DoctorFate.setHelmet(p, false);
		DoctorFate.clearCooldowns(p);
		return p;
	}

	private static FateHelmetRegistry reg(GameTestHelper helper) {
		return FateHelmetRegistry.get(helper.getLevel());
	}

	private static ItemStack liveStack(GameTestHelper helper, ServerPlayer p) {
		return FateHelmet.stack(p, reg(helper).generation(p.getUUID()));
	}

	private static AABB area(GameTestHelper helper) {
		return new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(24.0);
	}

	private static int inInventory(Player p, UUID owner) {
		int n = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			if (FateHelmet.belongsTo(p.getInventory().getItem(i), owner)) {
				n++;
			}
		}
		return n;
	}

	private static void summoned(GameTestHelper helper, ServerPlayer p) {
		helper.assertTrue(DoctorFate.state(p).helmetChangeAt > DoctorFate.now(p) && DoctorFate.state(p).helmetGoingOn,
				"the summon is flying in, then the put-on sequence");
		helper.assertTrue(inInventory(p, p.getUUID()) == 0, "no helmet item lands in his inventory (it goes straight on)");
	}

	// ---------------------------------------------------------------- summoning: no duplicates

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 120)
	public void summonFromAPlacedHelmetLeavesNoCopyAndADoublePressMakesNoSecond(GameTestHelper helper) {
		ServerPlayer p = fate(helper);
		ServerLevel level = helper.getLevel();
		Vec3 spot = helper.absoluteVec(new Vec3(5.5, 2.0, 5.5));
		FateHelmetEntity placed = FateHelmetEntity.place(level, spot, liveStack(helper, p).get(FateHelmet.TAG), 0f, false);
		level.addFreshEntity(placed);
		reg(helper).note(p.getUUID(), FateHelmetRegistry.Where.PLACED, level, placed.blockPosition(), placed.getUUID());
		int gen = reg(helper).generation(p.getUUID());
		helper.assertTrue(DoctorFateHelmet.liveEntities(level, p.getUUID(), area(helper)) == 1, "one helmet set down");
		helper.assertTrue(DoctorFate.pressH(p), "H summons it");
		helper.assertTrue(placed.isRemoved(), "it left the spot it was set down on");
		helper.assertTrue(reg(helper).generation(p.getUUID()) == gen + 1, "a new generation (every old copy is a ghost)");
		summoned(helper, p);
		DoctorFate.clearCooldowns(p);
		helper.assertTrue(!DoctorFate.pressH(p), "a second press while it flies in does nothing");
		helper.assertTrue(DoctorFateHelmet.liveEntities(level, p.getUUID(), area(helper)) == 0 && inInventory(p, p.getUUID()) == 0,
				"and makes no second helmet");
		helper.onEachTick(() -> DoctorFate.tick(p));
		helper.runAfterDelay(DoctorFateConfig.HELMET_SUMMON_TICKS + DoctorFateConfig.HELMET_EQUIP_TICKS + 4, () -> {
			helper.assertTrue(DoctorFate.wearsHelmet(p), "it's on his head");
			helper.assertTrue(DoctorFateHelmet.liveEntities(level, p.getUUID(), area(helper)) == 0 && inInventory(p, p.getUUID()) == 0,
					"and nowhere else");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40)
	public void summonFromADroppedItem(GameTestHelper helper) {
		ServerPlayer p = fate(helper);
		ServerLevel level = helper.getLevel();
		Vec3 spot = helper.absoluteVec(new Vec3(5.5, 2.5, 2.5));
		ItemEntity dropped = new ItemEntity(level, spot.x, spot.y, spot.z, liveStack(helper, p));
		level.addFreshEntity(dropped);
		helper.runAfterDelay(3, () -> {
			FateHelmetRegistry.Entry e = reg(helper).entry(p.getUUID());
			helper.assertTrue(e.where() == FateHelmetRegistry.Where.ITEM_ENTITY && e.thing().map(dropped.getUUID()::equals).orElse(false),
					"the dropped helmet told the registry where it is, " + e);
			helper.assertTrue(DoctorFate.pressH(p), "H summons it");
			helper.assertTrue(dropped.isRemoved(), "the dropped item is gone");
			summoned(helper, p);
			helper.assertTrue(DoctorFateHelmet.liveEntities(level, p.getUUID(), area(helper)) == 0, "no copy on the ground");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void summonFromAChest(GameTestHelper helper) {
		ServerPlayer p = fate(helper);
		BlockPos rel = new BlockPos(4, 2, 4);
		helper.setBlock(rel, Blocks.CHEST);
		BlockPos pos = helper.absolutePos(rel);
		ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(pos);
		chest.setItem(3, liveStack(helper, p));
		reg(helper).note(p.getUUID(), FateHelmetRegistry.Where.CONTAINER, helper.getLevel(), pos, null);
		helper.assertTrue(DoctorFate.pressH(p), "H summons it");
		helper.assertTrue(chest.getItem(3).isEmpty(), "it left the chest");
		summoned(helper, p);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void summonFromAnotherPlayersInventory(GameTestHelper helper) {
		ServerPlayer p = fate(helper);
		ServerPlayer thief = mock(helper, 5.5, 5.5);
		thief.getInventory().add(liveStack(helper, p));
		helper.assertTrue(inInventory(thief, p.getUUID()) == 1, "someone else picked it up");
		helper.assertTrue(DoctorFate.pressH(p), "H summons it");
		helper.assertTrue(inInventory(thief, p.getUUID()) == 0, "it left his inventory");
		summoned(helper, p);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void inYourOwnInventoryHGoesStraightOn(GameTestHelper helper) {
		ServerPlayer p = fate(helper);
		p.getInventory().add(liveStack(helper, p));
		int gen = reg(helper).generation(p.getUUID());
		helper.assertTrue(DoctorFate.pressH(p), "H");
		helper.assertTrue(inInventory(p, p.getUUID()) == 0, "the item is used up");
		helper.assertTrue(DoctorFate.state(p).helmetChangeAt == DoctorFate.now(p) && DoctorFate.state(p).helmetGoingOn, "the put-on starts now");
		helper.assertTrue(reg(helper).generation(p.getUUID()) == gen, "no summon needed (same generation)");
		helper.succeed();
	}

	/**
	 * The copy is somewhere nobody has loaded: the summon can't reach it, so it comes out of a portal and the old copy is retired by
	 * its generation. When that place loads later, the ghost goes: a placed helmet, a dropped item, a chest and an inventory.
	 */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40)
	public void aCopyInAnUnloadedChunkDeletesItselfWhenItLoads(GameTestHelper helper) {
		ServerPlayer p = fate(helper);
		ServerLevel level = helper.getLevel();
		BlockPos far = helper.absolutePos(BlockPos.ZERO).offset(40000, 0, 40000);
		helper.assertTrue(!level.isLoaded(far), "that chunk isn't loaded");
		FateHelmetTag old = liveStack(helper, p).get(FateHelmet.TAG);
		reg(helper).note(p.getUUID(), FateHelmetRegistry.Where.PLACED, level, far, UUID.randomUUID());
		helper.assertTrue(DoctorFate.pressH(p), "H summons it anyway (out of a portal)");
		summoned(helper, p);
		helper.assertTrue(reg(helper).isStale(old), "the copy out there is a ghost now");
		// ...later its chunk loads: every kind of stale copy goes
		FateHelmetEntity ghost = FateHelmetEntity.place(level, helper.absoluteVec(new Vec3(5.5, 2.0, 5.5)), old, 0f, false);
		level.addFreshEntity(ghost);
		ItemStack oldStack = new ItemStack(FateHelmet.ITEM);
		oldStack.set(FateHelmet.TAG, old);
		Vec3 drop = helper.absoluteVec(new Vec3(2.5, 2.5, 5.5));
		ItemEntity ghostItem = new ItemEntity(level, drop.x, drop.y, drop.z, oldStack.copy());
		level.addFreshEntity(ghostItem);
		BlockPos rel = new BlockPos(6, 2, 2);
		helper.setBlock(rel, Blocks.CHEST);
		ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(helper.absolutePos(rel));
		chest.setItem(0, oldStack.copy());
		FateHelmetGuard.blockEntityLoadedForTest(chest, level);
		ServerPlayer other = mock(helper, 4.5, 4.5);
		other.getInventory().add(oldStack.copy());
		helper.assertTrue(FateHelmetGuard.purgePlayer(reg(helper), other) == 1 && inInventory(other, p.getUUID()) == 0,
				"a stale helmet in a player's inventory goes (as he joins / it ticks)");
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(ghost.isRemoved(), "the stale placed helmet deleted itself");
			helper.assertTrue(ghostItem.isRemoved(), "the stale dropped item deleted itself");
			helper.assertTrue(chest.getItem(0).isEmpty(), "the stale helmet in the chest deleted itself");
			helper.assertTrue(DoctorFateHelmet.liveEntities(level, p.getUUID(), area(helper)) == 0, "no helmet on the ground at all");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- wearing the item

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void shiftRightClickWearsOnlyYourOwnHelmet(GameTestHelper helper) {
		ServerPlayer p = fate(helper);
		ServerPlayer q = mock(helper, 5.5, 5.5);
		helper.assertTrue(DoctorFate.grant(q), "a second Doctor Fate");
		DoctorFate.setHelmet(q, false);
		ItemStack mine = liveStack(helper, p);
		helper.assertTrue(!DoctorFateHelmet.wearFromItem(q, mine, InteractionHand.MAIN_HAND), "someone else's helmet won't go on");
		ServerPlayer nobody = mock(helper, 3.5, 5.5);
		helper.assertTrue(!DoctorFateHelmet.wearFromItem(nobody, mine, InteractionHand.MAIN_HAND), "nor on someone without the power");
		helper.assertTrue(DoctorFateHelmet.wearFromItem(p, mine, InteractionHand.MAIN_HAND), "its owner puts it on");
		helper.assertTrue(DoctorFate.state(p).helmetChangeAt > 0L && DoctorFate.state(p).helmetGoingOn, "the put-on sequence");
		DoctorFate.setHelmet(p, false);
		edit(p);
		helper.assertTrue(!DoctorFateHelmet.wearFromItem(p, liveStack(helper, p), InteractionHand.MAIN_HAND), "not during the lockout after a Possession");
		helper.succeed();
	}

	private static void edit(ServerPlayer p) {
		var n = DoctorFate.state(p).copy();
		n.lockoutUntil = DoctorFate.now(p) + 200;
		DoctorFate.save(p, n);
	}

	// ---------------------------------------------------------------- Dominion retune

	/** Each spell adds exactly its step (helmet on, Whispers, no Override). */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void eachSpellAddsItsRetunedStep(GameTestHelper helper) {
		ServerPlayer p = fate(helper);
		DoctorFate.setHelmet(p, true);
		DoctorFate.setDominion(p, 5f);
		DoctorFateSpells.testOverride = false;
		helper.assertTrue(DoctorFateSpells.use(p, DoctorFateAbilityManager.ANKH_BOLT), "bolt");
		helper.assertTrue(Math.abs(DoctorFate.dominion(p) - 5f - DoctorFateConfig.BOLT_DOMINION) < 1.0e-4f, "Ankh Bolt step " + (DoctorFate.dominion(p) - 5f));
		float before = DoctorFate.dominion(p);
		helper.assertTrue(DoctorFateSpells.use(p, DoctorFateAbilityManager.WARD), "ward");
		helper.assertTrue(Math.abs(DoctorFate.dominion(p) - before - DoctorFateConfig.WARD_DOMINION) < 1.0e-4f, "Ward step");
		before = DoctorFate.dominion(p);
		helper.assertTrue(DoctorFateSpells.use(p, DoctorFateAbilityManager.SIGHT), "sight");
		helper.assertTrue(Math.abs(DoctorFate.dominion(p) - before - DoctorFateConfig.SIGHT_DOMINION) < 1.0e-4f, "Sight step");
		DoctorFateSpells.testOverride = null;
		DoctorFate.setDominion(p, 0f);
		helper.succeed();
	}

	/**
	 * User: "about 2 minutes straight" of heavy use before Nabu starts taking over. Heavy use = every spell cast the moment its
	 * cooldown allows (the Avatar, which sets Dominion to 90 itself, and the Amulet, which lowers it, left out), plus the slow rise of
	 * wearing the helmet; the Pull discount from 50. Summed tick by tick from the configured steps and cooldowns (no real time).
	 */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void heavyUseTakesAboutTwoMinutesToTakingHold(GameTestHelper helper) {
		String[] spells = { DoctorFateAbilityManager.ANKH_BOLT, DoctorFateAbilityManager.GOLDEN_CHAINS, DoctorFateAbilityManager.BANISHMENT,
				DoctorFateAbilityManager.GLYPH_STORM, DoctorFateAbilityManager.GATE_DROP, DoctorFateAbilityManager.ORDER_BLAST,
				DoctorFateAbilityManager.WARD, DoctorFateAbilityManager.SIGHT };
		int[] ready = new int[spells.length];
		float d = 0f;
		int toHold = -1;
		int toPossession = -1;
		for (int tick = 0; tick < 20 * 600 && toPossession < 0; tick++) {
			for (int i = 0; i < spells.length; i++) {
				if (tick >= ready[i]) {
					float step = DoctorFateAbilityManager.dominionOf(spells[i]);
					d += d >= DoctorFateConfig.TIER_PULL ? step * DoctorFateConfig.PULL_DISCOUNT : step;
					ready[i] = tick + DoctorFateAbilityManager.maxCooldown(spells[i]);
				}
			}
			d += DoctorFateConfig.WEAR_GAIN_PER_TICK;
			if (toHold < 0 && d >= DoctorFateConfig.TIER_HOLD) {
				toHold = tick;
			}
			if (d >= DoctorFateConfig.DOMINION_MAX) {
				toPossession = tick;
			}
		}
		float holdS = toHold / 20f;
		float possS = toPossession / 20f;
		helper.assertTrue(holdS >= 105f && holdS <= 135f, "heavy use reaches Taking hold in about 2 minutes, took " + holdS + " s");
		helper.assertTrue(possS >= 140f && possS <= 185f, "and Possession a little later, took " + possS + " s");
		helper.succeed();
	}
}
