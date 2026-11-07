"""Freeze AI-drafted exploratory queries. Labels require independent human review.

Queries were drafted from the eight source documents, before running this new set.
They are not a blind or human-labelled benchmark.
"""
import json
from pathlib import Path

topics = {
    "redis-cache": [
        "查不到的商品编号总是访问数据库，怎样挡住这些请求",
        "热点缓存恰好过期时大量线程回源怎么办",
        "一批缓存同一时刻失效为什么会压垮数据库",
        "RDB 快照与 AOF 日志恢复速度和丢数据风险怎么权衡",
        "Cache Aside 更新数据库后该如何处理旧缓存",
        "布隆过滤器误判会造成什么影响",
        "缓存空值应该设置多长的过期时间",
        "热点 Key 重建时如何避免所有请求抢同一把锁",
        "延迟双删能否保证数据库和缓存严格一致",
        "Redis 主从复制加 Sentinel 和 Cluster 有什么区别",
        "如果缓存命中率突然降低，怎么区分穿透、击穿与雪崩",
        "删除分布式锁时为什么要校验持有者标识",
    ],
    "jvm-gc": [
        "G1 的 Region 为什么不是固定分代空间",
        "Mixed GC 与 Young GC 各回收哪些区域",
        "频繁 Full GC 时先看哪些日志和指标",
        "线上堆内存持续增长如何定位泄漏对象",
        "OOM 后怎样取得堆转储文件",
        "设了停顿目标为什么 G1 仍可能超时",
        "类加载双亲委派能解决什么问题",
        "什么时候可能需要自定义类加载器",
        "对象已经不可达但内存仍紧张可能是什么原因",
        "如何比较垃圾回收吞吐量和停顿时长",
        "晋升失败或老年代占用高时如何排查",
        "只看平均 GC 时间能判断尾延迟吗",
    ],
    "mysql-index": [
        "联合索引范围条件后面的列还能充分利用吗",
        "聚簇索引叶子节点存放的到底是什么",
        "二级索引查询为什么有时需要回表",
        "EXPLAIN 显示走索引就一定比全表扫描快吗",
        "ReadView 如何决定版本是否可见",
        "RR 和 RC 下同一事务两次读到的数据为何不同",
        "Next-Key Lock 如何处理范围内的插入",
        "死锁发生后如何统一加锁顺序",
        "高频更新表上建立很多索引会带来什么代价",
        "覆盖索引和最左前缀分别解决什么问题",
        "SQL 变慢时如何结合统计信息与实际数据分布分析",
        "事务原子性与持久性分别由什么机制保证",
    ],
    "kafka-reliability": [
        "生产者设置 acks=all 后是否再也不会丢消息",
        "ISR 缩小会怎样影响可用性和写入可靠性",
        "业务处理成功后再提交 offset 会出现什么重复风险",
        "数据库写成功而 offset 没提交如何设计幂等",
        "同一订单的消息为什么要进入同一分区",
        "消费积压先检查分区数还是下游处理速度",
        "死信队列里的消息是否可以直接丢弃",
        "Exactly Once 能否保证外部数据库只写一次",
        "消费者重试时怎样避免重复扣款",
        "min.insync.replicas 与生产确认有什么关系",
        "消息处理耗时增加会怎样影响 backlog",
        "消息顺序要求与消费者并发数如何取舍",
    ],
    "spring-boot": [
        "Bean 未注入时怎样看自动配置条件报告",
        "@ConditionalOnClass 缺依赖时会发生什么",
        "@ConditionalOnMissingBean 为什么允许覆盖默认 Bean",
        "组件扫描范围配置错误会造成什么现象",
        "配置属性绑定失败时如何排查字段名称",
        "Profile 切换后 Bean 数量变化怎么解释",
        "同名 Bean 冲突时先查哪里",
        "自动配置类在 classpath 上但没有生效为什么",
        "Spring Boot 启动时如何决定装配哪些服务",
        "条件注解与普通 @Bean 声明如何配合",
        "多环境配置覆盖顺序出错怎样定位",
        "仅添加依赖为什么可能触发新的自动装配",
    ],
    "spring-cloud": [
        "微服务调用超时后能否直接重试写接口",
        "熔断和限流分别处理哪类故障",
        "配置中心变更后服务没有生效如何排查刷新",
        "服务发现注册信息滞后会有什么表现",
        "网关鉴权放行但下游拒绝访问怎么排查",
        "请求链路 ID 丢失后如何定位慢调用",
        "负载均衡与客户端超时怎样一起配置",
        "服务降级后返回默认值会掩盖什么问题",
        "多服务重复提交怎样保证写操作幂等",
        "一次调用跨多个服务时如何追踪错误节点",
        "上游重试和下游容量不足会如何互相放大",
        "服务注册、配置和网关各解决什么问题",
    ],
    "java-concurrency": [
        "CAS 失败后线程为什么需要重试",
        "高竞争下自旋会浪费什么资源",
        "ABA 问题为什么需要版本号",
        "volatile 可以让 count++ 原子化吗",
        "线程池无界队列为什么会隐藏背压",
        "拒绝策略和任务耗时怎样影响线程池容量",
        "CompletableFuture 异常不处理会发生什么",
        "默认执行器与自定义执行器怎么选择",
        "线程间可见性和复合操作同步有何区别",
        "无锁更新遇到长期竞争是否总比加锁快",
        "corePoolSize 过大可能导致什么问题",
        "并发任务都等下游时为何调大线程数不一定有效",
    ],
    "distributed-lock": [
        "Redis 锁为什么要有唯一 owner 标识",
        "解锁时只执行 DEL 会误删谁的锁",
        "租约到期但旧任务还在运行如何防止脏写",
        "fencing token 如何阻止过期持有者覆盖新结果",
        "SET NX PX 获取锁还缺少哪些业务保证",
        "Lua 校验释放为什么比先 GET 再 DEL 安全",
        "锁竞争失败时请求应该等待还是快速返回",
        "分布式锁和数据库唯一约束解决的问题一样吗",
        "业务幂等键如何防重复执行",
        "外部资源写入顺序要求很强时仅靠 Redis 锁够吗",
        "两个实例同时恢复同一会话怎样协调",
        "锁续约失败后旧 owner 延迟返回该怎么办",
    ],
}

negative = [
    "火星探测器的降落伞如何展开", "吉他标准调弦是什么", "唐诗平仄怎样判断",
    "烘焙面包用哪种酵母", "月球表面昼夜温差多大", "篮球三分球规则是什么",
    "如何给室内绿植换盆", "光合作用的化学方程式", "海水盐度如何测量",
    "巴赫赋格曲如何分析", "太阳系第七颗行星是什么", "咖啡豆浅烘和深烘区别",
    "猫咪绝育后如何护理", "风力发电机叶片为什么很长", "古埃及金字塔怎样建造",
    "游泳自由泳换气技巧", "如何申请护照签证", "小提琴松香多久涂一次",
    "台风路径如何预测", "冬季滑雪板怎样打蜡", "法语虚拟式用在什么场合",
    "摄影镜头光圈与景深关系", "人体免疫细胞有哪些", "国际象棋王车易位规则",
]

cases = []
for document_id, queries in topics.items():
    assert len(queries) == 12, document_id
    for query in queries:
        cases.append({"query": query, "relevantDocumentIds": [document_id],
                      "category": "AI候选-待人工分类", "labelStatus": "AI起草待人工确认"})
for query in negative:
    cases.append({"query": query, "relevantDocumentIds": [],
                  "category": "无相关文档", "labelStatus": "AI起草待人工确认"})
assert len(cases) == 120 and len({item["query"] for item in cases}) == 120
target = Path(__file__).with_name("candidate120.json")
target.write_text(json.dumps(cases, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(target)
