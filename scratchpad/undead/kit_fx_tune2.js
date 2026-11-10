// v0.17.3 Undead kit: second filming pass fixes (shoulder sword place, katana blade depth / drops, harness cameras).
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
edit('src/client/java/com/projecthero/mod/client/undead/UndeadBoneSwordRender.java', t => {
	// the shoulder sword: out over the joint of the arm, lower, the hilt leaning away from the head
	t = rep(t, `	static float SHOULDER_X = 3.4f, SHOULDER_Y = -0.6f, SHOULDER_Z = 0.4f, SHOULDER_ROT_Z = -150f, SHOULDER_ROT_Y = 90f, SHOULDER_TILT = 18f,
			SHOULDER_SINK = 0.18f, SHOULDER_SCALE = 0.85f;`, `	static float SHOULDER_X = 4.6f, SHOULDER_Y = -3.5f, SHOULDER_Z = 0.4f, SHOULDER_ROT_Z = 60f, SHOULDER_ROT_Y = 90f, SHOULDER_TILT = 10f,
			SHOULDER_SINK = 0f, SHOULDER_SCALE = 0.85f;`);
	// a deeper blade (it read as a wire at a distance)
	t = rep(t, `			float spine = y - 0.72f + 0.12f * um, edge = y + 0.72f - 0.16f * um;`, `			float spine = y - 0.9f + 0.14f * um, edge = y + 0.9f - 0.2f * um;`);
	// the drops: smaller beads
	t = rep(t, `			float w = 0.17f + 0.13f * c;`, `			float w = 0.11f + 0.09f * c;`);
	t = rep(t, `					BloodFx.bladeDrip(at, player.getDeltaMovement(), 0.055f + 0.015f * (i % 3));`,
		`					BloodFx.bladeDrip(at, player.getDeltaMovement(), 0.035f + 0.01f * (i % 3));`);
	return t;
});
edit('src/client/java/com/projecthero/mod/client/UndeadKitHarness.java', t => {
	t = rep(t, `		p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, p.getBoundingBox().inflate(128),
				e -> e instanceof Arrow`, `		p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, p.getBoundingBox().inflate(128),
				e -> e instanceof net.minecraft.world.entity.monster.Slime || e instanceof Arrow`);
	t = rep(t, `fp ? null : new Vec3(-4.2, 2.2, 3.4),
						fp ? null : new Vec3(0.0, 1.2, 1.2)`, `fp ? null : new Vec3(-2.6, 1.9, 2.4),
						fp ? null : new Vec3(0.0, 1.1, 0.9)`);
	t = rep(t, `whirl ? new Vec3(-4.5, 3.0, -3.5) : new Vec3(-7.0, 3.4, 1.5),
						whirl ? new Vec3(0, 1.0, 0.3) : new Vec3(0, 1.2, 7.0)`, `whirl ? new Vec3(-4.5, 3.0, -3.5) : new Vec3(-5.5, 2.8, -2.5),
						whirl ? new Vec3(0, 1.0, 0.3) : new Vec3(0.3, 1.2, 6.0)`);
	t = rep(t, `			at(t + 60, () -> orbit = new double[] { base.x, base.y + 1.45, base.z, 1.5, 0.3, inWorldTicks - t0, 4.5, 20 });`,
		`			at(t + 60, () -> orbit = new double[] { base.x, base.y + 1.5, base.z, 1.7, 0.25, inWorldTicks - t0, 4.5, 20 });`);
	return t;
});
console.log('ok');
