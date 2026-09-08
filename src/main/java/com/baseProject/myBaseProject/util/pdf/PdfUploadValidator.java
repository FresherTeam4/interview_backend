package com.baseProject.myBaseProject.util.pdf;

import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Slf4j
public final class PdfUploadValidator {
    private static final String PDF_EXTENSION = ".pdf";
    private static final byte[] PDF_MAGIC = "%PDF".getBytes(StandardCharsets.US_ASCII);

    private PdfUploadValidator() {
    }

    public static byte[] validateAndRead(MultipartFile file, Policy policy) {
        if (file == null || file.isEmpty()) {
            throw new DomainException(policy.fileRequired());
        }
        if (file.getSize() > policy.maxFileSizeBytes()) {
            throw new DomainException(
                    policy.fileTooLarge(),
                    "%s file must not exceed %d bytes"
                            .formatted(policy.documentName(), policy.maxFileSizeBytes()));
        }
        requirePdfExtension(file.getOriginalFilename(), policy);

        byte[] content = readFully(file, policy);
        requirePdfMagic(content, policy);
        PdfMetadata metadata = readMetadata(content, policy);
        if (metadata.encrypted() || metadata.pageCount() < 1) {
            throw new DomainException(policy.fileCorrupted());
        }
        if (metadata.pageCount() > policy.maxPages()) {
            throw new DomainException(
                    policy.tooManyPages(),
                    "%s has %d pages; maximum is %d".formatted(
                            policy.documentName(), metadata.pageCount(), policy.maxPages()));
        }
        return content;
    }

    private static void requirePdfExtension(String filename, Policy policy) {
        if (filename == null
                || !filename.toLowerCase(Locale.ROOT).endsWith(PDF_EXTENSION)) {
            throw new DomainException(policy.invalidFileType());
        }
    }

    private static byte[] readFully(MultipartFile file, Policy policy) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new DomainException(policy.fileCorrupted(), exception);
        }
    }

    private static void requirePdfMagic(byte[] content, Policy policy) {
        if (content.length < PDF_MAGIC.length) {
            throw new DomainException(policy.invalidFileType());
        }
        for (int index = 0; index < PDF_MAGIC.length; index++) {
            if (content[index] != PDF_MAGIC[index]) {
                throw new DomainException(policy.invalidFileType());
            }
        }
    }

    private static PdfMetadata readMetadata(byte[] content, Policy policy) {
        try (PDDocument document = Loader.loadPDF(content)) {
            return new PdfMetadata(document.isEncrypted(), document.getNumberOfPages());
        } catch (IOException | RuntimeException exception) {
            log.debug("Cannot open uploaded {} with PDFBox: {}",
                    policy.documentName().toLowerCase(Locale.ROOT), exception.getMessage());
            throw new DomainException(policy.fileCorrupted(), exception);
        }
    }

    public record Policy(
            String documentName,
            long maxFileSizeBytes,
            int maxPages,
            ErrorCode fileRequired,
            ErrorCode invalidFileType,
            ErrorCode fileTooLarge,
            ErrorCode fileCorrupted,
            ErrorCode tooManyPages) {
    }

    private record PdfMetadata(boolean encrypted, int pageCount) {
    }
}
