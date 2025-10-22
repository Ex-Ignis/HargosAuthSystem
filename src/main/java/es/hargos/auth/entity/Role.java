package es.hargos.auth.entity;

public enum Role {
    SUPER_ADMIN,    // You - full access to everything
    TENANT_ADMIN,   // Can manage users within their tenant
    USER            // Regular user
}
