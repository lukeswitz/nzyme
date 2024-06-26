package app.nzyme.core.database.generic;

import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

@AutoValue
public abstract class NumberBucketAggregationResult {

    public abstract DateTime bucket();
    public abstract long count();

    public static NumberBucketAggregationResult create(DateTime bucket, long count) {
        return builder()
                .bucket(bucket)
                .count(count)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_NumberBucketAggregationResult.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder bucket(DateTime bucket);

        public abstract Builder count(long count);

        public abstract NumberBucketAggregationResult build();
    }
}
