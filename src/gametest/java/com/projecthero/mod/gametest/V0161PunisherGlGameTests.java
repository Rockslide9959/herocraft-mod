package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.GreenLanternSniper;
import com.projecthero.mod.greenlantern.construct.ConstructType;
import com.projecthero.mod.greenlantern.construct.GreenLanternConstructs;
import com.projecthero.mod.greenlantern.data.GreenLanternState;
import com.projecthero.mod.punisher.item.PunisherItems;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: Punisher armour takes the normal armour enchantments (table and anvil), and the Green Lantern Sniper Rifle
 * construct -- forms from the wheel, costs Ring Charge per shot, hits at long range, refuses without charge.
 */
public class V0161PunisherGlGameTests implements FabricGameTest {
	// ---------------------------------------------------------------- Punisher armour enchanting

	private static Holder<Enchantment> ench(GameTestHelper h, ResourceKey<Enchantment> key) {
		return h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void punisherArmourTakesArmourEnchantments(GameTestHelper helper) {
		ItemStack vest = new ItemStack(PunisherItems.TACTICAL_VEST);
		ItemStack legs = new ItemStack(PunisherItems.TACTICAL_LEGGINGS);
		ItemStack boots = new ItemStack(PunisherItems.TACTICAL_BOOTS);
		for (ItemStack s : new ItemStack[] { vest, legs, boots }) {
			String name = s.getHoverName().getString();
			helper.assertTrue(s.isEnchantable(), name + " should be enchantable");
			helper.assertTrue(s.getItem().getEnchantmentValue() > 0, name + " needs an enchantment value");
			helper.assertTrue(s.is(ItemTags.ARMOR_ENCHANTABLE), name + " should be in enchantable/armor");
			helper.assertTrue(s.is(ItemTags.DURABILITY_ENCHANTABLE), name + " should be in enchantable/durability");
			for (ResourceKey<Enchantment> key : List.of(Enchantments.PROTECTION, Enchantments.PROJECTILE_PROTECTION,
					Enchantments.BLAST_PROTECTION, Enchantments.FIRE_PROTECTION, Enchantments.UNBREAKING, Enchantments.MENDING,
					Enchantments.THORNS)) {
				helper.assertTrue(ench(helper, key).value().canEnchant(s), name + " should accept " + key.location());
			}
		}
		helper.assertTrue(vest.is(ItemTags.CHEST_ARMOR_ENCHANTABLE), "the vest is chest armour");
		helper.assertTrue(legs.is(ItemTags.LEG_ARMOR_ENCHANTABLE), "the leggings are leg armour");
		helper.assertTrue(boots.is(ItemTags.FOOT_ARMOR_ENCHANTABLE), "the boots are foot armour");
		helper.assertTrue(ench(helper, Enchantments.FEATHER_FALLING).value().canEnchant(boots), "boots take Feather Falling");
		helper.assertTrue(ench(helper, Enchantments.SWIFT_SNEAK).value().canEnchant(legs), "leggings take Swift Sneak");
		helper.assertFalse(ench(helper, Enchantments.FEATHER_FALLING).value().canEnchant(vest), "a vest is not boots");
		helper.assertFalse(ench(helper, Enchantments.SHARPNESS).value().canEnchant(vest), "no weapon enchantments on armour");
		// the enchanting table offers the vest something at level 30
		List<EnchantmentInstance> offers = EnchantmentHelper.getAvailableEnchantmentResults(30, vest,
				helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(EnchantmentTags.IN_ENCHANTING_TABLE)
						.stream());
		helper.assertTrue(!offers.isEmpty(), "the enchanting table should have something to offer the vest");
		helper.assertTrue(offers.stream().anyMatch(o -> o.enchantment.is(Enchantments.PROTECTION)),
				"Protection should be among the table's offers for the vest");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Green Lantern Sniper Rifle

	/** A bonded Lantern at the test origin, looking straight up (the long shot goes up the test's own chunk column). */
	private static ServerPlayer lantern(GameTestHelper helper, float charge) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
		player.moveTo(at.x, at.y, at.z, 0f, -90f);
		player.setYHeadRot(0f);
		GreenLantern.bond(player);
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = charge;
		GreenLantern.save(player, s);
		return player;
	}

	private static float charge(ServerPlayer player) {
		return GreenLantern.state(player).ringCharge;
	}

	private static void setCharge(ServerPlayer player, float charge) {
		GreenLanternState s = GreenLantern.state(player).copy();
		s.ringCharge = charge;
		GreenLantern.save(player, s);
	}

	/** A still, floating iron golem (100 HP, no armour) {@code up} blocks straight above the player. */
	private static IronGolem target(GameTestHelper helper, ServerPlayer player, double up) {
		IronGolem g = EntityType.IRON_GOLEM.create(helper.getLevel());
		g.moveTo(player.getX(), player.getEyeY() + up, player.getZ(), 0f, 0f);
		g.setNoAi(true);
		g.setNoGravity(true);
		helper.getLevel().addFreshEntity(g);
		return g;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sniperFormsAndPutsAwayFromTheWheel(GameTestHelper helper) {
		ServerPlayer player = lantern(helper, 2000f);
		helper.assertTrue(ConstructType.SNIPER_RIFLE.category() == ConstructType.Category.ATTACK, "the rifle is an Attack construct");
		GreenLanternConstructs.deploy(player, ConstructType.SNIPER_RIFLE);
		helper.assertTrue(GreenLanternSniper.isOut(player), "C forms the rifle");
		helper.assertTrue(charge(player) == 2000f - GreenLanternConfig.SNIPER_FORM_COST, "forming it costs its form cost");
		int mask = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_HAND_CONSTRUCTS, 0);
		helper.assertTrue((mask & GreenLanternConstructs.HAND_SNIPER) != 0, "every client is told to draw the rifle");
		GreenLanternConstructs.deploy(player, ConstructType.SNIPER_RIFLE);
		helper.assertFalse(GreenLanternSniper.isOut(player), "C again puts it away");
		helper.assertTrue(charge(player) == 2000f - GreenLanternConfig.SNIPER_FORM_COST, "putting it away is free");
		mask = player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_HAND_CONSTRUCTS, 0);
		helper.assertTrue((mask & GreenLanternConstructs.HAND_SNIPER) == 0, "the rifle is no longer drawn");
		helper.assertFalse(GreenLanternSniper.fire(player, GreenLanternConfig.SNIPER_FULL_CHARGE_TICKS),
				"no rifle, no shot");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, skyAccess = true) // the shot goes straight up, out of the test's cage
	public void sniperShotCostsChargeAndHitsAtLongRange(GameTestHelper helper) {
		ServerPlayer player = lantern(helper, 2000f);
		IronGolem golem = target(helper, player, 100.0);
		GreenLanternConstructs.deploy(player, ConstructType.SNIPER_RIFLE);
		float before = charge(player);
		float hp = golem.getHealth();
		helper.assertTrue(GreenLanternSniper.fire(player, GreenLanternConfig.SNIPER_FULL_CHARGE_TICKS), "a full-charge shot fires");
		helper.assertTrue(Math.abs(before - charge(player) - GreenLanternConfig.SNIPER_SHOT_COST) < 0.01f,
				"a shot costs " + GreenLanternConfig.SNIPER_SHOT_COST + " charge, spent " + (before - charge(player)));
		float dealt = hp - golem.getHealth();
		helper.assertTrue(dealt >= GreenLanternConfig.SNIPER_DAMAGE * 0.9f,
				"a full-charge bolt should land ~" + GreenLanternConfig.SNIPER_DAMAGE + " at 100 blocks, dealt " + dealt);
		// straight away again: on cooldown, nothing spent
		float mid = charge(player);
		helper.assertFalse(GreenLanternSniper.fire(player, GreenLanternConfig.SNIPER_FULL_CHARGE_TICKS), "1 s between shots");
		helper.assertTrue(charge(player) == mid, "a refused shot costs nothing");
		helper.assertTrue(GreenLantern.cooldownRemaining(player, GreenLanternSniper.COOLDOWN_ID) > 0, "the cooldown is synced for the HUD");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, skyAccess = true)
	public void aTappedShotHitsForLess(GameTestHelper helper) {
		ServerPlayer player = lantern(helper, 2000f);
		IronGolem golem = target(helper, player, 40.0);
		GreenLanternConstructs.deploy(player, ConstructType.SNIPER_RIFLE);
		float hp = golem.getHealth();
		helper.assertTrue(GreenLanternSniper.fire(player, 0), "a tapped shot still fires");
		float dealt = hp - golem.getHealth();
		float expected = GreenLanternConfig.SNIPER_DAMAGE * GreenLanternConfig.SNIPER_MIN_CHARGE_FRACTION;
		helper.assertTrue(dealt > 0f && dealt < GreenLanternConfig.SNIPER_DAMAGE * 0.6f,
				"an uncharged shot should do about " + expected + ", dealt " + dealt);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, skyAccess = true)
	public void sniperRefusedWithoutCharge(GameTestHelper helper) {
		ServerPlayer player = lantern(helper, 2000f);
		IronGolem golem = target(helper, player, 30.0);
		GreenLanternConstructs.deploy(player, ConstructType.SNIPER_RIFLE);
		helper.assertTrue(GreenLanternSniper.isOut(player), "the rifle forms");
		// below the shot cost (plus the emergency reserve nothing may touch)
		float low = GreenLanternConfig.EMERGENCY_RESERVE + GreenLanternConfig.SNIPER_SHOT_COST - 1f;
		setCharge(player, low);
		float hp = golem.getHealth();
		helper.assertFalse(GreenLanternSniper.fire(player, GreenLanternConfig.SNIPER_FULL_CHARGE_TICKS),
				"without the charge for it the rifle must not fire");
		helper.assertTrue(golem.getHealth() == hp, "no damage from a refused shot");
		helper.assertTrue(charge(player) == low, "nothing spent on a refused shot");
		helper.assertTrue(GreenLantern.cooldownRemaining(player, GreenLanternSniper.COOLDOWN_ID) == 0,
				"a refused shot starts no cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, skyAccess = true, timeoutTicks = 100)
	public void chargeAndReleaseFiresThroughTheInputPath(GameTestHelper helper) {
		ServerPlayer player = lantern(helper, 2000f);
		IronGolem golem = target(helper, player, 60.0);
		GreenLanternConstructs.deploy(player, ConstructType.SNIPER_RIFLE);
		GreenLanternSniper.chargeStart(player);
		helper.assertTrue(GreenLanternSniper.isCharging(player), "attack down starts the charge");
		float hp = golem.getHealth();
		helper.runAfterDelay(GreenLanternConfig.SNIPER_FULL_CHARGE_TICKS + 2, () -> {
			GreenLanternSniper.release(player);
			helper.assertFalse(GreenLanternSniper.isCharging(player), "letting go fires");
			float dealt = hp - golem.getHealth();
			helper.assertTrue(dealt >= GreenLanternConfig.SNIPER_DAMAGE * 0.9f, "a held charge fires at full power, dealt " + dealt);
			// N dismisses it like any construct
			GreenLanternConstructs.dismissAll(player.getUUID());
			helper.assertFalse(GreenLanternSniper.isOut(player), "N puts the rifle away");
			helper.succeed();
		});
	}
}
