package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.gear.CelestialSet;
import com.projecthero.mod.gear.CelestialWarhammerItem;
import com.projecthero.mod.gear.Flashbang;
import com.projecthero.mod.gear.GadgetCooldowns;
import com.projecthero.mod.gear.GearItems;
import com.projecthero.mod.gear.GrappleHookEntity;
import com.projecthero.mod.gear.GrappleLineItem;
import com.projecthero.mod.gear.SmokeCloudEntity;
import com.projecthero.mod.loot.HeroRarity;
import com.projecthero.mod.loot.HeroVault;
import com.projecthero.mod.loot.LootItems;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 gear tiers I-IV: armour values per tier (on armour stands, which tick their equipment), the Celestial set
 * bonus, the Grapple Line reeling a player, the Smoke Bomb / Flashbang wiping a mob's target, the Warhammer slam's
 * radius, the Utility Belt's spare charge, the recipes and the vault gear tables. Mock players are not ticked, so the
 * grapple is stepped by ticking its hook by hand.
 */
public class GearTierGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper, double x, double z, float yaw, float pitch) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, 2.0, z));
		p.moveTo(at.x, at.y, at.z, yaw, pitch);
		p.setYHeadRot(yaw);
		return p;
	}

	private static void wear(net.minecraft.world.entity.LivingEntity e, Item head, Item chest, Item legs, Item feet) {
		e.setItemSlot(EquipmentSlot.HEAD, new ItemStack(head));
		e.setItemSlot(EquipmentSlot.CHEST, new ItemStack(chest));
		e.setItemSlot(EquipmentSlot.LEGS, new ItemStack(legs));
		e.setItemSlot(EquipmentSlot.FEET, new ItemStack(feet));
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 1.0e-4;
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void armourValuesPerTier(GameTestHelper helper) {
		ArmorStand alloy = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(1, 2, 1));
		ArmorStand vib = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(3, 2, 1));
		ArmorStand cel = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(5, 2, 1));
		wear(alloy, GearItems.HERO_ALLOY_HELMET, GearItems.HERO_ALLOY_CHESTPLATE, GearItems.HERO_ALLOY_LEGGINGS, GearItems.HERO_ALLOY_BOOTS);
		wear(vib, GearItems.VIBRANIUM_HELMET, GearItems.VIBRANIUM_CHESTPLATE, GearItems.VIBRANIUM_LEGGINGS, GearItems.VIBRANIUM_BOOTS);
		wear(cel, GearItems.CELESTIAL_HELMET, GearItems.CELESTIAL_CHESTPLATE, GearItems.CELESTIAL_LEGGINGS, GearItems.CELESTIAL_BOOTS);
		helper.runAfterDelay(4, () -> {
			helper.assertTrue(alloy.getArmorValue() == 15, "Hero Alloy 2/6/5/2 = 15 armour, was " + alloy.getArmorValue());
			helper.assertTrue(near(alloy.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 4.0), "Hero Alloy toughness 1 per piece");
			helper.assertTrue(near(alloy.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER), 1.0), "Hero Alloy doesn't cut falls");
			helper.assertTrue(vib.getArmorValue() == 20, "Vibranium 3/8/6/3 = 20 armour, was " + vib.getArmorValue());
			helper.assertTrue(near(vib.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 12.0), "Vibranium toughness 3 per piece");
			helper.assertTrue(near(vib.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.4), "Vibranium knockback 0.1 per piece");
			helper.assertTrue(near(vib.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER), 0.5), "a full Vibranium set halves fall damage");
			helper.assertTrue(cel.getArmorValue() == 20, "Celestial 3/8/6/3 = 20 armour, was " + cel.getArmorValue());
			helper.assertTrue(near(cel.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 16.0), "Celestial toughness 4 per piece");
			helper.assertTrue(near(cel.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.6), "Celestial knockback 0.15 per piece");
			helper.assertTrue(near(cel.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER), 0.5), "Celestial cuts falls like Vibranium");
			helper.assertTrue(new ItemStack(GearItems.HERO_ALLOY_CHESTPLATE).getMaxDamage() > new ItemStack(Items.IRON_CHESTPLATE).getMaxDamage()
					&& new ItemStack(GearItems.HERO_ALLOY_CHESTPLATE).getMaxDamage() < new ItemStack(Items.DIAMOND_CHESTPLATE).getMaxDamage(),
					"Hero Alloy durability sits between iron and diamond");
			helper.assertTrue(new ItemStack(GearItems.CELESTIAL_CHESTPLATE).getMaxDamage() > new ItemStack(Items.NETHERITE_CHESTPLATE).getMaxDamage(),
					"Celestial outlasts netherite");
			for (Item i : GearItems.all()) {
				helper.assertTrue(HeroRarity.of(new ItemStack(i)) != null, "every gear item has a rarity: " + i);
			}
			helper.assertTrue(HeroRarity.of(new ItemStack(GearItems.CELESTIAL_WARHAMMER)) == HeroRarity.LEGENDARY, "the Warhammer is Legendary");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void celestialSetBonusAppliesAndRemoves(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 2.5, 2.5, 0f, 0f);
		double base = p.getMaxHealth();
		wear(p, GearItems.CELESTIAL_HELMET, GearItems.CELESTIAL_CHESTPLATE, GearItems.CELESTIAL_LEGGINGS, GearItems.VIBRANIUM_BOOTS);
		helper.assertTrue(!CelestialSet.update(p), "three Celestial pieces are not a set");
		helper.assertTrue(near(p.getMaxHealth(), base), "no bonus hearts without the full set");
		helper.assertTrue(p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100)), "blindness lands without the full set");
		p.setItemSlot(EquipmentSlot.FEET, new ItemStack(GearItems.CELESTIAL_BOOTS));
		helper.assertTrue(CelestialSet.update(p), "four Celestial pieces are the set");
		helper.assertTrue(near(p.getMaxHealth(), base + CelestialSet.BONUS_HEALTH), "+2 hearts with the full set, was " + p.getMaxHealth());
		helper.assertTrue(!p.hasEffect(MobEffects.BLINDNESS), "completing the set clears blindness");
		helper.assertTrue(!p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100)), "blindness is refused while the set is worn");
		helper.assertTrue(!p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100)), "darkness is refused while the set is worn");
		helper.assertTrue(p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100)), "other effects still land");
		CelestialSet.update(p); // idempotent
		helper.assertTrue(near(p.getMaxHealth(), base + CelestialSet.BONUS_HEALTH), "the bonus is not stacked twice");
		p.setHealth(p.getMaxHealth());
		p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		helper.assertTrue(!CelestialSet.update(p), "taking the helmet off breaks the set");
		helper.assertTrue(near(p.getMaxHealth(), base), "the bonus hearts go");
		helper.assertTrue(p.getHealth() <= p.getMaxHealth(), "health is clamped to the new max");
		helper.assertTrue(p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100)), "darkness lands again without the set");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void grapplePullsThePlayer(GameTestHelper helper) {
		BlockPos target = new BlockPos(2, 5, 6);
		helper.setBlock(target, Blocks.STONE);
		Vec3 aim = helper.absoluteVec(new Vec3(2.5, 5.5, 6.0)); // the near face of the block
		ServerPlayer p = mock(helper, 2.5, 1.5, 0f, 0f);
		Vec3 d = aim.subtract(p.getEyePosition());
		float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
		p.moveTo(p.getX(), p.getY(), p.getZ(), yaw, pitch);
		ItemStack line = new ItemStack(GearItems.GRAPPLE_LINE);
		p.setItemInHand(InteractionHand.MAIN_HAND, line);
		GrappleLineItem item = (GrappleLineItem) GearItems.GRAPPLE_LINE;
		helper.assertTrue(item.aim(p) != null, "the stone block is in range");
		helper.assertTrue(item.fire(p, InteractionHand.MAIN_HAND), "the hook fires");
		List<GrappleHookEntity> hooks = helper.getLevel().getEntitiesOfClass(GrappleHookEntity.class, p.getBoundingBox().inflate(30), h -> h.isOwnedBy(p));
		helper.assertTrue(hooks.size() == 1, "one hook is out, found " + hooks.size());
		GrappleHookEntity hook = hooks.get(0);
		helper.assertTrue(hook.position().distanceTo(aim) < 0.6, "the hook sits on the block face");
		hook.tick();
		Vec3 v = p.getDeltaMovement();
		Vec3 want = hook.position().subtract(p.position().add(0, p.getBbHeight() * 0.5, 0)).normalize();
		helper.assertTrue(v.length() > 0.3, "the line reels the player in, speed " + v.length());
		helper.assertTrue(v.normalize().dot(want) > 0.95, "towards the hook");
		helper.assertTrue(v.y > 0, "upwards, to a block above");
		helper.assertTrue(line.getDamageValue() == 1, "a hook costs 1 durability");
		helper.assertTrue(p.getCooldowns().isOnCooldown(GearItems.GRAPPLE_LINE) && p.getCooldowns().isOnCooldown(GearItems.VIBRANIUM_GRAPPLE),
				"both grapples go on cooldown");
		// firing again replaces the old hook
		p.getCooldowns().removeCooldown(GearItems.GRAPPLE_LINE);
		helper.assertTrue(item.fire(p, InteractionHand.MAIN_HAND), "re-hooking works");
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(GrappleHookEntity.class, p.getBoundingBox().inflate(30), h -> h.isOwnedBy(p) && h.isAlive())
				.size() == 1, "re-hooking replaces the old hook");
		helper.assertTrue(item.isValidRepairItem(line, new ItemStack(LootItems.SALVAGED_TECH)), "repaired with Salvaged Tech");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void smokeAndFlashWipeTargets(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 1.5, 1.5, 0f, 0f);
		Husk mob = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(4, 2, 4));
		Husk boss = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(5, 2, 4));
		boss.getAttribute(Attributes.MAX_HEALTH).setBaseValue(300);
		mob.setTarget(p);
		boss.setTarget(p);
		SmokeCloudEntity.spawn(helper.getLevel(), mob.position(), p, false);
		helper.assertTrue(mob.getTarget() == null, "the smoke wipes the mob's target");
		helper.assertTrue(mob.hasEffect(MobEffects.BLINDNESS), "and blinds it");
		helper.assertTrue(boss.getTarget() == p, "a boss-sized mob keeps its target");
		helper.assertTrue(!p.hasEffect(MobEffects.BLINDNESS), "players are never smoked");
		// the cloud keeps working while it lives
		mob.setTarget(p);
		List<SmokeCloudEntity> clouds = helper.getLevel().getEntitiesOfClass(SmokeCloudEntity.class, mob.getBoundingBox().inflate(3));
		helper.assertTrue(clouds.size() == 1, "one cloud");
		clouds.get(0).affectMobs();
		helper.assertTrue(mob.getTarget() == null, "a mob that re-targets inside the cloud loses it again");

		Husk near = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(2, 2, 5));
		Husk far = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(6, 2, 6));
		near.setTarget(p);
		far.setTarget(p);
		Vec3 at = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
		int caught = Flashbang.detonate(helper.getLevel(), at, p, false);
		helper.assertTrue(caught >= 1, "the flash catches someone");
		helper.assertTrue(near.getTarget() == null && near.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && near.hasEffect(MobEffects.BLINDNESS),
				"a mob within 5 blocks is blinded, stunned and loses its target");
		helper.assertTrue(far.getTarget() == p && !far.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "a mob 7 blocks away is untouched");
		helper.assertTrue(!p.hasEffect(MobEffects.BLINDNESS), "the thrower is not flashed");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void warhammerSlamHitsTheRadius(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 1.5, 1.5, 0f, 0f); // facing south (+z): the slam lands 1.2 blocks ahead
		Husk inside = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(1, 2, 4));
		Husk outside = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(6, 2, 6));
		ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(2, 2, 3));
		float hpIn = inside.getHealth(), hpOut = outside.getHealth();
		int hit = CelestialWarhammerItem.slam(p, 1.0f);
		helper.assertTrue(hit >= 1, "the slam hits something");
		helper.assertTrue(inside.getHealth() < hpIn - 7f || !inside.isAlive(), "a mob 2 blocks out takes heavy damage, now " + inside.getHealth());
		helper.assertTrue(outside.getHealth() == hpOut, "a mob 6+ blocks out is untouched");
		helper.assertTrue(stand.isAlive(), "armour stands are left alone");
		helper.assertTrue(p.getHealth() == p.getMaxHealth(), "the slam never hurts the wielder");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void utilityBeltGivesASpareCharge(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 2.5, 2.5, 0f, 0f);
		helper.assertTrue(GadgetCooldowns.cooldownFor(p, 100) == 100, "no belt: full cooldown");
		helper.assertTrue(!GadgetCooldowns.afterUse(p, GadgetCooldowns.Kind.SMOKE, 100, GearItems.SMOKE_BOMB, GearItems.VIBRANIUM_SMOKE_BOMB),
				"no belt: no spare charge");
		helper.assertTrue(p.getCooldowns().isOnCooldown(GearItems.SMOKE_BOMB) && p.getCooldowns().isOnCooldown(GearItems.VIBRANIUM_SMOKE_BOMB),
				"no belt: both smoke bombs cool down");
		p.getInventory().setItem(20, new ItemStack(GearItems.UTILITY_BELT)); // a backpack row, not the hotbar
		helper.assertTrue(GadgetCooldowns.cooldownFor(p, 100) == 70, "belt: 30% shorter cooldowns");
		helper.assertTrue(GadgetCooldowns.afterUse(p, GadgetCooldowns.Kind.FLASH, 100, GearItems.FLASHBANG), "belt: the first flash uses the spare");
		helper.assertTrue(!p.getCooldowns().isOnCooldown(GearItems.FLASHBANG), "so no cooldown yet");
		helper.assertTrue(!GadgetCooldowns.afterUse(p, GadgetCooldowns.Kind.FLASH, 100, GearItems.FLASHBANG), "the second one doesn't");
		helper.assertTrue(p.getCooldowns().isOnCooldown(GearItems.FLASHBANG), "now it cools down");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void gearRecipesAndVaultTablesResolve(GameTestHelper helper) {
		var level = helper.getLevel();
		var rm = level.getRecipeManager();
		ItemStack a = new ItemStack(LootItems.HERO_ALLOY_INGOT);
		CraftingInput chest = CraftingInput.of(3, 3, List.of(a, ItemStack.EMPTY, a, a, a, a, a, a, a));
		helper.assertTrue(rm.getRecipeFor(RecipeType.CRAFTING, chest, level).map(r -> r.value().assemble(chest, level.registryAccess()))
				.filter(s -> s.is(GearItems.HERO_ALLOY_CHESTPLATE)).isPresent(), "8 Hero Alloy Ingots craft the chestplate");
		ItemStack t = new ItemStack(LootItems.SALVAGED_TECH);
		CraftingInput smoke = CraftingInput.of(3, 3, List.of(ItemStack.EMPTY, new ItemStack(Items.STRING), ItemStack.EMPTY,
				t, new ItemStack(Items.GUNPOWDER), t, ItemStack.EMPTY, new ItemStack(Items.CHARCOAL), ItemStack.EMPTY));
		helper.assertTrue(rm.getRecipeFor(RecipeType.CRAFTING, smoke, level).map(r -> r.value().assemble(smoke, level.registryAccess()))
				.filter(s -> s.is(GearItems.SMOKE_BOMB) && s.getCount() == 4).isPresent(), "smoke bombs craft in fours (charcoal works too)");

		smith(helper, ItemStack.EMPTY, GearItems.HERO_ALLOY_HELMET, com.projecthero.mod.ultron.contagion.item.ContagionItems.VIBRANIUM_INGOT,
				GearItems.VIBRANIUM_HELMET);
		smith(helper, ItemStack.EMPTY, GearItems.HERO_ALLOY_SWORD, com.projecthero.mod.ultron.contagion.item.ContagionItems.VIBRANIUM_INGOT,
				GearItems.VIBRANIUM_BLADE);
		smith(helper, ItemStack.EMPTY, GearItems.GRAPPLE_LINE, LootItems.VIBRANIUM_SHARD, GearItems.VIBRANIUM_GRAPPLE);
		smith(helper, ItemStack.EMPTY, GearItems.FLASHBANG, LootItems.VIBRANIUM_SHARD, GearItems.VIBRANIUM_FLASHBANG);
		smith(helper, new ItemStack(GearItems.CELESTIAL_UPGRADE_TEMPLATE), GearItems.VIBRANIUM_CHESTPLATE, LootItems.CELESTIAL_CORE,
				GearItems.CELESTIAL_CHESTPLATE);
		smith(helper, new ItemStack(GearItems.CELESTIAL_UPGRADE_TEMPLATE), GearItems.VIBRANIUM_BLADE, LootItems.CELESTIAL_CORE,
				GearItems.CELESTIAL_WARHAMMER);
		SmithingRecipeInput noTemplate = new SmithingRecipeInput(ItemStack.EMPTY, new ItemStack(GearItems.VIBRANIUM_CHESTPLATE),
				new ItemStack(LootItems.CELESTIAL_CORE));
		helper.assertTrue(rm.getRecipeFor(RecipeType.SMITHING, noTemplate, level).isEmpty(), "Celestial gear needs the template");

		// enchantable like their vanilla counterparts (the vanilla equipment tags)
		helper.assertTrue(new ItemStack(GearItems.CELESTIAL_HELMET).is(net.minecraft.tags.ItemTags.HEAD_ARMOR_ENCHANTABLE)
				&& new ItemStack(GearItems.HERO_ALLOY_BOOTS).is(net.minecraft.tags.ItemTags.FOOT_ARMOR_ENCHANTABLE)
				&& new ItemStack(GearItems.VIBRANIUM_CHESTPLATE).is(net.minecraft.tags.ItemTags.TRIMMABLE_ARMOR), "gear armour takes armour enchantments and trims");
		helper.assertTrue(new ItemStack(GearItems.CELESTIAL_WARHAMMER).is(net.minecraft.tags.ItemTags.SWORD_ENCHANTABLE)
				&& new ItemStack(GearItems.GRAPPLE_LINE).is(net.minecraft.tags.ItemTags.DURABILITY_ENCHANTABLE), "weapons take sword enchantments, grapples Unbreaking");

		// the vault gear tables: tier 4 hands out the template (35% a roll), tier 2 holds Hero Alloy
		helper.assertTrue(rolls(helper, 4, 80).stream().anyMatch(s -> s.is(GearItems.CELESTIAL_UPGRADE_TEMPLATE)), "tier 4 vaults drop the template");
		helper.assertTrue(rolls(helper, 2, 200).stream().anyMatch(s -> s.getItem() instanceof com.projecthero.mod.gear.GearArmorItem g && g.gearTier() == 2),
				"tier 2 vaults sometimes hold Hero Alloy armour");
		helper.assertTrue(rolls(helper, 1, 200).stream().noneMatch(s -> s.getItem() instanceof com.projecthero.mod.gear.GearArmorItem),
				"tier 1 vaults hold gadgets, never armour");
		helper.succeed();
	}

	private static void smith(GameTestHelper helper, ItemStack template, Item base, Item addition, Item result) {
		var level = helper.getLevel();
		SmithingRecipeInput in = new SmithingRecipeInput(template, new ItemStack(base), new ItemStack(addition));
		helper.assertTrue(level.getRecipeManager().getRecipeFor(RecipeType.SMITHING, in, level).map(r -> r.value().assemble(in, level.registryAccess()))
				.filter(s -> s.is(result)).isPresent(), "smithing " + base + " + " + addition + " -> " + result);
	}

	private static List<ItemStack> rolls(GameTestHelper helper, int tier, int n) {
		var level = helper.getLevel();
		ResourceKey<LootTable> key = ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
				HeroVault.lootTable(tier).location().withSuffix("_gear"));
		LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
		NonNullList<ItemStack> all = NonNullList.create();
		for (int i = 0; i < n; i++) {
			all.addAll(table.getRandomItems(new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, helper.absoluteVec(Vec3.ZERO))
					.create(LootContextParamSets.CHEST)));
		}
		return all;
	}
}
