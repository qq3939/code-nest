# IK 分词插件的版本必须和 ES 完全一致，升级 ES 时两处一起改
ARG ES_VERSION=8.19.5
FROM elasticsearch:${ES_VERSION}
ARG ES_VERSION
RUN bin/elasticsearch-plugin install --batch \
    "https://release.infinilabs.com/analysis-ik/stable/elasticsearch-analysis-ik-${ES_VERSION}.zip"
