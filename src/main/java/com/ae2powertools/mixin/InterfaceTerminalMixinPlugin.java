package com.ae2powertools.mixin;

import java.io.File;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Optional;

import com.ae2powertools.Tags;

import zone.rong.mixinbooter.ILateMixinLoader;

/**
 * Late mixin loader for optional Interface Terminal integrations.
 * <p>
 * The targets are AE2 mod classes, so they belong on the late-mixin path rather than the
 * early Mixin config plugin path. The config file is still read directly here because Forge's
 * {@code @Config} instances are not initialized yet when MixinBooter asks whether to queue the
 * mixin config.
 */
@Optional.Interface(iface = "zone.rong.mixinbooter.ILateMixinLoader", modid = "mixinbooter")
public class InterfaceTerminalMixinPlugin implements ILateMixinLoader {

    private static final Logger LOGGER = LogManager.getLogger(Tags.MODID);

    private static final String CATEGORY_INTERFACE_TERMINAL = "server.interfaceTerminal";
    private static final String KEY_ENABLE_AUTO_CRAFTER_PATTERN_ROWS = "enableAutoCrafterPatternRows";

    private static Boolean cachedEnabled;

    @Override
    @Optional.Method(modid = "mixinbooter")
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.ae2powertools.json");
    }

    @Override
    @Optional.Method(modid = "mixinbooter")
    public boolean shouldMixinConfigQueue(String mixinConfig) {
        return isEnabled();
    }

    private static boolean isEnabled() {
        if (cachedEnabled != null) return cachedEnabled;

        File configFile = resolveConfigFile();
        if (!configFile.isFile()) {
            cachedEnabled = Boolean.TRUE;
            return true;
        }

        try {
            Configuration configuration = new Configuration(configFile);
            configuration.load();
            cachedEnabled = configuration.get(
                CATEGORY_INTERFACE_TERMINAL,
                KEY_ENABLE_AUTO_CRAFTER_PATTERN_ROWS,
                true
            ).getBoolean(true);
        } catch (Exception e) {
            LOGGER.warn("Failed to read early Interface Terminal mixin config from {}. Using defaults.", configFile, e);
            cachedEnabled = Boolean.TRUE;
        }

        return cachedEnabled;
    }

    private static File resolveConfigFile() {
        File minecraftHome = Launch.minecraftHome;
        File configDir = minecraftHome != null ? new File(minecraftHome, "config") : new File("config");
        return new File(new File(configDir, Tags.MODID), "server.cfg");
    }
}