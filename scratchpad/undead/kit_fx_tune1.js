// v0.17.3 Undead kit: first filming pass fixes (first-person clutter, sky band, meteor look).
const fs = require('fs');
function edit(f, fn) {
	let t = fs.readFileSync(f, 'utf8');
	const crlf = t.includes('\r\n');
	t = t.replace(/\r\n/g, '\n');
	t = fn(t);
	if (crlf) t = t.replace(/\n/g, '\r\n');
	fs.writeFileSync(f, t);
}
function rep(t, a, b) {
	if (!t.includes(a)) throw new Error('not found: ' + a.slice(0, 90));
	return t.replace(a, b);
}
edit('src/main/java/com/projecthero/mod/undead/UndeadMeteor.java', t => rep(t, `		// red unluck winding up round him, faster and wider as it builds
		for (int i = 0; i < 2 + (int) (k * 3); i++) {
			double a = age * (0.35 + k * 0.4) + i * Math.PI * 2 / 3;
			double r = 0.7 + 0.6 * k;
			double y = ((age * 0.09 + i * 0.33) % 1.0) * 2.4;
			level.sendParticles(UNLUCK, p.getX() + Math.cos(a) * r, p.getY() + y, p.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
		}
		if (age % 3 == 0) {
			level.sendParticles(UNLUCK_DARK, p.getX(), p.getY() + 2.4, p.getZ(), 1, 0.3, 0.1, 0.3, 0.0);
		}
`, `		// (the unluck winding up round him is the clients' own -- UndeadKitFx -- so it can stay out of his own first-person view)
`));
edit('src/client/java/com/projecthero/mod/client/undead/UndeadKitFx.java', t => {
	t = rep(t, `				BloodFx.spurt(fx.a(), fx.b().scale(-1).add(0, 0.5, 0), 10, 0.25, 0.5, Vec3.ZERO);`,
		`				if (!nearView(fx.a())) {
					BloodFx.spurt(fx.a(), fx.b().scale(-1).add(0, 0.5, 0), 10, 0.25, 0.5, Vec3.ZERO);
				}`);
	t = rep(t, `			if (s.animId == UndeadKitConfig.ANIM_VORTEX_TWIST) {
				long age = now - s.animStart;
				if (age > 2 && age % 2 == 0) {`, `			if (s.animId == UndeadKitConfig.ANIM_VORTEX_TWIST && !ownView(p)) {
				long age = now - s.animStart;
				if (age > 2 && age % 2 == 0) {`);
	t = rep(t, `				drawCoil(glow, buffers, m, p.getPosition(partial).subtract(cam), k, now);`,
		`				if (!ownView(p)) {
					drawCoil(glow, buffers, m, p.getPosition(partial).subtract(cam), k, now);
				}`);
	t = rep(t, `	private static Vec3 meteorAt(Meteor m, float age) {`, `	/** {@code p} is the local player seen from his own eyes (his body is not drawn: effects round it would fill the view). */
	private static boolean ownView(Player p) {
		Minecraft mc = Minecraft.getInstance();
		return p == mc.player && mc.options.getCameraType().isFirstPerson();
	}

	/** {@code at} is right in front of the camera (in first person). */
	private static boolean nearView(Vec3 at) {
		Minecraft mc = Minecraft.getInstance();
		return mc.options.getCameraType().isFirstPerson() && mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(at) < 2.5 * 2.5;
	}

	private static Vec3 meteorAt(Meteor m, float age) {`);
	// the embers round him: only for the others (the coil and embers would fill his own view)
	t = rep(t, `			float k = charge(p, 0f);
			if (k >= 0f) {
				// unluck embers rising round him`, `			float k = charge(p, 0f);
			if (k >= 0f && !ownView(p)) {
				// unluck embers rising round him`);
	// the dark sky: from the horizon up, fading in from it (no band over the distant ground), redder
	t = rep(t, `		float alpha = 0.62f * k;`, `		float alpha = 0.66f * k;`);
	t = rep(t, `			float p0 = Mth.lerp(j / (float) lat, -0.35f, Mth.HALF_PI), p1 = Mth.lerp((j + 1) / (float) lat, -0.35f, Mth.HALF_PI);`,
		`			float p0 = Mth.lerp(j / (float) lat, -0.02f, Mth.HALF_PI), p1 = Mth.lerp((j + 1) / (float) lat, -0.02f, Mth.HALF_PI);
			float f0 = j == 0 ? 0f : 1f; // the lowest ring fades in from nothing at the horizon`);
	t = rep(t, `				UndeadSwordFx.band(vc, m, v00, v10, v11, v01, 0.25f * lo + 0.04f, 0.0f, 0.02f, alpha, 0.25f * lo + 0.04f, 0.0f, 0.02f, alpha, 0.25f * hi + 0.04f,
						0.0f, 0.02f, alpha, 0.25f * hi + 0.04f, 0.0f, 0.02f, alpha);`,
		`				UndeadSwordFx.band(vc, m, v00, v10, v11, v01, 0.42f * lo + 0.06f, 0.0f, 0.03f, alpha * f0, 0.42f * lo + 0.06f, 0.0f, 0.03f, alpha * f0,
						0.42f * hi + 0.06f, 0.0f, 0.03f, alpha, 0.42f * hi + 0.06f, 0.0f, 0.03f, alpha);`);
	// a bigger rock, its crust glowing hotter; the tail red-orange rather than a yellow beam
	t = rep(t, `			cube(body, m, c.add(o.x, o.y, o.z), l[3], q, 0.16f, 0.12f, 0.11f, 1f);`, `			cube(body, m, c.add(o.x * 1.6f, o.y * 1.6f, o.z * 1.6f), l[3] * 1.6f, q, 0.16f, 0.12f, 0.11f, 1f);`);
	t = rep(t, `				cube(add, m, c.add(o.x, o.y, o.z), l[3] * 1.15f, q, 1f, 0.45f, 0.1f, 0.55f);`, `				cube(add, m, c.add(o.x * 1.6f, o.y * 1.6f, o.z * 1.6f), l[3] * 1.8f, q, 1f, 0.35f, 0.08f, 0.6f);`);
	t = rep(t, `					float gg0 = 0.85f - 0.75f * t0, gg1 = 0.85f - 0.75f * t1;`, `					float gg0 = 0.6f - 0.55f * t0, gg1 = 0.6f - 0.55f * t1;`);
	return t;
});
edit('src/client/java/com/projecthero/mod/client/UndeadKitHarness.java', t => {
	t = rep(t, `			scene(mc, t, idx++, CameraType.THIRD_PERSON_BACK, new Vec3(-7.5, 3.2, 7.0), new Vec3(0.0, 1.0, 11.0), 0f, p -> {`,
		`			scene(mc, t, idx++, CameraType.THIRD_PERSON_BACK, new Vec3(-5.0, 2.6, -3.0), new Vec3(0.5, 1.2, 9.0), 0f, p -> {`);
	t = rep(t, `			scene(mc, t, idx++, CameraType.THIRD_PERSON_BACK, new Vec3(-14.0, 7.5, -9.0), new Vec3(0.0, 2.5, 13.0), 22f, p -> {`,
		`			scene(mc, t, idx++, CameraType.THIRD_PERSON_BACK, new Vec3(-16.0, 6.0, -8.0), new Vec3(0.0, 8.0, 14.0), 4.6f, p -> {`);
	t = rep(t, `			scene(mc, t, idx++, CameraType.FIRST_PERSON, null, null, 22f, p -> {`, `			scene(mc, t, idx++, CameraType.FIRST_PERSON, null, null, 4.6f, p -> {`);
	// no chat in the shots
	t = rep(t, `			mc.gui.clear();`, `			mc.gui.clear();
			mc.gui.getChat().clearMessages(false);`);
	return t;
});
console.log('ok');
