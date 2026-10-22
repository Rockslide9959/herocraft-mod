package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.ProtocolPhoenix;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.ability.IronManAbilities;
import com.projecthero.mod.ironman.ability.IronManAbilityManager;
import com.projecthero.mod.ironman.ability.IronManHeldBeam;
import com.projecthero.mod.ironman.ability.IronManMark6;
import com.projecthero.mod.ironman.ability.IronManMark7;
import com.projecthero.mod.ironman.entity.IronManDeliveryPodEntity;
import com.projecthero.mod.ironman.fabricator.FabricationRecipe;
import com.projecthero.mod.ironman.fabricator.FabricatorRecipes;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.gear.StarkGear;
import com.projecthero.mod.ironman.item.BlueprintItem;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuitCall;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.ironman.suit.SummonType;
import com.projecthero.mod.ironman.suit.SuitUpType;
import com.projecthero.mod.network.IronManSuitListPayload;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.9 (explicit user spec): the Mark 8 -- 5000 energy / 3000 integrity on the Mark 7's rules, +8 melee, water
 * breathing, auto-feed, the Mark 7's kit; the Blank Blueprint step after the Mark 7 and pricier Fabricator pieces; called
 * by the Stark Glasses (flies in from its platform, no pod); Protocol Phoenix prefers it over a Mark 7.
 */
public class IronManMark8V0159GameTests implements FabricGameTest {
	private static final String M7 = IronManMark7.SUIT_ID;
	private static final String M8 = IronManMark7.MARK_8_ID;
	private static final ArmorItem.Type[] TYPES = {
			ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };

	private static ServerPlayer tony(GameTestHelper h, BlockPos rel) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = Vec3.atBottomCenterOf(h.absolutePos(rel));
		p.setPos(at.x, at.y, at.z);
		TonyStark.grant(p);
		p.getInventory().clearContent();
		return p;
	}

	private static void packSuit(ServerPlayer p, String suitId) {
		for (ArmorItem.Type t : TYPES) {
			p.getInventory().add(new ItemStack(IronManItems.armor(suitId, t)));
		}
	}

	private static IronManSuitPlatformBlockEntity platformWith(GameTestHelper h, ServerPlayer owner, BlockPos rel, String suitId) {
		h.setBlock(rel, IronManBlocks.IRON_MAN_SUIT_PLATFORM);
		IronManSuitPlatformBlockEntity be = (IronManSuitPlatformBlockEntity) h.getBlockEntity(rel);
		be.bindTo(owner.getUUID());
		for (ArmorItem.Type t : TYPES) {
			be.store(new ItemStack(IronManItems.armor(suitId, t)));
		}
		return be;
	}

	private static void leave(GameTestHelper h, ServerPlayer p) {
		if (!p.isRemoved()) {
			h.getLevel().getServer().getPlayerList().remove(p);
		}
	}

	private static int count(FabricationRecipe r, Item item) {
		for (FabricationRecipe.Input in : r.inputs()) {
			if (in.item() == item) {
				return in.count();
			}
		}
		return 0;
	}

	// ------------------------------------------------------------------ stats

	@GameTest(template = EMPTY_STRUCTURE)
	public void markEightStatsFollowTheSpec(GameTestHelper h) {
		IronManSuit m8 = IronManSuits.MARK_8;
		IronManSuit m7 = IronManSuits.MARK_VII;
		h.assertTrue(m8 == IronManSuits.byId(M8) && m8 == IronManSuits.byMark(8), "registered as mark_8 / Mark 8");
		// v0.16.1 comic rework: the HULKBUSTER
		h.assertTrue(m8.maxIntegrity() == 4500f, "4500 integrity, got " + m8.maxIntegrity());
		h.assertTrue(m8.strengthBonus() == 16f, "+16 melee, got " + m8.strengthBonus());
		h.assertTrue(Math.abs(m8.absorbShare() - 1.0f) < 1e-4f, "takes every hit itself (v0.16.7: HulkbusterShield, was 60%)");
		h.assertTrue(m8.selfRepairPerSecond() == 0f && m8.wornIntegrityRegenPerSecond() == 0f, "never self-repairs");
		h.assertTrue(m8.hulkbuster() && m8.scale() == 1.45f, "the Hulkbuster, scaled x1.45");
		h.assertTrue(m8.waterBreathing(), "breathes underwater");
		h.assertTrue(m8.resistanceAmplifier() < 0 && m7.resistanceAmplifier() < 0, "no Resistance on the Mark 7 / 8");
		// slots 1-5 are the comic kit (as on every mark); C (slot 6) is the Mark 8's Sentry Mode
		String[] comic = { com.projecthero.mod.ironman.ability.IronManComicKit.SLOT_R, com.projecthero.mod.ironman.ability.IronManComicKit.SLOT_G,
				com.projecthero.mod.ironman.ability.IronManComicKit.SLOT_X, com.projecthero.mod.ironman.ability.IronManComicKit.SLOT_Z,
				com.projecthero.mod.ironman.ability.IronManComicKit.SLOT_V };
		for (int slot = 1; slot <= 5; slot++) {
			h.assertTrue(comic[slot - 1].equals(m8.abilityInSlot(slot)), "slot " + slot + " is " + comic[slot - 1] + ", got " + m8.abilityInSlot(slot));
		}
		// v0.16.7 (user): C is a kit key -- tap = Hydraulic Brace, hold 1 s = Sentry Mode
		h.assertTrue(com.projecthero.mod.ironman.ability.IronManComicKit.SLOT_C.equals(m8.abilityInSlot(6)),
				"C is the kit's C key (Brace / hold: Sentry Mode)");
		h.assertTrue(m8.summonType() == SummonType.FLYING_SET && m8.suitUpType() == SuitUpType.MECHANICAL_REMOTE,
				"called pieces fly in -- no pod");
		h.assertTrue(IronManItems.armor(M8, ArmorItem.Type.CHESTPLATE) != null
				&& "iron_man_mark_8_chestplate".equals(net.minecraft.core.registries.BuiltInRegistries.ITEM
						.getKey(IronManItems.armor(M8, ArmorItem.Type.CHESTPLATE)).getPath()), "armour item ids use the number");
		h.succeed();
	}

	// ------------------------------------------------------------------ progression + fabrication

	@GameTest(template = EMPTY_STRUCTURE)
	public void markEightBlueprintFollowsTheMarkSeven(GameTestHelper h) {
		h.assertTrue(IronManItems.MARK_ORDER.get(IronManItems.MARK_ORDER.size() - 1).equals(M8), "the Mark 8 ends the track");
		h.assertTrue("mark_9".equals(IronManItems.prerequisiteSuit(M8)), "its prerequisite is the Mk IX Inferno (v0.16.7 Era II order)");
		h.assertTrue(IronManItems.blueprintFor(M8) == IronManItems.MARK_8_BLUEPRINT, "a Blank Blueprint stamps into the Mark 8 Blueprint");
		h.assertTrue(IronManItems.MARK_8_BLUEPRINT instanceof BlueprintItem bp && M8.equals(bp.suitId()), "which names the Mark 8");
		h.assertTrue(IronManSuits.MARK_8.requiredBlueprint() == IronManItems.MARK_8_BLUEPRINT, "the suit knows its blueprint");

		ServerPlayer p = tony(h, new BlockPos(2, 1, 2));
		String prereq = IronManItems.prerequisiteSuit(M8);
		h.assertFalse(TonyStark.hasFabricatedFullSuit(p, prereq), "locked until the Mk IX is built");
		TonyStark.markBuilt(p, IronManSuits.MARK_9);
		h.assertTrue(TonyStark.hasFabricatedFullSuit(p, prereq), "unlocked once the whole Mk IX is built");
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void markEightPiecesCostMoreThanTheMarkSeven(GameTestHelper h) {
		h.assertTrue(FabricatorRecipes.hasArmorRecipes(M8), "the Mark 8 is fabricated");
		h.assertTrue(FabricatorRecipes.ARMOR_BUILD_ORDER.get(FabricatorRecipes.ARMOR_BUILD_ORDER.size() - 1).equals(M8),
				"last in the build order");
		for (ArmorItem.Type t : TYPES) {
			FabricationRecipe r8 = FabricatorRecipes.byId("iron_man_" + M8 + "_" + t.getName());
			FabricationRecipe r7 = FabricatorRecipes.byId("iron_man_" + M7 + "_" + t.getName());
			h.assertTrue(r8 != null && r8.result().is(IronManItems.armor(M8, t)), "a " + t.getName() + " recipe making the piece");
			int plates8 = count(r8, IronManItems.TITANIUM_GOLD_PLATE), plates7 = count(r7, IronManItems.TITANIUM_GOLD_PLATE);
			h.assertTrue(plates8 > plates7, t.getName() + ": more plates than the Mark 7 (" + plates8 + " vs " + plates7 + ")");
			h.assertTrue(count(r8, IronManItems.HYDRAULIC_FRAME) >= 1, t.getName() + ": a Hydraulic Frame in every piece (v0.16.1)");
			h.assertTrue(r8.timeTicks() >= r7.timeTicks(), t.getName() + ": takes at least as long");
		}
		h.succeed();
	}

	// ------------------------------------------------------------------ calling

	@GameTest(template = EMPTY_STRUCTURE)
	public void starkGlassesCallTheMarkEightOffItsPlatform(GameTestHelper h) {
		ServerPlayer p = tony(h, new BlockPos(1, 1, 1));
		try {
			IronManSuitPlatformBlockEntity be = platformWith(h, p, new BlockPos(5, 1, 5), M8);
			StarkGear.equip(p, new ItemStack(IronManItems.STARK_GLASSES)); // real glasses (no TEST_ANY_MARK tag)
			h.assertTrue(StarkGear.canCall(p, M8), "the glasses call a Mark 8");
			h.assertFalse(StarkGear.canCall(p, M7), "but still not the Mark 7");
			IronManSuitCall.execute(p, M8, IronManSuitListPayload.SOURCE_PLATFORM);
			h.assertTrue(be.isEmptyPlatform(), "the Mark 8 leaves its platform");
			// v0.15.15: it flies in whole, as a Sentry (see IronManSentryV01515GameTests)
			var inbound = h.getLevel().getEntities(com.projecthero.mod.ironman.entity.IronManEntityTypes.SENTRY,
					p.getBoundingBox().inflate(32), s -> p.getUUID().equals(s.ownerId()));
			h.assertTrue(inbound.size() == 1, "and is inbound as one suit");
			// v0.16.1: the Hulkbuster always arrives as an orbital drop -- out of the sky, not off the platform
			var s = inbound.get(0);
			h.assertTrue(s.getY() > p.getY() + 3.0, "it starts up in the sky (y " + s.getY() + " vs player " + p.getY() + ")");
			h.assertTrue(s.position().distanceTo(Vec3.atCenterOf(h.absolutePos(new BlockPos(5, 1, 5)))) > 3.0,
					"not on its platform");
			inbound.forEach(net.minecraft.world.entity.Entity::discard);
			h.assertTrue(h.getLevel().getEntitiesOfClass(IronManDeliveryPodEntity.class, p.getBoundingBox().inflate(64),
					pod -> p.getUUID().equals(pod.ownerId())).isEmpty(), // v0.15.11: only OUR pod (a neighbouring Mark 7 test's pod can land nearby)
					"flying in piece by piece -- no delivery pod");
		} finally {
			leave(h, p);
		}
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void braceletsNeverCallTheMarkEight(GameTestHelper h) {
		ServerPlayer p = tony(h, new BlockPos(1, 1, 1));
		h.assertFalse(StarkGear.canCall(p, M8), "no gear: no call");
		StarkGear.equip(p, new ItemStack(IronManItems.COLANTOTTE_BRACELETS));
		h.assertTrue(StarkGear.canCall(p, M7), "the bracelets call the Mark 7");
		h.assertFalse(StarkGear.canCall(p, M8), "never the Mark 8");
		leave(h, p);
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void protocolPhoenixPrefersTheMarkEight(GameTestHelper h) {
		ServerPlayer p = tony(h, new BlockPos(2, 1, 2));
		try {
			h.assertTrue(StarkGear.phoenixEligible(M8), "the Mark 8 is a Phoenix suit (Mark 7+)");
			packSuit(p, M7);
			packSuit(p, M8);
			StarkGear.equip(p, new ItemStack(IronManItems.STARK_GLASSES));
			h.assertTrue(ProtocolPhoenix.tryActivate(p, p.damageSources().generic()), "glasses + a Mark 8: Phoenix takes over");
			h.assertTrue(M8.equals(TonyStark.state(p).phoenixSuitId),
					"and recalls the Mark 8 over the Mark 7, got " + TonyStark.state(p).phoenixSuitId);
		} finally {
			ProtocolPhoenix.clearEmergency(p);
			TonyStark.setPhoenixReadyAt(p, 0L);
			leave(h, p);
		}
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void markEightHasItsLangAndAssets(GameTestHelper h) {
		for (String path : new String[] { "/assets/projecthero/geo/mark_8.geo.json", "/assets/projecthero/textures/armor/mark_8.png",
				"/assets/projecthero/textures/armor/mark_8_glowmask.png", "/assets/projecthero/textures/item/mark_8_blueprint.png",
				"/assets/projecthero/models/item/mark_8_blueprint.json",
				"/assets/projecthero/textures/item/iron_man_mark_8_helmet.png", "/assets/projecthero/models/item/iron_man_mark_8_boots.json" }) {
			h.assertTrue(IronManMark8V0159GameTests.class.getResource(path) != null, "missing " + path);
		}
		h.assertTrue(IronManAbilities.SUIT_TOGGLE != null, "sanity");
		h.succeed();
	}

	/**
	 * The helmet's base layer (head faces of the skin layout: top / bottom / sides / front / back) must be fully opaque --
	 * a transparent texel there lets the wearer's own head show through the closed helmet (the user's skin had 18 such
	 * texels on the top front edge, the side front edges and the jaw corners, filled in after the in-client check).
	 */
	@GameTest(template = EMPTY_STRUCTURE)
	public void markEightHelmetBaseLayerIsOpaque(GameTestHelper h) {
		try (java.io.InputStream in = IronManMark8V0159GameTests.class.getResourceAsStream("/assets/projecthero/textures/armor/mark_8.png")) {
			h.assertTrue(in != null, "missing mark_8.png");
			java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(in);
			int[][] faces = { { 8, 0 }, { 16, 0 }, { 0, 8 }, { 8, 8 }, { 16, 8 }, { 24, 8 } };
			for (int[] f : faces) {
				for (int y = 0; y < 8; y++) {
					for (int x = 0; x < 8; x++) {
						int a = img.getRGB(f[0] + x, f[1] + y) >>> 24;
						h.assertTrue(a == 255, "see-through helmet texel at (" + (f[0] + x) + "," + (f[1] + y) + ") alpha " + a);
					}
				}
			}
		} catch (java.io.IOException e) {
			h.fail("could not read mark_8.png: " + e);
		}
		h.succeed();
	}
}
