package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.config.properites.CvProperties;
import com.baseProject.myBaseProject.exception.CvFileCorruptedException;
import com.baseProject.myBaseProject.exception.CvFileRequiredException;
import com.baseProject.myBaseProject.exception.CvFileTooLargeException;
import com.baseProject.myBaseProject.exception.CvInvalidFileTypeException;
import com.baseProject.myBaseProject.exception.CvTooManyPagesException;
import com.baseProject.myBaseProject.service.CvFileValidator;

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
            throw new CvFileRequiredException();
        }

        if (file.getSize() > cvProperties.maxFileSizeBytes()) {
            throw new CvFileTooLargeException(cvProperties.maxFileSizeBytes());
        }

        requirePdfExtension(file.getOriginalFilename());

        byte[] content = readFully(file);
        requirePdfMagicBytes(content);
        requireReadablePdf(content);

        return content;
    }

    // check extension
    private void requirePdfExtension(String originalFilename) {
        if (originalFilename == null
                || !originalFilename.toLowerCase(Locale.ROOT).endsWith(PDF_EXTENSION)) {
            throw new CvInvalidFileTypeException();
        }
    }

    // check readable
    private byte[] readFully(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new CvFileCorruptedException(e);
        }
    }

    // every pdf file has first 4 byte is "%PDF", check it to avoid
    // user rename extension
    private void requirePdfMagicBytes(byte[] content) {
        if (content.length < PDF_MAGIC.length) {
            throw new CvInvalidFileTypeException();
        }
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (content[i] != PDF_MAGIC[i]) {
                throw new CvInvalidFileTypeException();
            }
        }
    }

     // open PDF to check readable, no password require, page number in file
    private void requireReadablePdf(byte[] content) {
        boolean encrypted;
        int pages;
        try (PDDocument document = Loader.loadPDF(content)) {
            encrypted = document.isEncrypted();
            pages = document.getNumberOfPages();
        } catch (IOException | RuntimeException e) {
            log.debug("PDFBox không mở được file CV: {}", e.getMessage());
            throw new CvFileCorruptedException(e);
        }

        // zero page
        if (encrypted || pages < 1) {
            throw new CvFileCorruptedException();
        }

        if (pages > cvProperties.maxPages()) {
            throw new CvTooManyPagesException(pages, cvProperties.maxPages());
        }
    }
}
