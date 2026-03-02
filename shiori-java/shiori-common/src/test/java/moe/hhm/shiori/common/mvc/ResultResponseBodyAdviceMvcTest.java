package moe.hhm.shiori.common.mvc;

import moe.hhm.shiori.common.api.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.nullValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ResultResponseBodyAdviceMvcTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(new DemoController())
                .setControllerAdvice(new ResultResponseBodyAdvice(new ObjectMapper()))
                .build();
    }

    @Test
    void shouldWrapObjectResponse() throws Exception {
        mockMvc.perform(get("/wrap/object"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Result.SUCCESS_CODE))
                .andExpect(jsonPath("$.message").value(Result.SUCCESS_MESSAGE))
                .andExpect(jsonPath("$.data.name").value("alice"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldWrapNullResponse() throws Exception {
        mockMvc.perform(get("/wrap/null"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Result.SUCCESS_CODE))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void shouldNotDoubleWrapResultResponse() throws Exception {
        mockMvc.perform(get("/wrap/result"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Result.SUCCESS_CODE))
                .andExpect(jsonPath("$.data").value("ok"));
    }

    @Test
    void shouldSkipWrapWhenAnnotated() throws Exception {
        mockMvc.perform(get("/wrap/skip"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("skip"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void shouldKeepResponseEntityUnwrapped() throws Exception {
        mockMvc.perform(get("/wrap/entity"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("created"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void shouldWrapStringResponseAsJsonString() throws Exception {
        String content = mockMvc.perform(get("/wrap/string"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(content).contains("\"code\":0");
        assertThat(content).contains("\"data\":\"pong\"");
    }

    @RestController
    static class DemoController {

        @GetMapping("/wrap/object")
        public DemoResponse object() {
            return new DemoResponse("alice");
        }

        @GetMapping("/wrap/null")
        public DemoResponse nil() {
            return null;
        }

        @GetMapping("/wrap/result")
        public Result<String> result() {
            return Result.success("ok");
        }

        @SkipResultWrap
        @GetMapping("/wrap/skip")
        public DemoResponse skip() {
            return new DemoResponse("skip");
        }

        @GetMapping("/wrap/entity")
        public ResponseEntity<DemoResponse> entity() {
            return ResponseEntity.status(HttpStatus.CREATED).body(new DemoResponse("created"));
        }

        @GetMapping("/wrap/string")
        public String text() {
            return "pong";
        }
    }

    record DemoResponse(String name) {
    }
}
