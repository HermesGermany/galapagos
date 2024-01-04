package com.hermesworld.ais.galapagos.devauth.impl;

import com.hermesworld.ais.galapagos.devauth.DevAuthenticationMetadata;
import com.hermesworld.ais.galapagos.kafka.KafkaCluster;
import com.hermesworld.ais.galapagos.kafka.KafkaClusters;
import com.hermesworld.ais.galapagos.kafka.auth.CreateAuthenticationResult;
import com.hermesworld.ais.galapagos.kafka.auth.KafkaAuthenticationModule;
import com.hermesworld.ais.galapagos.kafka.impl.TopicBasedRepositoryMock;
import com.hermesworld.ais.galapagos.security.CurrentUserService;
import com.hermesworld.ais.galapagos.util.FutureUtil;
import com.hermesworld.ais.galapagos.util.TimeService;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeveloperAuthenticationServiceImplTest {

    @Mock
    private KafkaClusters kafkaClusters;

    @Mock
    private KafkaCluster testCluster;

    @Mock
    private CurrentUserService userService;

    @Mock
    private DevUserAclListener aclUpdater;

    @Mock
    private TimeService timeService;

    @Mock
    private KafkaAuthenticationModule authenticationModule;

    @Captor
    private ArgumentCaptor<Set<DevAuthenticationMetadata>> testClusterDeletedMetas;

    @Captor
    private ArgumentCaptor<Set<DevAuthenticationMetadata>> test2ClusterDeletedMetas;

    private final TopicBasedRepositoryMock<DevAuthenticationMetadata> metaRepo = new TopicBasedRepositoryMock<>();

    @Test
    void testCreateDeveloperAuthentication_positive() throws Exception {
        // given
        when(kafkaClusters.getEnvironment("test")).thenReturn(Optional.of(testCluster));
        when(testCluster.getRepository("devauth", DevAuthenticationMetadata.class)).thenReturn(metaRepo);

        when(kafkaClusters.getAuthenticationModule("test")).thenReturn(Optional.of(authenticationModule));

        when(userService.getCurrentUserName()).thenReturn(Optional.of("testuser"));

        when(aclUpdater.updateAcls(any(), any())).thenReturn(FutureUtil.noop());

        CreateAuthenticationResult result = new CreateAuthenticationResult(new JSONObject(Map.of("field1", "testval")),
                "topsecret".getBytes(StandardCharsets.UTF_8));

        when(authenticationModule.createDeveloperAuthentication(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(result));

        DeveloperAuthenticationServiceImpl service = new DeveloperAuthenticationServiceImpl(kafkaClusters, userService,
                aclUpdater, timeService);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // when
        DevAuthenticationMetadata metadata = service.createDeveloperAuthenticationForCurrentUser("test", baos).get();

        // then
        verify(authenticationModule, times(1)).createDeveloperAuthentication(eq("testuser"), any());
        verify(authenticationModule, times(0)).deleteDeveloperAuthentication(any(), any());

        verify(aclUpdater, times(0)).removeAcls(any(), any());
        verify(aclUpdater, times(1)).updateAcls(any(), eq(Set.of(metadata)));

        assertEquals("testuser", metadata.getUserName());
        assertEquals("testval", new JSONObject(metadata.getAuthenticationJson()).getString("field1"));
        assertEquals("topsecret", baos.toString(StandardCharsets.UTF_8));
    }

    @Test
    void testCreateDeveloperAuthentication_noUser() {
        // given
        when(userService.getCurrentUserName()).thenReturn(Optional.empty());

        DeveloperAuthenticationServiceImpl service = new DeveloperAuthenticationServiceImpl(kafkaClusters, userService,
                aclUpdater, timeService);

        // when / then
        ExecutionException exception = assertThrows(ExecutionException.class,
                () -> service.createDeveloperAuthenticationForCurrentUser("test", new ByteArrayOutputStream()).get());

        assertTrue(exception.getCause() instanceof IllegalStateException);
    }

    @Test
    void testCreateDeveloperAuthentication_invalidEnvironmentId() {
        // given
        lenient().when(kafkaClusters.getEnvironment("test")).thenReturn(Optional.of(testCluster));
        lenient().when(testCluster.getRepository("devauth", DevAuthenticationMetadata.class)).thenReturn(metaRepo);
        lenient().when(kafkaClusters.getAuthenticationModule("test")).thenReturn(Optional.of(authenticationModule));

        when(userService.getCurrentUserName()).thenReturn(Optional.of("testuser"));
        DeveloperAuthenticationServiceImpl service = new DeveloperAuthenticationServiceImpl(kafkaClusters, userService,
                aclUpdater, timeService);

        // when / then
        ExecutionException exception = assertThrows(ExecutionException.class,
                () -> service.createDeveloperAuthenticationForCurrentUser("test2", new ByteArrayOutputStream()).get());

        assertTrue(exception.getCause() instanceof NoSuchElementException);
    }

    @Test
    void testCreateDeveloperAuthentication_deletePreviousAuth() throws Exception {
        // given
        when(kafkaClusters.getEnvironment("test")).thenReturn(Optional.of(testCluster));
        when(testCluster.getRepository("devauth", DevAuthenticationMetadata.class)).thenReturn(metaRepo);

        when(kafkaClusters.getAuthenticationModule("test")).thenReturn(Optional.of(authenticationModule));

        when(userService.getCurrentUserName()).thenReturn(Optional.of("testuser"));

        CreateAuthenticationResult result = new CreateAuthenticationResult(new JSONObject(Map.of("field1", "testval")),
                "topsecret".getBytes(StandardCharsets.UTF_8));

        when(authenticationModule.createDeveloperAuthentication(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(result));
        when(authenticationModule.deleteDeveloperAuthentication(any(), any())).thenReturn(FutureUtil.noop());

        when(aclUpdater.updateAcls(any(), any())).thenReturn(FutureUtil.noop());
        when(aclUpdater.removeAcls(any(), any())).thenReturn(FutureUtil.noop());

        DevAuthenticationMetadata prevMeta = generateMetadata("testuser", "oldval");
        metaRepo.save(prevMeta).get();

        DeveloperAuthenticationServiceImpl service = new DeveloperAuthenticationServiceImpl(kafkaClusters, userService,
                aclUpdater, timeService);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // when
        DevAuthenticationMetadata metadata = service.createDeveloperAuthenticationForCurrentUser("test", baos).get();

        // then
        ArgumentCaptor<JSONObject> captor = ArgumentCaptor.forClass(JSONObject.class);
        verify(authenticationModule, times(1)).createDeveloperAuthentication(eq("testuser"), any());
        verify(authenticationModule, times(1)).deleteDeveloperAuthentication(eq("testuser"), captor.capture());

        verify(aclUpdater, times(1)).removeAcls(any(), any());
        verify(aclUpdater, times(1)).updateAcls(any(), eq(Set.of(metadata)));

        assertEquals("testuser", metadata.getUserName());
        assertEquals("oldval", captor.getValue().getString("field1"));
        assertEquals("testval", new JSONObject(metadata.getAuthenticationJson()).getString("field1"));
        assertEquals("topsecret", baos.toString(StandardCharsets.UTF_8));
    }

    @Test
    void testGetDeveloperAuthenticationForCurrentUser_positive() throws Exception {
        // given
        when(kafkaClusters.getEnvironment("test")).thenReturn(Optional.of(testCluster));
        when(testCluster.getRepository("devauth", DevAuthenticationMetadata.class)).thenReturn(metaRepo);

        when(kafkaClusters.getAuthenticationModule("test")).thenReturn(Optional.of(authenticationModule));

        when(userService.getCurrentUserName()).thenReturn(Optional.of("testuser"));

        DevAuthenticationMetadata prevMeta = generateMetadata("testuser", "oldval");
        metaRepo.save(prevMeta).get();

        when(userService.getCurrentUserName()).thenReturn(Optional.of(prevMeta.getUserName()));
        when(timeService.getTimestamp()).thenReturn(ZonedDateTime.now());
        when(authenticationModule.extractExpiryDate(json(new JSONObject(prevMeta.getAuthenticationJson()))))
                .thenReturn(Optional.of(ZonedDateTime.now().plusDays(10).toInstant()));

        DeveloperAuthenticationServiceImpl service = new DeveloperAuthenticationServiceImpl(kafkaClusters, userService,
                aclUpdater, timeService);

        // when
        DevAuthenticationMetadata metadata = service.getDeveloperAuthenticationOfCurrentUser("test").orElseThrow();

        // then
        assertEquals(prevMeta.getUserName(), metadata.getUserName());
        assertEquals(prevMeta.getAuthenticationJson(), metadata.getAuthenticationJson());
    }

    @Test
    void testGetDeveloperAuthenticationForCurrentUser_wrongUserName() throws Exception {
        // given
        when(kafkaClusters.getEnvironment("test")).thenReturn(Optional.of(testCluster));
        when(testCluster.getRepository("devauth", DevAuthenticationMetadata.class)).thenReturn(metaRepo);

        when(kafkaClusters.getAuthenticationModule("test")).thenReturn(Optional.of(authenticationModule));

        when(userService.getCurrentUserName()).thenReturn(Optional.of("testuser"));

        DevAuthenticationMetadata prevMeta = generateMetadata("testuser", "oldval");
        metaRepo.save(prevMeta).get();

        when(userService.getCurrentUserName()).thenReturn(Optional.of("otheruser"));

        DeveloperAuthenticationServiceImpl service = new DeveloperAuthenticationServiceImpl(kafkaClusters, userService,
                aclUpdater, timeService);

        // when
        Optional<DevAuthenticationMetadata> opMeta = service.getDeveloperAuthenticationOfCurrentUser("test");

        // then
        assertTrue(opMeta.isEmpty());
    }

    @Test
    void testGetDeveloperAuthenticationForCurrentUser_noCurrentUser() throws Exception {
        // given
        when(kafkaClusters.getEnvironment("test")).thenReturn(Optional.of(testCluster));

        // assert that USER missing is tested, not cluster missing
        lenient().when(testCluster.getRepository("devauth", DevAuthenticationMetadata.class)).thenReturn(metaRepo);
        lenient().when(kafkaClusters.getAuthenticationModule("test")).thenReturn(Optional.of(authenticationModule));

        when(userService.getCurrentUserName()).thenReturn(Optional.empty());

        DevAuthenticationMetadata prevMeta = generateMetadata("testuser", "oldval");
        metaRepo.save(prevMeta).get();

        when(userService.getCurrentUserName()).thenReturn(Optional.empty());

        DeveloperAuthenticationServiceImpl service = new DeveloperAuthenticationServiceImpl(kafkaClusters, userService,
                aclUpdater, timeService);

        // when
        Optional<DevAuthenticationMetadata> opMeta = service.getDeveloperAuthenticationOfCurrentUser("test");

        // then
        assertTrue(opMeta.isEmpty());
    }

    @Test
    void testGetDeveloperAuthenticationForCurrentUser_expired() throws Exception {
        // given
        when(kafkaClusters.getEnvironment("test")).thenReturn(Optional.of(testCluster));
        when(testCluster.getRepository("devauth", DevAuthenticationMetadata.class)).thenReturn(metaRepo);

        when(kafkaClusters.getAuthenticationModule("test")).thenReturn(Optional.of(authenticationModule));

        when(userService.getCurrentUserName()).thenReturn(Optional.of("testuser"));

        DevAuthenticationMetadata prevMeta = generateMetadata("testuser", "oldval");
        metaRepo.save(prevMeta).get();

        when(userService.getCurrentUserName()).thenReturn(Optional.of(prevMeta.getUserName()));
        when(timeService.getTimestamp()).thenReturn(ZonedDateTime.now());
        when(authenticationModule.extractExpiryDate(json(new JSONObject(prevMeta.getAuthenticationJson()))))
                .thenReturn(Optional.of(ZonedDateTime.now().minusHours(1).toInstant()));

        DeveloperAuthenticationServiceImpl service = new DeveloperAuthenticationServiceImpl(kafkaClusters, userService,
                aclUpdater, timeService);

        // when
        Optional<DevAuthenticationMetadata> opMeta = service.getDeveloperAuthenticationOfCurrentUser("test");

        // then
        assertTrue(opMeta.isEmpty(), "Metadata found although expired - should not have been returned.");
    }

    @Test
    void testClearExpiredDeveloperAuthenticationsOnAllClusters_positive() throws Exception {
        // given
        KafkaCluster cluster2 = mock(KafkaCluster.class);
        TopicBasedRepositoryMock<DevAuthenticationMetadata> metaRepo2 = new TopicBasedRepositoryMock<>();

        when(testCluster.getRepository("devauth", DevAuthenticationMetadata.class)).thenReturn(metaRepo);
        when(cluster2.getRepository("devauth", DevAuthenticationMetadata.class)).thenReturn(metaRepo2);

        lenient().when(kafkaClusters.getEnvironment("test")).thenReturn(Optional.of(testCluster));
        lenient().when(kafkaClusters.getEnvironment("test2")).thenReturn(Optional.of(cluster2));
        when(kafkaClusters.getEnvironments()).thenReturn(List.of(testCluster, cluster2));

        when(authenticationModule.deleteDeveloperAuthentication(any(), any())).thenReturn(FutureUtil.noop());
        when(kafkaClusters.getAuthenticationModule(any())).thenReturn(Optional.of(authenticationModule));

        metaRepo.save(generateMetadata("user1", "user1_test")).get();
        metaRepo.save(generateMetadata("user2", "user2_test")).get();
        metaRepo.save(generateMetadata("user3", "user3_test")).get();
        metaRepo2.save(generateMetadata("user1", "user1_test2")).get();
        metaRepo2.save(generateMetadata("user2", "user2_test2")).get();
        metaRepo2.save(generateMetadata("user3", "user3_test2")).get();
        metaRepo2.save(generateMetadata("user4", "user4_test2")).get();

        // user1 is expired on "test" environment, user2 on "test2", and user3 on both.
        // For user4, we do not provide an expiry date - should be untouched.
        ArgumentMatcher<JSONObject> expiredMatcher = (obj) -> {
            if (obj == null) {
                return false;
            }
            String val = obj.getString("field1");
            return List.of("user1_test", "user2_test2", "user3_test", "user3_test2").contains(val);
        };
        ArgumentMatcher<JSONObject> noDateMatcher = (obj) -> obj != null
                && obj.getString("field1").equals("user4_test2");

        when(authenticationModule.extractExpiryDate(any()))
                .thenReturn(Optional.of(ZonedDateTime.now().plusDays(10).toInstant()));
        when(authenticationModule.extractExpiryDate(argThat(expiredMatcher)))
                .thenReturn(Optional.of(ZonedDateTime.now().minusDays(10).toInstant()));
        when(authenticationModule.extractExpiryDate(argThat(noDateMatcher))).thenReturn(Optional.empty());

        when(timeService.getTimestamp()).thenReturn(ZonedDateTime.now());
        when(aclUpdater.removeAcls(any(), any())).thenReturn(FutureUtil.noop());

        DeveloperAuthenticationServiceImpl service = new DeveloperAuthenticationServiceImpl(kafkaClusters, userService,
                aclUpdater, timeService);

        // when
        int cleared = service.clearExpiredDeveloperAuthenticationsOnAllClusters().get();

        // then
        assertEquals(4, cleared);

        verify(authenticationModule, times(4)).deleteDeveloperAuthentication(any(), argThat(expiredMatcher));
        verify(authenticationModule, times(0)).deleteDeveloperAuthentication(any(), argThat(noDateMatcher));

        verify(aclUpdater).removeAcls(eq(testCluster), testClusterDeletedMetas.capture());
        verify(aclUpdater).removeAcls(eq(cluster2), test2ClusterDeletedMetas.capture());

        assertEquals(Set.of("user1", "user3"),
                testClusterDeletedMetas.getValue().stream().map(m -> m.getUserName()).collect(Collectors.toSet()));
        assertEquals(Set.of("user2", "user3"),
                test2ClusterDeletedMetas.getValue().stream().map(m -> m.getUserName()).collect(Collectors.toSet()));

        assertEquals(Set.of("user2"),
                metaRepo.getObjects().stream().map(m -> m.getUserName()).collect(Collectors.toSet()));
        assertEquals(Set.of("user1", "user4"),
                metaRepo2.getObjects().stream().map(m -> m.getUserName()).collect(Collectors.toSet()));
    }

    private static DevAuthenticationMetadata generateMetadata(String userName, String jsonField1Value) {
        DevAuthenticationMetadata meta = new DevAuthenticationMetadata();
        meta.setUserName(userName);
        meta.setAuthenticationJson(new JSONObject(Map.of("field1", jsonField1Value)).toString());
        return meta;
    }

    private static JSONObject json(JSONObject expected) {
        return argThat(new JSONMatcher(expected));
    }

    private static class JSONMatcher implements ArgumentMatcher<JSONObject> {

        private final JSONObject expected;

        private JSONMatcher(JSONObject expected) {
            this.expected = expected;
        }

        @Override
        public boolean matches(JSONObject argument) {
            return argument != null && expected.toString().equals(argument.toString());
        }
    }

}
