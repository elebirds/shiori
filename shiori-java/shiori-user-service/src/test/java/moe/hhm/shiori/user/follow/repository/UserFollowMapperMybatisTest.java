package moe.hhm.shiori.user.follow.repository;

import moe.hhm.shiori.user.follow.model.FollowUserRecord;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.jdbc.Sql;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest
@Import(UserFollowMapperMybatisTest.TestConfig.class)
@MapperScan("moe.hhm.shiori.user.follow.repository")
@Sql(scripts = "/sql/follow_mapper_schema.sql")
class UserFollowMapperMybatisTest {

    @Autowired
    private UserFollowMapper userFollowMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldDeleteFollow() {
        seedUsers();
        jdbcTemplate.update("INSERT INTO u_user_follow (follower_user_id, followed_user_id, created_at) VALUES (1,2,'2026-03-06 10:00:00')");
        int deleted = userFollowMapper.deleteFollow(1L, 2L);
        int deletedAgain = userFollowMapper.deleteFollow(1L, 2L);

        assertThat(deleted).isEqualTo(1);
        assertThat(deletedAgain).isEqualTo(0);
    }

    @Test
    void shouldCountWithDeletedUserFiltered() {
        seedUsers();
        jdbcTemplate.update("INSERT INTO u_user_follow (follower_user_id, followed_user_id, created_at) VALUES (1,2,'2026-03-06 10:00:00')");
        jdbcTemplate.update("INSERT INTO u_user_follow (follower_user_id, followed_user_id, created_at) VALUES (3,2,'2026-03-06 10:01:00')");
        jdbcTemplate.update("INSERT INTO u_user_follow (follower_user_id, followed_user_id, created_at) VALUES (4,2,'2026-03-06 10:02:00')");
        jdbcTemplate.update("INSERT INTO u_user_follow (follower_user_id, followed_user_id, created_at) VALUES (1,4,'2026-03-06 10:03:00')");

        long followerCount = userFollowMapper.countFollowers(2L);
        long followingCount = userFollowMapper.countFollowing(1L);

        assertThat(followerCount).isEqualTo(2L);
        assertThat(followingCount).isEqualTo(1L);
    }

    @Test
    void shouldListFollowersAndFollowingByRecentFirst() {
        seedUsers();
        jdbcTemplate.update("INSERT INTO u_user_follow (follower_user_id, followed_user_id, created_at) VALUES (1,2,'2026-03-06 10:00:00')");
        jdbcTemplate.update("INSERT INTO u_user_follow (follower_user_id, followed_user_id, created_at) VALUES (3,2,'2026-03-06 10:05:00')");
        jdbcTemplate.update("INSERT INTO u_user_follow (follower_user_id, followed_user_id, created_at) VALUES (1,3,'2026-03-06 10:06:00')");

        List<FollowUserRecord> followers = userFollowMapper.listFollowers(2L, 10, 0);
        List<FollowUserRecord> following = userFollowMapper.listFollowing(1L, 10, 0);

        assertThat(followers).hasSize(2);
        assertThat(followers.get(0).userId()).isEqualTo(3L);
        assertThat(followers.get(1).userId()).isEqualTo(1L);

        assertThat(following).hasSize(2);
        assertThat(following.get(0).userId()).isEqualTo(3L);
        assertThat(following.get(1).userId()).isEqualTo(2L);
    }

    private void seedUsers() {
        jdbcTemplate.update("INSERT INTO u_user (id, user_no, username, nickname, avatar_url, bio, is_deleted) VALUES (1,'U1','u1','n1','a1.jpg','b1',0)");
        jdbcTemplate.update("INSERT INTO u_user (id, user_no, username, nickname, avatar_url, bio, is_deleted) VALUES (2,'U2','u2','n2','a2.jpg','b2',0)");
        jdbcTemplate.update("INSERT INTO u_user (id, user_no, username, nickname, avatar_url, bio, is_deleted) VALUES (3,'U3','u3','n3','a3.jpg','b3',0)");
        jdbcTemplate.update("INSERT INTO u_user (id, user_no, username, nickname, avatar_url, bio, is_deleted) VALUES (4,'U4','u4','n4','a4.jpg','b4',1)");
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:follow_mapper;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            return dataSource;
        }
    }
}
