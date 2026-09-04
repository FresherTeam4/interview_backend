package com.baseProject.myBaseProject.service;

import com.baseProject.myBaseProject.dto.cv.CvDocumentResponse;
import com.baseProject.myBaseProject.dto.cv.CvFileUrlResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface CvDocumentService {

    CvUploadResult upload(Long userId, MultipartFile file);

    List<CvDocumentResponse> list(Long userId);

    CvDocumentResponse get(Long userId, Long cvId);

    CvFileUrlResponse fileUrl(Long userId, Long cvId);

    record CvUploadResult(CvDocumentResponse document, boolean reusedExisting) {
    }
}
