package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.ProtocolPhoenix;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.ability.IronManBeamRecipients;
import com.projecthero.mod.ironman.data.StarkSuitReturnQueue;
import com.projecthero.mod.ironman.entity.IronManDeliveryPodEntity;
import com.projecthero.mod.ironman.entity.IronManEntityTypes;
import com.projecthero.mod.ironman.entity.IronManSuitPartEntity;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.item.SuitcaseContents;
import com.projecthero.mod.ironman.suit.IronManSuitCall;
import com.projecthero.mod.ironman.suit.IronManSuitFx;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.SuitUpType;
import com.projecthero.mod.ironman.suit.SummonType;
import com.projecthero.mod.network.IronManSuitListPayload;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.21 Iron Man revamp: the real armour stack travels end to end (inventory, couriers, platform, pod, Mark V case,
 * death recovery, the return queue, Protocol Phoenix), the platform's animated deploy / retrieve never dupes or loses a
 * piece, the synced suit-up clock round-trips, the delivery pod cleans up after itself, beams reach far viewers, and the
 * dead Mark 42 / 50 code is gone.
 */
public class IronManSuitUpV01421GameTests implements FabricGameTest {
	private static final ArmorItem.Type[] TYPES = {
			ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };

	// ------------------------------------------------------------------ helpers

	private static ServerPlayer player(GameTestHelper h) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		StarkGlassesV0151GameTests.wearGlasses(p); // v0.15.1: calling a suit needs the Stark Glasses
		return p;
	}

	private static void placeAt(GameTestHelper h, ServerPlayer p, BlockPos rel) {
		Vec3 v = Vec3.atBottomCenterOf(h.absolutePos(rel));
		p.setPos(v.x, v.y, v.z);
	}

	private static Holder<Enchantment> protection(GameTestHelper h) {
		return h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION);
	}

	/** Unique per test (its structure origin), so tests running side by side in one level never count each other's items. */
	private static String nameFor(GameTestHelper h, String suitId, ArmorItem.Type type) {
		return "Tony's " + suitId + " " + type.getName() + " @" + h.absolutePos(BlockPos.ZERO).toShortString();
	}

	/** A piece with Protection III and a custom name -- the identity every path must preserve. */
	private static ItemStack marked(GameTestHelper h, String suitId, ArmorItem.Type type) {
		ItemStack s = new ItemStack(IronManItems.armor(suitId, type));
		s.enchant(protection(h), 3);
		s.set(DataComponents.CUSTOM_NAME, Component.literal(nameFor(h, suitId, type)));
		return s;
	}

	private static boolean isMarked(GameTestHelper h, ItemStack s, String suitId, ArmorItem.Type type) {
		return s.getItem() instanceof IronManArmorItem a && a.suitId().equals(suitId) && a.getType() == type
				&& s.getEnchantments().getLevel(protection(h)) == 3
				&& nameFor(h, suitId, type).equals(s.getHoverName().getString());
	}

	private static void assertWornMarked(GameTestHelper h, ServerPlayer p, String suitId) {
		for (ArmorItem.Type t : TYPES) {
			ItemStack worn = p.getItemBySlot(IronManSuitUpManager.slotFor(t));
			h.assertTrue(isMarked(h, worn, suitId, t), "the worn " + t.getName() + " must be the very same enchanted, named stack, got "
					+ worn + " named " + worn.getHoverName().getString());
		}
	}

	private static void runSequence(ServerPlayer p) {
		for (int i = 0; i < 300 && IronManSuitUpManager.inTransition(p); i++) {
			IronManSuitUpManager.tick(p);
		}
	}

	private static IronManSuitPlatformBlockEntity platform(GameTestHelper h, BlockPos rel) {
		h.setBlock(rel, IronManBlocks.IRON_MAN_SUIT_PLATFORM);
		return (IronManSuitPlatformBlockEntity) h.getBlockEntity(rel);
	}

	/** How many copies of the marked {@code type} piece exist anywhere we can see (worn, pack, rack, on the ground). */
	private static int copiesOf(GameTestHelper h, ServerPlayer p, IronManSuitPlatformBlockEntity be, String suitId, ArmorItem.Type type) {
		int n = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			n += isMarked(h, p.getItemBySlot(slot), suitId, type) ? 1 : 0;
		}
		for (ItemStack s : p.getInventory().items) {
			n += isMarked(h, s, suitId, type) ? 1 : 0;
		}
		if (be != null) {
			for (int i = 0; i < be.getContainerSize(); i++) {
				n += isMarked(h, be.getItem(i), suitId, type) ? 1 : 0;
			}
		}
		for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(BlockPos.ZERO)).inflate(40))) {
			n += isMarked(h, e.getItem(), suitId, type) ? 1 : 0;
		}
		return n;
	}

	// ------------------------------------------------------------------ the synced clock

	@GameTest(template = EMPTY_STRUCTURE)
	public void suitFxStreamCodecRoundTrips(GameTestHelper h) {
		IronManSuitFx fx = new IronManSuitFx(11L, 22L, 33L, 44L, 0b1010, IronManSuitFx.STYLE_CASE, 555L, 66,
				IronManSuitFx.POSE_CASE_UP, 777L, 3);
		ByteBuf buf = Unpooled.buffer();
		IronManSuitFx.STREAM_CODEC.encode(buf, fx);
		IronManSuitFx back = IronManSuitFx.STREAM_CODEC.decode(buf);
		h.assertTrue(fx.equals(back), "the suit-up clock must survive the sync codec unchanged, got " + back);
		h.assertTrue(buf.readableBytes() == 0, "the codec must consume exactly what it wrote");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void pieceProgressRunsTheLockOnAndReleaseWindows(GameTestHelper h) {
		IronManSuitFx fx = IronManSuitFx.EMPTY.withPiece(1, 100L, true).withPiece(0, 100L, false);
		h.assertTrue(fx.pieceProgress(EquipmentSlot.CHEST, 100L, 0f) == 0f, "a piece starts locking on invisible");
		// v0.14.27: an ordinary (STYLE_PLATES) piece builds on over BUILD_TICKS (3 s)
		h.assertTrue(Math.abs(fx.pieceProgress(EquipmentSlot.CHEST, 100L + IronManSuitFx.BUILD_TICKS / 2, 0f) - 0.5f) < 1e-4f, "halfway through the build");
		h.assertTrue(fx.pieceProgress(EquipmentSlot.CHEST, 100L + IronManSuitFx.BUILD_TICKS, 0f) == 1f, "fully on at the end");
		h.assertTrue(fx.pieceProgress(EquipmentSlot.HEAD, 100L, 0f) == 1f, "a release starts fully on");
		h.assertTrue(fx.pieceProgress(EquipmentSlot.HEAD, 100L + IronManSuitFx.RELEASE_TICKS, 0f) == 0f, "and ends fully off");
		h.assertTrue(fx.pieceProgress(EquipmentSlot.HEAD, 200L, 0f) == 1f,
				"a stale release clock must never hide a piece equipped later");
		h.assertTrue(fx.piecePhase(EquipmentSlot.CHEST, 101L) == IronManSuitFx.PHASE_LOCK_ON
				&& fx.piecePhase(EquipmentSlot.HEAD, 101L) == IronManSuitFx.PHASE_RELEASE
				&& fx.piecePhase(EquipmentSlot.LEGS, 101L) == IronManSuitFx.PHASE_NONE, "clip phases follow the clocks");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void suitUpWritesTheSyncedClockForEveryPiece(GameTestHelper h) {
		ServerPlayer p = player(h);
		for (ArmorItem.Type t : TYPES) {
			p.getInventory().add(marked(h, "mark_iii", t));
		}
		h.assertTrue(IronManSuitUpManager.beginSuitUp(p, "mark_iii"), "suit-up starts");
		h.assertTrue(IronManSuitFx.of(p).poseKind() == IronManSuitFx.POSE_SUIT_UP, "the suit-up pose clock is running");
		runSequence(p);
		IronManSuitFx fx = IronManSuitFx.of(p);
		for (int bit = 0; bit < 4; bit++) {
			h.assertTrue(fx.start(bit) > 0L && fx.assembling(bit), "piece " + bit + " got a lock-on clock");
		}
		h.assertTrue(fx.faceplateAt() > 0L, "the suit-up ends with the faceplate-close beat");
		h.succeed();
	}

	// ------------------------------------------------------------------ item identity

	@GameTest(template = EMPTY_STRUCTURE)
	public void inventorySuitUpAndDownKeepTheRealStacks(GameTestHelper h) {
		ServerPlayer p = player(h);
		for (ArmorItem.Type t : TYPES) {
			p.getInventory().add(marked(h, "mark_iii", t));
		}
		h.assertTrue(IronManSuitUpManager.beginSuitUp(p, "mark_iii"), "suit-up starts");
		runSequence(p);
		assertWornMarked(h, p, "mark_iii");

		h.assertTrue(IronManSuitUpManager.beginSuitDown(p, "mark_iii"), "suit-down starts");
		// the helmet's plates break away BEFORE it leaves the slot
		int helmetStage = IronManSuitUpManager.stageTick(0, false, SuitUpType.MECHANICAL_REMOTE);
		for (int i = 0; i <= helmetStage; i++) {
			IronManSuitUpManager.tick(p);
		}
		h.assertTrue(!p.getItemBySlot(EquipmentSlot.HEAD).isEmpty(),
				"while its release animation plays the helmet is still worn (never in limbo)");
		runSequence(p);
		h.assertFalse(IronManArmor.wearingAnyIronMan(p), "the suit is off");
		for (ArmorItem.Type t : TYPES) {
			h.assertTrue(copiesOf(h, p, null, "mark_iii", t) == 1, t.getName() + " exists exactly once, enchanted and named");
		}
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void courierCarriesTheRealStackOntoTheBody(GameTestHelper h) {
		ServerPlayer p = player(h);
		placeAt(h, p, new BlockPos(4, 1, 4));
		p.getInventory().add(marked(h, "mark_iii", ArmorItem.Type.CHESTPLATE));
		h.assertTrue(IronManSuitCall.callPiece(p, "mark_iii", ArmorItem.Type.CHESTPLATE), "the chestplate launches");
		List<IronManSuitPartEntity> couriers = h.getLevel().getEntitiesOfClass(IronManSuitPartEntity.class, p.getBoundingBox().inflate(12), e -> e.ownerEntityId() == p.getId());
		h.assertTrue(couriers.size() == 1 && isMarked(h, couriers.get(0).piece(), "mark_iii", ArmorItem.Type.CHESTPLATE),
				"the courier carries the very same enchanted, named stack");
		h.assertTrue(p.getInventory().countItem(IronManItems.armor("mark_iii", ArmorItem.Type.CHESTPLATE)) == 0,
				"and it left the inventory");
		h.succeedWhen(() -> {
			h.assertTrue(isMarked(h, p.getItemBySlot(EquipmentSlot.CHEST), "mark_iii", ArmorItem.Type.CHESTPLATE),
					"the courier clamps its real stack onto the chest");
			h.assertTrue(h.getLevel().getEntitiesOfClass(IronManSuitPartEntity.class, p.getBoundingBox().inflate(30), e -> e.ownerEntityId() == p.getId()).isEmpty(),
					"and is gone");
			h.assertTrue(IronManSuitFx.of(p).assembling(1), "arrival starts the chest's lock-on reveal");
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void courierSavesItsStackAndDropsItWhenTheOwnerIsGone(GameTestHelper h) {
		ServerPlayer p = player(h);
		placeAt(h, p, new BlockPos(4, 1, 4));
		Vec3 at = Vec3.atCenterOf(h.absolutePos(new BlockPos(2, 2, 2)));
		IronManSuitPartEntity c = IronManSuitPartEntity.spawn(h.getLevel(), at, p, marked(h, "mark_iii", ArmorItem.Type.BOOTS), 400);
		CompoundTag tag = new CompoundTag();
		c.saveWithoutId(tag);
		c.discard();
		h.assertTrue(tag.contains("Piece"), "the courier writes its stack to NBT");
		tag.putUUID("Owner", UUID.randomUUID()); // reloaded after its owner logged off
		IronManSuitPartEntity loaded = new IronManSuitPartEntity(IronManEntityTypes.SUIT_PART, h.getLevel());
		loaded.load(tag);
		loaded.setPos(at.x, at.y, at.z);
		h.assertTrue(isMarked(h, loaded.piece(), "mark_iii", ArmorItem.Type.BOOTS), "the stack round-trips through NBT intact");
		h.getLevel().addFreshEntity(loaded);
		h.succeedWhen(() -> {
			List<ItemEntity> drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(3),
					e -> isMarked(h, e.getItem(), "mark_iii", ArmorItem.Type.BOOTS));
			h.assertTrue(drops.size() == 1, "an orphaned courier drops its real stack, exactly once");
			h.assertTrue(loaded.isRemoved(), "and discards itself");
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void markVCaseHoldsTheRealSuitBothWays(GameTestHelper h) {
		ServerPlayer p = player(h);
		for (ArmorItem.Type t : TYPES) {
			p.setItemSlot(IronManSuitUpManager.slotFor(t), marked(h, "mark_v", t));
		}
		h.assertTrue(IronManSuitUpManager.beginSuitDownToCase(p, "mark_v"), "fold into the case");
		runSequence(p);
		h.assertFalse(IronManArmor.wearingAnyIronMan(p), "the suit is folded away");
		int caseIdx = -1;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			if (p.getInventory().getItem(i).is(IronManItems.MARK_V_SUITCASE)) {
				h.assertTrue(caseIdx < 0, "exactly one case");
				caseIdx = i;
			}
		}
		h.assertTrue(caseIdx >= 0, "the case is in the inventory");
		ItemStack caseStack = p.getInventory().getItem(caseIdx);
		var slots = SuitcaseContents.read(caseStack);
		for (int i = 0; i < 4; i++) {
			h.assertTrue(isMarked(h, slots.get(i), "mark_v", TYPES[i]), "the case holds the real " + TYPES[i].getName());
		}

		h.assertTrue(IronManSuitUpManager.beginSuitUpFromCase(p, "mark_v"), "unfold from the case");
		runSequence(p);
		assertWornMarked(h, p, "mark_v");
		h.assertTrue(p.getInventory().countItem(IronManItems.MARK_V_SUITCASE) == 0, "the emptied case is used up");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void legacyCaseStillUnfoldsButAnEmptyOneDoesNot(GameTestHelper h) {
		ServerPlayer p = player(h);
		ItemStack empty = new ItemStack(IronManItems.MARK_V_SUITCASE);
		SuitcaseContents.write(empty, net.minecraft.core.NonNullList.withSize(4, ItemStack.EMPTY));
		p.getInventory().add(empty);
		h.assertFalse(IronManSuitUpManager.beginSuitUpFromCase(p, "mark_v"),
				"an emptied (modern) case must never conjure a free suit");
		p.getInventory().clearContent();

		p.getInventory().add(new ItemStack(IronManItems.MARK_V_SUITCASE)); // crafted / creative / pre-0.14.21
		h.assertTrue(IronManSuitUpManager.beginSuitUpFromCase(p, "mark_v"), "a legacy case still unfolds");
		runSequence(p);
		h.assertTrue(IronManArmor.wearingFullSuit(p, "mark_v"), "into a whole Mark V");
		h.assertTrue(p.getInventory().countItem(IronManItems.MARK_V_SUITCASE) == 0, "and is used up");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void deathRecoveryDocksTheRealStacks(GameTestHelper h) {
		ServerPlayer p = player(h);
		placeAt(h, p, new BlockPos(5, 1, 5));
		h.getLevel().getServer().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY)
				.set(false, h.getLevel().getServer());
		for (ArmorItem.Type t : TYPES) {
			p.setItemSlot(IronManSuitUpManager.slotFor(t), marked(h, "mark_iii", t));
		}
		IronManSuitPlatformBlockEntity be = platform(h, new BlockPos(2, 2, 2));
		be.bindTo(p.getUUID());
		IronManSuitCall.recoverSuitOnDeath(p);
		for (int i = 0; i < 4; i++) {
			h.assertTrue(isMarked(h, be.getItem(i), "mark_iii", TYPES[i]), "the docked " + TYPES[i].getName()
					+ " is the real enchanted, named stack");
		}
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void returnQueueKeepsTheRealStacksThroughSaveAndLoad(GameTestHelper h) {
		var ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		List<ItemStack> stacks = new ArrayList<>();
		for (ArmorItem.Type t : TYPES) {
			stacks.add(marked(h, "mark_iii", t));
		}
		BlockPos rel = new BlockPos(2, 2, 2);
		IronManSuitPlatformBlockEntity be = platform(h, rel);
		GlobalPos target = GlobalPos.of(h.getLevel().dimension(), h.absolutePos(rel));
		var pending = new StarkSuitReturnQueue.Pending(UUID.randomUUID(), target, "mark_iii", 0b1111, 100f, 50f, stacks);
		var tag = StarkSuitReturnQueue.Pending.CODEC.encodeStart(ops, pending).getOrThrow();
		var back = StarkSuitReturnQueue.Pending.CODEC.parse(ops, tag).getOrThrow();
		h.assertTrue(back.stacks().size() == 4 && isMarked(h, back.stacks().get(1), "mark_iii", ArmorItem.Type.CHESTPLATE),
				"queued stacks survive a save / load");

		StarkSuitReturnQueue q = StarkSuitReturnQueue.get(h.getLevel());
		q.enqueue(back.owner(), target, "mark_iii", 0b1111, 100f, 50f, back.stacks());
		q.absorb(h.getLevel(), h.absolutePos(rel), be);
		for (int i = 0; i < 4; i++) {
			h.assertTrue(isMarked(h, be.getItem(i), "mark_iii", TYPES[i]), "the platform receives the real " + TYPES[i].getName());
		}
		h.assertFalse(q.hasPendingFor(h.getLevel(), h.absolutePos(rel)), "and the record is consumed");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void protocolPhoenixRecallKeepsTheRealStacks(GameTestHelper h) {
		ServerPlayer p = player(h);
		for (ArmorItem.Type t : TYPES) {
			p.getInventory().add(marked(h, "mark_vii", t)); // v0.15.1: Phoenix only ever recalls a Mark 7 or later
		}
		try {
			h.assertTrue(ProtocolPhoenix.tryActivate(p, p.damageSources().generic()), "Phoenix takes over");
			runSequence(p);
			assertWornMarked(h, p, "mark_vii");
		} finally {
			ProtocolPhoenix.clearEmergency(p);
			TonyStark.setPhoenixReadyAt(p, 0L);
		}
		h.succeed();
	}

	// ------------------------------------------------------------------ Suit Platform sequence

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300) // v0.15.1: the robotic-arm deploy takes 160 ticks
	public void platformDeployIsASequenceThatNeverDupesOrLoses(GameTestHelper h) {
		ServerPlayer p = player(h);
		IronManSuitPlatformBlockEntity be = platform(h, new BlockPos(2, 2, 2));
		placeAt(h, p, new BlockPos(4, 2, 2));
		for (ArmorItem.Type t : TYPES) {
			be.store(marked(h, "mark_iii", t));
		}
		h.assertTrue(be.deployTo(p), "deploy starts");
		h.assertFalse(IronManArmor.wearingAnyIronMan(p), "nothing is on yet -- the pieces lift off one by one");
		h.assertTrue(be.sequenceRunning() && IronManSuitUpManager.inTransition(p), "the sequence holds the player's suit-up state");
		h.assertFalse(be.deployTo(p), "a second deploy during the sequence is refused");
		h.onEachTick(() -> {
			for (ArmorItem.Type t : TYPES) {
				h.assertTrue(copiesOf(h, p, be, "mark_iii", t) == 1, t.getName() + " must exist exactly once every tick");
			}
		});
		h.succeedWhen(() -> {
			assertWornMarked(h, p, "mark_iii");
			h.assertTrue(be.isEmptyPlatform() && !be.sequenceRunning(), "the rack is empty and the sequence over");
			h.assertTrue(p.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof IronManArmorItem, "boots first");
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void platformRetrieveIsASequenceThatNeverDupesOrLoses(GameTestHelper h) {
		ServerPlayer p = player(h);
		IronManSuitPlatformBlockEntity be = platform(h, new BlockPos(2, 2, 2));
		placeAt(h, p, new BlockPos(4, 2, 2));
		for (ArmorItem.Type t : TYPES) {
			p.setItemSlot(IronManSuitUpManager.slotFor(t), marked(h, "mark_iii", t));
		}
		h.assertTrue(be.retrieveFrom(p), "retrieve starts");
		h.assertTrue(IronManArmor.wearingFullSuit(p, "mark_iii"), "the suit is still on -- it breaks away piece by piece");
		h.onEachTick(() -> {
			for (ArmorItem.Type t : TYPES) {
				h.assertTrue(copiesOf(h, p, be, "mark_iii", t) == 1, t.getName() + " must exist exactly once every tick");
			}
		});
		h.succeedWhen(() -> {
			h.assertFalse(IronManArmor.wearingAnyIronMan(p), "the suit is off");
			h.assertTrue(be.isFull() && !be.sequenceRunning(), "and racked");
			for (int i = 0; i < 4; i++) {
				h.assertTrue(isMarked(h, be.getItem(i), "mark_iii", TYPES[i]), "racked " + TYPES[i].getName() + " is the real stack");
			}
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void walkingAwayMidDeployStopsSafely(GameTestHelper h) {
		ServerPlayer p = player(h);
		IronManSuitPlatformBlockEntity be = platform(h, new BlockPos(2, 2, 2));
		placeAt(h, p, new BlockPos(4, 2, 2));
		for (ArmorItem.Type t : TYPES) {
			be.store(marked(h, "mark_iii", t));
		}
		h.assertTrue(be.deployTo(p), "deploy starts");
		// v0.15.1: boots land at tick 43, leggings at 73 -- get carried off in between (the deploy holds you still)
		int walkOff = IronManSuitPlatformBlockEntity.deployEquipTick(0, 4) + 3;
		h.runAfterDelay(walkOff, () -> {
			Vec3 far = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2, 2, 2))).add(30, 0, 0);
			p.setPos(far.x, far.y, far.z);
		});
		h.runAfterDelay(walkOff + 12, () -> {
			h.assertFalse(be.sequenceRunning(), "the sequence stopped when the player walked away");
			h.assertFalse(IronManSuitPlatformBlockEntity.isFrozen(p), "and let go of the player");
			h.assertFalse(IronManSuitUpManager.inTransition(p), "and handed the player's suit-up state back");
			int worn = 0;
			for (ArmorItem.Type t : TYPES) {
				h.assertTrue(copiesOf(h, p, be, "mark_iii", t) == 1, t.getName() + " exists exactly once after the interruption");
				worn += p.getItemBySlot(IronManSuitUpManager.slotFor(t)).isEmpty() ? 0 : 1;
			}
			h.assertTrue(worn >= 1 && worn < 4, "only what had already landed is worn, got " + worn);
			h.succeed();
		});
	}

	// ------------------------------------------------------------------ Mark VII delivery pod

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void markViiPodDeliversTheSuitAndCleansUp(GameTestHelper h) {
		ServerPlayer p = player(h);
		IronManSuitPlatformBlockEntity be = platform(h, new BlockPos(2, 2, 2));
		be.bindTo(p.getUUID());
		placeAt(h, p, new BlockPos(5, 2, 5));
		for (ArmorItem.Type t : TYPES) {
			be.store(marked(h, "mark_vii", t));
		}
		IronManSuitCall.execute(p, "mark_vii", IronManSuitListPayload.SOURCE_PLATFORM);
		// v0.14.29: the orbital drop starts 70 blocks up -- the box reaches the sky
		AABB area = new AABB(h.absolutePos(BlockPos.ZERO)).inflate(60).expandTowards(0, 100, 0);
		// (tests run side by side in one level: only look at this player's pod / couriers)
		java.util.function.Predicate<IronManDeliveryPodEntity> mine = e -> p.getUUID().equals(e.ownerId());
		java.util.function.Predicate<IronManSuitPartEntity> myCouriers = e -> e.ownerEntityId() == p.getId();
		List<IronManDeliveryPodEntity> pods = h.getLevel().getEntitiesOfClass(IronManDeliveryPodEntity.class, area, mine);
		h.assertTrue(pods.size() == 1, "one delivery pod launches, got " + pods.size());
		h.assertTrue(pods.get(0).cargo().size() == 4 && be.isEmptyPlatform(), "carrying all four real stacks off the rack");
		h.assertTrue(h.getLevel().getEntitiesOfClass(IronManSuitPartEntity.class, area, myCouriers).isEmpty(),
				"no loose couriers yet -- the pod fires them out once it has landed");
		h.succeedWhen(() -> {
			assertWornMarked(h, p, "mark_vii");
			h.assertTrue(h.getLevel().getEntitiesOfClass(IronManDeliveryPodEntity.class, area, mine).isEmpty(), "the pod has flown off");
			h.assertTrue(h.getLevel().getEntitiesOfClass(IronManSuitPartEntity.class, area, myCouriers).isEmpty(), "no stray couriers");
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void orphanedPodDropsItsCargo(GameTestHelper h) {
		Vec3 at = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(3, 2, 3)));
		IronManDeliveryPodEntity pod = new IronManDeliveryPodEntity(IronManEntityTypes.DELIVERY_POD, h.getLevel());
		CompoundTag tag = new CompoundTag();
		tag.putUUID("Owner", UUID.randomUUID());
		ListTag cargo = new ListTag();
		cargo.add(marked(h, "mark_vii", ArmorItem.Type.HELMET).save(h.getLevel().registryAccess()));
		cargo.add(marked(h, "mark_vii", ArmorItem.Type.BOOTS).save(h.getLevel().registryAccess()));
		tag.put("Cargo", cargo);
		pod.load(tag);
		pod.setPos(at.x, at.y, at.z);
		h.getLevel().addFreshEntity(pod);
		h.succeedWhen(() -> {
			h.assertTrue(pod.isRemoved(), "a pod whose owner is gone removes itself");
			for (ArmorItem.Type t : new ArmorItem.Type[] { ArmorItem.Type.HELMET, ArmorItem.Type.BOOTS }) {
				h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(4),
						e -> isMarked(h, e.getItem(), "mark_vii", t)).size() == 1, "and drops its real " + t.getName() + " once");
			}
		});
	}

	// ------------------------------------------------------------------ commands, beams, dead code

	@GameTest(template = EMPTY_STRUCTURE)
	public void commandSuitUsesTheRealCallPath(GameTestHelper h) {
		ServerPlayer p = player(h);
		for (ArmorItem.Type t : TYPES) {
			p.getInventory().add(marked(h, "mark_iii", t));
		}
		h.assertTrue(IronManSuitCall.commandCall(p, "mark_iii"), "/ironman suit with the suit in the pack suits up");
		h.assertTrue(IronManSuitUpManager.inTransition(p), "as a staged suit-up");
		runSequence(p);
		assertWornMarked(h, p, "mark_iii");
		h.assertFalse(IronManSuitCall.commandCall(p, "mark_iii"), "not while already suited");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void beamsReachViewersOutsideTrackingRange(GameTestHelper h) {
		ServerPlayer shooter = player(h);
		ServerPlayer target = h.makeMockServerPlayerInLevel();
		ServerPlayer faraway = h.makeMockServerPlayerInLevel();
		Vec3 origin = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1, 2, 1)));
		shooter.setPos(origin.x, origin.y, origin.z);
		target.setPos(origin.x + 110, origin.y, origin.z);   // where the beam lands, far past any tracking range
		faraway.setPos(origin.x, origin.y, origin.z + 400);  // nowhere near the beam
		Vec3 start = shooter.getEyePosition();
		Vec3 end = start.add(110, 0, 0);
		var who = IronManBeamRecipients.recipients(shooter, start, end);
		h.assertTrue(who.contains(shooter), "the shooter sees their own beam");
		h.assertTrue(who.contains(target), "the player the beam is aimed at sees it, however far away");
		h.assertFalse(who.contains(faraway), "a player 400 blocks from the beam is not sent it");
		h.assertTrue(IronManBeamRecipients.distanceToSegmentSqr(new Vec3(5, 3, 0), Vec3.ZERO, new Vec3(10, 0, 0)) == 9.0,
				"segment distance maths");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void deadMark42And50CodeIsGone(GameTestHelper h) {
		h.assertTrue(SuitUpType.values().length == 3, "only MECHANICAL_REMOTE, SUITCASE_MOVIE, REMOTE_AUTOMATED remain");
		h.assertTrue(SummonType.values().length == 3, "only FLYING_SET, TRACKING_POD, SUITCASE_ITEM remain");
		h.assertTrue(BuiltInRegistries.ITEM.containsKey(com.projecthero.mod.ProjectHeroMod.id("modular_armor_controller"))
				&& BuiltInRegistries.ITEM.containsKey(com.projecthero.mod.ProjectHeroMod.id("nanotech_matrix")),
				"the dead items stay registered so old worlds keep them");
		h.assertTrue(com.projecthero.mod.ironman.suit.IronManSuits.MARK_VII.summonType() == SummonType.TRACKING_POD,
				"the Mark VII is delivered by pod");
		h.succeed();
	}

	// ------------------------------------------------------------------ v0.14.21 self-assembly + faceplate lift

	@GameTest(template = EMPTY_STRUCTURE)
	public void assemblyTimetableIsOrderedAndFinishesInsideTheWindow(GameTestHelper h) {
		for (boolean fromCase : new boolean[] { false, true }) {
			for (int bit = 0; bit < 4; bit++) {
				List<List<String>> groups = com.projecthero.mod.ironman.suit.IronManAssemblyPlan.groups(bit, fromCase);
				h.assertTrue(!groups.isEmpty(), "piece " + bit + " has a timetable");
				float prev = -1f;
				for (List<String> g : groups) {
					for (String bone : g) {
						float s = com.projecthero.mod.ironman.suit.IronManAssemblyPlan.start(bit, bone, fromCase);
						float snap = com.projecthero.mod.ironman.suit.IronManAssemblyPlan.snapAt(bit, bone, fromCase);
						h.assertTrue(s > prev, bone + " starts after the group before it");
						h.assertTrue(snap * IronManSuitFx.LOCK_TICKS <= IronManSuitFx.LOCK_TICKS
								&& snap <= com.projecthero.mod.ironman.suit.IronManAssemblyPlan.END, bone + " snaps home inside the lock-on window");
						h.assertTrue(com.projecthero.mod.ironman.suit.IronManAssemblyPlan.local(bit, bone, fromCase, 0f) == 0f,
								bone + " has not started at progress 0");
						float done = com.projecthero.mod.ironman.suit.IronManAssemblyPlan.local(bit, bone, fromCase, 1f);
						h.assertTrue(done == 1f && com.projecthero.mod.ironman.suit.IronManAssemblyPlan.displacement(done, true) == 0f
								&& com.projecthero.mod.ironman.suit.IronManAssemblyPlan.displacement(done, false) == 0f,
								bone + " is exactly home when the window ends (lock-on) / starts (release)");
						h.assertTrue(com.projecthero.mod.ironman.suit.IronManAssemblyPlan.start(bit, bone, fromCase)
								== com.projecthero.mod.ironman.suit.IronManAssemblyPlan.start(bit, bone, fromCase), "deterministic");
					}
					prev = com.projecthero.mod.ironman.suit.IronManAssemblyPlan.start(bit, g.get(0), fromCase);
				}
			}
		}
		// the mechanical order the user asked for
		String[] chest = { "arc_reactor", "chest_armor", "waist", "right_shoulder", "right_upper_arm", "right_gauntlet" };
		for (int i = 1; i < chest.length; i++) {
			h.assertTrue(com.projecthero.mod.ironman.suit.IronManAssemblyPlan.start(1, chest[i - 1], false)
					< com.projecthero.mod.ironman.suit.IronManAssemblyPlan.start(1, chest[i], false), chest[i - 1] + " before " + chest[i]);
		}
		h.assertTrue(com.projecthero.mod.ironman.suit.IronManAssemblyPlan.start(0, "helmet", false)
				< com.projecthero.mod.ironman.suit.IronManAssemblyPlan.start(0, "helmet_brow", false)
				&& com.projecthero.mod.ironman.suit.IronManAssemblyPlan.start(0, "helmet_brow", false)
						< com.projecthero.mod.ironman.suit.IronManAssemblyPlan.start(0, "faceplate", false), "helmet: shell, brow, faceplate last");
		h.assertTrue(com.projecthero.mod.ironman.suit.IronManAssemblyPlan.start(2, "right_thigh", false)
				< com.projecthero.mod.ironman.suit.IronManAssemblyPlan.start(2, "right_thigh_plate", false)
				&& com.projecthero.mod.ironman.suit.IronManAssemblyPlan.start(2, "right_thigh_plate", false)
						< com.projecthero.mod.ironman.suit.IronManAssemblyPlan.start(2, "right_knee", false), "legs: thighs, plates, knees");
		h.assertTrue(com.projecthero.mod.ironman.suit.IronManAssemblyPlan.start(1, "right_gauntlet", true) == 0f,
				"Mark V case: the right gauntlet comes out of the case first");
		// a bone overshoots a little and settles, with no frame-to-frame jump anywhere on either path
		float minD = 1f;
		float prevUp = com.projecthero.mod.ironman.suit.IronManAssemblyPlan.displacement(0f, true);
		float prevDown = com.projecthero.mod.ironman.suit.IronManAssemblyPlan.displacement(0f, false);
		for (int i = 1; i <= 1000; i++) {
			float t = i / 1000f;
			float up = com.projecthero.mod.ironman.suit.IronManAssemblyPlan.displacement(t, true);
			float down = com.projecthero.mod.ironman.suit.IronManAssemblyPlan.displacement(t, false);
			h.assertTrue(Math.abs(up - prevUp) < 0.01f && Math.abs(down - prevDown) < 0.01f,
					"displacement is continuous at t=" + t + " (" + prevUp + " -> " + up + ", " + prevDown + " -> " + down + ")");
			minD = Math.min(minD, up);
			prevUp = up;
			prevDown = down;
		}
		h.assertTrue(minD < -0.02f && minD > -0.15f, "a small servo overshoot past home: " + minD);
		// the synced clock never makes a piece flash in whole: a client a tick behind the start reads "just started",
		// and a finished release keeps it gone until the empty slot syncs
		IronManSuitFx fx = IronManSuitFx.EMPTY.withPiece(1, 100L, true).withPiece(0, 100L, false);
		h.assertTrue(fx.pieceProgress(EquipmentSlot.CHEST, 99L, 0f) == 0f, "clock skew reads as the start of the lock-on");
		h.assertTrue(fx.pieceProgress(EquipmentSlot.HEAD, 100L + IronManSuitFx.RELEASE_TICKS + 8, 0f) == 0f,
				"a released piece stays gone after its release");
		for (int bit = 0; bit < 4; bit++) {
			for (List<String> g : com.projecthero.mod.ironman.suit.IronManAssemblyPlan.groups(bit, false)) {
				for (String bone : g) {
					float d = com.projecthero.mod.ironman.suit.IronManAssemblyPlan.distance(bone);
					float tilt = Math.abs(com.projecthero.mod.ironman.suit.IronManAssemblyPlan.tilt(bone));
					float s0 = com.projecthero.mod.ironman.suit.IronManAssemblyPlan.startScale(bone);
					h.assertTrue(d >= 3f && d <= 8f && tilt >= 10f && tilt <= 35f && s0 >= 0.6f && s0 <= 0.8f,
							bone + " exploded view in range: " + d + " / " + tilt + " / " + s0);
				}
			}
		}
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void faceplateLiftEasesUpSettlesAndStaysRaised(GameTestHelper h) {
		float x = 0f;
		float max = 0f;
		float prev = 0f;
		for (int k = 1; k <= 1000; k++) {
			float a = com.projecthero.mod.ironman.IronManFaceplateLook.angle(k / 1000f);
			h.assertTrue(Math.abs(a - prev) < 2f, "the swing is continuous at " + k);
			max = Math.max(max, a);
			prev = a;
		}
		for (int i = 0; i < com.projecthero.mod.ironman.IronManFaceplateLook.LIFT_TICKS; i++) {
			x = com.projecthero.mod.ironman.IronManFaceplateLook.step(x, true);
		}
		h.assertTrue(x == 1f, "fully raised after LIFT_TICKS");
		h.assertTrue(com.projecthero.mod.ironman.IronManFaceplateLook.angle(1f) == com.projecthero.mod.ironman.IronManFaceplateLook.RAISED_DEG
				&& com.projecthero.mod.ironman.IronManFaceplateLook.RAISED_DEG == 90f, "v0.14.22: lifts 90 degrees and stays in view");
		h.assertTrue(max <= com.projecthero.mod.ironman.IronManFaceplateLook.RAISED_DEG + 1e-3f, "never swings past its stop");
		h.assertTrue(com.projecthero.mod.ironman.IronManFaceplateLook.angle(0.9f) < com.projecthero.mod.ironman.IronManFaceplateLook.RAISED_DEG - 1f,
				"a small mechanical settle after landing");
		h.assertTrue(com.projecthero.mod.ironman.IronManFaceplateLook.step(1f, true) == 1f, "stays raised while open");
		h.assertTrue(com.projecthero.mod.ironman.IronManFaceplateLook.faceOpen(1f)
				&& !com.projecthero.mod.ironman.IronManFaceplateLook.faceOpen(0f), "the face shows only while it is up");
		h.assertTrue(com.projecthero.mod.ironman.IronManFaceplateLook.HINGE[1] == 32.1f && com.projecthero.mod.ironman.IronManFaceplateLook.HINGE[2] == -4.92f,
				"the hinge is the faceplate cube's own top-front edge (stays attached)");
		for (int i = 0; i < com.projecthero.mod.ironman.IronManFaceplateLook.LIFT_TICKS; i++) {
			x = com.projecthero.mod.ironman.IronManFaceplateLook.step(x, false);
		}
		h.assertTrue(x == 0f && com.projecthero.mod.ironman.IronManFaceplateLook.angle(x) == 0f, "closing seals it again");
		h.succeed();
	}
}
