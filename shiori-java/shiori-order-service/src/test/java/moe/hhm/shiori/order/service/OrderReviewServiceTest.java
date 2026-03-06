package moe.hhm.shiori.order.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import moe.hhm.shiori.common.exception.BizException;
import moe.hhm.shiori.order.config.OrderProperties;
import moe.hhm.shiori.order.domain.OrderReviewVisibilityStatus;
import moe.hhm.shiori.order.domain.OrderReviewerRole;
import moe.hhm.shiori.order.domain.OrderStatus;
import moe.hhm.shiori.order.dto.v2.OrderReviewUpsertRequest;
import moe.hhm.shiori.order.dto.v2.UserCreditProfileResponse;
import moe.hhm.shiori.order.model.OrderRecord;
import moe.hhm.shiori.order.model.OrderReviewRecord;
import moe.hhm.shiori.order.model.OrderReviewRoleAggregateRecord;
import moe.hhm.shiori.order.repository.OrderMapper;
import moe.hhm.shiori.order.repository.OrderReviewMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderReviewServiceTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderReviewMapper orderReviewMapper;

    private OrderReviewService orderReviewService;

    @BeforeEach
    void setUp() {
        OrderProperties properties = new OrderProperties();
        properties.getReview().setWindowDays(15);
        properties.getReview().setEditHours(24);
        properties.getReview().setCommentMaxLength(280);
        orderReviewService = new OrderReviewService(
                orderMapper,
                orderReviewMapper,
                properties,
                new OrderMetrics(new SimpleMeterRegistry())
        );
    }

    @Test
    void shouldCreateBuyerReviewWithBuyerWeight() {
        LocalDateTime now = LocalDateTime.now();
        String orderNo = "O202603060001";
        OrderRecord finishedOrder = order(orderNo, 1001L, 2001L, now.minusDays(1), now.minusDays(1));
        when(orderMapper.findOrderByOrderNo(orderNo)).thenReturn(finishedOrder);
        when(orderReviewMapper.findByOrderNoAndReviewer(orderNo, 1001L))
                .thenReturn(null)
                .thenReturn(review(
                        1L,
                        orderNo,
                        1001L,
                        2001L,
                        OrderReviewerRole.BUYER.name(),
                        4,
                        5,
                        3,
                        BigDecimal.valueOf(3.9),
                        0,
                        now
                ));

        var response = orderReviewService.createReview(
                1001L,
                orderNo,
                new OrderReviewUpsertRequest(4, 5, 3, "  沟通顺畅，发货及时  ")
        );

        assertThat(response.reviewerRole()).isEqualTo(OrderReviewerRole.BUYER.name());
        assertThat(response.reviewedUserId()).isEqualTo(2001L);
        assertThat(response.overallStar()).isEqualByComparingTo("3.9");

        ArgumentCaptor<moe.hhm.shiori.order.model.OrderReviewEntity> captor = ArgumentCaptor.forClass(moe.hhm.shiori.order.model.OrderReviewEntity.class);
        verify(orderReviewMapper).insertReview(captor.capture());
        assertThat(captor.getValue().getOverallStar()).isEqualByComparingTo("3.9");
        assertThat(captor.getValue().getComment()).isEqualTo("沟通顺畅，发货及时");
    }

    @Test
    void shouldCreateSellerReviewWithSellerWeight() {
        LocalDateTime now = LocalDateTime.now();
        String orderNo = "O202603060002";
        when(orderMapper.findOrderByOrderNo(orderNo))
                .thenReturn(order(orderNo, 1001L, 2001L, now.minusDays(1), now.minusDays(1)));
        when(orderReviewMapper.findByOrderNoAndReviewer(orderNo, 2001L))
                .thenReturn(null)
                .thenReturn(review(
                        2L,
                        orderNo,
                        2001L,
                        1001L,
                        OrderReviewerRole.SELLER.name(),
                        5,
                        2,
                        1,
                        BigDecimal.valueOf(2.6),
                        0,
                        now
                ));

        var response = orderReviewService.createReview(
                2001L,
                orderNo,
                new OrderReviewUpsertRequest(5, 2, 1, "买家配合")
        );

        assertThat(response.reviewerRole()).isEqualTo(OrderReviewerRole.SELLER.name());
        assertThat(response.reviewedUserId()).isEqualTo(1001L);
        assertThat(response.overallStar()).isEqualByComparingTo("2.6");
    }

    @Test
    void shouldRejectCreateWhenReviewWindowExpired() {
        String orderNo = "O202603060003";
        LocalDateTime finishedAt = LocalDateTime.now().minusDays(16);
        when(orderMapper.findOrderByOrderNo(orderNo))
                .thenReturn(order(orderNo, 1001L, 2001L, finishedAt, finishedAt));

        assertThatThrownBy(() -> orderReviewService.createReview(
                1001L,
                orderNo,
                new OrderReviewUpsertRequest(5, 5, 5, "late")
        ))
                .isInstanceOf(BizException.class)
                .matches(ex -> ((BizException) ex).getErrorCode().code() == 50004);
    }

    @Test
    void shouldRejectUpdateWhenEditLimitReached() {
        String orderNo = "O202603060004";
        LocalDateTime now = LocalDateTime.now();
        when(orderMapper.findOrderByOrderNo(orderNo))
                .thenReturn(order(orderNo, 1001L, 2001L, now.minusDays(1), now.minusDays(1)));
        when(orderReviewMapper.findByOrderNoAndReviewer(orderNo, 1001L))
                .thenReturn(review(
                        10L,
                        orderNo,
                        1001L,
                        2001L,
                        OrderReviewerRole.BUYER.name(),
                        5,
                        5,
                        5,
                        BigDecimal.valueOf(5.0),
                        1,
                        now.minusHours(2)
                ));

        assertThatThrownBy(() -> orderReviewService.updateMyReview(
                1001L,
                orderNo,
                new OrderReviewUpsertRequest(4, 4, 4, "修改")
        ))
                .isInstanceOf(BizException.class)
                .matches(ex -> ((BizException) ex).getErrorCode().code() == 50004);
    }

    @Test
    void shouldRejectUpdateWhenEditTimeExceeded() {
        String orderNo = "O202603060005";
        LocalDateTime now = LocalDateTime.now();
        when(orderMapper.findOrderByOrderNo(orderNo))
                .thenReturn(order(orderNo, 1001L, 2001L, now.minusDays(1), now.minusDays(1)));
        when(orderReviewMapper.findByOrderNoAndReviewer(orderNo, 1001L))
                .thenReturn(review(
                        11L,
                        orderNo,
                        1001L,
                        2001L,
                        OrderReviewerRole.BUYER.name(),
                        5,
                        5,
                        5,
                        BigDecimal.valueOf(5.0),
                        0,
                        now.minusHours(25)
                ));

        assertThatThrownBy(() -> orderReviewService.updateMyReview(
                1001L,
                orderNo,
                new OrderReviewUpsertRequest(4, 4, 4, "超时修改")
        ))
                .isInstanceOf(BizException.class)
                .matches(ex -> ((BizException) ex).getErrorCode().code() == 50004);
    }

    @Test
    void shouldBuildCompositeCreditProfileAndGrade() {
        when(orderReviewMapper.listRoleAggregatesByReviewedUser(3001L))
                .thenReturn(List.of(
                        new OrderReviewRoleAggregateRecord(OrderReviewerRole.BUYER.name(), 10L, BigDecimal.valueOf(4.8), 9L),
                        new OrderReviewRoleAggregateRecord(OrderReviewerRole.SELLER.name(), 5L, BigDecimal.valueOf(4.0), 4L)
                ));

        UserCreditProfileResponse response = orderReviewService.getUserCreditProfile(3001L);

        assertThat(response.buyerProfile().role()).isEqualTo("buyer");
        assertThat(response.buyerProfile().reviewCount()).isEqualTo(5L);
        assertThat(response.buyerProfile().avgStar()).isEqualByComparingTo("4.0");
        assertThat(response.buyerProfile().positiveRate()).isEqualByComparingTo("0.8000");

        assertThat(response.sellerProfile().role()).isEqualTo("seller");
        assertThat(response.sellerProfile().reviewCount()).isEqualTo(10L);
        assertThat(response.sellerProfile().avgStar()).isEqualByComparingTo("4.8");
        assertThat(response.sellerProfile().positiveRate()).isEqualByComparingTo("0.9000");

        assertThat(response.composite().totalReviewCount()).isEqualTo(15L);
        assertThat(response.composite().compositeAvgStar()).isEqualByComparingTo("4.5");
        assertThat(response.composite().compositeScore100()).isEqualTo(90);
        assertThat(response.composite().creditGrade()).isEqualTo("A");
    }

    @Test
    void shouldListVisibleReviewsAndHideNegativeComments() {
        LocalDateTime now = LocalDateTime.now();
        when(orderReviewMapper.countVisibleReviewsByReviewedUser(3001L)).thenReturn(2L);
        when(orderReviewMapper.listVisibleReviewsByReviewedUser(3001L, 10, 0))
                .thenReturn(List.of(
                        review(
                                21L,
                                "O202603060021",
                                1002L,
                                3001L,
                                OrderReviewerRole.BUYER.name(),
                                5,
                                5,
                                4,
                                BigDecimal.valueOf(4.7),
                                0,
                                now.minusDays(1)
                        ),
                        new OrderReviewRecord(
                                22L,
                                "O202603060022",
                                1003L,
                                3001L,
                                OrderReviewerRole.SELLER.name(),
                                2,
                                2,
                                2,
                                BigDecimal.valueOf(2.0),
                                "不太顺利",
                                OrderReviewVisibilityStatus.VISIBLE.name(),
                                null,
                                null,
                                null,
                                0,
                                null,
                                now.minusHours(1),
                                now.minusHours(1)
                        )
                ));

        var response = orderReviewService.listUserReviews(3001L, 1, 10);

        assertThat(response.total()).isEqualTo(2L);
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).comment()).isEqualTo("comment");
        assertThat(response.items().get(1).comment()).isNull();
    }

    @Test
    void shouldUpdateVisibilityForAdminModeration() {
        when(orderReviewMapper.updateReviewVisibility(eq(77L), eq(OrderReviewVisibilityStatus.HIDDEN_BY_ADMIN.name()), any(), eq(9001L), any()))
                .thenReturn(1);

        var response = orderReviewService.updateReviewVisibility(9001L, 77L, false, "违规文案");

        assertThat(response.reviewId()).isEqualTo(77L);
        assertThat(response.visibilityStatus()).isEqualTo(OrderReviewVisibilityStatus.HIDDEN_BY_ADMIN.name());
    }

    private OrderRecord order(String orderNo, Long buyerUserId, Long sellerUserId, LocalDateTime finishedAt, LocalDateTime updatedAt) {
        return new OrderRecord(
                1L,
                orderNo,
                buyerUserId,
                sellerUserId,
                OrderStatus.FINISHED.getCode(),
                1000L,
                1,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                finishedAt,
                null,
                null,
                null,
                0,
                updatedAt.minusDays(1),
                updatedAt
        );
    }

    private OrderReviewRecord review(Long id,
                                     String orderNo,
                                     Long reviewerUserId,
                                     Long reviewedUserId,
                                     String reviewerRole,
                                     Integer communicationStar,
                                     Integer timelinessStar,
                                     Integer credibilityStar,
                                     BigDecimal overallStar,
                                     Integer editCount,
                                     LocalDateTime createdAt) {
        return new OrderReviewRecord(
                id,
                orderNo,
                reviewerUserId,
                reviewedUserId,
                reviewerRole,
                communicationStar,
                timelinessStar,
                credibilityStar,
                overallStar,
                "comment",
                OrderReviewVisibilityStatus.VISIBLE.name(),
                null,
                null,
                null,
                editCount,
                null,
                createdAt,
                createdAt
        );
    }
}
