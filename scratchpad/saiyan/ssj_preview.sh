#!/bin/sh
# Offline SSJ hair preview (no Minecraft). Run from the worktree root. Arg 1: output png.
J="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.8-hotspot/bin"
JOML=$(cygpath -w "$(find ~/.gradle/caches/modules-2 -name "joml-1.10.5.jar" | head -1)")
T="${TMPDIR:-/tmp}/ssjprev"
rm -rf "$T"; mkdir -p "$T/src"
cp src/client/java/com/projecthero/mod/client/saiyan/hair/SsjHairModel.java "$T/src/"
cp scratchpad/saiyan/SsjHairPreview.java.txt "$T/src/SsjHairPreview.java"
"$J/javac" -nowarn -cp "$JOML" -d "$T/out" "$T/src/SsjHairModel.java" "$T/src/SsjHairPreview.java" || exit 1
"$J/java" -cp "$(cygpath -w "$T/out");$JOML" SsjHairPreview "${1:-preview.png}" $2
