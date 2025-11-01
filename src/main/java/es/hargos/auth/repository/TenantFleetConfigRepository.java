package es.hargos.auth.repository;

import es.hargos.auth.entity.TenantFleetConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantFleetConfigRepository extends JpaRepository<TenantFleetConfigEntity, Long> {

    Optional<TenantFleetConfigEntity> findByTenantId(Long tenantId);
}
