package com.baseProject.myBaseProject.jobdescription;

import com.baseProject.myBaseProject.config.properites.JobDescriptionProperties;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.jobdescription.validation.PdfJobDescriptionFileValidator;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfJobDescriptionFileValidatorTest {
    private final PdfJobDescriptionFileValidator validator =
            new PdfJobDescriptionFileValidator(
                    new JobDescriptionProperties(1_000_000, 2, 50, 30_000));

    @Test
    void acceptsReadablePdf() throws IOException {
        byte[] pdf = pdfWithPages(1);
        var file = new MockMultipartFile("file", "backend-jd.pdf", "application/pdf", pdf);

        assertThat(validator.validateAndRead(file)).isEqualTo(pdf);
    }

    @Test
    void rejectsNonPdfContentAndExcessPages() throws IOException {
        var renamedText = new MockMultipartFile(
                "file", "backend-jd.pdf", "application/pdf", "not a pdf".getBytes());
        assertThatThrownBy(() -> validator.validateAndRead(renamedText))
                .isInstanceOfSatisfying(DomainException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.JD_INVALID_FILE_TYPE));

        var tooManyPages = new MockMultipartFile(
                "file", "backend-jd.pdf", "application/pdf", pdfWithPages(3));
        assertThatThrownBy(() -> validator.validateAndRead(tooManyPages))
                .isInstanceOfSatisfying(DomainException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo(ErrorCode.JD_TOO_MANY_PAGES));
    }

    private byte[] pdfWithPages(int pageCount) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int index = 0; index < pageCount; index++) {
                document.addPage(new PDPage());
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
