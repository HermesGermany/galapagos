package com.hermesworld.ais.galapagos.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CnUtilTest {

    @Test
    void testToAppCn() {
        assertEquals("alpha", CertificateUtil.toAppCn("ALPHA"));
        assertEquals("track_trace", CertificateUtil.toAppCn("Track & Trace"));
        assertEquals("elisa", CertificateUtil.toAppCn("  Elisa "));
        assertEquals("elisa", CertificateUtil.toAppCn(" &!Elisa"));
        assertEquals("track_trace", CertificateUtil.toAppCn("track_trace"));
        assertEquals("ebay_shipping_client", CertificateUtil.toAppCn("Ebay Shipping  Client"));
    }

}
