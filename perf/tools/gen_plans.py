#!/usr/bin/env python3
"""生成 perf/plans/ 下的 JMeter 计划（.jmx）。

JMeter 的 jmx 是很啰嗦的 XML，手写容易出错，所以用这个脚本统一生成：
改了线程数默认值或者接口路径，重新运行一遍即可。
    python3 perf/tools/gen_plans.py

所有计划都通过 -J 参数控制，常用的有：
    -Jhost=localhost -Jport=8081 -Jthreads=50 -Jduration=30 -Jdata=/perf/data
"""
from pathlib import Path
from xml.sax.saxutils import escape

PLANS = Path(__file__).resolve().parent.parent / "plans"


def prop(name, value, kind="stringProp"):
    return f'<{kind} name="{name}">{escape(str(value))}</{kind}>'


def http_sampler(name, method, path, body=None):
    args = ""
    if body is not None:
        args = f"""<boolProp name="HTTPSampler.postBodyRaw">true</boolProp>
        <elementProp name="HTTPsampler.Arguments" elementType="Arguments">
          <collectionProp name="Arguments.arguments">
            <elementProp name="" elementType="HTTPArgument">
              {prop("Argument.value", body)}
              {prop("Argument.metadata", "=")}
            </elementProp>
          </collectionProp>
        </elementProp>"""
    else:
        args = """<elementProp name="HTTPsampler.Arguments" elementType="Arguments">
          <collectionProp name="Arguments.arguments"/>
        </elementProp>"""
    return f"""
      <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="{escape(name)}">
        {args}
        {prop("HTTPSampler.domain", "${__P(host,localhost)}")}
        {prop("HTTPSampler.port", "${__P(port,8081)}")}
        {prop("HTTPSampler.protocol", "http")}
        {prop("HTTPSampler.path", path)}
        {prop("HTTPSampler.method", method)}
        <boolProp name="HTTPSampler.follow_redirects">false</boolProp>
        <boolProp name="HTTPSampler.use_keepalive">true</boolProp>
      </HTTPSamplerProxy>
      <hashTree/>"""


def csv_data(filename, names):
    return f"""
      <CSVDataSet guiclass="TestBeanGUI" testclass="CSVDataSet" testname="CSV {escape(filename)}">
        {prop("filename", "${__P(data,/perf/data)}/" + filename)}
        {prop("fileEncoding", "UTF-8")}
        {prop("variableNames", names)}
        <boolProp name="ignoreFirstLine">false</boolProp>
        {prop("delimiter", ",")}
        <boolProp name="quotedData">false</boolProp>
        <boolProp name="recycle">true</boolProp>
        <boolProp name="stopThread">false</boolProp>
        {prop("shareMode", "shareMode.all")}
      </CSVDataSet>
      <hashTree/>"""


def headers(with_auth):
    auth = ""
    if with_auth:
        auth = f"""
            <elementProp name="" elementType="Header">
              {prop("Header.name", "Authorization")}
              {prop("Header.value", "Bearer ${token}")}
            </elementProp>"""
    return f"""
      <HeaderManager guiclass="HeaderPanel" testclass="HeaderManager" testname="Headers">
        <collectionProp name="HeaderManager.headers">
          <elementProp name="" elementType="Header">
            {prop("Header.name", "Content-Type")}
            {prop("Header.value", "application/json")}
          </elementProp>{auth}
        </collectionProp>
      </HeaderManager>
      <hashTree/>"""


def plan(name, comment, body, threads="${__P(threads,50)}", duration="${__P(duration,30)}", loops=None):
    # loops 不为空时按固定次数执行（计数准确性要求请求总数确定），否则按时长执行
    if loops is not None:
        loop = f'{prop("LoopController.loops", loops)}<boolProp name="LoopController.continue_forever">false</boolProp>'
        scheduler = '<boolProp name="ThreadGroup.scheduler">false</boolProp>'
    else:
        loop = '<intProp name="LoopController.loops">-1</intProp><boolProp name="LoopController.continue_forever">false</boolProp>'
        scheduler = f'<boolProp name="ThreadGroup.scheduler">true</boolProp>{prop("ThreadGroup.duration", duration)}{prop("ThreadGroup.delay", 0)}'
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<!-- {escape(comment)} -->
<!-- 由 perf/tools/gen_plans.py 生成，请改脚本而不是手改这个文件 -->
<jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="{escape(name)}">
      {prop("TestPlan.comments", comment)}
      <boolProp name="TestPlan.functional_mode">false</boolProp>
      <boolProp name="TestPlan.serialize_threadgroups">false</boolProp>
      <elementProp name="TestPlan.user_defined_variables" elementType="Arguments">
        <collectionProp name="Arguments.arguments"/>
      </elementProp>
    </TestPlan>
    <hashTree>
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="users">
        {prop("ThreadGroup.on_sample_error", "continue")}
        <elementProp name="ThreadGroup.main_controller" elementType="LoopController">
          {loop}
        </elementProp>
        {prop("ThreadGroup.num_threads", threads)}
        {prop("ThreadGroup.ramp_time", "${__P(ramp,5)}")}
        {scheduler}
        <boolProp name="ThreadGroup.same_user_on_next_iteration">true</boolProp>
      </ThreadGroup>
      <hashTree>{body}
      </hashTree>
    </hashTree>
  </hashTree>
</jmeterTestPlan>
"""


def write(filename, content):
    path = PLANS / filename
    path.write_text(content, encoding="utf-8")
    print("wrote", path)


def main():
    PLANS.mkdir(parents=True, exist_ok=True)

    write("01-hot-article-detail.jmx", plan(
        "01 热点文章详情",
        "90% 的请求落在 10 篇热点文章上（hot_mix.csv 由 prepare.sh 生成），对比 cache.mode = none / redis / two-level。",
        csv_data("hot_mix.csv", "articleId") + headers(False)
        + http_sampler("GET /api/articles/{id}", "GET", "/api/articles/${articleId}")))

    write("02-read-count.jmx", plan(
        "02 并发阅读同一篇文章",
        "threads 个线程各请求 loops 次同一篇文章详情；请求总数确定，用来对比三种计数策略的准确性、Com_update 增量和耗时。",
        headers(False) + http_sampler("GET /api/articles/{id}", "GET", "/api/articles/${__P(articleId,0)}"),
        loops="${__P(loops,20)}"))

    write("03-feed-read.jmx", plan(
        "03 读取关注动态",
        "tokens 文件里是登录好的用户令牌：tokens_normal.csv（只关注普通作者）或 tokens_bigv.csv（关注了大 V），用 -Jtokens= 切换。",
        csv_data("${__P(tokens,tokens_normal.csv)}", "token") + headers(True)
        + http_sampler("GET /api/feed", "GET", "/api/feed?size=20")))

    write("05-rate-limit.jmx", plan(
        "05 点赞限流",
        "同一个用户高频点赞/取消点赞（规则：每秒 5 次），统计 429 的比例。tokens_like.csv 里只有一个令牌。",
        csv_data("tokens_like.csv", "token") + headers(True)
        + http_sampler("POST like", "POST", "/api/articles/${__P(articleId,0)}/like", body="")
        + http_sampler("DELETE like", "DELETE", "/api/articles/${__P(articleId,0)}/like"),
        threads="${__P(threads,5)}"))


if __name__ == "__main__":
    main()
