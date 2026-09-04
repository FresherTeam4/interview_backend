package com.baseProject.myBaseProject.cv.impl;

import com.baseProject.myBaseProject.ai.AiService;
import com.baseProject.myBaseProject.config.properites.CvProperties;
import com.baseProject.myBaseProject.constant.PromptConstant;
import com.baseProject.myBaseProject.cv.CvParsingService;
import com.baseProject.myBaseProject.dto.ai.CvExtractionResult;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.util.pdf.PdfImageRenderer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CvParsingServiceImpl implements CvParsingService {

    private final AiService aiService;
    private final PdfImageRenderer pdfImageRenderer;
    private final CvProperties cvProperties;
    private String cvVisionPrompt;

    @PostConstruct
    public void init() throws IOException {
        // nạp trước prompt từ resources lúc khởi động bean
        this.cvVisionPrompt = new ClassPathResource(PromptConstant.CV_EXTRACT_VISION_PROMPT)
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @Override
    public CvExtractionResult parseCvFromPdf(byte[] pdfBytes) {
        log.info("Parsing CV from PDF, size: {} KB", pdfBytes != null ? pdfBytes.length / 1024 : 0);
        long startTime = System.currentTimeMillis();

        List<byte[]> pageImages = pdfImageRenderer.renderPagesAsImages(
                pdfBytes, cvProperties.maxPages(), 150);
        if (pageImages.isEmpty()) {
            throw new DomainException(ErrorCode.CV_PARSE_FAILED, "PDF file contains no renderable pages");
        }

        CvExtractionResult result = aiService.generateStructuredWithImages(
                cvVisionPrompt, pageImages, CvExtractionResult.class);

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("CV parsed successfully via Vision AI in {} ms", durationMs);

        return result;
    }
}
