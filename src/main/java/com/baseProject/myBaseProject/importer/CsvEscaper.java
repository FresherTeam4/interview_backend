package com.baseProject.myBaseProject.importer;

public final class CsvEscaper {
    private CsvEscaper() {
    }

    public static String escape(String value) {
        String safe = value == null ? "" : value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        return '"' + safe.replace("\"", "\"\"") + '"';
    }
}
