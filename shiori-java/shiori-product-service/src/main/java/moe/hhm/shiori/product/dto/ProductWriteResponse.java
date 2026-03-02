package moe.hhm.shiori.product.dto;

public record ProductWriteResponse(
        Long productId,
        String productNo,
        String status
) {
}
