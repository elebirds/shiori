package moe.hhm.shiori.product.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import moe.hhm.shiori.common.error.ProductErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.product.dto.SpecItemInput;
import moe.hhm.shiori.product.dto.SpecItemResponse;
import moe.hhm.shiori.product.model.SkuRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class SkuSpecCodec {

    private static final TypeReference<List<SpecItemResponse>> SPEC_ITEMS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public SkuSpecCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<SpecItemResponse> normalizeInput(List<SpecItemInput> rawItems) {
        if (rawItems == null || rawItems.isEmpty()) {
            throw new BizException(ProductErrorCode.INVALID_SKU_SPEC_ITEMS, HttpStatus.BAD_REQUEST);
        }
        List<SpecItemResponse> normalized = new ArrayList<>(rawItems.size());
        Set<String> seenNames = new LinkedHashSet<>();
        for (SpecItemInput item : rawItems) {
            if (item == null) {
                throw new BizException(ProductErrorCode.INVALID_SKU_SPEC_ITEMS, HttpStatus.BAD_REQUEST);
            }
            String name = normalizeText(item.name());
            String value = normalizeText(item.value());
            if (!StringUtils.hasText(name) || !StringUtils.hasText(value)) {
                throw new BizException(ProductErrorCode.INVALID_SKU_SPEC_ITEMS, HttpStatus.BAD_REQUEST);
            }
            if (!seenNames.add(name)) {
                throw new BizException(ProductErrorCode.INVALID_SKU_SPEC_ITEMS, HttpStatus.BAD_REQUEST);
            }
            normalized.add(new SpecItemResponse(name, value));
        }
        normalized.sort(Comparator.comparing(SpecItemResponse::name));
        return normalized;
    }

    public List<SpecItemResponse> fromSkuRecord(SkuRecord sku) {
        List<SpecItemResponse> fromSpecItemsJson = tryParseSpecItemsJson(sku.specItemsJson());
        if (!fromSpecItemsJson.isEmpty()) {
            return fromSpecItemsJson;
        }
        List<SpecItemResponse> fromSpecJson = tryParseLegacySpecJson(sku.specJson());
        if (!fromSpecJson.isEmpty()) {
            return fromSpecJson;
        }
        String fallback = firstText(sku.displayName(), sku.skuName(), "默认规格");
        return List.of(new SpecItemResponse("规格", fallback));
    }

    public String toSpecItemsJson(List<SpecItemResponse> specItems) {
        try {
            return objectMapper.writeValueAsString(specItems);
        } catch (Exception ex) {
            throw new BizException(ProductErrorCode.INVALID_SKU_SPEC_ITEMS, HttpStatus.BAD_REQUEST);
        }
    }

    public String toLegacySpecJson(List<SpecItemResponse> specItems) {
        Map<String, String> specObject = new LinkedHashMap<>();
        for (SpecItemResponse item : specItems) {
            specObject.put(item.name(), item.value());
        }
        try {
            return objectMapper.writeValueAsString(specObject);
        } catch (Exception ex) {
            throw new BizException(ProductErrorCode.INVALID_SKU_SPEC_ITEMS, HttpStatus.BAD_REQUEST);
        }
    }

    public String toDisplayName(List<SpecItemResponse> specItems) {
        return specItems.stream()
                .map(item -> item.name() + ":" + item.value())
                .reduce((left, right) -> left + " / " + right)
                .orElse("默认规格");
    }

    public String toSpecSignature(List<SpecItemResponse> specItems) {
        String canonical = toSpecItemsJson(specItems);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private List<SpecItemResponse> tryParseSpecItemsJson(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            List<SpecItemResponse> parsed = objectMapper.readValue(raw, SPEC_ITEMS_TYPE);
            if (parsed == null || parsed.isEmpty()) {
                return List.of();
            }
            List<SpecItemResponse> normalized = new ArrayList<>(parsed.size());
            Set<String> seenNames = new LinkedHashSet<>();
            for (SpecItemResponse item : parsed) {
                if (item == null) {
                    continue;
                }
                String name = normalizeText(item.name());
                String value = normalizeText(item.value());
                if (!StringUtils.hasText(name) || !StringUtils.hasText(value)) {
                    continue;
                }
                if (!seenNames.add(name)) {
                    continue;
                }
                normalized.add(new SpecItemResponse(name, value));
            }
            normalized.sort(Comparator.comparing(SpecItemResponse::name));
            return normalized;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<SpecItemResponse> tryParseLegacySpecJson(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(raw, MAP_TYPE);
            if (parsed == null || parsed.isEmpty()) {
                return List.of();
            }
            List<SpecItemResponse> normalized = new ArrayList<>(parsed.size());
            for (Map.Entry<String, Object> entry : parsed.entrySet()) {
                String name = normalizeText(entry.getKey());
                String value = normalizeText(entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
                if (!StringUtils.hasText(name) || !StringUtils.hasText(value)) {
                    continue;
                }
                normalized.add(new SpecItemResponse(name, value));
            }
            normalized.sort(Comparator.comparing(SpecItemResponse::name));
            return normalized;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String normalizeText(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw.trim();
    }

    private String firstText(String first, String second, String fallback) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        if (StringUtils.hasText(second)) {
            return second.trim();
        }
        return fallback;
    }
}
