package moe.hhm.shiori.common.mvc;

import tools.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.common.error.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerMvcTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        this.mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldReturnValidationError() throws Exception {
        mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DemoRequest(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.INVALID_PARAM.code()))
                .andExpect(jsonPath("$.data.errors[0].field").value("name"));
    }

    @Test
    void shouldReturnBizExceptionResult() throws Exception {
        mockMvc.perform(get("/test/biz"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.NOT_FOUND.code()))
                .andExpect(jsonPath("$.message").value(CommonErrorCode.NOT_FOUND.message()));
    }

    @Test
    void shouldReturnInternalServerErrorForUnknownException() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.INTERNAL_SERVER_ERROR.code()));
    }

    @RestController
    static class TestController {

        @PostMapping("/test/valid")
        public String valid(@Valid @RequestBody DemoRequest request) {
            return "ok";
        }

        @GetMapping("/test/biz")
        public String biz() {
            throw new BizException(CommonErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        @GetMapping("/test/boom")
        public String boom() {
            throw new IllegalStateException("boom");
        }
    }

    record DemoRequest(@NotBlank(message = "name不能为空") String name) {
    }
}
