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

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChineseMessageRendererTest {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void translatesReportTextAndPreservesTheUrlAction() {
        String url = "https://spark.lucko.me/example";
        Component input = Component.text("Health Report:").clickEvent(ClickEvent.openUrl(url));

        Component output = ChineseMessageRenderer.translate(input);

        assertEquals("服务器状态报告已生成，点击下方链接查看：", PLAIN.serialize(output));
        assertEquals(ClickEvent.openUrl(url), output.clickEvent());
    }

    @Test
    void leavesCommandsAndFlagsUnchanged() {
        String command = "/spark healthreport show --memory --network";
        Component input = Component.text(command).clickEvent(ClickEvent.suggestCommand(command));

        Component output = ChineseMessageRenderer.translate(input);

        assertEquals(command, PLAIN.serialize(output));
        assertEquals(ClickEvent.suggestCommand(command), output.clickEvent());
    }

    @Test
    void translatesDynamicReportFragments() {
        assertEquals("ZGC 次要回收周期 收集器：",
                ChineseMessageRenderer.translatePlain("ZGC Minor Cycles collector:"));
        assertEquals("G1 伊甸区 内存池（已用 / 上限）：",
                ChineseMessageRenderer.translatePlain("G1 Eden Space pool usage:"));
        assertEquals("2 分 6 秒（平均间隔）",
                ChineseMessageRenderer.translatePlain("2m 6s avg frequency"));
        assertEquals("lo 接收", ChineseMessageRenderer.translatePlain("lo rx"));
        assertEquals("3天 2小时 5分钟前", ChineseMessageRenderer.translatePlain("3d 2h 5m ago"));
        assertEquals("控制台", ChineseMessageRenderer.translatePlain("CONSOLE"));
        assertEquals("1 分 5 秒", ChineseMessageRenderer.translatePlain("1m 5s"));
        assertEquals("21 秒", ChineseMessageRenderer.translatePlain("21s"));
    }

    @Test
    void translatesSplitGcMonitorEventsAndJvmMemoryPools() {
        Component pause = Component.text("end of GC pause ")
                .append(Component.text("GC"))
                .append(Component.text(" lasting "))
                .append(Component.text("0"))
                .append(Component.text(" ms. (cause = Diagnostic Command)"));

        assertEquals("暂停阶段 GC，耗时 0 ms（原因：诊断命令）",
                PLAIN.serialize(ChineseMessageRenderer.translate(pause)));
        assertEquals("回收周期 GC，耗时 197 ms（原因：分配速率）",
                PLAIN.serialize(ChineseMessageRenderer.translate(Component.text("end of GC cycle ")
                        .append(Component.text("GC"))
                        .append(Component.text(" lasting 197 ms. (cause = Allocation Rate)")))));
        assertEquals("老年代 GC，耗时 59 ms（原因：诊断命令）",
                PLAIN.serialize(ChineseMessageRenderer.translate(Component.text("Old Gen ")
                        .append(Component.text("GC lasting 59 ms. (cause = Diagnostic Command)")))));
        assertEquals("代码缓存（已分析的方法）", ChineseMessageRenderer.translatePlain("CodeHeap 'profiled nmethods'"));
        assertEquals("代码缓存（未分析的方法）", ChineseMessageRenderer.translatePlain("CodeHeap 'non-profiled nmethods'"));
        assertEquals("元空间", ChineseMessageRenderer.translatePlain("Metaspace"));
        assertEquals("压缩类空间", ChineseMessageRenderer.translatePlain("Compressed Class Space"));
        assertEquals("已压缩 ", ChineseMessageRenderer.translatePlain("Compressed "));
    }

    @Test
    void translatesDynamicErrorAndProfilerFeedback() {
        assertEquals("无法获取玩家“NonexistentPlayer”的延迟数据。",
                ChineseMessageRenderer.translatePlain("Ping data is not available for 'NonexistentPlayer'."));
        assertEquals("后台性能分析器已重新启动。（如果不希望它继续运行，请输入：/spark profiler cancel）",
                ChineseMessageRenderer.translatePlain("Restarted the background profiler. (If you don't want this to happen, run: /spark profiler cancel)"));
    }

    @Test
    void translatesSplitBackgroundProfilerSentenceWithoutFragmentCollisions() {
        Component input = Component.text("It was started ")
                .append(Component.text("automatically"))
                .append(Component.text(" when spark enabled and has been running in the background for 2m 6s."));

        assertEquals("此任务由 spark 自动启动，已在后台运行 2 分 6 秒。",
                PLAIN.serialize(ChineseMessageRenderer.translate(input)));
        assertEquals("当前累计分析时长：2 分 6 秒。",
                ChineseMessageRenderer.translatePlain("So far, it has profiled for 2m 6s."));
        assertEquals("预计将在 9 秒后自动结束并上传结果。",
                ChineseMessageRenderer.translatePlain("It is due to complete automatically and upload results in 9s."));
        assertEquals("性能分析任务已不再运行。",
                ChineseMessageRenderer.translatePlain("Profiler job no longer active!"));
        assertEquals("性能分析器已启动。",
                ChineseMessageRenderer.translatePlain("Profiler is now running!"));
        assertEquals("分析器运行 11 秒后将自动返回结果。",
                ChineseMessageRenderer.translatePlain("The results will be automatically returned after the profiler has been running for 11s."));
        assertEquals("（Java 内置采样器）",
                ChineseMessageRenderer.translatePlain("(built-in java)"));
    }

    @Test
    void translatesHelpPlaceholdersWithoutChangingFlags() {
        assertEquals("--player <玩家名>",
                ChineseMessageRenderer.translatePlain("--player <username>"));
        assertEquals("--compress <压缩格式>",
                ChineseMessageRenderer.translatePlain("--compress <type>"));
    }

    @Test
    void translatesHoverTextWithoutChangingItsCommand() {
        String command = "/spark help";
        Component input = Component.text("Run ")
                .append(Component.text(command).clickEvent(ClickEvent.runCommand(command)))
                .append(Component.text(" to view usage information."))
                .hoverEvent(HoverEvent.showText(Component.text("Run /spark help to view usage information.")));

        Component output = ChineseMessageRenderer.translate(input);

        assertEquals("输入 /spark help 查看完整用法。", PLAIN.serialize(output));
        assertEquals("输入 /spark help 查看完整用法。", PLAIN.serialize((Component) output.hoverEvent().value()));
        assertEquals(ClickEvent.runCommand(command), output.children().get(0).clickEvent());
    }

    @Test
    void addsPracticalNotesOncePerReport() {
        Component input = Component.text("Memory usage:\nUsage at last GC:\n")
                .append(Component.text("Network usage: (system, last 15m)\nlo rx"));

        String output = PLAIN.serialize(ChineseMessageRenderer.translate(input));

        assertTrue(output.contains("单次内存占用不能单独证明内存泄漏"));
        assertTrue(output.contains("lo 是本机回环接口"));
        assertEquals(1, count(output, "单次内存占用不能单独证明内存泄漏"));
        assertEquals(1, count(output, "lo 是本机回环接口"));
    }

    private static int count(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
