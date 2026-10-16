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
		// 05 Geokinesis (v0.16.5 rework: its own class)
		GeokinesisHandlers.registerVisuals();
		// 06 Crystalkinesis
		MutationVisuals.registerFlag("p06.crystal_armor", CrystalkinesisHandlers::armorActive);
		MutationVisuals.registerFlag("p06.crystal_path", CrystalkinesisHandlers::onPath);
		// 08 Pyrokinesis
		PyrokinesisHandlers.registerVisuals(); // v0.16.5 rework
		// 09 Cryokinesis
		CryokinesisHandlers.registerVisuals(); // v0.16.5 rework
		// 25 Water Manipulation
		MutationVisuals.registerFlag("p25.aquatic", WaterHandlers::aquaticActive);
	}

	// ---------------- HUD ----------------

	private static void registerMeters() {
		// 05 Geokinesis (v0.16.5 rework): the always-on Earth Hairline + timers
		GeokinesisHandlers.registerMeters();

		// 06 Crystalkinesis
		MutationMeters.register(new Spec(CRYSTAL, "crystal_armor", Kind.RESERVE, Style.METER, "Crystal Armor",
				CrystalkinesisHandlers.MAX_STRAIN, 0xFFB57EF0, false, false));
		MutationMeters.register(new Spec(CRYSTAL, "nodes", Kind.BUILD, Style.GAUGE, "Crystal Nodes",
				CrystalNodeEntity.MAX_NODES, 0xFFD2A8FF, false, false));
		BatchBUtil.ultMeter(CRYSTAL);

		// 08 Pyrokinesis: since the v0.16.5 rework all in PyrokinesisHandlers (the always-on Heat Hairline + timers)
		PyrokinesisHandlers.registerMeters();

		// 09 Cryokinesis
		CryokinesisHandlers.registerMeters(); // v0.16.5 rework: the Cold Hairline + timers

		// 25 Water Manipulation: the carried supply is the whole power's fuel -- a SLAB, always shown
		MutationMeters.register(new Spec(WATER, WaterHandlers.SUPPLY, Kind.RESERVE, Style.SLAB, "Water",
				WaterHandlers.MAX_WATER, 0xFF3A8DFF, true, true));
		BatchBUtil.ultMeter(WATER);
		BatchBUtil.hideMeter(WATER, "water");
	}

}
