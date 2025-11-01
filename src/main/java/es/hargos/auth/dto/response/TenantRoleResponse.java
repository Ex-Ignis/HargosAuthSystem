package es.hargos.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TenantRoleResponse {
    private Long tenantId;
    private String tenantName;
    private String appName;
    private String role;
}
