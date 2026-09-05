package com.baseProject.myBaseProject.jobdescription.validation;

import org.springframework.web.multipart.MultipartFile;

public interface JobDescriptionFileValidator {
    byte[] validateAndRead(MultipartFile file);
}
