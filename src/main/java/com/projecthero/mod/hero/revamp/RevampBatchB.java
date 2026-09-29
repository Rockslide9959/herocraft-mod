package com.projecthero.mod.hero.revamp;

import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.power.p05.GeokinesisHandlers;
import com.projecthero.mod.hero.power.p05.GroundType;
import com.projecthero.mod.hero.power.p06.CrystalNodeEntity;
import com.projecthero.mod.hero.power.p06.CrystalkinesisHandlers;
import com.projecthero.mod.hero.power.p08.PyrokinesisHandlers;
import com.projecthero.mod.hero.power.p09.CryokinesisHandlers;
import com.projecthero.mod.hero.power.p09.FrostStacks;
import com.projecthero.mod.hero.power.p09.IceBladeItem;
import com.projecthero.mod.hero.power.p25.WaterHandlers;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.hero.visual.MutationMeters.Kind;
import com.projecthero.mod.hero.visual.MutationMeters.Spec;
import com.projecthero.mod.hero.visual.MutationMeters.Style;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

/**
 * v0.13.22 mutation revamp, batch B -- common-side registration for:
 * <ul>
 * <li>05 Geokinesis -- the ground under you is your ammo ({@link GroundType})</li>
 * <li>06 Crystalkinesis -- plant crystal nodes, then shatter them ({@link CrystalNodeEntity})</li>
 * <li>08 Pyrokinesis -- one Heat bar, blue flames, overheating</li>
 * <li>09 Cryokinesis -- frost stacks, then frozen solid ({@link FrostStacks})</li>
 * <li>25 Water Manipulation -- a carried water supply</li>
 * </ul>
 * Entities ({@link BatchBEntities}), the Ice Blade item ({@link BatchBItems}), visual flags / values, HUD meters and
 * the batch's world-level upkeep ({@link BatchBScheduler}, {@link FrostStacks}) are registered here.
 */
public final class RevampBatchB {
	public static final String GEO = GeokinesisHandlers.KEY;
	public static final String CRYSTAL = CrystalkinesisHandlers.KEY;
	public static final String PYRO = PyrokinesisHandlers.KEY;
	public static final String CRYO = CryokinesisHandlers.KEY;
	public static final String WATER = WaterHandlers.KEY;

	private RevampBatchB() {
	}

	/** Called once from {@code ProjectHeroMod.onInitialize}, after the ability handlers are registered. */
	public static void init() {
		BatchBEntities.initialize();
		BatchBItems.initialize();
		registerVisuals();
		registerMeters();
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> BatchBScheduler.restoreAllNow());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			BatchBScheduler.clearSessionState();
			FrostStacks.clearSessionState();
		});
	}

	/** Called once per server tick (END_SERVER_TICK). */
	public static void serverTick(MinecraftServer server) {
		BatchBScheduler.tick(server);
		FrostStacks.tick(server);
		// an Ice Blade can never be stored: sweep any open container it has been put into
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.containerMenu != p.inventoryMenu) {
				IceBladeItem.sweepMenu(p);
			}
		}
	}

	// ---------------- overlays (flags every viewer sees) ----------------

	private static void registerVisuals() {
		// 05 Geokinesis
		MutationVisuals.registerFlag("p05.stone_skin", GeokinesisHandlers::armorActive);
		MutationVisuals.registerFlag("p05.rock_surf", GeokinesisHandlers::surfing);
		MutationVisuals.registerValue("p05.ground", p -> ExperimentalPowers.owns(p, GEO)
				&& (GeokinesisHandlers.armorActive(p) || GeokinesisHandlers.surfing(p))
				? GroundType.under(p).type().ordinal() + 1 : 0);
		MutationVisuals.registerValue("p05.surf_block", p -> GeokinesisHandlers.surfing(p)
				? Block.getId(GroundType.under(p).state()) : 0);
		// 06 Crystalkinesis
		MutationVisuals.registerFlag("p06.crystal_armor", CrystalkinesisHandlers::armorActive);
		MutationVisuals.registerFlag("p06.crystal_path", CrystalkinesisHandlers::onPath);
		// 08 Pyrokinesis
		MutationVisuals.registerFlag("p08.flame_body", PyrokinesisHandlers::flameBodyActive);
		MutationVisuals.registerFlag("p08.jet", PyrokinesisHandlers::jetting);
		MutationVisuals.registerFlag("p08.blue", PyrokinesisHandlers::blue);
		MutationVisuals.registerValue("p08.heat", p -> ExperimentalPowers.owns(p, PYRO)
				? PyrokinesisHandlers.heat(p) / PyrokinesisHandlers.MAX_HEAT : 0);
		// 09 Cryokinesis
		MutationVisuals.registerFlag("p09.frozen_armor", CryokinesisHandlers::frozenArmorActive);
		MutationVisuals.registerFlag("p09.frozen_solid", FrostStacks::frozen);
		MutationVisuals.registerFlag("p09.slide", CryokinesisHandlers::sliding);
		// 25 Water Manipulation
		MutationVisuals.registerFlag("p25.aquatic", WaterHandlers::aquaticActive);
	}

	// ---------------- HUD ----------------

	private static void registerMeters() {
		// 05 Geokinesis: Earth Armor strain, the charge, and a line naming the ground you are drawing on
		MutationMeters.register(new Spec(GEO, "earth_armor", Kind.RESERVE, Style.METER, "Earth Armor",
				GeokinesisHandlers.MAX_STRAIN, 0xFF9C8E7A, false, false));
		BatchBUtil.ultMeter(GEO);
		groundLine(GroundType.NONE, "Ground: none — weak rock (x0.75)", 0xFF7A7A7A);
		groundLine(GroundType.STONE, "Ground: Stone", 0xFFA8A8A8);
		groundLine(GroundType.DEEPSLATE, "Ground: Deepslate — heavy (x1.35, slower)", 0xFF5A5A66);
		groundLine(GroundType.SAND, "Ground: Sand — blinding dust", 0xFFE3D08A);
		groundLine(GroundType.NETHER, "Ground: Nether — sets alight", 0xFFC0473A);
		groundLine(GroundType.ORE, "Ground: Ore — x1.45 damage", 0xFF5FD4C8);
		groundLine(GroundType.FROST, "Ground: Ice & Snow — slows", 0xFFA8DCFF);

		// 06 Crystalkinesis
		MutationMeters.register(new Spec(CRYSTAL, "crystal_armor", Kind.RESERVE, Style.METER, "Crystal Armor",
				CrystalkinesisHandlers.MAX_STRAIN, 0xFFB57EF0, false, false));
		MutationMeters.register(new Spec(CRYSTAL, "nodes", Kind.BUILD, Style.GAUGE, "Crystal Nodes",
				CrystalNodeEntity.MAX_NODES, 0xFFD2A8FF, false, false));
		BatchBUtil.ultMeter(CRYSTAL);

		// 08 Pyrokinesis: the one Heat bar (always on), and the overheat warning
		MutationMeters.register(new Spec(PYRO, "heat", Kind.BUILD, Style.GAUGE, "Heat",
				PyrokinesisHandlers.MAX_HEAT, 0xFFFF8A2A, true, true));
		MutationMeters.register(new Spec(PYRO, "overheated", Kind.BUILD, Style.HAIRLINE,
				"OVERHEATED — vent with Heat Wave or cool below 60%", 1.0f, 0xFFE03A2A, false, false));
		BatchBUtil.ultMeter(PYRO);
		BatchBUtil.hideMeter(PYRO, "flamethrower");
		BatchBUtil.hideMeter(PYRO, "flame_body");
		BatchBUtil.hideMeter(PYRO, "flameflight");

		// 09 Cryokinesis
		MutationMeters.register(new Spec(CRYO, "frozen_armor", Kind.RESERVE, Style.METER, "Frozen Armor",
				CryokinesisHandlers.MAX_COLD, 0xFF9FE0FF, false, false));
		MutationMeters.register(new Spec(CRYO, "freeze_beam", Kind.BUILD, Style.METER, "Frostbite",
				CryokinesisHandlers.MAX_COLD, 0xFF7EC8FF, false, false));
		MutationMeters.register(new Spec(CRYO, "ice_blade", Kind.TIMER, Style.HAIRLINE, "Ice Blade",
				IceBladeItem.LIFE_TICKS, 0xFFCFF4FF, false, false));
		BatchBUtil.ultMeter(CRYO);

		// 25 Water Manipulation: the carried supply is the whole power's fuel -- a SLAB, always shown
		MutationMeters.register(new Spec(WATER, WaterHandlers.SUPPLY, Kind.RESERVE, Style.SLAB, "Water",
				WaterHandlers.MAX_WATER, 0xFF3A8DFF, true, true));
		BatchBUtil.ultMeter(WATER);
		BatchBUtil.hideMeter(WATER, "water");
	}

	private static void groundLine(GroundType t, String label, int color) {
		MutationMeters.register(new Spec(GEO, t.resource(), Kind.BUILD, Style.HAIRLINE, label, 1.0f, color, false, false));
	}
}
