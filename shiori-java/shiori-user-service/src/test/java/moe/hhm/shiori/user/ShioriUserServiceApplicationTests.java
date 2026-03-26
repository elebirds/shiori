package moe.hhm.shiori.user;

import moe.hhm.shiori.user.admin.controller.InternalUserAuthzController;
import moe.hhm.shiori.user.admin.repository.AdminUserMapper;
import moe.hhm.shiori.user.address.repository.UserAddressMapper;
import moe.hhm.shiori.user.auth.repository.AuthUserMapper;
import moe.hhm.shiori.user.authz.repository.UserAuthzMapper;
import moe.hhm.shiori.user.follow.repository.UserFollowMapper;
import moe.hhm.shiori.user.profile.repository.UserProfileMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "security.gateway-sign.internal-secret=test-gateway-sign-secret-32-bytes-0001"
})
class ShioriUserServiceApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @MockitoBean
    private AuthUserMapper authUserMapper;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private UserProfileMapper userProfileMapper;

    @MockitoBean
    private AdminUserMapper adminUserMapper;

    @MockitoBean
    private UserFollowMapper userFollowMapper;

    @MockitoBean
    private UserAddressMapper userAddressMapper;

    @MockitoBean
    private UserAuthzMapper userAuthzMapper;

    @Test
    void contextLoads() {
    }

    @Test
    void shouldRegisterInternalUserAuthzController() {
        Assertions.assertFalse(
                applicationContext.getBeansOfType(InternalUserAuthzController.class).isEmpty(),
                "internal authz controller should be present in application context"
        );
    }

}
