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

import me.lucko.spark.common.metric.Metrics;
import org.bukkit.Server;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FoliaTickStatisticsTest {
    @Test
    void ignoresHaltedSchedulerDuringMetricCollection() {
        FoliaTickStatistics statistics = failingStatistics(false, "Scheduler halted");
        try {
            assertDoesNotThrow(statistics::collectMetrics);
        } finally {
            statistics.close();
        }
    }

    @Test
    void doesNotHideOtherCollectionFailures() {
        FoliaTickStatistics statistics = failingStatistics(false, "Unexpected failure");
        try {
            assertThrows(IllegalStateException.class, statistics::collectMetrics);
        } finally {
            statistics.close();
        }
    }

    @Test
    void doesNotSampleAfterServerStops() {
        FoliaTickStatistics statistics = failingStatistics(true, "Unexpected failure");
        try {
            assertDoesNotThrow(statistics::collectMetrics);
        } finally {
            statistics.close();
        }
    }

    private static FoliaTickStatistics failingStatistics(boolean stopping, String message) {
        Server server = (Server) Proxy.newProxyInstance(
                Server.class.getClassLoader(),
                new Class<?>[]{Server.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("isStopping")) {
                        return stopping;
                    }
                    throw new AssertionError("Unexpected Server call: " + method.getName());
                });
        return new FoliaTickStatistics(new Metrics(), server) {
            @Override
            public double tps10Sec() {
                throw new IllegalStateException(message);
            }
        };
    }
}
