package com.baseProject.myBaseProject.cv.validation;

import com.baseProject.myBaseProject.config.properites.CvProperties;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfBoxCvFileValidator implements CvFileValidator {

    private static final String PDF_EXTENSION = ".pdf";
    private static final byte[] PDF_MAGIC = "%PDF".getBytes(StandardCharsets.US_ASCII);

    private final CvProperties cvProperties;

    @Override
    public byte[] validateAndRead(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new DomainException(ErrorCode.CV_FILE_REQUIRED);
        }

        if (file.getSize() > cvProperties.maxFileSizeBytes()) {
            throw new DomainException(
                    ErrorCode.CV_FILE_TOO_LARGE,
                    "CV file must not exceed %d bytes".formatted(cvProperties.maxFileSizeBytes()));
        }

        requirePdfExtension(file.getOriginalFilename());

        byte[] content = readFully(file);
        // Kiểm tra chữ ký thật của PDF để chặn file khác chỉ được đổi đuôi thành .pdf.
        requirePdfMagicBytes(content);
        // PDFBox xác nhận file mở được, không mã hóa và nằm trong giới hạn số trang.
        requireReadablePdf(content);
        return content;
    }

    private void requirePdfExtension(String originalFilename) {
        if (originalFilename == null
                || !originalFilename.toLowerCase(Locale.ROOT).endsWith(PDF_EXTENSION)) {
            throw new DomainException(ErrorCode.CV_INVALID_FILE_TYPE);
        }
    }

    private byte[] readFully(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new DomainException(ErrorCode.CV_FILE_CORRUPTED, e);
        }
    }

    private void requirePdfMagicBytes(byte[] content) {
        if (content.length < PDF_MAGIC.length) {
            throw new DomainException(ErrorCode.CV_INVALID_FILE_TYPE);
        }

        for (int index = 0; index < PDF_MAGIC.length; index++) {
            if (content[index] != PDF_MAGIC[index]) {
                throw new DomainException(ErrorCode.CV_INVALID_FILE_TYPE);
            }
        }
    }

    private void requireReadablePdf(byte[] content) {
        boolean encrypted;
        int pageCount;
        try (PDDocument document = Loader.loadPDF(content)) {
            encrypted = document.isEncrypted();
            pageCount = document.getNumberOfPages();
        } catch (IOException | RuntimeException e) {
            log.debug("Cannot open uploaded CV with PDFBox: {}", e.getMessage());
            throw new DomainException(ErrorCode.CV_FILE_CORRUPTED, e);
        }

        if (encrypted || pageCount < 1) {
            throw new DomainException(ErrorCode.CV_FILE_CORRUPTED);
        }

        if (pageCount > cvProperties.maxPages()) {
            throw new DomainException(
                    ErrorCode.CV_TOO_MANY_PAGES,
                    "CV has %d pages; maximum is %d".formatted(pageCount, cvProperties.maxPages()));
        }
    }
}
