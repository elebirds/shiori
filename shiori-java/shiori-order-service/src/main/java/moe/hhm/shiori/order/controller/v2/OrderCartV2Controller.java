package moe.hhm.shiori.order.controller.v2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import moe.hhm.shiori.common.security.authz.PermissionGuard;
import moe.hhm.shiori.order.dto.CreateOrderResponse;
import moe.hhm.shiori.order.dto.v2.CartAddItemRequest;
import moe.hhm.shiori.order.dto.v2.CartCheckoutRequest;
import moe.hhm.shiori.order.dto.v2.CartResponse;
import moe.hhm.shiori.order.dto.v2.CartUpdateItemRequest;
import moe.hhm.shiori.order.security.CurrentUserSupport;
import moe.hhm.shiori.order.service.OrderCartService;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v2/order/cart")
public class OrderCartV2Controller {

    private final OrderCartService orderCartService;
    private final PermissionGuard permissionGuard;

    public OrderCartV2Controller(OrderCartService orderCartService, PermissionGuard permissionGuard) {
        this.orderCartService = orderCartService;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping
    public CartResponse get(Authentication authentication) {
        Long buyerUserId = CurrentUserSupport.requireUserId(authentication);
        return orderCartService.getCart(buyerUserId, CurrentUserSupport.resolveRoles(authentication));
    }

    @PostMapping("/items")
    public CartResponse addItem(@Valid @RequestBody CartAddItemRequest request,
                                Authentication authentication) {
        Long buyerUserId = CurrentUserSupport.requireUserId(authentication);
        return orderCartService.addItem(buyerUserId, CurrentUserSupport.resolveRoles(authentication), request);
    }

    @PutMapping("/items/{itemId}")
    public CartResponse updateItem(@PathVariable @Min(1) Long itemId,
                                   @Valid @RequestBody CartUpdateItemRequest request,
                                   Authentication authentication) {
        Long buyerUserId = CurrentUserSupport.requireUserId(authentication);
        return orderCartService.updateItem(buyerUserId, CurrentUserSupport.resolveRoles(authentication), itemId, request);
    }

    @DeleteMapping("/items/{itemId}")
    public CartResponse removeItem(@PathVariable @Min(1) Long itemId,
                                   Authentication authentication) {
        Long buyerUserId = CurrentUserSupport.requireUserId(authentication);
        return orderCartService.removeItem(buyerUserId, CurrentUserSupport.resolveRoles(authentication), itemId);
    }

    @PostMapping("/checkout")
    public CreateOrderResponse checkout(@Valid @RequestBody(required = false) CartCheckoutRequest request,
                                        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                        Authentication authentication,
                                        HttpServletRequest httpServletRequest) {
        permissionGuard.require("order.create", httpServletRequest::getHeader);
        Long buyerUserId = CurrentUserSupport.requireUserId(authentication);
        return orderCartService.checkout(
                buyerUserId,
                CurrentUserSupport.resolveRoles(authentication),
                idempotencyKey,
                request
        );
    }
}
