package moe.hhm.shiori.order.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import moe.hhm.shiori.common.error.OrderErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.order.client.ProductDetailSnapshot;
import moe.hhm.shiori.order.client.ProductServiceClient;
import moe.hhm.shiori.order.client.ProductSkuSnapshot;
import moe.hhm.shiori.order.dto.CreateOrderItem;
import moe.hhm.shiori.order.dto.CreateOrderRequest;
import moe.hhm.shiori.order.dto.CreateOrderResponse;
import moe.hhm.shiori.order.dto.v2.CartAddItemRequest;
import moe.hhm.shiori.order.dto.v2.CartCheckoutRequest;
import moe.hhm.shiori.order.dto.v2.CartItemResponse;
import moe.hhm.shiori.order.dto.v2.CartResponse;
import moe.hhm.shiori.order.dto.v2.CartSpecItemResponse;
import moe.hhm.shiori.order.dto.v2.CartUpdateItemRequest;
import moe.hhm.shiori.order.model.CartEntity;
import moe.hhm.shiori.order.model.CartItemEntity;
import moe.hhm.shiori.order.model.CartItemRecord;
import moe.hhm.shiori.order.model.CartRecord;
import moe.hhm.shiori.order.repository.OrderMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OrderCartService {

    private final OrderMapper orderMapper;
    private final ProductServiceClient productServiceClient;
    private final OrderCommandService orderCommandService;

    public OrderCartService(OrderMapper orderMapper,
                            ProductServiceClient productServiceClient,
                            OrderCommandService orderCommandService) {
        this.orderMapper = orderMapper;
        this.productServiceClient = productServiceClient;
        this.orderCommandService = orderCommandService;
    }

    public CartResponse getCart(Long buyerUserId, List<String> roles) {
        return buildCartResponse(buyerUserId, roles);
    }

    @Transactional(rollbackFor = Exception.class)
    public CartResponse addItem(Long buyerUserId, List<String> roles, CartAddItemRequest request) {
        if (request == null || request.productId() == null || request.skuId() == null
                || request.quantity() == null || request.quantity() <= 0) {
            throw new BizException(OrderErrorCode.ORDER_CART_ITEM_INVALID, HttpStatus.BAD_REQUEST);
        }
        ProductDetailSnapshot product = productServiceClient.getProductDetail(request.productId(), buyerUserId, roles);
        validateProductForCart(product, buyerUserId);
        ProductSkuSnapshot sku = findSku(product, request.skuId());
        if (sku == null || sku.priceCent() == null || sku.priceCent() <= 0) {
            throw new BizException(OrderErrorCode.ORDER_CART_ITEM_INVALID, HttpStatus.BAD_REQUEST);
        }
        ensureStockEnough(sku, request.quantity());

        CartRecord cart = orderMapper.findCartByBuyerId(buyerUserId);
        if (cart == null) {
            CartEntity cartEntity = new CartEntity();
            cartEntity.setBuyerUserId(buyerUserId);
            cartEntity.setSellerUserId(product.ownerUserId());
            orderMapper.insertCart(cartEntity);
            cart = new CartRecord(cartEntity.getId(), buyerUserId, product.ownerUserId());
        } else if (!Objects.equals(cart.sellerUserId(), product.ownerUserId())) {
            throw new BizException(OrderErrorCode.ORDER_CART_CROSS_SELLER_NOT_ALLOWED, HttpStatus.BAD_REQUEST);
        }

        CartItemRecord existed = orderMapper.findCartItemByBuyerAndSku(buyerUserId, request.skuId());
        if (existed == null) {
            CartItemEntity entity = new CartItemEntity();
            entity.setCartId(cart.id());
            entity.setBuyerUserId(buyerUserId);
            entity.setSellerUserId(product.ownerUserId());
            entity.setProductId(request.productId());
            entity.setSkuId(request.skuId());
            entity.setQuantity(request.quantity());
            orderMapper.insertCartItem(entity);
        } else {
            int mergedQuantity = existed.quantity() + request.quantity();
            ensureStockEnough(sku, mergedQuantity);
            orderMapper.increaseCartItemQuantity(existed.id(), buyerUserId, request.quantity());
        }
        return buildCartResponse(buyerUserId, roles);
    }

    @Transactional(rollbackFor = Exception.class)
    public CartResponse updateItem(Long buyerUserId, List<String> roles, Long itemId, CartUpdateItemRequest request) {
        if (itemId == null || itemId <= 0 || request == null || request.quantity() == null || request.quantity() <= 0) {
            throw new BizException(OrderErrorCode.ORDER_CART_ITEM_INVALID, HttpStatus.BAD_REQUEST);
        }
        CartItemRecord item = orderMapper.findCartItemByIdAndBuyer(itemId, buyerUserId);
        if (item == null) {
            throw new BizException(OrderErrorCode.ORDER_CART_ITEM_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        ProductDetailSnapshot product = productServiceClient.getProductDetail(item.productId(), buyerUserId, roles);
        validateProductForCart(product, buyerUserId);
        ProductSkuSnapshot sku = findSku(product, item.skuId());
        if (sku == null || sku.priceCent() == null || sku.priceCent() <= 0) {
            throw new BizException(OrderErrorCode.ORDER_CART_ITEM_INVALID, HttpStatus.BAD_REQUEST);
        }
        ensureStockEnough(sku, request.quantity());

        int affected = orderMapper.updateCartItemQuantity(itemId, buyerUserId, request.quantity());
        if (affected == 0) {
            throw new BizException(OrderErrorCode.ORDER_CART_ITEM_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return buildCartResponse(buyerUserId, roles);
    }

    @Transactional(rollbackFor = Exception.class)
    public CartResponse removeItem(Long buyerUserId, List<String> roles, Long itemId) {
        if (itemId == null || itemId <= 0) {
            throw new BizException(OrderErrorCode.ORDER_CART_ITEM_INVALID, HttpStatus.BAD_REQUEST);
        }
        int affected = orderMapper.deleteCartItemByIdAndBuyer(itemId, buyerUserId);
        if (affected == 0) {
            throw new BizException(OrderErrorCode.ORDER_CART_ITEM_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        cleanupEmptyCart(buyerUserId);
        return buildCartResponse(buyerUserId, roles);
    }

    @Transactional(rollbackFor = Exception.class)
    public CreateOrderResponse checkout(Long buyerUserId,
                                        List<String> roles,
                                        String idempotencyKey,
                                        CartCheckoutRequest request) {
        CartRecord cart = orderMapper.findCartByBuyerId(buyerUserId);
        if (cart == null) {
            throw new BizException(OrderErrorCode.ORDER_CART_EMPTY, HttpStatus.BAD_REQUEST);
        }
        List<CartItemRecord> allItems = orderMapper.listCartItemsByBuyerId(buyerUserId);
        if (allItems == null || allItems.isEmpty()) {
            throw new BizException(OrderErrorCode.ORDER_CART_EMPTY, HttpStatus.BAD_REQUEST);
        }

        List<CartItemRecord> selectedItems = resolveSelectedItems(allItems, request == null ? null : request.itemIds());
        if (selectedItems.isEmpty()) {
            throw new BizException(OrderErrorCode.ORDER_CART_EMPTY, HttpStatus.BAD_REQUEST);
        }

        List<CreateOrderItem> orderItems = selectedItems.stream()
                .map(item -> new CreateOrderItem(item.productId(), item.skuId(), item.quantity()))
                .toList();
        CreateOrderRequest createOrderRequest = new CreateOrderRequest(
                orderItems,
                request == null ? null : request.source(),
                request == null ? null : request.conversationId()
        );
        String normalizedKey = StringUtils.hasText(idempotencyKey)
                ? idempotencyKey.trim()
                : UUID.randomUUID().toString().replace("-", "");
        CreateOrderResponse response = orderCommandService.createOrder(
                buyerUserId,
                roles,
                normalizedKey,
                createOrderRequest
        );

        List<Long> selectedIds = selectedItems.stream().map(CartItemRecord::id).toList();
        orderMapper.deleteCartItemsByIdsAndBuyer(buyerUserId, selectedIds);
        cleanupEmptyCart(buyerUserId);
        return response;
    }

    private CartResponse buildCartResponse(Long buyerUserId, List<String> roles) {
        CartRecord cart = orderMapper.findCartByBuyerId(buyerUserId);
        if (cart == null) {
            return new CartResponse(null, null, 0, 0L, List.of());
        }
        List<CartItemRecord> records = orderMapper.listCartItemsByBuyerId(buyerUserId);
        if (records == null || records.isEmpty()) {
            return new CartResponse(cart.id(), cart.sellerUserId(), 0, 0L, List.of());
        }

        Map<Long, ProductDetailSnapshot> productCache = new HashMap<>();
        List<CartItemResponse> items = new ArrayList<>(records.size());
        long totalAmountCent = 0L;
        int totalItemCount = 0;

        for (CartItemRecord record : records) {
            ProductDetailSnapshot product = productCache.computeIfAbsent(
                    record.productId(),
                    id -> safeGetProductDetail(id, buyerUserId, roles)
            );
            ProductSkuSnapshot sku = product == null ? null : findSku(product, record.skuId());

            String displayName = resolveDisplayName(sku, record.skuId());
            List<CartSpecItemResponse> specItems = resolveSpecItems(sku);
            Long priceCent = sku == null ? null : sku.priceCent();
            Integer stock = sku == null ? null : sku.stock();
            long subtotalCent = 0L;
            if (priceCent != null && priceCent > 0) {
                try {
                    subtotalCent = Math.multiplyExact(priceCent, record.quantity());
                } catch (ArithmeticException ignored) {
                    subtotalCent = 0L;
                }
            }
            totalAmountCent += subtotalCent;
            totalItemCount += record.quantity();

            items.add(new CartItemResponse(
                    record.id(),
                    record.productId(),
                    product == null ? null : product.productNo(),
                    product == null ? null : product.title(),
                    product == null ? null : product.coverImageUrl(),
                    record.skuId(),
                    sku == null ? null : sku.skuNo(),
                    displayName,
                    specItems,
                    priceCent,
                    stock,
                    record.quantity(),
                    subtotalCent
            ));
        }
        return new CartResponse(cart.id(), cart.sellerUserId(), totalItemCount, totalAmountCent, items);
    }

    private ProductDetailSnapshot safeGetProductDetail(Long productId, Long buyerUserId, List<String> roles) {
        try {
            return productServiceClient.getProductDetail(productId, buyerUserId, roles);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private void validateProductForCart(ProductDetailSnapshot product, Long buyerUserId) {
        if (product == null || !"ON_SALE".equals(product.status())) {
            throw new BizException(OrderErrorCode.ORDER_PRODUCT_INVALID, HttpStatus.BAD_REQUEST);
        }
        if (Objects.equals(product.ownerUserId(), buyerUserId)) {
            throw new BizException(OrderErrorCode.ORDER_SELF_PURCHASE_NOT_ALLOWED, HttpStatus.BAD_REQUEST);
        }
    }

    private ProductSkuSnapshot findSku(ProductDetailSnapshot product, Long skuId) {
        if (product == null || product.skus() == null) {
            return null;
        }
        for (ProductSkuSnapshot sku : product.skus()) {
            if (Objects.equals(skuId, sku.skuId())) {
                return sku;
            }
        }
        return null;
    }

    private void ensureStockEnough(ProductSkuSnapshot sku, int quantity) {
        if (sku != null && sku.stock() != null && sku.stock() >= 0 && quantity > sku.stock()) {
            throw new BizException(OrderErrorCode.ORDER_STOCK_NOT_ENOUGH, HttpStatus.CONFLICT);
        }
    }

    private List<CartItemRecord> resolveSelectedItems(List<CartItemRecord> allItems, List<Long> selectedIds) {
        if (selectedIds == null || selectedIds.isEmpty()) {
            return allItems;
        }
        Map<Long, CartItemRecord> itemMap = new LinkedHashMap<>();
        for (CartItemRecord item : allItems) {
            itemMap.put(item.id(), item);
        }
        Set<Long> uniqueIds = new LinkedHashSet<>(selectedIds);
        List<CartItemRecord> selected = new ArrayList<>(uniqueIds.size());
        for (Long itemId : uniqueIds) {
            if (itemId == null || itemId <= 0) {
                throw new BizException(OrderErrorCode.ORDER_CART_ITEM_INVALID, HttpStatus.BAD_REQUEST);
            }
            CartItemRecord item = itemMap.get(itemId);
            if (item == null) {
                throw new BizException(OrderErrorCode.ORDER_CART_ITEM_NOT_FOUND, HttpStatus.NOT_FOUND);
            }
            selected.add(item);
        }
        return selected;
    }

    private String resolveDisplayName(ProductSkuSnapshot sku, Long skuId) {
        if (sku == null) {
            return "SKU#" + skuId;
        }
        if (StringUtils.hasText(sku.displayName())) {
            return sku.displayName().trim();
        }
        if (sku.specItems() != null && !sku.specItems().isEmpty()) {
            String merged = sku.specItems().stream()
                    .filter(item -> item != null && StringUtils.hasText(item.name()) && StringUtils.hasText(item.value()))
                    .map(item -> item.name().trim() + ":" + item.value().trim())
                    .reduce((left, right) -> left + " / " + right)
                    .orElse(null);
            if (StringUtils.hasText(merged)) {
                return merged;
            }
        }
        if (StringUtils.hasText(sku.skuNo())) {
            return sku.skuNo();
        }
        return "SKU#" + skuId;
    }

    private List<CartSpecItemResponse> resolveSpecItems(ProductSkuSnapshot sku) {
        if (sku == null || sku.specItems() == null || sku.specItems().isEmpty()) {
            return List.of();
        }
        return sku.specItems().stream()
                .filter(item -> item != null && StringUtils.hasText(item.name()) && StringUtils.hasText(item.value()))
                .map(item -> new CartSpecItemResponse(item.name().trim(), item.value().trim()))
                .toList();
    }

    private void cleanupEmptyCart(Long buyerUserId) {
        CartRecord cart = orderMapper.findCartByBuyerId(buyerUserId);
        if (cart == null) {
            return;
        }
        if (orderMapper.countCartItemsByCartId(cart.id()) == 0) {
            orderMapper.deleteCartByBuyerWhenEmpty(buyerUserId);
        }
    }
}
