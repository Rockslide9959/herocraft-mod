// v0.17.3 Starfire: the Guidebook chapter (DC category) + the chapter-count test. Run once: node scratchpad/starfire/wire_guide.js
const fs = require('fs');
const G = 'src/main/java/com/projecthero/mod/hero/guide/HeroPackGuide.java';
const T = 'src/gametest/java/com/projecthero/mod/gametest/HeroPackGameTests.java';
let g = fs.readFileSync(G, 'utf8');
if (/starfire/i.test(g)) throw new Error('already wired');
// the file has mixed line endings: match either, keep the match's own endings round the insert
function rep(s, a, b) {
  const re = new RegExp(a.split('\n').map(x => x.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('\r?\n'), 'g');
  const m = s.match(re);
  if (!m || m.length !== 1) throw new Error('found ' + (m ? m.length : 0) + 'x: ' + a);
  const eol = m[0].includes('\r\n') ? '\r\n' : '\n';
  return s.replace(re, () => b.replace(/\n/g, eol));
}
g = rep(g, '	/** the Spin, the Spin (v0.17.3). */\n	public static Chapter spinChapter() {',
`	/** v0.17.3 Starfire: the chapter's control rows {key, ability id}; each shows {@code projecthero.starfire.ability.<id>} and its .desc. */
	public static final String[][] STARFIRE_CONTROLS = { { "R", "starbolt" }, { "Shift+hold R", "star_burst" }, { "G", "grab" },
			{ "Shift+hold G", "eye_beams" }, { "Z", "barrage" }, { "Shift+Z", "supernova" }, { "Double-tap Space", "flight" }, { "X", "star_dash" },
			{ "Shift+X", "comet_dive" }, { "C", "charge" }, { "Shift+C", "ground_pound" }, { "V", "kiss" }, { "Shift+V", "radiance" } };

	/** Starfire (v0.17.3). */
	public static Chapter starfireChapter() {
		return chapters().get(CH_STARFIRE);
	}

	/** the Spin, the Spin (v0.17.3). */
	public static Chapter spinChapter() {`);
g = rep(g, '	private static final int CHAPTER_POWER_BASE = CH_SPIN + 1; // 70',
`	private static final int CH_STARFIRE = CH_SPIN + 1; // 70, v0.17.3: Starfire (Teen Titans)
	private static final int CHAPTER_POWER_BASE = CH_STARFIRE + 1; // 71`);
g = rep(g, `			head(lines, "projecthero.guide.spin.commands");
			para(lines, "projecthero.guide.spin.commands.body");
		}));
`, `			head(lines, "projecthero.guide.spin.commands");
			para(lines, "projecthero.guide.spin.commands.body");
		}));

		// v0.17.3: Starfire (CH_STARFIRE)
		out.add(chapter("projecthero.guide.starfire", lines -> {
			lines.add(Component.translatable("projecthero.guide.starfire.tier").withStyle(ChatFormatting.GOLD));
			para(lines, "projecthero.guide.starfire.body");
			blank(lines);
			for (String section : new String[]{"getting", "traits", "starbolt", "mood", "flight", "costume"}) {
				head(lines, "projecthero.guide.starfire." + section);
				para(lines, "projecthero.guide.starfire." + section + ".body");
				blank(lines);
			}
			head(lines, "projecthero.guide.starfire.controls");
			for (String[] row : STARFIRE_CONTROLS) {
				lines.add(Component.literal(" " + row[0] + "  ").withStyle(ChatFormatting.GOLD)
						.append(Component.translatable("projecthero.starfire.ability." + row[1]).withStyle(ChatFormatting.WHITE)));
				para(lines, "projecthero.starfire.ability." + row[1] + ".desc");
			}
			blank(lines);
			head(lines, "projecthero.guide.starfire.commands");
			para(lines, "projecthero.guide.starfire.commands.body");
		}));
`);
g = rep(g, '		icon(CH_SPIN, "zeppeli_steel_balls"); // v0.17.3\n', '		icon(CH_SPIN, "zeppeli_steel_balls"); // v0.17.3\n		icon(CH_STARFIRE, "tamaranean_starstone"); // v0.17.3\n');
g = rep(g, '		link(dc, "projecthero.guide.speed_force_speedster", CH_SPEED_FORCE); // v0.17.1\n',
  '		link(dc, "projecthero.guide.speed_force_speedster", CH_SPEED_FORCE); // v0.17.1\n		link(dc, "projecthero.guide.starfire", CH_STARFIRE); // v0.17.3: Teen Titans\n');
fs.writeFileSync(G, g);
let t = fs.readFileSync(T, 'utf8');
t = rep(t, 'chapters.size() == 70 + Powers.enabled().size(), // v0.17.3: + Spin,', 'chapters.size() == 71 + Powers.enabled().size(), // v0.17.3: + Starfire, + Spin,');
fs.writeFileSync(T, t);
console.log('guide wired');
