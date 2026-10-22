package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.ironman.HulkbusterInnerSuit;
import com.projecthero.mod.ironman.HulkbusterParts;
import com.projecthero.mod.ironman.HulkbusterShield;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.IronManDamage;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManFlight;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.ability.IronManComicRouter;
import com.projecthero.mod.ironman.crate.HulkbusterCrate;
import com.projecthero.mod.ironman.entity.HulkbusterPodEntity;
import com.projecthero.mod.ironman.entity.IronManEntityTypes;
import com.projecthero.mod.ironman.entity.IronManSentryEntity;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.item.SuitcaseContents;
import com.projecthero.mod.ironman.suit.IronManPlatformReturn;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user) Hulkbuster "Veronica" overhaul: the single-use Uplink and the suit's way home, per-part damage and the
 * moves it takes offline, the Replacement Pod's timing (3 s in, ~8 s of repair, worn and on a wrecked Sentry), the inner
 * suit kept under the Hulkbuster across every way out (Sentry step-out, wreck, death with / without keepInventory, a
 * save / load, a revoke, a slot already taken), and the mid-air build. See {@code docs/HULKBUSTER_V0167.md}.
 */
public class IronManHulkbusterVeronicaGameTests implements FabricGameTest {
	private static final String HB = "mark_8";
	private static final String MK3 = "mark_iii";
	private static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
	private static final ArmorItem.Type[] TYPES = { ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS,
			ArmorItem.Type.BOOTS };

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
		p.setOnGround(true);
		return p;
	}

	private static void wear(ServerPlayer p, String suit, float integrity) {
		for (int i = 0; i < 4; i++) {
			p.setItemSlot(SLOTS[i], new ItemStack(IronManItems.armor(suit, TYPES[i])));
		}
		TonyStark.setActiveSuit(p, suit);
		IronManEnergy.setIntegrity(p, suit, integrity);
		IronManEnergy.setEnergy(p, suit, IronManEnergy.capacity(suit));
	}

	/** Mk III on (333 integrity, 444 energy), then the Hulkbuster put on over it the way the step-in does. */
	private static void hulkbusterOverMk3(ServerPlayer p, float hbIntegrity) {
		wear(p, MK3, 333f);
		IronManEnergy.setEnergy(p, MK3, 444f);
		HulkbusterInnerSuit.stash(p);
		wear(p, HB, hbIntegrity);
	}

	private static boolean wearsMk3(ServerPlayer p) {
		return IronManArmor.wearingFullSuit(p, MK3);
	}

	private static void leave(GameTestHelper h, ServerPlayer p) {
		for (IronManSentryEntity s : h.getLevel().getEntities(IronManEntityTypes.SENTRY, new AABB(p.position(), p.position()).inflate(40),
				s -> p.getUUID().equals(s.ownerId()))) {
			s.discard();
		}
		for (HulkbusterPodEntity pod : h.getLevel().getEntities(IronManEntityTypes.HULKBUSTER_POD,
				new AABB(p.position(), p.position()).inflate(80), pod -> p.getUUID().equals(pod.ownerUuid()))) {
			pod.discard();
		}
		HulkbusterShield.forget(p.getUUID());
		if (p.isPassenger()) {
			p.stopRiding();
		}
		if (!p.isRemoved()) {
			h.getLevel().getServer().getPlayerList().remove(p);
		}
	}

	private static int count(ServerPlayer p, String suit) {
		int n = 0;
		for (ItemStack s : p.getInventory().items) {
			n += s.getItem() instanceof IronManArmorItem a && suit.equals(a.suitId()) ? 1 : 0;
		}
		return n;
	}

	// ================================================================== 1. the single-use Uplink

	@GameTest(template = EMPTY_STRUCTURE, skyAccess = true)
	public void theUplinkIsUsedUpAndTheSuitFliesHomeToItsPlatform(GameTestHelper h) {
		floor(h);
		ServerPlayer p = tony(h, new BlockPos(0, 2, 0));
		h.setBlock(new BlockPos(7, 2, 7), IronManBlocks.IRON_MAN_SUIT_PLATFORM);
		IronManSuitPlatformBlockEntity be = (IronManSuitPlatformBlockEntity) h.getBlockEntity(new BlockPos(7, 2, 7));
		be.bindTo(p.getUUID());
		for (ArmorItem.Type t : TYPES) {
			ItemStack piece = new ItemStack(IronManItems.armor(HB, t));
			IronManEnergy.stampStack(piece, IronManEnergy.FULL, 3000f);
			h.assertTrue(be.store(piece), "racked");
		}
		// the platform's Uplink tab programs a NEW single-use Uplink; each piece remembers this platform
		h.assertTrue(be.packUplink(p) && be.isEmptyPlatform(), "the Uplink tab");
		ItemStack up = ItemStack.EMPTY;
		for (ItemStack st : p.getInventory().items) {
			if (st.is(IronManItems.HULKBUSTER_UPLINK)) {
				up = st;
			}
		}
		h.assertTrue(!up.isEmpty(), "an Uplink");
		List<ItemStack> pieces = SuitcaseContents.nonEmpty(up);
		h.assertTrue(pieces.size() == 4 && pieces.stream().allMatch(s -> IronManPlatformReturn.homeOf(s)
				.map(g -> g.pos().equals(be.getBlockPos())).orElse(false)), "every piece knows its platform");
		// calling it spends it
		p.getInventory().items.set(0, up.copy());
		for (int i = 1; i < p.getInventory().items.size(); i++) {
			if (p.getInventory().items.get(i).is(IronManItems.HULKBUSTER_UPLINK)) {
				p.getInventory().items.set(i, ItemStack.EMPTY);
			}
		}
		h.assertTrue(HulkbusterCrate.callTo(p, Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(4, 2, 4)))), "called");
		h.assertTrue(p.getInventory().items.get(0).isEmpty(), "the Uplink is used up");
		h.getLevel().getEntities(IronManEntityTypes.HULKBUSTER_CRATE, new AABB(p.position(), p.position()).inflate(40),
				c -> p.getUUID().equals(c.ownerId())).forEach(c -> c.discard()); // (its flight is covered by the crate tests)
		// a fold / recall ends with the crate reaching the top: the pieces fly home and are racked again
		HulkbusterCrate.toOrbit(h.getLevel(), p, pieces, 2000f, p.position());
		h.assertTrue(be.isFull() && HB.equals(be.storedSuitId()), "racked again on its own platform");
		h.assertTrue(Math.abs(be.suitIntegrity() - 2000f) < 1f, "with its integrity (" + be.suitIntegrity() + ")");
		boolean anyUplink = false;
		for (ItemStack st : p.getInventory().items) {
			anyUplink |= st.is(IronManItems.HULKBUSTER_UPLINK);
		}
		h.assertFalse(anyUplink, "no Uplink comes back: take a new one from the tab");
		h.assertTrue(be.holdsHulkbuster() && be.packUplink(p), "and the tab makes the next one");
		leave(h, p);
		h.succeed();
	}

	// ================================================================== 2. per-part damage

	@GameTest(template = EMPTY_STRUCTURE)
	public void brokenPartsTakeTheirMovesOffline(GameTestHelper h) {
		ServerPlayer p = tony(h, new BlockPos(2, 2, 2));
		float max = IronManEnergy.maxIntegrity(HB);
		wear(p, HB, max);
		h.assertTrue(HulkbusterParts.brokenMask(HulkbusterParts.of(p)) == 0, "a fresh suit is whole");
		// a hit through the real damage path lands on some part as well as the integrity
		h.assertFalse(IronManDamage.onAllowDamage(p, h.getLevel().damageSources().generic(), 60f), "the suit takes it");
		float[] raw = HulkbusterParts.raw(p.getItemBySlot(EquipmentSlot.CHEST));
		float sum = 0f;
		for (float v : raw) {
			sum += v;
		}
		h.assertTrue(sum > 0f, "a part took the hit");
		h.assertTrue(Math.abs(HulkbusterParts.weighted(raw) - 60f / max) < 1e-3f, "parts and integrity agree");
		// break the right arm: the combo / cannon fall back to the left, the two-fisted Pile Driver is offline
		IronManEnergy.setIntegrity(p, HB, max * 0.5f);
		HulkbusterParts.hit(p, HulkbusterParts.RARM, max);
		float[] eff = HulkbusterParts.of(p);
		h.assertTrue(HulkbusterParts.broken(eff[HulkbusterParts.RARM]), "the right arm is broken");
		h.assertFalse(HulkbusterParts.armWorks(p, true) || !HulkbusterParts.armWorks(p, false), "right dead, left working");
		h.assertTrue(HulkbusterParts.blockedBy("mk8_punch_combo", eff) == null, "one fist still punches");
		h.assertTrue("arm".equals(HulkbusterParts.blockedBy("mk8_pile_driver", eff)), "no Pile Driver with one arm");
		h.assertFalse(IronManComicRouter.fireForTests(p, IronManSuits.MARK_8, 2, false), "the router refuses it");
		HulkbusterParts.hit(p, HulkbusterParts.LARM, max);
		eff = HulkbusterParts.of(p);
		h.assertTrue("arms".equals(HulkbusterParts.blockedBy("mk8_punch_combo", eff))
				&& "arms".equals(HulkbusterParts.blockedBy("mk8_palm_cannon", eff)), "both arms gone: no fist moves");
		h.assertFalse(IronManComicRouter.fireForTests(p, IronManSuits.MARK_8, 1, false), "no punches");
		h.assertFalse(IronManComicRouter.fireForTests(p, IronManSuits.MARK_8, 5, false), "no palm cannon");
		// a leg: slower, no stomp
		HulkbusterParts.hit(p, HulkbusterParts.LLEG, max);
		HulkbusterParts.tick(p);
		h.assertTrue(p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(HulkbusterParts.LEG_SLOW_ID) != null, "the broken leg slows it");
		h.assertTrue(HulkbusterParts.flightFactor(p) < 1.0, "and its boot jet");
		h.assertFalse(IronManComicRouter.fireForTests(p, IronManSuits.MARK_8, 2, true), "no Earthquake Stomp");
		// the head: no missiles (no targeting); the torso: no Mega Beam, the reactor leaks (the hits took integrity too)
		IronManEnergy.setIntegrity(p, HB, max * 0.1f);
		HulkbusterParts.hit(p, HulkbusterParts.HEAD, max);
		h.assertFalse(IronManComicRouter.fireForTests(p, IronManSuits.MARK_8, 4, true), "no Missile Swarm");
		HulkbusterParts.hit(p, HulkbusterParts.TORSO, max);
		h.assertFalse(IronManComicRouter.fireForTests(p, IronManSuits.MARK_8, 4, false), "no Mega Beam");
		float before = IronManEnergy.energy(p, HB);
		for (int i = 0; i < 40; i++) {
			p.tickCount++;
			HulkbusterParts.tick(p);
		}
		h.assertTrue(IronManEnergy.energy(p, HB) < before - 5f, "the cracked torso leaks energy (" + before + " -> "
				+ IronManEnergy.energy(p, HB) + ")");
		// the integrity still drives the suit: repaired to full, it is whole again; off, the slow goes
		IronManEnergy.setIntegrity(p, HB, max);
		h.assertTrue(HulkbusterParts.brokenMask(HulkbusterParts.of(p)) == 0, "a full suit is never shown broken");
		for (EquipmentSlot s : SLOTS) {
			p.setItemSlot(s, ItemStack.EMPTY);
		}
		HulkbusterParts.tick(p);
		h.assertTrue(p.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(HulkbusterParts.LEG_SLOW_ID) == null, "no slow out of it");
		leave(h, p);
		h.succeed();
	}

	// ================================================================== 3. the Replacement Pod

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void theReplacementPodArrivesIn3sAndRepairsIn8s(GameTestHelper h) {
		floor(h);
		ServerPlayer p = tony(h, new BlockPos(3, 2, 3));
		float max = IronManEnergy.maxIntegrity(HB);
		wear(p, HB, max);
		HulkbusterParts.hit(p, HulkbusterParts.RARM, max);
		HulkbusterParts.hit(p, HulkbusterParts.LLEG, max * 0.05f);
		IronManEnergy.setIntegrity(p, HB, 1000f);
		h.assertTrue(HulkbusterParts.broken(HulkbusterParts.of(p)[HulkbusterParts.RARM]), "the right arm is broken");
		h.assertTrue(IronManComicRouter.fireForTests(p, IronManSuits.MARK_8, 5, true), "Shift+V calls the pod");
		List<HulkbusterPodEntity> pods = h.getLevel().getEntities(IronManEntityTypes.HULKBUSTER_POD, new AABB(p.position(), p.position())
				.inflate(80), pod -> p.getUUID().equals(pod.ownerUuid()));
		h.assertTrue(pods.size() == 1, "one pod");
		HulkbusterPodEntity pod = pods.get(0);
		h.assertTrue(Math.abs(pod.anchor().y - p.getY() - HulkbusterPodEntity.HOVER_UP) < 1e-3, "it hovers 10 blocks above the call");
		h.assertTrue(pod.plates() >= 4 && pod.plateTarget(0) == HulkbusterParts.RARM, "plates, the worst part first");
		h.assertFalse(IronManComicRouter.fireForTests(p, IronManSuits.MARK_8, 5, true), "one at a time (cooldown)");
		int arrive = HulkbusterPodEntity.ARRIVE_TICKS;
		h.runAtTickTime(h.getTick() + arrive / 2, () -> h.assertTrue(pod.position().distanceTo(pod.anchor()) > 2.0,
				"still flying in halfway through the 3 s"));
		h.runAtTickTime(h.getTick() + arrive + 2, () -> {
			h.assertTrue(pod.position().distanceTo(pod.anchor()) < 0.5, "there after 3 s (" + pod.position().distanceTo(pod.anchor()) + ")");
			h.assertTrue(IronManEnergy.integrity(p, HB) == 1000f, "nothing repaired before the first plate lands");
		});
		h.runAtTickTime(h.getTick() + arrive + HulkbusterPodEntity.REPAIR_TICKS / 2, () -> {
			float mid = IronManEnergy.integrity(p, HB);
			h.assertTrue(mid > 1000f && mid < max, "repairing as the plates land (" + mid + ")");
		});
		h.runAtTickTime(h.getTick() + arrive + HulkbusterPodEntity.REPAIR_TICKS + 3, () -> {
			h.assertTrue(Math.abs(IronManEnergy.integrity(p, HB) - max) < 1f, "whole after ~8 s (" + IronManEnergy.integrity(p, HB) + ")");
			h.assertTrue(HulkbusterParts.raw(p.getItemBySlot(EquipmentSlot.CHEST))[HulkbusterParts.RARM] == 0f, "the arm is mended");
			h.assertTrue(pod.done(), "and it is leaving");
			leave(h, p);
			h.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void thePodRepairsAWreckedSentry(GameTestHelper h) {
		floor(h);
		ServerPlayer p = tony(h, new BlockPos(3, 2, 3));
		wear(p, HB, 15f);
		h.assertFalse(IronManDamage.onAllowDamage(p, h.getLevel().damageSources().generic(), 40f), "wrecked");
		IronManSentryEntity s = h.getLevel().getEntities(IronManEntityTypes.SENTRY, new AABB(p.position(), p.position()).inflate(8),
				e -> p.getUUID().equals(e.ownerId())).get(0);
		h.assertTrue(s.wrecked(), "a wreck");
		h.assertTrue(s.use(p, true), "sneak + right-click the wreck calls its pod");
		h.assertFalse(s.callPod(p), "the pod shares the move's cooldown");
		h.runAtTickTime(h.getTick() + HulkbusterPodEntity.ARRIVE_TICKS + HulkbusterPodEntity.REPAIR_TICKS + 4, () -> {
			h.assertTrue(Math.abs(s.integrity() - IronManEnergy.maxIntegrity(HB)) < 1f, "repaired (" + s.integrity() + ")");
			h.assertFalse(s.wrecked(), "and rebooted");
			leave(h, p);
			h.succeed();
		});
	}

	// ================================================================== 4. the inner suit

	@GameTest(template = EMPTY_STRUCTURE)
	public void theInnerSuitComesBackOnTheSentryStepOut(GameTestHelper h) {
		floor(h);
		ServerPlayer p = tony(h, new BlockPos(3, 2, 3));
		hulkbusterOverMk3(p, 3000f);
		h.assertTrue(HulkbusterInnerSuit.has(p) && MK3.equals(HulkbusterInnerSuit.stashedSuit(p)), "the Mk III is kept");
		h.assertTrue(IronManArmor.wearingFullSuit(p, HB), "the Hulkbuster on top");
		HulkbusterInnerSuit.tick(p);
		h.assertTrue(HulkbusterInnerSuit.has(p), "nothing comes back while the Hulkbuster is on");
		h.assertTrue(IronManSentryEntity.deploy(p) != null, "Sentry Mode: climb out");
		HulkbusterInnerSuit.tick(p);
		h.assertTrue(wearsMk3(p) && !HulkbusterInnerSuit.has(p), "back in the Mk III");
		h.assertTrue(Math.abs(IronManEnergy.integrity(p, MK3) - 333f) < 1f, "with its integrity (" + IronManEnergy.integrity(p, MK3) + ")");
		h.assertTrue(Math.abs(IronManEnergy.energy(p, MK3) - 444f) < 1f, "and its energy (" + IronManEnergy.energy(p, MK3) + ")");
		h.assertTrue(MK3.equals(TonyStark.state(p).activeSuit), "the Mk III is the active suit again");
		// and the Hulkbuster closes round it again, keeping it
		IronManSentryEntity s = h.getLevel().getEntities(IronManEntityTypes.SENTRY, new AABB(p.position(), p.position()).inflate(8),
				e -> p.getUUID().equals(e.ownerId())).get(0);
		h.assertTrue(s.canWrap(p), "the Hulkbuster may close round a worn suit");
		s.equipOnto(p);
		h.assertTrue(IronManArmor.wearingFullSuit(p, HB) && HulkbusterInnerSuit.has(p), "in the Hulkbuster again, the Mk III underneath");
		h.assertTrue(count(p, MK3) == 0, "nothing of the Mk III spilled into the pack");
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theInnerSuitComesBackWhenTheHulkbusterIsWrecked(GameTestHelper h) {
		floor(h);
		ServerPlayer p = tony(h, new BlockPos(3, 2, 3));
		hulkbusterOverMk3(p, 15f);
		h.assertFalse(IronManDamage.onAllowDamage(p, h.getLevel().damageSources().generic(), 40f), "the killing blow");
		h.assertFalse(IronManArmor.wearingFullSuit(p, HB), "thrown out");
		HulkbusterInnerSuit.tick(p);
		h.assertTrue(wearsMk3(p), "in the Mk III");
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void deathDropsTheInnerSuitUnlessKeepInventory(GameTestHelper h) {
		ServerPlayer p = tony(h, new BlockPos(3, 2, 3));
		GameRules.BooleanValue rule = h.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY);
		boolean old = rule.get();
		try {
			rule.set(true, h.getLevel().getServer());
			hulkbusterOverMk3(p, 3000f);
			HulkbusterInnerSuit.onDeath(p);
			h.assertTrue(HulkbusterInnerSuit.has(p), "keepInventory keeps it (copied to the respawned player)");
			rule.set(false, h.getLevel().getServer());
			HulkbusterInnerSuit.onDeath(p);
			h.assertFalse(HulkbusterInnerSuit.has(p), "otherwise it drops");
			List<ItemEntity> drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(3),
					e -> e.getItem().getItem() instanceof IronManArmorItem a && MK3.equals(a.suitId()));
			h.assertTrue(drops.size() == 4, "all four Mk III pieces on the ground (" + drops.size() + ")");
			drops.forEach(ItemEntity::discard);
		} finally {
			rule.set(old, h.getLevel().getServer());
		}
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theInnerSuitSurvivesASaveAndLoad(GameTestHelper h) {
		ServerPlayer p = tony(h, new BlockPos(3, 2, 3));
		hulkbusterOverMk3(p, 3000f);
		CompoundTag tag = new CompoundTag();
		p.saveWithoutId(tag);
		p.removeAttached(com.projecthero.mod.attachment.ModAttachments.HULKBUSTER_INNER_SUIT);
		h.assertFalse(HulkbusterInnerSuit.has(p), "cleared");
		p.load(tag);
		h.assertTrue(HulkbusterInnerSuit.has(p) && MK3.equals(HulkbusterInnerSuit.stashedSuit(p)), "back after a load (logout / login)");
		ItemStack chest = HulkbusterInnerSuit.stashed(p, 1);
		h.assertTrue(chest.getItem() instanceof IronManArmorItem a && MK3.equals(a.suitId())
				&& Math.abs(IronManEnergy.stackIntegrity(chest, MK3) - 333f) < 1f, "whole, integrity stamped");
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aRevokeOrATakenSlotNeverDeletesTheInnerSuit(GameTestHelper h) {
		ServerPlayer p = tony(h, new BlockPos(3, 2, 3));
		hulkbusterOverMk3(p, 3000f);
		TonyStark.revoke(p);
		IronManArmor.enforce(p); // the armour is rejected (into the pack)
		HulkbusterInnerSuit.tick(p);
		h.assertFalse(HulkbusterInnerSuit.has(p), "restored");
		h.assertFalse(IronManArmor.wearingAnyIronMan(p), "nothing Iron Man worn without the power");
		h.assertTrue(count(p, MK3) == 4 && count(p, HB) == 4, "both suits in the pack (" + count(p, MK3) + ", " + count(p, HB) + ")");
		// a slot already taken: that piece goes to the pack instead
		ServerPlayer q = tony(h, new BlockPos(5, 2, 5));
		hulkbusterOverMk3(q, 3000f);
		for (EquipmentSlot s : SLOTS) {
			q.setItemSlot(s, ItemStack.EMPTY);
		}
		q.setItemSlot(EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET));
		HulkbusterInnerSuit.tick(q);
		h.assertTrue(q.getItemBySlot(EquipmentSlot.HEAD).is(net.minecraft.world.item.Items.DIAMOND_HELMET), "the helmet slot left alone");
		h.assertTrue(count(q, MK3) == 1, "the Mk III helmet in the pack");
		h.assertTrue(q.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof IronManArmorItem a && MK3.equals(a.suitId()), "the rest on");
		leave(h, q);
		leave(h, p);
		h.succeed();
	}

	// ================================================================== 5. the mid-air build

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void theMidAirBuildPutsTheHulkbusterOnOverTheWornSuit(GameTestHelper h) {
		floor(h);
		ServerPlayer p = tony(h, new BlockPos(3, 9, 3));
		p.setOnGround(false);
		wear(p, MK3, 333f);
		IronManFlight.setFlying(p, true);
		p.getInventory().items.set(0, new ItemStack(IronManItems.HULKBUSTER_UPLINK)); // fresh
		p.getInventory().selected = 0;
		h.assertTrue(com.projecthero.mod.ironman.item.HulkbusterUplinkItem.airborne(p), "in the air");
		h.assertTrue(HulkbusterCrate.buildInAir(p), "the Uplink, used in the air, calls the build pod");
		h.assertTrue(p.getInventory().items.get(0).isEmpty(), "and is used up");
		h.assertTrue(p.getVehicle() instanceof HulkbusterPodEntity && HulkbusterPodEntity.building(p), "held by the pod");
		h.assertTrue(wearsMk3(p), "the Mk III stays on while the pieces fly in");
		HulkbusterPodEntity pod = (HulkbusterPodEntity) p.getVehicle();
		Vec3 back = Vec3.directionFromRotation(0f, p.getYRot()).scale(-1);
		h.assertTrue(pod.anchor().subtract(p.position()).dot(back) > 1.5 && pod.anchor().y > p.getY() + 1.5, "it hovers above and behind");
		HulkbusterInnerSuit.tick(p);
		h.assertFalse(HulkbusterInnerSuit.has(p), "nothing stashed yet");
		h.runAtTickTime(h.getTick() + HulkbusterPodEntity.BUILD_DONE + 3, () -> {
			h.assertTrue(IronManArmor.wearingFullSuit(p, HB), "the Hulkbuster built round them");
			h.assertTrue(HulkbusterInnerSuit.has(p) && MK3.equals(HulkbusterInnerSuit.stashedSuit(p)), "the Mk III kept underneath");
			h.assertFalse(p.isPassenger() || HulkbusterPodEntity.building(p), "let go");
			h.assertTrue(IronManFlight.isFlying(p), "flying in the Hulkbuster");
			h.assertTrue(BUILD_SECONDS_OK, "the build takes 4-6 s");
			leave(h, p);
			h.succeed();
		});
	}

	private static final boolean BUILD_SECONDS_OK = HulkbusterPodEntity.BUILD_DONE >= 80 && HulkbusterPodEntity.BUILD_DONE <= 120;

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void aLogoutMidBuildFinishesIt(GameTestHelper h) {
		ServerPlayer p = tony(h, new BlockPos(3, 9, 3));
		p.setOnGround(false);
		p.getInventory().items.set(0, new ItemStack(IronManItems.HULKBUSTER_UPLINK));
		h.assertTrue(HulkbusterCrate.buildInAir(p), "build started");
		HulkbusterPodEntity.completeFor(p); // what the disconnect hook does
		h.assertTrue(IronManArmor.wearingFullSuit(p, HB) && !HulkbusterPodEntity.building(p), "the suit goes on at once");
		leave(h, p);
		h.succeed();
	}
}
