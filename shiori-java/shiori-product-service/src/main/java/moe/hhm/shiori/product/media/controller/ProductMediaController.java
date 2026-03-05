package moe.hhm.shiori.product.media.controller;

import jakarta.validation.Valid;
import moe.hhm.shiori.product.media.dto.PresignUploadRequest;
import moe.hhm.shiori.product.media.dto.PresignUploadResponse;
import moe.hhm.shiori.product.security.CurrentUserSupport;
import moe.hhm.shiori.product.storage.OssObjectService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/product/media")
public class ProductMediaController {

    private final OssObjectService ossObjectService;

    public ProductMediaController(OssObjectService ossObjectService) {
        this.ossObjectService = ossObjectService;
    }

    @PostMapping("/presign-upload")
    public PresignUploadResponse presignUpload(@Valid @RequestBody PresignUploadRequest request,
                                               Authentication authentication) {
        Long ownerUserId = CurrentUserSupport.requireUserId(authentication);
        OssObjectService.PresignUploadResult result =
                ossObjectService.presignUpload(ownerUserId, request.fileName(), request.contentType());
        return new PresignUploadResponse(
                result.objectKey(),
                result.uploadUrl(),
                result.expireAt(),
                result.requiredHeaders()
        );
    }
}
