package com.baseProject.myBaseProject.jd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baseProject.myBaseProject.config.properites.JobDescriptionProperties;
import com.baseProject.myBaseProject.exception.JobDescriptionContentRequiredException;
import com.baseProject.myBaseProject.exception.JobDescriptionFileCorruptedException;
import com.baseProject.myBaseProject.exception.JobDescriptionFileTooLargeException;
import com.baseProject.myBaseProject.exception.JobDescriptionInvalidFileTypeException;
import com.baseProject.myBaseProject.exception.JobDescriptionInvalidTextException;
import com.baseProject.myBaseProject.exception.JobDescriptionTooManyPagesException;
import com.baseProject.myBaseProject.jd.extraction.PdfJobDescriptionTextExtractor;
import com.baseProject.myBaseProject.jd.extraction.TxtJobDescriptionTextExtractor;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

class JobDescriptionFileProcessorTest {

    private static final long MAX_FILE_SIZE = 10_000;
    private static final int MAX_PAGES = 2;

    private JobDescriptionFileProcessor processor;

    @BeforeEach
    void setUp() {
        JobDescriptionProperties properties =
                new JobDescriptionProperties(10, 5_000, 3, MAX_FILE_SIZE, MAX_PAGES);
        processor = new JobDescriptionFileProcessor(
                properties,
                List.of(
                        new PdfJobDescriptionTextExtractor(properties),
                        new TxtJobDescriptionTextExtractor()));
    }

    @Test
    void processExtractsReadablePdfAndIgnoresDeclaredMime() throws IOException {
        byte[] pdf = pdfWithPages(1, "Java backend responsibilities and requirements");
        MockMultipartFile file = new MockMultipartFile(
                "file", "C:\\fakepath\\backend.PDF", "text/plain", pdf);

        JobDescriptionFileProcessor.ProcessedFile result = processor.process(file);

        assertThat(result.originalFilename()).isEqualTo("backend.PDF");
        assertThat(result.extension()).isEqualTo("pdf");
        assertThat(result.contentType()).isEqualTo("application/pdf");
        assertThat(result.extractedText()).contains("Java backend responsibilities");
        assertThat(result.content()).isEqualTo(pdf);
    }

    @Test
    void processDecodesUtf8TxtAndRemovesBom() {
        byte[] text = "\uFEFFYêu cầu Java và Spring Boot".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "jd.txt", "application/octet-stream", text);

        JobDescriptionFileProcessor.ProcessedFile result = processor.process(file);

        assertThat(result.extension()).isEqualTo("txt");
        assertThat(result.contentType()).isEqualTo("text/plain; charset=UTF-8");
        assertThat(result.extractedText()).isEqualTo("Yêu cầu Java và Spring Boot");
    }

    @Test
    void processRejectsMissingOrEmptyFile() {
        assertThatThrownBy(() -> processor.process(null))
                .isInstanceOf(JobDescriptionContentRequiredException.class);
        assertThatThrownBy(() -> processor.process(
                new MockMultipartFile("file", "jd.txt", "text/plain", new byte[0])))
                .isInstanceOf(JobDescriptionContentRequiredException.class);
    }

    @Test
    void processRejectsFileAboveFeatureLimit() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "jd.txt", "text/plain", new byte[(int) MAX_FILE_SIZE + 1]);

        assertThatThrownBy(() -> processor.process(file))
                .isInstanceOf(JobDescriptionFileTooLargeException.class);
    }

    @Test
    void processRejectsUnsupportedExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "jd.docx", "application/pdf", "%PDF-fake".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> processor.process(file))
                .isInstanceOf(JobDescriptionInvalidFileTypeException.class);
    }

    @Test
    void processRejectsPdfRenamedAsTxt() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "jd.txt", "text/plain", "%PDF-fake".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> processor.process(file))
                .isInstanceOf(JobDescriptionInvalidFileTypeException.class);
    }

    @Test
    void processRejectsPdfExtensionWithoutPdfMagic() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "jd.pdf", "application/pdf", "plain text".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> processor.process(file))
                .isInstanceOf(JobDescriptionInvalidFileTypeException.class);
    }

    @Test
    void processRejectsMalformedUtf8Txt() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "jd.txt", "text/plain", new byte[]{(byte) 0xC3, (byte) 0x28});

        assertThatThrownBy(() -> processor.process(file))
                .isInstanceOf(JobDescriptionInvalidTextException.class);
    }

    @Test
    void processRejectsCorruptedPdf() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "jd.pdf", "application/pdf", "%PDF-corrupted".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> processor.process(file))
                .isInstanceOf(JobDescriptionFileCorruptedException.class);
    }

    @Test
    void processRejectsPdfAbovePageLimit() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "jd.pdf", "application/pdf", pdfWithPages(3, "Page"));

        assertThatThrownBy(() -> processor.process(file))
                .isInstanceOf(JobDescriptionTooManyPagesException.class);
    }

    @Test
    void processRejectsEncryptedPdf() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "jd.pdf", "application/pdf", encryptedPdf());

        assertThatThrownBy(() -> processor.process(file))
                .isInstanceOf(JobDescriptionFileCorruptedException.class);
    }

    private static byte[] pdfWithPages(int pages, String text) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int pageIndex = 0; pageIndex < pages; pageIndex++) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(50, 700);
                    stream.showText(text + " " + pageIndex);
                    stream.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private static byte[] encryptedPdf() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            StandardProtectionPolicy policy = new StandardProtectionPolicy(
                    "owner-password", "user-password", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            document.save(output);
            return output.toByteArray();
        }
    }
}
