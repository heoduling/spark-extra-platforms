/*
 * This file is part of spark.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.lucko.spark.folia;

import com.google.common.collect.ImmutableSet;
import me.lucko.spark.api.Spark;
import me.lucko.spark.common.SparkPlatform;
import me.lucko.spark.common.SparkPlugin;
import me.lucko.spark.common.metric.Metrics;
import me.lucko.spark.common.monitor.MonitoringExecutor;
import me.lucko.spark.common.monitor.ping.PlayerPingProvider;
import me.lucko.spark.common.monitor.tick.TickStatistics;
import me.lucko.spark.common.platform.PlatformInfo;
import me.lucko.spark.common.platform.serverconfig.ServerConfigProvider;
import me.lucko.spark.common.platform.world.WorldInfoProvider;
import me.lucko.spark.common.sampler.ThreadDumper;
import me.lucko.spark.common.sampler.source.ClassSourceLookup;
import me.lucko.spark.common.sampler.source.SourceMetadata;
import me.lucko.spark.folia.compat.FoliaTickStatisticsPre26;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.stream.Stream;

public class FoliaSparkPlugin extends JavaPlugin implements SparkPlugin {
    private static final String RELOAD_PERMISSION = "spark.reload";

    private final Object lifecycleLock = new Object();

    private ThreadDumper gameThreadDumper;

    private volatile SparkPlatform platform;

    @Override
    public void onEnable() {
        if (!classExists("io.papermc.paper.threadedregions.scheduler.RegionScheduler")) {
            getLogger().severe("This version of spark requires Folia! Please use the regular Bukkit plugin instead.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.gameThreadDumper = new ThreadDumper.Regex(ImmutableSet.of("Folia Region Scheduler Thread #\\d+"));

        enablePlatform();
    }

    @Override
    public void onDisable() {
        synchronized (this.lifecycleLock) {
            try {
                disablePlatform();
            } finally {
                // The shared monitoring executor owns long-lived threads and is not closed by SparkPlatform.
                // Stop it when this classloader is being discarded so a plugin manager can load a fresh copy.
                MonitoringExecutor.INSTANCE.shutdownNow();
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission(RELOAD_PERMISSION)) {
                sender.sendMessage("[spark] You do not have permission to reload spark.");
                return true;
            }

            try {
                reloadPlatform();
                sender.sendMessage("[spark] Reload complete.");
            } catch (RuntimeException | Error e) {
                getLogger().log(Level.SEVERE, "Failed to reload spark", e);
                sender.sendMessage("[spark] Reload failed. See the server log for details.");
            }
            return true;
        }

        SparkPlatform platform = this.platform;
        if (platform == null) {
            sender.sendMessage("[spark] spark is currently reloading.");
            return true;
        }

        platform.executeCommand(new FoliaCommandSender(sender), args);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        SparkPlatform platform = this.platform;
        List<String> completions = platform == null
                ? new ArrayList<>()
                : new ArrayList<>(platform.tabCompleteCommand(new FoliaCommandSender(sender), args));

        if (args.length == 1 && sender.hasPermission(RELOAD_PERMISSION)
                && "reload".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            completions.add("reload");
        }
        return completions;
    }

    @Override
    public String getVersion() {
        return getDescription().getVersion();
    }

    @Override
    public Path getPluginDirectory() {
        return getDataFolder().toPath();
    }

    @Override
    public String getCommandName() {
        return "spark";
    }

    @Override
    public Stream<FoliaCommandSender> getCommandSenders() {
        return Stream.concat(
                getServer().getOnlinePlayers().stream(),
                Stream.of(getServer().getConsoleSender())
        ).map(sender -> new FoliaCommandSender(sender));
    }

    @Override
    public void executeAsync(Runnable task) {
        getServer().getAsyncScheduler().runNow(this, t -> task.run());
    }

    @Override
    public void executeSync(Runnable task) {
        getServer().getGlobalRegionScheduler().execute(this, task);
    }

    @Override
    public void log(Level level, String msg) {
        getLogger().log(level, msg);
    }

    @Override
    public void log(Level level, String msg, Throwable throwable) {
        getLogger().log(level, msg, throwable);
    }

    @Override
    public ThreadDumper getDefaultThreadDumper() {
        return this.gameThreadDumper;
    }

    @Override
    public TickStatistics createTickStatistics(Metrics metrics) {
        if (classExists("ca.spottedleaf.common.time.TickData")) {
            return new FoliaTickStatistics(metrics, getServer());
        }
        return new FoliaTickStatisticsPre26(metrics, getServer());
    }

    @Override
    public ClassSourceLookup createClassSourceLookup() {
        return new FoliaClassSourceLookup();
    }

    @Override
    public Collection<SourceMetadata> getKnownSources() {
        return SourceMetadata.gather(
                Arrays.asList(getServer().getPluginManager().getPlugins()),
                Plugin::getName,
                plugin -> plugin.getDescription().getVersion(),
                plugin -> String.join(", ", plugin.getDescription().getAuthors()),
                plugin -> plugin.getDescription().getDescription()
        );
    }

    @Override
    public PlayerPingProvider createPlayerPingProvider() {
        return new FoliaPlayerPingProvider(getServer());
    }

    @Override
    public ServerConfigProvider createServerConfigProvider() {
        return new FoliaServerConfigProvider();
    }

    @Override
    public WorldInfoProvider createWorldInfoProvider() {
        return new FoliaWorldInfoProvider(this);
    }

    @Override
    public PlatformInfo getPlatformInfo() {
        return new FoliaPlatformInfo(getServer());
    }

    @Override
    public void registerApi(Spark api) {
        getServer().getServicesManager().register(Spark.class, api, this, ServicePriority.Normal);
    }

    private void reloadPlatform() {
        synchronized (this.lifecycleLock) {
            disablePlatform();
            enablePlatform();
        }
    }

    private void enablePlatform() {
        synchronized (this.lifecycleLock) {
            SparkPlatform newPlatform = new SparkPlatform(this);
            try {
                newPlatform.enable();
                this.platform = newPlatform;
            } catch (RuntimeException | Error e) {
                try {
                    newPlatform.disable();
                } catch (RuntimeException | Error cleanupFailure) {
                    e.addSuppressed(cleanupFailure);
                } finally {
                    getServer().getServicesManager().unregisterAll(this);
                }
                throw e;
            }
        }
    }

    private void disablePlatform() {
        SparkPlatform oldPlatform = this.platform;
        this.platform = null;
        try {
            if (oldPlatform != null) {
                oldPlatform.disable();
            }
        } finally {
            // Internal reloads do not pass through the server's plugin-disable cleanup.
            getServer().getServicesManager().unregisterAll(this);
        }
    }

    private static boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

}
