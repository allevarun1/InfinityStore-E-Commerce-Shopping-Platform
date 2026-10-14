package com.example.ecommerce.service;

import com.example.ecommerce.model.ERole;
import com.example.ecommerce.model.User;
import com.example.ecommerce.repository.CartItemRepository;
import com.example.ecommerce.repository.OrderRepository;
import com.example.ecommerce.repository.UserRepository;
import com.example.ecommerce.repository.WishlistItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AccountService {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private CartItemRepository cartItemRepository;
    @Autowired
    private WishlistItemRepository wishlistItemRepository;
    @Autowired
    private AuditService auditService;

    /**
     * Deletes an account that has no order history. Accounts with orders must be retained for
     * financial records until an anonymisation workflow exists.
     */
    public void deleteAccount(Long userId) {
        User target = userRepository.findById(userId).orElseThrow(() -> new BusinessException("User not found"));
        if (orderRepository.existsByUser(target)) {
            throw new BusinessException("This account has order history and cannot be deleted. Please contact support.");
        }
        boolean isAdmin = target.getRoles().stream().anyMatch(r -> r.getName() == ERole.ROLE_ADMIN);
        if (isAdmin && userRepository.countByRoles_Name(ERole.ROLE_ADMIN) <= 1) {
            throw new BusinessException("The last administrator cannot be deleted");
        }
        cartItemRepository.deleteByUser(target);
        wishlistItemRepository.deleteByUser(target);
        userRepository.delete(target);
        auditService.record("ACCOUNT_DELETED", "USER", userId, "username=" + target.getUsername());
    }
}
