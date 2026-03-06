package moe.hhm.shiori.order.dto;

public record OrderShippingAddressResponse(
        String receiverName,
        String receiverPhone,
        String province,
        String city,
        String district,
        String detailAddress
) {
}
