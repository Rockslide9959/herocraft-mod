// Offline contact sheets for the Titan Shifter assets (software renderer from lib_concept_models).
'use strict';
const path = require('path');
const L = require('./lib_concept_models.js');

function sample(clip, t) {
	const pose = {}; let root = [0, 0, 0];
	const ev = (ch) => {
		const ks = Object.keys(ch).map(Number).sort((a, b) => a - b);
		const val = k => { const v = ch[k.toFixed(3)] || ch[String(k)]; return Array.isArray(v) ? v : v.vector; };
		if (t <= ks[0]) return val(ks[0]);
		for (let i = 0; i < ks.length - 1; i++) if (t <= ks[i + 1]) {
			const a = val(ks[i]), b = val(ks[i + 1]), u = (t - ks[i]) / (ks[i + 1] - ks[i]);
			return a.map((x, j) => x + (b[j] - x) * u);
		}
		return val(ks[ks.length - 1]);
	};
	for (const [bn, chs] of Object.entries(clip.bones)) {
		if (chs.rotation) pose[bn] = ev(chs.rotation);
		if (bn === 'root' && chs.position) root = ev(chs.position);
	}
	return { pose, root };
}

const FORM_HIDE = c => c.corpseOnly || c.stump;

function view(M, tex) { const V = Object.create(M); V.tex = tex; return V; }

function masked(M, src, keep) {
	const out = Buffer.from(src);
	for (let i = 0; i < M.TW * M.TH; i++) if (!keep(i)) out[i * 4 + 3] = 0;
	return out;
}

function run(models, dir) {
	const ids = Object.keys(models);
	const item = (M, opts = {}) => {
		const s = opts.anim ? sample(M.anims['animation.titan.' + opts.anim], opts.t || 0) : { pose: {}, root: [0, 0, 0] };
		return { model: opts.tex ? view(M, opts.tex) : M, pose: s.pose, root: s.root, scale: M.k, pos: opts.pos || [0, 0, 0], yaw: opts.yaw || 0, hide: opts.hide || FORM_HIDE };
	};
	// 1. the four types
	const cams = [['front', 0, 6], ['three-quarter', 35, 8], ['side', 90, 4], ['back', 180, 6]];
	const panels = [];
	for (const id of ids) for (const [lab, yaw, pitch] of cams) panels.push({ label: `${models[id].P.name} ${lab}`, items: [item(models[id])], cam: { yaw, pitch } });
	for (let k = 0; k < ids.length; k += 1) {
		L.sheet(path.join(dir, `types_${ids[k]}.png`), `${models[ids[k]].P.name} Titan`, `${models[ids[k]].P.heightBlocks} blocks`, panels.slice(k * 4, k * 4 + 4), { panelH: 900, minW: 1600 });
	}
	L.sheet(path.join(dir, 'types_all.png'), 'Titan Shifters', 'Attack 15 / Female 12 / Armoured 15 / Beast 14', [{
		label: 'line-up', cam: { yaw: 20, pitch: 6 },
		items: ids.map((id, i) => Object.assign(item(models[id]), { pos: [(i - 1.5) * 9, 0, 0] })),
	}], { panelH: 1000, minW: 2000 });
	// 2. clips
	const clips = (process.env.CLIPS || 'idle:0,idle:1.6,walk:0,walk:0.3,run:0,run:0.2,punch:0.3,kick:0.35,heavy_punch:0.45,smash:1.0,stomp:0.3,leap:0.3,landing:0.15,roar:0.6,regeneration:1.0,hardening:0.6,grab:0.25,bite:0.35,throw:0.35,throw:0.6,charge:0,scream:0.8,roundhouse:0.4,nape_guard:0.5,death:3.0,corpse_kneel:1.15,corpse_kneel:3.0').split(',');
	for (const id of ids) {
		const M = models[id];
		const pn = clips.map(s => { const [a, t] = s.split(':'); return { label: `${a} ${t}`, items: [item(M, { anim: a, t: +t, hide: a === 'corpse_kneel' ? (c => c.stump) : FORM_HIDE, tex: a === 'corpse_kneel' ? M.corpseTex : null })], cam: { yaw: +(process.env.YAW || 35), pitch: 6 } }; });
		for (let k = 0; k < pn.length; k += 9) L.sheet(path.join(dir, `clips_${id}_${k / 9}.png`), `${M.P.name} clips`, '', pn.slice(k, k + 9), { panelH: 640, minW: 2400, maxW: 3600 });
	}
	// 3. build + dissolve
	for (const id of ids) {
		const M = models[id], T = M.T;
		const bp = [0.06, 0.15, 0.25, 0.36, 0.45, 0.55, 0.66, 0.75, 0.85, 1.0].map(p => ({
			label: `build ${Math.round(p * 100)}%`, cam: { yaw: 30, pitch: 6 },
			items: [item(M, { anim: 'transformation', t: p * 4.5, tex: p >= 1 ? M.tex : masked(M, M.tex, i => T.build[i] > 0 && T.build[i] <= p) })],
		}));
		L.sheet(path.join(dir, `build_${id}.png`), `${M.P.name} transformation`, 'skeleton - muscle - skin', bp, { panelH: 600, minW: 2400, maxW: 3600 });
		const dp = [0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9].map(p => ({
			label: `dissolve ${Math.round(p * 100)}%`, cam: { yaw: 150, pitch: 14 },
			items: [item(M, { anim: 'corpse_kneel', t: 3.0, hide: c => c.stump, tex: masked(M, M.corpseTex, i => T.diss[i] > p) })],
		}));
		L.sheet(path.join(dir, `dissolve_${id}.png`), `${M.P.name} corpse`, 'skin - muscle - skeleton', dp, { panelH: 600, minW: 2400, maxW: 3600 });
	}
}

module.exports = { run, sample };
