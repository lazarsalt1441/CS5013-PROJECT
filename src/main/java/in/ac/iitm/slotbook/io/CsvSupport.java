package in.ac.iitm.slotbook.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal RFC-4180-compatible reader for the small administrative fixtures. */
final class CsvSupport {
    private CsvSupport() {}

    static List<Map<String, String>> read(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        if (lines.isEmpty()) return List.of();
        List<String> headers = parseLine(lines.get(0));
        List<Map<String, String>> rows = new ArrayList<>();
        for (int lineNumber = 1; lineNumber < lines.size(); lineNumber++) {
            String line = lines.get(lineNumber);
            if (line.isBlank()) continue;
            List<String> values = parseLine(line);
            if (values.size() != headers.size()) {
                throw new IOException(path + ": line " + (lineNumber + 1)
                        + " has " + values.size() + " values; expected " + headers.size());
            }
            Map<String, String> row = new LinkedHashMap<>();
            for (int column = 0; column < headers.size(); column++) {
                row.put(headers.get(column).trim(), values.get(column).trim());
            }
            rows.add(row);
        }
        return rows;
    }

    static List<String> parseLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char character = line.charAt(i);
            if (character == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        values.add(current.toString());
        return values;
    }

    static String required(Map<String, String> row, String key, Path path) throws IOException {
        String value = row.get(key);
        if (value == null || value.isBlank()) {
            throw new IOException(path + ": missing required value " + key + " in " + row);
        }
        return value;
    }
}

