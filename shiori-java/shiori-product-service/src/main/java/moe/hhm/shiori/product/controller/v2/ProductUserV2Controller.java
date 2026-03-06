package moe.hhm.shiori.product.controller.v2;

import moe.hhm.shiori.product.dto.v2.ProductV2PageResponse;
import moe.hhm.shiori.product.service.ProductV2Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/product/users")
public class ProductUserV2Controller {

    private final ProductV2Service productV2Service;

    public ProductUserV2Controller(ProductV2Service productV2Service) {
        this.productV2Service = productV2Service;
    }

    @GetMapping("/{ownerUserId}/products")
    public ProductV2PageResponse listUserOnSaleProducts(@PathVariable Long ownerUserId,
                                                         @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "10") int size,
                                                         @RequestParam(required = false) String keyword,
                                                         @RequestParam(required = false) String categoryCode,
                                                         @RequestParam(required = false) String subCategoryCode,
                                                         @RequestParam(required = false) String conditionLevel,
                                                         @RequestParam(required = false) String tradeMode,
                                                         @RequestParam(required = false) String campusCode,
                                                         @RequestParam(required = false) String sortBy,
                                                         @RequestParam(required = false) String sortDir) {
        return productV2Service.listOnSaleProductsByOwner(ownerUserId, keyword, categoryCode, subCategoryCode,
                conditionLevel, tradeMode, campusCode, sortBy, sortDir, page, size);
    }
}
