package com.baseProject.myBaseProject.importer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class QuestionCsvParser {
    public static final List<String> HEADERS = List.of(
            "content_vi",
            "content_en",
            "level",
            "question_type",
            "difficulty",
            "company_ref",
            "tech_stack_codes",
            "technology_codes",
            "active"
    );
    public static final int MAX_ROWS = 5_000;
    private static final int MAX_FIELD_CHARACTERS = 100_000;

    public List<QuestionCsvRecord> parse(InputStream inputStream) {
        if (inputStream == null) {
            throw new QuestionCsvFormatException("CSV file is required");
        }
        try (PushbackReader reader = new PushbackReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8),
                2
        )) {
            List<List<String>> records = readRecords(reader);
            if (records.isEmpty()) {
                throw new QuestionCsvFormatException("CSV file is empty");
            }
            validateHeader(records.get(0));
            List<QuestionCsvRecord> result = new ArrayList<>();
            for (int index = 1; index < records.size(); index++) {
                List<String> values = records.get(index);
                if (isBlankRecord(values)) {
                    continue;
                }
                if (values.size() != HEADERS.size()) {
                    throw new QuestionCsvFormatException(
                            "CSV row %d has %d columns; expected %d"
                                    .formatted(index + 1, values.size(), HEADERS.size())
                    );
                }
                if (result.size() >= MAX_ROWS) {
                    throw new QuestionCsvFormatException(
                            "CSV must not contain more than %d data rows".formatted(MAX_ROWS)
                    );
                }
                result.add(new QuestionCsvRecord(
                        index + 1,
                        values.get(0),
                        values.get(1),
                        values.get(2),
                        values.get(3),
                        values.get(4),
                        values.get(5),
                        values.get(6),
                        values.get(7),
                        values.get(8)
                ));
            }
            if (result.isEmpty()) {
                throw new QuestionCsvFormatException("CSV does not contain any data rows");
            }
            return result;
        } catch (IOException ex) {
            throw new QuestionCsvFormatException("Cannot read CSV file: " + ex.getMessage());
        }
    }

    private static List<List<String>> readRecords(PushbackReader reader) throws IOException {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldStarted = false;
        boolean closedQuote = false;
        boolean firstCharacter = true;

        int value;
        while ((value = reader.read()) != -1) {
            char character = (char) value;
            if (firstCharacter) {
                firstCharacter = false;
                if (character == '\uFEFF') {
                    continue;
                }
            }

            if (inQuotes) {
                if (character == '"') {
                    int next = reader.read();
                    if (next == '"') {
                        append(field, '"');
                    } else {
                        inQuotes = false;
                        closedQuote = true;
                        if (next != -1) {
                            reader.unread(next);
                        }
                    }
                } else {
                    append(field, character);
                }
                continue;
            }

            if (closedQuote && character != ',' && character != '\r' && character != '\n') {
                throw new QuestionCsvFormatException(
                        "Unexpected character after a closing quote in CSV"
                );
            }
            if (character == '"') {
                if (fieldStarted || field.length() > 0) {
                    throw new QuestionCsvFormatException("Quote must start at the beginning of a CSV field");
                }
                inQuotes = true;
                fieldStarted = true;
            } else if (character == ',') {
                record.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
                closedQuote = false;
            } else if (character == '\r' || character == '\n') {
                if (character == '\r') {
                    int next = reader.read();
                    if (next != '\n' && next != -1) {
                        reader.unread(next);
                    }
                }
                record.add(field.toString());
                records.add(record);
                record = new ArrayList<>();
                field.setLength(0);
                fieldStarted = false;
                closedQuote = false;
            } else {
                append(field, character);
                fieldStarted = true;
            }
        }

        if (inQuotes) {
            throw new QuestionCsvFormatException("CSV contains an unclosed quoted field");
        }
        if (fieldStarted || closedQuote || field.length() > 0 || !record.isEmpty()) {
            record.add(field.toString());
            records.add(record);
        }
        return records;
    }

    private static void append(StringBuilder field, char value) {
        if (field.length() >= MAX_FIELD_CHARACTERS) {
            throw new QuestionCsvFormatException("A CSV field is too large");
        }
        field.append(value);
    }

    private static void validateHeader(List<String> actualHeader) {
        List<String> normalized = actualHeader.stream().map(String::trim).toList();
        if (!normalized.equals(HEADERS)) {
            throw new QuestionCsvFormatException(
                    "CSV header is invalid. Expected: " + String.join(",", HEADERS)
            );
        }
    }

    private static boolean isBlankRecord(List<String> values) {
        return values.stream().allMatch(value -> value == null || value.isBlank());
    }
}
