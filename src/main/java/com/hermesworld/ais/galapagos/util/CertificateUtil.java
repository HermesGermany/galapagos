package com.hermesworld.ais.galapagos.util;

import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.DERBMPString;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.AttributeTypeAndValue;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.asn1.x509.X509ObjectIdentifiers;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.OutputEncryptor;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.*;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS12SafeBagBuilder;
import org.bouncycastle.pkcs.jcajce.JcePKCS12MacCalculatorBuilder;
import org.bouncycastle.pkcs.jcajce.JcePKCSPBEOutputEncryptorBuilder;

import java.io.IOException;
import java.io.StringWriter;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;

public final class CertificateUtil {

    private static final Random RANDOM = new Random();

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

    public static PKCS10CertificationRequest buildCsr(X500Name subject, KeyPair keyPair)
            throws OperatorCreationException {
        PKCS10CertificationRequestBuilder csrBuilder = new JcaPKCS10CertificationRequestBuilder(subject,
                keyPair.getPublic());
        JcaContentSignerBuilder csBuilder = new JcaContentSignerBuilder("SHA256withRSA");
        ContentSigner signer = csBuilder.build(keyPair.getPrivate());

        return csrBuilder.build(signer);
    }

    public static byte[] buildPrivateKeyStore(X509Certificate publicCertificate, PrivateKey privateKey, char[] password)
            throws IOException, PKCSException, NoSuchAlgorithmException, OperatorCreationException {
        PKCS12PfxPduBuilder keyStoreBuilder = new PKCS12PfxPduBuilder();
        JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
        SubjectKeyIdentifier pubKeyId = extUtils.createSubjectKeyIdentifier(publicCertificate.getPublicKey());

        String cn = CertificateUtil.extractCn(publicCertificate.getSubjectX500Principal().getName());

        PKCS12SafeBagBuilder certBagBuilder = new JcaPKCS12SafeBagBuilder(publicCertificate);
        certBagBuilder.addBagAttribute(PKCS12SafeBag.friendlyNameAttribute, new DERBMPString(cn));
        certBagBuilder.addBagAttribute(PKCS12SafeBag.localKeyIdAttribute, pubKeyId);

        keyStoreBuilder
                .addEncryptedData(new JcePKCSPBEOutputEncryptorBuilder(PKCSObjectIdentifiers.pbeWithSHAAnd128BitRC2_CBC)
                        .setProvider("BC").build(password), certBagBuilder.build());

        OutputEncryptor encOut = new JcePKCSPBEOutputEncryptorBuilder(PKCSObjectIdentifiers.pbeWithSHAAnd128BitRC2_CBC)
                .setProvider("BC").build(password);
        PKCS12SafeBagBuilder keyBagBuilder = new JcaPKCS12SafeBagBuilder(privateKey, encOut);
        keyBagBuilder.addBagAttribute(PKCS12SafeBag.friendlyNameAttribute, new DERBMPString(cn));
        keyBagBuilder.addBagAttribute(PKCS12SafeBag.localKeyIdAttribute, pubKeyId);

        keyStoreBuilder.addData(keyBagBuilder.build());

        byte[] pkcs12Bytes = keyStoreBuilder
                .build(new JcePKCS12MacCalculatorBuilder(OIWObjectIdentifiers.idSHA1), password)
                .getEncoded(ASN1Encoding.DL);
        Arrays.fill(password, '*');
        return pkcs12Bytes;
    }

    public static KeyPair generateKeyPair() throws GeneralSecurityException {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA", "BC");
        gen.initialize(2048);
        return gen.generateKeyPair();
    }

    public static X500Name uniqueX500Name(String cn) {
        String ouValue = "certification_" + Integer.toHexString(RANDOM.nextInt());

        List<RDN> rdns = new ArrayList<>();
        rdns.add(new RDN(
                new AttributeTypeAndValue(X509ObjectIdentifiers.organizationalUnitName, new DERUTF8String(ouValue))));
        rdns.add(new RDN(new AttributeTypeAndValue(X509ObjectIdentifiers.commonName, new DERUTF8String(cn))));

        return new X500Name(rdns.toArray(new RDN[0]));
    }

    public static String toPemString(PKCS10CertificationRequest request) {
        StringWriter sw = new StringWriter();
        JcaPEMWriter writer = new JcaPEMWriter(sw);
        try {
            writer.writeObject(request);
            writer.flush();
            return sw.toString();
        }
        catch (IOException e) {
            // must not occur in memory
            throw new RuntimeException(e);
        }
    }

    private static String getCn(RDN[] rdns) {
        Optional<String> opValue = Arrays.stream(rdns).filter(rdn -> isCn(rdn))
                .map(rdn -> rdn.getFirst().getValue().toString()).findFirst();
        return opValue.orElse(null);
    }

    private static boolean isCn(RDN rdn) {
        return X509ObjectIdentifiers.commonName.equals(rdn.getFirst().getType());
    }

}
