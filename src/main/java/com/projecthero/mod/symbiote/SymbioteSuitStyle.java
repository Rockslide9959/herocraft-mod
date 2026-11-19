package com.projecthero.mod.symbiote;

/**
 * Which ART a build draws a Normal host's Symbiote suit with. Not a player choice and not saved: since v0.17.5 (user: "Remove all
 * the classic suit styles from this power and just make it the lethal protector variants") every Normal host wears
 * {@link #LETHAL_PROTECTOR}.
 * <ul>
 *   <li>{@link #LETHAL_PROTECTOR}: the user's PlanetMinecraft skin "Venom: Lethal Protector" on {@code geo/symbiote_host_lethal.geo.json},
 *   with the 3D animated jaw, teeth and tongue. Its processed textures are .gitignored (borrowed skin, licence unchecked), made by
 *   {@code node scratchpad/symbiote/make_lethal_protector.js}.</li>
 *   <li>{@link #CLASSIC}: the FALLBACK only -- the mod's own original v0.17.5 suit art ({@code symbiote_host.png} / its tint mask,
 *   {@code geo/symbiote_host.geo.json}, no mouth). A build without the Lethal Protector textures (a public build until the skin's
 *   licence is cleared) draws this instead, silently, so the suit still works. See {@code client/symbiote/SymbioteSuitTint#styleOf}.</li>
 * </ul>
 * Both take the player's colours.
 */
public enum SymbioteSuitStyle {
	CLASSIC,
	LETHAL_PROTECTOR;
}
