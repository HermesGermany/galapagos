package com.hermesworld.ais.galapagos.topics.impl;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.mockito.Mockito;

import com.hermesworld.ais.galapagos.applications.ApplicationMetadata;
import com.hermesworld.ais.galapagos.applications.BusinessCapability;
import com.hermesworld.ais.galapagos.applications.KnownApplication;
import com.hermesworld.ais.galapagos.topics.InvalidTopicNameException;
import com.hermesworld.ais.galapagos.topics.TopicType;

public class DefaultTopicNameValidatorTest {

    @Test
    public void testSpecialSeparator() throws InvalidTopicNameException {
        DefaultTopicNameValidator validator = new DefaultTopicNameValidator("de!test", "!");

        validator.validateTopicName("de!test!events!goods!one!TwoThree", TopicType.EVENTS, mockApp("TestApp", "Goods"),
                Mockito.mock(ApplicationMetadata.class));
        validator.validateTopicName("de!test!events!goods!one!two-three", TopicType.EVENTS, mockApp("TestApp", "Goods"),
                Mockito.mock(ApplicationMetadata.class));
        validator.validateTopicName("de!test!events!goods!one-two!three", TopicType.EVENTS, mockApp("TestApp", "Goods"),
                Mockito.mock(ApplicationMetadata.class));
    }

    @Test
    public void testTopicTypeNames() throws InvalidTopicNameException {
        DefaultTopicNameValidator validator = new DefaultTopicNameValidator("de.test", ".");

        validator.validateTopicName("de.test.commands.goods.one.TwoThree", TopicType.COMMANDS,
                mockApp("TestApp", "Goods"), Mockito.mock(ApplicationMetadata.class));
        validator.validateTopicName("de.test.data.goods.one.TwoThree", TopicType.DATA, mockApp("TestApp", "Goods"),
                Mockito.mock(ApplicationMetadata.class));
        validator.validateTopicName("testapp.internal.one.TwoThree", TopicType.INTERNAL, mockApp("TestApp", "Goods"),
                mockMeta("testapp.internal."));
        validator.validateTopicName("testapp.internal.one.two-three", TopicType.INTERNAL, mockApp("TestApp", "Goods"),
                mockMeta("testapp.internal."));
    }

    @Test
    public void testHyphens() throws InvalidTopicNameException {
        DefaultTopicNameValidator validator = new DefaultTopicNameValidator("de.test", ".");

        validator.validateTopicName("de.test.events.goods.o-ne.TwoThree", TopicType.EVENTS, mockApp("TestApp", "Goods"),
                Mockito.mock(ApplicationMetadata.class));
        validator.validateTopicName("de.test.events.goods.on-e.TwoThree", TopicType.EVENTS, mockApp("TestApp", "Goods"),
                Mockito.mock(ApplicationMetadata.class));
    }

    @Test
    public void testBusinessCapNames() throws InvalidTopicNameException {
        DefaultTopicNameValidator validator = new DefaultTopicNameValidator("de.test", ".");

        validator.validateTopicName("de.test.events.goods.one.TwoThree", TopicType.EVENTS,
                mockApp("TestApp", "!&Goods"), Mockito.mock(ApplicationMetadata.class));
        validator.validateTopicName("de.test.events.goods.one.TwoThree", TopicType.EVENTS,
                mockApp("TestApp", "GoodsÖß"), Mockito.mock(ApplicationMetadata.class));
    }

    @Test
    public void testMultipleBusinessCaps() throws InvalidTopicNameException {
        DefaultTopicNameValidator validator = new DefaultTopicNameValidator("de.test", ".");

        validator.validateTopicName("de.test.events.bads.one.TwoThree", TopicType.EVENTS,
                mockApp("TestApp", "Goods", "Bads", "Uglies"), Mockito.mock(ApplicationMetadata.class));
    }

    @Test
    public void testSingleLetterSections() throws InvalidTopicNameException {
        DefaultTopicNameValidator validator = new DefaultTopicNameValidator("de.test", ".");

        validator.validateTopicName("de.test.events.g.o.t", TopicType.EVENTS, mockApp("TestApp", "G"),
                Mockito.mock(ApplicationMetadata.class));
        validator.validateTopicName("testapp.internal.g.o.T", TopicType.INTERNAL, mockApp("TestApp", "G"),
                mockMeta("testapp.internal."));
    }

    @Test(expected = InvalidTopicNameException.class)
    public void testInvalidBusinessCap() throws InvalidTopicNameException {
        DefaultTopicNameValidator validator = new DefaultTopicNameValidator("de.test", ".");

        validator.validateTopicName("de.test.events.bads.one.TwoThree", TopicType.EVENTS, mockApp("TestApp", "Goods"),
                Mockito.mock(ApplicationMetadata.class));
    }

    @Test(expected = InvalidTopicNameException.class)
    public void testInvalidPrefix1() throws InvalidTopicNameException {
        DefaultTopicNameValidator validator = new DefaultTopicNameValidator("de.test", ".");

        validator.validateTopicName("detest.events.goods.one.TwoThree", TopicType.EVENTS, mockApp("TestApp", "Goods"),
                Mockito.mock(ApplicationMetadata.class));
    }

    @Test(expected = InvalidTopicNameException.class)
    public void testInvalidPrefix2() throws InvalidTopicNameException {
        DefaultTopicNameValidator validator = new DefaultTopicNameValidator("de.test", ".");

        validator.validateTopicName("De.test.events.goods.one.TwoThree", TopicType.EVENTS, mockApp("TestApp", "Goods"),
                Mockito.mock(ApplicationMetadata.class));
    }

    @Test(expected = InvalidTopicNameException.class)
    public void testInvalidPascalCaseBetween() throws InvalidTopicNameException {
        DefaultTopicNameValidator validator = new DefaultTopicNameValidator("de.test", ".");

        validator.validateTopicName("de.test.events.goods.OneTwo.three", TopicType.EVENTS, mockApp("TestApp", "Goods"),
                Mockito.mock(ApplicationMetadata.class));
    }

    @Test(expected = InvalidTopicNameException.class)
    public void testInvalidPascalCaseMustNotContainHyphen() throws InvalidTopicNameException {
        DefaultTopicNameValidator validator = new DefaultTopicNameValidator("de.test", ".");

        validator.validateTopicName("de.test.events.goods.one.Two-Three", TopicType.EVENTS, mockApp("TestApp", "Goods"),
                Mockito.mock(ApplicationMetadata.class));
    }

    @Test(expected = InvalidTopicNameException.class)
    public void testInvalidTwoHyphens() throws InvalidTopicNameException {
        DefaultTopicNameValidator validator = new DefaultTopicNameValidator("de.test", ".");

        validator.validateTopicName("de.test.events.goods.o--ne.TwoThree", TopicType.EVENTS,
                mockApp("TestApp", "Goods"), Mockito.mock(ApplicationMetadata.class));
    }

    @Test(expected = InvalidTopicNameException.class)
    public void testInvalidLeadHyphens() throws InvalidTopicNameException {
        DefaultTopicNameValidator validator = new DefaultTopicNameValidator("de.test", ".");

        validator.validateTopicName("de.test.events.goods.-one.TwoThree", TopicType.EVENTS, mockApp("TestApp", "Goods"),
                Mockito.mock(ApplicationMetadata.class));
    }

    @Test(expected = InvalidTopicNameException.class)
    public void testInvalidTrailHyphens() throws InvalidTopicNameException {
        DefaultTopicNameValidator validator = new DefaultTopicNameValidator("de.test", ".");

        validator.validateTopicName("de.test.events.goods.one-.TwoThree", TopicType.EVENTS, mockApp("TestApp", "Goods"),
                Mockito.mock(ApplicationMetadata.class));
    }

    @Test(expected = InvalidTopicNameException.class)
    public void testInvalidTwoSeparators() throws InvalidTopicNameException {
        DefaultTopicNameValidator validator = new DefaultTopicNameValidator("de!test", "!");

        validator.validateTopicName("de!test!events!goods!!one!TwoThree", TopicType.EVENTS, mockApp("TestApp", "Goods"),
                Mockito.mock(ApplicationMetadata.class));
    }

    @Test(expected = InvalidTopicNameException.class)
    public void testInvalidDotSpecialSeparators() throws InvalidTopicNameException {
        DefaultTopicNameValidator validator = new DefaultTopicNameValidator("de!test", "!");

        validator.validateTopicName("de!test!events.goods!one!TwoThree", TopicType.EVENTS, mockApp("TestApp", "Goods"),
                Mockito.mock(ApplicationMetadata.class));
    }

    @Test(expected = InvalidTopicNameException.class)
    public void testInvalidTopicTypeName1() throws InvalidTopicNameException {
        DefaultTopicNameValidator validator = new DefaultTopicNameValidator("de.test", ".");

        validator.validateTopicName("de.test.events.goods.one.TwoThree", TopicType.COMMANDS,
                mockApp("TestApp", "Goods"), Mockito.mock(ApplicationMetadata.class));
    }

    @Test(expected = InvalidTopicNameException.class)
    public void testInvalidTopicTypeName2() throws InvalidTopicNameException {
        DefaultTopicNameValidator validator = new DefaultTopicNameValidator("de.test", ".");

        validator.validateTopicName("de.test.events.goods.one.TwoThree", TopicType.INTERNAL,
                mockApp("TestApp", "Goods"), mockMeta("testapp.internal."));
    }

    @Test(expected = InvalidTopicNameException.class)
    public void testInvalidTopicTypeName3() throws InvalidTopicNameException {
        DefaultTopicNameValidator validator = new DefaultTopicNameValidator("de.test", ".");

        validator.validateTopicName("de.test.internal.goods.one.TwoThree", TopicType.INTERNAL,
                mockApp("TestApp", "Goods"), mockMeta("testapp.internal."));
    }

    @Test(expected = InvalidTopicNameException.class)
    public void testInvalidJustTopicPrefix() throws InvalidTopicNameException {
        DefaultTopicNameValidator validator = new DefaultTopicNameValidator("de.test", ".");

        validator.validateTopicName("testapp.internal.", TopicType.INTERNAL, mockApp("TestApp", "Goods"),
                mockMeta("testapp.internal."));
    }

    @Test(expected = InvalidTopicNameException.class)
    public void testInvalidPascalCase1() throws InvalidTopicNameException {
        DefaultTopicNameValidator validator = new DefaultTopicNameValidator("de.test", ".");

        validator.validateTopicName("testapp.internal.one.oTwoThree", TopicType.INTERNAL, mockApp("TestApp", "Goods"),
                mockMeta("testapp.internal."));
    }

    @Test(expected = InvalidTopicNameException.class)
    public void testInvalidPascalCase2() throws InvalidTopicNameException {
        DefaultTopicNameValidator validator = new DefaultTopicNameValidator("de.test", ".");

        validator.validateTopicName("testapp.internal.one.TT", TopicType.INTERNAL, mockApp("TestApp", "Goods"),
                mockMeta("testapp.internal."));
    }

    @Test(expected = InvalidTopicNameException.class)
    public void testInvalidOnlyNumbers1() throws InvalidTopicNameException {
        DefaultTopicNameValidator validator = new DefaultTopicNameValidator("de.test", ".");

        validator.validateTopicName("testapp.internal.13456.two", TopicType.INTERNAL, mockApp("TestApp", "Goods"),
                mockMeta("testapp.internal."));
    }

    @Test(expected = InvalidTopicNameException.class)
    public void testInvalidOnlyNumbers2() throws InvalidTopicNameException {
        DefaultTopicNameValidator validator = new DefaultTopicNameValidator("de.test", ".");

        validator.validateTopicName("testapp.internal.one.2", TopicType.INTERNAL, mockApp("TestApp", "Goods"),
                mockMeta("testapp.internal."));
    }

    @Test
    public void testSuggestions() {
        DefaultTopicNameValidator validator = new DefaultTopicNameValidator("de.test", ".");

        KnownApplication app = mockApp("TestApp", "Goods", "Bads");

        assertEquals("de.test.events.goods.my-topic", validator.getTopicNameSuggestion(TopicType.EVENTS, app,
                Mockito.mock(ApplicationMetadata.class), app.getBusinessCapabilities().get(0)));
        assertEquals("testapp.internal.my-topic",
                validator.getTopicNameSuggestion(TopicType.INTERNAL, app, mockMeta("testapp.internal."), null));
        validator = new DefaultTopicNameValidator("de!test", "!");
        assertEquals("de!test!commands!goods!my-topic", validator.getTopicNameSuggestion(TopicType.COMMANDS, app,
                Mockito.mock(ApplicationMetadata.class), app.getBusinessCapabilities().get(0)));
    }

    private KnownApplication mockApp(String appName, String... capNames) {
        KnownApplication app = Mockito.mock(KnownApplication.class);
        Mockito.when(app.getName()).thenReturn(appName);

        List<BusinessCapability> caps = new ArrayList<>();
        for (String capName : capNames) {
            BusinessCapability cap = Mockito.mock(BusinessCapability.class);
            Mockito.when(cap.getName()).thenReturn(capName);
            caps.add(cap);
        }

        Mockito.doReturn(caps).when(app).getBusinessCapabilities();
        return app;
    }

    private ApplicationMetadata mockMeta(String topicPrefix) {
        ApplicationMetadata meta = Mockito.mock(ApplicationMetadata.class);
        Mockito.when(meta.getTopicPrefix()).thenReturn(topicPrefix);
        return meta;
    }

}
