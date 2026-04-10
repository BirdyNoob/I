package com.icentric.Icentric.identity.repository;

import com.icentric.Icentric.identity.entity.GroupMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface GroupMembershipRepository extends JpaRepository<GroupMembership, UUID> {
    List<GroupMembership> findByGroupIdOrderByCreatedAtDesc(UUID groupId);

    boolean existsByGroupIdAndUserId(UUID groupId, UUID userId);

    long deleteByGroupIdAndUserId(UUID groupId, UUID userId);

    long countByGroupId(UUID groupId);

    @Query("select gm.groupId, count(gm.id) from GroupMembership gm where gm.groupId in :groupIds group by gm.groupId")
    List<Object[]> countByGroupIds(@Param("groupIds") Collection<UUID> groupIds);
}
