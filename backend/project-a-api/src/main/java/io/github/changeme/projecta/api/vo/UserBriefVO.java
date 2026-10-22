package io.github.changeme.projecta.api.vo;

/** 出现在文章、评论、通知等处的作者/用户简要信息。 */
public record UserBriefVO(Long id, String nickname, String avatarUrl, String bio) {

    public static UserBriefVO unknown(Long id) {
        return new UserBriefVO(id, "已注销用户", "", "");
    }
}
