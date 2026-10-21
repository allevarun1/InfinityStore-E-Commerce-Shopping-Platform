package com.example.ecommerce.repository;

import com.example.ecommerce.model.Testimonial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestimonialRepository extends JpaRepository<Testimonial, Long> {
    List<Testimonial> findByActiveTrueOrderBySortOrderAscIdAsc();

    List<Testimonial> findAllByOrderBySortOrderAscIdAsc();
}
