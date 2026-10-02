# PrideInventory

Extra inventory rows for Minecraft 1.12.2 that you unlock by playing, a scrollable rows panel, hotbar pages, and a full-width "wide hotbar" that shows every unlocked row along the bottom of the screen.

## About

PrideInventory adds up to 12 extra rows of 9 slots to every player's own inventory. Rows unlock as you play: completed advancements, hours played, blocks mined and mobs killed each add rows, and rows can also be bought with in-game coins or handed out by quests and admins. Locked rows show exactly what is still needed for the next one.

Unlocked rows can also act as hotbar pages. The real hotbar always stays the normal 9 slots, so every other mod still sees a normal hotbar. Turning a page swaps a row into the hotbar and the hotbar into the rows, so items are only ever moved, never copied or lost. With the wide hotbar (on by default) all pages are drawn side by side across the bottom of the screen and the scroll wheel walks along all of them.

PrideInventory was built for the Pride modpack, a 1.12.2 pack of about 750 mods, and works in other packs. RealmCoin (the Pride Realms money mod) is optional and only needed to buy rows.

## Features

### Extra rows
- The rows are real slots of the player's own inventory container, added after vanilla's 46 slots, so mods that rely on slots 0-45 keep working. They are the same on client and server and sync like any other slot.
- Storage is always sized for 12 rows; `maxRows` decides how many can ever be used.
- Rows are saved with the player (a capability `prideinventory:extra_rows`).
- Locked rows take nothing in but always let you take things out, so nothing can get trapped if a row is locked again.
- Creative mode works too: the panel shows in creative's inventory tab, and a mixin lets creative mode save changes to the extra slots (vanilla only accepts slots 1-45 from creative), so items do not vanish on relog.

### Unlocking rows
Each rule adds rows on its own and they stack, up to `maxRows`:

| Source | Default |
|---|---|
| Rows everyone starts with | `startRows` = 0 |
| Completed advancements (recipe unlocks do not count) | +1 row per 20 |
| Hours played | +1 row per 5 hours |
| Blocks mined (all blocks) | +1 row per 10,000 |
| Mobs killed | +1 row per 500 |
| Bought with RealmCoin | +1 row per purchase; the first costs 1,000 coins and each next one costs 2× the one before |
| Granted by quests or admins | `/prideinv grant` or the `PrideInventory.grantRows` API |

- Creative mode unlocks every row (`creativeUnlocksAll`). Ops can be given every row (`opsUnlockAll`), and so can players named in `alwaysUnlockedPlayers`.
- Rows are recounted every 30 seconds, right away when a new advancement is earned, and within a second when a player switches into or out of creative or op status.
- A chat message announces each newly unlocked row (not on login).
- Bought and granted rows are never lost.

### The rows panel
- Shown under the survival inventory (or to the right, `panelSide`), and under creative's inventory tab.
- Shows `visibleRows` rows at once; scroll with the mouse wheel over the panel or drag the scroll bar.
- Locked rows are dimmed with "Row N locked". Hovering one shows every way to get the next row: how many more advancements, hours, blocks or kills are needed, the coin price, and quest rewards.
- A footer shows `unlocked/max rows`, the current page `1/7` between ◀ ▶ buttons, and a `+` button to buy the next row. The buttons are added in the survival inventory.
- The panel never goes below the bottom of the screen on small windows.

### Hotbar pages
- The first unlocked rows (up to `hotbarPages`) act as pages. Turning forward brings row 1 into the hotbar and sends the hotbar to the back of the line; turning back does the reverse.
- Pages turn with the ◀ ▶ buttons in the panel, the keybinds, or `/prideinv page`.
- When the number of page rows shrinks (for example after leaving creative), the real hotbar is put back first.
- With the normal hotbar, a `◀ 2/5 ▶` indicator is drawn to the left of it (`hudIndicator`).

### Wide hotbar
On by default (`wideHotbar`).
- The hotbar is drawn as a dark band across the whole bottom of the screen: page 1 (the real 9 slots), then every unlocked page row, left to right, each as its own hotbar strip with its page number above it.
- The scroll wheel moves the selection along all of them. Stepping into another page asks the server to swap that row into the real hotbar, so the selected item is always really in your hand and every other mod still sees a normal 9-slot hotbar. Number keys pick within the current page.
- Holding Shift, Ctrl or Alt leaves the scroll wheel to other mods. With only one page, vanilla scrolling is used.
- When the pages are wider than the screen, the bar slides so the selected slot stays visible. The offhand slot is drawn to the left of the bar, or above its left end when the bar fills the width.
- Hidden in spectator mode.

### Death
- By default the extra rows drop like the rest of the inventory when you die (`keepExtraRowsOnDeath = false`). The `keepInventory` gamerule keeps them.
- Unlocked, bought and granted rows are never lost either way. Leaving the End keeps everything.

### Self-test
If a file named `prideinventory-selftest` exists in the game folder, the first player to join runs a check: the extra slots exist, numbered items survive every page flip forwards and backwards with nothing lost or duplicated, the unlock rules give the right counts, and granting every advancement unlocks rows right away. The inventory is put back exactly and the log shows `PRIDEINV PASS` or `PRIDEINV FAIL`. The live part grants every advancement, so only use it in a throwaway world.

## Commands

`/prideinv` (alias `/pinv`). Everyone can use it; `grant` and recounting another player need op level 2.

| Command | What it does |
|---|---|
| `/prideinv` or `/prideinv info` | Your rows (unlocked/max), advancements, hours played, blocks mined, kills, bought rows and reward rows |
| `/prideinv buy` | Buy the next row with RealmCoin |
| `/prideinv page [next\|back]` | Turn the hotbar page (`back`, or anything starting with `b`, turns backwards) |
| `/prideinv grant <player> <rows>` | Give (or with a negative number take back) reward rows, between -12 and 12 |
| `/prideinv recount [player]` | Recount unlocks now |

### Keybinds
Category "PrideInventory".

| Key | Default | Action |
|---|---|---|
| Next hotbar page | `'` (apostrophe) | Turn one page forward |
| Previous hotbar page | not bound | Turn one page back |

## Config

`config/prideinventory.cfg`

### `inventory`
| Key | Default | Effect |
|---|---|---|
| `maxRows` | `6` | Most extra rows a player can ever have (0-12). 0 turns the panel off |
| `startRows` | `0` | Rows everyone has from the start |
| `visibleRows` | `3` | Rows shown at once in the panel; scroll for the rest |
| `panelSide` | `below` | `below` or `right` of the inventory |

### `hotbar_pages`
| Key | Default | Effect |
|---|---|---|
| `hotbarPages` | `6` | Most rows that can be hotbar pages (0 = no pages) |
| `hudIndicator` | `true` | Show `◀ 2/5 ▶` beside the normal hotbar |
| `wideHotbar` | `true` | One long hotbar across the bottom of the screen with every unlocked row. `false` = the normal hotbar with page arrows |

### `unlock_by_playing`
Each rule adds rows on its own; 0 turns a rule off.

| Key | Default | Effect |
|---|---|---|
| `advancementsPerRow` | `20` | +1 row per this many advancements |
| `hoursPerRow` | `5` | +1 row per this many hours played |
| `blocksMinedPerRow` | `10000` | +1 row per this many blocks mined |
| `mobKillsPerRow` | `500` | +1 row per this many mobs killed |

### `buy_with_realmcoin`
| Key | Default | Effect |
|---|---|---|
| `enabled` | `true` | Allow buying rows with RealmCoin (`/prideinv buy` or the `+` button) |
| `firstRowPrice` | `1000` | Price of the first bought row, in coins |
| `priceMultiplier` | `2.0` | Each next row costs this many times the one before |

Payments go from the player's account to a business account "Inventory Rows" (`b:prideinventory`) that is created on first use.

### `unlock_everything`
| Key | Default | Effect |
|---|---|---|
| `creativeUnlocksAll` | `true` | Creative mode = every row unlocked |
| `opsUnlockAll` | `false` | Ops always have every row |
| `alwaysUnlockedPlayers` | empty | Player names that always have every row, even in survival |

### `death`
| Key | Default | Effect |
|---|---|---|
| `keepExtraRowsOnDeath` | `false` | Keep the extra rows' items on death. `false` = they drop like the rest of the inventory |

## API for other mods

```java
int total = PrideInventory.grantRows(player, rows, "Quest reward");
```

Gives a player reward rows for good, shows `✦ <why>: +N inventory rows` in chat and returns their new number of unlocked rows. A quest book without a direct hook can use a command reward instead: `prideinv grant {player} 1`.

## Requirements
- Minecraft 1.12.2 with Forge 14.23.5.2860 or Cleanroom.
- MixinBooter (built into Cleanroom). The mixins are required: one adds the slots to the player container, one accepts creative-mode changes to them.
- Java 8 or newer.
- Install on both server and client (the slots must match on both sides).
- Optional: RealmCoin, to buy rows.

## Install
1. Download `PrideInventory-1.12.2-<version>.jar`.
2. Put it in the `mods` folder on the server and every client.
3. Start the game, then adjust `config/prideinventory.cfg` if you want different unlock rules.

## Building

```
./gradlew build
```

The jar is written to `build/libs/`. The project targets Java 8 (ForgeGradle 3, MCP snapshot `20171003-1.12`). It compiles against `libs/mixinbooter-api.jar`, `libs/sponge-mixin.jar` and `libs/realmcoin-dev.jar` (for the optional coin payments), which you need to place in `libs/` before building. A compile-only stub of `zone.rong.mixinbooter.ILateMixinLoader` is in the sources and left out of the jar. A small check for the page logic is in `checks/PagesCheck.java`.

## License

MIT License. © 2026 crunkazcanbe.

## Credits

Made by crunkazcanbe, with Claude.


## Compile-only jars

The build compiles against these jars in `libs/` (other authors' mods / APIs). They are not included in this repo — get them from their official pages and drop them in `libs/` before building:

- `mixinbooter-api.jar`
- `realmcoin-dev.jar`
- `sponge-mixin.jar`
