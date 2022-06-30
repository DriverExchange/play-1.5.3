package play.db.c3p0;

import com.mchange.v2.c3p0.AbstractComboPooledDataSource;
import com.mchange.v2.c3p0.AbstractComboPooledDataSource;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.DefaultAwsRegionProviderChain;
import com.amazonaws.services.rds.auth.GetIamAuthTokenRequest;
import com.amazonaws.services.rds.auth.RdsIamAuthTokenGenerator;

import java.util.Map;
import java.util.HashMap;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import javax.naming.Referenceable;

public class RdsIamC3P0DataSource extends AbstractComboPooledDataSource implements Serializable, Referenceable {

    private static final long serialVersionUID = 1L;
    private static final short VERSION = 2;

    public RdsIamC3P0DataSource() {
    }

    public RdsIamC3P0DataSource(boolean autoregister) {
        super(autoregister);
    }

    public RdsIamC3P0DataSource(String configName) {
        super(configName);
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        oos.writeShort(2);
    }

    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        short version = ois.readShort();
        switch (version) {
            case 2:
                return;
            default:
                throw new IOException("Unsupported Serialized Version: " + version);
        }
    }

    @Override
    public String getPassword() {
        return getToken();
    }

    private String getToken() {
        var region = new DefaultAwsRegionProviderChain().getRegion();
        Map<String, Integer> hostnamePort = getHostnamePort();
        Map.Entry<String, Integer> entry = hostnamePort.entrySet().iterator().next();

        RdsIamAuthTokenGenerator generator = RdsIamAuthTokenGenerator.builder()
            .credentials(new DefaultAWSCredentialsProviderChain())
            .region(region)
            .build();

        GetIamAuthTokenRequest request = GetIamAuthTokenRequest.builder()
            .hostname(entry.getKey())
            .port(entry.getValue())
            .userName(getUser())
            .build();

        return generator.getAuthToken(request);
    }

    private Map<String, Integer> getHostnamePort() {
        Map<String, Integer> map = new HashMap<String, Integer>();
        int slashing = getJdbcUrl().indexOf("//") + 2;
        String sub = getJdbcUrl().substring(slashing, getJdbcUrl().indexOf("/", slashing));
        String[] splitted = sub.split(":");
        map.put(splitted[0], 5432);
        return map;
    }
}