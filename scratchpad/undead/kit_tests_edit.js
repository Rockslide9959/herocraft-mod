// v0.17.3 Undead kit: trims UndeadGameTests of what the kit replaced (the Shift+V regen toggle, the old sword kit, the old Vortex).
const fs = require('fs');
const f = 'src/gametest/java/com/projecthero/mod/gametest/UndeadGameTests.java';
let t = fs.readFileSync(f, 'utf8');
const crlf = t.includes('\r\n');
t = t.replace(/\r\n/g, '\n');
function cut(startMarker, endMarker) {
	const a = t.indexOf(startMarker);
	const b = t.indexOf(endMarker, a + 1);
	if (a < 0 || b < 0) throw new Error('markers not found: ' + startMarker.slice(0, 70));
	t = t.slice(0, a) + t.slice(b);
}
function rep(a, b) {
	if (!t.includes(a)) throw new Error('not found: ' + a.slice(0, 90));
	t = t.replace(a, b);
}
// Shift+V is the Rib Cage Guard now (UndeadKitGameTests); the regen toggle / menu keys are the regen rework's
cut('\t@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)\n\tpublic void shiftVTogglesRegenAndHoldOpensTheMenu', '\t/** Auto-regrowth off: nothing grows on its own;');
// the old sword kit's test (the katana's skills are in UndeadKitGameTests)
cut('\t@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)\n\tpublic void theSwordKitMovesHit', '\t@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)\n\tpublic void theThrownSwordPins');
rep(`		helper.assertTrue(UndeadBoneSword.SLASH.equals(com.projecthero.mod.undead.UndeadAbilityManager.idsOf(p, com.projecthero.mod.hero.AbilitySlot.SLOT_1)[0])
				&& UndeadBoneSword.DASH.equals(com.projecthero.mod.undead.UndeadAbilityManager.idsOf(p, com.projecthero.mod.hero.AbilitySlot.SLOT_3)[0])
				&& UndeadBoneSword.PARRY.equals(com.projecthero.mod.undead.UndeadAbilityManager.idsOf(p, com.projecthero.mod.hero.AbilitySlot.SLOT_6)[0]),
				"the keys are the sword kit");`,
`		helper.assertTrue(UndeadBoneSword.K_CRESCENT.equals(com.projecthero.mod.undead.UndeadAbilityManager.idsOf(p, com.projecthero.mod.hero.AbilitySlot.SLOT_1)[0])
				&& UndeadBoneSword.K_WHIRL.equals(com.projecthero.mod.undead.UndeadAbilityManager.idsOf(p, com.projecthero.mod.hero.AbilitySlot.SLOT_6)[0])
				&& UndeadBoneSword.K_FLASH.equals(com.projecthero.mod.undead.UndeadAbilityManager.idsOf(p, com.projecthero.mod.hero.AbilitySlot.SLOT_4)[0]),
				"the keys are the katana's skills");`);
// the Vortex Bullet has a wind-up now (UndeadKitGameTests): the arm here is just thrown off
rep(`	public void vortexBulletLaunchesTheArmAndHeadBulletTheHead(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		int arm = Undead.mainArm(p);
		UndeadAbilities.vortexBullet(p);
		helper.assertTrue(Undead.missing(p, arm) && Undead.state(p).kind(arm) == UndeadState.KIND_LAUNCHED, "the arm is gone, as the projectile");
		AABB box = new AABB(p.blockPosition()).inflate(4);
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(UndeadPartEntity.class, box, e -> e.bodyPart() == arm).size() == 1, "the drill flies");
		edit(p, n -> n.abilityReadyAt.clear());`,
`	public void aOneArmMoveUsesTheOtherArmAndHeadBulletTheHead(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		int arm = Undead.mainArm(p);
		Undead.breakPart(p, arm, UndeadState.KIND_LAUNCHED);
		helper.assertTrue(Undead.missing(p, arm) && Undead.state(p).kind(arm) == UndeadState.KIND_LAUNCHED, "the arm is gone, thrown off");
		AABB box = new AABB(p.blockPosition()).inflate(4);`);
if (crlf) t = t.replace(/\n/g, '\r\n');
fs.writeFileSync(f, t);
console.log('ok');
