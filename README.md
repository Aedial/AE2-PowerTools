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

### AutoCrafter
A powerful automation block that automatically crafts items using patterns from your AE2 network.

**Features:**
- **12 Recipe Slots**: Configure up to 12 different recipes
- **Pattern Support**: Insert any AE2 crafting pattern to define the recipe
- **Recipe Preview**: Visual 3x3 input grid showing required ingredients and output
- **Catalyst Inventory**: 9-slot internal inventory per recipe for reusable/duplication items, or intermediary tools with durability (if the last recipe didn't completely consume them)
- **Performance Optimization**: Caches recipe simulations and only recalculates when necessary (when recipe changes)
- **Batch Crafting**: Configure how many items to craft per operation, while decreasing the crafting speed accordingly. A batch size of 50x means 50x the inputs, for 50x the output, but it will run 1/50th of the speed, so it will still consume the same amount of resources per second, just in bigger bursts. This is ideal if you want to run less frequently, while still getting the same overall throughput. A Crafter Speed Upgrade card (tier I/II/III/IV) can be used to increase this batch size even further, without speed penalty (the batch size is increased instead of the speed to avoid spamming the network, which causes lag). The base batch per operation can be set in config.
- **Speed Control**: Set crafting interval from 1 second to days (1s, 1m, 1h, 1d increments). Note: this explicitly slows down crafting. You may want to use "Batch size" instead, to craft more items per operation without slowing down the overall crafting speed.
- **Network Integration**: Automatically extracts inputs from ME storage and inserts outputs back
- **State Indicators**: Color-coded status for each recipe entry:
  - **Gray (Disabled)**: Recipe is disabled
  - **No color (Idle)**: Waiting for next craft cycle
  - **Orange (Missing Catalyst)**: Required catalyst items (inserted manually in the internal inventory)
  - **Red (Missing Input)**: Required ingredients not available in network (couldn't even make a single item)
  - **Purple (No Output Space)**: Network storage full
  - **Yellow (Simulation Failed)**: Recipe simulation failed
  - **Blue (Holding Output)**: Waiting to insert output into network
- **Overview Mode**: See all 12 recipes at a glance on a single page
- **Efficiency statistics**: The error rate and occupancy (since world load) of each recipe is tracked and displayed in the GUI, to help identify bottlenecks or resource shortages
- **Fake Player Crafting**: Compatible with mods that require player context for crafting

**Usage:**
1. Place the AutoCrafter block and right-click to open the GUI
2. Insert a crafting pattern into the pattern slot
3. The recipe preview will show required inputs and output
4. Add the required reusable/duplication items to the 9-slot internal inventory
5. Configure batch size (how many to craft per cycle) via the Batch button
6. Configure speed (base crafting interval) via the Speed button
7. Toggle entries on/off by clicking the state indicator
8. Use page navigation or Overview button to switch between recipes

**Tips:**
- Use larger batch size to avoid querying the network too often. Do note a very large batch size may cause resource shortages or crafting failures if the network can't keep up with the demand, but the recipes should automatically scale down the batch size if resources are insufficient.
- The Overview mode lets you quickly check the status of all 12 recipes
- Shift-click on +/- buttons in Speed GUI for 10x increments

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
