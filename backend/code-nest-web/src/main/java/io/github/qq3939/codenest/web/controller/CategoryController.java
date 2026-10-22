package io.github.qq3939.codenest.web.controller;

import io.github.qq3939.codenest.api.vo.CategoryVO;
import io.github.qq3939.codenest.api.vo.TagVO;
import io.github.qq3939.codenest.core.result.Result;
import io.github.qq3939.codenest.service.article.CategoryService;
import io.github.qq3939.codenest.service.article.TagService;
import io.github.qq3939.codenest.web.interceptor.Public;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "分类和标签")
@Public
@RestController
@RequiredArgsConstructor
public class CategoryController {
    private final CategoryService categoryService;
    private final TagService tagService;

    @Operation(summary = "全部分类")
    @GetMapping("/api/categories")
    public Result<List<CategoryVO>> categories() {
        return Result.ok(categoryService.all());
    }

    @Operation(summary = "按前缀搜索标签（编辑器联想用）")
    @GetMapping("/api/tags")
    public Result<List<TagVO>> tags(@RequestParam(required = false) String keyword) {
        return Result.ok(tagService.search(keyword, 20));
    }
}
