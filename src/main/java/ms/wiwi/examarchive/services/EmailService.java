package ms.wiwi.examarchive.services;

import jakarta.mail.*;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    private final Session session;
    private final String from;

    /**
     * Creates a new EmailService instance.
     * @param username Email address
     * @param password Email password
     * @param host SMTP host
     * @param port SMTP port
     * @param from From address
     */
    public EmailService(String username, String password, String host, String port, String from){
        Properties prop = new Properties();
        prop.put("mail.smtp.host", host);
        prop.put("mail.smtp.port", port);
        prop.put("mail.smtp.auth", "true");
        prop.put("mail.smtp.starttls.enable", "true");
        this.from = from;
        session = Session.getInstance(prop, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
    }

    /**
     * Tests the connection to the email server.
     * @return true if the connection was successful, false otherwise
     */
    public boolean testConnection(){
        try {
            Transport transport = session.getTransport();
            transport.connect();
            transport.close();
            return true;
        } catch (MessagingException e) {
            logger.error("Failed to connect to email server", e);
            return false;
        }
    }

    /**
     * Sends an email to a list of recipients. The email is sent asynchronously.
     * @param emails List of email addresses
     * @param subject Subject of the email
     * @param content Content of the email
     */
    public void sendEmails(List<String> emails, String subject, String content){
        CompletableFuture.runAsync(() -> {
            try {
                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(from));
                InternetAddress[] addresses = emails.stream().map(email -> {
                    try {
                        return new InternetAddress(email);
                    } catch (AddressException e) {
                        logger.error("Invalid email address: {}", email, e);
                        return null;
                    }
                }).filter(Objects::nonNull).toArray(InternetAddress[]::new);
                message.setRecipients(Message.RecipientType.TO, addresses);
                message.setSubject(subject);
                message.setText(content);
                message.setSentDate(new Date());
                Transport transport = session.getTransport();
                transport.connect();
                transport.sendMessage(message, message.getAllRecipients());
                transport.close();
                logger.info("Email sent to {} recipients", addresses.length);
            } catch (Exception e) {
                logger.error("Failed to send email", e);
            }
        });
    }
}
