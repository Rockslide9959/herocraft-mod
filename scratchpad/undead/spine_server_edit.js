// v0.17.3 Spine Whip: server-side edits (Rib Cage Guard out, Spine Whip in). CRLF-safe. Run from the repo root.
const { edit } = require('./edit_lib.js');
const D = 'src/main/java/com/projecthero/mod/undead/';
edit(D + 'UndeadKitConfig.java', [
	[' * {@link UndeadConfig}), Shift+V (Rib Cage Guard),', ' * {@link UndeadConfig}), Shift+V (Spine Whip),'],
	['\tpublic static final int ANIM_REFORM_GATHER = 124;\n',
	 '\tpublic static final int ANIM_REFORM_GATHER = 124;\n\t/** Tap Shift+V: Spine Whip -- the spine torn out (to {@link #SPINE_WINDUP}), the lash, the whip let go and crumbling. */\n\tpublic static final int ANIM_SPINE_WHIP = 125;\n'],
	['\t/** Shift+V: the Rib Cage Guard is up (UndeadState.flags; a high bit, clear of the shared ones). */\n\tpublic static final int FLAG_RIB_GUARD = 1 << 24;\n', ''],
	['\t/** Shift+V let go before this is a tap: the Rib Cage Guard.', '\t/** Shift+V let go before this is a tap: the Spine Whip.'],
	[`	// ---------------------------------------------------------------- Shift+V: Rib Cage Guard

	/** The longest it stays up (held); a quick tap keeps it up this long too. */
	public static final int RIB_TICKS = 80;
	/** Let go of Shift+V before this: it was a tap, and the guard stays up its full time. */
	public static final int RIB_TAP = 6;
	/** What a frontal melee hit still does (75% blocked). */
	public static final float RIB_MELEE_SHARE = 0.25f;
	/** Who hits the ribs (melee) takes this back. */
	public static final float RIB_THORNS = 4f;
	/** How wide "in front" is: the cosine of the half-angle. */
	public static final double RIB_FRONT_COS = 0.1;
	public static final int RIB_COOLDOWN = 240;
`, `	// ---------------------------------------------------------------- tap Shift+V: Spine Whip

	/** He reaches back and tears his spine out through his back this long before the lash. */
	public static final int SPINE_WINDUP = 7;
	/** The lash: across the whole arc in this many ticks. */
	public static final int SPINE_SWEEP = 4;
	/** After the lash (client only): the whip goes slack, he lets go and it crumbles. */
	public static final int SPINE_AFTER = 14;
	/** The arc's half-angle in degrees (a 150 degree sweep in front of him). */
	public static final double SPINE_ARC = 75.0;
	/** How far out the whip reaches (blocks, flat). */
	public static final double SPINE_RANGE = 7.0;
	/** The height band it hits, from his chest down / up (blocks). */
	public static final double SPINE_BELOW = 2.0, SPINE_ABOVE = 1.5;
	public static final float SPINE_DAMAGE = 14f;
	/** The shove out from him, the lift, and the push along the swing (blocks a tick). */
	public static final double SPINE_KNOCK = 1.0;
	public static final double SPINE_LIFT = 0.25;
	public static final double SPINE_SWEEP_PUSH = 0.35;
	/** The tip snags the one creature nearest his crosshair inside this cone (degrees), at least this far out ... */
	public static final double SPINE_SNAG_CONE = 35.0;
	public static final double SPINE_SNAG_MIN = 2.5;
	/** ... and yanks it in to land about this far in front of him: speed per block of distance, capped, and a lift. */
	public static final double SPINE_YANK_TO = 1.8;
	public static final double SPINE_YANK_PER_BLOCK = 0.2;
	public static final double SPINE_YANK_MAX = 1.3;
	public static final double SPINE_YANK_LIFT = 0.38;
	public static final int SPINE_COOLDOWN = 140;
`],
]);
edit(D + 'UndeadKitNet.java', [
	['\t/** a = the ribs\' middle, b = which way they face. */\n\tpublic static final int RIB_BLOCK = 5;\n',
	 '\t/** v0.17.3 Spine Whip: a = where the tip snagged a creature, b = the whipper\'s feet, extra = the snagged entity\'s id. */\n\tpublic static final int SPINE_SNAG = 6;\n'],
]);
edit(D + 'UndeadKit.java', [
	['\t\tUndeadRibGuard.clearSessionState();\n', '\t\tUndeadSpineWhip.clearSessionState();\n'],
	['\t\tUndeadRibGuard.forget(id);\n', '\t\tUndeadSpineWhip.forget(id);\n'],
	['the twists, the charge and the guard end', 'the twists, the charge, the rib shot and the spine whip end'],
	['\t\tUndeadRibGuard.forTestsLower(p);\n', '\t\tUndeadSpineWhip.cancel(p);\n'],
	['\t\tUndeadRibGuard.tick(p);\n', '\t\tUndeadSpineWhip.tick(p);\n'],
]);
edit(D + 'UndeadBoneSword.java', [
	['{@link UndeadRibGuard} (Shift+V)', '{@link UndeadSpineWhip} (Shift+V)'],
	['\t\t\tcase UndeadRibGuard.ID -> UndeadKitConfig.RIB_COOLDOWN;\n', '\t\t\tcase UndeadSpineWhip.ID -> UndeadKitConfig.SPINE_COOLDOWN;\n'],
	[`	/** From {@code UndeadDamage.allowDamage}: false if the Rib Cage Guard (Shift+V) stops the hit ({@link UndeadRibGuard#blocks}). */
	public static boolean blocks(ServerPlayer p, DamageSource source, float amount) {
		return UndeadRibGuard.blocks(p, source, amount);
	}

`, ''],
]);
edit(D + 'UndeadRibShot.java', [
	[' * {@link UndeadKitConfig#RIB_SHOT_COOLDOWN} from the shot. Fired with the Rib Cage Guard up, the guard\'s ribs go too (it folds,\n * its cooldown starts).\n',
	 ' * {@link UndeadKitConfig#RIB_SHOT_COOLDOWN} from the shot.\n'],
	['\t\tif (UndeadRibGuard.up(p)) {\n\t\t\tUndeadRibGuard.forTestsLower(p); // the guard\'s ribs are the ones that fly\n\t\t}\n', ''],
	['\t\tif (s.boneSword()) {\n\t\t\treturn false;\n\t\t}\n\t\tint cd = Undead.cooldownRemaining(p, ID);',
	 '\t\tif (s.boneSword() || UndeadSpineWhip.active(p)) {\n\t\t\treturn false;\n\t\t}\n\t\tint cd = Undead.cooldownRemaining(p, ID);'],
]);
edit(D + 'UndeadAbilityManager.java', [
	['Shift+V tap: Rib Cage Guard (UndeadRibGuard: up 4 s, on the release)', 'Shift+V tap: Spine Whip (UndeadSpineWhip, on the release)'],
	['could not and said why)}. Let go while 0: the Rib Cage Guard;', 'could not and said why)}. Let go while 0: the Spine Whip;'],
	['\t\t\t\t\t\t\tUndeadRibGuard.raise(player); // a tap\n', '\t\t\t\t\t\t\tUndeadSpineWhip.start(player); // a tap\n'],
	['a Shift+V let go before REFORM_TAP raises the guard (it then stays up its 4 s -- the hold is Reform\'s\n\t\t\t\t// now), past it',
	 'a Shift+V let go before REFORM_TAP lashes the Spine Whip (it replaced the Rib Cage Guard), past it'],
	['\t\t\t// v0.17.3: tap V Rib Shotgun, Shift+V Rib Cage Guard (hold V,', '\t\t\t// v0.17.3: tap V Rib Shotgun, Shift+V Spine Whip (hold V,'],
	['new String[] { UndeadRibShot.ID, UndeadRibGuard.ID };', 'new String[] { UndeadRibShot.ID, UndeadSpineWhip.ID };'],
	['\t\t\tcase UndeadRibShot.ID -> UndeadKitConfig.RIB_SHOT_COOLDOWN;\n', '\t\t\tcase UndeadRibShot.ID -> UndeadKitConfig.RIB_SHOT_COOLDOWN;\n\t\t\tcase UndeadSpineWhip.ID -> UndeadKitConfig.SPINE_COOLDOWN;\n'],
]);
edit(D + 'UndeadDamage.java', [
	['\t\tif (UndeadBoneSword.blocks(player, source, amount)) {\n\t\t\treturn false; // v0.17.3 Shift+V: the Rib Cage Guard (a frontal melee hit is re-dealt at a quarter)\n\t\t}\n', ''],
]);
