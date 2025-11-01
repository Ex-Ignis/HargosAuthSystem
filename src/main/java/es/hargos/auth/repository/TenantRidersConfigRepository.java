package es.hargos.auth.repository;

import es.hargos.auth.entity.TenantRidersConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantRidersConfigRepository extends JpaRepository<TenantRidersConfigEntity, Long> {

    Optional<TenantRidersConfigEntity> findByTenantId(Long tenantId);
}
