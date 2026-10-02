const fs=require("fs");
function edit(f,pairs){let s=fs.readFileSync(f,"utf8");const crlf=s.includes("\r\n");s=s.replace(/\r\n/g,"\n");
for(const [a,b] of pairs){if(!s.includes(a))throw new Error(f+" missing "+a.slice(0,80));s=s.replace(a,b);}
if(crlf)s=s.replace(/\n/g,"\r\n");fs.writeFileSync(f,s);}
edit("src/main/java/com/projecthero/mod/flight/DirectionalFlightModel.java",[
["		double accel = Math.max(0.10, Math.min(0.35, suitAcceleration * 2.5));\n		Tune tune = new Tune(VANILLA_FLYING_SPEED * VANILLA_CRUISE_FACTOR * suitSpeed * (sprint ? 2.0 : 1.0),",
"		double accel = Math.max(0.10, Math.min(0.35, suitAcceleration * 2.5));\n		// v0.14.16 (merge): never slower than the plain creative-flight speed every mark and the boots really flew at\n		// before directional flight -- the faster marks still go faster.\n		Tune tune = new Tune(VANILLA_FLYING_SPEED * VANILLA_CRUISE_FACTOR * Math.max(1.0, suitSpeed) * (sprint ? 2.0 : 1.0),"],
["	 * Repulsor Boots: exactly what their tooltip promises -- the Mark 2's flight at {@code RepulsorBoots.FLIGHT_SPEED}\n	 * (50%) speed and {@code FLIGHT_ACCELERATION} (half its pick-up), under the 15 m/s ceiling.\n	 */",
"	 * Repulsor Boots: the Mark 2's cruise speed (never slower than the creative-flight speed they always really had) with\n	 * {@code FLIGHT_ACCELERATION} (half its pick-up) -- no extra ceiling, so sprint flight is as quick as it was.\n	 */"],
["				com.projecthero.mod.ironman.RepulsorBoots.FLIGHT_ACCELERATION, 0.0, sprint, false)\n				.withMaxHorizontal(REPULSOR_BOOTS_CAP);",
"				com.projecthero.mod.ironman.RepulsorBoots.FLIGHT_ACCELERATION, 0.0, sprint, false);"]]);
edit("src/gametest/java/com/projecthero/mod/gametest/DirectionalFlightGameTests.java",[
["		helper.assertTrue(Math.abs(DirectionalFlightModel.repulsorBoots(false).speed() - ironMan.speed() / 2.0) < EPS\n				&& boots.maxHorizontal() == DirectionalFlightModel.REPULSOR_BOOTS_CAP,\n				\"Repulsor Boots: half the Mark 2's speed, 15 m/s ceiling\");",
"		helper.assertTrue(Math.abs(DirectionalFlightModel.repulsorBoots(false).speed() - ironMan.speed()) < EPS\n				&& boots.maxHorizontal() == 0.0,\n				\"Repulsor Boots: never slower than the creative-flight speed they always had, no extra ceiling\");"]]);
console.log("ok");
