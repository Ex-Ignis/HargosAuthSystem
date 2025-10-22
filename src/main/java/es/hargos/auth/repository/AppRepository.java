package es.hargos.auth.repository;

import es.hargos.auth.entity.AppEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AppRepository extends JpaRepository<AppEntity, Long> {
    Optional<AppEntity> findByName(String name);
    boolean existsByName(String name);
}
