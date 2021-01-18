package com.hermesworld.ais.galapagos.certificates;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;

import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCSException;

import com.hermesworld.ais.galapagos.kafka.config.KafkaEnvironmentConfig;

public interface CertificateService {

    CaManager buildCaManager(KafkaEnvironmentConfig environmentConfig, File certificatesWorkdir)
            throws IOException, GeneralSecurityException, OperatorCreationException;

    void buildTrustStore(Map<String, CaManager> caManagers) throws IOException, GeneralSecurityException, PKCSException;

    /**
     * Returns the byte contents of a PKCS12 Keystore usable by clients as the Kafka Truststore. It contains all public
     * CA certificates of all configured Kafka environments. The password for this truststore is
     * <code>"changeit"</code>.
     *
     * @return The byte contents of a PKCS12 keystore, never <code>null</code>.
     */
    byte[] getTrustStorePkcs12();

}
