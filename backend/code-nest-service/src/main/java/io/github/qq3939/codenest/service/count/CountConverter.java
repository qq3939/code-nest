package io.github.qq3939.codenest.service.count;

import io.github.qq3939.codenest.api.vo.ArticleCountVO;
import io.github.qq3939.codenest.service.count.entity.ArticleStatDO;

import java.util.Map;

final class CountConverter {

    private CountConverter() {
    }

    static ArticleCountVO toVO(ArticleStatDO s) {
        if (s == null) {
            return ArticleCountVO.zero();
        }
        return new ArticleCountVO(nz(s.getReadCount()), nz(s.getLikeCount()), nz(s.getCollectCount()),
                nz(s.getCommentCount()));
    }

    static ArticleCountVO fromHash(Map<Object, Object> hash) {
        return new ArticleCountVO(num(hash.get("read")), num(hash.get("like")), num(hash.get("collect")),
                num(hash.get("comment")));
    }

    static ArticleStatDO toDO(Long articleId, ArticleCountVO vo) {
        ArticleStatDO s = new ArticleStatDO();
        s.setArticleId(articleId);
        s.setReadCount(vo.read());
        s.setLikeCount(vo.like());
        s.setCollectCount(vo.collect());
        s.setCommentCount(vo.comment());
        return s;
    }

    private static long nz(Long v) {
        return v == null ? 0 : v;
    }

    private static long num(Object v) {
        return v == null ? 0 : Long.parseLong(v.toString());
    }
}
