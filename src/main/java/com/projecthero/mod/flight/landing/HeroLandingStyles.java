package com.projecthero.mod.flight.landing;

import com.projecthero.mod.bluebeetle.BlueBeetle;
import com.projecthero.mod.cyborg.Cyborg;
import com.projecthero.mod.greenlantern.GreenLanternFlight;
import com.projecthero.mod.hero.power.HeroFlight;
import com.projecthero.mod.hero.power.TimedSelfFlight;
import com.projecthero.mod.hero.power.p03.FlightHandlers;
import com.projecthero.mod.hero.power.p05.GeokinesisHandlers;
import com.projecthero.mod.hero.power.p08.PyrokinesisHandlers;
import com.projecthero.mod.hero.power.p09.CryoHoldMoves;
import com.projecthero.mod.hero.power.p25.WaterHandlers;
import com.projecthero.mod.kryptonian.Kryptonian;
import com.projecthero.mod.maxsteel.MaxSteelFlight;
import com.projecthero.mod.miraculous.hawkmoth.HawkMothKit;
import com.projecthero.mod.moonknight.ability.MoonKnightCape;
import com.projecthero.mod.nova.Nova;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.heatblast.HeatblastKit;
import com.projecthero.mod.power.ThorPowers;
import com.projecthero.mod.quirk.darkshadow.DarkShadow;
import com.projecthero.mod.quirk.hellflame.Hellflame;
import com.projecthero.mod.silversurfer.SilverSurfer;

/**
 * v0.17.1: which flight lands in which {@link HeroLandingStyle} -- one line per power, in claim order (the first that
 * matches wins: the specific heroes before the generic Flight power, whose flag some of them share).
 *
 * <p>Not here on purpose: Falcon ({@code FalconFlight}'s kneel and dust ring), Iron Man and the Repulsor Boots / Thruster
 * Pack ({@code IronManFallLanding} / {@code IronManLandingSlam}), Super Strength's Power Leap ({@code StrengthLandingPose})
 * and Spider-Man ({@code SpiderAnim}) already have their own superhero landings. Ghost Rider, the Speed Force and the
 * Flash have no flight to land from. Heatblast is read-only here (his kit keeps the {@code F_FLYING} flag) so the alien
 * kits being built alongside never have to change for it.
 */
final class HeroLandingStyles {
	private HeroLandingStyles() {
	}

	static void registerAll() {
		HeroLanding.register(HeroLandingStyle.GREEN_LANTERN, GreenLanternFlight::isFlying);
		HeroLanding.register(HeroLandingStyle.KRYPTONIAN, Kryptonian::isFlying);
		HeroLanding.register(HeroLandingStyle.THOR, ThorPowers::isFlying);
		HeroLanding.register(HeroLandingStyle.NOVA, Nova::isFlying);
		HeroLanding.register(HeroLandingStyle.SILVER_SURFER, SilverSurfer::surfing);
		HeroLanding.register(HeroLandingStyle.MAX_STEEL, MaxSteelFlight::isFlying);
		HeroLanding.register(HeroLandingStyle.CYBORG, Cyborg::flying);
		HeroLanding.register(HeroLandingStyle.BLUE_BEETLE, BlueBeetle::flying);
		HeroLanding.register(HeroLandingStyle.HELLFLAME, Hellflame::flying);
		HeroLanding.register(HeroLandingStyle.DARK_SHADOW, p -> DarkShadow.is(p) && DarkShadow.abyssal(p));
		HeroLanding.register(HeroLandingStyle.HEATBLAST,
				p -> HeatblastKit.ID.equals(Omnitrix.active(p)) && (Omnitrix.state(p).kitFlags & HeatblastKit.F_FLYING) != 0);
		HeroLanding.register(HeroLandingStyle.HAWK_MOTH, HawkMothKit::flying);
		HeroLanding.register(HeroLandingStyle.MOON_KNIGHT, MoonKnightCape::isGliding);
		// the mutations: the element flights first, then the plain Flight power (they all ride its hero-flight flag)
		HeroLanding.register(HeroLandingStyle.PYRO, PyrokinesisHandlers::jetting);
		HeroLanding.register(HeroLandingStyle.CRYO, CryoHoldMoves::winged);
		HeroLanding.register(HeroLandingStyle.HYDRO, WaterHandlers::hydroJetting);
		HeroLanding.register(HeroLandingStyle.ROCK, p -> GeokinesisHandlers.surfing(p) || TimedSelfFlight.anyActive(p));
		HeroLanding.register(HeroLandingStyle.FLIGHT, p -> FlightHandlers.flying(p) || HeroFlight.isFlying(p));

		// fall-immune heroes who also land like this off a long drop, flying or not
		HeroLanding.registerDrop(HeroLandingStyle.KRYPTONIAN, Kryptonian::empowered);
		HeroLanding.registerDrop(HeroLandingStyle.THOR, com.projecthero.mod.power.ThorPassives::hasPowerOfThor);
	}
}
