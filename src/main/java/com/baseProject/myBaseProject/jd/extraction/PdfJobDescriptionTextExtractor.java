package com.baseProject.myBaseProject.jd.extraction;

import com.baseProject.myBaseProject.config.properites.JobDescriptionProperties;
import com.baseProject.myBaseProject.exception.JobDescriptionFileCorruptedException;
import com.baseProject.myBaseProject.exception.JobDescriptionInvalidFileTypeException;
import com.baseProject.myBaseProject.exception.JobDescriptionTooManyPagesException;

import lombok.RequiredArgsConstructor;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class PdfJobDescriptionTextExtractor implements JobDescriptionTextExtractor {

    private static final byte[] PDF_MAGIC = "%PDF".getBytes(StandardCharsets.US_ASCII);

    private final JobDescriptionProperties properties;

    @Override
    public JobDescriptionFileType fileType() {
        return JobDescriptionFileType.PDF;
    }

    @Override
    public String extract(byte[] content) {
        requirePdfMagic(content);

        try (PDDocument document = Loader.loadPDF(content)) {
            if (document.isEncrypted() || document.getNumberOfPages() < 1) {
                throw new JobDescriptionFileCorruptedException();
            }
            if (document.getNumberOfPages() > properties.maxPages()) {
                throw new JobDescriptionTooManyPagesException(
                        document.getNumberOfPages(), properties.maxPages());
            }
            return new PDFTextStripper().getText(document);
        } catch (JobDescriptionFileCorruptedException | JobDescriptionTooManyPagesException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new JobDescriptionFileCorruptedException(e);
        }
    }

    private void requirePdfMagic(byte[] content) {
        if (content.length < PDF_MAGIC.length) {
            throw new JobDescriptionInvalidFileTypeException();
        }
        for (int index = 0; index < PDF_MAGIC.length; index++) {
            if (content[index] != PDF_MAGIC[index]) {
                throw new JobDescriptionInvalidFileTypeException();
            }
        }
    }
}
