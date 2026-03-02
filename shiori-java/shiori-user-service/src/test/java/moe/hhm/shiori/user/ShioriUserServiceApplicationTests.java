package moe.hhm.shiori.user;

import moe.hhm.shiori.user.auth.repository.AuthUserMapper;
import moe.hhm.shiori.user.profile.repository.UserProfileMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class ShioriUserServiceApplicationTests {

    @MockitoBean
    private AuthUserMapper authUserMapper;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private UserProfileMapper userProfileMapper;

    @Test
    void contextLoads() {
    }

}
