package com.baseProject.myBaseProject.service.impl;

import com.baseProject.myBaseProject.ai.AiService;
import com.baseProject.myBaseProject.constant.PromptConstant;
import com.baseProject.myBaseProject.dto.ai.CvExtractionResult;
import com.baseProject.myBaseProject.service.CvParsingService;
import com.baseProject.myBaseProject.util.pdf.PdfImageRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CvParsingServiceImpl implements CvParsingService {

    private final AiService aiService;
    private final PdfImageRenderer pdfImageRenderer;

    @Override
    public CvExtractionResult parseCvFromPdf(byte[] pdfBytes) {
        log.info("Parsing CV from PDF, size: {} KB", pdfBytes != null ? pdfBytes.length / 1024 : 0);
        long startTime = System.currentTimeMillis();

        try {
            List<byte[]> pageImages = pdfImageRenderer.renderPagesAsImages(pdfBytes);
            if (pageImages.isEmpty()) {
                throw new IllegalArgumentException("PDF file contains no renderable pages");
            }

            String promptTemplate = new ClassPathResource(PromptConstant.CV_EXTRACT_VISION_PROMPT)
                    .getContentAsString(StandardCharsets.UTF_8);
            CvExtractionResult result = aiService.generateStructuredWithImages(
                    promptTemplate, pageImages, CvExtractionResult.class);

            long durationMs = System.currentTimeMillis() - startTime;
            log.info("CV parsed successfully via Vision AI in {} ms", durationMs);
            return result;
        } catch (Exception ex) {
            log.error("Failed to parse CV from PDF via Vision AI: {}", ex.getMessage(), ex);
            throw new RuntimeException("CV parsing failed: " + ex.getMessage(), ex);
        }
    }
}
