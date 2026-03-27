# Product Search ES CDC Runbook

## 目标

为 `/api/v2/product/products?keyword=...` 提供基于 Elasticsearch 的中文分词搜索，并通过 `product-service -> outbox -> Kafka CDC -> search-indexer -> Elasticsearch` 异步维护索引。

## 配置项

### product-service

- `product.search.enabled`
- `product.search.endpoint`
- `product.search.index-name`
- `product.search.connect-timeout`
- `product.search.read-timeout`
- `product.search.mysql-fallback-enabled`

### search-indexer-service

- `search.kafka.enabled`
- `search.kafka.product-outbox-topic`
- `search.kafka.product-outbox-group-id`
- `search.elasticsearch.endpoint`
- `search.elasticsearch.index-name`

### 对应环境变量

- `PRODUCT_SEARCH_ENABLED`
- `PRODUCT_SEARCH_ENDPOINT_DOCKER` / `PRODUCT_SEARCH_ENDPOINT_LOCAL`
- `PRODUCT_SEARCH_INDEX_NAME`
- `PRODUCT_SEARCH_CONNECT_TIMEOUT`
- `PRODUCT_SEARCH_READ_TIMEOUT`
- `PRODUCT_SEARCH_MYSQL_FALLBACK_ENABLED`
- `SEARCH_KAFKA_ENABLED`
- `SEARCH_PRODUCT_OUTBOX_TOPIC`
- `SEARCH_PRODUCT_OUTBOX_GROUP_ID`
- `SEARCH_ELASTICSEARCH_ENDPOINT_DOCKER` / `SEARCH_ELASTICSEARCH_ENDPOINT_LOCAL`
- `SEARCH_ELASTICSEARCH_INDEX_NAME`

## 启动基础设施

1. 准备 `deploy/.env`，至少补齐数据库、Nacos、MinIO、JWT 等敏感变量。
2. 启动基础设施与应用：

```bash
docker compose -f deploy/docker-compose.yml --profile app up -d \
  mysql redis kafka kafka-connect kafka-connect-init nacos nacos-config-init \
  minio minio-init elasticsearch shiori-product-service shiori-search-indexer-service shiori-gateway-service
```

3. 确认 Elasticsearch 已加载 IK：

```bash
curl http://127.0.0.1:9200/_cat/plugins?v
```

预期能看到 `analysis-ik`。

## 验证索引写入

1. 发布一个有库存的商品。
2. 检查 Kafka CDC raw topic 是否出现搜索事件：

```bash
docker exec -it shiori-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:29092 \
  --topic shiori.cdc.product.outbox.raw \
  --from-beginning \
  --max-messages 20
```

预期能看到：

- `PRODUCT_SEARCH_UPSERTED`
- 下架时为 `PRODUCT_SEARCH_REMOVED`

3. 检查 ES 索引：

```bash
curl "http://127.0.0.1:9200/shiori_product_search/_search?pretty"
```

## 验证查询链路

```bash
curl "http://127.0.0.1:8080/api/v2/product/products?keyword=Java"
```

预期：

- 返回在售商品
- 命中标题的商品优先
- 显式 `sortBy=MIN_PRICE` 或 `MAX_PRICE` 时，业务排序优先，相关性作为次级排序

## 验证 MySQL fallback

1. 停掉 Elasticsearch：

```bash
docker compose -f deploy/docker-compose.yml stop elasticsearch
```

2. 再次请求：

```bash
curl "http://127.0.0.1:8080/api/v2/product/products?keyword=Java"
```

预期：

- 请求仍成功返回
- 结果退回 MySQL `LIKE` 查询

3. 恢复 Elasticsearch：

```bash
docker compose -f deploy/docker-compose.yml start elasticsearch
```

## 全量重建

重建不直写 ES，而是重新发 `PRODUCT_SEARCH_UPSERTED` 事件。

```bash
curl -X POST "http://127.0.0.1:8082/api/v2/product/internal/search/reindex?batchSize=200" \
  -H "X-User-Id: 1001"
```

预期返回：

- `reindexedCount`
- `batchCount`
- `lastProductId`

## 常见排查

- `search-indexer` 没消费：先看 `search.kafka.enabled`、group id、topic 名是否一致。
- ES 没建索引：看 `shiori-search-indexer-service` 日志里是否有索引创建或写入异常。
- 搜索结果为空：先查 ES 中是否有文档，再查商品是否仍为 `ON_SALE`。
- fallback 未生效：确认 `product.search.mysql-fallback-enabled=true`。
