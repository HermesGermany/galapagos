package com.hermesworld.ais.galapagos.uisupport.controller;

import com.hermesworld.ais.galapagos.uisupport.controller.CustomLinkConfig;

import static org.junit.jupiter.api.Assertions.assertThrows;
import com.hermesworld.ais.galapagos.uisupport.controller.CustomLinksConfig;
import com.hermesworld.ais.galapagos.uisupport.controller.LinkType;
import org.junit.jupiter.api.Test;

import java.util.List;

class CustomLinksConfigTest {

    @Test
    void testCustomLinksConfigID() {
        assertThrows(RuntimeException.class, () -> {
            CustomLinkConfig customLinkConfig = generatedCustomLinkConfig(null, "www.test.de", "Test-Label",
                    LinkType.OTHER);
            new CustomLinksConfig().setLinks(List.of(customLinkConfig));
        });
    }

    @Test
    void testCustomLinksConfigH_Ref() {
        assertThrows(RuntimeException.class, () -> {
            CustomLinkConfig customLinkConfig = generatedCustomLinkConfig("42", null, "Test-Label", LinkType.OTHER);
            new CustomLinksConfig().setLinks(List.of(customLinkConfig));
        });
    }

    @Test
    void testCustomLinksConfigLabel() {
        assertThrows(RuntimeException.class, () -> {
            CustomLinkConfig customLinkConfig = generatedCustomLinkConfig("42", "www.test.de", null, LinkType.OTHER);
            new CustomLinksConfig().setLinks(List.of(customLinkConfig));
        });
    }

    @Test
    void testCustomLinksConfigLinkType() {
        assertThrows(RuntimeException.class, () -> {
            CustomLinkConfig customLinkConfig = generatedCustomLinkConfig("42", "www.test.de", "Test-Label", null);
            new CustomLinksConfig().setLinks(List.of(customLinkConfig));
        });
    }

    @Test
    void testCustomLinkConfigPositive() {
        CustomLinkConfig customLinkConfig = generatedCustomLinkConfig("42", "www.test.de", "Test-Label",
                LinkType.OTHER);
        new CustomLinksConfig().setLinks(List.of(customLinkConfig));
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
