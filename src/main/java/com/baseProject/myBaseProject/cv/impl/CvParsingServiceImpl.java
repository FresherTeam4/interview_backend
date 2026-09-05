package com.baseProject.myBaseProject.cv.impl;

import com.baseProject.myBaseProject.ai.AiService;
import com.baseProject.myBaseProject.constant.PromptConstant;
import com.baseProject.myBaseProject.cv.CvParsingService;
import com.baseProject.myBaseProject.dto.ai.CvExtractionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class CvParsingServiceImpl implements CvParsingService {

    private final AiService aiService;
    private final String cvPrompt;

    public CvParsingServiceImpl(AiService aiService) throws IOException {
        this.aiService = aiService;
        this.cvPrompt = new ClassPathResource(PromptConstant.CV_EXTRACT_PROMPT)
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @Override
    public CvExtractionResult parseCvFromPdf(byte[] pdfBytes) {
        log.info("Parsing CV from PDF, size: {} KB", pdfBytes != null ? pdfBytes.length / 1024 : 0);
        long startTime = System.currentTimeMillis();

        CvExtractionResult result = aiService.generateStructuredWithPdf(
                cvPrompt, pdfBytes, CvExtractionResult.class);

        long durationMs = System.currentTimeMillis() - startTime;
        log.info("CV parsed successfully from PDF in {} ms", durationMs);

        return result;
    }
}
