// v0.17.3 Undead kit: rewrites the Bone Sword renderer into the bone katana (mesh, poses, first person, shoulder sword).
// Run from the repo root. Reads the snippets from scratchpad/undead/render_parts/*.java.txt.
const fs = require('fs');
const f = 'src/client/java/com/projecthero/mod/client/undead/UndeadBoneSwordRender.java';
let t = fs.readFileSync(f, 'utf8');
const crlf = t.includes('\r\n');
t = t.replace(/\r\n/g, '\n');
const part = n => fs.readFileSync('scratchpad/undead/render_parts/' + n + '.java.txt', 'utf8').replace(/\r\n/g, '\n');
function between(startMarker, endMarker, replacement) {
	const a = t.indexOf(startMarker);
	const b = t.indexOf(endMarker, a + 1);
	if (a < 0 || b < 0) throw new Error('markers not found: ' + startMarker.slice(0, 60) + ' / ' + endMarker.slice(0, 60));
	t = t.slice(0, a) + replacement + t.slice(b);
}
function rep(a, b) {
	if (!t.includes(a)) throw new Error('not found: ' + a.slice(0, 90));
	t = t.replace(a, b);
}
// the class javadoc
between('/**\n * v0.17.3: the Bone Sword', 'public final class UndeadBoneSwordRender {', part('doc'));
rep('\tstatic final float TIP_Z = -24.2f;', '\tstatic final float TIP_Z = -29.4f;');
rep('\tstatic final float GRIP_X = 0.24f, GRIP_Y = 0.24f, HELD_SCALE = 0.78f;', '\tstatic final float GRIP_X = 0.24f, GRIP_Y = 0.24f, HELD_SCALE = 0.72f;');
// the poses: from the "held: up off the hip" switch to the end of pose()
between('\t\t// held: up off the hip like a held item (added, so vanilla\'s attack swing stays)', '\t/** Tap V, Reform:', part('pose_held'));
// the not-held poses: add the kit's before "return false" of the !boneSword branch
rep(`			if (s.animId == UndeadState.ANIM_SWORD_AWAY && age >= 0f && age < 8f) {
				set(arm, fade(age, 8f), -1.2f, 0.2f * sd, -0.1f * sd); // the hand opening as it bursts
				return true;
			}
			return false;`, `			if (s.animId == UndeadState.ANIM_SWORD_AWAY && age >= 0f && age < 8f) {
				set(arm, fade(age, 8f), -1.2f, 0.2f * sd, -0.1f * sd); // the hand opening as it bursts
				return true;
			}
			return kitPose(player, s, model, now, age, arm, other, sd, aim);`);
rep('\t/** Tap V, Reform:', part('kit_pose') + '\t/** Tap V, Reform:');
// third person: the shoulder sword instead of the old ribs
between('\tstatic void thirdPerson(PoseStack pose, MultiBufferSource buffers, int light, Player player, PlayerModel<?> model, UndeadState s, float now) {',
		'\t/**\n\t * The thing in the sword hand at sword space', part('third_person'));
// first person: the Ten Twists arms and the shoulder sword's hilt when the katana is not out
rep(`		if (!s.boneSword()) {
			if (s.animId == UndeadState.ANIM_SWORD_STRUGGLE && age >= 0f && age < UndeadConfig.BONE_SWORD_HOLD + 4f) {
				if (hand == InteractionHand.MAIN_HAND) {
					struggleFirstPerson(renderer, player, age, pose, buffers, light);
				}
				return true;
			}
			return false;
		}`, `		if (!s.boneSword()) {
			if (s.animId == UndeadState.ANIM_SWORD_STRUGGLE && age >= 0f && age < UndeadConfig.BONE_SWORD_HOLD + 4f) {
				if (hand == InteractionHand.MAIN_HAND) {
					struggleFirstPerson(renderer, player, age, pose, buffers, light);
				}
				return true;
			}
			if (s.animId == UndeadKitConfig.ANIM_VORTEX_TWIST && age >= 0f && age < UndeadKitConfig.VORTEX_WINDUP + 1f) {
				if (hand == InteractionHand.MAIN_HAND) {
					twistFirstPerson(player, s, age, pose, buffers, light);
				}
				return true;
			}
			if (hand == InteractionHand.OFF_HAND) {
				shoulderFirstPerson(player, partial, pose, buffers, light);
			}
			return false;
		}`);
rep('\tprivate static final float STRUGGLE_OFF_SHIFT', part('fp_extra') + '\tprivate static final float STRUGGLE_OFF_SHIFT');
// the first-person move offsets
between('\t/** First person: each sword move\'s swing on top of the held pose', '\tprivate static float ease(float t) {', part('move_offsets'));
// drips: on the katana's edge and tip
rep(`	private static final float[][] DRIP_AT = { { 0f, 0.6f, TIP_Z + 0.78f, 0.4f }, { 0f, 1.88f, -4.2f, 1.6f }, { 0f, 1.63f, -9.8f, 1.1f },
			{ 0f, 1.375f, -15.4f, 1.4f }, { 0f, 5.5f, -2.05f, 1.2f }, { 0.67f, 1.7f, -1.8f, 1.2f } };
	private static final int[] DRIP_EVERY = { 47, 63, 55, 71, 52, 79 };`,
`	private static final float[][] DRIP_AT = { { 0f, -0.9f, TIP_Z + 1.2f, 0.5f }, { 0f, 0.62f, -8.5f, 1.2f }, { 0f, 0.45f, -16.5f, 0.9f },
			{ 0f, 0.1f, -23.0f, 1.1f }, { 0f, 2.3f, -1.8f, 1.0f } };
	private static final int[] DRIP_EVERY = { 47, 63, 55, 71, 79 };`);
// the mesh and the old ribs
between('\t/** The whole sword in sword space', '\t/** A shard of the Splinter Volley', part('mesh'));
rep('import com.projecthero.mod.undead.UndeadConfig;\n', 'import com.projecthero.mod.undead.UndeadConfig;\nimport com.projecthero.mod.undead.UndeadKitConfig;\nimport com.projecthero.mod.undead.UndeadShoulderSword;\n');
if (crlf) t = t.replace(/\n/g, '\r\n');
fs.writeFileSync(f, t);
console.log('ok');
