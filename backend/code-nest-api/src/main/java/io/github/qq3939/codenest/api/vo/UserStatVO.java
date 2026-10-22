package io.github.qq3939.codenest.api.vo;

public record UserStatVO(long articleCount, long followerCount, long followingCount, long likeReceived, long readReceived) {

    public static UserStatVO zero() {
        return new UserStatVO(0, 0, 0, 0, 0);
    }
}
