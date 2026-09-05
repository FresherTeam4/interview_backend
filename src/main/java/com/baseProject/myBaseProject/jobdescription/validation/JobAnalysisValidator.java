package com.baseProject.myBaseProject.jobdescription.validation;

import com.baseProject.myBaseProject.dto.ai.JobAnalysis;
import com.baseProject.myBaseProject.exception.DomainException;
import com.baseProject.myBaseProject.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Component
public class JobAnalysisValidator {

    public JobAnalysis validate(JobAnalysis result, String jd) {
        return doValidate(result);
    }

    public JobAnalysis validateEditable(JobAnalysis result, String jd) {
        return doValidate(result);
    }

    private JobAnalysis doValidate(JobAnalysis result) {
        if (result == null) {
            throw invalid("analysis is null");
        }
        if (!result.sufficientJobContext()) {
            throw new DomainException(ErrorCode.TEMPLATE_INSUFFICIENT_JD);
        }
        required(result.sourceLanguage(), 20, "sourceLanguage");
        optional(result.jobTitle(), 150, "jobTitle");
        optional(result.targetSeniority(), 100, "targetSeniority");
        optional(result.domain(), 150, "domain");
        required(result.summary(), 3000, "summary");

        bounded(result.keySkills(), 30, "keySkills");
        if (result.keySkills() == null || result.keySkills().isEmpty()) {
            throw invalid("keySkills must contain at least one skill");
        }

        Set<String> names = new HashSet<>();
        for (int index = 0; index < result.keySkills().size(); index++) {
            var item = result.keySkills().get(index);
            String path = "keySkills[" + index + "]";
            if (item == null || item.level() == null) {
                throw invalid(path + " is incomplete");
            }
            required(item.name(), 150, path + ".name");
            if (!names.add(item.name().strip().toLowerCase(Locale.ROOT))) {
                throw invalid(path + ".name is duplicated");
            }
            optional(item.description(), 1000, path + ".description");
        }
        return result;
    }

    private void required(String value, int max, String path) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw invalid(path + " is missing or exceeds " + max + " characters");
        }
    }

    private void optional(String value, int max, String path) {
        if (value != null && value.length() > max) {
            throw invalid(path + " exceeds " + max + " characters");
        }
    }

    private void bounded(List<?> items, int max, String path) {
        if (items == null || items.size() > max) {
            throw invalid(path + " is null or exceeds " + max + " items");
        }
    }

    private DomainException invalid(String message) {
        return new DomainException(ErrorCode.TEMPLATE_INVALID_ANALYSIS,
                "Job analysis did not match the required contract: " + message);
    }
}
