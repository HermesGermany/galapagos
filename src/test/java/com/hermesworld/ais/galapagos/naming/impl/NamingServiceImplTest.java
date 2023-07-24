package com.hermesworld.ais.galapagos.naming.impl;

import com.hermesworld.ais.galapagos.applications.BusinessCapability;
import com.hermesworld.ais.galapagos.applications.KnownApplication;
import com.hermesworld.ais.galapagos.naming.ApplicationPrefixes;
import com.hermesworld.ais.galapagos.naming.InvalidTopicNameException;
import com.hermesworld.ais.galapagos.naming.config.AdditionNamingRules;
import com.hermesworld.ais.galapagos.naming.config.CaseStrategy;
import com.hermesworld.ais.galapagos.naming.config.NamingConfig;
import com.hermesworld.ais.galapagos.naming.config.TopicNamingConfig;
import com.hermesworld.ais.galapagos.topics.TopicType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NamingServiceImplTest {

    private AdditionNamingRules rules;

    private NamingConfig config;

    private KnownApplication app;

    private BusinessCapability cap1;

    private BusinessCapability cap2;

    @BeforeEach
    void feedMocks() {
        rules = new AdditionNamingRules();
        rules.setAllowedSeparators(".");
        rules.setAllowPascalCase(true);
        rules.setAllowKebabCase(true);
        rules.setAllowCamelCase(false);

        TopicNamingConfig eventConfig = new TopicNamingConfig();
        eventConfig.setNameFormat("de.hlg.events.{business-capability}.{addition}");
        eventConfig.setAdditionRules(rules);

        config = new NamingConfig();
        config.setEvents(eventConfig);
        config.setInternalTopicPrefixFormat("{app-or-alias}.internal.");
        config.setConsumerGroupPrefixFormat("groups.{app-or-alias}.");
        config.setTransactionalIdPrefixFormat("transactions.{app-or-alias}.");

        app = mock(KnownApplication.class);
        when(app.getName()).thenReturn("Track & Trace");
        when(app.getAliases()).thenReturn(Set.of());

        cap1 = mock(BusinessCapability.class);
        when(cap1.getName()).thenReturn("Orders");

        cap2 = mock(BusinessCapability.class);
        when(cap2.getName()).thenReturn("Sales");

        when(app.getBusinessCapabilities()).thenReturn(List.of(cap1, cap2));
    }

    @Test
    void testAllowInternalTopicNamesForConsumerGroups() {
        config.setAllowInternalTopicNamesAsConsumerGroups(true);
        NamingServiceImpl service = new NamingServiceImpl(config);
        ApplicationPrefixes prefixes = service.getAllowedPrefixes(app);

        assertEquals(1, prefixes.getInternalTopicPrefixes().size());
        assertEquals(2, prefixes.getConsumerGroupPrefixes().size());
        assertTrue(prefixes.getInternalTopicPrefixes().contains("track-trace.internal."));
        assertTrue(prefixes.getConsumerGroupPrefixes().contains("track-trace.internal."));
        assertTrue(prefixes.getConsumerGroupPrefixes().contains("groups.track-trace."));

        config.setAllowInternalTopicNamesAsConsumerGroups(false);
        service = new NamingServiceImpl(config);
        prefixes = service.getAllowedPrefixes(app);

        assertEquals(1, prefixes.getInternalTopicPrefixes().size());
        assertEquals(1, prefixes.getConsumerGroupPrefixes().size());
        assertTrue(prefixes.getInternalTopicPrefixes().contains("track-trace.internal."));
        assertTrue(prefixes.getConsumerGroupPrefixes().contains("groups.track-trace."));
    }

    @Test
    void testGetTopicNameSuggestion() {
        NamingServiceImpl service = new NamingServiceImpl(config);

        assertEquals("de.hlg.events.orders.my-topic", service.getTopicNameSuggestion(TopicType.EVENTS, app, cap1));
        assertEquals("de.hlg.events.sales.my-topic", service.getTopicNameSuggestion(TopicType.EVENTS, app, cap2));

        assertEquals("track-trace.internal.my-topic", service.getTopicNameSuggestion(TopicType.INTERNAL, app, null));

        // check that different normalization is considered correctly
        config.setNormalizationStrategy(CaseStrategy.SNAKE_CASE);
        assertEquals("de.hlg.events.ORDERS.MY_TOPIC", service.getTopicNameSuggestion(TopicType.EVENTS, app, cap1));
        assertEquals("TRACK_TRACE.internal.MY_TOPIC", service.getTopicNameSuggestion(TopicType.INTERNAL, app, null));
    }

    @Test
    void testGetAllowedPrefixes() {
        when(app.getAliases()).thenReturn(Set.of("tt"));

        NamingServiceImpl service = new NamingServiceImpl(config);
        ApplicationPrefixes prefixes = service.getAllowedPrefixes(app);

        assertEquals(2, prefixes.getInternalTopicPrefixes().size());
        assertEquals(2, prefixes.getConsumerGroupPrefixes().size());
        assertEquals(2, prefixes.getTransactionIdPrefixes().size());

        assertTrue(prefixes.getInternalTopicPrefixes().contains("tt.internal."));
        assertTrue(prefixes.getInternalTopicPrefixes().contains("track-trace.internal."));

        assertTrue(prefixes.getConsumerGroupPrefixes().contains("groups.track-trace."));
        assertTrue(prefixes.getConsumerGroupPrefixes().contains("groups.tt."));

        assertTrue(prefixes.getTransactionIdPrefixes().contains("transactions.track-trace."));
        assertTrue(prefixes.getTransactionIdPrefixes().contains("transactions.tt."));

        config.setConsumerGroupPrefixFormat("groups.{application}.");
        service = new NamingServiceImpl(config);
        prefixes = service.getAllowedPrefixes(app);

        assertEquals(2, prefixes.getInternalTopicPrefixes().size());
        assertEquals(1, prefixes.getConsumerGroupPrefixes().size());
        assertEquals(2, prefixes.getTransactionIdPrefixes().size());

        assertTrue(prefixes.getConsumerGroupPrefixes().contains("groups.track-trace."));
    }

    @Test
    void testValidateTopicName() throws InvalidTopicNameException {
        NamingServiceImpl service = new NamingServiceImpl(config);

        service.validateTopicName("de.hlg.events.orders.my-custom-order", TopicType.EVENTS, app);
        service.validateTopicName("de.hlg.events.sales.MyCustomOrder", TopicType.EVENTS, app);
        service.validateTopicName("de.hlg.events.orders.My.Custom.Order", TopicType.EVENTS, app);
        // must be valid - could be "kebab-case" with only one word
        service.validateTopicName("de.hlg.events.sales.mycustomorder", TopicType.EVENTS, app);

        expectException(InvalidTopicNameException.class,
                () -> service.validateTopicName("de.hlg.events.orders.myCustomOrder", TopicType.EVENTS, app));
        expectException(InvalidTopicNameException.class,
                () -> service.validateTopicName("de.hlg.events.orders.My-Custom-Order", TopicType.EVENTS, app));
        expectException(InvalidTopicNameException.class,
                () -> service.validateTopicName("de.hlg.events.orders.MY_CUSTOM_ORDER", TopicType.EVENTS, app));

        // wrong prefix
        expectException(InvalidTopicNameException.class,
                () -> service.validateTopicName("de.hlg.orders.my-custom-order", TopicType.EVENTS, app));

        // invalid business capability
        expectException(InvalidTopicNameException.class,
                () -> service.validateTopicName("de.hlg.events.shipment.my-custom-order", TopicType.EVENTS, app));

        rules.setAllowPascalCase(false);
        rules.setAllowKebabCase(false);

        service.validateTopicName("de.hlg.events.orders.mycustomorder", TopicType.EVENTS, app);

        expectException(InvalidTopicNameException.class,
                () -> service.validateTopicName("de.hlg.events.orders.my-custom-order", TopicType.EVENTS, app));
        expectException(InvalidTopicNameException.class,
                () -> service.validateTopicName("de.hlg.events.orders.MyCustomOrder", TopicType.EVENTS, app));

        rules.setAllowSnakeCase(true);

        service.validateTopicName("de.hlg.events.orders.MY_CUSTOM_ORDER", TopicType.EVENTS, app);
        expectException(InvalidTopicNameException.class,
                () -> service.validateTopicName("de.hlg.events.orders.mycustomorder", TopicType.EVENTS, app));
    }

    @Test
    void testNormalize_simpleCases() {
        NamingConfig config = mock(NamingConfig.class);
        when(config.getNormalizationStrategy()).thenReturn(CaseStrategy.PASCAL_CASE);

        NamingServiceImpl service = new NamingServiceImpl(config);
        assertEquals("TrackTrace", service.normalize("Track & Trace"));

        when(config.getNormalizationStrategy()).thenReturn(CaseStrategy.CAMEL_CASE);
        assertEquals("trackTrace", service.normalize("Track & Trace"));

        when(config.getNormalizationStrategy()).thenReturn(CaseStrategy.KEBAB_CASE);
        assertEquals("track-trace", service.normalize("Track & Trace"));

        when(config.getNormalizationStrategy()).thenReturn(CaseStrategy.SNAKE_CASE);
        assertEquals("TRACK_TRACE", service.normalize("Track & Trace"));

        when(config.getNormalizationStrategy()).thenReturn(CaseStrategy.LOWERCASE);
        assertEquals("tracktrace", service.normalize("Track & Trace"));
    }

    @Test
    void testNormalize_lead_trail() {
        NamingConfig config = mock(NamingConfig.class);
        when(config.getNormalizationStrategy()).thenReturn(CaseStrategy.KEBAB_CASE);
        NamingServiceImpl service = new NamingServiceImpl(config);

        assertEquals("lead-space", service.normalize(" Lead Space"));
        assertEquals("lead-special", service.normalize("!!Lead Special"));
        assertEquals("lead-and-trail-space", service.normalize(" Lead and Trail Space "));
        assertEquals("trail-special", service.normalize("Trail Special?# "));
    }

    @Test
    void testNormalize_localization() {
        NamingConfig config = mock(NamingConfig.class);
        when(config.getNormalizationStrategy()).thenReturn(CaseStrategy.KEBAB_CASE);
        NamingServiceImpl service = new NamingServiceImpl(config);

        assertEquals("neue-auftraege", service.normalize("Neue Aufträge"));
        assertEquals("tres-bien", service.normalize("Trés bien"));
        assertEquals("neue-aenderungen", service.normalize("Neue »Änderungen«"));

        when(config.getNormalizationStrategy()).thenReturn(CaseStrategy.PASCAL_CASE);
        assertEquals("NeueAenderungen", service.normalize("Neue änderungen"));
    }

    // TODO replace with JUnit 5 operator when migrated to JUnit 5
    private void expectException(Class<? extends Exception> exceptionClass, ThrowingRunnable code) {
        try {
            code.run();
            fail("Expected exception of type " + exceptionClass.getName() + ", but no exception was thrown");
        }
        catch (Exception e) {
            if (!exceptionClass.isAssignableFrom(e.getClass())) {
                e.printStackTrace();
                fail("Expected exception of type " + exceptionClass.getName() + ", but caught "
                        + e.getClass().getName());
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
