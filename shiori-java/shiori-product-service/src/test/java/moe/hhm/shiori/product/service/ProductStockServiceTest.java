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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductStockServiceTest {

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductStockService productStockService;

    @Test
    void shouldDeductStockSuccess() {
        when(productMapper.findActiveSkuById(10L)).thenReturn(
                new SkuRecord(10L, 1L, "S001", "标准版", "{}", 3900L, 10, 0)
        );
        when(productMapper.findStockTxnByBizNoAndType("BIZ-1", "DEDUCT")).thenReturn(null);
        when(productMapper.deductStockAtomic(10L, 2)).thenReturn(1);
        when(productMapper.findStockBySkuId(10L)).thenReturn(8);

        StockOperateResponse response = productStockService.deduct(new StockDeductRequest(10L, 2, "BIZ-1"));

        assertThat(response.success()).isTrue();
        assertThat(response.idempotent()).isFalse();
        assertThat(response.currentStock()).isEqualTo(8);
        verify(productMapper).updateStockTxnSuccess("BIZ-1", "DEDUCT", 1);
    }

    @Test
    void shouldRejectWhenDeductStockNotEnough() {
        when(productMapper.findActiveSkuById(10L)).thenReturn(
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
        when(productMapper.findActiveSkuById(10L)).thenReturn(
                new SkuRecord(10L, 1L, "S001", "标准版", "{}", 3900L, 10, 0)
        );
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
        when(productMapper.findActiveSkuById(10L)).thenReturn(
                new SkuRecord(10L, 1L, "S001", "标准版", "{}", 3900L, 10, 0)
        );
        when(productMapper.findStockTxnByBizNoAndType("BIZ-4", "RELEASE")).thenReturn(null);
        when(productMapper.increaseStockAtomic(10L, 2)).thenReturn(1);
        when(productMapper.findStockBySkuId(10L)).thenReturn(12);

        StockOperateResponse response = productStockService.release(new StockReleaseRequest(10L, 2, "BIZ-4"));

        assertThat(response.success()).isTrue();
        assertThat(response.currentStock()).isEqualTo(12);
        verify(productMapper).updateStockTxnSuccess("BIZ-4", "RELEASE", 1);
    }
}
