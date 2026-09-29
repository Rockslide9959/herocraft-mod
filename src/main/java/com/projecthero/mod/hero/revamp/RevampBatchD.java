package com.projecthero.mod.hero.revamp;

import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p10.TelekinesisHandlers;
import com.projecthero.mod.hero.power.p15.InvisibilityLightHandlers;
import com.projecthero.mod.hero.power.p19.ShadowManipulationHandlers;
import com.projecthero.mod.hero.power.p23.GravityHandlers;
import com.projecthero.mod.hero.power.p26.MagneticHandlers;
import com.projecthero.mod.hero.revamp.d.BatchDContent;
import com.projecthero.mod.hero.revamp.d.HardLightBladeItem;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.hero.visual.MutationMeters.Kind;
import com.projecthero.mod.hero.visual.MutationMeters.Spec;
import com.projecthero.mod.hero.visual.MutationMeters.Style;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import net.minecraft.server.level.ServerPlayer;

/**
 * v0.13.22 mutation revamp, batch D -- common-side registration for:
 * <ul>
 * <li>10 Telekinesis</li>
 * <li>11 Teleportation</li>
 * <li>15 Invisibility / Light</li>
 * <li>19 Shadow Manipulation</li>
 * <li>23 Gravity Manipulation</li>
 * <li>26 Magnetic Manipulation</li>
 * </ul>
 * Entities, payloads, visual flags ({@code MutationVisuals.registerFlag}), HUD meters ({@code MutationMeters}) and any
 * world-level upkeep for these powers are registered here, so each batch owns its own file.
 */
public final class RevampBatchD {
	public static final String TELEKINESIS = "power_10_telekinesis";
	public static final String TELEPORTATION = "power_11_teleportation";
	public static final String LIGHT = "power_15_invisibility_light_manipulation";
	public static final String SHADOW = "power_19_shadow_manipulation";
	public static final String GRAVITY = "power_23_gravity_manipulation";
	public static final String MAGNETIC = "power_26_magnetic_manipulation";

	private RevampBatchD() {
	}

	/** Called once from {@code ProjectHeroMod.onInitialize}, after the ability handlers are registered. */
	public static void init() {
		BatchDContent.init();
		registerFlags();
		registerMeters();
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TelekinesisHandlers.clearSessionState();
			GravityHandlers.clearSessionState();
			com.projecthero.mod.hero.power.p11.TeleportationHandlers.clearSessionState();
		});
	}

	/** Called once per server tick (END_SERVER_TICK). */
	public static void serverTick(net.minecraft.server.MinecraftServer server) {
		TelekinesisHandlers.worldTick(server);
		GravityHandlers.worldTick(server);
	}

	private static boolean owns(ServerPlayer p, String key) {
		Power power = Powers.byKey(key);
		return power != null && ExperimentalPowers.owns(p, power);
	}

	private static float res(ServerPlayer p, String key, String name) {
		Power power = Powers.byKey(key);
		return power == null ? 0.0f : ExperimentalPowers.getResource(p, power, name);
	}

	// ---------------------------------------------------------------- overlay flags (every viewer sees these)

	private static void registerFlags() {
		// 10: purple glowing eyes while channelling / holding anything
		MutationVisuals.registerFlag("p10.eyes", p -> owns(p, TELEKINESIS) && TelekinesisHandlers.channelling(p));
		// 11: violet eyes while a portal charges, a blink is being aimed or a Bamf chain is running
		MutationVisuals.registerFlag("p11.charge", p -> owns(p, TELEPORTATION)
				&& (res(p, TELEPORTATION, "portal_charge_start") > 0.5f || res(p, TELEPORTATION, "aiming") > 0.5f
						|| res(p, TELEPORTATION, "bamf_timer") > 0.5f));
		// 15: the cloak (also hides armour + held items for everyone), the prism pane, the blade's glow
		MutationVisuals.registerFlag("p15.cloak", p -> owns(p, LIGHT)
				&& (InvisibilityLightHandlers.cloaked(p) || InvisibilityLightHandlers.sparkleFlying(p)));
		MutationVisuals.registerFlag("p15.prism", p -> owns(p, LIGHT) && InvisibilityLightHandlers.prismActive(p));
		MutationVisuals.registerFlag("p15.blade", p -> owns(p, LIGHT) && res(p, LIGHT, "blade_until") > 0.5f
				&& HardLightBladeItem.isBlade(p.getMainHandItem()));
		// 19: the black silhouette shell + purple eyes, and the Shadow Walk puddle (hides armour/items)
		MutationVisuals.registerFlag("p19.shadow_form", p -> owns(p, SHADOW) && ShadowManipulationHandlers.shadowFormActive(p));
		MutationVisuals.registerFlag("p19.shadow_walk", p -> owns(p, SHADOW) && ShadowManipulationHandlers.shadowWalking(p));
		// 23: the thin violet field shell while Gravitational Nexus is on; dark eyes while a black hole charges
		MutationVisuals.registerFlag("p23.field", p -> owns(p, GRAVITY) && GravityHandlers.fieldActive(p));
		MutationVisuals.registerFlag("p23.well", p -> owns(p, GRAVITY) && res(p, GRAVITY, "bh_charging") > 0.5f);
		// 26: magnetic field lines over the body while Magneto Hover is lifting you
		MutationVisuals.registerFlag("p26.hover", p -> owns(p, MAGNETIC) && MagneticHandlers.hovering(p)
				&& res(p, MAGNETIC, "hover_src") > 0.5f);
	}

	// ---------------------------------------------------------------- HUD bars

	private static void registerMeters() {
		// 10 Telekinesis: Psi is the whole power's fuel -- always on screen
		MutationMeters.register(new Spec(TELEKINESIS, "psi", Kind.RESERVE, Style.METER, "Psi",
				TelekinesisHandlers.MAX_PSI, 0xFFB266FF, true, true));
		MutationMeters.register(new Spec(TELEKINESIS, "burnout_left", Kind.TIMER, Style.HAIRLINE, "Psi burned out",
				200, 0xFFE05252, false, false));
		MutationMeters.register(new Spec(TELEKINESIS, "orbit", Kind.BUILD, Style.GAUGE, "Orbit",
				TelekinesisHandlers.ORBIT_MAX, 0xFFD9A6FF, false, false));
		MutationMeters.register(new Spec(TELEKINESIS, "ult_charge", Kind.BUILD, Style.SLAB, "Psychic Detonation",
				100, 0xFF9B4DFF, false, true));
		MutationMeters.register(new Spec(TELEKINESIS, "crush_ticks", Kind.TIMER, Style.HAIRLINE, "Force Crush",
				160, 0xFFC08CFF, false, false));

		// 11 Teleportation
		MutationMeters.register(new Spec(TELEPORTATION, "portal_charge", Kind.BUILD, Style.SLAB, "Portal",
				100, 0xFFB57BFF, false, true));

		// 15 Invisibility / Light
		MutationMeters.register(new Spec(LIGHT, "sparkle", Kind.RESERVE, Style.METER, "Sparkling Flight",
				115, 0xFFFFD84A, false, false));
		MutationMeters.register(new Spec(LIGHT, "prism", Kind.RESERVE, Style.METER, "Prism",
				InvisibilityLightHandlers.MAX_PRISM, 0xFF9CF2FF, false, false));
		MutationMeters.register(new Spec(LIGHT, "blade_left", Kind.TIMER, Style.HAIRLINE, "Hard-Light Blade",
				HardLightBladeItem.LIFETIME_TICKS, 0xFFFFF3A8, false, false));
		MutationMeters.register(new Spec(LIGHT, "light_charge", Kind.BUILD, Style.METER, "Light Blast",
				100, 0xFFFFE680, false, true));
		MutationMeters.register(new Spec(LIGHT, "holy_charge", Kind.BUILD, Style.SLAB, "Holy Light",
				100, 0xFFFFF0B0, false, true));
		MutationMeters.register(new Spec(LIGHT, "holy_ticks", Kind.TIMER, Style.HAIRLINE, "Holy Light",
				60, 0xFFFFF0B0, false, false));

		// 19 Shadow
		MutationMeters.register(new Spec(SHADOW, "shadow_cloak", Kind.RESERVE, Style.METER, "Shadow Cloak",
				115, 0xFF7A5AA8, false, false));
		MutationMeters.register(new Spec(SHADOW, "shadow_walk", Kind.RESERVE, Style.METER, "Shadow Walk",
				ShadowManipulationHandlers.MAX_WALK, 0xFF5B3D8C, false, false));
		MutationMeters.register(new Spec(SHADOW, "zone_charge", Kind.BUILD, Style.SLAB, "Shadow Zone",
				100, 0xFF8E6BC2, false, true));

		// 23 Gravity
		MutationMeters.register(new Spec(GRAVITY, "nexus_bar", Kind.RESERVE, Style.METER, "Gravitational Nexus",
				115, 0xFF8A4FD1, false, false));
		MutationMeters.register(new Spec(GRAVITY, "crush_hold_ticks", Kind.TIMER, Style.HAIRLINE, "Gravity Crush",
				160, 0xFFB98CFF, false, false));
		MutationMeters.register(new Spec(GRAVITY, "bh_charge", Kind.BUILD, Style.SLAB, "Black Hole",
				100, 0xFF5E2A99, false, true));
		MutationMeters.register(new Spec(GRAVITY, "heavy_left", Kind.TIMER, Style.HAIRLINE, "Heavy Ground",
				GravityHandlers.HEAVY_TICKS, 0xFF6C3FB0, false, false));

		// 26 Magnetic
		MutationMeters.register(new Spec(MAGNETIC, "hover", Kind.RESERVE, Style.METER, "Magneto Hover",
				MagneticHandlers.MAX_HOVER, 0xFF9DB4CC, false, false));
	}
}
