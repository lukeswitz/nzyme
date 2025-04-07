package app.nzyme.core.security.sessions.db;

import com.google.auto.value.AutoValue;
import org.joda.time.DateTime;

import javax.annotation.Nullable;
import java.util.UUID;

@AutoValue
public abstract class SessionEntryWithUserDetails {

    public abstract Long id();
    public abstract String sessionId();
    public abstract UUID userId();
    public abstract boolean isSuperadmin();
    public abstract boolean isOrgadmin();
    public abstract String userEmail();
    public abstract String userName();
    public abstract String remoteIp();
    public abstract DateTime createdAt();

    @Nullable
    public abstract UUID organizationId();

    @Nullable
    public abstract UUID tenantId();

    @Nullable
    public abstract DateTime lastActivity();

    public abstract boolean mfaValid();

    @Nullable
    public abstract DateTime mfaRequestedAt();

    public abstract boolean mfaDisabled();

    public static SessionEntryWithUserDetails create(Long id, String sessionId, UUID userId, boolean isSuperadmin, boolean isOrgadmin, String userEmail, String userName, String remoteIp, DateTime createdAt, UUID organizationId, UUID tenantId, DateTime lastActivity, boolean mfaValid, DateTime mfaRequestedAt, boolean mfaDisabled) {
        return builder()
                .id(id)
                .sessionId(sessionId)
                .userId(userId)
                .isSuperadmin(isSuperadmin)
                .isOrgadmin(isOrgadmin)
                .userEmail(userEmail)
                .userName(userName)
                .remoteIp(remoteIp)
                .createdAt(createdAt)
                .organizationId(organizationId)
                .tenantId(tenantId)
                .lastActivity(lastActivity)
                .mfaValid(mfaValid)
                .mfaRequestedAt(mfaRequestedAt)
                .mfaDisabled(mfaDisabled)
                .build();
    }

    public static Builder builder() {
        return new AutoValue_SessionEntryWithUserDetails.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(Long id);

        public abstract Builder sessionId(String sessionId);

        public abstract Builder userId(UUID userId);

        public abstract Builder isSuperadmin(boolean isSuperadmin);

        public abstract Builder isOrgadmin(boolean isOrgadmin);

        public abstract Builder userEmail(String userEmail);

        public abstract Builder userName(String userName);

        public abstract Builder remoteIp(String remoteIp);

        public abstract Builder createdAt(DateTime createdAt);

        public abstract Builder organizationId(UUID organizationId);

        public abstract Builder tenantId(UUID tenantId);

        public abstract Builder lastActivity(DateTime lastActivity);

        public abstract Builder mfaValid(boolean mfaValid);

        public abstract Builder mfaRequestedAt(DateTime mfaRequestedAt);

        public abstract Builder mfaDisabled(boolean mfaDisabled);

        public abstract SessionEntryWithUserDetails build();
    }
}
