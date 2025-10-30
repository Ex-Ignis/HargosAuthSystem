package es.hargos.auth.repository;

import es.hargos.auth.entity.AppEntity;
import es.hargos.auth.entity.OrganizationEntity;
import es.hargos.auth.entity.TenantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<TenantEntity, Long> {
    List<TenantEntity> findByApp(AppEntity app);
    List<TenantEntity> findByOrganization(OrganizationEntity organization);
    List<TenantEntity> findByAppAndOrganization(AppEntity app, OrganizationEntity organization);
    Optional<TenantEntity> findByAppAndName(AppEntity app, String name);
    Optional<TenantEntity> findByAppAndOrganizationAndName(AppEntity app, OrganizationEntity organization, String name);
    boolean existsByAppAndName(AppEntity app, String name);
    boolean existsByAppAndOrganizationAndName(AppEntity app, OrganizationEntity organization, String name);
}
