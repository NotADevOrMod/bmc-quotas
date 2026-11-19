# BendersMC Quotas (Fabric, Minecraft Java 26.3)

Client-side mod that tracks staff quotas:

- Playtime per gamemode (detected from `/joinq <mode>` + server join; manual override in the menu)
- Discord message counters (manual +/- in the menu, or feed it from `config/bmcquota/discord.json`)
- Quotas per soup (supervisor profile), soup mark + rank (name, Minecraft colour, image)
- Advancement-style corner toast + level-up sound when a quota is completed
- Open with **K**, or the **BMC Quotas** button on the title / pause screen

## Build
Push to GitHub: the included Action builds the jar (Actions tab > latest run > Artifacts > `bmcquota`).
Locally: JDK 25 + `gradle build` (Gradle 9.5+), jar lands in `build/libs/`.

## Install (Lunar Client)
Put `bmcquota-1.0.0.jar` and Fabric API (0.162.0+26.3) in the mods folder of your Lunar Fabric 26.3 profile.

## Config
`config/bmcquota/data.json` — `serverMatch` (text the server address must contain, default `bendersmc`)
and `stopCommands` (commands that stop counting, default lobby/hub/l) can be edited there.
`config/bmcquota/debug.log` records what the server prints right after `/joinq` to help improve detection.

`discord.json` format: `{"messages": 123, "help": 10}`
