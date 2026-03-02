package moe.hhm.shiori.product.repository;

import java.util.List;
import moe.hhm.shiori.product.model.ProductEntity;
import moe.hhm.shiori.product.model.ProductRecord;
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
                cover_object_key,
                status,
                is_deleted,
                created_at,
                updated_at
            ) VALUES (
                #{productNo},
                #{ownerUserId},
                #{title},
                #{description},
                #{coverObjectKey},
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
                cover_object_key = #{coverObjectKey},
                updated_at = CURRENT_TIMESTAMP(3),
                version = version + 1
            WHERE id = #{productId}
              AND is_deleted = 0
            """)
    int updateProductBase(@Param("productId") Long productId,
                          @Param("title") String title,
                          @Param("description") String description,
                          @Param("coverObjectKey") String coverObjectKey);

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
            SET sku_name = #{skuName},
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
}
