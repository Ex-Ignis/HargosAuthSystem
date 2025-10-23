package es.hargos.auth.repository;

import es.hargos.auth.entity.TenantWarehouseConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantWarehouseConfigRepository extends JpaRepository<TenantWarehouseConfigEntity, Long> {

    Optional<TenantWarehouseConfigEntity> findByTenantId(Long tenantId);
}
