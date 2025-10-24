package es.hargos.auth.repository;

import es.hargos.auth.entity.AccessCodeEntity;
import es.hargos.auth.entity.TenantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccessCodeRepository extends JpaRepository<AccessCodeEntity, Long> {
    Optional<AccessCodeEntity> findByCode(String code);
    List<AccessCodeEntity> findByTenant(TenantEntity tenant);
    List<AccessCodeEntity> findByTenantAndIsActive(TenantEntity tenant, Boolean isActive);
}
