package es.hargos.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_tenant_roles", schema = "auth",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "tenant_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserTenantRoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private TenantEntity tenant;

    @Column(nullable = false, length = 50)
    private String role; // SUPER_ADMIN, TENANT_ADMIN, USER

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
