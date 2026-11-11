// v0.17.3 rib cage + Spine Whip, tune 1 (from the first films). CRLF-safe. Run from the repo root.
const { edit } = require('./edit_lib.js');
const D = 'src/client/java/com/projecthero/mod/client/undead/';
// ribs: longer, spread out in front instead of curling down like fingers; less (and darker) blood at the break; a dimmer backbone
edit(D + 'UndeadRibShotFx.java', [
	['float len = (4.6f + 2.8f * mid) * (0.45f + 0.55f * Math.min(1f, k));', 'float len = (5.4f + 3.2f * mid) * (0.45f + 0.55f * Math.min(1f, k));'],
	['float phi0 = Mth.lerp(k, -60f, 52f - 6f * (1f - mid)), curl = 78f;', 'float phi0 = Mth.lerp(k, -60f, 64f - 8f * (1f - mid)), curl = 62f;'],
	['float dy = 0.16f + 0.3f * u * k; // sloping down, drooping a little more toward the end', 'float dy = 0.07f + 0.1f * u * k; // sloping down a little, a little more toward the end'],
	['UndeadBones.rib(vc, pose, cageRib(side, i, k), UP, 0.95f, 0.6f, 0.5f, 0.36f, 0.72f, false, light);', 'UndeadBones.rib(vc, pose, cageRib(side, i, k), UP, 0.95f, 0.6f, 0.5f, 0.36f, 0.84f, false, light);'],
	['float hw = 0.5f * size;\n\t\t\t\tUndeadBoneSwordRender.box(vc, pp, -hw, y, -2.34f, hw, y + 0.72f, -2.31f, 0x8A7A62, light);\n\t\t\t\tUndeadBoneSwordRender.box(vc, pp, -hw * 1.6f, y + 0.18f, -2.335f, hw * 1.6f, y + 0.5f, -2.31f, 0x6E604C, light);',
	 'float hw = 0.36f * size;\n\t\t\t\tUndeadBoneSwordRender.box(vc, pp, -hw, y, -2.34f, hw, y + 0.66f, -2.31f, 0x5A483A, light);\n\t\t\t\tUndeadBoneSwordRender.box(vc, pp, -hw * 1.7f, y + 0.2f, -2.335f, hw * 1.7f, y + 0.46f, -2.31f, 0x463428, light);'],
	// the blast: a smaller eruption and fewer bone bits (the user finds big blood annoying)
	['BloodFx.eruption(chest.add(fwd.scale(0.12)), look, carry, 0.55f);\n\t\t\t\t\tUndeadSwordFx.bone(chest.add(fwd.scale(0.15)), look.scale(0.22), 9, 0.1);',
	 'BloodFx.eruption(chest.add(fwd.scale(0.12)), look, carry, 0.35f);\n\t\t\t\t\tUndeadSwordFx.bone(chest.add(fwd.scale(0.15)), look.scale(0.22), 5, 0.1);'],
]);
edit(D + 'UndeadBones.java', [
	['col = mix(col, UndeadBoneSwordRender.BLOOD_WET, 0.25f + 0.6f * k);\n\t\t\t\thi = mix(hi, UndeadBoneSwordRender.BLOOD_GLOSS, 0.2f + 0.55f * k);\n\t\t\t\tlo = mix(lo, UndeadBoneSwordRender.BLOOD, 0.35f + 0.6f * k);',
	 'col = mix(col, UndeadBoneSwordRender.BLOOD, 0.2f + 0.6f * k);\n\t\t\t\thi = mix(hi, UndeadBoneSwordRender.BLOOD_WET, 0.15f + 0.6f * k);\n\t\t\t\tlo = mix(lo, UndeadBoneSwordRender.BLOOD_DARK, 0.3f + 0.6f * k);'],
]);
// the wind-up pose: the hands dig in, then haul the chest open wide (arms out to the sides, clear of the ribs)
edit(D + 'UndeadBoneSwordRender.java', [
	['set(model.rightArm, grab, Mth.lerp(pull, -1.3f, -1.05f) + tremble, Mth.lerp(pull, -0.72f, 0.3f), Mth.lerp(pull, 0f, 0.45f));\n\t\t\t\tset(model.leftArm, grab, Mth.lerp(pull, -1.3f, -1.05f) - tremble, Mth.lerp(pull, 0.72f, -0.3f), Mth.lerp(pull, 0f, -0.45f));',
	 'set(model.rightArm, grab, Mth.lerp(pull, -1.3f, -0.75f) + tremble, Mth.lerp(pull, -0.72f, 0.55f), Mth.lerp(pull, 0f, 0.7f));\n\t\t\t\tset(model.leftArm, grab, Mth.lerp(pull, -1.3f, -0.75f) - tremble, Mth.lerp(pull, 0.72f, -0.55f), Mth.lerp(pull, 0f, -0.7f));'],
]);
// the whip: chunkier; the snag blends in from the sweep; less blood and bone dust; first person: the arm drops out of view to reach back
edit(D + 'UndeadSpineWhipFx.java', [
	['float sc = Mth.lerp(u, 1.0f, 0.45f);', 'float sc = Mth.lerp(u, 1.45f, 0.65f);'],
	[`		if (caught != null && caught.isAlive() && now() - sn.at() < 12) {
			// pulled taut to what it caught as it comes in, a little slack in the middle
			Vec3 to = caught.getPosition(partial).add(0, caught.getBbHeight() * 0.55, 0);
			float slack = 0.12f + 0.25f * Mth.clamp((now() - sn.at() + partial) / 8f, 0f, 1f);
			for (int i = 1; i <= SEGS; i++) {
				double u = i / (double) SEGS;
				pts[i] = h.lerp(to, u).add(0, -slack * Math.sin(u * Math.PI) - 0.6 * ta * u, 0);
			}
			return pts;
		}
		float tl`, `		Vec3[] taut = null;
		float pull = 0f;
		if (caught != null && caught.isAlive() && now() - sn.at() < 12) {
			// pulled taut to what it caught as it comes in, a little slack in the middle (blended in from the swing)
			Vec3 to = caught.getPosition(partial).add(0, caught.getBbHeight() * 0.55, 0);
			float since = now() - sn.at() + partial;
			float slack = 0.12f + 0.25f * Mth.clamp(since / 8f, 0f, 1f);
			pull = ease(since / 2f);
			taut = new Vec3[SEGS + 1];
			for (int i = 1; i <= SEGS; i++) {
				double u = i / (double) SEGS;
				taut[i] = h.lerp(to, u).add(0, -slack * Math.sin(u * Math.PI) - 0.6 * ta * u, 0);
			}
		}
		float tl`],
	[`			pts[i] = next;
		}
		return pts;
	}`, `			pts[i] = next;
		}
		if (taut != null) {
			for (int i = 1; i <= SEGS; i++) {
				pts[i] = pts[i].lerp(taut[i], pull);
			}
		}
		return pts;
	}`],
	['BloodFx.spurt(back.add(0, 0.25 - age * 0.04, 0), fr[0].scale(-1).add(0, 0.5, 0), 1, 0.1, 0.35, carry);',
	 'BloodFx.spurt(back.add(0, 0.25 - age * 0.04, 0), fr[0].scale(-1).add(0, 0.8, 0), 1, 0.06, 0.3, carry);'],
	['BloodFx.spurt(back.add(0, -0.15, 0), fr[0].scale(-1).add(0, 0.3, 0), 6, 0.22, 0.4, carry);\n\t\t\t\tUndeadSwordFx.bone(back, fr[0].scale(-0.1), 4, 0.07);',
	 'BloodFx.spurt(back.add(0, -0.15, 0), fr[0].scale(-1).add(0, 0.5, 0), 3, 0.15, 0.35, carry);\n\t\t\t\tUndeadSwordFx.bone(back, fr[0].scale(-0.1), 2, 0.07);'],
	['for (int i = count[0]; i < before[0] && i < SEGS; i++) {\n\t\t\t\t\tUndeadSwordFx.bone(pts[i + 1], new Vec3(0, 0.03, 0), own ? 1 : 3, 0.06);',
	 'for (int i = count[0]; i < before[0] && i < SEGS; i++) {\n\t\t\t\t\tif ((i & 1) == 0) {\n\t\t\t\t\t\tUndeadSwordFx.bone(pts[i + 1], new Vec3(0, 0.03, 0), 1, 0.05);\n\t\t\t\t\t}'],
	[`			// up out of the top of the view (over his shoulder), then back down over it holding the spine
			x = sdArm * 0.05f * gone;
			y = 0.9f * gone;
			z = 0.1f * gone;
			yaw = 0f;
			pitch = 70f * gone;
			roll = 0f;`, `			// down out of the bottom of the view (reaching back over his shoulder, where the camera can not see), then back up
			x = sdArm * 0.1f * gone;
			y = -0.75f * gone;
			z = 0.1f * gone;
			yaw = 0f;
			pitch = -20f * gone;
			roll = 0f;`],
	['y = 0.02f + 0.5f * in - 0.55f * ease(ta / 0.6f);', 'y = 0.02f - 0.4f * in - 0.55f * ease(ta / 0.6f);'],
]);
