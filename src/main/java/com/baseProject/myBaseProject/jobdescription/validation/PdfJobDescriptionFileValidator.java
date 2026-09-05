package com.baseProject.myBaseProject.jobdescription.validation;

import com.baseProject.myBaseProject.config.properites.JobDescriptionProperties;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class PdfJobDescriptionFileValidator implements JobDescriptionFileValidator {
    private static final String PDF_EXTENSION = ".pdf";
    private static final byte[] PDF_MAGIC = "%PDF".getBytes(StandardCharsets.US_ASCII);

    private final JobDescriptionProperties properties;

    @Override
    public byte[] validateAndRead(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new DomainException(ErrorCode.JD_FILE_REQUIRED);
        }
        if (file.getSize() > properties.maxFileSizeBytes()) {
            throw new DomainException(ErrorCode.JD_FILE_TOO_LARGE,
                    "Job description file must not exceed %d bytes"
                            .formatted(properties.maxFileSizeBytes()));
        }
        requirePdfExtension(file.getOriginalFilename());
        byte[] content = readFully(file);
        requirePdfMagic(content);
        requireReadablePdf(content);
        return content;
    }

    private void requirePdfExtension(String filename) {
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(PDF_EXTENSION)) {
            throw new DomainException(ErrorCode.JD_INVALID_FILE_TYPE);
        }
    }

    private byte[] readFully(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new DomainException(ErrorCode.JD_FILE_CORRUPTED, exception);
        }
    }

    private void requirePdfMagic(byte[] content) {
        if (content.length < PDF_MAGIC.length) {
            throw new DomainException(ErrorCode.JD_INVALID_FILE_TYPE);
        }
        for (int index = 0; index < PDF_MAGIC.length; index++) {
            if (content[index] != PDF_MAGIC[index]) {
                throw new DomainException(ErrorCode.JD_INVALID_FILE_TYPE);
            }
        }
    }

    private void requireReadablePdf(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            if (document.isEncrypted() || document.getNumberOfPages() < 1) {
                throw new DomainException(ErrorCode.JD_FILE_CORRUPTED);
            }
            if (document.getNumberOfPages() > properties.maxPages()) {
                throw new DomainException(ErrorCode.JD_TOO_MANY_PAGES,
                        "Job description has %d pages; maximum is %d"
                                .formatted(document.getNumberOfPages(), properties.maxPages()));
            }
        } catch (DomainException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            log.debug("Cannot open uploaded job description: {}", exception.getMessage());
            throw new DomainException(ErrorCode.JD_FILE_CORRUPTED, exception);
        }
    }
}
