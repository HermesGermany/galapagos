package com.hermesworld.ais.galapagos.devauth;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hermesworld.ais.galapagos.util.HasKey;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@JsonSerialize
@Getter
@Setter
public class DevAuthenticationMetadata implements HasKey {

    private String userName;

    private String authenticationJson;

    @Override
    public String key() {
        return userName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        DevAuthenticationMetadata metadata = (DevAuthenticationMetadata) o;
        return Objects.equals(userName, metadata.userName)
                && Objects.equals(authenticationJson, metadata.authenticationJson);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userName);
    }
}
