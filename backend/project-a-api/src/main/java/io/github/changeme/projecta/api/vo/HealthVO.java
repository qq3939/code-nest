package io.github.changeme.projecta.api.vo;

import java.util.Map;

public record HealthVO(String status, String version, Map<String, String> components) {
}
