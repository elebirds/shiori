package moe.hhm.shiori.product.service;

import java.time.Duration;
import java.time.Instant;
import moe.hhm.shiori.common.error.ProductErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.product.config.ProductMqProperties;
import moe.hhm.shiori.product.config.ProductOutboxProperties;
import moe.hhm.shiori.product.domain.OutboxStatus;
import moe.hhm.shiori.product.domain.ProductStatus;
import moe.hhm.shiori.product.domain.StockOpType;
import moe.hhm.shiori.product.dto.StockDeductRequest;
import moe.hhm.shiori.product.dto.StockOperateResponse;
import moe.hhm.shiori.product.dto.StockReleaseRequest;
import moe.hhm.shiori.product.event.EventEnvelope;
import moe.hhm.shiori.product.event.ProductSearchUpsertedPayload;
import moe.hhm.shiori.product.model.ProductOutboxEventEntity;
import moe.hhm.shiori.product.model.ProductSearchSnapshotRecord;
import moe.hhm.shiori.product.model.StockTxnRecord;
import moe.hhm.shiori.product.repository.ProductMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class ProductStockService {

    private final ProductMapper productMapper;
    private final ProductMetrics productMetrics;
    private final ProductDetailCacheService productDetailCacheService;
    private final ProductMqProperties productMqProperties;
    private final ProductOutboxProperties productOutboxProperties;
    private final ObjectMapper objectMapper;

    public ProductStockService(ProductMapper productMapper,
                               ProductMetrics productMetrics,
                               ProductDetailCacheService productDetailCacheService,
                               ProductMqProperties productMqProperties,
                               ProductOutboxProperties productOutboxProperties,
                               ObjectMapper objectMapper) {
        this.productMapper = productMapper;
        this.productMetrics = productMetrics;
        this.productDetailCacheService = productDetailCacheService;
        this.productMqProperties = productMqProperties;
        this.productOutboxProperties = productOutboxProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public StockOperateResponse deduct(StockDeductRequest request) {
        long startNanos = System.nanoTime();
        String result = "unknown";
        try {
            StockOperateResponse response = operateStock(request.bizNo(), request.skuId(), request.quantity(), StockOpType.DEDUCT);
            result = response.idempotent() ? "idempotent_success" : "success";
            return response;
        } catch (BizException ex) {
            result = ProductErrorCode.STOCK_NOT_ENOUGH.equals(ex.getErrorCode()) ? "stock_not_enough" : "error";
            throw ex;
        } catch (RuntimeException ex) {
            result = "error";
            throw ex;
        } finally {
            productMetrics.recordStockDeductLatency(result, Duration.ofNanos(System.nanoTime() - startNanos));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public StockOperateResponse release(StockReleaseRequest request) {
        return operateStock(request.bizNo(), request.skuId(), request.quantity(), StockOpType.RELEASE);
    }

    private StockOperateResponse operateStock(String bizNo, Long skuId, Integer quantity, StockOpType opType) {
        StockTxnRecord existed = productMapper.findStockTxnByBizNoAndType(bizNo, opType.name());
        if (existed != null) {
            if (existed.success() != null && existed.success() == 1) {
                return new StockOperateResponse(true, true, bizNo, skuId, quantity, productMapper.findStockBySkuId(skuId));
            }
            if (opType == StockOpType.DEDUCT) {
                throw new BizException(ProductErrorCode.STOCK_NOT_ENOUGH, HttpStatus.BAD_REQUEST);
            }
            return new StockOperateResponse(true, true, bizNo, skuId, quantity, productMapper.findStockBySkuId(skuId));
        }

        if (productMapper.findActiveSkuByIdForUpdate(skuId) == null) {
            throw new BizException(ProductErrorCode.SKU_NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        StockTxnRecord latest = productMapper.findStockTxnByBizNoAndType(bizNo, opType.name());
        if (latest != null) {
            if (latest.success() != null && latest.success() == 1) {
                return new StockOperateResponse(true, true, bizNo, skuId, quantity, productMapper.findStockBySkuId(skuId));
            }
            if (opType == StockOpType.DEDUCT) {
                throw new BizException(ProductErrorCode.STOCK_NOT_ENOUGH, HttpStatus.BAD_REQUEST);
            }
            return new StockOperateResponse(true, true, bizNo, skuId, quantity, productMapper.findStockBySkuId(skuId));
        }

        try {
            productMapper.insertStockTxn(bizNo, skuId, opType.name(), quantity, 0);
        } catch (DuplicateKeyException e) {
            StockTxnRecord duplicated = productMapper.findStockTxnByBizNoAndType(bizNo, opType.name());
            if (duplicated != null && duplicated.success() != null && duplicated.success() == 1) {
                return new StockOperateResponse(true, true, bizNo, skuId, quantity, productMapper.findStockBySkuId(skuId));
            }
            if (opType == StockOpType.DEDUCT) {
                throw new BizException(ProductErrorCode.STOCK_NOT_ENOUGH, HttpStatus.BAD_REQUEST);
            }
            return new StockOperateResponse(true, true, bizNo, skuId, quantity, productMapper.findStockBySkuId(skuId));
        }

        int affected;
        if (opType == StockOpType.DEDUCT) {
            affected = productMapper.deductStockAtomic(skuId, quantity);
            if (affected == 0) {
                productMapper.updateStockTxnSuccess(bizNo, opType.name(), 0);
                throw new BizException(ProductErrorCode.STOCK_NOT_ENOUGH, HttpStatus.BAD_REQUEST);
            }
        } else {
            affected = productMapper.increaseStockAtomic(skuId, quantity);
            if (affected == 0) {
                throw new BizException(ProductErrorCode.SKU_NOT_FOUND, HttpStatus.NOT_FOUND);
            }
        }

        productMapper.updateStockTxnSuccess(bizNo, opType.name(), 1);
        Integer currentStock = productMapper.findStockBySkuId(skuId);
        Long productId = productMapper.findProductIdBySkuId(skuId);
        if (productId != null) {
            productDetailCacheService.evictProductDetail(productId);
            appendProductSearchUpsertOutbox(productId);
        }
        return new StockOperateResponse(true, false, bizNo, skuId, quantity, currentStock);
    }

    private void appendProductSearchUpsertOutbox(Long productId) {
        if (!productOutboxProperties.isEnabled()) {
            return;
        }
        ProductSearchSnapshotRecord snapshot = productMapper.findProductSearchSnapshotById(productId);
        if (snapshot == null
                || snapshot.productId() == null
                || snapshot.ownerUserId() == null
                || !StringUtils.hasText(snapshot.productNo())
                || snapshot.isDeleted() == null
                || snapshot.isDeleted() != 0
                || snapshot.status() == null
                || snapshot.status() != ProductStatus.ON_SALE.getCode()) {
            return;
        }

        ProductSearchUpsertedPayload payload = new ProductSearchUpsertedPayload(
                snapshot.productId(),
                snapshot.productNo(),
                snapshot.ownerUserId(),
                snapshot.title(),
                snapshot.description(),
                snapshot.coverObjectKey(),
                snapshot.categoryCode(),
                snapshot.subCategoryCode(),
                snapshot.conditionLevel(),
                snapshot.tradeMode(),
                snapshot.campusCode(),
                snapshot.minPriceCent(),
                snapshot.maxPriceCent(),
                snapshot.totalStock(),
                snapshot.status(),
                snapshot.version(),
                snapshot.createdAt() == null ? null : snapshot.createdAt().toString(),
                snapshot.updatedAt() == null ? Instant.now().toString() : snapshot.updatedAt().toString()
        );
        EventEnvelope envelope = new EventEnvelope(
                java.util.UUID.randomUUID().toString().replace("-", ""),
                "PRODUCT_SEARCH_UPSERTED",
                snapshot.productNo(),
                Instant.now().toString(),
                objectMapper.valueToTree(payload)
        );
        insertOutboxEvent(snapshot.productNo(), envelope);
    }

    private void insertOutboxEvent(String aggregateId, EventEnvelope envelope) {
        String envelopeJson;
        try {
            envelopeJson = objectMapper.writeValueAsString(envelope);
        } catch (JacksonException ex) {
            throw new IllegalStateException("构建 product outbox 事件失败", ex);
        }

        ProductOutboxEventEntity entity = new ProductOutboxEventEntity();
        entity.setEventId(envelope.eventId());
        entity.setAggregateType("product");
        entity.setAggregateId(aggregateId);
        entity.setMessageKey(aggregateId);
        entity.setType(envelope.type());
        entity.setPayload(envelopeJson);
        entity.setExchangeName(productMqProperties.getEventExchange());
        entity.setRoutingKey(productMqProperties.getProductPublishedRoutingKey());
        entity.setStatus(OutboxStatus.PENDING.name());
        entity.setRetryCount(0);
        productMapper.insertProductOutboxEvent(entity);
    }
}
