package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.ability.IronManMark8;
import com.projecthero.mod.ironman.entity.IronManEntityTypes;
import com.projecthero.mod.ironman.entity.IronManSentryEntity;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.gear.StarkGear;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManMark8Call;
import com.projecthero.mod.ironman.suit.IronManSuitCall;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.network.IronManSuitListPayload;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
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
 * v0.15.15 (user requests) -- the Mark 8 and Sentry Mode: walking backwards out of the open back; walking round behind
 * it and in, turned to its facing; Follow 1%/s and Defensive 2%/s energy drain, dropping to Regular at zero;
 * auto-Defensive when the owner is hurt and back to the old mode once the fight is over; a Mark 8 call costs 10% of its
 * energy (refused below that) and arrives whole as a sentry landing 2 blocks in front of its owner, facing them, closed,
 * Regular; send-home on the worn Mark 8 steps out and flies the whole suit to its platform.
 */
public class IronManSentryV01515GameTests implements FabricGameTest {
	private static final String SUIT = IronManMark8.SUIT_ID;
	private static final ArmorItem.Type[] ALL = {
			ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };

	private static void floor(GameTestHelper h) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
			}
		}
	}

	private static ServerPlayer tony(GameTestHelper h, BlockPos rel) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		p.getInventory().clearContent();
		Vec3 at = Vec3.atBottomCenterOf(h.absolutePos(rel));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		return p;
	}

	private static ServerPlayer suitedTony(GameTestHelper h, BlockPos rel) {
		ServerPlayer p = tony(h, rel);
		for (ArmorItem.Type t : ALL) {
			p.setItemSlot(IronManSuitUpManager.slotFor(t), new ItemStack(IronManItems.armor(SUIT, t)));
		}
		IronManEnergy.setEnergy(p, SUIT, 5000f);
		IronManEnergy.setIntegrity(p, SUIT, 3000f);
		return p;
	}

	private static IronManSentryEntity deploy(GameTestHelper h, ServerPlayer p) {
		IronManMark8.trigger(p, IronManSuits.byId(SUIT), IronManMark8.SENTRY, true);
		return only(h, p);
	}

	private static IronManSentryEntity only(GameTestHelper h, ServerPlayer p) {
		List<IronManSentryEntity> found = h.getLevel().getEntities(IronManEntityTypes.SENTRY,
				new AABB(p.position(), p.position()).inflate(40), s -> p.getUUID().equals(s.ownerId()));
		h.assertTrue(found.size() == 1, "exactly one sentry of this owner (" + found.size() + ")");
		return found.get(0);
	}

	private static void leave(GameTestHelper h, ServerPlayer p) {
		if (!p.isRemoved()) {
			h.getLevel().getServer().getPlayerList().remove(p);
		}
	}

	private static IronManSuitPlatformBlockEntity platform(GameTestHelper h, ServerPlayer owner, BlockPos rel) {
		h.setBlock(rel, IronManBlocks.IRON_MAN_SUIT_PLATFORM);
		IronManSuitPlatformBlockEntity be = (IronManSuitPlatformBlockEntity) h.getBlockEntity(rel);
		be.bindTo(owner.getUUID());
		return be;
	}

	private static float cap() {
		return IronManEnergy.capacity(SUIT);
	}

	// ------------------------------------------------------------------ step out / step in

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void deployWalksTheOwnerBackwardsOutOfTheBack(GameTestHelper h) {
		floor(h);
		ServerPlayer p = suitedTony(h, new BlockPos(3, 2, 3));
		IronManSentryEntity s = deploy(h, p);
		h.assertTrue(s.isOpen() && s.phase() == IronManSentryEntity.PHASE_EJECT, "the back opens");
		h.runAfterDelay(IronManSentryEntity.EJECT_PUSH_TICK + IronManSentryEntity.STEP_OUT_TICKS / 2, () -> {
			h.assertTrue(s.stepperId() == p.getId() && s.stepKind() == IronManSentryEntity.STEP_OUT,
					"mid-way the owner is the one being walked out (body locked to the suit)");
			h.assertTrue(s.isOpen(), "still open");
		});
		h.runAfterDelay(IronManSentryEntity.EJECT_CLOSE_TICK + 2, () -> {
			Vec3 fwd = Vec3.directionFromRotation(0f, s.getYRot());
			double along = p.position().subtract(s.position()).dot(fwd);
			h.assertTrue(along < -0.6, "the owner ended up BEHIND the suit -- walked backwards out of its back (" + along + ")");
			h.assertTrue(Math.abs(Mth.wrapDegrees(p.getYRot() - s.getYRot())) < 1f, "still facing the suit's way");
			h.assertTrue(!s.isOpen() && s.phase() == IronManSentryEntity.PHASE_IDLE, "then it closes");
			h.assertTrue(s.stepperId() == -1, "and lets go of them");
			s.discard();
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void stepInGoesRoundBehindTurnsToItsFacingAndWalksIn(GameTestHelper h) {
		floor(h);
		ServerPlayer p = suitedTony(h, new BlockPos(3, 2, 2));
		IronManSentryEntity s = deploy(h, p);
		h.runAfterDelay(IronManSentryEntity.EJECT_CLOSE_TICK + 2, () -> {
			// stand IN FRONT of it, facing it
			Vec3 front = s.position().add(Vec3.directionFromRotation(0f, s.getYRot()).scale(2.0));
			p.teleportTo(h.getLevel(), front.x, front.y, front.z, s.getYRot() + 180f, 0f);
			s.use(p, false); // open
			h.assertTrue(s.use(p, true), "Sneak + right-click the open suit: step in");
			h.assertTrue(s.stepKind() == IronManSentryEntity.STEP_APPROACH, "first they are walked round to its back");
			h.succeedWhen(() -> {
				if (s.stepKind() == IronManSentryEntity.STEP_IN) {
					// caught on the way in: behind it and turned to face its way
					double along = p.position().subtract(s.position()).dot(Vec3.directionFromRotation(0f, s.getYRot()));
					h.assertTrue(along <= 0.05, "coming in from behind (" + along + ")");
					h.assertTrue(Math.abs(Mth.wrapDegrees(p.getYRot() - s.getYRot())) < 1f, "facing the suit's way");
				}
				h.assertTrue(s.isRemoved() && IronManArmor.wearingFullSuit(p, SUIT), "and it closes around them");
				h.assertTrue(Math.abs(Mth.wrapDegrees(p.getYRot() - s.getYRot())) < 1f, "lined up with the suit");
				leave(h, p);
			});
		});
	}

	// ------------------------------------------------------------------ drain

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void followDrainsOnePercentAndDefensiveTwoPercentASecond(GameTestHelper h) {
		floor(h);
		ServerPlayer p = suitedTony(h, new BlockPos(1, 2, 1));
		IronManSentryEntity s = deploy(h, p);
		p.teleportTo(p.getX(), p.getY(), p.getZ() + 2.5);
		h.runAfterDelay(IronManSentryEntity.EJECT_CLOSE_TICK + 2, () -> {
			s.use(p, true);
			s.use(p, true); // Follow
			h.assertTrue(s.mode() == IronManSentryEntity.FOLLOW, "Follow");
			float e0 = s.energy();
			h.runAfterDelay(40, () -> {
				float used = e0 - s.energy();
				h.assertTrue(Math.abs(used - cap() * 0.02f) < cap() * 0.002f, "Follow: 1% a second (" + used + " in 2 s)");
				s.use(p, true);
				s.use(p, true); // Regular -> Defensive
				h.assertTrue(s.mode() == IronManSentryEntity.DEFENSIVE, "Defensive");
				float e1 = s.energy();
				h.runAfterDelay(40, () -> {
					float used2 = e1 - s.energy();
					h.assertTrue(Math.abs(used2 - cap() * 0.04f) < cap() * 0.002f, "Defensive: 2% a second (" + used2 + " in 2 s)");
					s.use(p, true); // Regular
					float e2 = s.energy();
					h.runAfterDelay(20, () -> {
						h.assertTrue(s.energy() == e2, "Regular costs nothing");
						s.setEnergy(cap() * 0.005f);
						s.use(p, true);
						s.use(p, true); // Follow again, almost empty
						h.runAfterDelay(20, () -> {
							h.assertTrue(s.energy() == 0f && !s.powered(), "drained to zero");
							h.assertTrue(s.mode() == IronManSentryEntity.REGULAR, "drops to Regular");
							s.discard();
							leave(h, p);
							h.succeed();
						});
					});
				});
			});
		});
	}

	// ------------------------------------------------------------------ auto-defend

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 320)
	public void ownerHurtSwitchesToDefensiveThenBackOnceCalm(GameTestHelper h) {
		h.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		floor(h);
		ServerPlayer p = suitedTony(h, new BlockPos(1, 2, 1));
		IronManSentryEntity s = deploy(h, p);
		p.teleportTo(p.getX(), p.getY(), p.getZ() + 2.5);
		Zombie z = h.spawn(EntityType.ZOMBIE, new BlockPos(6, 2, 6));
		z.setNoAi(true);
		z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0);
		z.setHealth(500f);
		h.runAfterDelay(IronManSentryEntity.EJECT_CLOSE_TICK + 2, () -> {
			s.use(p, true);
			s.use(p, true); // Follow
			h.runAfterDelay(5, () -> {
				h.assertTrue(s.mode() == IronManSentryEntity.FOLLOW && !s.autoDefending(), "Follow, nothing going on");
				p.hurt(p.damageSources().mobAttack(z), 1f);
				h.runAfterDelay(2, () -> {
					h.assertTrue(s.mode() == IronManSentryEntity.DEFENSIVE && s.autoDefending(), "the owner got hurt: Defensive by itself");
					h.assertTrue(s.modeBeforeFight() == IronManSentryEntity.FOLLOW, "remembering it was in Follow");
					z.discard(); // the fight is over
					h.runAfterDelay(IronManSentryEntity.CALM_TICKS / 2, () -> h.assertTrue(
							s.mode() == IronManSentryEntity.DEFENSIVE, "not before 5 s of calm"));
					h.runAfterDelay(IronManSentryEntity.CALM_TICKS + 10, () -> {
						h.assertTrue(s.mode() == IronManSentryEntity.FOLLOW && !s.autoDefending(),
								"5 s with no hits and no enemy near: back to Follow (" + s.mode() + ")");
						s.discard();
						leave(h, p);
						h.succeed();
					});
				});
			});
		});
	}

	// ------------------------------------------------------------------ calling + send-home

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void aMarkEightCallCostsTenPercentAndLandsInFrontAsASentry(GameTestHelper h) {
		floor(h);
		ServerPlayer p = tony(h, new BlockPos(1, 2, 1));
		IronManSuitPlatformBlockEntity be = platform(h, p, new BlockPos(6, 2, 6));
		for (ArmorItem.Type t : ALL) {
			be.store(new ItemStack(IronManItems.armor(SUIT, t))); // fresh = full charge
		}
		StarkGear.equip(p, new ItemStack(IronManItems.STARK_GLASSES));
		IronManSuitCall.execute(p, SUIT, IronManSuitListPayload.SOURCE_PLATFORM);
		h.assertTrue(be.isEmptyPlatform(), "the whole suit leaves the platform");
		h.assertTrue(!IronManSuitUpManager.inTransition(p) && !IronManArmor.wearingAnyIronMan(p), "no piece-by-piece suit-up");
		IronManSentryEntity s = only(h, p);
		h.assertTrue(s.phase() == IronManSentryEntity.PHASE_ARRIVE && s.pieceCount() == 4, "it flies in as one suit");
		h.assertTrue(Math.abs(s.energy() - cap() * 0.9f) < 0.5f, "10% of its energy spent on the call (" + s.energy() + ")");
		h.succeedWhen(() -> {
			h.assertTrue(s.phase() == IronManSentryEntity.PHASE_IDLE, "landed");
			h.assertTrue(s.mode() == IronManSentryEntity.REGULAR && !s.isOpen() && !s.flying(), "standing, closed, in Regular");
			Vec3 want = p.position().add(Vec3.directionFromRotation(0f, p.getYRot()).scale(IronManSentryEntity.ARRIVE_FRONT));
			h.assertTrue(s.position().multiply(1, 0, 1).distanceTo(want.multiply(1, 0, 1)) < 0.6,
					"2 blocks in front of its owner (" + s.position() + " vs " + want + ")");
			Vec3 to = p.position().subtract(s.position());
			float yawTo = (float) (Mth.atan2(to.z, to.x) * (180.0 / Math.PI)) - 90f;
			h.assertTrue(Math.abs(Mth.wrapDegrees(yawTo - s.getYRot())) < 20f, "facing them");
			s.discard();
			leave(h, p);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void aMarkEightUnderTenPercentCannotBeCalled(GameTestHelper h) {
		floor(h);
		ServerPlayer p = tony(h, new BlockPos(1, 2, 1));
		IronManSuitPlatformBlockEntity be = platform(h, p, new BlockPos(6, 2, 6));
		for (ArmorItem.Type t : ALL) {
			ItemStack piece = new ItemStack(IronManItems.armor(SUIT, t));
			IronManEnergy.stampStack(piece, cap() * 0.08f, IronManEnergy.maxIntegrity(SUIT));
			be.store(piece);
		}
		StarkGear.equip(p, new ItemStack(IronManItems.STARK_GLASSES));
		IronManSuitCall.execute(p, SUIT, IronManSuitListPayload.SOURCE_PLATFORM);
		h.assertTrue(be.isFull(), "an 8% suit stays on its platform");
		h.assertTrue(h.getLevel().getEntities(IronManEntityTypes.SENTRY, new AABB(p.position(), p.position()).inflate(40),
				s -> p.getUUID().equals(s.ownerId())).isEmpty(), "nothing flies in");
		h.assertTrue(IronManMark8Call.CALL_COST == 0.10f, "the cost is 10%");
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260)
	public void sendHomeStepsOutThenFliesTheWholeSuitToItsPlatform(GameTestHelper h) {
		floor(h);
		ServerPlayer p = suitedTony(h, new BlockPos(1, 2, 1));
		IronManSuitPlatformBlockEntity be = platform(h, p, new BlockPos(6, 2, 6));
		h.assertTrue(IronManSuitCall.sendBack(p, SUIT) == 4, "Send Home on the worn Mark 8");
		IronManSentryEntity s = only(h, p);
		h.assertTrue(s.phase() == IronManSentryEntity.PHASE_EJECT && s.isOpen(), "it opens and the owner steps out first");
		h.assertTrue(s.homeDock() != null && s.homeDock().equals(be.getBlockPos()), "bound for the platform");
		h.assertTrue(!IronManArmor.wearingAnyIronMan(p), "the owner is out of it");
		h.runAfterDelay(IronManSentryEntity.EJECT_CLOSE_TICK + 2, () -> h.assertTrue(
				!s.isOpen() && s.phase() == IronManSentryEntity.PHASE_HOME, "closed, then off home"));
		h.succeedWhen(() -> {
			h.assertTrue(s.isRemoved(), "the sentry is gone");
			h.assertTrue(be.isFull() && SUIT.equals(be.storedSuitId()), "all four pieces are back on the platform");
			h.assertTrue(Math.abs(be.suitEnergy() - 5000f) < 50f, "with the suit's energy");
			leave(h, p);
		});
	}
}
