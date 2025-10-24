package es.hargos.auth.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {

    private final Resend resend;

    @Value("${resend.from-email:noreply@hargos.es}")
    private String fromEmail;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    public EmailService(@Value("${resend.api-key}") String apiKey) {
        this.resend = new Resend(apiKey);
    }

    /**
     * Envía email de recuperación de contraseña de forma asíncrona
     * No bloquea el thread principal - se ejecuta en un thread separado
     */
    @Async
    public void sendPasswordResetEmail(String toEmail, String userName, String token) {
        String resetLink = frontendUrl + "/reset-password?token=" + token;

        String htmlContent = buildPasswordResetEmailHtml(userName, resetLink);

        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(fromEmail)
                .to(toEmail)
                .subject("Recuperación de contraseña - Hargos")
                .html(htmlContent)
                .build();

        try {
            CreateEmailResponse data = resend.emails().send(params);
            log.info("Password reset email sent successfully to {}. Email ID: {}", toEmail, data.getId());
        } catch (ResendException e) {
            log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage(), e);
            throw new RuntimeException("Error al enviar el email de recuperación de contraseña", e);
        }
    }

    /**
     * Envia email de invitación a un usuario para unirse a un tenant de forma asíncrona
     * No bloquea el thread principal - se ejecuta en un thread separado
     */
    @Async
    public void sendInvitationEmail(String toEmail, String tenantName, String token) {
        String invitationLink = frontendUrl + "/accept-invitation?token=" + token;

        String htmlContent = buildInvitationEmailHtml(tenantName, invitationLink);

        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(fromEmail)
                .to(toEmail)
                .subject("Invitación a unirse a " + tenantName)
                .html(htmlContent)
                .build();

        try {
            CreateEmailResponse data = resend.emails().send(params);
            log.info("Invitation email sent successfully to {}. Email ID: {}", toEmail, data.getId());
        } catch (ResendException e) {
            log.error("Failed to send invitation email to {}: {}", toEmail, e.getMessage(), e);
            throw new RuntimeException("Error al enviar el email de invitación", e);
        }
    }

    /**
     * Construye el HTML del email de invitación.
     */
    private String buildInvitationEmailHtml(String tenantName, String invitationLink) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px;">
                <div style="background-color: #f4f4f4; border-radius: 10px; padding: 30px;">
                    <h1 style="color: #2c3e50; margin-bottom: 20px;">Invitación a %s</h1>

                    <p style="font-size: 16px; margin-bottom: 20px;">
                        Has sido invitado a unirte al equipo de <strong>%s</strong> en la plataforma Hargos.
                    </p>

                    <p style="font-size: 16px; margin-bottom: 30px;">
                        Para aceptar la invitación y crear tu cuenta, haz clic en el siguiente botón:
                    </p>

                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s"
                           style="background-color: #3498db; color: white; padding: 15px 30px; text-decoration: none; border-radius: 5px; font-size: 16px; font-weight: bold; display: inline-block;">
                            Aceptar Invitación
                        </a>
                    </div>

                    <p style="font-size: 14px; color: #7f8c8d; margin-top: 30px;">
                        O copia y pega este enlace en tu navegador:<br>
                        <a href="%s" style="color: #3498db; word-break: break-all;">%s</a>
                    </p>

                    <p style="font-size: 14px; color: #7f8c8d; margin-top: 30px;">
                        Esta invitación expirará en 7 días.
                    </p>

                    <hr style="border: none; border-top: 1px solid #ddd; margin: 30px 0;">

                    <p style="font-size: 12px; color: #95a5a6; text-align: center;">
                        Este es un email automático, por favor no respondas a este mensaje.<br>
                        © %d Hargos. Todos los derechos reservados.
                    </p>
                </div>
            </body>
            </html>
            """.formatted(
                tenantName,
                tenantName,
                invitationLink,
                invitationLink,
                invitationLink,
                java.time.Year.now().getValue()
        );
    }

    /**
     * Construye el HTML del email de recuperación de contraseña.
     */
    private String buildPasswordResetEmailHtml(String userName, String resetLink) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px;">
                <div style="background-color: #f4f4f4; border-radius: 10px; padding: 30px;">
                    <h1 style="color: #2c3e50; margin-bottom: 20px;">Recuperación de Contraseña</h1>

                    <p style="font-size: 16px; margin-bottom: 20px;">
                        Hola <strong>%s</strong>,
                    </p>

                    <p style="font-size: 16px; margin-bottom: 20px;">
                        Hemos recibido una solicitud para restablecer la contraseña de tu cuenta en Hargos.
                    </p>

                    <p style="font-size: 16px; margin-bottom: 30px;">
                        Si realizaste esta solicitud, haz clic en el siguiente botón para crear una nueva contraseña:
                    </p>

                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s"
                           style="background-color: #e74c3c; color: white; padding: 15px 30px; text-decoration: none; border-radius: 5px; font-size: 16px; font-weight: bold; display: inline-block;">
                            Restablecer Contraseña
                        </a>
                    </div>

                    <p style="font-size: 14px; color: #7f8c8d; margin-top: 30px;">
                        O copia y pega este enlace en tu navegador:<br>
                        <a href="%s" style="color: #e74c3c; word-break: break-all;">%s</a>
                    </p>

                    <p style="font-size: 14px; color: #7f8c8d; margin-top: 30px;">
                        Este enlace expirará en <strong>1 hora</strong> por seguridad.
                    </p>

                    <div style="background-color: #fff3cd; border-left: 4px solid #ffc107; padding: 15px; margin: 30px 0; border-radius: 5px;">
                        <p style="margin: 0; font-size: 14px; color: #856404;">
                            ⚠️ <strong>Importante:</strong> Si no solicitaste este cambio, ignora este mensaje y tu contraseña permanecerá sin cambios.
                            Te recomendamos cambiar tu contraseña si crees que alguien más tiene acceso a tu cuenta.
                        </p>
                    </div>

                    <hr style="border: none; border-top: 1px solid #ddd; margin: 30px 0;">

                    <p style="font-size: 12px; color: #95a5a6; text-align: center;">
                        Este es un email automático, por favor no respondas a este mensaje.<br>
                        © %d Hargos. Todos los derechos reservados.
                    </p>
                </div>
            </body>
            </html>
            """.formatted(
                userName,
                resetLink,
                resetLink,
                resetLink,
                java.time.Year.now().getValue()
        );
    }
}
