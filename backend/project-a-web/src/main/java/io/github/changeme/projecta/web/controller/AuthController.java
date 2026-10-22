package io.github.changeme.projecta.web.controller;

import io.github.changeme.projecta.api.dto.LoginReq;
import io.github.changeme.projecta.api.dto.RefreshReq;
import io.github.changeme.projecta.api.dto.RegisterReq;
import io.github.changeme.projecta.api.vo.TokenVO;
import io.github.changeme.projecta.core.ratelimit.Dim;
import io.github.changeme.projecta.core.ratelimit.RateLimit;
import io.github.changeme.projecta.core.result.Result;
import io.github.changeme.projecta.service.user.AuthService;
import io.github.changeme.projecta.web.interceptor.Public;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证")
@Public
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @Operation(summary = "注册（成功后直接登录）")
    @RateLimit(name = "register", windowMillis = 60_000, limit = 10, dims = Dim.IP)
    @PostMapping("/register")
    public Result<TokenVO> register(@Valid @RequestBody RegisterReq req) {
        return Result.ok(authService.register(req));
    }

    @Operation(summary = "登录，连续失败 5 次锁定 15 分钟")
    @RateLimit(name = "login", windowMillis = 60_000, limit = 10, dims = Dim.IP)
    @PostMapping("/login")
    public Result<TokenVO> login(@Valid @RequestBody LoginReq req) {
        return Result.ok(authService.login(req));
    }

    @Operation(summary = "用刷新令牌换一对新令牌（旧刷新令牌立即作废）")
    @PostMapping("/refresh")
    public Result<TokenVO> refresh(@Valid @RequestBody RefreshReq req) {
        return Result.ok(authService.refresh(req.refreshToken()));
    }

    @Operation(summary = "退出：删除刷新令牌")
    @PostMapping("/logout")
    public Result<Void> logout(@Valid @RequestBody RefreshReq req) {
        authService.logout(req.refreshToken());
        return Result.ok();
    }
}
