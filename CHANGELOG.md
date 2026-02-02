# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog and this project adheres to Semantic Versioning.

- Keep a Changelog: https://keepachangelog.com/en/1.1.0/
- Semantic Versioning: https://semver.org/spec/v2.0.0.html


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
