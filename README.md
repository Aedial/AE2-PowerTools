# AE2 PowerTools

A collection of handy utility tools for Applied Energistics 2 network management and debugging.

## Features

### Network Health Scanner
A diagnostic tool for detecting network issues:
- **Loop Detection**: Scans your AE2 network to find cable loops that may cause network instability
- **Non-Chunkloaded Chunks Detection**: Identifies network components in non-chunkloaded chunks
- **Channel Chokepoints**: Finds locations where channel demand exceeds cable capacity, with per-direction flow breakdown
- **Missing Channels**: Lists devices that require a channel but couldn't get one
- **Visual Overlay**: Shows directional arrows pointing to problem locations
- **Interactive GUI**: Browse and select detected issues, organized by dimension

**Usage:**
- Right-click on any network component to start a scan
- Right-click in air to open the results GUI
- Shift-right-click to toggle the overlay display

### Priority Tuner
A tool for quickly setting and applying storage priorities:
- **Set Priority**: Shift-right-click in air to open a GUI and set the stored priority value
- **Apply Priority**: Right-click on any AE2 block to apply the stored priority
- **Auto-Apply**: When held in off-hand, automatically applies stored priority when placing AE2 blocks

**Usage:**
- Shift-right-click in air to open the Priority GUI and set a stored value
- Right-click on AE2 blocks (ME Drives, ME Chests, Buses, etc.) to apply the stored priority
- Hold in off-hand for automatic priority application on block placement

### Cards Distributor
A tool to distribute cards from your inventory to Molecular Assemblers on the network.
Supports:
- **Acceleration Cards** for AE2 Molecular Assemblers


## FAQ

### Will the scanner detect all unloaded chunks in my network?
No, this is an AE2 limitation, due to them not storing grid nodes for unloaded chunks. Detecting them would involve loading neighboring chunks during the scan, which would refresh the whole network and trigger a heavy lag spike over large networks. The scanner can only detect chunks that were loaded at scan time but are not force-loaded. See `NetworkScanner.checkChunkLoaded()` for more details.


## Requirements
- Minecraft 1.12.2
- Forge 14.23.5.2847+
- Applied Energistics 2 Extended Life (AE2-UEL)

## Building
Run:
```bash
./gradlew -q build
```
Resulting jar will be under `build/libs/`.

## License
This project is licensed under the MIT License - see the LICENSE file for details.

## Textures
**SangreBK** : Priority Tuner, Network Health Scanner
