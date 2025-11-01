package es.hargos.auth.repository;

import es.hargos.auth.entity.TenantEntity;
import es.hargos.auth.entity.UserEntity;
import es.hargos.auth.entity.UserTenantRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserTenantRoleRepository extends JpaRepository<UserTenantRoleEntity, Long> {
    List<UserTenantRoleEntity> findByUser(UserEntity user);
    List<UserTenantRoleEntity> findByTenant(TenantEntity tenant);
    Optional<UserTenantRoleEntity> findByUserAndTenant(UserEntity user, TenantEntity tenant);

    @Query("SELECT utr FROM UserTenantRoleEntity utr " +
           "JOIN FETCH utr.tenant t " +
           "JOIN FETCH t.app " +
           "WHERE utr.user = :user")
    List<UserTenantRoleEntity> findByUserWithTenantAndApp(@Param("user") UserEntity user);

    @Query("SELECT COUNT(utr) FROM UserTenantRoleEntity utr WHERE utr.tenant = :tenant")
    long countByTenant(@Param("tenant") TenantEntity tenant);

    @Query("SELECT utr FROM UserTenantRoleEntity utr " +
           "WHERE utr.user = :user AND utr.role = :role")
    List<UserTenantRoleEntity> findByUserAndRole(@Param("user") UserEntity user, @Param("role") String role);

    void deleteByUserAndTenant(UserEntity user, TenantEntity tenant);
}
