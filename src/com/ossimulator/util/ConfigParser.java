package com.ossimulator.util;

import com.ossimulator.process.Burst;
import com.ossimulator.process.BurstType;
import com.ossimulator.process.Proceso;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ConfigParser
 *
 * Utilidades para leer/escribir ficheros de procesos en formato simple.
 *
 * Formato por línea:
 * PID ARRIVAL BURSTS PRIORITY PAGECOUNT
 *
 * Ejemplo de bursts: CPU(5),E/S(3),CPU(4)
 *
 * Los métodos usan try-with-resources para garantizar el cierre de streams.
 */
public final class ConfigParser {

    private static final Pattern BURST_PATTERN = Pattern.compile("(CPU|E/S)\\((\\d+)\\)");

    private ConfigParser() {
    }

    /**
     * Parsea una lista de procesos desde un fichero.
     *
     * @param filename ruta del fichero a leer
     * @return lista de procesos parseados
     * @throws IOException si ocurre un error de I/O o de formato numérico
     */
    public static List<Proceso> parseProcessesFromFile(String filename) throws IOException {
        List<Proceso> processes = new ArrayList<>();
        Path p = Path.of(filename);

        try (BufferedReader reader = Files.newBufferedReader(p)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                Proceso proc = parseProcessLine(line);
                if (proc != null) {
                    processes.add(proc);
                }
            }
        }

        return processes;
    }

    /**
     * Parsea una línea que describe un proceso.
     *
     * @param line línea con formato: PID ARRIVAL BURSTS PRIORITY PAGECOUNT
     * @return Proceso creado o null si la línea no es válida
     * @throws IOException si hay error de formato en números
     */
    private static Proceso parseProcessLine(String line) throws IOException {
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
        return new Proceso(pid, arrivalTime, bursts, priority, pageCount);
    }

    /**
     * Parsea la representación compacta de ráfagas.
     *
     * @param burstsStr ejemplo: "CPU(5),E/S(3),CPU(2)"
     * @return lista de Burst (posiblemente vacía)
     */
    private static List<Burst> parseBursts(String burstsStr) {
        List<Burst> bursts = new ArrayList<>();
        if (burstsStr == null || burstsStr.isBlank()) {
            return bursts;
        }

        Matcher matcher = BURST_PATTERN.matcher(burstsStr);
        while (matcher.find()) {
            String type = matcher.group(1);
            int duration = Integer.parseInt(matcher.group(2));
            BurstType burstType = "CPU".equals(type) ? BurstType.CPU : BurstType.IO;
            bursts.add(new Burst(burstType, duration));
        }

        return bursts;
    }
}
