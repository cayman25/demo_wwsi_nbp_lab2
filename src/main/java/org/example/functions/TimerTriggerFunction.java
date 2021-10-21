package org.example.functions;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.*;
import java.util.List;
import java.util.Properties;

import com.google.gson.Gson;
import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;

import javax.mail.*;
import javax.mail.internet.*;

/**
 * Azure Functions with Timer trigger.
 */
public class TimerTriggerFunction {
    /**
     * This function will be invoked periodically according to the specified schedule.
     */
    @FunctionName("TimerTrigger-Java")
    public void run(
        @TimerTrigger(name = "timerInfo", schedule = "0 * * * * *") String timerInfo,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java Timer trigger function executed at: " + LocalDateTime.now());
        context.getLogger().info(makeRequest());
    }

    private String makeRequest(){
        Gson gson = new Gson();

        final HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("http://api.nbp.pl/api/exchangerates/rates/a/eur/"))
                .setHeader("User-Agent", "Java 11 HttpClient Bot") // add request header
                .build();

        HttpResponse<String> response = null;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            return e.getMessage();
        } catch (InterruptedException e) {
            return e.getMessage();
        }

        Root root = gson.fromJson(response.body().toString(),Root.class);
        double mid = root.rates.get(0).mid;

        if(mid >= 4.56){
            try {
                sendEmail(getSession(), "Current euro exchange rate is " + mid);
                return "Send Email";
            }catch(Exception ex){
                return ex.toString();
            }
        }
        return "Euro price is not higher then 4.56";
    }

    private class Rate{
        public String no;
        public String effectiveDate;
        public double mid;
    }

    private class Root{
        public String table;
        public String currency;
        public String code;
        public List<Rate> rates;
    }

    private static Properties prepareSMTPProperties(){
        Properties prop = new Properties();
        prop.put("mail.smtp.auth", true);
        prop.put("mail.smtp.host", "smtp.gmail.com");
        prop.put("mail.smtp.port", "587");
        prop.put("mail.smtp.ssl.enable", false);
        prop.put("mail.smtp.starttls.enable", true);

        return prop;
    }

    private static Session getSession(){
        Session session = Session.getInstance(prepareSMTPProperties(), new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication("drobotm@legionista.com", System.getenv("SMTP_PASSWORD"));
            }
        });
        return session;
    }

    private void sendEmail(Session session, String msg) throws MessagingException {
        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress("drobotm@legionista.com"));
        message.setRecipients(
                Message.RecipientType.TO, InternetAddress.parse("drobotm@legionista.com"));
        message.setSubject("High exchange EURO rate");

        MimeBodyPart mimeBodyPart = new MimeBodyPart();
        mimeBodyPart.setContent(msg, "text/html");

        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(mimeBodyPart);

        message.setContent(multipart);

        Transport.send(message);
    }
}
