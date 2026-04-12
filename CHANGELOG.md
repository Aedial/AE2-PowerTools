# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog and this project adheres to Semantic Versioning.

- Keep a Changelog: https://keepachangelog.com/en/1.1.0/
- Semantic Versioning: https://semver.org/spec/v2.0.0.html


## [1.5.2] - 2026-03-30
### Added
- Add sorting options for the Network Health Scanner display lists, allowing sorting by distance or name. Chokepoints still sort by excess channels by default, sorting by distance/name as a tiebreaker. Sorting preference is saved per-tab.


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
