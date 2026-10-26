// Renders the Kwami models as they are in the game (geo.json + png) side by side: node render_current.js [ids] [yaw] [out]
'use strict';
const path = require('path');
const L = require('../viperion/lib_concept_models.js');
const { loadGeo } = require('./geo_loader.js');
const A = path.join(__dirname, '..', '..', 'src', 'main', 'resources', 'assets', 'projecthero');
const ids = process.argv[2] ? process.argv[2].split(',') : ['sass', 'tikki', 'plagg', 'trixx', 'nooroo', 'kaalki', 'wayzz'];
const yaw = +(process.argv[3] || 25);
const panels = ids.map(id => {
	const M = loadGeo(path.join(A, 'geo', 'miraculous', id + '.geo.json'), path.join(A, 'textures', 'entity', 'miraculous', id + '.png'));
	const b = M.bounds(M.world({}));
	console.log(id, 'lo', b.lo.map(v => v.toFixed(2)).join(','), 'hi', b.hi.map(v => v.toFixed(2)).join(','));
	return { label: id, items: [{ model: M, pose: {} }], cam: { yaw, pitch: 8 }, floor: [{ x: 0, z: 0, r: 0.4 }] };
});
L.sheet(path.join(__dirname, (process.argv[4] || 'current') + '.png'), 'KWAMIS', 'IN GAME NOW', panels, { panelH: 600, minW: 1800, bg: { top: [150, 160, 170], bottom: [60, 66, 74] } });
