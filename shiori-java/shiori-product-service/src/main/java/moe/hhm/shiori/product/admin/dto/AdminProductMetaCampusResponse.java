package moe.hhm.shiori.product.admin.dto;

public record AdminProductMetaCampusResponse(
        Long id,
        String campusCode,
        String campusName,
        Integer status,
        Integer sortOrder
) {
}
