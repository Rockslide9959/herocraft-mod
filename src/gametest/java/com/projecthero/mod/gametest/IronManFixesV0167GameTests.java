package com.projecthero.mod.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.ability.IronManComicRouter;
import com.projecthero.mod.ironman.ability.IronManComicState;
import com.projecthero.mod.ironman.ability.IronManMark9;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.squad.Squad;
import com.projecthero.mod.squad.SquadManager;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user) Iron Man fix batch: the Mk I-IX naming in every Iron Man line, the Mk IX Lava Geyser throwing you as
 * high out of deep lava as off the ground, and the Mk IX Heat Dump sparing squadmates (the Era II Workbench tab is in
 * {@link StarkWorkbenchGameTests}).
 */
public class IronManFixesV0167GameTests implements FabricGameTest {
	private static final String MK9 = "mark_9";
	private static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
	private static final ArmorItem.Type[] TYPES = { ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS,
			ArmorItem.Type.BOOTS };

	private static final StringBuilder TRACE = new StringBuilder();

	private static JsonObject lang() {
		try (InputStream in = IronManFixesV0167GameTests.class.getResourceAsStream("/assets/projecthero/lang/en_us.json")) {
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			throw new IllegalStateException("cannot read en_us.json", e);
		}
	}

	private static boolean ironManKey(String k) {
		return k.contains("iron_man") || k.contains("ironman") || k.contains("stark") || k.contains("suit_call")
				|| k.contains("suit_platform") || k.contains("gantry") || k.contains("weapon_wheel") || k.contains("colantotte")
				|| k.contains("repulsor") || k.contains("mark_");
	}

	// ------------------------------------------------------------------ 4: naming

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyIronManLineUsesTheMkOneToNineNames(GameTestHelper h) {
		// "Mark 8", "Mark 1-7", "Marks 2 to 7", "MARK 7", "Mark 8+" ... -- the old numbering (the future Mark 42 / Mark 50
		// steps of Eras III / IV are their real names and stay)
		Pattern old = Pattern.compile("(?i)\\bmarks? ?[0-9](?![0-9])");
		StringBuilder bad = new StringBuilder();
		for (Map.Entry<String, JsonElement> e : lang().entrySet()) {
			if (ironManKey(e.getKey()) && old.matcher(e.getValue().getAsString()).find()) {
				bad.append(e.getKey()).append("; ");
			}
		}
		h.assertTrue(bad.length() == 0, "old Mark numbering left in: " + bad);
		JsonObject l = lang();
		h.assertTrue(l.get("projecthero.guide.iron_man.mark_8").getAsString().contains("Hulkbuster I"), "the Hulkbuster page is the Hulkbuster I");
		// the Stark Glasses call what the code lets them call: the Mk IX Inferno and the Hulkbuster (mark number 8+)
		String glasses = l.get("screen.projecthero.suit_call.glasses_mk8_only_hint").getAsString();
		h.assertTrue(glasses.contains("Mk IX") && glasses.contains("Hulkbuster"), "the glasses hint names the Mk IX and the Hulkbuster");
		h.assertTrue(IronManSuits.byId("mark_9").markNumber() >= com.projecthero.mod.ironman.gear.StarkGear.GLASSES_MIN_MARK
				&& IronManSuits.byId("mark_8").markNumber() >= com.projecthero.mod.ironman.gear.StarkGear.GLASSES_MIN_MARK
				&& IronManSuits.byId("mark_vii").markNumber() < com.projecthero.mod.ironman.gear.StarkGear.GLASSES_MIN_MARK,
				"...which is what StarkGear allows");
		h.assertTrue(l.get("item.projecthero.repulsor.tooltip.flight").getAsString().contains("of the %s"), "repulsor tooltip names the suit");
		h.succeed();
	}

	// ------------------------------------------------------------------ 8: Lava Geyser

	private static ServerPlayer tony(GameTestHelper h, BlockPos rel) {
		h.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		p.getInventory().clearContent();
		BlockPos at = h.absolutePos(rel);
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		for (int i = 0; i < 4; i++) {
			p.setItemSlot(SLOTS[i], new ItemStack(IronManItems.armor(MK9, TYPES[i])));
		}
		TonyStark.setActiveSuit(p, MK9);
		IronManEnergy.setIntegrity(p, MK9, IronManEnergy.maxIntegrity(MK9));
		IronManMark9.setHeat(p, 0f);
		return p;
	}

	/**
	 * Fires the Lava Geyser and follows the throw with vanilla's movement rules (the mock player is not ticked): lava =
	 * move, then velocity x0.5 and -0.02; air = move, then (vy - 0.08) x 0.98. Returns the peak feet height.
	 */
	private static double geyserPeak(GameTestHelper h, ServerPlayer p) {
		IronManSuit suit = IronManSuits.byId(MK9);
		h.assertTrue(IronManComicRouter.fireForTests(p, suit, 3, true), "Shift+X Lava Geyser fires");
		double peak = p.getY();
		TRACE.setLength(0);
		for (int t = 0; t < 60; t++) {
			IronManMark9.tickForTests(p);
			boolean lava = IronManMark9.inLava(p);
			TRACE.append(String.format(java.util.Locale.ROOT, "[%.2f %.2f%s]", p.getY(), p.getDeltaMovement().y, lava ? "L" : ""));
			p.setPos(p.position().add(0, p.getDeltaMovement().y, 0)); // straight up a clear column (no ceiling in the test box)
			Vec3 v = p.getDeltaMovement();
			p.setDeltaMovement(lava ? new Vec3(v.x * 0.5, v.y * 0.5 - 0.02, v.z * 0.5) : new Vec3(v.x * 0.91, (v.y - 0.08) * 0.98, v.z * 0.91));
			peak = Math.max(peak, p.getY());
			if (!lava && v.y < 0) {
				break;
			}
		}
		return peak;
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void theLavaGeyserThrowsAsHighOutOfDeepLavaAsOffTheGround(GameTestHelper h) {
		// on stone
		for (int x = 0; x < 3; x++) {
			for (int z = 0; z < 3; z++) {
				h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
			}
		}
		ServerPlayer ground = tony(h, new BlockPos(1, 2, 1));
		double groundY = ground.getY();
		double groundRise = geyserPeak(h, ground) - groundY;
		String groundTrace = TRACE.toString();
		h.getLevel().getServer().getPlayerList().remove(ground);
		// a lava well five deep (surface at y 7), stone round it
		for (int y = 1; y <= 7; y++) {
			for (int x = 4; x <= 6; x++) {
				for (int z = 0; z <= 2; z++) {
					boolean wall = x != 5 || z != 1 || y == 1;
					h.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE : Blocks.LAVA);
				}
			}
		}
		ServerPlayer deep = tony(h, new BlockPos(5, 2, 1));
		double surface = h.absolutePos(new BlockPos(5, 8, 1)).getY();
		h.assertTrue(IronManMark9.inLava(deep), "starts at the bottom of the lava");
		double lavaRise = geyserPeak(h, deep) - surface;
		h.getLevel().getServer().getPlayerList().remove(deep);
		h.assertTrue(groundRise >= 8.0, "off the ground ~10 blocks, got " + groundRise + " " + groundTrace);
		h.assertTrue(lavaRise >= groundRise - 1.5, "out of deep lava as high above the surface (" + lavaRise + ") as off the ground ("
				+ groundRise + ") " + TRACE);
		h.succeed();
	}

	// ------------------------------------------------------------------ 9: Heat Dump vs squads

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void theHeatDumpSparesSquadmatesButNotStrangers(GameTestHelper h) {
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 9; z++) {
				h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
			}
		}
		MinecraftServer server = h.getLevel().getServer();
		boolean savedPvp = server.isPvpAllowed();
		boolean savedAbilityPvp = HeroConfig.get().abilityPvpDamage;
		server.setPvpAllowed(true);
		HeroConfig.get().abilityPvpDamage = true;
		ServerPlayer p = tony(h, new BlockPos(4, 2, 4));
		ServerPlayer mate = h.makeMockServerPlayerInLevel();
		ServerPlayer stranger = h.makeMockServerPlayerInLevel();
		for (ServerPlayer o : new ServerPlayer[] { mate, stranger }) {
			o.setGameMode(GameType.SURVIVAL);
			o.getInventory().clearContent();
			try { // a fresh mock player has 3 s of spawn protection: take it off so a real hit can land
				java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
				f.setAccessible(true);
				f.setInt(o, 0);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
		}
		Vec3 a = h.absoluteVec(new Vec3(6.5, 2, 4.5));
		Vec3 b = h.absoluteVec(new Vec3(2.5, 2, 4.5));
		mate.teleportTo(a.x, a.y, a.z);
		stranger.teleportTo(b.x, b.y, b.z);
		Husk husk = EntityType.HUSK.create(h.getLevel());
		Vec3 c = h.absoluteVec(new Vec3(4.5, 2, 6.5));
		husk.moveTo(c.x, c.y, c.z);
		husk.setNoAi(true);
		husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		husk.setHealth(200f);
		h.getLevel().addFreshEntity(husk);
		SquadManager squads = SquadManager.get(server);
		Squad squad = squads.create("hd" + p.getUUID().toString().substring(0, 8), p.getUUID());
		squads.addMember(squad, mate.getUUID());
		float mateHp = mate.getHealth();
		float strangerHp = stranger.getHealth();
		IronManMark9.setHeat(p, IronManMark9.HEAT_MAX);
		h.assertTrue(IronManComicRouter.fireForTests(p, IronManSuits.byId(MK9), 5, false), "Heat Dump fires");
		h.runAfterDelay(6, () -> {
			IronManComicState.tickServerForTests(server);
			try {
				h.assertTrue(husk.getHealth() < 200f, "the nova hits the husk");
				h.assertTrue(mate.getHealth() == mateHp && !mate.isOnFire(), "the squadmate is spared (no damage, no fire)");
				h.assertTrue(mate.getDeltaMovement().horizontalDistanceSqr() < 1.0e-4, "and not shoved");
				h.assertTrue(stranger.getHealth() < strangerHp, "a stranger (ability PvP on) is hit");
			} finally {
				squads.disband(squad);
				server.setPvpAllowed(savedPvp);
				HeroConfig.get().abilityPvpDamage = savedAbilityPvp;
				for (ServerPlayer o : new ServerPlayer[] { p, mate, stranger }) {
					server.getPlayerList().remove(o);
				}
			}
			h.succeed();
		});
	}
}
