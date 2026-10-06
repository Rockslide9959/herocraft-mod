package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.ability.IronManMark8;
import com.projecthero.mod.ironman.entity.IronManEntityTypes;
import com.projecthero.mod.ironman.entity.IronManSentryEntity;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.9 Sentry Mode (the Mark 8's C), developed on the Mark 7: C in the full suit leaves it standing as a sentry (the
 * real stacks move into it, the armour slots empty); right-click opens / closes it, Sneak + right-click cycles the modes;
 * stepping into the open suit equips it; in Defensive mode it closes around an owner at 4 hearts or less; every shot costs
 * energy and it stops at zero; and it saves / loads everything it carries.
 */
public class IronManSentryV0159GameTests implements FabricGameTest {
	private static final String SUIT = "mark_vii";
	private static final ArmorItem.Type[] ALL = {
			ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
			}
		}
	}

	private static ServerPlayer suitedTony(GameTestHelper helper, BlockPos rel, float energy, float integrity) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(rel));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		for (ArmorItem.Type t : ALL) {
			p.setItemSlot(IronManSuitUpManager.slotFor(t), new ItemStack(IronManItems.armor(SUIT, t)));
		}
		IronManEnergy.setEnergy(p, SUIT, energy);
		IronManEnergy.setIntegrity(p, SUIT, integrity);
		return p;
	}

	private static IronManSentryEntity deploy(GameTestHelper helper, ServerPlayer p) {
		IronManMark8.trigger(p, IronManSuits.byId(SUIT), IronManMark8.SENTRY, true);
		List<IronManSentryEntity> found = helper.getLevel().getEntities(IronManEntityTypes.SENTRY,
				new AABB(p.position(), p.position()).inflate(3), s -> p.getUUID().equals(s.ownerId()));
		helper.assertTrue(found.size() == 1, "C in the full suit deploys exactly one sentry (" + found.size() + ")");
		return found.get(0);
	}

	private static void moveOwner(GameTestHelper helper, ServerPlayer p, BlockPos rel) {
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(rel));
		p.teleportTo(at.x, at.y, at.z);
	}

	private static boolean slotsEmpty(ServerPlayer p) {
		for (ArmorItem.Type t : ALL) {
			if (!p.getItemBySlot(IronManSuitUpManager.slotFor(t)).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void cDeploysASentryAndEmptiesTheArmourSlots(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = suitedTony(helper, new BlockPos(3, 2, 3), 3000f, 2000f);
		IronManSentryEntity s = deploy(helper, p);
		helper.assertTrue(slotsEmpty(p), "every armour slot is empty -- the pieces left the player");
		helper.assertTrue(!IronManArmor.wearingAnyIronMan(p), "the player is out of the suit");
		helper.assertTrue(s.pieceCount() == 4, "the sentry carries all four pieces");
		for (int i = 0; i < 4; i++) {
			helper.assertTrue(s.stack(i).getItem() instanceof IronManArmorItem a && a.suitId().equals(SUIT)
					&& a.getType() == IronManSentryEntity.TYPES[i], "piece " + i + " is the right Mark 7 piece");
		}
		helper.assertTrue(Math.abs(s.energy() - 3000f) < 0.01f && Math.abs(s.integrity() - 2000f) < 0.01f,
				"the sentry carries the suit's energy / integrity (" + s.energy() + " / " + s.integrity() + ")");
		helper.assertTrue(s.isOpen() && s.phase() == IronManSentryEntity.PHASE_EJECT, "the back is open while the owner steps out");
		helper.assertTrue(s.mode() == IronManSentryEntity.REGULAR, "a first deploy starts in Regular mode");
		// a second C with nothing on does nothing
		IronManMark8.trigger(p, IronManSuits.byId(SUIT), IronManMark8.SENTRY, true);
		helper.assertTrue(helper.getLevel().getEntities(IronManEntityTypes.SENTRY, new AABB(p.position(), p.position()).inflate(6),
				e -> true).size() == 1, "no second sentry out of thin air");
		helper.succeedWhen(() -> {
			helper.assertTrue(s.phase() == IronManSentryEntity.PHASE_IDLE && !s.isOpen(), "the back closes after the owner steps out");
			s.discard();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void rightClickOpensAndClosesAndSneakCyclesModes(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = suitedTony(helper, new BlockPos(2, 2, 2), 3000f, 2000f);
		IronManSentryEntity s = deploy(helper, p);
		moveOwner(helper, p, new BlockPos(2, 2, 5)); // stepped out, 3 blocks away
		helper.runAfterDelay(IronManSentryEntity.EJECT_CLOSE_TICK + 4, () -> {
			helper.assertTrue(!s.isOpen(), "closed after deploying");
			// someone else's right-click does nothing
			ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
			helper.assertTrue(s.interact(stranger, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME && !s.isOpen(),
					"only the owner can open it");
			helper.assertTrue(s.interact(p, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS && s.isOpen(),
					"the owner's right-click opens it");
			helper.assertTrue(s.interact(p, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS && !s.isOpen(),
					"right-clicking the open suit closes it again");
			p.setShiftKeyDown(true);
			s.interact(p, InteractionHand.MAIN_HAND);
			helper.assertTrue(s.mode() == IronManSentryEntity.DEFENSIVE && !s.isOpen(), "Sneak + right-click: Regular -> Defensive");
			s.interact(p, InteractionHand.MAIN_HAND);
			helper.assertTrue(s.mode() == IronManSentryEntity.FOLLOW, "-> Follow");
			s.interact(p, InteractionHand.MAIN_HAND);
			helper.assertTrue(s.mode() == IronManSentryEntity.REGULAR, "-> back to Regular");
			p.setShiftKeyDown(false);
			// open; just walking into it does nothing any more
			s.interact(p, InteractionHand.MAIN_HAND);
			helper.assertTrue(s.isOpen(), "open again");
			p.teleportTo(s.getX(), s.getY(), s.getZ());
			helper.runAfterDelay(10, () -> {
				helper.assertTrue(!s.isRemoved() && s.isOpen() && !IronManArmor.wearingAnyIronMan(p),
						"walking into the open suit no longer equips it");
				// Sneak + right-click the OPEN suit: the step-in animation, then it closes around the owner
				moveOwner(helper, p, new BlockPos(2, 2, 4));
				p.setShiftKeyDown(true);
				helper.assertTrue(s.interact(p, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS, "Sneak + right-click: step in");
				p.setShiftKeyDown(false);
				helper.assertTrue(s.phase() == IronManSentryEntity.PHASE_STEP_IN && s.mode() == IronManSentryEntity.REGULAR,
						"stepping in (and the mode did not change)");
				helper.assertTrue(IronManSentryEntity.steppingIn(p), "the owner is marked as stepping in");
				helper.assertTrue(!com.projecthero.mod.ironman.IronManDamage.onAllowDamage(p, p.damageSources().generic(), 5f),
						"nothing hurts them while they step in");
				helper.runAfterDelay(IronManSentryEntity.STEP_TICKS / 2, () -> {
					double d = p.position().distanceTo(s.position());
					helper.assertTrue(d > 0.1 && d < 1.9, "half-way through the walk the owner is between start and suit (" + d + ")");
					helper.assertTrue(!IronManArmor.wearingAnyIronMan(p), "not on yet mid-walk");
				});
				helper.succeedWhen(() -> {
					helper.assertTrue(s.isRemoved(), "the sentry is gone once it has closed around its owner");
					helper.assertTrue(IronManArmor.wearingFullSuit(p, SUIT), "the owner is wearing the full suit again");
					helper.assertTrue(p.position().distanceTo(s.position()) < 0.05, "standing where the suit stood");
					helper.assertTrue(!IronManSentryEntity.steppingIn(p), "the immunity ends with the suit on");
					helper.assertTrue(IronManEnergy.energy(p, SUIT) > 2999.5f && IronManEnergy.energy(p, SUIT) < 3100f, "with its energy, recharging as worn now (" + IronManEnergy.energy(p, SUIT) + ", sentry had " + s.energy() + ")");
					helper.assertTrue(IronManEnergy.integrity(p, SUIT) > 1999.5f && IronManEnergy.integrity(p, SUIT) < 2010f, "and its integrity");
				});
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260)
	public void defensiveModeEquipsTheOwnerAtFourHearts(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = suitedTony(helper, new BlockPos(1, 2, 1), 3000f, 2000f);
		IronManSentryEntity s = deploy(helper, p);
		moveOwner(helper, p, new BlockPos(1, 2, 4));
		helper.runAfterDelay(IronManSentryEntity.EJECT_CLOSE_TICK + 4, () -> {
			s.use(p, true);
			helper.assertTrue(s.mode() == IronManSentryEntity.DEFENSIVE, "Defensive");
			helper.runAfterDelay(10, () -> {
				helper.assertTrue(s.lightPos() != null
						&& helper.getLevel().getBlockState(s.lightPos()).is(Blocks.LIGHT), "Defensive lights up the area");
				helper.assertTrue(!IronManArmor.wearingAnyIronMan(p), "nothing happens at full health");
				BlockPos light = s.lightPos();
				p.setHealth(7.0f); // 3.5 hearts
				helper.succeedWhen(() -> {
					helper.assertTrue(IronManArmor.wearingFullSuit(p, SUIT), "the suit closes around its hurt owner");
					helper.assertTrue(s.isRemoved(), "and the sentry is gone");
					helper.assertTrue(!helper.getLevel().getBlockState(light).is(Blocks.LIGHT), "its light block is cleaned up");
				});
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 240)
	public void sentryShotsDrainEnergyAndStopAtZero(GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		floor(helper);
		ServerPlayer p = suitedTony(helper, new BlockPos(1, 2, 1), 3000f, 2000f);
		IronManSentryEntity s = deploy(helper, p);
		moveOwner(helper, p, new BlockPos(1, 2, 4));
		Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(6, 2, 6));
		z.setNoAi(true);
		z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0);
		z.setHealth(500f);
		// two shots' worth: the Mark 7 tap costs 10 x 0.6 = 6, so 10 = one full shot and a last one that empties it
		s.setEnergy(10f);
		s.use(p, true); // can't while stepping out
		helper.assertTrue(s.mode() == IronManSentryEntity.REGULAR, "busy while the owner steps out");
		helper.runAfterDelay(IronManSentryEntity.EJECT_CLOSE_TICK + 2, () -> {
			s.use(p, true);
			helper.assertTrue(s.mode() == IronManSentryEntity.DEFENSIVE, "Defensive");
		});
		helper.runAfterDelay(150, () -> {
			helper.assertTrue(s.shotsFired() == 2, "two repulsor shots then nothing (" + s.shotsFired() + ")");
			helper.assertTrue(s.energy() == 0f, "every shot used energy, down to zero (" + s.energy() + ")");
			helper.assertTrue(z.getHealth() < 500f, "the zombie was hit (" + z.getHealth() + ")");
			helper.assertTrue(!s.powered(), "no energy = powered down");
			helper.assertTrue(s.lightPos() == null, "and dark");
			// at zero it still closes around its owner when asked
			s.use(p, false);
			helper.assertTrue(s.isOpen(), "a drained suit still opens");
			s.discard();
			z.discard();
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160)
	public void defensivePrioritisesWhoeverHurtTheOwner(GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		floor(helper);
		ServerPlayer p = suitedTony(helper, new BlockPos(1, 2, 1), 3000f, 2000f);
		IronManSentryEntity s = deploy(helper, p);
		moveOwner(helper, p, new BlockPos(1, 2, 4));
		Zombie near = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 6));
		Zombie far = helper.spawn(EntityType.ZOMBIE, new BlockPos(6, 2, 6));
		for (Zombie z : new Zombie[] { near, far }) {
			z.setNoAi(true);
			z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0);
			z.setHealth(500f);
		}
		helper.runAfterDelay(IronManSentryEntity.EJECT_CLOSE_TICK + 2, () -> {
			s.use(p, true);
			helper.runAfterDelay(8, () -> {
				helper.assertTrue(s.currentTarget() == near, "with nobody hurting the owner: the enemy closest to the owner");
				p.setLastHurtByMob(far);
				helper.runAfterDelay(8, () -> {
					helper.assertTrue(s.currentTarget() == far, "whoever hurt the owner most recently comes first");
					s.discard();
					near.discard();
					far.discard();
					helper.succeed();
				});
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void saveLoadKeepsThePiecesModeAndOwner(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = suitedTony(helper, new BlockPos(3, 2, 3), 1234f, 567f);
		IronManSentryEntity s = deploy(helper, p);
		moveOwner(helper, p, new BlockPos(3, 2, 6));
		helper.runAfterDelay(IronManSentryEntity.EJECT_CLOSE_TICK + 2, () -> {
			s.use(p, true);
			s.use(p, true); // Follow
			s.use(p, false); // open
			CompoundTag tag = new CompoundTag();
			s.saveWithoutId(tag);
			IronManSentryEntity copy = new IronManSentryEntity(IronManEntityTypes.SENTRY, helper.getLevel());
			copy.load(tag);
			helper.assertTrue(copy.pieceCount() == 4, "all four pieces survive a save / load");
			for (int i = 0; i < 4; i++) {
				helper.assertTrue(ItemStack.isSameItem(copy.stack(i), s.stack(i)), "piece " + i + " is the same item");
			}
			helper.assertTrue(p.getUUID().equals(copy.ownerId()), "the owner is kept");
			helper.assertTrue(copy.mode() == IronManSentryEntity.FOLLOW, "the mode is kept");
			helper.assertTrue(copy.isOpen(), "open stays open");
			helper.assertTrue(Math.abs(copy.energy() - s.energy()) < 0.01f && Math.abs(copy.integrity() - 567f) < 0.01f,
					"energy / integrity are kept (" + copy.energy() + " / " + copy.integrity() + ")");
			helper.assertTrue(SUIT.equals(copy.suitId()), "the suit id is kept");
			// the mode is also remembered on the chestplate for the next deploy
			helper.assertTrue(s.use(p, true) && s.phase() == IronManSentryEntity.PHASE_STEP_IN, "Sneak + right-click the open suit: step in");
			helper.succeedWhen(() -> {
				helper.assertTrue(s.isRemoved() && IronManArmor.wearingFullSuit(p, SUIT), "back on");
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void integrityRulesAndZeroIntegrityPowersDown(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = suitedTony(helper, new BlockPos(3, 2, 3), 3000f, 300f);
		IronManSentryEntity s = deploy(helper, p);
		helper.assertTrue(!s.hurt(helper.getLevel().damageSources().playerAttack(p), 50f), "the owner can't hurt their own suit");
		s.hurt(helper.getLevel().damageSources().generic(), 100f);
		helper.assertTrue(Math.abs(s.integrity() - 200f) < 0.01f, "a hit takes integrity 1:1 (" + s.integrity() + ")");
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(Math.abs(s.integrity() - 200f) < 0.01f, "and it never repairs itself");
			s.hurt(helper.getLevel().damageSources().generic(), 1000f);
			helper.assertTrue(!s.isRemoved() && s.integrity() == 0f && !s.powered(), "at zero it powers down but stays standing");
			helper.assertTrue(s.pieceCount() == 4, "the suit is never destroyed");
			s.kill(); // /kill drops the pieces where it stands -- nothing lost
			List<net.minecraft.world.entity.item.ItemEntity> drops = helper.getLevel().getEntitiesOfClass(
					net.minecraft.world.entity.item.ItemEntity.class, new AABB(s.position(), s.position()).inflate(4),
					e -> e.getItem().getItem() instanceof IronManArmorItem);
			helper.assertTrue(s.isRemoved() && drops.size() == 4, "/kill drops all four pieces (" + drops.size() + ")");
			for (var e : drops) {
				e.discard();
			}
			helper.succeed();
		});
	}
}
