// v0.17.3 tester round: appends the landing gametests to StarfireGameTests / RavenGameTests (CRLF-safe, idempotent).
// Usage: node scratchpad/titans/add_tests.js <worktree root>
const fs = require('fs');
const W = process.argv[2];
const G = W + '/src/gametest/java/com/projecthero/mod/gametest/';

const helper = (fly, name) => `
	/**
	 * v0.17.3 tester round: ${name}'s superhero landing. Mock players never move, so the descent is the "known movement" and ground
	 * contact is setOnGround; HeroLanding.tick is driven directly (as HeroLandingGameTests does).
	 */
	private static void diveAndLand(GameTestHelper helper, ServerPlayer p, Vec3 motion, java.util.function.Consumer<ServerPlayer> then) {
		for (int i = 0; i < 4; i++) {
			helper.runAfterDelay(i, () -> {
				p.setOnGround(false);
				p.setKnownMovement(motion);
				com.projecthero.mod.flight.landing.HeroLanding.tick(p);
			});
		}
		helper.runAfterDelay(4, () -> {
			p.setOnGround(true);
			com.projecthero.mod.flight.landing.HeroLanding.tick(p);
			then.accept(p);
		});
	}
`;

const starfireTests = helper('StarfireFlight.start(p)', 'Starfire') + `
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void diveLandsInTheStarfireStyle(GameTestHelper helper) {
		ServerPlayer p = starfire(helper);
		StarfireFlight.start(p);
		helper.assertTrue(Starfire.isFlying(p), "precondition: flying");
		diveAndLand(helper, p, new Vec3(0.0, -0.8, 0.0), q -> {
			com.projecthero.mod.flight.landing.HeroLanding.Active a = com.projecthero.mod.flight.landing.HeroLanding.active(q);
			helper.assertTrue(a != null, "a cruise-speed dive into the ground is a hero landing (and its effects ran without throwing)");
			helper.assertTrue(a.style() == com.projecthero.mod.flight.landing.HeroLandingStyle.STARFIRE, "in her style, was " + a.style());
			helper.assertTrue(a.style().pose == com.projecthero.mod.flight.landing.HeroLandingStyle.Pose.FIST, "fist into the ground");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void gentleTouchdownIsNoLanding(GameTestHelper helper) {
		ServerPlayer p = starfire(helper);
		StarfireFlight.start(p);
		diveAndLand(helper, p, new Vec3(0.05, -0.2, 0.0), q -> {
			helper.assertFalse(com.projecthero.mod.flight.landing.HeroLanding.landing(q), "settling down slowly just lands");
			helper.succeed();
		});
	}
`;

const ravenTests = helper('RavenFlight.toggle(p)', 'Raven') + `
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void sprintDiveLandsInTheRavenStyle(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 3.5, 3.5);
		RavenFlight.toggle(p);
		helper.assertTrue(Raven.isFlying(p), "precondition: levitating");
		// her sprint is 16 b/s: straight down that is 0.8 blocks a tick
		diveAndLand(helper, p, new Vec3(0.0, -0.8, 0.0), q -> {
			com.projecthero.mod.flight.landing.HeroLanding.Active a = com.projecthero.mod.flight.landing.HeroLanding.active(q);
			helper.assertTrue(a != null, "a sprinting dive into the ground is a hero landing (and its effects ran without throwing)");
			helper.assertTrue(a.style() == com.projecthero.mod.flight.landing.HeroLandingStyle.RAVEN, "in her style, was " + a.style());
			helper.assertTrue(a.style().pose == com.projecthero.mod.flight.landing.HeroLandingStyle.Pose.WINGS, "kneeling, arms swept back");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void calmLevitationTouchdownIsNoLanding(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 3.5, 3.5);
		RavenFlight.toggle(p);
		// her 9 b/s cruise straight down (0.45 b/t) is under the shared fast-descent bar
		diveAndLand(helper, p, new Vec3(0.0, -0.45, 0.0), q -> {
			helper.assertFalse(com.projecthero.mod.flight.landing.HeroLanding.landing(q), "a calm descent just lands");
			helper.succeed();
		});
	}
`;

function add(file, block) {
	const f = G + file;
	let s = fs.readFileSync(f, 'utf8');
	if (s.includes('diveAndLand')) { console.log(file, 'already has the tests'); return; }
	const nl = s.includes('\r\n') ? '\r\n' : '\n';
	const end = s.lastIndexOf('}');
	s = s.slice(0, end).replace(/\s*$/, nl) + block.replace(/\n/g, nl) + '}' + nl;
	fs.writeFileSync(f, s);
	console.log(file, 'ok');
}
add('StarfireGameTests.java', starfireTests);
add('RavenGameTests.java', ravenTests);
