package com.hermesworld.ais.galapagos.util;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.X509ObjectIdentifiers;

public final class CertificateUtil {

	private CertificateUtil() {
	}

	public static String toAppCn(String appName) {
		String name = appName.toLowerCase(Locale.US);
		name = name.replaceAll("[^0-9a-zA-Z]", "_");
		while (name.contains("__")) {
			name = name.replace("__", "_");
		}
		if (name.startsWith("_")) {
			name = name.substring(1);
		}
		if (name.endsWith("_")) {
			name = name.substring(0, name.length() - 1);
		}

		return name;
	}

	public static String extractCn(String dn) {
		return extractCn(new X500Name(dn));
	}

	public static String extractCn(X500Name name) {
		return getCn(name.getRDNs());
	}

	private static String getCn(RDN[] rdns) {
		Optional<String> opValue = Arrays.asList(rdns).stream().filter(rdn -> isCn(rdn))
				.map(rdn -> rdn.getFirst().getValue().toString()).findFirst();
		return opValue.orElse(null);
	}

	private static boolean isCn(RDN rdn) {
		return X509ObjectIdentifiers.commonName.equals(rdn.getFirst().getType());
	}

}
