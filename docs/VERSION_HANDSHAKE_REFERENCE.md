# Version handshake and build check

Code: `network/VersionCheck` (server + payloads), `client/VersionCheckClient` (client answers), `build.gradle` task `generateBuildInfo`.
Tests: `GuidebookStabilityGameTests.buildCheckRefusesADifferentBuildOfTheSameVersion`.

## Two checks

| Check | Since | Phase | Compares | Refuses with |
|---|---|---|---|---|
| Build check | v0.17.5 | CONFIGURATION, before Fabric's registry sync | version + build id | "Your Project Hero jar is a different build from the host's (yours: ..., host: ...). Get the latest jar and try again." |
| Version check | v0.14.22 | PLAY (join) | version only | "Project Hero version mismatch! ..." |

The version stays the same (e.g. `0.17.5`) across many test builds. Two different 0.17.5 jars passed the old version check but had
different items and attachments, so the joining client failed later with no clear reason. The build check catches that first.

## The build id

`generateBuildInfo` runs on every build (it is never up to date) and writes `build/generated/buildinfo/projecthero_build.properties`,
which ships in the jar root:

```
id=<git short hash>-<yyyyMMddHHmmss UTC>
built=<yyyy-MM-dd HH:mm UTC>
commit=<git short hash, or "nogit">
```

Every build gets a new id, including two builds of the same commit, so a jar built from uncommitted changes never matches one
built without them. `VersionCheck.buildId()` reads the file through the mod container. A jar without the file (an IDE run that
skipped Gradle) reports `dev`, which matches any build. The versions must still match.

## Flow

1. `ServerConfigurationConnectionEvents.BEFORE_CONFIGURE`, in a phase ordered before the default one: Fabric adds its registry-sync
   task in the default phase, and configuration tasks run in the order they are added.
2. If the client can receive `projecthero:build_check`, a `BuildTask` is queued. It sends the server's `BuildPayload(version, id, built)`.
   The client answers with its own, and the server completes the task or disconnects.
3. A client that can't receive the payload is running a jar from before the check, so it's a different build. It's refused with
   "yours: an older build".

## Not checked

- **The singleplayer / LAN host** (`server.isSingleplayerOwner`). Their connection is in-memory and uses the same jar. Fabric skips
  registry sync for them too.
- **GameTest mock players.** They never go through configuration, and the `GameTestServer` is excluded as well. This is the
  v0.14.22 mock-player kick gotcha.
- LAN guests and dedicated-server players are checked.

Kick texts are literal strings, not lang keys, because an older client would not have newer keys.
