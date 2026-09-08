package com.baseProject.myBaseProject.cv.validation;

import com.baseProject.myBaseProject.config.properites.CvProperties;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.util.pdf.PdfUploadValidator;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PdfBoxCvFileValidator implements CvFileValidator {
    private final PdfUploadValidator.Policy policy;

    public PdfBoxCvFileValidator(CvProperties properties) {
        this.policy = new PdfUploadValidator.Policy(
                "CV",
                properties.maxFileSizeBytes(),
                properties.maxPages(),
                ErrorCode.CV_FILE_REQUIRED,
                ErrorCode.CV_INVALID_FILE_TYPE,
                ErrorCode.CV_FILE_TOO_LARGE,
                ErrorCode.CV_FILE_CORRUPTED,
                ErrorCode.CV_TOO_MANY_PAGES);
    }

    @Override
    public byte[] validateAndRead(MultipartFile file) {
        return PdfUploadValidator.validateAndRead(file, policy);
    }
}
