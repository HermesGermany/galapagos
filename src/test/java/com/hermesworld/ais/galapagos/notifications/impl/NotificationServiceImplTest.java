package com.hermesworld.ais.galapagos.notifications.impl;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMessage.RecipientType;

import com.hermesworld.ais.galapagos.applications.ApplicationOwnerRequest;
import com.hermesworld.ais.galapagos.applications.ApplicationsService;
import com.hermesworld.ais.galapagos.applications.RequestState;
import com.hermesworld.ais.galapagos.notifications.NotificationParams;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionMetadata;
import com.hermesworld.ais.galapagos.subscriptions.service.SubscriptionService;
import com.hermesworld.ais.galapagos.topics.service.TopicService;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import static org.mockito.Mockito.*;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor;
import org.thymeleaf.ITemplateEngine;

class NotificationServiceImplTest {

    private static final String TEST_ENV = "test-Environment";

    private static final String TEST_TOPIC = "test-Topic";

    private static final String TEST_USER = "test-User";

    private SubscriptionService subscriptionService;

    private ApplicationsService applicationService;

    private TopicService topicService;

    private JavaMailSender mailSender;

    private ITemplateEngine templateEngine;

    private ExecutorService executor;

    private MimeMessageHolder messageHolder;

    @BeforeEach
    void feedMocks() throws MessagingException {
        subscriptionService = mock(SubscriptionService.class);
        applicationService = mock(ApplicationsService.class);
        topicService = mock(TopicService.class);

        mailSender = mock(JavaMailSender.class);
        messageHolder = new MimeMessageHolder();
        when(mailSender.createMimeMessage()).thenReturn(messageHolder.mockMessage);

        templateEngine = mock(ITemplateEngine.class);

        executor = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void destroyMocks() {
        executor.shutdown();
    }

    @Test
    void testDoSendAsync_NoFailOnMailException() throws Exception {
        String testFromAddress = "test@abc.de";
        String testAdminMailsRecipients = "Test@abc.de";
        NotificationParams testNotificationParams = generateNotificationParams(TEST_USER, TEST_TOPIC);

        doThrow(new MailSendException("mail cannot be sent")).when(mailSender).send((MimeMessage) any());
        String htmlCode = "<html><head><title>Testmail</title></head><body><p>Test</p></body></html>";
        when(templateEngine.process(ArgumentMatchers.<String>any(), ArgumentMatchers.any())).thenReturn(htmlCode);

        ConcurrentTaskExecutor exec = new ConcurrentTaskExecutor(executor);
        NotificationServiceImpl notificationServiceImpl = new NotificationServiceImpl(subscriptionService,
                applicationService, topicService, mailSender, exec, templateEngine, testFromAddress,
                testAdminMailsRecipients);

        notificationServiceImpl.notifyAdmins(testNotificationParams).get();
    }

    @Test
    void testNotifySubscribersWithExclusionUser() throws Exception {
        String testFromAddress = "test@abc.de";
        String testAdminMailsRecipients = "Test@abc.de";
        String testApplicationId = "42";
        NotificationParams testNotificationParams = generateNotificationParams(TEST_USER, TEST_TOPIC);

        List<SubscriptionMetadata> subscriptionMetadatas = generateSubscriptionMetadatas(testApplicationId);
        when(subscriptionService.getSubscriptionsForTopic(TEST_ENV, TEST_TOPIC, false))
                .thenReturn(subscriptionMetadatas);

        List<ApplicationOwnerRequest> applicationOwnerRequests = generateApplicationOwnerRequests(testFromAddress,
                testApplicationId);
        when(applicationService.getAllApplicationOwnerRequests()).thenReturn(applicationOwnerRequests);

        String htmlCode = "<html><head><title>Testmail</title></head><body><p>Test</p></body></html>";
        when(templateEngine.process(ArgumentMatchers.<String>any(), ArgumentMatchers.any())).thenReturn(htmlCode);

        ConcurrentTaskExecutor exec = new ConcurrentTaskExecutor(executor);

        NotificationServiceImpl notificationServiceImpl = new NotificationServiceImpl(subscriptionService,
                applicationService, topicService, mailSender, exec, templateEngine, testFromAddress,
                testAdminMailsRecipients);

        notificationServiceImpl.notifySubscribers(TEST_ENV, TEST_TOPIC, testNotificationParams, TEST_USER).get();

        assertFalse(messageHolder.recipients.contains(testFromAddress));
    }

    @Test
    void testNotifyApplicationTopicOwners_noSubmittedIncluded() throws Exception {
        String testFromAddress = "test@abc.de";
        String testAdminMailsRecipients = "Test@abc.de";
        String applicationId = "1";
        NotificationParams testNotificationParams = generateNotificationParams(TEST_USER, TEST_TOPIC);

        ApplicationOwnerRequest requestSucess1 = new ApplicationOwnerRequest();
        requestSucess1.setApplicationId("1");
        requestSucess1.setUserName("User1");
        requestSucess1.setState(RequestState.APPROVED);
        requestSucess1.setComments("my comment");
        requestSucess1.setNotificationEmailAddress("foo@bar.com");

        ApplicationOwnerRequest requestSucess2 = new ApplicationOwnerRequest();
        requestSucess2.setApplicationId("1");
        requestSucess2.setUserName("User2");
        requestSucess2.setState(RequestState.APPROVED);
        requestSucess2.setComments("my awesome comment");
        requestSucess2.setNotificationEmailAddress("foo2@bar2.com");

        ApplicationOwnerRequest requestFail = new ApplicationOwnerRequest();
        requestFail.setApplicationId("1");
        requestFail.setUserName("User2");
        requestFail.setState(RequestState.SUBMITTED);
        requestFail.setComments("This user will get no email");
        requestFail.setNotificationEmailAddress("no@mail.com");

        when(applicationService.getAllApplicationOwnerRequests())
                .thenReturn(List.of(requestSucess1, requestSucess2, requestFail));

        String htmlCode = "<html><head><title>Testmail</title></head><body><p>Test</p></body></html>";

        when(templateEngine.process(ArgumentMatchers.<String>any(), ArgumentMatchers.any())).thenReturn(htmlCode);

        ConcurrentTaskExecutor exec = new ConcurrentTaskExecutor(executor);

        NotificationServiceImpl notificationServiceImpl = new NotificationServiceImpl(subscriptionService,
                applicationService, topicService, mailSender, exec, templateEngine, testFromAddress,
                testAdminMailsRecipients);

        notificationServiceImpl.notifyApplicationTopicOwners(applicationId, testNotificationParams).get();
        assertTrue(messageHolder.recipients.contains("foo@bar.com"));
        assertTrue(messageHolder.recipients.contains("foo2@bar2.com"));
        assertFalse(messageHolder.recipients.contains("no@mail.com"));

    }

    @Test
    void testNotifySubscribers_noSubmittedIncluded() throws Exception {
        String testFromAddress = "test@abc.de";
        String testAdminMailsRecipients = "Test@abc.de";
        NotificationParams testNotificationParams = generateNotificationParams(TEST_USER, TEST_TOPIC);

        ApplicationOwnerRequest requestSucess1 = new ApplicationOwnerRequest();
        requestSucess1.setApplicationId("1");
        requestSucess1.setUserName("User1");
        requestSucess1.setState(RequestState.APPROVED);
        requestSucess1.setComments("my comment");
        requestSucess1.setNotificationEmailAddress("foo@bar.com");

        ApplicationOwnerRequest requestSucess2 = new ApplicationOwnerRequest();
        requestSucess2.setApplicationId("1");
        requestSucess2.setUserName("User2");
        requestSucess2.setState(RequestState.APPROVED);
        requestSucess2.setComments("my awesome comment");
        requestSucess2.setNotificationEmailAddress("foo2@bar2.com");

        ApplicationOwnerRequest requestFail = new ApplicationOwnerRequest();
        requestFail.setApplicationId("1");
        requestFail.setUserName("User3");
        requestFail.setState(RequestState.SUBMITTED);
        requestFail.setComments("This user will get no email");
        requestFail.setNotificationEmailAddress("no@mail.com");

        SubscriptionMetadata sub = new SubscriptionMetadata();
        sub.setId("123");
        sub.setClientApplicationId("1");
        sub.setTopicName("topic1");
        when(subscriptionService.getSubscriptionsForTopic(TEST_ENV, TEST_TOPIC, false)).thenReturn(List.of(sub));

        when(applicationService.getAllApplicationOwnerRequests())
                .thenReturn(List.of(requestSucess1, requestSucess2, requestFail));

        String htmlCode = "<html><head><title>Testmail</title></head><body><p>Test</p></body></html>";

        when(templateEngine.process(ArgumentMatchers.<String>any(), ArgumentMatchers.any())).thenReturn(htmlCode);

        ConcurrentTaskExecutor exec = new ConcurrentTaskExecutor(executor);

        NotificationServiceImpl notificationServiceImpl = new NotificationServiceImpl(subscriptionService,
                applicationService, topicService, mailSender, exec, templateEngine, testFromAddress,
                testAdminMailsRecipients);

        notificationServiceImpl.notifySubscribers(TEST_ENV, TEST_TOPIC, testNotificationParams, TEST_USER).get();

        assertTrue(messageHolder.recipients.contains("foo@bar.com"));
        assertTrue(messageHolder.recipients.contains("foo2@bar2.com"));
        assertFalse(messageHolder.recipients.contains("no@mail.com"));
    }

    private List<SubscriptionMetadata> generateSubscriptionMetadatas(String applicationId) {
        List<SubscriptionMetadata> subscriptionMetadatas = new ArrayList<>();

        SubscriptionMetadata subscriptionMetadata = generateSubscriptionMetadata(applicationId, TEST_TOPIC);
        subscriptionMetadatas.add(subscriptionMetadata);

        SubscriptionMetadata subscriptionMetadata2 = generateSubscriptionMetadata("100", "moin");
        subscriptionMetadatas.add(subscriptionMetadata2);

        SubscriptionMetadata subscriptionMetadata3 = generateSubscriptionMetadata("", "");
        subscriptionMetadatas.add(subscriptionMetadata3);

        return subscriptionMetadatas;
    }

    private SubscriptionMetadata generateSubscriptionMetadata(String applicationId, String topicName) {
        SubscriptionMetadata subscriptionMetadata = new SubscriptionMetadata();
        subscriptionMetadata.setClientApplicationId(applicationId);
        subscriptionMetadata.setId("1");
        subscriptionMetadata.setTopicName(topicName);
        return subscriptionMetadata;
    }

    private List<ApplicationOwnerRequest> generateApplicationOwnerRequests(String notificationEmailAddress,
            String applicationId) {
        List<ApplicationOwnerRequest> requests = new ArrayList<>();

        ApplicationOwnerRequest applicationOwnerRequest = generateApplicationOwnerRequest(TEST_USER,
                notificationEmailAddress, applicationId);
        requests.add(applicationOwnerRequest);

        ApplicationOwnerRequest applicationOwnerRequest2 = generateApplicationOwnerRequest("Alice", "abc@abc.de",
                "100");
        requests.add(applicationOwnerRequest2);

        ApplicationOwnerRequest applicationOwnerRequest3 = generateApplicationOwnerRequest("Bob", "null@null.de", "");
        requests.add(applicationOwnerRequest3);

        return requests;
    }

    private ApplicationOwnerRequest generateApplicationOwnerRequest(String userName, String notificationEmailAddress,
            String applicationId) {
        ZonedDateTime past = ZonedDateTime.of(LocalDateTime.of(2020, 6, 19, 10, 0), ZoneOffset.UTC);
        ZonedDateTime now = ZonedDateTime.of(LocalDateTime.of(2020, 6, 20, 10, 0), ZoneOffset.UTC);
        ApplicationOwnerRequest applicationOwnerRequest = new ApplicationOwnerRequest();
        applicationOwnerRequest.setApplicationId(applicationId);
        applicationOwnerRequest.setComments(null);
        applicationOwnerRequest.setCreatedAt(now);
        applicationOwnerRequest.setId("1");
        applicationOwnerRequest.setLastStatusChangeAt(past);
        applicationOwnerRequest.setLastStatusChangeBy(null);
        applicationOwnerRequest.setNotificationEmailAddress(notificationEmailAddress);
        applicationOwnerRequest.setState(RequestState.APPROVED);
        applicationOwnerRequest.setUserName(userName);
        return applicationOwnerRequest;
    }

    private NotificationParams generateNotificationParams(String userName, String topicName) {
        NotificationParams notificationParams = new NotificationParams("topic-changed");
        notificationParams.addVariable("user_name", userName);
        notificationParams.addVariable("topic_name", topicName);
        notificationParams.addVariable("change_action_text", "ein neues JSON-Schema veröffentlicht");
        notificationParams.addVariable("galapagos_topic_url", "/some/url");
        return notificationParams;
    }

    private static class MimeMessageHolder {

        private final List<String> recipients = new ArrayList<>();

        private final MimeMessage mockMessage;

        public MimeMessageHolder() throws MessagingException {
            mockMessage = mock(MimeMessage.class);
            doAnswer(inv -> {
                Address[] addrs = inv.getArgument(1);
                recipients.addAll(Arrays.stream(addrs).map(Address::toString).collect(Collectors.toList()));
                return null;
            }).when(mockMessage).setRecipients(ArgumentMatchers.<RecipientType>any(),
                    ArgumentMatchers.<Address[]>any());
        }

    }

}
