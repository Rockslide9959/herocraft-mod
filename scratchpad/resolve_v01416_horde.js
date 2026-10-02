const fs=require("fs");
function fix(f,repl){let s=fs.readFileSync(f,"utf8");const crlf=s.includes("\r\n");s=s.replace(/\r\n/g,"\n");
s=s.replace(/<<<<<<< HEAD\n[\s\S]*?>>>>>>> [^\n]*\n/,repl);if(s.includes("<<<<<<<"))throw f;if(crlf)s=s.replace(/\n/g,"\r\n");fs.writeFileSync(f,s);}
fix("src/client/java/com/projecthero/mod/client/horde/HordeClient.java",
"		// v0.14.16: the Bone Tyrant is a GeckoLib model now; it and the Skeleton Horde's own mobs are registered here\n		SkeletonHordeClient.initialize();\n		// v0.14.16: the Brood Queen is a GeckoLib boss now; her brood variants share the vanilla spider mesh\n		EntityRendererRegistry.register(HordeEntityTypes.BROOD_QUEEN, BroodQueenRenderer::new);\n		EntityRendererRegistry.register(HordeEntityTypes.BROOD_SPIDER, BroodSpiderRenderer::new);\n");
fix("src/main/java/com/projecthero/mod/horde/HordeWaves.java",
"			// v0.14.16: the six horde skeletons (one per zombie kind) join the mix -- see SkeletonHordeRoster\n			case SKELETON -> com.projecthero.mod.horde.entity.skeleton.SkeletonHordeRoster.create(level, w);\n			case SPIDER -> SpiderWaves.create(level, w); // v0.14.16: Horde Spiders, cave spiders and the seven brood variants\n");
console.log("ok");
