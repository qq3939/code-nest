package io.github.qq3939.codenest.web.controller;

/** 分页参数兜底：页码至少 1，每页 1–50 条。 */
final class Pages {

    private Pages() {
    }

    static long page(Long page) {
        return page == null || page < 1 ? 1 : page;
    }

    static long size(Long size) {
        return size == null || size < 1 ? 20 : Math.min(size, 50);
    }

    static int cursorSize(Integer size) {
        return size == null || size < 1 ? 20 : Math.min(size, 50);
    }
}
