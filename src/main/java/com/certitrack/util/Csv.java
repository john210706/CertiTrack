package com.certitrack.util;

import java.io.*;
import java.util.Arrays;
import java.util.stream.Collectors;

public final class Csv {
    private Csv() {}

    public static void row(Writer writer, Object... values) throws IOException {
        writer.write(Arrays.stream(values).map(Csv::cell).collect(Collectors.joining(",")));
        writer.write("\r\n");
    }

    private static String cell(Object value) {
        String text = value == null ? "" : value.toString();
        String start = text.stripLeading();
        // Spreadsheet programs must treat formula-looking input as text.
        if (!start.isEmpty() && "=+-@".indexOf(start.charAt(0)) >= 0) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
