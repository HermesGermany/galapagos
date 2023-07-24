package com.hermesworld.ais.galapagos.notifications.impl;

import com.hermesworld.ais.galapagos.applications.ApplicationOwnerRequest;
import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.applications.RequestState;
import com.hermesworld.ais.galapagos.notifications.NotificationParams;
import com.hermesworld.ais.galapagos.notifications.NotificationService;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionMetadata;
import com.hermesworld.ais.galapagos.subscriptions.service.SubscriptionService;
import com.hermesworld.ais.galapagos.topics.service.TopicService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final SubscriptionService subscriptionService;

    private final ApplicationsService applicationsService;

    private final TopicService topicService;

    private final JavaMailSender mailSender;

    private final TaskExecutor taskExecutor;

    private final ITemplateEngine templateEngine;

    private final InternetAddress fromAddress;

    private final List<InternetAddress> adminMailRecipients;

    public NotificationServiceImpl(SubscriptionService subscriptionService, ApplicationsService applicationsService,
            TopicService topicService, JavaMailSender mailSender, TaskExecutor taskExecutor,
            @Qualifier("emailTemplateEngine") ITemplateEngine templateEngine,
            @Value("${galapagos.mail.sender:Galapagos <me@privacy.net>}") String fromAddress,
            @Value("${galapagos.mail.admin-recipients:}") String adminMailRecipients) {
        this.subscriptionService = subscriptionService;
        this.applicationsService = applicationsService;
        this.topicService = topicService;
        this.mailSender = mailSender;
        this.taskExecutor = taskExecutor;
        this.templateEngine = templateEngine;
        try {
            this.fromAddress = InternetAddress.parse(fromAddress)[0];
        }
        catch (AddressException e) {
            throw new RuntimeException("Invalid e-mail address specified as Galapagos FROM address", e);
        }
        try {
            this.adminMailRecipients = toRecipientsList(adminMailRecipients);
        }
        catch (AddressException e) {
            throw new RuntimeException("Invalid e-mail address(es) specified as Galapagos admin e-mail recipients", e);
        }
    }

    @Override
    public CompletableFuture<Void> notifySubscribers(String environmentId, String topicName,
            NotificationParams notificationParams, String excludeUser) {
        List<SubscriptionMetadata> subscriptions = subscriptionService.getSubscriptionsForTopic(environmentId,
                topicName, false);

        Set<String> applicationIds = subscriptions.stream().map(SubscriptionMetadata::getClientApplicationId)
                .collect(Collectors.toSet());

        Collection<ApplicationOwnerRequest> requests = applicationsService.getAllApplicationOwnerRequests();

        final String finalExcludeUser = excludeUser == null ? "" : excludeUser;

        Set<String> recipients = new HashSet<>();
        for (String applicationId : applicationIds) {
            recipients.addAll(requests.stream()
                    .filter(req -> req.getState().equals(RequestState.APPROVED)
                            && applicationId.equals(req.getApplicationId())
                            && !finalExcludeUser.equals(req.getUserName()))
                    .map(ApplicationOwnerRequest::getNotificationEmailAddress).filter(s -> !StringUtils.isEmpty(s))
                    .collect(Collectors.toSet()));
        }

        return doSendAsync(notificationParams, safeToRecipientsList(recipients, true), true);
    }

    @Override
    public CompletableFuture<Void> notifyRequestor(ApplicationOwnerRequest request,
            NotificationParams notificationParams) {
        if (StringUtils.isEmpty(request.getNotificationEmailAddress())) {
            log.warn("Could not send e-mail to requestor: no e-mail address found in request " + request.getId());
            return CompletableFuture.completedFuture(null);
        }

        try {
            return doSendAsync(notificationParams,
                    Arrays.asList(InternetAddress.parse(request.getNotificationEmailAddress())), false);
        }
        catch (AddressException e) {
            log.error("Invalid e-mail address found in request, could not send notification e-mail to "
                    + request.getNotificationEmailAddress(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public CompletableFuture<Void> notifyAdmins(NotificationParams notificationParams) {
        return doSendAsync(notificationParams, this.adminMailRecipients, false);
    }

    @Override
    public CompletableFuture<Void> notifyProducer(NotificationParams notificationParams, String currentUserEmail,
            String producerApplicationId) {
        Set<String> mailAddresses = applicationsService.getAllApplicationOwnerRequests().stream()
                .filter(ownerReq -> ownerReq.getState().equals(RequestState.APPROVED)
                        && ownerReq.getApplicationId().equals(producerApplicationId)
                        && !ownerReq.getNotificationEmailAddress().equals(currentUserEmail))
                .map(req -> req.getNotificationEmailAddress()).collect(Collectors.toSet());

        return doSendAsync(notificationParams, safeToRecipientsList(mailAddresses, true), false);
    }

    @Override
    public CompletableFuture<Void> notifyTopicOwners(String environmentId, String topicName,
            NotificationParams notificationParams) {
        String ownerApplicationId = topicService.getTopic(environmentId, topicName).map(m -> m.getOwnerApplicationId())
                .orElse(null);
        if (ownerApplicationId == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Unknown topic " + topicName + " on environment " + environmentId));
        }

        return notifyApplicationTopicOwners(ownerApplicationId, notificationParams);
    }

    @Override
    public CompletableFuture<Void> notifyApplicationTopicOwners(String applicationId,
            NotificationParams notificationParams) {
        List<String> mailAddresses = applicationsService.getAllApplicationOwnerRequests().stream()
                .filter(r -> r.getState().equals(RequestState.APPROVED) && applicationId.equals(r.getApplicationId()))
                .map(ApplicationOwnerRequest::getNotificationEmailAddress).distinct().collect(Collectors.toList());

        return doSendAsync(notificationParams, safeToRecipientsList(mailAddresses, true), true);
    }

    private CompletableFuture<Void> doSendAsync(NotificationParams params, List<InternetAddress> recipients,
            boolean bcc) {
        if (recipients.isEmpty()) {
            log.debug("No recipients specified, not sending e-mail with template " + params.getTemplateName());
            return CompletableFuture.completedFuture(null);
        }

        String[] data = processTemplate(params);
        String subject = data[0];
        String htmlBody = data[1];
        String plainBody = data[2];

        CompletableFuture<Void> future = new CompletableFuture<>();

        Runnable task = () -> {
            try {
                MimeMessage msg = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(msg, true);
                helper.setFrom(fromAddress);
                if (bcc) {
                    helper.setBcc(recipients.toArray(new InternetAddress[recipients.size()]));
                }
                else {
                    helper.setTo(recipients.toArray(new InternetAddress[recipients.size()]));
                }

                helper.setText(plainBody, htmlBody);
                helper.setSubject(subject);

                log.debug("Sending e-mail to " + recipients.size() + " recipient(s)...");
                mailSender.send(msg);
                log.debug("E-mail sent successfully.");

                future.complete(null);
            }
            catch (MessagingException | MailException e) {
                log.error("Exception when sending notification e-mail", e);
                future.complete(null);
            }
        };

        taskExecutor.execute(task);
        return future;
    }

    private String[] processTemplate(NotificationParams params) {
        Context ctx = new Context();
        ctx.setVariables(params.getVariables());

        String htmlCode = this.templateEngine.process(params.getTemplateName(), ctx);
        Document doc = Jsoup.parse(htmlCode);
        String subject = doc.head().getElementsByTag("title").text();
        String plainText = doc.body().text();

        return new String[] { subject, htmlCode, plainText };
    }

    private List<InternetAddress> safeToRecipientsList(Collection<String> recipients, boolean logWarnings) {
        List<InternetAddress> addresses = new ArrayList<>(recipients.size());
        for (String address : recipients) {
            try {
                addresses.addAll(Arrays.asList(InternetAddress.parse(address)));
            }
            catch (AddressException e) {
                if (logWarnings) {
                    log.warn("Invalid e-mail address found in request, cannot notify: " + address, e);
                }
            }
        }

        return addresses;
    }

    private static List<InternetAddress> toRecipientsList(String recipients) throws AddressException {
        if (ObjectUtils.isEmpty(recipients)) {
            return Collections.emptyList();
        }

        return Arrays.asList(InternetAddress.parse(recipients));
    }

}
