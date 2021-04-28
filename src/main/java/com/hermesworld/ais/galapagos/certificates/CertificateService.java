package com.hermesworld.ais.galapagos.certificates;

import com.hermesworld.ais.galapagos.certificates.auth.CertificatesAuthenticationConfig;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCSException;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.Optional;

public interface CertificateService {

    void buildCaManagers(Map<String, CertificatesAuthenticationConfig> config, File certificatesWorkdir)
            throws IOException, GeneralSecurityException, OperatorCreationException, PKCSException;

    Optional<CaManager> getCaManager(String environmentId);

    /**
     * Returns the byte contents of a PKCS12 Keystore usable by clients as the Kafka Truststore. It contains all public
     * CA certificates of all configured Kafka environments. The password for this truststore is
     * <code>"changeit"</code>.
     *
     * @return The byte contents of a PKCS12 keystore, never <code>null</code>.
     */
    byte[] getTrustStorePkcs12();

}
