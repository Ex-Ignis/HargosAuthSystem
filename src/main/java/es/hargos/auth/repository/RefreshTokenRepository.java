package es.hargos.auth.repository;

import es.hargos.auth.entity.RefreshTokenEntity;
import es.hargos.auth.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {
    Optional<RefreshTokenEntity> findByToken(String token);
    List<RefreshTokenEntity> findByUser(UserEntity user);
    void deleteByUser(UserEntity user);
    void deleteByExpiresAtBefore(LocalDateTime dateTime);
}
