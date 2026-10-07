# Changelog

## 0.2.0 - 2026-10-07

### Added
- **Inventory sharing**: right-click another player with an empty hand to see their inventory: armour, offhand, main inventory, hotbar and unlocked extra rows.
  - Everyone starts **locked** (view only). Unlock with the new padlock button in the top-right corner of your inventory, an optional key binding ("Lock / unlock your inventory for others"), or `/invlock off`; others can then take and give.
  - Shift-click the padlock or use `/invlock gifts on|off` to let people give you items while you stay locked.
  - The owner gets a chat notice when someone opens, takes or gives, and `/invlock log` shows the last 15 events.
  - Fully server-side checks: every click type (pick up, shift-click, number keys, drag, double-click, throw) re-checks the owner's live lock state, and the view closes on distance, death, logout or re-lock.
  - New config category `inventory_sharing`: `enabled`, `openWith` (`empty_hand`, `sneak_empty_hand`, `any_empty_hand`), `maxDistance` (default 6), `notifyOwner`. Other mods that use right-click on players always take priority.
- **Themed wide hotbar**: when PrideCanvas is installed and its HUD theme is active, the wide hotbar band and slots are drawn in the HUD theme's colours and style.

### Fixed
- Items no longer "bounce" into the next hotbar page and back while scrolling: items are drawn by the page the server has confirmed, and only the selection frame moves ahead.
- Dedicated servers no longer load client-only classes when syncing rows.
- Added `pack.mcmeta` so Cleanroom loads the language file (names and key bindings showed raw translation keys).

