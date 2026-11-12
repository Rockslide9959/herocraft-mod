package com.projecthero.mod.client.flight;

import com.projecthero.mod.attachment.ModAttachments;

import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3 (tester: "I really like Undead's flight with full locomotion ... could you also apply that sort of flight to the other
 * flying powers to make flight feel less stiff?"): how alive a flight's body looks, the way Undead's Blood Propulsion does. One
 * shared set of motions, tuned per power, read by {@code FlightPoseHelper} (lean and bank), {@code PlayerRendererMixin} (the
 * hover bob) and {@code HumanoidModelMixin} (the limbs):
 * <ul>
 *   <li><b>bank</b> -- the body rolls into a turn ({@link #bankPerDegree} degrees of roll per degree a tick of yaw, up to
 *       {@link #maxBank}) and into a sideways slide ({@link #strafeBank} degrees per block a tick);</li>
 *   <li><b>path lean</b> -- flying along, the body lies along the actual flight path: a climb stands it up, a dive tips it head
 *       down ({@link #pitchFollow} of the path's pitch);</li>
 *   <li><b>hover</b> -- hanging in the air the body bobs ({@link #bobAmp} blocks, {@link #bobRate} radians a tick) and sways a
 *       little;</li>
 *   <li><b>limbs</b> -- legs dangle and sway in a hover and trail with a gentle scissor at speed ({@link #limbWave} scales it).</li>
 * </ul>
 * Iron Man keeps his own suit poses ({@code IronManFlightPose}: his thruster effects are placed through the lean alone), the
 * Physics Flight setting keeps its own attitude, and Undead has his own (UndeadMotion / UndeadPose).
 */
public record FlightFeel(float bankPerDegree, float maxBank, float strafeBank, float pitchFollow, float bobAmp, float bobRate,
		float limbWave) {

	/** Wind / rock / flame / the experimental Flight power and anything else on the shared hero flight. */
	public static final FlightFeel GENERIC = new FlightFeel(2.0f, 40f, 22f, 0.85f, 0.045f, 0.075f, 1.0f);
	/** Graceful and smooth: deep banks, follows the path closely, a slow float. */
	public static final FlightFeel KRYPTONIAN = new FlightFeel(2.2f, 45f, 24f, 0.95f, 0.04f, 0.06f, 0.8f);
	public static final FlightFeel NOVA = new FlightFeel(2.2f, 45f, 24f, 0.9f, 0.045f, 0.08f, 1.0f);
	public static final FlightFeel GREEN_LANTERN = new FlightFeel(1.8f, 38f, 20f, 0.85f, 0.05f, 0.07f, 0.9f);
	/** Carried by the hammer: steadier, a heavier body. */
	public static final FlightFeel THOR = new FlightFeel(1.6f, 35f, 18f, 0.8f, 0.05f, 0.065f, 0.8f);
	public static final FlightFeel MAX_STEEL = new FlightFeel(2.0f, 42f, 22f, 0.9f, 0.035f, 0.09f, 0.7f);
	/** Jet boots: stiffer, a quick buzzing hover. */
	public static final FlightFeel CYBORG = new FlightFeel(1.5f, 30f, 16f, 0.8f, 0.03f, 0.1f, 0.4f);
	public static final FlightFeel BLUE_BEETLE = new FlightFeel(1.9f, 40f, 20f, 0.9f, 0.04f, 0.09f, 0.6f);
	/** The Falcon banks his wingsuit himself ({@code FalconFlightClient}); only the path lean and the limbs here. */
	public static final FlightFeel FALCON = new FlightFeel(0f, 0f, 0f, 0.9f, 0f, 0f, 0.5f);

	/**
	 * The feel of whatever this player is flying with right now, or null for none (not flying on the shared pose, or Iron Man,
	 * who keeps his own). {@code thor}: flying with Mjolnir.
	 */
	public static FlightFeel of(Player player, boolean thor) {
		if (thor) {
			return THOR;
		}
		if (com.projecthero.mod.kryptonian.Kryptonian.isFlying(player)) {
			return KRYPTONIAN;
		}
		if (com.projecthero.mod.nova.Nova.isFlying(player)) {
			return NOVA;
		}
		if (com.projecthero.mod.saiyan.Saiyan.isFlying(player)) {
			return KRYPTONIAN; // v0.17.3: the Saiyan banks and leans like the Kryptonian
		}
		if (com.projecthero.mod.starfire.Starfire.isFlying(player)) {
			return KRYPTONIAN; // v0.17.3: Starfire too
		}

		if (com.projecthero.mod.shazam.Shazam.isFlying(player)) {
			return KRYPTONIAN; // v0.17.3: Shazam flies like the Kryptonian
		}

		if (com.projecthero.mod.doctorfate.DoctorFate.isFlying(player)) {
			return GENERIC; // v0.17.3: Doctor Fate hovers upright, cloak billowing
		}
		if (com.projecthero.mod.vision.Vision.isFlying(player)) {
			return KRYPTONIAN; // v0.17.3: Vision flies like the Kryptonian, cape streaming
		}
		if (player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_FLYING, false)) {
			return GREEN_LANTERN;
		}
		if (player.getAttachedOrElse(ModAttachments.MAX_STEEL_FLYING, false)) {
			return MAX_STEEL;
		}
		if (com.projecthero.mod.cyborg.Cyborg.flying(player)) {
			return CYBORG;
		}
		if (com.projecthero.mod.bluebeetle.BlueBeetle.flying(player)) {
			return BLUE_BEETLE;
		}
		if (com.projecthero.mod.falcon.Falcon.flying(player)) {
			return FALCON;
		}
		if (player.getAttachedOrElse(ModAttachments.HERO_FLYING, false)) {
			return GENERIC;
		}
		return null;
	}
}
