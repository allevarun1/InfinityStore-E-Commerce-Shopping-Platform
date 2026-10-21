package com.example.ecommerce.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.ecommerce.model.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    String PUBLIC = "p.deleted = false and (p.status is null or p.status = com.example.ecommerce.model.ProductStatus.PUBLISHED)";

    // ---- Admin: everything that is not soft-deleted, whatever its lifecycle state
    List<Product> findByDeletedFalse();

    Optional<Product> findByIdAndDeletedFalse(Long id);

    Optional<Product> findBySkuIgnoreCaseAndDeletedFalse(String sku);

    @Query("select p from Product p where p.styleGroup = :group and " + PUBLIC + " order by p.id")
    List<Product> findPublicByStyleGroup(@Param("group") String group);

    List<Product> findByStyleGroupAndDeletedFalse(String group);

    // ---- Shopper-facing: published only
    @Query("select p from Product p where " + PUBLIC)
    List<Product> findPublic();

    @Query("select p from Product p where p.id = :id and " + PUBLIC)
    Optional<Product> findPublicById(@Param("id") Long id);

    @Query("select p from Product p where p.category.id = :categoryId and " + PUBLIC)
    List<Product> findPublicByCategory(@Param("categoryId") Long categoryId);

    @Query("select p from Product p where lower(p.name) like lower(concat('%', :q, '%')) and " + PUBLIC)
    List<Product> searchPublic(@Param("q") String q);

    @Query("select p from Product p where p.deal = true and " + PUBLIC)
    List<Product> findPublicDeals();
}
