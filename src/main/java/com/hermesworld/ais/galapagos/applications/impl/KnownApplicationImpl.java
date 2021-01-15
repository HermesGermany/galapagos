package com.hermesworld.ais.galapagos.applications.impl;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.hermesworld.ais.galapagos.applications.BusinessCapability;
import com.hermesworld.ais.galapagos.applications.KnownApplication;
import com.hermesworld.ais.galapagos.util.HasKey;
import lombok.extern.slf4j.Slf4j;

@JsonSerialize
@Slf4j
public class KnownApplicationImpl implements KnownApplication, HasKey, Comparable<KnownApplicationImpl> {

    private String id;

    private String name;

    private List<String> aliases;

    private String infoUrl;

    private List<BusinessCapabilityImpl> businessCapabilities;

    @JsonCreator
    public KnownApplicationImpl(@JsonProperty(value = "id", required = true) String id,
            @JsonProperty(value = "name", required = true) String name) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (name == null) {
            throw new IllegalArgumentException("name must not be null");
        }
        this.id = id;
        this.name = name;
    }

    @Override
    public String key() {
        return id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Set<String> getAliases() {
        return this.aliases == null ? Collections.emptySet() : new HashSet<>(this.aliases);
    }

    public void setAliases(List<String> aliases) {
        this.aliases = aliases;
    }

    @Override
    public URL getInfoUrl() {
        try {
            return this.infoUrl == null ? null : new URL(this.infoUrl);
        }
        catch (MalformedURLException e) {
            log.warn("Invalid info URL found in galapagos.internal.known-applications topic: " + this.infoUrl, e);
            return null;
        }
    }

    public void setInfoUrl(String infoUrl) {
        this.infoUrl = infoUrl;
    }

    @Override
    public List<BusinessCapability> getBusinessCapabilities() {
        return this.businessCapabilities == null ? Collections.emptyList()
                : this.businessCapabilities.stream().collect(Collectors.toList());
    }

    public void setBusinessCapabilities(List<BusinessCapabilityImpl> businessCapabilities) {
        this.businessCapabilities = businessCapabilities;
    }

    @Override
    public int compareTo(KnownApplicationImpl o) {
        if (o == null) {
            return 1;
        }
        return name.compareToIgnoreCase(o.name);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (obj.getClass() != getClass()) {
            return false;
        }

        return id.equals(((KnownApplicationImpl) obj).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

}
