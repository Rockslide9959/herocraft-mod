// TEMP: swap NbcHarness.script for the Andy's Suit lost-pad / missing-part check.
const fs = require('fs');
const f = 'src/client/java/com/projecthero/mod/client/NbcHarness.java';
let t = fs.readFileSync(f, 'utf8').replace(/\r\n/g, '\n');
const a = t.indexOf('\tprivate static void script(Minecraft mc) {');
const b = t.lastIndexOf('}');
if (a < 0) throw new Error('script');
t = t.slice(0, a) + `\tprivate static void suit(ServerPlayer p) {
\t\tp.setItemSlot(EquipmentSlot.CHEST, new ItemStack(com.projecthero.mod.andy.item.AndyItems.SUIT_CHESTPLATE));
\t\tp.setItemSlot(EquipmentSlot.LEGS, new ItemStack(com.projecthero.mod.andy.item.AndyItems.SUIT_LEGGINGS));
\t\tp.setItemSlot(EquipmentSlot.FEET, new ItemStack(com.projecthero.mod.andy.item.AndyItems.SUIT_BOOTS));
\t}

\tprivate static void script(Minecraft mc) {
\t\tint t = 10;
\t\tat(t, () -> server(mc, p -> {
\t\t\tbase = origin;
\t\t\tp.setGameMode(GameType.SURVIVAL);
\t\t\tAndy.grant(p);
\t\t\treset(p);
\t\t}));
\t\tt += 30;
\t\tVec3 front = new Vec3(1.2, 1.4, 2.6), frontLook = new Vec3(0, 1.0, 0);
\t\tVec3 back = new Vec3(-1.4, 1.4, -2.4), backLook = new Vec3(0, 1.0, 0);
\t\t// 1: the suit on, then the right arm lost with auto-regrowth held off (no sleeve on the stump), then the arm back with
\t\t// the pad still gone (no sleeve on it), front and back
\t\tscene(mc, t, 1, front, frontLook, p -> suit(p));
\t\tstill(t + 20, "suit_1_on");
\t\tat(t + 24, () -> server(mc, p -> {
\t\t\tAndy.setRegenOff(p, true);
\t\t\tAndy.breakPart(p, AndyState.RIGHT_ARM, AndyState.KIND_BURST);
\t\t}));
\t\tstill(t + 34, "suit_2_arm_lost");
\t\t// the arm back whole at once (Reform-like restore of just the arm segments), pad still torn off
\t\tat(t + 38, () -> server(mc, p -> {
\t\t\tAndyState n = Andy.state(p).copy();
\t\t\tn.setTimes(AndyState.RIGHT_ARM, 0L, 0L, 0L);
\t\t\tn.setTimes(AndyState.RIGHT_HAND, 0L, 0L, 0L);
\t\t\tn.setKind(AndyState.RIGHT_ARM, AndyState.KIND_NONE);
\t\t\tn.setKind(AndyState.RIGHT_HAND, AndyState.KIND_NONE);
\t\t\tp.setAttached(ModAttachments.ANDY_STATE, n);
\t\t\tSystem.out.println("[HARNESS] pads=" + com.projecthero.mod.andy.AndyArmour.wornLostPads(p));
\t\t}));
\t\tstill(t + 41, "suit_3_arm_back_pad_gone_front");
\t\tat(t + 43, () -> {
\t\t\tcamPos = base.add(back);
\t\t\tcamLook = base.add(backLook);
\t\t});
\t\tstill(t + 46, "suit_4_arm_back_pad_gone_back");
\t\tt += 60;
\t\t// 2: only the left hand cut (regen off): the sleeve stays on the arm; then a foot cut: the boots drop, the leggings stay
\t\tscene(mc, t, 2, front, frontLook, p -> suit(p));
\t\tat(t + 15, () -> server(mc, p -> {
\t\t\tAndy.setRegenOff(p, true);
\t\t\tAndy.breakPart(p, AndyState.LEFT_HAND, AndyState.KIND_BURST);
\t\t}));
\t\tstill(t + 25, "suit_5_hand_cut_sleeve_stays");
\t\tat(t + 28, () -> server(mc, p -> Andy.breakPart(p, AndyState.RIGHT_FOOT, AndyState.KIND_BURST)));
\t\tstill(t + 40, "suit_6_foot_cut");
\t\t// 3: regen back on: everything regrows; the suit back on every part (the pad back too once mended)
\t\tat(t + 44, () -> server(mc, p -> Andy.setRegenOff(p, false)));
\t\trecord(t + 44, "suit_regrow", 50);
\t\tt += 110;
\t\tat(t, () -> {
\t\t\tSystem.out.println("[HARNESS] done");
\t\t\tRuntime.getRuntime().halt(0);
\t\t});
\t}
}
`;
fs.writeFileSync(f, t);
console.log('ok');
