package com.icentric.Icentric.platform.service;

import com.icentric.Icentric.platform.exception.TenantNotFoundException;
import com.icentric.Icentric.platform.tenant.repository.TenantRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PlatformUserService {

    private final TenantRepository tenantRepository;
    private final com.icentric.Icentric.identity.repository.UserRepository userRepository;
    private final com.icentric.Icentric.identity.repository.TenantUserRepository tenantUserRepository;

    public PlatformUserService(
            TenantRepository tenantRepository,
            com.icentric.Icentric.identity.repository.UserRepository userRepository,
            com.icentric.Icentric.identity.repository.TenantUserRepository tenantUserRepository
    ) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.tenantUserRepository = tenantUserRepository;
    }

    public List<Map<String, Object>> getTenantUsers(UUID tenantId) {

        if (!tenantRepository.existsById(tenantId)) {
            throw new TenantNotFoundException(tenantId);
        }

        List<com.icentric.Icentric.identity.entity.TenantUser> memberships = tenantUserRepository.findByTenantId(tenantId);
        List<UUID> userIds = memberships.stream().map(com.icentric.Icentric.identity.entity.TenantUser::getUserId).toList();

        if (userIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<com.icentric.Icentric.identity.entity.User> users = userRepository.findByIdIn(userIds);
        Map<UUID, com.icentric.Icentric.identity.entity.User> userMap = users.stream()
                .collect(java.util.stream.Collectors.toMap(com.icentric.Icentric.identity.entity.User::getId, u -> u));

        List<Map<String, Object>> result = new ArrayList<>();

        for (com.icentric.Icentric.identity.entity.TenantUser membership : memberships) {
            com.icentric.Icentric.identity.entity.User u = userMap.get(membership.getUserId());
            if (u != null) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", u.getId());
                map.put("name", u.getName());
                map.put("email", u.getEmail());
                map.put("role", membership.getRole());
                map.put("department", membership.getDepartment());
                map.put("isActive", u.getIsActive());
                result.add(map);
            }
        }

        return result;
    }
}
