package horse.wtf.nzyme.rest.resources.taps.reports.tables;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import java.util.List;
import java.util.Map;

@AutoValue
public abstract class DNSTablesReport {

    public abstract Map<String, DNSIPStatisticsReport> ips();
    public abstract List<DNSNxDomainLogReport> nxdomains();

    public abstract List<DNSEntropyLogReport> entropyLog();

    @JsonCreator
    public static DNSTablesReport create(@JsonProperty("ips") Map<String, DNSIPStatisticsReport> ips,
                                         @JsonProperty("nxdomains") List<DNSNxDomainLogReport> nxdomains,
                                         @JsonProperty("entropy_log") List<DNSEntropyLogReport> entropyLog) {
        return builder()
                .ips(ips)
                .nxdomains(nxdomains)
                .entropyLog(entropyLog)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_DNSTablesReport.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder ips(Map<String, DNSIPStatisticsReport> ips);

        public abstract Builder nxdomains(List<DNSNxDomainLogReport> nxdomains);

        public abstract Builder entropyLog(List<DNSEntropyLogReport> entropyLog);

        public abstract DNSTablesReport build();
    }

}