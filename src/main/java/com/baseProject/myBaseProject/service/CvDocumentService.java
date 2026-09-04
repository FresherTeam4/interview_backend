package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.cv.CvDocumentResponse;
import org.springframework.web.multipart.MultipartFile;

public interface CvDocumentService {

    CvUploadResult upload(Long userId, MultipartFile file);

    record CvUploadResult(CvDocumentResponse document, boolean reusedExisting) {
    }
}
