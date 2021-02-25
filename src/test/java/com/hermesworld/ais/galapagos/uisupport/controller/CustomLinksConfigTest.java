package com.hermesworld.ais.galapagos.uisupport.controller;

import org.junit.Test;

import java.util.Collections;

public class CustomLinksConfigTest {

    @Test(expected = RuntimeException.class)
    public void testCustomLinksConfigID() {
        CustomLinkConfig customLinkConfig = generatedCustomLinkConfig(null, "www.test.de", "Test-Label",
                LinkType.OTHER);
        new CustomLinksConfig().setLinks(Collections.singletonList(customLinkConfig));
    }

    @Test(expected = RuntimeException.class)
    public void testCustomLinksConfigH_Ref() {
        CustomLinkConfig customLinkConfig = generatedCustomLinkConfig("42", null, "Test-Label", LinkType.OTHER);
        new CustomLinksConfig().setLinks(Collections.singletonList(customLinkConfig));
    }

    @Test(expected = RuntimeException.class)
    public void testCustomLinksConfigLabel() {
        CustomLinkConfig customLinkConfig = generatedCustomLinkConfig("42", "www.test.de", null, LinkType.OTHER);
        new CustomLinksConfig().setLinks(Collections.singletonList(customLinkConfig));
    }

    @Test(expected = RuntimeException.class)
    public void testCustomLinksConfigLinkType() {
        CustomLinkConfig customLinkConfig = generatedCustomLinkConfig("42", "www.test.de", "Test-Label", null);
        new CustomLinksConfig().setLinks(Collections.singletonList(customLinkConfig));
    }

    @Test
    public void testCustomLinkConfigPositive() {
        CustomLinkConfig customLinkConfig = generatedCustomLinkConfig("42", "www.test.de", "Test-Label",
                LinkType.OTHER);
        new CustomLinksConfig().setLinks(Collections.singletonList(customLinkConfig));
    }

    private CustomLinkConfig generatedCustomLinkConfig(String id, String href, String label, LinkType linkType) {
        CustomLinkConfig resultCustomLinkConfig = new CustomLinkConfig();
        resultCustomLinkConfig.setId(id);
        resultCustomLinkConfig.setHref(href);
        resultCustomLinkConfig.setLabel(label);
        resultCustomLinkConfig.setLinkType(linkType);
        return resultCustomLinkConfig;
    }

}
