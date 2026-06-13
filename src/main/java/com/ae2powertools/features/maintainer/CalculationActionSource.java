package com.ae2powertools.features.maintainer;

import java.util.Optional;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayerFactory;

import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;


/**
 * Action source used ONLY when calling {@code ICraftingGrid.beginCraftingJob(...)}.
 *
 * Why this exists:
 *   AE2's {@code CraftingJob.handlePausing()} contains a latent bug where the
 *   {@code craftingTreeWatch} stopwatch is stopped during a pause but never restarted on
 *   resume; the next call to {@code stop()} after {@code request()} completes then throws
 *   {@code IllegalStateException: This stopwatch is already stopped}, which surfaces here
 *   as "Failed to calculate crafting job". The pause/resume code path is gated entirely on
 *   {@code !actionSrc.player().isPresent()}, presenting a player short-circuits the buggy
 *   branch without altering AE2's calculation result.
 *
 * What this source does:
 *   - {@code player()} returns a Forge fake player only while AE2 evaluates
 *     {@code handlePausing()}, so the buggy AE2 pause/resume branch is skipped without making
 *     the whole calculation look like an intentional player craft to other integrations.
 *   - {@code machine()} still returns the real tile so any pattern provider that introspects
 *     the source via {@code machine()} sees the maintainer.
 *
 * What this source does NOT do:
 *   - It is NOT used for {@code submitJob}, {@code injectCraftedItems}, or any real network
 *     extraction. Those keep using the original {@link appeng.me.helpers.MachineSource}, so
 *     AE2 security/permission checks on actual item movement remain unchanged.
 *
 * Trade-off:
 *   With a player source, AE2 will not yield the calculation thread between ticks. Big
 *   crafting trees burn one worker thread continuously instead of cooperatively pausing.
 *   This is acceptable because the maintainer already caps concurrent calculations and
 *   enforces a hard timeout; the cost is bounded.
 */
public class CalculationActionSource implements IActionSource {

    private static final String CRAFTING_JOB_CLASS_NAME = "appeng.crafting.CraftingJob";
    private static final String HANDLE_PAUSING_METHOD_NAME = "handlePausing";

    private final IActionHost machine;
    private final WorldServer world;

    public CalculationActionSource(IActionHost machine, WorldServer world) {
        this.machine = machine;
        this.world = world;
    }

    @Override
    public Optional<EntityPlayer> player() {
        if (!isAe2HandlePausingCheck()) return Optional.empty();

        // FakePlayerFactory.getMinecraft returns a stable, reusable fake player tied to the
        // world. We intentionally accept the cost of producing one each call, the factory
        // caches internally and beginCraftingJob is not on a hot path.
        EntityPlayer fake = FakePlayerFactory.getMinecraft(world);

        return Optional.ofNullable(fake);
    }

    private boolean isAe2HandlePausingCheck() {
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            if (CRAFTING_JOB_CLASS_NAME.equals(element.getClassName())
                    && HANDLE_PAUSING_METHOD_NAME.equals(element.getMethodName())) {
                return true;
            }
        }

        return false;
    }

    @Override
    public Optional<IActionHost> machine() {
        return Optional.ofNullable(machine);
    }

    @Override
    public <T> Optional<T> context(Class<T> key) {
        return Optional.empty();
    }
}
