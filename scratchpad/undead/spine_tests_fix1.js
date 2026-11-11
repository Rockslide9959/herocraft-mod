// v0.17.3 Spine Whip tests, fix 1: the sweep-order zombies clear of the test's walls; the snagged zombie has AI (a no-AI mob never
// moves) but no walking speed.
const { edit } = require('./edit_lib.js');
edit('src/gametest/java/com/projecthero/mod/gametest/UndeadVMoveGameTests.java', [
	['Zombie right = zombie(helper, new Vec3(0.0, 2.0, 1.4));\n\t\tZombie left = zombie(helper, new Vec3(5.0, 2.0, 1.4));',
	 'Zombie right = zombie(helper, new Vec3(0.5, 2.0, 1.4));\n\t\tZombie left = zombie(helper, new Vec3(4.5, 2.0, 1.4));'],
	['\t\tahead.setNoGravity(false);\n',
	 '\t\tahead.setNoGravity(false);\n\t\tahead.setNoAi(false); // (a no-AI mob is never moved by its velocity)\n\t\tahead.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);\n'],
]);
