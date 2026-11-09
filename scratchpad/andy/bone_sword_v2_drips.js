// v0.17.3: the Bone Sword's hanging blood (the runs off the edge, the guard's drops, the tip) hangs toward the ground whichever
// way the blade is held: drawn by drips() along world-down, each swelling into a drop that falls; mesh() keeps only the smears.
const fs = require('fs');
const f = 'src/client/java/com/projecthero/mod/client/andy/AndyBoneSwordRender.java';
let t = fs.readFileSync(f, 'utf8').replace(/\r\n/g, '\n');
function rep(a, b) {
  if (!t.includes(a)) throw new Error('missing: ' + a.slice(0, 90));
  t = t.replace(a, b);
}
rep(`	private static final float[][] DRIP_AT = { { 0f, 0.7f, TIP_Z + 0.78f }, { 0f, 3.6f, -4.2f }, { 0f, 2.9f, -9.8f }, { 0f, 2.95f, -15.4f },
			{ 0f, 6.8f, -2.05f }, { 0.67f, 3.0f, -1.8f } };`,
`	private static final float[][] DRIP_AT = { { 0f, 0.6f, TIP_Z + 0.78f, 0.4f }, { 0f, 1.88f, -4.2f, 1.6f }, { 0f, 1.63f, -9.8f, 1.1f },
			{ 0f, 1.375f, -15.4f, 1.4f }, { 0f, 5.5f, -2.05f, 1.2f }, { 0.67f, 1.7f, -1.8f, 1.2f } };`);
rep(`			float len = 0.25f + 1.9f * c * c;`, `			float len = a[3] + 1.9f * c * c; // the run's own length, then the drop swelling at its end`);
rep(`					Vector4f end = m.transform(new Vector4f((a[0] + d.x * 2.1f) / 16f, (a[1] + d.y * 2.1f) / 16f, (a[2] + d.z * 2.1f) / 16f, 1f));`,
`					float reach = a[3] + 1.9f;
					Vector4f end = m.transform(new Vector4f((a[0] + d.x * reach) / 16f, (a[1] + d.y * reach) / 16f, (a[2] + d.z * reach) / 16f, 1f));`);
rep(`			box(vc, p, -0.2f, edge, r[0] - 0.2f, 0.2f, edge + r[1], r[0] + 0.2f, BLOOD_WET, light);
			box(vc, p, -0.28f, edge + r[1] - 0.1f, r[0] - 0.28f, 0.28f, edge + r[1] + 0.4f, r[0] + 0.28f, BLOOD_DARK, light);`,
`			if (!wet) {
				// (held, the run hangs toward the ground and drips: drips())
				box(vc, p, -0.2f, edge, r[0] - 0.2f, 0.2f, edge + r[1], r[0] + 0.2f, BLOOD_WET, light);
				box(vc, p, -0.28f, edge + r[1] - 0.1f, r[0] - 0.28f, 0.28f, edge + r[1] + 0.4f, r[0] + 0.28f, BLOOD_DARK, light);
			}`);
rep(`		// drops hanging from the guard
		box(vc, p, -0.22f, 5.5f, -2.25f, 0.22f, 6.7f, -1.85f, BLOOD_WET, light);
		box(vc, p, -0.3f, 6.5f, -2.32f, 0.3f, 7.05f, -1.78f, BLOOD_DARK, light);
		box(vc, p, 0.5f, 1.7f, -2.0f, 0.85f, 2.9f, -1.6f, BLOOD_WET, light);
		box(vc, p, 0.45f, 2.75f, -2.05f, 0.9f, 3.2f, -1.55f, BLOOD_DARK, light);`,
`		if (!wet) {
			// drops hanging from the guard (held, they hang toward the ground and fall: drips())
			box(vc, p, -0.22f, 5.5f, -2.25f, 0.22f, 6.7f, -1.85f, BLOOD_WET, light);
			box(vc, p, -0.3f, 6.5f, -2.32f, 0.3f, 7.05f, -1.78f, BLOOD_DARK, light);
			box(vc, p, 0.5f, 1.7f, -2.0f, 0.85f, 2.9f, -1.6f, BLOOD_WET, light);
			box(vc, p, 0.45f, 2.75f, -2.05f, 0.9f, 3.2f, -1.55f, BLOOD_DARK, light);
		}`);
// the bead: thick at the root (a run), thinning toward the swelling drop
rep(`		float neck = c > 0.7f ? w * (1f - (c - 0.7f) / 0.3f * 0.6f) : w;
		orientedBox(vc, pose, x, y, z, d, a, b, 0f, len - w * 1.2f, neck * 0.7f, BLOOD_WET, light);`,
`		float neck = c > 0.7f ? w * (1f - (c - 0.7f) / 0.3f * 0.6f) : w;
		orientedBox(vc, pose, x, y, z, d, a, b, -0.2f, Math.min(len * 0.45f, 0.9f), 0.3f, BLOOD, light); // where it runs off the blade
		orientedBox(vc, pose, x, y, z, d, a, b, 0f, len - w * 1.2f, neck * 0.7f, BLOOD_WET, light);`);
// the stray debug line
t = t.replace(/\n\t\t\t\t\tSystem\.out\.println\("\[HARNESS\] drip[^\n]*\n/, '\n');
fs.writeFileSync(f, t.replace(/\n/g, '\r\n'));
console.log('ok');
