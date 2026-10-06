package com.projecthero.mod.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.ability.IronManAbilities;
import com.projecthero.mod.ironman.ability.IronManAbilityManager;
import com.projecthero.mod.ironman.ability.IronManHeldBeam;
import com.projecthero.mod.ironman.ability.IronManMark3;
import com.projecthero.mod.ironman.ability.IronManMark6;
import com.projecthero.mod.ironman.ability.IronManMark7;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.entity.IronManDeliveryPodEntity;
import com.projecthero.mod.ironman.entity.IronManMissileEntity;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuitCall;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.network.IronManSuitListPayload;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.29 (agent C): the Mark 7's orbital drop (the pod starts high above, homes onto an airborne owner and clamps the
 * suit on; a pack call while airborne comes by pod; a pod that can't reach hands the suit over to the ordinary staged
 * suit-up). v0.15.4: the old Mark 6 surge / Mark 7 wheel-on-X kit tests are replaced by IronManMk67V0154GameTests.
 */
public class IronManV01429Mk67GameTests implements FabricGameTest {
	private static final String M6 = IronManMark6.SUIT_ID;
	private static final String M7 = IronManMark7.SUIT_ID;
	private static final ArmorItem.Type[] TYPES = {
			ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };
	private static JsonObject lang;

	private static String t(String key) {
		if (lang == null) {
			try (InputStream in = IronManV01429Mk67GameTests.class.getResourceAsStream("/assets/projecthero/lang/en_us.json")) {
				lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			} catch (Exception e) {
				throw new IllegalStateException("cannot read en_us.json", e);
			}
		}
		if (!lang.has(key)) {
			throw new IllegalStateException("missing lang key " + key);
		}
		return lang.get(key).getAsString();
	}

	private static ServerPlayer bare(GameTestHelper h) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		StarkGlassesV0151GameTests.wearGlasses(p); // v0.15.1: calling a suit needs the Stark Glasses
		BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		p.setYRot(0f); // facing +Z
		p.setXRot(0f);
		p.setYHeadRot(0f);
		return p;
	}

	private static ServerPlayer suited(GameTestHelper h, String suitId) {
		ServerPlayer p = bare(h);
		for (ArmorItem.Type type : TYPES) {
			p.setItemSlot(IronManSuitUpManager.slotFor(type), new ItemStack(IronManItems.armor(suitId, type)));
		}
		IronManEnergy.setEnergy(p, suitId, 5000f);
		IronManEnergy.setIntegrity(p, suitId, 500f);
		return p;
	}

	private static <T extends Mob> T mob(GameTestHelper h, EntityType<T> type, ServerPlayer p, double dx, double dz) {
		T m = type.create(h.getLevel());
		m.moveTo(p.getX() + dx, p.getY(), p.getZ() + dz, 0f, 0f);
		m.setNoAi(true);
		h.getLevel().addFreshEntity(m);
		return m;
	}

	private static void press(ServerPlayer p, AbilitySlot slot) {
		IronManAbilityManager.handle(p, slot, true);
		IronManAbilityManager.handle(p, slot, false);
	}

	private static boolean wearing(ServerPlayer p, String suitId) {
		for (ArmorItem.Type t : TYPES) {
			if (!(p.getItemBySlot(IronManSuitUpManager.slotFor(t)).getItem() instanceof IronManArmorItem a) || !a.suitId().equals(suitId)) {
				return false;
			}
		}
		return true;
	}

	private static java.util.function.Predicate<IronManDeliveryPodEntity> podsOf(ServerPlayer p) {
		return e -> p.getUUID().equals(e.ownerId());
	}

	private static AABB sky(GameTestHelper h) {
		return new AABB(h.absolutePos(BlockPos.ZERO)).inflate(40).expandTowards(0, 100, 0);
	}

	// ------------------------------------------------------------------ the orbital drop

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void orbitalDropCatchesAnAirbornePlayer(GameTestHelper h) {
		ServerPlayer p = bare(h);
		p.setOnGround(false); // falling
		h.setBlock(new BlockPos(3, 1, 3), IronManBlocks.IRON_MAN_SUIT_PLATFORM);
		IronManSuitPlatformBlockEntity be = (IronManSuitPlatformBlockEntity) h.getBlockEntity(new BlockPos(3, 1, 3));
		be.bindTo(p.getUUID());
		for (ArmorItem.Type t : TYPES) {
			be.store(new ItemStack(IronManItems.armor(M7, t)));
		}
		// a loaded platform nearby: the pod streaks straight off it, homes onto the falling player and catches them
		IronManSuitCall.execute(p, M7, IronManSuitListPayload.SOURCE_PLATFORM);
		List<IronManDeliveryPodEntity> pods = h.getLevel().getEntitiesOfClass(IronManDeliveryPodEntity.class, sky(h), podsOf(p));
		h.assertTrue(pods.size() == 1 && pods.get(0).cargo().size() == 4, "one pod carrying the whole suit");
		IronManDeliveryPodEntity pod = pods.get(0);
		boolean[] caught = { false };
		h.onEachTick(() -> caught[0] |= pod.catching());
		h.succeedWhen(() -> {
			h.assertTrue(wearing(p, M7), "the pod clamps the full Mark 7 onto the airborne player");
			h.assertTrue(caught[0], "it caught them mid-air (no landing)");
			h.assertTrue(h.getLevel().getEntitiesOfClass(IronManDeliveryPodEntity.class, sky(h), podsOf(p)).isEmpty(), "and flies off");
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void orbitalDropFromThePackWhileAirborne(GameTestHelper h) {
		ServerPlayer p = bare(h);
		for (ArmorItem.Type t : TYPES) {
			p.getInventory().add(new ItemStack(IronManItems.armor(M7, t)));
		}
		p.setOnGround(true);
		h.assertFalse(IronManSuitCall.orbitalDrop(p, IronManSuits.MARK_VII), "on the ground the pack suit builds normally");
		h.assertFalse(IronManSuitCall.orbitalDrop(p, IronManSuits.MARK_III), "only the pod suit drops from orbit");
		// genuinely airborne: hovering 6 blocks up with no gravity, so a physics tick can't land the player mid-descent
		// (landing would switch the pod to its slower ground delivery and run past the timeout)
		p.setNoGravity(true);
		p.teleportTo(p.getX(), p.getY() + 6.0, p.getZ());
		p.setOnGround(false);
		h.assertTrue(IronManSuitCall.autoEquipInventorySuit(p), "C while airborne calls the Mark 7");
		List<IronManDeliveryPodEntity> pods = h.getLevel().getEntitiesOfClass(IronManDeliveryPodEntity.class, sky(h), podsOf(p));
		h.assertTrue(pods.size() == 1 && pods.get(0).cargo().size() == 4, "...by pod, with the four pieces out of the pack");
		h.assertTrue(pods.get(0).getY() >= p.getY() + IronManDeliveryPodEntity.COVERED_HEIGHT - 1.0,
				"the pod starts high above the player (y " + pods.get(0).getY() + " vs " + p.getY() + ")");
		h.succeedWhen(() -> h.assertTrue(wearing(p, M7), "and the suit ends up worn"));
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void unreachablePodFallsBackToTheStagedSuitUp(GameTestHelper h) {
		ServerPlayer p = bare(h);
		p.setOnGround(true);
		List<ItemStack> pieces = new ArrayList<>();
		for (ArmorItem.Type t : TYPES) {
			pieces.add(new ItemStack(IronManItems.armor(M7, t)));
		}
		IronManDeliveryPodEntity pod = IronManDeliveryPodEntity.spawn(h.getLevel(), p, pieces, null);
		Vec3 start = pod.position();
		h.assertTrue(start.y > p.getY() + 20.0, "an orbital start high above");
		pod.giveUp(p);
		h.assertTrue(pod.isRemoved() && pod.cargo().isEmpty(), "the pod hands everything over and leaves");
		h.assertTrue(IronManSuitUpManager.inTransition(p), "the ordinary staged suit-up starts instead");
		for (int i = 0; i < 400 && IronManSuitUpManager.inTransition(p); i++) {
			IronManSuitUpManager.tick(p);
		}
		h.assertTrue(wearing(p, M7), "and finishes with the whole suit on -- nothing stranded");
		h.assertTrue(IronManArmor.wearingFullSuit(p, M7), "full suit");
		int strays = 0;
		for (ItemStack s : p.getInventory().items) {
			strays += s.getItem() instanceof IronManArmorItem ? 1 : 0;
		}
		h.assertTrue(strays == 0, "no duplicate pieces left in the pack");
		h.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).getCount() == 1, "one chestplate");
		h.succeed();
	}
}
