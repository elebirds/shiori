package moe.hhm.shiori.product.media.controller;

import java.util.Map;
import moe.hhm.shiori.common.error.ProductErrorCode;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.common.mvc.GlobalExceptionHandler;
import moe.hhm.shiori.common.mvc.ResultResponseBodyAdvice;
import moe.hhm.shiori.product.storage.OssObjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductMediaControllerMvcTest {

    @Mock
    private OssObjectService ossObjectService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        ProductMediaController controller = new ProductMediaController(ossObjectService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler(), new ResultResponseBodyAdvice(new ObjectMapper()))
                .build();
    }

    @Test
    void shouldReturnPresignUploadResponse() throws Exception {
        when(ossObjectService.presignUpload(1001L, "cover.jpg", "image/jpeg"))
                .thenReturn(new OssObjectService.PresignUploadResult(
                        "product/1001/202603/abc.jpg",
                        "http://upload.local/url",
                        1_777_777_777_000L,
                        Map.of("Content-Type", "image/jpeg")
                ));

        mockMvc.perform(post("/api/product/media/presign-upload")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "1001", "N/A", java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fileName":"cover.jpg","contentType":"image/jpeg"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.objectKey").value("product/1001/202603/abc.jpg"))
                .andExpect(jsonPath("$.data.uploadUrl").value("http://upload.local/url"));

        verify(ossObjectService).presignUpload(1001L, "cover.jpg", "image/jpeg");
    }

    @Test
    void shouldReturnUnifiedErrorWhenInvalidFileExtension() throws Exception {
        when(ossObjectService.presignUpload(1001L, "cover.exe", "application/octet-stream"))
                .thenThrow(new BizException(ProductErrorCode.INVALID_MEDIA_OBJECT_KEY, HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/product/media/presign-upload")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "1001", "N/A", java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fileName":"cover.exe","contentType":"application/octet-stream"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ProductErrorCode.INVALID_MEDIA_OBJECT_KEY.code()));
    }
}
