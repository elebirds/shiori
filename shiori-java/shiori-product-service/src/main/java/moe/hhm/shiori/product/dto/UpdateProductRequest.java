package moe.hhm.shiori.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateProductRequest(
        @NotBlank(message = "商品标题不能为空")
        @Size(max = 120, message = "商品标题长度不能超过120")
        String title,
        @Size(max = 5000, message = "商品描述长度不能超过5000")
        String description,
        @Size(max = 255, message = "封面对象键长度不能超过255")
        String coverObjectKey,
        @NotEmpty(message = "SKU列表不能为空")
        @Valid
        List<SkuInput> skus
) {
}
