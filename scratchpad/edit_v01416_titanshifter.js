const fs=require("fs");
function edit(f,pairs){let s=fs.readFileSync(f,"utf8");const crlf=s.includes("\r\n");s=s.replace(/\r\n/g,"\n");
for(const [a,b] of pairs){if(!s.includes(a))throw new Error(f+" missing "+a.slice(0,80));s=s.replace(a,b);}
if(crlf)s=s.replace(/\n/g,"\r\n");fs.writeFileSync(f,s);}
edit("src/main/java/com/projecthero/mod/titanshifter/TitanShifter.java",[
["	private static final Map<UUID, Long> EMERGENCY_HOLD = new HashMap<>();\n",
 "	private static final Map<UUID, Long> EMERGENCY_HOLD = new HashMap<>();\n	/** v0.14.16: the game tick each shifter's bar last refilled on -- a player ticked twice in one tick refills once. */\n	private static final Map<UUID, Long> LAST_REFILL = new HashMap<>();\n"],
["		EMERGENCY_HOLD.clear();\n	}","		EMERGENCY_HOLD.clear();\n		LAST_REFILL.clear();\n	}"],
["		if (player.level().getGameTime() % 20L != 0L || s.energy >= e.max) {\n			return;\n		}",
 "		long now = player.level().getGameTime();\n		if (now % 20L != 0L || s.energy >= e.max) {\n			return;\n		}\n		Long last = LAST_REFILL.put(player.getUUID(), now);\n		if (last != null && last == now) {\n			return; // already refilled this tick (the player was ticked twice)\n		}"]]);
console.log("ok");
