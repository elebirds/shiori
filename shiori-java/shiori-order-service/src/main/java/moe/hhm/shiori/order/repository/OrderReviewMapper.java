package moe.hhm.shiori.order.repository;

import moe.hhm.shiori.order.model.OrderReviewEntity;
import moe.hhm.shiori.order.model.OrderReviewRecord;
import moe.hhm.shiori.order.model.OrderReviewRoleAggregateRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderReviewMapper {

    @Insert("""
            INSERT INTO o_order_review (
                order_no,
                reviewer_user_id,
                reviewed_user_id,
                reviewer_role,
                communication_star,
                timeliness_star,
                credibility_star,
                overall_star,
                comment,
                visibility_status,
                visibility_reason,
                visibility_operator_user_id,
                visibility_updated_at,
                edit_count,
                last_edited_at,
                created_at,
                updated_at
            ) VALUES (
                #{orderNo},
                #{reviewerUserId},
                #{reviewedUserId},
                #{reviewerRole},
                #{communicationStar},
                #{timelinessStar},
                #{credibilityStar},
                #{overallStar},
                #{comment},
                #{visibilityStatus},
                #{visibilityReason},
                #{visibilityOperatorUserId},
                #{visibilityUpdatedAt},
                #{editCount},
                #{lastEditedAt},
                CURRENT_TIMESTAMP(3),
                CURRENT_TIMESTAMP(3)
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertReview(OrderReviewEntity entity);

    @Select("""
            SELECT id,
                   order_no AS orderNo,
                   reviewer_user_id AS reviewerUserId,
                   reviewed_user_id AS reviewedUserId,
                   reviewer_role AS reviewerRole,
                   communication_star AS communicationStar,
                   timeliness_star AS timelinessStar,
                   credibility_star AS credibilityStar,
                   overall_star AS overallStar,
                   comment,
                   visibility_status AS visibilityStatus,
                   visibility_reason AS visibilityReason,
                   visibility_operator_user_id AS visibilityOperatorUserId,
                   visibility_updated_at AS visibilityUpdatedAt,
                   edit_count AS editCount,
                   last_edited_at AS lastEditedAt,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM o_order_review
            WHERE order_no = #{orderNo}
              AND reviewer_user_id = #{reviewerUserId}
            LIMIT 1
            """)
    OrderReviewRecord findByOrderNoAndReviewer(@Param("orderNo") String orderNo,
                                               @Param("reviewerUserId") Long reviewerUserId);

    @Select("""
            SELECT id,
                   order_no AS orderNo,
                   reviewer_user_id AS reviewerUserId,
                   reviewed_user_id AS reviewedUserId,
                   reviewer_role AS reviewerRole,
                   communication_star AS communicationStar,
                   timeliness_star AS timelinessStar,
                   credibility_star AS credibilityStar,
                   overall_star AS overallStar,
                   comment,
                   visibility_status AS visibilityStatus,
                   visibility_reason AS visibilityReason,
                   visibility_operator_user_id AS visibilityOperatorUserId,
                   visibility_updated_at AS visibilityUpdatedAt,
                   edit_count AS editCount,
                   last_edited_at AS lastEditedAt,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM o_order_review
            WHERE order_no = #{orderNo}
            ORDER BY id ASC
            """)
    List<OrderReviewRecord> listByOrderNo(@Param("orderNo") String orderNo);

    @Update("""
            UPDATE o_order_review
            SET communication_star = #{communicationStar},
                timeliness_star = #{timelinessStar},
                credibility_star = #{credibilityStar},
                overall_star = #{overallStar},
                comment = #{comment},
                edit_count = edit_count + 1,
                last_edited_at = #{lastEditedAt},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND reviewer_user_id = #{reviewerUserId}
              AND edit_count = #{expectedEditCount}
            """)
    int updateReviewContent(@Param("id") Long id,
                            @Param("reviewerUserId") Long reviewerUserId,
                            @Param("communicationStar") Integer communicationStar,
                            @Param("timelinessStar") Integer timelinessStar,
                            @Param("credibilityStar") Integer credibilityStar,
                            @Param("overallStar") BigDecimal overallStar,
                            @Param("comment") String comment,
                            @Param("lastEditedAt") LocalDateTime lastEditedAt,
                            @Param("expectedEditCount") Integer expectedEditCount);

    @Update("""
            UPDATE o_order_review
            SET visibility_status = #{visibilityStatus},
                visibility_reason = #{visibilityReason},
                visibility_operator_user_id = #{visibilityOperatorUserId},
                visibility_updated_at = #{visibilityUpdatedAt},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE id = #{reviewId}
            """)
    int updateReviewVisibility(@Param("reviewId") Long reviewId,
                               @Param("visibilityStatus") String visibilityStatus,
                               @Param("visibilityReason") String visibilityReason,
                               @Param("visibilityOperatorUserId") Long visibilityOperatorUserId,
                               @Param("visibilityUpdatedAt") LocalDateTime visibilityUpdatedAt);

    @Select("""
            SELECT reviewer_role AS reviewerRole,
                   COUNT(1) AS reviewCount,
                   AVG(overall_star) AS avgOverallStar,
                   SUM(CASE WHEN overall_star >= 4 THEN 1 ELSE 0 END) AS positiveCount
            FROM o_order_review
            WHERE reviewed_user_id = #{reviewedUserId}
              AND visibility_status = 'VISIBLE'
            GROUP BY reviewer_role
            """)
    List<OrderReviewRoleAggregateRecord> listRoleAggregatesByReviewedUser(@Param("reviewedUserId") Long reviewedUserId);

    @Select("""
            SELECT COUNT(1)
            FROM o_order_review
            WHERE reviewed_user_id = #{reviewedUserId}
              AND visibility_status = 'VISIBLE'
              AND overall_star >= 4
              AND comment IS NOT NULL
              AND comment <> ''
            """)
    long countPraiseWallByReviewedUser(@Param("reviewedUserId") Long reviewedUserId);

    @Select("""
            SELECT id,
                   order_no AS orderNo,
                   reviewer_user_id AS reviewerUserId,
                   reviewed_user_id AS reviewedUserId,
                   reviewer_role AS reviewerRole,
                   communication_star AS communicationStar,
                   timeliness_star AS timelinessStar,
                   credibility_star AS credibilityStar,
                   overall_star AS overallStar,
                   comment,
                   visibility_status AS visibilityStatus,
                   visibility_reason AS visibilityReason,
                   visibility_operator_user_id AS visibilityOperatorUserId,
                   visibility_updated_at AS visibilityUpdatedAt,
                   edit_count AS editCount,
                   last_edited_at AS lastEditedAt,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM o_order_review
            WHERE reviewed_user_id = #{reviewedUserId}
              AND visibility_status = 'VISIBLE'
              AND overall_star >= 4
              AND comment IS NOT NULL
              AND comment <> ''
            ORDER BY created_at DESC, id DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<OrderReviewRecord> listPraiseWallByReviewedUser(@Param("reviewedUserId") Long reviewedUserId,
                                                         @Param("size") int size,
                                                         @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM o_order_review
            WHERE 1 = 1
            <if test="reviewedUserId != null">
              AND reviewed_user_id = #{reviewedUserId}
            </if>
            <if test="reviewerUserId != null">
              AND reviewer_user_id = #{reviewerUserId}
            </if>
            <if test="reviewerRole != null and reviewerRole != ''">
              AND reviewer_role = #{reviewerRole}
            </if>
            <if test="visibilityStatus != null and visibilityStatus != ''">
              AND visibility_status = #{visibilityStatus}
            </if>
            <if test="minOverallStar != null">
              AND overall_star <![CDATA[ >= ]]> #{minOverallStar}
            </if>
            <if test="maxOverallStar != null">
              AND overall_star <![CDATA[ <= ]]> #{maxOverallStar}
            </if>
            <if test="createdFrom != null">
              AND created_at <![CDATA[ >= ]]> #{createdFrom}
            </if>
            <if test="createdTo != null">
              AND created_at <![CDATA[ <= ]]> #{createdTo}
            </if>
            </script>
            """)
    long countAdminReviews(@Param("reviewedUserId") Long reviewedUserId,
                           @Param("reviewerUserId") Long reviewerUserId,
                           @Param("reviewerRole") String reviewerRole,
                           @Param("visibilityStatus") String visibilityStatus,
                           @Param("minOverallStar") BigDecimal minOverallStar,
                           @Param("maxOverallStar") BigDecimal maxOverallStar,
                           @Param("createdFrom") LocalDateTime createdFrom,
                           @Param("createdTo") LocalDateTime createdTo);

    @Select("""
            <script>
            SELECT id,
                   order_no AS orderNo,
                   reviewer_user_id AS reviewerUserId,
                   reviewed_user_id AS reviewedUserId,
                   reviewer_role AS reviewerRole,
                   communication_star AS communicationStar,
                   timeliness_star AS timelinessStar,
                   credibility_star AS credibilityStar,
                   overall_star AS overallStar,
                   comment,
                   visibility_status AS visibilityStatus,
                   visibility_reason AS visibilityReason,
                   visibility_operator_user_id AS visibilityOperatorUserId,
                   visibility_updated_at AS visibilityUpdatedAt,
                   edit_count AS editCount,
                   last_edited_at AS lastEditedAt,
                   created_at AS createdAt,
                   updated_at AS updatedAt
            FROM o_order_review
            WHERE 1 = 1
            <if test="reviewedUserId != null">
              AND reviewed_user_id = #{reviewedUserId}
            </if>
            <if test="reviewerUserId != null">
              AND reviewer_user_id = #{reviewerUserId}
            </if>
            <if test="reviewerRole != null and reviewerRole != ''">
              AND reviewer_role = #{reviewerRole}
            </if>
            <if test="visibilityStatus != null and visibilityStatus != ''">
              AND visibility_status = #{visibilityStatus}
            </if>
            <if test="minOverallStar != null">
              AND overall_star <![CDATA[ >= ]]> #{minOverallStar}
            </if>
            <if test="maxOverallStar != null">
              AND overall_star <![CDATA[ <= ]]> #{maxOverallStar}
            </if>
            <if test="createdFrom != null">
              AND created_at <![CDATA[ >= ]]> #{createdFrom}
            </if>
            <if test="createdTo != null">
              AND created_at <![CDATA[ <= ]]> #{createdTo}
            </if>
            ORDER BY created_at DESC, id DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<OrderReviewRecord> listAdminReviews(@Param("reviewedUserId") Long reviewedUserId,
                                             @Param("reviewerUserId") Long reviewerUserId,
                                             @Param("reviewerRole") String reviewerRole,
                                             @Param("visibilityStatus") String visibilityStatus,
                                             @Param("minOverallStar") BigDecimal minOverallStar,
                                             @Param("maxOverallStar") BigDecimal maxOverallStar,
                                             @Param("createdFrom") LocalDateTime createdFrom,
                                             @Param("createdTo") LocalDateTime createdTo,
                                             @Param("size") int size,
                                             @Param("offset") int offset);
}

