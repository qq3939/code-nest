package io.github.qq3939.codenest.service.file;

import io.github.qq3939.codenest.api.common.ErrorCode;
import io.github.qq3939.codenest.api.vo.FileVO;
import io.github.qq3939.codenest.core.config.AppProperties;
import io.github.qq3939.codenest.core.exception.BizException;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 图片上传（SPEC 9.12）：只收 jpg/png/webp/gif，≤ 5 MB；按文件头（魔数）判断真实类型，不信扩展名和 Content-Type——
 * 两者都是客户端随便填的，攻击者可以把 HTML/SVG 改名成 .png 上传，再诱导别人打开造成 XSS。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {
    private static final DateTimeFormatter MONTH_PATH = DateTimeFormatter.ofPattern("yyyy/MM");

    private final MinioClient minioClient;
    private final AppProperties props;

    public FileVO uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > props.getUpload().getMaxSizeBytes()) {
            throw new BizException(ErrorCode.FILE_INVALID);
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BizException(ErrorCode.FILE_INVALID);
        }
        ImageType type = ImageType.detect(bytes);
        if (type == null) {
            throw new BizException(ErrorCode.FILE_INVALID, "只支持 jpg、png、webp、gif 图片");
        }
        String objectName = LocalDate.now().format(MONTH_PATH) + "/" + UUID.randomUUID().toString().replace("-", "")
                + "." + type.extension();
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(props.getMinio().getBucket())
                    .object(objectName)
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .contentType(type.contentType())
                    .build());
        } catch (Exception e) {
            log.error("upload to minio failed", e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "上传失败，请稍后再试");
        }
        return new FileVO(props.getUpload().getPublicPrefix() + objectName, objectName);
    }
}
