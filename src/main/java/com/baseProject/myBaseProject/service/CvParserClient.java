package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.ai.CvParsedPayload;

public interface CvParserClient {

    ParseOutcome parse(byte[] pdfContent);
    record ParseOutcome(
            String rawJson,
            CvParsedPayload payload,
            String modelName,
            String schemaVersion,
            Integer tokenCost,
            Integer durationMs
    ) {
    }
}
