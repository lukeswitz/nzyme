package app.nzyme.core.security.authentication.db;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.joda.time.DateTime;

import java.sql.ResultSet;
import java.sql.SQLException;

public class TenantEntryMapper implements RowMapper<TenantEntry> {

    @Override
    public TenantEntry map(ResultSet rs, StatementContext ctx) throws SQLException {
        return TenantEntry.create(
                rs.getLong("id"),
                rs.getLong("organization_id"),
                rs.getString("name"),
                rs.getString("description"),
                new DateTime(rs.getTimestamp("created_at")),
                new DateTime(rs.getTimestamp("updated_at"))
        );
    }

}