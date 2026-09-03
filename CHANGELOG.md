# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog and this project adheres to Semantic Versioning.

- Keep a Changelog: https://keepachangelog.com/en/1.1.0/
- Semantic Versioning: https://semver.org/spec/v2.0.0.html


## [1.6.9] - ???
### Added
- Add proper model for the Storage Level Emitter part, instead of reusing the Level Emitter's model (volumetric head instead of flat one).

### Fixed
- Fix Storage Display part's face overlay disappearing when going too far from the part, due to AE2's cable-bus TESR being disabled at long range (separately from our own TESR culling).


## [1.6.8] - 2026-08-???
### Added
- Add chunk-boundary checks for AE2 multiblocks to the Network Health Scanner's fatal errors.
- Add support for all Molecular Assembler-like machines to the Cards Distributor, for Acceleration Card distribution.
- Add a warning overlay if the AutoCrafter is still at the default batch size (1), nudging players to raise it to a more performance-friendly value.
- Add a "Run Now" button to entries of the Better Level Maintainer GUI.

### Fixed
- Fix tooltip leaking from under the Maintainer modal when it is open.
- Fix Maintainer not being able to reconcile finished jobs after a network disconnect, which could cause the Maintainer to think it was still waiting for a job that had already ended.
- Fix Network Component Locator not merging entries of the same component type when they had different NBT data.
- Fix possible overflow in Storage Level Emitter, when using a Fuzzy card (realistically only possible with Creative Cell).

### Changed
- Improve tooltips everywhere to highlight the actions (in AQUA) and the times (in GREEN).
- Improve the visuals on the AE2 AutoCrafter's catalysts, making the item a proper ghost item instead of a full-slot overlay.
- Make the Maintainer use the smallest available CPU for a task instead of letting the system decide.


## [1.6.7-hotfix2] - 2026-08-11
### Added
- Add a manual poll button to the Storage Monitor and Remote Storage Monitor polling screens, so their current network sample can be refreshed on demand.

### Fixed
- Fix TOP integration crashing on dedicated servers due to TOP only being registered client-side.


## [1.6.7-hotfix] - 2026-08-08
### Fixed
- Fix Better Level Maintainer recipe settings being discarded when closing the entry editor.
- Fix AutoCrafter catalyst rendering leaking GUI GL state into later inventory item renders.
- Fix AutoCrafter duplication recipes crafting the full output instead of the net gain.


## [1.6.7] - 2026-08-08
### Added
- Add AutoCrafter WAILA and The One Probe summary tooltips, including next-operation timing, error warnings, and active/full/disabled pattern counts.
- Add support for the Crafting and Fuzzy card to the Storage Level Emitter, bringing it to feature parity with the AE2 Level Emitter, alongside a cards picker GUI to easily select the card to use.
- Add AutoCrafter and Better Level Maintainer WAILA/The One Probe performance lines that report the block-side work time, including rolling last/average/max samples.
- Optimize the AutoCrafter's performance. The majority of the time is now taken by the AE2 network (insertion, extraction), with little time spent in the AutoCrafter's own logic.
- Add Better Level Maintainer, Storage Level Emitter, and Level Monitor Alarm WAILA/The One Probe lines for maintainer queue status, emitter card/redstone mode, and per-player alarm registration.
- Add right-click insertion of catalyst items into the AutoCrafter block, in the same way as patterns.

### Fixed
- Fix AutoCrafter crafting instantly when batch * speed (in ticks) goes above max int, which happens when batch is at max int / 20 at default speed.
- Tighten the Network Health Scanner's conflicting-pattern detection to match AE2's per-output-item craftable index, so patterns that share any output item now conflict even when it is not the exact same pattern identity (e.g. different counts or secondary outputs).
- Maybe fix AutoCrafter GUI lag when an attached Pattern Multi-Tool contains many patterns by caching the rendered item.


## [1.6.6] - 2026-07-30
### Added
- Add detailed AutoCrafter status hover tooltips in the recipe view so error states explain which catalyst or input is missing, or which outputs could not be reinserted into the network.
- Add Remote Storage Monitor overlay configs to show the post-poll total beside each delta and to switch between shortened and full numeric formatting.
- Keep the Remote Storage Monitor selector search text when reopening the selector and allow right-clicking the selector search box to clear it.
- Add optional Interface Terminal integration for the AE2 AutoCrafter, exposing its 12 pattern slots as two editable rows with the last six filler slots disabled, and a startup config toggle for the mixin.
- Add more leeway to the Remote Storage Monitor so that the overlay does not reset its baseline when the device is removed from the player's inventory. The session will be kept for 30 seconds after the device disappears, allowing for temporary drops or swaps without losing the current state.

### Fixed
- Fix the Remote Storage Monitor resetting its baseline when refreshing while the overlay is covered or client FPS throttling delay sync requests.
- Fix the Network Health Scanner fatal error pass treating item and fluid storage buses on the same target as duplicate storage links.
- Fix the scroll bar of the Remote Storage Monitor's content selection not being draggable (but still scrollable with the mouse wheel).

### Changed
- Split the Remote Storage Monitor timing controls into separate refresh interval and sliding window settings, so that the refresh interval (responsivity) can be set independently of the sliding window duration (sampling period).


## [1.6.5] - 2026-07-15
### Fixed
- Fix the Remote Storage Monitor selector crashing when typing in the search field after receiving selector entries without a cached display name.


## [1.6.4] - 2026-07-05
### Fixed
- Fix crash on server load due to client-only code not being properly annotated as client-only.


## [1.6.3] - 2026-07-05
### Fixed
- Fix large GUI-synced numeric values being truncated above 32k in the AutoCrafter and Storage Monitor screens by syncing susceptible counters as long instead of int.

### Added
- Add configurable 1-15 redstone strength controls to the Storage Level Emitter GUI.
- Add Remote Storage Monitor, a bauble/held item that shows the quantity variation of configured content in the network as an overlay on the screen, over a set period of time.
- Add client config for the Remote Storage Monitor overlay visuals.
- Add Storage Level Alarm, a block that tracks the quantity of a configured content in the network and send a continuous warning to any player that registered to it when any content goes below the configured threshold.
- Add Memory Card support for all member of the Storage Monitor family (Emitter, Display, Alarm), allowing to import settings from each others, or AE2 AutoCrafter/Better Level Maintainer patterns for the same content.
- Add a Patterns tab to the Network Health Scanner that reports invalid crafting patterns, conflicting outputs, and nested input/output recipes, including PackagedAuto-aware nested detection.


## [1.6.2] - 2026-06-18
### Fixed
- Fix Better Level Maintainer being mistaken for a RandomComplement missing-craft request when `enableMissCraft` is enabled, which could force impossible crafts instead of reporting the failure normally.
- Fix Pattern/Inventory item rendering eating on the AutoCrafter overview's item renders when the overview is open, by short-circuiting the recipe/inventory rendering when it is open.
- Fix Storage Monitor rendering leaking (just like the Maintainer's) when the selector is open.

### Added
- Add textures for the Storage Level Emitter and Storage Display.
- Add subnet support to the Network Health Scanner.
- Add Subnet Proxy support for anything with subnet support (NACL, NHS).
- Add smaller and smallerer part variants for the Storage Display.


## [1.6.1-alpha2] - 2026-06-02
### Added
- Improve the Network Health Scanner's unloaded chunk detection so it can detect adjacent chunks that are not chunkloaded, nor loaded by any player. Unloaded Quantum Network Bridges are still not detected, as the grid has no way to know about their existence until they are loaded.
- Add Wireless Connectors (AE2 Stuff) to the Network Health Scanner's list of components to check for unloaded chunks. The Wireless Hub only reports the 32 first connections in its NBT, so misconfigured hubs with more than 32 connections can have undetected unloaded chunks.


## [1.6.1-alpha] - 2026-06-01
### Added
- Add a Fatal Errors tab to the Network Health Scanner, for the detection of duplicate storage bus targets and storage buses that point back into interfaces on the same network.

## [1.6.0-alpha4] - 2026-05-26
### Fixed
- Fix PMT not having a JEI exclusion zone for the AutoCrafter.
- Fix Better Level Maintainer delaying some entries for far too long after AE2 network or storage topology changes.


## [1.6.0-alpha3] - 2026-05-12
### Added
- Add condition persistence to the Storage Monitor and Storage Level Emitter, allowing them to maintain their state over restarts and chunk unloads. This way, machines relaying on the quantity condition won't be disrupted by temporary issues or restarts.
- Add an optional hysteresis mode with separate increasing and decreasing thresholds for each monitored entry.

### Changed
- Move live quantity to the left side, with the resource icon, in the Storage Level Emitter / Storage Display's GUI.


## [1.6.0-alpha2] - 2026-05-11
### Added
- Delay Better Level Maintainer startup requests until the AE2 network and storage cell topology have settled. This also affects topology changes like adding/removing storage cells, which should prevent a lot of weird behaviors and errors that can occur when the maintainer tries to run while the network is in an inconsistent state.


## [1.6.0-alpha] - 2026-05-10
### Added
- Add Storage Level Emitter and Storage Display, alternative versions of the AE2 Level Emitter and Storage Monitor, with configurable refresh rate and more controlable matching. This should provide better performance when dealing with a lot of rapid changes, but do not need to react immediately.
- Add AE2's Offline / Missing Channel / Online status in WAILA and The One Probe to all blocks.

### Fixed
- Fix the Better Level Maintainer's selector being empty on the very first click after opening the GUI (race condition between container open and craftable item sync).


## [1.5.6] - 2026-04-29
### Fixed
- Fix Better Level Maintainer AE2 Fluid Crafting fluid and gas outputs being routed back into their ME storage channels instead of being counted as drop items, and show fluid or gas targets as fluid or gas in the selector and entry displays instead of raw drops.


## [1.5.5] - 2026-04-23
### Changed
- Rework the client sync of the Better Level Maintainer to use per-listener diff packets via `detectAndSendChanges` instead of every tile sending its full state to all nearby players every tick. This should make the block lighter on the server.


## [1.5.4] - 2026-04-22
### Added
- Add config for the performance limits of the Better Level Maintainer, allowing users to adjust the thresholds and behavior if the default settings are too aggressive.

### Fixed
- (Probably) fix Better Level Maintainer spamming "Failed to calculate crafting job" errors for large/complex recipes.

### Changed
- Rework the client sync of the AE2 AutoCrafter to be more responsive and less prone to desync issues. It should also lighten the load on the server by only syncing the actively viewed crafter, instead of all of them every second.
- Eject the catalyst items from the catalyst slots when the pattern is changed or removed, to prevent them from being left out in the slots and potentially causing issues later on.
- Move all server-translated text to the client side by sending raw status message keys and parameters instead of pre-formatted messages. This allows the client to format the messages in the player's locale instead of the server's default locale.


## [1.5.3] - 2026-04-16
### Added
- Add Memory Card support for the AutoCrafter. It will copy both settings and patterns, and format blank patterns are found in the player's inventory on settings application.
- Add a Creative tab for AE2 PowerTools (getting a tad cramped in AE2's tab)


## [1.5.2] - 2026-04-14
### Added
- Add Network Advanced Component Locator: a new tool that scans the AE2 network and displays all components in a grid (like the AE2 Network Tool). Click on a component type to see all its locations sorted by distance. Selected locations are highlighted with on-screen overlays, like the Network Health Scanner.
- Add sorting options for the Network Health Scanner display lists, allowing sorting by distance or name. Chokepoints still sort by excess channels by default, sorting by distance/name as a tiebreaker. Sorting preference is saved per-tab.
- Right-clicking the AutoCrafter with a crafting pattern in hand will insert it into the first available slot. Opens the GUI normally if the pattern is invalid (processing) or the crafter is full.

### Fixed
- Fix Network Health Scanner not resizing GUI when opened just after a scan (or as the scan is going).


## [1.5.1] - 2026-03-27
### Added
- Add Pattern Multi-Tool integration for AutoCrafter when NAE2 is installed. The PMT panel appears to the left of the GUI, providing convenient pattern storage access.

### Fixed
- Try to mitigate high CPU load in some edge cases of the Better Level Maintainer by adding some caching, throttling, and retry limits.
- Fix some errors potentially being interpreted as not enough CPU space (because AE2 doesn't provide accurate error reporting).


## [1.5.0-beta4] - 2026-03-09
### Fixed
- Fix the textures for the AutoCrafter.


## [1.5.0-beta3] - 2026-03-02
### Added
- Add proper textures for Batch and Speed buttons in the AutoCrafter GUI.
- Add "No pattern" state for entries without a pattern, instead of just "Disabled".

### Fixed
- Fix the auto-crafting state being stale in many cases due to sync issues (AE2 syncing is not made for real time).
- Fix recipes stopping at max int items on the network.

### Changed
- Change "Idle" state to "Running normally".


## [1.5.0-beta2] - 2026-03-01
### Fixed
- Fix lang files names due to .mcmeta addition.


## [1.5.0-beta] - 2026-03-01
### Added
- Add AE2 AutoCrafter block.
  - A powerful automation block that automatically crafts items from configured patterns.
  - Supports up to 12 recipe entries, each set to a specific pattern.
  - Pattern slot with full insertion/extraction/swapping/shift-click support.
  - Recipe preview showing 3x3 input grid and output slot.
  - 9-slot internal inventory per recipe for catalyst/reusable items.
  - Configurable batch size (to craft more at once less frequently).
  - Configurable speed (to slow down crafting, sparring performance).
  - Overview mode showing all 12 entries at a glance.
  - Page navigation for detailed recipe view.
  - Full network integration: extracts inputs from ME network, inserts outputs back.
  - State indicators: Disabled, Idle, Missing Catalyst, Missing Input, No Output Space, Simulation Failed, Holding Output.
  - Uses Fake Player for crafting operations (compatible with mods that require player context).


## [1.4.1] - 2026-02-19
### Added
- Add tall/compact view toggle for the Better Level Maintainer GUI.
  - Tall view shows one recipe per row with full item icons, state indicators, and detailed info.
  - Compact view shows three recipes per row in a more condensed layout.
  - Toggle button on the left side of the GUI, similar to AE2's terminal style button.
  - View preference is persisted in client config.


## [1.4.0] - 2026-02-17
### Added
- Add Better Level Maintainer.
  - A block that automatically maintains item quantities in your AE2 network by scheduling crafting jobs.
  - Features multiple recipes, target quantities, batch crafting, customizable frequency, CPU management, and status indicators.
  - Includes a detailed GUI for easy configuration and monitoring.


## [1.3.3] - 2026-02-11
### Added
- Add client config for NHS arrow and text scaling.

### Changed
- Make NHS distance text more visible.


## [1.3.2] - 2026-02-10
### Added
- Add scan finished message to the player when a network scan is completed, showing the number of issues found in each category.

### Fixed
- Fix localization for ME Network part names in the Network Health Scanner GUI.


## [1.3.1] - 2026-02-07
### Added
- Add multi-session support for Network Health Scanner to allow using multiple scanners on different networks simultaneously.
- Improve the tooltip of the Cards Distributor a bit.


## [1.3.0] - 2026-02-01
### Added
- Add the Cards Distributor tool for AE2 networks. This tool allows players to send acceleration cards from inventory directly to any free Molecular Assembler (or compatible) on the network by right-clicking.
- Add linking support via Security Station to allow the Cards Distributor to pull cards from AE2 storage when needed.
- Add textures and recipe for the Cards Distributor tool.


## [1.2.2] - 2026-01-28
### Fixed
- Add missing localization for some GUI elements.
- Fix the wireframe overlay not changing selection when changing dimensions.
- Fix the chunk detection using the wrong dimension when matching network components to loaded chunks.
- Fix some other tabs counting multiblock parts as different components.


## [1.2.1] - 2026-01-25
### Fixed
- Fix GUI not resizing properly on tab change.
- Fix GUI not taking the full height of the tabs area.
- Fix entries highlighting the wrong block (belonging to another entry) when selected.


## [1.2.0] - 2026-01-25
### Added
- Add textures and recipes for both scanner and priority tuner tools.


## [1.1.1] - 2026-01-25
### Fixed
- Fix loop calculation counting multiblock parts as different components with loops.


## [1.1.0] - 2026-01-17
### Added
Network Health Scanner:
- Detects channel chokepoints where demand exceeds cable capacity
- Identifies devices missing channels (requiring but not receiving a channel)
- Per-direction breakdown showing channel flow at intersections
- New tabs in the scanner GUI for chokepoints and missing channel devices


## [1.0.0] - 2026-01-16
### Added
Network Health Scanner:
- Detects cable loops in AE2 networks
- Identifies network components in non-chunkloaded areas
- Visual overlay with directional arrows
- Interactive GUI with dimension-grouped results
- Tabs for loops vs non-chunkloaded areas

Priority Tuner:
- Apply stored priority to multiple blocks
- Visual highlight feedback on application
- Automatic application on blocks placed when in off-hand
