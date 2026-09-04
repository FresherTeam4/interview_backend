package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.ai.CvExtractionResult;

public interface CvParsingService {

    // bóc tách thông tin CV từ file PDF qua Vision AI
    CvExtractionResult parseCvFromPdf(byte[] pdfBytes);
}
