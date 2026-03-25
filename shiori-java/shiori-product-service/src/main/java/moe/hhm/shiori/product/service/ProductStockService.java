package moe.hhm.shiori.product.service;

import moe.hhm.shiori.common.error.ProductErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.product.domain.StockOpType;
import moe.hhm.shiori.product.dto.StockDeductRequest;
import moe.hhm.shiori.product.dto.StockOperateResponse;
import moe.hhm.shiori.product.dto.StockReleaseRequest;
import moe.hhm.shiori.product.model.StockTxnRecord;
import moe.hhm.shiori.product.repository.ProductMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductStockService {

    private final ProductMapper productMapper;

    public ProductStockService(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public StockOperateResponse deduct(StockDeductRequest request) {
        return operateStock(request.bizNo(), request.skuId(), request.quantity(), StockOpType.DEDUCT);
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
        return new StockOperateResponse(true, false, bizNo, skuId, quantity, currentStock);
    }
}
