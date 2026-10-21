// Lists the most common opaque colours of each Iron Man armour skin (to pick icon palettes from).
const k = require('./pngkit.js');
const dir = __dirname + '/../src/main/resources/assets/projecthero/textures/armor/';
for (const id of ['mark_1', 'mark_1_v2', 'mark_iii', 'mark_4', 'mark_v', 'mark_2', 'mark_6', 'mark_vii', 'mark_8']) {
	const im = k.decode(dir + id + '.png'); const m = new Map();
	for (let i = 0; i < im.px.length; i += 4) {
		if (im.px[i + 3] < 200) continue;
		const q = [0, 1, 2].map(c => (im.px[i + c] >> 3) << 3); const key = q.map(v => v.toString(16).padStart(2, '0')).join('');
		m.set(key, (m.get(key) || 0) + 1);
	}
	console.log(id, im.w + 'x' + im.h, [...m.entries()].sort((a, b) => b[1] - a[1]).slice(0, 14).map(e => e[0] + ':' + e[1]).join(' '));
}
