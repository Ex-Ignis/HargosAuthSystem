package es.hargos.auth.repository;

import es.hargos.auth.entity.InvitationEntity;
import es.hargos.auth.entity.TenantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvitationRepository extends JpaRepository<InvitationEntity, Long> {
    Optional<InvitationEntity> findByToken(String token);
    List<InvitationEntity> findByTenant(TenantEntity tenant);
    List<InvitationEntity> findByTenantAndAccepted(TenantEntity tenant, Boolean accepted);
    Optional<InvitationEntity> findByEmailAndTenantAndAccepted(String email, TenantEntity tenant, Boolean accepted);
    boolean existsByEmailAndTenantAndAccepted(String email, TenantEntity tenant, Boolean accepted);
}
