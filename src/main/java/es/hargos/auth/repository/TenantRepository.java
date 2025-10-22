package es.hargos.auth.repository;

import es.hargos.auth.entity.AppEntity;
import es.hargos.auth.entity.TenantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<TenantEntity, Long> {
    List<TenantEntity> findByApp(AppEntity app);
    Optional<TenantEntity> findByAppAndName(AppEntity app, String name);
    boolean existsByAppAndName(AppEntity app, String name);
}
