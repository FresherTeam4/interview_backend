package com.baseProject.myBaseProject.jobdescription.validation;

import com.baseProject.myBaseProject.config.properites.JobDescriptionProperties;
import com.baseProject.myBaseProject.exception.ErrorCode;
import com.baseProject.myBaseProject.util.pdf.PdfUploadValidator;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class PdfJobDescriptionFileValidator implements JobDescriptionFileValidator {
    private final PdfUploadValidator.Policy policy;

    public PdfJobDescriptionFileValidator(JobDescriptionProperties properties) {
        this.policy = new PdfUploadValidator.Policy(
                "Job description",
                properties.maxFileSizeBytes(),
                properties.maxPages(),
                ErrorCode.JD_FILE_REQUIRED,
                ErrorCode.JD_INVALID_FILE_TYPE,
                ErrorCode.JD_FILE_TOO_LARGE,
                ErrorCode.JD_FILE_CORRUPTED,
                ErrorCode.JD_TOO_MANY_PAGES);
    }

    @Override
    public byte[] validateAndRead(MultipartFile file) {
        return PdfUploadValidator.validateAndRead(file, policy);
    }
}
