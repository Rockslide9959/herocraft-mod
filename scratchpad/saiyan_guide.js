// v0.17.3 Saiyan wave 1: the guidebook chapter (run from the repo root).
const fs = require('fs');
function edit(f, pairs) {
  let t = fs.readFileSync(f, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
  for (const [a, b] of pairs) { if (!t.includes(a)) throw new Error(f + ' missing: ' + a.slice(0, 100)); t = t.replace(a, b); }
  fs.writeFileSync(f, crlf ? t.replace(/\n/g, '\r\n') : t);
}
const G = 'src/main/java/com/projecthero/mod/hero/guide/HeroPackGuide.java';
edit(G, [
  [`	public static Chapter blackPantherChapter() {`,
   `	/** The Saiyan (v0.17.3). */
	public static Chapter saiyanChapter() {
		return chapters().get(CH_SAIYAN);
	}

	public static Chapter blackPantherChapter() {`],
  [`	private static final int CHAPTER_POWER_BASE = CH_ANDY + 1; // 68`,
   `	private static final int CH_SAIYAN = CH_ANDY + 1; // 68, v0.17.3
	private static final int CHAPTER_POWER_BASE = CH_SAIYAN + 1; // 69`],
  [`		// one chapter per ENABLED power (v0.14.8), in registration order (CHAPTER_POWER_BASE + i)
		for (Power power : Powers.enabled()) {`,
   `		// v0.17.3: the Saiyan (CH_SAIYAN)
		out.add(chapter("projecthero.guide.saiyan", lines -> {
			lines.add(Component.translatable("projecthero.guide.saiyan.tier").withStyle(ChatFormatting.GOLD));
			para(lines, "projecthero.guide.saiyan.body");
			blank(lines);
			for (String section : new String[]{"getting", "ki", "forms", "mastery", "instinct", "overdrive", "flight"}) {
				head(lines, "projecthero.guide.saiyan." + section);
				para(lines, "projecthero.guide.saiyan." + section + ".body");
				blank(lines);
			}
			head(lines, "projecthero.guide.saiyan.controls");
			for (String[] row : new String[][] { { "Left click", "combo" }, { "R", "feint" }, { "G", "ki_beam" }, { "Shift+G", "spirit_orb" },
					{ "Z", "barrage" }, { "Shift+Z", "sky_combo" }, { "X", "ki_dash" }, { "Shift+X", "flight" }, { "C", "dragon_rush" },
					{ "V", "instant_step" }, { "Shift+V", "power_down" }, { "H", "form_wheel" }, { "Hold Q", "charge" } }) {
				lines.add(Component.literal(" " + row[0] + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.saiyan.ability." + row[1]).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.saiyan.ability." + row[1] + ".desc");
			}
			blank(lines);
			head(lines, "projecthero.guide.saiyan.commands");
			para(lines, "projecthero.guide.saiyan.commands.body");
		}));

		// one chapter per ENABLED power (v0.14.8), in registration order (CHAPTER_POWER_BASE + i)
		for (Power power : Powers.enabled()) {`],
  [`		icon(CH_ANDY, "undead_card"); // v0.17.3\n`, `		icon(CH_ANDY, "undead_card"); // v0.17.3\n		icon(CH_SAIYAN, "saiyan_blood"); // v0.17.3\n`],
  [`		other.add(new IndexEntry(Component.translatable("projecthero.guide.andy"), 2, CH_ANDY)); // v0.17.3\n`,
   `		other.add(new IndexEntry(Component.translatable("projecthero.guide.andy"), 2, CH_ANDY)); // v0.17.3\n		other.add(new IndexEntry(Component.translatable("projecthero.guide.saiyan"), 2, CH_SAIYAN)); // v0.17.3\n`],
]);
console.log('ok');
