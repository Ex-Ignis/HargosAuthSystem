-- Hargos Auth Service Database Initialization Script
-- Database: hargos_auth_db
-- Schema: auth

-- ==============================================
-- SECTION 1: CREATE SCHEMA
-- ==============================================

CREATE SCHEMA IF NOT EXISTS auth;

SET search_path TO auth, public;

-- ==============================================
-- SECTION 2: USERS TABLE
-- ==============================================

CREATE TABLE IF NOT EXISTS auth.users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    email_verified BOOLEAN DEFAULT FALSE,
    email_verification_token VARCHAR(500),
    email_verification_expires_at TIMESTAMP,
    password_reset_token VARCHAR(500),
    password_reset_expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ==============================================
-- SECTION 3: REFRESH TOKENS TABLE
-- ==============================================

CREATE TABLE IF NOT EXISTS auth.refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    token VARCHAR(500) UNIQUE NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    revoked BOOLEAN DEFAULT FALSE,
    revoked_at TIMESTAMP
);

-- ==============================================
-- SECTION 4: APPS TABLE
-- ==============================================

CREATE TABLE IF NOT EXISTS auth.apps (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ==============================================
-- SECTION 5: ORGANIZATIONS TABLE (NUEVA)
-- ==============================================

CREATE TABLE IF NOT EXISTS auth.organizations (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE auth.organizations IS 'Client organizations/companies (e.g., Arendel, Entregalia)';
COMMENT ON COLUMN auth.organizations.name IS 'Unique name of the organization';

-- ==============================================
-- SECTION 6: TENANTS TABLE (ACTUALIZADA)
-- ==============================================

CREATE TABLE IF NOT EXISTS auth.tenants (
    id BIGSERIAL PRIMARY KEY,
    app_id BIGINT NOT NULL REFERENCES auth.apps(id) ON DELETE CASCADE,
    organization_id BIGINT NOT NULL REFERENCES auth.organizations(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    account_limit INTEGER NOT NULL DEFAULT 1,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(app_id, organization_id, name),
    CONSTRAINT chk_account_limit CHECK (account_limit >= 1)
);

COMMENT ON TABLE auth.tenants IS 'Service instances for organizations (e.g., RiTrack for Arendel)';
COMMENT ON COLUMN auth.tenants.account_limit IS 'Maximum number of user accounts allowed for this tenant (minimum 1, includes TENANT_ADMIN)';

-- ==============================================
-- SECTION 6A: TENANT RIDERS MANAGEMENT CONFIG
-- ==============================================

CREATE TABLE IF NOT EXISTS auth.tenant_riders_config (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL UNIQUE REFERENCES auth.tenants(id) ON DELETE CASCADE,
    rider_limit INTEGER,
    delivery_zones INTEGER,
    max_daily_deliveries INTEGER,
    real_time_tracking BOOLEAN DEFAULT TRUE,
    sms_notifications BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_rider_limit CHECK (rider_limit IS NULL OR rider_limit >= 0),
    CONSTRAINT chk_delivery_zones CHECK (delivery_zones IS NULL OR delivery_zones > 0),
    CONSTRAINT chk_max_daily_deliveries CHECK (max_daily_deliveries IS NULL OR max_daily_deliveries > 0)
);

COMMENT ON TABLE auth.tenant_riders_config IS 'Specific configuration for RiTrack app tenants';
COMMENT ON COLUMN auth.tenant_riders_config.rider_limit IS 'Maximum number of riders allowed (NULL = unlimited)';
COMMENT ON COLUMN auth.tenant_riders_config.delivery_zones IS 'Number of delivery zones configured';
COMMENT ON COLUMN auth.tenant_riders_config.max_daily_deliveries IS 'Maximum deliveries per day (NULL = unlimited)';

-- ==============================================
-- SECTION 6B: TENANT WAREHOUSE MANAGEMENT CONFIG
-- ==============================================

CREATE TABLE IF NOT EXISTS auth.tenant_warehouse_config (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL UNIQUE REFERENCES auth.tenants(id) ON DELETE CASCADE,
    warehouse_capacity_m3 DECIMAL(10,2),
    loading_docks INTEGER,
    inventory_sku_limit INTEGER,
    barcode_scanning BOOLEAN DEFAULT TRUE,
    rfid_enabled BOOLEAN DEFAULT FALSE,
    temperature_controlled_zones INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_warehouse_capacity CHECK (warehouse_capacity_m3 IS NULL OR warehouse_capacity_m3 > 0),
    CONSTRAINT chk_loading_docks CHECK (loading_docks IS NULL OR loading_docks > 0),
    CONSTRAINT chk_inventory_sku_limit CHECK (inventory_sku_limit IS NULL OR inventory_sku_limit > 0),
    CONSTRAINT chk_temperature_zones CHECK (temperature_controlled_zones IS NULL OR temperature_controlled_zones >= 0)
);

COMMENT ON TABLE auth.tenant_warehouse_config IS 'Specific configuration for Warehouse Management app tenants';
COMMENT ON COLUMN auth.tenant_warehouse_config.warehouse_capacity_m3 IS 'Total warehouse capacity in cubic meters';
COMMENT ON COLUMN auth.tenant_warehouse_config.loading_docks IS 'Number of loading docks available';
COMMENT ON COLUMN auth.tenant_warehouse_config.inventory_sku_limit IS 'Maximum number of SKUs that can be managed';
COMMENT ON COLUMN auth.tenant_warehouse_config.temperature_controlled_zones IS 'Number of temperature-controlled storage zones';

-- ==============================================
-- SECTION 6C: TENANT FLEET MANAGEMENT CONFIG
-- ==============================================

CREATE TABLE IF NOT EXISTS auth.tenant_fleet_config (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL UNIQUE REFERENCES auth.tenants(id) ON DELETE CASCADE,
    vehicle_limit INTEGER,
    gps_tracking BOOLEAN DEFAULT TRUE,
    maintenance_alerts BOOLEAN DEFAULT TRUE,
    fuel_monitoring BOOLEAN DEFAULT FALSE,
    driver_scoring BOOLEAN DEFAULT FALSE,
    telematics_enabled BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_vehicle_limit CHECK (vehicle_limit IS NULL OR vehicle_limit > 0)
);

COMMENT ON TABLE auth.tenant_fleet_config IS 'Specific configuration for Fleet Management app tenants';
COMMENT ON COLUMN auth.tenant_fleet_config.vehicle_limit IS 'Maximum number of vehicles that can be managed (NULL = unlimited)';
COMMENT ON COLUMN auth.tenant_fleet_config.gps_tracking IS 'Enable GPS tracking for vehicles';
COMMENT ON COLUMN auth.tenant_fleet_config.maintenance_alerts IS 'Enable maintenance alerts and scheduling';
COMMENT ON COLUMN auth.tenant_fleet_config.fuel_monitoring IS 'Enable fuel consumption monitoring';
COMMENT ON COLUMN auth.tenant_fleet_config.driver_scoring IS 'Enable driver performance scoring system';

-- ==============================================
-- SECTION 7: USER_TENANT_ROLES TABLE
-- ==============================================

CREATE TABLE IF NOT EXISTS auth.user_tenant_roles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    tenant_id BIGINT NOT NULL REFERENCES auth.tenants(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, tenant_id),
    CONSTRAINT chk_role CHECK (role IN ('SUPER_ADMIN', 'TENANT_ADMIN', 'USER'))
);

COMMENT ON TABLE auth.user_tenant_roles IS 'Many-to-many relationship between users and tenants with roles';
COMMENT ON COLUMN auth.user_tenant_roles.role IS 'Role of the user in this specific tenant: SUPER_ADMIN, TENANT_ADMIN, or USER';

-- ==============================================
-- SECTION 7A: INVITATIONS TABLE
-- ==============================================

CREATE TABLE IF NOT EXISTS auth.invitations (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES auth.tenants(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL,
    token VARCHAR(500) NOT NULL UNIQUE,
    role VARCHAR(50) NOT NULL,
    invited_by_user_id BIGINT REFERENCES auth.users(id) ON DELETE SET NULL,
    expires_at TIMESTAMP NOT NULL,
    accepted BOOLEAN DEFAULT FALSE NOT NULL,
    accepted_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_invitation_role CHECK (role IN ('TENANT_ADMIN', 'USER'))
);

COMMENT ON TABLE auth.invitations IS 'Email invitations for users to join tenants';
COMMENT ON COLUMN auth.invitations.token IS 'Unique token for accepting the invitation';
COMMENT ON COLUMN auth.invitations.expires_at IS 'Invitation expiration date (typically 7 days)';

-- ==============================================
-- SECTION 7B: ACCESS CODES TABLE
-- ==============================================

CREATE TABLE IF NOT EXISTS auth.access_codes (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES auth.tenants(id) ON DELETE CASCADE,
    code VARCHAR(50) NOT NULL UNIQUE,
    role VARCHAR(50) NOT NULL,
    created_by_user_id BIGINT REFERENCES auth.users(id) ON DELETE SET NULL,
    max_uses INTEGER,
    current_uses INTEGER DEFAULT 0 NOT NULL,
    expires_at TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_access_code_role CHECK (role IN ('TENANT_ADMIN', 'USER')),
    CONSTRAINT chk_max_uses CHECK (max_uses IS NULL OR max_uses > 0),
    CONSTRAINT chk_current_uses CHECK (current_uses >= 0)
);

COMMENT ON TABLE auth.access_codes IS 'Access codes for users to self-register to tenants';
COMMENT ON COLUMN auth.access_codes.code IS 'Unique code that users can use to join (e.g., ARENDEL-2025-X7K9)';
COMMENT ON COLUMN auth.access_codes.max_uses IS 'Maximum number of times this code can be used (NULL = unlimited)';
COMMENT ON COLUMN auth.access_codes.current_uses IS 'Number of times this code has been used';

-- ==============================================
-- SECTION 7C: STRIPE SUBSCRIPTIONS TABLE
-- ==============================================

CREATE TABLE IF NOT EXISTS auth.stripe_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL UNIQUE REFERENCES auth.tenants(id) ON DELETE CASCADE,
    organization_id BIGINT NOT NULL REFERENCES auth.organizations(id) ON DELETE CASCADE,

    -- Stripe IDs
    stripe_customer_id VARCHAR(255) NOT NULL,
    stripe_subscription_id VARCHAR(255) NOT NULL UNIQUE,
    stripe_price_id VARCHAR(255) NOT NULL,
    stripe_product_id VARCHAR(255),

    -- Subscription details
    status VARCHAR(50) NOT NULL,
    current_period_start TIMESTAMP,  -- Nullable: May be null initially during subscription creation
    current_period_end TIMESTAMP,    -- Nullable: May be null initially during subscription creation
    cancel_at_period_end BOOLEAN DEFAULT FALSE NOT NULL,
    canceled_at TIMESTAMP,

    -- Billing information
    billing_cycle_anchor TIMESTAMP,
    trial_start TIMESTAMP,
    trial_end TIMESTAMP,

    -- Rollback support for downgrades
    previous_account_quantity INTEGER,
    previous_rider_quantity INTEGER,

    -- Metadata
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_subscription_status CHECK (status IN (
        'active',           -- Subscription is active and paid
        'trialing',         -- In trial period
        'past_due',         -- Payment failed, retrying
        'canceled',         -- Canceled (ends at period_end)
        'unpaid',           -- Payment failed, no more retries
        'incomplete',       -- Initial payment not completed
        'incomplete_expired', -- Initial payment timed out
        'paused'            -- Subscription paused
    ))
);

COMMENT ON TABLE auth.stripe_subscriptions IS 'Stripe subscription information for tenant billing';
COMMENT ON COLUMN auth.stripe_subscriptions.stripe_customer_id IS 'Stripe Customer ID (starts with cus_)';
COMMENT ON COLUMN auth.stripe_subscriptions.stripe_subscription_id IS 'Stripe Subscription ID (starts with sub_)';
COMMENT ON COLUMN auth.stripe_subscriptions.stripe_price_id IS 'Stripe Price ID (starts with price_) - determines account_limit';
COMMENT ON COLUMN auth.stripe_subscriptions.status IS 'Stripe subscription status: active, trialing, past_due, canceled, unpaid, etc.';
COMMENT ON COLUMN auth.stripe_subscriptions.current_period_end IS 'When the current billing period ends (renew or cancel)';
COMMENT ON COLUMN auth.stripe_subscriptions.cancel_at_period_end IS 'True if subscription will cancel at period end';

-- ==============================================
-- SECTION 7D: STRIPE PAYMENT HISTORY TABLE
-- ==============================================

CREATE TABLE IF NOT EXISTS auth.stripe_payment_history (
    id BIGSERIAL PRIMARY KEY,
    subscription_id BIGINT NOT NULL REFERENCES auth.stripe_subscriptions(id) ON DELETE CASCADE,

    -- Stripe IDs
    stripe_invoice_id VARCHAR(255) NOT NULL UNIQUE,
    stripe_payment_intent_id VARCHAR(255),
    stripe_charge_id VARCHAR(255),

    -- Payment details
    amount_cents INTEGER NOT NULL,
    currency VARCHAR(3) DEFAULT 'EUR' NOT NULL,
    status VARCHAR(50) NOT NULL,

    -- Invoice details
    invoice_pdf_url VARCHAR(500),
    hosted_invoice_url VARCHAR(500),

    -- Timestamps
    attempted_at TIMESTAMP,
    paid_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_amount_cents CHECK (amount_cents >= 0),
    CONSTRAINT chk_payment_status CHECK (status IN (
        'draft',
        'open',
        'paid',
        'void',
        'uncollectible'
    ))
);

COMMENT ON TABLE auth.stripe_payment_history IS 'Payment history for Stripe subscriptions';
COMMENT ON COLUMN auth.stripe_payment_history.stripe_invoice_id IS 'Stripe Invoice ID (starts with in_)';
COMMENT ON COLUMN auth.stripe_payment_history.amount_cents IS 'Amount in cents (e.g., 2999 = €29.99)';
COMMENT ON COLUMN auth.stripe_payment_history.status IS 'Invoice status: draft, open, paid, void, uncollectible';

-- ==============================================
-- SECTION 7.5: USER SESSIONS TABLE
-- ==============================================

CREATE TABLE IF NOT EXISTS auth.user_sessions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    refresh_token_id BIGINT NOT NULL REFERENCES auth.refresh_tokens(id) ON DELETE CASCADE,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    device_type VARCHAR(20),
    access_token_jti VARCHAR(100),
    last_activity_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_revoked BOOLEAN DEFAULT FALSE NOT NULL,
    CONSTRAINT chk_device_type CHECK (device_type IN ('web', 'mobile', 'desktop', 'unknown'))
);

COMMENT ON TABLE auth.user_sessions IS 'Active user sessions for concurrent login control (max 2 per user)';
COMMENT ON COLUMN auth.user_sessions.ip_address IS 'IP address of the client';
COMMENT ON COLUMN auth.user_sessions.user_agent IS 'User agent string (browser, OS info)';
COMMENT ON COLUMN auth.user_sessions.device_type IS 'Detected device type: web, mobile, desktop, unknown';
COMMENT ON COLUMN auth.user_sessions.access_token_jti IS 'JWT ID (jti claim) of the current access token. Allows immediate revocation on logout.';
COMMENT ON COLUMN auth.user_sessions.last_activity_at IS 'Last time this session made a request (for idle detection)';
COMMENT ON COLUMN auth.user_sessions.is_revoked IS 'True if session was manually revoked by user';

-- ==============================================
-- SECTION 7.6: LIMIT EXCEEDED NOTIFICATIONS TABLE
-- ==============================================

CREATE TABLE IF NOT EXISTS auth.limit_exceeded_notifications (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES auth.tenants(id) ON DELETE CASCADE,
    current_count INTEGER NOT NULL,
    allowed_limit INTEGER NOT NULL,
    excess_count INTEGER NOT NULL,
    detected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_acknowledged BOOLEAN DEFAULT FALSE NOT NULL,
    acknowledged_at TIMESTAMP,
    acknowledged_by_user_id BIGINT REFERENCES auth.users(id) ON DELETE SET NULL,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_limit_counts CHECK (current_count >= 0 AND allowed_limit >= 0 AND excess_count >= 0)
);

COMMENT ON TABLE auth.limit_exceeded_notifications IS 'Notifications when tenants exceed their rider limits (reported by RiTrack)';
COMMENT ON COLUMN auth.limit_exceeded_notifications.current_count IS 'Number of riders the tenant currently has';
COMMENT ON COLUMN auth.limit_exceeded_notifications.allowed_limit IS 'Maximum riders allowed by subscription';
COMMENT ON COLUMN auth.limit_exceeded_notifications.excess_count IS 'Number of riders exceeding the limit';

-- ==============================================
-- SECTION 8: INDEXES FOR PERFORMANCE
-- ==============================================

CREATE INDEX IF NOT EXISTS idx_users_email ON auth.users(email);
CREATE INDEX IF NOT EXISTS idx_users_is_active ON auth.users(is_active);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON auth.refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token ON auth.refresh_tokens(token);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at ON auth.refresh_tokens(expires_at);
CREATE INDEX IF NOT EXISTS idx_tenants_app_id ON auth.tenants(app_id);
CREATE INDEX IF NOT EXISTS idx_tenants_organization_id ON auth.tenants(organization_id);
CREATE INDEX IF NOT EXISTS idx_user_tenant_roles_user_id ON auth.user_tenant_roles(user_id);
CREATE INDEX IF NOT EXISTS idx_user_tenant_roles_tenant_id ON auth.user_tenant_roles(tenant_id);
CREATE INDEX IF NOT EXISTS idx_user_tenant_roles_role ON auth.user_tenant_roles(role);
CREATE INDEX IF NOT EXISTS idx_tenant_riders_config_tenant_id ON auth.tenant_riders_config(tenant_id);
CREATE INDEX IF NOT EXISTS idx_tenant_warehouse_config_tenant_id ON auth.tenant_warehouse_config(tenant_id);
CREATE INDEX IF NOT EXISTS idx_tenant_fleet_config_tenant_id ON auth.tenant_fleet_config(tenant_id);
CREATE INDEX IF NOT EXISTS idx_invitations_token ON auth.invitations(token);
CREATE INDEX IF NOT EXISTS idx_invitations_email ON auth.invitations(email);
CREATE INDEX IF NOT EXISTS idx_invitations_tenant_id ON auth.invitations(tenant_id);
CREATE INDEX IF NOT EXISTS idx_invitations_accepted ON auth.invitations(accepted);
CREATE INDEX IF NOT EXISTS idx_access_codes_code ON auth.access_codes(code);
CREATE INDEX IF NOT EXISTS idx_access_codes_tenant_id ON auth.access_codes(tenant_id);
CREATE INDEX IF NOT EXISTS idx_access_codes_is_active ON auth.access_codes(is_active);
CREATE INDEX IF NOT EXISTS idx_user_sessions_user_id ON auth.user_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_user_sessions_refresh_token_id ON auth.user_sessions(refresh_token_id);
CREATE INDEX IF NOT EXISTS idx_user_sessions_last_activity ON auth.user_sessions(last_activity_at);
CREATE INDEX IF NOT EXISTS idx_user_sessions_is_revoked ON auth.user_sessions(is_revoked);
CREATE INDEX IF NOT EXISTS idx_user_sessions_jti ON auth.user_sessions(access_token_jti);
CREATE INDEX IF NOT EXISTS idx_stripe_subscriptions_tenant_id ON auth.stripe_subscriptions(tenant_id);
CREATE INDEX IF NOT EXISTS idx_stripe_subscriptions_organization_id ON auth.stripe_subscriptions(organization_id);
CREATE INDEX IF NOT EXISTS idx_stripe_subscriptions_customer_id ON auth.stripe_subscriptions(stripe_customer_id);
CREATE INDEX IF NOT EXISTS idx_stripe_subscriptions_subscription_id ON auth.stripe_subscriptions(stripe_subscription_id);
CREATE INDEX IF NOT EXISTS idx_stripe_subscriptions_status ON auth.stripe_subscriptions(status);
CREATE INDEX IF NOT EXISTS idx_stripe_payment_history_subscription_id ON auth.stripe_payment_history(subscription_id);
CREATE INDEX IF NOT EXISTS idx_stripe_payment_history_invoice_id ON auth.stripe_payment_history(stripe_invoice_id);
CREATE INDEX IF NOT EXISTS idx_stripe_payment_history_status ON auth.stripe_payment_history(status);

-- Limit Exceeded Notifications indexes
CREATE INDEX IF NOT EXISTS idx_limit_exceeded_notifications_tenant_id ON auth.limit_exceeded_notifications(tenant_id);
CREATE INDEX IF NOT EXISTS idx_limit_exceeded_notifications_is_acknowledged ON auth.limit_exceeded_notifications(is_acknowledged);
CREATE INDEX IF NOT EXISTS idx_limit_exceeded_notifications_detected_at ON auth.limit_exceeded_notifications(detected_at DESC);

-- ==============================================
-- SECTION 9: SEED DATA
-- ==============================================

-- Insert default app
INSERT INTO auth.apps (name, description, is_active)
VALUES
    ('SYSTEM', 'Sistema Interno Hargos - Administración Global', true)
ON CONFLICT (name) DO NOTHING;

-- Insert default organization
INSERT INTO auth.organizations (name, description, is_active)
VALUES
    ('HARGOS_SYSTEM', 'Sistema Interno Hargos - Organización Global', true)
ON CONFLICT (name) DO NOTHING;

-- Insert default tenant GLOBAL
INSERT INTO auth.tenants (app_id, organization_id, name, description, account_limit, is_active)
VALUES
    (
        (SELECT id FROM auth.apps WHERE name = 'SYSTEM'),
        (SELECT id FROM auth.organizations WHERE name = 'HARGOS_SYSTEM'),
        'GLOBAL',
        'Tenant global para superadministradores del sistema',
        999999,
        true
    )
ON CONFLICT (app_id, organization_id, name) DO NOTHING;

-- ==============================================
-- INSERT USERS
-- ==============================================

-- SUPER_ADMIN user
-- Password: SuperAdmin123!
-- Hash generado con BCryptPasswordEncoder
INSERT INTO auth.users (email, password_hash, full_name, is_active, email_verified)
VALUES
    ('admin@hargos.es', '$2a$10$5kCXZvqF7YGJ5w.mJNNKKeKJ7x8xB0yOXYN9F2bXx8.8ZN9xC8XYK', 'Hargos Super Admin', true, true)
ON CONFLICT (email) DO NOTHING;

-- ==============================================
-- ASSIGN ROLES TO USERS
-- ==============================================

-- SUPER_ADMIN role (admin@hargos.es) - asignar al tenant GLOBAL
INSERT INTO auth.user_tenant_roles (user_id, tenant_id, role)
SELECT
    (SELECT id FROM auth.users WHERE email = 'admin@hargos.es'),
    (SELECT id FROM auth.tenants WHERE name = 'GLOBAL'),
    'SUPER_ADMIN'
ON CONFLICT (user_id, tenant_id) DO NOTHING;

-- ==============================================
-- SECTION 10: FUNCTIONS FOR TIMESTAMP UPDATES
-- ==============================================

CREATE OR REPLACE FUNCTION auth.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create triggers for updated_at
DROP TRIGGER IF EXISTS update_users_updated_at ON auth.users;
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON auth.users
    FOR EACH ROW EXECUTE FUNCTION auth.update_updated_at_column();

DROP TRIGGER IF EXISTS update_apps_updated_at ON auth.apps;
CREATE TRIGGER update_apps_updated_at BEFORE UPDATE ON auth.apps
    FOR EACH ROW EXECUTE FUNCTION auth.update_updated_at_column();

DROP TRIGGER IF EXISTS update_organizations_updated_at ON auth.organizations;
CREATE TRIGGER update_organizations_updated_at BEFORE UPDATE ON auth.organizations
    FOR EACH ROW EXECUTE FUNCTION auth.update_updated_at_column();

DROP TRIGGER IF EXISTS update_tenants_updated_at ON auth.tenants;
CREATE TRIGGER update_tenants_updated_at BEFORE UPDATE ON auth.tenants
    FOR EACH ROW EXECUTE FUNCTION auth.update_updated_at_column();

DROP TRIGGER IF EXISTS update_user_tenant_roles_updated_at ON auth.user_tenant_roles;
CREATE TRIGGER update_user_tenant_roles_updated_at BEFORE UPDATE ON auth.user_tenant_roles
    FOR EACH ROW EXECUTE FUNCTION auth.update_updated_at_column();

DROP TRIGGER IF EXISTS update_tenant_riders_config_updated_at ON auth.tenant_riders_config;
CREATE TRIGGER update_tenant_riders_config_updated_at BEFORE UPDATE ON auth.tenant_riders_config
    FOR EACH ROW EXECUTE FUNCTION auth.update_updated_at_column();

DROP TRIGGER IF EXISTS update_tenant_warehouse_config_updated_at ON auth.tenant_warehouse_config;
CREATE TRIGGER update_tenant_warehouse_config_updated_at BEFORE UPDATE ON auth.tenant_warehouse_config
    FOR EACH ROW EXECUTE FUNCTION auth.update_updated_at_column();

DROP TRIGGER IF EXISTS update_tenant_fleet_config_updated_at ON auth.tenant_fleet_config;
CREATE TRIGGER update_tenant_fleet_config_updated_at BEFORE UPDATE ON auth.tenant_fleet_config
    FOR EACH ROW EXECUTE FUNCTION auth.update_updated_at_column();

DROP TRIGGER IF EXISTS update_stripe_subscriptions_updated_at ON auth.stripe_subscriptions;
CREATE TRIGGER update_stripe_subscriptions_updated_at BEFORE UPDATE ON auth.stripe_subscriptions
    FOR EACH ROW EXECUTE FUNCTION auth.update_updated_at_column();
