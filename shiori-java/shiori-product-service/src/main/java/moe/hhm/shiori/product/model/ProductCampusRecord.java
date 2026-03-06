package moe.hhm.shiori.product.model;

public record ProductCampusRecord(
        Long id,
        String campusCode,
        String campusName,
        Integer status,
        Integer sortOrder,
        Integer isDeleted
) {
}
