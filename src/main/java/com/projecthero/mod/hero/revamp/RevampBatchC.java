package com.projecthero.mod.hero.revamp;

import com.projecthero.mod.hero.power.p07.ElectrokinesisHandlers;
import com.projecthero.mod.hero.power.p14.SonicScreamHandlers;
import com.projecthero.mod.hero.power.p20.EnergyAbsorptionHandlers;
import com.projecthero.mod.hero.power.p21.ShockwaveHandlers;
import com.projecthero.mod.hero.power.p24.WindEntities;
import com.projecthero.mod.hero.power.p24.WindHandlers;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.hero.visual.MutationMeters.Kind;
import com.projecthero.mod.hero.visual.MutationMeters.Spec;
import com.projecthero.mod.hero.visual.MutationMeters.Style;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * v0.13.22 mutation revamp, batch C -- common-side registration for:
 * <ul>
 * <li>07 Electrokinesis</li>
 * <li>14 Sonic Scream</li>
 * <li>20 Energy Absorption</li>
 * <li>21 Shockwave Manipulation</li>
 * <li>24 Wind Manipulation</li>
 * </ul>
 * Entities, payloads, visual flags ({@code MutationVisuals.registerFlag}), HUD meters ({@code MutationMeters}) and any
 * world-level upkeep for these powers are registered here, so each batch owns its own file.
 */
public final class RevampBatchC {
	public static final String ELECTRO = ElectrokinesisHandlers.KEY;
	public static final String SONIC = SonicScreamHandlers.KEY;
	public static final String ENERGY = EnergyAbsorptionHandlers.KEY;
	public static final String SHOCK = ShockwaveHandlers.KEY;
	public static final String WIND = WindHandlers.KEY;

	/** Overlay flags (client renderers registered in {@code RevampClientC}). */
	public static final String FLAG_CHARGED = "p07.charged";
	public static final String FLAG_ECHO = "p14.echo";
	public static final String FLAG_ABSORB = "p20.absorb";
	public static final String FLAG_HANDS = "p20.hands";
	public static final String FLAG_KINETIC = "p21.kinetic";
	public static final String FLAG_AIR = "p24.air";
	/** Synced visual values. */
	public static final String VALUE_ELEMENT = "p20.element";
	public static final String VALUE_FILL = "p20.fill";
	public static final String VALUE_KINETIC = "p21.charge";

	private RevampBatchC() {
	}

	/** Called once from {@code ProjectHeroMod.onInitialize}, after the ability handlers are registered. */
	public static void init() {
		WindEntities.initialize();
		registerVisuals();
		registerMeters();
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearSessionState());
	}

	private static void registerVisuals() {
		MutationVisuals.registerFlag(FLAG_CHARGED, p -> BatchCFx.toggledQuick(p, ELECTRO, "charged_mode"));
		MutationVisuals.registerFlag(FLAG_ECHO, p -> BatchCFx.toggledQuick(p, SONIC, "echolocation"));
		MutationVisuals.registerFlag(FLAG_ABSORB, p -> BatchCFx.toggledQuick(p, ENERGY, "absorption_mode"));
		MutationVisuals.registerFlag(FLAG_HANDS, p -> BatchCFx.ownsQuick(p, ENERGY)
				&& BatchCFx.resourceQuick(p, ENERGY, EnergyAbsorptionHandlers.METER) >= EnergyAbsorptionHandlers.MAX * 0.2f);
		MutationVisuals.registerValue(VALUE_ELEMENT, p -> BatchCFx.ownsQuick(p, ENERGY) ? EnergyAbsorptionHandlers.element(p) : 0);
		MutationVisuals.registerValue(VALUE_FILL, p -> BatchCFx.ownsQuick(p, ENERGY)
				? Math.round(BatchCFx.resourceQuick(p, ENERGY, EnergyAbsorptionHandlers.METER) / EnergyAbsorptionHandlers.MAX * 10) / 10.0
				: 0);
		MutationVisuals.registerFlag(FLAG_KINETIC, p -> BatchCFx.ownsQuick(p, SHOCK)
				&& BatchCFx.resourceQuick(p, SHOCK, "charge") >= ShockwaveHandlers.MAX_CHARGE * 0.25f);
		MutationVisuals.registerValue(VALUE_KINETIC, p -> BatchCFx.ownsQuick(p, SHOCK)
				? Math.round(BatchCFx.resourceQuick(p, SHOCK, "charge") / ShockwaveHandlers.MAX_CHARGE * 10) / 10.0
				: 0);
		MutationVisuals.registerFlag(FLAG_AIR, WindHandlers::airShell);
	}

	private static void registerMeters() {
		// 07 Electrokinesis: the charge cell + the storm's hold-to-charge
		MutationMeters.register(new Spec(ELECTRO, "ecell", Kind.RESERVE, Style.METER, "Charge",
				ElectrokinesisHandlers.MAX_CHARGE, 0xFF4FB8FF, true, false));
		MutationMeters.register(new Spec(ELECTRO, "storm_charge", Kind.BUILD, Style.HAIRLINE, "Electrical Storm",
				100f, 0xFFC8EEFF, false, false));
		// 14 Sonic Scream: Voice + the supersonic hold
		MutationMeters.register(new Spec(SONIC, "voice", Kind.RESERVE, Style.METER, "Voice",
				SonicScreamHandlers.VOICE_MAX, 0xFF8FF0E6, true, false));
		MutationMeters.register(new Spec(SONIC, "sonic_charge", Kind.BUILD, Style.HAIRLINE, "Supersonic Scream",
				100f, 0xFFD4FBFF, false, false));
		// 20 Energy Absorption: the meter (the client recolours it by element) + the two hold bars
		MutationMeters.register(energySpec(EnergyAbsorptionHandlers.RAW));
		MutationMeters.register(new Spec(ENERGY, "blast_charge", Kind.BUILD, Style.HAIRLINE, "Energy Blast",
				100f, 0xFFFFFFFF, false, false));
		MutationMeters.register(new Spec(ENERGY, "pulse_charge", Kind.BUILD, Style.HAIRLINE, "Overload",
				100f, 0xFFFFFFFF, false, false));
		// 21 Shockwave: the kinetic gauge + the detonation hold
		MutationMeters.register(new Spec(SHOCK, "charge", Kind.BUILD, Style.GAUGE, "Kinetic",
				ShockwaveHandlers.MAX_CHARGE, 0xFFFFB347, true, false));
		MutationMeters.register(new Spec(SHOCK, "kd_progress", Kind.BUILD, Style.HAIRLINE, "Kinetic Detonation",
				100f, 0xFFFFE0B0, false, false));
		// 24 Wind: the wind reserve + the hurricane charge / storm and the wide blade
		MutationMeters.register(new Spec(WIND, "tailwind", Kind.RESERVE, Style.METER, "Wind",
				WindHandlers.MAX_WIND, 0xFFD8F4FF, true, false));
		MutationMeters.register(new Spec(WIND, "ult_charge", Kind.BUILD, Style.HAIRLINE, "Hurricane",
				100f, 0xFFB8E0FF, false, false));
		MutationMeters.register(new Spec(WIND, "hurr", Kind.TIMER, Style.HAIRLINE, "Hurricane",
				11 * 20f, 0xFF9FC8F0, false, false));
		MutationMeters.register(new Spec(WIND, "blade_hold", Kind.BUILD, Style.HAIRLINE, "Wind Blade",
				34f, 0xFFF0F8FF, false, false));
	}

	private static final String[] ELEMENT_LABEL = { "Energy", "Energy - Fire", "Energy - Lightning", "Energy - Explosion",
			"Energy - Magic" };

	/** The Energy meter spec for an element (the client re-registers it when its own element changes). */
	public static Spec energySpec(int element) {
		int el = Math.max(0, Math.min(4, element));
		return new Spec(ENERGY, EnergyAbsorptionHandlers.METER, Kind.BUILD, Style.METER, ELEMENT_LABEL[el],
				EnergyAbsorptionHandlers.MAX, 0xFF000000 | EnergyAbsorptionHandlers.ELEMENT_RGB[el], true, true);
	}

	/** Called once per server tick (END_SERVER_TICK). */
	public static void serverTick(net.minecraft.server.MinecraftServer server) {
		ElectrokinesisHandlers.serverTick(server);
		SonicScreamHandlers.serverTick(server);
		ShockwaveHandlers.serverTick(server);
		WindHandlers.serverTick(server);
	}

	/** Drops every static scratch map of this batch (server stop). */
	public static void clearSessionState() {
		ElectrokinesisHandlers.clearSessionState();
		SonicScreamHandlers.clearSessionState();
		ShockwaveHandlers.clearSessionState();
		WindHandlers.clearSessionState();
	}
}
