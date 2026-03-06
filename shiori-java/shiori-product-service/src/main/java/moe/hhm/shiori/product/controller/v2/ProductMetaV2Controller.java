package moe.hhm.shiori.product.controller.v2;

import java.util.List;
import moe.hhm.shiori.product.dto.v2.ProductMetaCampusResponse;
import moe.hhm.shiori.product.dto.v2.ProductMetaCategoryResponse;
import moe.hhm.shiori.product.service.ProductMetaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/product/meta")
public class ProductMetaV2Controller {

    private final ProductMetaService productMetaService;

    public ProductMetaV2Controller(ProductMetaService productMetaService) {
        this.productMetaService = productMetaService;
    }

    @GetMapping("/campuses")
    public List<ProductMetaCampusResponse> listCampuses() {
        return productMetaService.listEnabledCampuses();
    }

    @GetMapping("/categories")
    public List<ProductMetaCategoryResponse> listCategories() {
        return productMetaService.listEnabledCategories();
    }
}
