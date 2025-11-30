package com.ossimulator.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ossimulator.process.Burst;
import com.ossimulator.process.BurstType;

public class ConfigParser {
    public static List<com.ossimulator.process.Proceso> parseProcessesFromFile(String filename) throws IOException {
        List<com.ossimulator.process.Proceso> processes = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new FileReader(filename));
        String line;

        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            com.ossimulator.process.Proceso p = parseProcessLine(line);
            if (p != null) {
                processes.add(p);
            }
        }

        reader.close();
        return processes;
    }

    private static com.ossimulator.process.Proceso parseProcessLine(String line) {
        String[] parts = line.split("\\s+");
        if (parts.length < 5) {
            return null;
        }

        String pid = parts[0];
        int arrivalTime = Integer.parseInt(parts[1]);
        String burstsStr = parts[2];
        int priority = Integer.parseInt(parts[3]);
        int pageCount = Integer.parseInt(parts[4]);

        List<Burst> bursts = parseBursts(burstsStr);
        return new com.ossimulator.process.Proceso(pid, arrivalTime, bursts, priority, pageCount);
    }

    private static List<Burst> parseBursts(String burstsStr) {
        List<Burst> bursts = new ArrayList<>();
        Pattern pattern = Pattern.compile("(CPU|E/S)\\((\\d+)\\)");
        Matcher matcher = pattern.matcher(burstsStr);

        while (matcher.find()) {
            String type = matcher.group(1);
            int duration = Integer.parseInt(matcher.group(2));

            BurstType burstType = type.equals("CPU") ? BurstType.CPU : BurstType.IO;
            bursts.add(new Burst(burstType, duration));
        }

        return bursts;
    }

    public static void saveProcessesToFile(String filename, List<com.ossimulator.process.Proceso> processes) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(filename));

        for (com.ossimulator.process.Proceso p : processes) {
            StringBuilder sb = new StringBuilder();
            sb.append(p.getPid()).append(" ");
            sb.append(p.getArrivalTime()).append(" ");

            sb.append(p.getBursts().stream()
                    .map(Burst::toString)
                    .reduce((a, b) -> a + "," + b)
                    .orElse(""));

            sb.append(" ").append(p.getPriority());
            sb.append(" ").append(p.getPageCount());

            writer.write(sb.toString());
            writer.newLine();
        }

        writer.close();
    }
}
