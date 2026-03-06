package moe.hhm.shiori.social.controller.v2;

import java.util.List;
import moe.hhm.shiori.common.mvc.GlobalExceptionHandler;
import moe.hhm.shiori.common.mvc.ResultResponseBodyAdvice;
import moe.hhm.shiori.social.dto.v2.PostV2PageResponse;
import moe.hhm.shiori.social.service.SocialPostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SocialPostV2ControllerMvcTest {

    @Mock
    private SocialPostService socialPostService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        SocialPostV2Controller controller = new SocialPostV2Controller(socialPostService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler(), new ResultResponseBodyAdvice(new ObjectMapper()))
                .build();
    }

    @Test
    void shouldQueryPostsByAuthorIds() throws Exception {
        when(socialPostService.listPostsByAuthors(List.of(1001L, 1002L), 2, 10))
                .thenReturn(new PostV2PageResponse(0, 2, 10, List.of()));

        mockMvc.perform(post("/api/v2/social/posts/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "authorUserIds":[1001,1002],
                                  "page":2,
                                  "size":10
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(10));

        verify(socialPostService).listPostsByAuthors(List.of(1001L, 1002L), 2, 10);
    }

    @Test
    void shouldRejectInvalidAuthorIds() throws Exception {
        mockMvc.perform(post("/api/v2/social/posts/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "authorUserIds":[1001,-2],
                                  "page":1,
                                  "size":10
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10000));
    }

    @Test
    void shouldRejectOversizedPageSize() throws Exception {
        mockMvc.perform(post("/api/v2/social/posts/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "authorUserIds":[1001,1002],
                                  "page":1,
                                  "size":51
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10000));
    }

    @Test
    void shouldReturnNotFoundForRemovedSquareFeedEndpoint() throws Exception {
        MockMvc rawMockMvc = MockMvcBuilders.standaloneSetup(new SocialPostV2Controller(socialPostService)).build();
        rawMockMvc.perform(get("/api/v2/social/square/feed"))
                .andExpect(status().isNotFound());
    }
}
