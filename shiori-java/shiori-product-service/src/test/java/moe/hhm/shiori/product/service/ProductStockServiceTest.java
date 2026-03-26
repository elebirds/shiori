package moe.hhm.shiori.product.service;

import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.product.dto.StockDeductRequest;
import moe.hhm.shiori.product.dto.StockOperateResponse;
import moe.hhm.shiori.product.dto.StockReleaseRequest;
import moe.hhm.shiori.product.model.SkuRecord;
import moe.hhm.shiori.product.model.StockTxnRecord;
import moe.hhm.shiori.product.repository.ProductMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductStockServiceTest {

    @Mock
    private ProductMapper productMapper;

    @Mock
    private ProductDetailCacheService productDetailCacheService;

    private ProductStockService productStockService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        productStockService = new ProductStockService(
                productMapper,
                productDetailCacheService
        );
    }

    @Test
    void shouldDeductStockSuccess() {
        when(productMapper.findActiveSkuByIdForUpdate(10L)).thenReturn(
                new SkuRecord(10L, 1L, "S001", "标准版", "{}", 3900L, 10, 0)
        );
        when(productMapper.findStockTxnByBizNoAndType("BIZ-1", "DEDUCT")).thenReturn(null);
        when(productMapper.deductStockAtomic(10L, 2)).thenReturn(1);
        when(productMapper.findStockBySkuId(10L)).thenReturn(8);
        when(productMapper.findProductIdBySkuId(10L)).thenReturn(1L);

        StockOperateResponse response = productStockService.deduct(new StockDeductRequest(10L, 2, "BIZ-1"));

        assertThat(response.success()).isTrue();
        assertThat(response.idempotent()).isFalse();
        assertThat(response.currentStock()).isEqualTo(8);
        verify(productMapper).updateStockTxnSuccess("BIZ-1", "DEDUCT", 1);
        verify(productMapper).findProductIdBySkuId(10L);
        verify(productDetailCacheService).evictProductDetail(1L);
    }

    @Test
    void shouldRejectWhenDeductStockNotEnough() {
        when(productMapper.findActiveSkuByIdForUpdate(10L)).thenReturn(
                new SkuRecord(10L, 1L, "S001", "标准版", "{}", 3900L, 1, 0)
        );
        when(productMapper.findStockTxnByBizNoAndType("BIZ-2", "DEDUCT")).thenReturn(null);
        when(productMapper.deductStockAtomic(10L, 2)).thenReturn(0);

        assertThatThrownBy(() -> productStockService.deduct(new StockDeductRequest(10L, 2, "BIZ-2")))
                .isInstanceOf(BizException.class);

        verify(productMapper).updateStockTxnSuccess("BIZ-2", "DEDUCT", 0);
    }

    @Test
    void shouldReturnIdempotentWhenTxnAlreadySuccess() {
        when(productMapper.findStockTxnByBizNoAndType("BIZ-3", "DEDUCT")).thenReturn(
                new StockTxnRecord(1L, "BIZ-3", 10L, "DEDUCT", 2, 1)
        );
        when(productMapper.findStockBySkuId(10L)).thenReturn(8);

        StockOperateResponse response = productStockService.deduct(new StockDeductRequest(10L, 2, "BIZ-3"));

        assertThat(response.success()).isTrue();
        assertThat(response.idempotent()).isTrue();
    }

    @Test
    void shouldReleaseStockSuccess() {
        when(productMapper.findActiveSkuByIdForUpdate(10L)).thenReturn(
                new SkuRecord(10L, 1L, "S001", "标准版", "{}", 3900L, 10, 0)
        );
        when(productMapper.findStockTxnByBizNoAndType("BIZ-4", "RELEASE")).thenReturn(null);
        when(productMapper.increaseStockAtomic(10L, 2)).thenReturn(1);
        when(productMapper.findStockBySkuId(10L)).thenReturn(12);
        when(productMapper.findProductIdBySkuId(10L)).thenReturn(1L);

        StockOperateResponse response = productStockService.release(new StockReleaseRequest(10L, 2, "BIZ-4"));

        assertThat(response.success()).isTrue();
        assertThat(response.currentStock()).isEqualTo(12);
        verify(productMapper).updateStockTxnSuccess("BIZ-4", "RELEASE", 1);
        verify(productDetailCacheService).evictProductDetail(1L);
    }

    @Test
    void shouldProbeTxnBeforeLockingSku() {
        SkuRecord sku = new SkuRecord(10L, 1L, "S001", "标准版", "{}", 3900L, 10, 0);
        when(productMapper.findActiveSkuByIdForUpdate(10L)).thenReturn(sku);
        when(productMapper.findStockTxnByBizNoAndType("BIZ-LOCK", "DEDUCT")).thenReturn(null);
        when(productMapper.deductStockAtomic(10L, 2)).thenReturn(1);
        when(productMapper.findStockBySkuId(10L)).thenReturn(8);
        when(productMapper.findProductIdBySkuId(10L)).thenReturn(1L);

        productStockService.deduct(new StockDeductRequest(10L, 2, "BIZ-LOCK"));

        InOrder inOrder = inOrder(productMapper);
        inOrder.verify(productMapper).findStockTxnByBizNoAndType("BIZ-LOCK", "DEDUCT");
        inOrder.verify(productMapper).findActiveSkuByIdForUpdate(10L);
        inOrder.verify(productMapper).findStockTxnByBizNoAndType("BIZ-LOCK", "DEDUCT");
        inOrder.verify(productMapper).insertStockTxn("BIZ-LOCK", 10L, "DEDUCT", 2, 0);
        inOrder.verify(productMapper).deductStockAtomic(10L, 2);
    }

    @Test
    void shouldLockSkuBeforeWritingStockTxn() {
        SkuRecord sku = new SkuRecord(10L, 1L, "S001", "标准版", "{}", 3900L, 10, 0);
        when(productMapper.findActiveSkuByIdForUpdate(10L)).thenReturn(sku);
        when(productMapper.findStockTxnByBizNoAndType("BIZ-FAST", "DEDUCT")).thenReturn(null);
        when(productMapper.deductStockAtomic(10L, 2)).thenReturn(1);
        when(productMapper.findStockBySkuId(10L)).thenReturn(8);
        when(productMapper.findProductIdBySkuId(10L)).thenReturn(1L);

        productStockService.deduct(new StockDeductRequest(10L, 2, "BIZ-FAST"));

        InOrder inOrder = inOrder(productMapper);
        inOrder.verify(productMapper).findStockTxnByBizNoAndType("BIZ-FAST", "DEDUCT");
        inOrder.verify(productMapper).findActiveSkuByIdForUpdate(10L);
        inOrder.verify(productMapper).findStockTxnByBizNoAndType("BIZ-FAST", "DEDUCT");
        inOrder.verify(productMapper).insertStockTxn("BIZ-FAST", 10L, "DEDUCT", 2, 0);
        inOrder.verify(productMapper).deductStockAtomic(10L, 2);
    }
}
