package com.projecthero.mod.hero.revamp;

import com.projecthero.mod.hero.power.p16.SpiderClimbingHandlers;
import com.projecthero.mod.hero.power.p17.ElasticityHandlers;
import com.projecthero.mod.hero.power.p18.CrushingDensityEffect;
import com.projecthero.mod.hero.power.p18.DensityManipulationHandlers;
import com.projecthero.mod.hero.power.p22.PlantEntities;
import com.projecthero.mod.hero.power.p22.PlantManipulationHandlers;
import com.projecthero.mod.hero.power.p27.ShrunkenEffect;
import com.projecthero.mod.hero.power.p27.SizeHandlers;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.hero.visual.MutationMeters.Kind;
import com.projecthero.mod.hero.visual.MutationMeters.Spec;
import com.projecthero.mod.hero.visual.MutationMeters.Style;
import com.projecthero.mod.hero.visual.MutationVisuals;

/**
 * v0.13.22 mutation revamp, batch E -- common-side registration for:
 * <ul>
 * <li>16 Spider Climbing / Adhesion</li>
 * <li>17 Elasticity</li>
 * <li>18 Density Manipulation</li>
 * <li>22 Plant Manipulation</li>
 * <li>27 Size Manipulation</li>
 * </ul>
 * Entities, payloads, visual flags ({@code MutationVisuals.registerFlag}), HUD meters ({@code MutationMeters}) and any
 * world-level upkeep for these powers are registered here, so each batch owns its own file.
 *
 * <p>The ability handlers themselves live in each power's {@code hero.power.pNN} package (registered earlier by
 * {@code HeroPowerHandlers}); this class adds what the revamp layered on top: the two new mob effects (Crushing
 * Density, Shrunken), the Thorn Sentry / Thorn entities, every overlay flag and synced visual value the client
 * renderers in {@code RevampClientE} key off, and the HUD bars.
 */
public final class RevampBatchE {
	public static final String P16 = "power_16_spider_climbing_adhesion";
	public static final String P17 = "power_17_elasticity";
	public static final String P18 = "power_18_density_manipulation";
	public static final String P22 = "power_22_plant_manipulation_chlorokinesis";
	public static final String P27 = "power_27_size_manipulation";

	private RevampBatchE() {
	}

	/** Called once from {@code ProjectHeroMod.onInitialize}, after the ability handlers are registered. */
	public static void init() {
		// registries: class-load the holders while the registries are still open
		CrushingDensityEffect.initialize();
		ShrunkenEffect.initialize();
		PlantEntities.initialize();

		registerVisuals();
		registerMeters();
	}

	/** Called once per server tick (END_SERVER_TICK). Everything batch E runs per tick is per-player, in the handlers. */
	public static void serverTick(net.minecraft.server.MinecraftServer server) {
	}

	// ---------------- overlay flags + synced values (every predicate checks ownership first) ----------------

	private static void registerVisuals() {
		// 16 -- web-lined palms/soles while a grip toggle is on, red eyes in Predator Rush, the Spider-Sense tingle
		MutationVisuals.registerFlag("p16.grip", SpiderClimbingHandlers::gripping);
		MutationVisuals.registerFlag("p16.rush", SpiderClimbingHandlers::rushing);
		MutationVisuals.registerFlag("p16.sense", SpiderClimbingHandlers::senseActive);

		// 17 -- the stretched arm (length, start tick, draw mode), the Rubber Shield balloon, the glide canopy,
		// a rubbery sheen while Elastic Form is on
		MutationVisuals.registerFlag("p17.arm", ElasticityHandlers::armStretched);
		MutationVisuals.registerValue("p17.arm_len", ElasticityHandlers::armLength);
		MutationVisuals.registerValue("p17.arm_t", ElasticityHandlers::armStart);
		MutationVisuals.registerValue("p17.arm_mode", ElasticityHandlers::armMode);
		MutationVisuals.registerFlag("p17.shield", ElasticityHandlers::shielding);
		MutationVisuals.registerFlag("p17.glide", ElasticityHandlers::gliding);
		MutationVisuals.registerFlag("p17.form", p -> ElasticityHandlers.formActiveClient(p)
				&& ElasticityHandlers.form(p) != ElasticityHandlers.Form.COMPRESSION);

		// 18 -- the density shell (blue light / orange heavy), the dodge shimmer, the armed Crushing Touch fist
		MutationVisuals.registerFlag("p18.shell", DensityManipulationHandlers::shellVisible);
		MutationVisuals.registerValue("p18.density", p -> DensityManipulationHandlers.owns(p)
				? DensityManipulationHandlers.density(p) / 100.0 : 0.0);
		MutationVisuals.registerFlag("p18.intangible", DensityManipulationHandlers::intangible);
		MutationVisuals.registerFlag("p18.crush", DensityManipulationHandlers::crushArmed);

		// 22 -- the bark-and-leaf skin of Nature's Blessing, the Overgrowth root-gather
		MutationVisuals.registerFlag("p22.blessing", PlantManipulationHandlers::blessingOn);
		MutationVisuals.registerFlag("p22.overgrowth", PlantManipulationHandlers::overgrowthCharging);

		// 27 -- nothing extra: the body itself changes size (1 s ease) and the moves carry their own poses
		MutationVisuals.registerFlag("p27.carry", SizeHandlers::carrying);
	}

	// ---------------- HUD bars ----------------

	private static void registerMeters() {
		// 16
		MutationMeters.register(new Spec(P16, "rush_ticks", Kind.TIMER, Style.HAIRLINE, "Predator Rush", 700, 0xFFD23A3A, false, false));
		MutationMeters.register(new Spec(P16, "sense_ticks", Kind.TIMER, Style.HAIRLINE, "Spider-Sense", SpiderClimbingHandlers.SENSE_TICKS,
				0xFFFF6A6A, false, false));
		// 17
		MutationMeters.register(new Spec(P17, "stretch_charge", Kind.BUILD, Style.METER, "Stretch Charge", 100, 0xFFE8A06A, false, true));
		MutationMeters.register(new Spec(P17, "rubber", Kind.RESERVE, Style.METER, "Rubber", ElasticityHandlers.MAX_RUBBER, 0xFFF08CB4, false, false));
		MutationMeters.register(new Spec(P17, "glide_ticks", Kind.TIMER, Style.HAIRLINE, "Parachute Glide", ElasticityHandlers.GLIDE_TICKS,
				0xFFB8E0FF, false, false));
		// 18
		MutationMeters.register(new Spec(P18, "density", Kind.RESERVE, Style.GAUGE, "Density", 300, 0xFFFFB24A, true, false));
		MutationMeters.register(new Spec(P18, "phase", Kind.RESERVE, Style.METER, "Phase", DensityManipulationHandlers.MAX_PHASE, 0xFF7FD0FF,
				false, false));
		MutationMeters.register(new Spec(P18, "crush_armed", Kind.TIMER, Style.HAIRLINE, "Crushing Touch", DensityManipulationHandlers.CRUSH_ARM_TICKS,
				0xFFFF8A2A, false, false));
		// 22
		MutationMeters.register(new Spec(P22, "ult_charge", Kind.BUILD, Style.SLAB, "Overgrowth", 100, 0xFF5FCB4A, false, true));
		MutationMeters.register(new Spec(P22, "sentry_ticks", Kind.TIMER, Style.HAIRLINE, "Thorn Sentry",
				com.projecthero.mod.hero.power.p22.ThornSentryEntity.LIFETIME, 0xFF8FD16A, false, false));
		MutationMeters.register(new Spec(P22, "spore_ticks", Kind.TIMER, Style.HAIRLINE, "Spore Cloud", PlantManipulationHandlers.SPORE_TICKS,
				0xFFB6C96A, false, false));
		// 27
		MutationMeters.register(new Spec(P27, "giant_form", Kind.RESERVE, Style.METER, "Size Strain", SizeHandlers.MAX_STRAIN, 0xFF9C6BFF,
				true, false));
		MutationMeters.register(new Spec(P27, "mount_ticks", Kind.TIMER, Style.HAIRLINE, "Mount", SizeHandlers.RIDE_TICKS, 0xFFC9A36A,
				false, false));
	}
}
