package com.baseProject.myBaseProject.interview;

import com.baseProject.myBaseProject.entity.CandidateProfile;
import com.baseProject.myBaseProject.entity.ProfileEducation;
import com.baseProject.myBaseProject.entity.ProfileProject;
import com.baseProject.myBaseProject.entity.ProfileSkill;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.stereotype.Component;

import java.time.temporal.TemporalAccessor;
import java.util.List;

@Component
public class ProfileSnapshotFactory {

    public JsonNode create(
            CandidateProfile profile,
            List<ProfileEducation> educations,
            List<ProfileSkill> skills,
            List<ProfileProject> projects) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        putNullable(root, "headline", profile.getHeadline());
        putNullable(root, "targetPosition", profile.getTargetPosition());
        putNullable(root, "seniorityLevel", profile.getSeniorityLevel());
        if (profile.getYearsExperience() == null) {
            root.putNull("yearsExperience");
        } else {
            root.put("yearsExperience", profile.getYearsExperience());
        }
        root.set("educations", educations(educations));
        root.set("skills", skills(skills));
        root.set("projects", projects(projects));
        return root;
    }

    private ArrayNode educations(List<ProfileEducation> educations) {
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        for (ProfileEducation education : educations) {
            ObjectNode item = result.addObject();
            item.put("id", education.getId());
            putNullable(item, "school", education.getSchool());
            putNullable(item, "degree", education.getDegree());
            putNullable(item, "fieldOfStudy", education.getFieldOfStudy());
            putNullable(item, "startYear", education.getStartYear());
            putNullable(item, "endYear", education.getEndYear());
        }
        return result;
    }

    private ArrayNode skills(List<ProfileSkill> skills) {
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        for (ProfileSkill skill : skills) {
            ObjectNode item = result.addObject();
            item.put("id", skill.getId());
            putNullable(item, "name", skill.getName());
            putNullable(item, "category", skill.getCategory());
        }
        return result;
    }

    private ArrayNode projects(List<ProfileProject> projects) {
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        for (ProfileProject project : projects) {
            ObjectNode item = result.addObject();
            item.put("id", project.getId());
            putNullable(item, "name", project.getName());
            putNullable(item, "description", project.getDescription());
            putNullable(item, "roleInProject", project.getRoleInProject());
            putNullable(item, "techStack", project.getTechStack());
            putNullable(item, "startDate", project.getStartDate());
            putNullable(item, "endDate", project.getEndDate());
        }
        return result;
    }

    private void putNullable(ObjectNode node, String field, String value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private void putNullable(ObjectNode node, String field, Number value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value.longValue());
        }
    }

    private void putNullable(ObjectNode node, String field, TemporalAccessor value) {
        putNullable(node, field, value == null ? null : value.toString());
    }
}
