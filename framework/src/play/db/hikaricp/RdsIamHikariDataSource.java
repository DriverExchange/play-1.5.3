package play.db.hikaricp;

import com.zaxxer.hikari.HikariDataSource;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.DefaultAwsRegionProviderChain;
import com.amazonaws.services.rds.auth.GetIamAuthTokenRequest;
import com.amazonaws.services.rds.auth.RdsIamAuthTokenGenerator;

import java.util.Map;
import java.util.HashMap;

public class RdsIamHikariDataSource extends HikariDataSource {

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
            .userName(getUsername())
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