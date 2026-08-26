package com.baseProject.myBaseProject.jd;

import com.baseProject.myBaseProject.config.properites.JobDescriptionProperties;
import com.baseProject.myBaseProject.exception.JobDescriptionContentRequiredException;
import com.baseProject.myBaseProject.exception.JobDescriptionFileCorruptedException;
import com.baseProject.myBaseProject.exception.JobDescriptionFileTooLargeException;
import com.baseProject.myBaseProject.exception.JobDescriptionInvalidFileTypeException;
import com.baseProject.myBaseProject.jd.extraction.JobDescriptionFileType;
import com.baseProject.myBaseProject.jd.extraction.JobDescriptionTextExtractor;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class JobDescriptionFileProcessor {

    private static final int MAX_FILENAME_LENGTH = 255;

    private final JobDescriptionProperties properties;
    private final Map<JobDescriptionFileType, JobDescriptionTextExtractor> extractors;

    public JobDescriptionFileProcessor(
            JobDescriptionProperties properties,
            List<JobDescriptionTextExtractor> extractors) {
        this.properties = properties;
        this.extractors = new EnumMap<>(JobDescriptionFileType.class);
        extractors.forEach(extractor -> {
            JobDescriptionTextExtractor previous =
                    this.extractors.put(extractor.fileType(), extractor);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate JD extractor for " + extractor.fileType());
            }
        });
    }

    public ProcessedFile process(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new JobDescriptionContentRequiredException();
        }
        if (file.getSize() > properties.maxFileSizeBytes()) {
            throw new JobDescriptionFileTooLargeException(properties.maxFileSizeBytes());
        }

        String filename = safeFilename(file.getOriginalFilename());
        JobDescriptionFileType fileType = JobDescriptionFileType.fromFilename(filename);
        byte[] content = readFully(file);
        if (content.length > properties.maxFileSizeBytes()) {
            throw new JobDescriptionFileTooLargeException(properties.maxFileSizeBytes());
        }

        JobDescriptionTextExtractor extractor = extractors.get(fileType);
        if (extractor == null) {
            throw new IllegalStateException("Missing JD extractor for " + fileType);
        }
        return new ProcessedFile(
                filename,
                fileType.extension(),
                fileType.contentType(),
                content,
                extractor.extract(content));
    }

    private byte[] readFully(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new JobDescriptionFileCorruptedException(e);
        }
    }

    private String safeFilename(String rawFilename) {
        if (rawFilename == null) {
            throw new JobDescriptionInvalidFileTypeException();
        }

        String filename = rawFilename.strip();
        int lastSeparator = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        if (lastSeparator >= 0) {
            filename = filename.substring(lastSeparator + 1).strip();
        }
        filename = filename.replaceAll("[\\p{Cntrl}]", "_");
        if (filename.isBlank()) {
            throw new JobDescriptionInvalidFileTypeException();
        }
        return filename.length() <= MAX_FILENAME_LENGTH
                ? filename
                : filename.substring(filename.length() - MAX_FILENAME_LENGTH);
    }

    public record ProcessedFile(
            String originalFilename,
            String extension,
            String contentType,
            byte[] content,
            String extractedText) {
    }
}
