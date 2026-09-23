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
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chinese presentation layer for the English messages supplied by spark-common.
 * Command names, flags, URLs and click actions are intentionally left untouched.
 */
final class ChineseMessageRenderer {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final Map<String, String> TRANSLATIONS = createTranslations();

    private static final Pattern AGE = Pattern.compile("^(?:(\\d+)d\\s*)?(?:(\\d+)h\\s*)?(?:(\\d+)m\\s*)?(?:(\\d+)s\\s*)?ago$");
    private static final Pattern DURATION_TOKEN = Pattern.compile("(?<![A-Za-z])(\\d+)([dhms])(?=\\s|$|[。,.，（，）)])");
    private static final Pattern DURATION_ONLY = Pattern.compile("(?:\\d+[dhms](?:\\s+|$))+");

    private ChineseMessageRenderer() {
    }

    static Component translate(Component message) {
        String original = PLAIN.serialize(message);
        Component translated = translateTree(message);

        List<Component> notes = new ArrayList<>();
        if (original.contains("Memory usage:") && original.contains("Usage at last GC:")) {
            notes.add(Component.text("提示：单次内存占用不能单独证明内存泄漏，请对比多次“最近一次 GC 后占用”的变化趋势。", NamedTextColor.DARK_GRAY));
        }
        if (original.contains("Network usage: (system, last 15m)")
                && (original.contains("lo rx") || original.contains("lo tx"))) {
            notes.add(Component.text("提示：lo 是本机回环接口，通常不代表公网带宽。", NamedTextColor.DARK_GRAY));
        }
        if (original.contains("Garbage Collector statistics") && original.contains("ZGC")) {
            notes.add(Component.text("说明：ZGC 回收周期耗时不等于服务器完整停顿时间。", NamedTextColor.DARK_GRAY));
        }

        for (Component note : notes) {
            translated = translated.append(Component.newline()).append(note);
        }
        return translated;
    }

    private static Component translateTree(Component component) {
        Component translated = component;
        if (component instanceof TextComponent) {
            TextComponent text = (TextComponent) component;
            translated = text.content(translatePlain(text.content()));
        }

        List<Component> children = component.children();
        if (!children.isEmpty()) {
            List<Component> translatedChildren = new ArrayList<>(children.size());
            for (Component child : children) {
                translatedChildren.add(translateTree(child));
            }
            translated = translated.children(translatedChildren);
        }

        HoverEvent<?> hoverEvent = translated.hoverEvent();
        if (hoverEvent != null && hoverEvent.action() == HoverEvent.Action.SHOW_TEXT) {
            @SuppressWarnings("unchecked")
            HoverEvent<Component> textHover = (HoverEvent<Component>) hoverEvent;
            translated = translated.hoverEvent(HoverEvent.showText(translateTree(textHover.value())));
        }
        return translated;
    }

    static String translatePlain(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        if (message.equals("now")) {
            return "刚刚";
        }
        if (message.equals("Player ")) {
            return "玩家 ";
        }
        if (message.equals(" has ")) {
            return " 的延迟为 ";
        }
        if (message.equals("automatically")) {
            return "由 spark 自动";
        }
        if (message.equals("Profiler")) {
            return "性能分析";
        }
        if (message.equals(" included ")) {
            return " 期间包含 ";
        }
        if (message.equals("CONSOLE")) {
            return "控制台";
        }
        if (message.equals("Compressed ")) {
            return "已压缩 ";
        }
        if (message.equals("end of GC pause ")) {
            return "暂停阶段 ";
        }
        if (message.equals("end of GC cycle ")) {
            return "回收周期 ";
        }
        if (message.equals("Young Gen ")) {
            return "年轻代 ";
        }
        if (message.equals("Old Gen ")) {
            return "老年代 ";
        }
        if (message.equals("Metaspace")) {
            return "元空间";
        }
        if (message.equals("Compressed Class Space")) {
            return "压缩类空间";
        }
        if (message.equals("CodeHeap 'profiled nmethods'")) {
            return "代码缓存（已分析的方法）";
        }
        if (message.equals("CodeHeap 'non-profiled nmethods'")) {
            return "代码缓存（未分析的方法）";
        }
        if (message.equals("CodeHeap 'non-nmethods'")) {
            return "代码缓存（非方法代码）";
        }
        String gcCausePrefix = " ms. (cause = ";
        int gcCauseIndex = message.indexOf(gcCausePrefix);
        if (gcCauseIndex >= 0 && message.endsWith(")")) {
            String cause = message.substring(gcCauseIndex + gcCausePrefix.length(), message.length() - 1);
            if (cause.equals("Diagnostic Command")) {
                cause = "诊断命令";
            } else if (cause.equals("Allocation Rate")) {
                cause = "分配速率";
            }
            message = message.substring(0, gcCauseIndex) + " ms（原因：" + cause + "）";
        }
        if (message.endsWith(" ms.")) {
            message = message.substring(0, message.length() - 1) + "。";
        }
        String pingUnavailablePrefix = "Ping data is not available for '";
        if (message.startsWith(pingUnavailablePrefix) && message.endsWith("'.")) {
            return "无法获取玩家“" + message.substring(pingUnavailablePrefix.length(), message.length() - 2) + "”的延迟数据。";
        }
        if (DURATION_ONLY.matcher(message).matches()) {
            return localizeDurationTokens(message);
        }

        Matcher age = AGE.matcher(message);
        if (age.matches()) {
            StringBuilder output = new StringBuilder();
            appendAgePart(output, age.group(1), "天");
            appendAgePart(output, age.group(2), "小时");
            appendAgePart(output, age.group(3), "分钟");
            appendAgePart(output, age.group(4), "秒");
            return output.append("前").toString();
        }

        // This sentence is split into three styled components by spark-common.
        // Handle the dynamic final component before generic fragments such as
        // " has ", which are also used by the ping response.
        String backgroundPrefix = " when spark enabled and has been running in the background for ";
        if (message.startsWith(backgroundPrefix) && message.endsWith(".")) {
            String duration = message.substring(backgroundPrefix.length(), message.length() - 1);
            return "启动，已在后台运行 " + localizeDurationTokens(duration) + "。";
        }

        String translated = message;
        for (Map.Entry<String, String> entry : TRANSLATIONS.entrySet()) {
            translated = translated.replace(entry.getKey(), entry.getValue());
        }

        if (translated.contains("（如果不希望它继续运行，请输入：/") && translated.endsWith(")")) {
            translated = translated.substring(0, translated.length() - 1) + "）";
        }

        if (translated.endsWith(" rx")) {
            translated = translated.substring(0, translated.length() - 3) + " 接收";
        } else if (translated.endsWith(" tx")) {
            translated = translated.substring(0, translated.length() - 3) + " 发送";
        }

        if (translated.startsWith("分析器运行 ") && translated.endsWith(".")) {
            translated = translated.substring(0, translated.length() - 1) + " 后将自动返回结果。";
        } else if (translated.startsWith("当前累计分析时长：") && translated.endsWith(".")) {
            translated = translated.substring(0, translated.length() - 1) + "。";
        } else if (translated.startsWith("预计将在 ") && translated.endsWith(".")) {
            translated = translated.substring(0, translated.length() - 1) + " 后自动结束并上传结果。";
        }

        if (containsDurationContext(translated)) {
            translated = localizeDurationTokens(translated);
            translated = translated
                    .replace(" 天 后", " 天后")
                    .replace(" 小时 后", " 小时后")
                    .replace(" 分 后", " 分后")
                    .replace(" 秒 后", " 秒后");
        }
        return translated;
    }

    private static boolean containsDurationContext(String text) {
        return text.contains("平均间隔")
                || text.contains("运行时间")
                || text.startsWith("分析器运行 ")
                || text.contains("自动结束")
                || text.contains("前启动")
                || text.contains("分析时长")
                || text.contains("持续时间")
                || text.endsWith("前");
    }

    private static String localizeDurationTokens(String text) {
        Matcher matcher = DURATION_TOKEN.matcher(text);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String unit;
            switch (matcher.group(2)) {
                case "d":
                    unit = " 天";
                    break;
                case "h":
                    unit = " 小时";
                    break;
                case "m":
                    unit = " 分";
                    break;
                case "s":
                    unit = " 秒";
                    break;
                default:
                    throw new IllegalStateException("Unexpected duration unit");
            }
            matcher.appendReplacement(output, Matcher.quoteReplacement(matcher.group(1) + unit));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private static void appendAgePart(StringBuilder output, String value, String unit) {
        if (value == null) {
            return;
        }
        if (output.length() > 0) {
            output.append(' ');
        }
        output.append(value).append(unit);
    }

    private static Map<String, String> createTranslations() {
        Map<String, String> translations = new LinkedHashMap<>();

        // Command framework and help.
        add(translations, "A command execution has not completed after ", "一条 spark 命令执行超过 ");
        add(translations, " seconds but there is no executor present. Perhaps the executor shutdown?", " 秒仍未完成，并且找不到执行线程，执行器可能已经关闭。");
        add(translations, " seconds, it *might* be stuck. Trace: \n", " 秒仍未完成，可能已经卡住。线程堆栈：\n");
        add(translations, "If the command subsequently completes without any errors, this warning should be ignored. :)", "如果命令随后正常完成且没有报错，可以忽略此警告。");
        add(translations, "Exception occurred whilst executing a spark command", "执行 spark 命令时发生异常");
        add(translations, "You do not have permission to use this command.", "你没有使用此命令的权限。");
        add(translations, "For full usage information, please go to: ", "完整使用说明：");
        add(translations, " to view usage information.", " 查看完整用法。");
        add(translations, "Expected flag at position ", "第 ");
        add(translations, " but got '", " 个参数应为选项，但实际收到“");
        add(translations, "' instead!", "”。");
        add(translations, "Invalid input for '", "参数“");
        add(translations, "' argument. Please specify a number!", "”的值无效，请输入数字。");
        add(translations, "Run ", "输入 ");

        // Health, TPS, ping, memory, network and disk reports.
        add(translations, "Tick durations (min/med/95%ile/max ms) from last 10s, 1m:", "Tick 耗时（最小值 / 中位数 / 95 分位 / 最大值，单位 ms；最近 10 秒 / 1 分钟）：");
        add(translations, "TPS from last 5s, 10s, 1m, 5m, 15m:", "TPS（最近 5 秒 / 10 秒 / 1 分钟 / 5 分钟 / 15 分钟）：");
        add(translations, "CPU usage from last 10s, 1m, 15m:", "CPU 使用率（最近 10 秒 / 1 分钟 / 15 分钟）：");
        add(translations, "Average Pings (min/med/95%ile/max ms) from now, last 15m:", "延迟统计（最小值 / 中位数 / 95 分位 / 最大值，当前 / 最近 15 分钟）：");
        add(translations, "There is not enough data to show ping averages yet. Please try again later.", "当前样本不足，暂时无法计算平均延迟，请稍后重试。");
        add(translations, "Ping data is not available on this platform.", "当前平台不支持获取延迟数据。");
        add(translations, " ms ping.", " ms。");
        add(translations, "Generating server health report...", "正在采集服务器状态，请稍候……");
        add(translations, "Health Report:", "服务器状态报告已生成，点击下方链接查看：");
        add(translations, "Health report", "服务器状态报告");
        add(translations, "The live viewer is not supported.", "当前环境不支持实时查看器。");
        add(translations, "An error occurred whilst opening the live viewer connection.", "连接实时查看器时发生错误。");
        add(translations, "Error whilst opening live viewer connection", "连接实时查看器时发生错误");
        add(translations, "An error occurred whilst uploading the data. Attempting to save to disk instead.", "上传数据失败，正在改为保存到本地文件。");
        add(translations, "An error occurred whilst uploading the data.", "上传数据时发生错误。");
        add(translations, "An error occurred whilst uploading data", "上传数据时发生错误");
        add(translations, "Non-heap memory usage:", "JVM 非堆内存：");
        add(translations, "Memory usage:", "Java 堆内存（已用 / 上限）：");
        add(translations, "Usage at last GC:", "最近一次 GC 后占用：");
        add(translations, "ZGC Young Generation", "ZGC 年轻代");
        add(translations, "ZGC Old Generation", "ZGC 老年代");
        add(translations, "G1 Old Generation", "G1 老年代");
        add(translations, "G1 Eden Space", "G1 伊甸区");
        add(translations, "G1 Survivor Space", "G1 幸存区");
        add(translations, "G1 Old Gen", "G1 老年代");
        add(translations, "PS Eden Space", "Parallel GC 伊甸区");
        add(translations, "PS Survivor Space", "Parallel GC 幸存区");
        add(translations, "PS Old Gen", "Parallel GC 老年代");
        add(translations, "Par Eden Space", "CMS 伊甸区");
        add(translations, "Par Survivor Space", "CMS 幸存区");
        add(translations, "CMS Old Gen", "CMS 老年代");
        add(translations, " bytes", " 字节");
        add(translations, " pool usage:", " 内存池（已用 / 上限）：");
        add(translations, "Network usage: (system, last 15m)", "系统网络流量（最近 15 分钟平均）：");
        add(translations, " pps ", " 包/秒 ");
        add(translations, "Disk usage:", "磁盘空间（已用 / 总容量）：");
        add(translations, "  (system)", "  （系统）");
        add(translations, "  (process)", "  （Java 进程）");

        // Garbage collection reports and live monitor.
        add(translations, "Calculating GC statistics...", "正在统计垃圾回收数据……");
        add(translations, "Garbage Collector statistics", "垃圾回收（GC）统计");
        add(translations, "ZGC Minor Cycles", "ZGC 次要回收周期");
        add(translations, "ZGC Minor Pauses", "ZGC 次要回收暂停");
        add(translations, "ZGC Major Cycles", "ZGC 主要回收周期");
        add(translations, "ZGC Major Pauses", "ZGC 主要回收暂停");
        add(translations, "G1 Young Generation", "G1 年轻代");
        add(translations, "G1 Concurrent GC", "G1 并发 GC");
        add(translations, "No garbage collectors are reporting data.", "没有垃圾回收器返回统计数据。");
        add(translations, "GC monitor enabled.", "GC 实时监控已开启。");
        add(translations, "GC monitor disabled.", "GC 实时监控已关闭。");
        add(translations, " total collections", " 次回收");
        add(translations, " collections", " 次回收");
        add(translations, " collector:", " 收集器：");
        add(translations, " ms avg", " ms/次");
        add(translations, " avg frequency", "（平均间隔）");
        add(translations, " lasting ", "，耗时 ");
        add(translations, " freed from ", "，释放自 ");
        add(translations, " moved to ", "，转移至 ");

        // Profiler commands and lifecycle.
        add(translations, "Stopping the background profiler before starting... please wait", "正在停止后台分析器并准备新的分析任务，请稍候……");
        add(translations, "The specified timeout is not long enough for accurate results to be formed. Please choose a value greater than 10.", "设定的分析时长过短，无法生成可靠结果，请设置为大于 10 秒。");
        add(translations, "The accuracy of the output will significantly improve when the profiler is able to run for longer periods. Consider setting a timeout value over 30 seconds.", "分析时间越长，结果越准确，建议设置为 30 秒以上。");
        add(translations, "Tick counting is not supported!", "当前平台不支持 Tick 计数。");
        add(translations, "Starting a new profiler, please wait...", "正在启动新的性能分析任务，请稍候……");
        add(translations, "Profiler is now running!", "性能分析器已启动。");
        add(translations, "Allocation Profiler", "内存分配分析器");
        add(translations, "(built-in java)", "（Java 内置采样器）");
        add(translations, "(async)", "（async-profiler）");
        add(translations, "built-in java", "Java 内置采样器");
        add(translations, " is now running!", " 已启动。");
        add(translations, "It will run in the background until it is stopped by an admin.", "分析器将在后台持续运行，直到管理员将其停止。");
        add(translations, "To stop the profiler and upload the results, run:", "停止分析并上传结果，请输入：");
        add(translations, "To view the profiler while it's running, run:", "实时查看正在运行的分析器，请输入：");
        add(translations, "The results will be automatically returned after the profiler has been running for ", "分析器运行 ");
        add(translations, "Profiler operation failed unexpectedly. Error: ", "性能分析器意外失败，错误：");
        add(translations, "Profiler operation failed unexpectedly", "性能分析器意外失败");
        add(translations, "The active profiler has completed! Uploading results...", "性能分析已完成，正在上传结果……");
        add(translations, "The profiler isn't running!", "当前没有正在运行的性能分析器。");
        add(translations, "To start a new one, run:", "启动新的分析任务，请输入：");
        add(translations, "Profiler is already running!", "性能分析器已经在运行。");
        add(translations, "It was started ", "此任务");
        add(translations, "So far, it has profiled for ", "当前累计分析时长：");
        add(translations, "It is due to complete automatically and upload results in ", "预计将在 ");
        add(translations, "To cancel the profiler without uploading the results, run:", "取消分析且不上传结果，请输入：");
        add(translations, "Please provide a client id with '--id <client id>'.", "请使用 --id <client id> 提供客户端 ID。");
        add(translations, "Client connected to the viewer using id '", "已信任连接到查看器的客户端，ID 为“");
        add(translations, "' is now trusted.", "”。");
        add(translations, "Unable to find pending client with id '", "找不到等待授权的客户端，ID 为“");
        add(translations, "There isn't an active profiler running.", "当前没有正在运行的性能分析器。");
        add(translations, "Profiler has been cancelled.", "性能分析已取消。");
        add(translations, "Stopping the profiler & saving results, please wait...", "正在停止性能分析并保存结果，请稍候……");
        add(translations, "Stopping the profiler & uploading results, please wait...", "正在停止性能分析并上传结果，请稍候……");
        add(translations, "Restarted the background profiler. ", "后台性能分析器已重新启动。");
        add(translations, "(If you don't want this to happen, run: /", "（如果不希望它继续运行，请输入：/");
        add(translations, "Profiler stopped & upload complete!", "性能分析已停止，结果上传完成。");
        add(translations, "An error occurred whilst uploading the results. Attempting to save to disk instead.", "上传分析结果失败，正在改为保存到本地文件。");
        add(translations, "Error whilst uploading profiler results", "上传性能分析结果时发生错误");
        add(translations, "Profiler stopped & save complete!", "性能分析已停止，结果保存完成。");
        add(translations, "Data has been written to: ", "数据已写入：");
        add(translations, "You can view the profile file using the web app @ ", "可以使用以下网页工具打开分析文件：");
        add(translations, "Error whilst saving profiler results", "保存性能分析结果时发生错误");
        add(translations, "Profiler live viewer:", "性能分析实时查看器：");
        add(translations, "(NOTE: this link is temporary and will expire after a short period of time. ", "（提示：此链接是临时链接，将在短时间后失效。");
        add(translations, "If you need a link to share with other people (e.g. in a bug report), please use ", "如需生成可分享的链接（例如提交问题报告），请使用 ");
        add(translations, " instead.)", "。）");
        add(translations, "An error occurred whilst opening the live profiler.", "打开性能分析实时查看器时发生错误。");
        add(translations, "Error whilst opening live profiler", "打开性能分析实时查看器时发生错误");
        add(translations, "Starting background profiler...", "正在启动后台性能分析器……");
        add(translations, "Failed to start background profiler.", "后台性能分析器启动失败。");
        add(translations, "It seems the background profiler failed to start when spark was last enabled. Sorry about that!", "spark 上次启用时，后台性能分析器似乎启动失败。");
        add(translations, "In the future, spark will try to use the built-in Java profiling engine instead.", "spark 将改用 Java 内置采样引擎再次尝试。");

        // Heap summary and heap dump operations.
        add(translations, "Running garbage collector...", "正在执行垃圾回收……");
        add(translations, "Creating a new heap dump summary, please wait...", "正在生成堆内存摘要，请稍候……");
        add(translations, "An error occurred whilst inspecting the heap.", "分析堆内存时发生错误。");
        add(translations, "Heap dump summmary output:", "堆内存摘要报告：");
        add(translations, "Heap dump summary written to: ", "堆内存摘要已写入：");
        add(translations, "You can read the heap dump summary file using the viewer web-app - ", "可以使用以下网页工具查看堆内存摘要：");
        add(translations, "Creating a new heap dump, please wait...", "正在生成堆转储文件，请稍候……");
        add(translations, "An error occurred whilst creating a heap dump.", "生成堆转储文件时发生错误。");
        add(translations, "Heap dump written to: ", "堆转储文件已写入：");
        add(translations, "An error occurred whilst compressing the heap dump.", "压缩堆转储文件时发生错误。");
        add(translations, "Compressing heap dump, please wait...", "正在压缩堆转储文件，请稍候……");
        add(translations, "Compression complete: ", "压缩完成：");
        add(translations, "Compressed heap dump written to: ", "压缩后的堆转储文件已写入：");
        add(translations, "An error occurred whilst saving the data.", "保存数据时发生错误。");
        add(translations, "Heap dump summary", "堆内存摘要");
        add(translations, "Heap dump", "堆转储");
        add(translations, " so far... (", "，当前进度 (");
        add(translations, " --> ", " → ");

        // Tick monitoring.
        add(translations, "Tick monitor started. Before the monitor becomes fully active, the server's average tick rate will be calculated over a period of 120 ticks (approx 6 seconds).", "Tick 监控已启动。系统将先用 120 个 Tick（约 6 秒）计算服务器平均 Tick 耗时，随后开始报告异常 Tick。");
        add(translations, "Starting now, any ticks with >", "从现在开始，将报告耗时增幅超过 ");
        add(translations, "% increase in ", "% 的 Tick；比较基准为 ");
        add(translations, "duration compared to the average will be reported.", "的平均耗时。");
        add(translations, "Starting now, any ticks with duration >", "从现在开始，将报告耗时超过 ");
        add(translations, " will be reported.", " 的 Tick。");
        add(translations, "Analysis is now complete.", "基准分析已完成，Tick 异常监控现已生效。");
        add(translations, "Tick monitor disabled.", "Tick 异常监控已关闭。");
        add(translations, "Not supported!", "当前平台不支持此功能。");
        add(translations, "Max: ", "最大值：");
        add(translations, "Min: ", "最小值：");
        add(translations, "Average: ", "平均值：");
        add(translations, "Tick ", "Tick ");
        add(translations, " lasted ", " 耗时 ");
        add(translations, " increase from avg)", " 高于平均值）");
        add(translations, " ms. (type = ", " ms（类型：");

        // Activity log.
        add(translations, "There are no entries present in the log.", "当前没有 spark 操作记录。");
        add(translations, "Unknown page selected. ", "指定的页码不存在，共 ");
        add(translations, " total pages.", " 页。");
        add(translations, "Created by: ", "执行者：");
        add(translations, "Recent spark activity", "最近的 spark 操作记录");
        add(translations, "page no", "页码");
        add(translations, "Profiler (live)", "性能分析（实时）");
        add(translations, "Url: ", "链接：");
        add(translations, "File: ", "文件：");

        // Argument descriptions shown by /spark help. Flags and commands stay English.
        add(translations, "timeout seconds", "超时秒数");
        add(translations, "thread name", "线程名称");
        add(translations, "tick length millis", "Tick 耗时（毫秒）");
        add(translations, "interval millis", "采样间隔（毫秒）");
        add(translations, "percentage increase", "增幅百分比");
        add(translations, "tick duration", "Tick 耗时");
        add(translations, "<username>", "<玩家名>");
        add(translations, "<type>", "<压缩格式>");

        // Runtime, native profiler, viewer and metadata logs.
        add(translations, "The async-profiler engine is not supported for your os/arch (", "当前操作系统或架构不支持 async-profiler（");
        add(translations, "The async-profiler engine is not supported for your JVM (", "当前 JVM 不支持 async-profiler（");
        add(translations, "), so the built-in Java engine will be used instead.", "），将改用 Java 内置采样引擎。");
        add(translations, "Unable to initialise the async-profiler engine because libstdc++ is not installed.", "未安装 libstdc++，无法初始化 async-profiler。");
        add(translations, "Unable to initialise the async-profiler engine: ", "无法初始化 async-profiler：");
        add(translations, "Please see here for more information: ", "更多信息：");
        add(translations, "The allocation profiling mode is not supported on your system. This is most likely because Hotspot debug symbols are not available.", "当前系统不支持内存分配分析，通常是因为缺少 HotSpot 调试符号。");
        add(translations, "To resolve, try installing the 'openjdk-11-dbg' or 'openjdk-8-dbg' package using your OS package manager.", "请尝试使用系统包管理器安装 openjdk-11-dbg 或 openjdk-8-dbg。");
        add(translations, "Allocation profiling is not supported on your system. Check the console for more info.", "当前系统不支持内存分配分析，请查看控制台了解详情。");
        add(translations, "Ignoring sleeping threads is not supported in allocation profiling mode. Sleeping threads will be included in the results.", "内存分配分析模式不支持忽略休眠线程，结果中将包含休眠线程。");
        add(translations, "Failed to stop previous profiler job", "停止上一个性能分析任务时发生错误");
        add(translations, "Failed to measure window statistics", "采集时间窗口统计信息时发生错误");
        add(translations, "Exception occurred while rotating profiler job", "轮换性能分析任务时发生异常");
        add(translations, "A profiler is already running on the ", "当前已有性能分析器运行在 ");
        add(translations, "You need to stop it (using /", "请先使用 /");
        add(translations, " profiler cancel) ", " profiler cancel 停止它，");
        add(translations, "before you can start one on the ", "然后才能在 ");
        add(translations, " side. ", " 端。");
        add(translations, " side.", " 端启动新的分析任务。");
        add(translations, "A profiler is already running.", "当前已有性能分析器正在运行。");
        add(translations, "Profiler job no longer active!", "性能分析任务已不再运行。");
        add(translations, "Exception occurred while sending statistics to viewer", "向查看器发送统计信息时发生异常");
        add(translations, "Error whilst sending updated sampler data to the socket", "通过连接发送最新分析数据时发生错误");
        add(translations, "Error whilst sending updated statistics to the socket", "通过连接发送最新统计数据时发生错误");
        add(translations, "Exception occurred while reading data from the socket", "从连接读取数据时发生异常");
        add(translations, "Exception occurred while sending data to the socket", "通过连接发送数据时发生异常");
        add(translations, "Socket error: ", "连接错误：");
        add(translations, "[Viewer - ", "[查看器 - ");
        add(translations, "Uncaught exception thrown in thread ", "线程发生未捕获异常：");
        add(translations, "No clients have pinged for 30s, closing socket", "客户端已 30 秒无响应，正在关闭连接");
        add(translations, "Unexpected packet: ", "收到非预期数据包：");
        add(translations, "Missing public key", "缺少公钥");
        add(translations, "Client connected: clientId=", "客户端已连接：clientId=");
        add(translations, "Unknown start time for window ", "无法确定统计窗口的开始时间：");
        add(translations, "Exception occurred while getting world info", "获取世界信息时发生异常");
        add(translations, "Timed out waiting for world statistics", "等待世界统计信息超时");
        add(translations, "Failed to gather platform statistics", "收集平台统计信息失败");
        add(translations, "Failed to gather system statistics", "收集系统统计信息失败");
        add(translations, "Failed to gather server configurations", "收集服务端配置失败");
        add(translations, "Failed to gather extra platform metadata", "收集附加平台信息失败");
        add(translations, "Failed to gather world statistics", "收集世界统计信息失败");
        add(translations, "Failed to create ClassSourceLookup", "创建类来源索引失败");
        add(translations, "If you see a warning above that says \"WARNING: A Java agent has been loaded dynamically\", it can be safely ignored.", "如果上方出现“WARNING: A Java agent has been loaded dynamically”警告，可以安全忽略。");
        add(translations, "See here for more information: ", "更多信息：");

        return Collections.unmodifiableMap(translations);
    }

    private static void add(Map<String, String> translations, String english, String chinese) {
        translations.put(english, chinese);
    }
}
