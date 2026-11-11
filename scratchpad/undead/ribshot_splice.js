// v0.17.3: the Rib Shotgun's new rib cage / cavity / spike drawing spliced into UndeadRibShotFx (CRLF-safe). Run from the repo root.
const fs = require('fs');
const { edit } = require('./edit_lib.js');
const f = 'src/client/java/com/projecthero/mod/client/undead/UndeadRibShotFx.java';
let t = fs.readFileSync(f, 'utf8').replace(/\r\n/g, '\n');
const at = t.indexOf('\t// ---------------------------------------------------------------- drawing');
if (at < 0) throw new Error('no drawing section');
t = t.slice(0, at) + fs.readFileSync('scratchpad/undead/ribshot_draw.java.txt', 'utf8').replace(/\r\n/g, '\n');
fs.writeFileSync(f, t.replace(/\n/g, '\r\n'));
edit(f, [
	['import net.minecraft.world.phys.Vec3;\n', 'import net.minecraft.world.phys.Vec3;\n\nimport org.joml.Vector3f;\n'],
	[' * <li>the wind-up: his ribs spring half open out of his chest (the Rib Cage Guard\'s ribs, {@link #opening}) with a little blood\n * where they tear loose;</li>\n',
	 ' * <li>the wind-up: his chest tears open and his ribs spring out of it ({@link #cageRib}: curved, tapering bands of bone that arc\n * forward and out from the sides of the hole and curl back in at their broken, bloody ends) with a little blood where they tear\n * loose;</li>\n'],
	[' * <li>the hole it leaves ({@link #wound}, third person, on the body): a raw cavity with broken rib stubs and blood running down,\n',
	 ' * <li>the hole it leaves ({@link #wound}, third person, on the body): torn skin, a ring of raw muscle, a dark red cavity with the\n * backbone deep inside and broken rib stubs, a little blood running down,\n'],
	[`	/** 0..1 through the rib shot's wind-up as the ribs spring half out (the Rib Cage Guard's ribs), 0 outside it. */
	static float opening(Player p, float partial) {
		Long w = WOUNDS.get(p.getUUID());
		Minecraft mc = Minecraft.getInstance();
		if (w == null || (p == mc.player && mc.options.getCameraType().isFirstPerson())) {
			return 0f; // (from his own eyes the ribs right under the camera would fill the bottom of the view)
		}
		float age = now() - w + partial;
		if (age < 0f || age >= UndeadKitConfig.RIB_SHOT_WINDUP) {
			return 0f;
		}
		float k = age / UndeadKitConfig.RIB_SHOT_WINDUP;
		return 0.12f + 0.5f * k * k * (3f - 2f * k);
	}

`, ''],
]);
