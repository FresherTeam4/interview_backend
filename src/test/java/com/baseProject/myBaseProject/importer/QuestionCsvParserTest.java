package com.baseProject.myBaseProject.importer;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuestionCsvParserTest {
    private final QuestionCsvParser parser = new QuestionCsvParser();

    @Test
    void parsesUtf8BomQuotedCommasNewlinesAndEscapedQuotes() {
        String csv = "\uFEFF" + String.join(",", QuestionCsvParser.HEADERS) + "\r\n"
                + "\"REST, API là gì?\",\"Line 1\nLine \"\"2\"\"\",JUNIOR,TECHNICAL,EASY,,"
                + "\"BACKEND|DEVOPS\",\"JAVA|SPRING_BOOT\",true\r\n";

        List<QuestionCsvRecord> rows = parser.parse(stream(csv));

        assertEquals(1, rows.size());
        assertEquals(2, rows.get(0).rowNumber());
        assertEquals("REST, API là gì?", rows.get(0).contentVi());
        assertEquals("Line 1\nLine \"2\"", rows.get(0).contentEn());
        assertEquals("BACKEND|DEVOPS", rows.get(0).techStackCodes());
    }

    @Test
    void rejectsAnInvalidHeader() {
        String csv = "wrong,header\r\nvalue,value\r\n";

        assertThrows(QuestionCsvFormatException.class, () -> parser.parse(stream(csv)));
    }

    @Test
    void rejectsAnUnclosedQuotedField() {
        String csv = String.join(",", QuestionCsvParser.HEADERS) + "\r\n\"unclosed";

        assertThrows(QuestionCsvFormatException.class, () -> parser.parse(stream(csv)));
    }

    private static ByteArrayInputStream stream(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
