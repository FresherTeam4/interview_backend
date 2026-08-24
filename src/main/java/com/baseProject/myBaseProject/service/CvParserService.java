package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.cv.CvParseOutcome;

/** Gọi AI đọc file CV PDF và trả về hồ sơ có cấu trúc. */
public interface CvParserService {

    CvParseOutcome parse(byte[] pdfBytes);
}
