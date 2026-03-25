package moe.hhm.shiori.product.repository;

import java.time.LocalDateTime;
import java.util.List;
import moe.hhm.shiori.product.model.ProductEntity;
import moe.hhm.shiori.product.model.ProductOutboxEventEntity;
import moe.hhm.shiori.product.model.ProductOutboxEventRecord;
import moe.hhm.shiori.product.model.ProductRecord;
import moe.hhm.shiori.product.model.ProductV2Record;
import moe.hhm.shiori.product.model.SkuEntity;
import moe.hhm.shiori.product.model.SkuRecord;
import moe.hhm.shiori.product.model.StockTxnRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProductMapper {

    @Insert("""
            INSERT INTO p_product (
                product_no,
                owner_user_id,
                title,
                description,
                detail_html,
                cover_object_key,
                category_code,
                sub_category_code,
                condition_level,
                trade_mode,
                campus_code,
                status,
                is_deleted,
                created_at,
                updated_at
            ) VALUES (
                #{productNo},
                #{ownerUserId},
                #{title},
                #{description},
                #{detailHtml},
                #{coverObjectKey},
                #{categoryCode},
                #{subCategoryCode},
                #{conditionLevel},
                #{tradeMode},
                #{campusCode},
                #{status},
                0,
                CURRENT_TIMESTAMP(3),
                CURRENT_TIMESTAMP(3)
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertProduct(ProductEntity product);

    @Update("""
            UPDATE p_product
            SET title = #{title},
                description = #{description},
                detail_html = #{detailHtml},
                cover_object_key = #{coverObjectKey},
                category_code = #{categoryCode},
                sub_category_code = #{subCategoryCode},
                condition_level = #{conditionLevel},
                trade_mode = #{tradeMode},
                campus_code = #{campusCode},
                updated_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE id = #{productId}
              AND is_deleted = 0
            """)
    int updateProductBase(@Param("productId") Long productId,
                          @Param("title") String title,
                          @Param("description") String description,
                          @Param("detailHtml") String detailHtml,
                          @Param("coverObjectKey") String coverObjectKey,
                          @Param("categoryCode") String categoryCode,
                          @Param("subCategoryCode") String subCategoryCode,
                          @Param("conditionLevel") String conditionLevel,
                          @Param("tradeMode") String tradeMode,
                          @Param("campusCode") String campusCode);

    @Select("""
            SELECT id,
                   product_no AS productNo,
                   owner_user_id AS ownerUserId,
                   title,
                   description,
                   cover_object_key AS coverObjectKey,
                   status,
                   is_deleted AS isDeleted
            FROM p_product
            WHERE id = #{productId}
            LIMIT 1
            """)
    ProductRecord findProductById(@Param("productId") Long productId);

    @Select("""
            SELECT p.id,
                   p.product_no AS productNo,
                   p.owner_user_id AS ownerUserId,
                   p.title,
                   p.description,
                   p.detail_html AS detailHtml,
                   p.cover_object_key AS coverObjectKey,
                   p.category_code AS categoryCode,
                   p.sub_category_code AS subCategoryCode,
                   p.condition_level AS conditionLevel,
                   p.trade_mode AS tradeMode,
                   p.campus_code AS campusCode,
                   p.status,
                   p.is_deleted AS isDeleted,
                   agg.min_price_cent AS minPriceCent,
                   agg.max_price_cent AS maxPriceCent,
                   agg.total_stock AS totalStock
            FROM p_product p
            LEFT JOIN (
                SELECT product_id,
                       MIN(price_cent) AS min_price_cent,
                       MAX(price_cent) AS max_price_cent,
                       SUM(stock) AS total_stock
                FROM p_sku
                WHERE is_deleted = 0
                GROUP BY product_id
            ) agg ON agg.product_id = p.id
            WHERE p.id = #{productId}
            LIMIT 1
            """)
    ProductV2Record findProductV2ById(@Param("productId") Long productId);

    @Select("""
            SELECT id,
                   product_no AS productNo,
                   owner_user_id AS ownerUserId,
                   title,
                   description,
                   cover_object_key AS coverObjectKey,
                   status,
                   is_deleted AS isDeleted
            FROM p_product
            WHERE id = #{productId}
              AND is_deleted = 0
              AND status = 2
            LIMIT 1
            """)
    ProductRecord findOnSaleProductById(@Param("productId") Long productId);

    @Select("""
            SELECT owner_user_id
            FROM p_product
            WHERE id = #{productId}
              AND is_deleted = 0
              AND status = 2
            LIMIT 1
            """)
    Long findOnSaleOwnerUserIdByProductId(@Param("productId") Long productId);

    @Select("""
            SELECT p.id,
                   p.product_no AS productNo,
                   p.owner_user_id AS ownerUserId,
                   p.title,
                   p.description,
                   p.detail_html AS detailHtml,
                   p.cover_object_key AS coverObjectKey,
                   p.category_code AS categoryCode,
                   p.sub_category_code AS subCategoryCode,
                   p.condition_level AS conditionLevel,
                   p.trade_mode AS tradeMode,
                   p.campus_code AS campusCode,
                   p.status,
                   p.is_deleted AS isDeleted,
                   agg.min_price_cent AS minPriceCent,
                   agg.max_price_cent AS maxPriceCent,
                   agg.total_stock AS totalStock
            FROM p_product p
            LEFT JOIN (
                SELECT product_id,
                       MIN(price_cent) AS min_price_cent,
                       MAX(price_cent) AS max_price_cent,
                       SUM(stock) AS total_stock
                FROM p_sku
                WHERE is_deleted = 0
                GROUP BY product_id
            ) agg ON agg.product_id = p.id
            WHERE p.id = #{productId}
              AND p.is_deleted = 0
              AND p.status = 2
            LIMIT 1
            """)
    ProductV2Record findOnSaleProductV2ById(@Param("productId") Long productId);

    @Select("""
            <script>
            SELECT id,
                   product_no AS productNo,
                   owner_user_id AS ownerUserId,
                   title,
                   description,
                   cover_object_key AS coverObjectKey,
                   status,
                   is_deleted AS isDeleted
            FROM p_product
            WHERE is_deleted = 0
              AND status = 2
            <if test="keyword != null and keyword != ''">
              AND (title LIKE CONCAT('%', #{keyword}, '%')
                   OR description LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY id DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<ProductRecord> listOnSaleProducts(@Param("keyword") String keyword,
                                           @Param("size") int size,
                                           @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM p_product
            WHERE is_deleted = 0
              AND status = 2
            <if test="keyword != null and keyword != ''">
              AND (title LIKE CONCAT('%', #{keyword}, '%')
                   OR description LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            </script>
            """)
    long countOnSaleProducts(@Param("keyword") String keyword);

    @Select("""
            <script>
            SELECT p.id,
                   p.product_no AS productNo,
                   p.owner_user_id AS ownerUserId,
                   p.title,
                   p.description,
                   NULL AS detailHtml,
                   p.cover_object_key AS coverObjectKey,
                   p.category_code AS categoryCode,
                   p.sub_category_code AS subCategoryCode,
                   p.condition_level AS conditionLevel,
                   p.trade_mode AS tradeMode,
                   p.campus_code AS campusCode,
                   p.status,
                   p.is_deleted AS isDeleted,
                   agg.min_price_cent AS minPriceCent,
                   agg.max_price_cent AS maxPriceCent,
                   agg.total_stock AS totalStock
            FROM p_product p
            LEFT JOIN (
                SELECT product_id,
                       MIN(price_cent) AS min_price_cent,
                       MAX(price_cent) AS max_price_cent,
                       SUM(stock) AS total_stock
                FROM p_sku
                WHERE is_deleted = 0
                GROUP BY product_id
            ) agg ON agg.product_id = p.id
            WHERE p.is_deleted = 0
              AND p.status = 2
            <if test="keyword != null and keyword != ''">
              AND (p.title LIKE CONCAT('%', #{keyword}, '%')
                   OR p.description LIKE CONCAT('%', #{keyword}, '%')
                   OR p.product_no LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="categoryCode != null and categoryCode != ''">
              AND p.category_code = #{categoryCode}
            </if>
            <if test="subCategoryCode != null and subCategoryCode != ''">
              AND p.sub_category_code = #{subCategoryCode}
            </if>
            <if test="conditionLevel != null and conditionLevel != ''">
              AND p.condition_level = #{conditionLevel}
            </if>
            <if test="tradeMode != null and tradeMode != ''">
              AND p.trade_mode = #{tradeMode}
            </if>
            <if test="campusCode != null and campusCode != ''">
              AND p.campus_code = #{campusCode}
            </if>
            ORDER BY
            <choose>
              <when test="sortBy == 'MIN_PRICE'">IFNULL(agg.min_price_cent, 0)</when>
              <when test="sortBy == 'MAX_PRICE'">IFNULL(agg.max_price_cent, 0)</when>
              <otherwise>p.created_at</otherwise>
            </choose>
            <choose>
              <when test="sortDir == 'ASC'">ASC</when>
              <otherwise>DESC</otherwise>
            </choose>,
            p.id DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<ProductV2Record> listOnSaleProductsV2(@Param("keyword") String keyword,
                                               @Param("categoryCode") String categoryCode,
                                               @Param("subCategoryCode") String subCategoryCode,
                                               @Param("conditionLevel") String conditionLevel,
                                               @Param("tradeMode") String tradeMode,
                                               @Param("campusCode") String campusCode,
                                               @Param("sortBy") String sortBy,
                                               @Param("sortDir") String sortDir,
                                               @Param("size") int size,
                                               @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM p_product p
            WHERE p.is_deleted = 0
              AND p.status = 2
            <if test="keyword != null and keyword != ''">
              AND (p.title LIKE CONCAT('%', #{keyword}, '%')
                   OR p.description LIKE CONCAT('%', #{keyword}, '%')
                   OR p.product_no LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="categoryCode != null and categoryCode != ''">
              AND p.category_code = #{categoryCode}
            </if>
            <if test="subCategoryCode != null and subCategoryCode != ''">
              AND p.sub_category_code = #{subCategoryCode}
            </if>
            <if test="conditionLevel != null and conditionLevel != ''">
              AND p.condition_level = #{conditionLevel}
            </if>
            <if test="tradeMode != null and tradeMode != ''">
              AND p.trade_mode = #{tradeMode}
            </if>
            <if test="campusCode != null and campusCode != ''">
              AND p.campus_code = #{campusCode}
            </if>
            </script>
            """)
    long countOnSaleProductsV2(@Param("keyword") String keyword,
                               @Param("categoryCode") String categoryCode,
                               @Param("subCategoryCode") String subCategoryCode,
                               @Param("conditionLevel") String conditionLevel,
                               @Param("tradeMode") String tradeMode,
                               @Param("campusCode") String campusCode);

    @Select("""
            <script>
            SELECT id,
                   product_no AS productNo,
                   owner_user_id AS ownerUserId,
                   title,
                   description,
                   cover_object_key AS coverObjectKey,
                   status,
                   is_deleted AS isDeleted
            FROM p_product
            WHERE is_deleted = 0
              AND owner_user_id = #{ownerUserId}
            <if test="keyword != null and keyword != ''">
              AND (title LIKE CONCAT('%', #{keyword}, '%')
                   OR description LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="status != null">
              AND status = #{status}
            </if>
            ORDER BY id DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<ProductRecord> listProductsByOwner(@Param("ownerUserId") Long ownerUserId,
                                            @Param("keyword") String keyword,
                                            @Param("status") Integer status,
                                            @Param("size") int size,
                                            @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM p_product
            WHERE is_deleted = 0
              AND owner_user_id = #{ownerUserId}
            <if test="keyword != null and keyword != ''">
              AND (title LIKE CONCAT('%', #{keyword}, '%')
                   OR description LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="status != null">
              AND status = #{status}
            </if>
            </script>
            """)
    long countProductsByOwner(@Param("ownerUserId") Long ownerUserId,
                              @Param("keyword") String keyword,
                              @Param("status") Integer status);

    @Select("""
            <script>
            SELECT p.id,
                   p.product_no AS productNo,
                   p.owner_user_id AS ownerUserId,
                   p.title,
                   p.description,
                   NULL AS detailHtml,
                   p.cover_object_key AS coverObjectKey,
                   p.category_code AS categoryCode,
                   p.sub_category_code AS subCategoryCode,
                   p.condition_level AS conditionLevel,
                   p.trade_mode AS tradeMode,
                   p.campus_code AS campusCode,
                   p.status,
                   p.is_deleted AS isDeleted,
                   agg.min_price_cent AS minPriceCent,
                   agg.max_price_cent AS maxPriceCent,
                   agg.total_stock AS totalStock
            FROM p_product p
            LEFT JOIN (
                SELECT product_id,
                       MIN(price_cent) AS min_price_cent,
                       MAX(price_cent) AS max_price_cent,
                       SUM(stock) AS total_stock
                FROM p_sku
                WHERE is_deleted = 0
                GROUP BY product_id
            ) agg ON agg.product_id = p.id
            WHERE p.is_deleted = 0
              AND p.owner_user_id = #{ownerUserId}
            <if test="keyword != null and keyword != ''">
              AND (p.title LIKE CONCAT('%', #{keyword}, '%')
                   OR p.description LIKE CONCAT('%', #{keyword}, '%')
                   OR p.product_no LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="status != null">
              AND p.status = #{status}
            </if>
            <if test="categoryCode != null and categoryCode != ''">
              AND p.category_code = #{categoryCode}
            </if>
            <if test="subCategoryCode != null and subCategoryCode != ''">
              AND p.sub_category_code = #{subCategoryCode}
            </if>
            <if test="conditionLevel != null and conditionLevel != ''">
              AND p.condition_level = #{conditionLevel}
            </if>
            <if test="tradeMode != null and tradeMode != ''">
              AND p.trade_mode = #{tradeMode}
            </if>
            <if test="campusCode != null and campusCode != ''">
              AND p.campus_code = #{campusCode}
            </if>
            ORDER BY
            <choose>
              <when test="sortBy == 'MIN_PRICE'">IFNULL(agg.min_price_cent, 0)</when>
              <when test="sortBy == 'MAX_PRICE'">IFNULL(agg.max_price_cent, 0)</when>
              <otherwise>p.created_at</otherwise>
            </choose>
            <choose>
              <when test="sortDir == 'ASC'">ASC</when>
              <otherwise>DESC</otherwise>
            </choose>,
            p.id DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<ProductV2Record> listProductsByOwnerV2(@Param("ownerUserId") Long ownerUserId,
                                                @Param("keyword") String keyword,
                                                @Param("status") Integer status,
                                                @Param("categoryCode") String categoryCode,
                                                @Param("subCategoryCode") String subCategoryCode,
                                                @Param("conditionLevel") String conditionLevel,
                                                @Param("tradeMode") String tradeMode,
                                                @Param("campusCode") String campusCode,
                                                @Param("sortBy") String sortBy,
                                                @Param("sortDir") String sortDir,
                                                @Param("size") int size,
                                                @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM p_product p
            WHERE p.is_deleted = 0
              AND p.owner_user_id = #{ownerUserId}
            <if test="keyword != null and keyword != ''">
              AND (p.title LIKE CONCAT('%', #{keyword}, '%')
                   OR p.description LIKE CONCAT('%', #{keyword}, '%')
                   OR p.product_no LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="status != null">
              AND p.status = #{status}
            </if>
            <if test="categoryCode != null and categoryCode != ''">
              AND p.category_code = #{categoryCode}
            </if>
            <if test="subCategoryCode != null and subCategoryCode != ''">
              AND p.sub_category_code = #{subCategoryCode}
            </if>
            <if test="conditionLevel != null and conditionLevel != ''">
              AND p.condition_level = #{conditionLevel}
            </if>
            <if test="tradeMode != null and tradeMode != ''">
              AND p.trade_mode = #{tradeMode}
            </if>
            <if test="campusCode != null and campusCode != ''">
              AND p.campus_code = #{campusCode}
            </if>
            </script>
            """)
    long countProductsByOwnerV2(@Param("ownerUserId") Long ownerUserId,
                                @Param("keyword") String keyword,
                                @Param("status") Integer status,
                                @Param("categoryCode") String categoryCode,
                                @Param("subCategoryCode") String subCategoryCode,
                                @Param("conditionLevel") String conditionLevel,
                                @Param("tradeMode") String tradeMode,
                                @Param("campusCode") String campusCode);

    @Select("""
            <script>
            SELECT id,
                   product_no AS productNo,
                   owner_user_id AS ownerUserId,
                   title,
                   description,
                   cover_object_key AS coverObjectKey,
                   status,
                   is_deleted AS isDeleted
            FROM p_product
            WHERE is_deleted = 0
            <if test="keyword != null and keyword != ''">
              AND (title LIKE CONCAT('%', #{keyword}, '%')
                   OR description LIKE CONCAT('%', #{keyword}, '%')
                   OR product_no LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="status != null">
              AND status = #{status}
            </if>
            <if test="ownerUserId != null">
              AND owner_user_id = #{ownerUserId}
            </if>
            ORDER BY id DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<ProductRecord> listProductsForAdmin(@Param("keyword") String keyword,
                                             @Param("status") Integer status,
                                             @Param("ownerUserId") Long ownerUserId,
                                             @Param("size") int size,
                                             @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM p_product
            WHERE is_deleted = 0
            <if test="keyword != null and keyword != ''">
              AND (title LIKE CONCAT('%', #{keyword}, '%')
                   OR description LIKE CONCAT('%', #{keyword}, '%')
                   OR product_no LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="status != null">
              AND status = #{status}
            </if>
            <if test="ownerUserId != null">
              AND owner_user_id = #{ownerUserId}
            </if>
            </script>
            """)
    long countProductsForAdmin(@Param("keyword") String keyword,
                               @Param("status") Integer status,
                               @Param("ownerUserId") Long ownerUserId);

    @Select("""
            <script>
            SELECT p.id,
                   p.product_no AS productNo,
                   p.owner_user_id AS ownerUserId,
                   p.title,
                   p.description,
                   NULL AS detailHtml,
                   p.cover_object_key AS coverObjectKey,
                   p.category_code AS categoryCode,
                   p.sub_category_code AS subCategoryCode,
                   p.condition_level AS conditionLevel,
                   p.trade_mode AS tradeMode,
                   p.campus_code AS campusCode,
                   p.status,
                   p.is_deleted AS isDeleted,
                   agg.min_price_cent AS minPriceCent,
                   agg.max_price_cent AS maxPriceCent,
                   agg.total_stock AS totalStock
            FROM p_product p
            LEFT JOIN (
                SELECT product_id,
                       MIN(price_cent) AS min_price_cent,
                       MAX(price_cent) AS max_price_cent,
                       SUM(stock) AS total_stock
                FROM p_sku
                WHERE is_deleted = 0
                GROUP BY product_id
            ) agg ON agg.product_id = p.id
            WHERE p.is_deleted = 0
            <if test="keyword != null and keyword != ''">
              AND (p.title LIKE CONCAT('%', #{keyword}, '%')
                   OR p.description LIKE CONCAT('%', #{keyword}, '%')
                   OR p.product_no LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="status != null">
              AND p.status = #{status}
            </if>
            <if test="ownerUserId != null">
              AND p.owner_user_id = #{ownerUserId}
            </if>
            <if test="categoryCode != null and categoryCode != ''">
              AND p.category_code = #{categoryCode}
            </if>
            <if test="subCategoryCode != null and subCategoryCode != ''">
              AND p.sub_category_code = #{subCategoryCode}
            </if>
            <if test="conditionLevel != null and conditionLevel != ''">
              AND p.condition_level = #{conditionLevel}
            </if>
            <if test="tradeMode != null and tradeMode != ''">
              AND p.trade_mode = #{tradeMode}
            </if>
            <if test="campusCode != null and campusCode != ''">
              AND p.campus_code = #{campusCode}
            </if>
            ORDER BY
            <choose>
              <when test="sortBy == 'MIN_PRICE'">IFNULL(agg.min_price_cent, 0)</when>
              <when test="sortBy == 'MAX_PRICE'">IFNULL(agg.max_price_cent, 0)</when>
              <otherwise>p.created_at</otherwise>
            </choose>
            <choose>
              <when test="sortDir == 'ASC'">ASC</when>
              <otherwise>DESC</otherwise>
            </choose>,
            p.id DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<ProductV2Record> listProductsForAdminV2(@Param("keyword") String keyword,
                                                 @Param("status") Integer status,
                                                 @Param("ownerUserId") Long ownerUserId,
                                                 @Param("categoryCode") String categoryCode,
                                                 @Param("subCategoryCode") String subCategoryCode,
                                                 @Param("conditionLevel") String conditionLevel,
                                                 @Param("tradeMode") String tradeMode,
                                                 @Param("campusCode") String campusCode,
                                                 @Param("sortBy") String sortBy,
                                                 @Param("sortDir") String sortDir,
                                                 @Param("size") int size,
                                                 @Param("offset") int offset);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM p_product p
            WHERE p.is_deleted = 0
            <if test="keyword != null and keyword != ''">
              AND (p.title LIKE CONCAT('%', #{keyword}, '%')
                   OR p.description LIKE CONCAT('%', #{keyword}, '%')
                   OR p.product_no LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="status != null">
              AND p.status = #{status}
            </if>
            <if test="ownerUserId != null">
              AND p.owner_user_id = #{ownerUserId}
            </if>
            <if test="categoryCode != null and categoryCode != ''">
              AND p.category_code = #{categoryCode}
            </if>
            <if test="subCategoryCode != null and subCategoryCode != ''">
              AND p.sub_category_code = #{subCategoryCode}
            </if>
            <if test="conditionLevel != null and conditionLevel != ''">
              AND p.condition_level = #{conditionLevel}
            </if>
            <if test="tradeMode != null and tradeMode != ''">
              AND p.trade_mode = #{tradeMode}
            </if>
            <if test="campusCode != null and campusCode != ''">
              AND p.campus_code = #{campusCode}
            </if>
            </script>
            """)
    long countProductsForAdminV2(@Param("keyword") String keyword,
                                 @Param("status") Integer status,
                                 @Param("ownerUserId") Long ownerUserId,
                                 @Param("categoryCode") String categoryCode,
                                 @Param("subCategoryCode") String subCategoryCode,
                                 @Param("conditionLevel") String conditionLevel,
                                 @Param("tradeMode") String tradeMode,
                                 @Param("campusCode") String campusCode);

    @Update("""
            UPDATE p_product
            SET status = #{status},
                updated_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE id = #{productId}
              AND is_deleted = 0
            """)
    int updateProductStatusById(@Param("productId") Long productId, @Param("status") Integer status);

    @Insert("""
            INSERT INTO p_sku (
                product_id,
                sku_no,
                display_name,
                spec_items_json,
                spec_signature,
                sku_name,
                spec_json,
                price_cent,
                stock,
                is_deleted,
                created_at,
                updated_at
            ) VALUES (
                #{productId},
                #{skuNo},
                #{displayName},
                #{specItemsJson},
                #{specSignature},
                #{skuName},
                #{specJson},
                #{priceCent},
                #{stock},
                0,
                CURRENT_TIMESTAMP(3),
                CURRENT_TIMESTAMP(3)
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertSku(SkuEntity sku);

    @Update("""
            UPDATE p_sku
            SET display_name = #{displayName},
                spec_items_json = #{specItemsJson},
                spec_signature = #{specSignature},
                sku_name = #{skuName},
                spec_json = #{specJson},
                price_cent = #{priceCent},
                stock = #{stock},
                updated_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE id = #{id}
              AND product_id = #{productId}
              AND is_deleted = 0
            """)
    int updateSku(SkuEntity sku);

    @Select("""
            SELECT id,
                   product_id AS productId,
                   sku_no AS skuNo,
                   display_name AS displayName,
                   spec_items_json AS specItemsJson,
                   spec_signature AS specSignature,
                   sku_name AS skuName,
                   spec_json AS specJson,
                   price_cent AS priceCent,
                   stock,
                   is_deleted AS isDeleted
            FROM p_sku
            WHERE product_id = #{productId}
              AND is_deleted = 0
            ORDER BY id ASC
            """)
    List<SkuRecord> listActiveSkusByProductId(@Param("productId") Long productId);

    @Select("""
            SELECT id,
                   product_id AS productId,
                   sku_no AS skuNo,
                   display_name AS displayName,
                   spec_items_json AS specItemsJson,
                   spec_signature AS specSignature,
                   sku_name AS skuName,
                   spec_json AS specJson,
                   price_cent AS priceCent,
                   stock,
                   is_deleted AS isDeleted
            FROM p_sku
            WHERE id = #{skuId}
              AND is_deleted = 0
            LIMIT 1
            """)
    SkuRecord findActiveSkuById(@Param("skuId") Long skuId);

    @Select("""
            SELECT id,
                   product_id AS productId,
                   sku_no AS skuNo,
                   display_name AS displayName,
                   spec_items_json AS specItemsJson,
                   spec_signature AS specSignature,
                   sku_name AS skuName,
                   spec_json AS specJson,
                   price_cent AS priceCent,
                   stock,
                   is_deleted AS isDeleted
            FROM p_sku
            WHERE id = #{skuId}
              AND is_deleted = 0
            LIMIT 1
            FOR UPDATE
            """)
    SkuRecord findActiveSkuByIdForUpdate(@Param("skuId") Long skuId);

    @Update("""
            UPDATE p_sku
            SET is_deleted = 1,
                updated_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE id = #{skuId}
              AND product_id = #{productId}
              AND is_deleted = 0
            """)
    int softDeleteSkuById(@Param("skuId") Long skuId, @Param("productId") Long productId);

    @Update("""
            UPDATE p_sku
            SET stock = stock - #{quantity},
                updated_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE id = #{skuId}
              AND is_deleted = 0
              AND stock >= #{quantity}
            """)
    int deductStockAtomic(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);

    @Update("""
            UPDATE p_sku
            SET stock = stock + #{quantity},
                updated_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE id = #{skuId}
              AND is_deleted = 0
            """)
    int increaseStockAtomic(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);

    @Select("""
            SELECT stock
            FROM p_sku
            WHERE id = #{skuId}
              AND is_deleted = 0
            LIMIT 1
            """)
    Integer findStockBySkuId(@Param("skuId") Long skuId);

    @Select("""
            SELECT id,
                   biz_no AS bizNo,
                   sku_id AS skuId,
                   op_type AS opType,
                   quantity,
                   success
            FROM p_stock_txn
            WHERE biz_no = #{bizNo}
              AND op_type = #{opType}
            LIMIT 1
            """)
    StockTxnRecord findStockTxnByBizNoAndType(@Param("bizNo") String bizNo, @Param("opType") String opType);

    @Insert("""
            INSERT INTO p_stock_txn (
                biz_no,
                sku_id,
                op_type,
                quantity,
                success,
                created_at
            ) VALUES (
                #{bizNo},
                #{skuId},
                #{opType},
                #{quantity},
                #{success},
                CURRENT_TIMESTAMP(3)
            )
            """)
    int insertStockTxn(@Param("bizNo") String bizNo,
                       @Param("skuId") Long skuId,
                       @Param("opType") String opType,
                       @Param("quantity") Integer quantity,
                       @Param("success") Integer success);

    @Update("""
            UPDATE p_stock_txn
            SET success = #{success}
            WHERE biz_no = #{bizNo}
              AND op_type = #{opType}
            """)
    int updateStockTxnSuccess(@Param("bizNo") String bizNo,
                              @Param("opType") String opType,
                              @Param("success") Integer success);

    @Insert("""
            INSERT INTO p_outbox_event (
                event_id,
                aggregate_id,
                type,
                payload,
                exchange_name,
                routing_key,
                status,
                retry_count,
                last_error,
                next_retry_at,
                created_at,
                sent_at
            ) VALUES (
                #{eventId},
                #{aggregateId},
                #{type},
                #{payload},
                #{exchangeName},
                #{routingKey},
                #{status},
                #{retryCount},
                #{lastError},
                #{nextRetryAt},
                CURRENT_TIMESTAMP(3),
                #{sentAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertProductOutboxEvent(ProductOutboxEventEntity outboxEventEntity);

    @Select("""
            SELECT id,
                   event_id AS eventId,
                   aggregate_id AS aggregateId,
                   type,
                   payload,
                   exchange_name AS exchangeName,
                   routing_key AS routingKey,
                   status,
                   retry_count AS retryCount,
                   last_error AS lastError,
                   next_retry_at AS nextRetryAt,
                   created_at AS createdAt,
                   sent_at AS sentAt
            FROM p_outbox_event
            WHERE status = 'PENDING'
               OR (status = 'FAILED'
                   AND (next_retry_at IS NULL OR next_retry_at <= CURRENT_TIMESTAMP(3)))
            ORDER BY id ASC
            LIMIT #{limit}
            """)
    List<ProductOutboxEventRecord> listProductOutboxRelayCandidates(@Param("limit") int limit);

    @Update("""
            UPDATE p_outbox_event
            SET status = 'SENT',
                sent_at = CURRENT_TIMESTAMP(3),
                last_error = NULL,
                next_retry_at = NULL
            WHERE id = #{id}
              AND status IN ('PENDING', 'FAILED')
            """)
    int markProductOutboxSent(@Param("id") Long id);

    @Update("""
            UPDATE p_outbox_event
            SET status = 'FAILED',
                retry_count = #{retryCount},
                last_error = #{lastError},
                next_retry_at = #{nextRetryAt}
            WHERE id = #{id}
              AND status IN ('PENDING', 'FAILED')
            """)
    int markProductOutboxFailed(@Param("id") Long id,
                                @Param("retryCount") int retryCount,
                                @Param("lastError") String lastError,
                                @Param("nextRetryAt") LocalDateTime nextRetryAt);

    @Insert("""
            INSERT INTO p_admin_audit_log (
                operator_user_id,
                target_product_id,
                action,
                before_json,
                after_json,
                reason,
                created_at
            ) VALUES (
                #{operatorUserId},
                #{targetProductId},
                #{action},
                #{beforeJson},
                #{afterJson},
                #{reason},
                CURRENT_TIMESTAMP(3)
            )
            """)
    int insertAdminAuditLog(@Param("operatorUserId") Long operatorUserId,
                            @Param("targetProductId") Long targetProductId,
                            @Param("action") String action,
                            @Param("beforeJson") String beforeJson,
                            @Param("afterJson") String afterJson,
                            @Param("reason") String reason);
}
