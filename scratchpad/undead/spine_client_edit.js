// v0.17.3 Spine Whip: client edits (Rib Cage Guard drawing out, Spine Whip + model-layer rib cage in). CRLF-safe. Run from the repo root.
const fs = require('fs');
const { edit } = require('./edit_lib.js');
const D = 'src/client/java/com/projecthero/mod/client/undead/';
// UndeadKitFx: drop the guard's ribs; feed UndeadSpineWhipFx
{
	const f = D + 'UndeadKitFx.java';
	let t = fs.readFileSync(f, 'utf8').replace(/\r\n/g, '\n');
	const a = t.indexOf('\t/**\n\t * Rib Cage Guard: his ribs sprung out of his chest in front of him');
	const b = t.indexOf('\t\tpose.popPose();\n\t}\n', a);
	if (a < 0 || b < 0) throw new Error('drawRibs not found');
	t = t.slice(0, a).replace(/\n+$/, '\n') + t.slice(b + '\t\tpose.popPose();\n\t}\n'.length);
	fs.writeFileSync(f, t.replace(/\n/g, '\r\n'));
	edit(f, [
		[' * v0.17.3 Undead kit: the client side of Z, Shift+Z, Shift+V and the shoulder sword, for every viewer.',
		 ' * v0.17.3 Undead kit: the client side of Z, Shift+Z and the shoulder sword, for every viewer (V and Shift+V: {@link UndeadRibShotFx},\n * {@link UndeadSpineWhipFx}, fed from here).'],
		[' * <li>Rib Cage Guard (Shift+V): his ribs sprung out in front of him as a cage of bone, blood at their roots; a flash where\n * something hits them.</li>\n', ''],
		['\t/** player -> {when the ribs went up, when they started folding back (0 while up)}. */\n\tprivate static final Map<UUID, long[]> RIBS = new HashMap<>();\n', ''],
		['\t\tRIBS.clear();\n', ''],
		['\t\tUndeadRibShotFx.reset(); // v0.17.3 tap V\n', '\t\tUndeadRibShotFx.reset(); // v0.17.3 tap V\n\t\tUndeadSpineWhipFx.reset(); // v0.17.3 tap Shift+V\n'],
		['\t\t\tcase UndeadKitNet.VORTEX_BURST, UndeadKitNet.RIB_BLOCK -> BURSTS.add(new Burst(fx.kind(), fx.a(), fx.b(), now));\n',
		 '\t\t\tcase UndeadKitNet.VORTEX_BURST -> BURSTS.add(new Burst(fx.kind(), fx.a(), fx.b(), now));\n\t\t\tcase UndeadKitNet.SPINE_SNAG -> UndeadSpineWhipFx.onSnag(fx); // v0.17.3 tap Shift+V\n'],
		['\t\tUndeadRibShotFx.watch(p, s, now); // v0.17.3 tap V: Rib Shotgun\n', '\t\tUndeadRibShotFx.watch(p, s, now); // v0.17.3 tap V: Rib Shotgun\n\t\tUndeadSpineWhipFx.watch(p, s, now); // v0.17.3 tap Shift+V: Spine Whip\n'],
		[`		long[] r = RIBS.get(id);
		if (s.flag(UndeadKitConfig.FLAG_RIB_GUARD)) {
			if (r == null || r[1] != 0L) {
				RIBS.put(id, new long[] { now, 0L });
			}
		} else if (r != null && r[1] == 0L) {
			r[1] = now;
		} else if (r != null && now - r[1] > 8) {
			RIBS.remove(id);
		}
`, ''],
		[`	/** Rib Cage Guard: how far out the ribs are (0..1), 0 when not up. */
	public static float ribs(Player p, float partial) {
		long[] r = RIBS.get(p.getUUID());
		if (r == null) {
			return 0f;
		}
		float t = now() + partial;
		float out = Mth.clamp((t - r[0]) / 4f, 0f, 1f);
		out = out * out * (3f - 2f * out);
		if (r[1] != 0L) {
			out = Math.min(out, Mth.clamp(1f - (t - r[1]) / 5f, 0f, 1f));
		}
		return out;
	}

`, ''],
		['\t\tUndeadRibShotFx.tick(now); // v0.17.3 tap V: Rib Shotgun\n', '\t\tUndeadRibShotFx.tick(now); // v0.17.3 tap V: Rib Shotgun\n\t\tUndeadSpineWhipFx.tick(now); // v0.17.3 tap Shift+V: Spine Whip\n'],
		[`			// (v0.17.3 tap V: the Rib Shotgun's wind-up springs the same ribs half out before they fly)
			float ribs = Math.max(ribs(p, partial), UndeadRibShotFx.opening(p, partial));
			if (ribs > 0.01f) {
				solid.add(() -> drawRibs(pose, buffers, p, partial, cam, ribs, now));
			}
`, ''],
		['\t\t\t\tcase UndeadKitNet.RIB_BLOCK -> UndeadSwordFx.star(glow, buffers, m, context.camera().rotation(), b.at().subtract(cam), age * 1.4f, 0.7f);\n', ''],
		['\t\tfor (Runnable r : solid) {\n\t\t\tr.run();\n\t\t}\n', '\t\tfor (Runnable r : solid) {\n\t\t\tr.run();\n\t\t}\n\t\tUndeadSpineWhipFx.render(pose, buffers, cam, partial, now); // v0.17.3 tap Shift+V: the whip\n'],
	]);
}
// UndeadBoneSwordRender: the guard's pose out; the whip's poses, first person and the split in his back in
edit(D + 'UndeadBoneSwordRender.java', [
	[`		float ribs = UndeadKitFx.ribs(player, partial);
		if (ribs > 0.01f) {
			// both hands at his chest, hauling the ribs apart
			set(model.rightArm, ribs, -1.15f, -0.55f, 0.35f);
			set(model.leftArm, ribs, -1.15f, 0.55f, -0.35f);
			model.body.xRot -= 0.1f * ribs;
			return true;
		}
`, ''],
	['the Unluck Meteor (both arms raised to the sky, trembling as it builds; then one thrown\n\t * down at the target), the Rib Cage Guard (both hands hauling the ribs open), the shoulder sword going in and out.',
	 'the Unluck Meteor (both arms raised to the sky, trembling as it builds; then one thrown\n\t * down at the target), the Spine Whip ({@link UndeadSpineWhipFx#pose}), the shoulder sword going in and out.'],
	['\t\tif (ribShotPose(s, model, now, age)) {\n\t\t\treturn true; // v0.17.3 tap V (Rib Shotgun) / Shift + hold V (gathering for Reform)\n\t\t}\n',
	 '\t\tif (ribShotPose(s, model, now, age)) {\n\t\t\treturn true; // v0.17.3 tap V (Rib Shotgun) / Shift + hold V (gathering for Reform)\n\t\t}\n\t\tif (UndeadSpineWhipFx.pose(player, s, model, age)) {\n\t\t\treturn true; // v0.17.3 tap Shift+V (Spine Whip)\n\t\t}\n'],
	['\t/** In his hand (from {@link UndeadRender#renderLayer}, the pose at the model), plus the Rib Cage Guard. */',
	 '\t/** In his hand (from {@link UndeadRender#renderLayer}, the pose at the model), plus the Rib Shotgun\'s and Spine Whip\'s wounds. */'],
	['\t\tUndeadRibShotFx.wound(pose, buffers, light, player, model, s, now); // v0.17.3 tap V: the hole the Rib Shotgun tore\n',
	 '\t\tUndeadRibShotFx.wound(pose, buffers, light, player, model, s, now); // v0.17.3 tap V: the hole the Rib Shotgun tore\n\t\tUndeadSpineWhipFx.backWound(pose, buffers, light, player, model, s, now); // v0.17.3 tap Shift+V: the split in his back\n'],
	[`				return true;
			}
			float draw = shoulderDraw(player, partial);`, `				return true;
			}
			if (UndeadSpineWhipFx.firstPerson(player, s, age, hand == InteractionHand.MAIN_HAND ? pose : null, buffers, light)) {
				return true; // v0.17.3 tap Shift+V: the whip hand (drawn on the main hand's call)
			}
			float draw = shoulderDraw(player, partial);`],
	['\tprivate static void fpArm(AbstractClientPlayer player, PoseStack pose,', '\tstatic void fpArm(AbstractClientPlayer player, PoseStack pose,'],
	['the v0.17.3 kit\'s poses (Ten Twists,\n * the Unluck Meteor, the Rib Cage Guard)', 'the v0.17.3 kit\'s poses (Ten Twists,\n * the Unluck Meteor, the Rib Shotgun)'],
]);
