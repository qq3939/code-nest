package io.github.changeme.projecta.web.init;

import io.github.changeme.projecta.core.config.AppProperties;
import io.github.changeme.projecta.service.search.SearchIndexService;
import io.github.changeme.projecta.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/** 启动时：创建第一个管理员（不存在才建）；开启搜索时确保 ES 索引存在。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupInitializer implements ApplicationRunner {
    private final UserService userService;
    private final SearchIndexService searchIndexService;
    private final AppProperties props;

    @Override
    public void run(ApplicationArguments args) {
        try {
            userService.ensureAdmin(props.getAdmin().getUsername(), props.getAdmin().getPassword());
        } catch (DuplicateKeyException e) {
            // 两个实例同时启动时，另一个已经建好了
            log.info("admin already created by another instance");
        }
        if (searchIndexService.enabled()) {
            try {
                searchIndexService.ensureIndex();
            } catch (Exception e) {
                log.warn("elasticsearch not ready, search will fall back to mysql until it is: {}", e.getMessage());
            }
        }
    }
}
