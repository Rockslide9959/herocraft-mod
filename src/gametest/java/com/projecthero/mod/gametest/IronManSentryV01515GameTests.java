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

	/**
	 * v0.16.7 (user): the Hulkbuster's Sentry Mode climbs its owner up and out of the BACK -- riding the suit along the
	 * climb path (over the rim, along the open plate, a hop and a drop) -- onto the ground behind it, facing away.
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160)
	public void deployClimbsTheOwnerOutOfTheBack(GameTestHelper h) {
		floor(h);
		ServerPlayer p = suitedTony(h, new BlockPos(3, 2, 3));
		IronManSentryEntity s = deploy(h, p);
		h.assertTrue(s.isOpen() && s.phase() == IronManSentryEntity.PHASE_EJECT, "the back opens");
		Vec3 fwd = Vec3.directionFromRotation(0f, s.getYRot());
		h.runAfterDelay(24, () -> {
			h.assertTrue(s.stepperId() == p.getId() && s.stepKind() == IronManSentryEntity.STEP_CLIMB_OUT,
					"mid-way the owner is the one climbing out");
			h.assertTrue(p.getVehicle() == s, "carried along the climb (riding the suit)");
			double along = p.position().subtract(s.position()).dot(fwd);
			h.assertTrue(along < -0.2 && p.getY() > s.getY() + 1.0, "up over the rim and out of the back (" + along + ", "
					+ (p.getY() - s.getY()) + ")");
			h.assertTrue(IronManSentryEntity.steppingIn(p), "unhurtable while climbing");
			h.assertTrue(s.isOpen(), "still open");
		});
		h.runAfterDelay(IronManSentryEntity.CLIMB_OUT_CLOSE_TICK + 3, () -> {
			double along = p.position().subtract(s.position()).dot(fwd);
			h.assertTrue(along < -1.4, "the owner ended up well BEHIND the suit -- out of its back (" + along + ")");
			h.assertTrue(Math.abs(p.getY() - s.getY()) < 0.6, "down on the ground (" + (p.getY() - s.getY()) + ")");
			h.assertTrue(p.getVehicle() == null, "off the ride");
			h.assertTrue(Math.abs(Mth.wrapDegrees(p.getYRot() - (s.getYRot() + 180f))) < 1f, "facing away from it, the way they climbed");
			h.assertTrue(!s.isOpen() && s.phase() == IronManSentryEntity.PHASE_IDLE, "then it closes");
			h.assertTrue(s.stepperId() == -1 && !IronManSentryEntity.steppingIn(p), "and lets go of them");
			s.discard();
			leave(h, p);
			h.succeed();
		});
	}

	/** v0.16.7 (user): round behind it, then (the Hulkbuster) up onto the open plate and in over the rim. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260)
	public void stepInGoesRoundBehindTurnsToItsFacingAndWalksIn(GameTestHelper h) {
		floor(h);
		ServerPlayer p = suitedTony(h, new BlockPos(3, 2, 2));
		IronManSentryEntity s = deploy(h, p);
		boolean[] sawClimb = { false };
		h.runAfterDelay(IronManSentryEntity.CLIMB_OUT_CLOSE_TICK + 2, () -> {
			// stand IN FRONT of it, facing it
			Vec3 front = s.position().add(Vec3.directionFromRotation(0f, s.getYRot()).scale(2.0));
			p.teleportTo(h.getLevel(), front.x, front.y, front.z, s.getYRot() + 180f, 0f);
			s.use(p, false); // open
			h.assertTrue(s.use(p, true), "Sneak + right-click the open suit: step in");
			h.assertTrue(s.stepKind() == IronManSentryEntity.STEP_APPROACH, "first they are walked round to its back");
			h.succeedWhen(() -> {
				if (s.stepKind() == IronManSentryEntity.STEP_IN || s.stepKind() == IronManSentryEntity.STEP_CLIMB_IN) {
					// caught on the way in: behind it and turned to face its way
					double along = p.position().subtract(s.position()).dot(Vec3.directionFromRotation(0f, s.getYRot()));
					h.assertTrue(along <= 0.05, "coming in from behind (" + along + ")");
					h.assertTrue(Math.abs(Mth.wrapDegrees(p.getYRot() - s.getYRot())) < 1f, "facing the suit's way");
					if (s.stepKind() == IronManSentryEntity.STEP_CLIMB_IN) {
						sawClimb[0] = true;
						h.assertTrue(p.getVehicle() == s, "climbing in, carried by the suit");
					}
				}
				h.assertTrue(s.isRemoved() && IronManArmor.wearingFullSuit(p, SUIT), "and it closes around them");
				h.assertTrue(sawClimb[0], "the Hulkbuster was climbed into (not walked into)");
				h.assertTrue(p.getVehicle() == null, "off the ride once it is on");
				h.assertTrue(Math.abs(Mth.wrapDegrees(p.getYRot() - s.getYRot())) < 1f, "lined up with the suit");
				leave(h, p);
			});
		});
	}

	// ------------------------------------------------------------------ drain

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void followAndDefensiveCostNothing(GameTestHelper h) {
		// v0.16.1: no suit energy -- Follow and Defensive run for free and the sentry never powers down for power
		floor(h);
		ServerPlayer p = suitedTony(h, new BlockPos(1, 2, 1));
		IronManSentryEntity s = deploy(h, p);
		p.teleportTo(p.getX(), p.getY(), p.getZ() + 2.5);
		h.runAfterDelay(IronManSentryEntity.EJECT_CLOSE_TICK + 2, () -> {
			s.use(p, true);
			s.use(p, true); // Follow
			h.assertTrue(s.mode() == IronManSentryEntity.FOLLOW, "Follow");
			h.runAfterDelay(40, () -> {
				h.assertTrue(s.energy() == cap() && s.powered(), "Follow costs nothing (" + s.energy() + ")");
				s.use(p, true);
				s.use(p, true); // Regular -> Defensive
				h.assertTrue(s.mode() == IronManSentryEntity.DEFENSIVE, "Defensive");
				h.runAfterDelay(40, () -> {
					h.assertTrue(s.energy() == cap() && s.powered(), "Defensive costs nothing (" + s.energy() + ")");
					h.assertTrue(s.mode() == IronManSentryEntity.DEFENSIVE, "and stays Defensive");
					s.setEnergy(cap() * 0.005f);
					h.assertTrue(s.energy() == cap(), "the sentry can't be drained any more");
					s.discard();
					leave(h, p);
					h.succeed();
				});
			});
		});
	}

	// ------------------------------------------------------------------ auto-defend

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 380)
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
			h.runAfterDelay(40, () -> { // past the mock player's 3 s spawn invulnerability
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
	public void aMarkEightCallIsFreeAndLandsInFrontAsASentry(GameTestHelper h) {
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
		// v0.16.1: the call is free, and the Hulkbuster always drops in out of the sky (never straight off the platform)
		h.assertTrue(s.energy() == cap() && IronManMark8Call.cost(IronManSuits.byId(SUIT)) == 0f, "the call costs nothing (" + s.energy() + ")");
		h.assertTrue(s.getY() > p.getY() + 3.0, "it starts up in the sky (y " + s.getY() + " vs " + p.getY() + ")");
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
	public void aLowChargeMarkEightIsStillCalled(GameTestHelper h) {
		floor(h);
		ServerPlayer p = tony(h, new BlockPos(1, 2, 1));
		IronManSuitPlatformBlockEntity be = platform(h, p, new BlockPos(6, 2, 6));
		for (ArmorItem.Type t : ALL) {
			ItemStack piece = new ItemStack(IronManItems.armor(SUIT, t));
			IronManEnergy.stampStack(piece, cap() * 0.08f, IronManEnergy.maxIntegrity(SUIT));
			be.store(piece);
		}
		StarkGear.equip(p, new ItemStack(IronManItems.STARK_GLASSES));
		// v0.16.1: no suit energy -- an old save's low-charge stamp no longer blocks the call
		h.assertFalse(IronManMark8Call.refuseLowEnergy(p, IronManSuits.byId(SUIT), cap() * 0.08f), "never refused for power");
		IronManSuitCall.execute(p, SUIT, IronManSuitListPayload.SOURCE_PLATFORM);
		h.assertTrue(be.isEmptyPlatform(), "the suit leaves its platform");
		List<IronManSentryEntity> inbound = h.getLevel().getEntities(IronManEntityTypes.SENTRY,
				new AABB(p.position(), p.position()).inflate(40), s -> p.getUUID().equals(s.ownerId()));
		h.assertTrue(inbound.size() == 1, "and flies in");
		inbound.forEach(net.minecraft.world.entity.Entity::discard);
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
		h.runAfterDelay(IronManSentryEntity.CLIMB_OUT_CLOSE_TICK + 2, () -> h.assertTrue(
				!s.isOpen() && s.phase() == IronManSentryEntity.PHASE_HOME, "closed, then off home"));
		h.succeedWhen(() -> {
			h.assertTrue(s.isRemoved(), "the sentry is gone");
			h.assertTrue(be.isFull() && SUIT.equals(be.storedSuitId()), "all four pieces are back on the platform");
			// (the platform starts repairing it at 5/s straight away, so allow a little on top)
			h.assertTrue(be.suitIntegrity() > 2999f && be.suitIntegrity() < 3020f, "with the suit's integrity (" + be.suitIntegrity() + ")");
			leave(h, p);
		});
	}
}
