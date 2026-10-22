package io.github.qq3939.codenest.api.vo;

import java.util.Map;

public record HealthVO(String status, String version, Map<String, String> components) {
}
