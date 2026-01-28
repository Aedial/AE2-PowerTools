package com.ae2powertools.features.scanner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.translation.I18n;

import appeng.api.networking.IGrid;


/**
 * Manages network scan sessions for players.
 * Each player can have one active scan session.
 */
public class ScanSessionManager {

    private static final Map<UUID, ScanSession> sessions = new HashMap<>();

    /**
     * Represents an active scan session for a player.
     */
    public static class ScanSession {
        private final NetworkScanner scanner;
        private final long startTime;
        private final BlockPos startPos;
        private final int dimension;
        private final String dimensionName;
        private List<IssueLocation> sortedLoopResults = null;
        private List<ChunkLocation> sortedChunkResults = null;

        public ScanSession(NetworkScanner scanner, BlockPos startPos, int dimension, String dimensionName) {
            this.scanner = scanner;
            this.startTime = System.currentTimeMillis();
            this.startPos = startPos;
            this.dimension = dimension;
            this.dimensionName = dimensionName;
        }

        public NetworkScanner getScanner() {
            return scanner;
        }

        public long getStartTime() {
            return startTime;
        }

        public BlockPos getStartPos() {
            return startPos;
        }

        public int getDimension() {
            return dimension;
        }

        public String getDimensionName() {
            return dimensionName;
        }

        /**
         * Get loop results sorted by dimension, then by distance from a position.
         */
        public List<IssueLocation> getSortedLoopResults(BlockPos playerPos, int playerDimension) {
            if (sortedLoopResults == null || !scanner.isComplete()) {
                sortedLoopResults = new ArrayList<>(scanner.getDetectedLoops());

                // Sort: current dimension first, then by distance
                sortedLoopResults.sort((a, b) -> {
                    // Current dimension comes first
                    boolean aCurrentDim = a.getDimension() == playerDimension;
                    boolean bCurrentDim = b.getDimension() == playerDimension;

                    if (aCurrentDim != bCurrentDim) return aCurrentDim ? -1 : 1;

                    // Same dimension group - sort by dimension ID, then by distance
                    if (a.getDimension() != b.getDimension()) return Integer.compare(a.getDimension(), b.getDimension());

                    // Same dimension - sort by distance
                    double distA = a.getDistanceFrom(playerPos);
                    double distB = b.getDistanceFrom(playerPos);

                    return Double.compare(distA, distB);
                });
            }

            return sortedLoopResults;
        }

        /**
         * Get chunk results sorted by dimension, then by distance from a position.
         */
        public List<ChunkLocation> getSortedChunkResults(BlockPos playerPos, int playerDimension) {
            if (sortedChunkResults == null || !scanner.isComplete()) {
                Set<ChunkLocation> unloadedChunks = scanner.getUnloadedChunks();
                sortedChunkResults = new ArrayList<>(unloadedChunks);

                // Sort: current dimension first, then by dimension ID, then by distance
                sortedChunkResults.sort((a, b) -> {
                    // Current dimension comes first
                    boolean aCurrentDim = a.getDimension() == playerDimension;
                    boolean bCurrentDim = b.getDimension() == playerDimension;

                    if (aCurrentDim != bCurrentDim) return aCurrentDim ? -1 : 1;

                    // Same dimension group - sort by dimension ID
                    if (a.getDimension() != b.getDimension()) {
                        return Integer.compare(a.getDimension(), b.getDimension());
                    }

                    // Same dimension - sort by distance
                    double distA = a.getDistanceFrom(playerPos);
                    double distB = b.getDistanceFrom(playerPos);

                    return Double.compare(distA, distB);
                });
            }

            return sortedChunkResults;
        }

        /**
         * Group sorted loop results by dimension for tree display.
         */
        public Map<String, List<IssueLocation>> getGroupedLoopResults(BlockPos playerPos, int playerDimension) {
            Map<String, List<IssueLocation>> grouped = new HashMap<>();

            for (IssueLocation loc : getSortedLoopResults(playerPos, playerDimension)) {
                String dimKey = I18n.translateToLocalFormatted("gui.ae2powertools.scanner.dimension_format", loc.getDimensionName(), loc.getDimension());
                grouped.computeIfAbsent(dimKey, k -> new ArrayList<>()).add(loc);
            }

            return grouped;
        }
    }

    /**
     * Start a new scan session for a player.
     */
    public static void startSession(EntityPlayer player, IGrid grid) {
        UUID playerId = player.getUniqueID();

        // Cancel any existing session
        sessions.remove(playerId);

        NetworkScanner scanner = new NetworkScanner(grid, player.world);
        ScanSession session = new ScanSession(
            scanner,
            player.getPosition(),
            player.world.provider.getDimension(),
            player.world.provider.getDimensionType().getName()
        );
        sessions.put(playerId, session);
    }

    /**
     * Get the active session for a player, if any.
     */
    public static ScanSession getSession(EntityPlayer player) {
        return sessions.get(player.getUniqueID());
    }

    /**
     * Get the active session for a player by UUID.
     */
    public static ScanSession getSession(UUID playerId) {
        return sessions.get(playerId);
    }

    /**
     * End a player's session.
     */
    public static void endSession(EntityPlayer player) {
        sessions.remove(player.getUniqueID());
    }

    /**
     * End a player's session by UUID.
     */
    public static void endSession(UUID playerId) {
        sessions.remove(playerId);
    }

    /**
     * Check if a player has an active session.
     */
    public static boolean hasSession(EntityPlayer player) {
        return sessions.containsKey(player.getUniqueID());
    }

    /**
     * Process pending scan work for all sessions.
     * Call this from server tick handler.
     */
    public static void tickSessions() {
        for (ScanSession session : sessions.values()) {
            if (!session.getScanner().isComplete()) session.getScanner().processBatch();
        }
    }
}
