/*
 * This file is part of nzyme.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Server Side Public License, version 1,
 * as published by MongoDB, Inc.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * Server Side Public License for more details.
 *
 * You should have received a copy of the Server Side Public License
 * along with this program. If not, see
 * <http://www.mongodb.com/licensing/server-side-public-license>.
 */

package app.nzyme.core.ethernet.dns.db;

import com.google.auto.value.AutoValue;
import jakarta.annotation.Nullable;

@AutoValue
public abstract class DNSPairSummary {

    public abstract String serverAddress();
    public abstract int serverPort();
    @Nullable
    public abstract Integer serverGeoAsnNumber();
    @Nullable
    public abstract String serverGeoAsnName();
    @Nullable
    public abstract String serverGeoAsnDomain();
    @Nullable
    public abstract String serverGeoCountryCode();
    public abstract Long requestCount();
    public abstract Long clientCount();

    public static DNSPairSummary create(String serverAddress, int serverPort, Integer serverGeoAsnNumber, String serverGeoAsnName, String serverGeoAsnDomain, String serverGeoCountryCode, Long requestCount, Long clientCount) {
        return builder()
                .serverAddress(serverAddress)
                .serverPort(serverPort)
                .serverGeoAsnNumber(serverGeoAsnNumber)
                .serverGeoAsnName(serverGeoAsnName)
                .serverGeoAsnDomain(serverGeoAsnDomain)
                .serverGeoCountryCode(serverGeoCountryCode)
                .requestCount(requestCount)
                .clientCount(clientCount)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DNSPairSummary.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder serverAddress(String serverAddress);

        public abstract Builder serverPort(int serverPort);

        public abstract Builder serverGeoAsnNumber(Integer serverGeoAsnNumber);

        public abstract Builder serverGeoAsnName(String serverGeoAsnName);

        public abstract Builder serverGeoAsnDomain(String serverGeoAsnDomain);

        public abstract Builder serverGeoCountryCode(String serverGeoCountryCode);

        public abstract Builder requestCount(Long requestCount);

        public abstract Builder clientCount(Long clientCount);

        public abstract DNSPairSummary build();
    }
}
