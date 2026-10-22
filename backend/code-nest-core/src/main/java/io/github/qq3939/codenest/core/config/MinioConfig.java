package io.github.qq3939.codenest.core.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    /** bucket 由 Compose 里的 minio-init 一次性容器创建并设为匿名只读，后端只负责上传。 */
    @Bean
    public MinioClient minioClient(AppProperties props) {
        AppProperties.Minio minio = props.getMinio();
        return MinioClient.builder()
                .endpoint(minio.getEndpoint())
                .credentials(nullToEmpty(minio.getAccessKey()), nullToEmpty(minio.getSecretKey()))
                .build();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
