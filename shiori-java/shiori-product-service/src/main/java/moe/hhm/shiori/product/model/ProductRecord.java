package moe.hhm.shiori.product.model;

public record ProductRecord(
        Long id,
        String productNo,
        Long ownerUserId,
        String title,
        String description,
        String coverObjectKey,
        Integer status,
        Integer isDeleted
) {
}
