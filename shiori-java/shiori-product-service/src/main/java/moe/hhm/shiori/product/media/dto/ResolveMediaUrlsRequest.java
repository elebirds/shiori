package moe.hhm.shiori.product.media.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ResolveMediaUrlsRequest(
        @NotEmpty @Size(max = 50) List<@Size(max = 255) String> objectKeys
) {
}
