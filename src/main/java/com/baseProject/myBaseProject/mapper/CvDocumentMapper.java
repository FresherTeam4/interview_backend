package com.baseProject.myBaseProject.mapper;

import com.baseProject.myBaseProject.dto.cv.CvDocumentResponse;
import com.baseProject.myBaseProject.entity.CandidateProfile;
import com.baseProject.myBaseProject.entity.CvDocument;
import org.springframework.stereotype.Component;

@Component
public class CvDocumentMapper {

    public CvDocumentResponse toResponse(CvDocument document, CandidateProfile profile) {
        return new CvDocumentResponse(
                document.getId(),
                document.getOriginalFilename(),
                document.getContentType(),
                document.getFileSizeBytes(),
                document.getStatus(),
                document.getStatusMessage(),
                document.getUploadedAt(),
                document.getParsedAt(),
                profile == null ? null : profile.getId(),
                profile != null && profile.isConfirmed(),
                profile == null ? null : profile.getHeadline());
    }
}
