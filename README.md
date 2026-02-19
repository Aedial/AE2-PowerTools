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

### Better Level Maintainer
A block that automatically maintains item quantities in your AE2 network by scheduling crafting jobs.

**Features:**
- **Multiple Recipes**: Manage an unlimited(-ish) number of items to maintain
- **Target Quantities**: Set the desired quantity for each item (shift-scroll the entry to double/halve quantity quickly)
- **Batch Crafting**: Configure how many items to craft per run
- **Customizable Frequency**: Set check intervals from seconds to days (ctrl-scroll the entry to double/halve the time quickly)
- **CPU Management**: Automatically queues tasks when no CPU is available
- **Status Indicators**: Visual color coding shows the state of each recipe:
  - **Gray**: Recipe is disabled (click the item name or right-click the entry to enable/disable)
  - **No color**: Idle, waiting for next check
  - **Light Blue**: Scheduled to run
  - **Green**: Currently crafting
  - **Yellow**: Stalled (waiting for resources)
  - **Red**: Error (no recipe, no CPU, missing resources)
  - **Purple**: Post-crafting error (no space for output)

**Usage:**
1. Place the Better Level Maintainer block and right-click to open the GUI
2. Click on an empty slot to add a recipe
3. In the modal, click the item slot to select a craftable item from your network
4. Set the target quantity (how many items you want to maintain)
5. Set the batch size (how many to craft at once)
6. Set the frequency (how often to check, e.g., "1h 30m" for every 1.5 hours)
7. The maintainer will automatically craft <batch size> items when quantity falls below target

**Tips:**
- Set batch size high to avoid frequent crafting (you may even set it to several times the target quantity). Be mindful of setting it too high, as it may cause resource shortages or crafting failures if the network can't keep up.
- Use subnets to reduce the load when calculating the recipes, as the load scales with the number of items and patterns in the network.
- Try to keep the recipes simple and avoid long crafting chains, as they are exponentially more expensive to calculate and schedule.
- Prefer longer check intervals. You can batch 10k every 100 minutes instead of 100 every minute, which will be much easier on the network and still keep your stock at the desired level.
- Make sure you have enough CPUs, energy, and crafting resources to keep up with the scheduled tasks, especially if you have many recipes, long recipes, or short check intervals.


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
